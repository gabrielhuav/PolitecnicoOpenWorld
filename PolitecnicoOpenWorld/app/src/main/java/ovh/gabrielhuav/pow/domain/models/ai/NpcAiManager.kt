package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.random.Random

class NpcAiManager {

    companion object {
        const val CAR_SPEED = 0.000008
        const val PERSON_SPEED = 0.0000015

        // NPCs de PRUEBA sembrados a lo largo de la ruta roja de campaña (debug + misión).
        // Llevan este prefijo de id para quedar EXENTOS del despawn por distancia y del cull
        // por maxTotalNpcs: deben sobrevivir repartidos por TODA la ruta (no solo cerca del jugador).
        const val ROUTE_NPC_PREFIX = "CAMPAIGN_ROUTE_"

        // ─── Parámetros de comportamiento (compartidos SP/MP: el host los corre) ───
        const val FEAR_RADIUS = 0.0018
        const val FEAR_DURATION_MS = 4500L
        const val FEAR_SPEED_MULT = 3.8f

        const val CHAT_DISTANCE = 0.00035
        const val CHAT_DURATION_MS = 10000L
        const val CHAT_CHANCE = 0.038f

        const val CAR_FOLLOW_DISTANCE = 0.00025 // ~27m: distancia de seguimiento realista
        const val LANE_OFFSET = 0.000024

        @Volatile var aggressiveRatio: Float = 0.3f
        const val AGGRO_DURATION_MS = 6000L
        const val AGGRO_SPEED_MULT = 5f
        const val AGGRO_STOP_DIST = 0.000018
        const val AGGRO_VISION_RADIUS = 0.0005

        fun rollTrait(): ovh.gabrielhuav.pow.domain.models.map.NpcTrait =
            if (Random.nextFloat() < aggressiveRatio)
                ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE
            else
                ovh.gabrielhuav.pow.domain.models.map.NpcTrait.COWARD

        fun rollZombieRole(): ovh.gabrielhuav.pow.domain.models.map.ZombieRole {
            val r = Random.nextFloat()
            return when {
                r < 0.22f -> ovh.gabrielhuav.pow.domain.models.map.ZombieRole.RUNNER
                r < 0.34f -> ovh.gabrielhuav.pow.domain.models.map.ZombieRole.TANK
                r < 0.42f -> ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT
                else      -> ovh.gabrielhuav.pow.domain.models.map.ZombieRole.NORMAL
            }
        }
        fun maxHealthForRole(role: ovh.gabrielhuav.pow.domain.models.map.ZombieRole): Float = when (role) {
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.RUNNER -> 15f
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.TANK   -> 60f
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT  -> 15f
            else -> 30f
        }
        fun speedMulForRole(role: ovh.gabrielhuav.pow.domain.models.map.ZombieRole): Float = when (role) {
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.RUNNER -> 1.6f
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.TANK   -> 0.55f
            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT  -> 1.5f
            else -> 1f
        }

        val NPC_OUTFITS: List<ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig> by lazy {
            fun outfit(hairId: Int, shirt: Long, hair: Long, pants: Long) =
                ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig(
                    bodyFolder = "npc_walk_1", bodyPrefix = "npc_walk_1_",
                    hairId = hairId,
                    shirtColor = androidx.compose.ui.graphics.Color(shirt),
                    hairColor = androidx.compose.ui.graphics.Color(hair),
                    pantsColor = androidx.compose.ui.graphics.Color(pants)
                )
            listOf(
                outfit(1, 0xFFD32F2F, 0xFF3B2A1A, 0xFF424242),
                outfit(2, 0xFF1976D2, 0xFF1A1A1A, 0xFF2E3B4E),
                outfit(3, 0xFF388E3C, 0xFF5A3A1A, 0xFF424242),
                outfit(1, 0xFFFBC02D, 0xFF2B1A0A, 0xFF3A3A3A),
                outfit(4, 0xFF00ACC1, 0xFF1A1A1A, 0xFF24303A),
                outfit(2, 0xFF8E24AA, 0xFF4A2A1A, 0xFF424242),
                outfit(3, 0xFFECEFF1, 0xFF2B1A0A, 0xFF2E2E2E),
                outfit(4, 0xFFFF8800, 0xFF1A1A1A, 0xFF333333)
            )
        }

        val CAR_COLORS = intArrayOf(
            android.graphics.Color.rgb(200, 30, 30),
            android.graphics.Color.rgb(30, 60, 160),
            android.graphics.Color.rgb(235, 235, 235),
            android.graphics.Color.rgb(25, 25, 25),
            android.graphics.Color.rgb(120, 120, 130),
            android.graphics.Color.rgb(30, 120, 60),
            android.graphics.Color.rgb(210, 180, 40)
        )
    }

    private val _npcs = MutableStateFlow<List<Npc>>(emptyList())
    val npcs: StateFlow<List<Npc>> = _npcs.asStateFlow()

    private val cachedRoadNetwork = AtomicReference<List<MapWay>>(emptyList())
    private val cachedLandmarks = AtomicReference<List<Landmark>>(emptyList())
    internal val cachedNavLandmarks = AtomicReference<List<Landmark>>(emptyList())
    @Volatile private var lastParkingDbgMs = 0L   // throttle del log de diagnóstico del estacionamiento

    fun setLandmarks(landmarks: List<Landmark>) {
        cachedLandmarks.set(landmarks)
        cachedNavLandmarks.set(landmarks.filter { it.navGraph != null })
    }

