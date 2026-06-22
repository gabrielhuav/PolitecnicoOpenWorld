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
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
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
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.InputStreamReader
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig

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
    // ¿El mapa global está en primer plano? El game loop es Activity-scoped y SIGUE corriendo cuando
    // entras a un interior (solo se detiene en onCleared). Sin esto, el audio del loop (stopWalk cada
    // tick con el jugador exterior quieto) PISABA el sonido de pasos de los interiores. WorldMapScreen
    // pone este flag a true/false con un DisposableEffect; el bloque de audio del loop se gatea con él.
    @Volatile internal var worldMapForeground = false

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

                        // ─── AUDIO (de-dup par 8 + GATE 2026-06-21): bloque FUSIONADO desde la extensión muerta
                        // WorldMapGameLoop.kt. soundManager es internal. **GATEADO por worldMapForeground**: el
                        // game loop sigue corriendo en interiores (es Activity-scoped, solo para en onCleared), y
                        // sin este gate su `stopWalk()` por tick PISABA el sonido de pasos de los interiores. En
                        // un interior NO tocamos audio aquí; cada interior gestiona el suyo. ────────────────────
                        if (worldMapForeground) {
                        if (!_uiState.value.isDriving) {
                            when (_uiState.value.playerAction) {
                                PlayerAction.WALK -> {
                                    soundManager.playWalk()
                                    soundManager.stopRun()
                                }
                                PlayerAction.RUN -> {
                                    soundManager.playRun()
                                    soundManager.stopWalk()
                                }
                                else -> {
                                    soundManager.stopWalk()
                                    soundManager.stopRun()
                                }
                            }
                        } else {
                            soundManager.stopWalk()
                            soundManager.stopRun()
                        }

                        // FIX (par 8, 2026-06-21): el sonido de coche suena SOLO cuando TÚ conduces y te
                        // mueves. La lógica original (de la extensión muerta, nunca probada) también lo
                        // activaba con CUALQUIER coche-NPC en movimiento a <0.001° (~111 m); como siempre
                        // hay tráfico, sonaba a coche aunque fueras a pie. Se quitó el tráfico ambiental.
                        val playerDrivingMoving = _uiState.value.isDriving &&
                            kotlin.math.abs(_uiState.value.vehicleSpeed) > 0.0
                        if (playerDrivingMoving) soundManager.playCar() else soundManager.stopCar()

                        if (tickCount % 150 == 0L) {
                            var zombieNear = false
                            for (npc in remoteEntities.values) {
                                if (npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE) {
                                    if (distance(location, npc.location) < 0.0005) {
                                        zombieNear = true
                                        break
                                    }
                                }
                            }
                            if (zombieNear) soundManager.playZombieNear()
                        }
                        } // ── fin GATE worldMapForeground (audio del mapa global) ──

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
                                (driveObjId == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOLTAR_PRANKEDY.id ||
                                 driveObjId == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id) &&
                                location.distanceToAsDouble(GeoPoint(
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOM_FORCEWALK_LAT,
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOM_FORCEWALK_LON)
                                ) <= ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOM_FORCEWALK_RADIUS_M

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
                                if (npc.type == NpcType.CAR || npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.POLICE_CAR) {
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
                val jsonString = context.assets.open("CONFIG/exterior_collisions.json").bufferedReader().use { it.readText() }
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

    // DE-DUP: `applyRoadNetwork` ahora vive SOLO como extensión en WorldMapRoadNetwork.kt
    // (sincronizada al cuerpo de este miembro antes de borrarlo). Ver 09 §12.

    // maybeRefetchRoadNetwork(currentLoc) vive en WorldMapRoadNetwork.kt (de-dup 2026-06-21, par 5:
    // miembro canónico movido a la extensión homónima, sincronizado: reconstruye grid+grafo A*).
    // Cascada verificada: rebuildRoadNodeGrid gemelo IDÉNTICO; buildRoadGraph/isInsideEscom/spawnEscomItems
    // def única. NO toca la cadena de routing. (≠ par 2: aquí ningún gemelo de la cascada diverge.)

    // updateVisibleRoads(location, force) vive en WorldMapRoadNetwork.kt (de-dup 2026-06-21, par 6:
    // miembro canónico movido a la extensión homónima, sincronizado: filtro circular + anti-carrera).
    // Cascada verificada: distance/isFreeMovementZone son miembros internal únicos (sin gemelo); todos
    // los call-sites son posicionales (el param se llama `location` en la extensión). (≠ par 2.)

    // handleMultiplayerMessage(messageJson) vive en WorldMapMultiplayer.kt (de-dup 2026-06-21, par 7).
    // Era un gemelo DIVERGENTE en ambos sentidos; se FUSIONÓ en la extensión (safeDisplayName del miembro
    // + los 3 arreglos que la extensión ya tenía pero estaban muertos: MASTER_SYNC_CHECK isRemote,
    // PLAYER_DAMAGE en hilo Main, miedo al combate). Miembro borrado. Ver DEDUP_VM_pendiente.md par 7.

    // addRemoteEntity(remote) vive en WorldMapMultiplayer.kt (de-dup 2026-06-21, par 4: miembro
    // canónico movido a la extensión homónima, sincronizada con la replicación de zombi/vida).
    // Cascada verificada: su único call externo, isCarTombstoned(), es un miembro internal único.

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

    // ─── CAMPAÑA / MODO HISTORIA → WorldMapCampaign.kt ──────────────────────
    // setStorySpawn(lat,lon) (punto de entrada del spawn de campaña) vive en
    // WorldMapCampaign.kt. El ESTADO de campaña (inCampaign, campaign*/mission2*)
    // sigue aquí abajo. Lógica de misiones: WorldMapCampaignPolice/Prankedy/SaveGame.

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
    // MainActivity (puente con ZombieInteriorViewModel) para que el GUARDADO los persista y CARGAR los
    // restaure al reabrir el interior.
    internal var currentInteriorInventory: List<String> = emptyList()
    internal var currentInteriorLab1KeyFound: Boolean = false

    // REFACTOR: toggles de widgets + zoom/cámara movidos a WorldMapCameraUi.kt (extensiones del VM,
    // mismo paquete). Los campos de interpolación (autoZoomMode/targetZoomLevel) siguen aquí (internal).

    // ─── ZOOM AUTOMÁTICO POR ESTADO (a pie / conduciendo / conduciendo rápido) ───
    // A pie 22; al subir a un vehículo 21; a MUY alta velocidad (≥85% de MAX_SPEED)
    // baja a 20, y vuelve a 21 por debajo del 65% (histéresis anti-parpadeo). Solo
    // actúa en TRANSICIONES de modo, así el pinch manual del usuario se respeta
    // hasta el siguiente cambio de estado.
    internal var autoZoomMode = 0 // 0 = a pie, 1 = conduciendo, 2 = conduciendo rápido
    internal var targetZoomLevel = ZOOM_ON_FOOT // Zoom objetivo para interpolación suave (usado por WorldMapCameraUi.kt)

    // updateAutoZoom / zoomIn / zoomOut / onMapZoomChanged / centerOnPlayer / zoomToPlayer /
    // onMapPanStart / onMapPanEnd → movidos a WorldMapCameraUi.kt (extensiones del VM).

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
                navState = if (isInsideEscom(loc.latitude, loc.longitude)) ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED else ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM
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

    // spawnOustedDriver(carLocation) vive en WorldMapEscom.kt (de-dup 2026-06-21: el miembro
    // canónico se movió a la extensión homónima, que estaba muerta/divergida y ya fue sincronizada).


    // ─── TELETRANSPORTE (gate TP + Metro/Metrobús) → WorldMapTeleport.kt ─────
    // teleportTo / teleportToMetroStation / loadMetroStations /
    // teleportToMetrobusStation / loadMetrobusStations / toggleTeleportMenu
    // viven en WorldMapTeleport.kt. El ESTADO usado sigue en el ViewModel.

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

    // DE-DUP: `startHealthBarTimer` ahora vive SOLO como extensión en WorldMapMisc.kt
    // (cuerpos idénticos; el miembro privado sombreaba a la extensión). Ver 09 §12.

    // triggerWastedSequence() vive en WorldMapMisc.kt (de-dup 2026-06-21, par 3: el miembro canónico
    // se movió a la extensión homónima, que estaba muerta/divergida y ya fue sincronizada al miembro).
    // Cascada verificada: su único call externo, clearCampaignPolice(), tiene una sola definición.

    fun showInitialHealthBar() {
        showHealthBar = true
        startHealthBarTimer(4000L)
    }

    // ─── COMBATE → WorldMapCombat.kt ────────────────────────────────────────
    // performPlayerAttack / runOverNpcs / provokeApocalypsePolice /
    // applyNpcContactDamage / startRelentlessAttacker viven en WorldMapCombat.kt.
    // Aquí solo queda el ESTADO que usan (lastAttackTime, npcHitStreak, constantes, etc.).

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
                // Enrutamos la puerta a su interior por el NOMBRE del landmark, vía el
                // catálogo data-driven InteriorEntryCatalog (antes era un `when` hardcodeado
                // aquí). Usa `contains` (no match exacto) para tolerar variantes/acentos al
                // colocar la puerta en el Diseñador; si nada casa, cae a DEFAULT_ROUTE (lobby
                // ESCOM). Para añadir un edificio enterable, edita InteriorEntryCatalog. Ver 04/06.
                val targetRoute = ovh.gabrielhuav.pow.domain.models.map.InteriorEntryCatalog.routeForDoorName(nearby.name)
                // MODO HISTORIA · Misión 2 "Ingresa a la ESCOM": se cumple al ENTRAR por la puerta
                // (este es el momento de "ingresar"). Marca el objetivo cumplido + jingle.
                if (_uiState.value.currentObjective?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id
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
    // setNpcDensity / setNpcEmojiLod / setNpcFullEmoji → WorldMapSettings.kt (extensiones).
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
                val inputStream = context.assets.open("CONFIG/navgraphs/escom_navgraph.json")
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
        val newCar = ovh.gabrielhuav.pow.domain.models.map.Npc(
            id = newCarId,
            type = ovh.gabrielhuav.pow.domain.models.map.NpcType.CAR,
            location = spawnGeoPoint,
            carColor = android.graphics.Color.WHITE,
            carModel = ovh.gabrielhuav.pow.domain.models.map.CarModel.SPORT,
            rotationAngle = 0f,
            speed = ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager.CAR_SPEED,

            // 👇 PROPIEDADES QUE EVITAN QUE LA IA LO ELIMINE
            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
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

    // toggleSkinSelector / selectSkin / refreshSkin → WorldMapSettings.kt (extensiones).

    // ─── ShineCTO Easter Egg ────────────────────────────────────────────────

    // ─── EASTER EGG ShineCTO + PUERTA ESCOM → WorldMapShineCTO.kt ────────────
    // spawnShineCTOMarker / onShineCTODiscoveryConfirmed / consumeNavigateToShineCTO /
    // dismissShineCTODiscovery / onEscomDoorFadeComplete / consumeEscomDoorNavigation
    // viven en WorldMapShineCTO.kt.

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
