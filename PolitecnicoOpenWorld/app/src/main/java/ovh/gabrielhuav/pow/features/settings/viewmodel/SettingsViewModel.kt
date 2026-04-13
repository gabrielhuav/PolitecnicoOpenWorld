package ovh.gabrielhuav.pow.features.settings.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

class SettingsViewModel : ViewModel() {
    // Estado inicial de los ajustes
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun selectCategory(category: SettingsCategory) {
        _state.value = _state.value.copy(selectedCategory = category)
    }

    fun changeMapProvider(provider: MapProvider) {
        _state.value = _state.value.copy(mapProvider = provider)
    }

    fun toggleCacheWidget(enabled: Boolean) {
        _state.value = _state.value.copy(showCacheWidget = enabled)
    }

    fun toggleFpsWidget(enabled: Boolean) {
        _state.value = _state.value.copy(showFpsWidget = enabled)
    }
}