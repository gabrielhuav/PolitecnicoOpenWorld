package ovh.gabrielhuav.pow.features.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    // Inicializa el estado leyendo la base de datos de preferencias
    private val _state = MutableStateFlow(
        SettingsState(
            controlType = repository.getControlType(),
            controlsScale = repository.getControlsScale(),
            swapControls = repository.getSwapControls()
        )
    )
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    fun selectCategory(category: SettingsCategory) { _state.update { it.copy(selectedCategory = category) } }
    fun changeMapProvider(provider: MapProvider) { _state.update { it.copy(mapProvider = provider) } }
    fun toggleCacheWidget(enabled: Boolean) { _state.update { it.copy(showCacheWidget = enabled) } }
    fun toggleFpsWidget(enabled: Boolean) { _state.update { it.copy(showFpsWidget = enabled) } }

    fun changeControlType(type: ControlType) { _state.update { it.copy(controlType = type) } }
    fun changeControlsScale(scale: Float) { _state.update { it.copy(controlsScale = scale) } }
    fun toggleSwapControls(swap: Boolean) { _state.update { it.copy(swapControls = swap) } }

    // Función para guardar
    fun saveControlsSettings() {
        val currentState = _state.value
        repository.saveControlsSettings(
            type = currentState.controlType,
            scale = currentState.controlsScale,
            swap = currentState.swapControls
        )
    }

    // Factory para inyectar el contexto
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            return SettingsViewModel(SettingsRepository(context.applicationContext)) as T
        }
    }
}