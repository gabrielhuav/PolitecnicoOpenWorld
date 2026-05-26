package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.data.network.MultiplayerNpc
import ovh.gabrielhuav.pow.data.network.MultiplayerPlayer
import ovh.gabrielhuav.pow.data.network.ServerMessage
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.LandmarkAssetTemplate
import ovh.gabrielhuav.pow.domain.models.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.engine.RoadNetworkSpatialIndex
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (!modelClass.isAssignableFrom(WorldMapViewModel::class.java)) {
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
            val appCtx = context.applicationContext
            val database = PowDatabase.getInstance(appCtx)
            return WorldMapViewModel(
                roadNetworkCache      = RoadNetworkCache(database.roadNetworkDao()),
                tileCache             = TileCache(database.mapTileDao()),
                settingsRepository    = SettingsRepository(appCtx),
                collectibleRepository = CollectibleRepository(database.collectibleDao())
            ) as T
        }
    }

    private val _uiState = MutableStateFlow(
        WorldMapState(
            controlType   = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls  = settingsRepository.getSwapControls()
        )
    )
    val uiState: StateFlow<WorldMapState> = _uiState.asStateFlow()

    private val npcAiManager       = NpcAiManager()
    private val overpassRepository  = OverpassRepository()
    private val roadSpatialIndex    = RoadNetworkSpatialIndex()
    private var roadNetwork: List<MapWay> = emptyList()
    private var lastNetworkFetchLocation: GeoPoint? = null
    private var gameLoopJob: Job? = null
    private val isFetchingNetwork  = AtomicBoolean(false)
    private var lastFetchAttemptMs = 0L

    private val REFETCH_DISTANCE_DEG = 0.015
    private val REFETCH_COOLDOWN_MS  = 5 * 60 * 1000L

    var isSteeringLeftPressed  = false
    var isSteeringRightPressed = false
    var isGasPressed           = false
    var isBrakePressed         = false

    private val MAX_SPEED         = 0.000017
    private val ACCELERATION      = 0.0000003
    private val BRAKING_FRICTION  = 0.000001
    private val INTERACT_RADIUS   = 0.0005
    private val PLAYER_PUNCH_DAMAGE = 15f
    private val ATTACK_COOLDOWN_MS  = 2400L
    private val ATTACK_RADIUS       = 0.00015
    private var lastAttackTime      = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
        startGameLoop()
    }

    // ─── WEBSOCKET MULTIJUGADOR ──────────────────────────────────────────────────

    private var webSocketManager: WebSocketManager? = null
    private var messagesCollectorJob: Job? = null
    private val gson = Gson()
    private var myPlayerUUID        = "Player_${UUID.randomUUID()}"
    private var myPlayerDisplayName = ""
    private val remoteEntities      = ConcurrentHashMap<String, Npc>()
    private var isServerDelegatedHost = true

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
        webSocketManager?.disconnect()
        webSocketManager = null
        messagesCollectorJob?.cancel()
        messagesCollectorJob = null
        remoteEntities.clear()
        updateNpcsState()
    }

    private fun handleMultiplayerMessage(messageJson: String) {
        try {
            val msg = gson.fromJson(messageJson, ServerMessage::class.java)
            when (msg.type) {
                "SESSION_INIT" -> msg.sessionId?.let { myPlayerUUID = it }

                "SYNC_ALL_NPCS" -> {
                    msg.npcs?.forEach { if (it.ownerId != myPlayerUUID) addRemoteEntity(it) }
                    updateNpcsState()
                }

                "ROLE_UPDATE" -> msg.isZoneHost?.let {
                    isServerDelegatedHost = it
                    Log.d("Multiplayer", "Rol de zona host: $it")
                }

                "NPC_SPAWN", "NPC_UPDATE" -> msg.npc?.let {
                    if (it.ownerId != myPlayerUUID) { addRemoteEntity(it); updateNpcsState() }
                }

                "NPC_BATCH_UPDATE" -> {
                    msg.npcs?.forEach { if (it.ownerId != myPlayerUUID) addRemoteEntity(it) }
                    updateNpcsState()
                }

                "NPC_DESTROY" -> {
                    msg.npcId?.let { remoteEntities.remove(it) }
                    updateNpcsState()
                }

                "DISCONNECT" -> {
                    msg.id?.let { remoteEntities.remove(it) }
                    msg.orphanedNpcs?.forEach { remoteEntities.remove(it) }
                    updateNpcsState()
                }

                "MASTER_SYNC_CHECK" -> msg.activeNpcIds?.let { officialIds ->
                    val officialSet = officialIds.toSet()
                    var changed = false
                    val iterator = remoteEntities.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (entry.value.displayName.isNullOrEmpty() && !officialSet.contains(entry.key)) {
                            iterator.remove(); changed = true
                        }
                    }
                    if (changed) updateNpcsState()
                }

                else -> {
                    if (msg.id != null && msg.id != myPlayerUUID && msg.x != null && msg.y != null) {
                        val isRemoteMoving  = msg.action == "WALK" || msg.action == "RUN"
                        val isRemoteDriving = msg.isDriving == true
                        val remoteCarModel  = try {
                            msg.carModel?.let { CarModel.valueOf(it) } ?: CarModel.SEDAN
                        } catch (e: Exception) { CarModel.SEDAN }

                        val multiplayerConfig = CharacterVisualConfig(
                            bodyFolder  = "otherPlayer",
                            bodyPrefix  = "p_mult_",
                            hairId      = 1,
                            hairColor   = androidx.compose.ui.graphics.Color.White,
                            shirtColor  = androidx.compose.ui.graphics.Color.Cyan,
                            pantsColor  = androidx.compose.ui.graphics.Color.DarkGray
                        )

                        remoteEntities[msg.id] = Npc(
                            id             = msg.id,
                            type           = if (isRemoteDriving) NpcType.CAR else NpcType.PERSON,
                            location       = GeoPoint(msg.y, msg.x),
                            rotationAngle  = if (isRemoteDriving) ((msg.vehicleRotation ?: 0f) + 270f) % 360f else 0f,
                            speed          = 0.0,
                            isRemote       = true,
                            isMoving       = isRemoteMoving || isRemoteDriving,
                            facingRight    = msg.facingRight == true,
                            carModel       = remoteCarModel,
                            carColor       = msg.carColor ?: 0xFFFFFFFF.toInt(),
                            visualConfig   = if (!isRemoteDriving) multiplayerConfig else null,
                            displayName    = msg.displayName
                        )
                        updateNpcsState()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorldMapVM", "Error procesando mensaje: ${e.message}")
        }
    }

    private fun addRemoteEntity(remote: MultiplayerNpc) {
        val npcType = try { NpcType.valueOf(remote.npcType) } catch (e: Exception) { NpcType.PERSON }
        val cModel  = try {
            remote.carModel?.let { CarModel.valueOf(it) } ?: CarModel.SEDAN
        } catch (e: Exception) { CarModel.SEDAN }

        val visualConfig = if (npcType == NpcType.PERSON) {
            CharacterVisualConfig(
                bodyFolder  = "npc_walk_1",
                bodyPrefix  = "npc_walk_1_",
                hairId      = remote.hairId ?: 1,
                hairColor   = remote.hairColor?.let  { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.White,
                shirtColor  = remote.shirtColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.LightGray,
                pantsColor  = remote.pantsColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.DarkGray
            )
        } else null

        val restoredSpeed = if (npcType == NpcType.CAR) NpcAiManager.CAR_SPEED else NpcAiManager.PERSON_SPEED

        remoteEntities[remote.id] = Npc(
            id            = remote.id,
            type          = npcType,
            location      = GeoPoint(remote.y, remote.x),
            rotationAngle = remote.rotation,
            speed         = restoredSpeed,
            isRemote      = true,
            isMoving      = npcType == NpcType.PERSON,
            facingRight   = cos(Math.toRadians(remote.rotation.toDouble())) >= 0,
            ownerId       = remote.ownerId,
            carModel      = cModel,
            carColor      = remote.carColor ?: 0xFFFFFFFF.toInt(),
            visualConfig  = visualConfig,
            displayName   = null
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
                        if (_uiState.value.isRoadNetworkReady && roadNetwork.isNotEmpty() && tickCount % 30 == 0L) {
                            trySpawningCollectible(location.latitude, location.longitude)
                        }
                        checkCollectibleProximity(location.latitude, location.longitude)

                        if (_uiState.value.playerAction == PlayerAction.SPECIAL) {
                            performPlayerAttack()
                        }

                        if (_uiState.value.isDriving) {
                            tickVehiclePhysics(location)
                        }

                        maybeRefetchRoadNetwork(location)

                        if (_uiState.value.isRoadNetworkReady) {
                            tickCount++
                            if (tickCount % 3 == 0L) tickNpcAi(location)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("GameLoop", "Error en ciclo principal: ${e.message}")
                }
                kotlinx.coroutines.delay(33)
            }
        }
    }

    private fun tickVehiclePhysics(location: GeoPoint) {
        var currentSpeed    = _uiState.value.vehicleSpeed
        var currentRotation = _uiState.value.vehicleRotation

        if (isSteeringLeftPressed  && currentSpeed != 0.0) currentRotation -= 2f
        if (isSteeringRightPressed && currentSpeed != 0.0) currentRotation += 2f

        when {
            isGasPressed   -> currentSpeed = (currentSpeed + ACCELERATION).coerceAtMost(MAX_SPEED)
            isBrakePressed -> currentSpeed = (currentSpeed - BRAKING_FRICTION).coerceAtLeast(-MAX_SPEED / 2)
            else -> {
                if (currentSpeed > 0) currentSpeed = (currentSpeed - ACCELERATION / 2).coerceAtLeast(0.0)
                if (currentSpeed < 0) currentSpeed = (currentSpeed + ACCELERATION / 2).coerceAtMost(0.0)
            }
        }

        val angleRad = Math.toRadians(currentRotation.toDouble())
        val dx = kotlin.math.sin(angleRad) * currentSpeed
        val dy = kotlin.math.cos(angleRad) * currentSpeed
        val tempLoc = GeoPoint(location.latitude + dy, location.longitude + dx)

        val nearestRoadPoint = roadSpatialIndex.nearestPoint(tempLoc, roadNetwork)
        val distToRoad       = roadSpatialIndex.distance(tempLoc, nearestRoadPoint)
        val maxRoadRadius    = 0.000025

        val finalLoc = if (distToRoad <= maxRoadRadius) {
            tempLoc
        } else {
            val angleBack = kotlin.math.atan2(
                tempLoc.latitude  - nearestRoadPoint.latitude,
                tempLoc.longitude - nearestRoadPoint.longitude
            )
            currentSpeed *= 0.8
            GeoPoint(
                nearestRoadPoint.latitude  + kotlin.math.sin(angleBack) * maxRoadRadius,
                nearestRoadPoint.longitude + kotlin.math.cos(angleBack) * maxRoadRadius
            )
        }

        _uiState.update {
            it.copy(
                currentLocation = finalLoc,
                vehicleSpeed    = currentSpeed,
                vehicleRotation = (currentRotation + 360) % 360f
            )
        }
    }

    private suspend fun tickNpcAi(location: GeoPoint) {
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
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    ws.sendMessage(gson.toJson(MultiplayerPlayer(
                        id              = myPlayerUUID,
                        displayName     = myPlayerDisplayName,
                        x               = location.longitude,
                        y               = location.latitude,
                        action          = _uiState.value.playerAction.name,
                        facingRight     = _uiState.value.isPlayerFacingRight,
                        isDriving       = _uiState.value.isDriving,
                        carModel        = _uiState.value.currentVehicleModel?.name,
                        carColor        = _uiState.value.currentVehicleColor,
                        vehicleRotation = _uiState.value.vehicleRotation
                    )))

                    if (isServerDelegatedHost) {
                        val despawnsToSend = synchronized(npcAiManager.pendingDespawns) {
                            val list = npcAiManager.pendingDespawns.toList()
                            npcAiManager.pendingDespawns.clear()
                            list
                        }
                        despawnsToSend.forEach { id ->
                            ws.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to id)))
                        }
                        if (processedNpcs.isNotEmpty()) {
                            val npcBatch = processedNpcs.map { npc ->
                                MultiplayerNpc(
                                    id         = npc.id,
                                    x          = npc.location.longitude,
                                    y          = npc.location.latitude,
                                    rotation   = npc.rotationAngle,
                                    npcType    = npc.type.name,
                                    ownerId    = myPlayerUUID,
                                    carModel   = npc.carModel?.name,
                                    carColor   = npc.carColor,
                                    hairId     = npc.visualConfig?.hairId,
                                    hairColor  = npc.visualConfig?.hairColor?.toArgb(),
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

    fun stopGameLoop() { gameLoopJob?.cancel(); gameLoopJob = null }

    private suspend fun applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network
        npcAiManager.updateRoadNetwork(network)
        val snapped = withContext(Dispatchers.Default) { roadSpatialIndex.nearestPoint(playerLocation, roadNetwork) }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(currentLocation = snapped, isRoadNetworkReady = true) }
        }
        val targetZoom = if (_uiState.value.mapProvider.isWebProvider) ZOOM_GAMEPLAY_WEB else ZOOM_GAMEPLAY_OSM
        if (_uiState.value.zoomLevel <= ZOOM_LOADING) {
            var z = ZOOM_LOADING + 1.0
            while (z <= targetZoom) {
                delay(120)
                withContext(Dispatchers.Main) { _uiState.update { it.copy(zoomLevel = z) } }
                z += 1.0
            }
        }
    }

    private fun maybeRefetchRoadNetwork(currentLoc: GeoPoint) {
        val moved = if (lastNetworkFetchLocation != null)
            roadSpatialIndex.distance(lastNetworkFetchLocation!!, currentLoc) else Double.MAX_VALUE
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
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                    }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(roadSource = RoadSource.NETWORK) }
                    }
                    val network = overpassRepository.fetchRoadNetwork(currentLoc.latitude, currentLoc.longitude)
                    if (network.isNotEmpty()) {
                        roadNetworkCache.put(currentLoc.latitude, currentLoc.longitude, network)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            roadNetwork = network
                            npcAiManager.updateRoadNetwork(network)
                            lastNetworkFetchLocation = currentLoc
                            _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB, isRoadNetworkReady = true) }
                        }
                    } else {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            _uiState.update { it.copy(isRoadNetworkReady = true) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error refetching road network", e)
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
        if (_uiState.value.tileSource != source) _uiState.update { it.copy(tileSource = source) }
    }

    fun moveCharacter(direction: Direction) {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        val isMovingRight = when (direction) {
            Direction.RIGHT -> true
            Direction.LEFT  -> false
            else            -> null
        }
        startMovementAction(isMovingRight)

        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003
        val temp = when (direction) {
            Direction.UP    -> GeoPoint(loc.latitude + step, loc.longitude)
            Direction.DOWN  -> GeoPoint(loc.latitude - step, loc.longitude)
            Direction.LEFT  -> GeoPoint(loc.latitude, loc.longitude - step)
            Direction.RIGHT -> GeoPoint(loc.latitude, loc.longitude + step)
        }
        snapToRoad(temp)
    }

    fun moveCharacterByAngle(angleRad: Double) {
        val loc = _uiState.value.currentLocation ?: return
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return

        val dx = cos(angleRad)
        startMovementAction(if (abs(dx) > 0.01) dx > 0 else null)

        val step = if (_uiState.value.isRunning) 0.000006 else 0.000003
        val temp = GeoPoint(
            loc.latitude  + sin(angleRad) * step,
            loc.longitude + cos(angleRad) * step
        )
        snapToRoad(temp)
    }

    private fun snapToRoad(temp: GeoPoint) {
        val nearest = roadSpatialIndex.nearestPoint(temp, roadNetwork)
        val dist    = roadSpatialIndex.distance(temp, nearest)
        val radius  = 0.000012
        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else {
            val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude)
            _uiState.update {
                it.copy(currentLocation = GeoPoint(
                    nearest.latitude  + sin(angle) * radius,
                    nearest.longitude + cos(angle) * radius
                ))
            }
        }
    }

    fun updateControlSettings(type: ControlType, scale: Float, swap: Boolean) {
        _uiState.update { it.copy(controlType = type, controlsScale = scale, swapControls = swap) }
    }

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
            provider == MapProvider.OSM && currentZoom < ZOOM_GAMEPLAY_OSM  -> ZOOM_GAMEPLAY_OSM
            provider.isWebProvider && currentZoom > ZOOM_GAMEPLAY_WEB       -> ZOOM_GAMEPLAY_WEB
            else                                                             -> currentZoom
        }
        _uiState.update { it.copy(mapProvider = provider, tileSource = ts, zoomLevel = newZoom) }
    }

    fun toggleCacheWidget(show: Boolean) { _uiState.update { it.copy(showCacheWidget = show) } }
    fun toggleFpsWidget(show: Boolean)   { _uiState.update { it.copy(showFpsWidget = show) } }
    fun updateShowCacheWidget(show: Boolean) = _uiState.update { it.copy(showCacheWidget = show) }
    fun updateShowFpsWidget(show: Boolean)   = _uiState.update { it.copy(showFpsWidget = show) }
    fun zoomIn()  = _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it }
    fun zoomOut() = _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it }

    override fun onCleared() {
        super.onCleared()
        stopGameLoop()
        messagesCollectorJob?.cancel()
        tileCache.closeAll()
        webSocketManager?.disconnect()
    }

    private var idleJob: Job? = null

    private fun startMovementAction(isMovingRight: Boolean? = null) {
        idleJob?.cancel()
        val newFacingRight = isMovingRight ?: _uiState.value.isPlayerFacingRight
        val currentAction  = if (_uiState.value.isRunning) PlayerAction.RUN else PlayerAction.WALK
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
                .filter { it.value.type == NpcType.CAR && roadSpatialIndex.distance(loc, it.value.location) <= INTERACT_RADIUS }
                .minByOrNull { roadSpatialIndex.distance(loc, it.value.location) }

            if (nearbyCarEntry != null) {
                val carNpc = nearbyCarEntry.value
                remoteEntities.remove(nearbyCarEntry.key)
                if (carNpc.isFirstTimeBoarded) spawnOustedDriver(carNpc.location)
                _uiState.update {
                    it.copy(
                        isDriving             = true,
                        currentVehicleModel   = carNpc.carModel,
                        currentVehicleColor   = carNpc.carColor,
                        vehicleRotation       = (carNpc.rotationAngle + 90f) % 360f,
                        vehicleSpeed          = 0.0,
                        vehicleIsFirstTimeBoarded = false
                    )
                }
                updateNpcsState()
            }
        } else {
            val abandonedCar = Npc(
                id                 = UUID.randomUUID().toString(),
                type               = NpcType.CAR,
                location           = loc,
                rotationAngle      = (_uiState.value.vehicleRotation + 270f) % 360f,
                speed              = 0.0,
                isMoving           = false,
                carModel           = _uiState.value.currentVehicleModel ?: CarModel.SEDAN,
                carColor           = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
                isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded
            )
            remoteEntities[abandonedCar.id] = abandonedCar
            _uiState.update {
                it.copy(
                    isDriving             = false,
                    currentVehicleModel   = null,
                    currentVehicleColor   = null,
                    vehicleSpeed          = 0.0,
                    vehicleIsFirstTimeBoarded = true
                )
            }
            updateNpcsState()
        }
    }

    // ─── LANDMARKS ───────────────────────────────────────────────────────────────

    fun loadLandmarks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                LandmarkCatalogManager.loadCatalog(context)
                val database = PowDatabase.getInstance(context)
                val dao = database.landmarkDao()
                var entities = dao.getAllLandmarks()

                if (entities.isEmpty()) {
                    try {
                        val jsonString = context.assets.open("default_landmarks.json").bufferedReader().use { it.readText() }
                        val type = object : TypeToken<List<LandmarkEntity>>() {}.type
                        val defaultEntities: List<LandmarkEntity> = Gson().fromJson(jsonString, type)
                        dao.insertLandmarks(defaultEntities)
                        entities = dao.getAllLandmarks()
                        Log.d("WorldMapVM", "Mapa sembrado con ${entities.size} edificios.")
                    } catch (e: java.io.FileNotFoundException) {
                        Log.w("WorldMapVM", "default_landmarks.json no encontrado.")
                    } catch (e: Exception) {
                        Log.e("WorldMapVM", "Error leyendo default_landmarks.json", e)
                    }
                }

                val templatesByAssetPath = LandmarkCatalogManager.availableAssets.associateBy { it.assetPath }
                val domainLandmarks = entities.map { entity ->
                    val template = templatesByAssetPath[entity.assetPath]
                    Landmark(
                        id               = entity.id,
                        name             = entity.name,
                        location         = GeoPoint(entity.latitude, entity.longitude),
                        assetPath        = entity.assetPath,
                        scaleFactor      = entity.scaleFactor,
                        rotationAngle    = entity.rotationAngle,
                        baseWidthMeters  = template?.baseWidthMeters  ?: 100f,
                        baseHeightMeters = template?.baseHeightMeters ?: 100f
                    )
                }
                _uiState.update { it.copy(landmarks = domainLandmarks) }
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error cargando landmarks", e)
            }
        }
    }

    // ─── MODO DISEÑADOR ──────────────────────────────────────────────────────────

    fun toggleDesignerMode(isDesigner: Boolean) {
        _uiState.update { it.copy(isDesignerMode = isDesigner, selectedLandmarkId = if (!isDesigner) null else it.selectedLandmarkId) }
    }

    fun showAssetPicker(show: Boolean) { _uiState.update { it.copy(showAssetPicker = show) } }

    fun selectLandmark(id: Long?) { _uiState.update { it.copy(selectedLandmarkId = id) } }

    fun addLandmarkAtPlayer(context: Context, template: LandmarkAssetTemplate) {
        val playerLoc = _uiState.value.currentLocation ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PowDatabase.getInstance(context).landmarkDao()
                val newId = dao.insertLandmark(LandmarkEntity(
                    name          = template.displayName,
                    latitude      = playerLoc.latitude,
                    longitude     = playerLoc.longitude,
                    assetPath     = template.assetPath,
                    scaleFactor   = template.defaultScale,
                    rotationAngle = 0f
                ))
                loadLandmarks(context)
                _uiState.update { it.copy(showAssetPicker = false, selectedLandmarkId = newId) }
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error al agregar landmark", e)
            }
        }
    }

    fun moveSelectedLandmark(dLat: Double, dLon: Double) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            state.copy(landmarks = state.landmarks.map {
                if (it.id == id) it.copy(location = GeoPoint(it.location.latitude + dLat, it.location.longitude + dLon))
                else it
            })
        }
    }

    fun rotateSelectedLandmark(angle: Float) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            state.copy(landmarks = state.landmarks.map { if (it.id == id) it.copy(rotationAngle = angle) else it })
        }
    }

    fun scaleSelectedLandmark(scale: Float) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            state.copy(landmarks = state.landmarks.map { if (it.id == id) it.copy(scaleFactor = scale) else it })
        }
    }

    fun deleteSelectedLandmark(context: Context) {
        val id = _uiState.value.selectedLandmarkId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dao = PowDatabase.getInstance(context).landmarkDao()
                dao.getLandmarkById(id)?.let {
                    dao.deleteLandmark(it)
                    loadLandmarks(context)
                    _uiState.update { s -> s.copy(selectedLandmarkId = null) }
                }
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error al borrar landmark", e)
            }
        }
    }

    fun saveSelectedLandmark(context: Context) {
        val id = _uiState.value.selectedLandmarkId ?: return
        val current = _uiState.value.landmarks.find { it.id == id } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                PowDatabase.getInstance(context).landmarkDao().updateLandmark(
                    LandmarkEntity(
                        id            = current.id,
                        name          = current.name,
                        latitude      = current.location.latitude,
                        longitude     = current.location.longitude,
                        assetPath     = current.assetPath,
                        scaleFactor   = current.scaleFactor,
                        rotationAngle = current.rotationAngle
                    )
                )
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error al guardar landmark", e)
            }
        }
    }

    fun exportLandmarksToUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entities  = PowDatabase.getInstance(context).landmarkDao().getAllLandmarks()
                val json      = Gson().toJson(entities)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error exportando landmarks", e)
            }
        }
    }

    fun importLandmarksFromUri(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json     = context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() } ?: return@launch
                val type     = object : TypeToken<List<LandmarkEntity>>() {}.type
                val imported : List<LandmarkEntity> = Gson().fromJson(json, type)
                val dao      = PowDatabase.getInstance(context).landmarkDao()
                dao.getAllLandmarks().forEach { dao.deleteLandmark(it) }
                dao.insertLandmarks(imported)
                loadLandmarks(context)
            } catch (e: Exception) {
                Log.e("WorldMapVM", "Error importando landmarks", e)
            }
        }
    }

    // ─── VEHÍCULO ────────────────────────────────────────────────────────────────

    fun steerLeft(pressed: Boolean)   { isSteeringLeftPressed  = pressed }
    fun steerRight(pressed: Boolean)  { isSteeringRightPressed = pressed }
    fun accelerate(pressed: Boolean)  { isGasPressed  = pressed }
    fun brake(pressed: Boolean)       { isBrakePressed = pressed }

    fun toggleTeleportMenu(show: Boolean) { _uiState.update { it.copy(showTeleportMenu = show) } }

    fun teleportTo(lat: Double, lon: Double) {
        _uiState.update {
            it.copy(
                currentLocation    = GeoPoint(lat, lon),
                showTeleportMenu   = false,
                isRoadNetworkReady = false
            )
        }
        lastNetworkFetchLocation = null
        lastFetchAttemptMs       = 0L
    }

    private fun spawnOustedDriver(carLocation: GeoPoint) {
        val colors = listOf(
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color.DarkGray,
            androidx.compose.ui.graphics.Color(0xFF8B4513),
            androidx.compose.ui.graphics.Color(0xFFDAA520)
        )
        val shirtColors = listOf(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color.Red,
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Green
        )
        val driver = Npc(
            id           = UUID.randomUUID().toString(),
            type         = NpcType.PERSON,
            location     = GeoPoint(carLocation.latitude + 0.00005, carLocation.longitude + 0.00005),
            speed        = NpcAiManager.PERSON_SPEED,
            isMoving     = true,
            visualConfig = CharacterVisualConfig(
                bodyFolder  = "npc_walk_1",
                bodyPrefix  = "npc_walk_1_",
                hairId      = (1..5).random(),
                hairColor   = colors.random(),
                shirtColor  = shirtColors.random(),
                pantsColor  = androidx.compose.ui.graphics.Color.DarkGray
            )
        )
        remoteEntities[driver.id] = driver
    }

    // ─── COLECCIONABLES ──────────────────────────────────────────────────────────

    private val isSpawningCollectible = AtomicBoolean(false)
    private var promptJob: Job? = null

    private fun trySpawningCollectible(playerLat: Double, playerLon: Double) {
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        if (_uiState.value.activeCollectibles.isNotEmpty() || !isSpawningCollectible.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uncollected = collectibleRepository.getUncollectedCollectibles()
                if (uncollected.isNotEmpty()) {
                    val itemToSpawn  = uncollected.random()
                    val bearing      = Math.random() * 2 * Math.PI
                    val distMeters   = 300.0 + Math.random() * 300.0
                    val clampedLat   = playerLat.coerceIn(-85.0, 85.0)
                    val deltaLat     = (distMeters * Math.cos(bearing)) / 111000.0
                    val deltaLon     = (distMeters * Math.sin(bearing)) / (111000.0 * Math.cos(Math.toRadians(clampedLat)))
                    val spawnNode    = roadSpatialIndex.nearestPoint(GeoPoint(playerLat + deltaLat, playerLon + deltaLon), roadNetwork)

                    val activeItem = ActiveCollectible(
                        id          = itemToSpawn.id,
                        name        = itemToSpawn.name,
                        description = itemToSpawn.description,
                        assetPath   = itemToSpawn.assetPath,
                        latitude    = spawnNode.latitude,
                        longitude   = spawnNode.longitude
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
        val activeItem = _uiState.value.activeCollectibles.firstOrNull() ?: return
        val distanceInMeters = GeoPoint(playerLat, playerLon)
            .distanceToAsDouble(GeoPoint(activeItem.latitude, activeItem.longitude))

        if (distanceInMeters <= 15.0) {
            if (_uiState.value.nearbyCollectible?.id != activeItem.id) {
                _uiState.update { it.copy(nearbyCollectible = activeItem) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    _uiState.update { it.copy(interactionPrompt = "PRESIONA X PARA RECOGER") }
                    delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
        } else if (_uiState.value.nearbyCollectible != null) {
            promptJob?.cancel(); promptJob = null
            _uiState.update { it.copy(nearbyCollectible = null, interactionPrompt = null) }
        }
    }

    fun onClaimCollectiblePressed() {
        val itemToClaim = _uiState.value.nearbyCollectible ?: return
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.claimCollectible(itemToClaim.id)
            withContext(Dispatchers.Main) {
                promptJob?.cancel(); promptJob = null
                _uiState.update {
                    it.copy(
                        activeCollectibles = emptyList(),
                        nearbyCollectible  = null,
                        interactionPrompt  = null,
                        showClaimedPopupFor = itemToClaim
                    )
                }
            }
        }
    }

    fun dismissClaimedPopup() { _uiState.update { it.copy(showClaimedPopupFor = null) } }

    // ─── COMBATE ─────────────────────────────────────────────────────────────────

    fun takeDamage(amount: Float) {
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++
        showHealthBar = true
        if (playerHealth > 30f) startHealthBarTimer(3000L) else healthBarJob?.cancel()
        if (playerHealth <= 0f) triggerWastedSequence()
    }

    fun heal(amount: Float) {
        playerHealth = (playerHealth + amount).coerceAtMost(maxPlayerHealth)
        showHealthBar = true
        if (playerHealth > 30f) startHealthBarTimer(3000L) else healthBarJob?.cancel()
    }

    fun showInitialHealthBar() { showHealthBar = true; startHealthBarTimer(4000L) }

    private fun startHealthBarTimer(delayMillis: Long) {
        healthBarJob?.cancel()
        healthBarJob = viewModelScope.launch {
            delay(delayMillis)
            showHealthBar = false
        }
    }

    private fun triggerWastedSequence() { /* Fase 3: pantalla WASTED */ }

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
                    (it.value.displayName?.isBlank() != false) &&
                    roadSpatialIndex.distance(playerLoc, it.value.location) <= ATTACK_RADIUS
                }
                .minByOrNull { roadSpatialIndex.distance(playerLoc, it.value.location) }

            if (targetNpcEntry != null) {
                val npcId      = targetNpcEntry.key
                val currentNpc = targetNpcEntry.value
                val newHealth  = (currentNpc.health - PLAYER_PUNCH_DAMAGE).coerceAtLeast(0f)
                if (newHealth <= 0f) {
                    remoteEntities[npcId] = currentNpc.copy(health = 0f, isDying = true)
                    updateNpcsState()
                    delay(1000L)
                    remoteEntities.remove(npcId)
                    try {
                        webSocketManager?.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to npcId)))
                    } catch (e: Exception) {
                        Log.e("Combat", "Error enviando NPC_DESTROY: ${e.message}")
                    }
                } else {
                    remoteEntities[npcId] = currentNpc.copy(health = newHealth)
                }
                updateNpcsState()
            }
        }
    }
}
