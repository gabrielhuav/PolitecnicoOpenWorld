package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import kotlin.math.pow
import kotlin.math.sqrt

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()
    private val npcAiManager = NpcAiManager()
    private val overpassRepository = OverpassRepository()

    private var lastNetworkFetchLocation: GeoPoint? = null

    init {
        startGameLoop()
    }

    private fun startGameLoop() {
        viewModelScope.launch {
            while (true) {
                _uiState.value.currentLocation?.let { location ->

                    // 1. Validar si necesitamos descargar una nueva zona del mapa (Cada 500m)
                    checkAndFetchRoadNetwork(location)

                    // 2. Actualizar la matemática de los NPCs
                    npcAiManager.updateNpcs(location)
                    _uiState.value = _uiState.value.copy(npcs = npcAiManager.npcs.value)
                }
                delay(33) // ~30 FPS para un movimiento fluido
            }
        }
    }

    private fun checkAndFetchRoadNetwork(currentLoc: GeoPoint) {
        val lastLoc = lastNetworkFetchLocation
        val needsFetch = lastLoc == null || distance(lastLoc, currentLoc) > 0.005 // Aprox 500m

        if (needsFetch) {
            lastNetworkFetchLocation = currentLoc
            // Descargar en segundo plano sin detener el Game Loop
            viewModelScope.launch(Dispatchers.IO) {
                val network = overpassRepository.fetchRoadNetwork(currentLoc.latitude, currentLoc.longitude)
                npcAiManager.updateRoadNetwork(network)
            }
        }
    }

    private fun distance(p1: GeoPoint, p2: GeoPoint): Double {
        return sqrt((p1.latitude - p2.latitude).pow(2) + (p1.longitude - p2.longitude).pow(2))
    }

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
}