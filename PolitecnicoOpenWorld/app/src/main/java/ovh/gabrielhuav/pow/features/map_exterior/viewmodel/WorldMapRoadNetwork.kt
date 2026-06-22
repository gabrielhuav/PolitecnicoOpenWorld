package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation

internal fun WorldMapViewModel.updateVisibleRoads(location: GeoPoint, force: Boolean = false) {
        // DE-DUP (2026-06-21, par 6): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes de
        // borrarlo. El miembro usaba filtro CIRCULAR (distance <= RADIO) + ANTI-CARRERA (re-chequea zona
        // libre tras el filtro); esta extensión vieja usaba caja CUADRADA (abs<RADIO) sin anti-carrera y
        // un guard extra (!showRoadNetwork || roadNetwork.isEmpty()) que el miembro NO tiene (con red vacía
        // el filtro ya devuelve lista vacía). Param renombrado playerLoc→location (todos los call-sites son
        // posicionales). Cascada SEGURA: distance/isFreeMovementZone son miembros internal únicos (sin gemelo).
        // 🆕 ZONA LIBRE (ESCOM/ENCB): dentro del campus NO se pintan calles; vacía el Flow y SALTA el filtro.
        if (isFreeMovementZone(location.latitude, location.longitude)) {
            wasInFreeMovementZone = true
            if (_roadNetworkFlow.value.isNotEmpty()) _roadNetworkFlow.value = emptyList()
            return
        }
        // SALIDA de zona libre: en el primer tick fuera del campus FORZAMOS el repintado, porque el flow
        // quedó vacío al entrar y el throttle por distancia lo suprimiría (campus pequeño). Así las calles
        // reaparecen al instante al pisar el mundo abierto.
        val leftFreeZone = wasInFreeMovementZone
        wasInFreeMovementZone = false
        val effectiveForce = force || leftFreeZone

        val lastUpdate = lastVisibleRoadUpdateLocation
        if (!effectiveForce && lastUpdate != null) {
            val dist = distance(location, lastUpdate)
            if (dist < VISIBLE_ROAD_UPDATE_THRESHOLD) return
        }
        lastVisibleRoadUpdateLocation = GeoPoint(location.latitude, location.longitude)

        viewModelScope.launch(Dispatchers.Default) {
            val visible = roadNetwork.filter { way ->
                way.nodes.any { node ->
                    distance(location, GeoPoint(node.lat, node.lon)) <= VISIBLE_ROAD_RADIUS
                }
            }
            // Anti-carrera: si al terminar el filtro el jugador YA está en zona libre, no repintamos
            // (evita que una corutina lanzada en el borde reponga las líneas).
            if (!isFreeMovementZone(location.latitude, location.longitude)) {
                _roadNetworkFlow.value = visible
            } else if (_roadNetworkFlow.value.isNotEmpty()) {
                _roadNetworkFlow.value = emptyList()
            }
        }
    }

// DE-DUP (2026-06-21): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes de
// eliminarlo. El miembro construía ADEMÁS el grafo A* de la policía (`buildRoadGraph`) y pintaba
// las calles con la ubicación YA snapeada (`updateVisibleRoads(snapped, ...)`); la vieja extensión
// muerta omitía `buildRoadGraph` y usaba `playerLocation` sin snapear + un `prefetchCurrentZoneTiles`
// que el miembro no hacía. Ahora la extensión REPRODUCE exactamente el miembro. Ver 09 §12.
internal suspend fun WorldMapViewModel.applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network
        rebuildRoadNodeGrid(network)
        buildRoadGraph(network)   // grafo para el A* de la policía
        npcAiManager.updateRoadNetwork(network)

        if (isInsideEscom(playerLocation.latitude, playerLocation.longitude)) {
            spawnEscomItems(network)
        } else {
            _escomItems.value = emptyList()
            _uiState.update { it.copy(isZombieHandSpawned = false) }
        }

        val snapped = withContext(Dispatchers.Default) { getNearestPointOnNetwork(playerLocation) }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(currentLocation = snapped, isRoadNetworkReady = true) }
        }
        // Pinta las calles (líneas amarillas) de inmediato al quedar lista la red, sin
        // esperar al throttle del game loop (antes "tardaban en colocarse" tras entrar).
        updateVisibleRoads(snapped, force = true)
        // Zoom de juego A PIE = 22 para TODOS los proveedores (los web sobre-escalan
        // desde su maxNativeZoom; CARTO llega a z20 real).
        val targetZoom = ZOOM_ON_FOOT

        if (_uiState.value.zoomLevel <= ZOOM_LOADING) {
            var z = ZOOM_LOADING + 1.0
            while (z <= targetZoom) {
                delay(120)
                withContext(Dispatchers.Main) { _uiState.update { it.copy(zoomLevel = z) } }
                z += 1.0
            }
        }
    }

