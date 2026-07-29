package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository

/** Adaptador Hilt; la carga y el estado viven en el ViewModel común. */
@HiltViewModel
class AndroidCollectiblesViewModel @Inject constructor(
    repository: CollectibleRepository,
) : CollectiblesViewModel(repository)
