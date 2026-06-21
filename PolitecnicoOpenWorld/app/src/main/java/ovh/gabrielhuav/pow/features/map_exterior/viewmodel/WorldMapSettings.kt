package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

/**
 * Parcial de AJUSTES de jugabilidad + SKIN aplicados a [WorldMapViewModel] (extraído para reducir
 * el tamaño del VM; mismo paquete `viewmodel`). Densidad/LOD de NPCs y selección/refresco de skin.
 * Extensiones que solo tocan miembros `internal` del VM (`npcAiManager`, `settingsRepository`,
 * `_uiState`). Sin gemelo miembro. Call-sites fuera del paquete (MainActivity, WorldMapScreen)
 * importan estas extensiones. `setShowRoadNetwork` se quedó en el VM (llama a `updateVisibleRoads`,
 * que es `private`). MVVM intacto.
 */

fun WorldMapViewModel.setNpcDensity(v: Float) { npcAiManager.userPopulationFactor = v }

/** NPCs lejanos como emoji (optimización gama baja). */
fun WorldMapViewModel.setNpcEmojiLod(enabled: Boolean) { _uiState.update { it.copy(npcEmojiLod = enabled) } }

fun WorldMapViewModel.setNpcFullEmoji(enabled: Boolean) { _uiState.update { it.copy(npcFullEmoji = enabled) } }

fun WorldMapViewModel.toggleSkinSelector(show: Boolean) {
    _uiState.update { it.copy(showSkinSelector = show) }
}

fun WorldMapViewModel.selectSkin(skin: PlayerSkin) {
    settingsRepository.savePlayerSkin(skin)
    _uiState.update { it.copy(selectedSkin = skin, showSkinSelector = false) }
}

fun WorldMapViewModel.refreshSkin() {
    _uiState.update { it.copy(selectedSkin = settingsRepository.getPlayerSkin()) }
}
