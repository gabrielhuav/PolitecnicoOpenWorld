package ovh.gabrielhuav.pow.features.settings.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

// ETAPA 4 (Hilt): @HiltViewModel + @Inject; el SettingsRepository lo provee AppModule.
@dagger.hilt.android.lifecycle.HiltViewModel
class SettingsViewModel @javax.inject.Inject constructor(private val repository: SettingsRepository) : ViewModel() {

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
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun selectCategory(category: SettingsCategory) { _state.update { it.copy(selectedCategory = category) } }
    fun changeMapProvider(provider: MapProvider) { _state.update { it.copy(mapProvider = provider) } }
    fun toggleCacheWidget(enabled: Boolean) { _state.update { it.copy(showCacheWidget = enabled) } }
    fun toggleFpsWidget(enabled: Boolean) { _state.update { it.copy(showFpsWidget = enabled) } }
    fun toggleZoomWidget(enabled: Boolean) {
        _state.update { it.copy(showZoomWidget = enabled) }
        repository.saveShowZoomWidget(enabled)
    }
    fun toggleSpeedometer(enabled: Boolean) {
        _state.update { it.copy(showSpeedometer = enabled) }
        repository.saveShowSpeedometer(enabled)
    }
    fun toggleCoordsWidget(enabled: Boolean) {
        _state.update { it.copy(showCoordsWidget = enabled) }
        repository.saveShowCoordsWidget(enabled)
    }
    // Modo Desarrollador: persiste al instante. Las pantallas que tengan botones de
    // prueba deben observar state.developerMode para mostrarlos/ocultarlos.
    fun toggleDeveloperMode(enabled: Boolean) {
        _state.update { it.copy(developerMode = enabled) }
        repository.saveDeveloperMode(enabled)
    }
    // 🆕 Mostrar hitboxes del modo pelea (estilo Minecraft). Persiste al instante.
    fun toggleHitboxes(enabled: Boolean) {
        _state.update { it.copy(showHitboxes = enabled) }
        repository.saveShowHitboxes(enabled)
    }

    // Audio: persisten al instante; MainActivity los empuja en vivo al SoundManager.
    fun changeMusicVolume(v: Float) {
        val c = v.coerceIn(0f, 1f)
        _state.update { it.copy(musicVolume = c) }
        repository.saveMusicVolume(c)
    }
    fun changeSfxVolume(v: Float) {
        val c = v.coerceIn(0f, 1f)
        _state.update { it.copy(sfxVolume = c) }
        repository.saveSfxVolume(c)
    }

    // Los cambios de controles solo tocan el estado TEMPORAL: no afectan al juego
    // hasta que el usuario presiona GUARDAR (saveControlsSettings()).
    fun changeControlType(type: ControlType) { _state.update { it.copy(tempControlType = type) } }
    fun changeControlsScale(scale: Float) { _state.update { it.copy(tempControlsScale = scale) } }
    fun toggleSwapControls(swap: Boolean) { _state.update { it.copy(tempSwapControls = swap) } }

    /** Descarta los cambios temporales no guardados, volviéndolos a los committeados. */
    fun discardControlsChanges() {
        _state.update {
            it.copy(
                tempControlType = it.controlType,
                tempControlsScale = it.controlsScale,
                tempSwapControls = it.swapControls
            )
        }
    }

    fun toggleRoadNetwork(show: Boolean) {
        _state.update { it.copy(showRoadNetwork = show) }
        repository.saveShowRoadNetwork(show)
    }

    // ─── Jugabilidad: población de NPCs (persisten al instante, como la red vial) ──
    fun changeNpcDensity(v: Float) {
        val c = v.coerceIn(SettingsRepository.NPC_DENSITY_MIN, SettingsRepository.NPC_DENSITY_MAX)
        _state.update { it.copy(npcDensity = c) }
        repository.saveNpcDensity(c)
    }
    fun toggleNpcEmojiLod(enabled: Boolean) {
        _state.update { it.copy(npcEmojiLod = enabled) }
        repository.saveNpcEmojiLod(enabled)
    }
    fun toggleNpcFullEmoji(enabled: Boolean) {
        _state.update { it.copy(npcFullEmoji = enabled) }
        repository.saveNpcFullEmoji(enabled)
    }

    // i18n: persiste el idioma de la UI. La Activity debe recrearse para que tome
    // efecto (se aplica en MainActivity.attachBaseContext). Ver SettingsScreen.
    fun changeLanguage(tag: String) {
        _state.update { it.copy(language = tag) }
        repository.saveLanguage(tag)
    }

    // Función para guardar: sincroniza los temporales a los committeados y persiste.
    fun saveControlsSettings() {
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

    // ETAPA 4 (Hilt): el Factory manual se eliminó — el VM se obtiene con hiltViewModel() /
    // by viewModels() y Hilt inyecta el SettingsRepository (AppModule).
}