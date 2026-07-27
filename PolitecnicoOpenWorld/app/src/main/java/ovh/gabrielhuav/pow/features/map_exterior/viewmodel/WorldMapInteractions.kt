package ovh.gabrielhuav.pow.features.map_exterior.viewmodel


import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Interacciones del jugador (intenciones de UI) extraídas de WorldMapViewModel.kt:
// abordar/bajar vehículo, interactuar con puerta/metro/coleccionable, reclamar objeto,
// teletransporte directo, y el toggle del apocalipsis zombi global (instancing).
// El ESTADO sigue en el ViewModel; aquí solo hay lógica (extensiones internal).
// ─────────────────────────────────────────────────────────────────────────────

// ─── INVENTARIO EN EL MAPA (mantener Y, 🆕 2026-07-13) ──────────────────────
// Panel de SOLO LECTURA con los objetos de misión (llave M1 / lata M2): mismos datos que el
// inventario de interiores (currentInteriorInventory). Probar/desechar siguen siendo de
// interiores (ahí vive la lógica del puzzle).
fun WorldMapViewModel.toggleWorldInventory(show: Boolean) {
    _uiState.update { it.copy(showWorldInventory = show) }
}

/** Slots desbloqueados para el panel del mapa (mismo gate que AppNavGraph: mochila M2 = 4). */
fun WorldMapViewModel.worldInventoryUnlockedSlots(): Int =
    if (mission2Phase >= ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_DONE ||
        campaignManager.isCompleted(ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_2_ID)) 4 else 2

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
                    // Subirse a la patrulla pone TODAS las estrellas (5★) + marca el delito (reinicia
                    // el decaimiento). El nivel lo POSEE WantedManager (fachada combine).
                    wantedManager.setMaxWanted(nowMs)
                    _uiState.update { it.copy(
                        isDriving = true,
                        currentVehicleModel = boarded.carModel,
                        currentVehicleColor = boarded.carColor,
                        vehicleRotation = (boarded.rotationAngle + 90f) % 360f,
                        vehicleSpeed = 0.0,
                        vehicleIsFirstTimeBoarded = false,
                        isDrivingPoliceCar = true
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
        // Estaciones/fades los POSEE transitTeleportManager (manager 5/6).
        val nearbyMetro = transitTeleportManager.state.value.nearbyMetroStation
        if (nearbyMetro != null) {
            transitTeleportManager.beginMetroFade()
            return
        }

        val nearbyMetrobus = transitTeleportManager.state.value.nearbyMetrobusStation
        if (nearbyMetrobus != null) {
            transitTeleportManager.beginMetrobusFade()
            return
        }

        val nearby = collectiblesManager.state.value.nearbyCollectible ?: return

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
                // MISIÓN 2 · fase MOCHILA (🆕 2026-07-13): la puerta de la ESCOM YA NO redirige
                // al salón — se entra por el flujo normal (lobby → Edificio Principal → salón,
                // puertas en ZombieRoomCatalog). El TP del modo dev sí va directo (devTpRoute).
                // MODO HISTORIA · Misión 2 "Ingresa a la ESCOM": se cumple al ENTRAR por la puerta
                // (este es el momento de "ingresar"). Marca el objetivo cumplido + jingle.
                if (_uiState.value.currentObjective?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id
                    && !_uiState.value.objectiveDone) {
                    _uiState.update { it.copy(objectiveDone = true, interactionPrompt = "✅ Objetivo cumplido: ${_uiState.value.currentObjective?.let { getLocalizedString(it.titleRes) } ?: ""}") }
                    soundManager.playMisionCumplida()
                    // MISIÓN 1 COMPLETADA (entraste a la ESCOM): se registra en el selector.
                    markMissionCompleted(ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_1_ID)
                    // 🆕 2026-07-12: la historia SIGUE SOLA — al completar la M1 se sigue la
                    // Misión 2 automáticamente (su fase 1 "esconderse" se juega justo aquí
                    // adentro, en el lobby al que estás entrando). Antes quedaba solo DISPONIBLE
                    // y había que seguirla a mano desde Opciones → Misiones. En un REPLAY de la
                    // M1 no se encadena (el replay no debe alterar el flujo/progreso).
                    if (replayingMissionId == null) {
                        selectCampaignMission(ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_2_ID)
                    }
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
                transitTeleportManager.beginEscomDoorFade(targetRoute)
            }

            nearby.id == ShineCTOLocation.MARKER_ID -> {
                _uiState.update { it.copy(showShineCTODiscovery = true) }
            }
            nearby.id.startsWith("VENDOR_") -> {
                // Fase 2: Abrir el menú de la tienda en Compose
                _uiState.update { it.copy(showVendorMenu = true) }
            }
            nearby.id.startsWith("CAT_") -> {
                // Acariciar al gato: +10% de vida
                soundManager.playItem()
                playerHealth = (playerHealth + 10f).coerceAtMost(100f)
                showHealthBar = true
                
                _uiState.update { it.copy(interactionPrompt = "¡Miau! ❤️ (+10% Salud)") }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2000)
                    _uiState.update { if (it.interactionPrompt == "¡Miau! ❤️ (+10% Salud)") it.copy(interactionPrompt = null) else it }
                }
            }
            else -> onClaimCollectiblePressed()
        }
    }

internal fun WorldMapViewModel.onClaimCollectiblePressed() {
        val itemToClaim = collectiblesManager.state.value.nearbyCollectible ?: return

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
                collectiblesManager.claim(itemToClaim)
                _uiState.update { it.copy(interactionPrompt = null) }
                // ECONOMÍA: cada coleccionable reclamado da dinero (ver WorldMapEconomy.kt).
                addMoney(COLLECTIBLE_MONEY)
            }
        }
    }

internal fun WorldMapViewModel.dismissClaimedPopup() { collectiblesManager.dismissClaimedPopup() }

internal fun WorldMapViewModel.teleportToLocation(newLat: Double, newLon: Double) {
        val insideEscom = isInsideEscom(newLat, newLon)

        // El menú de TP lo POSEE transitTeleportManager (manager 5/6).
        transitTeleportManager.setTeleportMenu(false)
        _uiState.update { currentState ->
            currentState.copy(
                currentLocation = GeoPoint(newLat, newLon),
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
        // ETAPA 3: el estado del editor de debug vive en el DesignerManager (fachada combine).
        designerManager.toggleOverlay(show)
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

