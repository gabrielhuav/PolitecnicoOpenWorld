package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.LandmarkAssetTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ovh.gabrielhuav.pow.domain.models.ShineCTOLocation


class WorldMapViewModel(
    internal val roadNetworkCache: RoadNetworkCache,
    val tileCache: TileCache,
    internal val settingsRepository: SettingsRepository,
    internal val collectibleRepository: CollectibleRepository
) : ViewModel() {

    var playerHealth by mutableStateOf(100f)
        internal set
    val maxPlayerHealth = 100f

    var showHealthBar by mutableStateOf(false)
        internal set
    var damagePulseTrigger by mutableStateOf(0)
        internal set

    internal var healthBarJob: Job? = null
    internal var promptJob: Job? = null

    // ─── FLAG: tras el video, navegar al minijuego de zombis ──────────────────
    // En lugar de entrar a un interior concreto, la mano lleva al minijuego.
    var pendingZombieMinigame: Boolean = false
        internal set

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
                settingsRepository = SettingsRepository(appCtx),
                collectibleRepository = CollectibleRepository(database.collectibleDao())
            ) as T
        }
    }

    internal val _uiState = MutableStateFlow(
        WorldMapState(
            controlType   = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls  = settingsRepository.getSwapControls(),
            selectedSkin  = settingsRepository.getPlayerSkin()    // ← NUEVO
        )
    )
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    internal val npcAiManager      = NpcAiManager()
    internal val overpassRepository = OverpassRepository()
    internal var roadNetwork: List<MapWay> = emptyList()

    // ─── Red de calles expuesta a la UI ──────────────────────────────────────
    // La WorldMapScreen consume este Flow para pintar las Polylines de los
    // caminos transitables ENCIMA de cualquier landmark del Modo Diseñador.
    internal val _roadNetworkFlow = MutableStateFlow<List<MapWay>>(emptyList())
    val roadNetworkFlow: StateFlow<List<MapWay>> = _roadNetworkFlow.asStateFlow()

    internal var roadNetworkNodeGrid: Map<Pair<Int, Int>, List<GeoPoint>> = emptyMap()
    internal var routeCalculationJob: Job? = null
    internal var routeRetryJob: Job? = null
    internal var lastNetworkFetchLocation: GeoPoint? = null
    internal var gameLoopJob: Job? = null
    internal var tickCount = 0
    internal val isFetchingNetwork  = AtomicBoolean(false)
    internal var lastFetchAttemptMs = 0L

    internal val REFETCH_DISTANCE_DEG = 0.015
    internal val REFETCH_COOLDOWN_MS  = 5 * 60 * 1000L
    internal val ROAD_NODE_GRID_SIZE_DEG = 0.001

    internal var lastVisibleRoadUpdateLocation: GeoPoint? = null
    internal val VISIBLE_ROAD_UPDATE_THRESHOLD = 0.002
    internal val VISIBLE_ROAD_RADIUS = 0.006

    var isSteeringLeftPressed = false
    var isSteeringRightPressed = false
    var isGasPressed = false
    var isBrakePressed = false

    internal val MAX_SPEED = 0.000017
    internal val ACCELERATION = 0.0000003
    internal val BRAKING_FRICTION = 0.000001
    internal val INTERACT_RADIUS = 0.0005

    internal val PLAYER_PUNCH_DAMAGE = 15f
    internal var lastAttackTime = 0L
    internal val ATTACK_COOLDOWN_MS = 2400L
    internal val ATTACK_RADIUS = 0.00015

    internal val hospitalRespawnPoints = listOf(
        GeoPoint(19.5034, -99.1469),
        GeoPoint(19.4990, -99.1350),
        GeoPoint(19.5070, -99.1400)
    )

    internal val ESCOM_BASE_LAT = 19.50456
    internal val ESCOM_BASE_LON = -99.14674
    internal val ESCOM_OFFSET = 0.001

    internal val ESCOM_DOOR_ASSET = "DOORS/ESCOM_DOOR.webp"
    internal val ESCOM_DOOR_INTERACT_RADIUS = 0.00020   // ~20 m

    internal val _escomItems = MutableStateFlow<List<ActiveCollectible>>(emptyList())
    val escomItems: StateFlow<List<ActiveCollectible>> = _escomItems.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
        startGameLoop()
    }

