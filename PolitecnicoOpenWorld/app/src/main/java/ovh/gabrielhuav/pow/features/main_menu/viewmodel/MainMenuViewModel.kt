package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.data.network.ServerWarmupManager
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider

class MainMenuViewModel : ViewModel() {

    private val _state = MutableStateFlow(MainMenuState())
    val state: StateFlow<MainMenuState> = _state.asStateFlow()

    private var warmupJob: Job? = null

    fun onStartGame() { }

    fun setMapProvider(provider: MapProvider) {
        _state.update { it.copy(selectedProvider = provider) }
    }

    // Funciones para sincronizar widgets
    fun toggleCacheWidget(show: Boolean) = _state.update { it.copy(showCacheWidget = show) }
    fun toggleFpsWidget(show: Boolean) = _state.update { it.copy(showFpsWidget = show) }

    // Funciones para el diálogo de nombre
    fun updateShowMultiplayerDialog(show: Boolean) = _state.update { it.copy(showMultiplayerDialog = show) }
    fun updatePlayerName(name: String) = _state.update { it.copy(playerName = name) }

    // ─── Warm-up del servidor multijugador (Render free tier) ─────────────────
    //
    // El usuario tocó "MULTIJUGADOR". Antes de mostrar el diálogo de nombre,
    // hacemos polling al /status del open-world server para que Render lo
    // despierte. Mostramos un spinner bloqueante mientras tanto; el usuario
    // puede cancelar con el botón "CANCELAR".
    //
    // Solo cuando el server responde 200 OK abrimos el diálogo de nombre.
    // Si timeout o falla, ofrecemos reintentar.

    fun onMultiplayerPressed() {
        // Si ya hay un warmup en curso, no relances otro.
        if (_state.value.isWarmingUp) return

        _state.update {
            it.copy(
                isWarmingUp = true,
                warmupSeconds = 0,
                warmupFailed = false
            )
        }

        warmupJob = viewModelScope.launch {
            val result = ServerWarmupManager.warmup(
                wsUrl = BuildConfig.MULTIPLAYER_SERVER_URL,
                onProgress = { seconds ->
                    _state.update { it.copy(warmupSeconds = seconds) }
                }
            )

            when (result) {
                is ServerWarmupManager.WarmupResult.Ready -> {
                    // Server caliente: cierra el spinner y abre el diálogo de nombre.
                    _state.update {
                        it.copy(
                            isWarmingUp = false,
                            warmupSeconds = 0,
                            warmupFailed = false,
                            showMultiplayerDialog = true
                        )
                    }
                }
                is ServerWarmupManager.WarmupResult.Timeout -> {
                    // Pasaron ~60 s sin respuesta: deja el spinner cerrado y
                    // marca el fallo para que la UI ofrezca reintentar.
                    _state.update {
                        it.copy(
                            isWarmingUp = false,
                            warmupSeconds = 0,
                            warmupFailed = true
                        )
                    }
                }
                is ServerWarmupManager.WarmupResult.Cancelled -> {
                    // El usuario tocó "CANCELAR": vuelve al menú sin ruido.
                    _state.update {
                        it.copy(
                            isWarmingUp = false,
                            warmupSeconds = 0,
                            warmupFailed = false
                        )
                    }
                }
            }
        }
    }

    /** Cancela un warmup en curso (botón "CANCELAR" del spinner). */
    fun cancelWarmup() {
        warmupJob?.cancel()
        warmupJob = null
        _state.update {
            it.copy(
                isWarmingUp = false,
                warmupSeconds = 0,
                warmupFailed = false
            )
        }
    }

    /** Cierra el banner de error después de un timeout. */
    fun dismissWarmupError() {
        _state.update { it.copy(warmupFailed = false) }
    }

    override fun onCleared() {
        super.onCleared()
        warmupJob?.cancel()
    }
}
