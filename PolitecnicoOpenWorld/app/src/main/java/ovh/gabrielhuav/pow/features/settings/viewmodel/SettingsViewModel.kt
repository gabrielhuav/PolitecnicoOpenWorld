package ovh.gabrielhuav.pow.features.settings.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

class SettingsViewModel : ViewModel() {
    // Estado inicial de los ajustes
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun selectCategory(category: SettingsCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun changeMapProvider(provider: MapProvider) {
        _state.update { it.copy(mapProvider = provider) }
    }

    fun toggleCacheWidget(enabled: Boolean) {
        _state.update { it.copy(showCacheWidget = enabled) }
    }

    fun toggleFpsWidget(enabled: Boolean) {
        _state.update { it.copy(showFpsWidget = enabled) }
    }

    // Dentro de la clase SettingsViewModel añade:
    fun changeControlType(type: ControlType) {
        _state.update { it.copy(controlType = type) }
    }
    fun changeControlsScale(scale: Float) {
        _state.update { it.copy(controlsScale = scale) }
    }
    fun toggleSwapControls(swap: Boolean) {
        _state.update { it.copy(swapControls = swap) }
    }
}