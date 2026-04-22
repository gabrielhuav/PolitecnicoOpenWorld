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
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.settings.models.ControlType
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
    private val roadNetworkCache: RoadNetworkCache,
    val tileCache: TileCache,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(WorldMapViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            val appCtx = context.applicationContext
            val database = PowDatabase.getInstance(appCtx)
            return WorldMapViewModel(
                roadNetworkCache = RoadNetworkCache(database.roadNetworkDao()),
                tileCache        = TileCache(database.mapTileDao()),
                settingsRepository = SettingsRepository(appCtx)
            ) as T
        }
    }

    // Ahora lee del repositorio
    private val _uiState = MutableStateFlow(
        WorldMapState(
            landmarks = listOf(
                Landmark(
                    id = "escom_main",
                    name = "ESCOM IPN",
                    location = GeoPoint(19.5041508, -99.1471029),
                    rotationAngle = 0f,
                    assetPath = "BUILDINGS/IPN/building_escom.webp",
                    scaleFactor = 0.15f
                )
            ),
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls()
        )
    )

    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    private val npcAiManager      = NpcAiManager()
    private val overpassRepository = OverpassRepository()
    private var roadNetwork: List<MapWay> = emptyList()
    private var lastNetworkFetchLocation: GeoPoint? = null
    private var gameLoopJob: Job? = null
    private var tickCount = 0
    private val isFetchingNetwork  = AtomicBoolean(false)
    private var lastFetchAttemptMs = 0L

    private val REFETCH_DISTANCE_DEG = 0.015
    private val REFETCH_COOLDOWN_MS  = 5 * 60 * 1000L

    // ─── GAME LOOP ───────────────────────────────────────────────────────────────

    fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch {

            while (_uiState.value.currentLocation == null) { delay(100) }
            val initialLoc = _uiState.value.currentLocation!!

            // Tiles OSM nativo → siempre local (osmdroid maneja su caché)
            if (_uiState.value.mapProvider == MapProvider.OSM) {
                _uiState.update { it.copy(tileSource = TileSource.LOCAL_OSM) }
            }

            // ── PASO 1: Intentar Room ──────────────────────────────────────────
            val cached = roadNetworkCache.get(initialLoc.latitude, initialLoc.longitude)
            if (cached != null) {
                android.util.Log.d("WorldMapVM", "Room HIT inicio: ${roadNetworkCache.getStats()}")
                // Marcar LOCAL_DB ANTES de applyRoadNetwork para que el widget ya lo muestre
                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                applyRoadNetwork(cached, initialLoc)
                lastNetworkFetchLocation = initialLoc
            } else {
                // ── PASO 2: Overpass con backoff ───────────────────────────────
                android.util.Log.d("WorldMapVM", "Room MISS inicio — Overpass")
                _uiState.update { it.copy(roadSource = RoadSource.LOADING) }
                var retryMs = 1_000L

                while (isActive && roadNetwork.isEmpty()) {
                    val network = overpassRepository.fetchRoadNetwork(initialLoc.latitude, initialLoc.longitude)
                    if (network.isNotEmpty()) {
                        // Marcar NETWORK mientras se usa, luego LOCAL_DB cuando ya esté guardado
                        _uiState.update { it.copy(roadSource = RoadSource.NETWORK) }
                        applyRoadNetwork(network, initialLoc)
                        lastNetworkFetchLocation = initialLoc

                        // Guardar en Room y luego actualizar widget a LOCAL_DB
                        launch(Dispatchers.IO) {
                            roadNetworkCache.put(initialLoc.latitude, initialLoc.longitude, network)
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                                android.util.Log.d("WorldMapVM", "Calles guardadas en Room ✓")
                            }
                        }
                        break
                    } else {
                        _uiState.update { it.copy(isRoadNetworkReady = false) }
                        delay(retryMs)
                        retryMs = (retryMs * 2).coerceAtMost(30_000L)
                    }
                }
            }

            // ── PASO 3: Game loop ~30fps ───────────────────────────────────────
            while (isActive) {
                _uiState.value.currentLocation?.let { location ->
                    maybeRefetchRoadNetwork(location)
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

    fun stopGameLoop() { gameLoopJob?.cancel(); gameLoopJob = null }

    private suspend fun applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network
        npcAiManager.updateRoadNetwork(network)
        val snapped = withContext(Dispatchers.Default) { getNearestPointOnNetwork(playerLocation) }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(currentLocation = snapped, isRoadNetworkReady = true) }
        }
        // Zoom-in cinematográfico: diferente según proveedor
        // OSM nativo: 17 → 21 (tiles locales, rápido)
        // Proveedores web: 17 → 18 (tiles remotos, menos zoom para cargar bien)
        val targetZoom = if (_uiState.value.mapProvider.isWebProvider)
            WorldMapState.ZOOM_GAMEPLAY_WEB
        else
            WorldMapState.ZOOM_GAMEPLAY_OSM

        if (_uiState.value.zoomLevel <= WorldMapState.ZOOM_LOADING) {
            var z = WorldMapState.ZOOM_LOADING + 1.0
            while (z <= targetZoom) {
                delay(120)
                withContext(Dispatchers.Main) { _uiState.update { it.copy(zoomLevel = z) } }
                z += 1.0
            }
        }
    }

    private fun maybeRefetchRoadNetwork(currentLoc: GeoPoint) {
        val moved = if (lastNetworkFetchLocation != null)
            distance(lastNetworkFetchLocation!!, currentLoc) else Double.MAX_VALUE
        if (moved < REFETCH_DISTANCE_DEG) return

        val now = System.currentTimeMillis()
        if (now - lastFetchAttemptMs < REFETCH_COOLDOWN_MS) return
        if (!isFetchingNetwork.compareAndSet(false, true)) return
        lastFetchAttemptMs = now

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = roadNetworkCache.get(currentLoc.latitude, currentLoc.longitude)
                if (cached != null) {
                    android.util.Log.d("WorldMapVM", "Room HIT al moverse")
                    withContext(Dispatchers.Main) {
                        roadNetwork = cached
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                    }
                } else {
                    android.util.Log.d("WorldMapVM", "Room MISS al moverse — Overpass")
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(roadSource = RoadSource.NETWORK) }
                    }
                    val network = overpassRepository.fetchRoadNetwork(
                        currentLoc.latitude, currentLoc.longitude)
                    if (network.isNotEmpty()) {
                        roadNetworkCache.put(currentLoc.latitude, currentLoc.longitude, network)
                        withContext(Dispatchers.Main) {
                            roadNetwork = network
                            npcAiManager.updateRoadNetwork(network)
                            lastNetworkFetchLocation = currentLoc
                            // Una vez guardado en Room, mostrar LOCAL_DB
                            _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                        }
                    }
                }
            } finally {
                isFetchingNetwork.set(false)
            }
        }
    }

    // ─── NOTIFICACIÓN DE TILES (WebView) ─────────────────────────────────────────

    fun notifyTileSource(fromCache: Boolean) {
        if (_uiState.value.mapProvider == MapProvider.OSM) return
        val source = if (fromCache) TileSource.LOCAL_CACHE else TileSource.NETWORK
        if (_uiState.value.tileSource != source) {
            _uiState.update { it.copy(tileSource = source) }
        }
    }

    // ─── MOVIMIENTO ───────────────────────────────────────────────────────────────

    fun moveCharacter(direction: Direction) {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        val step = 0.000003
        val temp = when (direction) {
            Direction.UP    -> GeoPoint(loc.latitude + step, loc.longitude)
            Direction.DOWN  -> GeoPoint(loc.latitude - step, loc.longitude)
            Direction.LEFT  -> GeoPoint(loc.latitude, loc.longitude - step)
            Direction.RIGHT -> GeoPoint(loc.latitude, loc.longitude + step)
        }
        val nearest = getNearestPointOnNetwork(temp)
        val dist    = distance(temp, nearest)
        val radius  = 0.000012
        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else {
            val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude)
            _uiState.update { it.copy(currentLocation = GeoPoint(
                nearest.latitude  + sin(angle) * radius,
                nearest.longitude + cos(angle) * radius
            ))}
        }
    }

    // Movimiento por joystick
    fun moveCharacterByAngle(angleRad: Double) {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return

        val step = 0.000003 // Misma velocidad estática que el D-Pad

        // Calculamos el nuevo punto destino (Seno para Y/Latitud, Coseno para X/Longitud)
        val temp = GeoPoint(
            loc.latitude + sin(angleRad) * step,
            loc.longitude + cos(angleRad) * step
        )

        // Lógica existente de Snap-to-Road (ajuste a la calle)
        val nearest = getNearestPointOnNetwork(temp)
        val dist = distance(temp, nearest)
        val radius = 0.000012

        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else {
            val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude)
            _uiState.update { it.copy(currentLocation = GeoPoint(
                nearest.latitude + sin(angle) * radius,
                nearest.longitude + cos(angle) * radius
            ))}
        }
    }


    fun updateControlSettings(type: ControlType, scale: Float, swap: Boolean) {
        _uiState.update { it.copy(controlType = type, controlsScale = scale, swapControls = swap) }
    }

    // ─── SPATIAL INDEX ────────────────────────────────────────────────────────────

    private data class Seg(val s: GeoPoint, val e: GeoPoint,
                           val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

    private val CELL = 0.0025
    private var indexedRef: List<MapWay>?    = null
    private var segs: List<Seg>              = emptyList()
    private var grid: Map<Long, List<Seg>>   = emptyMap()

    private fun ensureIndex() {
        if (indexedRef === roadNetwork) return
        val newSegs = ArrayList<Seg>(roadNetwork.sumOf { it.nodes.size })
        val newGrid = HashMap<Long, MutableList<Seg>>()
        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val a = way.nodes[i]; val b = way.nodes[i + 1]
                val seg = Seg(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon),
                    min(a.lat, b.lat), max(a.lat, b.lat), min(a.lon, b.lon), max(a.lon, b.lon))
                newSegs.add(seg)
                for (r in cell(seg.minLat)..cell(seg.maxLat))
                    for (c in cell(seg.minLon)..cell(seg.maxLon))
                        newGrid.getOrPut(pack(r, c)) { mutableListOf() }.add(seg)
            }
        }
        indexedRef = roadNetwork; segs = newSegs; grid = newGrid
    }

    private fun candidates(loc: GeoPoint): List<Seg> {
        val r = cell(loc.latitude); val c = cell(loc.longitude)
        val res = LinkedHashSet<Seg>()
        for (dr in -1..1) for (dc in -1..1) grid[pack(r + dr, c + dc)]?.let { res.addAll(it) }
        return if (res.isNotEmpty()) res.toList() else segs
    }

    private fun pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
    private fun cell(v: Double): Int = floor(v / CELL).toInt()

    private fun getNearestPointOnNetwork(t: GeoPoint): GeoPoint {
        ensureIndex()
        val cands = candidates(t); if (cands.isEmpty()) return t
        var best = Double.MAX_VALUE; var pt = t
        for (seg in cands) {
            val p = project(t, seg.s, seg.e); val d = distance(t, p)
            if (d < best) { best = d; pt = p }
        }
        return pt
    }

    private fun project(p: GeoPoint, v: GeoPoint, w: GeoPoint): GeoPoint {
        val l2 = (w.latitude - v.latitude).pow(2) + (w.longitude - v.longitude).pow(2)
        if (l2 == 0.0) return v
        val t = max(0.0, min(1.0, ((p.latitude - v.latitude) * (w.latitude - v.latitude) +
                (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2))
        return GeoPoint(v.latitude + t * (w.latitude - v.latitude),
            v.longitude + t * (w.longitude - v.longitude))
    }

    private fun distance(a: GeoPoint, b: GeoPoint): Double =
        sqrt((a.latitude - b.latitude).pow(2) + (a.longitude - b.longitude).pow(2))

    // ─── API PÚBLICA ──────────────────────────────────────────────────────────────

    fun updateInitialLocation(lat: Double, lon: Double) {
        if (_uiState.value.isLoadingLocation)
            _uiState.update { it.copy(currentLocation = GeoPoint(lat, lon), isLoadingLocation = false) }
    }

    fun executeAction(action: GameAction) { android.util.Log.d("GameAction", "$action") }

    fun setMapProvider(provider: MapProvider) {
        val ts = if (provider == MapProvider.OSM) TileSource.LOCAL_OSM else TileSource.NETWORK
        // Ajustar zoom al cambiar de proveedor si ya estamos en gameplay
        val currentZoom = _uiState.value.zoomLevel
        val newZoom = when {
            provider == MapProvider.OSM && currentZoom < WorldMapState.ZOOM_GAMEPLAY_OSM ->
                WorldMapState.ZOOM_GAMEPLAY_OSM
            provider.isWebProvider && currentZoom > WorldMapState.ZOOM_GAMEPLAY_WEB ->
                WorldMapState.ZOOM_GAMEPLAY_WEB
            else -> currentZoom
        }
        _uiState.update { it.copy(mapProvider = provider, tileSource = ts, zoomLevel = newZoom) }
    }

    fun toggleCacheWidget(show: Boolean) { _uiState.update { it.copy(showCacheWidget = show) } }
    fun toggleFpsWidget(show: Boolean) { _uiState.update { it.copy(showFpsWidget = show) } }
    fun updateShowCacheWidget(show: Boolean) = _uiState.update { it.copy(showCacheWidget = show) }
    fun updateShowFpsWidget(show: Boolean) = _uiState.update { it.copy(showFpsWidget = show) }

    fun zoomIn()  = _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it }
    fun zoomOut() = _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it }

    override fun onCleared() { super.onCleared(); tileCache.closeAll() }
}