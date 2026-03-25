package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainMenuViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainMenuState())
    val state: StateFlow<MainMenuState> = _state.asStateFlow()

    // Aquí iría la lógica si necesitaras cargar datos antes de iniciar
    fun onStartGame() {
        // Por ahora, no hace mucho, pero aquí podrías inicializar
        // la base de datos local (Room) antes de cambiar de pantalla.
    }
}