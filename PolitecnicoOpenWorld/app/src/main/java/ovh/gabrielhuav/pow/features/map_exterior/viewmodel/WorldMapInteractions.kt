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
import java.io.InputStreamReader
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig

// ─────────────────────────────────────────────────────────────────────────────
// Interacciones del jugador (intenciones de UI) extraídas de WorldMapViewModel.kt:
// abordar/bajar vehículo, interactuar con puerta/metro/coleccionable, reclamar objeto,
// teletransporte directo, y el toggle del apocalipsis zombi global (instancing).
// El ESTADO sigue en el ViewModel; aquí solo hay lógica (extensiones internal).
// ─────────────────────────────────────────────────────────────────────────────

internal fun WorldMapViewModel.onInteractButtonPressed() {
        val loc = _uiState.value.currentLocation ?: return

        // FIX duplicación de autos: (1) DEBOUNCE — spamear Y alternaba subir/bajar más
        // rápido que el ciclo de la IA y duplicaba el coche; se ignoran pulsaciones a
        // menos de 450 ms de la anterior.
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastVehicleToggleMs < 450L) return

        // PRANKEDY ya NO es contratable: es un NPC hostil; no hay interacción con X.

        if (!_uiState.value.isDriving) {
            val nearbyCarEntry = remoteEntities.entries
                .filter { it.value.type == NpcType.CAR && distance(loc, it.value.location) <= INTERACT_RADIUS }
                .minByOrNull { distance(loc, it.value.location) }

            if (nearbyCarEntry != null) {
                lastVehicleToggleMs = nowMs
                val carId = nearbyCarEntry.key
                val carNpc = nearbyCarEntry.value
                remoteEntities.remove(carId)
                // (2) TOMBSTONE — el game loop tomaba el snapshot de la IA ANTES de subirte
                // y al volcar processedNpcs RE-INSERTABA el coche recién abordado (carrera
                // main-thread vs loop) → coche duplicado. Marcamos el id como "abordado"
                // unos segundos para que el volcado lo ignore.
                boardedCarTombstones[carId] = nowMs + 10_000L
                // Y avisar a los demás clientes que ese NPC dejó de existir.
                synchronized(npcAiManager.pendingDespawns) { npcAiManager.pendingDespawns.add(carId) }
                if (carNpc.isFirstTimeBoarded) {
                    spawnOustedDriver(carNpc.location)
                    raiseWantedLevel(1) // robar un auto ocupado es delito → +1 estrella
                }
                // Si el coche traía skin de patrulla (una patrulla que abandonaste), al
                // re-subirte vuelves a conducirla con el skin de policía.
                _uiState.update { it.copy(isDriving = true, currentVehicleModel = carNpc.carModel, currentVehicleColor = carNpc.carColor, vehicleRotation = (carNpc.rotationAngle + 90f) % 360f, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = false, isDrivingPoliceCar = carNpc.isPoliceSkin) }
                // H (Modo Historia): si Prankedy es tu ACOMPAÑANTE (escolta), debe SUBIR contigo: corre
                // hasta tu posición y el coche NO avanza hasta que se sube (lo completa runPrankedyTick).
                if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED &&
                    _uiState.value.prankedyEnabled && prankedyManager.location != null) {
                    prankedyBoardingStartMs = nowMs
                    _uiState.update { it.copy(prankedyBoarding = true) }
                }
                prankedyManager.onVehicleInteraction()
                updateNpcsState()
                return
            }

            // PATRULLAS: si no hay coche civil cerca, intenta SUBIRTE a una patrulla. Las
            // patrullas las posee PoliceManager (no remoteEntities), así que se buscan en
            // sus unidades activas. Robar una patrulla = nivel de búsqueda MÁXIMO (5★).
            val nearbyPatrol = policeManager.activeUnits()
                .filter { it.type == NpcType.POLICE_CAR && distance(loc, it.location) <= INTERACT_RADIUS }
                .minByOrNull { distance(loc, it.location) }
            if (nearbyPatrol != null) {
                val boarded = policeManager.boardPatrol(nearbyPatrol.id)
                if (boarded != null) {
                    lastVehicleToggleMs = nowMs
                    // Avisar a los demás clientes que esa patrulla dejó de existir.
                    webSocketManager?.let { ws ->
                        viewModelScope.launch(Dispatchers.IO) {
                            try { ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to boarded.id))) } catch (_: Exception) {}
                        }
                    }
                    // Subirse a la patrulla pone TODAS las estrellas (5★).
                    lastCrimeTime = nowMs
                    _uiState.update { it.copy(
                        isDriving = true,
                        currentVehicleModel = boarded.carModel,
                        currentVehicleColor = boarded.carColor,
                        vehicleRotation = (boarded.rotationAngle + 90f) % 360f,
                        vehicleSpeed = 0.0,
                        vehicleIsFirstTimeBoarded = false,
                        isDrivingPoliceCar = true,
                        wantedLevel = MAX_WANTED_LEVEL
                    ) }
                    prankedyManager.onVehicleInteraction()
                    updateNpcsState()
                }
            }
        } else {
            lastVehicleToggleMs = nowMs
            val abandonedCar = Npc(
                id = UUID.randomUUID().toString(),
                type = NpcType.CAR,
                location = loc,
                rotationAngle = (_uiState.value.vehicleRotation + 270f) % 360f,
                speed = 0.0,
                isMoving = false,
                carModel = _uiState.value.currentVehicleModel ?: CarModel.SEDAN,
                carColor = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
                isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded,
                // Si te bajas de una PATRULLA robada, el coche que queda conserva el skin de
                // patrulla (sigue siendo tipo CAR para que la IA lo conduzca como tráfico).
                isPoliceSkin = _uiState.value.isDrivingPoliceCar,
                navState = if (isInsideEscom(loc.latitude, loc.longitude)) ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED else ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM
            )
            remoteEntities[abandonedCar.id] = abandonedCar
            _uiState.update { it.copy(isDriving = false, currentVehicleModel = null, currentVehicleColor = null, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = true, isDrivingPoliceCar = false, prankedyBoarding = false) }
            prankedyManager.onVehicleInteraction()
            updateNpcsState()
        }
    }

    /**
     * Interacción con la mano: en lugar de entrar a un interior concreto, marca
     * el flag pendingZombieMinigame para que, tras el video, WorldMapScreen
     * navegue a la ruta "interiores_zombies" (modo Interiores → capa zombis).
     */
