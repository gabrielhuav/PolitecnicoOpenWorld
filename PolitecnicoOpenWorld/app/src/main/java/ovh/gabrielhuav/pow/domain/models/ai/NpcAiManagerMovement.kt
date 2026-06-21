package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parcial de MOVIMIENTO / GEOMETRÍA de [NpcAiManager] (extraído para reducir el tamaño de la
 * clase; mismo paquete `ai`). Movers de zombi y policía cazador, IA aggro, seguimiento de
 * coches y utilidades de distancia. Son EXTENSIONES: solo tocan miembros `internal`/`public`
 * del manager (`serverNpcs`, `personSpeed`, `aggroPlayerLat/Lon`, `moveNpc`, consts de
 * instancia) y cualifican los miembros del companion (`NpcAiManager.speedMulForRole`,
 * `CAR_FOLLOW_DISTANCE`, `AGGRO_*`). `moveNpc`/`moveLocalNpc` siguen en la clase (núcleo).
 */

internal fun NpcAiManager.moveZombieNpc(
    npc: Npc,
    network: List<MapWay>,
    now: Long,
    playerLat: Double,
    playerLon: Double
): Npc? {
    if (npc.health <= 0f) {
        if (!npc.isDying) {
            return npc.copy(isDying = true, aggroUntil = now + HUMAN_CONVERT_DELAY_MS)
        }
        if (now > npc.aggroUntil) return null
        return npc
    }

    if (npc.zombieRole == ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT) {
        val nearestH = serverNpcs
            .filter { it.type == NpcType.PERSON && it.health > 0f && it.displayName.isNullOrEmpty() }
            .minByOrNull { calculateDistance(it.location.latitude, it.location.longitude, npc.location.latitude, npc.location.longitude) }
        if (nearestH == null) return moveNpc(npc, network, now, 0.5f)
        val dh = calculateDistance(nearestH.location.latitude, nearestH.location.longitude, npc.location.latitude, npc.location.longitude)
        val scoutScreamDist = 0.0002
        val scoutFleeMs = 4500L
        var newScream = npc.screamUntil
        if (now >= npc.screamUntil && dh <= scoutScreamDist) newScream = now + scoutFleeMs
        val fleeing = now < newScream
        val dLatT = if (fleeing) (npc.location.latitude - nearestH.location.latitude) else (nearestH.location.latitude - npc.location.latitude)
        val dLonT = if (fleeing) (npc.location.longitude - nearestH.location.longitude) else (nearestH.location.longitude - npc.location.longitude)
        val a = atan2(dLatT, dLonT)
        val sp = personSpeed * ZOMBIE_SPEED_MULT * NpcAiManager.speedMulForRole(ovh.gabrielhuav.pow.domain.models.map.ZombieRole.SCOUT)
        return npc.copy(
            location = GeoPoint(npc.location.latitude + sin(a) * sp, npc.location.longitude + cos(a) * sp),
            rotationAngle = (-Math.toDegrees(a).toFloat()),
            isMoving = true,
            facingRight = cos(a) >= 0,
            screamUntil = newScream,
            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM
        )
    }

    val distPlayer = calculateDistance(npc.location.latitude, npc.location.longitude, playerLat, playerLon)
    var targetLat = playerLat
    var targetLon = playerLon
    var bestScore = distPlayer * 0.45
    var realDist = distPlayer
    var targetIsHuman: Npc? = null
    val humans = serverNpcs.filter {
        (it.type == NpcType.PERSON || it.type == NpcType.POLICE_COP) && it.health > 0f && it.displayName.isNullOrEmpty()
    }

    for (h in humans) {
        val d = calculateDistance(npc.location.latitude, npc.location.longitude, h.location.latitude, h.location.longitude)
        if (d < bestScore) {
            bestScore = d
            realDist = d
            targetLat = h.location.latitude
            targetLon = h.location.longitude
            targetIsHuman = h
        }
    }

    if (realDist > ZOMBIE_VISION) {
        return moveNpc(npc, network, now, 0.5f)
    }

    if (targetIsHuman != null && realDist <= ZOMBIE_CONTACT_DIST) {
        if (now > npc.chatUntil) {
            val targetIndex = serverNpcs.indexOfFirst { it.id == targetIsHuman.id }
            if (targetIndex >= 0) {
                var h = serverNpcs[targetIndex]
                val newHealth = h.health - ZOMBIE_BITE_DAMAGE
                if (newHealth <= 0f) {
                    h = h.copy(health = 0f, isDying = true, fearUntil = now + HUMAN_CONVERT_DELAY_MS)
                } else {
                    h = h.copy(health = newHealth)
                }
                serverNpcs[targetIndex] = h
            }
            return npc.copy(chatUntil = now + ZOMBIE_BITE_COOLDOWN_MS)
        }
        return npc
    }

    val dLatForDir = targetLat - npc.location.latitude
    val dLonForDir = targetLon - npc.location.longitude
    val dir = kotlin.math.atan2(dLatForDir, dLonForDir)
    val effSpeed = personSpeed * ZOMBIE_SPEED_MULT * NpcAiManager.speedMulForRole(npc.zombieRole)
    val dLat = kotlin.math.sin(dir) * effSpeed
    val dLon = kotlin.math.cos(dir) * effSpeed

    val facingRight = kotlin.math.cos(dir) >= 0

    return npc.copy(
        location = GeoPoint(npc.location.latitude + dLat, npc.location.longitude + dLon),
        rotationAngle = -Math.toDegrees(dir).toFloat(),
        isMoving = true,
        facingRight = facingRight,
        navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM
    )
}

