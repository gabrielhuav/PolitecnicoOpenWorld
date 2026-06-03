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
// A diferencia de los NPCs civiles (que simula el host de zona), la policía la simula
// SIEMPRE el jugador que tiene el nivel de búsqueda, porque debe perseguirlo a ÉL en
// concreto. Sus unidades se difunden por la red para que los demás clientes las VEAN
// (solo render), pero el dueño es la autoridad de su movimiento y del daño que recibe.
//
// Flujo: al subir el nivel de búsqueda aparecen patrullas LEJOS del jugador y conducen
// hacia él. Al llegar a su lado se detienen y "sueltan" de 2 a 3 policías (emoji) que lo
// persiguen y golpean. Con 2+ estrellas, además le disparan a distancia.
class PoliceManager {

    companion object {
        const val MAX_CARS = 8                 // tope duro de patrullas simultáneas
        const val SPAWN_RING = 0.0008          // ~90 m: aparecen cerca (justo al borde del fog)
        const val ARRIVE_DIST = 0.00028        // ~28 m: al llegar, los policías se bajan
        const val CAR_SPEED = 0.000021         // por tick (algo más rápido que el jugador al volante)
        const val COP_SPEED = 0.0000105        // por tick (a pie, persecución agresiva)
        const val COP_STOP_DIST = 0.000018     // se planta frente al jugador
        const val PUNCH_DIST = 0.00006         // ~6 m: alcance del golpe
        const val PUNCH_DAMAGE = 9f
        const val PUNCH_COOLDOWN_MS = 900L
        const val SHOOT_RANGE = 0.0007         // ~70 m: alcance del disparo (2+ estrellas)
        const val SHOOT_DAMAGE = 6f
        const val SHOOT_COOLDOWN_MS = 1600L
        const val CAR_SPAWN_COOLDOWN_MS = 3000L // entre aparición de patrullas

        // Nº de patrullas objetivo según el nivel de búsqueda (índice = estrellas).
        // Más estrellas → más patrullas persiguiéndote.
        fun desiredCarsFor(wantedLevel: Int): Int = when (wantedLevel) {
            0 -> 0
            1 -> 1
            2 -> 2
            3 -> 4
            4 -> 6
            else -> MAX_CARS   // 5 estrellas
        }
    }

    // Resultado de cada tick de simulación que el ViewModel aplica.
    data class PoliceTick(
        val units: List<Npc>,          // patrullas + policías a renderizar
        val damage: Float,             // daño total a aplicar al jugador este tick
        val impact: Boolean,           // dispara el efecto 💥
        val destroyedIds: List<String> // ids que dejaron de existir (para NPC_DESTROY)
    )

    // Unidades que ESTE jugador posee y simula.
    private val units = ConcurrentHashMap<String, Npc>()
    private val punchCooldowns = ConcurrentHashMap<String, Long>()
    private val shootCooldowns = ConcurrentHashMap<String, Long>()
    private val carArmed = ConcurrentHashMap<String, Boolean>() // patrulla ya soltó policías
    @Volatile private var lastCarSpawn = 0L

    fun activeUnits(): List<Npc> = units.values.toList()

    fun clearAll(): List<String> {
        val ids = units.keys.toList()
        units.clear(); punchCooldowns.clear(); shootCooldowns.clear(); carArmed.clear()
        return ids
    }