    private class WayBox(
        val way: MapWay,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )
    private val cachedWayBoxes = AtomicReference<List<WayBox>>(emptyList())
    // OPT: lista de vías ya filtradas (sin nodos vacíos) precomputada UNA vez al fijar la red, para
    // no re-materializar cachedWaysFiltered.get() en cada chequeo de spawn por tick.
    private val cachedWaysFiltered = AtomicReference<List<MapWay>>(emptyList())
    internal val nodeToWays = AtomicReference<Map<Long, List<MapWay>>>(emptyMap())

    val pendingDespawns = mutableListOf<String>()
    val pendingPoliceShots = mutableListOf<Pair<GeoPoint, GeoPoint>>()

    @Volatile var globalZombieMode: Boolean = false
    @Volatile var deviceTierFactor: Float = 1.0f
    @Volatile var urbanFactor: Float = 1.0f
    @Volatile var userPopulationFactor: Float = 1.0f
    private val popFactor get() = deviceTierFactor * urbanFactor * userPopulationFactor

    internal var exteriorCollisions: ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig? = null

    fun setExteriorCollisions(config: ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig?) {
        this.exteriorCollisions = config
    }

    // FIX "se generan muchos NPCs": topes base BAJOS (10 activos / 22 totales, antes 18/38)
    // para una densidad más realista al caminar; siguen escalando por popFactor (gama +
    // ciudad + ajuste del usuario), así que NO se hardcodea la densidad final.
    private val maxActiveNpcs get() = ((if (globalZombieMode) 45 else 10) * popFactor).toInt().coerceIn(4, 120)
    private val maxTotalNpcs  get() = ((if (globalZombieMode) 90 else 22) * popFactor).toInt().coerceIn(8, 240)

    // Cadencia de aparición más lenta (antes 500 ms): los NPCs entran de a pocos, no en masa.
    private val SPAWN_SCAN_MS = 900L
    @Volatile private var lastSpawnScanMs = 0L
    private val simRadius      = 0.0010
    private val despawnDistance = 0.0028
    private val spawnDistance   = 0.0012
    private val spawnRingMin = 0.0004
    private val spawnRingMax = 0.00068

    private val carPopulationRatio: Float
        get() = if (globalZombieMode) 0f else (0.35f * urbanFactor).coerceIn(0.2f, 0.6f)

    val ZOMBIE_SPEED_MULT = 3.5f
    val ZOMBIE_VISION = 0.0009
    val ZOMBIE_CONTACT_DIST = 0.00003
    val ZOMBIE_BITE_DAMAGE = 8f
    val ZOMBIE_BITE_COOLDOWN_MS = 1000L
    val HUMAN_CONVERT_DELAY_MS = 1500L
    val MAX_ZOMBIES = 35
    val INITIAL_ZOMBIE_SEED = 25

    val HORDE_INTERVAL_MS = 20000L
    val HORDE_SIZE = 10
    val HORDE_MIN_HUMANS = 4
    @Volatile var lastHordeMs = 0L
    @Volatile var hordeIncomingAt = 0L

    val POLICE_HUNTER_MAX = 4
    val POLICE_SPAWN_INTERVAL_MS = 9000L
    val POLICE_SPAWN_CHANCE = 0.6f
    val POLICE_SPEED_MULT = 3.2f
    val POLICE_SHOOT_DIST = 0.00025
    val POLICE_SHOOT_DAMAGE = 40f
    val POLICE_SHOOT_COOLDOWN_MS = 1200L
    @Volatile var lastPoliceSpawnMs = 0L

    internal val parkedTimers = java.util.concurrent.ConcurrentHashMap<String, Long>()
    internal val parkingCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    internal val carExitCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val populatedLandmarks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    // FIX ESCOM vacía: cooldown de re-población por landmark. Los NPCs del campus se
    // despawnean a despawnDistance (~310 m) pero el campus solo se "des-poblaba" a
    // >0.02° (~2.2 km), así que al volver quedaba SIN IA. Ahora, si el campus está
    // marcado como poblado pero ya no tiene NPCs vivos, se repuebla (con cooldown).
    private val landmarkRepopulateAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val LANDMARK_REPOPULATE_COOLDOWN_MS = 8_000L
    // ── ESTACIONAMIENTO como ESCENOGRAFÍA ──────────────────────────────────
    // Los carros estacionados son escenario fijo del campus, NO NPCs efímeros. Antes:
    //  (1) despertaban y se iban en 15-60 s (PARKED→MICRO_LANDMARK), y
    //  (2) `needsRepopulate` comprobaba CUALQUIER NPC del landmark (los peatones SIEMPRE existen),
    //      así que tras irse los carros el lote quedaba VACÍO para siempre.
    // Ahora: se mantiene un OBJETIVO de carros PARKED, se rellena cuando bajan del mínimo y
    // tardan MUCHO más en despertar (escenografía estable, con bajas ocasionales que se reponen).
    // El lote se mantiene LLENO: objetivo = 100% de su capacidad (slots reales del navGraph); si
    // baja del 90% se rellena. Así el estacionamiento SIEMPRE se ve >85% ocupado (hasta 100%).
    private val PARKING_FILL_RATIO = 1.00f    // llenar al 100% de los slots
    private val PARKING_REFILL_RATIO = 0.90f  // si baja del 90%, rellenar (nunca baja del 85%)
    private val PARKING_MIN_CARS = 4          // piso absoluto (lotes con pocos slots)
    internal val PARKING_WAKE_MIN_MS = 90_000L
    internal val PARKING_WAKE_MAX_MS = 240_000L
    internal val landmarkEntranceCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    internal val carSpeed    = CAR_SPEED
    internal val personSpeed = PERSON_SPEED

