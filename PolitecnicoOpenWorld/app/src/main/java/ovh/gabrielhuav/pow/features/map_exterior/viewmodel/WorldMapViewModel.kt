package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

class WorldMapViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    private val npcAiManager = NpcAiManager()
    private val overpassRepository = OverpassRepository()

    private var lastNetworkFetchLocation: GeoPoint? = null
    private var gameLoopJob: Job? = null
    private var tickCount = 0

    private var roadNetwork: List<MapWay> = emptyList()

    fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch {

            while (_uiState.value.currentLocation == null) {
                delay(100)
            }
            val initialLoc = _uiState.value.currentLocation!!

            val initialNetwork = withContext(Dispatchers.IO) {
                overpassRepository.fetchRoadNetwork(initialLoc.latitude, initialLoc.longitude)
            }

            withContext(Dispatchers.Main) {
                if (initialNetwork.isNotEmpty()) {
                    roadNetwork = initialNetwork
                    npcAiManager.updateRoadNetwork(initialNetwork)

                    val forcedLocation = getNearestPointOnNetwork(initialLoc)

                    _uiState.update { it.copy(
                        currentLocation = forcedLocation,
                        isRoadNetworkReady = true
                    )}
                } else {
                    _uiState.update { it.copy(isRoadNetworkReady = true) }
                }
            }
            lastNetworkFetchLocation = initialLoc

            while (isActive) {
                _uiState.value.currentLocation?.let { location ->
                    checkAndFetchRoadNetwork(location)

                    if (_uiState.value.isRoadNetworkReady) {
                        tickCount++
                        if (tickCount % 3 == 0) {
                            npcAiManager.updateNpcs(location)
                            _uiState.update { it.copy(npcs = npcAiManager.npcs.value) }
                        }
                    }
                }
                delay(33)
            }
        }
    }

    fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
    }

    private fun checkAndFetchRoadNetwork(currentLoc: GeoPoint) {
        val lastLoc = lastNetworkFetchLocation
        val needsFetch = lastLoc == null || distance(lastLoc, currentLoc) > 0.005

        if (!needsFetch) return
        lastNetworkFetchLocation = currentLoc

        viewModelScope.launch(Dispatchers.IO) {
            val network = overpassRepository.fetchRoadNetwork(
                currentLoc.latitude, currentLoc.longitude
            )
            withContext(Dispatchers.Main) {
                if (network.isNotEmpty()) {
                    roadNetwork = network
                    npcAiManager.updateRoadNetwork(network)
                }
            }
        }
    }

    fun moveCharacter(direction: Direction) {
        val currentLoc = _uiState.value.currentLocation ?: return
        val step = 0.000003

        val tempLocation = when (direction) {
            Direction.UP    -> GeoPoint(currentLoc.latitude + step, currentLoc.longitude)
            Direction.DOWN  -> GeoPoint(currentLoc.latitude - step, currentLoc.longitude)
            Direction.LEFT  -> GeoPoint(currentLoc.latitude, currentLoc.longitude - step)
            Direction.RIGHT -> GeoPoint(currentLoc.latitude, currentLoc.longitude + step)
        }

        // BLOQUEO ESTRICTO: Si no hay calles (falla de red o de carga), no te deja moverte
        // Esto evita al 100% que salgas volando por los edificios si algo falla.
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) {
            return
        }

        val nearestCenterLinePoint = getNearestPointOnNetwork(tempLocation)
        val distToCenter = distance(tempLocation, nearestCenterLinePoint)

        // Radio muy ajustado (Aprox 1.2 metros reales).
        // Ya no abarcarás los edificios, pero puedes moverte para elegir en cruces.
        val streetRadius = 0.000012

        if (distToCenter <= streetRadius) {
            _uiState.update { it.copy(currentLocation = tempLocation) }
        } else {
            // "Pared invisible": Te deslizas suavemente por el borde del asfalto sin salirte.
            val angle = atan2(tempLocation.latitude - nearestCenterLinePoint.latitude, tempLocation.longitude - nearestCenterLinePoint.longitude)
            val edgeLat = nearestCenterLinePoint.latitude + sin(angle) * streetRadius
            val edgeLon = nearestCenterLinePoint.longitude + cos(angle) * streetRadius
            _uiState.update { it.copy(currentLocation = GeoPoint(edgeLat, edgeLon)) }
        }
    }

    private fun getNearestPointOnNetwork(targetLocation: GeoPoint): GeoPoint {
        var bestDist = Double.MAX_VALUE
        var bestPoint = targetLocation

        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val n1 = way.nodes[i]
                val n2 = way.nodes[i + 1]
                val p = projectPointOnSegment(
                    targetLocation,
                    GeoPoint(n1.lat, n1.lon),
                    GeoPoint(n2.lat, n2.lon)
                )
                val d = distance(targetLocation, p)
                if (d < bestDist) {
                    bestDist = d
                    bestPoint = p
                }
            }
        }
        return bestPoint
    }

    private fun projectPointOnSegment(p: GeoPoint, v: GeoPoint, w: GeoPoint): GeoPoint {
        val l2 = (w.latitude - v.latitude).pow(2) + (w.longitude - v.longitude).pow(2)
        if (l2 == 0.0) return v
        var t = ((p.latitude - v.latitude) * (w.latitude - v.latitude) + (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2
        t = max(0.0, min(1.0, t))
        return GeoPoint(
            v.latitude + t * (w.latitude - v.latitude),
            v.longitude + t * (w.longitude - v.longitude)
        )
    }

    private fun distance(p1: GeoPoint, p2: GeoPoint): Double =
        sqrt((p1.latitude - p2.latitude).pow(2) + (p1.longitude - p2.longitude).pow(2))

    fun updateInitialLocation(latitude: Double, longitude: Double) {
        if (_uiState.value.isLoadingLocation) {
            _uiState.update {
                it.copy(
                    currentLocation = GeoPoint(latitude, longitude),
                    isLoadingLocation = false
                )
            }
        }
    }

    fun executeAction(action: GameAction) { println("Acción ejecutada: $action") }
    fun toggleSettingsDialog(show: Boolean) { _uiState.update { it.copy(showSettingsDialog = show) } }
    fun setMapProvider(provider: MapProvider) { _uiState.update { it.copy(mapProvider = provider) } }
    fun zoomIn() { _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it } }
    fun zoomOut() { _uiState.update { if (it.zoomLevel > 2.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it } }
}