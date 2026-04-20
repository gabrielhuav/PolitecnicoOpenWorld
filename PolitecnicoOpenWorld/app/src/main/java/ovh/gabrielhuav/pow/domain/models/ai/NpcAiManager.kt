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

    fun updateRoadNetwork(network: List<MapWay>) {
        cachedRoadNetwork.set(network)
        networkIsReady = network.isNotEmpty()
    }

    suspend fun updateNpcs(playerLocation: GeoPoint) {
        if (!networkIsReady) return
        val currentNetwork = cachedRoadNetwork.get()
        if (currentNetwork.isEmpty()) return

        withContext(Dispatchers.Default) {
            val currentNpcs = _npcs.value.toMutableList()

            // Eliminar NPCs que se alejaron mucho
            currentNpcs.removeAll { npc ->
                calculateDistance(
                    npc.location.latitude, npc.location.longitude,
                    playerLocation.latitude, playerLocation.longitude
                ) > despawnDistance
            }

            // Generar nuevos NPCs si faltan
            val closeWays = currentNetwork.filter { way ->
                way.nodes.any { node ->
                    calculateDistance(node.lat, node.lon, playerLocation.latitude, playerLocation.longitude) < spawnDistance
                }
            }

            if (closeWays.isNotEmpty()) {
                while (currentNpcs.size < maxNpcs) {
                    spawnNpcOnRoad(playerLocation, closeWays)?.let { currentNpcs.add(it) } ?: break
                }
            }

            // Mover los NPCs existentes
            val updatedNpcs = currentNpcs.mapNotNull { moveNpc(it, currentNetwork) }
            _npcs.value = updatedNpcs
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
        val startGeo    = GeoPoint(startNode.lat, startNode.lon)

        // Evitar generar NPCs encima del jugador
        if (calculateDistance(startGeo.latitude, startGeo.longitude, playerLocation.latitude, playerLocation.longitude) < 0.0002) return null

        val dir = if (startIndex == selectedWay.nodes.size - 1) -1 else 1

        // Generar un color aleatorio para los vehículos
        val randomColor = android.graphics.Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))

        // Al iniciar, le damos un ángulo inicial genérico (se corregirá en el primer frame de movimiento)
        return Npc(
            type = npcType,
            location = startGeo,
            speed = speed,
            currentWay = selectedWay,
            targetNodeIndex = startIndex + dir,
            moveDirection = dir,
            carColor = randomColor
        )
    }

    private fun moveNpc(npc: Npc, network: List<MapWay>): Npc? {
        val way = npc.currentWay ?: return null

        // 1. ¿Llegó al final (o principio) de su calle actual?
        if (npc.targetNodeIndex < 0 || npc.targetNodeIndex >= way.nodes.size) {

            // Descubrir en qué nodo exacto está parado en este momento
            val reachedNode = if (npc.targetNodeIndex < 0) way.nodes.first() else way.nodes.last()

            // Buscar SOLO calles que compartan ESTE nodo específico
            val connectedWays = network.filter { w ->
                w.id != way.id &&
                        w.nodes.any { it.id == reachedNode.id } &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople))
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay = connectedWays.random()

                // Encontrar en qué parte de la nueva calle está la intersección
                val nodeIndexInNextWay = nextWay.nodes.indexOfFirst { it.id == reachedNode.id }

                // Decidir hacia dónde girar (Si es inicio de calle -> adelante, si es final -> atrás)
                val nextDir = when (nodeIndexInNextWay) {
                    0 -> 1
                    nextWay.nodes.size - 1 -> -1
                    else -> if (Random.nextBoolean()) 1 else -1 // Intersección en forma de T o X
                }

                return npc.copy(
                    currentWay = nextWay,
                    targetNodeIndex = nodeIndexInNextWay + nextDir,
                    moveDirection = nextDir,
                    // ANCLAJE ESTRÍCTO: Forzamos las coordenadas al nodo exacto para evitar que salgan de la calle
                    location = GeoPoint(reachedNode.lat, reachedNode.lon)
                )
            } else {
                // Callejón sin salida: Dar vuelta en U
                val newDir   = npc.moveDirection * -1
                val newIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2
                if (newIndex < 0 || newIndex >= way.nodes.size) return npc

                return npc.copy(
                    targetNodeIndex = newIndex,
                    moveDirection = newDir,
                    // ANCLAJE ESTRÍCTO
                    location = GeoPoint(reachedNode.lat, reachedNode.lon)
                )
            }
        }

        // --- 2. Lógica de movimiento en ruta (frenado y giro) ---
        val target = way.nodes[npc.targetNodeIndex]
        val dLon   = target.lon - npc.location.longitude
        val dLat   = target.lat - npc.location.latitude
        val dist   = sqrt(dLon * dLon + dLat * dLat)

        val angle  = atan2(dLat, dLon)
        val targetAngle = -Math.toDegrees(angle).toFloat()

        val turnSpeed = 0.20f
        val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
        val smoothedAngle = (npc.rotationAngle + diff * turnSpeed + 360) % 360

        val misalignment = Math.abs(diff)
        val speedFactor = (1.0f - (misalignment / 60f).toFloat()).coerceIn(0.15f, 1.0f)
        val actualSpeed = npc.speed * speedFactor

        // Si ya llegó al siguiente nodo del camino, anclar posición y pasar al siguiente
        if (dist < actualSpeed) {
            return npc.copy(
                location = GeoPoint(target.lat, target.lon), // ANCLAJE DURANTE LA RUTA
                targetNodeIndex = npc.targetNodeIndex + npc.moveDirection,
                rotationAngle = smoothedAngle
            )
        }

        val newLat = npc.location.latitude  + sin(angle) * actualSpeed
        val newLon = npc.location.longitude + cos(angle) * actualSpeed

        return npc.copy(
            location = GeoPoint(newLat, newLon),
            rotationAngle = smoothedAngle
        )
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2
        val dLon = lon1 - lon2
        return sqrt(dLat * dLat + dLon * dLon)
    }
}