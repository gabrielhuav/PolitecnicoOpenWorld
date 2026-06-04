package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// SISTEMA DE POLICÍA (nivel de búsqueda estilo GTA).
//
// La simula SIEMPRE el jugador que tiene el nivel de búsqueda (debe perseguirlo a ÉL).
// Sus unidades se difunden para que los demás clientes las vean (solo render).
//
// Las unidades RESPETAN LAS CALLES: cada paso se ajusta (snap) al punto más cercano de la
// red de carreteras, así no vuelan entre edificios.
//
// Flujo:
//  - A PIE: la patrulla llega cerca, se detiene y suelta 2-3 policías (emoji) que te
//    persiguen y golpean; con 2+ estrellas además disparan.
//  - EN AUTO: las patrullas te persiguen en coche; si te alcanzan, sueltan policías que
//    corren a tu coche para bajarte (si te quedas quieto). Si te alejas, vuelven a su
//    patrulla y siguen la persecución en auto.
//  - AL MORIR (wantedLevel = 0): se retiran alejándose hasta desaparecer.
class PoliceManager {

    companion object {
        const val MAX_CARS = 8
        const val SPAWN_RING = 0.0024          // ~265 m: aparecen claramente lejos
        const val ARRIVE_DIST = 0.00045        // ~50 m: al llegar (a pie) se bajan los policías
        const val CAR_SPEED = 0.0000185        // por tick (apenas más rápido que tu auto)
        const val COP_SPEED = 0.0000044        // por tick: más lento que correr → escapas a pie
        const val COP_STOP_DIST = 0.000018
        const val PUNCH_DIST = 0.00006
        const val PUNCH_DAMAGE = 5f
        const val PUNCH_COOLDOWN_MS = 1400L
        const val SHOOT_RANGE = 0.0007
        const val SHOOT_DAMAGE = 4f
        const val SHOOT_COOLDOWN_MS = 2400L
        const val CAR_SPAWN_COOLDOWN_MS = 3000L
        const val ADJACENT_DIST = 0.00009      // ~10 m: policía pegado a tu auto (carjack)
        const val BOARD_DIST = 0.0001          // ~11 m: el policía llegó a su patrulla y se sube
        const val RECALL_DIST = 0.0006         // ~65 m: si te alejas tanto, el policía vuelve al coche
        const val RETREAT_DESPAWN = 0.0026     // ~290 m: al retirarse, desaparecen pasando esto

        fun desiredCarsFor(wantedLevel: Int): Int = when (wantedLevel) {
            0 -> 0; 1 -> 1; 2 -> 2; 3 -> 4; 4 -> 6; else -> MAX_CARS
        }
    }

    data class PoliceTick(
        val units: List<Npc>,
        val damage: Float,
        val impact: Boolean,
        val destroyedIds: List<String>,
        val adjacentThreat: Boolean   // policía a pie pegado a tu auto → posible carjack
    )

    private val units = ConcurrentHashMap<String, Npc>()
    private val punchCooldowns = ConcurrentHashMap<String, Long>()
    private val shootCooldowns = ConcurrentHashMap<String, Long>()
    @Volatile private var lastCarSpawn = 0L

    fun activeUnits(): List<Npc> = units.values.toList()

    fun clearAll(): List<String> {
        val ids = units.keys.toList()
        units.clear(); punchCooldowns.clear(); shootCooldowns.clear()
        return ids
    }

    // Avanza desde 'from' hacia (toLat,toLon) a 'speed', ajustando el resultado a la calle
    // más cercana (snap). Devuelve la nueva posición y el ángulo de avance (convención NPC).
    private fun stepOnRoad(
        from: GeoPoint, toLat: Double, toLon: Double, speed: Double,
        snap: ((GeoPoint) -> GeoPoint)?
    ): Pair<GeoPoint, Float> {
        val dLat = toLat - from.latitude
        val dLon = toLon - from.longitude
        val a = atan2(dLat, dLon)
        val raw = GeoPoint(from.latitude + sin(a) * speed, from.longitude + cos(a) * speed)
        val placed = snap?.invoke(raw) ?: raw
        val mLat = placed.latitude - from.latitude
        val mLon = placed.longitude - from.longitude
        val rot = if (mLat * mLat + mLon * mLon > 1e-14)
            (-Math.toDegrees(atan2(mLat, mLon)).toFloat() + 360f) % 360f
        else (-Math.toDegrees(a).toFloat() + 360f) % 360f
        return placed to rot
    }

