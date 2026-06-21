package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
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

        fun rollTrait(): ovh.gabrielhuav.pow.domain.models.NpcTrait =
            if (Random.nextFloat() < aggressiveRatio)
                ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE
            else
                ovh.gabrielhuav.pow.domain.models.NpcTrait.COWARD

        fun rollZombieRole(): ovh.gabrielhuav.pow.domain.models.ZombieRole {
            val r = Random.nextFloat()
            return when {
                r < 0.22f -> ovh.gabrielhuav.pow.domain.models.ZombieRole.RUNNER
                r < 0.34f -> ovh.gabrielhuav.pow.domain.models.ZombieRole.TANK
                r < 0.42f -> ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT
                else      -> ovh.gabrielhuav.pow.domain.models.ZombieRole.NORMAL
            }
        }
        fun maxHealthForRole(role: ovh.gabrielhuav.pow.domain.models.ZombieRole): Float = when (role) {
            ovh.gabrielhuav.pow.domain.models.ZombieRole.RUNNER -> 15f
            ovh.gabrielhuav.pow.domain.models.ZombieRole.TANK   -> 60f
            ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT  -> 15f
            else -> 30f
        }
        fun speedMulForRole(role: ovh.gabrielhuav.pow.domain.models.ZombieRole): Float = when (role) {
            ovh.gabrielhuav.pow.domain.models.ZombieRole.RUNNER -> 1.6f
            ovh.gabrielhuav.pow.domain.models.ZombieRole.TANK   -> 0.55f
            ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT  -> 1.5f
            else -> 1f
        }

        val NPC_OUTFITS: List<ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig> by lazy {
            fun outfit(hairId: Int, shirt: Long, hair: Long, pants: Long) =
                ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
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
    private val cachedNavLandmarks = AtomicReference<List<Landmark>>(emptyList())
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
    private val nodeToWays = AtomicReference<Map<Long, List<MapWay>>>(emptyMap())

    val pendingDespawns = mutableListOf<String>()
    val pendingPoliceShots = mutableListOf<Pair<GeoPoint, GeoPoint>>()

    @Volatile var globalZombieMode: Boolean = false
    @Volatile var deviceTierFactor: Float = 1.0f
    @Volatile var urbanFactor: Float = 1.0f
    @Volatile var userPopulationFactor: Float = 1.0f
    private val popFactor get() = deviceTierFactor * urbanFactor * userPopulationFactor

    private var exteriorCollisions: ovh.gabrielhuav.pow.domain.models.ExteriorCollisionsConfig? = null

    fun setExteriorCollisions(config: ovh.gabrielhuav.pow.domain.models.ExteriorCollisionsConfig?) {
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

    private val parkedTimers = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val parkingCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val carExitCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
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
    private val PARKING_WAKE_MIN_MS = 90_000L
    private val PARKING_WAKE_MAX_MS = 240_000L
    private val landmarkEntranceCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val carSpeed    = CAR_SPEED
    private val personSpeed = PERSON_SPEED

    // ─── ESQUIVE DE TRÁFICO alrededor del jugador (en el MARCO del NPC) ───────
    // Sustituye al viejo "empujón" posicional desde el loop del jugador (causaba
    // órbitas/oscilaciones). El coche NPC desplaza su OBJETIVO perpendicularmente
    // mientras el jugador esté en su trayectoria; al rebasarlo el offset se apaga
    // y el smoothing normal lo reincorpora a su carril. Sin tocar su posición.
    private val TRAFFIC_AVOID_RADIUS = 0.00008     // ~9 m: distancia al jugador para empezar a abrirse
    private val TRAFFIC_AVOID_PATH_HALF = 0.00004  // ~4.4 m: medio ancho de "su trayectoria"
    private val TRAFFIC_AVOID_BEHIND = 0.00004     // ~4.4 m: mantener el offset hasta rebasar por completo
    private val TRAFFIC_AVOID_OFFSET = 0.000015    // ~1.6 m: apertura máxima (medio carril, no se sale de la red)
    private val TRAFFIC_AVOID_LOOKAHEAD = 0.00008  // ~9 m: el objetivo de esquive es LOCAL (ver moveNpc)

    @Volatile private var networkIsReady = false
    @Volatile private var aggroPlayerLat = 0.0
    @Volatile private var aggroPlayerLon = 0.0

    fun updateRoadNetwork(network: List<MapWay>) {
        cachedRoadNetwork.set(network)
        cachedWayBoxes.set(network.mapNotNull { way ->
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
        })
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

    private var serverNpcs = CopyOnWriteArrayList<Npc>()

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

    private fun isNativeWayOverlappingCustom(way: MapWay, activeLandmarks: List<Landmark>): Boolean {
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
            if (npc.trait == ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE) continue
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
                        it.navState != ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED &&
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
                    n.navState != ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED) {
                    totalCount++
                    if (calculateDistance(n.location.latitude, n.location.longitude, pLat0, pLon0) <= simRadius) activeCount++
                }
            }
            // Cupo de NPCs vivos NO estacionados (los PARKED son escenografía exenta, como la ruta).
            fun nonParkedAlive() = serverNpcs.count {
                it.displayName.isNullOrEmpty() && !it.id.startsWith(ROUTE_NPC_PREFIX) &&
                    it.navState != ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED
            }

            if (totalCount > maxTotalNpcs) {
                val excess = totalCount - maxTotalNpcs
                val farthest = serverNpcs.filter { it.displayName.isNullOrEmpty() && it.navState != ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED && !it.id.startsWith(ROUTE_NPC_PREFIX) }
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
                    it.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED && it.currentLandmark?.id == landmark.id
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
                        it.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED && it.currentLandmark?.id == landmark.id
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
                            n.navState != ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED &&
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
                    val closeWays = cachedWayBoxes.get().map { it.way }
                    if (closeWays.isNotEmpty()) {
                        spawnNpcOnRoad(playerLocation, closeWays, activeLandmarks)?.let {
                            val role = rollZombieRole()
                            val hp = maxHealthForRole(role)
                            serverNpcs.add(it.copy(type = NpcType.ZOMBIE, speed = personSpeed * ZOMBIE_SPEED_MULT * speedMulForRole(role), trait = ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE, visualConfig = null, zombieRole = role, health = hp, maxHealth = hp))
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
                        val hordeWays = cachedWayBoxes.get().map { it.way }
                        if (hordeWays.isNotEmpty()) {
                            var k = 0
                            while (k < HORDE_SIZE && nonParkedAlive() < maxTotalNpcs) {
                                spawnNpcOnRoad(playerLocation, hordeWays, activeLandmarks)?.let {
                                    val role = rollZombieRole()
                                    val hp = maxHealthForRole(role)
                                    serverNpcs.add(it.copy(type = NpcType.ZOMBIE, speed = personSpeed * ZOMBIE_SPEED_MULT * speedMulForRole(role), trait = ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE, visualConfig = null, zombieRole = role, health = hp, maxHealth = hp))
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
                        val pWays = cachedWayBoxes.get().map { it.way }
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
                                serverNpcs[i] = h.copy(type = NpcType.ZOMBIE, health = hp, maxHealth = hp, isDying = false, chatUntil = 0L, fearUntil = 0L, trait = ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE, visualConfig = null, zombieRole = role)
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


    private fun moveZombieNpc(
        npc: Npc,
        network: List<MapWay>,
        now: Long,
        playerLat: Double,
        playerLon: Double
    ): Npc? {
        if (npc.health <= 0f) {
            if (!npc.isDying) {
                return npc.copy(isDying = true, aggroUntil = now + HUMAN_CONVERT_DELAY_MS)
            }
            if (now > npc.aggroUntil) return null
            return npc
        }

        if (npc.zombieRole == ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT) {
            val nearestH = serverNpcs
                .filter { it.type == NpcType.PERSON && it.health > 0f && it.displayName.isNullOrEmpty() }
                .minByOrNull { calculateDistance(it.location.latitude, it.location.longitude, npc.location.latitude, npc.location.longitude) }
            if (nearestH == null) return moveNpc(npc, network, now, 0.5f)
            val dh = calculateDistance(nearestH.location.latitude, nearestH.location.longitude, npc.location.latitude, npc.location.longitude)
            val scoutScreamDist = 0.0002
            val scoutFleeMs = 4500L
            var newScream = npc.screamUntil
            if (now >= npc.screamUntil && dh <= scoutScreamDist) newScream = now + scoutFleeMs
            val fleeing = now < newScream
            val dLatT = if (fleeing) (npc.location.latitude - nearestH.location.latitude) else (nearestH.location.latitude - npc.location.latitude)
            val dLonT = if (fleeing) (npc.location.longitude - nearestH.location.longitude) else (nearestH.location.longitude - npc.location.longitude)
            val a = atan2(dLatT, dLonT)
            val sp = personSpeed * ZOMBIE_SPEED_MULT * speedMulForRole(ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT)
            return npc.copy(
                location = GeoPoint(npc.location.latitude + sin(a) * sp, npc.location.longitude + cos(a) * sp),
                rotationAngle = (-Math.toDegrees(a).toFloat()),
                isMoving = true,
                facingRight = cos(a) >= 0,
                screamUntil = newScream,
                navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM
            )
        }

        val distPlayer = calculateDistance(npc.location.latitude, npc.location.longitude, playerLat, playerLon)
        var targetLat = playerLat
        var targetLon = playerLon
        var bestScore = distPlayer * 0.45
        var realDist = distPlayer
        var targetIsHuman: Npc? = null
        val humans = serverNpcs.filter {
            (it.type == NpcType.PERSON || it.type == NpcType.POLICE_COP) && it.health > 0f && it.displayName.isNullOrEmpty()
        }

        for (h in humans) {
            val d = calculateDistance(npc.location.latitude, npc.location.longitude, h.location.latitude, h.location.longitude)
            if (d < bestScore) {
                bestScore = d
                realDist = d
                targetLat = h.location.latitude
                targetLon = h.location.longitude
                targetIsHuman = h
            }
        }

        if (realDist > ZOMBIE_VISION) {
            return moveNpc(npc, network, now, 0.5f)
        }

        if (targetIsHuman != null && realDist <= ZOMBIE_CONTACT_DIST) {
            if (now > npc.chatUntil) {
                val targetIndex = serverNpcs.indexOfFirst { it.id == targetIsHuman.id }
                if (targetIndex >= 0) {
                    var h = serverNpcs[targetIndex]
                    val newHealth = h.health - ZOMBIE_BITE_DAMAGE
                    if (newHealth <= 0f) {
                        h = h.copy(health = 0f, isDying = true, fearUntil = now + HUMAN_CONVERT_DELAY_MS)
                    } else {
                        h = h.copy(health = newHealth)
                    }
                    serverNpcs[targetIndex] = h
                }
                return npc.copy(chatUntil = now + ZOMBIE_BITE_COOLDOWN_MS)
            }
            return npc
        }

        val dLatForDir = targetLat - npc.location.latitude
        val dLonForDir = targetLon - npc.location.longitude
        val dir = kotlin.math.atan2(dLatForDir, dLonForDir)
        val effSpeed = personSpeed * ZOMBIE_SPEED_MULT * speedMulForRole(npc.zombieRole)
        val dLat = kotlin.math.sin(dir) * effSpeed
        val dLon = kotlin.math.cos(dir) * effSpeed

        val facingRight = kotlin.math.cos(dir) >= 0

        return npc.copy(
            location = GeoPoint(npc.location.latitude + dLat, npc.location.longitude + dLon),
            rotationAngle = -Math.toDegrees(dir).toFloat(),
            isMoving = true,
            facingRight = facingRight,
            navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM
        )
    }

    private fun movePoliceHunter(npc: Npc, network: List<MapWay>, now: Long, playerLat: Double, playerLon: Double): Npc? {
        if (npc.health <= 0f) return null
        val provoked = npc.aggroUntil > now
        var targetLat: Double
        var targetLon: Double
        if (provoked) {
            targetLat = playerLat; targetLon = playerLon
        } else {
            val z = serverNpcs
                .filter { it.type == NpcType.ZOMBIE && it.health > 0f && !it.isDying }
                .minByOrNull { calculateDistance(it.location.latitude, it.location.longitude, npc.location.latitude, npc.location.longitude) }
            if (z == null) return moveNpc(npc, network, now, 0.5f)
            val dz = calculateDistance(z.location.latitude, z.location.longitude, npc.location.latitude, npc.location.longitude)
            if (dz <= POLICE_SHOOT_DIST) {
                if (now > npc.chatUntil) {
                    val zi = serverNpcs.indexOfFirst { it.id == z.id }
                    if (zi >= 0) {
                        val nh = serverNpcs[zi].health - POLICE_SHOOT_DAMAGE
                        serverNpcs[zi] = if (nh <= 0f) serverNpcs[zi].copy(health = 0f, isDying = true)
                        else serverNpcs[zi].copy(health = nh)
                    }
                    synchronized(pendingPoliceShots) { pendingPoliceShots.add(npc.location to z.location) }
                    val a0 = atan2(z.location.latitude - npc.location.latitude, z.location.longitude - npc.location.longitude)
                    return npc.copy(chatUntil = now + POLICE_SHOOT_COOLDOWN_MS,
                        rotationAngle = (-Math.toDegrees(a0).toFloat() + 360) % 360,
                        facingRight = cos(a0) >= 0, isMoving = false)
                }
                return npc
            }
            targetLat = z.location.latitude; targetLon = z.location.longitude
        }
        val dir = atan2(targetLat - npc.location.latitude, targetLon - npc.location.longitude)
        val sp = personSpeed * POLICE_SPEED_MULT
        return npc.copy(
            location = GeoPoint(npc.location.latitude + sin(dir) * sp, npc.location.longitude + cos(dir) * sp),
            rotationAngle = (-Math.toDegrees(dir).toFloat() + 360) % 360,
            isMoving = true,
            facingRight = cos(dir) >= 0,
            navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM
        )
    }

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
                (npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED ||
                        npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK) &&
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
            navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED,
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
            navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
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

    private fun carFollowScale(car: Npc, cars: List<Npc>): Float {
        val way = car.currentWay ?: return 1f
        val ti = car.targetNodeIndex
        if (ti < 0 || ti >= way.nodes.size) return 1f
        val target = way.nodes[ti]
        val fwd = atan2(target.lat - car.location.latitude, target.lon - car.location.longitude)
        var minAhead = Double.MAX_VALUE
        for (other in cars) {
            if (other.id == car.id) continue
            val headDiff = Math.abs(((other.rotationAngle - car.rotationAngle + 540f) % 360f) - 180f)
            if (headDiff > 90f) continue
            val dLat = other.location.latitude - car.location.latitude
            val dLon = other.location.longitude - car.location.longitude
            val d = sqrt(dLat * dLat + dLon * dLon)
            if (d > CAR_FOLLOW_DISTANCE) continue
            val ang = atan2(dLat, dLon)
            val diff = Math.abs(((Math.toDegrees(ang - fwd) + 540) % 360) - 180)
            if (diff < 45 && d < minAhead) minAhead = d
        }
        if (minAhead == Double.MAX_VALUE) return 1f
        // Frenado de emergencia si está MUY cerca (< ~8m)
        if (minAhead < 0.00008) return 0.1f
        return (minAhead / CAR_FOLLOW_DISTANCE).toFloat().coerceIn(0.35f, 1f)
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

    private fun moveLocalNpc(npc: Npc): Npc? {
        val way = npc.currentLocalWay ?: return null
        val landmark = npc.currentLandmark ?: return null
        val navGraph = landmark.navGraph ?: return null

        val nodeIndex = npc.targetNodeIndex
        val direction = npc.moveDirection

        if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
            val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

            if (reachedNode.isParkingSlot) {
                return npc.copy(navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED, speed = 0.0)
            }

            if (navGraph.entryWays.contains(way.id) && nodeIndex < 0) {
                if (npc.type == NpcType.PERSON) return npc.copy(targetNodeIndex = 1, moveDirection = 1)

                carExitCooldowns[npc.id] = System.currentTimeMillis() + 60000L
                return npc.copy(
                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM,
                    currentLocalWay = null,
                    currentLandmark = null,
                    currentWay = null
                )
            }

            val connectedWays = navGraph.ways.filter { w ->
                w.id != way.id && w.nodes.size >= 2 && !w.nodes.any { it.isParkingSlot } &&
                        ((npc.type == NpcType.CAR && w.id < 200) || (npc.type == NpcType.PERSON && w.id >= 200)) &&
                        run {
                            var isNear = false
                            for (i in 0 until w.nodes.size - 1) {
                                val n1 = w.nodes[i]
                                val n2 = w.nodes[i+1]
                                val dist = pointToLineDist(
                                    reachedNode.localX.toDouble(), reachedNode.localY.toDouble(),
                                    n1.localX.toDouble(), n1.localY.toDouble(),
                                    n2.localX.toDouble(), n2.localY.toDouble()
                                )
                                // 👇 FIX 1: Radar reducido a 0.015 para evitar que "brinquen"
                                if (dist < 0.015) { isNear = true; break }
                            }
                            isNear
                        }
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay = connectedWays.random()

                // 👇 FIX 1b: Apuntar al nodo más cercano para seguir la línea y no pisar pasto
                var closestIdx = 0
                var minDist = Double.MAX_VALUE
                for (i in nextWay.nodes.indices) {
                    val n = nextWay.nodes[i]
                    val d = Math.pow(n.localX - reachedNode.localX.toDouble(), 2.0) +
                            Math.pow(n.localY - reachedNode.localY.toDouble(), 2.0)
                    if (d < minDist) {
                        minDist = d
                        closestIdx = i
                    }
                }

                val nextDir = when (closestIdx) {
                    0 -> 1
                    nextWay.nodes.size - 1 -> -1
                    else -> if (Random.nextBoolean()) 1 else -1
                }

                val newTarget = (closestIdx + nextDir).coerceIn(0, nextWay.nodes.size - 1)

                return npc.copy(
                    currentLocalWay = nextWay,
                    targetNodeIndex = closestIdx, // Apunta exacto a la intersección para doblar bien
                    moveDirection = nextDir
                )
            } else {
                val newDir = direction * -1
                val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(targetNodeIndex = newIndex, moveDirection = newDir)
            }
        }

        val targetLocalNode = way.nodes[nodeIndex]
        val targetGlobal = landmark.toGlobalGeoPoint(targetLocalNode.localX, targetLocalNode.localY)

        val dLon = targetGlobal.longitude - npc.location.longitude
        val dLat = targetGlobal.latitude - npc.location.latitude
        val dist = sqrt(dLon * dLon + dLat * dLat)
        val angle = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()
        val isFacingRight = cos(angle) >= 0

        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        val smoothFactor = if (npc.type == NpcType.CAR) 0.45f else 0.20f
        val smoothedAngle = (npc.rotationAngle + diff * smoothFactor + 360) % 360
        val actualSpeed = npc.speed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)
        // FIX "ángulo incorrecto" + anti-órbita (ver mover de calles): heading suavizado
        // solo con desvío pequeño; con desvío grande, directo al objetivo (converge siempre).
        val moveRad = if (npc.type == NpcType.CAR && Math.abs(diff) < 50f)
            Math.toRadians(-smoothedAngle.toDouble())
        else angle

        val isOnCooldown = parkingCooldowns[npc.id]?.let { System.currentTimeMillis() < it } ?: false

        if (dist > actualSpeed * 3 && npc.type == NpcType.CAR && !way.nodes.any { it.isParkingSlot } && !isOnCooldown) {
            val nearbyParkingEntrances = navGraph.ways.filter { w ->
                w.id != way.id && w.nodes.any { it.isParkingSlot }
            }

            for (parkWay in nearbyParkingEntrances) {
                val entryNode = parkWay.nodes.first()
                val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)
                val distToEntry = calculateDistance(npc.location.latitude, npc.location.longitude, entryGlobal.latitude, entryGlobal.longitude)

                if (distToEntry < 0.00006) {
                    val isOccupied = serverNpcs.any { otherCar ->
                        otherCar.id != npc.id && otherCar.currentLocalWay?.id == parkWay.id
                    }

                    if (!isOccupied && Random.nextFloat() < 0.80f) {
                        return npc.copy(
                            currentLocalWay = parkWay,
                            targetNodeIndex = 0,
                            moveDirection = 1
                        )
                    }
                }
            }
        }

        return if (dist < actualSpeed) {
            val pauseTime = if (npc.type == NpcType.PERSON && Random.nextFloat() < 0.08f) {
                System.currentTimeMillis() + Random.nextLong(800, 1800)
            } else {
                npc.chatUntil
            }

            npc.copy(
                location = GeoPoint(targetGlobal.latitude, targetGlobal.longitude),
                targetNodeIndex = nodeIndex + direction,
                rotationAngle = smoothedAngle,
                facingRight = isFacingRight,
                chatUntil = pauseTime,
                isMoving = pauseTime <= System.currentTimeMillis()
            )
        } else {
            npc.copy(
                location = GeoPoint(
                    npc.location.latitude + sin(moveRad) * actualSpeed,
                    npc.location.longitude + cos(moveRad) * actualSpeed
                ),
                rotationAngle = smoothedAngle,
                facingRight = isFacingRight,
                isMoving = true
            )
        }
    }

    private fun pointToLineDist(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        if (l2 == 0.0) return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        val t = maxOf(0.0, minOf(1.0, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2))
        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)
        return kotlin.math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }

    private fun moveNpc(npc: Npc, network: List<MapWay>, now: Long, speedScale: Float): Npc? {
        if (npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED) {
            if (npc.currentLocalWay == null) return npc
            val wakeUpTime = parkedTimers[npc.id]

            if (wakeUpTime == null) {
                parkedTimers[npc.id] = System.currentTimeMillis() + Random.nextLong(PARKING_WAKE_MIN_MS, PARKING_WAKE_MAX_MS)
                return npc
            } else if (System.currentTimeMillis() > wakeUpTime) {
                parkedTimers.remove(npc.id)
                parkingCooldowns[npc.id] = System.currentTimeMillis() + 20000

                val way = npc.currentLocalWay ?: return null
                val newDir = npc.moveDirection * -1
                val newIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2

                return npc.copy(
                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                    speed = carSpeed,
                    moveDirection = newDir,
                    targetNodeIndex = newIndex
                )
            }
            return npc
        }

        if (npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK) {
            return moveLocalNpc(npc)
        }

        if (npc.type == NpcType.PERSON && npc.aggroUntil > now) {
            return moveAggroNpc(npc)
        }

        if (npc.chatUntil > now) {
            return npc.copy(isMoving = false)
        }
        val feared = npc.fearUntil > now

        var way = npc.currentWay
        var nodeIndex = npc.targetNodeIndex
        var direction = npc.moveDirection

        val activeLandmarks = cachedNavLandmarks.get()

        if (way == null) {
            val validWays = network.filter { w ->
                val matchType = (npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople)
                matchType && !isNativeWayOverlappingCustom(w, activeLandmarks)
            }

            if (validWays.isEmpty()) return null

            var closestWay: MapWay? = null
            var closestDist = Double.MAX_VALUE
            var bestNodeIdx = 0

            for (w in validWays) {
                w.nodes.forEachIndexed { idx, node ->
                    val dist = calculateDistance(npc.location.latitude, npc.location.longitude, node.lat, node.lon)
                    if (dist < closestDist) {
                        closestDist = dist
                        closestWay = w
                        bestNodeIdx = idx
                    }
                }
            }
            if (closestWay != null && closestDist < 0.005) {
                way = closestWay
                nodeIndex = bestNodeIdx
                direction = if (bestNodeIdx >= closestWay.nodes.size / 2) -1 else 1
            } else {
                return null
            }
        }

        if (feared && nodeIndex in way.nodes.indices) {
            val tnode = way.nodes[nodeIndex]
            val distNow = calculateDistance(npc.location.latitude, npc.location.longitude, npc.fearFromLat, npc.fearFromLon)
            val distTarget = calculateDistance(tnode.lat, tnode.lon, npc.fearFromLat, npc.fearFromLon)
            if (distTarget < distNow) {
                val oldDir = direction
                direction = -oldDir
                nodeIndex -= oldDir
            }
        }

        // Decrementar compromiso de intersección cada tick
        val newCommitmentTicks = if (npc.commitmentTicks > 0) npc.commitmentTicks - 1 else 0

        if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
            val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

            // Si estamos comprometidos con esta vía, no re-evaluar intersecciones.
            // Simplemente corregimos el índice para que siga moviéndose.
            if (newCommitmentTicks > 0 && npc.committedWayId == way.id) {
                val fixedIndex = nodeIndex.coerceIn(0, way.nodes.size - 1)
                return npc.copy(targetNodeIndex = fixedIndex, commitmentTicks = newCommitmentTicks)
            }

            if (npc.type == NpcType.CAR) {
                for (landmark in cachedNavLandmarks.get()) {
                    val navGraph = landmark.navGraph ?: continue
                    if (navGraph.entryWays.isEmpty()) continue
                    for (entryWayId in navGraph.entryWays) {
                        val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                        val entryNode = entryWay.nodes.first()
                        val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)
                        val distToEntry = calculateDistance(reachedNode.lat, reachedNode.lon, entryGlobal.latitude, entryGlobal.longitude)
                        if (distToEntry < 0.00010) {
                            val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                            val exitCooldown = carExitCooldowns[npc.id] ?: 0L

                            if (now > exitCooldown && now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
                                landmarkEntranceCooldowns[landmark.id.toString()] = now
                                return npc.copy(
                                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                                    currentLandmark = landmark,
                                    currentLocalWay = entryWay,
                                    targetNodeIndex = 0,
                                    moveDirection = 1,
                                    currentWay = null,
                                    commitmentTicks = 0
                                )
                            }
                        }
                    }
                }
            }

            val connectedWays = (nodeToWays.get()[reachedNode.id] ?: emptyList()).filter { w ->
                w.id != way!!.id &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople)) &&
                        !isNativeWayOverlappingCustom(w, activeLandmarks)
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay: MapWay
                val newNodeIndex: Int
                var nextDir: Int
                if (feared) {
                    var bestWay = connectedWays.first()
                    var bestIdx = bestWay.nodes.indexOfFirst { it.id == reachedNode.id }
                    var bestDir = 1
                    var bestDist = -1.0
                    for (w in connectedWays) {
                        val idx = w.nodes.indexOfFirst { it.id == reachedNode.id }
                        for (dir in intArrayOf(1, -1)) {
                            val ni = idx + dir
                            if (ni < 0 || ni >= w.nodes.size) continue
                            val nn = w.nodes[ni]
                            val dist = calculateDistance(nn.lat, nn.lon, npc.fearFromLat, npc.fearFromLon)
                            if (dist > bestDist) { bestDist = dist; bestWay = w; bestIdx = idx; bestDir = dir }
                        }
                    }
                    nextWay = bestWay; newNodeIndex = bestIdx; nextDir = bestDir
                } else if (npc.type == NpcType.CAR && Random.nextFloat() > 0.25f) {
                    val inAng = atan2(reachedNode.lat - npc.location.latitude, reachedNode.lon - npc.location.longitude)
                    var bestW = connectedWays.first()
                    var bestI = bestW.nodes.indexOfFirst { it.id == reachedNode.id }
                    var bestD = if (bestI == 0) 1 else -1
                    var bestAlign = -2.0
                    for (w in connectedWays) {
                        val idx = w.nodes.indexOfFirst { it.id == reachedNode.id }
                        for (dir in intArrayOf(1, -1)) {
                            val ni = idx + dir
                            if (ni < 0 || ni >= w.nodes.size) continue
                            val nn = w.nodes[ni]
                            val outAng = atan2(nn.lat - reachedNode.lat, nn.lon - reachedNode.lon)
                            val align = cos(outAng - inAng)
                            if (align > bestAlign) { bestAlign = align; bestW = w; bestI = idx; bestD = dir }
                        }
                    }
                    nextWay = bestW; newNodeIndex = bestI; nextDir = bestD
                } else {
                    nextWay = connectedWays.random()
                    newNodeIndex = nextWay.nodes.indexOfFirst { it.id == reachedNode.id }
                    nextDir = when (newNodeIndex) {
                        0 -> 1
                        nextWay.nodes.size - 1 -> -1
                        else -> if (Random.nextBoolean()) 1 else -1
                    }
                }
                
                // FIX: Asegurar que targetNodeIndex esté estrictamente dentro de los límites
                var finalTargetIndex = newNodeIndex + nextDir
                if (finalTargetIndex < 0 || finalTargetIndex >= nextWay.nodes.size) {
                    nextDir = -nextDir
                    finalTargetIndex = newNodeIndex + nextDir
                }
                // Si AÚN está fuera de límites (vía de 1 solo nodo), quedarse en la vía actual y reversar
                if (finalTargetIndex < 0 || finalTargetIndex >= nextWay.nodes.size) {
                    val newDir = direction * -1
                    val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                    return npc.copy(currentWay = way, targetNodeIndex = newIndex.coerceIn(0, way.nodes.size - 1), moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon), commitmentTicks = 0)
                }

                return npc.copy(currentWay = nextWay, targetNodeIndex = finalTargetIndex,
                    moveDirection = nextDir, location = GeoPoint(reachedNode.lat, reachedNode.lon),
                    committedWayId = nextWay.id, commitmentTicks = 15) // Compromiso de ~0.5s
            } else {
                val exitCooldown = carExitCooldowns[npc.id] ?: 0L
                if (now < exitCooldown) {
                    return null
                }

                val newDir = direction * -1
                val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(currentWay = way, targetNodeIndex = newIndex.coerceIn(0, way.nodes.size - 1), moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon), commitmentTicks = 0)
            }
        }

        val baseTarget = way.nodes[nodeIndex]
        var tLat: Double
        var tLon: Double
        if (npc.type == NpcType.CAR) {
            val segDLat = baseTarget.lat - npc.location.latitude
            val segDLon = baseTarget.lon - npc.location.longitude
            val segLen = sqrt(segDLat * segDLat + segDLon * segDLon)
            if (segLen > 0.00008) {
                val a = atan2(segDLat, segDLon)
                tLat = baseTarget.lat - cos(a) * LANE_OFFSET
                tLon = baseTarget.lon + sin(a) * LANE_OFFSET
            } else {
                tLat = baseTarget.lat
                tLon = baseTarget.lon
            }
        } else {
            tLat = baseTarget.lat
            tLon = baseTarget.lon
        }

        // ─── ESQUIVE DE TRÁFICO (jugador en mi trayectoria) ──────────────────
        // Geometría en el marco del PROPIO coche: si el jugador está delante (o aún
        // a un costado, hasta rebasarlo por completo) y dentro del ancho de mi
        // trayectoria, desplazo MI OBJETIVO un carril hacia el lado contrario. El
        // offset se recalcula cada tick a partir de la geometría, así que: abre →
        // pasa → se apaga solo → el smoothing lo regresa al carril. Nunca se toca
        // la posición del NPC (eso causaba las órbitas alrededor del jugador).
        var avoidingPlayer = false
        if (npc.type == NpcType.CAR) {
            val relLat = aggroPlayerLat - npc.location.latitude
            val relLon = aggroPlayerLon - npc.location.longitude
            val dPlayer = sqrt(relLat * relLat + relLon * relLon)
            if (dPlayer < TRAFFIC_AVOID_RADIUS && (aggroPlayerLat != 0.0 || aggroPlayerLon != 0.0)) {
                val dirLat0 = tLat - npc.location.latitude
                val dirLon0 = tLon - npc.location.longitude
                val dirLen = sqrt(dirLat0 * dirLat0 + dirLon0 * dirLon0)
                if (dirLen > 1e-9) {
                    val fLat = dirLat0 / dirLen; val fLon = dirLon0 / dirLen   // hacia delante
                    val pLat = -fLon; val pLon = fLat                          // perpendicular
                    val ahead = relLat * fLat + relLon * fLon                  // + = jugador delante
                    val side = relLat * pLat + relLon * pLon                   // de qué lado está
                    // Activo desde que entra a mi trayectoria hasta que quede CLARAMENTE
                    // atrás (histéresis TRAFFIC_AVOID_BEHIND): evita cerrarse encima del
                    // jugador justo al pasarlo.
                    if (ahead > -TRAFFIC_AVOID_BEHIND && kotlin.math.abs(side) < TRAFFIC_AVOID_PATH_HALF) {
                        val s = if (side >= 0) -1.0 else 1.0                   // abrir al lado contrario
                        // Más cerca = apertura más decidida (mín. 40% al borde del radio).
                        val strength = TRAFFIC_AVOID_OFFSET *
                            (1.0 - (dPlayer / TRAFFIC_AVOID_RADIUS)).coerceIn(0.4, 1.0)
                        // FIX "me atraviesan como fantasmas": el OBJETIVO de esquive debe ser
                        // LOCAL (un punto ~9 m adelante + un carril al lado). Desviar el NODO
                        // lejano (50-100 m) cambiaba el rumbo AQUÍ en ~2° — imperceptible, los
                        // coches seguían derecho a través del jugador. Con el objetivo local el
                        // cambio de rumbo es real (~20°): abre, rebasa y al apagarse el offset
                        // retoma su nodo y se reincorpora al carr0il.
                        tLat = npc.location.latitude + fLat * TRAFFIC_AVOID_LOOKAHEAD + pLat * s * strength
                        tLon = npc.location.longitude + fLon * TRAFFIC_AVOID_LOOKAHEAD + pLon * s * strength
                        avoidingPlayer = true
                    }
                }
            }
        }

        val dLon = tLon - npc.location.longitude
        val dLat = tLat - npc.location.latitude
        val dist = sqrt(dLon * dLon + dLat * dLat)
        val angle = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()
        val isFacingRight = cos(angle) >= 0

        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        // Los coches giran más rápido (0.30 vs 0.20) para converger antes en esquinas.
        val smoothFactor = if (npc.type == NpcType.CAR) 0.45f else 0.20f
        val smoothedAngle = (npc.rotationAngle + diff * smoothFactor + 360) % 360
        val effectiveSpeed = npc.speed * speedScale.coerceIn(0f, 1f).toDouble() *
                (if (feared) FEAR_SPEED_MULT.toDouble() else 1.0) *
                (if (npc.type == NpcType.CAR) npc.speedVariation.toDouble() else 1.0)
        val actualSpeed = effectiveSpeed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)
        val moving = actualSpeed > 1e-9
        // FIX "ángulo incorrecto" + FIX "círculos alrededor del jugador":
        // El sprite usa smoothedAngle. Mover SIEMPRE a lo largo del heading suavizado
        // (fix anterior) provocaba el bug clásico de pure-pursuit: con desvío grande y
        // radio de giro insuficiente, el coche ORBITA su objetivo para siempre (se veía
        // dando vueltas en círculos junto al jugador cuando el esquive movía su objetivo).
        // Ahora: con desvío PEQUEÑO (manejo normal) se mueve según su sprite (coinciden
        // visualmente); con desvío GRANDE (giros cerrados/esquives) avanza DIRECTO al
        // objetivo, que converge siempre — el sprite lo alcanza vía el smoothing.
        val moveRad = if (npc.type == NpcType.CAR && Math.abs(diff) < 50f)
            Math.toRadians(-smoothedAngle.toDouble())
        else angle

        if (dist > actualSpeed * 3 && npc.type == NpcType.CAR) {
            for (landmark in activeLandmarks) {
                val navGraph = landmark.navGraph ?: continue
                if (navGraph.entryWays.isEmpty()) continue

                for (entryWayId in navGraph.entryWays) {
                    val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                    val entryNode = entryWay.nodes.first()
                    val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

                    val distToEntry = calculateDistance(npc.location.latitude, npc.location.longitude, entryGlobal.latitude, entryGlobal.longitude)

                    if (distToEntry < 0.00010) {
                        val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                        val exitCooldown = carExitCooldowns[npc.id] ?: 0L

                        if (now > exitCooldown && now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
                            landmarkEntranceCooldowns[landmark.id.toString()] = now
                            return npc.copy(
                                navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                                currentLandmark = landmark,
                                currentLocalWay = entryWay,
                                targetNodeIndex = 0,
                                moveDirection = 1,
                                currentWay = null
                            )
                        }
                    }
                }
            }
        }

        // 👇 FIX 2: Escudo protector anti-intrusos de calles OSM. Si tocan la escuela, se esfuman.
        if (activeLandmarks.any { it.contains(GeoPoint(tLat, tLon)) }) {
            return null
        }

        var canMove = true
        exteriorCollisions?.let { config ->
            for (poly in config.polygons) {
                if (poly.contains(tLat, tLon)) {
                    canMove = false
                    break
                }
            }
            if (canMove) {
                for (wall in config.walls) {
                    if (wall.didHitWall(npc.location.latitude, npc.location.longitude, tLat, tLon)) {
                        canMove = false
                        break
                    }
                }
            }
        }

        if (!canMove) {
            return npc.copy(speed = 0.0, isMoving = false)
        }

        // FIX "rara vez me rebasan / dan vueltas en círculos": cuando el coche esquiva
        // al jugador persigue un carrot LOCAL (~9 m), así que `dist` nunca baja de
        // actualSpeed y el `targetNodeIndex` NO avanzaba → al apagarse el esquive el
        // nodo base quedaba DETRÁS y el coche se daba la vuelta hacia el jugador
        // (bucle/órbita). Si mientras esquiva ya REBASÓ el nodo base (quedó detrás del
        // avance), avanzamos el índice: el coche sigue su ruta y te rebasa de verdad.
        val stepLat = sin(moveRad) * actualSpeed
        val stepLon = cos(moveRad) * actualSpeed
        val newLat = npc.location.latitude + stepLat
        val newLon = npc.location.longitude + stepLon
        val passedBaseNode = avoidingPlayer &&
            ((baseTarget.lat - newLat) * stepLat + (baseTarget.lon - newLon) * stepLon) < 0.0

        return if (dist < actualSpeed || passedBaseNode) {
            npc.copy(currentWay = way, location = if (dist < actualSpeed) GeoPoint(tLat, tLon) else GeoPoint(newLat, newLon), targetNodeIndex = nodeIndex + direction, moveDirection = direction, rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
        } else {
            npc.copy(currentWay = way, targetNodeIndex = nodeIndex, moveDirection = direction, location = GeoPoint(newLat, newLon), rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
        }
    }

    private fun moveAggroNpc(npc: Npc): Npc {
        val dLat = aggroPlayerLat - npc.location.latitude
        val dLon = aggroPlayerLon - npc.location.longitude
        val dist = sqrt(dLat * dLat + dLon * dLon)
        val angle = atan2(dLat, dLon)
        val targetAngle = (-Math.toDegrees(angle).toFloat() + 360) % 360
        val facing = cos(angle) >= 0
        if (dist <= AGGRO_STOP_DIST) {
            return npc.copy(isMoving = false, rotationAngle = targetAngle, facingRight = facing)
        }
        val speed = personSpeed * AGGRO_SPEED_MULT
        return npc.copy(
            location = GeoPoint(
                npc.location.latitude + sin(angle) * speed,
                npc.location.longitude + cos(angle) * speed
            ),
            rotationAngle = targetAngle,
            facingRight = facing,
            isMoving = true
        )
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180)
        return sqrt(dLat * dLat + dLon * dLon)
    }
}