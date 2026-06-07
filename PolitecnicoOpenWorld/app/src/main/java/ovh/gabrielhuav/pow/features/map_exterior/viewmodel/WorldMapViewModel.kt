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
import java.io.InputStreamReader
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.ShineCTOLocation
import ovh.gabrielhuav.pow.data.repository.MetroRepository


class WorldMapViewModel(
    internal val roadNetworkCache: RoadNetworkCache,
    val tileCache: TileCache,
    internal val settingsRepository: SettingsRepository,
    internal val collectibleRepository: CollectibleRepository
) : ViewModel() {

    var playerHealth by mutableStateOf(100f)
        internal set
    val maxPlayerHealth = 100f

    // FX DE IMPACTO: cada incremento dispara un destello/💥 en pantalla. Lo usamos para
    // que se NOTE una colisión (NPC que te golpea, o atropello al conducir).
    var impactEffectTrigger by mutableStateOf(0)
        internal set
    internal fun fireImpactEffect() { impactEffectTrigger++ }

    var showHealthBar by mutableStateOf(false)
        internal set
    var damagePulseTrigger by mutableStateOf(0)
        internal set

    // Timestamp hasta el cual el jugador es inmune al daño (post-respawn / teletransporte).
    // Mientras System.currentTimeMillis() < respawnImmunityUntilMs, takeDamage es un no-op.
    // Esto evita que golpes "fantasma" de policías/NPCs que aún existen en la zona anterior
    // disparen la animación de daño justo al llegar a la nueva ubicación.
    @Volatile internal var respawnImmunityUntilMs: Long = 0L

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
    // Guardaremos el grafo de ESCOM en memoria para no leer el archivo cada vez
    private var escomNavGraph: LandmarkNavGraph? = null
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

    // ─── Grafo de calles para A* (pathfinding de la policía) ─────────────────
    // Adyacencia por id de nodo (calles que comparten nodo = intersección conectada),
    // posición de cada nodo, y una rejilla id→celda para hallar el nodo más cercano.
    internal var roadAdjacency: Map<Long, List<Long>> = emptyMap()
    internal var roadNodePos: Map<Long, GeoPoint> = emptyMap()
    internal var roadNodeGridById: Map<Pair<Int, Int>, List<Long>> = emptyMap()
    internal var routeCalculationJob: Job? = null
    internal var routeRetryJob: Job? = null
    internal var lastNetworkFetchLocation: GeoPoint? = null
    internal var gameLoopJob: Job? = null
    internal var tickCount = 0
    internal val isFetchingNetwork  = AtomicBoolean(false)
    internal var lastFetchAttemptMs = 0L

    // ─── Pre-descarga de tiles de la zona (offline) ──────────────────────────
    internal val tilePrefetch = ovh.gabrielhuav.pow.data.cache.TilePrefetchManager(tileCache)
    internal var lastPrefetchCellKey: String? = null

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
    internal val INTERACT_RADIUS = 0.00018   // ~18 m: hay que estar realmente junto al auto

    internal val PLAYER_PUNCH_DAMAGE = 15f
    internal var lastAttackTime = 0L
    internal val ATTACK_COOLDOWN_MS = 1200L
    internal val ATTACK_RADIUS = 0.00022

    // ─── ATROPELLO + REACCIONES DE NPC (host) ────────────────────────────────
    internal val RUN_OVER_RADIUS = 0.00003        // ~3 m alrededor del vehículo
    internal val RUN_OVER_MIN_SPEED = MAX_SPEED * 0.18  // por debajo no hace daño (estás casi parado)
    internal val NPC_CONTACT_RADIUS = 0.00006     // ~6.6 m: golpe del NPC agresivo (holgado)
    internal val NPC_CONTACT_DAMAGE = 10f
    internal val NPC_CONTACT_COOLDOWN_MS = 900L
    // Cooldown por NPC para que no drene la vida del jugador en cada tick.
    internal val npcContactCooldowns = ConcurrentHashMap<String, Long>()
    // Racha de golpes que le has dado a CADA NPC. A partir de RELENTLESS_HIT_STREAK
    // golpes seguidos, el NPC agresivo se vuelve IMPLACABLE: te persigue y golpea sin
    // parar hasta matarte (o hasta que muera). Cuanto más agresivo seas, peor para ti.
    internal val npcHitStreak = ConcurrentHashMap<String, Int>()
    internal val relentlessNpcs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    internal val RELENTLESS_HIT_STREAK = 6

    // ─── NIVEL DE BÚSQUEDA / POLICÍA ─────────────────────────────────────────
    internal val policeManager = ovh.gabrielhuav.pow.domain.models.ai.PoliceManager()
    internal val MAX_WANTED_LEVEL = 5
    // Policía REMOTA (de otros jugadores): solo se renderiza, no se simula. id -> (npc, lastSeenMs).
    internal val remotePolice = ConcurrentHashMap<String, Npc>()
    internal val remotePoliceSeen = ConcurrentHashMap<String, Long>()
    internal val REMOTE_POLICE_STALE_MS = 5000L
    // Decaimiento: el nivel baja si no cometes delitos durante un rato.
    @Volatile internal var lastCrimeTime = 0L
    @Volatile internal var lastWantedDecayTime = 0L
    @Volatile internal var lastPoliceBroadcast = 0L
    internal val POLICE_BROADCAST_MS = 120L   // ~8 Hz por la red (la simulación sigue a 30 Hz)
    internal val WANTED_DECAY_GRACE_MS = 25000L   // tiempo sin delito antes de empezar a bajar
    internal val WANTED_DECAY_STEP_MS = 15000L    // cada cuánto baja una estrella

    // Sube el nivel de búsqueda (con tope) y reinicia el contador de impunidad.
    internal fun raiseWantedLevel(amount: Int = 1) {
        lastCrimeTime = System.currentTimeMillis()
        val current = _uiState.value.wantedLevel
        if (current < MAX_WANTED_LEVEL) {
            _uiState.update { it.copy(wantedLevel = (current + amount).coerceAtMost(MAX_WANTED_LEVEL)) }
        }
    }

    // Baja el nivel de búsqueda gradualmente cuando dejas de delinquir.
    internal fun tickWantedDecay(now: Long) {
        val level = _uiState.value.wantedLevel
        if (level <= 0) return
        if (now - lastCrimeTime < WANTED_DECAY_GRACE_MS) return
        // Cuantas MÁS estrellas tengas, MÁS tarda en bajar cada una (el paso escala con el
        // nivel actual): 1★ baja en ~1×base, 5★ tarda ~5×base por estrella.
        if (now - lastWantedDecayTime < WANTED_DECAY_STEP_MS * level) return
        lastWantedDecayTime = now
        _uiState.update { it.copy(wantedLevel = (it.wantedLevel - 1).coerceAtLeast(0)) }
    }

    // ─── CARJACK (te bajan del vehículo) ─────────────────────────────────────
    internal val CARJACK_MS = 2500L                 // tiempo quieto antes de que te bajen
    internal val CARJACK_ADJ_RADIUS = 0.00009       // ~10 m: NPC agresivo pegado al coche
    @Volatile internal var carjackStartTime = 0L

    // ¿Hay algún NPC AGRESIVO (en embestida) pegado a tu coche?
    internal fun anyAggressorAdjacent(location: GeoPoint, now: Long): Boolean {
        for (npc in remoteEntities.values) {
            if (npc.type != NpcType.PERSON) continue
            if (npc.aggroUntil <= now) continue
            if (distance(location, npc.location) <= CARJACK_ADJ_RADIUS) return true
        }
        return false
    }

    // Gestiona el aviso y el descenso forzado del vehículo.
    internal fun handleCarjack(driving: Boolean, aggressorAdjacent: Boolean, now: Long) {
        if (!driving || !aggressorAdjacent) {
            if (carjackStartTime != 0L) {
                carjackStartTime = 0L
                if (_uiState.value.carjackWarning != null) {
                    _uiState.update { it.copy(carjackWarning = null) }
                }
            }
            return
        }
        // Si vas acelerando (te mueves), no pueden bajarte: reinicia el contador.
        val movingFast = kotlin.math.abs(_uiState.value.vehicleSpeed) > MAX_SPEED * 0.25
        if (movingFast) {
            if (carjackStartTime != 0L) {
                carjackStartTime = 0L
                _uiState.update { it.copy(carjackWarning = null) }
            }
            return
        }
        if (carjackStartTime == 0L) carjackStartTime = now
        _uiState.update { it.copy(carjackWarning = "¡Te van a bajar del auto! ¡Acelera!") }
        if (now - carjackStartTime >= CARJACK_MS) {
            carjackStartTime = 0L
            _uiState.update { it.copy(carjackWarning = null) }
            viewModelScope.launch(Dispatchers.Main) { forceExitVehicle() }
        }
    }

    // Baja al jugador del coche a la fuerza (deja el auto abandonado en su sitio).
    internal fun forceExitVehicle() {
        if (!_uiState.value.isDriving) return
        val loc = _uiState.value.currentLocation ?: return
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

    // Simula y difunde la policía PROPIA (el dueño del nivel de búsqueda). Se llama cada
    // tick del game loop. También purga la policía remota que dejó de actualizarse.
    internal fun runPoliceTick(location: GeoPoint) {
        val now = System.currentTimeMillis()
        tickWantedDecay(now)

        val wanted = _uiState.value.wantedLevel
        val driving = _uiState.value.isDriving
        val tick = policeManager.update(
            playerLat = location.latitude,
            playerLon = location.longitude,
            roadNetwork = roadNetwork,
            wantedLevel = wanted,
            canShoot = wanted >= 3,   // solo disparan con 3+ estrellas
            playerInVehicle = driving,
            now = now,
            snap = { gp -> getNearestPointOnNetwork(gp) },
            pathfind = { from, to -> findRoadRoute(from, to) }   // A* real por calles
        )

        // BALAS VISIBLES: guardamos los disparos nuevos con su timestamp y purgamos los
        // viejos (>280 ms), para dibujar un trazo breve desde el policía hacia ti.
        val prevShots = _uiState.value.policeShots
        val freshShots = if (tick.shots.isNotEmpty())
            tick.shots.map { PoliceShot(it.first, it.second, now) } else emptyList()
        if (freshShots.isNotEmpty() || prevShots.isNotEmpty()) {
            val kept = (prevShots + freshShots).filter { now - it.at <= 450L }
            if (kept != prevShots) _uiState.update { it.copy(policeShots = kept) }
        }

        // Daño que los policías te hacen (golpes/disparos). En coche NO te hacen daño
        // directo: te persiguen y, si te detienes, te bajan del vehículo (carjack).
        if (tick.damage > 0f && !driving) {
            viewModelScope.launch(Dispatchers.Main) { takeDamage(tick.damage) }
        }

        // CARJACK: si conduces y un perseguidor te alcanza, te avisa; si no aceleras (te
        // quedas casi quieto) durante CARJACK_MS, te bajan del coche. Aplica también a los
        // NPCs agresivos pegados a tu coche.
        val aggressorAdjacent = tick.adjacentThreat || (driving && anyAggressorAdjacent(location, now))
        handleCarjack(driving, aggressorAdjacent, now)

        // Purga de policía remota obsoleta (su dueño se alejó/desconectó).
        val staleCutoff = now - REMOTE_POLICE_STALE_MS
        val staleIds = remotePoliceSeen.filterValues { it < staleCutoff }.keys
        if (staleIds.isNotEmpty()) {
            staleIds.forEach { remotePolice.remove(it); remotePoliceSeen.remove(it) }
        }

        updateNpcsState()

        // Difusión a los demás clientes (para que vean mis patrullas/policías).
        // Throttle: los destroys siempre salen; el batch de posiciones, a ~8 Hz.
        val doBroadcastBatch = now - lastPoliceBroadcast >= POLICE_BROADCAST_MS
        if (doBroadcastBatch) lastPoliceBroadcast = now
        if (tick.destroyedIds.isEmpty() && (!doBroadcastBatch || tick.units.isEmpty())) return
        webSocketManager?.let { ws ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    tick.destroyedIds.forEach { pid ->
                        ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to pid)))
                    }
                    if (doBroadcastBatch && tick.units.isNotEmpty()) {
                        val batch = tick.units.map { u ->
                            MultiplayerNpc(
                                id = u.id,
                                x = u.location.longitude,
                                y = u.location.latitude,
                                rotation = u.rotationAngle,
                                npcType = u.type.name,
                                ownerId = myPlayerUUID
                            )
                        }
                        ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_BATCH_UPDATE", "npcs" to batch)))
                    }
                } catch (e: Exception) {
                    Log.e("Police", "Error difundiendo policía: ${e.message}")
                }
            }
        }
    }

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
        spawnShineCTOMarker() // AUTO-SPAWN: Coloca la entrada interactuable de Shine CTO al iniciar
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

                        // POLICÍA: nivel de búsqueda (spawn de patrullas, persecución,
                        // golpes/disparos) y decaimiento. La simula el dueño del nivel.
                        if (_uiState.value.isRoadNetworkReady && !_uiState.value.showWastedScreen) {
                            runPoliceTick(location)
                        }

                        maybeRefetchRoadNetwork(location)
                        if (_uiState.value.showRoadNetwork) {
                            updateVisibleRoads(location)
                        }

                        if (_uiState.value.isRoadNetworkReady) {
                            tickCount++
                            if (tickCount % 3 == 0L) {
                                val npcOnlyList = remoteEntities.values.filter { it.displayName.isNullOrEmpty() }
                                npcAiManager.setServerNpcs(npcOnlyList)

                                // Pasamos los landmarks (edificios) con sus navGraphs al motor
                                npcAiManager.setLandmarks(_uiState.value.landmarks.filter { it.navGraph != null })

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
                                                            pantsColor = npc.visualConfig?.pantsColor?.toArgb(),
                                                            health = npc.health,
                                                            isDying = npc.isDying,
                                                            aggroUntil = npc.aggroUntil
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

    private suspend fun applyRoadNetwork(network: List<MapWay>, playerLocation: GeoPoint) {
        roadNetwork = network
        rebuildRoadNodeGrid(network)
        buildRoadGraph(network)   // grafo para el A* de la policía
        npcAiManager.updateRoadNetwork(network)

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
        // Pinta las calles (líneas amarillas) de inmediato al quedar lista la red, sin
        // esperar al throttle del game loop (antes "tardaban en colocarse" tras entrar).
        updateVisibleRoads(snapped, force = true)
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

    private fun updateVisibleRoads(playerLoc: GeoPoint, force: Boolean = false) {
        val lastUpdate = lastVisibleRoadUpdateLocation
        if (!force && lastUpdate != null) {
            val dist = distance(playerLoc, lastUpdate)
            if (dist < VISIBLE_ROAD_UPDATE_THRESHOLD) return
        }
        lastVisibleRoadUpdateLocation = GeoPoint(playerLoc.latitude, playerLoc.longitude)

        viewModelScope.launch(Dispatchers.Default) {
            val visible = roadNetwork.filter { way ->
                way.nodes.any { node ->
                    distance(playerLoc, GeoPoint(node.lat, node.lon)) <= VISIBLE_ROAD_RADIUS
                }
            }
            _roadNetworkFlow.value = visible
        }
    }

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

                // ─── POLICÍA REMOTA (de otro jugador): solo render ───────────────
                "POLICE_BATCH_UPDATE" -> {
                    val now = System.currentTimeMillis()
                    msg.npcs?.forEach { p ->
                        if (p.ownerId != myPlayerUUID) {
                            val type = try { NpcType.valueOf(p.npcType) } catch (e: Exception) { NpcType.POLICE_COP }
                            remotePolice[p.id] = Npc(
                                id = p.id,
                                type = type,
                                location = GeoPoint(p.y, p.x),
                                rotationAngle = p.rotation,
                                speed = 0.0,
                                isRemote = true,
                                isMoving = true,
                                facingRight = cos(Math.toRadians(p.rotation.toDouble())) >= 0,
                                ownerId = p.ownerId,
                                policeDisembarked = type == NpcType.POLICE_COP
                            )
                            remotePoliceSeen[p.id] = now
                        }
                    }
                    updateNpcsState()
                }

                "POLICE_DESTROY" -> {
                    msg.npcId?.let {
                        remotePolice.remove(it)
                        remotePoliceSeen.remove(it)
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
            displayName = null,
            // Vida replicada del host: así los demás clientes ven la barra de vida y el
            // estado de muerte del NPC (atropellos/golpes) igual que el host.
            health = remote.health ?: 100f,
            isDying = remote.isDying ?: false,
            aggroUntil = remote.aggroUntil ?: 0L
        )
    }

    private fun updateNpcsState() {
        // Civiles/jugadores remotos + policía propia (simulada) + policía remota (render).
        val combined = remoteEntities.values + policeManager.activeUnits() + remotePolice.values
        _uiState.update { it.copy(npcs = combined.toList()) }
    }


    fun notifyTileSource(fromCache: Boolean) {
        if (_uiState.value.mapProvider == MapProvider.OSM) return
        val source = if (fromCache) TileSource.LOCAL_CACHE else TileSource.NETWORK
        if (_uiState.value.tileSource != source) {
            _uiState.update { it.copy(tileSource = source) }
        }
    }

    fun moveCharacter(direction: Direction) {
        if (_uiState.value.showWastedScreen) return // muerto: sin movimiento (WASTED)
        // Si el mapa está descentrado (exploración), el primer toque de los controles
        // de movimiento (izquierda) recentra en el jugador (SIN cambiar el zoom) en vez
        // de moverlo a ciegas fuera de cuadro.
        if (_uiState.value.isUserPanningMap) { centerOnPlayer(); return }
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
        if (_uiState.value.showWastedScreen) return // muerto: sin movimiento (WASTED)
        // Igual que moveCharacter: con el mapa descentrado, recentrar en el jugador (sin zoom).
        if (_uiState.value.isUserPanningMap) { centerOnPlayer(); return }
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

    private var debugNodeIdCounter = 1

    // 1. Función para cambiar el Checkbox
    fun toggleParkingMode(enabled: Boolean) {
        _uiState.update { it.copy(isParkingSlotMode = enabled) }
    }

    // 2. Función para empezar un nuevo carril
    fun startNewWay() {
        debugNodeIdCounter = 1
        _uiState.update {
            it.copy(
                currentWayId = it.currentWayId + 1,
                routeDebugWaypoints = emptyList() // Limpiamos las migas visuales (opcional)
            )
        }
        Log.d("CREADOR_RUTAS", "\n--- INICIANDO NUEVO CARRIL (Way ID: ${_uiState.value.currentWayId}) ---\n\"nodes\": [")
    }

    // 3. Tu función actualizada para capturar
    // 3. Tu función actualizada para capturar (Con margen de tolerancia)
    fun debugPlayerLocalCoordinates(context: Context) {
        val state = _uiState.value
        val loc = state.currentLocation ?: return
        val landmarkId = state.selectedLandmarkId ?: return
        val landmark = state.landmarks.find { it.id == landmarkId } ?: return

        val (localX, localY) = landmark.toLocalCoordinates(loc)

        // Esto compensa la distorsión curva del mapa y te deja puntear las orillas
        if (localX in -0.15f..1.15f && localY in -0.15f..1.15f) {
            val formX = String.format(java.util.Locale.US, "%.4f", localX)
            val formY = String.format(java.util.Locale.US, "%.4f", localY)

            // Usamos el flag del estado
            val isParking = state.isParkingSlotMode
            val desc = if (isParking) "Cajón de estacionamiento" else "Punto de ruta (Carril ${state.currentWayId})"

            val jsonNode = """
        {
          "id": $debugNodeIdCounter,
          "localX": $formX,
          "localY": $formY,
          "isParkingSlot": $isParking,
          "description": "$desc"
        },
        """.trimIndent()

            Log.d("CREADOR_RUTAS", "\n$jsonNode")

            // Agregamos la "miga de pan" a la lista para dibujarla
            _uiState.update {
                it.copy(routeDebugWaypoints = it.routeDebugWaypoints + loc)
            }

            android.widget.Toast.makeText(context, "Nodo $debugNodeIdCounter capturado", android.widget.Toast.LENGTH_SHORT).show()
            debugNodeIdCounter++
        } else {
            android.widget.Toast.makeText(context, "Estás fuera del edificio", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
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

    internal fun pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
    internal fun cell(v: Double): Int = floor(v / CELL).toInt()

    internal fun getNearestPointOnNetwork(t: GeoPoint): GeoPoint {
        // Usa la nueva matemática exacta del rectángulo rotado
        val insideLandmark = _uiState.value.landmarks.any { landmark ->
            landmark.contains(t)
        }

        if (insideLandmark) {
            return t // Eres 100% libre solo si estás tocando un píxel de la imagen
        }

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
            if (provider == MapProvider.GOOGLE_MAPS_NATIVE) {
                // SDK nativo de Google: las teselas las gestiona el SDK; progreso breve.
                for (i in 1..10) {
                    _uiState.update { it.copy(mapLoadProgress = i / 10f) }
                    delay(70)
                }
                _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
                return@launch
            }
            if (provider == MapProvider.OSM) {
                // OSM Nativo: descargar de VERDAD las teselas alrededor del jugador a
                // nivel MÁXIMO real (z19) y a un nivel MEDIO (z17) para que el mapa esté
                // listo y nítido al instante (incluido el over-zoom 20–22, que se escala
                // a partir de z19). Antes solo se simulaba progreso y por eso "no cargaba".
                downloadOsmNativeForEntry()
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

    // ─── COMPUERTA DE MAPA TRAS TELETRANSPORTE ────────────────────────────────
    // El teletransporte (ESCOM / "Ir a tu Ubicación") NO debe soltarte hasta que
    // el mapa de la zona esté descargado, sea cual sea el proveedor. Re-activa la
    // compuerta (isMapReady=false) y descarga el vecindario inmediato:
    //  - OSM nativo: guarda REAL en Room (bucket "osm") → render inmediato + offline.
    //  - Web: calienta el CDN (luego el WebView + CachingWebViewClient cachean a Room).
    //  - Google nativo: sin prefetch por URL, progreso breve.
    // Corre en paralelo a la recarga de calles; worldReady se cumple cuando AMBOS
    // (calles + tiles) terminan.
    internal fun gateMapDownloadAfterTeleport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isMapReady = false, mapLoadProgress = 0f) }
            downloadGateTiles()
            _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
        }
    }

    private suspend fun downloadGateTiles() = withContext(Dispatchers.IO) {
        val loc = _uiState.value.currentLocation ?: return@withContext
        val provider = _uiState.value.mapProvider
        if (provider == MapProvider.GOOGLE_MAPS_NATIVE) {
            // Mapa nativo de Google: las teselas las gestiona el SDK; solo simulamos
            // un breve progreso para mostrar la compuerta de forma consistente.
            for (i in 1..6) { _uiState.update { it.copy(mapLoadProgress = i / 6f) }; delay(60) }
            return@withContext
        }
        if (provider == MapProvider.OSM) {
            // OSM Nativo tras teletransporte: misma estrategia que la entrada (z19 + z17)
            // para que la nueva zona quede nítida y lista para el over-zoom.
            downloadOsmNativeForEntry()
            return@withContext
        }
        val z = if (provider.isWebProvider) ZOOM_GAMEPLAY_WEB.toInt() else 18
        val n = 1 shl z
        val xC = ((loc.longitude + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(loc.latitude)
        val yC = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        // Vecindario inmediato 3x3 (suficiente para soltar al jugador); el resto de la
        // zona (~2km) lo completa prefetchCurrentZoneTiles en segundo plano para offline.
        val coords = ArrayList<Pair<Int, Int>>()
        for (dx in -1..1) for (dy in -1..1) coords.add((xC + dx) to (yC + dy))
        val total = coords.size
        var done = 0
        for ((x, y) in coords) {
            if (!isActive) break
            val xx = x.coerceIn(0, n - 1); val yy = y.coerceIn(0, n - 1)
            if (provider == MapProvider.OSM) {
                val url = "https://tile.openstreetmap.org/$z/$xx/$yy.png"
                val key = sha256Hex(url)
                if (tileCache.getTileByUrl("osm", key) == null) {
                    val bytes = downloadTileBytes(url)
                    if (bytes != null && bytes.isNotEmpty()) tileCache.putTileByUrl("osm", key, bytes)
                }
            } else {
                tileUrlFor(provider, z, xx, yy)?.let { fetchTile(it) }
            }
            done++
            _uiState.update { it.copy(mapLoadProgress = done.toFloat() / total) }
        }
    }

    // ─── PREFETCH OSM NATIVO (pantalla de carga) ──────────────────────────────
    // Descarga y persiste en Room (bucket "osm", mismo esquema de clave que
    // RoomTileModuleProvider) un vecindario alrededor del jugador en DOS niveles:
    //  - z19 (máximo real de OSM): nitidez y base para el over-zoom 20–22.
    //  - z17 (medio): respaldo para alejar y para que el over-zoom tenga de dónde
    //    escalar aunque falte algún z19 puntual.
    private suspend fun downloadOsmNativeForEntry() = withContext(Dispatchers.IO) {
        val loc = _uiState.value.currentLocation
            ?: run { _uiState.update { it.copy(mapLoadProgress = 1f) }; return@withContext }
        // (zoom, radio en teselas). z19 con radio 2 (5x5) cubre la zona inmediata;
        // z17 con radio 1 (3x3) da contexto al alejar.
        val plan = listOf(19 to 2, 17 to 1)
        val total = plan.sumOf { (_, r) -> (2 * r + 1) * (2 * r + 1) }
        var done = 0
        for ((z, r) in plan) {
            if (!isActive) break
            val n = 1 shl z
            val xC = ((loc.longitude + 180.0) / 360.0 * n).toInt()
            val latRad = Math.toRadians(loc.latitude)
            val yC = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
            for (dx in -r..r) for (dy in -r..r) {
                if (!isActive) break
                val x = (xC + dx).coerceIn(0, n - 1)
                val y = (yC + dy).coerceIn(0, n - 1)
                cacheOsmTileToRoom(z, x, y)
                done++
                _uiState.update { it.copy(mapLoadProgress = (done.toFloat() / total).coerceIn(0f, 1f)) }
            }
        }
    }

    /** Descarga (si falta) un tile OSM canónico y lo guarda en Room bucket "osm". */
    private fun cacheOsmTileToRoom(z: Int, x: Int, y: Int) {
        val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
        val key = sha256Hex(url)
        if (tileCache.getTileByUrl("osm", key) != null) return
        val bytes = downloadTileBytes(url)
        if (bytes != null && bytes.isNotEmpty()) tileCache.putTileByUrl("osm", key, bytes)
    }

    private fun downloadTileBytes(url: String): ByteArray? {
        var c: java.net.HttpURLConnection? = null
        return try {
            c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 12000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PolitecnicoOpenWorld/1.0")
                setRequestProperty("Accept", "image/png,image/webp,image/*,*/*")
                setRequestProperty("Referer", "https://www.openstreetmap.org/")
            }
            if (c.responseCode == java.net.HttpURLConnection.HTTP_OK) c.inputStream.readBytes() else null
        } catch (e: Exception) { null } finally { c?.disconnect() }
    }

    private fun sha256Hex(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun toggleCacheWidget(show: Boolean) { _uiState.update { it.copy(showCacheWidget = show) } }
    fun toggleFpsWidget(show: Boolean) { _uiState.update { it.copy(showFpsWidget = show) } }
    fun updateShowCacheWidget(show: Boolean) = _uiState.update { it.copy(showCacheWidget = show) }
    fun updateShowFpsWidget(show: Boolean) = _uiState.update { it.copy(showFpsWidget = show) }

    fun zoomIn()  = _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = it.zoomLevel + 1.0) else it }
    fun zoomOut() = _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = it.zoomLevel - 1.0) else it }

    // Canal de retorno del zoom por GESTO (pinch de dos dedos) desde el mapa (web,
    // OSM nativo o Google). Sin esto, el bucle de render volvía a fijar el zoom al
    // valor del estado y el pinch "rebotaba". Acota a los límites de juego.
    fun onMapZoomChanged(zoom: Double) {
        if (!zoom.isFinite()) return
        // Cuantizamos a pasos de 0.5 y solo actualizamos si el cambio es grande. Así el
        // zoom por gesto no produce micro-cambios continuos que invaliden el estado (y con
        // él la caché de sprites de NPC, cuya clave depende del tamaño en píxeles → zoom).
        val z = (Math.round(zoom * 2.0) / 2.0).coerceIn(14.0, 22.0)
        if (Math.abs(z - _uiState.value.zoomLevel) >= 0.5) {
            _uiState.update { it.copy(zoomLevel = z) }
        }
    }

    fun centerOnPlayer() { _uiState.update { it.copy(isUserPanningMap = false) } }

    /** Centra en el jugador Y acerca al máximo nivel de zoom permitido. */
    fun zoomToPlayer() { _uiState.update { it.copy(isUserPanningMap = false, zoomLevel = 22.0) } }

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
                if (carNpc.isFirstTimeBoarded) {
                    spawnOustedDriver(carNpc.location)
                    raiseWantedLevel(1) // robar un auto ocupado es delito → +1 estrella
                }
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
        loadMetroStations(context)
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
                            rotationAngle = 285f,
                            scaleX = 0.50f,
                            scaleY = 0.50f
                        )
                    )
                    entities = dao.getAllLandmarks()
                }

                // Backfill: inserta las puertas del Deportivo Miguel Alemán si no existen
                var backfillNeeded = false
                if (entities.none { it.name == "Entrada Campo Béisbol" }) {
                    dao.insertLandmark(
                        LandmarkEntity(
                            name = "Entrada Campo Béisbol",
                            latitude = 19.494200,
                            longitude = -99.129200,
                            assetPath = "DOORS/ESCOM_DOOR.webp",
                            scaleFactor = 0.60f,
                            rotationAngle = 0.0f
                        )
                    )
                    backfillNeeded = true
                }
                if (entities.none { it.name == "Entrada Campo Fútbol" }) {
                    dao.insertLandmark(
                        LandmarkEntity(
                            name = "Entrada Campo Fútbol",
                            latitude = 19.492800,
                            longitude = -99.127800,
                            assetPath = "DOORS/ESCOM_DOOR.webp",
                            scaleFactor = 0.60f,
                            rotationAngle = 0.0f
                        )
                    )
                    backfillNeeded = true
                }
                if (backfillNeeded) {
                    entities = dao.getAllLandmarks()
                }

                val templatesByAssetPath = LandmarkCatalogManager.availableAssets.associateBy { it.assetPath }

                // Cargamos el navGraph de ESCOM en memoria si no está cargado.
                // Lo hacemos una sola vez para no abrir el archivo por cada edificio.
                if (escomNavGraph == null) {
                    try {
                        val inputStream = context.assets.open("navgraphs/escom_navgraph.json")
                        val reader = java.io.InputStreamReader(inputStream)
                        escomNavGraph = Gson().fromJson(reader, ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph::class.java)
                        reader.close()
                    } catch (e: Exception) {
                        Log.e("WorldMapViewModel", "No se pudo cargar el navGraph de ESCOM al inicio", e)
                    }
                }

                // Mapeamos las entidades de la Base de Datos a la clase Landmark
                val domainLandmarks = entities.map { entity ->
                    val template = templatesByAssetPath[entity.assetPath]

                    // Si el edificio es ESCOM, le inyectamos su navGraph.
                    // Si mañana agregas "Zacatenco", puedes poner "else if" aquí.
                    val assignedNavGraph = if (entity.assetPath.contains("building_escom", ignoreCase = true)) {
                        escomNavGraph
                    } else {
                        null // Los demás edificios nacen sin cerebro (por ahora)
                    }

                    // Coalesce de escala: las entidades sembradas desde default_landmarks.json
                    // (o JSON importados antiguos) NO traen scaleX/scaleY. Gson NO aplica los
                    // valores por defecto de Kotlin a campos primitivos ausentes, así que llegan
                    // como 0.0f. Un scaleX/scaleY de 0 colapsa el GroundOverlay a tamaño cero y
                    // el asset se vuelve INVISIBLE. Caemos a scaleFactor y, en último caso, a 1.0.
                    val effectiveScaleX = when {
                        entity.scaleX > 0f -> entity.scaleX
                        entity.scaleFactor > 0f -> entity.scaleFactor
                        else -> 1.0f
                    }
                    val effectiveScaleY = when {
                        entity.scaleY > 0f -> entity.scaleY
                        entity.scaleFactor > 0f -> entity.scaleFactor
                        else -> 1.0f
                    }

                    Landmark(
                        id = entity.id,
                        name = entity.name,
                        location = GeoPoint(entity.latitude, entity.longitude),
                        assetPath = entity.assetPath,
                        scaleX = effectiveScaleX,
                        scaleY = effectiveScaleY,
                        rotationAngle = entity.rotationAngle,
                        baseWidthMeters = template?.baseWidthMeters ?: 100f,
                        baseHeightMeters = template?.baseHeightMeters ?: 100f,

                        navGraph = assignedNavGraph
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
                    rotationAngle = 0f,
                    scaleX = template.defaultScale,
                    scaleY = template.defaultScale
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

    fun scaleXSelectedLandmark(scaleX: Float) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            val updated = state.landmarks.map {
                if (it.id == id) it.copy(scaleX = scaleX) else it
            }
            state.copy(landmarks = updated)
        }
    }

    fun scaleYSelectedLandmark(scaleY: Float) {
        val id = _uiState.value.selectedLandmarkId ?: return
        _uiState.update { state ->
            val updated = state.landmarks.map {
                if (it.id == id) it.copy(scaleY = scaleY) else it
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
                    scaleFactor = currentLandmark.scaleX,
                    rotationAngle = currentLandmark.rotationAngle,
                    scaleX = currentLandmark.scaleX,
                    scaleY = currentLandmark.scaleY
                )
                dao.updateLandmark(updatedEntity)
            } catch (e: Exception) {
                Log.e("WorldMapViewModel", "Error al actualizar landmark", e)
            }
        }
    }

    private fun spawnOustedDriver(carLocation: GeoPoint) {
        // El conductor desalojado aparece JUNTO al coche (~2 m), como si se bajara por
        // la puerta, en vez de a ~7 m de distancia (que se veía poco realista).
        val offsetLoc = GeoPoint(carLocation.latitude + 0.00002, carLocation.longitude + 0.00002)
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
        // REACCIÓN AL ROBO según personalidad: el cobarde huye (estado de miedo), el
        // agresivo te embiste (estado aggro) y el pasivo simplemente se aleja andando.
        val trait = NpcAiManager.rollTrait()
        val now = System.currentTimeMillis()
        val driver = Npc(
            id = UUID.randomUUID().toString(),
            type = NpcType.PERSON,
            location = offsetLoc,
            speed = NpcAiManager.PERSON_SPEED,
            isMoving = true,
            visualConfig = visualConfig,
            trait = trait,
            fearUntil = if (trait == ovh.gabrielhuav.pow.domain.models.NpcTrait.COWARD) now + NpcAiManager.FEAR_DURATION_MS else 0L,
            fearFromLat = carLocation.latitude,
            fearFromLon = carLocation.longitude,
            aggroUntil = if (trait == ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE) now + NpcAiManager.AGGRO_DURATION_MS else 0L,
            // Llama a la policía unos segundos (muestra 📞 sobre su cabeza).
            callingUntil = now + 4000L
        )
        remoteEntities[driver.id] = driver
    }


    fun teleportToMetroStation(stationName: String) {
        val station = _uiState.value.metroStations.find { it.name.equals(stationName, ignoreCase = true) }
        station?.let {
            teleportTo(it.location.latitude, it.location.longitude)
        }
    }

    fun loadMetroStations(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val stations = MetroRepository.loadStations(context)
            _uiState.update { it.copy(metroStations = stations) }
        }
    }

    fun toggleTeleportMenu(show: Boolean) { _uiState.update { it.copy(showTeleportMenu = show) } }

    fun teleportTo(lat: Double, lon: Double) {
        val newLocation = org.osmdroid.util.GeoPoint(lat, lon)
        // TELETRANSPORTE = borrón y cuenta nueva del combate: si no, los NPCs/policías que
        // te perseguían en la zona vieja quedaban con aggro y daban un golpe "fantasma"
        // justo al llegar. Limpiamos perseguidores, policía y nivel de búsqueda.
        // También se reinician los triggers de animación de daño y se activa la inmunidad
        // temporal para que ningún golpe residual de la zona anterior dispare la animación
        // al llegar al destino.
        relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
        damagePulseTrigger = 0
        impactEffectTrigger = 0
        respawnImmunityUntilMs = System.currentTimeMillis() + 2000L
        carjackStartTime = 0L
        val clearedPolice = policeManager.clearAll()
        for ((id, npc) in remoteEntities) {
            if (npc.aggroUntil > 0L) remoteEntities[id] = npc.copy(aggroUntil = 0L)
        }
        webSocketManager?.let { ws ->
            viewModelScope.launch(Dispatchers.IO) {
                clearedPolice.forEach { pid ->
                    try { ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to pid))) } catch (_: Exception) {}
                }
            }
        }
        _uiState.update {
            it.copy(
                currentLocation = newLocation,
                showTeleportMenu = false,
                isRoadNetworkReady = false,
                isMapReady = false,        // ← re-activa la compuerta: no soltar hasta descargar
                isUserPanningMap = false,  // ← recentra el mapa y reactiva la neblina
                wantedLevel = 0,
                carjackWarning = null
            )
        }
        lastNetworkFetchLocation = null
        lastFetchAttemptMs = 0L
        // Descarga el mapa de la nueva zona ANTES de soltar al jugador (en paralelo a
        // la recarga de calles). worldReady = calles listas && mapa listo.
        gateMapDownloadAfterTeleport()
    }

    // Cualquier control de conducción (girar/acelerar/frenar = X, ○, □) recentra en el
    // jugador si el mapa estaba descentrado. El botón △ (SALIR) NO recentra: bajarse del
    // coche es otra acción (onInteractButtonPressed).
    fun steerLeft(pressed: Boolean) { isSteeringLeftPressed = pressed; if (pressed) recenterIfPanning() }
    fun steerRight(pressed: Boolean) { isSteeringRightPressed = pressed; if (pressed) recenterIfPanning() }
    fun accelerate(pressed: Boolean) { isGasPressed = pressed; if (pressed) recenterIfPanning() }
    fun brake(pressed: Boolean) { isBrakePressed = pressed; if (pressed) recenterIfPanning() }
    private fun recenterIfPanning() { if (_uiState.value.isUserPanningMap) centerOnPlayer() }

    internal val isSpawningCollectible = AtomicBoolean(false)

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
        val playerGeo = org.osmdroid.util.GeoPoint(playerLat, playerLon)
        val INTERACT_RADIUS_METERS = 15.0

        // 1. Verificar cercanía a estaciones del metro
        val metroStations = _uiState.value.metroStations
        val nearbyMetro = metroStations.minByOrNull {
            playerGeo.distanceToAsDouble(it.location)
        }

        if (nearbyMetro != null && playerGeo.distanceToAsDouble(nearbyMetro.location) <= INTERACT_RADIUS_METERS) {
            if (_uiState.value.nearbyMetroStation?.name != nearbyMetro.name) {
                _uiState.update { it.copy(nearbyMetroStation = nearbyMetro, nearbyCollectible = null) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = "PRESIONA X PARA ENTRAR A ESTACIÓN ${nearbyMetro.name.uppercase()}"
                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
            return
        }

        // Si no está cerca de un metro, limpia el estado de metro
        if (_uiState.value.nearbyMetroStation != null) {
            _uiState.update { it.copy(nearbyMetroStation = null, interactionPrompt = null) }
        }

        // 2. Recopilamos los coleccionables normales y de ESCOM (nuestro código)
        val baseItems = _uiState.value.activeCollectibles + _escomItems.value

        // Convertimos los Landmarks de tipo "Puerta" en coleccionables virtuales interactuables
        val doorItems = _uiState.value.landmarks
            .filter { it.assetPath.contains("DOORS/") }
            .map { doorLandmark ->
                ActiveCollectible(
                    id = "escom_door_${doorLandmark.id}",
                    name = doorLandmark.name,
                    description = "Puerta interactiva",
                    assetPath = doorLandmark.assetPath,
                    latitude = doorLandmark.location.latitude,
                    longitude = doorLandmark.location.longitude
                )
            }

        // 3. Juntamos todo en un solo radar global
        val allPossibleItems = baseItems + doorItems

        val activeItem = allPossibleItems.minByOrNull {
            playerGeo.distanceToAsDouble(org.osmdroid.util.GeoPoint(it.latitude, it.longitude))
        } ?: return

        val itemGeo = org.osmdroid.util.GeoPoint(activeItem.latitude, activeItem.longitude)
        val distanceInMeters = playerGeo.distanceToAsDouble(itemGeo)

        // 4. Radio de detección especial para las puertas (20 metros) o estándar para objetos (15 metros)
        val radius = if (activeItem.id.startsWith("escom_door_")) ESCOM_DOOR_INTERACT_RADIUS * 100000 else 15.0

        if (distanceInMeters <= radius) {
            if (_uiState.value.nearbyCollectible?.id != activeItem.id) {
                _uiState.update { it.copy(nearbyCollectible = activeItem) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = when {
                        activeItem.name == "Objeto Misterioso ESCOM" -> "PRESIONA X PARA INTERACTUAR"
                        activeItem.id == ShineCTOLocation.MARKER_ID  -> "PRESIONA X PARA ENTRAR"
                        activeItem.id.startsWith("escom_door_")      -> "PRESIONA X PARA ENTRAR" // <--- Aquí aparece el texto de la puerta
                        else -> "PRESIONA X PARA RECOGER"
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
        // Inmunidad post-respawn / post-teletransporte: ignorar el daño durante los primeros
        // segundos tras reaaparecer para que ningún policía/NPC con aggro residual dispare
        // la animación de golpe de forma inesperada.
        if (System.currentTimeMillis() < respawnImmunityUntilMs) return
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++
        fireImpactEffect() // 💥 visible en CUALQUIER golpe que recibe el jugador
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
            // Al morir te bajas del coche (no se respawnea conduciendo) y se quita el pánico
            // de la zona. Tras la pantalla WASTED, respawn en el hospital más cercano.
            _uiState.update {
                it.copy(
                    showWastedScreen = true,
                    isDriving = false,
                    currentVehicleModel = null,
                    currentVehicleColor = null,
                    vehicleSpeed = 0.0
                )
            }
            delay(4000L)
            // Limpiar el estado de combate (rachas / NPCs implacables / cooldowns) para
            // no revivir siendo perseguido al instante.
            relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
            // Al morir se pierde el nivel de búsqueda, pero la policía NO desaparece de
            // golpe: con wantedLevel = 0 entra en modo retirada (se aleja hasta despawnear,
            // salvo que cometas un nuevo delito en tu nueva vida).
            carjackStartTime = 0L
            _uiState.update { it.copy(wantedLevel = 0, carjackWarning = null) }
            // RESPAWN EN LA MISMA ZONA YA DESCARGADA (ahorra recursos: no teletransporta a
            // ESCOM ni descarga teselas nuevas). Reaparece a ~80 m del lugar de muerte,
            // pegado a la red de calles ya cacheada; si no hay calles, en el mismo punto.
            val deathLoc = _uiState.value.currentLocation ?: GeoPoint(19.504505, -99.146911)
            val ang = Math.random() * 2.0 * Math.PI
            val r = 0.0007 // ~77 m
            val candidate = GeoPoint(deathLoc.latitude + sin(ang) * r, deathLoc.longitude + cos(ang) * r)
            val respawn = if (roadNetwork.isNotEmpty()) getNearestPointOnNetwork(candidate) else deathLoc
            _uiState.update { it.copy(currentLocation = respawn, showWastedScreen = false) }
            playerHealth = maxPlayerHealth
            // Reiniciar contadores de animación y activar inmunidad temporal (2 s) para que
            // ningún policía/NPC con aggro residual dispare la animación de daño justo al
            // reaaparecer. La inmunidad también cubre el teletransporte inmediato post-respawn.
            damagePulseTrigger = 0
            impactEffectTrigger = 0
            respawnImmunityUntilMs = System.currentTimeMillis() + 2000L
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
            // MIEDO AL COMBATE (SP y host MP): cada golpe asusta a los civiles cercanos,
            // CONECTE O NO. Así huyen cuando los atacas, aunque falles el puñetazo.
            if (isServerDelegatedHost) {
                npcAiManager.triggerFear(playerLoc.latitude, playerLoc.longitude)
            }
            // También puedes golpear a los POLICÍAS a pie cercanos (mueren si llegan a 0).
            val deadCops = policeManager.playerHitPolice(
                playerLoc.latitude, playerLoc.longitude, ATTACK_RADIUS, PLAYER_PUNCH_DAMAGE
            )
            if (deadCops.isNotEmpty()) {
                viewModelScope.launch(Dispatchers.Main) { fireImpactEffect(); updateNpcsState() }
                webSocketManager?.let { ws ->
                    viewModelScope.launch(Dispatchers.IO) {
                        deadCops.forEach { pid ->
                            try { ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to pid))) } catch (_: Exception) {}
                        }
                    }
                }
            }
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
                    // DELITO: golpear a un civil sube el nivel de búsqueda (como en GTA).
                    if (currentNpc.type == NpcType.PERSON) raiseWantedLevel(1)
                    val damage = PLAYER_PUNCH_DAMAGE
                    val newHealth = (currentNpc.health - damage).coerceAtLeast(0f)
                    if (newHealth <= 0f) {
                        npcHitStreak.remove(npcId)
                        relentlessNpcs.remove(npcId)
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
                        // CONTRAATAQUE: si el NPC golpeado sobrevive y es AGGRESSIVE, entra
                        // en estado de embestida hacia el jugador (lo persigue, visual) Y
                        // te DEVUELVE el golpe de forma garantizada poco después.
                        val retaliate = currentNpc.trait == ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE
                        remoteEntities[npcId] = currentNpc.copy(
                            health = newHealth,
                            aggroUntil = if (retaliate) System.currentTimeMillis() + NpcAiManager.AGGRO_DURATION_MS else currentNpc.aggroUntil
                        )
                        updateNpcsState()
                        if (retaliate) {
                            // Racha de golpes seguidos contra este NPC.
                            val streak = (npcHitStreak[npcId] ?: 0) + 1
                            npcHitStreak[npcId] = streak

                            // Golpe de vuelta DETERMINISTA: tras un breve "windup", si el NPC
                            // sigue vivo y el jugador continúa a su alcance, le pega y lo
                            // hace notar (vida baja + destello + 💥). No depende de la
                            // detección de contacto del bucle (que podía no dispararse).
                            viewModelScope.launch(Dispatchers.Main) {
                                delay(450L)
                                val pl = _uiState.value.currentLocation
                                val attacker = remoteEntities[npcId]
                                if (pl != null && attacker != null && !attacker.isDying &&
                                    !_uiState.value.isDriving &&
                                    distance(pl, attacker.location) <= ATTACK_RADIUS) {
                                    takeDamage(NPC_CONTACT_DAMAGE)
                                }
                            }

                            // IMPLACABLE: si lo golpeas RELENTLESS_HIT_STREAK veces o más, ya
                            // no para de pegarte hasta matarte (o hasta morir él).
                            if (streak >= RELENTLESS_HIT_STREAK && relentlessNpcs.add(npcId)) {
                                startRelentlessAttacker(npcId)
                            }
                        }
                    }
                }
            }
        }
    }

    // ATROPELLO: estando al volante, los peatones dentro de RUN_OVER_RADIUS reciben
    // daño proporcional a la velocidad; mueren si llegan a 0 y, en cualquier caso, los
    // testigos se asustan. Solo el host simula NPCs, así que solo él aplica esto.
    internal fun runOverNpcs(playerLoc: GeoPoint, speed: Double) {
        if (!isServerDelegatedHost) return
        val spd = kotlin.math.abs(speed)
        if (spd < RUN_OVER_MIN_SPEED) return
        val damage = (spd / MAX_SPEED).toFloat().coerceIn(0f, 1f) * 120f
        var changed = false
        for ((id, npc) in remoteEntities) {
            if (!npc.displayName.isNullOrEmpty()) continue   // no a jugadores remotos
            if (npc.type != NpcType.PERSON) continue          // solo peatones
            if (npc.isDying) continue
            if (distance(playerLoc, npc.location) > RUN_OVER_RADIUS) continue
            val newHealth = (npc.health - damage).coerceAtLeast(0f)
            if (newHealth <= 0f) {
                remoteEntities[id] = npc.copy(health = 0f, isDying = true)
                viewModelScope.launch { delay(1000L); remoteEntities.remove(id); updateNpcsState() }
                try {
                    webSocketManager?.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to id)))
                } catch (_: Exception) {}
            } else {
                remoteEntities[id] = npc.copy(health = newHealth)
            }
            changed = true
        }
        if (changed) {
            npcAiManager.triggerFear(playerLoc.latitude, playerLoc.longitude)
            viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() } // 💥 atropello
            updateNpcsState()
        }
    }

    // GOLPE DE NPC AGRESIVO: los NPCs en estado de embestida (aggro) que tocan al
    // jugador le hacen daño, con un cooldown por NPC para no vaciar la vida de golpe.
    internal fun applyNpcContactDamage(playerLoc: GeoPoint) {
        // SIN gate de host: el daño se aplica a TU PROPIO jugador en TU cliente, seas o no
        // el host de zona (el host solo decide quién SIMULA la IA, no quién recibe daño).
        val now = System.currentTimeMillis()
        for ((id, npc) in remoteEntities) {
            if (npc.aggroUntil <= now || npc.type != NpcType.PERSON) continue
            if (distance(playerLoc, npc.location) > NPC_CONTACT_RADIUS) continue
            val last = npcContactCooldowns[id] ?: 0L
            if (now - last < NPC_CONTACT_COOLDOWN_MS) continue
            if (_uiState.value.isDriving) continue // en coche no te golpean (te bajan, no te pegan)
            npcContactCooldowns[id] = now
            viewModelScope.launch(Dispatchers.Main) { takeDamage(NPC_CONTACT_DAMAGE) } // takeDamage ya dispara el 💥
        }
    }

    // IMPLACABLE: el NPC persigue y golpea al jugador sin descanso hasta matarlo (o hasta
    // morir/desaparecer). Refresca su aggro para que nunca deje de perseguir y le pega
    // cada NPC_CONTACT_COOLDOWN_MS si está a su alcance.
    private fun startRelentlessAttacker(npcId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                delay(NPC_CONTACT_COOLDOWN_MS)
                val npc = remoteEntities[npcId] ?: break        // murió/despawneó
                if (npc.isDying) break
                if (playerHealth <= 0f) break                    // jugador muerto → la secuencia WASTED sigue
                // Mantener vivo el aggro para que NO deje de perseguir.
                remoteEntities[npcId] = npc.copy(aggroUntil = System.currentTimeMillis() + NpcAiManager.AGGRO_DURATION_MS)
                val pl = _uiState.value.currentLocation
                if (pl != null && !_uiState.value.isDriving && distance(pl, npc.location) <= ATTACK_RADIUS) {
                    takeDamage(NPC_CONTACT_DAMAGE)
                }
            }
            relentlessNpcs.remove(npcId)
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
        val nearbyMetro = _uiState.value.nearbyMetroStation
        if (nearbyMetro != null) {
            _uiState.update { it.copy(showMetroFade = true) }
            return
        }

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
                val targetRoute = when (nearby.name) {
                    "Entrada Campo Béisbol" -> "interior_deportivo_beis"
                    "Entrada Campo Fútbol" -> "interior_deportivo_futbol"
                    else -> "zombie_minigame"
                }
                _uiState.update { it.copy(showEscomDoorFade = true, pendingDoorDestination = targetRoute) }
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
                isMapReady = false,         // ← re-activa la compuerta de descarga del mapa
                isUserPanningMap = false,   // ← igual que arriba
                isZombieHandSpawned = if (!insideEscom) false else currentState.isZombieHandSpawned
            )
        }

        lastNetworkFetchLocation = null
        lastFetchAttemptMs = 0L
        gateMapDownloadAfterTeleport()
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

    private fun isInsideEscom(lat: Double, lon: Double): Boolean {
        return abs(lat - ESCOM_BASE_LAT) < ESCOM_OFFSET &&
                abs(lon - ESCOM_BASE_LON) < ESCOM_OFFSET
    }


    fun checkDestinationArrival() {
        val destination = _uiState.value.destinationMarker ?: return
        val currentLoc = _uiState.value.currentLocation ?: return
        val distToDestinationMeters = currentLoc.distanceToAsDouble(destination)
        if (distToDestinationMeters <= _uiState.value.destinationArrivalThreshold) clearDestinationMarker()
    }

    fun spawnDynamicCarInEscom(context: Context) {
        // 1. Cargar el JSON del navgraph de ESCOM si no está en memoria
        if (escomNavGraph == null) {
            try {
                val inputStream = context.assets.open("navgraphs/escom_navgraph.json")
                val reader = java.io.InputStreamReader(inputStream)
                escomNavGraph = Gson().fromJson(reader, ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph::class.java)
                reader.close()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error leyendo escom_navgraph.json", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        val navGraph = escomNavGraph ?: return

        // 2. Buscar el edificio ESCOM en el mapa
        val escomLandmarkBase = _uiState.value.landmarks.find { it.assetPath.contains("building_escom", ignoreCase = true) }
        if (escomLandmarkBase == null) {
            android.widget.Toast.makeText(context, "Error: ESCOM no está en el mapa", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // CRUCIAL: Inyectarle el navGraph al Landmark para que la IA (NpcAiManager) pueda leer las "entryWays"
        val escomLandmark = escomLandmarkBase.copy(navGraph = navGraph)

        // 3. Obtener el carril de entrada (el objeto real LocalWay)
        val entryWayId = navGraph.entryWays.firstOrNull() ?: return
        val entryWay = navGraph.ways.find { it.id == entryWayId } ?: return
        val entryNode = entryWay.nodes.firstOrNull() ?: return

        // 4. Calcular posición global real en base al nodo local 0,0
        val spawnGeoPoint = escomLandmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

        // 5. Crear el NPC con los estados exactos que exige el motor de IA
        val newCarId = "DYN_CAR_${System.currentTimeMillis()}"
        val newCar = ovh.gabrielhuav.pow.domain.models.Npc(
            id = newCarId,
            type = ovh.gabrielhuav.pow.domain.models.NpcType.CAR,
            location = spawnGeoPoint,
            carColor = android.graphics.Color.WHITE,
            carModel = ovh.gabrielhuav.pow.domain.models.CarModel.SPORT,
            rotationAngle = 0f,
            speed = ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager.CAR_SPEED,

            // 👇 PROPIEDADES QUE EVITAN QUE LA IA LO ELIMINE
            navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
            currentLandmark = escomLandmark, // Pasamos el objeto con el navGraph
            currentLocalWay = entryWay,      // Pasamos el objeto de la calle
            targetNodeIndex = 1,             // Le decimos que avance al nodo 1
            moveDirection = 1                // Dirección hacia adelante
        )

        // 6. Inyectarlo a la FUENTE DE LA VERDAD (remoteEntities)
        remoteEntities[newCarId] = newCar

        // 7. Refrescar la pantalla
        updateNpcsState()

        android.widget.Toast.makeText(context, "🚗 Auto inyectado en MICRO_LANDMARK", android.widget.Toast.LENGTH_SHORT).show()
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

    fun consumeEscomDoorNavigation(): String? {
        val dest = _uiState.value.pendingDoorDestination
        _uiState.update { it.copy(escomDoorFadeComplete = false, pendingDoorDestination = null) }
        return dest
    }

    // ─── Metro Stations Fade ───────────────────────────────────────────────────
    fun onMetroFadeComplete() {
        val station = _uiState.value.nearbyMetroStation
        if (station != null) {
            _uiState.update {
                it.copy(
                    showMetroFade = false,
                    metroFadeCompleteStation = station,
                    nearbyMetroStation = null,
                    interactionPrompt = null
                )
            }
        }
    }

    fun consumeMetroFadeComplete() {
        _uiState.update { it.copy(metroFadeCompleteStation = null) }
    }
}