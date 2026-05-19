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
    val pendingDespawns = mutableListOf<String>()

    private val maxNpcs = 40
    private val despawnDistance = 0.035
    private val spawnDistance   = 0.0060
    
    // Umbrales al cuadrado para optimizar comparaciones evitando raíces cuadradas
    private val despawnDistanceSq = despawnDistance * despawnDistance
    private val spawnDistanceSq   = spawnDistance * spawnDistance

    private val carSpeed    = CAR_SPEED
    private val personSpeed = PERSON_SPEED

    @Volatile private var networkIsReady = false

    fun updateRoadNetwork(network: List<MapWay>) {
        cachedRoadNetwork.set(network)
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
            // Trabajamos sobre una copia local para evitar el overhead de CopyOnWriteArrayList en cada operación
            val workingList = serverNpcs.toMutableList()

            // 1. Despawn por distancia (Optimizado: removeIf + Distancia al cuadrado)
            workingList.removeAll { npc ->
                npc.displayName.isNullOrEmpty() &&
                        calculateDistanceSq(npc.location.latitude, npc.location.longitude, playerLocation.latitude, playerLocation.longitude) > despawnDistanceSq
            }

            // 2. Control de Población
            val currentNpcsCount = workingList.count { it.displayName.isNullOrEmpty() }
            if (currentNpcsCount > maxNpcs) {
                val excess = currentNpcsCount - maxNpcs
                val toAnnihilate = workingList
                    .filter { it.displayName.isNullOrEmpty() }
                    .sortedByDescending {
                        calculateDistanceSq(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude)
                    }
                    .take(excess)
                
                val toAnnihilateIds = toAnnihilate.map { it.id }.toSet()
                workingList.removeAll { it.id in toAnnihilateIds }
                
                synchronized(pendingDespawns) {
                    pendingDespawns.addAll(toAnnihilateIds)
                }
            } else if (currentNpcsCount < maxNpcs) {
                val closeWays = currentNetwork.filter { way ->
                    way.nodes.any { calculateDistanceSq(it.lat, it.lon, playerLocation.latitude, playerLocation.longitude) < spawnDistanceSq }
                }
                if (closeWays.isNotEmpty()) {
                    val numToSpawn = minOf(2, maxNpcs - currentNpcsCount)
                    for (i in 0 until numToSpawn) {
                        spawnNpcOnRoad(playerLocation, closeWays)?.let { workingList.add(it) }
                    }
                }
            }

            // 3. Movimiento de sobrevivientes
            val updated = workingList.mapNotNull { npc ->
                if (!npc.displayName.isNullOrEmpty()) {
                    npc
                } else {
                    val moved = moveNpc(npc, currentNetwork)
                    if (moved == null) {
                        synchronized(pendingDespawns) { pendingDespawns.add(npc.id) }
                    }
                    moved
                }
            }
            
            // Actualización única de la lista atómica al final
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

        val distToPlayerSq = calculateDistanceSq(startNode.lat, startNode.lon, playerLocation.latitude, playerLocation.longitude)
        // Umbrales: 0.0002 -> 4e-8, 0.0040 -> 1.6e-5
        if (distToPlayerSq < 0.00000004 || distToPlayerSq > 0.000016) return null

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
            null
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

        if (way == null) {
            val validWays = network.filter {
                (npc.type == NpcType.CAR && it.isForCars) || (npc.type == NpcType.PERSON && it.isForPeople)
            }
            if (validWays.isEmpty()) return null

            var closestWay: MapWay? = null
            var closestDistSq = Double.MAX_VALUE
            var bestNodeIdx = 0

            for (w in validWays) {
                w.nodes.forEachIndexed { idx, node ->
                    val distSq = calculateDistanceSq(npc.location.latitude, npc.location.longitude, node.lat, node.lon)
                    if (distSq < closestDistSq) {
                        closestDistSq = distSq
                        closestWay = w
                        bestNodeIdx = idx
                    }
                }
            }
            // 0.002 -> 0.000004
            if (closestWay != null && closestDistSq < 0.000004) {
                way = closestWay
                nodeIndex = bestNodeIdx
                direction = if (bestNodeIdx >= closestWay.nodes.size / 2) -1 else 1
            } else {
                return null
            }
        }

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
        
        // En movimiento lineal, el cálculo simplificado es suficiente
        val distSq = dLon * dLon + dLat * dLat
        val angle = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()
        val isFacingRight = cos(angle) >= 0

        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        val smoothedAngle = (npc.rotationAngle + diff * 0.20f + 360) % 360
        val actualSpeed = npc.speed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)

        return if (distSq < actualSpeed * actualSpeed) {
            npc.copy(currentWay = way, location = GeoPoint(target.lat, target.lon), targetNodeIndex = nodeIndex + direction, moveDirection = direction, rotationAngle = smoothedAngle, facingRight = isFacingRight)
        } else {
            npc.copy(currentWay = way, targetNodeIndex = nodeIndex, moveDirection = direction, location = GeoPoint(npc.location.latitude + sin(angle) * actualSpeed, npc.location.longitude + cos(angle) * actualSpeed), rotationAngle = smoothedAngle, facingRight = isFacingRight)
        }
    }

    private fun calculateDistanceSq(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180.0)
        return dLat * dLat + dLon * dLon
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return sqrt(calculateDistanceSq(lat1, lon1, lat2, lon2))
    }
}