// ─── WEBSOCKET MULTIJUGADOR ───────────────────────────────────────────────────

    internal var webSocketManager: WebSocketManager? = null
    internal var messagesCollectorJob: Job? = null
    internal val gson = Gson()
    internal var myPlayerUUID = "Player_${UUID.randomUUID()}"
    internal var myPlayerDisplayName = ""
    internal val remoteEntities = ConcurrentHashMap<String, Npc>()

    fun connectToMultiplayer(serverUrl: String, playerName: String) {
        myPlayerDisplayName = playerName
        _uiState.update { it.copy(isMultiplayer = true, playerName = playerName) }
        if (webSocketManager == null) {
            Log.d("WorldMapVM", "Iniciando conexión multijugador a $serverUrl")
            webSocketManager = WebSocketManager(serverUrl)
            messagesCollectorJob?.cancel()
            messagesCollectorJob = viewModelScope.launch(Dispatchers.IO) {
                webSocketManager?.messagesFlow?.collect { messageJson ->
                    handleMultiplayerMessage(messageJson)
                }
            }
        }
        if (webSocketManager?.isConnected() == false) {
            webSocketManager?.connect()
        }
    }

    fun disconnectFromMultiplayer() {
        _uiState.update { it.copy(isMultiplayer = false, playerName = "") }
        webSocketManager?.disconnect()
        webSocketManager = null
        messagesCollectorJob?.cancel()
        messagesCollectorJob = null
        remoteEntities.clear()
        updateNpcsState()
    }

    internal var isServerDelegatedHost = true



    // ─── GAME LOOP ───────────────────────────────────────────────────────────────


    fun stopGameLoop() { gameLoopJob?.cancel(); gameLoopJob = null }




    fun notifyTileSource(fromCache: Boolean) {
        if (_uiState.value.mapProvider == MapProvider.OSM) return
        val source = if (fromCache) TileSource.LOCAL_CACHE else TileSource.NETWORK
        if (_uiState.value.tileSource != source) {
            _uiState.update { it.copy(tileSource = source) }
        }
    }

    fun moveCharacter(direction: Direction) {
        if (_uiState.value.isUserPanningMap) return
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        val isMovingRight = when (direction) {
            Direction.RIGHT -> true
            Direction.LEFT -> false
            else -> null
        }
        startMovementAction(isMovingRight)

        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003

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

    fun moveCharacterByAngle(angleRad: Double) {
        if (_uiState.value.isUserPanningMap) return
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return

        val dx = cos(angleRad)
        val isMovingRight = if (abs(dx) > 0.01) dx > 0 else null
        startMovementAction(isMovingRight)

        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003

        val temp = GeoPoint(
            loc.latitude + sin(angleRad) * step,
            loc.longitude + cos(angleRad) * step
        )

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

    internal data class Seg(val s: GeoPoint, val e: GeoPoint,
                           val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)

    internal val CELL = 0.0025
    internal var indexedRef: List<MapWay>?    = null
    internal var segs: List<Seg>              = emptyList()
    internal var grid: Map<Long, List<Seg>>   = emptyMap()



    internal fun pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
    internal fun cell(v: Double): Int = floor(v / CELL).toInt()



    internal fun distance(a: GeoPoint, b: GeoPoint): Double =
        sqrt((a.latitude - b.latitude).pow(2) + (a.longitude - b.longitude).pow(2))

    fun updateInitialLocation(lat: Double, lon: Double) {
        if (_uiState.value.isLoadingLocation)
            _uiState.update { it.copy(currentLocation = GeoPoint(lat, lon), isLoadingLocation = false) }
    }

    fun updateActionState(action: GameAction, isPressed: Boolean) {
        when (action) {
            GameAction.A -> {
                _uiState.update { it.copy(isRunning = isPressed) }
                val currentAction = _uiState.value.playerAction
                if (isPressed && currentAction == PlayerAction.WALK) {
                    _uiState.update { it.copy(playerAction = PlayerAction.RUN) }
                } else if (!isPressed && currentAction == PlayerAction.RUN) {
                    _uiState.update { it.copy(playerAction = PlayerAction.WALK) }
                }
            }
            GameAction.B -> {
                if (isPressed) {
                    _uiState.update { it.copy(playerAction = PlayerAction.SPECIAL) }
                    idleJob?.cancel()
                } else {
                    _uiState.update { it.copy(playerAction = PlayerAction.IDLE) }
                }
            }
            else -> {}
        }
    }

    fun setMapProvider(provider: MapProvider) {
        val ts = if (provider == MapProvider.OSM) TileSource.LOCAL_OSM else TileSource.NETWORK
        val currentZoom = _uiState.value.zoomLevel
        val newZoom = when {
            provider == MapProvider.OSM && currentZoom < ZOOM_GAMEPLAY_OSM -> ZOOM_GAMEPLAY_OSM
            provider.isWebProvider && currentZoom > ZOOM_GAMEPLAY_WEB -> ZOOM_GAMEPLAY_WEB
            else -> currentZoom
        }
        _uiState.update { it.copy(mapProvider = provider, tileSource = ts, zoomLevel = newZoom) }
    }

    // ─── CAMBIO DE PROVEEDOR CON PRECARGA + AVISO ─────────────────────────────
    private var providerPreloadJob: Job? = null

    /**
     * Solicita cambiar de proveedor SIN interrumpir el actual: lo precarga en
     * segundo plano. Cuando termina, 'pendingProviderReady' se pone en true y la
     * UI avisa para confirmar el cambio.
     */
    fun requestMapProvider(provider: MapProvider) {
        val st = _uiState.value
        if (provider == st.mapProvider) {
            // El destino ya es el activo: descartar cualquier pendiente.
            if (st.pendingProvider != null) {
                providerPreloadJob?.cancel()
                _uiState.update { it.copy(pendingProvider = null, pendingProviderReady = false) }
            }
            return
        }
        if (provider == st.pendingProvider) return // ya se está precargando

        providerPreloadJob?.cancel()
        _uiState.update { it.copy(pendingProvider = provider, pendingProviderReady = false) }
        providerPreloadJob = viewModelScope.launch {
            preloadProvider(provider)
            if (isActive && _uiState.value.pendingProvider == provider) {
                _uiState.update { it.copy(pendingProviderReady = true) }
            }
        }
    }

    /** Aplica el proveedor ya precargado (lo invoca el botón "Cambiar"). */
    fun commitMapProvider() {
        val p = _uiState.value.pendingProvider ?: return
        providerPreloadJob?.cancel()
        _uiState.update { it.copy(pendingProvider = null, pendingProviderReady = false) }
        setMapProvider(p)
    }

    /** Descarta el cambio pendiente y se queda con el proveedor actual. */
    fun cancelPendingProvider() {
        providerPreloadJob?.cancel()
        _uiState.update { it.copy(pendingProvider = null, pendingProviderReady = false) }
    }

    /** Calienta tiles/conexión del nuevo proveedor para que el cambio sea fluido. */
    private suspend fun preloadProvider(provider: MapProvider): Boolean = withContext(Dispatchers.IO) {
        if (!provider.isWebProvider) { delay(400); return@withContext true } // nativo/local
        val loc = _uiState.value.currentLocation ?: run { delay(400); return@withContext false }
        val z = ZOOM_GAMEPLAY_WEB.toInt()
        val n = 1 shl z
        val xCenter = ((loc.longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(loc.latitude)
        val yCenter = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n)
            .toInt().coerceIn(0, n - 1)
        var anyOk = false
        for (dx in -1..1) for (dy in -1..1) {
            if (!isActive) break
            val x = (xCenter + dx).coerceIn(0, n - 1)
            val y = (yCenter + dy).coerceIn(0, n - 1)
            tileUrlFor(provider, z, x, y)?.let { if (fetchTile(it)) anyOk = true }
        }
        anyOk
    }

    private fun tileUrlFor(provider: MapProvider, z: Int, x: Int, y: Int): String? {
        val template = when (provider) {
            MapProvider.CARTO_DB_DARK  -> "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
            MapProvider.CARTO_DB_LIGHT -> "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
            MapProvider.ESRI           -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
            MapProvider.ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
            MapProvider.OPEN_TOPO      -> "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
            MapProvider.OSM_WEB        -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
            MapProvider.GOOGLE_MAPS    -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
            else -> null
        } ?: return null
        return template.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
    }

    private fun fetchTile(url: String): Boolean = try {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 4000; readTimeout = 4000
            setRequestProperty("User-Agent", "PolitecnicoOpenWorld/1.0")
        }
        val code = conn.responseCode
        runCatching { conn.inputStream.use { it.readBytes() } } // descarga para cachear en CDN/SO
        conn.disconnect()
        code in 200..299
    } catch (e: Exception) { false }

    // ─── CARGA INICIAL DEL MAPA (gate de entrada con % de progreso) ───────────
    private var mapPrepStarted = false

    /**
     * Descarga el mapa alrededor del spawn ANTES de permitir entrar. Reporta
     * progreso (0f..1f) en mapLoadProgress y al terminar pone isMapReady=true.
     * Idempotente: solo corre una vez por sesión de pantalla.
     */
    fun prepareMapForEntry() {
        if (mapPrepStarted) return
        mapPrepStarted = true
        viewModelScope.launch {
            val provider = _uiState.value.mapProvider
            if (!provider.isWebProvider) {
                // OSMDroid offline / SDK nativo: tiles locales, progreso rápido.
                for (i in 1..10) {
                    _uiState.update { it.copy(mapLoadProgress = i / 10f) }
                    delay(70)
                }
                _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
                return@launch
            }
            downloadMapAround(provider)
            _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
        }
    }

    private suspend fun downloadMapAround(provider: MapProvider): Boolean = withContext(Dispatchers.IO) {
        val loc = _uiState.value.currentLocation
            ?: run { _uiState.update { it.copy(mapLoadProgress = 1f) }; return@withContext false }
        val z = ZOOM_GAMEPLAY_WEB.toInt()
        val n = 1 shl z
        val xC = ((loc.longitude + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(loc.latitude)
        val yC = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        val coords = ArrayList<Pair<Int, Int>>()
        for (dx in -1..1) for (dy in -2..2) coords.add((xC + dx) to (yC + dy)) // 3x5 alrededor
        val total = coords.size
        var done = 0
        var okAny = false
        for ((x, y) in coords) {
            if (!isActive) break
            val xx = x.coerceIn(0, n - 1); val yy = y.coerceIn(0, n - 1)
            tileUrlFor(provider, z, xx, yy)?.let { if (fetchTile(it)) okAny = true }
            done++
            _uiState.update { it.copy(mapLoadProgress = done.toFloat() / total) }
        }
        okAny
    }

    fun toggleCacheWidget(show: Boolean) { _uiState.update { it.copy(showCacheWidget = show) } }
    fun toggleFpsWidget(show: Boolean) { _uiState.update { it.copy(showFpsWidget = show) } }
    fun updateShowCacheWidget(show: Boolean) = _uiState.update { it.copy(showCacheWidget = show) }
    fun updateShowFpsWidget(show: Boolean) = _uiState.update { it.copy(showFpsWidget = show) }

    fun zoomIn()  = _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it }
    fun zoomOut() = _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it }

    fun centerOnPlayer() { _uiState.update { it.copy(isUserPanningMap = false) } }

    fun onMapPanStart() { _uiState.update { it.copy(isUserPanningMap = true) } }
    fun onMapPanEnd() { }

    override fun onCleared() {
        super.onCleared()
        stopGameLoop()
        routeCalculationJob?.cancel()
        routeRetryJob?.cancel()
        messagesCollectorJob?.cancel()
        healthBarJob?.cancel()
        promptJob?.cancel()
        idleJob?.cancel()
        tileCache.closeAll()
        webSocketManager?.disconnect()
    }

    internal var idleJob: Job? = null


    fun onInteractButtonPressed() {
        val loc = _uiState.value.currentLocation ?: return

        if (!_uiState.value.isDriving) {
            val nearbyCarEntry = remoteEntities.entries
                .filter { it.value.type == NpcType.CAR && distance(loc, it.value.location) <= INTERACT_RADIUS }
                .minByOrNull { distance(loc, it.value.location) }

            if (nearbyCarEntry != null) {
                val carId = nearbyCarEntry.key
                val carNpc = nearbyCarEntry.value
                remoteEntities.remove(carId)
                if (carNpc.isFirstTimeBoarded) { spawnOustedDriver(carNpc.location) }
                _uiState.update { it.copy(isDriving = true, currentVehicleModel = carNpc.carModel, currentVehicleColor = carNpc.carColor, vehicleRotation = (carNpc.rotationAngle + 90f) % 360f, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = false) }
                updateNpcsState()
            }
        } else {
            val abandonedCar = Npc(
                id = UUID.randomUUID().toString(),
                type = NpcType.CAR,
                location = loc,
                rotationAngle = (_uiState.value.vehicleRotation + 270f) % 360f,
                speed = 0.0,
                isMoving = false,
                carModel = _uiState.value.currentVehicleModel ?: CarModel.SEDAN,
                carColor = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
                isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded
            )
            remoteEntities[abandonedCar.id] = abandonedCar
            _uiState.update { it.copy(isDriving = false, currentVehicleModel = null, currentVehicleColor = null, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = true) }
            updateNpcsState()
        }
    }

    fun loadLandmarks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                LandmarkCatalogManager.loadCatalog(context)
                val database = ovh.gabrielhuav.pow.data.local.room.PowDatabase.getInstance(context)
                val dao = database.landmarkDao()
                var entities = dao.getAllLandmarks()
                if (entities.isEmpty()) {
                    try {
                        val jsonString = context.assets.open("default_landmarks.json").bufferedReader().use { it.readText() }
                        val type = object : TypeToken<List<LandmarkEntity>>() {}.type
                        val defaultEntities: List<LandmarkEntity> = Gson().fromJson(jsonString, type)
                        dao.insertLandmarks(defaultEntities)
                        entities = dao.getAllLandmarks()
                        Log.d("WorldMapViewModel", "Mapa sembrado con éxito desde default_landmarks.json con ${entities.size} edificios.")
                    } catch (e: java.io.FileNotFoundException) {
                        Log.w("WorldMapViewModel", "Archivo default_landmarks.json no encontrado.")
                    } catch (e: Exception) {
                        Log.e("WorldMapViewModel", "Error leyendo default_landmarks.json", e)
                    }
                }
                // Backfill: inserta el landmark de Shine si la BD ya estaba sembrada sin él
                if (entities.none { it.assetPath == "BUILDINGS/BAR/Shine.webp" }) {
                    dao.insertLandmark(
                        LandmarkEntity(
                            name = "Shine CTO",
                            latitude = 19.459038634489882,
                            longitude = -99.1633282698258,
                            assetPath = "BUILDINGS/BAR/Shine.webp",
                            scaleFactor = 0.50f,
                            rotationAngle = 285f
                        )
                    )
                    entities = dao.getAllLandmarks()
                }
                val templatesByAssetPath = LandmarkCatalogManager.availableAssets.associateBy { it.assetPath }
                val domainLandmarks = entities.map { entity ->

                val template = templatesByAssetPath[entity.assetPath]
                    Landmark(
                        id = entity.id,
                        name = entity.name,
                        location = GeoPoint(entity.latitude, entity.longitude),
                        assetPath = entity.assetPath,
                        scaleFactor = entity.scaleFactor,
                        rotationAngle = entity.rotationAngle,
                        baseWidthMeters = template?.baseWidthMeters ?: 100f,
                        baseHeightMeters = template?.baseHeightMeters ?: 100f
                    )
                }
                _uiState.update { currentState -> currentState.copy(landmarks = domainLandmarks) }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error fatal al cargar las estructuras estáticas", e)
            }
        }
    }

    fun toggleDesignerMode(isDesigner: Boolean) { _uiState.update { it.copy(isDesignerMode = isDesigner, selectedLandmarkId = if (!isDesigner) null else it.selectedLandmarkId) } }
    fun showAssetPicker(show: Boolean) { _uiState.update { it.copy(showAssetPicker = show) } }
    fun selectLandmark(id: Long?) { _uiState.update { it.copy(selectedLandmarkId = id) } }

    fun addLandmarkAtPlayer(context: Context, template: LandmarkAssetTemplate) {
        val playerLoc = _uiState.value.currentLocation ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PowDatabase.getInstance(context).landmarkDao()
                val newEntity = LandmarkEntity(
                    name = template.displayName,
                    latitude = playerLoc.latitude,
                    longitude = playerLoc.longitude,
                    assetPath = template.assetPath,
                    scaleFactor = template.defaultScale,
                    rotationAngle = 0f
                )
                val newId = dao.insertLandmark(newEntity)
                loadLandmarks(context)
                _uiState.update { it.copy(showAssetPicker = false, selectedLandmarkId = newId) }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al agregar landmark", e)
            }
        }
    }

    fun moveSelectedLandmark(dLat: Double, dLon: Double) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            val updated = state.landmarks.map {
                if (it.id == id) {
                    it.copy(location = GeoPoint(it.location.latitude + dLat, it.location.longitude + dLon))
                } else it
            }
            state.copy(landmarks = updated)
        }
    }

    fun rotateSelectedLandmark(angle: Float) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            val updated = state.landmarks.map {
                if (it.id == id) it.copy(rotationAngle = angle)
                else it
            }
            state.copy(landmarks = updated)
        }
    }

    fun scaleSelectedLandmark(scale: Float) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            val updated = state.landmarks.map {
                if (it.id == id) it.copy(scaleFactor = scale)
                else it
            }
            state.copy(landmarks = updated)
        }
    }

    fun deleteSelectedLandmark(context: Context) {
        val id = _uiState.value.selectedLandmarkId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PowDatabase.getInstance(context).landmarkDao()
                val entity = dao.getLandmarkById(id)
                if (entity != null) {
                    dao.deleteLandmark(entity)
                    loadLandmarks(context)
                    _uiState.update { it.copy(selectedLandmarkId = null) }
                }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al borrar landmark", e)
            }
        }
    }

    fun exportLandmarksToUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PowDatabase.getInstance(context).landmarkDao()
                val entities = dao.getAllLandmarks()
                val jsonString = Gson().toJson(entities)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al guardar JSON en archivo", e)
            }
        }
    }

    fun importLandmarksFromUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader().use { it?.readText() } ?: return@launch
                val type = object : com.google.gson.reflect.TypeToken<List<LandmarkEntity>>() {}.type
                val importedEntities: List<LandmarkEntity> = Gson().fromJson(jsonString, type)
                val dao = PowDatabase.getInstance(context).landmarkDao()
                val currentLandmarks = dao.getAllLandmarks()
                currentLandmarks.forEach { dao.deleteLandmark(it) }
                dao.insertLandmarks(importedEntities)
                loadLandmarks(context)
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al importar JSON desde archivo", e)
            }
        }
    }

    fun saveSelectedLandmark(context: Context) {
        val id = _uiState.value.selectedLandmarkId ?: return
        val currentLandmark = _uiState.value.landmarks.find { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PowDatabase.getInstance(context).landmarkDao()
                val updatedEntity = LandmarkEntity(
                    id = currentLandmark.id,
                    name = currentLandmark.name,
                    latitude = currentLandmark.location.latitude,
                    longitude = currentLandmark.location.longitude,
                    assetPath = currentLandmark.assetPath,
                    scaleFactor = currentLandmark.scaleFactor,
                    rotationAngle = currentLandmark.rotationAngle
                )
                dao.updateLandmark(updatedEntity)
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al actualizar landmark", e)
            }
        }
    }


    fun toggleTeleportMenu(show: Boolean) { _uiState.update { it.copy(showTeleportMenu = show) } }

    fun teleportTo(lat: Double, lon: Double) {
        val newLocation = org.osmdroid.util.GeoPoint(lat, lon)
        _uiState.update {
            it.copy(
                currentLocation = newLocation,
                showTeleportMenu = false,
                isRoadNetworkReady = false
            )
        }
        lastNetworkFetchLocation = null
        lastFetchAttemptMs = 0L
    }

    fun steerLeft(pressed: Boolean) { isSteeringLeftPressed = pressed }
    fun steerRight(pressed: Boolean) { isSteeringRightPressed = pressed }
    fun accelerate(pressed: Boolean) { isGasPressed = pressed }
    fun brake(pressed: Boolean) { isBrakePressed = pressed }

    internal val isSpawningCollectible = AtomicBoolean(false)



    fun onClaimCollectiblePressed() {
        val itemToClaim = _uiState.value.nearbyCollectible ?: return

        if (itemToClaim.name == "Objeto Misterioso ESCOM" ||
            itemToClaim.id == ShineCTOLocation.MARKER_ID ||
            itemToClaim.id.startsWith("escom_door_")) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.claimCollectible(itemToClaim.id)
            withContext(Dispatchers.Main) {
                promptJob?.cancel()
                promptJob = null
                _uiState.update {
                    it.copy(
                        activeCollectibles = emptyList(),
                        nearbyCollectible = null,
                        interactionPrompt = null,
                        showClaimedPopupFor = itemToClaim
                    )
                }
            }
        }
    }

    fun dismissClaimedPopup() { _uiState.update { it.copy(showClaimedPopupFor = null) } }

    fun takeDamage(amount: Float) {
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
        if (playerHealth <= 0f) {
            triggerWastedSequence()
        }
    }

    fun heal(amount: Float) {
        playerHealth = (playerHealth + amount).coerceAtMost(maxPlayerHealth)
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
    }



    fun showInitialHealthBar() {
        showHealthBar = true
        startHealthBarTimer(4000L)
    }

    fun performPlayerAttack() {
        val now = System.currentTimeMillis()
        if (now - lastAttackTime < ATTACK_COOLDOWN_MS) return
        lastAttackTime = now
        viewModelScope.launch(Dispatchers.Default) {
            delay(300L)
            val playerLoc = _uiState.value.currentLocation ?: return@launch
            val targetNpcEntry = remoteEntities.entries
                .filter {
                    !it.value.isDying &&
                            it.value.type == NpcType.PERSON &&
                            distance(playerLoc, it.value.location) <= ATTACK_RADIUS
                }
                .minByOrNull { distance(playerLoc, it.value.location) }
            if (targetNpcEntry != null) {
                val npcId = targetNpcEntry.key
                val currentNpc = targetNpcEntry.value
                val isRemotePlayer = !currentNpc.displayName.isNullOrBlank()
                if (isRemotePlayer) {
                    try {
                        webSocketManager?.sendMessage(
                            gson.toJson(
                                mapOf(
                                    "type" to "PLAYER_DAMAGE",
                                    "targetId" to npcId,
                                    "damage" to PLAYER_PUNCH_DAMAGE
                                )
                            )
                        )
                    } catch (e: Exception) { Log.e("Combat", "Error enviando PLAYER_DAMAGE: ${e.message}") }
                } else {
                    val damage = PLAYER_PUNCH_DAMAGE
                    val newHealth = (currentNpc.health - damage).coerceAtLeast(0f)
                    if (newHealth <= 0f) {
                        remoteEntities[npcId] = currentNpc.copy(health = 0f, isDying = true)
                        updateNpcsState()
                        delay(1000L)
                        remoteEntities.remove(npcId)
                        try {
                            webSocketManager?.sendMessage(
                                gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to npcId))
                            )
                        } catch (e: Exception) { Log.e("Combat", "Error enviando NPC_DESTROY para npcId=$npcId", e) }
                        updateNpcsState()
                    } else {
                        remoteEntities[npcId] = currentNpc.copy(health = newHealth)
                        updateNpcsState()
                    }
                }
            }
        }
    }

    fun toggleWaypointTargeting(active: Boolean) { _uiState.update { it.copy(isTargetingWaypoint = active) } }

    fun placeDestinationMarker(latitude: Double, longitude: Double) {
        Log.d("Navigation", "Colocando waypoint en: $latitude, $longitude")
        routeRetryJob?.cancel()
        routeCalculationJob?.cancel()
        val newDestination = GeoPoint(latitude, longitude)
        _uiState.update { it.copy(destinationMarker = newDestination, isTargetingWaypoint = false, routeWaypoints = emptyList()) }
        updateDestinationRoute()
    }

    fun clearDestinationMarker() {
        routeRetryJob?.cancel()
        routeCalculationJob?.cancel()
        _uiState.update { it.copy(destinationMarker = null, isTargetingWaypoint = false, routeWaypoints = emptyList()) }
    }

    fun toggleDestinationRoute(show: Boolean) { _uiState.update { it.copy(showDestinationRoute = show) } }





    /**
     * Spawnea UNA SOLA ZombiHand cerca del jugador (o en el centro de ESCOM).
     * Al interactuar con ella, lleva al minijuego de zombis (que arranca en el
     * lobby = croquis del campus). Antes spawneaba 6 manos, una por edificio.
     */
    /**
     * Spawnea UNA SOLA ZombiHand, pero SOLO si el jugador está dentro de ESCOM.
     * Si no está en ESCOM, no hace nada (y deja la lista vacía).
     */
    fun spawnEscomItems(roadNetwork: List<MapWay>, cantidad: Int = 1) {
        val center = _uiState.value.currentLocation ?: return

        // ── GUARDA CLAVE: nada de manos fuera de ESCOM ──
        if (!isInsideEscom(center.latitude, center.longitude)) {
            _escomItems.value = emptyList()
            _uiState.update { it.copy(isZombieHandSpawned = false) }
            return
        }

        // Evita duplicar si ya hay una mano spawneada
        if (_uiState.value.isZombieHandSpawned && _escomItems.value.isNotEmpty()) return

        // Mano zombi desactivada del exterior — el acceso al lobby
        // ahora se realiza únicamente por las puertas físicas (ESCOM_DOOR).
        _escomItems.value = emptyList()
        _uiState.update { it.copy(isZombieHandSpawned = true) }   // flag para no reintentar
        return
    }

    fun collectEscomItem() {
        val loc = _uiState.value.currentLocation ?: return
        val interactionRadius = 0.00015
        val itemToCollect = _escomItems.value.find {
            distance(loc, org.osmdroid.util.GeoPoint(it.latitude, it.longitude)) <= interactionRadius
        }

        if (itemToCollect != null) {
            _escomItems.update { currentList -> currentList.filter { it.id != itemToCollect.id } }
        }
    }

    /**
     * Interacción con la mano: en lugar de entrar a un interior concreto, marca
     * el flag pendingZombieMinigame para que, tras el video, WorldMapScreen
     * navegue a la ruta "zombie_minigame".
     */
    fun handleInteraction() {
        val nearby = _uiState.value.nearbyCollectible ?: return

        when {
            nearby.name == "Objeto Misterioso ESCOM" -> {
                pendingZombieMinigame = true
                _uiState.update {
                    it.copy(
                        showZombiVideo = true,
                        pendingInteriorDestination = InteriorBuilding.EDIFICIO
                    )
                }
            }
            nearby.id.startsWith("escom_door_") -> {
                _uiState.update { it.copy(showEscomDoorFade = true) }
            }
            nearby.id == ShineCTOLocation.MARKER_ID -> {
                _uiState.update { it.copy(showShineCTODiscovery = true) }
            }
            else -> onClaimCollectiblePressed()
        }
    }

    fun dismissVideo() {
        _uiState.update { it.copy(showZombiVideo = false) }
        // pendingInteriorDestination queda intacto: WorldMapScreen lo observará
        // y disparará la navegación. La pantalla lo limpiará con
        // clearPendingInteriorDestination() después de navegar.
    }

    fun clearPendingInteriorDestination() {
        _uiState.update { it.copy(pendingInteriorDestination = null) }
    }

    /** Limpia el flag tras navegar al minijuego de zombis. */
    fun clearPendingZombieMinigame() { pendingZombieMinigame = false }

    fun toggleInteriorDebugOverlay(show: Boolean) {
        _uiState.update { it.copy(showInteriorDebugOverlay = show) }
    }

    fun teleportToLocation(newLat: Double, newLon: Double) {
        val insideEscom = isInsideEscom(newLat, newLon)

        _uiState.update { currentState ->
            currentState.copy(
                currentLocation = GeoPoint(newLat, newLon),
                showTeleportMenu = false,
                isRoadNetworkReady = false,
                isZombieHandSpawned = if (!insideEscom) false else currentState.isZombieHandSpawned
            )
        }

        lastNetworkFetchLocation = null
        lastFetchAttemptMs = 0L
    }


    fun setShowRoadNetwork(show: Boolean) {
        _uiState.update { it.copy(showRoadNetwork = show) }
        if (!show) {
            _roadNetworkFlow.value = emptyList()
        } else {
            _uiState.value.currentLocation?.let { loc ->
                updateVisibleRoads(loc, force = true)
            }
        }
    }

    fun checkDestinationArrival() {
        val destination = _uiState.value.destinationMarker ?: return
        val currentLoc = _uiState.value.currentLocation ?: return
        val distToDestinationMeters = currentLoc.distanceToAsDouble(destination)
        if (distToDestinationMeters <= _uiState.value.destinationArrivalThreshold) clearDestinationMarker()
    }
    // ─── Selector de skin ────────────────────────────────────────────────

    fun toggleSkinSelector(show: Boolean) {
        _uiState.update { it.copy(showSkinSelector = show) }
    }

    fun selectSkin(skin: ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin) {
        settingsRepository.savePlayerSkin(skin)
        _uiState.update { it.copy(selectedSkin = skin, showSkinSelector = false) }
    }

    // ─── ShineCTO Easter Egg ────────────────────────────────────────────────

    fun spawnShineCTOMarker() {
        if (_uiState.value.activeCollectibles.none { it.id == ShineCTOLocation.MARKER_ID }) {
            val marker = ActiveCollectible(
                id          = ShineCTOLocation.MARKER_ID,
                name        = ShineCTOLocation.MARKER_NAME,
                description = "easter_egg",
                assetPath   = "LUGARES/shineCTO/s_logo.webp",
                latitude    = ShineCTOLocation.LAT,
                longitude   = ShineCTOLocation.LON
            )
            _uiState.update { it.copy(activeCollectibles = it.activeCollectibles + marker) }
        }
    }

    fun onShineCTODiscoveryConfirmed() {
        // El marker es persistente: NO se elimina de activeCollectibles.
        _uiState.update { s ->
            s.copy(
                showShineCTODiscovery = false,
                navigateToShineCTO   = true,
                nearbyCollectible    = null,
                interactionPrompt    = null
            )
        }
    }

    fun consumeNavigateToShineCTO() {
        _uiState.update { it.copy(navigateToShineCTO = false) }
    }

    fun dismissShineCTODiscovery() {
        _uiState.update { it.copy(showShineCTODiscovery = false) }
    }
    fun onEscomDoorFadeComplete() {
        _uiState.update {
            it.copy(
                showEscomDoorFade    = false,
                escomDoorFadeComplete = true,
                nearbyCollectible    = null,
                interactionPrompt    = null
            )
        }
    }

    fun consumeEscomDoorNavigation() {
        _uiState.update { it.copy(escomDoorFadeComplete = false) }
    }
}