internal fun WorldMapViewModel.handleInteraction() {
        val nearbyMetro = _uiState.value.nearbyMetroStation
        if (nearbyMetro != null) {
            _uiState.update { it.copy(showMetroFade = true) }
            return
        }

        val nearbyMetrobus = _uiState.value.nearbyMetrobusStation
        if (nearbyMetrobus != null) {
            _uiState.update { it.copy(showMetrobusFade = true) }
            return
        }

        val nearby = _uiState.value.nearbyCollectible ?: return

        when {
            nearby.id == "global_zombie_hand" -> toggleGlobalZombieMode()
            nearby.name == "Objeto Misterioso ESCOM" -> {
                pendingZombieMinigame = true
                _uiState.update {
                    it.copy(
                        showZombiVideo = true,
                        pendingInteriorDestination = InteriorBuilding.EDIFICIO
                    )
                }
            }
            nearby.id.startsWith("escom_door_") -> {
                // Enrutamos la puerta a su interior por el NOMBRE del landmark, vía el
                // catálogo data-driven InteriorEntryCatalog (antes era un `when` hardcodeado
                // aquí). Usa `contains` (no match exacto) para tolerar variantes/acentos al
                // colocar la puerta en el Diseñador; si nada casa, cae a DEFAULT_ROUTE (lobby
                // ESCOM). Para añadir un edificio enterable, edita InteriorEntryCatalog. Ver 04/06.
                val targetRoute = ovh.gabrielhuav.pow.domain.models.map.InteriorEntryCatalog.routeForDoorName(nearby.name)
                // MODO HISTORIA · Misión 2 "Ingresa a la ESCOM": se cumple al ENTRAR por la puerta
                // (este es el momento de "ingresar"). Marca el objetivo cumplido + jingle.
                if (_uiState.value.currentObjective?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id
                    && !_uiState.value.objectiveDone) {
                    _uiState.update { it.copy(objectiveDone = true, interactionPrompt = "✅ Objetivo cumplido: ${_uiState.value.currentObjective?.let { getLocalizedString(it.titleRes) } ?: ""}") }
                    soundManager.playMisionCumplida()
                }
                // Al ENTRAR a la ESCOM, Prankedy ya quedó a salvo dentro: deja de acompañarte para
                // que NO siga contigo al volver al mapa (Misión 1 terminada). Solo afecta al
                // acompañante de campaña (fase HIRED); el Prankedy hostil del menú no se toca aquí.
                if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
                    prankedyManager.deactivate()
                    prankedyCompanionActivated = true   // no re-encenderlo en este tramo
                    _uiState.update { it.copy(
                        prankedyEnabled = false,
                        prankedyVisible = false,
                        prankedyLocation = null,
                        prankedyProjectileActive = false,
                        prankedyDialogue = null
                    ) }
                }
                _uiState.update { it.copy(showEscomDoorFade = true, pendingDoorDestination = targetRoute) }
            }

            nearby.id == ShineCTOLocation.MARKER_ID -> {
                _uiState.update { it.copy(showShineCTODiscovery = true) }
            }
            else -> onClaimCollectiblePressed()
        }
    }

