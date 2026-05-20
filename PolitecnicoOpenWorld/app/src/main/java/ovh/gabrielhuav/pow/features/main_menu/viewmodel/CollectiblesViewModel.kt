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

class CollectiblesViewModel(
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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = PowDatabase.getInstance(context.applicationContext)
            val repository = CollectibleRepository(database.collectibleDao())
            return CollectiblesViewModel(repository) as T
        }
    }
}