    // ─── ESQUIVE DE TRÁFICO alrededor del jugador (en el MARCO del NPC) ───────
    // Sustituye al viejo "empujón" posicional desde el loop del jugador (causaba
    // órbitas/oscilaciones). El coche NPC desplaza su OBJETIVO perpendicularmente
    // mientras el jugador esté en su trayectoria; al rebasarlo el offset se apaga
    // y el smoothing normal lo reincorpora a su carril. Sin tocar su posición.
    internal val TRAFFIC_AVOID_RADIUS = 0.00008     // ~9 m: distancia al jugador para empezar a abrirse
    internal val TRAFFIC_AVOID_PATH_HALF = 0.00004  // ~4.4 m: medio ancho de "su trayectoria"
    internal val TRAFFIC_AVOID_BEHIND = 0.00004     // ~4.4 m: mantener el offset hasta rebasar por completo
    internal val TRAFFIC_AVOID_OFFSET = 0.000015    // ~1.6 m: apertura máxima (medio carril, no se sale de la red)
    internal val TRAFFIC_AVOID_LOOKAHEAD = 0.00008  // ~9 m: el objetivo de esquive es LOCAL (ver moveNpc)

    @Volatile private var networkIsReady = false
    @Volatile internal var aggroPlayerLat = 0.0
    @Volatile internal var aggroPlayerLon = 0.0

    fun updateRoadNetwork(network: List<MapWay>) {
        cachedRoadNetwork.set(network)
        val boxes = network.mapNotNull { way ->
            if (way.nodes.isEmpty()) return@mapNotNull null
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            for (n in way.nodes) {
                if (n.lat < minLat) minLat = n.lat
                if (n.lat > maxLat) maxLat = n.lat
                if (n.lon < minLon) minLon = n.lon
                if (n.lon > maxLon) maxLon = n.lon
            }
            WayBox(way, minLat, maxLat, minLon, maxLon)
        }
        cachedWayBoxes.set(boxes)
        cachedWaysFiltered.set(boxes.map { it.way })
        val index = HashMap<Long, MutableList<MapWay>>()
        for (way in network) {
            for (n in way.nodes) {
                index.getOrPut(n.id) { mutableListOf() }.add(way)
            }
        }
        nodeToWays.set(index)
        networkIsReady = network.isNotEmpty()
        urbanFactor = if (network.isEmpty()) 1.0f else (network.size / 140f).coerceIn(0.6f, 1.8f)
    }

    fun setRemoteNpcs(remoteList: List<Npc>) {
        val currentLocals = _npcs.value.filter { !it.isRemote }
        _npcs.value = currentLocals + remoteList
    }

    internal var serverNpcs = CopyOnWriteArrayList<Npc>()

    fun setServerNpcs(npcs: List<Npc>) {
        serverNpcs.clear()
        serverNpcs.addAll(npcs)
    }

    /**
     * Inyecta NPCs adicionales en la lista del servidor de IA SIN borrar los existentes
     * (a diferencia de [setServerNpcs]). El ciclo [updateNpcs]/[moveNpc] los avanzará por su
     * `currentWay`. Dedup por id (no duplica si ya están). Usado para sembrar los NPCs de la
     * ruta roja de campaña sin esperar al siguiente re-sync desde remoteEntities.
     */
    fun addServerNpcs(npcs: List<Npc>) {
        if (npcs.isEmpty()) return
        val existing = serverNpcs.mapTo(HashSet()) { it.id }
        serverNpcs.addAll(npcs.filter { it.id !in existing })
    }

    fun getServerNpcs(): List<Npc> = serverNpcs

    internal fun isNativeWayOverlappingCustom(way: MapWay, activeLandmarks: List<Landmark>): Boolean {
        if (activeLandmarks.isEmpty()) return false
        for (landmark in activeLandmarks) {
            var nodesInside = 0
            for (node in way.nodes) {
                val gp = GeoPoint(node.lat, node.lon)
                if (landmark.contains(gp)) {
                    nodesInside++
                }
            }
            if (nodesInside > 0 && (nodesInside.toFloat() / way.nodes.size) > 0.45f) {
                return true
            }
        }
        return false
    }

    private class FearEvent(val lat: Double, val lon: Double, val until: Long)
    private val pendingFear = CopyOnWriteArrayList<FearEvent>()

    fun triggerFear(lat: Double, lon: Double) {
        pendingFear.add(FearEvent(lat, lon, System.currentTimeMillis() + FEAR_DURATION_MS))
    }

    private fun applyPendingFear() {
        if (pendingFear.isEmpty()) return
        val events = ArrayList(pendingFear)
        pendingFear.clear()
        for (i in serverNpcs.indices) {
            val npc = serverNpcs[i]
            if (!npc.displayName.isNullOrEmpty()) continue
            if (npc.trait == ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE) continue
            var best: FearEvent? = null
            for (ev in events) {
                if (calculateDistance(npc.location.latitude, npc.location.longitude, ev.lat, ev.lon) <= FEAR_RADIUS) {
                    if (best == null || ev.until > best.until) best = ev
                }
            }
            best?.let { ev ->
                serverNpcs[i] = npc.copy(
                    fearUntil = ev.until,
                    fearFromLat = ev.lat,
                    fearFromLon = ev.lon,
                    chatUntil = 0L,
                    chatPartnerId = null,
                    isMoving = true
                )
            }
        }
    }

