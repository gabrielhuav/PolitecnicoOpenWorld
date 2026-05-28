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

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

data class MultiplayerPlayer(
    val type: String = "PLAYER_UPDATE",
    val id: String,
    val displayName: String = "",
    val x: Double,
    val y: Double,
    val action: String,
    val facingRight: Boolean,
    val isDriving: Boolean = false,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null,
    val health: Float = 100f
)

data class MultiplayerNpc(
    val id: String,
    val x: Double,
    val y: Double,
    val rotation: Float,
    val npcType: String,
    val ownerId: String? = null,
    val carModel: String? = null,
    val carColor: Int? = null,
    val hairId: Int? = null,
    val hairColor: Int? = null,
    val shirtColor: Int? = null,
    val pantsColor: Int? = null
)

private data class ServerMessage(
    val type: String? = null,
    val id: String? = null,
    val sessionId: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val action: String? = null,
    val facingRight: Boolean? = null,
    val displayName: String? = null,
    val isDriving: Boolean? = null,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null,
    val npc: MultiplayerNpc? = null,
    val npcs: List<MultiplayerNpc>? = null,
    val npcId: String? = null,
    val orphanedNpcs: List<String>? = null,
    val activeNpcIds: List<String>? = null,
    val isZoneHost: Boolean? = null,
    val health: Float? = null,
    val targetId: String? = null,
    val damage: Float? = null,
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
    private var promptJob: Job? = null

    // ─── FLAG: tras el video, navegar al minijuego de zombis ──────────────────
    // En lugar de entrar a un interior concreto, la mano lleva al minijuego.
    var pendingZombieMinigame: Boolean = false
        private set

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

    // ─── Red de calles expuesta a la UI ──────────────────────────────────────
    // La WorldMapScreen consume este Flow para pintar las Polylines de los
    // caminos transitables ENCIMA de cualquier landmark del Modo Diseñador.
    private val _roadNetworkFlow = MutableStateFlow<List<MapWay>>(emptyList())
    val roadNetworkFlow: StateFlow<List<MapWay>> = _roadNetworkFlow.asStateFlow()

    private var roadNetworkNodeGrid: Map<Pair<Int, Int>, List<GeoPoint>> = emptyMap()
    private var routeCalculationJob: Job? = null
    private var routeRetryJob: Job? = null
    private var lastNetworkFetchLocation: GeoPoint? = null
    private var gameLoopJob: Job? = null
    private var tickCount = 0
    private val isFetchingNetwork  = AtomicBoolean(false)
    private var lastFetchAttemptMs = 0L

    private val REFETCH_DISTANCE_DEG = 0.015
    private val REFETCH_COOLDOWN_MS  = 5 * 60 * 1000L
    private val ROAD_NODE_GRID_SIZE_DEG = 0.001

    private var lastVisibleRoadUpdateLocation: GeoPoint? = null
    private val VISIBLE_ROAD_UPDATE_THRESHOLD = 0.002
    private val VISIBLE_ROAD_RADIUS = 0.006

    var isSteeringLeftPressed = false
    var isSteeringRightPressed = false
    var isGasPressed = false
    var isBrakePressed = false

    private val MAX_SPEED = 0.000017
    private val ACCELERATION = 0.0000003
    private val BRAKING_FRICTION = 0.000001
    private val INTERACT_RADIUS = 0.0005

    private val PLAYER_PUNCH_DAMAGE = 15f
    private var lastAttackTime = 0L
    private val ATTACK_COOLDOWN_MS = 2400L
    private val ATTACK_RADIUS = 0.00015

    private val hospitalRespawnPoints = listOf(
        GeoPoint(19.5034, -99.1469),
        GeoPoint(19.4990, -99.1350),
        GeoPoint(19.5070, -99.1400)
    )

    private val ESCOM_BASE_LAT = 19.50456
    private val ESCOM_BASE_LON = -99.14674
    private val ESCOM_OFFSET = 0.001

    private val _escomItems = MutableStateFlow<List<ActiveCollectible>>(emptyList())
    val escomItems: StateFlow<List<ActiveCollectible>> = _escomItems.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
        startGameLoop()
    }

