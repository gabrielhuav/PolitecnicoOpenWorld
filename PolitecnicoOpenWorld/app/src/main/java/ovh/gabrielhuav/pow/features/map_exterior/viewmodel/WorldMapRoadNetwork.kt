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
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.LandmarkAssetTemplate
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
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ovh.gabrielhuav.pow.domain.models.ShineCTOLocation

internal fun WorldMapViewModel.updateVisibleRoads(location: GeoPoint, force: Boolean = false) {
        if (!_uiState.value.showRoadNetwork || roadNetwork.isEmpty()) {
            if (_roadNetworkFlow.value.isNotEmpty()) _roadNetworkFlow.value = emptyList()
            return
        }
        val lastLoc = lastVisibleRoadUpdateLocation
        // Solo recalculamos si forzamos la actualización o si el jugador se movió lo suficiente (~200m)
        if (force || lastLoc == null || distance(lastLoc, location) > VISIBLE_ROAD_UPDATE_THRESHOLD) {
            lastVisibleRoadUpdateLocation = location
            // Ejecutamos el filtro en un hilo secundario para no trabar el Game Loop
            viewModelScope.launch(Dispatchers.Default) {
                val visibleWays = roadNetwork.filter { way ->
                    // Una calle es visible si al menos uno de sus nodos está dentro del radio del jugador
                    way.nodes.any { node ->
                        abs(node.lat - location.latitude) < VISIBLE_ROAD_RADIUS &&
                                abs(node.lon - location.longitude) < VISIBLE_ROAD_RADIUS
                    }
                }
                // Actualizamos el Flow que lee la UI (pasará de ~5,000 calles a solo ~100)
                _roadNetworkFlow.value = visibleWays
            }
        }
    }

internal suspend fun WorldMapViewModel.applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network

        updateVisibleRoads(playerLocation, force = true)
        rebuildRoadNodeGrid(network)
        npcAiManager.updateRoadNetwork(network)

        // Solo intentamos spawnear la mano si estamos en ESCOM.
        // (spawnEscomItems igual se autoprotege, esto solo evita la llamada inútil.)
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
        val targetZoom = if (_uiState.value.mapProvider.isWebProvider)
            ZOOM_GAMEPLAY_WEB
        else
            ZOOM_GAMEPLAY_OSM

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
                        updateVisibleRoads(currentLoc, force = true)
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        val inside = isInsideEscom(currentLoc.latitude, currentLoc.longitude)
                        if (inside && !_uiState.value.isZombieHandSpawned) {
                            Log.d("DEBUG_ESCOM", "Red cargada tras teleport, spawneando...")
                            spawnEscomItems(roadNetwork)
                        }
                        _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                        spawnShineCTOMarker()
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
                            updateVisibleRoads(currentLoc, force = true)
                            npcAiManager.updateRoadNetwork(network)
                            lastNetworkFetchLocation = currentLoc
                            _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                            spawnShineCTOMarker()
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