    suspend fun updateNpcs(playerLocation: GeoPoint, amIHost: Boolean) {
        if (!networkIsReady || !amIHost) return

        aggroPlayerLat = playerLocation.latitude
        aggroPlayerLon = playerLocation.longitude

        val currentNetwork = cachedRoadNetwork.get()
        if (currentNetwork.isEmpty()) return

        withContext(Dispatchers.Default) {
            val toRemove = serverNpcs.filter {
                it.displayName.isNullOrEmpty() &&
                        // Los carros ESTACIONADOS son escenario fijo del campus: NO se despawnean por
                        // distancia (si no, al poblarlos de LEJOS se borraban al instante → "no aparecen").
                        // Se limpian cuando el jugador SALE del campus (dist>=0.02, más abajo).
                        it.navState != ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED &&
                        // Los NPCs de la RUTA roja de campaña están repartidos por TODA la ruta:
                        // exentos del despawn por distancia (se limpian aparte al acabar la misión).
                        !it.id.startsWith(ROUTE_NPC_PREFIX) &&
                        calculateDistance(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude) > despawnDistance
            }
            serverNpcs.removeAll(toRemove)

            val pLat0 = playerLocation.latitude
            val pLon0 = playerLocation.longitude
            var activeCount = 0
            var totalCount = 0
            for (n in serverNpcs) {
                // Los NPCs de la ruta de campaña NO cuentan para el cupo (son escenografía exenta).
                // Los AUTOS ESTACIONADOS (PARKED) tampoco: son escenografía del campus. Si contaran,
                // 81 cajones (> maxTotalNpcs) saturaban el cupo y BLOQUEABAN peatones y tráfico
                // ("no aparecen NPCs"). Por eso se excluyen del cupo aquí y en los gates de spawn.
                if (n.displayName.isNullOrEmpty() && !n.id.startsWith(ROUTE_NPC_PREFIX) &&
                    n.navState != ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED) {
                    totalCount++
                    if (calculateDistance(n.location.latitude, n.location.longitude, pLat0, pLon0) <= simRadius) activeCount++
                }
            }
            // Cupo de NPCs vivos NO estacionados (los PARKED son escenografía exenta, como la ruta).
            fun nonParkedAlive() = serverNpcs.count {
                it.displayName.isNullOrEmpty() && !it.id.startsWith(ROUTE_NPC_PREFIX) &&
                    it.navState != ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED
            }

            if (totalCount > maxTotalNpcs) {
                val excess = totalCount - maxTotalNpcs
                val farthest = serverNpcs.filter { it.displayName.isNullOrEmpty() && it.navState != ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED && !it.id.startsWith(ROUTE_NPC_PREFIX) }
                    .sortedByDescending { calculateDistance(it.location.latitude, it.location.longitude, pLat0, pLon0) }
                    .take(excess)
                serverNpcs.removeAll(farthest)
                farthest.forEach { synchronized(pendingDespawns) { pendingDespawns.add(it.id) } }
            }

            val activeLandmarks = cachedNavLandmarks.get()

            // DIAGNÓSTICO (POW_DBG, cada ~2 s): cuántos landmarks con navGraph ve la IA y a qué distancia
            // está el más cercano. Si activeLandmarks=0 → el navGraph NO llega a la IA (problema de carga).
            val nowDbg = System.currentTimeMillis()
            if (nowDbg - lastParkingDbgMs > 2000L) {
                lastParkingDbgMs = nowDbg
                val nearest = activeLandmarks.minByOrNull { calculateDistance(pLat0, pLon0, it.location.latitude, it.location.longitude) }
                val nd = nearest?.let { calculateDistance(pLat0, pLon0, it.location.latitude, it.location.longitude) }
                val poblado = nearest?.let { populatedLandmarks.contains(it.id.toString()) }
                android.util.Log.d("POW_DBG", "parking: navLandmarks=${activeLandmarks.size} nearestId=${nearest?.id} nearestDist=$nd yaPoblado=$poblado (umbral<0.01) maxTotalNpcs=$maxTotalNpcs npcsVivos=${serverNpcs.size} globalZombie=$globalZombieMode")
            }

            for (landmark in activeLandmarks) {
                val dist = calculateDistance(pLat0, pLon0, landmark.location.latitude, landmark.location.longitude)
                val lmKey = landmark.id.toString()
                // Cuántos CARROS ESTACIONADOS siguen vivos aquí. Solo cuenta PARKED: si contáramos
                // cualquier NPC, los peatones del campus (siempre presentes) bloquearían el relleno
                // y el lote quedaría vacío para siempre tras irse los carros.
                val parkedAlive = serverNpcs.count {
                    it.navState == ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED && it.currentLandmark?.id == landmark.id
                }
                // Capacidad real del lote = nº de slots de estacionamiento del navGraph. Objetivo 90%,
                // se rellena si baja del 80% (siempre lleno).
                val totalSlots = landmark.navGraph?.ways?.sumOf { w -> w.nodes.count { it.isParkingSlot } } ?: 0
                val targetCars = (totalSlots * PARKING_FILL_RATIO).toInt().coerceAtLeast(PARKING_MIN_CARS)
                val minCars = (totalSlots * PARKING_REFILL_RATIO).toInt().coerceAtLeast(PARKING_MIN_CARS)
                // Repuebla si el campus ya estaba poblado pero los carros bajaron del 80% (con cooldown).
                // Si el lote ya estaba POBLADO pero quedo VACIO (0 carros) — caso tipico al
                // VOLVER del interior de la ESCOM, donde se limpian las entidades sin pasar por
                // dist>=0.02 — repoblamos YA, sin esperar el cooldown (los carros deben seguir ahi).
                val lotEmptyButPopulated = parkedAlive == 0 && populatedLandmarks.contains(lmKey)
                val needsRepopulate = dist < 0.01 && populatedLandmarks.contains(lmKey) &&
                    parkedAlive < minCars &&
                    (lotEmptyButPopulated || System.currentTimeMillis() >= (landmarkRepopulateAt[lmKey] ?: 0L))
                if (dist < 0.01 && (!populatedLandmarks.contains(lmKey) || needsRepopulate)) {
                  try {
                    val firstPopulate = !populatedLandmarks.contains(lmKey)   // true solo la 1ª vez (no en rellenos)
                    android.util.Log.d("POW_DBG", "parking ENTRA al bloque lm=$lmKey dist=$dist parkedAlive=$parkedAlive primera=$firstPopulate (slots a buscar...)")
                    populatedLandmarks.add(lmKey)
                    landmarkRepopulateAt[lmKey] = System.currentTimeMillis() + LANDMARK_REPOPULATE_COOLDOWN_MS

                    val availableSlots = getAvailableParkingSlots(landmark, serverNpcs)
                    // Rellena SOLO hasta el objetivo (no por porcentaje). Los carros estacionados NO se
                    // topan por maxTotalNpcs: son escenografía exenta de culls y acotada por el objetivo.
                    val numToSpawn = (targetCars - parkedAlive).coerceIn(0, availableSlots.size)
                    var dbgSpawned = 0
                    if (numToSpawn > 0) {
                        var timerOffset = Random.nextLong(PARKING_WAKE_MIN_MS, PARKING_WAKE_MAX_MS)
                        availableSlots.shuffled().take(numToSpawn).forEach { slot ->
                            serverNpcs.add(spawnParkedCar(landmark, slot.first, slot.second, timerOffset))
                            dbgSpawned++
                            timerOffset += Random.nextLong(20000L, 40000L)
                        }
                    }
                    android.util.Log.d("POW_DBG", "parking POBLANDO lm=${landmark.id} dist=$dist totalSlots=$totalSlots objetivo=$targetCars slotsLibres=${availableSlots.size} spawneados=$dbgSpawned (npcs=${serverNpcs.size}/$maxTotalNpcs)")

                    val navGraph = landmark.navGraph
                    if (firstPopulate && navGraph != null && !globalZombieMode) {
                        val pedestrianWays = navGraph.ways.filter { it.id >= 200 }
                        if (pedestrianWays.isNotEmpty()) {
                            val numStudents = Random.nextInt(15, 30)
                            for (i in 0 until numStudents) {
                                if (nonParkedAlive() < maxTotalNpcs) {
                                    val pWay = pedestrianWays.random()
                                    val pNode = pWay.nodes.random()
                                    serverNpcs.add(spawnCampusPedestrian(landmark, pWay, pNode))
                                }
                            }
                        }
                    }
                  } catch (e: Exception) {
                    android.util.Log.e("POW_DBG", "parking EXCEPCIÓN al poblar lm=$lmKey", e)
                  }
                } else if (dist >= 0.02) {
                    populatedLandmarks.remove(landmark.id.toString())
                    // Al SALIR del campus sí limpia los carros estacionados de este landmark (evita
                    // acumularlos). Se vuelven a poblar al regresar.
                    val parkedHere = serverNpcs.filter {
                        it.navState == ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED && it.currentLandmark?.id == landmark.id
                    }
                    if (parkedHere.isNotEmpty()) {
                        serverNpcs.removeAll(parkedHere)
                        parkedHere.forEach { synchronized(pendingDespawns) { pendingDespawns.add(it.id) } }
                    }
                }
            }

            val nowSpawn = System.currentTimeMillis()
            if (activeCount < maxActiveNpcs && nowSpawn - lastSpawnScanMs >= SPAWN_SCAN_MS) {
                lastSpawnScanMs = nowSpawn
                val pLat = pLat0
                val pLon = pLon0
                val closeWays = cachedWayBoxes.get()
                    .filter { box ->
                        pLat >= box.minLat - spawnDistance && pLat <= box.maxLat + spawnDistance &&
                                pLon >= box.minLon - spawnDistance && pLon <= box.maxLon + spawnDistance
                    }
                    .filter { box ->
                        box.way.nodes.any { calculateDistance(it.lat, it.lon, pLat, pLon) < spawnDistance }
                    }
                    .map { it.way }

                if (closeWays.isNotEmpty() || activeLandmarks.isNotEmpty()) {
                    // FIX "ya no hay humanos": sin cuota por tipo, los coches (que viven más
                    // cerca del jugador: estacionados de campus + tráfico) saturaban
                    // maxActiveNpcs y el spawner nunca volvía a meter PEATONES. Si los coches
                    // ya llenaron su cuota (carPopulationRatio del cupo activo), los spawns
                    // de calle se FUERZAN a persona.
                    var activeCarCount = 0
                    for (n in serverNpcs) {
                        // Excluye estacionados: son escenografía, no "tráfico" para la cuota de coches.
                        if (n.displayName.isNullOrEmpty() && n.type == NpcType.CAR &&
                            n.navState != ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED &&
                            calculateDistance(n.location.latitude, n.location.longitude, pLat, pLon) <= simRadius
                        ) activeCarCount++
                    }
                    val carQuotaFull = activeCarCount >=
                        (maxActiveNpcs * carPopulationRatio).toInt().coerceAtLeast(2)
                    // Máx 2 por escaneo (antes 4): aparición gradual, no en bloque.
                    val numToSpawn = minOf(2, maxActiveNpcs - activeCount)
                    for (i in 0 until numToSpawn) {
                        var spawnedStudent = false

                        if (activeLandmarks.isNotEmpty() && !globalZombieMode && Random.nextFloat() < 0.50f) {
                            val targetLandmark = activeLandmarks.random()
                            val pedestrianWays = targetLandmark.navGraph?.ways?.filter { it.id >= 200 } ?: emptyList()
                            if (pedestrianWays.isNotEmpty()) {
                                val pWay = pedestrianWays.random()
                                val pNode = pWay.nodes.random()
                                val groupSize = Random.nextInt(1, 4)
                                for (g in 0 until groupSize) {
                                    if (nonParkedAlive() < maxTotalNpcs) {
                                        serverNpcs.add(spawnCampusPedestrian(targetLandmark, pWay, pNode))
                                    }
                                }
                                spawnedStudent = true
                            }
                        }

                        if (!spawnedStudent && closeWays.isNotEmpty()) {
                            var targetWays = closeWays
                            if (activeLandmarks.isNotEmpty() && Random.nextFloat() < 0.25f) {
                                val targetLandmark = activeLandmarks.random()
                                val waysNearLandmark = closeWays.filter { way ->
                                    way.nodes.any { node ->
                                        calculateDistance(node.lat, node.lon, targetLandmark.location.latitude, targetLandmark.location.longitude) < 0.002
                                    }
                                }
                                if (waysNearLandmark.isNotEmpty()) {
                                    targetWays = waysNearLandmark
                                }
                            }
                            spawnNpcOnRoad(playerLocation, targetWays, activeLandmarks, forcePerson = carQuotaFull)?.let { serverNpcs.add(it) }
                        }
                    }
                }
            }

            val now = System.currentTimeMillis()

            applyPendingFear()

            maybeStartChats(now, pLat0, pLon0)

            val presentIds = serverNpcs.mapTo(HashSet()) { it.id }
            for (i in serverNpcs.indices) {
                val npc = serverNpcs[i]
                if (npc.chatUntil > now) {
                    val partner = npc.chatPartnerId
                    if (partner == null || !presentIds.contains(partner)) {
                        serverNpcs[i] = npc.copy(chatUntil = 0L, chatPartnerId = null)
                    }
                }
            }

            val cars = serverNpcs.filter {
                it.displayName.isNullOrEmpty() && it.type == NpcType.CAR &&
                        calculateDistance(it.location.latitude, it.location.longitude, pLat0, pLon0) <= simRadius
            }

            if (globalZombieMode) {
                val zombies = serverNpcs.filter { it.type == NpcType.ZOMBIE && it.health > 0 }

                if (zombies.size < INITIAL_ZOMBIE_SEED && nonParkedAlive() < maxTotalNpcs) {
                    val closeWays = cachedWaysFiltered.get()
                    if (closeWays.isNotEmpty()) {
                        spawnNpcOnRoad(playerLocation, closeWays, activeLandmarks)?.let {
                            val role = rollZombieRole()
                            val hp = maxHealthForRole(role)
                            serverNpcs.add(it.copy(type = NpcType.ZOMBIE, speed = personSpeed * ZOMBIE_SPEED_MULT * speedMulForRole(role), trait = ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE, visualConfig = null, zombieRole = role, health = hp, maxHealth = hp))
                        }
                    }
                }

                if (now - lastHordeMs >= HORDE_INTERVAL_MS) {
                    val nearbyHumans = serverNpcs.count {
                        it.type == NpcType.PERSON && it.health > 0f && it.displayName.isNullOrEmpty() &&
                                calculateDistance(it.location.latitude, it.location.longitude,
                                    playerLocation.latitude, playerLocation.longitude) <= simRadius
                    }
                    if (nearbyHumans >= HORDE_MIN_HUMANS) {
                        lastHordeMs = now
                        hordeIncomingAt = now
                        val hordeWays = cachedWaysFiltered.get()
                        if (hordeWays.isNotEmpty()) {
                            var k = 0
                            while (k < HORDE_SIZE && nonParkedAlive() < maxTotalNpcs) {
                                spawnNpcOnRoad(playerLocation, hordeWays, activeLandmarks)?.let {
                                    val role = rollZombieRole()
                                    val hp = maxHealthForRole(role)
                                    serverNpcs.add(it.copy(type = NpcType.ZOMBIE, speed = personSpeed * ZOMBIE_SPEED_MULT * speedMulForRole(role), trait = ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE, visualConfig = null, zombieRole = role, health = hp, maxHealth = hp))
                                }
                                k++
                            }
                        }
                    }
                }

                if (now - lastPoliceSpawnMs >= POLICE_SPAWN_INTERVAL_MS) {
                    lastPoliceSpawnMs = now
                    val cops = serverNpcs.count { it.type == NpcType.POLICE_COP && it.health > 0f }
                    if (cops < POLICE_HUNTER_MAX && Random.nextFloat() < POLICE_SPAWN_CHANCE && nonParkedAlive() < maxTotalNpcs) {
                        val pWays = cachedWaysFiltered.get()
                        if (pWays.isNotEmpty()) {
                            spawnNpcOnRoad(playerLocation, pWays, activeLandmarks)?.let {
                                serverNpcs.add(it.copy(
                                    type = NpcType.POLICE_COP,
                                    speed = personSpeed * POLICE_SPEED_MULT,
                                    visualConfig = null,
                                    health = 100f, maxHealth = 100f, aggroUntil = 0L
                                ))
                            }
                        }
                    }
                }

                for (i in serverNpcs.indices) {
                    val h = serverNpcs[i]
                    if (h.type == NpcType.PERSON && h.health > 0 && h.displayName.isNullOrEmpty()) {
                        val nearestZ = zombies.minByOrNull { calculateDistance(it.location.latitude, it.location.longitude, h.location.latitude, h.location.longitude) }
                        if (nearestZ != null) {
                            val distZ = calculateDistance(nearestZ.location.latitude, nearestZ.location.longitude, h.location.latitude, h.location.longitude)
                            if (distZ <= FEAR_RADIUS) {
                                serverNpcs[i] = h.copy(
                                    fearUntil = now + FEAR_DURATION_MS,
                                    fearFromLat = nearestZ.location.latitude,
                                    fearFromLon = nearestZ.location.longitude,
                                    chatUntil = 0L,
                                    chatPartnerId = null,
                                    isMoving = true
                                )
                            }
                        }
                    }
                }

                for (i in serverNpcs.indices) {
                    val h = serverNpcs[i]
                    if (h.type == NpcType.PERSON && h.health <= 0f && h.isDying) {
                        if (now > h.fearUntil) {
                            if (zombies.size < MAX_ZOMBIES) {
                                val role = rollZombieRole()
                                val hp = maxHealthForRole(role)
                                serverNpcs[i] = h.copy(type = NpcType.ZOMBIE, health = hp, maxHealth = hp, isDying = false, chatUntil = 0L, fearUntil = 0L, trait = ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE, visualConfig = null, zombieRole = role)
                            } else {
                                synchronized(pendingDespawns) { pendingDespawns.add(h.id) }
                            }
                        }
                    }
                }
            }

            val updated = serverNpcs.mapNotNull { npc ->
                if (!npc.displayName.isNullOrEmpty()) {
                    npc
                } else if (calculateDistance(npc.location.latitude, npc.location.longitude, pLat0, pLon0) > simRadius) {
                    // Fuera del radio de simulación no se mueven (LOD). Los NPCs de ruta deben quedar
                    // QUIETOS (no reproducir la animación de caminado en el sitio) hasta acercarse.
                    if (npc.id.startsWith(ROUTE_NPC_PREFIX) && npc.isMoving) npc.copy(isMoving = false) else npc
                } else {
                    if (npc.dodgeUntil > now) {
                        val ds = personSpeed * 10.0
                        npc.copy(
                            location = GeoPoint(npc.location.latitude + npc.dodgeDirLat * ds, npc.location.longitude + npc.dodgeDirLon * ds),
                            isMoving = true,
                            facingRight = npc.dodgeDirLon >= 0
                        )
                    } else if (globalZombieMode && npc.type == NpcType.ZOMBIE) {
                        val moved = moveZombieNpc(npc, currentNetwork, now, pLat0, pLon0)
                        if (moved == null) {
                            synchronized(pendingDespawns) { pendingDespawns.add(npc.id) }
                        }
                        moved
                    } else if (globalZombieMode && npc.type == NpcType.POLICE_COP) {
                        movePoliceHunter(npc, currentNetwork, now, pLat0, pLon0)
                    } else {
                        val speedScale = if (npc.type == NpcType.CAR) carFollowScale(npc, cars) else 1f
                        val moved = moveNpc(npc, currentNetwork, now, speedScale)
                        if (moved == null && !npc.id.startsWith(ROUTE_NPC_PREFIX)) {
                            synchronized(pendingDespawns) { pendingDespawns.add(npc.id) }
                            null
                        } else {
                            // Los NPCs de ruta NUNCA se despawnean por la IA (p. ej. nodo dentro de un
                            // landmark): si moveNpc no pudo moverlos, se quedan quietos en su sitio.
                            moved ?: npc.copy(isMoving = false)
                        }
                    }
                }
            }
            serverNpcs.clear()
            serverNpcs.addAll(updated)
        }
    }


