package ovh.gabrielhuav.pow.features.main_menu.ui

import kotlinx.coroutines.flow.StateFlow
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity

/** Datos que necesita la pantalla común de Coleccionables. */
interface CollectiblesController {
    val collectiblesList: StateFlow<List<CollectibleEntity>>
}
