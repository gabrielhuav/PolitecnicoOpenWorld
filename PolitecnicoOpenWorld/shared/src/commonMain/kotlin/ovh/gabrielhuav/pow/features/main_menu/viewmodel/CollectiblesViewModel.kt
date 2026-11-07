package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.features.main_menu.ui.CollectiblesController
import ovh.gabrielhuav.pow.presentation.PowViewModel
import ovh.gabrielhuav.pow.features.main_menu.gallery.GalleryCatalog
import ovh.gabrielhuav.pow.features.main_menu.gallery.GalleryState
import ovh.gabrielhuav.pow.platform.assets.PowAssets

open class CollectiblesViewModel(
    private val collectibleRepository: CollectibleRepository,
) : PowViewModel(), CollectiblesController {
    private val galleryState = MutableStateFlow(GalleryState())
    override val gallery = galleryState.asStateFlow()
    private var galleryJob: Job? = null

    override fun loadGallery() {
        if (galleryJob?.isActive == true || galleryState.value.items.isNotEmpty()) return
        galleryJob = scope.launch {
            galleryState.value = GalleryState()
            try {
                val items = withContext(Dispatchers.Default) { GalleryCatalog.load(PowAssets) }
                galleryState.value = GalleryState(items = items, loading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                galleryState.value = GalleryState(loading = false, failed = true)
            }
        }
    }

    init {
        scope.launch {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
    }

    override val collectiblesList: StateFlow<List<CollectibleEntity>> =
        collectibleRepository.allCollectiblesFlow
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
}
