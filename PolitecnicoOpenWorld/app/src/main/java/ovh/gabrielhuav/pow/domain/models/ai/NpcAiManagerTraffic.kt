package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Parcial de TRÁFICO / NAVEGACIÓN de calles de [NpcAiManager] (extraído para reducir el tamaño
 * de la clase; mismo paquete `ai`). Contiene los dos movers grandes: `moveNpc` (red OSM del mundo
 * abierto, con compromiso de intersección, esquive de tráfico y entrada a estacionamientos) y
 * `moveLocalNpc` (navGraph de campus). Son EXTENSIONES: solo tocan miembros `internal`/`public`
 * del manager (`serverNpcs`, `personSpeed`, `carSpeed`, `cachedNavLandmarks`, `nodeToWays`,
 * `exteriorCollisions`, `parkedTimers`/`parkingCooldowns`/`carExitCooldowns`/
 * `landmarkEntranceCooldowns`, `aggroPlayerLat/Lon`, `TRAFFIC_AVOID_*`, `PARKING_WAKE_*`,
 * `isNativeWayOverlappingCustom`) y cualifican los consts del companion
 * (`NpcAiManager.LANE_OFFSET`, `NpcAiManager.FEAR_SPEED_MULT`). Reutilizan extensiones de
 * `NpcAiManagerMovement.kt` (`moveAggroNpc`, `calculateDistance`, `pointToLineDist`).
 */

internal fun NpcAiManager.moveLocalNpc(npc: Npc): Npc? {
    val way = npc.currentLocalWay ?: return null
    val landmark = npc.currentLandmark ?: return null
    val navGraph = landmark.navGraph ?: return null

    val nodeIndex = npc.targetNodeIndex
    val direction = npc.moveDirection

    if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
        val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

        if (reachedNode.isParkingSlot) {
            return npc.copy(navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED, speed = 0.0)
        }

        if (navGraph.entryWays.contains(way.id) && nodeIndex < 0) {
            if (npc.type == NpcType.PERSON) return npc.copy(targetNodeIndex = 1, moveDirection = 1)

            carExitCooldowns[npc.id] = System.currentTimeMillis() + 60000L
            return npc.copy(
                navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM,
                currentLocalWay = null,
                currentLandmark = null,
                currentWay = null
            )
        }

        val connectedWays = navGraph.ways.filter { w ->
            w.id != way.id && w.nodes.size >= 2 && !w.nodes.any { it.isParkingSlot } &&
                    ((npc.type == NpcType.CAR && w.id < 200) || (npc.type == NpcType.PERSON && w.id >= 200)) &&
                    run {
                        var isNear = false
                        for (i in 0 until w.nodes.size - 1) {
                            val n1 = w.nodes[i]
                            val n2 = w.nodes[i+1]
                            val dist = pointToLineDist(
                                reachedNode.localX.toDouble(), reachedNode.localY.toDouble(),
                                n1.localX.toDouble(), n1.localY.toDouble(),
                                n2.localX.toDouble(), n2.localY.toDouble()
                            )
                            // 👇 FIX 1: Radar reducido a 0.015 para evitar que "brinquen"
                            if (dist < 0.015) { isNear = true; break }
                        }
                        isNear
                    }
        }

        if (connectedWays.isNotEmpty()) {
            val nextWay = connectedWays.random()

            // 👇 FIX 1b: Apuntar al nodo más cercano para seguir la línea y no pisar pasto
            var closestIdx = 0
            var minDist = Double.MAX_VALUE
            for (i in nextWay.nodes.indices) {
                val n = nextWay.nodes[i]
                val d = Math.pow(n.localX - reachedNode.localX.toDouble(), 2.0) +
                        Math.pow(n.localY - reachedNode.localY.toDouble(), 2.0)
                if (d < minDist) {
                    minDist = d
                    closestIdx = i
                }
            }

            val nextDir = when (closestIdx) {
                0 -> 1
                nextWay.nodes.size - 1 -> -1
                else -> if (Random.nextBoolean()) 1 else -1
            }

            val newTarget = (closestIdx + nextDir).coerceIn(0, nextWay.nodes.size - 1)

            return npc.copy(
                currentLocalWay = nextWay,
                targetNodeIndex = closestIdx, // Apunta exacto a la intersección para doblar bien
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
    val smoothFactor = if (npc.type == NpcType.CAR) 0.45f else 0.20f
    val smoothedAngle = (npc.rotationAngle + diff * smoothFactor + 360) % 360
    val actualSpeed = npc.speed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)
    // FIX "ángulo incorrecto" + anti-órbita (ver mover de calles): heading suavizado
    // solo con desvío pequeño; con desvío grande, directo al objetivo (converge siempre).
    val moveRad = if (npc.type == NpcType.CAR && Math.abs(diff) < 50f)
        Math.toRadians(-smoothedAngle.toDouble())
    else angle

    val isOnCooldown = parkingCooldowns[npc.id]?.let { System.currentTimeMillis() < it } ?: false

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
                        targetNodeIndex = 0,
                        moveDirection = 1
                    )
                }
            }
        }
    }

    return if (dist < actualSpeed) {
        val pauseTime = if (npc.type == NpcType.PERSON && Random.nextFloat() < 0.08f) {
            System.currentTimeMillis() + Random.nextLong(800, 1800)
        } else {
            npc.chatUntil
        }

        npc.copy(
            location = GeoPoint(targetGlobal.latitude, targetGlobal.longitude),
            targetNodeIndex = nodeIndex + direction,
            rotationAngle = smoothedAngle,
            facingRight = isFacingRight,
            chatUntil = pauseTime,
            isMoving = pauseTime <= System.currentTimeMillis()
        )
    } else {
        npc.copy(
            location = GeoPoint(
                npc.location.latitude + sin(moveRad) * actualSpeed,
                npc.location.longitude + cos(moveRad) * actualSpeed
            ),
            rotationAngle = smoothedAngle,
            facingRight = isFacingRight,
            isMoving = true
        )
    }
}

