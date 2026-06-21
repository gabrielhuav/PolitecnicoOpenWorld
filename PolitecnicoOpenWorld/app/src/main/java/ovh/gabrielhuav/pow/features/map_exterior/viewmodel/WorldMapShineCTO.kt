package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ───────────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: EASTER EGG ShineCTO + transición de PUERTA ESCOM.
// Extraído de WorldMapViewModel.kt en el refactor de tamaño. Solo manipulan WorldMapState
// (marcador del easter egg, navegación y fades de puerta). NO duplicar como miembros.
// ───────────────────────────────────────────────────────────────────────────────────

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation

fun WorldMapViewModel.spawnShineCTOMarker() {
    if (_uiState.value.activeCollectibles.none { it.id == ShineCTOLocation.MARKER_ID }) {
        val marker = ActiveCollectible(
            id          = ShineCTOLocation.MARKER_ID,
            name        = ShineCTOLocation.MARKER_NAME,
            description = "easter_egg",
            assetPath   = "PLACES/shine_cto/s_logo.webp",
            latitude    = ShineCTOLocation.LAT,
            longitude   = ShineCTOLocation.LON
        )
        _uiState.update { it.copy(activeCollectibles = it.activeCollectibles + marker) }
    }
}

fun WorldMapViewModel.onShineCTODiscoveryConfirmed() {
    // El marker es persistente: NO se elimina de activeCollectibles.
    _uiState.update { s ->
        s.copy(
            showShineCTODiscovery = false,
            navigateToShineCTO   = true,
            nearbyCollectible    = null,
            interactionPrompt    = null
        )
    }
}

fun WorldMapViewModel.consumeNavigateToShineCTO() {
    _uiState.update { it.copy(navigateToShineCTO = false) }
}

fun WorldMapViewModel.dismissShineCTODiscovery() {
    _uiState.update { it.copy(showShineCTODiscovery = false) }
}
fun WorldMapViewModel.onEscomDoorFadeComplete() {
    _uiState.update {
        it.copy(
            showEscomDoorFade    = false,
            escomDoorFadeComplete = true,
            nearbyCollectible    = null,
            interactionPrompt    = null
        )
    }
}

fun WorldMapViewModel.consumeEscomDoorNavigation(): String? {
    val dest = _uiState.value.pendingDoorDestination
    _uiState.update { it.copy(escomDoorFadeComplete = false, pendingDoorDestination = null) }
    return dest
}
