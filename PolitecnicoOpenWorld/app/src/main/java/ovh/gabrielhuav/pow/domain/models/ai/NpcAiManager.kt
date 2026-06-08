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

        // ─── Parámetros de comportamiento (compartidos SP/MP: el host los corre) ───
        // MIEDO: radio en el que un PLAYER_DAMAGE / ataque dispersa NPCs, duración
        // del pánico y multiplicador de velocidad al huir.
        const val FEAR_RADIUS = 0.0018          // ~180 m alrededor del evento
        const val FEAR_DURATION_MS = 4500L      // cuánto huyen
        const val FEAR_SPEED_MULT = 3.8f        // corren claramente más rápido al huir
        // CHARLAS: distancia para emparejar peatones, duración de la charla y
        // probabilidad por tick de iniciar una (baja para que sea ocasional).
        const val CHAT_DISTANCE = 0.00035       // ~35 m
        const val CHAT_DURATION_MS = 5000L
        const val CHAT_CHANCE = 0.012f
        // TRÁFICO: distancia de "coche delante" para frenar (car-following). ~11 m.
        const val CAR_FOLLOW_DISTANCE = 0.00010
        // CARRILES DUALES: desplazamiento lateral (a la derecha del sentido de marcha,
        // tráfico por la derecha) que se aplica al PUNTO OBJETIVO de cada vehículo.
        // Convierte una misma calle de OSM en dos carriles virtuales. ~1 m (sutil, para
        // que no se vean "torpes" al cruzar nodos densos).
        const val LANE_OFFSET = 0.000024

        // PERSONALIDADES: la fracción de NPCs AGRESIVOS es CONFIGURABLE (por defecto la
        // mitad). El resto son COBARDES (huyen al ser golpeados). Los agresivos NO huyen
        // ("no les da miedo"): devuelven el golpe. Cambia aggressiveRatio para ajustarlo.
        @Volatile var aggressiveRatio: Float = 0.3f
        // EMBESTIDA (aggro): un NPC agresivo persigue al jugador en línea recta durante
        // este tiempo y a este múltiplo de velocidad. Barato: ignora el grafo de calles
        // mientras dura (es un arrebato corto), solo interpola hacia el jugador.
        const val AGGRO_DURATION_MS = 6000L
        // El agresor debe ALCANZAR al jugador: como la IA corre cada 3 ticks del game
        // loop, su velocidad efectiva por tick es ~mult/3 × personSpeed. Con 2.4 era más
        // LENTO que un jugador caminando (0.000003/tick) y nunca llegaba, así que el daño
        // por contacto jamás se aplicaba. Con 9 mantiene el paso de un caminante y casi
        // el de uno corriendo, por lo que sí te acorrala.
        // Bajado de 9 a 5: antes los NPCs corrían MÁS rápido que el jugador caminando y
        // siempre te alcanzaban. Con 5 su velocidad efectiva (÷3 por el ritmo de la IA)
        // queda al nivel de un caminante, así que corriendo puedes escapar de ellos.
        const val AGGRO_SPEED_MULT = 5f
        // Distancia a la que el agresor se "planta" frente al jugador (no lo atraviesa).
        const val AGGRO_STOP_DIST = 0.000018     // ~2 m
        // VISIÓN HOSTIL: un NPC AGRESIVO ataca al jugador que entre en este radio (sin
        // necesidad de ser provocado), para que se note el daño. ~55 m (dentro del fog).
        const val AGGRO_VISION_RADIUS = 0.0005

        fun rollTrait(): ovh.gabrielhuav.pow.domain.models.NpcTrait =
            if (Random.nextFloat() < aggressiveRatio)
                ovh.gabrielhuav.pow.domain.models.NpcTrait.AGGRESSIVE
            else
                ovh.gabrielhuav.pow.domain.models.NpcTrait.COWARD

        // PALETA FIJA DE ATUENDOS (optimización de render): en vez de colores aleatorios
        // por NPC (~2000 combinaciones → un sprite único por peatón), usamos un conjunto
        // PEQUEÑO de atuendos predefinidos. Así los sprites se COMPARTEN entre NPCs: la
        // caché de bitmaps se llena enseguida y casi no se genera/tinta nada nuevo, que
        // es lo más caro en gama baja. Da variedad suficiente sin penalizar el rendimiento.
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

        // Paleta FIJA de colores de coche (mismo motivo: sprites de vehículo compartidos
        // en vez de un tinte RGB aleatorio único por coche × 48 frames de rotación).
        val CAR_COLORS = intArrayOf(
            android.graphics.Color.rgb(200, 30, 30),    // rojo
            android.graphics.Color.rgb(30, 60, 160),    // azul
            android.graphics.Color.rgb(235, 235, 235),  // blanco
            android.graphics.Color.rgb(25, 25, 25),     // negro
            android.graphics.Color.rgb(120, 120, 130),  // gris
            android.graphics.Color.rgb(30, 120, 60),    // verde
            android.graphics.Color.rgb(210, 180, 40)    // dorado
        )
    }

    private val _npcs = MutableStateFlow<List<Npc>>(emptyList())
    val npcs: StateFlow<List<Npc>> = _npcs.asStateFlow()

    private val cachedRoadNetwork = AtomicReference<List<MapWay>>(emptyList())
    private val cachedLandmarks = AtomicReference<List<Landmark>>(emptyList())
    // OPT CPU/GC gama baja: lista PRE-FILTRADA de landmarks con navGraph. Antes se
    // recalculaba (.filter { it.navGraph != null }) en updateNpcs Y dentro de moveNpc por
    // CADA NPC en CADA tick, asignando una lista nueva cada vez (O(NPCs·landmarks)). Como
    // los landmarks solo cambian al editarlos, se computa una sola vez en setLandmarks.
    private val cachedNavLandmarks = AtomicReference<List<Landmark>>(emptyList())

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

    // Índice de adyacencia nodo→ways: para resolver intersecciones en O(grado) en vez
    // de filtrar TODA la red en cada cambio de nodo. Clave para que subir la cantidad
    // de NPCs no dispare la CPU en gama baja.
    private val nodeToWays = AtomicReference<Map<Long, List<MapWay>>>(emptyMap())

    val pendingDespawns = mutableListOf<String>()

    // POBLACIÓN ESCALABLE Y BARATA EN GAMA BAJA:
    //  - maxActiveNpcs: NPCs SIMULADOS y visibles (dentro de simRadius ≈ fog). Solo
    //    estos gastan CPU (movimiento, tráfico, charlas).
    //  - maxTotalNpcs: tope de NPCs guardados en memoria (incluye los "congelados"
    //    fuera del fog). Los congelados NO se mueven: solo ocupan memoria, sin CPU.
    //  - simRadius: radio alrededor del jugador dentro del cual se simula. Un poco
    //    mayor que el anillo de spawn para que entren al fog ya en movimiento.
    // ESCALA AL FOG: el fog de NPCs es de ~70 m (NPC_FOG_VISION_METERS). Para que el
    // mundo NO se vea vacío, los NPCs deben concentrarse alrededor de ese radio, no a
    // cientos de metros. Por eso el anillo de spawn y simRadius están en esa escala.
    private val maxActiveNpcs = 12      // simulados/visibles en el fog (gama baja)
    private val maxTotalNpcs  = 30      // tope en memoria (incluye congelados)
    // Throttle del escaneo de calles para spawnear: es lo más caro (recorre la red).
    // Solo lo hacemos cada SPAWN_SCAN_MS, no en cada tick de simulación.
    private val SPAWN_SCAN_MS = 500L
    @Volatile private var lastSpawnScanMs = 0L
    private val simRadius      = 0.0010 // ~110 m: se simula dentro de esto
    private val despawnDistance = 0.0028 // ~310 m: más allá se eliminan
    private val spawnDistance   = 0.0012 // radio de búsqueda de calles para spawnear

    // SPAWN SUAVE: aparecen en el borde del fog (no encima del jugador) y entran
    // caminando; el fade de aparición suaviza el pop-in.
    private val spawnRingMin = 0.0004   // ~44 m (dentro/al borde del fog)
    private val spawnRingMax = 0.00068  // ~76 m (justo pasando el fog de 70 m)

    // Proporción objetivo de coches: menos coches = menos atascos y más peatones.
    private val carPopulationRatio = 0.35f

    private val parkedTimers = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val parkingCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val carExitCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val populatedLandmarks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val landmarkEntranceCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val carSpeed    = CAR_SPEED
    private val personSpeed = PERSON_SPEED

    @Volatile private var networkIsReady = false

    // Última posición del jugador (la fija updateNpcs cada tick) para que los NPCs en
    // estado de embestida sepan hacia dónde ir sin pasar player por toda la cadena.
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
        // Construir el índice nodo→ways una sola vez al cargar la red.
        val index = HashMap<Long, MutableList<MapWay>>()
        for (way in network) {
            for (n in way.nodes) {
                index.getOrPut(n.id) { mutableListOf() }.add(way)
            }
        }
        nodeToWays.set(index)
        networkIsReady = network.isNotEmpty()
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
            // 👇 FIX: Modificado a 0.45f para aniquilar la calle en U que rozaba el exterior
            if (nodesInside > 0 && (nodesInside.toFloat() / way.nodes.size) > 0.45f) {
                return true
            }
        }
        return false
    }

    // Cola de eventos de pánico pendientes de aplicar. triggerFear se llama desde otro
    // hilo (red / combate) y el game loop reconstruye serverNpcs desde remoteEntities en
    // cada tick; por eso NO marcamos aquí directamente (se perdería). En su lugar
    // encolamos el evento y updateNpcs lo aplica sobre el serverNpcs recién construido,
    // que luego se escribe de vuelta a remoteEntities (así el flag persiste hasta expirar).
    private class FearEvent(val lat: Double, val lon: Double, val until: Long)
    private val pendingFear = CopyOnWriteArrayList<FearEvent>()

    // MIEDO AL COMBATE: registra un evento de pánico en (lat, lon). Los civiles dentro
    // de FEAR_RADIUS huirán alejándose durante FEAR_DURATION_MS. Llamado por el ViewModel
    // SOLO en el host (en SP el jugador siempre es host). Barato y thread-safe.
    fun triggerFear(lat: Double, lon: Double) {
        pendingFear.add(FearEvent(lat, lon, System.currentTimeMillis() + FEAR_DURATION_MS))
    }

    // Aplica (y vacía) los eventos de pánico encolados sobre serverNpcs.
    private fun applyPendingFear() {
        if (pendingFear.isEmpty()) return
        val events = ArrayList(pendingFear)
        pendingFear.clear()
        for (i in serverNpcs.indices) {
            val npc = serverNpcs[i]
            if (!npc.displayName.isNullOrEmpty()) continue
            // Los AGRESIVOS no sienten miedo: nunca huyen (devuelven el golpe). Solo los
            // cobardes (y demás) se dispersan ante un ataque cercano.
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
                    chatUntil = 0L,          // el pánico corta la charla
                    chatPartnerId = null,
                    isMoving = true
                )
            }
        }
    }

    suspend fun updateNpcs(playerLocation: GeoPoint, amIHost: Boolean) {
        if (!networkIsReady || !amIHost) return

        // Guardamos la posición del jugador para la persecución (aggro).
        aggroPlayerLat = playerLocation.latitude
        aggroPlayerLon = playerLocation.longitude

        val currentNetwork = cachedRoadNetwork.get()
        if (currentNetwork.isEmpty()) return

        withContext(Dispatchers.Default) {
            val toRemove = serverNpcs.filter {
                it.displayName.isNullOrEmpty() &&
                        calculateDistance(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude) > despawnDistance
            }
            serverNpcs.removeAll(toRemove)

            // 2. Control de Población (activos = dentro del fog; total = en memoria)
            val pLat0 = playerLocation.latitude
            val pLon0 = playerLocation.longitude
            var activeCount = 0
            var totalCount = 0
            for (n in serverNpcs) {
                if (n.displayName.isNullOrEmpty()) {
                    totalCount++
                    if (calculateDistance(n.location.latitude, n.location.longitude, pLat0, pLon0) <= simRadius) activeCount++
                }
            }

            // Tope de MEMORIA: si hay demasiados civiles en total, eliminamos los más
            // lejanos (no se ven). El mundo queda poblado pero acotado en gama baja.
            if (totalCount > maxTotalNpcs) {
                val excess = totalCount - maxTotalNpcs
                val farthest = serverNpcs.filter { it.displayName.isNullOrEmpty() }
                    .sortedByDescending { calculateDistance(it.location.latitude, it.location.longitude, pLat0, pLon0) }
                    .take(excess)
                serverNpcs.removeAll(farthest)
                farthest.forEach { synchronized(pendingDespawns) { pendingDespawns.add(it.id) } }
            }

            // Landmarks con grafo de navegación (rama de landmarks). Se usa tanto para el
            // pre-llenado de estacionamientos como para el spawn sesgado hacia ellos.
            val activeLandmarks = cachedNavLandmarks.get()

            // PRE-LLENADO ORGÁNICO: al acercarse a un landmark con estacionamiento, llena
            // algunos cajones con autos aparcados (una sola vez por visita).
            for (landmark in activeLandmarks) {
                val dist = calculateDistance(pLat0, pLon0, landmark.location.latitude, landmark.location.longitude)
                if (dist < 0.01 && !populatedLandmarks.contains(landmark.id.toString())) {
                    populatedLandmarks.add(landmark.id.toString())
                    val availableSlots = getAvailableParkingSlots(landmark, serverNpcs)
                    if (availableSlots.isNotEmpty()) {
                        val fillPercentage = Random.nextFloat() * 0.3f + 0.5f
                        val numToSpawn = (availableSlots.size * fillPercentage).toInt().coerceAtLeast(1)
                        val slotsToUse = availableSlots.shuffled().take(numToSpawn)
                        var timerOffset = Random.nextLong(15000L, 25000L)
                        slotsToUse.forEach { slot ->
                            if (serverNpcs.size < maxTotalNpcs) {
                                val newCar = spawnParkedCar(landmark, slot.first, slot.second, timerOffset)
                                serverNpcs.add(newCar)
                                timerOffset += Random.nextLong(15000L, 30000L)
                            }
                        }
                    }
                } else if (dist >= 0.02) {
                    populatedLandmarks.remove(landmark.id.toString())
                }
            }

            // SPAWN normal de calle: rellenamos hasta maxActiveNpcs DENTRO del fog. El
            // escaneo de calles es lo más caro, así que solo lo hacemos cada SPAWN_SCAN_MS.
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

                if (closeWays.isNotEmpty()) {
                    val numToSpawn = minOf(4, maxActiveNpcs - activeCount)
                    for (i in 0 until numToSpawn) {
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
                        spawnNpcOnRoad(playerLocation, targetWays, activeLandmarks)?.let { serverNpcs.add(it) }
                    }
                }
            }

            val now = System.currentTimeMillis()

            // 2.4 MIEDO: aplicar eventos de pánico encolados (PLAYER_DAMAGE / ataques).
            applyPendingFear()

            // 2.5 CHARLAS: emparejar peatones cercanos, libres (sin miedo ni charla),
            // para que se detengan unos segundos mirándose. Solo entre activos (fog).
            maybeStartChats(now, pLat0, pLon0)

            // 2.6 Mantenimiento de charlas: si la pareja desapareció, terminar la charla.
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

            // Snapshot de coches ACTIVOS (dentro del fog) para el car-following.
            val cars = serverNpcs.filter {
                it.displayName.isNullOrEmpty() && it.type == NpcType.CAR &&
                        calculateDistance(it.location.latitude, it.location.longitude, pLat0, pLon0) <= simRadius
            }

            // 3. Movimiento: SOLO se simulan los civiles dentro de simRadius (fog). Los
            //    de fuera quedan CONGELADOS (se devuelven tal cual): en memoria, sin CPU.
            //    Los autos aparcados/en-landmark se simulan vía moveNpc (estados PARKED /
            //    MICRO_LANDMARK) cuando están dentro del fog.
            val updated = serverNpcs.mapNotNull { npc ->
                if (!npc.displayName.isNullOrEmpty()) {
                    npc // Si es un jugador real, no lo tocamos.
                } else if (calculateDistance(npc.location.latitude, npc.location.longitude, pLat0, pLon0) > simRadius) {
                    npc // Congelado: fuera del fog. No gasta CPU de movimiento.
                } else {
                    // TRÁFICO: un coche frena si tiene otro coche justo delante.
                    val speedScale = if (npc.type == NpcType.CAR) carFollowScale(npc, cars) else 1f
                    val moved = moveNpc(npc, currentNetwork, now, speedScale)
                    if (moved == null) {
                        synchronized(pendingDespawns) { pendingDespawns.add(npc.id) }
                    }
                    moved
                }
            }
            serverNpcs.clear()
            serverNpcs.addAll(updated)
        }
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

    // CHARLAS: busca pares de peatones cercanos y libres y, con baja probabilidad,
    // los pone a charlar (detenidos, mirándose) durante CHAT_DURATION_MS.
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
                    // Rotaciones para que se miren (mismo criterio que moveNpc).
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

    // TRÁFICO (car-following): devuelve un factor de velocidad ∈ [0,1]. Si hay otro
    // coche justo delante (en la dirección de avance), frena hasta detenerse.
    private fun carFollowScale(car: Npc, cars: List<Npc>): Float {
        val way = car.currentWay ?: return 1f
        val ti = car.targetNodeIndex
        if (ti < 0 || ti >= way.nodes.size) return 1f
        val target = way.nodes[ti]
        val fwd = atan2(target.lat - car.location.latitude, target.lon - car.location.longitude)
        var minAhead = Double.MAX_VALUE
        for (other in cars) {
            if (other.id == car.id) continue
            // Solo seguimos coches que van en NUESTRA MISMA dirección; los de sentido
            // contrario (que ya van por su carril) NO deben hacernos frenar/parar.
            val headDiff = Math.abs(((other.rotationAngle - car.rotationAngle + 540f) % 360f) - 180f)
            if (headDiff > 90f) continue
            val dLat = other.location.latitude - car.location.latitude
            val dLon = other.location.longitude - car.location.longitude
            val d = sqrt(dLat * dLat + dLon * dLon)
            if (d > CAR_FOLLOW_DISTANCE) continue
            // ¿Está delante? (mismo sentido que el vector de avance)
            val ang = atan2(dLat, dLon)
            val diff = Math.abs(((Math.toDegrees(ang - fwd) + 540) % 360) - 180)
            if (diff < 45 && d < minAhead) minAhead = d
        }
        if (minAhead == Double.MAX_VALUE) return 1f
        // Más cerca = más frena, pero NUNCA se detiene del todo (piso 0.35): así no se
        // forman atascos de coches parados; solo reducen la marcha.
        return (minAhead / CAR_FOLLOW_DISTANCE).toFloat().coerceIn(0.35f, 1f)
    }

    private fun spawnNpcOnRoad(playerLocation: GeoPoint, closeWays: List<MapWay>, activeLandmarks: List<Landmark>): Npc? {
        val npcType = if (Random.nextFloat() < carPopulationRatio) NpcType.CAR else NpcType.PERSON
        val speed   = if (npcType == NpcType.CAR) carSpeed else personSpeed

        val validWays = closeWays.filter { way ->
            val matchType = (npcType == NpcType.CAR && way.isForCars) || (npcType == NpcType.PERSON && way.isForPeople)
            matchType && !isNativeWayOverlappingCustom(way, activeLandmarks)
        }

        if (validWays.isEmpty()) return null

        // SPAWN SUAVE: elegimos directamente un nodo que caiga en el anillo lejano
        // [spawnRingMin, spawnRingMax], para no aparecer encima del jugador pero
        // garantizando que SIEMPRE haya candidatos si alguna calle llega al anillo.
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

        // TRÁFICO EN DOBLE SENTIDO: la dirección de marcha se elige al azar (no sesgada
        // hacia +1), de modo que por una misma calle circulan coches en AMBOS sentidos.
        // El desplazamiento de carril (LANE_OFFSET, a la derecha) ya los separa para que
        // no se crucen de frente.
        val dir = when (startIndex) {
            0 -> 1
            selectedWay.nodes.size - 1 -> -1
            else -> if (Random.nextBoolean()) 1 else -1
        }

        // Atuendo tomado de la paleta FIJA (sprites compartidos = caché barata).
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
            trait = rollTrait()
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
                carExitCooldowns[npc.id] = System.currentTimeMillis() + 60000L
                return npc.copy(
                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM,
                    currentLocalWay = null,
                    currentLandmark = null,
                    currentWay = null
                )
            }

            val connectedWays = navGraph.ways.filter { w ->
                w.id != way.id && w.nodes.size >= 2 && !w.nodes.any { it.isParkingSlot } && run {
                    var isNear = false
                    for (i in 0 until w.nodes.size - 1) {
                        val n1 = w.nodes[i]
                        val n2 = w.nodes[i+1]
                        val dist = pointToLineDist(
                            reachedNode.localX.toDouble(), reachedNode.localY.toDouble(),
                            n1.localX.toDouble(), n1.localY.toDouble(),
                            n2.localX.toDouble(), n2.localY.toDouble()
                        )
                        if (dist < 0.05) { isNear = true; break }
                    }
                    isNear
                }
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay = connectedWays.random()
                val nextDir = if (Random.nextBoolean()) 1 else -1
                val newTarget = if (nextDir == 1) 1 else nextWay.nodes.size - 2

                return npc.copy(
                    currentLocalWay = nextWay,
                    targetNodeIndex = newTarget,
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
        val smoothedAngle = (npc.rotationAngle + diff * 0.20f + 360) % 360
        val actualSpeed = npc.speed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)

        val isOnCooldown = parkingCooldowns[npc.id]?.let { System.currentTimeMillis() < it } ?: false

        // Detección para entrar al CAJÓN sin teletransportarse
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
                            targetNodeIndex = 0, // Le decimos que conduzca hacia la orilla del cajón, no teletransportarse
                            moveDirection = 1
                        )
                    }
                }
            }
        }

        return if (dist < actualSpeed) {
            npc.copy(
                location = GeoPoint(targetGlobal.latitude, targetGlobal.longitude),
                targetNodeIndex = nodeIndex + direction,
                rotationAngle = smoothedAngle,
                facingRight = isFacingRight
            )
        } else {
            npc.copy(
                location = GeoPoint(
                    npc.location.latitude + sin(angle) * actualSpeed,
                    npc.location.longitude + cos(angle) * actualSpeed
                ),
                rotationAngle = smoothedAngle,
                facingRight = isFacingRight
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
        // Estados de navegación por landmark (rama de landmarks): lógica propia, sin
        // pasar por chat/miedo/tráfico de calle.
        if (npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED) {
            val wakeUpTime = parkedTimers[npc.id]

            if (wakeUpTime == null) {
                parkedTimers[npc.id] = System.currentTimeMillis() + Random.nextLong(15000, 60000)
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

        // EMBESTIDA: un NPC AGRESIVO persigue al jugador en línea recta SOLO si fue
        // provocado (lo golpeaste / le robaste el coche) → aggroUntil. NO atacan sin
        // motivo: el comportamiento "agresivo" es DEVOLVER el golpe, no agredir primero.
        if (npc.type == NpcType.PERSON && npc.aggroUntil > now) {
            return moveAggroNpc(npc)
        }

        // CHARLA: el peatón está detenido mirando a su pareja; no se mueve.
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

        // MIEDO (media vuelta): si el nodo objetivo me acerca al punto de pánico,
        // doy la vuelta para alejarme. La elección en intersecciones afina el resto.
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

        // Lógica normal de movimiento
        if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
            val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

            // Entrada a landmark para autos que llegan a un nodo de OSM (rama de landmarks).
            if (npc.type == NpcType.CAR) {
                for (landmark in cachedNavLandmarks.get()) {
                    val navGraph = landmark.navGraph ?: continue
                    if (navGraph.entryWays.isEmpty()) continue
                    for (entryWayId in navGraph.entryWays) {
                        val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                        val entryNode = entryWay.nodes.first()
                        val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)
                        val distToEntry = calculateDistance(reachedNode.lat, reachedNode.lon, entryGlobal.latitude, entryGlobal.longitude)
                        if (distToEntry < 0.00010) { // ~10 m
                            val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                            // 👇 FIX: Leemos el castigo en el choque directo
                            val exitCooldown = carExitCooldowns[npc.id] ?: 0L

                            // Usamos el 'now' del parámetro
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

            // Intersección en O(grado) por índice nodo→ways + evita ways que solapan landmarks.
            val connectedWays = (nodeToWays.get()[reachedNode.id] ?: emptyList()).filter { w ->
                w.id != way!!.id &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople)) &&
                        !isNativeWayOverlappingCustom(w, activeLandmarks)
            }

            if (connectedWays.isNotEmpty()) {
                // MIEDO: al elegir calle en una intersección, prefiere la que aleja
                // del punto de pánico (fearFrom). Sin miedo: elección aleatoria.
                val nextWay: MapWay
                val newNodeIndex: Int
                val nextDir: Int
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
                    // COCHES: 75% del tiempo siguen lo más RECTO posible (la calle saliente
                    // mejor alineada con su rumbo de entrada). Evita giros bruscos y las
                    // "vueltas en bucle" en las intersecciones; el 25% restante gira al azar.
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
                return npc.copy(currentWay = nextWay, targetNodeIndex = newNodeIndex + nextDir,
                    moveDirection = nextDir, location = GeoPoint(reachedNode.lat, reachedNode.lon))
            } else {
                // 👇 FIX MAESTRO: Si acaba de salir de ESCOM (castigado) y topó con un callejón
                // sin salida (la infame 'U'), lo despawneamos para que no dé vueltas infinitas.
                val exitCooldown = carExitCooldowns[npc.id] ?: 0L
                if (now < exitCooldown) {
                    return null // Esto lo desaparece limpia y silenciosamente
                }

                val newDir = direction * -1
                val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(currentWay = way, targetNodeIndex = newIndex, moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon))
            }
        }

        val baseTarget = way.nodes[nodeIndex]
        // CARRIL DUAL: para coches, el punto objetivo se desplaza a la DERECHA del
        // sentido de marcha (tráfico por la derecha), creando dos carriles virtuales en
        // la misma calle de OSM y evitando solapamientos de frente. Peatones: centro.
        val tLat: Double
        val tLon: Double
        if (npc.type == NpcType.CAR) {
            val segDLat = baseTarget.lat - npc.location.latitude
            val segDLon = baseTarget.lon - npc.location.longitude
            val segLen = sqrt(segDLat * segDLat + segDLon * segDLon)
            // Solo desplazamos a carril en tramos LARGOS. En tramos cortos (nodos densos
            // de intersecciones) el offset lateral hacía girar el rumbo bruscamente y los
            // coches "daban vueltas"; ahí van por el centro.
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
        val dLon = tLon - npc.location.longitude
        val dLat = tLat - npc.location.latitude
        val dist = sqrt(dLon * dLon + dLat * dLat)
        val angle = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()
        val isFacingRight = cos(angle) >= 0

        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        val smoothedAngle = (npc.rotationAngle + diff * 0.20f + 360) % 360
        // Velocidad efectiva: base × frenado-de-tráfico (speedScale) × pánico (FEAR).
        val effectiveSpeed = npc.speed * speedScale.coerceIn(0f, 1f).toDouble() *
                (if (feared) FEAR_SPEED_MULT.toDouble() else 1.0)
        val actualSpeed = effectiveSpeed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)
        // Si el tráfico lo paró del todo, se queda quieto (sin animación de avance).
        val moving = actualSpeed > 1e-9

        //  Detección periférica (Para que entren sin importar si chocaron o no con el nodo OSM)
        if (dist > actualSpeed * 3 && npc.type == NpcType.CAR) {
            for (landmark in activeLandmarks) {
                val navGraph = landmark.navGraph ?: continue
                if (navGraph.entryWays.isEmpty()) continue

                for (entryWayId in navGraph.entryWays) {
                    val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                    val entryNode = entryWay.nodes.first()
                    val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

                    val distToEntry = calculateDistance(npc.location.latitude, npc.location.longitude, entryGlobal.latitude, entryGlobal.longitude)

                    if (distToEntry < 0.00010) { // Distancia ajustada a 10 metros para que no atropellen el pasto
                        val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                        // 👇 FIX: El Radar Periférico también lee el castigo
                        val exitCooldown = carExitCooldowns[npc.id] ?: 0L

                        // Validamos que 'now' sea mayor al castigo antes de forzar el volantazo
                        if (now > exitCooldown && now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
                            landmarkEntranceCooldowns[landmark.id.toString()] = now
                            return npc.copy(
                                navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                                currentLandmark = landmark,
                                currentLocalWay = entryWay,
                                targetNodeIndex = 0, // Le decimos que conduzca hacia la entrada
                                moveDirection = 1,
                                currentWay = null
                            )
                        }
                    }
                }
            }
        }

        return if (dist < actualSpeed) {
            npc.copy(currentWay = way, location = GeoPoint(tLat, tLon), targetNodeIndex = nodeIndex + direction, moveDirection = direction, rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
        } else {
            npc.copy(currentWay = way, targetNodeIndex = nodeIndex, moveDirection = direction, location = GeoPoint(npc.location.latitude + sin(angle) * actualSpeed, npc.location.longitude + cos(angle) * actualSpeed), rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
        }
    }

    // Persecución directa al jugador (estado aggro). Interpola hacia su última posición
    // conocida; se detiene a AGGRO_STOP_DIST para no solaparlo (el daño por contacto lo
    // aplica el ViewModel, que es quien posee la vida del jugador).
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