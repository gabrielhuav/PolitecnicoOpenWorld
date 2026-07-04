package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation

internal fun WorldMapViewModel.trySpawningCollectible(playerLat: Double, playerLon: Double) {
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        if (collectiblesManager.state.value.activeCollectibles.isNotEmpty() || !isSpawningCollectible.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uncollected = collectibleRepository.getUncollectedCollectibles()
                if (uncollected.isNotEmpty()) {
                    val itemToSpawn = uncollected.random()
                    val bearing = Math.random() * 2 * Math.PI
                    val distanceMeters = 300.0 + Math.random() * 300.0
                    val clampedLat = playerLat.coerceIn(-85.0, 85.0)
                    val deltaLat = (distanceMeters * Math.cos(bearing)) / 111000.0
                    val deltaLon = (distanceMeters * Math.sin(bearing)) / (111000.0 * Math.cos(Math.toRadians(clampedLat)))
                    val offsetLat = playerLat + deltaLat
                    val offsetLon = playerLon + deltaLon
                    val tempLoc = org.osmdroid.util.GeoPoint(offsetLat, offsetLon)
                    val spawnNode = getNearestPointOnNetwork(tempLoc)
                    val activeItem = ActiveCollectible(
                        id = itemToSpawn.id,
                        name = itemToSpawn.name,
                        description = itemToSpawn.description,
                        assetPath = itemToSpawn.assetPath,
                        latitude = spawnNode.latitude,
                        longitude = spawnNode.longitude
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        collectiblesManager.setActive(listOf(activeItem))
                    }
                }
            } finally {
                isSpawningCollectible.set(false)
            }
        }
    }


