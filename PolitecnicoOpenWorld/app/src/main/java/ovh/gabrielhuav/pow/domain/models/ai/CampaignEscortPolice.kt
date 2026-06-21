package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
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

    // ESCORT = Misión 1 (2 policías que te SIGUEN a distancia, despacio).
    // CHASE  = Misión 2 (varios policías que te PERSIGUEN para obligarte a entrar a la ESCOM).
    // RESOLUTION = REMATE de la Misión 2: Prankedy se les escapó a la ESCOM → se detienen, "platican"
    //   (burbuja) y se reparten: la MITAD entra a la ESCOM (como Prankedy) y la MITAD se regresa.
    enum class Mode { ESCORT, CHASE, RESOLUTION }

    companion object {
        const val COP_COUNT = 2
        const val SPAWN_BEHIND = 0.0005     // ~55 m: aparecen DETRÁS, cerca y visibles
        const val FOLLOW_DISTANCE = 0.0004  // ~45 m: distancia INICIAL de la escolta
        const val RESUME_BAND = 0.00012     // ~13 m de histéresis: evita el tembleque en el borde
        const val COP_SPEED = 0.0000032     // por tick: a pie y lentos (no te alcanzan corriendo)

        // ─── ESCOLTA (Misión 1): se acercan CADA VEZ MÁS con el tiempo y atacan a Prankedy ──
        // La distancia que mantienen ENCOGE de FOLLOW_DISTANCE a ESCORT_CONTACT en CLOSE_IN_MS,
        // así "te alcanzan pero tardan". Al estar en contacto, atacan a Prankedy.
        const val ESCORT_CONTACT = 0.00003      // ~3.3 m: ya alcanzaron
        const val CLOSE_IN_MS = 70000L          // ~70 s para cerrar de 45 m a contacto (tardan; da chance de llegar)
        const val ESCORT_ATTACK_RANGE = 0.00004 // ~4.5 m: a esta distancia de Prankedy lo golpean
        const val ESCORT_ATTACK_DAMAGE = 7f
        const val ESCORT_ATTACK_COOLDOWN_MS = 1100L

        // Teleport: si el policía queda a MÁS del DOBLE de tu fog of war (~70 m → 2× = ~140 m),
        // se reubica cerca de ti (te alejaste demasiado). ~140 m en grados.
        const val TELEPORT_DIST = 0.00126       // ~140 m (2× fog)

        // ─── MISIÓN 2 (persecución) ──────────────────────────────────────────
        const val CHASE_SPEED = 0.0000050   // más rápido que la escolta (presionan), pero escapable a pie
        const val CHASE_SPAWN_RING = 0.0013 // ~145 m: aparecen ALGO LEJOS, rodeándote
        const val CHASE_CONTACT = 0.00004   // ~4.5 m: a esta distancia ya te "alcanzaron" (se detienen encima)

        // ─── REMATE (RESOLUTION): Prankedy se les escapó a la ESCOM ──────────
        const val RESOLUTION_GATHER_RADIUS = 0.00016  // ~18 m: "ya llegaron a la puerta" (se juntan ahí)
        const val RESOLUTION_GATHER_SPEED = 0.0000110 // llegan CORRIENDO a la escena (más rápido que el chase)
        const val RESOLUTION_GATHER_TIMEOUT_MS = 12000L // tope: si alguno no llega, se platica igual
        const val RESOLUTION_TALK_MS = 3000L          // se detienen y "platican"/reaccionan ~3 s
        const val RESOLUTION_REACH = 0.00006          // ~6.6 m: el policía llega a su destino y desaparece

        // Pathfinding / anti-atasco (mismos umbrales que PoliceManager).
        const val ROUTE_TTL_MS = 1500L      // recalcular la ruta hacia el objetivo cada ~1.5 s
        const val WAYPOINT_REACH = 0.00012  // ~13 m: distancia para pasar al siguiente nodo
        const val STUCK_TIME_MS = 1500L     // si no avanza 1.5 s, va DIRECTO un momento para despegarse
        const val STUCK_EPS = 0.00002       // ~2 m: umbral de "no se movió"
    }

    @Volatile var mode: Mode = Mode.ESCORT
        private set
    @Volatile private var spawnTimeMs = 0L

    private val units = ConcurrentHashMap<String, Npc>()
    // Estado de ruta por policía (A* sobre la red de calles).
    private val route = ConcurrentHashMap<String, List<GeoPoint>>()
    private val routeIdx = ConcurrentHashMap<String, Int>()
    private val routeTime = ConcurrentHashMap<String, Long>()
    private val stuckPos = ConcurrentHashMap<String, GeoPoint>()
    private val stuckSince = ConcurrentHashMap<String, Long>()
    private val attackCooldown = ConcurrentHashMap<String, Long>()

    // ─── REMATE (RESOLUTION) ──────────────────────────────────────────────
    @Volatile private var resolutionStartMs = 0L               // inicio de la PLÁTICA (tras reunirse)
    @Volatile private var resolutionBeginMs = 0L               // inicio del remate (para el tope de reunión)
    @Volatile private var resolutionGathered = false           // ¿ya se juntaron todos en la puerta?
    private val copRole = ConcurrentHashMap<String, Int>()    // 1 = ENTRA a la ESCOM · 2 = SE REGRESA
    @Volatile private var doorPoint: GeoPoint? = null          // dónde se metió Prankedy (se juntan aquí)
    @Volatile private var retreatPoint: GeoPoint? = null       // destino de los que SE REGRESAN

    fun activeUnits(): List<Npc> = units.values.toList()
    fun isActive(): Boolean = units.isNotEmpty()
    fun isResolving(): Boolean = mode == Mode.RESOLUTION
    fun clear() {
        units.clear(); route.clear(); routeIdx.clear(); routeTime.clear()
        stuckPos.clear(); stuckSince.clear(); attackCooldown.clear()
        copRole.clear(); doorPoint = null; retreatPoint = null; resolutionStartMs = 0L
        resolutionBeginMs = 0L; resolutionGathered = false
    }

    /**
     * MISIÓN 2 — REMATE: Prankedy se metió a la ESCOM (se les ESCAPÓ). Los policías se DETIENEN y
     * "platican"/reaccionan un momento (burbuja 📞 en el render nativo), y luego la MITAD ENTRA a la
     * ESCOM (como Prankedy) y la otra MITAD SE REGRESA por donde llegó.
     *  - `doorLat/doorLon`    = entrada de la ESCOM (destino de los que entran).
     *  - `retreatLat/retreatLon` = de dónde llegaron los policías (destino de los que se regresan).
     */
    fun startResolution(doorLat: Double, doorLon: Double, retreatLat: Double, retreatLon: Double, now: Long) {
        if (mode == Mode.RESOLUTION) return
        mode = Mode.RESOLUTION
        resolutionStartMs = 0L            // la plática arranca cuando se REÚNAN en la puerta
        resolutionBeginMs = now
        resolutionGathered = false
        doorPoint = GeoPoint(doorLat, doorLon)
        retreatPoint = GeoPoint(retreatLat, retreatLon)
        // Reparte roles ALTERNANDO (de 6 → 3 entran a la ESCOM, 3 se regresan).
        units.keys.toList().forEachIndexed { i, id -> copRole[id] = if (i % 2 == 0) 1 else 2; forgetRoute(id) }
    }

    /** MISIÓN 1: crea los 2 policías DETRÁS del jugador (en abanico) que lo SIGUEN a distancia. */
    fun spawn(playerLat: Double, playerLon: Double, snap: ((GeoPoint) -> GeoPoint)? = null) {
        clear()
        mode = Mode.ESCORT
        spawnTimeMs = System.currentTimeMillis()
        for (i in 0 until COP_COUNT) {
            val ang = Math.PI + (if (i == 0) -0.30 else 0.30)   // detrás, abierto en abanico
            val raw = GeoPoint(
                playerLat + sin(ang) * SPAWN_BEHIND,
                playerLon + cos(ang) * SPAWN_BEHIND
            )
            spawnCop(i, raw, snap)
        }
    }

    /**
     * MISIÓN 2: crea `count` policías ALGO LEJOS que te PERSIGUEN.
     *
     * Si se pasa [awayFromLat]/[awayFromLon] (la ENTRADA marcada con 🎯), los policías aparecen en
     * el **LADO CONTRARIO** a esa entrada — un abanico centrado en la dirección puerta→jugador, es
     * decir DETRÁS del jugador respecto a la puerta — para EMPUJARLO hacia la entrada. Si no se
     * pasa, caen en un anillo completo alrededor (comportamiento anterior).
     */
    fun spawnChase(
        count: Int, playerLat: Double, playerLon: Double,
        snap: ((GeoPoint) -> GeoPoint)? = null,
        awayFromLat: Double? = null, awayFromLon: Double? = null
    ) {
        clear()
        mode = Mode.CHASE
        spawnTimeMs = System.currentTimeMillis()
        // Dirección puerta→jugador (hacia el lado opuesto a la entrada). null = anillo completo.
        val baseAng = if (awayFromLat != null && awayFromLon != null)
            atan2(playerLat - awayFromLat, playerLon - awayFromLon) else null
        val spread = Math.toRadians(110.0)   // abanico de ~110° en el lado contrario
        for (i in 0 until count) {
            val ang = if (baseAng != null) {
                val frac = if (count > 1) i.toDouble() / (count - 1) else 0.5
                baseAng - spread / 2.0 + spread * frac
            } else {
                2.0 * Math.PI * i / count   // repartidos alrededor del jugador
            }
            val raw = GeoPoint(
                playerLat + sin(ang) * CHASE_SPAWN_RING,
                playerLon + cos(ang) * CHASE_SPAWN_RING
            )
            spawnCop(i, raw, snap)
        }
    }

    private fun spawnCop(i: Int, raw: GeoPoint, snap: ((GeoPoint) -> GeoPoint)?) {
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
            policeCanShoot = false       // NUNCA dispara
        )
    }

    /**
     * Un ciclo. `targetLat/targetLon` = a quién PERSIGUEN: en ESCORT es **Prankedy** (lo van a
     * atacar); en CHASE es el jugador. `playerLat/playerLon` se usa para el teleport (si el
     * policía queda a > [TELEPORT_DIST] del JUGADOR, se reubica cerca).
     *
     * Devuelve el **daño total infligido a Prankedy** este tick (0 en CHASE), para que el VM lo
     * aplique y dispare "MISIÓN FALLIDA" si lo mata.
     */
    fun tick(
        playerLat: Double,
        playerLon: Double,
        targetLat: Double,
        targetLon: Double,
        now: Long,
        snap: ((GeoPoint) -> GeoPoint)? = null,
        pathfind: ((GeoPoint, GeoPoint) -> List<GeoPoint>)? = null
    ): Float {
        if (mode == Mode.RESOLUTION) return tickResolution(now, snap, pathfind)
        // ESCORT: la distancia que mantienen ENCOGE con el tiempo (te alcanzan pero tardan).
        val stopDist = if (mode == Mode.CHASE) CHASE_CONTACT else {
            val t = ((now - spawnTimeMs).toDouble() / CLOSE_IN_MS).coerceIn(0.0, 1.0)
            FOLLOW_DISTANCE + (ESCORT_CONTACT - FOLLOW_DISTANCE) * t
        }
        val speed = if (mode == Mode.CHASE) CHASE_SPEED else COP_SPEED
        var prankedyDamage = 0f

        for (u in units.values.toList()) {
            // TELEPORT: si te alejaste a > 2× tu fog, reubica al policía cerca de ti (detrás).
            if (dist(u.location.latitude, u.location.longitude, playerLat, playerLon) > TELEPORT_DIST) {
                relocateNear(u, playerLat, playerLon, snap)
                continue
            }

            val d = dist(u.location.latitude, u.location.longitude, targetLat, targetLon)

            // ESCORT: si llegó a Prankedy, lo ATACA (con cooldown por policía).
            if (mode == Mode.ESCORT && d <= ESCORT_ATTACK_RANGE) {
                if (now - (attackCooldown[u.id] ?: 0L) >= ESCORT_ATTACK_COOLDOWN_MS) {
                    attackCooldown[u.id] = now
                    prankedyDamage += ESCORT_ATTACK_DAMAGE
                }
            }

            if (d <= stopDist) {
                forgetRoute(u.id)
                if (u.isMoving) units[u.id] = u.copy(isMoving = false)
                continue
            }
            val (loc, rot, facing) = advanceAlong(u, targetLat, targetLon, speed, now, snap, pathfind)
            units[u.id] = u.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
        }
        return prankedyDamage
    }

    /**
     * REMATE en 3 fases:
     *  1) REUNIRSE: todos caminan a la PUERTA (donde se metió Prankedy) a buscarlo.
     *  2) PLATICAR: una vez juntos, se DETIENEN [RESOLUTION_TALK_MS] ms mostrando la burbuja ❓/💬.
     *  3) REPARTIRSE: 3 ENTRAN a la ESCOM (rol 1, desaparecen en la puerta) y 3 SE REGRESAN (rol 2).
     */
    private fun tickResolution(
        now: Long, snap: ((GeoPoint) -> GeoPoint)?, pathfind: ((GeoPoint, GeoPoint) -> List<GeoPoint>)?
    ): Float {
        val door = doorPoint ?: return 0f
        val retreat = retreatPoint ?: return 0f

        // FASE 1 — REUNIRSE en la puerta (buscan a Prankedy donde se metió).
        if (!resolutionGathered) {
            var allClose = true
            for (u in units.values.toList()) {
                val d = dist(u.location.latitude, u.location.longitude, door.latitude, door.longitude)
                if (d > RESOLUTION_GATHER_RADIUS) {
                    allClose = false
                    val (loc, rot, facing) = advanceAlong(u, door.latitude, door.longitude, RESOLUTION_GATHER_SPEED, now, snap, pathfind)
                    units[u.id] = u.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
                } else if (u.isMoving) {
                    units[u.id] = u.copy(isMoving = false)
                }
            }
            if (allClose || now - resolutionBeginMs > RESOLUTION_GATHER_TIMEOUT_MS) {
                resolutionGathered = true
                resolutionStartMs = now
                // Activa la burbuja ❓/💬 en todos mientras "platican".
                units.values.toList().forEach { u ->
                    units[u.id] = u.copy(talkingUntil = now + RESOLUTION_TALK_MS, isMoving = false)
                }
            }
            return 0f
        }

        // FASE 2 — PLATICAR: quietos hasta que pase RESOLUTION_TALK_MS (la burbuja ya está puesta).
        if (now - resolutionStartMs < RESOLUTION_TALK_MS) return 0f

        // FASE 3 — REPARTIRSE: cada quien a su destino; desaparece al llegar.
        for (u in units.values.toList()) {
            val tgt = if ((copRole[u.id] ?: 2) == 1) door else retreat
            val d = dist(u.location.latitude, u.location.longitude, tgt.latitude, tgt.longitude)
            if (d <= RESOLUTION_REACH) { units.remove(u.id); forgetRoute(u.id); continue }  // llegó → desaparece
            val (loc, rot, facing) = advanceAlong(u, tgt.latitude, tgt.longitude, CHASE_SPEED, now, snap, pathfind)
            units[u.id] = u.copy(location = loc, rotationAngle = rot, facingRight = facing, isMoving = true)
        }
        return 0f
    }

    // Reubica al policía cerca del jugador (detrás), sobre la calle si hay snap. Conserva el
    // cronómetro de cierre (sigue acercándose) y olvida su ruta vieja.
    private fun relocateNear(unit: Npc, playerLat: Double, playerLon: Double, snap: ((GeoPoint) -> GeoPoint)?) {
        val ang = Math.PI + (Math.random() - 0.5)   // detrás, con algo de variación
        val raw = GeoPoint(playerLat + sin(ang) * SPAWN_BEHIND, playerLon + cos(ang) * SPAWN_BEHIND)
        val placed = snap?.invoke(raw) ?: raw
        forgetRoute(unit.id)
        units[unit.id] = unit.copy(location = placed, isMoving = true)
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