// ─── WEBSOCKET MULTIJUGADOR ───────────────────────────────────────────────────

    private var webSocketManager: WebSocketManager? = null
    private var messagesCollectorJob: Job? = null
    private val gson = Gson()
    private var myPlayerUUID = "Player_${UUID.randomUUID()}"
    private var myPlayerDisplayName = ""
    private val remoteEntities = ConcurrentHashMap<String, Npc>()

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

    private var isServerDelegatedHost = true
    private fun handleMultiplayerMessage(messageJson: String) {
        try {
            val msg = gson.fromJson(messageJson, ServerMessage::class.java)

            when (msg.type) {
                "SESSION_INIT" -> {
                    msg.sessionId?.let { myPlayerUUID = it }
                }

                "SYNC_ALL_NPCS" -> {
                    msg.npcs?.forEach { remoteNpc ->
                        if (remoteNpc.ownerId != myPlayerUUID) {
                            addRemoteEntity(remoteNpc)
                        }
                    }
                    updateNpcsState()
                }

                "ROLE_UPDATE" -> {
                    msg.isZoneHost?.let {
                        isServerDelegatedHost = it
                        Log.d("Multiplayer", "Mi rol en esta zona ahora es Host: $it")
                    }
                }

                "NPC_SPAWN", "NPC_UPDATE" -> {
                    msg.npc?.let {
                        if (it.ownerId != myPlayerUUID) {
                            addRemoteEntity(it)
                            updateNpcsState()
                        }
                    }
                }

                "NPC_BATCH_UPDATE" -> {
                    msg.npcs?.forEach { remoteNpc ->
                        if (remoteNpc.ownerId != myPlayerUUID) {
                            addRemoteEntity(remoteNpc)
                        }
                    }
                    updateNpcsState()
                }

                "NPC_DESTROY" -> {
                    msg.npcId?.let {
                        remoteEntities.remove(it)
                        updateNpcsState()
                    }
                }

                "DISCONNECT" -> {
                    msg.id?.let { remoteEntities.remove(it) }
                    msg.orphanedNpcs?.forEach { remoteEntities.remove(it) }
                    updateNpcsState()
                }

                "MASTER_SYNC_CHECK" -> {
                    msg.activeNpcIds?.let { officialIds ->
                        val officialSet = officialIds.toSet()
                        var stateChanged = false
                        val iterator = remoteEntities.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (entry.value.displayName.isNullOrEmpty()) {
                                if (!officialSet.contains(entry.key)) {
                                    iterator.remove()
                                    stateChanged = true
                                }
                            }
                        }
                        if (stateChanged) updateNpcsState()
                    }
                }

                "PLAYER_DAMAGE" -> {
                    if (msg.targetId == myPlayerUUID && msg.damage != null) {
                        takeDamage(msg.damage)
                    }
                }

                else -> {
                    if (msg.id != null && msg.id != myPlayerUUID && msg.x != null && msg.y != null) {

                        val isRemoteMoving = msg.action == "WALK" || msg.action == "RUN"
                        val isRemoteDriving = msg.isDriving == true

                        val multiplayerConfig = ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
                            bodyFolder = "otherPlayer",
                            bodyPrefix = "p_mult_",
                            hairId = 1,
                            hairColor = androidx.compose.ui.graphics.Color.White,
                            shirtColor = androidx.compose.ui.graphics.Color.Cyan,
                            pantsColor = androidx.compose.ui.graphics.Color.DarkGray
                        )

                        val remoteCarModel = try {
                            msg.carModel?.let { ovh.gabrielhuav.pow.domain.models.CarModel.valueOf(it) }
                                ?: ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
                        } catch(e: Exception) {
                            ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
                        }

                        val otherPlayer = Npc(
                            id = msg.id,
                            type = if (isRemoteDriving) NpcType.CAR else NpcType.PERSON,
                            location = GeoPoint(msg.y, msg.x),
                            rotationAngle = if (isRemoteDriving) ((msg.vehicleRotation ?: 0f) + 270f) % 360f else 0f,
                            speed = 0.0,
                            isRemote = true,
                            isMoving = isRemoteMoving || isRemoteDriving,
                            facingRight = msg.facingRight == true,
                            carModel = remoteCarModel,
                            carColor = msg.carColor ?: 0xFFFFFFFF.toInt(),
                            visualConfig = if (!isRemoteDriving) multiplayerConfig else null,
                            displayName = msg.displayName,
                            health = msg.health ?: 100f,
                            isDying = (msg.health ?: 100f) <= 0f
                        )
                        remoteEntities[msg.id] = otherPlayer
                        updateNpcsState()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorldMapVM", "Error procesando JSON: ${e.message}")
        }
    }

    private fun addRemoteEntity(remote: MultiplayerNpc) {
        val npcType = try { NpcType.valueOf(remote.npcType) } catch(e: Exception) { NpcType.PERSON }

        val cModel = try {
            remote.carModel?.let { ovh.gabrielhuav.pow.domain.models.CarModel.valueOf(it) }
                ?: ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
        } catch (e: Exception) { ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN }
        val cColor = remote.carColor ?: 0xFFFFFFFF.toInt()

        val visualConfig = if (npcType == NpcType.PERSON) {
            ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
                bodyFolder = "npc_walk_1",
                bodyPrefix = "npc_walk_1_",
                hairId = remote.hairId ?: 1,
                hairColor  = remote.hairColor?.let  { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.White,
                shirtColor = remote.shirtColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.LightGray,
                pantsColor = remote.pantsColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.DarkGray
            )
        } else null

        val isMoving = npcType == NpcType.PERSON
        val facingRight = cos(Math.toRadians(remote.rotation.toDouble())) >= 0

        val restoredSpeed = if (npcType == NpcType.CAR) NpcAiManager.CAR_SPEED else NpcAiManager.PERSON_SPEED

        remoteEntities[remote.id] = Npc(
            id = remote.id,
            type = npcType,
            location = GeoPoint(remote.y, remote.x),
            rotationAngle = remote.rotation,
            speed = restoredSpeed,
            isRemote = true,
            isMoving = isMoving,
            facingRight = facingRight,
            ownerId = remote.ownerId,
            carModel = cModel,
            carColor = cColor,
            visualConfig = visualConfig,
            displayName = null
        )
    }

    private fun updateNpcsState() {
        _uiState.update { it.copy(npcs = remoteEntities.values.toList()) }
    }

    // ─── GAME LOOP ───────────────────────────────────────────────────────────────

    private fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return

        gameLoopJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {

            while (_uiState.value.currentLocation == null) { kotlinx.coroutines.delay(100) }
            val initialLoc = _uiState.value.currentLocation!!

            if (_uiState.value.mapProvider == MapProvider.OSM) {
                _uiState.update { it.copy(tileSource = TileSource.LOCAL_OSM) }
            }

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

                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            roadNetworkCache.put(initialLoc.latitude, initialLoc.longitude, network)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                            }
                        }
                        break
                    } else {
                        _uiState.update { it.copy(isRoadNetworkReady = false) }
                        kotlinx.coroutines.delay(retryMs)
                        retryMs = (retryMs * 2).coerceAtMost(30_000L)
                    }
                }
            }

            var tickCount = 0L
            while (isActive) {
                try {
                    _uiState.value.currentLocation?.let { location ->
                        val inside = isInsideEscom(location.latitude, location.longitude)

                        // Sincroniza la mano con la zona ESCOM:
                        //  - Si salgo de ESCOM: borro la mano (lista + flag).
                        //  - Si entro a ESCOM y aún no hay mano: la genero.
                        if (!inside) {
                            if (_uiState.value.isZombieHandSpawned || _escomItems.value.isNotEmpty()) {
                                _escomItems.value = emptyList()
                                _uiState.update { it.copy(isZombieHandSpawned = false) }
                            }
                        } else {
                            if (!_uiState.value.isZombieHandSpawned && _uiState.value.isRoadNetworkReady) {
                                spawnEscomItems(roadNetwork)
                            }
                        }

                        if (tickCount % 30 == 0L) {
                            trySpawningCollectible(location.latitude, location.longitude)
                        }
                        checkCollectibleProximity(location.latitude, location.longitude)

                        checkDestinationArrival()

                        if (tickCount % 30 == 0L && _uiState.value.destinationMarker != null) {
                            updateDestinationRoute()
                        }

                        if (_uiState.value.playerAction == PlayerAction.SPECIAL) {
                            performPlayerAttack()
                        }

                        if (_uiState.value.isDriving) {
                            var currentSpeed = _uiState.value.vehicleSpeed
                            var currentRotation = _uiState.value.vehicleRotation

                            if (isSteeringLeftPressed && currentSpeed != 0.0) currentRotation -= 2f
                            if (isSteeringRightPressed && currentSpeed != 0.0) currentRotation += 2f

                            if (isGasPressed) {
                                currentSpeed = (currentSpeed + ACCELERATION).coerceAtMost(MAX_SPEED)
                            } else if (isBrakePressed) {
                                currentSpeed -= BRAKING_FRICTION
                                if (currentSpeed < -MAX_SPEED / 2) currentSpeed = -MAX_SPEED / 2
                            } else {
                                if (currentSpeed > 0) currentSpeed = (currentSpeed - (ACCELERATION / 2)).coerceAtLeast(0.0)
                                if (currentSpeed < 0) currentSpeed = (currentSpeed + (ACCELERATION / 2)).coerceAtMost(0.0)
                            }

                            val angleRad = Math.toRadians(currentRotation.toDouble())
                            val dx = kotlin.math.sin(angleRad) * currentSpeed
                            val dy = kotlin.math.cos(angleRad) * currentSpeed

                            val tempLoc = GeoPoint(location.latitude + dy, location.longitude + dx)

                            val nearestRoadPoint = getNearestPointOnNetwork(tempLoc)
                            val distToRoad = distance(tempLoc, nearestRoadPoint)
                            val maxRoadRadius = 0.000025

                            val finalLoc = if (distToRoad <= maxRoadRadius) {
                                tempLoc
                            } else {
                                val angleBack = atan2(tempLoc.latitude - nearestRoadPoint.latitude, tempLoc.longitude - nearestRoadPoint.longitude)
                                currentSpeed *= 0.8
                                GeoPoint(
                                    nearestRoadPoint.latitude + sin(angleBack) * maxRoadRadius,
                                    nearestRoadPoint.longitude + cos(angleBack) * maxRoadRadius
                                )
                            }

                            _uiState.update {
                                it.copy(
                                    currentLocation = finalLoc,
                                    vehicleSpeed = currentSpeed,
                                    vehicleRotation = (currentRotation + 360) % 360f
                                )
                            }
                        }

                        maybeRefetchRoadNetwork(location)
                        if (tickCount % 5 == 0L) {
                            updateVisibleRoads(location)
                        }
                        updateVisibleRoads(location)
                        if (_uiState.value.isRoadNetworkReady) {
                            tickCount++
                            if (tickCount % 3 == 0L) {
                                val npcOnlyList = remoteEntities.values.filter { it.displayName.isNullOrEmpty() }
                                npcAiManager.setServerNpcs(npcOnlyList)

                                npcAiManager.updateNpcs(location, isServerDelegatedHost)
                                val processedNpcs = npcAiManager.getServerNpcs()

                                if (isServerDelegatedHost) {
                                    synchronized(npcAiManager.pendingDespawns) {
                                        npcAiManager.pendingDespawns.forEach { remoteEntities.remove(it) }
                                    }
                                    processedNpcs.forEach { remoteEntities[it.id] = it }
                                }
                                updateNpcsState()

                                webSocketManager?.let { ws ->
                                    launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val myData = MultiplayerPlayer(
                                                id = myPlayerUUID,
                                                displayName = myPlayerDisplayName,
                                                x = location.longitude,
                                                y = location.latitude,
                                                action = _uiState.value.playerAction.name,
                                                facingRight = _uiState.value.isPlayerFacingRight,
                                                isDriving = _uiState.value.isDriving,
                                                carModel = _uiState.value.currentVehicleModel?.name,
                                                carColor = _uiState.value.currentVehicleColor,
                                                vehicleRotation = _uiState.value.vehicleRotation,
                                                health = playerHealth
                                            )
                                            ws.sendMessage(gson.toJson(myData))

                                            if (isServerDelegatedHost) {
                                                val despawnsToSend = synchronized(npcAiManager.pendingDespawns) {
                                                    val list = npcAiManager.pendingDespawns.toList()
                                                    npcAiManager.pendingDespawns.clear()
                                                    list
                                                }

                                                despawnsToSend.forEach { idToRemove ->
                                                    ws.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to idToRemove)))
                                                }

                                                if (processedNpcs.isNotEmpty()) {
                                                    val npcBatch = processedNpcs.map { npc ->
                                                        MultiplayerNpc(
                                                            id = npc.id,
                                                            x = npc.location.longitude,
                                                            y = npc.location.latitude,
                                                            rotation = npc.rotationAngle,
                                                            npcType = npc.type.name,
                                                            ownerId = myPlayerUUID,
                                                            carModel = npc.carModel?.name,
                                                            carColor = npc.carColor,
                                                            hairId = npc.visualConfig?.hairId,
                                                            hairColor = npc.visualConfig?.hairColor?.toArgb(),
                                                            shirtColor = npc.visualConfig?.shirtColor?.toArgb(),
                                                            pantsColor = npc.visualConfig?.pantsColor?.toArgb()
                                                        )
                                                    }
                                                    ws.sendMessage(gson.toJson(mapOf("type" to "NPC_BATCH_UPDATE", "npcs" to npcBatch)))
                                                }
                                            } else {
                                                synchronized(npcAiManager.pendingDespawns) { npcAiManager.pendingDespawns.clear() }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("Network", "Error al enviar datos: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("GameLoop", "Crasheo evitado en el ciclo principal: ${e.message}")
                }
                kotlinx.coroutines.delay(33)
            }
        }
    }

    fun stopGameLoop() { gameLoopJob?.cancel(); gameLoopJob = null }

    private fun updateVisibleRoads(location: GeoPoint, force: Boolean = false) {
        if (!_uiState.value.showRoadNetwork || roadNetwork.isEmpty()) {
            if (_roadNetworkFlow.value.isNotEmpty()) _roadNetworkFlow.value = emptyList()
            return
        }
        val lastLoc = lastVisibleRoadUpdateLocation
        // Solo recalculamos si forzamos la actualización o si el jugador se movió lo suficiente (~200m)
        if (force || lastLoc == null || distance(lastLoc, location) > VISIBLE_ROAD_UPDATE_THRESHOLD) {
            lastVisibleRoadUpdateLocation = location
            // Ejecutamos el filtro en un hilo secundario para no trabar el Game Loop
            viewModelScope.launch(Dispatchers.Default) {
                val visibleWays = roadNetwork.filter { way ->
                    // Una calle es visible si al menos uno de sus nodos está dentro del radio del jugador
                    way.nodes.any { node ->
                        abs(node.lat - location.latitude) < VISIBLE_ROAD_RADIUS &&
                                abs(node.lon - location.longitude) < VISIBLE_ROAD_RADIUS
                    }
                }
                // Actualizamos el Flow que lee la UI (pasará de ~5,000 calles a solo ~100)
                _roadNetworkFlow.value = visibleWays
            }
        }
    }

    private suspend fun applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network

        updateVisibleRoads(playerLocation, force = true)
        rebuildRoadNodeGrid(network)
        npcAiManager.updateRoadNetwork(network)

        // Solo intentamos spawnear la mano si estamos en ESCOM.
        // (spawnEscomItems igual se autoprotege, esto solo evita la llamada inútil.)
        if (isInsideEscom(playerLocation.latitude, playerLocation.longitude)) {
            spawnEscomItems(network)
        } else {
            _escomItems.value = emptyList()
            _uiState.update { it.copy(isZombieHandSpawned = false) }
        }

        val snapped = withContext(Dispatchers.Default) { getNearestPointOnNetwork(playerLocation) }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(currentLocation = snapped, isRoadNetworkReady = true) }
        }
        val targetZoom = if (_uiState.value.mapProvider.isWebProvider)
            ZOOM_GAMEPLAY_WEB
        else
            ZOOM_GAMEPLAY_OSM

        if (_uiState.value.zoomLevel <= ZOOM_LOADING) {
            var z = ZOOM_LOADING + 1.0
            while (z <= targetZoom) {
                delay(120)
                withContext(Dispatchers.Main) { _uiState.update { it.copy(zoomLevel = z) } }
                z += 1.0
            }
        }
    }

    private fun maybeRefetchRoadNetwork(currentLoc: org.osmdroid.util.GeoPoint) {
        val moved = if (lastNetworkFetchLocation != null)
            distance(lastNetworkFetchLocation!!, currentLoc) else Double.MAX_VALUE
        if (moved < REFETCH_DISTANCE_DEG) return

        val now = System.currentTimeMillis()
        if (now - lastFetchAttemptMs < REFETCH_COOLDOWN_MS) return
        if (!isFetchingNetwork.compareAndSet(false, true)) return
        lastFetchAttemptMs = now

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cached = roadNetworkCache.get(currentLoc.latitude, currentLoc.longitude)
                if (cached != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        roadNetwork = cached
                        updateVisibleRoads(currentLoc, force = true)
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        val inside = isInsideEscom(currentLoc.latitude, currentLoc.longitude)
                        if (inside && !_uiState.value.isZombieHandSpawned) {
                            Log.d("DEBUG_ESCOM", "Red cargada tras teleport, spawneando...")
                            spawnEscomItems(roadNetwork)
                        }
                        _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.NETWORK) }
                    }
                    val network = overpassRepository.fetchRoadNetwork(
                        currentLoc.latitude, currentLoc.longitude)
                    if (network.isNotEmpty()) {
                        roadNetworkCache.put(currentLoc.latitude, currentLoc.longitude, network)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            roadNetwork = network
                            updateVisibleRoads(currentLoc, force = true)
                            npcAiManager.updateRoadNetwork(network)
                            lastNetworkFetchLocation = currentLoc
                            _uiState.update { it.copy(roadSource = ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _uiState.update { it.copy(isRoadNetworkReady = true) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error refetching road network", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _uiState.update { it.copy(isRoadNetworkReady = true) }
                }
            } finally {
                isFetchingNetwork.set(false)
            }
        }
    }

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
        tileCache.closeAll()
        webSocketManager?.disconnect()
    }

    private var idleJob: Job? = null

    private fun startMovementAction(isMovingRight: Boolean? = null) {
        idleJob?.cancel()
        val newFacingRight = isMovingRight ?: _uiState.value.isPlayerFacingRight
        val currentAction = if (_uiState.value.isRunning) PlayerAction.RUN else PlayerAction.WALK
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            if (_uiState.value.playerAction != currentAction || _uiState.value.isPlayerFacingRight != newFacingRight) {
                _uiState.update { it.copy(playerAction = currentAction, isPlayerFacingRight = newFacingRight) }
            }
        }
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            idleJob = viewModelScope.launch {
                delay(150)
                if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
                    _uiState.update { it.copy(playerAction = PlayerAction.IDLE) }
                }
            }
        }
    }

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

    private fun spawnOustedDriver(carLocation: GeoPoint) {
        val offsetLoc = GeoPoint(carLocation.latitude + 0.00005, carLocation.longitude + 0.00005)
        val randomHairId = (1..5).random()
        val randomHairColor = listOf(
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color.DarkGray,
            androidx.compose.ui.graphics.Color(0xFF8B4513),
            androidx.compose.ui.graphics.Color(0xFFDAA520)
        ).random()
        val randomShirtColor = listOf(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color.Red,
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Green
        ).random()
        val visualConfig = ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
            bodyFolder = "npc_walk_1",
            bodyPrefix = "npc_walk_1_",
            hairId = randomHairId,
            hairColor = randomHairColor,
            shirtColor = randomShirtColor,
            pantsColor = androidx.compose.ui.graphics.Color.DarkGray
        )
        val driver = Npc(
            id = UUID.randomUUID().toString(),
            type = NpcType.PERSON,
            location = offsetLoc,
            speed = NpcAiManager.PERSON_SPEED,
            isMoving = true,
            visualConfig = visualConfig
        )
        remoteEntities[driver.id] = driver
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

    private val isSpawningCollectible = AtomicBoolean(false)

    private fun trySpawningCollectible(playerLat: Double, playerLon: Double) {
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        if (_uiState.value.activeCollectibles.isNotEmpty() || !isSpawningCollectible.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uncollected = collectibleRepository.getUncollectedCollectibles()
                if (uncollected.isNotEmpty()) {
                    val itemToSpawn = uncollected.random()
                    val bearing = Math.random() * 2 * Math.PI
                    val distanceMeters = 300.0 + Math.random() * 300.0
                    val clampedLat = playerLat.coerceIn(-85.0, 85.0)
                    val deltaLat = (distanceMeters * Math.cos(bearing)) / 111000.0
                    val deltaLon = (distanceMeters * Math.sin(bearing)) / (111000.0 * Math.cos(Math.toRadians(clampedLat)))
                    val offsetLat = playerLat + deltaLat
                    val offsetLon = playerLon + deltaLon
                    val tempLoc = org.osmdroid.util.GeoPoint(offsetLat, offsetLon)
                    val spawnNode = getNearestPointOnNetwork(tempLoc)
                    val activeItem = ActiveCollectible(
                        id = itemToSpawn.id,
                        name = itemToSpawn.name,
                        description = itemToSpawn.description,
                        assetPath = itemToSpawn.assetPath,
                        latitude = spawnNode.latitude,
                        longitude = spawnNode.longitude
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(activeCollectibles = listOf(activeItem)) }
                    }
                }
            } finally {
                isSpawningCollectible.set(false)
            }
        }
    }

    private fun checkCollectibleProximity(playerLat: Double, playerLon: Double) {
        val allPossibleItems = _uiState.value.activeCollectibles + _escomItems.value

        val playerGeo = org.osmdroid.util.GeoPoint(playerLat, playerLon)
        val activeItem = allPossibleItems.minByOrNull {
            playerGeo.distanceToAsDouble(org.osmdroid.util.GeoPoint(it.latitude, it.longitude))
        } ?: return

        val itemGeo = org.osmdroid.util.GeoPoint(activeItem.latitude, activeItem.longitude)
        val distanceInMeters = playerGeo.distanceToAsDouble(itemGeo)
        val INTERACT_RADIUS_METERS = 15.0

        if (distanceInMeters <= INTERACT_RADIUS_METERS) {
            if (_uiState.value.nearbyCollectible?.id != activeItem.id) {
                _uiState.update { it.copy(nearbyCollectible = activeItem) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = if (activeItem.name == "Objeto Misterioso ESCOM") {
                        "PRESIONA X PARA INTERACTUAR"
                    } else {
                        "PRESIONA X PARA RECOGER"
                    }

                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
        } else {
            if (_uiState.value.nearbyCollectible != null) {
                promptJob?.cancel()
                promptJob = null
                _uiState.update { it.copy(nearbyCollectible = null, interactionPrompt = null) }
            }
        }
    }

    fun onClaimCollectiblePressed() {
        val itemToClaim = _uiState.value.nearbyCollectible ?: return

        if (itemToClaim.name == "Objeto Misterioso ESCOM") {
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

    private fun startHealthBarTimer(delayMillis: Long) {
        healthBarJob?.cancel()
        healthBarJob = viewModelScope.launch {
            delay(delayMillis)
            showHealthBar = false
        }
    }

    private fun triggerWastedSequence() {
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update { it.copy(showWastedScreen = true) }
            delay(4000L)
            val deathLoc = _uiState.value.currentLocation ?: GeoPoint(19.504505, -99.146911)
            val nearestHospital = hospitalRespawnPoints.minByOrNull { distance(deathLoc, it) } ?: hospitalRespawnPoints.first()
            _uiState.update { it.copy(currentLocation = nearestHospital, showWastedScreen = false) }
            playerHealth = maxPlayerHealth
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

    private fun updateDestinationRoute() {
        val destination = _uiState.value.destinationMarker ?: return
        val currentLoc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) {
            if (routeRetryJob?.isActive == true) return
            routeRetryJob = viewModelScope.launch {
                delay(1000)
                routeRetryJob = null
                if (_uiState.value.destinationMarker != null) updateDestinationRoute()
            }
            return
        }
        if (routeCalculationJob?.isActive == true) return
        routeRetryJob?.cancel()
        routeRetryJob = null
        routeCalculationJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                Log.d("Navigation", "Calculando ruta...")
                val route = calculateRouteOnNetwork(currentLoc, destination, roadNetwork)
                Log.d("Navigation", "Ruta calculada con ${route.size} puntos")
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(routeWaypoints = if (route.isNotEmpty()) route else listOf(currentLoc, destination)) }
                    val distToDestinationMeters = currentLoc.distanceToAsDouble(destination)
                    if (distToDestinationMeters <= _uiState.value.destinationArrivalThreshold) clearDestinationMarker()
                }
            } catch (e: Exception) { Log.e("Navigation", "Error calculando ruta: ${e.message}") }
            finally { routeCalculationJob = null }
        }
    }

    private fun calculateRouteOnNetwork(from: GeoPoint, to: GeoPoint, network: List<MapWay>): List<GeoPoint> {
        if (network.isEmpty()) return listOf(from, to)
        val route = mutableListOf<GeoPoint>()
        route.add(from)
        val startPoint = getNearestPointOnNetwork(from)
        val endPoint = getNearestPointOnNetwork(to)
        var current = startPoint
        val visitedNodes = mutableSetOf<String>()
        val maxSteps = 20
        for (step in 0 until maxSteps) {
            val distToTarget = distance(current, endPoint)
            if (distToTarget < 0.0005) break
            var bestNext: GeoPoint? = null
            var bestDist = distToTarget
            val candidateNodes = nearbyRoadNodes(current)
            for (nodePt in candidateNodes) {
                val nodeKey = "${nodePt.latitude},${nodePt.longitude}"
                if (visitedNodes.contains(nodeKey)) continue
                val dFromCurrent = distance(current, nodePt)
                if (dFromCurrent < 0.003) {
                    val dToTarget = distance(nodePt, endPoint)
                    if (dToTarget < bestDist) {
                        bestDist = dToTarget
                        bestNext = nodePt
                    }
                }
            }
            if (bestNext != null) {
                current = bestNext
                visitedNodes.add("${current.latitude},${current.longitude}")
                route.add(current)
            } else break
        }
        route.add(endPoint)
        route.add(to)
        return route.distinctBy { "${it.latitude},${it.longitude}" }
    }

    private fun rebuildRoadNodeGrid(network: List<MapWay>) {
        val uniqueNodes = linkedMapOf<String, GeoPoint>()
        network.forEach { way ->
            way.nodes.forEach { node ->
                val key = "${node.lat},${node.lon}"
                if (!uniqueNodes.containsKey(key)) uniqueNodes[key] = GeoPoint(node.lat, node.lon)
            }
        }
        roadNetworkNodeGrid = uniqueNodes.values.groupBy { point ->
            val latCell = floor(point.latitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
            val lonCell = floor(point.longitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
            latCell to lonCell
        }
    }

    private fun nearbyRoadNodes(point: GeoPoint): List<GeoPoint> {
        if (roadNetworkNodeGrid.isEmpty()) return emptyList()
        val latCell = floor(point.latitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
        val lonCell = floor(point.longitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
        val nearby = mutableListOf<GeoPoint>()
        for (latOffset in -1..1) {
            for (lonOffset in -1..1) {
                roadNetworkNodeGrid[(latCell + latOffset) to (lonCell + lonOffset)]?.let { nearby.addAll(it) }
            }
        }
        if (nearby.isNotEmpty()) return nearby
        return roadNetworkNodeGrid.values.flatten()
    }

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

        val spawnPoint = if (roadNetwork.isNotEmpty()) getNearestPointOnNetwork(center) else center

        val hand = ActiveCollectible(
            id = "escom_hand_lobby",
            name = "Objeto Misterioso ESCOM",
            description = "INTERIOR_TARGET:lobby",
            assetPath = "ZOMBIS_MOD/zombi_hand.webp",
            latitude = spawnPoint.latitude,
            longitude = spawnPoint.longitude
        )

        _escomItems.value = listOf(hand)
        _uiState.update { it.copy(isZombieHandSpawned = true) }
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

        if (nearby.name == "Objeto Misterioso ESCOM") {
            // La mano lleva al minijuego de zombis (arranca en el lobby/croquis).
            // Mostramos el video de carga y dejamos un destino placeholder no nulo
            // para que el LaunchedEffect de WorldMapScreen se dispare al terminar.
            pendingZombieMinigame = true
            _uiState.update {
                it.copy(
                    showZombiVideo = true,
                    pendingInteriorDestination = InteriorBuilding.EDIFICIO
                )
            }
        } else {
            onClaimCollectiblePressed()
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

    private fun isInsideEscom(lat: Double, lon: Double): Boolean {
        return abs(lat - ESCOM_BASE_LAT) < ESCOM_OFFSET &&
                abs(lon - ESCOM_BASE_LON) < ESCOM_OFFSET
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
}