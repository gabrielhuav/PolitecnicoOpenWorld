package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            _uiState.value = _uiState.value.copy(
                currentLocation = GeoPoint(latitude, longitude),
                isLoadingLocation = false
            )
        }
    }

    fun moveCharacter(direction: Direction) {
        val current = _uiState.value.currentLocation ?: return
        val step = 0.000015

        val newLocation = when (direction) {
            Direction.UP -> GeoPoint(current.latitude + step, current.longitude)
            Direction.DOWN -> GeoPoint(current.latitude - step, current.longitude)
            Direction.LEFT -> GeoPoint(current.latitude, current.longitude - step)
            Direction.RIGHT -> GeoPoint(current.latitude, current.longitude + step)
        }

        _uiState.value = _uiState.value.copy(currentLocation = newLocation)
    }

    fun executeAction(action: GameAction) {
        println("Acción ejecutada: $action")
    }

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettingsDialog = show)
    }

    fun setMapProvider(provider: MapProvider) {
        _uiState.value = _uiState.value.copy(
            mapProvider = provider
        )
    }

    fun zoomIn() {
        val currentZoom = _uiState.value.zoomLevel
        if (currentZoom < 22.0) {
            _uiState.value = _uiState.value.copy(zoomLevel = currentZoom + 1.0)
        }
    }

    fun zoomOut() {
        val currentZoom = _uiState.value.zoomLevel
        if (currentZoom > 2.0) {
            _uiState.value = _uiState.value.copy(zoomLevel = currentZoom - 1.0)
        }
    }
    // Se agregan 3 funciones para la vida y habre de las barras.
    fun takeDamage(amount: Float) {
        _uiState.update { currentState ->
            currentState.copy(health = (currentState.health - amount).coerceIn(0f, 1f))
        }
    }

    fun consumeEnergy(amount: Float) {
        _uiState.update { currentState ->
            currentState.copy(hunger = (currentState.hunger - amount).coerceIn(0f, 1f))
        }
    }

    fun eatFood() {
        // Por ejemplo, recuperar 30% de hambre al comer unas gorditas afuera de ESCOM
        _uiState.update { currentState ->
            currentState.copy(hunger = (currentState.hunger + 0.3f).coerceIn(0f, 1f))
        }
    }
}