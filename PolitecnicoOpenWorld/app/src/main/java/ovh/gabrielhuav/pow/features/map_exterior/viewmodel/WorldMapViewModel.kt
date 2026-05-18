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
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.Landmark
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

// Clase de datos para el payload del servidor
data class MultiplayerPlayer(
    val type: String = "PLAYER_UPDATE",
    val id: String,
    val displayName: String = "",
    val x: Double,
    val y: Double,
    val action: String,
    val facingRight: Boolean,
    // Nuevos campos para sincronizar vehículos:
    val isDriving: Boolean = false,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null
)

// Clase para empaquetar un NPC en el JSON.
//
// IMPORTANTE: hairColor / shirtColor / pantsColor se serializan como Int ARGB (no como
// Long con el valor ULong interno de Compose Color). El valor de Compose Color codifica
// el ColorSpace en los bits altos; serializarlo como Long y reconstruir con Color(ULong)
// puede producir un ColorSpace inválido y hacer crashear toArgb() con
// ArrayIndexOutOfBoundsException. Usar Int ARGB es seguro y siempre interpreta sRGB.
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

// Modelo unificado para todos los mensajes del servidor
private data class ServerMessage(
    val type: String? = null,
    val id: String? = null,
    val sessionId: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val action: String? = null,
    val facingRight: Boolean? = null,
    val displayName: String? = null,
    // Nuevos campos para sincronizar vehículos:
    val isDriving: Boolean? = null,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null,
    val npc: MultiplayerNpc? = null,
    val npcs: List<MultiplayerNpc>? = null,
    val npcId: String? = null,
    val orphanedNpcs: List<String>? = null,
    val activeNpcIds: List<String>? = null,
    val isZoneHost: Boolean? = null
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
    var damagePulseTrigger by mutableStateOf(0) // Cambia para disparar la animación de golpe
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
    private var tickCount = 0
    private val isFetchingNetwork  = AtomicBoolean(false)
    private var lastFetchAttemptMs = 0L

    private val REFETCH_DISTANCE_DEG = 0.015
    private val REFETCH_COOLDOWN_MS  = 5 * 60 * 1000L

    // Variables de Control para Vehículos
    var isSteeringLeftPressed = false
    var isSteeringRightPressed = false
    var isGasPressed = false
    var isBrakePressed = false

    private val MAX_SPEED = 0.000017
    private val ACCELERATION = 0.0000003
    private val BRAKING_FRICTION = 0.000001
    private val INTERACT_RADIUS = 0.0005 // Rango para detectar autos

    private val PLAYER_PUNCH_DAMAGE = 15f

    private var lastAttackTime = 0L

    private val ATTACK_COOLDOWN_MS = 2400L // Tiempo entre cada puñetazo para sincronizar con la animación

    private val ATTACK_RADIUS = 0.00015     // Radio geográfico de alcance del golpe (corta distancia)

    // El game loop arranca aquí, atado al ciclo de vida del ViewModel (no del Composable).
    // Así, navegar a Settings y volver no detiene a los NPCs.
    init {
        // Inicializa la base de datos de coleccionables de forma segura en background
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
        }
        startGameLoop()
    }

