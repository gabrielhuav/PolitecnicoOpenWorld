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
import kotlin.math.floor
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

    // OPTIMIZACIÓN COPILOT: Prevención de tormentas de peticiones y bloqueos
    private var isFetchingNetwork = false
    private var lastFetchAttemptTimeMs = 0L

    fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch {

            while (_uiState.value.currentLocation == null) {
                delay(100)
            }
            val initialLoc = _uiState.value.currentLocation!!

            var retryDelayMs = 1000L
            while (isActive && roadNetwork.isEmpty()) {
                val initialNetwork = withContext(Dispatchers.IO) {
                    overpassRepository.fetchRoadNetwork(initialLoc.latitude, initialLoc.longitude)
                }

                if (initialNetwork.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        roadNetwork = initialNetwork
                        npcAiManager.updateRoadNetwork(initialNetwork)
                        val forcedLocation = getNearestPointOnNetwork(initialLoc)
                        _uiState.update { it.copy(
                            currentLocation = forcedLocation,
                            isRoadNetworkReady = true
                        ) }
                    }
                    lastNetworkFetchLocation = initialLoc
                    break
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(isRoadNetworkReady = false) }
                    }
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(10_000L)
                }
            }

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
        // Bloqueo estricto para no lanzar descargas paralelas
        if (isFetchingNetwork) return

        val lastLoc = lastNetworkFetchLocation
        val needsFetch = lastLoc == null || distance(lastLoc, currentLoc) > 0.005

        if (!needsFetch) return

        // Backoff de 10 segundos en caso de fallo para no hacer spam al servidor Overpass
        val now = System.currentTimeMillis()
        if (now - lastFetchAttemptTimeMs < 10000) return

        isFetchingNetwork = true
        lastFetchAttemptTimeMs = now

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val network = overpassRepository.fetchRoadNetwork(
                    currentLoc.latitude, currentLoc.longitude
                )
                withContext(Dispatchers.Main) {
                    if (network.isNotEmpty()) {
                        roadNetwork = network
                        npcAiManager.updateRoadNetwork(network)
                        lastNetworkFetchLocation = currentLoc
                    }
                }
            } finally {
                // Liberar el candado pase lo que pase (éxito, error o timeout)
                isFetchingNetwork = false
            }
        }
    }

    fun moveCharacter(direction: Direction) {
        val currentLoc = _uiState.value.currentLocation ?: return

        val stepDegrees = 0.000003

        val tempLocation = when (direction) {
            Direction.UP    -> GeoPoint(currentLoc.latitude + stepDegrees, currentLoc.longitude)
            Direction.DOWN  -> GeoPoint(currentLoc.latitude - stepDegrees, currentLoc.longitude)
            Direction.LEFT  -> GeoPoint(currentLoc.latitude, currentLoc.longitude - stepDegrees)
            Direction.RIGHT -> GeoPoint(currentLoc.latitude, currentLoc.longitude + stepDegrees)
        }

        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return

        val nearestCenterLinePoint = getNearestPointOnNetwork(tempLocation)
        val distToCenter = distance(tempLocation, nearestCenterLinePoint)

        val streetRadiusDegrees = 0.000012

        if (distToCenter <= streetRadiusDegrees) {
            _uiState.update { it.copy(currentLocation = tempLocation) }
        } else {
            val angle = atan2(tempLocation.latitude - nearestCenterLinePoint.latitude, tempLocation.longitude - nearestCenterLinePoint.longitude)
            val edgeLat = nearestCenterLinePoint.latitude + sin(angle) * streetRadiusDegrees
            val edgeLon = nearestCenterLinePoint.longitude + cos(angle) * streetRadiusDegrees
            _uiState.update { it.copy(currentLocation = GeoPoint(edgeLat, edgeLon)) }
        }
    }

    // ==========================================
    // Spatial Indexing Grid
    // ==========================================
    private data class NetworkSegment(
        val start: GeoPoint,
        val end: GeoPoint,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    private val spatialCellSizeDegrees = 0.0025
    private var indexedRoadNetworkRef: List<MapWay>? = null
    private var indexedRoadNetworkSegmentCount: Int = -1
    private var indexedSegments: List<NetworkSegment> = emptyList()
    private var segmentGrid: Map<Pair<Int, Int>, List<NetworkSegment>> = emptyMap()

    private fun ensureSpatialIndex() {
        // OPTIMIZACIÓN COPILOT: Comprobación de referencia O(1) de forma inmediata
        // Salida temprana antes de ejecutar sumOf (que es O(N))
        if (indexedRoadNetworkRef === roadNetwork) return

        val currentSegmentCount = roadNetwork.sumOf { way -> max(0, way.nodes.size - 1) }

        val newSegments = mutableListOf<NetworkSegment>()
        val newGrid = HashMap<Pair<Int, Int>, MutableList<NetworkSegment>>()

        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val n1 = way.nodes[i]
                val n2 = way.nodes[i + 1]
                val start = GeoPoint(n1.lat, n1.lon)
                val end = GeoPoint(n2.lat, n2.lon)
                val segment = NetworkSegment(
                    start = start, end = end,
                    minLat = min(start.latitude, end.latitude),
                    maxLat = max(start.latitude, end.latitude),
                    minLon = min(start.longitude, end.longitude),
                    maxLon = max(start.longitude, end.longitude)
                )
                newSegments += segment

                val minCellLat = cellCoordinate(segment.minLat)
                val maxCellLat = cellCoordinate(segment.maxLat)
                val minCellLon = cellCoordinate(segment.minLon)
                val maxCellLon = cellCoordinate(segment.maxLon)

                for (cellLat in minCellLat..maxCellLat) {
                    for (cellLon in minCellLon..maxCellLon) {
                        newGrid.getOrPut(cellLat to cellLon) { mutableListOf() }.add(segment)
                    }
                }
            }
        }
        indexedRoadNetworkRef = roadNetwork
        indexedRoadNetworkSegmentCount = currentSegmentCount
        indexedSegments = newSegments
        segmentGrid = newGrid
    }

    private fun getCandidateSegments(targetLocation: GeoPoint): List<NetworkSegment> {
        val centerLat = cellCoordinate(targetLocation.latitude)
        val centerLon = cellCoordinate(targetLocation.longitude)
        val candidates = LinkedHashSet<NetworkSegment>()

        for (lat in (centerLat - 1)..(centerLat + 1)) {
            for (lon in (centerLon - 1)..(centerLon + 1)) {
                segmentGrid[lat to lon]?.let { candidates.addAll(it) }
            }
        }
        return candidates.toList()
    }

    private fun cellCoordinate(value: Double): Int = floor(value / spatialCellSizeDegrees).toInt()

    private fun getNearestPointOnNetwork(targetLocation: GeoPoint): GeoPoint {
        ensureSpatialIndex()
        val candidates = getCandidateSegments(targetLocation).ifEmpty { indexedSegments }
        if (candidates.isEmpty()) return targetLocation

        var bestDist = Double.MAX_VALUE
        var bestPoint = targetLocation

        for (segment in candidates) {
            val p = projectPointOnSegment(targetLocation, segment.start, segment.end)
            val d = distance(targetLocation, p)
            if (d < bestDist) {
                bestDist = d
                bestPoint = p
            }
        }
        return bestPoint
    }
    // ==========================================

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
                it.copy(currentLocation = GeoPoint(latitude, longitude), isLoadingLocation = false)
            }
        }
    }

    fun executeAction(action: GameAction) { println("Acción ejecutada: $action") }
    fun toggleSettingsDialog(show: Boolean) { _uiState.update { it.copy(showSettingsDialog = show) } }
    fun setMapProvider(provider: MapProvider) { _uiState.update { it.copy(mapProvider = provider) } }
    fun zoomIn() { _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it } }
    fun zoomOut() { _uiState.update { if (it.zoomLevel > 2.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it } }
}