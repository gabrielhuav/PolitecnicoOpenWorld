package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * POLICÍA DE LA CAMPAÑA (Modo Historia · Misión 1, escolta a la ESCOM).
 *
 * Kotlin puro (como [PoliceManager] y [PrankedyManager]). Es una clase **independiente** del
 * sistema de nivel de búsqueda del MUNDO LIBRE ([PoliceManager] / `runPoliceTick`) para que sus
 * comportamientos NO choquen: aquí no hay patrullas, ni disparos, ni carjack. Son **2 policías a
 * PIE** que **SIGUEN al jugador a una distancia considerable y DESPACIO** (escolta narrativa).
 *
 * Para NO quedarse atascados, persiguen siguiendo una **RUTA por la red de calles (A*)**,
 * recalculada cada [ROUTE_TTL_MS] y avanzando nodo a nodo (igual que [PoliceManager.advanceAlong]).
 * La distancia que mantienen ([FOLLOW_DISTANCE]) es mayor que el radio de la niebla, así el
 * jugador SIEMPRE los ve como un **waypoint** (como cuando tienes estrellas), no como sprite.
 */
class CampaignEscortPolice {

    companion object {
        const val COP_COUNT = 2
        const val SPAWN_BEHIND = 0.00095    // ~105 m: aparecen DETRÁS, fuera de la niebla
        const val FOLLOW_DISTANCE = 0.00078 // ~87 m: distancia considerable (> niebla 70 m → waypoint)
        const val RESUME_BAND = 0.00015     // ~17 m de histéresis: evita el tembleque en el borde
        const val COP_SPEED = 0.0000030     // por tick: a pie y LENTOS (no te alcanzan corriendo)

        // Pathfinding / anti-atasco (mismos umbrales que PoliceManager).
        const val ROUTE_TTL_MS = 1500L      // recalcular la ruta hacia ti cada ~1.5 s
        const val WAYPOINT_REACH = 0.00012  // ~13 m: distancia para pasar al siguiente nodo
        const val STUCK_TIME_MS = 1500L     // si no avanza 1.5 s, va DIRECTO un momento para despegarse
        const val STUCK_EPS = 0.00002       // ~2 m: umbral de "no se movió"
    }

    private val units = ConcurrentHashMap<String, Npc>()
    // Estado de ruta por policía (A* sobre la red de calles).
    private val route = ConcurrentHashMap<String, List<GeoPoint>>()
    private val routeIdx = ConcurrentHashMap<String, Int>()
    private val routeTime = ConcurrentHashMap<String, Long>()
    private val stuckPos = ConcurrentHashMap<String, GeoPoint>()
    private val stuckSince = ConcurrentHashMap<String, Long>()

    fun activeUnits(): List<Npc> = units.values.toList()
    fun isActive(): Boolean = units.isNotEmpty()
    fun clear() {
        units.clear(); route.clear(); routeIdx.clear(); routeTime.clear()
        stuckPos.clear(); stuckSince.clear()
    }

    /** Crea los 2 policías DETRÁS del jugador (en abanico), sobre la calle si hay `snap`. */
    fun spawn(playerLat: Double, playerLon: Double, snap: ((GeoPoint) -> GeoPoint)? = null) {
        clear()
        for (i in 0 until COP_COUNT) {
            val ang = Math.PI + (if (i == 0) -0.30 else 0.30)   // detrás, abierto en abanico
            val raw = GeoPoint(
                playerLat + sin(ang) * SPAWN_BEHIND,
                playerLon + cos(ang) * SPAWN_BEHIND
            )
            val placed = snap?.invoke(raw) ?: raw
            val id = "CAMPAIGN_COP_$i"
            units[id] = Npc(
                id = id,
                type = NpcType.POLICE_COP,
                location = placed,
                speed = COP_SPEED,
                isRemote = false,
                isMoving = true,
                policeDisembarked = true,   // ya está "a pie" (no pertenece a ninguna patrulla)
                policeCanShoot = false       // NUNCA dispara: es escolta narrativa
            )
        }
    }

    /**
     * Un ciclo de seguimiento. Cada policía:
     *  - si ya está dentro de [FOLLOW_DISTANCE] → se queda (mantiene la distancia considerable);
     *  - si está más lejos → avanza DESPACIO hacia el jugador siguiendo la RUTA por las calles
     *    (A*), así no se atasca ni atraviesa edificios.
     */
    fun tick(
        playerLat: Double,
        playerLon: Double,
        now: Long,
        snap: ((GeoPoint) -> GeoPoint)? = null,
        pathfind: ((GeoPoint, GeoPoint) -> List<GeoPoint>)? = null
    ) {
        for (u in units.values.toList()) {
            val d = dist(u.location.latitude, u.location.longitude, playerLat, playerLon)
            if (d <= FOLLOW_DISTANCE) {
                // Mantiene la distancia: deja de avanzar y olvida la ruta (se recalcula al alejarse).
                forgetRoute(u.id)
                if (u.isMoving) units[u.id] = u.copy(isMoving = false)
                continue
            }
            val (loc, rot, facing) = advanceAlong(u, playerLat, playerLon, COP_SPEED, now, snap, pathfind)
            units[u.id] = u.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
        }
    }

