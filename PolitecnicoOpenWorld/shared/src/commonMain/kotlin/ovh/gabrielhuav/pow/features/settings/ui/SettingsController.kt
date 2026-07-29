package ovh.gabrielhuav.pow.features.settings.ui

import kotlinx.coroutines.flow.StateFlow
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState

/**
 * Contrato de la pantalla de Ajustes (patrón Controller de §2bis).
 *
 * La UI común expresa intenciones; Android y iOS deciden cómo construir el ViewModel y cómo
 * ejecutar efectos de plataforma como recrear la Activity, aplicar audio o borrar una cuenta.
 */
interface SettingsController {
    val state: StateFlow<SettingsState>

    fun selectCategory(category: SettingsCategory)
    fun changeMapProvider(provider: MapProvider)
    fun toggleCacheWidget(enabled: Boolean)
    fun toggleFpsWidget(enabled: Boolean)
    fun toggleZoomWidget(enabled: Boolean)
    fun toggleSpeedometer(enabled: Boolean)
    fun toggleCoordsWidget(enabled: Boolean)
    fun toggleDeveloperMode(enabled: Boolean)
    fun toggleHitboxes(enabled: Boolean)
    fun toggleSfFps(enabled: Boolean)
    fun toggleVoiceSubtitles(enabled: Boolean)
    fun toggleWorldShoulderButtons(enabled: Boolean)
    fun changeMusicVolume(value: Float)
    fun changeSfxVolume(value: Float)
    fun changeControlType(type: ControlType)
    fun changeControlsScale(scale: Float)
    fun toggleSwapControls(enabled: Boolean)
    fun toggleRoadNetwork(enabled: Boolean)
    fun changeNpcDensity(value: Float)
    fun toggleNpcEmojiLod(enabled: Boolean)
    fun toggleNpcFullEmoji(enabled: Boolean)
    fun changeLanguage(tag: String)
    fun saveControlsSettings()
    fun discardControlsChanges()
}
