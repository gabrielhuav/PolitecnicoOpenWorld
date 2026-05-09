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
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class NpcAiManager {

    private val _npcs = MutableStateFlow<List<Npc>>(emptyList())
    val npcs: StateFlow<List<Npc>> = _npcs.asStateFlow()

    private val cachedRoadNetwork = AtomicReference<List<MapWay>>(emptyList())
    val pendingDespawns = mutableListOf<String>()

    private val maxNpcs = 40
    private val despawnDistance = 0.035
    private val spawnDistance   = 0.0020

    private val carSpeed    = 0.000008
    private val personSpeed = 0.0000015

    @Volatile private var networkIsReady = false

    fun updateRoadNetwork(network: List<MapWay>) {
        cachedRoadNetwork.set(network)
        networkIsReady = network.isNotEmpty()
    }

    fun setRemoteNpcs(remoteList: List<Npc>) {
        val currentLocals = _npcs.value.filter { !it.isRemote }
        _npcs.value = currentLocals + remoteList
    }

    suspend fun updateNpcs(playerLocation: GeoPoint) {
        if (!networkIsReady) return
        val currentNetwork = cachedRoadNetwork.get()
        if (currentNetwork.isEmpty()) return

        withContext(Dispatchers.Default) {
            val currentNpcs = _npcs.value
            val localNpcs = currentNpcs.filter { !it.isRemote }.toMutableList()
            val remoteNpcs = currentNpcs.filter { it.isRemote }

            // 1. Despawn local natural por distancia
            val toRemoveByDistance = localNpcs.filter { npc ->
                calculateDistance(npc.location.latitude, npc.location.longitude,
                    playerLocation.latitude, playerLocation.longitude) > despawnDistance
            }
            localNpcs.removeAll(toRemoveByDistance)
            toRemoveByDistance.forEach { synchronized(pendingDespawns) { pendingDespawns.add(it.id) } }

            // 2. Calcular población actual visible
            val totalNear = (localNpcs + remoteNpcs).count {
                calculateDistance(it.location.latitude, it.location.longitude,
                    playerLocation.latitude, playerLocation.longitude) < spawnDistance
            }

            // 3. HARD CULLING (Prioridad total al servidor)
            if (totalNear > maxNpcs) {
                val excess = totalNear - maxNpcs

                // Ordenamos los locales por distancia para matar a los más lejanos primero
                val sortedLocals = localNpcs.sortedByDescending {
                    calculateDistance(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude)
                }

                // ¡Ejecución masiva en un solo frame! Eliminamos EXACTAMENTE los que sobran
                val toAnnihilate = sortedLocals.take(excess)
                localNpcs.removeAll(toAnnihilate)

                toAnnihilate.forEach {
                    synchronized(pendingDespawns) { pendingDespawns.add(it.id) }
                }
            }
            // 4. Spawning si faltan
            else if (totalNear < maxNpcs) {
                val closeWays = currentNetwork.filter { way ->
                    way.nodes.any { calculateDistance(it.lat, it.lon,
                        playerLocation.latitude, playerLocation.longitude) < spawnDistance }
                }
                if (closeWays.isNotEmpty()) {
                    val numToSpawn = minOf(2, maxNpcs - totalNear)
                    for (i in 0 until numToSpawn) {
                        spawnNpcOnRoad(playerLocation, closeWays)?.let { localNpcs.add(it) }
                    }
                }
            }

            // 5. Movimiento e Integración
            val updatedLocals = localNpcs.mapNotNull { moveNpc(it, currentNetwork) }
            _npcs.value = updatedLocals + remoteNpcs
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

        if (calculateDistance(startNode.lat, startNode.lon, playerLocation.latitude, playerLocation.longitude) < 0.0002) return null

        val dir = if (startIndex == selectedWay.nodes.size - 1) -1 else 1
        return Npc(
            type = npcType,
            location = GeoPoint(startNode.lat, startNode.lon),
            speed = speed,
            currentWay = selectedWay,
            targetNodeIndex = startIndex + dir,
            moveDirection = dir,
            carColor = android.graphics.Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256)),
            carModel = CarModel.entries.random(),
            isRemote = false
        )
    }

    private fun moveNpc(npc: Npc, network: List<MapWay>): Npc? {
        val way = npc.currentWay ?: return null

        if (npc.targetNodeIndex < 0 || npc.targetNodeIndex >= way.nodes.size) {
            val reachedNode = if (npc.targetNodeIndex < 0) way.nodes.first() else way.nodes.last()
            val connectedWays = network.filter { w ->
                w.id != way.id && w.nodes.any { it.id == reachedNode.id } &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople))
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay = connectedWays.random()
                val nodeIndex = nextWay.nodes.indexOfFirst { it.id == reachedNode.id }
                val nextDir = when (nodeIndex) {
                    0 -> 1
                    nextWay.nodes.size - 1 -> -1
                    else -> if (Random.nextBoolean()) 1 else -1
                }
                return npc.copy(currentWay = nextWay, targetNodeIndex = nodeIndex + nextDir,
                    moveDirection = nextDir, location = GeoPoint(reachedNode.lat, reachedNode.lon))
            } else {
                val newDir = npc.moveDirection * -1
                val newIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(targetNodeIndex = newIndex, moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon))
            }
        }

        val target = way.nodes[npc.targetNodeIndex]
        val dLon = target.lon - npc.location.longitude
        val dLat = target.lat - npc.location.latitude
        val dist = sqrt(dLon * dLon + dLat * dLat)
        val angle = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()

        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        val smoothedAngle = (npc.rotationAngle + diff * 0.20f + 360) % 360
        val actualSpeed = npc.speed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)

        return if (dist < actualSpeed) {
            npc.copy(location = GeoPoint(target.lat, target.lon), targetNodeIndex = npc.targetNodeIndex + npc.moveDirection, rotationAngle = smoothedAngle)
        } else {
            npc.copy(location = GeoPoint(npc.location.latitude + sin(angle) * actualSpeed, npc.location.longitude + cos(angle) * actualSpeed), rotationAngle = smoothedAngle)
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180)
        return sqrt(dLat * dLat + dLon * dLon)
    }
}