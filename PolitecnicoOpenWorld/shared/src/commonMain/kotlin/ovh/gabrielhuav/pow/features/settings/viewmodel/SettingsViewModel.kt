package ovh.gabrielhuav.pow.features.settings.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory
import ovh.gabrielhuav.pow.features.settings.ui.SettingsController
import ovh.gabrielhuav.pow.presentation.PowViewModel

/** Lógica de Ajustes común. Android la envuelve con Hilt; iOS la construye con NSUserDefaults. */
open class SettingsViewModel(
    private val repository: SettingsRepository,
) : PowViewModel(), SettingsController {

    // Inicializa el estado leyendo la base de datos de preferencias
    private val _state = MutableStateFlow(
        run {
            val type = repository.getControlType()
            val scale = repository.getControlsScale()
            val swap = repository.getSwapControls()
            SettingsState(
                controlType = type,
                controlsScale = scale,
                swapControls = swap,
                showRoadNetwork = repository.getShowRoadNetwork(),
                showZoomWidget = repository.getShowZoomWidget(),
                showSpeedometer = repository.getShowSpeedometer(),
                showCoordsWidget = repository.getShowCoordsWidget(),
                developerMode = repository.getDeveloperMode(),
                showHitboxes = repository.getShowHitboxes(),
                showSfFps = repository.getShowSfFps(),
                showVoiceSubtitles = repository.getShowVoiceSubtitles(),
                showWorldShoulderButtons = repository.getShowWorldShoulderButtons(),
                musicVolume = repository.getMusicVolume(),
                sfxVolume = repository.getSfxVolume(),
                npcDensity = repository.getNpcDensity(),
                npcEmojiLod = repository.getNpcEmojiLod(),
                npcFullEmoji = repository.getNpcFullEmoji(),
                language = repository.getLanguage(),
                // Los temporales arrancan sincronizados con lo persistido.
                tempControlType = type,
                tempControlsScale = scale,
                tempSwapControls = swap
            )
        }
    )
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    override fun selectCategory(category: SettingsCategory) { _state.update { it.copy(selectedCategory = category) } }
    override fun changeMapProvider(provider: MapProvider) { _state.update { it.copy(mapProvider = provider) } }
    override fun toggleCacheWidget(enabled: Boolean) { _state.update { it.copy(showCacheWidget = enabled) } }
    override fun toggleFpsWidget(enabled: Boolean) { _state.update { it.copy(showFpsWidget = enabled) } }
    override fun toggleZoomWidget(enabled: Boolean) {
        _state.update { it.copy(showZoomWidget = enabled) }
        repository.saveShowZoomWidget(enabled)
    }
    override fun toggleSpeedometer(enabled: Boolean) {
        _state.update { it.copy(showSpeedometer = enabled) }
        repository.saveShowSpeedometer(enabled)
    }
    override fun toggleCoordsWidget(enabled: Boolean) {
        _state.update { it.copy(showCoordsWidget = enabled) }
        repository.saveShowCoordsWidget(enabled)
    }
    // Modo Desarrollador: persiste al instante. Las pantallas que tengan botones de
    // prueba deben observar state.developerMode para mostrarlos/ocultarlos.
    override fun toggleDeveloperMode(enabled: Boolean) {
        _state.update { it.copy(developerMode = enabled) }
        repository.saveDeveloperMode(enabled)
    }
    // 🆕 Mostrar hitboxes del modo pelea (estilo Minecraft). Persiste al instante.
    override fun toggleHitboxes(enabled: Boolean) {
        _state.update { it.copy(showHitboxes = enabled) }
        repository.saveShowHitboxes(enabled)
    }
    // 🆕 (2026-07-25) Mostrar FPS del modo pelea. Persiste al instante.
    override fun toggleSfFps(enabled: Boolean) {
        _state.update { it.copy(showSfFps = enabled) }
        repository.saveShowSfFps(enabled)
    }
    // 🆕 (2026-07-25) Subtítulos de las voces de los peleadores. Persiste al instante.
    override fun toggleVoiceSubtitles(enabled: Boolean) {
        _state.update { it.copy(showVoiceSubtitles = enabled) }
        repository.saveShowVoiceSubtitles(enabled)
    }
    // 🆕 (2026-07-26) Gatillos L1/L2/R1/R2 en el mundo abierto. Persiste al instante.
    override fun toggleWorldShoulderButtons(enabled: Boolean) {
        _state.update { it.copy(showWorldShoulderButtons = enabled) }
        repository.saveShowWorldShoulderButtons(enabled)
    }

    // Audio: persisten al instante; MainActivity los empuja en vivo al SoundManager.
    override fun changeMusicVolume(value: Float) {
        val c = value.coerceIn(0f, 1f)
        _state.update { it.copy(musicVolume = c) }
        repository.saveMusicVolume(c)
    }
    override fun changeSfxVolume(value: Float) {
        val c = value.coerceIn(0f, 1f)
        _state.update { it.copy(sfxVolume = c) }
        repository.saveSfxVolume(c)
    }

    // Los cambios de controles solo tocan el estado TEMPORAL: no afectan al juego
    // hasta que el usuario presiona GUARDAR (saveControlsSettings()).
    override fun changeControlType(type: ControlType) { _state.update { it.copy(tempControlType = type) } }
    override fun changeControlsScale(scale: Float) { _state.update { it.copy(tempControlsScale = scale) } }
    override fun toggleSwapControls(enabled: Boolean) { _state.update { it.copy(tempSwapControls = enabled) } }

    /** Descarta los cambios temporales no guardados, volviéndolos a los committeados. */
    override fun discardControlsChanges() {
        _state.update {
            it.copy(
                tempControlType = it.controlType,
                tempControlsScale = it.controlsScale,
                tempSwapControls = it.swapControls
            )
        }
    }

    override fun toggleRoadNetwork(enabled: Boolean) {
        _state.update { it.copy(showRoadNetwork = enabled) }
        repository.saveShowRoadNetwork(enabled)
    }

    // ─── Jugabilidad: población de NPCs (persisten al instante, como la red vial) ──
    override fun changeNpcDensity(value: Float) {
        val c = value.coerceIn(SettingsRepository.NPC_DENSITY_MIN, SettingsRepository.NPC_DENSITY_MAX)
        _state.update { it.copy(npcDensity = c) }
        repository.saveNpcDensity(c)
    }
    override fun toggleNpcEmojiLod(enabled: Boolean) {
        _state.update { it.copy(npcEmojiLod = enabled) }
        repository.saveNpcEmojiLod(enabled)
    }
    override fun toggleNpcFullEmoji(enabled: Boolean) {
        _state.update { it.copy(npcFullEmoji = enabled) }
        repository.saveNpcFullEmoji(enabled)
    }

    // i18n: persiste el idioma de la UI. La Activity debe recrearse para que tome
    // efecto (se aplica en MainActivity.attachBaseContext). Ver SettingsScreen.
    override fun changeLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        repository.saveLanguage(tag)
    }

    // Función para guardar: sincroniza los temporales a los committeados y persiste.
    override fun saveControlsSettings() {
        _state.update {
            it.copy(
                controlType = it.tempControlType,
                controlsScale = it.tempControlsScale,
                swapControls = it.tempSwapControls
            )
        }
        val currentState = _state.value
        repository.saveControlsSettings(
            type = currentState.controlType,
            scale = currentState.controlsScale,
            swap = currentState.swapControls
        )
    }

}
