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
    }

    private val _npcs = MutableStateFlow<List<Npc>>(emptyList())
    val npcs: StateFlow<List<Npc>> = _npcs.asStateFlow()

    private val cachedRoadNetwork = AtomicReference<List<MapWay>>(emptyList())
    private val cachedLandmarks = AtomicReference<List<Landmark>>(emptyList())

    fun setLandmarks(landmarks: List<Landmark>) {
        cachedLandmarks.set(landmarks)
    }

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

    private val parkedTimers = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val parkingCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val populatedLandmarks = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val landmarkEntranceCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private val carSpeed    = CAR_SPEED
    private val personSpeed = PERSON_SPEED

    @Volatile private var networkIsReady = false

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
            if (nodesInside > 0 && (nodesInside.toFloat() / way.nodes.size) > 0.85f) {
                return true
            }
        }
        return false
    }

    suspend fun updateNpcs(playerLocation: GeoPoint, amIHost: Boolean) {
        if (!networkIsReady || !amIHost) return

        val currentNetwork = cachedRoadNetwork.get()
        if (currentNetwork.isEmpty()) return

        withContext(Dispatchers.Default) {
            val toRemove = serverNpcs.filter {
                it.displayName.isNullOrEmpty() &&
                        calculateDistance(it.location.latitude, it.location.longitude, playerLocation.latitude, playerLocation.longitude) > despawnDistance
            }
            serverNpcs.removeAll(toRemove)

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
                val activeLandmarks = cachedLandmarks.get().filter { it.navGraph != null }
                val pLat = playerLocation.latitude
                val pLon = playerLocation.longitude

                // --- 1. LÓGICA DE PRE-LLENADO ORGÁNICO ---
                for (landmark in activeLandmarks) {
                    val dist = calculateDistance(pLat, pLon, landmark.location.latitude, landmark.location.longitude)

                    if (dist < 0.01 && !populatedLandmarks.contains(landmark.id.toString())) {
                        populatedLandmarks.add(landmark.id.toString())

                        val availableSlots = getAvailableParkingSlots(landmark, serverNpcs)
                        if (availableSlots.isNotEmpty()) {
                            val fillPercentage = Random.nextFloat() * 0.3f + 0.5f
                            val numToSpawn = (availableSlots.size * fillPercentage).toInt().coerceAtLeast(1)

                            val slotsToUse = availableSlots.shuffled().take(numToSpawn)
                            var timerOffset = Random.nextLong(15000L, 25000L)

                            slotsToUse.forEach { slot ->
                                if (serverNpcs.size < maxNpcs) {
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

                // --- 2. GENERACIÓN NORMAL DE CALLE ---
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
                    val numToSpawn = minOf(2, maxNpcs - serverNpcs.count { it.displayName.isNullOrEmpty() })

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

            val updated = serverNpcs.mapNotNull { npc ->
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

    private fun spawnNpcOnRoad(playerLocation: GeoPoint, closeWays: List<MapWay>, activeLandmarks: List<Landmark>): Npc? {
        val npcType = if (Random.nextFloat() < 0.6f) NpcType.CAR else NpcType.PERSON
        val speed   = if (npcType == NpcType.CAR) carSpeed else personSpeed

        val validWays = closeWays.filter { way ->
            val matchType = (npcType == NpcType.CAR && way.isForCars) || (npcType == NpcType.PERSON && way.isForPeople)
            matchType && !isNativeWayOverlappingCustom(way, activeLandmarks)
        }

        if (validWays.isEmpty()) return null

        val selectedWay = validWays.random()
        val startIndex  = Random.nextInt(selectedWay.nodes.size)
        val startNode   = selectedWay.nodes[startIndex]

        val distToPlayer = calculateDistance(startNode.lat, startNode.lon, playerLocation.latitude, playerLocation.longitude)
        if (distToPlayer < 0.0002 || distToPlayer > 0.0040) return null

        val startGeo = GeoPoint(startNode.lat, startNode.lon)
        if (activeLandmarks.any { it.contains(startGeo) }) {
            return null
        }

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
        } else { null }

        return Npc(
            type = npcType,
            location = startGeo,
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

    private fun moveNpc(npc: Npc, network: List<MapWay>): Npc? {
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

        var way = npc.currentWay
        var nodeIndex = npc.targetNodeIndex
        var direction = npc.moveDirection

        val activeLandmarks = cachedLandmarks.get().filter { it.navGraph != null }

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

        if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
            val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

            // Detección estricta para autos que "chocan" directo con el nodo de OSM
            if (npc.type == NpcType.CAR) {
                for (landmark in cachedLandmarks.get()) {
                    val navGraph = landmark.navGraph ?: continue
                    if (navGraph.entryWays.isEmpty()) continue

                    for (entryWayId in navGraph.entryWays) {
                        val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                        val entryNode = entryWay.nodes.first()
                        val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

                        val distToEntry = calculateDistance(reachedNode.lat, reachedNode.lon, entryGlobal.latitude, entryGlobal.longitude)

                        if (distToEntry < 0.00010) { // Distancia bajada a 10 metros
                            val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                            val now = System.currentTimeMillis()

                            if (now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
                                landmarkEntranceCooldowns[landmark.id.toString()] = now
                                return npc.copy(
                                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                                    currentLandmark = landmark,
                                    currentLocalWay = entryWay,
                                    targetNodeIndex = 0, // Le decimos que conduzca hacia la entrada
                                    moveDirection = 1,
                                    currentWay = null
                                    // NOTA: Eliminamos location = entryGlobal
                                )
                            }
                        }
                    }
                }
            }

            val connectedWays = network.filter { w ->
                w.id != way.id && w.nodes.any { it.id == reachedNode.id } &&
                        ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople)) &&
                        !isNativeWayOverlappingCustom(w, activeLandmarks)
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
                        val now = System.currentTimeMillis()

                        if (now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
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