internal fun WorldMapViewModel.maybeRefetchRoadNetwork(currentLoc: org.osmdroid.util.GeoPoint) {
        // DE-DUP (2026-06-21, par 5): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes de
        // borrarlo. El miembro DIVERGÍA: reconstruye TODOS los índices (rebuildRoadNodeGrid + buildRoadGraph
        // = grid de routing + grafo A*) en vez de llamar updateVisibleRoads/spawnShineCTOMarker/
        // prefetchCurrentZoneTiles (que hacía esta extensión vieja). Cascada verificada SEGURA:
        // rebuildRoadNodeGrid es gemelo pero IDÉNTICO (miembro==ext); buildRoadGraph/isInsideEscom/
        // spawnEscomItems son def única. NO toca la cadena de routing (no llama calculateRouteOnNetwork).
        val moved = if (lastNetworkFetchLocation != null)
            distance(lastNetworkFetchLocation!!, currentLoc) else Double.MAX_VALUE
        if (moved < REFETCH_DISTANCE_DEG) return

        val now = System.currentTimeMillis()
        if (now - lastFetchAttemptMs < REFETCH_COOLDOWN_MS) return
        if (!isFetchingNetwork.compareAndSet(false, true)) return
        lastFetchAttemptMs = now

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cached = roadNetworkCache.get(currentLoc.latitude, currentLoc.longitude)
                if (cached != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        roadNetwork = cached
                        // Mantener TODOS los índices en sync con la red nueva (antes solo se
                        // actualizaba la IA; el grid de routing y el grafo A* quedaban viejos).
                        rebuildRoadNodeGrid(cached)
                        buildRoadGraph(cached)
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        val inside = isInsideEscom(currentLoc.latitude, currentLoc.longitude)
                        if (inside && !_uiState.value.isZombieHandSpawned) {
                            Log.d("DEBUG_ESCOM", "Red cargada tras teleport, spawneando...")
                            spawnEscomItems(roadNetwork)
                        }
                        _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.NETWORK) }
                    }
                    val network = overpassRepository.fetchRoadNetwork(
                        currentLoc.latitude, currentLoc.longitude)
                    if (network.isNotEmpty()) {
                        roadNetworkCache.put(currentLoc.latitude, currentLoc.longitude, network)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            roadNetwork = network
                            rebuildRoadNodeGrid(network)
                            buildRoadGraph(network)
                            npcAiManager.updateRoadNetwork(network)
                            lastNetworkFetchLocation = currentLoc
                            _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _uiState.update { it.copy(isRoadNetworkReady = true) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error refetching road network", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isRoadNetworkReady = true) }
                }
            } finally {
                isFetchingNetwork.set(false)
            }
        }
    }

/**
 * Pre-descarga (NO bloqueante) los tiles de la zona actual (~2 km) a la caché Room
 * para juego offline. Solo aplica al proveedor NATIVO OSM (los Web cachean por
 * WebView bajo demanda). Debounce por celda ~0.01° (~1 km) para no repetir; si la
 * descarga falla por falta de red, se permite reintentar al volver a la celda.
 */
internal fun WorldMapViewModel.prefetchCurrentZoneTiles(loc: GeoPoint) {
    if (_uiState.value.mapProvider != MapProvider.OSM) return
    val cellKey = "${floor(loc.latitude / 0.01).toInt()}_${floor(loc.longitude / 0.01).toInt()}"
    if (cellKey == lastPrefetchCellKey || tilePrefetch.isRunning()) return
    lastPrefetchCellKey = cellKey

    viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    zonePrefetchActive = true,
                    zonePrefetchProgress = 0f,
                    zoneOfflineReady = false,
                    zoneOfflineWarning = false
                )
            }
        }
        tilePrefetch.prefetchOsmZone(
            centerLat = loc.latitude,
            centerLon = loc.longitude,
            radiusMeters = 1000.0,
            zooms = 16..18,
            onProgress = { f -> _uiState.update { it.copy(zonePrefetchProgress = f) } },
            onDone = { ok ->
                if (!ok) lastPrefetchCellKey = null // permitir reintento sin red
                _uiState.update {
                    it.copy(
                        zonePrefetchActive = false,
                        zonePrefetchProgress = 1f,
                        zoneOfflineReady = ok,
                        zoneOfflineWarning = !ok
                    )
                }
            }
        )
    }
}
