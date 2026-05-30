package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.MapWay

// ─── COLECCIONABLES ───────────────────────────────────────────────────────────

internal fun WorldMapViewModel.trySpawningCollectible(playerLat: Double, playerLon: Double) {
    if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
    if (_uiState.value.activeCollectibles.isNotEmpty() || !isSpawningCollectible.compareAndSet(false, true)) return

    viewModelScope.launch(Dispatchers.IO) {
        try {
            val uncollected = collectibleRepository.getUncollectedCollectibles()
            if (uncollected.isNotEmpty()) {
                val itemToSpawn     = uncollected.random()
                val bearing         = Math.random() * 2 * Math.PI
                val distanceMeters  = 300.0 + Math.random() * 300.0
                val clampedLat      = playerLat.coerceIn(-85.0, 85.0)
                val deltaLat        = (distanceMeters * Math.cos(bearing)) / 111000.0
                val deltaLon        = (distanceMeters * Math.sin(bearing)) / (111000.0 * Math.cos(Math.toRadians(clampedLat)))
                val tempLoc         = GeoPoint(playerLat + deltaLat, playerLon + deltaLon)
                val spawnNode       = getNearestPointOnNetwork(tempLoc)
                val activeItem = ActiveCollectible(
                    id          = itemToSpawn.id,
                    name        = itemToSpawn.name,
                    description = itemToSpawn.description,
                    assetPath   = itemToSpawn.assetPath,
                    latitude    = spawnNode.latitude,
                    longitude   = spawnNode.longitude
                )
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(activeCollectibles = listOf(activeItem)) }
                }
            }
        } finally {
            isSpawningCollectible.set(false)
        }
    }
}

internal fun WorldMapViewModel.checkCollectibleProximity(playerLat: Double, playerLon: Double) {
    val allPossibleItems = _uiState.value.activeCollectibles + _escomItems.value
    val playerGeo  = GeoPoint(playerLat, playerLon)
    val activeItem = allPossibleItems.minByOrNull {
        playerGeo.distanceToAsDouble(GeoPoint(it.latitude, it.longitude))
    } ?: return

    val itemGeo              = GeoPoint(activeItem.latitude, activeItem.longitude)
    val distanceInMeters     = playerGeo.distanceToAsDouble(itemGeo)
    val INTERACT_RADIUS_METERS = 15.0

    if (distanceInMeters <= INTERACT_RADIUS_METERS) {
        if (_uiState.value.nearbyCollectible?.id != activeItem.id) {
            _uiState.update { it.copy(nearbyCollectible = activeItem) }
            promptJob?.cancel()
            promptJob = viewModelScope.launch {
                val promptText = if (activeItem.name == "Objeto Misterioso ESCOM") {
                    "PRESIONA X PARA INTERACTUAR"
                } else {
                    "PRESIONA X PARA RECOGER"
                }
                _uiState.update { it.copy(interactionPrompt = promptText) }
                delay(3000)
                _uiState.update { it.copy(interactionPrompt = null) }
            }
        }
    } else {
        if (_uiState.value.nearbyCollectible != null) {
            promptJob?.cancel()
            promptJob = null
            _uiState.update { it.copy(nearbyCollectible = null, interactionPrompt = null) }
        }
    }
}

fun WorldMapViewModel.onClaimCollectiblePressed() {
    val itemToClaim = _uiState.value.nearbyCollectible ?: return
    if (itemToClaim.name == "Objeto Misterioso ESCOM") return

    viewModelScope.launch(Dispatchers.IO) {
        collectibleRepository.claimCollectible(itemToClaim.id)
        withContext(Dispatchers.Main) {
            promptJob?.cancel()
            promptJob = null
            _uiState.update {
                it.copy(
                    activeCollectibles  = emptyList(),
                    nearbyCollectible   = null,
                    interactionPrompt   = null,
                    showClaimedPopupFor = itemToClaim
                )
            }
        }
    }
}

fun WorldMapViewModel.dismissClaimedPopup() {
    _uiState.update { it.copy(showClaimedPopupFor = null) }
}

// ─── MANO ZOMBI (ESCOM) ───────────────────────────────────────────────────────

fun WorldMapViewModel.spawnEscomItems(roadNetwork: List<MapWay>, cantidad: Int = 1) {
    val center = _uiState.value.currentLocation ?: return

    if (!isInsideEscom(center.latitude, center.longitude)) {
        _escomItems.value = emptyList()
        _uiState.update { it.copy(isZombieHandSpawned = false) }
        return
    }

    if (_uiState.value.isZombieHandSpawned && _escomItems.value.isNotEmpty()) return

    val spawnPoint = if (roadNetwork.isNotEmpty()) getNearestPointOnNetwork(center) else center

    val hand = ActiveCollectible(
        id          = "escom_hand_lobby",
        name        = "Objeto Misterioso ESCOM",
        description = "INTERIOR_TARGET:lobby",
        assetPath   = "ZOMBIS_MOD/zombi_hand.webp",
        latitude    = spawnPoint.latitude,
        longitude   = spawnPoint.longitude
    )
    _escomItems.value = listOf(hand)
    _uiState.update { it.copy(isZombieHandSpawned = true) }
}

fun WorldMapViewModel.collectEscomItem() {
    val loc             = _uiState.value.currentLocation ?: return
    val interactionRadius = 0.00015
    val itemToCollect   = _escomItems.value.find {
        distance(loc, GeoPoint(it.latitude, it.longitude)) <= interactionRadius
    }
    if (itemToCollect != null) {
        _escomItems.update { currentList -> currentList.filter { it.id != itemToCollect.id } }
    }
}

fun WorldMapViewModel.handleInteraction() {
    val nearby = _uiState.value.nearbyCollectible ?: return
    if (nearby.name == "Objeto Misterioso ESCOM") {
        pendingZombieMinigame = true
        _uiState.update {
            it.copy(
                showZombiVideo              = true,
                pendingInteriorDestination  = InteriorBuilding.EDIFICIO
            )
        }
    } else {
        onClaimCollectiblePressed()
    }
}

fun WorldMapViewModel.dismissVideo() {
    _uiState.update { it.copy(showZombiVideo = false) }
}

fun WorldMapViewModel.clearPendingInteriorDestination() {
    _uiState.update { it.copy(pendingInteriorDestination = null) }
}

fun WorldMapViewModel.clearPendingZombieMinigame() {
    pendingZombieMinigame = false
}