// ─── WEBSOCKET MULTIJUGADOR ───────────────────────────────────────────────────

    private var webSocketManager: WebSocketManager? = null
    private var messagesCollectorJob: Job? = null
    private val gson = Gson()
    // Stable UUID that uniquely identifies this client for the lifetime of the ViewModel.
    // Updated to the server-assigned session ID upon receiving SESSION_INIT.
    private var myPlayerUUID = "Player_${UUID.randomUUID()}"
    // Display name chosen by the user (separate from the immutable UUID).
    private var myPlayerDisplayName = ""
    // Stores remote players AND NPC entities received from the server.
    private val remoteEntities = ConcurrentHashMap<String, Npc>()

    fun connectToMultiplayer(serverUrl: String, playerName: String) {
        myPlayerDisplayName = playerName
        if (webSocketManager == null) {
            Log.d("WorldMapVM", "Iniciando conexión multijugador a $serverUrl")
            webSocketManager = WebSocketManager(serverUrl)
            // Cancelamos cualquier collector previo antes de lanzar uno nuevo, para evitar
            // que se acumulen tras varios connect/disconnect (causaba doble procesado de
            // cada mensaje del servidor en sesiones largas).
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
                    // Adopt the server-assigned session ID as our authoritative player ID.
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
                    // EL SERVIDOR TE DA O TE QUITA EL PODER
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
                    // Borrar NPCs que dependían del jugador que se fue
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
                            // Protegemos firmemente a los jugadores reales
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

                else -> {
                    // Si es una actualización de posición de otro JUGADOR
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

                        // CORRECCIÓN: Se asigna SEDAN por defecto si es nulo, para respetar el tipo 'CarModel'
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
                            carModel = remoteCarModel, // <- El compilador ya aceptará esto
                            carColor = msg.carColor ?: 0xFFFFFFFF.toInt(),
                            visualConfig = if (!isRemoteDriving) multiplayerConfig else null,
                            displayName = msg.displayName
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

    // Función auxiliar para convertir el JSON en objeto Npc
    private fun addRemoteEntity(remote: MultiplayerNpc) {
        val npcType = try { NpcType.valueOf(remote.npcType) } catch(e: Exception) { NpcType.PERSON }

        val cModel = try {
            remote.carModel?.let { ovh.gabrielhuav.pow.domain.models.CarModel.valueOf(it) }
                ?: ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
        } catch (e: Exception) { ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN }
        val cColor = remote.carColor ?: 0xFFFFFFFF.toInt()

        // Reconstrucción de colores: los campos llegan como Int ARGB (ver MultiplayerNpc).
        // Color(Int) interpreta el entero como ARGB sRGB sin tocar bits de ColorSpace,
        // por lo que es seguro vs. Color(ULong) que asume el formato binario interno.
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

        // Velocidad canónica de NpcAiManager. Antes había constantes locales distintas que
        // aceleraban los NPCs adoptados cada vez que entraba un nuevo jugador a la zona.
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
            displayName = null // Aseguramos que no se confundan con jugadores
        )
    }

    private fun updateNpcsState() {
        // La FUENTE ÚNICA DE LA VERDAD. Se dibuja lo que el servidor diga, nada más.
        _uiState.update { it.copy(npcs = remoteEntities.values.toList()) }
    }
    // ─── GAME LOOP ───────────────────────────────────────────────────────────────

    fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch {

            while (_uiState.value.currentLocation == null) { delay(100) }
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

                        launch(Dispatchers.IO) {
                            roadNetworkCache.put(initialLoc.latitude, initialLoc.longitude, network)
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
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

            // Game loop principal ~30fps
            while (isActive) {
                try { // ESCUDO ANTI-CRASHEO INICIADO
                    _uiState.value.currentLocation?.let { location ->
                        // Intentamos generar uno si no hay ninguno (1 de cada 30 ticks para no saturar)
                        if (tickCount % 30 == 0) {
                            trySpawningCollectible(location.latitude, location.longitude)
                        }
                        // Revisamos constantemente si estamos parados sobre él
                        checkCollectibleProximity(location.latitude, location.longitude)

                        // --- CONDICIÓN DE COMBATE ---
                        if (_uiState.value.playerAction == PlayerAction.SPECIAL) {
                            performPlayerAttack()
                        }

                        // --- LÓGICA DE CONDUCCIÓN DEL JUGADOR ---
                        if (_uiState.value.isDriving) {
                            var currentSpeed = _uiState.value.vehicleSpeed
                            var currentRotation = _uiState.value.vehicleRotation

                            // Dirección (Flechas)
                            if (isSteeringLeftPressed && currentSpeed != 0.0) currentRotation -= 2f
                            if (isSteeringRightPressed && currentSpeed != 0.0) currentRotation += 2f

                            // Acelerador y Freno
                            if (isGasPressed) {
                                currentSpeed = (currentSpeed + ACCELERATION).coerceAtMost(MAX_SPEED)
                            } else if (isBrakePressed) {
                                currentSpeed -= BRAKING_FRICTION
                                if (currentSpeed < -MAX_SPEED / 2) currentSpeed = -MAX_SPEED / 2
                            } else {
                                if (currentSpeed > 0) currentSpeed = (currentSpeed - (ACCELERATION / 2)).coerceAtLeast(0.0)
                                if (currentSpeed < 0) currentSpeed = (currentSpeed + (ACCELERATION / 2)).coerceAtMost(0.0)
                            }

                            // CORRECCIÓN DEFINITIVA: Trigonometría Geográfica (0° = Norte/Arriba)
                            val angleRad = Math.toRadians(currentRotation.toDouble())

                            // El eje X (Longitud) se calcula con el Seno
                            val dx = kotlin.math.sin(angleRad) * currentSpeed
                            // El eje Y (Latitud) se calcula con el Coseno
                            val dy = kotlin.math.cos(angleRad) * currentSpeed

                            val tempLoc = GeoPoint(location.latitude + dy, location.longitude + dx)

                            // RESTRICCIÓN DE MAPA (NavMesh / Network)
                            val nearestRoadPoint = getNearestPointOnNetwork(tempLoc)
                            val distToRoad = distance(tempLoc, nearestRoadPoint)
                            val maxRoadRadius = 0.000025 // Tolerancia de salida de calle (ajustable)

                            val finalLoc = if (distToRoad <= maxRoadRadius) {
                                tempLoc // Flujo normal, está dentro del asfalto
                            } else {
                                // Se salió del camino, lo obligamos a quedarse en el borde
                                val angleBack = atan2(tempLoc.latitude - nearestRoadPoint.latitude, tempLoc.longitude - nearestRoadPoint.longitude)

                                // Penalización de choque: Pierde velocidad si intenta salirse de la calle
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
                        if (_uiState.value.isRoadNetworkReady) {
                            tickCount++
                            if (tickCount % 3 == 0) {
                                // 1. Damos SOLO los NPCs a la IA (Filtrando posibles jugadores basura)
                                val npcOnlyList = remoteEntities.values.filter { it.displayName.isNullOrEmpty() }
                                npcAiManager.setServerNpcs(npcOnlyList)

                                // 2. IA procesa físicas
                                npcAiManager.updateNpcs(location, isServerDelegatedHost)
                                val processedNpcs = npcAiManager.getServerNpcs()

                                // 3. Aplicar cambios locales
                                if (isServerDelegatedHost) {
                                    synchronized(npcAiManager.pendingDespawns) {
                                        npcAiManager.pendingDespawns.forEach { remoteEntities.remove(it) }
                                    }
                                    processedNpcs.forEach { remoteEntities[it.id] = it }
                                }
                                updateNpcsState()

                                // 4. Enviar datos por red
                                webSocketManager?.let { ws ->
                                    // Envolvemos todo el envío de red en un bloque seguro
                                    viewModelScope.launch(Dispatchers.IO) {
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
                                                vehicleRotation = _uiState.value.vehicleRotation
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
                                                    // Colores serializados como Int ARGB para evitar corrupción de ColorSpace.
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
                } catch (e: Exception) {
                    Log.e("GameLoop", "Crasheo evitado en el ciclo principal: ${e.message}")
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
                    withContext(Dispatchers.Main) {
                        roadNetwork = cached
                        npcAiManager.updateRoadNetwork(cached)
                        lastNetworkFetchLocation = currentLoc
                        _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                    }
                } else {
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
                            _uiState.update { it.copy(roadSource = RoadSource.LOCAL_DB) }
                        }
                    }
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
            provider == MapProvider.OSM && currentZoom < ZOOM_GAMEPLAY_OSM ->
                ZOOM_GAMEPLAY_OSM
            provider.isWebProvider && currentZoom > ZOOM_GAMEPLAY_WEB ->
                ZOOM_GAMEPLAY_WEB
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
        val currentAction = if (_uiState.value.isRunning) PlayerAction.RUN else PlayerAction.WALK

        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            if (_uiState.value.playerAction != currentAction || _uiState.value.isPlayerFacingRight != newFacingRight) {
                _uiState.update {
                    it.copy(playerAction = currentAction, isPlayerFacingRight = newFacingRight)
                }
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
            // --- 1. INTENTAR ABORDAR (A PIE -> AUTO) ---
            val nearbyCarEntry = remoteEntities.entries
                .filter { it.value.type == NpcType.CAR && distance(loc, it.value.location) <= INTERACT_RADIUS }
                .minByOrNull { distance(loc, it.value.location) }

            if (nearbyCarEntry != null) {
                val carId = nearbyCarEntry.key
                val carNpc = nearbyCarEntry.value

                remoteEntities.remove(carId)

                // LÓGICA DE INSTANCIACIÓN: Solo asustamos al conductor la primera vez
                if (carNpc.isFirstTimeBoarded) {
                    spawnOustedDriver(carNpc.location)
                }

                _uiState.update {
                    it.copy(
                        isDriving = true,
                        currentVehicleModel = carNpc.carModel,
                        currentVehicleColor = carNpc.carColor,
                        // CONVERSIÓN: De Sprite (NPC) a Jugador Local
                        vehicleRotation = (carNpc.rotationAngle + 90f) % 360f,
                        vehicleSpeed = 0.0,
                        vehicleIsFirstTimeBoarded = false
                    )
                }
                updateNpcsState()
            }
        } else {
            // --- 5. PERSISTENCIA: BAJAR DEL AUTO (AUTO -> A PIE) ---
            val abandonedCar = Npc(
                id = UUID.randomUUID().toString(),
                type = NpcType.CAR,
                location = loc,
                // CONVERSIÓN: De Jugador Local a Sprite (NPC abandonado)
                rotationAngle = (_uiState.value.vehicleRotation + 270f) % 360f,
                speed = 0.0,
                isMoving = false,
                carModel = _uiState.value.currentVehicleModel ?: CarModel.SEDAN,
                carColor = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
                // Hereda el estado de que ya fue robado
                isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded
            )

            remoteEntities[abandonedCar.id] = abandonedCar

            _uiState.update {
                it.copy(
                    isDriving = false,
                    currentVehicleModel = null,
                    currentVehicleColor = null,
                    vehicleSpeed = 0.0,
                    vehicleIsFirstTimeBoarded = true
                )
            }
            updateNpcsState()
        }
    }

    fun loadLandmarks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val database = PowDatabase.getInstance(context)
                val dao = database.landmarkDao()
                var entities = dao.getAllLandmarks()

                if (entities.isEmpty()) {
                    val escomDefault = LandmarkEntity(
                        name = "ESCOM",
                        latitude = 19.504505,
                        longitude = -99.146911,
                        assetPath = "BUILDINGS/IPN/building_escom.webp",
                        scaleFactor = 0.15f
                    )
                    dao.insertLandmarks(listOf(escomDefault))
                    entities = dao.getAllLandmarks()
                }

                val domainLandmarks = entities.map { entity ->
                    Landmark(
                        id = entity.id,
                        name = entity.name,
                        location = GeoPoint(entity.latitude, entity.longitude),
                        assetPath = entity.assetPath,
                        scaleFactor = entity.scaleFactor,
                        rotationAngle = entity.rotationAngle
                    )
                }

                android.util.Log.d("Landmarks", "Cargados ${domainLandmarks.size} landmarks desde Room")

                _uiState.update { currentState ->
                    currentState.copy(landmarks = domainLandmarks)
                }
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al cargar las estructuras estáticas", e)
            }
        }
    }

    // ─── MODO DISEÑADOR ──────────────────────────────────────────────────────

    fun toggleDesignerMode(isDesigner: Boolean) {
        _uiState.update { it.copy(isDesignerMode = isDesigner, selectedLandmarkId = if (!isDesigner) null else it.selectedLandmarkId) }
    }

    fun showAssetPicker(show: Boolean) {
        _uiState.update { it.copy(showAssetPicker = show) }
    }

    fun selectLandmark(id: Long?) {
        _uiState.update { it.copy(selectedLandmarkId = id) }
    }

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

                // Recargar para que aparezca en el mapa
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

                // Eliminar estructuras actuales
                val currentLandmarks = dao.getAllLandmarks()
                currentLandmarks.forEach { dao.deleteLandmark(it) }

                // Insertar desde el JSON (se insertan con su ID original para respetar el maestro)
                dao.insertLandmarks(importedEntities)

                // Recargar en el mapa
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

                // Mapear el objeto de dominio (Landmark) de regreso a la Entidad (LandmarkEntity)
                val updatedEntity = LandmarkEntity(
                    id = currentLandmark.id,
                    name = currentLandmark.name,
                    latitude = currentLandmark.location.latitude,
                    longitude = currentLandmark.location.longitude,
                    assetPath = currentLandmark.assetPath,
                    scaleFactor = currentLandmark.scaleFactor,
                    rotationAngle = currentLandmark.rotationAngle
                )

                // Ejecutar el update en la base de datos
                dao.updateLandmark(updatedEntity)

                // Opcional: Deseleccionar el asset automáticamente al guardar
                // _uiState.update { it.copy(selectedLandmarkId = null) }

            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al actualizar landmark", e)
            }
        }
    }

    // Genera un NPC peatón asustado/desalojado al lado del auto
    private fun spawnOustedDriver(carLocation: GeoPoint) {
        val offsetLoc = GeoPoint(carLocation.latitude + 0.00005, carLocation.longitude + 0.00005)

        // Generamos características físicas aleatorias para el NPC
        val randomHairId = (1..5).random()
        val randomHairColor = listOf(
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color.DarkGray,
            androidx.compose.ui.graphics.Color(0xFF8B4513), // Café
            androidx.compose.ui.graphics.Color(0xFFDAA520)  // Rubio
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
            speed = NpcAiManager.PERSON_SPEED, // Usa la velocidad canónica
            isMoving = true,
            visualConfig = visualConfig
        )
        remoteEntities[driver.id] = driver
    }

    fun toggleTeleportMenu(show: Boolean) {
        _uiState.update { it.copy(showTeleportMenu = show) }
    }

    fun teleportTo(lat: Double, lon: Double) {
        val newLocation = GeoPoint(lat, lon)
        _uiState.update {
            it.copy(
                currentLocation = newLocation,
                showTeleportMenu = false // Cerramos el menú tras hacer TP
            )
        }
    }

    fun steerLeft(pressed: Boolean) { isSteeringLeftPressed = pressed }
    fun steerRight(pressed: Boolean) { isSteeringRightPressed = pressed }
    fun accelerate(pressed: Boolean) { isGasPressed = pressed }
    fun brake(pressed: Boolean) { isBrakePressed = pressed }

    // ─── SISTEMA DE COLECCIONABLES ───────────────────────────────────────────────

    private var isSpawningCollectible = false

    private fun trySpawningCollectible(playerLat: Double, playerLon: Double) {
        // Si ya hay un coleccionable activo, o ya estamos calculando uno, salimos.
        if (_uiState.value.activeCollectibles.isNotEmpty() || isSpawningCollectible) return

        isSpawningCollectible = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uncollected = collectibleRepository.getUncollectedCollectibles()
                if (uncollected.isNotEmpty()) {
                    val itemToSpawn = uncollected.random()

                    val bearing = Math.random() * 2 * Math.PI
                    val distanceMeters = 300.0 + Math.random() * 300.0 // 300m – 600m
                    val clampedLat = playerLat.coerceIn(-85.0, 85.0)
                    val deltaLat = (distanceMeters * Math.cos(bearing)) / 111000.0
                    val deltaLon = (distanceMeters * Math.sin(bearing)) / (111000.0 * Math.cos(Math.toRadians(clampedLat)))
                    val offsetLat = playerLat + deltaLat
                    val offsetLon = playerLon + deltaLon

                    val tempLoc = org.osmdroid.util.GeoPoint(offsetLat, offsetLon)

                    // USAMOS LA FUNCIÓN DE TU VIEWMODEL QUE SÍ EXISTE
                    val spawnNode = getNearestPointOnNetwork(tempLoc)

                    val activeItem = ActiveCollectible(
                        id = itemToSpawn.id,
                        name = itemToSpawn.name,
                        description = itemToSpawn.description,
                        assetPath = itemToSpawn.assetPath,
                        latitude = spawnNode.latitude,   // En GeoPoint es .latitude
                        longitude = spawnNode.longitude  // y .longitude
                    )

                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(activeCollectibles = listOf(activeItem)) }
                    }
                }
            } finally {
                isSpawningCollectible = false
            }
        }
    }

    private var promptJob: kotlinx.coroutines.Job? = null

    private fun checkCollectibleProximity(playerLat: Double, playerLon: Double) {
        val activeItem = _uiState.value.activeCollectibles.firstOrNull() ?: return

        val playerGeo = org.osmdroid.util.GeoPoint(playerLat, playerLon)
        val itemGeo = org.osmdroid.util.GeoPoint(activeItem.latitude, activeItem.longitude)
        val distanceInMeters = playerGeo.distanceToAsDouble(itemGeo)

        val INTERACT_RADIUS_METERS = 15.0

        if (distanceInMeters <= INTERACT_RADIUS_METERS) {
            if (_uiState.value.nearbyCollectible?.id != activeItem.id) {
                // El jugador acaba de entrar a la zona del objeto
                _uiState.update { it.copy(nearbyCollectible = activeItem) }

                // Mostrar aviso arriba por 3 segundos
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    _uiState.update { it.copy(interactionPrompt = "PRESIONA X PARA RECOGER") }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
        } else {
            if (_uiState.value.nearbyCollectible != null) {
                _uiState.update { it.copy(nearbyCollectible = null) }
            }
        }
    }
    // Se llama cuando el usuario presiona el botón X (o el botón que designes)
    fun onClaimCollectiblePressed() {
        val itemToClaim = _uiState.value.nearbyCollectible ?: return

        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.claimCollectible(itemToClaim.id)

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeCollectibles = emptyList(), // Lo quitamos del mapa
                        nearbyCollectible = null,         // Ya no está cerca
                        showClaimedPopupFor = itemToClaim // Mostramos la tarjeta divertida
                    )
                }
            }
        }
    }

    fun dismissClaimedPopup() {
        _uiState.update { it.copy(showClaimedPopupFor = null) }
    }
    fun takeDamage(amount: Float) {
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++ // Dispara el efecto visual
        showHealthBar = true

        // Si la vida es menor al 30%, no ocultamos la barra (estado crítico)
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L) // Ocultar después de 3 segundos de no recibir daño
        } else {
            healthBarJob?.cancel() // Se queda visible permanentemente
        }

        if (playerHealth <= 0f) {
            triggerWastedSequence() // Lógica futura para morir
        }
    }

    fun heal(amount: Float) {
        playerHealth = (playerHealth + amount).coerceAtMost(maxPlayerHealth)
        showHealthBar = true

        // Si la vida sigue en el rango crítico, no ocultamos la barra
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
        // Aquí implementaremos la Fase 3: Pantalla "WASTED" y búsqueda de hospital
    }

    fun showInitialHealthBar() {
        showHealthBar = true
        startHealthBarTimer(4000L)
    }

    fun performPlayerAttack() {
        val now = System.currentTimeMillis()
        if (now - lastAttackTime < ATTACK_COOLDOWN_MS) return
        lastAttackTime = now


        // 🌟 Envolvemos en una corrutina para sincronizar con la animación visual
        viewModelScope.launch(Dispatchers.Default) {

            delay(300L) // Esperamos 300ms a que el puño "conecte" en la animación

            val playerLoc = _uiState.value.currentLocation ?: return@launch

            // Solo atacamos NPCs reales: no muriendo, en rango, de tipo PERSON y sin displayName de jugador remoto
            val targetNpcEntry = remoteEntities.entries
                .filter {
                    !it.value.isDying &&
                        it.value.type == NpcType.PERSON &&
                        (it.value.displayName?.isBlank() != false) &&
                        distance(playerLoc, it.value.location) <= ATTACK_RADIUS
                }
                .minByOrNull { distance(playerLoc, it.value.location) }

            if (targetNpcEntry != null) {
                val npcId = targetNpcEntry.key
                val currentNpc = targetNpcEntry.value

                val damage = PLAYER_PUNCH_DAMAGE
                val newHealth = (currentNpc.health - damage).coerceAtLeast(0f)

                if (newHealth <= 0f) {
                    // Inicia muerte progresiva
                    remoteEntities[npcId] = currentNpc.copy(health = 0f, isDying = true)
                    updateNpcsState()

                    delay(1000L) // Espera a que termine el fade-out
                    remoteEntities.remove(npcId)

                    try {
                        webSocketManager?.sendMessage(
                            gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to npcId))
                        )
                    } catch (e: Exception) {
                        Log.e("Combat", "Error enviando NPC_DESTROY: ${e.message}")
                    }
                    updateNpcsState()
                } else {
                    remoteEntities[npcId] = currentNpc.copy(health = newHealth)
                    updateNpcsState()
                }
            }
        }
    }
}