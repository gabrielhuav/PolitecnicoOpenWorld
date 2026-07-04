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
    if (collectiblesManager.state.value.activeCollectibles.none { it.id == ShineCTOLocation.MARKER_ID }) {
        val marker = ActiveCollectible(
            id          = ShineCTOLocation.MARKER_ID,
            name        = ShineCTOLocation.MARKER_NAME,
            description = "easter_egg",
            assetPath   = "PLACES/shine_cto/s_logo.webp",
            latitude    = ShineCTOLocation.LAT,
            longitude   = ShineCTOLocation.LON
        )
        collectiblesManager.addActive(marker)
    }
}

fun WorldMapViewModel.onShineCTODiscoveryConfirmed() {
    // El marker es persistente: NO se elimina de activeCollectibles.
    collectiblesManager.clearNearby()
    _uiState.update { s ->
        s.copy(
            showShineCTODiscovery = false,
            navigateToShineCTO   = true,
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
    collectiblesManager.clearNearby()
    // El fade/flag de la puerta ESCOM lo POSEE transitTeleportManager (manager 5/6); el
    // interactionPrompt (de OTRO grupo) se limpia aquí.
    transitTeleportManager.onEscomDoorFadeComplete()
    _uiState.update { it.copy(interactionPrompt = null) }
}

fun WorldMapViewModel.consumeEscomDoorNavigation(): String? =
    transitTeleportManager.consumeEscomDoorNavigation()
