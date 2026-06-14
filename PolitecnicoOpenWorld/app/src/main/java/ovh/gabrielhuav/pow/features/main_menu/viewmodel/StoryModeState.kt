package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import ovh.gabrielhuav.pow.domain.models.SchoolCatalog

// Estado del Modo Historia / Campaña. Solo guarda la escuela elegida; el prólogo
// y la lista de escuelas son estáticos (ver SchoolCatalog). Inmutable: se actualiza
// con _state.update { it.copy(...) }.
data class StoryModeState(
    // Id de la escuela seleccionada (default = primera disponible, ESCOM).
    val selectedSchoolId: String = SchoolCatalog.default.id
)
