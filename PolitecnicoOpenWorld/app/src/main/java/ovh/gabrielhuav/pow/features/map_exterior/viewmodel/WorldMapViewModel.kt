package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Nueva función para forzar el centrado en el jugador
    fun centerOnPlayer() {
        _uiState.value = _uiState.value.copy(centerTrigger = System.currentTimeMillis())
    }
}