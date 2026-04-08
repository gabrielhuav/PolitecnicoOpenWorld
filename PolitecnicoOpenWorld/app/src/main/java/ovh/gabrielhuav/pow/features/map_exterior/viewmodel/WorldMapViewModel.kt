package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import java.util.concurrent.atomic.AtomicBoolean
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

class WorldMapViewModel(
    private val roadNetworkCache: RoadNetworkCache
) : ViewModel() {

    // ─── FACTORY ─────────────────────────────────────────────────────────────────
    /**
     * Factory necesaria porque el ViewModel recibe un parámetro (Context para la caché).
     * Se usa en WorldMapScreen: viewModel(factory = WorldMapViewModel.Factory(context))
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WorldMapViewModel(
                roadNetworkCache = RoadNetworkCache(context.applicationContext)
            ) as T
        }
    }

    // ─── STATE ───────────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(WorldMapState())
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    private val npcAiManager = NpcAiManager()
    private val overpassRepository = OverpassRepository()

    private var roadNetwork: List<MapWay> = emptyList()
    private var lastNetworkFetchLocation: GeoPoint? = null
    private var gameLoopJob: Job? = null
    private var tickCount = 0

    // ─── CONTROL DE FETCH ────────────────────────────────────────────────────────
    private val isFetchingNetwork = AtomicBoolean(false)
    private var lastFetchAttemptTimeMs = 0L

    /**
     * Distancia mínima (en grados) que el jugador debe alejarse del último punto de
     * descarga para considerar buscar datos nuevos.
     *
     * Con radio de descarga de 2000m (~0.018°), usamos 0.015° como umbral de re-fetch.
     * Esto garantiza que siempre haya al menos ~300m de "buffer" de calles por delante.
     */
    private val REFETCH_DISTANCE_DEGREES = 0.015

    /**
     * Tiempo mínimo entre intentos de fetch fallidos (5 minutos).
     * Evita martillear Overpass si el servidor está lento o sin conexión.
     */
    private val REFETCH_COOLDOWN_MS = 5 * 60 * 1000L

    // ─── GAME LOOP ───────────────────────────────────────────────────────────────

    fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return

        gameLoopJob = viewModelScope.launch {

            // Esperar a tener ubicación antes de hacer cualquier cosa
            while (_uiState.value.currentLocation == null) {
                delay(100)
            }
            val initialLoc = _uiState.value.currentLocation!!

            // PASO 1: Intentar cargar desde caché (rápido, sin red)
            val cachedNetwork = roadNetworkCache.get(initialLoc.latitude, initialLoc.longitude)

            if (cachedNetwork != null) {
                // ¡HIT de caché! Arrancar inmediatamente sin tocar la red.
                applyRoadNetwork(cachedNetwork, initialLoc)
                lastNetworkFetchLocation = initialLoc

                // Log de diagnóstico
                val stats = roadNetworkCache.getStats()
                android.util.Log.d("WorldMapVM", "Caché HIT al iniciar. $stats")
            } else {
                // MISS: Hay que descargar. Retry con backoff exponencial.
                android.util.Log.d("WorldMapVM", "Caché MISS al iniciar. Descargando de Overpass...")
                var retryDelayMs = 1_000L

                while (isActive && roadNetwork.isEmpty()) {
                    val network = withContext(Dispatchers.IO) {
                        overpassRepository.fetchRoadNetwork(
                            initialLoc.latitude, initialLoc.longitude
                        )
                    }

                    if (network.isNotEmpty()) {
                        // Guardar en caché en background — no bloquea el game loop
                        launch(Dispatchers.IO) {
                            roadNetworkCache.put(initialLoc.latitude, initialLoc.longitude, network)
                        }
                        applyRoadNetwork(network, initialLoc)
                        lastNetworkFetchLocation = initialLoc
                        break
                    } else {
                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(isRoadNetworkReady = false) }
                        }
                        delay(retryDelayMs)
                        retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
                    }
                }
            }

            // PASO 2: Game loop principal — corre a ~30fps
            while (isActive) {
                _uiState.value.currentLocation?.let { location ->
                    maybeRefetchRoadNetwork(location)

                    if (_uiState.value.isRoadNetworkReady) {
                        tickCount++
                        // Actualizar NPCs cada 3 ticks (~100ms) para no sobrecargar la CPU
                        if (tickCount % 3 == 0) {
                            npcAiManager.updateNpcs(location)
                            _uiState.update { it.copy(npcs = npcAiManager.npcs.value) }
                        }
                    }
                }
                delay(33) // ~30fps
            }
        }
    }

    fun stopGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = null
    }

    /**
     * Aplica una red de calles descargada o cargada desde caché al estado del juego.
     * Siempre debe llamarse con la red ya validada (no vacía).
     *
     * ZOOM CINEMATOGRÁFICO:
     * Al confirmar que las calles están listas, hacemos zoom-in progresivo de
     * ZOOM_LOADING (17) → ZOOM_GAMEPLAY (21). Esto da el efecto de "acercarse a
     * la calle" estilo GTA y garantiza que osmdroid haya tenido tiempo de cachear
     * tiles a zoom 17 antes de pasar al zoom más cercano.
     */
    private suspend fun applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network
        npcAiManager.updateRoadNetwork(network)

        val snappedLocation = withContext(Dispatchers.Default) {
            getNearestPointOnNetwork(playerLocation)
        }

        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    currentLocation = snappedLocation,
                    isRoadNetworkReady = true
                )
            }
        }

        // Zoom-in cinematográfico: 17 → 18 → 19 → 20 → 21
        // Solo si el usuario no hizo zoom manual antes de que terminara la carga
        if (_uiState.value.zoomLevel <= WorldMapState.ZOOM_LOADING) {
            var z = WorldMapState.ZOOM_LOADING + 1.0
            while (z <= WorldMapState.ZOOM_GAMEPLAY) {
                delay(120) // 120ms por paso → animación total ~480ms
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(zoomLevel = z) }
                }
                z += 1.0
            }
        }
    }

    /**
     * Comprueba si hay que descargar una nueva zona y lanza la descarga si es necesario.
     *
     * LÓGICA (en orden de prioridad):
     * 1. ¿El jugador sigue cerca del último punto de descarga? → No hacer nada.
     * 2. ¿Estamos en cooldown por un intento fallido reciente? → No hacer nada.
     * 3. ¿Ya hay una descarga en curso? → No duplicar.
     * 4. ¿Hay datos en caché para esta nueva zona? → Cargar desde caché.
     * 5. Sin caché → Llamar a Overpass.
     */
    private fun maybeRefetchRoadNetwork(currentLoc: GeoPoint) {
        val lastLoc = lastNetworkFetchLocation
        val distanceMoved = if (lastLoc != null) distance(lastLoc, currentLoc) else Double.MAX_VALUE

        if (distanceMoved < REFETCH_DISTANCE_DEGREES) return

        val now = System.currentTimeMillis()
        if (now - lastFetchAttemptTimeMs < REFETCH_COOLDOWN_MS) return
        if (!isFetchingNetwork.compareAndSet(false, true)) return

        lastFetchAttemptTimeMs = now

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Intentar caché primero (operación de disco, rápida)
                val cached = roadNetworkCache.get(currentLoc.latitude, currentLoc.longitude)

                if (cached != null) {
                    android.util.Log.d("WorldMapVM", "Caché HIT al moverse a zona nueva.")
                    withContext(Dispatchers.Main) {
                        roadNetwork = cached
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        // No reseteamos isRoadNetworkReady — ya estaba true
                    }
                } else {
                    // Overpass como último recurso
                    android.util.Log.d("WorldMapVM", "Caché MISS al moverse. Descargando...")
                    val network = overpassRepository.fetchRoadNetwork(
                        currentLoc.latitude, currentLoc.longitude
                    )
                    if (network.isNotEmpty()) {
                        // Guardar en caché para la próxima vez
                        roadNetworkCache.put(currentLoc.latitude, currentLoc.longitude, network)

                        withContext(Dispatchers.Main) {
                            roadNetwork = network
                            npcAiManager.updateRoadNetwork(network)
                            lastNetworkFetchLocation = currentLoc
                        }
                    }
                }
            } finally {
                isFetchingNetwork.set(false)
            }
        }
    }

    // ─── MOVIMIENTO DEL JUGADOR ───────────────────────────────────────────────────

    fun moveCharacter(direction: Direction) {
        val currentLoc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return

        val stepDegrees = 0.000003

        val tempLocation = when (direction) {
            Direction.UP    -> GeoPoint(currentLoc.latitude + stepDegrees, currentLoc.longitude)
            Direction.DOWN  -> GeoPoint(currentLoc.latitude - stepDegrees, currentLoc.longitude)
            Direction.LEFT  -> GeoPoint(currentLoc.latitude, currentLoc.longitude - stepDegrees)
            Direction.RIGHT -> GeoPoint(currentLoc.latitude, currentLoc.longitude + stepDegrees)
        }

        val nearestPoint = getNearestPointOnNetwork(tempLocation)
        val distToCenter = distance(tempLocation, nearestPoint)
        val streetRadiusDegrees = 0.000012

        if (distToCenter <= streetRadiusDegrees) {
            _uiState.update { it.copy(currentLocation = tempLocation) }
        } else {
            // Proyectar al borde de la calle más cercana
            val angle = atan2(
                tempLocation.latitude - nearestPoint.latitude,
                tempLocation.longitude - nearestPoint.longitude
            )
            _uiState.update {
                it.copy(
                    currentLocation = GeoPoint(
                        nearestPoint.latitude + sin(angle) * streetRadiusDegrees,
                        nearestPoint.longitude + cos(angle) * streetRadiusDegrees
                    )
                )
            }
        }
    }

    // ─── SPATIAL INDEX ────────────────────────────────────────────────────────────
    // Grid espacial para encontrar el segmento de calle más cercano en O(log N)
    // en lugar de O(N), crítico cuando roadNetwork tiene miles de segmentos.

    private data class NetworkSegment(
        val start: GeoPoint,
        val end: GeoPoint,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    private val SPATIAL_CELL_DEGREES = 0.0025
    private var indexedRoadNetworkRef: List<MapWay>? = null
    private var indexedSegments: List<NetworkSegment> = emptyList()
    private var segmentGrid: Map<Long, List<NetworkSegment>> = emptyMap()

    /**
     * Reconstruye el índice espacial solo cuando la red de calles cambia.
     * Usa una clave Long (cellLat * PRIME + cellLon) en lugar de Pair<Int,Int>
     * para evitar el boxing/unboxing que tiene Pair con tipos primitivos.
     */
    private fun ensureSpatialIndex() {
        if (indexedRoadNetworkRef === roadNetwork) return

        val newSegments = ArrayList<NetworkSegment>(roadNetwork.sumOf { it.nodes.size })
        // Long como clave elimina el overhead de Pair<Int,Int> (boxing)
        val newGrid = HashMap<Long, MutableList<NetworkSegment>>()

        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val n1 = way.nodes[i]
                val n2 = way.nodes[i + 1]
                val startLat = n1.lat; val startLon = n1.lon
                val endLat   = n2.lat; val endLon   = n2.lon

                val segment = NetworkSegment(
                    start  = GeoPoint(startLat, startLon),
                    end    = GeoPoint(endLat, endLon),
                    minLat = min(startLat, endLat),
                    maxLat = max(startLat, endLat),
                    minLon = min(startLon, endLon),
                    maxLon = max(startLon, endLon)
                )
                newSegments.add(segment)

                val minCellLat = cellCoord(segment.minLat)
                val maxCellLat = cellCoord(segment.maxLat)
                val minCellLon = cellCoord(segment.minLon)
                val maxCellLon = cellCoord(segment.maxLon)

                for (cLat in minCellLat..maxCellLat) {
                    for (cLon in minCellLon..maxCellLon) {
                        newGrid.getOrPut(cellKey(cLat, cLon)) { mutableListOf() }.add(segment)
                    }
                }
            }
        }

        indexedRoadNetworkRef = roadNetwork
        indexedSegments = newSegments
        segmentGrid = newGrid
    }

    private fun getCandidateSegments(loc: GeoPoint): List<NetworkSegment> {
        val cLat = cellCoord(loc.latitude)
        val cLon = cellCoord(loc.longitude)
        val result = LinkedHashSet<NetworkSegment>()
        for (dLat in -1..1) {
            for (dLon in -1..1) {
                segmentGrid[cellKey(cLat + dLat, cLon + dLon)]?.let { result.addAll(it) }
            }
        }
        return if (result.isNotEmpty()) result.toList() else indexedSegments
    }

    // Clave Long: evita boxing de Pair<Int,Int>. Número primo para reducir colisiones.
    private fun cellKey(lat: Int, lon: Int): Long = lat.toLong() * 1_000_003L + lon.toLong()
    private fun cellCoord(value: Double): Int = floor(value / SPATIAL_CELL_DEGREES).toInt()

    private fun getNearestPointOnNetwork(target: GeoPoint): GeoPoint {
        ensureSpatialIndex()
        val candidates = getCandidateSegments(target)
        if (candidates.isEmpty()) return target

        var bestDist = Double.MAX_VALUE
        var bestPoint = target

        for (seg in candidates) {
            val p = projectPointOnSegment(target, seg.start, seg.end)
            val d = distance(target, p)
            if (d < bestDist) { bestDist = d; bestPoint = p }
        }
        return bestPoint
    }

    private fun projectPointOnSegment(p: GeoPoint, v: GeoPoint, w: GeoPoint): GeoPoint {
        val l2 = (w.latitude - v.latitude).pow(2) + (w.longitude - v.longitude).pow(2)
        if (l2 == 0.0) return v
        val t = ((p.latitude - v.latitude) * (w.latitude - v.latitude) +
                (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2
        val tc = max(0.0, min(1.0, t))
        return GeoPoint(
            v.latitude  + tc * (w.latitude  - v.latitude),
            v.longitude + tc * (w.longitude - v.longitude)
        )
    }

    private fun distance(p1: GeoPoint, p2: GeoPoint): Double =
        sqrt((p1.latitude - p2.latitude).pow(2) + (p1.longitude - p2.longitude).pow(2))

    // ─── API PÚBLICA ──────────────────────────────────────────────────────────────

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

    fun executeAction(action: GameAction) {
        android.util.Log.d("GameAction", "Acción: $action")
    }

    fun toggleSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setMapProvider(provider: MapProvider) {
        _uiState.update { it.copy(mapProvider = provider) }
    }

    fun zoomIn() {
        _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it }
    }

    fun zoomOut() {
        // Límite inferior: zoom 14 (más alejado que eso pierde el sentido de GTA)
        _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it }
    }
}