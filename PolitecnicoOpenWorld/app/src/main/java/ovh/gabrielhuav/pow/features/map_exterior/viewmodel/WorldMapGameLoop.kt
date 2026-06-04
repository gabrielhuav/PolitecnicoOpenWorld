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

internal fun WorldMapViewModel.startGameLoop() {
        if (gameLoopJob?.isActive == true) return

        gameLoopJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {

            while (_uiState.value.currentLocation == null) { kotlinx.coroutines.delay(100) }
            val initialLoc = _uiState.value.currentLocation!!

            if (_uiState.value.mapProvider == MapProvider.OSM) {
                _uiState.update { it.copy(tileSource = TileSource.LOCAL_OSM) }
            }

            val cached = roadNetworkCache.get(initialLoc.latitude, initialLoc.longitude)
            if (cached != null) {
                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                applyRoadNetwork(cached, initialLoc)
                lastNetworkFetchLocation = initialLoc
                spawnShineCTOMarker()
            } else {
                _uiState.update { it.copy(roadSource = RoadSource.LOADING) }
                var retryMs = 1_000L

                while (isActive && roadNetwork.isEmpty()) {
                    val network = overpassRepository.fetchRoadNetwork(initialLoc.latitude, initialLoc.longitude)
                    if (network.isNotEmpty()) {
                        _uiState.update { it.copy(roadSource = RoadSource.NETWORK) }
                        applyRoadNetwork(network, initialLoc)
                        lastNetworkFetchLocation = initialLoc

                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            roadNetworkCache.put(initialLoc.latitude, initialLoc.longitude, network)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                            }
                        }
                        break
                    } else {
                        _uiState.update { it.copy(isRoadNetworkReady = false) }
                        kotlinx.coroutines.delay(retryMs)
                        retryMs = (retryMs * 2).coerceAtMost(30_000L)
                    }
                }
            }

            var tickCount = 0L
            while (isActive) {
                try {
                    _uiState.value.currentLocation?.let { location ->
                        val inside = isInsideEscom(location.latitude, location.longitude)

                        // Sincroniza la mano con la zona ESCOM:
                        //  - Si salgo de ESCOM: borro la mano (lista + flag).
                        //  - Si entro a ESCOM y aún no hay mano: la genero.
                        if (!inside) {
                            if (_uiState.value.isZombieHandSpawned || _escomItems.value.isNotEmpty()) {
                                _escomItems.value = emptyList()
                                _uiState.update { it.copy(isZombieHandSpawned = false) }
                            }
                        } else {
                            if (!_uiState.value.isZombieHandSpawned && _uiState.value.isRoadNetworkReady) {
                                _uiState.update { it.copy(isZombieHandSpawned = true) }
                            }
                        }

                        if (tickCount % 30 == 0L) {
                            trySpawningCollectible(location.latitude, location.longitude)
                        }
                        checkCollectibleProximity(location.latitude, location.longitude)

                        checkDestinationArrival()

                        if (tickCount % 30 == 0L && _uiState.value.destinationMarker != null) {
                            updateDestinationRoute()
                        }

                        if (_uiState.value.playerAction == PlayerAction.SPECIAL) {
                            performPlayerAttack()
                        }

                        if (_uiState.value.isDriving && !_uiState.value.showWastedScreen) {
                            // Si el mapa está descentrado (exploración), usar CUALQUIER control
                            // de conducción (acelerar/frenar/girar) recentra en el jugador, igual
                            // que a pie con el joystick/D-pad.
                            if (_uiState.value.isUserPanningMap &&
                                (isGasPressed || isBrakePressed || isSteeringLeftPressed || isSteeringRightPressed)) {
                                centerOnPlayer()
                            }

                            var currentSpeed = _uiState.value.vehicleSpeed
                            var currentRotation = _uiState.value.vehicleRotation

                            if (isSteeringLeftPressed && currentSpeed != 0.0) currentRotation -= 2f
                            if (isSteeringRightPressed && currentSpeed != 0.0) currentRotation += 2f

                            if (isGasPressed) {
                                currentSpeed = (currentSpeed + ACCELERATION).coerceAtMost(MAX_SPEED)
                            } else if (isBrakePressed) {
                                currentSpeed -= BRAKING_FRICTION
                                if (currentSpeed < -MAX_SPEED / 2) currentSpeed = -MAX_SPEED / 2
                            } else {
                                if (currentSpeed > 0) currentSpeed = (currentSpeed - (ACCELERATION / 2)).coerceAtLeast(0.0)
                                if (currentSpeed < 0) currentSpeed = (currentSpeed + (ACCELERATION / 2)).coerceAtMost(0.0)
                            }

                            val angleRad = Math.toRadians(currentRotation.toDouble())
                            val dx = kotlin.math.sin(angleRad) * currentSpeed
                            val dy = kotlin.math.cos(angleRad) * currentSpeed

                            val tempLoc = GeoPoint(location.latitude + dy, location.longitude + dx)

                            val nearestRoadPoint = getNearestPointOnNetwork(tempLoc)
                            val distToRoad = distance(tempLoc, nearestRoadPoint)
                            val maxRoadRadius = 0.000025

                            val finalLoc = if (distToRoad <= maxRoadRadius) {
                                tempLoc
                            } else {
                                val angleBack = atan2(tempLoc.latitude - nearestRoadPoint.latitude, tempLoc.longitude - nearestRoadPoint.longitude)
                                currentSpeed *= 0.8
                                GeoPoint(
                                    nearestRoadPoint.latitude + sin(angleBack) * maxRoadRadius,
                                    nearestRoadPoint.longitude + cos(angleBack) * maxRoadRadius
                                )
                            }

                            _uiState.update {
                                it.copy(
                                    currentLocation = finalLoc,
                                    vehicleSpeed = currentSpeed,
                                    vehicleRotation = (currentRotation + 360) % 360f
                                )
                            }

                            // ATROPELLO: daña peatones que el vehículo arrolla (escala con la
                            // velocidad). Barato: recorre los NPCs en memoria con corte temprano.
                            runOverNpcs(finalLoc, currentSpeed)
                        }

                        // GOLPES de NPCs agresivos en embestida (a pie o en coche).
                        _uiState.value.currentLocation?.let { applyNpcContactDamage(it) }

                        // POLICÍA: nivel de búsqueda (spawn de patrullas, persecución,
                        // golpes/disparos) y decaimiento. La simula el dueño del nivel.
                        if (_uiState.value.isRoadNetworkReady && !_uiState.value.showWastedScreen) {
                            _uiState.value.currentLocation?.let { runPoliceTick(it) }
                        }

                        maybeRefetchRoadNetwork(location)
                        // El throttle (cada 5 ticks) es el control de frecuencia deseado.
                        // Antes había además una llamada incondicional aquí que lo anulaba y
                        // lanzaba el filtrado de calles hasta ~30x más seguido de lo previsto.
                        if (tickCount % 5 == 0L) {
                            updateVisibleRoads(location)
                        }
                        if (_uiState.value.isRoadNetworkReady) {
                            tickCount++
                            if (tickCount % 3 == 0L) {
                                val npcOnlyList = remoteEntities.values.filter { it.displayName.isNullOrEmpty() }
                                npcAiManager.setServerNpcs(npcOnlyList)

                                npcAiManager.updateNpcs(location, isServerDelegatedHost)
                                val processedNpcs = npcAiManager.getServerNpcs()

                                if (isServerDelegatedHost) {
                                    synchronized(npcAiManager.pendingDespawns) {
                                        npcAiManager.pendingDespawns.forEach { remoteEntities.remove(it) }
                                    }
                                    processedNpcs.forEach { remoteEntities[it.id] = it }
                                }
                                updateNpcsState()

                                webSocketManager?.let { ws ->
                                    launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val myData = MultiplayerPlayer(
                                                id = myPlayerUUID,
                                                displayName = myPlayerDisplayName,
                                                x = location.longitude,
                                                y = location.latitude,
                                                action = _uiState.value.playerAction.name,
                                                facingRight = _uiState.value.isPlayerFacingRight,
                                                isDriving = _uiState.value.isDriving,
                                                carModel = _uiState.value.currentVehicleModel?.name,
                                                carColor = _uiState.value.currentVehicleColor,
                                                vehicleRotation = _uiState.value.vehicleRotation,
                                                health = playerHealth
                                            )
                                            ws.sendMessage(gson.toJson(myData))

                                            if (isServerDelegatedHost) {
                                                val despawnsToSend = synchronized(npcAiManager.pendingDespawns) {
                                                    val list = npcAiManager.pendingDespawns.toList()
                                                    npcAiManager.pendingDespawns.clear()
                                                    list
                                                }

                                                despawnsToSend.forEach { idToRemove ->
                                                    ws.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to idToRemove)))
                                                }

                                                // Solo reenviamos los NPCs ACTIVOS (dentro del fog ~0.0012);
                                                // los "congelados" lejanos no se mueven y ya viven en el
                                                // roster del servidor, así no malgastamos ancho de banda.
                                                val activeNpcs = processedNpcs.filter { distance(location, it.location) <= 0.0012 }
                                                if (activeNpcs.isNotEmpty()) {
                                                    val npcBatch = activeNpcs.map { npc ->
                                                        MultiplayerNpc(
                                                            id = npc.id,
                                                            x = npc.location.longitude,
                                                            y = npc.location.latitude,
                                                            rotation = npc.rotationAngle,
                                                            npcType = npc.type.name,
                                                            ownerId = myPlayerUUID,
                                                            carModel = npc.carModel?.name,
                                                            carColor = npc.carColor,
                                                            hairId = npc.visualConfig?.hairId,
                                                            hairColor = npc.visualConfig?.hairColor?.toArgb(),
                                                            shirtColor = npc.visualConfig?.shirtColor?.toArgb(),
                                                            pantsColor = npc.visualConfig?.pantsColor?.toArgb(),
                                                            health = npc.health,
                                                            isDying = npc.isDying,
                                                            aggroUntil = npc.aggroUntil
                                                        )
                                                    }
                                                    ws.sendMessage(gson.toJson(mapOf("type" to "NPC_BATCH_UPDATE", "npcs" to npcBatch)))
                                                }
                                            } else {
                                                synchronized(npcAiManager.pendingDespawns) { npcAiManager.pendingDespawns.clear() }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("Network", "Error al enviar datos: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("GameLoop", "Crasheo evitado en el ciclo principal: ${e.message}")
                }
                kotlinx.coroutines.delay(33)
            }
        }
    }
