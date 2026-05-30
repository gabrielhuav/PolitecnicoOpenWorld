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

    val pendingDespawns = mutableListOf<String>()

    private val maxNpcs = 40
    private val despawnDistance = 0.035
    private val spawnDistance   = 0.0060

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

            // 2. Control de Población
            val currentNpcsCount = serverNpcs.count { it.displayName.isNullOrEmpty() }
            if (currentNpcsCount > maxNpcs) {
                val excess = currentNpcsCount - maxNpcs
                val sorted = serverNpcs.filter { it.displayName.isNullOrEmpty() }.sortedByDescending {
                    calculateDistance(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude)
                }
                val toAnnihilate = sorted.take(excess)
                serverNpcs.removeAll(toAnnihilate)
                toAnnihilate.forEach { synchronized(pendingDespawns) { pendingDespawns.add(it.id) } }
            } else if (currentNpcsCount < maxNpcs) {
                val pLat = playerLocation.latitude
                val pLon = playerLocation.longitude
                val closeWays = cachedWayBoxes.get()
                    // Pre-filtro barato por bounding box (expandido por spawnDistance):
                    // descarta ways lejanas sin recorrer sus nodos.
                    .filter { box ->
                        pLat >= box.minLat - spawnDistance && pLat <= box.maxLat + spawnDistance &&
                                pLon >= box.minLon - spawnDistance && pLon <= box.maxLon + spawnDistance
                    }
                    // Check caro por nodo solo sobre las candidatas que pasaron el bbox.
                    .filter { box ->
                        box.way.nodes.any { calculateDistance(it.lat, it.lon, pLat, pLon) < spawnDistance }
                    }
                    .map { it.way }
                if (closeWays.isNotEmpty()) {
                    val numToSpawn = minOf(2, maxNpcs - currentNpcsCount)
                    for (i in 0 until numToSpawn) {
                        spawnNpcOnRoad(playerLocation, closeWays)?.let { serverNpcs.add(it) }
                    }
                }
            }

            // 3. Movimiento de sobrevivientes (EXCLUYENDO OTROS JUGADORES)
            val updated = serverNpcs.mapNotNull { npc ->
                if (!npc.displayName.isNullOrEmpty()) {
                    npc // Si es un jugador real, no lo tocamos.
                } else {
                    val moved = moveNpc(npc, currentNetwork)
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

    private fun spawnNpcOnRoad(playerLocation: GeoPoint, closeWays: List<MapWay>): Npc? {
        val npcType = if (Random.nextFloat() < 0.6f) NpcType.CAR else NpcType.PERSON
        val speed   = if (npcType == NpcType.CAR) carSpeed else personSpeed

        val validWays = closeWays.filter {
            (npcType == NpcType.CAR && it.isForCars) || (npcType == NpcType.PERSON && it.isForPeople)
        }
        if (validWays.isEmpty()) return null

        val selectedWay = validWays.random()
        val startIndex  = Random.nextInt(selectedWay.nodes.size)
        val startNode   = selectedWay.nodes[startIndex]

        val distToPlayer = calculateDistance(startNode.lat, startNode.lon, playerLocation.latitude, playerLocation.longitude)
        if (distToPlayer < 0.0002 || distToPlayer > 0.0040) return null

        val dir = if (startIndex == selectedWay.nodes.size - 1) -1 else 1

        val visualConfig = if (npcType == NpcType.PERSON) {
            val colors = listOf(
                androidx.compose.ui.graphics.Color.Red,
                androidx.compose.ui.graphics.Color.Blue,
                androidx.compose.ui.graphics.Color.Green,
                androidx.compose.ui.graphics.Color.Yellow,
                androidx.compose.ui.graphics.Color.Cyan,
                androidx.compose.ui.graphics.Color.Magenta,
                androidx.compose.ui.graphics.Color.White,
                androidx.compose.ui.graphics.Color.DarkGray
            )
            ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
                bodyFolder = "npc_walk_1",
                bodyPrefix = "npc_walk_1_",
                hairId = Random.nextInt(1, 5),
                shirtColor = colors.random(),
                hairColor = colors.random(),
                pantsColor = colors.random()
            )
        } else {
            null // Los coches no usan este sistema visual
        }

        return Npc(
            type = npcType,
            location = GeoPoint(startNode.lat, startNode.lon),
            speed = speed,
            currentWay = selectedWay,
            targetNodeIndex = startIndex + dir,
            moveDirection = dir,
            carColor = android.graphics.Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
            carModel = CarModel.entries.random(),
            isRemote = false,
            visualConfig = visualConfig
        )
    }

    private fun moveNpc(npc: Npc, network: List<MapWay>): Npc? {
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

        // Lógica normal de movimiento
        if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
            val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()
            val connectedWays = network.filter { w ->
                w.id != way!!.id && w.nodes.any { it.id == reachedNode.id } &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople))
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay = connectedWays.random()
                val newNodeIndex = nextWay.nodes.indexOfFirst { it.id == reachedNode.id }
                val nextDir = when (newNodeIndex) {
                    0 -> 1
                    nextWay.nodes.size - 1 -> -1
                    else -> if (Random.nextBoolean()) 1 else -1
                }
                return npc.copy(currentWay = nextWay, targetNodeIndex = newNodeIndex + nextDir,
                    moveDirection = nextDir, location = GeoPoint(reachedNode.lat, reachedNode.lon))
            } else {
                val newDir = direction * -1
                val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(currentWay = way, targetNodeIndex = newIndex, moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon))
            }
        }

        val target = way.nodes[nodeIndex]
        val dLon = target.lon - npc.location.longitude
        val dLat = target.lat - npc.location.latitude
        val dist = sqrt(dLon * dLon + dLat * dLat)
        val angle = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()
        val isFacingRight = cos(angle) >= 0

        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        val smoothedAngle = (npc.rotationAngle + diff * 0.20f + 360) % 360
        val actualSpeed = npc.speed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)

        return if (dist < actualSpeed) {
            npc.copy(currentWay = way, location = GeoPoint(target.lat, target.lon), targetNodeIndex = nodeIndex + direction, moveDirection = direction, rotationAngle = smoothedAngle, facingRight = isFacingRight)
        } else {
            npc.copy(currentWay = way, targetNodeIndex = nodeIndex, moveDirection = direction, location = GeoPoint(npc.location.latitude + sin(angle) * actualSpeed, npc.location.longitude + cos(angle) * actualSpeed), rotationAngle = smoothedAngle, facingRight = isFacingRight)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180)
        return sqrt(dLat * dLat + dLon * dLon)
    }
}