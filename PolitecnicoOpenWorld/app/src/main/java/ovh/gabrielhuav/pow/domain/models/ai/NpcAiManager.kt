package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CarModel
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
        // Velocidades canónicas de NPCs. Expuestas para que el ViewModel pueda usarlas
        // al "adoptar" NPCs remotos y no haya nunca desincronización entre el spawner local
        // y los NPCs adoptados desde la red.
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
        const val LANE_OFFSET = 0.000010

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

    // Bounding box (min/max lat/lon) precomputado por cada way al cargar la red.
    // Permite descartar ways lejanas con una comparación O(1) antes del check
    // caro por nodo (distancia), evitando el O(N*M) en cada spawn.
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

    private val carSpeed    = CAR_SPEED
    private val personSpeed = PERSON_SPEED

    @Volatile private var networkIsReady = false

    fun updateRoadNetwork(network: List<MapWay>) {
        cachedRoadNetwork.set(network)
        // Precomputar el bounding box de cada way una sola vez al cargar la red.
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

        val currentNetwork = cachedRoadNetwork.get()
        if (currentNetwork.isEmpty()) return

        withContext(Dispatchers.Default) {
            // 1. Despawn por distancia (SOLO limpiamos localmente, SIN MANDAR MENSAJE AL SERVIDOR)
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

            // SPAWN: rellenamos hasta maxActiveNpcs DENTRO del fog. El escaneo de calles
            // es lo más caro, así que solo lo hacemos cada SPAWN_SCAN_MS (no en cada tick).
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
                        spawnNpcOnRoad(playerLocation, closeWays)?.let { serverNpcs.add(it) }
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

    private fun spawnNpcOnRoad(playerLocation: GeoPoint, closeWays: List<MapWay>): Npc? {
        val npcType = if (Random.nextFloat() < carPopulationRatio) NpcType.CAR else NpcType.PERSON
        val speed   = if (npcType == NpcType.CAR) carSpeed else personSpeed

        val validWays = closeWays.filter {
            (npcType == NpcType.CAR && it.isForCars) || (npcType == NpcType.PERSON && it.isForPeople)
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

        val dir = if (startIndex == selectedWay.nodes.size - 1) -1 else 1

        // Atuendo tomado de la paleta FIJA (sprites compartidos = caché barata).
        val visualConfig = if (npcType == NpcType.PERSON) NPC_OUTFITS.random() else null

        return Npc(
            type = npcType,
            location = GeoPoint(startNode.lat, startNode.lon),
            speed = speed,
            currentWay = selectedWay,
            targetNodeIndex = startIndex + dir,
            moveDirection = dir,
            carColor = CAR_COLORS.random(),
            carModel = CarModel.entries.random(),
            isRemote = false,
            visualConfig = visualConfig
        )
    }

    private fun moveNpc(npc: Npc, network: List<MapWay>, now: Long, speedScale: Float): Npc? {
        // CHARLA: el peatón está detenido mirando a su pareja; no se mueve.
        if (npc.chatUntil > now) {
            return npc.copy(isMoving = false)
        }
        val feared = npc.fearUntil > now

        var way = npc.currentWay
        var nodeIndex = npc.targetNodeIndex
        var direction = npc.moveDirection

        // RECONSTRUCCIÓN DE RUTA (ADOPCIÓN)
        // Si el NPC viene del servidor, no tiene calle asignada localmente. Lo pegamos a la más cercana.
        if (way == null) {
            val validWays = network.filter {
                (npc.type == NpcType.CAR && it.isForCars) || (npc.type == NpcType.PERSON && it.isForPeople)
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
            // Si está a menos de ~200 metros de una calle, lo adoptamos. Si está en la nada, muere.
            if (closestWay != null && closestDist < 0.002) {
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
            // Intersección en O(grado) usando el índice nodo→ways (no filtramos toda la red).
            val connectedWays = (nodeToWays.get()[reachedNode.id] ?: emptyList()).filter { w ->
                w.id != way!!.id &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople))
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
            if (segLen > 0.00012) {
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

        return if (dist < actualSpeed) {
            npc.copy(currentWay = way, location = GeoPoint(tLat, tLon), targetNodeIndex = nodeIndex + direction, moveDirection = direction, rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
        } else {
            npc.copy(currentWay = way, targetNodeIndex = nodeIndex, moveDirection = direction, location = GeoPoint(npc.location.latitude + sin(angle) * actualSpeed, npc.location.longitude + cos(angle) * actualSpeed), rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180)
        return sqrt(dLat * dLat + dLon * dLon)
    }
}