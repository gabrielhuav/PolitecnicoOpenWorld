package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.CampaignSchool
import ovh.gabrielhuav.pow.domain.models.SchoolCatalog

// ViewModel del Modo Historia. Alcance: NavBackStackEntry (se reinicia al salir de
// la pantalla). Solo orquesta la selección de escuela; la lógica de spawn vive en
// el WorldMapViewModel (Activity-scoped) cuando se confirma "COMENZAR".
class StoryModeViewModel : ViewModel() {

    private val _state = MutableStateFlow(StoryModeState())
    val state: StateFlow<StoryModeState> = _state.asStateFlow()

    // Selecciona una escuela. Las no disponibles (en desarrollo) se ignoran:
    // la View ya las dibuja deshabilitadas, esto es una segunda barrera.
    fun selectSchool(id: String) {
        val school = SchoolCatalog.schools.firstOrNull { it.id == id } ?: return
        if (!school.available) return
        _state.update { it.copy(selectedSchoolId = id) }
    }

    // Escuela actualmente seleccionada (para que la View arranque la campaña).
    fun selectedSchool(): CampaignSchool =
        SchoolCatalog.schools.firstOrNull { it.id == _state.value.selectedSchoolId }
            ?: SchoolCatalog.default
}