    // REFACTOR: moveZombieNpc/movePoliceHunter/moveAggroNpc/carFollowScale/pointToLineDist/
    // calculateDistance se movieron a NpcAiManagerMovement.kt (extensiones, mismo paquete).
    // Tocan solo miembros internal/public (serverNpcs/personSpeed/aggroPlayerLat-Lon/moveNpc).

    private fun getAvailableParkingSlots(landmark: Landmark, currentNpcs: List<Npc>): List<Pair<ovh.gabrielhuav.pow.domain.models.ai.LocalWay, ovh.gabrielhuav.pow.domain.models.ai.LocalNode>> {
        val navGraph = landmark.navGraph ?: return emptyList()
        val allSlots = mutableListOf<Pair<ovh.gabrielhuav.pow.domain.models.ai.LocalWay, ovh.gabrielhuav.pow.domain.models.ai.LocalNode>>()

        for (way in navGraph.ways) {
            for (node in way.nodes) {
                if (node.isParkingSlot) allSlots.add(Pair(way, node))
            }
        }

        return allSlots.filter { slot ->
            val wayId = slot.first.id
            val isOccupied = currentNpcs.any { npc ->
                (npc.navState == ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED ||
                        npc.navState == ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK) &&
                        npc.currentLocalWay?.id == wayId
            }
            !isOccupied
        }
    }