    fun update(
        playerLat: Double,
        playerLon: Double,
        roadNetwork: List<MapWay>,
        wantedLevel: Int,
        canShoot: Boolean,
        playerInVehicle: Boolean,
        now: Long,
        snap: ((GeoPoint) -> GeoPoint)? = null
    ): PoliceTick {
        // ─── RETIRADA ───────────────────────────────────────────────────────────
        if (wantedLevel <= 0) {
            if (units.isEmpty()) return PoliceTick(emptyList(), 0f, false, emptyList(), false)
            val destroyed = ArrayList<String>()
            for (unit in units.values.toList()) {
                val dLat = unit.location.latitude - playerLat
                val dLon = unit.location.longitude - playerLon
                if (sqrt(dLat * dLat + dLon * dLon) > RETREAT_DESPAWN) {
                    units.remove(unit.id); destroyed.add(unit.id); continue
                }
                // Objetivo: un punto lejos del jugador (en su misma dirección de huida).
                val spd = if (unit.type == NpcType.POLICE_CAR) CAR_SPEED else COP_SPEED
                val awayLat = unit.location.latitude + (unit.location.latitude - playerLat)
                val awayLon = unit.location.longitude + (unit.location.longitude - playerLon)
                val (loc, rot) = stepOnRoad(unit.location, awayLat, awayLon, spd, snap)
                units[unit.id] = unit.copy(location = loc, rotationAngle = rot, isMoving = true)
            }
            return PoliceTick(units.values.toList(), 0f, false, destroyed, false)
        }

        val destroyed = ArrayList<String>()
        var damage = 0f
        var impact = false
        var adjacent = false

        // ─── ESTADO DE LOS POLICÍAS SEGÚN TU VEHÍCULO ────────────────────────────
        // En coche: si te alejas mucho de un policía a pie, vuelve a su patrulla; si está
        // cerca, se queda fuera para bajarte. A pie: nunca están "volviendo".
        for (u in units.values.toList()) {
            if (u.type != NpcType.POLICE_COP) continue
            val d = dist(u.location.latitude, u.location.longitude, playerLat, playerLon)
            val shouldReturn = playerInVehicle && d > RECALL_DIST
            if (shouldReturn != u.policeReturning) units[u.id] = u.copy(policeReturning = shouldReturn)
        }

        // ─── SPAWN de patrullas ──────────────────────────────────────────────────
        val desiredCars = desiredCarsFor(wantedLevel)
        val carCount = units.values.count { it.type == NpcType.POLICE_CAR }
        if (carCount < desiredCars && now - lastCarSpawn >= CAR_SPAWN_COOLDOWN_MS) {
            lastCarSpawn = now
            repeat((desiredCars - carCount).coerceAtMost(2)) {
                spawnPatrol(playerLat, playerLon, roadNetwork, canShoot)?.let { units[it.id] = it }
            }
        }

        // ─── MOVIMIENTO / ACCIÓN ─────────────────────────────────────────────────
        for (unit in units.values.toList()) {
            when (unit.type) {
                NpcType.POLICE_CAR -> {
                    val dist = dist(unit.location.latitude, unit.location.longitude, playerLat, playerLon)

                    if (unit.policeDisembarked) {
                        // Patrulla con policías fuera.
                        if (!playerInVehicle) continue // a pie: queda estacionada
                        // En coche: espera mientras sus policías sigan fuera; cuando todos
                        // se subieron, vuelve a arrancar para perseguirte en auto.
                        val pendingCops = units.values.any { it.type == NpcType.POLICE_COP && it.policeCarId == unit.id }
                        if (pendingCops) continue
                        units[unit.id] = unit.copy(policeDisembarked = false, isMoving = true)
                    }

                    // ¿Toca soltar policías? (a pie al llegar; en coche al pegarse a ti).
                    val dropDist = if (playerInVehicle) ADJACENT_DIST else ARRIVE_DIST
                    if (dist <= dropDist) {
                        units[unit.id] = unit.copy(policeDisembarked = true, isMoving = false)
                        val numCops = Random.nextInt(2, 4)
                        repeat(numCops) { i -> makeCop(unit, i, unit.policeCanShoot).let { units[it.id] = it } }
                    } else {
                        val (loc, rot) = stepOnRoad(unit.location, playerLat, playerLon, CAR_SPEED, snap)
                        units[unit.id] = unit.copy(location = loc, rotationAngle = rot, isMoving = true)
                    }
                }

                NpcType.POLICE_COP -> {
                    // VOLVIENDO A LA PATRULLA (te alejaste en coche).
                    if (unit.policeReturning) {
                        val car = units[unit.policeCarId]
                        if (car == null) { units.remove(unit.id); destroyed.add(unit.id) }
                        else {
                            val cDist = dist(unit.location.latitude, unit.location.longitude,
                                car.location.latitude, car.location.longitude)
                            if (cDist <= BOARD_DIST) { units.remove(unit.id); destroyed.add(unit.id) }
                            else {
                                val (loc, rot) = stepOnRoad(unit.location,
                                    car.location.latitude, car.location.longitude, COP_SPEED, snap)
                                units[unit.id] = unit.copy(location = loc, rotationAngle = rot, isMoving = true)
                            }
                        }
                        continue
                    }

                    val dist = dist(unit.location.latitude, unit.location.longitude, playerLat, playerLon)
                    if (dist > COP_STOP_DIST) {
                        val (loc, rot) = stepOnRoad(unit.location, playerLat, playerLon, COP_SPEED, snap)
                        units[unit.id] = unit.copy(location = loc, rotationAngle = rot, isMoving = true)
                    } else {
                        units[unit.id] = unit.copy(isMoving = false)
                    }

                    if (playerInVehicle) {
                        // En coche NO te golpean: si están pegados a tu auto, te bajan (carjack).
                        if (dist <= ADJACENT_DIST) adjacent = true
                    } else {
                        if (dist <= PUNCH_DIST) {
                            val last = punchCooldowns[unit.id] ?: 0L
                            if (now - last >= PUNCH_COOLDOWN_MS) {
                                punchCooldowns[unit.id] = now; damage += PUNCH_DAMAGE; impact = true
                            }
                        }
                        if (unit.policeCanShoot && dist > PUNCH_DIST && dist <= SHOOT_RANGE) {
                            val last = shootCooldowns[unit.id] ?: 0L
                            if (now - last >= SHOOT_COOLDOWN_MS) {
                                shootCooldowns[unit.id] = now; damage += SHOOT_DAMAGE; impact = true
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        return PoliceTick(units.values.toList(), damage, impact, destroyed, adjacent)
    }

    private fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2; val dLon = lon1 - lon2
        return sqrt(dLat * dLat + dLon * dLon)
    }

    private fun spawnPatrol(
        playerLat: Double, playerLon: Double, roadNetwork: List<MapWay>, canShoot: Boolean
    ): Npc? {
        val ang = Random.nextDouble(0.0, 2.0 * Math.PI)
        val candLat = playerLat + sin(ang) * SPAWN_RING
        val candLon = playerLon + cos(ang) * SPAWN_RING

        var bestLat = candLat; var bestLon = candLon; var bestDist = Double.MAX_VALUE
        for (way in roadNetwork) {
            if (!way.isForCars) continue
            for (n in way.nodes) {
                val d = (n.lat - candLat) * (n.lat - candLat) + (n.lon - candLon) * (n.lon - candLon)
                if (d < bestDist) { bestDist = d; bestLat = n.lat; bestLon = n.lon }
            }
        }

        val id = "POLICE_CAR_${System.currentTimeMillis()}_${Random.nextInt(10000)}"
        return Npc(
            id = id, type = NpcType.POLICE_CAR, location = GeoPoint(bestLat, bestLon),
            speed = CAR_SPEED, isRemote = false, policeCanShoot = canShoot
        )
    }

    private fun makeCop(car: Npc, index: Int, canShoot: Boolean): Npc {
        val off = 0.00004
        val a = index * (2.0 * Math.PI / 3.0)
        val id = "POLICE_COP_${car.id}_${index}_${Random.nextInt(10000)}"
        return Npc(
            id = id, type = NpcType.POLICE_COP,
            location = GeoPoint(car.location.latitude + sin(a) * off, car.location.longitude + cos(a) * off),
            speed = COP_SPEED, isRemote = false, isMoving = true,
            policeDisembarked = true, policeCanShoot = canShoot, policeCarId = car.id
        )
    }
}
