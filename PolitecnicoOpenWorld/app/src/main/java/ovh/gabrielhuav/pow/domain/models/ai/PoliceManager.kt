package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
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
        const val COP_SPEED = 0.0000038        // por tick: caminan algo lento → escapas a pie
        const val COP_STOP_DIST = 0.000018
        const val PUNCH_DIST = 0.00003         // ~3 m: solo te pegan si están JUSTO a tu lado
        const val PUNCH_DAMAGE = 5f
        const val PUNCH_COOLDOWN_MS = 1400L
        const val SHOOT_FRAME_MS = 1000L         // Duración del frame de disparo (quieto)
        const val SHOOT_RANGE = 0.0002         // ~22 m: disparan solo si están bastante cerca
        const val SHOOT_DAMAGE = 4f
        const val SHOOT_COOLDOWN_MS = 2400L
        const val CAR_SPAWN_COOLDOWN_MS = 3000L
        const val ADJACENT_DIST = 0.00009      // ~10 m: policía pegado a tu auto (carjack)
        const val BOARD_DIST = 0.0001          // ~11 m: el policía llegó a su patrulla y se sube
        const val RECALL_DIST = 0.0006         // ~65 m: si te alejas tanto, el policía vuelve al coche
        const val RETREAT_DESPAWN = 0.0026     // ~290 m: al retirarse, desaparecen pasando esto

        // ─── Pathfinding de patrullas (rutas por la red de calles) ───────────────
        const val ROUTE_TTL_MS = 1500L         // recalcular la ruta hacia ti cada ~1.5 s
        const val STUCK_TIME_MS = 1500L        // si no avanza 1.5 s, se considera atascada
        const val STUCK_EPS = 0.00002          // ~2 m: umbral para detectar que no se movió
        const val WAYPOINT_REACH = 0.00012     // ~13 m: distancia para pasar al siguiente nodo
        const val PROGRESS_CHECK_MS = 6000L    // cada cuánto se evalúa si AVANZA hacia ti
        const val MIN_PROGRESS = 0.00025       // ~28 m: si no se acercó al menos esto, da vueltas
        const val DETOUR_DIST = 0.0013         // ~145 m: a qué distancia se manda el desvío

        fun desiredCarsFor(wantedLevel: Int): Int = when (wantedLevel) {
            0 -> 0; 1 -> 1; 2 -> 2; 3 -> 4; 4 -> 6; else -> MAX_CARS
        }
    }

    data class PoliceTick(
        val units: List<Npc>,
        val damage: Float,
        val prankedyDamage: Float = 0f,
        val impact: Boolean,
        val destroyedIds: List<String>,
        val adjacentThreat: Boolean,  // policía a pie pegado a tu auto → posible carjack
        val shots: List<Pair<GeoPoint, GeoPoint>> = emptyList() // disparos (origen→jugador) para dibujar la bala
    )

    private val units = ConcurrentHashMap<String, Npc>()
    private val punchCooldowns = ConcurrentHashMap<String, Long>()
    private val shootCooldowns = ConcurrentHashMap<String, Long>()
    @Volatile private var lastCarSpawn = 0L

    // Estado de ruta por patrulla (pathfinding sobre la red de calles).
    private val carRoute = ConcurrentHashMap<String, List<GeoPoint>>()
    private val carRouteIdx = ConcurrentHashMap<String, Int>()
    private val carRouteTime = ConcurrentHashMap<String, Long>()
    private val carStuckPos = ConcurrentHashMap<String, GeoPoint>()
    private val carStuckSince = ConcurrentHashMap<String, Long>()
    // Detección de "dar vueltas sin progresar" + desvío forzado por otra ruta.
    private val progressDist = ConcurrentHashMap<String, Double>()
    private val progressTime = ConcurrentHashMap<String, Long>()
    private val detour = ConcurrentHashMap<String, GeoPoint>()

    private fun forgetCar(id: String) {
        carRoute.remove(id); carRouteIdx.remove(id); carRouteTime.remove(id)
        carStuckPos.remove(id); carStuckSince.remove(id)
        progressDist.remove(id); progressTime.remove(id); detour.remove(id)
    }

    fun activeUnits(): List<Npc> = units.values.toList()

    // El jugador golpea: daña a los policías a pie dentro del radio. Devuelve los ids de
    // los que cayeron (para difundir POLICE_DESTROY). Las patrullas no se dañan a golpes.
    fun playerHitPolice(lat: Double, lon: Double, radius: Double, damage: Float): List<String> {
        val destroyed = ArrayList<String>()
        for (u in units.values.toList()) {
            if (u.type != NpcType.POLICE_COP) continue
            if (dist(u.location.latitude, u.location.longitude, lat, lon) > radius) continue
            val nh = (u.health - damage).coerceAtLeast(0f)
            if (nh <= 0f) { units.remove(u.id); destroyed.add(u.id) }
            else units[u.id] = u.copy(health = nh)
        }
        return destroyed
    }

    // El jugador SE SUBE a una patrulla: la quitamos de las unidades activas y la
    // devolvemos para que el VM la convierta en su vehículo. Devuelve null si el id no
    // es una patrulla (POLICE_CAR). El VM debe difundir POLICE_DESTROY con ese id.
    fun boardPatrol(id: String): Npc? {
        val u = units[id] ?: return null
        if (u.type != NpcType.POLICE_CAR) return null
        units.remove(id)
        punchCooldowns.remove(id); shootCooldowns.remove(id)
        forgetCar(id)
        return u
    }

    fun clearAll(): List<String> {
        val ids = units.keys.toList()
        units.clear(); punchCooldowns.clear(); shootCooldowns.clear()
        carRoute.clear(); carRouteIdx.clear(); carRouteTime.clear()
        carStuckPos.clear(); carStuckSince.clear()
        return ids
    }

    // Avanza una unidad (patrulla o policía) hacia un objetivo siguiendo una RUTA por la
    // red de calles (pathfinding), recalculada periódicamente. Si se atasca (no avanza en
    // STUCK_TIME_MS) va DIRECTO al objetivo sin snap durante un momento para despegarse, y
    // recalcula la ruta — así nunca se queda pegada pero tampoco vaga por los edificios.
    // Sigue la RUTA del A* como una polilínea, SIN snap: los nodos de la ruta ya están
    // sobre las calles y los tramos entre nodos consecutivos son segmentos de calle
    // reales, así que avanzar en línea de nodo a nodo mantiene a la unidad SOBRE la calle
    // y SIEMPRE progresa hacia ti (no se atasca ni oscila por el ajuste a la calle).
    private fun advanceAlong(
        unit: Npc, tLat: Double, tLon: Double, speed: Double, now: Long,
        snap: ((GeoPoint) -> GeoPoint)?, pathfind: ((GeoPoint, GeoPoint) -> List<GeoPoint>)?
    ): Triple<GeoPoint, Float, Boolean> {
        val id = unit.id

        // (Re)calcular la ruta si no hay, está vacía, o caducó (el objetivo se mueve).
        val route0 = carRoute[id]
        val needRoute = pathfind != null &&
            (route0 == null || route0.isEmpty() || now - (carRouteTime[id] ?: 0L) > ROUTE_TTL_MS)
        if (needRoute && pathfind != null) {
            carRoute[id] = pathfind(unit.location, GeoPoint(tLat, tLon))
            carRouteIdx[id] = 0
            carRouteTime[id] = now
        }

        val route = carRoute[id]
        if (route == null || route.size < 3) {
            // Sin ruta real del A* (objetivo sin conexión): nos pegamos a la calle con snap
            // para NO atravesar edificios mientras nos acercamos en línea.
            return stepOnRoad(unit.location, tLat, tLon, speed, snap)
        }

        // Avanza el cursor saltando los nodos ya alcanzados (deja el último como meta final).
        val reach = maxOf(WAYPOINT_REACH, speed * 4.0)
        var idx = (carRouteIdx[id] ?: 0).coerceIn(0, route.size - 1)
        while (idx < route.size - 1 &&
            dist(unit.location.latitude, unit.location.longitude, route[idx].latitude, route[idx].longitude) < reach) {
            idx++
        }
        carRouteIdx[id] = idx
        val wp = route[idx]
        // Apuntamos al SIGUIENTE nodo de la ruta (que ya está sobre la calle) y además
        // hacemos snap: como el objetivo va por la calle, el snap mantiene a la unidad
        // SOBRE el asfalto (no vuela edificios) sin el rebote/atasco de antes.
        return stepOnRoad(unit.location, wp.latitude, wp.longitude, speed, snap)
    }

    // Avanza desde 'from' hacia (toLat,toLon) a 'speed', ajustando el resultado a la calle
    // más cercana (snap). Devuelve la nueva posición y el ángulo de avance (convención NPC).
    private fun stepOnRoad(
        from: GeoPoint, toLat: Double, toLon: Double, speed: Double,
        snap: ((GeoPoint) -> GeoPoint)?
    ): Triple<GeoPoint, Float, Boolean> {
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
        val facing = if (mLat * mLat + mLon * mLon > 1e-14) mLon >= 0 else cos(a) >= 0
        return Triple(placed, rot, facing)
    }

    fun update(
        playerLat: Double,
        playerLon: Double,
        roadNetwork: List<MapWay>,
        wantedLevel: Int,
        canShoot: Boolean,
        playerInVehicle: Boolean,
        now: Long,
        snap: ((GeoPoint) -> GeoPoint)? = null,
        pathfind: ((GeoPoint, GeoPoint) -> List<GeoPoint>)? = null,
        prankedyLoc: GeoPoint? = null,
        isPrankedyFighting: Boolean = false
    ): PoliceTick {
        // ─── RETIRADA ───────────────────────────────────────────────────────────
        if (wantedLevel <= 0) {
            if (units.isEmpty()) return PoliceTick(emptyList(), 0f, 0f, false, emptyList(), false)
            val destroyed = ArrayList<String>()
            for (unit in units.values.toList()) {
                val dLat = unit.location.latitude - playerLat
                val dLon = unit.location.longitude - playerLon
                if (sqrt(dLat * dLat + dLon * dLon) > RETREAT_DESPAWN) {
                    units.remove(unit.id); forgetCar(unit.id); destroyed.add(unit.id); continue
                }
                // Objetivo: un punto lejos del jugador (en su misma dirección de huida).
                val spd = if (unit.type == NpcType.POLICE_CAR) CAR_SPEED else COP_SPEED
                val awayLat = unit.location.latitude + (unit.location.latitude - playerLat)
                val awayLon = unit.location.longitude + (unit.location.longitude - playerLon)
                val (loc, rot, facing) = stepOnRoad(unit.location, awayLat, awayLon, spd, snap)
                units[unit.id] = unit.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
            }
            return PoliceTick(units.values.toList(), 0f, 0f, false, destroyed, false)
        }

        val destroyed = ArrayList<String>()
        var damage = 0f
        var prankedyDamage = 0f
        var impact = false
        var adjacent = false
        val shots = ArrayList<Pair<GeoPoint, GeoPoint>>()

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
                        // Persecución con RUTA por las calles (pathfinding + anti-atasco).
                        val (loc, rot, facing) = advanceAlong(unit, playerLat, playerLon, CAR_SPEED, now, snap, pathfind)
                        units[unit.id] = unit.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
                    }
                }

                NpcType.POLICE_COP -> {
                    val lastShoot = shootCooldowns[unit.id] ?: 0L
                    val isShootingFrame = now - lastShoot < SHOOT_FRAME_MS

                    // VOLVIENDO A LA PATRULLA (te alejaste en coche).
                    if (unit.policeReturning) {
                        val car = units[unit.policeCarId]
                        if (car == null) { units.remove(unit.id); destroyed.add(unit.id) }
                        else {
                            val cDist = dist(unit.location.latitude, unit.location.longitude,
                                car.location.latitude, car.location.longitude)
                            if (cDist <= BOARD_DIST) { units.remove(unit.id); destroyed.add(unit.id) }
                            else {
                                // Vuelven a la patrulla por la calle (ruta + anti-atasco).
                                val (loc, rot, facing) = advanceAlong(unit,
                                    car.location.latitude, car.location.longitude, COP_SPEED, now, snap, pathfind)
                                units[unit.id] = unit.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
                            }
                        }
                        continue
                    }

                    val dist = dist(unit.location.latitude, unit.location.longitude, playerLat, playerLon)
                    
                    if (isShootingFrame) {
                        // Mientras dura el frame de disparo, se queda quieto mirando al jugador.
                        val a = atan2(playerLat - unit.location.latitude, playerLon - unit.location.longitude)
                        val rot = (-Math.toDegrees(a).toFloat() + 360f) % 360f
                        units[unit.id] = unit.copy(rotationAngle = rot, facingRight = cos(a) >= 0, isMoving = false)
                    } else if (dist > COP_STOP_DIST) {
                        // Persiguen por la calle (ruta + anti-atasco): no atraviesan edificios
                        // pero tampoco se quedan trabados.
                        val (loc, rot, facing) = advanceAlong(unit, playerLat, playerLon, COP_SPEED, now, snap, pathfind)
                        units[unit.id] = unit.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
                    } else {
                        units[unit.id] = unit.copy(isMoving = false)
                    }

                    if (playerInVehicle) {
                        // En coche NO te golpean: si están pegados a tu auto, te bajan (carjack).
                        if (dist <= ADJACENT_DIST) adjacent = true
                    } else {
                        val prankedyIsTarget = isPrankedyFighting && prankedyLoc != null && dist(unit.location.latitude, unit.location.longitude, prankedyLoc.latitude, prankedyLoc.longitude) <= PUNCH_DIST * 1.5
                        if (dist <= PUNCH_DIST) {
                            val last = punchCooldowns[unit.id] ?: 0L
                            if (now - last >= PUNCH_COOLDOWN_MS) {
                                punchCooldowns[unit.id] = now
                                if (prankedyIsTarget) { prankedyDamage += PUNCH_DAMAGE } else { damage += PUNCH_DAMAGE }
                                impact = true
                            }
                        }
                        // canShoot es el nivel ACTUAL (≥3 estrellas), no el que tenía al
                        // aparecer: así no te disparan al bajar de estrellas ni con 1★.
                        val prankedyIsShootTarget = isPrankedyFighting && prankedyLoc != null && dist(unit.location.latitude, unit.location.longitude, prankedyLoc.latitude, prankedyLoc.longitude) <= SHOOT_RANGE
                        if (canShoot && dist > PUNCH_DIST && dist <= SHOOT_RANGE) {
                            val last = shootCooldowns[unit.id] ?: 0L
                            if (now - last >= SHOOT_COOLDOWN_MS) {
                                shootCooldowns[unit.id] = now
                                if (prankedyIsShootTarget) { prankedyDamage += SHOOT_DAMAGE } else { damage += SHOOT_DAMAGE }
                                impact = true
                                shots.add(unit.location to GeoPoint(playerLat, playerLon)) // bala visible
                                
                                // Forzar que se quede quieto y mire al jugador al disparar
                                val a = atan2(playerLat - unit.location.latitude, playerLon - unit.location.longitude)
                                val rot = (-Math.toDegrees(a).toFloat() + 360f) % 360f
                                units[unit.id] = units[unit.id]!!.copy(rotationAngle = rot, facingRight = cos(a) >= 0, isMoving = false)
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        return PoliceTick(units.values.toList(), damage, prankedyDamage, impact, destroyed, adjacent, shots)
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