internal fun NpcAiManager.moveNpc(npc: Npc, network: List<MapWay>, now: Long, speedScale: Float): Npc? {
    if (npc.navState == ovh.gabrielhuav.pow.domain.models.map.NpcNavState.PARKED) {
        if (npc.currentLocalWay == null) return npc
        val wakeUpTime = parkedTimers[npc.id]

        if (wakeUpTime == null) {
            parkedTimers[npc.id] = System.currentTimeMillis() + Random.nextLong(PARKING_WAKE_MIN_MS, PARKING_WAKE_MAX_MS)
            return npc
        } else if (System.currentTimeMillis() > wakeUpTime) {
            parkedTimers.remove(npc.id)
            parkingCooldowns[npc.id] = System.currentTimeMillis() + 20000

            val way = npc.currentLocalWay ?: return null
            val newDir = npc.moveDirection * -1
            val newIndex = if (npc.targetNodeIndex < 0) 1 else way.nodes.size - 2

            return npc.copy(
                navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
                speed = carSpeed,
                moveDirection = newDir,
                targetNodeIndex = newIndex
            )
        }
        return npc
    }

    if (npc.navState == ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK) {
        return moveLocalNpc(npc)
    }

    if (npc.type == NpcType.PERSON && npc.aggroUntil > now) {
        return moveAggroNpc(npc)
    }

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

    // Decrementar compromiso de intersección cada tick
    val newCommitmentTicks = if (npc.commitmentTicks > 0) npc.commitmentTicks - 1 else 0

    if (nodeIndex < 0 || nodeIndex >= way.nodes.size) {
        val reachedNode = if (nodeIndex < 0) way.nodes.first() else way.nodes.last()

        // Si estamos comprometidos con esta vía, no re-evaluar intersecciones.
        // Simplemente corregimos el índice para que siga moviéndose.
        if (newCommitmentTicks > 0 && npc.committedWayId == way.id) {
            val fixedIndex = nodeIndex.coerceIn(0, way.nodes.size - 1)
            return npc.copy(targetNodeIndex = fixedIndex, commitmentTicks = newCommitmentTicks)
        }

        if (npc.type == NpcType.CAR) {
            for (landmark in cachedNavLandmarks.get()) {
                val navGraph = landmark.navGraph ?: continue
                if (navGraph.entryWays.isEmpty()) continue
                for (entryWayId in navGraph.entryWays) {
                    val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                    val entryNode = entryWay.nodes.first()
                    val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)
                    val distToEntry = calculateDistance(reachedNode.lat, reachedNode.lon, entryGlobal.latitude, entryGlobal.longitude)
                    if (distToEntry < 0.00010) {
                        val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                        val exitCooldown = carExitCooldowns[npc.id] ?: 0L

                        if (now > exitCooldown && now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
                            landmarkEntranceCooldowns[landmark.id.toString()] = now
                            return npc.copy(
                                navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
                                currentLandmark = landmark,
                                currentLocalWay = entryWay,
                                targetNodeIndex = 0,
                                moveDirection = 1,
                                currentWay = null,
                                commitmentTicks = 0
                            )
                        }
                    }
                }
            }
        }

        val connectedWays = (nodeToWays.get()[reachedNode.id] ?: emptyList()).filter { w ->
            w.id != way!!.id &&
                    ((npc.type == NpcType.CAR && w.isForCars) || (npc.type == NpcType.PERSON && w.isForPeople)) &&
                    !isNativeWayOverlappingCustom(w, activeLandmarks)
        }

        if (connectedWays.isNotEmpty()) {
            val nextWay: MapWay
            val newNodeIndex: Int
            var nextDir: Int
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

            // FIX: Asegurar que targetNodeIndex esté estrictamente dentro de los límites
            var finalTargetIndex = newNodeIndex + nextDir
            if (finalTargetIndex < 0 || finalTargetIndex >= nextWay.nodes.size) {
                nextDir = -nextDir
                finalTargetIndex = newNodeIndex + nextDir
            }
            // Si AÚN está fuera de límites (vía de 1 solo nodo), quedarse en la vía actual y reversar
            if (finalTargetIndex < 0 || finalTargetIndex >= nextWay.nodes.size) {
                val newDir = direction * -1
                val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
                return npc.copy(currentWay = way, targetNodeIndex = newIndex.coerceIn(0, way.nodes.size - 1), moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon), commitmentTicks = 0)
            }

            return npc.copy(currentWay = nextWay, targetNodeIndex = finalTargetIndex,
                moveDirection = nextDir, location = GeoPoint(reachedNode.lat, reachedNode.lon),
                committedWayId = nextWay.id, commitmentTicks = 15) // Compromiso de ~0.5s
        } else {
            val exitCooldown = carExitCooldowns[npc.id] ?: 0L
            if (now < exitCooldown) {
                return null
            }

            val newDir = direction * -1
            val newIndex = if (nodeIndex < 0) 1 else way.nodes.size - 2
            return npc.copy(currentWay = way, targetNodeIndex = newIndex.coerceIn(0, way.nodes.size - 1), moveDirection = newDir, location = GeoPoint(reachedNode.lat, reachedNode.lon), commitmentTicks = 0)
        }
    }

    val baseTarget = way.nodes[nodeIndex]
    var tLat: Double
    var tLon: Double
    if (npc.type == NpcType.CAR) {
        val segDLat = baseTarget.lat - npc.location.latitude
        val segDLon = baseTarget.lon - npc.location.longitude
        val segLen = sqrt(segDLat * segDLat + segDLon * segDLon)
        if (segLen > 0.00008) {
            val a = atan2(segDLat, segDLon)
            tLat = baseTarget.lat - cos(a) * NpcAiManager.LANE_OFFSET
            tLon = baseTarget.lon + sin(a) * NpcAiManager.LANE_OFFSET
        } else {
            tLat = baseTarget.lat
            tLon = baseTarget.lon
        }
    } else {
        tLat = baseTarget.lat
        tLon = baseTarget.lon
    }

    // ─── ESQUIVE DE TRÁFICO (jugador en mi trayectoria) ──────────────────
    // Geometría en el marco del PROPIO coche: si el jugador está delante (o aún
    // a un costado, hasta rebasarlo por completo) y dentro del ancho de mi
    // trayectoria, desplazo MI OBJETIVO un carril hacia el lado contrario. El
    // offset se recalcula cada tick a partir de la geometría, así que: abre →
    // pasa → se apaga solo → el smoothing lo regresa al carril. Nunca se toca
    // la posición del NPC (eso causaba las órbitas alrededor del jugador).
    var avoidingPlayer = false
    if (npc.type == NpcType.CAR) {
        val relLat = aggroPlayerLat - npc.location.latitude
        val relLon = aggroPlayerLon - npc.location.longitude
        val dPlayer = sqrt(relLat * relLat + relLon * relLon)
        if (dPlayer < TRAFFIC_AVOID_RADIUS && (aggroPlayerLat != 0.0 || aggroPlayerLon != 0.0)) {
            val dirLat0 = tLat - npc.location.latitude
            val dirLon0 = tLon - npc.location.longitude
            val dirLen = sqrt(dirLat0 * dirLat0 + dirLon0 * dirLon0)
            if (dirLen > 1e-9) {
                val fLat = dirLat0 / dirLen; val fLon = dirLon0 / dirLen   // hacia delante
                val pLat = -fLon; val pLon = fLat                          // perpendicular
                val ahead = relLat * fLat + relLon * fLon                  // + = jugador delante
                val side = relLat * pLat + relLon * pLon                   // de qué lado está
                // Activo desde que entra a mi trayectoria hasta que quede CLARAMENTE
                // atrás (histéresis TRAFFIC_AVOID_BEHIND): evita cerrarse encima del
                // jugador justo al pasarlo.
                if (ahead > -TRAFFIC_AVOID_BEHIND && kotlin.math.abs(side) < TRAFFIC_AVOID_PATH_HALF) {
                    val s = if (side >= 0) -1.0 else 1.0                   // abrir al lado contrario
                    // Más cerca = apertura más decidida (mín. 40% al borde del radio).
                    val strength = TRAFFIC_AVOID_OFFSET *
                        (1.0 - (dPlayer / TRAFFIC_AVOID_RADIUS)).coerceIn(0.4, 1.0)
                    // FIX "me atraviesan como fantasmas": el OBJETIVO de esquive debe ser
                    // LOCAL (un punto ~9 m adelante + un carril al lado). Desviar el NODO
                    // lejano (50-100 m) cambiaba el rumbo AQUÍ en ~2° — imperceptible, los
                    // coches seguían derecho a través del jugador. Con el objetivo local el
                    // cambio de rumbo es real (~20°): abre, rebasa y al apagarse el offset
                    // retoma su nodo y se reincorpora al carr0il.
                    tLat = npc.location.latitude + fLat * TRAFFIC_AVOID_LOOKAHEAD + pLat * s * strength
                    tLon = npc.location.longitude + fLon * TRAFFIC_AVOID_LOOKAHEAD + pLon * s * strength
                    avoidingPlayer = true
                }
            }
        }
    }

    val dLon = tLon - npc.location.longitude
    val dLat = tLat - npc.location.latitude
    val dist = sqrt(dLon * dLon + dLat * dLat)
    val angle = atan2(dLat, dLon)
    val targetAngle = -Math.toDegrees(angle).toFloat()
    val isFacingRight = cos(angle) >= 0

    val diff = (targetAngle - npc.rotationAngle + 540) % 360 - 180
    // Los coches giran más rápido (0.30 vs 0.20) para converger antes en esquinas.
    val smoothFactor = if (npc.type == NpcType.CAR) 0.45f else 0.20f
    val smoothedAngle = (npc.rotationAngle + diff * smoothFactor + 360) % 360
    val effectiveSpeed = npc.speed * speedScale.coerceIn(0f, 1f).toDouble() *
            (if (feared) NpcAiManager.FEAR_SPEED_MULT.toDouble() else 1.0) *
            (if (npc.type == NpcType.CAR) npc.speedVariation.toDouble() else 1.0)
    val actualSpeed = effectiveSpeed * (1.0f - (Math.abs(diff) / 60f).toFloat()).coerceIn(0.15f, 1.0f)
    val moving = actualSpeed > 1e-9
    // FIX "ángulo incorrecto" + FIX "círculos alrededor del jugador":
    // El sprite usa smoothedAngle. Mover SIEMPRE a lo largo del heading suavizado
    // (fix anterior) provocaba el bug clásico de pure-pursuit: con desvío grande y
    // radio de giro insuficiente, el coche ORBITA su objetivo para siempre (se veía
    // dando vueltas en círculos junto al jugador cuando el esquive movía su objetivo).
    // Ahora: con desvío PEQUEÑO (manejo normal) se mueve según su sprite (coinciden
    // visualmente); con desvío GRANDE (giros cerrados/esquives) avanza DIRECTO al
    // objetivo, que converge siempre — el sprite lo alcanza vía el smoothing.
    val moveRad = if (npc.type == NpcType.CAR && Math.abs(diff) < 50f)
        Math.toRadians(-smoothedAngle.toDouble())
    else angle

    if (dist > actualSpeed * 3 && npc.type == NpcType.CAR) {
        for (landmark in activeLandmarks) {
            val navGraph = landmark.navGraph ?: continue
            if (navGraph.entryWays.isEmpty()) continue

            for (entryWayId in navGraph.entryWays) {
                val entryWay = navGraph.ways.find { it.id == entryWayId } ?: continue
                val entryNode = entryWay.nodes.first()
                val entryGlobal = landmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

                val distToEntry = calculateDistance(npc.location.latitude, npc.location.longitude, entryGlobal.latitude, entryGlobal.longitude)

                if (distToEntry < 0.00010) {
                    val lastEntryTime = landmarkEntranceCooldowns[landmark.id.toString()] ?: 0L
                    val exitCooldown = carExitCooldowns[npc.id] ?: 0L

                    if (now > exitCooldown && now - lastEntryTime > 5000L && Random.nextFloat() < 0.85f) {
                        landmarkEntranceCooldowns[landmark.id.toString()] = now
                        return npc.copy(
                            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
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

    // 👇 FIX 2: Escudo protector anti-intrusos de calles OSM. Si tocan la escuela, se esfuman.
    if (activeLandmarks.any { it.contains(GeoPoint(tLat, tLon)) }) {
        return null
    }

    var canMove = true
    exteriorCollisions?.let { config ->
        for (poly in config.polygons) {
            if (poly.contains(tLat, tLon)) {
                canMove = false
                break
            }
        }
        if (canMove) {
            for (wall in config.walls) {
                if (wall.didHitWall(npc.location.latitude, npc.location.longitude, tLat, tLon)) {
                    canMove = false
                    break
                }
            }
        }
    }

    if (!canMove) {
        return npc.copy(speed = 0.0, isMoving = false)
    }

    // FIX "rara vez me rebasan / dan vueltas en círculos": cuando el coche esquiva
    // al jugador persigue un carrot LOCAL (~9 m), así que `dist` nunca baja de
    // actualSpeed y el `targetNodeIndex` NO avanzaba → al apagarse el esquive el
    // nodo base quedaba DETRÁS y el coche se daba la vuelta hacia el jugador
    // (bucle/órbita). Si mientras esquiva ya REBASÓ el nodo base (quedó detrás del
    // avance), avanzamos el índice: el coche sigue su ruta y te rebasa de verdad.
    val stepLat = sin(moveRad) * actualSpeed
    val stepLon = cos(moveRad) * actualSpeed
    val newLat = npc.location.latitude + stepLat
    val newLon = npc.location.longitude + stepLon
    val passedBaseNode = avoidingPlayer &&
        ((baseTarget.lat - newLat) * stepLat + (baseTarget.lon - newLon) * stepLon) < 0.0

    return if (dist < actualSpeed || passedBaseNode) {
        npc.copy(currentWay = way, location = if (dist < actualSpeed) GeoPoint(tLat, tLon) else GeoPoint(newLat, newLon), targetNodeIndex = nodeIndex + direction, moveDirection = direction, rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
    } else {
        npc.copy(currentWay = way, targetNodeIndex = nodeIndex, moveDirection = direction, location = GeoPoint(newLat, newLon), rotationAngle = smoothedAngle, facingRight = isFacingRight, isMoving = moving)
    }
}
