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
import ovh.gabrielhuav.pow.data.repository.MetrobusRepository
import ovh.gabrielhuav.pow.domain.models.ExteriorCollisionsConfig

class WorldMapViewModel(
    application: android.app.Application,
    internal val roadNetworkCache: RoadNetworkCache,
    val tileCache: TileCache,
    internal val settingsRepository: SettingsRepository,
    internal val collectibleRepository: CollectibleRepository
) : androidx.lifecycle.AndroidViewModel(application) {

    internal val soundManager = ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(application)

    var playerHealth by mutableStateOf(100f)
        internal set
    val maxPlayerHealth = 100f

    // FX DE IMPACTO: cada incremento dispara un destello/💥 en pantalla. Lo usamos para
    // que se NOTE una colisión (NPC que te golpea, o atropello al conducir).
    var impactEffectTrigger by mutableStateOf(0)
        internal set
    // Throttle del 💥: con muchos zombis/NPCs golpeándote, applyNpcContactDamage llamaba a
    // fireImpactEffect cada mordida (~cada 900 ms por atacante) y el 💥 central se veía "a cada
    // rato". Limitamos a uno cada IMPACT_EFFECT_THROTTLE_MS para que siga marcando colisiones
    // notables sin spamear.
    private var lastImpactEffectMs = 0L
    private val IMPACT_EFFECT_THROTTLE_MS = 900L
    // Última horda migratoria avisada al jugador (para no repetir el aviso del HUD).
    private var lastHordeSeenMs = 0L
    internal fun fireImpactEffect() {
        val now = System.currentTimeMillis()
        if (now - lastImpactEffectMs < IMPACT_EFFECT_THROTTLE_MS) return
        lastImpactEffectMs = now
        impactEffectTrigger++
    }

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
            val vm = WorldMapViewModel(
                application = appCtx as android.app.Application,
                roadNetworkCache = RoadNetworkCache(database.roadNetworkDao()),
                tileCache        = TileCache(database.mapTileDao()),
                settingsRepository = SettingsRepository(appCtx),
                collectibleRepository = CollectibleRepository(database.collectibleDao())
            )
            // GAMA DEL TELÉFONO: escala la población de NPCs (menos en equipos débiles, más en
            // gama alta). Combina con la densidad urbana (urbanFactor) y el ajuste del usuario.
            vm.npcAiManager.deviceTierFactor = computeDeviceTierFactor(appCtx)
            vm.npcAiManager.userPopulationFactor = vm.settingsRepository.getNpcDensity()
            return vm as T
        }

        // RAM total (y isLowRamDevice) → factor de población. No persiste; se calcula al crear el VM.
        private fun computeDeviceTierFactor(ctx: Context): Float = try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val mi = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            val gb = mi.totalMem / (1024.0 * 1024.0 * 1024.0)
            when {
                am.isLowRamDevice || gb <= 2.2 -> 0.6f   // gama baja (≤2 GB / Android Go)
                gb <= 4.2 -> 1.0f                         // gama media (≤4 GB)
                gb <= 6.2 -> 1.3f                         // gama alta (≤6 GB)
                else       -> 1.5f                        // tope (no saturar gama alta)
            }
        } catch (e: Exception) { 1.0f }
    }

    internal val _uiState = MutableStateFlow(
        WorldMapState(
            controlType   = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls  = settingsRepository.getSwapControls(),
            selectedSkin  = settingsRepository.getPlayerSkin(),   // ← NUEVO
            npcEmojiLod   = settingsRepository.getNpcEmojiLod(),  // optimizar dibujado de NPCs (LOD)
            npcFullEmoji  = settingsRepository.getNpcFullEmoji(), // optimizar para gama baja (emoji total)
            showZoomWidget = settingsRepository.getShowZoomWidget(),
            showSpeedometer = settingsRepository.getShowSpeedometer(),
            showCoordsWidget = settingsRepository.getShowCoordsWidget()
        )
    )
    // Guardaremos el grafo de ESCOM en memoria para no leer el archivo cada vez
    internal var escomNavGraph: LandmarkNavGraph? = null // usado por WorldMapDesigner.kt
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
    // 🆕 ¿El tick anterior el jugador estaba en zona libre (ESCOM/ENCB)? Sirve para FORZAR un
    // repintado inmediato de las calles en el tick en que SALE de la zona (el flow quedó vacío
    // y el throttle por distancia lo suprimiría porque el campus es pequeño).
    internal var wasInFreeMovementZone = false
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
    internal val ATTACK_RADIUS = 0.00008      // ~9 m: golpe CUERPO A CUERPO (antes 0.00022 ≈ ~24 m, pegaba de lejos)

    // ─── ATROPELLO + REACCIONES DE NPC (host) ────────────────────────────────
    internal val RUN_OVER_RADIUS = 0.00003        // ~3 m alrededor del vehículo
    internal val RUN_OVER_MIN_SPEED = MAX_SPEED * 0.18  // por debajo no hace daño (estás casi parado)
    // MIDNIGHT CLUB: los PEATONES tienen reflejos sobrehumanos — esquivan SIEMPRE salvo que vayas
    // EXTREMADAMENTE rápido (>= este umbral, casi a fondo). Los zombis no esquivan (shamblean).
    internal val RUN_OVER_EXTREME_SPEED = MAX_SPEED * 0.92
    // El peatón EMPIEZA a esquivar a esta distancia (algo mayor que RUN_OVER_RADIUS) y solo si está
    // DELANTE del coche → da tiempo a animar el sidestep antes del contacto (no teletransporte).
    internal val DODGE_TRIGGER_RADIUS = 0.00007   // ~7-8 m por delante
    internal val DODGE_MS = 480L                  // duración del sidestep animado
    // Radio de choque coche-coche: al pasar tan cerca de un auto NPC, 💥 + lo empujas (rebasas).
    internal val CAR_BUMP_RADIUS = 0.000045
    internal val NPC_CONTACT_RADIUS = 0.00006     // ~6.6 m: golpe del NPC agresivo (holgado)
    internal val NPC_CONTACT_DAMAGE = 10f
    internal val NPC_CONTACT_COOLDOWN_MS = 900L
    // Cooldown por NPC para que no drene la vida del jugador en cada tick.
    internal val npcContactCooldowns = ConcurrentHashMap<String, Long>()
    // Cooldown GLOBAL de mordida de zombi: con una horda encima, recibes como mucho UNA mordida
    // cada ZOMBIE_BITE_TO_PLAYER_MS → daño moderado (no muerte instantánea por estar rodeado).
    @Volatile internal var lastZombieBiteMs = 0L
    internal val ZOMBIE_BITE_TO_PLAYER_MS = 650L
    internal val ZOMBIE_BITE_TO_PLAYER_DMG = 6f
    // Racha de golpes que le has dado a CADA NPC. A partir de RELENTLESS_HIT_STREAK
    // golpes seguidos, el NPC agresivo se vuelve IMPLACABLE: te persigue y golpea sin
    // parar hasta matarte (o hasta que muera). Cuanto más agresivo seas, peor para ti.
    internal val npcHitStreak = ConcurrentHashMap<String, Int>()
    internal val relentlessNpcs = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    internal val RELENTLESS_HIT_STREAK = 6

    // ─── NIVEL DE BÚSQUEDA / POLICÍA ─────────────────────────────────────────
    internal val policeManager = ovh.gabrielhuav.pow.domain.models.ai.PoliceManager()
    internal val MAX_WANTED_LEVEL = 5

    // ─── POLICÍA DE LA CAMPAÑA (Modo Historia · Misión 1) ────────────────────
    // SEPARADA del sistema de búsqueda del mundo libre para que no choquen los comportamientos.
    // Son 2 policías a pie que siguen al jugador a distancia (ver WorldMapCampaignPolice.kt).
    internal val campaignEscortPolice = ovh.gabrielhuav.pow.domain.models.ai.CampaignEscortPolice()
    // Spawn diferido (una vez por activación); setStorySpawn la re-arma en cada entrada de campaña.
    internal var campaignPoliceActivated = false
    // MISIÓN 2: persecución de 6 policías + multitud saliendo de la ESCOM (ver WorldMapCampaignPolice.kt).
    internal var mission2ChaseActivated = false
    // MISIÓN 2: true una vez que Prankedy ENTRA a la ESCOM (huyendo); deja de animarse a partir de ahí.
    internal var mission2PrankedyEntered = false
    // MISIÓN 2: posición EXACTA donde Prankedy desespawneó al meterse a la ESCOM. La policía del REMATE
    // se reúne AQUÍ a "platicar" (no en la puerta del objetivo, que queda unos metros más allá).
    internal var mission2PrankedyExitPoint: org.osmdroid.util.GeoPoint? = null
    // Multitud de NPCs que SALEN de la puerta de la ESCOM (hora de salida) y se despawnean al
    // salir de tu fog of war. Lista propia (no la toca NpcAiManager); se fusiona en uiState.npcs.
    internal val mission2Crowd = ConcurrentHashMap<String, Npc>()
    internal var mission2CrowdLastSpawn = 0L

    // ─── PRANKEDY (NPC compañero) ─────────────────────────────────────────────
    internal val prankedyManager = ovh.gabrielhuav.pow.domain.models.ai.PrankedyManager()
    // Policía REMOTA (de otros jugadores): solo se renderiza, no se simula. id -> (npc, lastSeenMs).
    internal val remotePolice = ConcurrentHashMap<String, Npc>()
    internal val remotePoliceSeen = ConcurrentHashMap<String, Long>()
    internal val REMOTE_POLICE_STALE_MS = 5000L
    // Decaimiento: el nivel baja si no cometes delitos durante un rato.
    @Volatile internal var lastCrimeTime = 0L
    @Volatile internal var lastWantedDecayTime = 0L
    @Volatile internal var lastPoliceBroadcast = 0L
    @Volatile internal var lastDodgeTime = 0L
    internal val POLICE_BROADCAST_MS = 120L   // ~8 Hz por la red (la simulación sigue a 30 Hz)
    internal val WANTED_DECAY_GRACE_MS = 25000L   // tiempo sin delito antes de empezar a bajar
    internal val WANTED_DECAY_STEP_MS = 15000L    // cada cuánto baja una estrella

    // ─── NIVEL DE BÚSQUEDA / POLICÍA / CARJACK (REFACTOR) ─────────────────────
    // raiseWantedLevel / tickWantedDecay / anyAggressorAdjacent / handleCarjack /
    // forceExitVehicle / runPoliceTick viven en WorldMapWanted.kt. Aquí queda solo
    // el ESTADO que esas extensiones usan:
    internal val CARJACK_MS = 2500L                 // tiempo quieto antes de que te bajen
    internal val CARJACK_ADJ_RADIUS = 0.00009       // ~10 m: NPC agresivo pegado al coche
    @Volatile internal var carjackStartTime = 0L

    internal val hospitalRespawnPoints = listOf(
        GeoPoint(19.5034, -99.1469),
        GeoPoint(19.4990, -99.1350),
        GeoPoint(19.5070, -99.1400)
    )

    internal val ESCOM_BASE_LAT = 19.50456
    internal val ESCOM_BASE_LON = -99.14674
    internal val ESCOM_OFFSET = 0.001

    // ─── ZONA LIBRE DE LA ENCB (Modo Historia) ───────────────────────────────
    // Bounding box centrado en el spawn de la ENCB. Dentro de él se SUSPENDE la
    // restricción de malla vial (igual que en ESCOM): el jugador y Prankedy se
    // mueven 100% libres por explanadas/áreas verdes. Offset ligeramente mayor que
    // el de ESCOM para cubrir todo el campus (~0.0012° ≈ 130 m de radio).
    internal val ENCB_BASE_LAT = 19.5001588
    internal val ENCB_BASE_LON = -99.1450298
    internal val ENCB_OFFSET = 0.0012

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
        
        // Si ya tenemos una ubicación (p. ej. tras una restauración de estado), intentar spawn
        _uiState.value.currentLocation?.let { checkPrankedySpawn(it) }
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
        // Identidad del jugador = UID de Firebase si hay sesión (reemplaza al UUID de dispositivo).
        // El servidor, al verificar el token, también usa ese UID como sessionId.
        ovh.gabrielhuav.pow.data.auth.AuthSession.uid?.let { myPlayerUUID = it }
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

    // ─── ANTI-DUPLICACIÓN DE AUTOS (botón Y) ─────────────────────────────────
    // Debounce de subir/bajar + "tombstones" de coches recién abordados: ids que el
    // volcado de la IA y el NPC_BATCH_UPDATE deben IGNORAR unos segundos (si no, el
    // snapshot viejo re-insertaba el coche que acabas de abordar y se duplicaba).
    internal var lastVehicleToggleMs = 0L
    // H: instante en que Prankedy empezó a "subir" al coche (escolta). Sirve de timeout de
    // seguridad: si no llega en PRANKEDY_BOARD_TIMEOUT_MS, se le teletransporta contigo y se sube
    // (evita que el coche quede bloqueado para siempre si no puede pathear hasta ti).
    internal var prankedyBoardingStartMs = 0L
    internal var lastObjDbgMs = 0L   // throttle del log de diagnóstico de objetivos/misión (POW_DBG)

    // Contador de ciclos de IA tras (re)cargar el mundo, para el warm-up de NPCs del
    // gate de carga (npcsWarmedUp). Se reinicia en cada teleport.
    internal var npcWarmupCycles = 0
    internal val boardedCarTombstones = java.util.concurrent.ConcurrentHashMap<String, Long>()
    internal fun isCarTombstoned(id: String): Boolean {
        val until = boardedCarTombstones[id] ?: return false
        if (System.currentTimeMillis() > until) { boardedCarTombstones.remove(id); return false }
        return true
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

                        // MODO HISTORIA: ¿llegó al objetivo de campaña (p. ej. la ENCB)?
                        // y, en el vecindario de la ENCB, enciende a Prankedy acompañante.
                        if (inCampaign) {
                            checkObjectiveProgress(location)
                            maybeSpawnPrankedyCompanion(location)
                            maybeHideCampaignRouteNearEscom(location)
                        }

                        checkDestinationArrival()

                        if (tickCount % 30 == 0L && _uiState.value.destinationMarker != null) {
                            updateDestinationRoute()
                        }

                        if (_uiState.value.playerAction == PlayerAction.SPECIAL) {
                            performPlayerAttack()
                        }

                        // Zoom automático: 22 a pie, 21 conduciendo, 20 a muy alta velocidad.
                        updateAutoZoom()

                        if (_uiState.value.isDriving) {
                            var currentSpeed = _uiState.value.vehicleSpeed
                            var currentRotation = _uiState.value.vehicleRotation

                            // H: si Prankedy está SUBIENDO al coche, el coche NO avanza hasta que se suba.
                            val prankedyBoarding = _uiState.value.prankedyBoarding
                            if (prankedyBoarding) currentSpeed = 0.0
                            // I: a <= 50 m de la ESCOM (durante la escolta/ingreso) el coche SOLO da reversa
                            //    → te obliga a BAJARTE y entrar a pie por la puerta.
                            val driveObjId = _uiState.value.currentObjective?.id
                            val forceWalkNearEscom = inCampaign &&
                                (driveObjId == ovh.gabrielhuav.pow.domain.models.MissionCatalog.ESCOLTAR_PRANKEDY.id ||
                                 driveObjId == ovh.gabrielhuav.pow.domain.models.MissionCatalog.INGRESAR_ESCOM.id) &&
                                location.distanceToAsDouble(GeoPoint(
                                    ovh.gabrielhuav.pow.domain.models.MissionCatalog.ESCOM_FORCEWALK_LAT,
                                    ovh.gabrielhuav.pow.domain.models.MissionCatalog.ESCOM_FORCEWALK_LON)
                                ) <= ovh.gabrielhuav.pow.domain.models.MissionCatalog.ESCOM_FORCEWALK_RADIUS_M

                            if (isSteeringLeftPressed && currentSpeed != 0.0) {
                                currentRotation -= if (currentSpeed > 0) 2f else 3f
                            }
                            if (isSteeringRightPressed && currentSpeed != 0.0) {
                                currentRotation += if (currentSpeed > 0) 2f else 3f
                            }

                            if (isGasPressed && !prankedyBoarding && !forceWalkNearEscom) {
                                val speedRatio = (currentSpeed / MAX_SPEED).coerceIn(0.0, 1.0)
                                val dynamicAcc = ACCELERATION * (1.0 - speedRatio * 0.75) // Cuesta más llegar al 100%
                                currentSpeed = (currentSpeed + dynamicAcc).coerceAtMost(MAX_SPEED)
                            } else if (isBrakePressed && !prankedyBoarding) {
                                currentSpeed -= BRAKING_FRICTION
                                if (currentSpeed < -MAX_SPEED / 2) currentSpeed = -MAX_SPEED / 2
                            } else {
                                if (currentSpeed > 0) currentSpeed = (currentSpeed - (ACCELERATION / 2)).coerceAtLeast(0.0)
                                if (currentSpeed < 0) currentSpeed = (currentSpeed + (ACCELERATION / 2)).coerceAtMost(0.0)
                            }
                            // I: refuerza "solo reversa" cerca de la ESCOM (anula cualquier avance hacia adelante).
                            if (forceWalkNearEscom && currentSpeed > 0.0) currentSpeed = 0.0

                            val angleRad = Math.toRadians(currentRotation.toDouble())
                            val dx = kotlin.math.sin(angleRad) * currentSpeed
                            val dy = kotlin.math.cos(angleRad) * currentSpeed

                            var tempLoc = GeoPoint(location.latitude + dy, location.longitude + dx)

                            // REBASE AUTOMÁTICO Y COLISIONES:
                            // Comportamiento variado según velocidad y movimientos:
                            val absSpeed = kotlin.math.abs(currentSpeed)
                            val isGoingVeryFast = absSpeed > MAX_SPEED * 0.95
                            val isSteeringSharply = isSteeringLeftPressed || isSteeringRightPressed

                            val overtakeRadius = 0.00008

                            val hdLat = kotlin.math.cos(angleRad)
                            val hdLon = kotlin.math.sin(angleRad)
                            val perpLat = -hdLon
                            val perpLon = hdLat

                            var dodgeOffsetX = 0.0
                            var dodgeOffsetY = 0.0

                            for ((id, npc) in remoteEntities.entries.toList()) {
                                if (npc.type == NpcType.CAR || npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.POLICE_CAR) {
                                    val vLat = npc.location.latitude - tempLoc.latitude
                                    val vLon = npc.location.longitude - tempLoc.longitude
                                    val dist = kotlin.math.sqrt(vLat * vLat + vLon * vLon)

                                    val inFront = vLat * hdLat + vLon * hdLon
                                    val rightDist = vLat * perpLat + vLon * perpLon

                                    if (dist < overtakeRadius) {
                                        // 1. (ELIMINADO) El "empujón" posicional a los NPCs cuando estabas
                                        // detenido causaba órbitas/oscilaciones alrededor del jugador (se
                                        // empujaba la POSICIÓN sin tocar el RUMBO, y el road-following los
                                        // regresaba al punto de empuje en bucle). Ahora el rebase lo hace
                                        // el PROPIO NPC en NpcAiManager.moveNpc: desplaza su OBJETIVO un
                                        // carril mientras el jugador esté en su trayectoria, lo pasa y se
                                        // reincorpora (ver "ESQUIVE DE TRÁFICO").
                                        if (absSpeed >= MAX_SPEED * 0.09 && !isSteeringSharply && inFront > 0) {
                                            // 2. Manejando recto a velocidad normal o alta: NOSOTROS los esquivamos
                                            // de forma automática sin desviarnos de la calle (desplazando el tempLoc).
                                            val dodgeDir = if (rightDist >= 0) -1.0 else 1.0
                                            val dodgeForce = (overtakeRadius - dist) * 0.08
                                            dodgeOffsetY += perpLat * dodgeDir * dodgeForce
                                            dodgeOffsetX += perpLon * dodgeDir * dodgeForce
                                        }
                                        // 3. Si vamos muy rápido o giramos bruscamente, no hay esquive suave,
                                        // tempLoc no se altera y runOverNpcs se encargará del choque (Toretto).
                                    }
                                }
                            }

                            // FIX "te sales mucho al esquivar": cap del desplazamiento lateral POR TICK
                            // del esquive (con varios coches cerca las fuerzas se sumaban). ~1.6 m/tick máx.
                            val dodgeMag = kotlin.math.sqrt(dodgeOffsetX * dodgeOffsetX + dodgeOffsetY * dodgeOffsetY)
                            val maxDodgePerTick = 0.0000015
                            if (dodgeMag > maxDodgePerTick) {
                                val k = maxDodgePerTick / dodgeMag
                                dodgeOffsetX *= k; dodgeOffsetY *= k
                            }
                            tempLoc = GeoPoint(tempLoc.latitude + dodgeOffsetY, tempLoc.longitude + dodgeOffsetX)

                            var nearestRoadPoint = getNearestPointOnNetwork(tempLoc)

                            // Auto-centrado suave al carril: si acabamos de rebasar o vamos recto sin girar,
                            // regresamos al centro del camino de forma elegante para no mantenernos fuera de carril.
                            var isAutoCentering = false
                            val timeSinceDodge = System.currentTimeMillis() - lastDodgeTime
                            val isRecoveringFromDodge = dodgeOffsetX == 0.0 && dodgeOffsetY == 0.0 && timeSinceDodge in 1L..1500L

                            if (!isSteeringSharply && isRecoveringFromDodge && absSpeed > 0.0) {
                                val pullLat = nearestRoadPoint.latitude - tempLoc.latitude
                                val pullLon = nearestRoadPoint.longitude - tempLoc.longitude
                                tempLoc = GeoPoint(tempLoc.latitude + pullLat * 0.15, tempLoc.longitude + pullLon * 0.15)
                                nearestRoadPoint = getNearestPointOnNetwork(tempLoc) // recalcular porque nos movimos
                                isAutoCentering = true
                            }

                            // 👇 ADUANA DE CHOQUE PARA EL COCHE 👇
                            if (isCollisionDetected(location.latitude, location.longitude, tempLoc.latitude, tempLoc.longitude)) {
                                // Frena en seco (Speed = 0) y anula el choque
                                _uiState.update {
                                    it.copy(
                                        vehicleSpeed = 0.0,
                                        vehicleRotation = (currentRotation + 360) % 360f
                                    )
                                }
                            } else {
                                // Lógica de 'main': Auto-centrado y límite dinámico de la calle
                                val distToRoad = distance(tempLoc, nearestRoadPoint)
                                // Radio expandido al rebasar REDUCIDO (0.00006 → 0.000045 ≈ 5 m): con el
                                // anterior el coche se veía claramente FUERA de la carretera al esquivar.
                                val maxRoadRadius = if (dodgeOffsetX != 0.0 || dodgeOffsetY != 0.0 || isAutoCentering) 0.000045 else 0.00004

                                val finalLoc = if (distToRoad <= maxRoadRadius) {
                                    tempLoc
                                } else {
                                    // Te saliste de la calle: literalmente no avanzas, te frenas en seco.
                                    currentSpeed = 0.0
                                    location
                                }

                                _uiState.update {
                                    it.copy(
                                        currentLocation = finalLoc,
                                        vehicleSpeed = currentSpeed,
                                        vehicleRotation = (currentRotation + 360) % 360f
                                    )
                                }

                                // ATROPELLO: Lógica de esquive de 'main'
                                val isAutoDodging = dodgeOffsetX != 0.0 || dodgeOffsetY != 0.0
                                if (isAutoDodging) lastDodgeTime = System.currentTimeMillis()
                                // Cancelar inmunidad si el jugador gira voluntariamente (para permitir chocar intencionalmente)
                                if (isSteeringSharply) lastDodgeTime = 0L
                                val dodgeGrace = System.currentTimeMillis() - lastDodgeTime < 600L
                                runOverNpcs(finalLoc, currentSpeed, dodgeGrace)
                            }
                        }

                        // GOLPES/MORDIDAS por CONTACTO: NPCs agresivos en embestida y ZOMBIS cercanos
                        // dañan al jugador. Cada cliente lo aplica a SU propio jugador (no host-gated).
                        // (Faltaba esta llamada en el loop miembro → ni los NPCs agresivos ni los
                        // zombis hacían daño; solo estaba en la extensión muerta.)
                        applyNpcContactDamage(location)

                        // COMPAÑERO PRANKEDY: spawn diferido + tick de IA (seguir/correr/combatir/
                        // animar/proyectil/diálogo). Cada cliente lo simula para SU propio jugador
                        // (local, como la policía). Antes vivía SOLO en la extensión muerta
                        // WorldMapGameLoop.kt → el loop MIEMBRO gana y nunca lo ejecutaba, por eso el
                        // NPC "no aparecía en el mapa" ni seguía al jugador. checkPrankedySpawn es
                        // idempotente (solo spawnea si location==null && phase!=DEAD) y garantiza el
                        // spawn aunque updateInitialLocation no se haya disparado.
                        checkPrankedySpawn(location)
                        // MISIÓN 2: Prankedy ya NO te sigue; CORRE hacia la puerta de la ESCOM y se
                        // mete (huyendo de la policía, que lo persigue por detrás). Tras entrar, no
                        // se le anima más. Fuera de eso, corre su seguimiento normal.
                        val nowMs = System.currentTimeMillis()
                        val m2 = isMission2ChaseActive()
                        when {
                            m2 && mission2PrankedyEntered -> { /* ya entró: no animar a Prankedy */ }
                            m2 && prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED ->
                                runMission2PrankedyEscape(location, nowMs)
                            else -> runPrankedyTick(location, nowMs)
                        }

                        // BALAS de la POLICÍA DEL APOCALIPSIS (caza-zombis): el Host las acumula en
                        // movePoliceHunter; aquí las volcamos a policeShots para DIBUJARLAS (runPoliceTick
                        // de abajo las expira a 450 ms, igual que las de la policía normal).
                        if (npcAiManager.pendingPoliceShots.isNotEmpty()) {
                            val nowS = System.currentTimeMillis()
                            val shots = synchronized(npcAiManager.pendingPoliceShots) {
                                val l = npcAiManager.pendingPoliceShots.toList(); npcAiManager.pendingPoliceShots.clear(); l
                            }
                            if (shots.isNotEmpty()) {
                                _uiState.update { st -> st.copy(policeShots = st.policeShots + shots.map { PoliceShot(it.first, it.second, nowS) }) }
                            }
                        }

                        // POLICÍA. Durante la MISIÓN 1 (escolta) corre la policía de la CAMPAÑA
                        // (clase aparte: 2 a pie, siguen a distancia, 1★) y NO el sistema de
                        // búsqueda del mundo libre, para que no choquen. Fuera de la escolta corre
                        // la policía normal del mundo libre. Al terminar la escolta (o salir de la
                        // campaña) se limpia la policía de campaña.
                        // El objetivo de la ESCOM apunta a la PUERTA real (landmark) más cercana.
                        if (isCampaignEscortActive() || isMission2ChaseActive()) syncObjectiveToEscomDoor(location)
                        when {
                            // MISIÓN 1: escolta (2 a pie, te siguen a distancia).
                            isCampaignEscortActive() -> {
                                if (_uiState.value.isRoadNetworkReady && !_uiState.value.showWastedScreen) {
                                    runCampaignEscortTick(location)
                                }
                            }
                            // MISIÓN 2: persecución (6 policías) + multitud saliendo de la ESCOM.
                            isMission2ChaseActive() -> {
                                if (_uiState.value.isRoadNetworkReady && !_uiState.value.showWastedScreen) {
                                    runMission2Tick(location)
                                }
                            }
                            // Fuera de la campaña / misión cumplida: limpia la policía de campaña y
                            // corre la policía normal del mundo libre.
                            else -> {
                                if (campaignPoliceActivated || mission2ChaseActivated) clearCampaignPolice()
                                if (_uiState.value.isRoadNetworkReady && !_uiState.value.showWastedScreen) {
                                    runPoliceTick(location)
                                }
                            }
                        }

                        maybeRefetchRoadNetwork(location)
                        if (_uiState.value.showRoadNetwork) {
                            updateVisibleRoads(location)
                        }

                        // ORDEN DE CARGA: los NPCs solo se simulan/spawnean cuando el mundo está
                        // COMPLETO (mapa descargado Y red de calles lista). Tras un teleport ambos
                        // flags se apagan → primero tiles, luego calles, y AL FINAL los NPCs.
                        if (_uiState.value.isRoadNetworkReady && _uiState.value.isMapReady) {
                            tickCount++
                            if (tickCount % 3 == 0L) {
                                val npcOnlyList = remoteEntities.values.filter { it.displayName.isNullOrEmpty() }
                                npcAiManager.setServerNpcs(npcOnlyList)

                                // Pasamos los landmarks (edificios) con sus navGraphs al motor
                                npcAiManager.setLandmarks(_uiState.value.landmarks.filter { it.navGraph != null })

                                npcAiManager.updateNpcs(location, isServerDelegatedHost)
                                val processedNpcs = npcAiManager.getServerNpcs()

                                // HORDA MIGRATORIA: si el Host disparó una oleada, avisar al jugador.
                                if (npcAiManager.hordeIncomingAt != 0L && npcAiManager.hordeIncomingAt != lastHordeSeenMs) {
                                    lastHordeSeenMs = npcAiManager.hordeIncomingAt
                                    launch(kotlinx.coroutines.Dispatchers.Main) {
                                        _uiState.update { it.copy(interactionPrompt = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_horde_approaching)) }
                                        kotlinx.coroutines.delay(3500)
                                        _uiState.update { if (it.interactionPrompt == "🧟 ¡UNA HORDA SE ACERCA!") it.copy(interactionPrompt = null) else it }
                                    }
                                }

                                if (isServerDelegatedHost) {
                                    synchronized(npcAiManager.pendingDespawns) {
                                        npcAiManager.pendingDespawns.forEach { remoteEntities.remove(it) }
                                    }
                                    // No re-insertar coches recién abordados (snapshot viejo de la IA).
                                    processedNpcs.forEach { if (!isCarTombstoned(it.id)) remoteEntities[it.id] = it }
                                }
                                updateNpcsState()

                                // WARM-UP de NPCs (última fase del gate de carga): tras (re)cargar el
                                // mundo, esperamos unos ciclos de IA (siembra de campus/estacionamientos/
                                // calles) antes de soltar al jugador. ~5 ciclos ≈ 0.5 s, o antes si ya
                                // hay NPCs en pantalla. En zonas vacías igual libera (no bloquea).
                                if (!_uiState.value.npcsWarmedUp) {
                                    npcWarmupCycles++
                                    if (npcWarmupCycles >= 5 || _uiState.value.npcs.isNotEmpty()) {
                                        _uiState.update { it.copy(npcsWarmedUp = true) }
                                    }
                                }

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
                                                            aggroUntil = npc.aggroUntil,
                                                            zombieRole = npc.zombieRole.name,
                                                            screamUntil = npc.screamUntil
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

    fun getLocalizedString(resId: Int, vararg args: Any): String {
        val lang = settingsRepository.getLanguage()
        val baseContext = getApplication<android.app.Application>()
        val contextToUse = if (lang.isNotEmpty()) {
            val locale = java.util.Locale(lang)
            val config = android.content.res.Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        } else {
            baseContext
        }
        return contextToUse.getString(resId, *args)
    }


    private var exteriorCollisions: ExteriorCollisionsConfig? = null

    // Llama esta función en el init{} de tu ViewModel
    internal fun loadExteriorCollisions(context: Context) { // llamado desde WorldMapDesigner.kt
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.assets.open("exterior_collisions.json").bufferedReader().use { it.readText() }
                exteriorCollisions = Gson().fromJson(jsonString, ExteriorCollisionsConfig::class.java)

                npcAiManager.setExteriorCollisions(exteriorCollisions)
                // Exponer al estado para el overlay de Debug Interiores (zonas no caminables).
                val cfg = exteriorCollisions
                withContext(Dispatchers.Main) { _uiState.update { it.copy(exteriorCollisions = cfg) } }

            } catch (e: Exception) {
                Log.e("Collisions", "Error: ${e.message}")
            }
        }
    }

    private fun isCollisionDetected(oldLat: Double, oldLon: Double, newLat: Double, newLon: Double): Boolean {
        val config = exteriorCollisions ?: return false
        // A) Revisar si pisa un edificio
        for (poly in config.polygons) {
            if (poly.contains(newLat, newLon)) return true
        }
        // B) Revisar si choca con una barda
        for (wall in config.walls) {
            if (wall.didHitWall(oldLat, oldLon, newLat, newLon)) return true
        }
        return false
    }

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
        // Zoom de juego A PIE = 22 para TODOS los proveedores (los web sobre-escalan
        // desde su maxNativeZoom; CARTO llega a z20 real).
        val targetZoom = ZOOM_ON_FOOT

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
                        // Mantener TODOS los índices en sync con la red nueva (antes solo se
                        // actualizaba la IA; el grid de routing y el grafo A* quedaban viejos).
                        rebuildRoadNodeGrid(cached)
                        buildRoadGraph(cached)
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
                            rebuildRoadNodeGrid(network)
                            buildRoadGraph(network)
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
        // 🆕 ZONA LIBRE (ESCOM/ENCB): dentro de cualquiera de los dos campus NO se pintan las
        // líneas de calles. Vaciamos el Flow de inmediato y SALTAMOS el filtro en Default (sin
        // parpadeo/recarga). Al SALIR del perímetro, la condición falla y se repinta normal.
        // ⚠️ Esta es la versión MIEMBRO (gana sobre la extensión homónima de WorldMapRoadNetwork.kt,
        // gotcha del archivo 09): es la que realmente se ejecuta, por eso el check va AQUÍ.
        if (isFreeMovementZone(playerLoc.latitude, playerLoc.longitude)) {
            wasInFreeMovementZone = true
            if (_roadNetworkFlow.value.isNotEmpty()) _roadNetworkFlow.value = emptyList()
            return
        }
        // SALIDA de zona libre: en el primer tick fuera del campus FORZAMOS el repintado, porque
        // el flow quedó vacío al entrar y el throttle por distancia lo suprimiría (el campus es
        // pequeño → el jugador no se ha alejado del último punto pintado). Así las calles
        // reaparecen al instante al pisar el mundo abierto.
        val leftFreeZone = wasInFreeMovementZone
        wasInFreeMovementZone = false
        val effectiveForce = force || leftFreeZone

        val lastUpdate = lastVisibleRoadUpdateLocation
        if (!effectiveForce && lastUpdate != null) {
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
            // Anti-carrera: si para cuando termina el filtro el jugador YA está en zona libre,
            // no repintamos (evita que una corutina lanzada en el borde reponga las líneas).
            if (!isFreeMovementZone(playerLoc.latitude, playerLoc.longitude)) {
                _roadNetworkFlow.value = visible
            } else if (_roadNetworkFlow.value.isNotEmpty()) {
                _roadNetworkFlow.value = emptyList()
            }
        }
    }

    private fun handleMultiplayerMessage(messageJson: String) {
        try {
            val msg = gson.fromJson(messageJson, ServerMessage::class.java)

            when (msg.type) {
                // (ZOMBIE_MODE_SET eliminado: el apocalipsis ya no es un flag global difundido a
                //  todos, sino la INSTANCIA en la que estás. El toggle envía JOIN_INSTANCE y el
                //  servidor responde con SYNC_ALL_NPCS de la nueva instancia. Ver setZombieInstance.)
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
                            bodyFolder = "other_player",
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

                        // FIX: Asegurar que displayName nunca sea blank para poder identificar jugadores remotos
                        val safeDisplayName = msg.displayName?.takeIf { it.isNotBlank() } ?: "Player_${msg.id.take(4)}"

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
                            displayName = safeDisplayName,
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
        // Coche recién abordado por MÍ: ignorar reinserciones que lleguen del host
        // remoto durante unos segundos (anti-duplicación, ver boardedCarTombstones).
        if (isCarTombstoned(remote.id)) return
        val npcType = try { NpcType.valueOf(remote.npcType) } catch(e: Exception) { NpcType.PERSON }

        // Rol de zombi replicado: el maxHealth se DERIVA del rol (no viaja por el cable).
        val zRole = try {
            remote.zombieRole?.let { ovh.gabrielhuav.pow.domain.models.ZombieRole.valueOf(it) }
                ?: ovh.gabrielhuav.pow.domain.models.ZombieRole.NORMAL
        } catch (e: Exception) { ovh.gabrielhuav.pow.domain.models.ZombieRole.NORMAL }
        val zMaxHealth = if (npcType == NpcType.ZOMBIE) NpcAiManager.maxHealthForRole(zRole) else 100f

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
            aggroUntil = remote.aggroUntil ?: 0L,
            zombieRole = zRole,
            maxHealth = zMaxHealth,
            screamUntil = remote.screamUntil ?: 0L
        )
    }

    // REFACTOR: updateNpcsState vive SOLO en WorldMapMultiplayer.kt (la extensión era
    // idéntica a la copia miembro que había aquí).

    fun notifyTileSource(fromCache: Boolean) {
        if (_uiState.value.mapProvider == MapProvider.OSM) return
        val source = if (fromCache) TileSource.LOCAL_CACHE else TileSource.NETWORK
        if (_uiState.value.tileSource != source) {
            _uiState.update { it.copy(tileSource = source) }
        }
    }

    fun moveCharacter(direction: Direction) {
        if (_uiState.value.showWastedScreen || _uiState.value.showMissionFailed) return // muerto/misión fallida: sin movimiento
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

        // ADUANA DE CHOQUE A PIE
        if (isCollisionDetected(loc.latitude, loc.longitude, temp.latitude, temp.longitude)) {
            return // CHOCÓ: Rompemos la función y el jugador no avanza
        }

        // ZONA LIBRE (ESCOM / ENCB) o SOBRE UN ASSET/LANDMARK: se suspende la malla vial → el
        // JUGADOR se mueve libre en (x,y). (isOnLandmark es solo para ti; las calles siguen
        // visibles y los NPCs siguen atados a la malla.)
        if (isFreeMovementZone(temp.latitude, temp.longitude) || isOnLandmark(temp.latitude, temp.longitude)) {
            _uiState.update { it.copy(currentLocation = temp) }
            return
        }

        val nearest = getNearestPointOnNetwork(temp)
        val dist    = distance(temp, nearest)
        val radius  = 0.000012
        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else if (dist > MAX_SNAP_DISTANCE_DEG) {
            // FIX TP aleatorio: si la calle "más cercana" está LEJOS (la red recién
            // recargada aún no cubre esta zona, o candidates() cayó al fallback de
            // TODOS los segmentos), NO teletransportamos al jugador hacia ella.
            // Mejor no moverse este tick que aparecer en una calle al azar.
            return
        } else {
            val angle = atan2(temp.latitude - nearest.latitude, temp.longitude - nearest.longitude)
            _uiState.update { it.copy(currentLocation = GeoPoint(
                nearest.latitude  + sin(angle) * radius,
                nearest.longitude + cos(angle) * radius
            ))}
        }
    }

    fun moveCharacterByAngle(angleRad: Double) {
        if (_uiState.value.showWastedScreen || _uiState.value.showMissionFailed) return // muerto/misión fallida
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

        // ADUANA DE CHOQUE JOYSTICK
        if (isCollisionDetected(loc.latitude, loc.longitude, temp.latitude, temp.longitude)) {
            return // CHOCÓ: Rompemos la función
        }

        // ZONA LIBRE (ESCOM / ENCB) o SOBRE UN ASSET/LANDMARK: se suspende la malla vial → el
        // JUGADOR se mueve libre en (x,y) (solo tú; las calles siguen visibles y los NPCs atados).
        if (isFreeMovementZone(temp.latitude, temp.longitude) || isOnLandmark(temp.latitude, temp.longitude)) {
            _uiState.update { it.copy(currentLocation = temp) }
            return
        }

        val nearest = getNearestPointOnNetwork(temp)
        val dist = distance(temp, nearest)
        val radius = 0.000012

        if (dist <= radius) {
            _uiState.update { it.copy(currentLocation = temp) }
        } else if (dist > MAX_SNAP_DISTANCE_DEG) {
            // FIX TP aleatorio (ver moveCharacter): no saltar a una calle lejana.
            return
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
    // FIX TP aleatorio: distancia máxima (~33 m) a la que el snap-to-road puede
    // "jalar" al jugador hacia una calle. Más lejos = la red no cubre esta zona
    // todavía; el movimiento se ignora en vez de teletransportar.
    internal val MAX_SNAP_DISTANCE_DEG = 0.0003
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
            val desc = if (isParking) getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_parking_spot) else getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_waypoint_lane, state.currentWayId)

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

            android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_node_captured, debugNodeIdCounter), android.widget.Toast.LENGTH_SHORT).show()
            debugNodeIdCounter++
        } else {
            android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_outside_building), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    // REFACTOR: ensureIndex/candidates/getNearestPointOnNetwork/project viven SOLO en
    // WorldMapRouting.kt (la extensión, ya sincronizada con el check de landmarks que
    // tenía la copia miembro). pack/cell/distance se quedan aquí porque los usan
    // tanto miembros como extensiones.
    internal fun pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
    internal fun cell(v: Double): Int = floor(v / CELL).toInt()

    internal fun distance(a: GeoPoint, b: GeoPoint): Double =
        sqrt((a.latitude - b.latitude).pow(2) + (a.longitude - b.longitude).pow(2))

    fun updateInitialLocation(lat: Double, lon: Double) {
        val loc = GeoPoint(lat, lon)
        if (_uiState.value.isLoadingLocation) {
            _uiState.update { it.copy(currentLocation = loc, isLoadingLocation = false) }
            checkPrankedySpawn(loc) // Iniciar spawn del compañero en cuanto sabemos dónde está el jugador
        }
    }

    // MODO HISTORIA: fija la escuela de inicio elegida en el menú de campaña.
    // A diferencia de [updateInitialLocation] (gateada por isLoadingLocation, ya
    // consumida en MainActivity.onCreate), esto FUERZA el punto de aparición y
    // re-arma las compuertas de carga para que el mapa y las calles se descarguen
    // alrededor de la escuela elegida. Se llama ANTES de navegar al mapa, cuando el
    // mundo aún no está cargado.
    fun setStorySpawn(lat: Double, lon: Double) {
        val loc = GeoPoint(lat, lon)
        // FIX "se queda cargando": prepareMapForEntry() es idempotente (gateada por
        // mapPrepStarted, que es de la Activity y persiste entre navegaciones). Si el
        // jugador ya entró al mundo una vez (p. ej. MUNDO LIBRE), re-armar isMapReady=false
        // aquí NO volvía a descargar los tiles → la compuerta de carga no se soltaba nunca.
        // Solución: hacer que el spawn de campaña se comporte como un TELETRANSPORTE, que sí
        // re-descarga (gateMapDownloadAfterTeleport NO está gateado por mapPrepStarted).
        inCampaign = true            // sesión de campaña → habilita el auto-guardado al salir
        prankedyCompanionActivated = false  // re-arma el encendido del acompañante en la ENCB
        campaignPoliceActivated = false     // re-arma la policía de escolta de la Misión 1
        mission2ChaseActivated = false      // re-arma la persecución de la Misión 2
        campaignEscortPolice.clear()
        mission2Crowd.clear()
        npcWarmupCycles = 0          // re-arma el warm-up de NPCs del gate de carga
        lastNetworkFetchLocation = null  // fuerza el re-fetch de calles alrededor de la escuela
        lastFetchAttemptMs = 0L
        _uiState.update {
            it.copy(
                currentLocation = loc,
                isLoadingLocation = false,
                isMapReady = false,        // ← re-activa la compuerta de carga del mapa
                isRoadNetworkReady = false, // ← y la de la red de calles
                npcsWarmedUp = false,       // ← y el warm-up de NPCs (orden: tiles → calles → NPCs)
                isUserPanningMap = false,   // ← recentra el mapa y reactiva la neblina
                showMissionFailed = false   // ← limpia un posible "MISIÓN FALLIDA" anterior
            )
        }
        // Descarga el mapa de la escuela ANTES de soltar al jugador (en paralelo a la
        // recarga de calles). Esto SÍ pone isMapReady=true al terminar, sin depender de
        // prepareMapForEntry (idempotente). Así "COMENZAR" carga y spawnea en la escuela.
        gateMapDownloadAfterTeleport()
        checkPrankedySpawn(loc)
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

    // ─── PROVEEDORES DE MAPA / TILES (REFACTOR) ───────────────────────────────
    // setMapProvider / requestMapProvider / commitMapProvider / cancelPendingProvider /
    // preloadProvider / tileUrlFor / fetchTile / prepareMapForEntry / downloadMapAround /
    // gateMapDownloadAfterTeleport / downloadGateTiles / downloadOsmNativeForEntry /
    // cacheOsmTileToRoom / downloadTileBytes / sha256Hex viven en WorldMapProviders.kt.
    // Aquí solo queda el ESTADO que esas extensiones usan:
    internal var providerPreloadJob: Job? = null

    // ─── CARGA INICIAL DEL MAPA (gate de entrada con % de progreso) ───────────
    // (estado usado por WorldMapProviders.kt; las funciones viven allá)
    internal var mapPrepStarted = false

    // ─── MODO HISTORIA: contexto de la partida actual (para el guardado JSON) ──
    // Escuela de la campaña en curso (la fija MainActivity al COMENZAR/CARGAR) e
    // indicador de si estamos en una sesión de campaña (para el auto-guardado al
    // salir). MUNDO LIBRE pone inCampaign=false y no auto-guarda. Ver WorldMapSaveGame.kt.
    internal var campaignSchoolId: String = "escom"
    internal var inCampaign: Boolean = false
    // MODO HISTORIA: el acompañante Prankedy (fase HIRED) se enciende una sola vez por entrada
    // de campaña, solo en el vecindario de la ENCB. setStorySpawn re-arma esta bandera.
    internal var prankedyCompanionActivated: Boolean = false
    // Slot de guardado activo (1..SaveGameRepository.SLOT_COUNT). Lo fija MainActivity al
    // COMENZAR/CARGAR; el auto-guardado al salir escribe en este slot.
    internal var campaignSlot: Int = 1
    // Sala de interiores actual (id de ZombieRoomCatalog) o null si el jugador está en el
    // MAPA GLOBAL. Lo mantiene MainActivity (onRoomChanged al entrar/cambiar de sala, null al
    // salir al mapa). El guardado lo persiste para que CARGAR reabra el interior correcto.
    internal var currentInteriorRoomId: String? = null

    // INVENTARIO de interiores (llaves recogidas) y progreso del puzzle de ENCB_lab1. Los mantiene
    // MainActivity (puente con ZombieGameViewModel) para que el GUARDADO los persista y CARGAR los
    // restaure al reabrir el interior.
    internal var currentInteriorInventory: List<String> = emptyList()
    internal var currentInteriorLab1KeyFound: Boolean = false

    fun toggleCacheWidget(show: Boolean) { _uiState.update { it.copy(showCacheWidget = show) } }
    fun toggleFpsWidget(show: Boolean) { _uiState.update { it.copy(showFpsWidget = show) } }

    fun toggleZoomWidget(show: Boolean) { _uiState.update { it.copy(showZoomWidget = show) } }

    fun toggleSpeedometer(show: Boolean) { _uiState.update { it.copy(showSpeedometer = show) } }
    fun toggleCoordsWidget(show: Boolean) { _uiState.update { it.copy(showCoordsWidget = show) } }
    fun updateShowCacheWidget(show: Boolean) = _uiState.update { it.copy(showCacheWidget = show) }
    fun updateShowFpsWidget(show: Boolean) = _uiState.update { it.copy(showFpsWidget = show) }

    // ─── ZOOM AUTOMÁTICO POR ESTADO (a pie / conduciendo / conduciendo rápido) ───
    // A pie 22; al subir a un vehículo 21; a MUY alta velocidad (≥85% de MAX_SPEED)
    // baja a 20, y vuelve a 21 por debajo del 65% (histéresis anti-parpadeo). Solo
    // actúa en TRANSICIONES de modo, así el pinch manual del usuario se respeta
    // hasta el siguiente cambio de estado.
    private var autoZoomMode = 0 // 0 = a pie, 1 = conduciendo, 2 = conduciendo rápido
    private var targetZoomLevel = ZOOM_ON_FOOT // Zoom objetivo para interpolación suave

    internal fun updateAutoZoom() {
        val st = _uiState.value
        val absSpeed = kotlin.math.abs(st.vehicleSpeed)
        val newMode = when {
            !st.isDriving -> 0
            autoZoomMode == 2 -> if (absSpeed < MAX_SPEED * 0.65) 1 else 2
            else -> if (absSpeed >= MAX_SPEED * 0.85) 2 else 1
        }
        if (newMode != autoZoomMode) {
            autoZoomMode = newMode
            targetZoomLevel = when (newMode) {
                0 -> ZOOM_ON_FOOT
                1 -> ZOOM_DRIVING
                else -> ZOOM_DRIVING_FAST
            }
        }
        // Interpolar zoomLevel hacia targetZoomLevel para transición suave
        val currentZoom = st.zoomLevel
        if (Math.abs(currentZoom - targetZoomLevel) > 0.01) {
            val newZoom = currentZoom + (targetZoomLevel - currentZoom) * 0.1 // 10% por tick
            _uiState.update { it.copy(zoomLevel = newZoom) }
        }
    }

    fun zoomIn()  { 
        targetZoomLevel = (_uiState.value.zoomLevel + 1.0).coerceAtMost(22.0)
        _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = targetZoomLevel) else it } 
    }
    fun zoomOut() { 
        targetZoomLevel = (_uiState.value.zoomLevel - 1.0).coerceAtLeast(14.0)
        _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = targetZoomLevel) else it } 
    }

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
            targetZoomLevel = z
            _uiState.update { it.copy(zoomLevel = z) }
        }
    }

    fun centerOnPlayer() { _uiState.update { it.copy(isUserPanningMap = false) } }

    /** Centra en el jugador Y acerca al máximo nivel de zoom permitido. */
    fun zoomToPlayer() {
        // Fijamos TAMBIEN el objetivo de interpolacion: si no, updateAutoZoom() arrastraba
        // el zoom de vuelta al valor anterior (p. ej. tras un zoom out) y "rebotaba".
        targetZoomLevel = 22.0
        _uiState.update { it.copy(isUserPanningMap = false, zoomLevel = 22.0) }
    }

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

        // FIX duplicación de autos: (1) DEBOUNCE — spamear Y alternaba subir/bajar más
        // rápido que el ciclo de la IA y duplicaba el coche; se ignoran pulsaciones a
        // menos de 450 ms de la anterior.
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastVehicleToggleMs < 450L) return

        // PRANKEDY ya NO es contratable: es un NPC hostil; no hay interacción con X.

        if (!_uiState.value.isDriving) {
            val nearbyCarEntry = remoteEntities.entries
                .filter { it.value.type == NpcType.CAR && distance(loc, it.value.location) <= INTERACT_RADIUS }
                .minByOrNull { distance(loc, it.value.location) }

            if (nearbyCarEntry != null) {
                lastVehicleToggleMs = nowMs
                val carId = nearbyCarEntry.key
                val carNpc = nearbyCarEntry.value
                remoteEntities.remove(carId)
                // (2) TOMBSTONE — el game loop tomaba el snapshot de la IA ANTES de subirte
                // y al volcar processedNpcs RE-INSERTABA el coche recién abordado (carrera
                // main-thread vs loop) → coche duplicado. Marcamos el id como "abordado"
                // unos segundos para que el volcado lo ignore.
                boardedCarTombstones[carId] = nowMs + 10_000L
                // Y avisar a los demás clientes que ese NPC dejó de existir.
                synchronized(npcAiManager.pendingDespawns) { npcAiManager.pendingDespawns.add(carId) }
                if (carNpc.isFirstTimeBoarded) {
                    spawnOustedDriver(carNpc.location)
                    raiseWantedLevel(1) // robar un auto ocupado es delito → +1 estrella
                }
                // Si el coche traía skin de patrulla (una patrulla que abandonaste), al
                // re-subirte vuelves a conducirla con el skin de policía.
                _uiState.update { it.copy(isDriving = true, currentVehicleModel = carNpc.carModel, currentVehicleColor = carNpc.carColor, vehicleRotation = (carNpc.rotationAngle + 90f) % 360f, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = false, isDrivingPoliceCar = carNpc.isPoliceSkin) }
                // H (Modo Historia): si Prankedy es tu ACOMPAÑANTE (escolta), debe SUBIR contigo: corre
                // hasta tu posición y el coche NO avanza hasta que se sube (lo completa runPrankedyTick).
                if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED &&
                    _uiState.value.prankedyEnabled && prankedyManager.location != null) {
                    prankedyBoardingStartMs = nowMs
                    _uiState.update { it.copy(prankedyBoarding = true) }
                }
                prankedyManager.onVehicleInteraction()
                updateNpcsState()
                return
            }

            // PATRULLAS: si no hay coche civil cerca, intenta SUBIRTE a una patrulla. Las
            // patrullas las posee PoliceManager (no remoteEntities), así que se buscan en
            // sus unidades activas. Robar una patrulla = nivel de búsqueda MÁXIMO (5★).
            val nearbyPatrol = policeManager.activeUnits()
                .filter { it.type == NpcType.POLICE_CAR && distance(loc, it.location) <= INTERACT_RADIUS }
                .minByOrNull { distance(loc, it.location) }
            if (nearbyPatrol != null) {
                val boarded = policeManager.boardPatrol(nearbyPatrol.id)
                if (boarded != null) {
                    lastVehicleToggleMs = nowMs
                    // Avisar a los demás clientes que esa patrulla dejó de existir.
                    webSocketManager?.let { ws ->
                        viewModelScope.launch(Dispatchers.IO) {
                            try { ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to boarded.id))) } catch (_: Exception) {}
                        }
                    }
                    // Subirse a la patrulla pone TODAS las estrellas (5★).
                    lastCrimeTime = nowMs
                    _uiState.update { it.copy(
                        isDriving = true,
                        currentVehicleModel = boarded.carModel,
                        currentVehicleColor = boarded.carColor,
                        vehicleRotation = (boarded.rotationAngle + 90f) % 360f,
                        vehicleSpeed = 0.0,
                        vehicleIsFirstTimeBoarded = false,
                        isDrivingPoliceCar = true,
                        wantedLevel = MAX_WANTED_LEVEL
                    ) }
                    prankedyManager.onVehicleInteraction()
                    updateNpcsState()
                }
            }
        } else {
            lastVehicleToggleMs = nowMs
            val abandonedCar = Npc(
                id = UUID.randomUUID().toString(),
                type = NpcType.CAR,
                location = loc,
                rotationAngle = (_uiState.value.vehicleRotation + 270f) % 360f,
                speed = 0.0,
                isMoving = false,
                carModel = _uiState.value.currentVehicleModel ?: CarModel.SEDAN,
                carColor = _uiState.value.currentVehicleColor ?: 0xFFFFFFFF.toInt(),
                isFirstTimeBoarded = _uiState.value.vehicleIsFirstTimeBoarded,
                // Si te bajas de una PATRULLA robada, el coche que queda conserva el skin de
                // patrulla (sigue siendo tipo CAR para que la IA lo conduzca como tráfico).
                isPoliceSkin = _uiState.value.isDrivingPoliceCar,
                navState = if (isInsideEscom(loc.latitude, loc.longitude)) ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED else ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM
            )
            remoteEntities[abandonedCar.id] = abandonedCar
            _uiState.update { it.copy(isDriving = false, currentVehicleModel = null, currentVehicleColor = null, vehicleSpeed = 0.0, vehicleIsFirstTimeBoarded = true, isDrivingPoliceCar = false, prankedyBoarding = false) }
            prankedyManager.onVehicleInteraction()
            updateNpcsState()
        }
    }

    // ─── MODO DISEÑADOR / LANDMARKS (REFACTOR) ────────────────────────────────
    // loadLandmarks / toggleDesignerMode / showAssetPicker / selectLandmark /
    // addLandmarkAtPlayer / moveSelectedLandmark / moveLandmarkTo / rotateSelectedLandmark /
    // scaleXSelectedLandmark / scaleYSelectedLandmark / deleteSelectedLandmark /
    // exportLandmarksToUri / importLandmarksFromUri / saveSelectedLandmark viven en
    // WorldMapDesigner.kt (el estado escomNavGraph sigue aquí).

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

    fun teleportToMetrobusStation(stationName: String) {
    val station = _uiState.value.metrobusStations.find { it.name.equals(stationName, ignoreCase = true) }
    station?.let { teleportTo(it.location.latitude, it.location.longitude) }
    }

    fun loadMetrobusStations(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val stations = MetrobusRepository.loadStations(context)
            _uiState.update { it.copy(metrobusStations = stations) }
        }
    }

    fun toggleTeleportMenu(show: Boolean) { _uiState.update { it.copy(showTeleportMenu = show) } }

    fun teleportTo(lat: Double, lon: Double) {
        // GATE DE TELETRANSPORTE: no se acepta otro TP hasta que el mundo actual esté
        // COMPLETAMENTE listo (mapa descargado + red de calles). Evita TPs encadenados
        // que dejaban la carga a medias y los NPCs mal puestos.
        val st0 = _uiState.value
        if (!st0.isLoadingLocation && (!st0.isMapReady || !st0.isRoadNetworkReady)) {
            _uiState.update { it.copy(showTeleportMenu = false, interactionPrompt = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_wait_loading)) }
            viewModelScope.launch {
                delay(2500)
                _uiState.update { if (it.interactionPrompt?.startsWith("⏳") == true) it.copy(interactionPrompt = null) else it }
            }
            return
        }
        val newLocation = org.osmdroid.util.GeoPoint(lat, lon)
        // Limpia los NPCs locales de la zona vieja: se regeneran cuando la nueva zona
        // esté completamente lista (ver gate del game loop). Sin esto quedaban NPCs
        // "fantasma" de la zona anterior mientras cargaba la nueva.
        val staleNpcIds = remoteEntities.entries
            .filter { it.value.displayName.isNullOrEmpty() }
            .map { it.key }
        staleNpcIds.forEach { remoteEntities.remove(it) }
        npcAiManager.setServerNpcs(emptyList())
        npcWarmupCycles = 0   // re-arma el warm-up de NPCs del gate de carga
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
                npcsWarmedUp = false,      // ← y tampoco hasta que la IA siembre los NPCs
                isUserPanningMap = false,  // ← recentra el mapa y reactiva la neblina
                wantedLevel = 0,
                carjackWarning = null
            )
        }
        // El acompañante (Prankedy, campaña) se TELETRANSPORTA contigo (si no, quedaba atrás).
        warpPrankedyCompanionTo(newLocation)
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
                    val promptText = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_prompt_metro, nearbyMetro.name.uppercase())
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

        // 1b. Verificar cercanía a estaciones del Metrobús
        val metrobusStations = _uiState.value.metrobusStations
        val nearbyMetrobus = metrobusStations.minByOrNull { playerGeo.distanceToAsDouble(it.location) }

        if (nearbyMetrobus != null && playerGeo.distanceToAsDouble(nearbyMetrobus.location) <= INTERACT_RADIUS_METERS) {
            if (_uiState.value.nearbyMetrobusStation?.name != nearbyMetrobus.name) {
                _uiState.update { it.copy(nearbyMetrobusStation = nearbyMetrobus, nearbyCollectible = null) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = "PRESIONA X PARA ENTRAR A ESTACIÓN METROBÚS ${nearbyMetrobus.name.uppercase()}"
                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
            return
        }

        if (_uiState.value.nearbyMetrobusStation != null) {
            _uiState.update { it.copy(nearbyMetrobusStation = null, interactionPrompt = null) }
        }

        // 2. Recopilamos los collectibles normales y de ESCOM (nuestro código)
        val baseItems = _uiState.value.activeCollectibles + _escomItems.value

        // Convertimos los Landmarks de tipo "Puerta" en collectibles virtuales interactuables
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
                        activeItem.id == "global_zombie_hand" -> if (_uiState.value.globalZombieMode) getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_deactivate_zombie) else getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_activate_zombie)
                        activeItem.name == "Objeto Misterioso ESCOM" -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_interact)
                        activeItem.id == ShineCTOLocation.MARKER_ID  -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_enter)
                        activeItem.id.startsWith("escom_door_")      -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_enter) // <--- Aquí aparece el texto de la puerta
                        else -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_pickup)
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
        // Si YA estás muerto o en la pantalla WASTED, ignora el daño: si no, los zombis cercanos
        // al cuerpo seguían llamando takeDamage durante el WASTED y el 💥 aparecía "a cada rato"
        // al morir (y re-disparaban la secuencia).
        if (_uiState.value.showWastedScreen || playerHealth <= 0f) return
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++
        if (playerHealth > 0f) fireImpactEffect() // 💥 solo si SOBREVIVES (no en el golpe mortal)
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
        if (playerHealth <= 0f) {
            triggerWastedSequence()
        }
        // Notificar a Prankedy para que active su búsqueda de agresor
        if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
            prankedyManager.onPlayerDamaged()
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
            // MODO HISTORIA: morir DURANTE una misión de campaña = MISIÓN FALLIDA (reinicia desde el
            // último checkpoint con "REINTENTAR MISIÓN"), NO el respawn normal — si no, el jugador podría
            // dejarse matar para SALTARSE todo el trayecto de la escolta de Prankedy. Este es el MIEMBRO
            // (gana sobre la extensión homónima de WorldMapMisc.kt, que está sombreada — ver 09).
            val missionObj = _uiState.value.currentObjective
            val inMission = inCampaign && (
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.MissionCatalog.ESCOLTAR_PRANKEDY.id ||
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.MissionCatalog.INGRESAR_ESCOM.id)
            if (inMission) {
                delay(2500L)
                relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
                clearCampaignPolice()
                carjackStartTime = 0L
                playerHealth = maxPlayerHealth
                damagePulseTrigger = 0
                impactEffectTrigger = 0
                _uiState.update { it.copy(wantedLevel = 0, carjackWarning = null, isDrivingPoliceCar = false, showWastedScreen = false, showMissionFailed = true) }
                return@launch
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
            // RESPAWN EN ESCOM: Al morir, el jugador es llevado de vuelta a la ESCOM.
            val respawn = GeoPoint(19.504603, -99.145985)
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
        soundManager.playPunch()
        viewModelScope.launch(Dispatchers.Default) {
            delay(300L)
            val playerLoc = _uiState.value.currentLocation ?: return@launch
            // PRANKEDY hostil: el jugador puede defenderse golpeándolo. Si lo mata, desaparece
            // y reaparece tras un tiempo (lo gestiona PrankedyManager; el render lo oculta al
            // quedar su location en null).
            // En el MODO HISTORIA (escolta, fase HIRED) Prankedy es tu acompañante: TÚ NO le
            // puedes pegar (solo la policía puede dañarlo). Fuera de la escolta (Prankedy hostil)
            // sí puedes golpearlo para defenderte.
            if (prankedyManager.phase != ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
                prankedyManager.location?.let { pkLoc ->
                    if (distance(playerLoc, pkLoc) <= ATTACK_RADIUS) {
                        prankedyManager.takeDamage(PLAYER_PUNCH_DAMAGE)
                        viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() }
                    }
                }
            }
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
            // APOCALIPSIS: golpear a un policía CAZADOR (en remoteEntities, POLICE_COP) lo daña y lo
            // PROVOCA — a él y a los cercanos → te persiguen. (La policía del sistema de delitos está
            // en policeManager y no corre en apocalipsis.)
            if (_uiState.value.globalZombieMode) {
                val nowP = System.currentTimeMillis()
                var hitCop = false
                remoteEntities.entries.toList().forEach { (id, n) ->
                    if (n.type == ovh.gabrielhuav.pow.domain.models.NpcType.POLICE_COP &&
                        distance(playerLoc, n.location) <= ATTACK_RADIUS) {
                        val nh = (n.health - PLAYER_PUNCH_DAMAGE).coerceAtLeast(0f)
                        if (nh <= 0f) {
                            remoteEntities.remove(id)
                            try { webSocketManager?.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to id))) } catch (_: Exception) {}
                        } else {
                            remoteEntities[id] = n.copy(health = nh, aggroUntil = nowP + NpcAiManager.AGGRO_DURATION_MS)
                        }
                        hitCop = true
                    }
                }
                if (hitCop) {
                    provokeApocalypsePolice(playerLoc)
                    viewModelScope.launch(Dispatchers.Main) { fireImpactEffect(); updateNpcsState() }
                }
            }
            val targetNpcEntry = remoteEntities.entries
                .filter {
                    !it.value.isDying &&
                            (it.value.type == NpcType.PERSON || it.value.type == ovh.gabrielhuav.pow.domain.models.NpcType.ZOMBIE) &&
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
                        // FIX: Feedback visual para el atacante al golpear a otro jugador
                        viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() }
                    } catch (e: Exception) { Log.e("Combat", "Error enviando PLAYER_DAMAGE: ${e.message}") }
                } else {
                    // DELITO: golpear a un civil sube el nivel de búsqueda (como en GTA).
                    if (currentNpc.type == NpcType.PERSON) raiseWantedLevel(1)
                    // APOCALIPSIS: agredir a un CIVIL provoca a la policía que esté en tu fog (te ven).
                    if (_uiState.value.globalZombieMode && currentNpc.type == NpcType.PERSON) {
                        provokeApocalypsePolice(playerLoc)
                    }
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
                        // KNOCKBACK: al golpear un ZOMBI que sobrevive, lo empujamos hacia atrás
                        // (alejándolo del jugador) para que se note el golpe, como en el minijuego.
                        // El Host lo retoma desde remoteEntities en el siguiente tick (recoil visible).
                        val knockedLoc = if (currentNpc.type == ovh.gabrielhuav.pow.domain.models.NpcType.ZOMBIE) {
                            val dLat = currentNpc.location.latitude - playerLoc.latitude
                            val dLon = currentNpc.location.longitude - playerLoc.longitude
                            val d = kotlin.math.sqrt(dLat * dLat + dLon * dLon)
                            if (d > 1e-9) {
                                val kb = 0.00007 // ~7-8 m de empuje
                                GeoPoint(currentNpc.location.latitude + (dLat / d) * kb, currentNpc.location.longitude + (dLon / d) * kb)
                            } else currentNpc.location
                        } else currentNpc.location
                        remoteEntities[npcId] = currentNpc.copy(
                            location = knockedLoc,
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
    internal fun runOverNpcs(playerLoc: GeoPoint, speed: Double, isAutoDodging: Boolean = false) {
        if (!isServerDelegatedHost) return
        val spd = kotlin.math.abs(speed)
        if (spd < RUN_OVER_MIN_SPEED) return
        val damage = (spd / MAX_SPEED).toFloat().coerceIn(0f, 1f) * 120f
        val extreme = spd >= RUN_OVER_EXTREME_SPEED
        val now = System.currentTimeMillis()
        // Vector de avance del coche y su perpendicular (para esquivar/empujar a un lado).
        val heading = Math.toRadians(_uiState.value.vehicleRotation.toDouble())
        val hdLat = cos(heading); val hdLon = sin(heading)
        val perpLat = -hdLon; val perpLon = hdLat

        fun killOrHurt(id: String, npc: Npc, giveStar: Boolean = false) {
            val newHealth = (npc.health - damage).coerceAtLeast(0f)
            if (newHealth <= 0f) {
                remoteEntities[id] = npc.copy(health = 0f, isDying = true)
                if (giveStar) raiseWantedLevel(1)
                viewModelScope.launch { delay(1000L); remoteEntities.remove(id); updateNpcsState() }
                try { webSocketManager?.sendMessage(gson.toJson(mapOf("type" to "NPC_DESTROY", "npcId" to id))) } catch (_: Exception) {}
            } else remoteEntities[id] = npc.copy(health = newHealth)
        }
        // Empuja un NPC al lado HACIA EL QUE YA ESTÁ (lo saca del camino del coche).
        fun shove(npc: Npc, dist: Double): GeoPoint {
            val rel = (npc.location.latitude - playerLoc.latitude) * perpLat + (npc.location.longitude - playerLoc.longitude) * perpLon
            val s = if (rel >= 0) 1.0 else -1.0
            return GeoPoint(npc.location.latitude + perpLat * dist * s, npc.location.longitude + perpLon * dist * s)
        }

        var changed = false
        var impactWorthy = false   // 💥 solo en atropello real o choque de auto, no al esquivar
        for ((id, npc) in remoteEntities) {
            if (!npc.displayName.isNullOrEmpty()) continue
            if (npc.isDying) continue
            val d = distance(playerLoc, npc.location)
            when (npc.type) {
                NpcType.PERSON -> {
                    if (extreme && d <= RUN_OVER_RADIUS * 0.4) {
                        // Vas casi A FONDO (>= RUN_OVER_EXTREME_SPEED) → los reflejos no alcanzan, lo atropellas.
                        // Hitbox reducida (0.4) para que sea más difícil darles. Si los matas, obtienes 1 estrella.
                        killOrHurt(id, npc, giveStar = true); changed = true; impactWorthy = true
                        continue
                    }
                    if (d > DODGE_TRIGGER_RADIUS) continue
                    // ¿Está DELANTE del coche? (producto punto con el avance). Solo esos esquivan.
                    val relLat = npc.location.latitude - playerLoc.latitude
                    val relLon = npc.location.longitude - playerLoc.longitude
                    if (relLat * hdLat + relLon * hdLon <= 0) continue   // detrás/al costado: ignóralo
                    // MIDNIGHT CLUB: marca el ESQUIVE ANIMADO (el Host lo anima como sidestep suave, NO
                    // teletransporte). Si ya está esquivando, no lo re-disparo (dirección estable).
                    if (npc.dodgeUntil <= now) {
                        val rel = relLat * perpLat + relLon * perpLon
                        val s = if (rel >= 0) 1.0 else -1.0   // hacia el lado al que ya se inclina
                        remoteEntities[id] = npc.copy(
                            dodgeUntil = now + DODGE_MS,
                            dodgeDirLat = perpLat * s,
                            dodgeDirLon = perpLon * s
                        )
                        changed = true
                    }
                }
                ovh.gabrielhuav.pow.domain.models.NpcType.ZOMBIE -> {
                    if (d > RUN_OVER_RADIUS) continue
                    // Los zombis NO esquivan (shamblean): atropellables a cualquier velocidad.
                    killOrHurt(id, npc); changed = true; impactWorthy = true
                }
                NpcType.CAR, ovh.gabrielhuav.pow.domain.models.NpcType.POLICE_CAR -> {
                    // Si estamos haciendo un rebase profesional suave, desactivamos la colisión
                    // para permitir pasar rozando.
                    if (isAutoDodging) continue
                    if (d > CAR_BUMP_RADIUS) continue
                    // CHOCAMOS: Si no hubo rebase suave (por ir muy rápido o giro brusco), chocamos.
                    // Empujas el auto a un lado (rebasas tipo Toretto) + 💥. Sin daño al jugador.
                    remoteEntities[id] = npc.copy(location = shove(npc, CAR_BUMP_RADIUS * 0.6))
                    changed = true; impactWorthy = true
                }
                else -> continue
            }
        }
        if (changed) {
            npcAiManager.triggerFear(playerLoc.latitude, playerLoc.longitude)
            updateNpcsState()
        }
        if (impactWorthy) viewModelScope.launch(Dispatchers.Main) { fireImpactEffect() } // 💥 (con throttle)
    }

    // GOLPE DE NPC AGRESIVO: los NPCs en estado de embestida (aggro) que tocan al
    // jugador le hacen daño, con un cooldown por NPC para no vaciar la vida de golpe.
    // Provoca a la policía del apocalipsis que esté en tu FOG (en frente): pasa a perseguirte
    // (aggroUntil). Se llama al golpear a un poli o al agredir a un civil con un poli cerca.
    internal fun provokeApocalypsePolice(playerLoc: GeoPoint) {
        if (!_uiState.value.globalZombieMode) return
        val until = System.currentTimeMillis() + NpcAiManager.AGGRO_DURATION_MS
        val fogDeg = 0.0003 // ~33 m: el poli debe ver el crimen LITERALMENTE enfrente para reaccionar
        var changed = false
        remoteEntities.entries.toList().forEach { (id, n) ->
            if (n.type == ovh.gabrielhuav.pow.domain.models.NpcType.POLICE_COP &&
                distance(playerLoc, n.location) <= fogDeg) {
                remoteEntities[id] = n.copy(aggroUntil = until)
                changed = true
            }
        }
        if (changed) updateNpcsState()
    }

    internal fun applyNpcContactDamage(playerLoc: GeoPoint) {
        // Muerto / en WASTED: no recibas daño (evita el 💥 repetido sobre el cadáver).
        if (_uiState.value.showWastedScreen || playerHealth <= 0f) return
        // SIN gate de host: el daño se aplica a TU PROPIO jugador en TU cliente, seas o no
        // el host de zona (el host solo decide quién SIMULA la IA, no quién recibe daño).
        val now = System.currentTimeMillis()
        for ((id, npc) in remoteEntities) {
            val isAggroPerson = npc.type == NpcType.PERSON && npc.aggroUntil > now
            // Policía del apocalipsis PROVOCADA (la golpeaste o agrediste a un civil frente a ella).
            val isAggroCop = npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.POLICE_COP && npc.aggroUntil > now
            // El SCOUT ("Explorador") NO ataca al jugador (solo grita y huye).
            val isZombie = npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.ZOMBIE && npc.health > 0f &&
                    npc.zombieRole != ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT
            if (!isAggroPerson && !isAggroCop && !isZombie) continue
            if (distance(playerLoc, npc.location) > NPC_CONTACT_RADIUS) continue
            if (_uiState.value.isDriving) continue // en coche no te golpean (te bajan, no te pegan)
            // Mordida de zombi: cooldown GLOBAL (daño moderado aunque te rodeen muchos).
            if (isZombie) {
                if (now - lastZombieBiteMs < ZOMBIE_BITE_TO_PLAYER_MS) continue
                lastZombieBiteMs = now
                npcContactCooldowns[id] = now
                viewModelScope.launch(Dispatchers.Main) { takeDamage(ZOMBIE_BITE_TO_PLAYER_DMG) }
                continue
            }
            val last = npcContactCooldowns[id] ?: 0L
            if (now - last < NPC_CONTACT_COOLDOWN_MS) continue
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
     * Sincroniza los items de ESCOM. La "Mano del Apocalipsis" se ELIMINÓ: ya no se
     * spawnea ninguna mano (el apocalipsis se activa desde el menú de Opciones).
     */
    fun spawnEscomItems(roadNetwork: List<MapWay>, cantidad: Int = 1) {
        // La "Mano del Apocalipsis" se ELIMINÓ de ESCOM (a petición). El modo zombi global
        // se activa/desactiva desde Opciones → "Activar/Desactivar Apocalipsis" (o el botón
        // flotante de salida). Aquí ya no se spawnea ninguna mano: dejamos vacíos los items
        // de ESCOM y marcamos el flag "sincronizado" para que el game loop no re-llame.
        if (_escomItems.value.any { it.id == "global_zombie_hand" }) {
            _escomItems.value = _escomItems.value.filter { it.id != "global_zombie_hand" }
        }
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

    // ─── APOCALIPSIS ZOMBI GLOBAL ────────────────────────────────────────

    fun toggleGlobalZombieMode() = setZombieInstance(!_uiState.value.globalZombieMode)

    fun exitGlobalZombieMode() { if (_uiState.value.globalZombieMode) setZombieInstance(false) }

    /**
     * INSTANCING: activar/desactivar el apocalipsis = cambiar de INSTANCIA ("apocalipsis" /
     * "normal"). Limpiamos el mundo local (no arrastrar entidades de la otra instancia) y
     * pedimos al servidor (JOIN_INSTANCE) el roster de la nueva instancia. Así los jugadores en
     * "normal" no ven el apocalipsis y viceversa. En single-player solo cambia el flag local
     * (el toggle no manda red) y el seed repobla el mundo según el modo.
     */
    private fun setZombieInstance(apocalypse: Boolean) {
        _uiState.update { it.copy(globalZombieMode = apocalypse) }
        npcAiManager.globalZombieMode = apocalypse
        // Mundo limpio: vaciar entidades remotas (el servidor reenvía SYNC_ALL_NPCS de la nueva
        // instancia; en SP el seed repobla). Evita ver NPCs/zombis de la instancia anterior.
        remoteEntities.clear()
        updateNpcsState()
        try {
            webSocketManager?.sendMessage(gson.toJson(mapOf(
                "type" to "JOIN_INSTANCE",
                "instance" to if (apocalypse) "apocalipsis" else "normal"
            )))
        } catch (_: Exception) {}
    }

    /**
     * Interacción con la mano: en lugar de entrar a un interior concreto, marca
     * el flag pendingZombieMinigame para que, tras el video, WorldMapScreen
     * navegue a la ruta "interiores_zombies" (modo Interiores → capa zombis).
     */
    fun handleInteraction() {
        val nearbyMetro = _uiState.value.nearbyMetroStation
        if (nearbyMetro != null) {
            _uiState.update { it.copy(showMetroFade = true) }
            return
        }

        val nearbyMetrobus = _uiState.value.nearbyMetrobusStation
        if (nearbyMetrobus != null) {
            _uiState.update { it.copy(showMetrobusFade = true) }
            return
        }

        val nearby = _uiState.value.nearbyCollectible ?: return

        when {
            nearby.id == "global_zombie_hand" -> toggleGlobalZombieMode()
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
                // Enrutamos la puerta a su interior por el NOMBRE del landmark. Usamos
                // `contains` (no match exacto) para tolerar variantes/acentos/espacios al
                // colocar la puerta en el Diseñador: si el nombre no casa EXACTO, antes la
                // puerta caía al `else` y mandaba al minijuego zombi por error. Las puertas
                // ESCOM (p. ej. "Puerta Norte/Sur ESCOM") siguen yendo al minijuego por el else.
                val n = nearby.name
                val targetRoute = when {
                    n.contains("Béisbol", ignoreCase = true) || n.contains("Beisbol", ignoreCase = true) -> "interior_deportivo_beis"
                    n.contains("Fútbol", ignoreCase = true) || n.contains("Futbol", ignoreCase = true) -> "interior_deportivo_futbol"
                    // FES Aragón usa el MOTOR DE INTERIORES (mismos controles, opciones, botones
                    // de acción y HUD ZONA/vida/MODO) pero arranca en SU PROPIA sala "fes_interior"
                    // (no el lobby de ESCOM): el arg startRoom selecciona la sala del catálogo.
                    n.contains("FES", ignoreCase = true) -> "interiores_zombies?startRoom=fes_interior"
                    // Puertas ESCOM (Norte/Sur, etc.) → lobby de ESCOM (sin arg = default).
                    else -> "interiores_zombies"
                }
                // MODO HISTORIA · Misión 2 "Ingresa a la ESCOM": se cumple al ENTRAR por la puerta
                // (este es el momento de "ingresar"). Marca el objetivo cumplido + jingle.
                if (_uiState.value.currentObjective?.id == ovh.gabrielhuav.pow.domain.models.MissionCatalog.INGRESAR_ESCOM.id
                    && !_uiState.value.objectiveDone) {
                    _uiState.update { it.copy(objectiveDone = true, interactionPrompt = "✅ Objetivo cumplido: ${_uiState.value.currentObjective?.title ?: ""}") }
                    soundManager.playMisionCumplida()
                }
                // Al ENTRAR a la ESCOM, Prankedy ya quedó a salvo dentro: deja de acompañarte para
                // que NO siga contigo al volver al mapa (Misión 1 terminada). Solo afecta al
                // acompañante de campaña (fase HIRED); el Prankedy hostil del menú no se toca aquí.
                if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
                    prankedyManager.deactivate()
                    prankedyCompanionActivated = true   // no re-encenderlo en este tramo
                    _uiState.update { it.copy(
                        prankedyEnabled = false,
                        prankedyVisible = false,
                        prankedyLocation = null,
                        prankedyProjectileActive = false,
                        prankedyDialogue = null
                    ) }
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

    // ─── Jugabilidad (live, desde Ajustes) ──────────────────────────────────
    /** Multiplicador de densidad de NPCs elegido por el usuario (se combina con gama/ciudad). */
    fun setNpcDensity(v: Float) { npcAiManager.userPopulationFactor = v }
    /** NPCs lejanos como emoji (optimización gama baja). */
    fun setNpcEmojiLod(enabled: Boolean) { _uiState.update { it.copy(npcEmojiLod = enabled) } }

    fun setNpcFullEmoji(enabled: Boolean) { _uiState.update { it.copy(npcFullEmoji = enabled) } }

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

    // ¿El punto está dentro del bounding box del campus de la ENCB? (zona libre de campaña)
    internal fun isInsideEncb(lat: Double, lon: Double): Boolean {
        return abs(lat - ENCB_BASE_LAT) < ENCB_OFFSET &&
                abs(lon - ENCB_BASE_LON) < ENCB_OFFSET
    }

    // ZONA DE MOVIMIENTO LIBRE: ESCOM o ENCB. Dentro de cualquiera de los dos campus se
    // suspende la restricción de malla vial (jugador y Prankedy se mueven libres en (x,y)).
    // internal → accesible también desde las extensiones (p. ej. WorldMapPrankedy.kt).
    internal fun isFreeMovementZone(lat: Double, lon: Double): Boolean {
        return isInsideEscom(lat, lon) || isInsideEncb(lat, lon)
    }

    // ¿El punto cae SOBRE el footprint de algún landmark/asset del mapa? Se usa SOLO para el
    // movimiento del JUGADOR: estando sobre un asset (p. ej. el estacionamiento) se suspende el
    // snap a la red de calles y te mueves libre en (x,y). OJO: NO se mete en isFreeMovementZone
    // a propósito → así las calles SIGUEN dibujándose y los NPCs SIGUEN atados a la malla vial.
    // Footprint = caja del asset en metros (baseW/H × escala), alineada a ejes (ignora rotación).
    internal fun isOnLandmark(lat: Double, lon: Double): Boolean {
        val lms = _uiState.value.landmarks
        if (lms.isEmpty()) return false
        val cosLat = kotlin.math.cos(Math.toRadians(lat))
        var bestEdgeM = Double.MAX_VALUE   // diagnóstico: qué tan "dentro/fuera" del landmark más cercano
        var on = false
        for (lm in lms) {
            val halfW = (lm.baseWidthMeters * lm.scaleX) / 2.0
            val halfH = (lm.baseHeightMeters * lm.scaleY) / 2.0
            val dLatM = (lat - lm.location.latitude) * 111_320.0
            val dLonM = (lon - lm.location.longitude) * 111_320.0 * cosLat
            val edge = kotlin.math.max(kotlin.math.abs(dLatM) - halfH, kotlin.math.abs(dLonM) - halfW)
            if (edge < bestEdgeM) bestEdgeM = edge
            if (kotlin.math.abs(dLatM) <= halfH && kotlin.math.abs(dLonM) <= halfW) { on = true }
        }
        // DIAGNÓSTICO (POW_DBG, throttle ~1.5 s): nº de landmarks y "borde" del más cercano (m). Si
        // bestEdge>0 NUNCA estás sobre un asset (footprints chicos o estás siempre en la calle).
        val nowDbg = System.currentTimeMillis()
        if (nowDbg - lastLandmarkDbgMs > 1500L) {
            lastLandmarkDbgMs = nowDbg
            android.util.Log.d("POW_DBG", "isOnLandmark: lms=${lms.size} on=$on bordeMasCercano=${"%.1f".format(bestEdgeM)}m")
        }
        return on
    }
    private var lastLandmarkDbgMs = 0L

    // Normaliza un navgraph recién deserializado. Gson NO aplica los defaults de Kotlin a los campos
    // AUSENTES del JSON: si los `ways` de escom_navgraph.json no traen isForCars/isForPeople, llegan
    // como `false` → los autos NO casan con NINGÚN carril (matchType en NpcAiManager) y los carros del
    // estacionamiento "no surten efecto". Restauramos la intención por la convención de id que ya usa la
    // IA (id < 200 = autos, id >= 200 = peatonal). Solo toca ways que vienen sin clasificar (ambos false).
    internal fun normalizeNavGraph(ng: LandmarkNavGraph?): LandmarkNavGraph? {
        if (ng == null) return null
        val fixedWays = ng.ways.map { w ->
            if (!w.isForCars && !w.isForPeople) w.copy(isForCars = w.id < 200, isForPeople = w.id >= 200) else w
        }
        return ng.copy(ways = fixedWays)
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
                escomNavGraph = normalizeNavGraph(Gson().fromJson(reader, ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph::class.java))
                reader.close()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_error_escom_navgraph), android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        val navGraph = escomNavGraph ?: return

        // 2. Buscar el edificio ESCOM en el mapa
        val escomLandmarkBase = _uiState.value.landmarks.find { it.assetPath.contains("building_escom", ignoreCase = true) }
        if (escomLandmarkBase == null) {
            android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_error_escom_missing), android.widget.Toast.LENGTH_SHORT).show()
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

        android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_car_injected), android.widget.Toast.LENGTH_SHORT).show()
    }

    // ─── Selector de skin ────────────────────────────────────────────────

    fun toggleSkinSelector(show: Boolean) {
        _uiState.update { it.copy(showSkinSelector = show) }
    }

    fun selectSkin(skin: ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin) {
        settingsRepository.savePlayerSkin(skin)
        _uiState.update { it.copy(selectedSkin = skin, showSkinSelector = false) }
    }

    fun refreshSkin() {
        _uiState.update { it.copy(selectedSkin = settingsRepository.getPlayerSkin()) }
    }

    // ─── ShineCTO Easter Egg ────────────────────────────────────────────────

    fun spawnShineCTOMarker() {
        if (_uiState.value.activeCollectibles.none { it.id == ShineCTOLocation.MARKER_ID }) {
            val marker = ActiveCollectible(
                id          = ShineCTOLocation.MARKER_ID,
                name        = ShineCTOLocation.MARKER_NAME,
                description = "easter_egg",
                assetPath   = "PLACES/shine_cto/s_logo.webp",
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
    fun onMetrobusFadeComplete() {
        val station = _uiState.value.nearbyMetrobusStation
        if (station != null) {
            _uiState.update {
                it.copy(
                    showMetrobusFade = false,
                    metrobusFadeCompleteStation = station,
                    nearbyMetrobusStation = null,
                    interactionPrompt = null
                )
            }
        }
    }

    fun consumeMetrobusFadeComplete() {
        _uiState.update { it.copy(metrobusFadeCompleteStation = null) }
    }
}
