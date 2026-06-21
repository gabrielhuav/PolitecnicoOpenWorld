package ovh.gabrielhuav.pow.features.campaign.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignSchool
import ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog

// ViewModel del Modo Historia. Alcance: NavBackStackEntry (se reinicia al salir de
// la pantalla, así que re-lee la partida guardada cada vez que se entra). Orquesta la
// selección de escuela y la lectura de la partida guardada (CampaignRepository). La
// escritura del guardado y el spawn viven en MainActivity al COMENZAR/INICIAR.
class StoryModeViewModel(
    private val campaignRepository: CampaignRepository,
    private val saveGameRepository: SaveGameRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StoryModeState())
    val state: StateFlow<StoryModeState> = _state.asStateFlow()

    init {
        // "CARGAR PARTIDA" se habilita si HAY alguna partida en cualquier slot (JSON).
        _state.update {
            it.copy(
                hasSave = saveGameRepository.anySave(),
                savedSchoolId = campaignRepository.getSavedSchoolId(),
                savedAt = campaignRepository.getSavedAt()
            )
        }
    }

    // Selecciona una escuela. Las no disponibles (en desarrollo) se ignoran:
    // la View ya las dibuja deshabilitadas, esto es una segunda barrera.
    fun selectSchool(id: String) {
        val school = SchoolCatalog.schools.firstOrNull { it.id == id } ?: return
        if (!school.available) return
        _state.update { it.copy(selectedSchoolId = id) }
    }

    // Escuela actualmente seleccionada (para arrancar una campaña nueva).
    fun selectedSchool(): CampaignSchool =
        SchoolCatalog.schools.firstOrNull { it.id == _state.value.selectedSchoolId }
            ?: SchoolCatalog.default

    // Escuela de la partida guardada (para "CARGAR PARTIDA"), o null si no hay.
    fun savedSchool(): CampaignSchool? =
        _state.value.savedSchoolId?.let { id -> SchoolCatalog.schools.firstOrNull { it.id == id } }

    // DI manual co-localizada (ver convención del archivo 01).
    class Factory(context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StoryModeViewModel(CampaignRepository(appContext), SaveGameRepository(appContext)) as T
        }
    }
}
