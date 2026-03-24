package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.features.map_exterior.data.RoutingRepository
import ovh.gabrielhuav.pow.features.map_exterior.engine.WorldSimulationEngine

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    // 1. Instanciamos nuestra nueva arquitectura limpia
    private val routingRepository = RoutingRepository()
    private val engine = WorldSimulationEngine(viewModelScope, routingRepository)

    // 2. Estado de la Interfaz (UI State)
    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    init {
        // Escuchamos silenciosamente al Motor. Cuando la matemática cambie la posición de un NPC o Coche,
        // automáticamente se reflejará en nuestro uiState para que Compose lo dibuje.
        viewModelScope.launch {
            engine.simulationState.collect { simState ->
                _uiState.update { it.copy(npcs = simState.npcs, cars = simState.cars) }
            }
        }
    }

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            _uiState.value = _uiState.value.copy(
                currentLocation = GeoPoint(latitude, longitude),
                isLoadingLocation = false
            )

            // Le delegamos al motor la creación y arranque
            engine.spawnEntitiesNearPlayer(latitude, longitude)
            engine.startLoop()
        }
    }

    // --- CONTROLES DEL JUGADOR ---

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

    // --- CONFIGURACIÓN DE LA UI ---

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSettingsDialog = show)
    }

    fun setMapProvider(provider: MapProvider) {
        _uiState.value = _uiState.value.copy(mapProvider = provider)
    }

    // --- CONTROLES DE ZOOM PARA LEAFLET Y OSM ---

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
}