    private fun spawnParkedCar(landmark: Landmark, way: ovh.gabrielhuav.pow.domain.models.ai.LocalWay, node: ovh.gabrielhuav.pow.domain.models.ai.LocalNode, delayMs: Long): Npc {
        val globalPos = landmark.toGlobalGeoPoint(node.localX, node.localY)
        val newCarId = "PARKED_CAR_${System.currentTimeMillis()}_${Random.nextInt(1000)}"

        val nodeIndex = way.nodes.indexOf(node)
        val prevNode = if (nodeIndex > 0) way.nodes[nodeIndex - 1] else node

        val globalPrev = landmark.toGlobalGeoPoint(prevNode.localX, prevNode.localY)
        val dLon = globalPos.longitude - globalPrev.longitude
        val dLat = globalPos.latitude - globalPrev.latitude
        val angle = -Math.toDegrees(atan2(dLat, dLon)).toFloat()

        parkedTimers[newCarId] = System.currentTimeMillis() + delayMs

        return Npc(
            id = newCarId,
            type = NpcType.CAR,
            location = globalPos,
            rotationAngle = angle,
            speed = 0.0,
            carModel = CarModel.entries.random(),
            carColor = android.graphics.Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED,
            currentLandmark = landmark,
            currentLocalWay = way,
            targetNodeIndex = nodeIndex,
            moveDirection = 1,
            isRemote = false
        )
    }

