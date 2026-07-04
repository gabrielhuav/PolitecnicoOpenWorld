package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity

// ETAPA 4 (Hilt): @HiltViewModel + @Inject; el CollectibleRepository lo provee AppModule.
@dagger.hilt.android.lifecycle.HiltViewModel
class CollectiblesViewModel @javax.inject.Inject constructor(
    private val collectibleRepository: CollectibleRepository
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
    }

    // Convertimos el Flow de Room en un StateFlow para Compose
    val collectiblesList: StateFlow<List<CollectibleEntity>> =
        collectibleRepository.allCollectiblesFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // ETAPA 4 (Hilt): Factory manual eliminado → hiltViewModel()/by viewModels() + inyección.
}
