package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.*
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

// --- ENUMS Y DATA CLASSES DE APOYO ---
enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

data class ServerMessage(
    val type: String,
    val sessionId: String? = null,
    val isZoneHost: Boolean? = null,
    val npc: MultiplayerNpc? = null,
    val npcs: List<MultiplayerNpc>? = null,
    val npcId: String? = null,
    val activeNpcIds: List<String>? = null,
    val orphanedNpcs: List<String>? = null,
    val id: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val action: String? = null,
    val facingRight: Boolean? = null,
    val isDriving: Boolean? = null,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null,
    val displayName: String? = null
)

data class MultiplayerNpc(
    val id: String,
    val x: Double,
    val y: Double,
    val rotation: Float,
    val npcType: String,
    val ownerId: String,
    val carModel: String? = null,
    val carColor: Int? = null,
    val hairId: Int? = null,
    val hairColor: Int? = null,
    val shirtColor: Int? = null,
    val pantsColor: Int? = null
)

data class MultiplayerPlayer(
    val id: String,
    val displayName: String,
    val x: Double,
    val y: Double,
    val action: String,
    val facingRight: Boolean,
    val isDriving: Boolean,
    val carModel: String?,
    val carColor: Int?,
    val vehicleRotation: Float
)

class WorldMapViewModel(
    private val roadNetworkCache: RoadNetworkCache,
    val tileCache: TileCache,
    private val settingsRepository: SettingsRepository,
    private val collectibleRepository: CollectibleRepository
) : ViewModel() {

    var playerHealth by mutableStateOf(100f)
        private set
    val maxPlayerHealth = 100f

    var showHealthBar by mutableStateOf(false)
        private set
    var damagePulseTrigger by mutableStateOf(0)
        private set

    private var healthBarJob: Job? = null

    // Variables de control de entrada (Vehículo)
    var isSteeringLeftPressed = false
    var isSteeringRightPressed = false
    var isGasPressed = false
    var isBrakePressed = false
    private var lastAttackTime = 0L

    // --- CACHES DE GRÁFICOS (Optimización de Memoria - Punto 1) ---
    val nativeDrawableCache = LruCache<String, android.graphics.drawable.Drawable>(200)
    val landmarkBitmapCache = LruCache<String, android.graphics.Bitmap>(50)
    val base64Cache = LruCache<String, String>(150)
    val widthCache = LruCache<String, Float>(150)
    val heightCache = LruCache<String, Float>(150)
    val registeredWebImages = Collections.synchronizedSet(mutableSetOf<String>())

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
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

    private val _uiState = MutableStateFlow(
        WorldMapState(
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
    private val isFetchingNetwork  = AtomicBoolean(false)
    private var lastFetchAttemptMs = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
        startGameLoop()
    }

    private var webSocketManager: WebSocketManager? = null
    private var messagesCollectorJob: Job? = null
    private val gson = Gson()
    private var myPlayerUUID = "Player_${UUID.randomUUID()}"
    private var myPlayerDisplayName = ""
    private val remoteEntities = ConcurrentHashMap<String, Npc>()

    fun connectToMultiplayer(serverUrl: String, playerName: String) {
        myPlayerDisplayName = playerName
        if (webSocketManager == null) {
            webSocketManager = WebSocketManager(serverUrl)
            messagesCollectorJob?.cancel()
            messagesCollectorJob = viewModelScope.launch(Dispatchers.IO) {
                webSocketManager?.messagesFlow?.collect { handleMultiplayerMessage(it) }
            }
        }
        if (webSocketManager?.isConnected() == false) webSocketManager?.connect()
    }

    fun disconnectFromMultiplayer() {
        webSocketManager?.disconnect(); webSocketManager = null
        messagesCollectorJob?.cancel(); messagesCollectorJob = null
        remoteEntities.clear(); updateNpcsState()
    }

    private var isServerDelegatedHost = true
    private fun handleMultiplayerMessage(messageJson: String) {
        try {
            val msg = gson.fromJson(messageJson, ServerMessage::class.java)
            when (msg.type) {
                "SESSION_INIT" -> msg.sessionId?.let { myPlayerUUID = it }
                "SYNC_ALL_NPCS" -> {
                    msg.npcs?.forEach { if (it.ownerId != myPlayerUUID) addRemoteEntity(it) }
                    updateNpcsState()
                }
                "ROLE_UPDATE" -> msg.isZoneHost?.let { isServerDelegatedHost = it }
                "NPC_SPAWN", "NPC_UPDATE" -> msg.npc?.let { if (it.ownerId != myPlayerUUID) { addRemoteEntity(it); updateNpcsState() } }
                "NPC_BATCH_UPDATE" -> {
                    msg.npcs?.forEach { if (it.ownerId != myPlayerUUID) addRemoteEntity(it) }
                    updateNpcsState()
                }
                "NPC_DESTROY" -> msg.npcId?.let { remoteEntities.remove(it); updateNpcsState() }
                "DISCONNECT" -> {
                    msg.id?.let { remoteEntities.remove(it) }
                    msg.orphanedNpcs?.forEach { remoteEntities.remove(it) }
                    updateNpcsState()
                }
                "MASTER_SYNC_CHECK" -> {
                    msg.activeNpcIds?.let { officialIds ->
                        val officialSet = officialIds.toSet()
                        var stateChanged = false
                        val it = remoteEntities.iterator()
                        while (it.hasNext()) {
                            val entry = it.next()
                            if (entry.value.displayName.isNullOrEmpty() && !officialSet.contains(entry.key)) {
                                it.remove(); stateChanged = true
                            }
                        }
                        if (stateChanged) updateNpcsState()
                    }
                }
                else -> {
                    if (msg.id != null && msg.id != myPlayerUUID && msg.x != null && msg.y != null) {
                        val multiplayerConfig = CharacterVisualConfig(
                            bodyFolder = "otherPlayer", bodyPrefix = "p_mult_", hairId = 1,
                            hairColor = androidx.compose.ui.graphics.Color.White, shirtColor = androidx.compose.ui.graphics.Color.Cyan, pantsColor = androidx.compose.ui.graphics.Color.DarkGray
                        )
                        val isRemoteDriving = msg.isDriving == true
                        val otherPlayer = Npc(
                            id = msg.id!!, type = if (isRemoteDriving) NpcType.CAR else NpcType.PERSON, location = GeoPoint(msg.y!!, msg.x!!),
                            rotationAngle = if (isRemoteDriving) ((msg.vehicleRotation ?: 0f) + 270f) % 360f else 0f, speed = 0.0,
                            isRemote = true, isMoving = (msg.action == "WALK" || msg.action == "RUN") || isRemoteDriving, facingRight = msg.facingRight == true,
                            carModel = try { msg.carModel?.let { CarModel.valueOf(it) } ?: CarModel.SEDAN } catch(e: Exception) { CarModel.SEDAN },
                            carColor = msg.carColor ?: 0xFFFFFFFF.toInt(), visualConfig = if (!isRemoteDriving) multiplayerConfig else null, displayName = msg.displayName
                        )
                        remoteEntities[msg.id!!] = otherPlayer; updateNpcsState()
                    }
                }
            }
        } catch (e: Exception) { Log.e("WorldMapVM", "Error JSON: ${e.message}") }
    }

    private fun addRemoteEntity(remote: MultiplayerNpc) {
        val npcType = try { NpcType.valueOf(remote.npcType) } catch(e: Exception) { NpcType.PERSON }
        val visualConfig = if (npcType == NpcType.PERSON) {
            CharacterVisualConfig(
                bodyFolder = "npc_walk_1", bodyPrefix = "npc_walk_1_", hairId = remote.hairId ?: 1,
                hairColor  = remote.hairColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.White,
                shirtColor = remote.shirtColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.LightGray,
                pantsColor = remote.pantsColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.DarkGray
            )
        } else null
        remoteEntities[remote.id] = Npc(
            id = remote.id, type = npcType, location = GeoPoint(remote.y, remote.x), rotationAngle = remote.rotation,
            speed = if (npcType == NpcType.CAR) NpcAiManager.CAR_SPEED else NpcAiManager.PERSON_SPEED, isRemote = true, isMoving = npcType == NpcType.PERSON,
            facingRight = cos(Math.toRadians(remote.rotation.toDouble())) >= 0, ownerId = remote.ownerId,
            carModel = try { remote.carModel?.let { CarModel.valueOf(it) } ?: CarModel.SEDAN } catch (e: Exception) { CarModel.SEDAN },
            carColor = remote.carColor ?: 0xFFFFFFFF.toInt(), visualConfig = visualConfig
        )
    }

    private fun updateNpcsState() { _uiState.update { it.copy(npcs = remoteEntities.values.toList()) } }

    private fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            while (_uiState.value.currentLocation == null) delay(100)
            val initialLoc = _uiState.value.currentLocation!!
            if (_uiState.value.mapProvider == MapProvider.OSM) _uiState.update { it.copy(tileSource = TileSource.LOCAL_OSM) }

            val cached = roadNetworkCache.get(initialLoc.latitude, initialLoc.longitude)
            if (cached != null) {
                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                applyRoadNetwork(cached, initialLoc)
                lastNetworkFetchLocation = initialLoc
            } else {
                _uiState.update { it.copy(roadSource = RoadSource.LOADING) }
                var retryMs = 1_000L
                while (isActive && roadNetwork.isEmpty()) {
                    val network = overpassRepository.fetchRoadNetwork(initialLoc.latitude, initialLoc.longitude)
                    if (network.isNotEmpty()) {
                        _uiState.update { it.copy(roadSource = RoadSource.NETWORK) }
                        applyRoadNetwork(network, initialLoc)
                        lastNetworkFetchLocation = initialLoc
                        launch(Dispatchers.IO) {
                            roadNetworkCache.put(initialLoc.latitude, initialLoc.longitude, network)
                            withContext(Dispatchers.Main) { _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) } }
                        }
                        break
                    } else {
                        _uiState.update { it.copy(isRoadNetworkReady = false) }
                        delay(retryMs); retryMs = (retryMs * 2).coerceAtMost(30_000L)
                    }
                }
            }

            var localTick = 0L
            while (isActive) {
                try {
                    _uiState.value.currentLocation?.let { location ->
                        if (_uiState.value.isRoadNetworkReady && roadNetwork.isNotEmpty() && localTick % 30 == 0L) trySpawningCollectible(location.latitude, location.longitude)
                        checkCollectibleProximity(location.latitude, location.longitude)
                        if (_uiState.value.playerAction == PlayerAction.SPECIAL) performPlayerAttack()
                        if (_uiState.value.isDriving) {
                            var currentSpeed = _uiState.value.vehicleSpeed; var currentRotation = _uiState.value.vehicleRotation
                            if (isSteeringLeftPressed && currentSpeed != 0.0) currentRotation -= 2f
                            if (isSteeringRightPressed && currentSpeed != 0.0) currentRotation += 2f
                            if (isGasPressed) currentSpeed = (currentSpeed + 0.0000003).coerceAtMost(0.000017)
                            else if (isBrakePressed) { currentSpeed -= 0.000001; if (currentSpeed < -0.0000085) currentSpeed = -0.0000085 }
                            else { if (currentSpeed > 0) currentSpeed = (currentSpeed - 0.00000015).coerceAtLeast(0.0); if (currentSpeed < 0) currentSpeed = (currentSpeed + 0.00000015).coerceAtMost(0.0) }
                            val angleRad = Math.toRadians(currentRotation.toDouble())
                            val dy = cos(angleRad) * currentSpeed; val dx = sin(angleRad) * currentSpeed
                            val tempLoc = GeoPoint(location.latitude + dy, location.longitude + dx)
                            val nearest = getNearestPointOnNetwork(tempLoc); val dist = distance(tempLoc, nearest)
                            val finalLoc = if (dist <= 0.000025) tempLoc else {
                                val angleBack = atan2(tempLoc.latitude - nearest.latitude, tempLoc.longitude - nearest.longitude)
                                currentSpeed *= 0.8; GeoPoint(nearest.latitude + sin(angleBack) * 0.000025, nearest.longitude + cos(angleBack) * 0.000025)
                            }
                            _uiState.update { it.copy(currentLocation = finalLoc, vehicleSpeed = currentSpeed, vehicleRotation = (currentRotation + 360) % 360f) }
                        }
                        maybeRefetchRoadNetwork(location)
                        if (_uiState.value.isRoadNetworkReady) {
                            localTick++
                            if (localTick % 3 == 0L) {
                                val npcOnlyList = remoteEntities.values.filter { it.displayName.isNullOrEmpty() }
                                npcAiManager.setServerNpcs(npcOnlyList); npcAiManager.updateNpcs(location, isServerDelegatedHost)
                                val processedNpcs = npcAiManager.getServerNpcs()
                                if (isServerDelegatedHost) {
                                    synchronized(npcAiManager.pendingDespawns) { npcAiManager.pendingDespawns.forEach { remoteEntities.remove(it) } }
                                    processedNpcs.forEach { remoteEntities[it.id] = it }
                                }
                                updateNpcsState()
                                webSocketManager?.let { ws ->
                                    launch(Dispatchers.IO) {
                                        try {
                                            val myData = MultiplayerPlayer(myPlayerUUID, myPlayerDisplayName, location.longitude, location.latitude, _uiState.value.playerAction.name, _uiState.value.isPlayerFacingRight, _uiState.value.isDriving, _uiState.value.currentVehicleModel?.name, _uiState.value.currentVehicleColor, _uiState.value.vehicleRotation)
                                            ws.sendMessage(gson.toJson(myData))
                                            if (isServerDelegatedHost) {
                                                val despawns = synchronized(npcAiManager.pendingDespawns) { val list = npcAiManager.pendingDespawns.toList(); npcAiManager.pendingDespawns.clear(); list }
                                                despawns.forEach { ws.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to it))) }
                                                if (processedNpcs.isNotEmpty()) {
                                                    val npcBatch = processedNpcs.map { MultiplayerNpc(it.id, it.location.longitude, it.location.latitude, it.rotationAngle, it.type.name, myPlayerUUID, it.carModel.name, it.carColor, it.visualConfig?.hairId, it.visualConfig?.hairColor?.toArgb(), it.visualConfig?.shirtColor?.toArgb(), it.visualConfig?.pantsColor?.toArgb()) }
                                                    ws.sendMessage(gson.toJson(mapOf("type" to "NPC_BATCH_UPDATE", "npcs" to npcBatch)))
                                                }
                                            } else synchronized(npcAiManager.pendingDespawns) { npcAiManager.pendingDespawns.clear() }
                                        } catch (e: Exception) { Log.e("Network", "Error: ${e.message}") }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { Log.e("GameLoop", "Error: ${e.message}") }
                delay(33)
            }
        }
    }

    fun stopGameLoop() { gameLoopJob?.cancel(); gameLoopJob = null }

    private suspend fun applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network; npcAiManager.updateRoadNetwork(network)
        val snapped = withContext(Dispatchers.Default) { getNearestPointOnNetwork(playerLocation) }
        withContext(Dispatchers.Main) { _uiState.update { it.copy(currentLocation = snapped, isRoadNetworkReady = true) } }
        if (_uiState.value.zoomLevel <= 14.0) {
            var z = 15.0; val target = if (_uiState.value.mapProvider.isWebProvider) 17.5 else 18.5
            while (z <= target) { delay(120); withContext(Dispatchers.Main) { _uiState.update { it.copy(zoomLevel = z) } }; z += 1.0 }
        }
    }

    private fun maybeRefetchRoadNetwork(currentLoc: GeoPoint) {
        val moved = if (lastNetworkFetchLocation != null) distance(lastNetworkFetchLocation!!, currentLoc) else Double.MAX_VALUE
        if (moved < 0.015) return
        if (System.currentTimeMillis() - lastFetchAttemptMs < 300000L || !isFetchingNetwork.compareAndSet(false, true)) return
        lastFetchAttemptMs = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = roadNetworkCache.get(currentLoc.latitude, currentLoc.longitude)
                if (cached != null) {
                    withContext(Dispatchers.Main) { roadNetwork = cached; npcAiManager.updateRoadNetwork(cached); lastNetworkFetchLocation = currentLoc; _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) } }
                } else {
                    withContext(Dispatchers.Main) { _uiState.update { it.copy(roadSource = RoadSource.NETWORK) } }
                    val network = overpassRepository.fetchRoadNetwork(currentLoc.latitude, currentLoc.longitude)
                    if (network.isNotEmpty()) {
                        roadNetworkCache.put(currentLoc.latitude, currentLoc.longitude, network)
                        withContext(Dispatchers.Main) { roadNetwork = network; npcAiManager.updateRoadNetwork(network); lastNetworkFetchLocation = currentLoc; _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) } }
                    }
                }
            } finally { isFetchingNetwork.set(false) }
        }
    }

    fun notifyTileSource(fromCache: Boolean) {
        if (_uiState.value.mapProvider == MapProvider.OSM) return
        val source = if (fromCache) TileSource.LOCAL_CACHE else TileSource.NETWORK
        if (_uiState.value.tileSource != source) _uiState.update { it.copy(tileSource = source) }
    }

    fun moveCharacter(direction: Direction) {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        startMovementAction(when (direction) { Direction.RIGHT -> true; Direction.LEFT -> false; else -> null })
        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003
        val temp = when (direction) { Direction.UP -> GeoPoint(loc.latitude + step, loc.longitude); Direction.DOWN -> GeoPoint(loc.latitude - step, loc.longitude); Direction.LEFT -> GeoPoint(loc.latitude, loc.longitude - step); Direction.RIGHT -> GeoPoint(loc.latitude, loc.longitude + step) }
        val nearest = getNearestPointOnNetwork(temp); val dist = distance(temp, nearest)
        if (dist <= 0.000012) _uiState.update { it.copy(currentLocation = temp) }
        else { val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude); _uiState.update { it.copy(currentLocation = GeoPoint(nearest.latitude + sin(angle) * 0.000012, nearest.longitude + cos(angle) * 0.000012)) } }
    }

    fun moveCharacterByAngle(angleRad: Double) {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        val dx = cos(angleRad); startMovementAction(if (abs(dx) > 0.01) dx > 0 else null)
        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003
        val temp = GeoPoint(loc.latitude + sin(angleRad) * step, loc.longitude + cos(angleRad) * step)
        val nearest = getNearestPointOnNetwork(temp); val dist = distance(temp, nearest)
        if (dist <= 0.000012) _uiState.update { it.copy(currentLocation = temp) }
        else { val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude); _uiState.update { it.copy(currentLocation = GeoPoint(nearest.latitude + sin(angle) * 0.000012, nearest.longitude + cos(angle) * 0.000012)) } }
    }

    fun updateControlSettings(type: ControlType, scale: Float, swap: Boolean) { _uiState.update { it.copy(controlType = type, controlsScale = scale, swapControls = swap) } }

    private data class Seg(val s: GeoPoint, val e: GeoPoint, val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)
    private var indexedRef: List<MapWay>? = null; private var segs: List<Seg> = emptyList(); private var grid: Map<Long, List<Seg>> = emptyMap()
    private fun ensureIndex() {
        if (indexedRef === roadNetwork) return
        val newSegs = ArrayList<Seg>(roadNetwork.sumOf { it.nodes.size }); val newGrid = HashMap<Long, MutableList<Seg>>()
        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val a = way.nodes[i]; val b = way.nodes[i + 1]; val seg = Seg(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon), min(a.lat, b.lat), max(a.lat, b.lat), min(a.lon, b.lon), max(a.lon, b.lon))
                newSegs.add(seg); for (r in floor(seg.minLat/0.0025).toInt()..floor(seg.maxLat/0.0025).toInt()) for (c in floor(seg.minLon/0.0025).toInt()..floor(seg.maxLon/0.0025).toInt()) newGrid.getOrPut(r.toLong() * 1000003L + c.toLong()) { mutableListOf() }.add(seg)
            }
        }
        indexedRef = roadNetwork; segs = newSegs; grid = newGrid
    }

    private fun getNearestPointOnNetwork(t: GeoPoint): GeoPoint {
        ensureIndex(); val r = floor(t.latitude/0.0025).toInt(); val c = floor(t.longitude/0.0025).toInt(); val res = LinkedHashSet<Seg>()
        for (dr in -1..1) for (dc in -1..1) grid[ (r + dr).toLong() * 1000003L + (c + dc).toLong() ]?.let { res.addAll(it) }
        val cands = if (res.isNotEmpty()) res.toList() else segs; if (cands.isEmpty()) return t
        var best = Double.MAX_VALUE; var pt = t
        for (seg in cands) {
            val l2 = (seg.e.latitude - seg.s.latitude).pow(2) + (seg.e.longitude - seg.s.longitude).pow(2)
            val proj = if (l2 == 0.0) seg.s else { val tt = max(0.0, min(1.0, ((t.latitude - seg.s.latitude) * (seg.e.latitude - seg.s.latitude) + (t.longitude - seg.s.longitude) * (seg.e.longitude - seg.s.longitude)) / l2)); GeoPoint(seg.s.latitude + tt * (seg.e.latitude - seg.s.latitude), seg.s.longitude + tt * (seg.e.longitude - seg.s.longitude)) }
            val d = distance(t, proj); if (d < best) { best = d; pt = proj }
        }
        return pt
    }

    private fun distance(a: GeoPoint, b: GeoPoint): Double = sqrt((a.latitude - b.latitude).pow(2) + (a.longitude - b.longitude).pow(2))
    fun updateInitialLocation(la: Double, lo: Double) { if (_uiState.value.isLoadingLocation) _uiState.update { it.copy(currentLocation = GeoPoint(la, lo), isLoadingLocation = false) } }
    fun updateActionState(a: GameAction, p: Boolean) {
        if (a == GameAction.A) { _uiState.update { it.copy(isRunning = p, playerAction = if (p) PlayerAction.RUN else PlayerAction.WALK) } }
        else if (a == GameAction.B) { if (p) { _uiState.update { it.copy(playerAction = PlayerAction.SPECIAL) }; idleJob?.cancel() } else _uiState.update { it.copy(playerAction = PlayerAction.IDLE) } }
    }

    fun setMapProvider(p: MapProvider) {
        val ts = if (p == MapProvider.OSM) TileSource.LOCAL_OSM else TileSource.NETWORK
        val curZ = _uiState.value.zoomLevel; val newZ = when { p == MapProvider.OSM && curZ < 18.5 -> 18.5; p.isWebProvider && curZ > 17.5 -> 17.5; else -> curZ }
        _uiState.update { it.copy(mapProvider = p, tileSource = ts, zoomLevel = newZ) }
    }

    fun toggleCacheWidget(s: Boolean) { _uiState.update { it.copy(showCacheWidget = s) } }
    fun toggleFpsWidget(s: Boolean) { _uiState.update { it.copy(showFpsWidget = s) } }
    fun zoomIn() = _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it }
    fun zoomOut() = _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it }

    override fun onCleared() {
        super.onCleared(); stopGameLoop(); messagesCollectorJob?.cancel(); tileCache.closeAll(); webSocketManager?.disconnect()
        nativeDrawableCache.evictAll(); landmarkBitmapCache.evictAll(); base64Cache.evictAll(); widthCache.evictAll(); heightCache.evictAll(); registeredWebImages.clear()
    }

    private var idleJob: Job? = null
    private fun startMovementAction(mR: Boolean? = null) {
        idleJob?.cancel(); val nFR = mR ?: _uiState.value.isPlayerFacingRight; val cA = if (_uiState.value.isRunning) PlayerAction.RUN else PlayerAction.WALK
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) _uiState.update { it.copy(playerAction = cA, isPlayerFacingRight = nFR) }
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) { idleJob = viewModelScope.launch { delay(150); if (_uiState.value.playerAction != PlayerAction.SPECIAL) _uiState.update { it.copy(playerAction = PlayerAction.IDLE) } } }
    }

    fun onInteractButtonPressed() {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isDriving) {
            val nearby = remoteEntities.entries.filter { it.value.type == NpcType.CAR && distance(loc, it.value.location) <= 0.0005 }.minByOrNull { distance(loc, it.value.location) }
            if (nearby != null) {
                remoteEntities.remove(nearby.key); if (nearby.value.isFirstTimeBoarded) spawnOustedDriver(nearby.value.location)
                _uiState.update { it.copy(isDriving = true, currentVehicleModel = nearby.value.carModel, currentVehicleColor = nearby.value.carColor, vehicleRotation = (nearby.value.rotationAngle + 90f) % 360f, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = false) }; updateNpcsState()
            }
        } else {
            val car = Npc(id = UUID.randomUUID().toString(), type = NpcType.CAR, location = loc, rotationAngle = (_uiState.value.vehicleRotation + 270f) % 360f, speed = 0.0, isMoving = false, carModel = _uiState.value.currentVehicleModel ?: CarModel.SEDAN, carColor = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(), isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded)
            remoteEntities[car.id] = car; _uiState.update { it.copy(isDriving = false, currentVehicleModel = null, currentVehicleColor = null, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = true) }; updateNpcsState()
        }
    }

    fun loadLandmarks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                LandmarkCatalogManager.loadCatalog(context); val dao = PowDatabase.getInstance(context).landmarkDao(); var entities = dao.getAllLandmarks()
                if (entities.isEmpty()) { dao.insertLandmarks(listOf(LandmarkEntity(name = "ESCOM", latitude = 19.504505, longitude = -99.146911, assetPath = "BUILDINGS/IPN/building_escom.webp", scaleFactor = 0.15f))); entities = dao.getAllLandmarks() }
                val templates = LandmarkCatalogManager.availableAssets.associateBy { it.assetPath }
                _uiState.update { it.copy(landmarks = entities.map { e -> val t = templates[e.assetPath]; Landmark(id = e.id, name = e.name, location = GeoPoint(e.latitude, e.longitude), assetPath = e.assetPath, scaleFactor = e.scaleFactor, rotationAngle = e.rotationAngle, baseWidthMeters = t?.baseWidthMeters ?: 100f, baseHeightMeters = t?.baseHeightMeters ?: 100f) }) }
            } catch (e: Exception) { Log.e("VM", "Error landmarks", e) }
        }
    }

    fun toggleDesignerMode(i: Boolean) { _uiState.update { it.copy(isDesignerMode = i, selectedLandmarkId = if (!i) null else it.selectedLandmarkId) } }
    fun showAssetPicker(s: Boolean) { _uiState.update { it.copy(showAssetPicker = s) } }
    fun selectLandmark(id: Long?) { _uiState.update { it.copy(selectedLandmarkId = id) } }
    fun addLandmarkAtPlayer(context: Context, t: LandmarkAssetTemplate) {
        val loc = _uiState.value.currentLocation ?: return
        viewModelScope.launch(Dispatchers.IO) { try { val dao = PowDatabase.getInstance(context).landmarkDao(); val id = dao.insertLandmark(LandmarkEntity(name = t.displayName, latitude = loc.latitude, longitude = loc.longitude, assetPath = t.assetPath, scaleFactor = t.defaultScale, rotationAngle = 0f)); loadLandmarks(context); _uiState.update { it.copy(showAssetPicker = false, selectedLandmarkId = id) } } catch (e: Exception) { Log.e("VM", "Error add", e) } }
    }
    fun moveSelectedLandmark(dLat: Double, dLon: Double) { val id = _uiState.value.selectedLandmarkId ?: return; _uiState.update { s -> s.copy(landmarks = s.landmarks.map { if (it.id == id) it.copy(location = GeoPoint(it.location.latitude + dLat, it.location.longitude + dLon)) else it }) } }
    fun rotateSelectedLandmark(a: Float) { val id = _uiState.value.selectedLandmarkId ?: return; _uiState.update { s -> s.copy(landmarks = s.landmarks.map { if (it.id == id) it.copy(rotationAngle = a) else it }) } }
    fun scaleSelectedLandmark(sc: Float) { val id = _uiState.value.selectedLandmarkId ?: return; _uiState.update { s -> s.copy(landmarks = s.landmarks.map { if (it.id == id) it.copy(scaleFactor = sc) else it }) } }
    fun deleteSelectedLandmark(context: Context) { val id = _uiState.value.selectedLandmarkId ?: return; viewModelScope.launch(Dispatchers.IO) { try { val dao = PowDatabase.getInstance(context).landmarkDao(); val e = dao.getLandmarkById(id); if (e != null) { dao.deleteLandmark(e); loadLandmarks(context); _uiState.update { it.copy(selectedLandmarkId = null) } } } catch (e: Exception) { Log.e("VM", "Error delete", e) } } }
    fun exportLandmarksToUri(context: Context, uri: android.net.Uri) { viewModelScope.launch(Dispatchers.IO) { try { val entities = PowDatabase.getInstance(context).landmarkDao().getAllLandmarks(); context.contentResolver.openOutputStream(uri)?.use { it.write(Gson().toJson(entities).toByteArray()) } } catch (e: Exception) { Log.e("VM", "Error export", e) } } }
    fun importLandmarksFromUri(context: Context, uri: android.net.Uri) { viewModelScope.launch(Dispatchers.IO) { try { val json = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() } ?: return@launch; val list: List<LandmarkEntity> = Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<LandmarkEntity>>() {}.type); val dao = PowDatabase.getInstance(context).landmarkDao(); dao.getAllLandmarks().forEach { dao.deleteLandmark(it) }; dao.insertLandmarks(list); loadLandmarks(context) } catch (e: Exception) { Log.e("VM", "Error import", e) } } }
    fun saveSelectedLandmark(context: Context) { val id = _uiState.value.selectedLandmarkId ?: return; val cur = _uiState.value.landmarks.find { it.id == id } ?: return; viewModelScope.launch(Dispatchers.IO) { try { PowDatabase.getInstance(context).landmarkDao().updateLandmark(LandmarkEntity(id = cur.id, name = cur.name, latitude = cur.location.latitude, longitude = cur.location.longitude, assetPath = cur.assetPath, scaleFactor = cur.scaleFactor, rotationAngle = cur.rotationAngle)) } catch (e: Exception) { Log.e("VM", "Error save", e) } } }

    private fun spawnOustedDriver(carLoc: GeoPoint) {
        val colors = listOf(androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Red, androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Green)
        val driver = Npc(id = UUID.randomUUID().toString(), type = NpcType.PERSON, location = GeoPoint(carLoc.latitude + 0.00005, carLoc.longitude + 0.00005), speed = NpcAiManager.PERSON_SPEED, isMoving = true, visualConfig = CharacterVisualConfig(bodyFolder = "npc_walk_1", bodyPrefix = "npc_walk_1_", hairId = (1..5).random(), hairColor = colors.random(), shirtColor = colors.random(), pantsColor = androidx.compose.ui.graphics.Color.DarkGray))
        remoteEntities[driver.id] = driver
    }

    fun toggleTeleportMenu(s: Boolean) { _uiState.update { it.copy(showTeleportMenu = s) } }
    fun teleportTo(la: Double, lo: Double) { _uiState.update { it.copy(currentLocation = GeoPoint(la, lo), showTeleportMenu = false) } }
    fun steerLeft(p: Boolean) { isSteeringLeftPressed = p }
    fun steerRight(p: Boolean) { isSteeringRightPressed = p }
    fun accelerate(p: Boolean) { isGasPressed = p }
    fun brake(p: Boolean) { isBrakePressed = p }

    private fun trySpawningCollectible(pLa: Double, pLo: Double) {
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty() || _uiState.value.activeCollectibles.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uncollected = collectibleRepository.getUncollectedCollectibles(); if (uncollected.isNotEmpty()) {
                    val item = uncollected.random(); val b = Math.random() * 2 * Math.PI; val d = 300.0 + Math.random() * 300.0; val sN = getNearestPointOnNetwork(GeoPoint(pLa + (d * cos(b)) / 111000.0, pLo + (d * sin(b)) / (111000.0 * cos(Math.toRadians(pLa))))); withContext(Dispatchers.Main) { _uiState.update { it.copy(activeCollectibles = listOf(ActiveCollectible(id = item.id, name = item.name, description = item.description, assetPath = item.assetPath, latitude = sN.latitude, longitude = sN.longitude))) } }
                }
            } catch (e: Exception) { }
        }
    }

    fun checkCollectibleProximity(pLa: Double, pLo: Double) {
        val col = _uiState.value.activeCollectibles.firstOrNull() ?: return
        if (GeoPoint(pLa, pLo).distanceToAsDouble(GeoPoint(col.latitude, col.longitude)) <= 15.0) {
            if (_uiState.value.nearbyCollectible?.id != col.id) {
                _uiState.update { it.copy(nearbyCollectible = col) }; promptJob?.cancel(); promptJob = viewModelScope.launch { _uiState.update { it.copy(interactionPrompt = "PRESIONA X PARA RECOGER") }; delay(3000); _uiState.update { it.copy(interactionPrompt = null) } }
            }
        } else if (_uiState.value.nearbyCollectible != null) { promptJob?.cancel(); promptJob = null; _uiState.update { it.copy(nearbyCollectible = null, interactionPrompt = null) } }
    }
    private var promptJob: Job? = null
    fun onClaimCollectiblePressed() { val item = _uiState.value.nearbyCollectible ?: return; viewModelScope.launch(Dispatchers.IO) { collectibleRepository.claimCollectible(item.id); withContext(Dispatchers.Main) { promptJob?.cancel(); promptJob = null; _uiState.update { it.copy(activeCollectibles = emptyList(), nearbyCollectible = null, interactionPrompt = null, showClaimedPopupFor = item) } } } }
    fun dismissClaimedPopup() { _uiState.update { it.copy(showClaimedPopupFor = null) } }

    fun takeDamage(a: Float) { playerHealth = (playerHealth - a).coerceAtLeast(0f); damagePulseTrigger++; showHealthBar = true; if (playerHealth > 30f) startHealthBarTimer(3000L) else healthBarJob?.cancel(); if (playerHealth <= 0f) { } }
    fun heal(a: Float) { playerHealth = (playerHealth + a).coerceAtMost(maxPlayerHealth); showHealthBar = true; if (playerHealth > 30f) startHealthBarTimer(3000L) else healthBarJob?.cancel() }
    private fun startHealthBarTimer(d: Long) { healthBarJob?.cancel(); healthBarJob = viewModelScope.launch { delay(d); showHealthBar = false } }
    fun showInitialHealthBar() { showHealthBar = true; startHealthBarTimer(4000L) }
    fun performPlayerAttack() {
        val now = System.currentTimeMillis(); if (now - lastAttackTime < 2400L) return; lastAttackTime = now
        viewModelScope.launch(Dispatchers.Default) {
            delay(300L); val loc = _uiState.value.currentLocation ?: return@launch
            val target = remoteEntities.entries.filter { !it.value.isDying && it.value.type == NpcType.PERSON && (it.value.displayName?.isBlank() != false) && distance(loc, it.value.location) <= 0.00015 }.minByOrNull { distance(loc, it.value.location) }
            if (target != null) {
                val newH = (target.value.health - 15f).coerceAtLeast(0f); if (newH <= 0f) { remoteEntities[target.key] = target.value.copy(health = 0f, isDying = true); updateNpcsState(); delay(1000L); remoteEntities.remove(target.key); try { webSocketManager?.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to target.key))) } catch (e: Exception) { } ; updateNpcsState() }
                else { remoteEntities[target.key] = target.value.copy(health = newH); updateNpcsState() }
            }
        }
    }
}

private fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, defaultValue: () -> V?): V? {
    val value = get(key)
    return if (value == null) {
        val answer = defaultValue()
        if (answer != null) put(key, answer)
        answer
    } else value
}
