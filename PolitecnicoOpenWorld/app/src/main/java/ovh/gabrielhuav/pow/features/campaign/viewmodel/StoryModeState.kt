package ovh.gabrielhuav.pow.features.campaign.viewmodel

import ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog

// Estado del Modo Historia / Campaña. Guarda la escuela elegida y si existe una
// partida guardada (para habilitar "CARGAR PARTIDA"). Inmutable: se actualiza con
// _state.update { it.copy(...) }.
data class StoryModeState(
    // Id de la escuela seleccionada (default = primera disponible, ESCOM).
    val selectedSchoolId: String = SchoolCatalog.default.id,
    // ¿Hay una partida guardada? Lo lee el ViewModel desde CampaignRepository.
    val hasSave: Boolean = false,
    // Id de la escuela de la partida guardada (null si no hay).
    val savedSchoolId: String? = null,
    // Marca de tiempo del guardado (epoch ms; 0 si no hay).
    val savedAt: Long = 0L
)
