package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider

class MainMenuViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainMenuState())
    val state: StateFlow<MainMenuState> = _state.asStateFlow()

    fun onStartGame() { }

    fun setMapProvider(provider: MapProvider) {
        _state.update { it.copy(selectedProvider = provider) }
    }

    // Funciones para sincronizar widgets
    fun toggleCacheWidget(show: Boolean) = _state.update { it.copy(showCacheWidget = show) }
    fun toggleFpsWidget(show: Boolean) = _state.update { it.copy(showFpsWidget = show) }
}