internal fun NpcAiManager.movePoliceHunter(npc: Npc, network: List<MapWay>, now: Long, playerLat: Double, playerLon: Double): Npc? {
    if (npc.health <= 0f) return null
    val provoked = npc.aggroUntil > now
    var targetLat: Double
    var targetLon: Double
    if (provoked) {
        targetLat = playerLat; targetLon = playerLon
    } else {
        val z = serverNpcs
            .filter { it.type == NpcType.ZOMBIE && it.health > 0f && !it.isDying }
            .minByOrNull { calculateDistance(it.location.latitude, it.location.longitude, npc.location.latitude, npc.location.longitude) }
        if (z == null) return moveNpc(npc, network, now, 0.5f)
        val dz = calculateDistance(z.location.latitude, z.location.longitude, npc.location.latitude, npc.location.longitude)
        if (dz <= POLICE_SHOOT_DIST) {
            if (now > npc.chatUntil) {
                val zi = serverNpcs.indexOfFirst { it.id == z.id }
                if (zi >= 0) {
                    val nh = serverNpcs[zi].health - POLICE_SHOOT_DAMAGE
                    serverNpcs[zi] = if (nh <= 0f) serverNpcs[zi].copy(health = 0f, isDying = true)
                    else serverNpcs[zi].copy(health = nh)
                }
                synchronized(pendingPoliceShots) { pendingPoliceShots.add(npc.location to z.location) }
                val a0 = atan2(z.location.latitude - npc.location.latitude, z.location.longitude - npc.location.longitude)
                return npc.copy(chatUntil = now + POLICE_SHOOT_COOLDOWN_MS,
                    rotationAngle = (-Math.toDegrees(a0).toFloat() + 360) % 360,
                    facingRight = cos(a0) >= 0, isMoving = false)
            }
            return npc
        }
        targetLat = z.location.latitude; targetLon = z.location.longitude
    }
    val dir = atan2(targetLat - npc.location.latitude, targetLon - npc.location.longitude)
    val sp = personSpeed * POLICE_SPEED_MULT
    return npc.copy(
        location = GeoPoint(npc.location.latitude + sin(dir) * sp, npc.location.longitude + cos(dir) * sp),
        rotationAngle = (-Math.toDegrees(dir).toFloat() + 360) % 360,
        isMoving = true,
        facingRight = cos(dir) >= 0,
        navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MACRO_OSM
    )
}

internal fun NpcAiManager.carFollowScale(car: Npc, cars: List<Npc>): Float {
    val way = car.currentWay ?: return 1f
    val ti = car.targetNodeIndex
    if (ti < 0 || ti >= way.nodes.size) return 1f
    val target = way.nodes[ti]
    val fwd = atan2(target.lat - car.location.latitude, target.lon - car.location.longitude)
    var minAhead = Double.MAX_VALUE
    for (other in cars) {
        if (other.id == car.id) continue
        val headDiff = Math.abs(((other.rotationAngle - car.rotationAngle + 540f) % 360f) - 180f)
        if (headDiff > 90f) continue
        val dLat = other.location.latitude - car.location.latitude
        val dLon = other.location.longitude - car.location.longitude
        val d = sqrt(dLat * dLat + dLon * dLon)
        if (d > NpcAiManager.CAR_FOLLOW_DISTANCE) continue
        val ang = atan2(dLat, dLon)
        val diff = Math.abs(((Math.toDegrees(ang - fwd) + 540) % 360) - 180)
        if (diff < 45 && d < minAhead) minAhead = d
    }
    if (minAhead == Double.MAX_VALUE) return 1f
    // Frenado de emergencia si está MUY cerca (< ~8m)
    if (minAhead < 0.00008) return 0.1f
    return (minAhead / NpcAiManager.CAR_FOLLOW_DISTANCE).toFloat().coerceIn(0.35f, 1f)
}

internal fun NpcAiManager.pointToLineDist(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
    val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
    if (l2 == 0.0) return kotlin.math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
    val t = maxOf(0.0, minOf(1.0, ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2))
    val projX = x1 + t * (x2 - x1)
    val projY = y1 + t * (y2 - y1)
    return kotlin.math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
}

internal fun NpcAiManager.moveAggroNpc(npc: Npc): Npc {
    val dLat = aggroPlayerLat - npc.location.latitude
    val dLon = aggroPlayerLon - npc.location.longitude
    val dist = sqrt(dLat * dLat + dLon * dLon)
    val angle = atan2(dLat, dLon)
    val targetAngle = (-Math.toDegrees(angle).toFloat() + 360) % 360
    val facing = cos(angle) >= 0
    if (dist <= NpcAiManager.AGGRO_STOP_DIST) {
        return npc.copy(isMoving = false, rotationAngle = targetAngle, facingRight = facing)
    }
    val speed = personSpeed * NpcAiManager.AGGRO_SPEED_MULT
    return npc.copy(
        location = GeoPoint(
            npc.location.latitude + sin(angle) * speed,
            npc.location.longitude + cos(angle) * speed
        ),
        rotationAngle = targetAngle,
        facingRight = facing,
        isMoving = true
    )
}

internal fun NpcAiManager.calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = lat1 - lat2
    val dLon = (lon1 - lon2) * cos(lat1 * Math.PI / 180)
    return sqrt(dLat * dLat + dLon * dLon)
}