    private fun spawnCampusPedestrian(landmark: Landmark, way: ovh.gabrielhuav.pow.domain.models.ai.LocalWay, node: ovh.gabrielhuav.pow.domain.models.ai.LocalNode): Npc {
        val globalPos = landmark.toGlobalGeoPoint(node.localX, node.localY)
        val newId = "CAMPUS_PED_${System.currentTimeMillis()}_${Random.nextInt(1000)}"

        val nodeIndex = way.nodes.indexOf(node)
        val visualConfig = NPC_OUTFITS.random()

        val randomSpeed = PERSON_SPEED * (0.7f + Random.nextFloat() * 0.5f)

        return Npc(
            id = newId,
            type = NpcType.PERSON,
            location = globalPos,
            rotationAngle = Random.nextFloat() * 360f,
            speed = randomSpeed,
            visualConfig = visualConfig,
            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
            currentLandmark = landmark,
            currentLocalWay = way,
            targetNodeIndex = nodeIndex,
            moveDirection = if (Random.nextBoolean()) 1 else -1,
            isMoving = true,
            isRemote = false,
            trait = rollTrait()
        )
    }

    private fun maybeStartChats(now: Long, pLat: Double, pLon: Double) {
        val free = serverNpcs.withIndex().filter { (_, n) ->
            n.displayName.isNullOrEmpty() && n.type == NpcType.PERSON &&
                    calculateDistance(n.location.latitude, n.location.longitude, pLat, pLon) <= simRadius &&
                    n.fearUntil <= now && n.chatUntil <= now
        }
        if (free.size < 2) return
        val used = HashSet<Int>()
        for (a in free.indices) {
            val (ia, na) = free[a]
            if (used.contains(ia)) continue
            for (b in a + 1 until free.size) {
                val (ib, nb) = free[b]
                if (used.contains(ib)) continue
                val d = calculateDistance(na.location.latitude, na.location.longitude,
                    nb.location.latitude, nb.location.longitude)
                if (d in 0.00002..CHAT_DISTANCE.toDouble() && Random.nextFloat() < CHAT_CHANCE) {
                    val angA = atan2(nb.location.latitude - na.location.latitude,
                        nb.location.longitude - na.location.longitude)
                    val angB = angA + Math.PI
                    val until = now + CHAT_DURATION_MS
                    serverNpcs[serverNpcs.indexOfFirst { it.id == na.id }.takeIf { it >= 0 } ?: continue] =
                        na.copy(chatUntil = until, chatPartnerId = nb.id, isMoving = false,
                            rotationAngle = (-Math.toDegrees(angA).toFloat() + 360) % 360,
                            facingRight = cos(angA) >= 0)
                    serverNpcs[serverNpcs.indexOfFirst { it.id == nb.id }.takeIf { it >= 0 } ?: continue] =
                        nb.copy(chatUntil = until, chatPartnerId = na.id, isMoving = false,
                            rotationAngle = (-Math.toDegrees(angB).toFloat() + 360) % 360,
                            facingRight = cos(angB) >= 0)
                    used.add(ia); used.add(ib)
                    break
                }
            }
        }
    }

