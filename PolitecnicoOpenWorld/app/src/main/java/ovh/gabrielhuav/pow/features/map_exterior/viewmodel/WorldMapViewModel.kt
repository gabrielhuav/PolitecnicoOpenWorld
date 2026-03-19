package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y } // Nuevos botones

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

        // Reducimos el paso para simular caminar suavemente
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
        // Por ahora solo imprimimos en consola, aquí irá la lógica de interactuar, correr, etc.
        println("Acción ejecutada: $action")
    }
}