internal fun WorldMapViewModel.onClaimCollectiblePressed() {
        val itemToClaim = _uiState.value.nearbyCollectible ?: return

        if (itemToClaim.name == "Objeto Misterioso ESCOM" ||
            itemToClaim.id == ShineCTOLocation.MARKER_ID ||
            itemToClaim.id.startsWith("escom_door_")) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.claimCollectible(itemToClaim.id)
            withContext(Dispatchers.Main) {
                promptJob?.cancel()
                promptJob = null
                _uiState.update {
                    it.copy(
                        activeCollectibles = emptyList(),
                        nearbyCollectible = null,
                        interactionPrompt = null,
                        showClaimedPopupFor = itemToClaim
                    )
                }
            }
        }
    }

internal fun WorldMapViewModel.dismissClaimedPopup() { _uiState.update { it.copy(showClaimedPopupFor = null) } }

internal fun WorldMapViewModel.teleportToLocation(newLat: Double, newLon: Double) {
        val insideEscom = isInsideEscom(newLat, newLon)

        _uiState.update { currentState ->
            currentState.copy(
                currentLocation = GeoPoint(newLat, newLon),
                showTeleportMenu = false,
                isRoadNetworkReady = false,
                isMapReady = false,         // ← re-activa la compuerta de descarga del mapa
                isUserPanningMap = false,   // ← igual que arriba
                isZombieHandSpawned = if (!insideEscom) false else currentState.isZombieHandSpawned
            )
        }

        lastNetworkFetchLocation = null
        lastFetchAttemptMs = 0L
        gateMapDownloadAfterTeleport()
    }

internal fun WorldMapViewModel.dismissVideo() {
        _uiState.update { it.copy(showZombiVideo = false) }
        // pendingInteriorDestination queda intacto: WorldMapScreen lo observará
        // y disparará la navegación. La pantalla lo limpiará con
        // clearPendingInteriorDestination() después de navegar.
    }

internal fun WorldMapViewModel.clearPendingInteriorDestination() {
        _uiState.update { it.copy(pendingInteriorDestination = null) }
    }

    /** Limpia el flag tras navegar al minijuego de zombis. */
internal fun WorldMapViewModel.clearPendingZombieMinigame() { pendingZombieMinigame = false }

internal fun WorldMapViewModel.toggleInteriorDebugOverlay(show: Boolean) {
        _uiState.update { it.copy(showInteriorDebugOverlay = show) }
    }

internal fun WorldMapViewModel.toggleGlobalZombieMode() = setZombieInstance(!_uiState.value.globalZombieMode)

internal fun WorldMapViewModel.exitGlobalZombieMode() { if (_uiState.value.globalZombieMode) setZombieInstance(false) }

    /**
     * INSTANCING: activar/desactivar el apocalipsis = cambiar de INSTANCIA ("apocalipsis" /
     * "normal"). Limpiamos el mundo local (no arrastrar entidades de la otra instancia) y
     * pedimos al servidor (JOIN_INSTANCE) el roster de la nueva instancia. Así los jugadores en
     * "normal" no ven el apocalipsis y viceversa. En single-player solo cambia el flag local
     * (el toggle no manda red) y el seed repobla el mundo según el modo.
     */
internal fun WorldMapViewModel.setZombieInstance(apocalypse: Boolean) {
        _uiState.update { it.copy(globalZombieMode = apocalypse) }
        npcAiManager.globalZombieMode = apocalypse
        // Mundo limpio: vaciar entidades remotas (el servidor reenvía SYNC_ALL_NPCS de la nueva
        // instancia; en SP el seed repobla). Evita ver NPCs/zombis de la instancia anterior.
        remoteEntities.clear()
        updateNpcsState()
        try {
            webSocketManager?.sendMessage(gson.toJson(mapOf(
                "type" to "JOIN_INSTANCE",
                "instance" to if (apocalypse) "apocalipsis" else "normal"
            )))
        } catch (_: Exception) {}
    }