    private fun spawnNpcOnRoad(playerLocation: GeoPoint, closeWays: List<MapWay>, activeLandmarks: List<Landmark>, forcePerson: Boolean = false): Npc? {
        val npcType = if (!forcePerson && Random.nextFloat() < carPopulationRatio) NpcType.CAR else NpcType.PERSON
        val speed   = if (npcType == NpcType.CAR) carSpeed else personSpeed

        val validWays = closeWays.filter { way ->
            val matchType = (npcType == NpcType.CAR && way.isForCars) || (npcType == NpcType.PERSON && way.isForPeople)
            matchType && !isNativeWayOverlappingCustom(way, activeLandmarks)
        }

        if (validWays.isEmpty()) return null

        val pLat = playerLocation.latitude
        val pLon = playerLocation.longitude
        val candidates = ArrayList<Pair<MapWay, Int>>()
        for (w in validWays) {
            for (idx in w.nodes.indices) {
                val n = w.nodes[idx]
                val d = calculateDistance(n.lat, n.lon, pLat, pLon)
                if (d in spawnRingMin..spawnRingMax) candidates.add(w to idx)
            }
        }
        if (candidates.isEmpty()) return null

        val (selectedWay, startIndex) = candidates.random()
        val startNode = selectedWay.nodes[startIndex]

        val startGeo = GeoPoint(startNode.lat, startNode.lon)
        if (activeLandmarks.any { it.contains(startGeo) }) {
            return null
        }

        val dir = when (startIndex) {
            0 -> 1
            selectedWay.nodes.size - 1 -> -1
            else -> if (Random.nextBoolean()) 1 else -1
        }

        val visualConfig = if (npcType == NpcType.PERSON) NPC_OUTFITS.random() else null

        return Npc(
            type = npcType,
            location = startGeo,
            speed = speed,
            currentWay = selectedWay,
            targetNodeIndex = startIndex + dir,
            moveDirection = dir,
            carColor = CAR_COLORS.random(),
            carModel = CarModel.entries.random(),
            isRemote = false,
            visualConfig = visualConfig,
            trait = rollTrait(),
            speedVariation = if (npcType == NpcType.CAR) 0.8f + Random.nextFloat() * 0.4f else 1.0f
        )
    }

    // (moveLocalNpc movido a NpcAiManagerTraffic.kt — extensión.)

    // (moveNpc movido a NpcAiManagerTraffic.kt — extensión.)
    // (moveAggroNpc / calculateDistance movidos a NpcAiManagerMovement.kt — extensiones.)
}