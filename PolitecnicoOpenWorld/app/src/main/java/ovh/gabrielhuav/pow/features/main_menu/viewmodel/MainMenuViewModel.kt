package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider


class MainMenuViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainMenuState())
    val state: StateFlow<MainMenuState> = _state.asStateFlow()

    fun onStartGame() { /* futuro: inicializar Room antes de navegar */ }

    fun setMapProvider(provider: MapProvider) {
        _state.value = _state.value.copy(selectedProvider = provider)
    }
}