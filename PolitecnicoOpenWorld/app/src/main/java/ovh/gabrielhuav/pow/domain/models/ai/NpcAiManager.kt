package ovh.gabrielhuav.pow.domain.models.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class NpcAiManager {

    private val _npcs = MutableStateFlow<List<Npc>>(emptyList())
    val npcs: StateFlow<List<Npc>> = _npcs.asStateFlow()

    private var cachedRoadNetwork: List<MapWay> = emptyList()

    private val maxNpcs = 20
    private val despawnDistance = 0.015 // ~1.5km para que no desaparezcan de la vista
    private val spawnDistance = 0.0025  // ~250 metros. Radio donde aparecerán los nuevos NPCs

    private val carSpeed = 0.000015
    private val personSpeed = 0.000003

    fun updateRoadNetwork(newNetwork: List<MapWay>) {
        cachedRoadNetwork = newNetwork
    }

    fun updateNpcs(playerLocation: GeoPoint) {
        if (cachedRoadNetwork.isEmpty()) return

        val currentList = _npcs.value.toMutableList()

        // 1Eliminar NPCs ÚNICAMENTE si están realmente lejos del jugador
        currentList.removeAll {
            calculateDistance(it.location, playerLocation) > despawnDistance
        }

        // Spawnear nuevos NPCs si faltan, pero ahora le pasamos la ubicación del jugador
        while (currentList.size < maxNpcs) {
            spawnNpcOnRoad(playerLocation)?.let { currentList.add(it) } ?: break
        }

        // Mover NPCs actuales
        val updatedList = currentList.map { moveNpc(it) }
        _npcs.value = updatedList
    }

    private fun spawnNpcOnRoad(playerLocation: GeoPoint): Npc? {
        val npcType = if (Random.nextBoolean()) NpcType.CAR else NpcType.PERSON
        val assignedSpeed = if (npcType == NpcType.CAR) carSpeed else personSpeed

        // Obtenemos todas las calles válidas según el tipo de NPC
        val waysForType = cachedRoadNetwork.filter {
            (npcType == NpcType.CAR && it.isForCars) || (npcType == NpcType.PERSON && it.isForPeople)
        }

        // Filtramos para quedarnos SOLO con las calles que estén cerca del jugador
        val closeWays = waysForType.filter { way ->
            way.nodes.any { node ->
                val nodeGeo = GeoPoint(node.lat, node.lon)
                calculateDistance(nodeGeo, playerLocation) <= spawnDistance
            }
        }

        // Si por alguna razón no hay calles cercanas (ej. el jugador está en el desierto),
        // usamos la lista completa como plan de respaldo para no quedarnos sin NPCs.
        val validWays = if (closeWays.isNotEmpty()) closeWays else waysForType

        if (validWays.isEmpty()) return null

        val selectedWay = validWays.random()
        val startIndex = Random.nextInt(selectedWay.nodes.size)
        val startNode = selectedWay.nodes[startIndex]

        val moveDirection = if (startIndex == selectedWay.nodes.size - 1) -1 else 1

        return Npc(
            type = npcType,
            location = GeoPoint(startNode.lat, startNode.lon),
            speed = assignedSpeed,
            currentWay = selectedWay,
            targetNodeIndex = startIndex + moveDirection,
            moveDirection = moveDirection
        )
    }

    private fun moveNpc(npc: Npc): Npc {
        val way = npc.currentWay ?: return npc

        if (npc.targetNodeIndex < 0 || npc.targetNodeIndex >= way.nodes.size) {
            // El NPC llegó al final de este segmento de calle.
            val currentNode = if (npc.targetNodeIndex < 0) way.nodes.first() else way.nodes.last()

            // Buscamos otras calles que se conecten a este mismo punto (Intersecciones)
            val connectedWays = cachedRoadNetwork.filter {
                ((npc.type == NpcType.CAR && it.isForCars) || (npc.type == NpcType.PERSON && it.isForPeople)) &&
                        it.id != way.id &&
                        it.nodes.any { node -> node.id == currentNode.id }
            }

            if (connectedWays.isNotEmpty()) {
                // Tomar una calle conectada al azar (Girar en la esquina)
                val nextWay = connectedWays.random()
                val nodeIndexInNextWay = nextWay.nodes.indexOfFirst { it.id == currentNode.id }

                // Decidir hacia dónde avanzar en la nueva calle
                val nextDirection = when (nodeIndexInNextWay) {
                    0 -> 1 // Si está al inicio, solo puede ir adelante
                    nextWay.nodes.size - 1 -> -1 // Si está al final, solo puede ir atrás
                    else -> if (Random.nextBoolean()) 1 else -1 // Si está en medio, elige al azar
                }

                return npc.copy(
                    currentWay = nextWay,
                    targetNodeIndex = nodeIndexInNextWay + nextDirection,
                    moveDirection = nextDirection
                )
            } else {
                // Callejón sin salida. Dar la vuelta completa (U-Turn).
                val newDirection = npc.moveDirection * -1
                val newTargetIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2

                // Prevenir crasheos si la calle tiene solo 1 nodo (raro, pero posible en datos de mapa corruptos)
                if (newTargetIndex < 0 || newTargetIndex >= way.nodes.size) return npc

                return npc.copy(
                    targetNodeIndex = newTargetIndex,
                    moveDirection = newDirection
                )
            }
        }

        // --- LÓGICA NORMAL DE AVANCE HACIA EL SIGUIENTE NODO ---
        val targetNode = way.nodes[npc.targetNodeIndex]
        val deltaLon = targetNode.lon - npc.location.longitude
        val deltaLat = targetNode.lat - npc.location.latitude
        val distanceToTarget = sqrt(deltaLon * deltaLon + deltaLat * deltaLat)

        // Si ya llegó al nodo objetivo, pasamos al siguiente
        if (distanceToTarget < npc.speed) {
            return npc.copy(
                location = GeoPoint(targetNode.lat, targetNode.lon),
                targetNodeIndex = npc.targetNodeIndex + npc.moveDirection
            )
        }

        // Movimiento matemático suave hacia el nodo
        val angleRadians = atan2(deltaLat, deltaLon)
        val newLat = npc.location.latitude + sin(angleRadians) * npc.speed
        val newLon = npc.location.longitude + cos(angleRadians) * npc.speed

        val rotationDegrees = Math.toDegrees(angleRadians).toFloat()

        return npc.copy(
            location = GeoPoint(newLat, newLon),
            rotationAngle = -rotationDegrees
        )
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        return sqrt((point1.latitude - point2.latitude).pow(2) + (point1.longitude - point2.longitude).pow(2))
    }
}