    private fun forgetRoute(id: String) {
        route.remove(id); routeIdx.remove(id); routeTime.remove(id)
        stuckPos.remove(id); stuckSince.remove(id)
    }

    // Avanza una unidad hacia (tLat,tLon) siguiendo una RUTA por la red de calles (A*),
    // recalculada periódicamente. Si se atasca, da un paso DIRECTO (sin snap) para despegarse.
    private fun advanceAlong(
        unit: Npc, tLat: Double, tLon: Double, speed: Double, now: Long,
        snap: ((GeoPoint) -> GeoPoint)?, pathfind: ((GeoPoint, GeoPoint) -> List<GeoPoint>)?
    ): Triple<GeoPoint, Float, Boolean> {
        val id = unit.id

        // Detección de atasco: si no avanzó STUCK_EPS en STUCK_TIME_MS, paso directo + recálculo.
        val prev = stuckPos[id]
        if (prev == null || dist(prev.latitude, prev.longitude, unit.location.latitude, unit.location.longitude) > STUCK_EPS) {
            stuckPos[id] = unit.location; stuckSince[id] = now
        }
        val stuck = now - (stuckSince[id] ?: now) > STUCK_TIME_MS

        val cur = route[id]
        val needRoute = pathfind != null &&
            (cur == null || cur.isEmpty() || stuck || now - (routeTime[id] ?: 0L) > ROUTE_TTL_MS)
        if (needRoute && pathfind != null) {
            route[id] = pathfind(unit.location, GeoPoint(tLat, tLon))
            routeIdx[id] = 0
            routeTime[id] = now
            if (stuck) { stuckSince[id] = now; return stepDirect(unit.location, tLat, tLon, speed) }
        }

        val r = route[id]
        if (r == null || r.size < 3) {
            // Sin ruta real del A* (objetivo sin conexión): nos pegamos a la calle con snap.
            return stepOnRoad(unit.location, tLat, tLon, speed, snap)
        }
        val reach = maxOf(WAYPOINT_REACH, speed * 4.0)
        var idx = (routeIdx[id] ?: 0).coerceIn(0, r.size - 1)
        while (idx < r.size - 1 &&
            dist(unit.location.latitude, unit.location.longitude, r[idx].latitude, r[idx].longitude) < reach) {
            idx++
        }
        routeIdx[id] = idx
        val wp = r[idx]
        return stepOnRoad(unit.location, wp.latitude, wp.longitude, speed, snap)
    }

    // Un paso DIRECTO (sin snap) hacia el objetivo, para despegarse de un atasco.
    private fun stepDirect(from: GeoPoint, toLat: Double, toLon: Double, speed: Double): Triple<GeoPoint, Float, Boolean> {
        val a = atan2(toLat - from.latitude, toLon - from.longitude)
        val placed = GeoPoint(from.latitude + sin(a) * speed, from.longitude + cos(a) * speed)
        val rot = (-Math.toDegrees(a).toFloat() + 360f) % 360f
        return Triple(placed, rot, cos(a) >= 0)
    }

    // Un paso hacia (toLat,toLon) ajustado a la calle más cercana (snap).
    private fun stepOnRoad(
        from: GeoPoint, toLat: Double, toLon: Double, speed: Double, snap: ((GeoPoint) -> GeoPoint)?
    ): Triple<GeoPoint, Float, Boolean> {
        val a = atan2(toLat - from.latitude, toLon - from.longitude)
        val raw = GeoPoint(from.latitude + sin(a) * speed, from.longitude + cos(a) * speed)
        val placed = snap?.invoke(raw) ?: raw
        val mLat = placed.latitude - from.latitude
        val mLon = placed.longitude - from.longitude
        val moved = mLat * mLat + mLon * mLon > 1e-14
        val rot = if (moved) (-Math.toDegrees(atan2(mLat, mLon)).toFloat() + 360f) % 360f
                  else (-Math.toDegrees(a).toFloat() + 360f) % 360f
        val facing = if (moved) mLon >= 0 else cos(a) >= 0
        return Triple(placed, rot, facing)
    }

    private fun dist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = lat1 - lat2; val dLon = lon1 - lon2
        return sqrt(dLat * dLat + dLon * dLon)
    }
}
