package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.features.main_menu.ui.CollectiblesController
import ovh.gabrielhuav.pow.presentation.PowViewModel

open class CollectiblesViewModel(
    private val collectibleRepository: CollectibleRepository,
) : PowViewModel(), CollectiblesController {

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
