package ovh.gabrielhuav.pow.features.settings.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import ovh.gabrielhuav.pow.data.repository.SettingsRepository

/** Adaptador Hilt; toda la lógica y el estado viven en el ViewModel común. */
@HiltViewModel
class AndroidSettingsViewModel @Inject constructor(
    repository: SettingsRepository,
) : SettingsViewModel(repository)
