package ovh.gabrielhuav.pow.features.main_menu.ui

import kotlinx.coroutines.flow.StateFlow
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.features.main_menu.gallery.GalleryState

/** Datos que necesita la pantalla común de Coleccionables. */
interface CollectiblesController {
    val collectiblesList: StateFlow<List<CollectibleEntity>>
    val gallery: StateFlow<GalleryState>
    fun loadGallery()
}