internal fun WorldMapViewModel.checkCollectibleProximity(playerLat: Double, playerLon: Double) {
        val playerGeo = org.osmdroid.util.GeoPoint(playerLat, playerLon)

        // 1. Verificar cercanía a estaciones del metro (el catálogo/cercanía los POSEE
        // transitTeleportManager, manager 5/6; el interactionPrompt temporizado se queda aquí).
        val metroStations = transitTeleportManager.state.value.metroStations
        val nearbyMetro = metroStations.minByOrNull {
            playerGeo.distanceToAsDouble(it.location)
        }

        // Las estaciones usan una zona MÁS GRANDE (METRO_INTERACT_RADIUS_METERS, 60 m) que
        // los coleccionables/puertas (15 m): el logo del metro a veces cae sobre un edificio
        // inaccesible por calles (Overpass) y con snap-to-road no se podría pisar; con la zona
        // amplia basta estar cerca en cualquier calle. Esta zona se dibuja en web y OSM nativo.
        if (nearbyMetro != null && playerGeo.distanceToAsDouble(nearbyMetro.location) <= METRO_INTERACT_RADIUS_METERS) {
            if (transitTeleportManager.state.value.nearbyMetroStation?.name != nearbyMetro.name) {
                transitTeleportManager.setNearbyMetro(nearbyMetro)
                collectiblesManager.clearNearby()
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_prompt_metro, nearbyMetro.name.uppercase())
                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
            return
        }

        // Si no está cerca de un metro, limpia el estado de metro
        if (transitTeleportManager.state.value.nearbyMetroStation != null) {
            transitTeleportManager.setNearbyMetro(null)
            _uiState.update { it.copy(interactionPrompt = null) }
        }

        // 1b. Verificar cercanía a estaciones del Metrobús
        val metrobusStations = transitTeleportManager.state.value.metrobusStations
        val nearbyMetrobus = metrobusStations.minByOrNull { playerGeo.distanceToAsDouble(it.location) }

        // Metrobús: zona propia MÁS PEQUEÑA que el metro (METROBUS_INTERACT_RADIUS_METERS).
        if (nearbyMetrobus != null && playerGeo.distanceToAsDouble(nearbyMetrobus.location) <= METROBUS_INTERACT_RADIUS_METERS) {
            if (transitTeleportManager.state.value.nearbyMetrobusStation?.name != nearbyMetrobus.name) {
                transitTeleportManager.setNearbyMetrobus(nearbyMetrobus)
                collectiblesManager.clearNearby()
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_prompt_metrobus, nearbyMetrobus.name.uppercase())
                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
            return
        }

        if (transitTeleportManager.state.value.nearbyMetrobusStation != null) {
            transitTeleportManager.setNearbyMetrobus(null)
            _uiState.update { it.copy(interactionPrompt = null) }
        }

        // 2. Recopilamos los collectibles normales y de ESCOM (nuestro código)
        val baseItems = collectiblesManager.state.value.activeCollectibles + _escomItems.value

        // Convertimos los Landmarks de tipo "Puerta" en collectibles virtuales interactuables
        val doorItems = _uiState.value.landmarks
            .filter { it.assetPath.contains("DOORS/") }
            .map { doorLandmark ->
                ActiveCollectible(
                    id = "escom_door_${doorLandmark.id}",
                    name = doorLandmark.name,
                    description = "Puerta interactiva",
                    assetPath = doorLandmark.assetPath,
                    latitude = doorLandmark.location.latitude,
                    longitude = doorLandmark.location.longitude
                )
            }

        // Convertimos los NPCs Vendedores y Gatos en collectibles virtuales para interactuar con ellos
        val npcItems = _uiState.value.npcs
            .filter { it.id.startsWith("VENDOR_") || it.id.startsWith("CAT_") }
            .map { npc ->
                val isCat = npc.id.startsWith("CAT_")
                ActiveCollectible(
                    id = npc.id,
                    name = npc.displayName ?: if (isCat) "Gato" else "Vendedor",
                    description = if (isCat) "Acariciar" else "Comprar objetos",
                    assetPath = "",
                    latitude = npc.location.latitude,
                    longitude = npc.location.longitude
                )
            }

        // 3. Juntamos todo en un solo radar global
        val allPossibleItems = baseItems + doorItems + npcItems

        val activeItem = allPossibleItems.minByOrNull {
            playerGeo.distanceToAsDouble(org.osmdroid.util.GeoPoint(it.latitude, it.longitude))
        } ?: return

        val itemGeo = org.osmdroid.util.GeoPoint(activeItem.latitude, activeItem.longitude)
        val distanceInMeters = playerGeo.distanceToAsDouble(itemGeo)

        // 4. Radio de detección especial para las puertas (20 metros), gatos (4 metros) o estándar para objetos (15 metros)
        val radius = when {
            activeItem.id.startsWith("escom_door_") -> ESCOM_DOOR_INTERACT_RADIUS * 100000
            activeItem.id.startsWith("CAT_") -> 4.0
            else -> 15.0
        }

        if (distanceInMeters <= radius) {
            if (collectiblesManager.state.value.nearbyCollectible?.id != activeItem.id) {
                collectiblesManager.setNearby(activeItem)
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = when {
                        activeItem.id == "global_zombie_hand" -> if (_uiState.value.globalZombieMode) getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_deactivate_zombie) else getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_activate_zombie)
                        activeItem.name == "Objeto Misterioso ESCOM" -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_interact)
                        activeItem.id == ShineCTOLocation.MARKER_ID  -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_enter)
                        activeItem.id.startsWith("escom_door_")      -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_enter) // <--- Aquí aparece el texto de la puerta
                        activeItem.id.startsWith("VENDOR_")          -> "[X] Comprar en tienda"
                        activeItem.id.startsWith("CAT_")             -> "[X] Acariciar al gato"
                        else -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_pickup)
                    }

                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
        } else {
            if (collectiblesManager.state.value.nearbyCollectible != null) {
                promptJob?.cancel()
                promptJob = null
                collectiblesManager.clearNearby()
                _uiState.update { it.copy(interactionPrompt = null) }
            }
        }
    }

