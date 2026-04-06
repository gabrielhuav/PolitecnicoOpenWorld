package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class NpcAiManager {

    private val _npcs = MutableStateFlow<List<Npc>>(emptyList())
    val npcs: StateFlow<List<Npc>> = _npcs.asStateFlow()

    private val cachedRoadNetwork = AtomicReference<List<MapWay>>(emptyList())

    private val maxNpcs = 40
    private val despawnDistance = 0.003
    private val spawnDistance   = 0.0015

    private val carSpeed    = 0.000008
    private val personSpeed = 0.0000015

    @Volatile private var networkIsReady = false

    fun updateRoadNetwork(newNetwork: List<MapWay>) {
        cachedRoadNetwork.set(newNetwork)
        networkIsReady = newNetwork.isNotEmpty()
    }

    suspend fun updateNpcs(playerLocation: GeoPoint) = withContext(Dispatchers.Default) {
        val currentNetwork = cachedRoadNetwork.get()
        if (!networkIsReady || currentNetwork.isEmpty()) return@withContext

        val currentList = _npcs.value.toMutableList()
        currentList.removeAll { calculateDistance(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude) > despawnDistance }

        // OPTIMIZACIÓN COPILOT: Usamos tipos primitivos (Double) en lugar de instanciar miles de GeoPoints
        val closeWays = currentNetwork.filter { way ->
            way.nodes.any { node ->
                calculateDistance(node.lat, node.lon, playerLocation.latitude, playerLocation.longitude) <= spawnDistance
            }
        }

        if (closeWays.isNotEmpty()) {
            var attempts = 0
            while (currentList.size < maxNpcs && attempts < 50) {
                spawnNpcOnRoad(playerLocation, closeWays)?.let { currentList.add(it) }
                    ?: run { attempts++ }
            }
        }

        _npcs.value = currentList.map { moveNpc(it, currentNetwork) }
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
        val startGeo    = GeoPoint(startNode.lat, startNode.lon)

        if (calculateDistance(startGeo.latitude, startGeo.longitude, playerLocation.latitude, playerLocation.longitude) < 0.0002) return null

        val dir = if (startIndex == selectedWay.nodes.size - 1) -1 else 1
        return Npc(type = npcType, location = startGeo, speed = speed,
            currentWay = selectedWay, targetNodeIndex = startIndex + dir, moveDirection = dir)
    }

    private fun moveNpc(npc: Npc, network: List<MapWay>): Npc {
        val way = npc.currentWay ?: return npc

        if (npc.targetNodeIndex < 0 || npc.targetNodeIndex >= way.nodes.size) {
            val currentNode = if (npc.targetNodeIndex < 0) way.nodes.first() else way.nodes.last()
            val connectedWays = network.filter {
                ((npc.type == NpcType.CAR && it.isForCars) || (npc.type == NpcType.PERSON && it.isForPeople)) &&
                        it.id != way.id && it.nodes.any { n -> n.id == currentNode.id }
            }
            if (connectedWays.isNotEmpty()) {
                val nextWay   = connectedWays.random()
                val nodeIndex = nextWay.nodes.indexOfFirst { it.id == currentNode.id }
                val nextDir   = when (nodeIndex) {
                    0 -> 1
                    nextWay.nodes.size - 1 -> -1
                    else -> if (Random.nextBoolean()) 1 else -1
                }
                return npc.copy(currentWay = nextWay,
                    targetNodeIndex = nodeIndex + nextDir, moveDirection = nextDir)
            } else {
                val newDir   = npc.moveDirection * -1
                val newIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2
                if (newIndex < 0 || newIndex >= way.nodes.size) return npc
                return npc.copy(targetNodeIndex = newIndex, moveDirection = newDir)
            }
        }

        val target = way.nodes[npc.targetNodeIndex]
        val dLon   = target.lon - npc.location.longitude
        val dLat   = target.lat - npc.location.latitude
        val dist   = sqrt(dLon * dLon + dLat * dLat)

        if (dist < npc.speed) {
            return npc.copy(location = GeoPoint(target.lat, target.lon),
                targetNodeIndex = npc.targetNodeIndex + npc.moveDirection)
        }

        val angle  = atan2(dLat, dLon)
        val newLat = npc.location.latitude  + sin(angle) * npc.speed
        val newLon = npc.location.longitude + cos(angle) * npc.speed
        return npc.copy(location = GeoPoint(newLat, newLon),
            rotationAngle = -Math.toDegrees(angle).toFloat())
    }

    // Sobrecarga de método usando dobles puros
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        sqrt((lat1 - lat2).pow(2) + (lon1 - lon2).pow(2))
}