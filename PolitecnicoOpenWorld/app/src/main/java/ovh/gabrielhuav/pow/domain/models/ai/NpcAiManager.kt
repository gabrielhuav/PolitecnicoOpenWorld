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
        // Velocidades canónicas de NPCs. Expuestas para que el ViewModel pueda usarlas
        // al "adoptar" NPCs remotos y no haya nunca desincronización entre el spawner local
        // y los NPCs adoptados desde la red.
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

    val pendingDespawns = mutableListOf<String>()

    private val maxNpcs = 40
    private val despawnDistance = 0.035
    private val spawnDistance   = 0.0060
    // Diccionario para saber en qué milisegundo debe despertar cada coche
    private val parkedTimers = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Diccionario para evitar que se vuelvan a estacionar inmediatamente
    private val parkingCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
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
                // ---- NUEVA LÓGICA DE SPAWN "MAGNETIZADO" A EDIFICIOS ----

                // 1. Encontrar todos los edificios con estacionamiento (navGraph != null)
                val activeLandmarks = cachedLandmarks.get().filter { it.navGraph != null }

                // 2. Filtrar calles que estén cerca del jugador (spawnDistance)
                val closeWays = currentNetwork.filter { way ->
                    way.nodes.any { calculateDistance(it.lat, it.lon, playerLocation.latitude, playerLocation.longitude) < spawnDistance }
                }

                if (closeWays.isNotEmpty()) {
                    val numToSpawn = minOf(2, maxNpcs - currentNpcsCount) // Spawneamos de 2 en 2 para no saturar el frame

                    for (i in 0 until numToSpawn) {
                        var targetWays = closeWays

                        // 3. "Imán" de tráfico: 70% de probabilidad de forzar a que el auto nazca
                        // cerca de un edificio con estacionamiento (si hay alguno cerca)
                        if (activeLandmarks.isNotEmpty() && Random.nextFloat() < 0.70f) {
                            val targetLandmark = activeLandmarks.random()
                            val waysNearLandmark = closeWays.filter { way ->
                                way.nodes.any { node ->
                                    calculateDistance(node.lat, node.lon, targetLandmark.location.latitude, targetLandmark.location.longitude) < 0.002 // Calles a 200m del edificio
                                }
                            }
                            // Si encontramos calles cercanas al edificio, forzamos el spawn ahí
                            if (waysNearLandmark.isNotEmpty()) {
                                targetWays = waysNearLandmark
                            }
                        }

                        // 4. Intentamos crear el NPC en las calles seleccionadas
                        spawnNpcOnRoad(playerLocation, targetWays)?.let { serverNpcs.add(it) }
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
    private fun moveLocalNpc(npc: Npc): Npc? {
        val way = npc.currentLocalWay ?: return null
        val landmark = npc.currentLandmark ?: return null
        val navGraph = landmark.navGraph ?: return null

        val nodeIndex = npc.targetNodeIndex
        val direction = npc.moveDirection

        // --- 1. LÓGICA DE FIN DE CARRIL (Llegó a un extremo) ---
        if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
            val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

            // ¿Llegó al final del cajón? (¡A estacionarse!)
            if (reachedNode.isParkingSlot) {
                return npc.copy(navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED, speed = 0.0)
            }

            // ¿Es la salida a la calle de OpenStreetMap?
            if (navGraph.entryWays.contains(way.id) && nodeIndex < 0) {
                return npc.copy(
                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MACRO_OSM,
                    currentLocalWay = null,
                    currentLandmark = null
                )
            }

            // SALIR DEL CAJÓN O LLEGAR A UN MURO: Buscar carril principal para incorporarse
            // Buscamos cualquier camino cuya línea pase muy cerca de este punto (Point-to-Line)
            val connectedWays = navGraph.ways.filter { w ->
                w.id != way.id && w.nodes.size >= 2 && run {
                    var isNear = false
                    for (i in 0 until w.nodes.size - 1) {
                        val n1 = w.nodes[i]
                        val n2 = w.nodes[i+1]
                        val dist = pointToLineDist(
                            reachedNode.localX.toDouble(), reachedNode.localY.toDouble(),
                            n1.localX.toDouble(), n1.localY.toDouble(),
                            n2.localX.toDouble(), n2.localY.toDouble()
                        )
                        // Si la línea del carril pasa a menos de 5% de distancia, nos incorporamos
                        if (dist < 0.05) { isNear = true; break }
                    }
                    isNear
                }
            }

            if (connectedWays.isNotEmpty()) {
                val nextWay = connectedWays.random()
                // Al incorporarse a la avenida, elige dirección al azar (izquierda o derecha)
                val nextDir = if (Random.nextBoolean()) 1 else -1
                val newTarget = if (nextDir == 1) 1 else nextWay.nodes.size - 2

                return npc.copy(
                    currentLocalWay = nextWay,
                    targetNodeIndex = newTarget,
                    moveDirection = nextDir
                )
            } else {
                // Callejón sin salida real, rebotar
                val newDir = direction * -1
                val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(targetNodeIndex = newIndex, moveDirection = newDir)
            }
        }

        // --- 2. MOVIMIENTO SUAVE HACIA EL NODO ---
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

        // --- 3. IA DE VISIÓN PERIFÉRICA (DETECTAR CAJONES MIENTRAS MANEJA) ---

        // Revisamos si este coche acaba de salir de un cajón y está penalizado
        val isOnCooldown = parkingCooldowns[npc.id]?.let { System.currentTimeMillis() < it } ?: false

        // Si vamos rápido, somos auto, no estamos en un cajón, Y NO ESTAMOS PENALIZADOS:
        if (dist > actualSpeed * 3 && npc.type == NpcType.CAR && !way.nodes.any { it.isParkingSlot } && !isOnCooldown) {

            val nearbyParkingEntrances = navGraph.ways.filter { w ->
                w.id != way.id && w.nodes.any { it.isParkingSlot }
            }

            for (parkWay in nearbyParkingEntrances) {
                val entryNode = parkWay.nodes.first()
                val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)
                val distToEntry = calculateDistance(npc.location.latitude, npc.location.longitude, entryGlobal.latitude, entryGlobal.longitude)

                if (distToEntry < 0.00003) {

                    // 👇 ARREGLO BUG 2: ¿Hay alguien en este cajón?
                    val isOccupied = serverNpcs.any { otherCar ->
                        // Si otro coche (diferente a mí) ya tiene esta ruta como su ruta actual, está ocupado
                        otherCar.id != npc.id && otherCar.currentLocalWay?.id == parkWay.id
                    }

                    // Solo entramos si el cajón está libre y ganamos el volado del 50%
                    if (!isOccupied && Random.nextFloat() < 0.50f) {
                        return npc.copy(
                            currentLocalWay = parkWay,
                            targetNodeIndex = 1,
                            moveDirection = 1,
                            location = entryGlobal
                        )
                    }
                }
            }
        }

        // --- 4. APLICAR MOVIMIENTO FÍSICO ---
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

    // --- FUNCIÓN MATEMÁTICA AUXILIAR ---
    // Calcula la distancia perpendicular desde un punto a una línea (segmento)
    private fun pointToLineDist(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
        if (l2 == 0.0) return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        val t = maxOf(0.0, minOf(1.0, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2))
        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)
        return kotlin.math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }
    private fun moveNpc(npc: Npc, network: List<MapWay>): Npc? {
        // --- NUEVA BIFURCACIÓN DE ESTADOS ---
        if (npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.PARKED) {
            val wakeUpTime = parkedTimers[npc.id]

            if (wakeUpTime == null) {
                // Acaba de estacionarse. Generamos un tiempo aleatorio entre 10 y 30 segundos
                parkedTimers[npc.id] = System.currentTimeMillis() + Random.nextLong(10000, 30000)
                return npc
            } else if (System.currentTimeMillis() > wakeUpTime) {
                // ¡Pasó el tiempo! Es hora de despertar
                parkedTimers.remove(npc.id)

                // Le damos 20 segundos donde tiene prohibido volver a estacionarse
                parkingCooldowns[npc.id] = System.currentTimeMillis() + 20000

                val way = npc.currentLocalWay ?: return null

                // Ponemos "reversa": invertimos su dirección para que salga del cajón
                val newDir = npc.moveDirection * -1

                // Calculamos a qué nodo debe retroceder para salir hacia el pasillo principal
                val newIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2

                return npc.copy(
                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                    speed = carSpeed, // Le devolvemos su velocidad
                    moveDirection = newDir,
                    targetNodeIndex = newIndex
                )
            }
            // Aún no es tiempo, sigue estacionado
            return npc
        }

        if (npc.navState == ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK) {
            // Todo lo demás sigue exactamente igual
            return moveLocalNpc(npc) // Salta a la nueva función de movimiento local
        }
        // ------------------------------------
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

            // ---- NUEVO: DETECCIÓN DE ENTRADA A ESTACIONAMIENTO ----
            if (npc.type == NpcType.CAR) {
                // Buscamos si hay algún edificio cerca de este cruce
                val nearbyLandmark = cachedLandmarks.get().find { landmark ->
                    calculateDistance(reachedNode.lat, reachedNode.lon, landmark.location.latitude, landmark.location.longitude) < 0.001 // Aprox 100 metros
                }

                if (nearbyLandmark != null) {
                    val navGraph = nearbyLandmark.navGraph
                    if (navGraph != null && navGraph.entryWays.isNotEmpty()) {
                        // ¡Hay un estacionamiento! Tiramos los dados (ej. 20% de probabilidad de entrar)
                        if (Random.nextFloat() < 0.80f) {
                            val entryWayId = navGraph.entryWays.random() // Elegir una entrada al azar
                            val entryWay = navGraph.ways.find { it.id == entryWayId }

                            if (entryWay != null) {
                                // "Secuestramos" al coche de OSM y lo metemos al estacionamiento
                                return npc.copy(
                                    navState = ovh.gabrielhuav.pow.domain.models.NpcNavState.MICRO_LANDMARK,
                                    currentLandmark = nearbyLandmark,
                                    currentLocalWay = entryWay,
                                    targetNodeIndex = 1, // Hacia adentro
                                    moveDirection = 1,
                                    currentWay = null // Borramos su ruta de calle normal
                                )
                            }
                        }
                    }
                }
            }
            // ---- FIN DE DETECCIÓN ----

            val connectedWays = network.filter { w ->
                // ... (tu código anterior de connectedWays)
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