    fun update(
        playerLat: Double,
        playerLon: Double,
        roadNetwork: List<MapWay>,
        wantedLevel: Int,
        canShoot: Boolean,
        now: Long
    ): PoliceTick {
        if (wantedLevel <= 0) {
            val destroyed = clearAll()
            return PoliceTick(emptyList(), 0f, false, destroyed)
        }

        val destroyed = ArrayList<String>()
        var damage = 0f
        var impact = false

        // 1. SPAWN de patrullas hasta el objetivo (escala con el nivel de búsqueda).
        val desiredCars = desiredCarsFor(wantedLevel)
        val carCount = units.values.count { it.type == NpcType.POLICE_CAR }
        if (carCount < desiredCars && now - lastCarSpawn >= CAR_SPAWN_COOLDOWN_MS) {
            lastCarSpawn = now
            // Si faltan varias, soltamos hasta 2 a la vez para que la presión suba rápido.
            val toSpawn = (desiredCars - carCount).coerceAtMost(2)
            repeat(toSpawn) {
                spawnPatrol(playerLat, playerLon, roadNetwork, canShoot)?.let { units[it.id] = it }
            }
        }

        // 2. Mover/actuar cada unidad.
        val snapshot = units.values.toList()
        for (unit in snapshot) {
            when (unit.type) {
                NpcType.POLICE_CAR -> {
                    if (unit.policeDisembarked) continue // ya llegó: patrulla detenida
                    val dLat = playerLat - unit.location.latitude
                    val dLon = playerLon - unit.location.longitude
                    val dist = sqrt(dLat * dLat + dLon * dLon)
                    if (dist <= ARRIVE_DIST) {
                        // Llegó: la patrulla se detiene y suelta de 2 a 3 policías.
                        units[unit.id] = unit.copy(policeDisembarked = true, isMoving = false)
                        val numCops = Random.nextInt(2, 4)
                        repeat(numCops) { i ->
                            val cop = makeCop(unit, i, unit.policeCanShoot)
                            units[cop.id] = cop
                        }
                    } else {
                        val angle = atan2(dLat, dLon)
                        units[unit.id] = unit.copy(
                            location = GeoPoint(
                                unit.location.latitude + sin(angle) * CAR_SPEED,
                                unit.location.longitude + cos(angle) * CAR_SPEED
                            ),
                            rotationAngle = (-Math.toDegrees(angle).toFloat() + 270f + 360f) % 360f,
                            isMoving = true
                        )
                    }
                }
                NpcType.POLICE_COP -> {
                    val dLat = playerLat - unit.location.latitude
                    val dLon = playerLon - unit.location.longitude
                    val dist = sqrt(dLat * dLat + dLon * dLon)
                    val angle = atan2(dLat, dLon)
                    val facing = cos(angle) >= 0
                    val rot = (-Math.toDegrees(angle).toFloat() + 360f) % 360f

                    // Persecución directa al jugador.
                    val moved = if (dist > COP_STOP_DIST) {
                        unit.copy(
                            location = GeoPoint(
                                unit.location.latitude + sin(angle) * COP_SPEED,
                                unit.location.longitude + cos(angle) * COP_SPEED
                            ),
                            rotationAngle = rot, facingRight = facing, isMoving = true
                        )
                    } else {
                        unit.copy(rotationAngle = rot, facingRight = facing, isMoving = false)
                    }
                    units[unit.id] = moved

                    // GOLPE cuerpo a cuerpo.
                    if (dist <= PUNCH_DIST) {
                        val last = punchCooldowns[unit.id] ?: 0L
                        if (now - last >= PUNCH_COOLDOWN_MS) {
                            punchCooldowns[unit.id] = now
                            damage += PUNCH_DAMAGE
                            impact = true
                        }
                    }
                    // DISPARO a distancia (2+ estrellas).
                    if (unit.policeCanShoot && dist > PUNCH_DIST && dist <= SHOOT_RANGE) {
                        val last = shootCooldowns[unit.id] ?: 0L
                        if (now - last >= SHOOT_COOLDOWN_MS) {
                            shootCooldowns[unit.id] = now
                            damage += SHOOT_DAMAGE
                            impact = true
                        }
                    }
                }
                else -> {}
            }
        }

        return PoliceTick(units.values.toList(), damage, impact, destroyed)
    }

    private fun spawnPatrol(
        playerLat: Double,
        playerLon: Double,
        roadNetwork: List<MapWay>,
        canShoot: Boolean
    ): Npc? {
        // Punto candidato en un anillo alrededor del jugador.
        val ang = Random.nextDouble(0.0, 2.0 * Math.PI)
        val candLat = playerLat + sin(ang) * SPAWN_RING
        val candLon = playerLon + cos(ang) * SPAWN_RING

        // Lo pegamos al nodo de calle más cercano para que aparezca sobre el asfalto.
        var bestLat = candLat
        var bestLon = candLon
        var bestDist = Double.MAX_VALUE
        for (way in roadNetwork) {
            if (!way.isForCars) continue
            for (n in way.nodes) {
                val d = (n.lat - candLat) * (n.lat - candLat) + (n.lon - candLon) * (n.lon - candLon)
                if (d < bestDist) { bestDist = d; bestLat = n.lat; bestLon = n.lon }
            }
        }

        val id = "POLICE_CAR_${System.currentTimeMillis()}_${Random.nextInt(10000)}"
        return Npc(
            id = id,
            type = NpcType.POLICE_CAR,
            location = GeoPoint(bestLat, bestLon),
            speed = CAR_SPEED,
            isRemote = false,
            policeCanShoot = canShoot
        )
    }

    private fun makeCop(car: Npc, index: Int, canShoot: Boolean): Npc {
        // Nacen pegados a la patrulla con un pequeño desfase para no apilarse.
        val off = 0.00004
        val a = index * (2.0 * Math.PI / 3.0)
        val id = "POLICE_COP_${car.id}_${index}_${Random.nextInt(10000)}"
        return Npc(
            id = id,
            type = NpcType.POLICE_COP,
            location = GeoPoint(
                car.location.latitude + sin(a) * off,
                car.location.longitude + cos(a) * off
            ),
            speed = COP_SPEED,
            isRemote = false,
            isMoving = true,
            policeDisembarked = true,
            policeCanShoot = canShoot
        )
    }
}
