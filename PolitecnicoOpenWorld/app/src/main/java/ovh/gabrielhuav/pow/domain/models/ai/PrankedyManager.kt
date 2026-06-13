package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Manager de IA del NPC compañero Prankedy.
 *
 * Patrón: análogo a [PoliceManager]. Mantiene todo el estado interno;
 * el ViewModel llama a [tick] en cada ciclo del game loop y aplica
 * los resultados ([PrankedyTickResult]) sobre el mundo.
 */
class PrankedyManager {

    /**
     * Resultado de un tick de IA de Prankedy.
     * @param hitNpcId  ID del NPC al que golpeó el proyectil (null = sin golpe este tick).
     * @param projectileDamage Daño del proyectil si impacta.
     */
    data class PrankedyTickResult(
        val hitNpcId: String? = null,
        val projectileDamage: Float = ATTACK_DAMAGE_PROJECTILE
    )

    companion object {
        // ── Distancias (en grados, aprox. plana) ────────────────────────────
        const val PRANKEDY_SPAWN_RADIUS    = 0.00035   // ~35 m del jugador al nacer
        const val PRANKEDY_INTERACT_RADIUS = 0.00012   // ~13 m → muestra botón X
        const val PRANKEDY_PROX_RADIUS     = 0.00018   // ~20 m → muestra burbuja
        const val PRANKEDY_FOLLOW_MIN_DIST = 0.00006   // ~7 m → margen anti-clipping
        const val PRANKEDY_FOLLOW_MAX_DIST = 0.00035   // >35 m → empieza a correr

        // ── Velocidades ──────────────────────────────────────────────────────
        const val WALK_SPEED        = 0.0000018
        const val RUN_SPEED         = 0.000005
        const val RUN_TANQUE_SPEED  = 0.0000075

        // ── Combate ──────────────────────────────────────────────────────────
        const val ENEMY_VISION_RADIUS      = 0.00045  // ~50 m de detección de enemigos
        const val ATTACK_RADIUS            = 0.00013  // ~14 m → lanza proyectil
        const val ATTACK_DAMAGE_PROJECTILE = 22f
        const val ATTACK_COOLDOWN_MS       = 2800L
        const val PROJECTILE_FLIGHT_MS     = 900L     // tiempo que tarda el proyectil en llegar

        // ── Vida y respawn ───────────────────────────────────────────────────
        const val MAX_HEALTH               = 80f
        const val RESPAWN_COOLDOWN_MS      = 60_000L  // 60 s hasta renacer
        const val HIRE_PENALTY_MS          = 60_000L  // 60 s adicionales antes de "Contratar"

        // ── Idle phrases ─────────────────────────────────────────────────────
        val IDLE_PHRASES = listOf(
            "¿Me sigues o qué, hermano?",
            "El anonimato es mi superpoder 🎭",
            "Nadie espera al rey de las bromas...",
            "Hoy hay víctima nueva, ¿vamos?",
            "Sigo aquí. Sin prisa.",
            "¿Ya viste eso? Increíble.",
            "Este campus huele a misión.",
            "Cuando quieras, jefe."
        )
        val HIRED_PHRASES = listOf(
            "¡Voy contigo!",
            "¡Allá vamos!",
            "¡Ese no pasa! 🎯",
            "Te tengo cubierto.",
            "¡Nadie te toca! 💥",
            "Siempre juntos."
        )

        // ── Idle wander ──────────────────────────────────────────────────────
        private const val IDLE_WANDER_STEP = 0.00001   // pequeño paso de deambular
        private const val IDLE_CHANGE_DIR_TICKS = 120  // cambia de dirección cada ~4 s
    }

    // ── Estado principal ─────────────────────────────────────────────────────
    var phase: PrankedyPhase = PrankedyPhase.NOT_HIRED
        private set
    var animState: PrankedyAnimState = PrankedyAnimState.IDLE
        private set
    var location: GeoPoint? = null
        private set
    var facingRight: Boolean = true
        private set
    var health: Float = MAX_HEALTH
        private set

    // ── Proyectil ────────────────────────────────────────────────────────────
    var projectileActive: Boolean = false
        private set
    var projectileStart: GeoPoint? = null
        private set
    var projectileTarget: GeoPoint? = null
        private set
    var projectileStartMs: Long = 0L
        private set
    // Fracción 0f..1f de la trayectoria del proyectil (para el renderer)
    val projectileProgress: Float
        get() {
            if (!projectileActive) return 0f
            val elapsed = System.currentTimeMillis() - projectileStartMs
            return (elapsed.toFloat() / PROJECTILE_FLIGHT_MS).coerceIn(0f, 1f)
        }

    // ── Timers ───────────────────────────────────────────────────────────────
    var respawnAt: Long = 0L       // cuando respawnear (solo en fase DEAD)
        private set
    var hireableAt: Long = 0L     // cuando "Contratar" queda disponible
        private set

    // ── Diálogo flotante ─────────────────────────────────────────────────────
    var currentDialogue: String? = null
        private set
    var dialogueUntil: Long = 0L
        private set

    // ── Objetivo de ataque ───────────────────────────────────────────────────
    private var attackTargetId: String? = null
    private var attackCooldownUntil: Long = 0L

    // ── Idle wander ──────────────────────────────────────────────────────────
    private var idleWanderDirLat = 0.0
    private var idleWanderDirLon = 0.0
    private var idleTicks = 0

    // ── IA principal ─────────────────────────────────────────────────────────

    /**
     * Ejecuta un tick de IA (~30 Hz).
     * @param playerLoc    Posición actual del jugador.
     * @param npcs         Lista de NPCs presentes en el mundo (para buscar enemigos).
     * @param isDriving    El jugador está conduciendo (Prankedy se "esconde").
     * @param now          Timestamp actual en ms.
     * @param roadNetwork  Red de calles (para spawn sobre vía).
     * @return [PrankedyTickResult] con efectos que el VM debe aplicar.
     */
    fun tick(
        playerLoc: GeoPoint,
        npcs: List<Npc>,
        isDriving: Boolean,
        now: Long,
        roadNetwork: List<MapWay>
    ): PrankedyTickResult {
        // Si el jugador va en coche, Prankedy desaparece visualmente (sin despawnear su estado)
        if (isDriving) {
            animState = PrankedyAnimState.IDLE
            return PrankedyTickResult()
        }

        when (phase) {
            PrankedyPhase.DEAD -> {
                if (now >= respawnAt) {
                    respawn(playerLoc, roadNetwork, now)
                }
                return PrankedyTickResult()
            }
            PrankedyPhase.NOT_HIRED -> {
                val loc = location ?: return PrankedyTickResult()
                tickIdleWander(loc, playerLoc, now)
                tickDialogue(now)
                return PrankedyTickResult()
            }
            PrankedyPhase.HIRED -> {
                val loc = location ?: return PrankedyTickResult()
                return tickHired(loc, playerLoc, npcs, now)
            }
        }
    }

    /** Lógica de IA cuando está contratado: seguir + combatir. */
    private fun tickHired(
        loc: GeoPoint,
        playerLoc: GeoPoint,
        npcs: List<Npc>,
        now: Long
    ): PrankedyTickResult {
        // 1. Resolver proyectil en vuelo
        var hitNpcId: String? = null
        if (projectileActive && now - projectileStartMs >= PROJECTILE_FLIGHT_MS) {
            projectileActive = false
            val targetId = attackTargetId
            if (targetId != null) {
                hitNpcId = targetId
            }
        }

        // 2. Buscar enemigo más cercano en radio de visión
        val enemy = findNearestEnemy(loc, npcs)
        val distToPlayer = dist(loc, playerLoc)

        if (enemy != null) {
            val distToEnemy = dist(loc, enemy.location)
            if (distToEnemy <= ATTACK_RADIUS && now >= attackCooldownUntil && !projectileActive) {
                // ─ ATAQUE: lanzar proyectil
                animState = PrankedyAnimState.ATTACK
                attackTargetId = enemy.id
                projectileStart = GeoPoint(loc.latitude, loc.longitude)
                projectileTarget = GeoPoint(enemy.location.latitude, enemy.location.longitude)
                projectileStartMs = now
                projectileActive = true
                attackCooldownUntil = now + ATTACK_COOLDOWN_MS
                facingRight = enemy.location.longitude >= loc.longitude
                triggerDialogue(HIRED_PHRASES.random(), now, 2000L)
            } else if (distToEnemy > ATTACK_RADIUS) {
                // ─ CORRER hacia el enemigo (RUN_TANQUE)
                animState = PrankedyAnimState.RUN_TANQUE
                val newLoc = stepToward(loc, enemy.location, RUN_TANQUE_SPEED)
                location = newLoc
                facingRight = enemy.location.longitude >= loc.longitude
            } else {
                // En rango pero en cooldown → IDLE esperando
                animState = PrankedyAnimState.IDLE
            }
        } else {
            // Sin enemigo → seguir al jugador
            when {
                distToPlayer < PRANKEDY_FOLLOW_MIN_DIST -> {
                    animState = PrankedyAnimState.IDLE
                }
                distToPlayer > PRANKEDY_FOLLOW_MAX_DIST -> {
                    animState = PrankedyAnimState.RUN
                    val newLoc = stepToward(loc, playerLoc, RUN_SPEED)
                    location = newLoc
                    facingRight = playerLoc.longitude >= loc.longitude
                }
                else -> {
                    animState = PrankedyAnimState.WALK
                    val newLoc = stepToward(loc, playerLoc, WALK_SPEED)
                    location = newLoc
                    facingRight = playerLoc.longitude >= loc.longitude
                }
            }
        }

        tickDialogue(now)
        return PrankedyTickResult(hitNpcId = hitNpcId)
    }

    /** Deambula aleatoriamente cuando aún no fue contratado. */
    private fun tickIdleWander(loc: GeoPoint, playerLoc: GeoPoint, now: Long) {
        idleTicks++
        // Cambiar dirección periódicamente o si se aleja demasiado del jugador
        val distToPlayer = dist(loc, playerLoc)
        if (idleTicks % IDLE_CHANGE_DIR_TICKS == 0 || distToPlayer > PRANKEDY_SPAWN_RADIUS * 2) {
            // Elegir dirección aleatoria que se mantenga cerca del jugador
            val angle = Random.nextDouble() * 2 * Math.PI
            idleWanderDirLat = sin(angle) * IDLE_WANDER_STEP
            idleWanderDirLon = cos(angle) * IDLE_WANDER_STEP
        }

        // Si nos alejamos mucho del jugador, caminamos de regreso hacia él
        val newLoc = if (distToPlayer > PRANKEDY_SPAWN_RADIUS * 1.5) {
            animState = PrankedyAnimState.WALK
            facingRight = playerLoc.longitude >= loc.longitude
            stepToward(loc, playerLoc, WALK_SPEED)
        } else {
            // Deambular suavemente
            val moved = idleTicks % (IDLE_CHANGE_DIR_TICKS / 2) < (IDLE_CHANGE_DIR_TICKS / 4)
            if (moved) {
                animState = PrankedyAnimState.WALK
                if (idleWanderDirLon != 0.0) facingRight = idleWanderDirLon > 0
                GeoPoint(loc.latitude + idleWanderDirLat, loc.longitude + idleWanderDirLon)
            } else {
                animState = PrankedyAnimState.IDLE
                loc
            }
        }
        location = newLoc

        // Burbuja de diálogo ocasional cerca del jugador
        if (idleTicks % 600 == 0 && dist(loc, playerLoc) < PRANKEDY_PROX_RADIUS) {
            triggerDialogue(IDLE_PHRASES.random(), now, 3500L)
        }
    }

    /** Limpia el diálogo si ya expiró. */
    private fun tickDialogue(now: Long) {
        if (currentDialogue != null && now > dialogueUntil) {
            currentDialogue = null
        }
    }

    /** Busca el NPC hostil más cercano en el radio de visión. */
    private fun findNearestEnemy(loc: GeoPoint, npcs: List<Npc>): Npc? {
        return npcs
            .filter { npc ->
                npc.type != NpcType.CAR &&
                npc.type != NpcType.POLICE_CAR &&
                npc.health > 0 &&
                !npc.isDying &&
                (npc.aggroUntil > System.currentTimeMillis() ||
                 npc.type == NpcType.ZOMBIE) &&
                dist(loc, npc.location) <= ENEMY_VISION_RADIUS
            }
            .minByOrNull { dist(loc, it.location) }
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /**
     * Coloca a Prankedy en el mundo, cerca del jugador sobre una calle de la red viaria.
     * Debe llamarse una sola vez al iniciar la partida o tras respawn.
     */
    fun spawn(nearPlayer: GeoPoint, roadNetwork: List<MapWay>, now: Long = System.currentTimeMillis()) {
        android.util.Log.d("Prankedy", "Iniciando spawn() en PrankedyManager. Roads=${roadNetwork.size}")
        val spawnPoint = findSpawnPoint(nearPlayer, roadNetwork)
        android.util.Log.d("Prankedy", "Punto de spawn decidido: $spawnPoint")
        location = spawnPoint
        health = MAX_HEALTH
        phase = PrankedyPhase.NOT_HIRED
        animState = PrankedyAnimState.IDLE
        projectileActive = false
        attackTargetId = null
        attackCooldownUntil = 0L
        idleTicks = 0
        currentDialogue = null
        dialogueUntil = 0L
    }

    /** El jugador acepta contratar a Prankedy. */
    fun hire() {
        if (phase != PrankedyPhase.NOT_HIRED) return
        phase = PrankedyPhase.HIRED
        triggerDialogue(HIRED_PHRASES.random(), System.currentTimeMillis(), 3000L)
    }

    /** Aplica daño a Prankedy. Devuelve true si acaba de morir. */
    fun takeDamage(amount: Float, now: Long = System.currentTimeMillis()): Boolean {
        if (phase == PrankedyPhase.DEAD) return false
        health = (health - amount).coerceAtLeast(0f)
        if (health <= 0f) {
            phase = PrankedyPhase.DEAD
            respawnAt  = now + RESPAWN_COOLDOWN_MS
            hireableAt = now + RESPAWN_COOLDOWN_MS + HIRE_PENALTY_MS
            location = null
            projectileActive = false
            return true
        }
        return false
    }

    /**
     * Notifica a Prankedy que el jugador recibió daño: activa búsqueda inmediata
     * de enemigos en el próximo tick (ya gestionado en [tickHired] naturalmente).
     */
    fun onPlayerDamaged() {
        // Prioridad ya está integrada: en tickHired siempre busca enemigos primero.
        // Esta función queda como hook para efectos extra (sonido, diálogo).
        if (phase == PrankedyPhase.HIRED) {
            triggerDialogue(HIRED_PHRASES.random(), System.currentTimeMillis(), 2000L)
        }
    }

    /** ¿Puede el jugador contratar a Prankedy ahora mismo? */
    fun isHireable(now: Long = System.currentTimeMillis()): Boolean =
        phase == PrankedyPhase.NOT_HIRED && now >= hireableAt

    /** Segundos hasta que "Contratar" quede disponible. 0 si ya está disponible. */
    fun hireableInSeconds(now: Long = System.currentTimeMillis()): Int =
        if (isHireable(now)) 0 else ((hireableAt - now) / 1000L).toInt().coerceAtLeast(0)

    /** Activa un diálogo flotante sobre el NPC. */
    fun triggerDialogue(text: String, now: Long, durationMs: Long = 3000L) {
        currentDialogue = text
        dialogueUntil = now + durationMs
    }

    // ── Helpers privados ─────────────────────────────────────────────────────

    /** Mueve un paso desde [from] hacia [to] a la velocidad dada. */
    private fun stepToward(from: GeoPoint, to: GeoPoint, speed: Double): GeoPoint {
        val dLat = to.latitude - from.latitude
        val dLon = to.longitude - from.longitude
        val d = sqrt(dLat * dLat + dLon * dLon).coerceAtLeast(1e-10)
        return GeoPoint(from.latitude + (dLat / d) * speed, from.longitude + (dLon / d) * speed)
    }

    /** Distancia euclidiana aproximada entre dos GeoPoints. */
    private fun dist(a: GeoPoint, b: GeoPoint): Double {
        val dLat = a.latitude - b.latitude
        val dLon = a.longitude - b.longitude
        return sqrt(dLat * dLat + dLon * dLon)
    }

    /** Intenta respawnear a Prankedy cuando el timer expira. */
    private fun respawn(nearPlayer: GeoPoint, roadNetwork: List<MapWay>, now: Long) {
        spawn(nearPlayer, roadNetwork, now)
        // hireableAt permanece desde la muerte: puede que aún no sea contrateable
    }

    /**
     * Busca un punto de spawn sobre la red viaria cerca del jugador.
     * Si no encuentra calles, usa un offset fijo alrededor del jugador.
     */
    private fun findSpawnPoint(nearPlayer: GeoPoint, roadNetwork: List<MapWay>): GeoPoint {
        // Fallback inmediato si no hay red viaria
        if (roadNetwork.isEmpty()) {
            android.util.Log.d("Prankedy", "RoadNetwork vacía, usando fallback aleatorio")
            val angle = Random.nextDouble() * 2 * Math.PI
            return GeoPoint(
                nearPlayer.latitude  + sin(angle) * PRANKEDY_SPAWN_RADIUS,
                nearPlayer.longitude + cos(angle) * PRANKEDY_SPAWN_RADIUS
            )
        }

        // Intenta buscar una calle en el radio deseado
        val nearby = roadNetwork.filter { way ->
            way.nodes.any { node ->
                val d = dist(GeoPoint(node.lat, node.lon), nearPlayer)
                d in PRANKEDY_SPAWN_RADIUS * 0.4..PRANKEDY_SPAWN_RADIUS * 1.6
            }
        }

        if (nearby.isNotEmpty()) {
            val way = nearby.random()
            val node = way.nodes.random()
            android.util.Log.d("Prankedy", "Spawn sobre calle ID: ${way.id}")
            return GeoPoint(node.lat, node.lon)
        }

        // Si hay calles pero ninguna cerca, usa la calle más cercana (sin filtrar por radio)
        val allNodes = roadNetwork.flatMap { it.nodes }
        val nearestNode = allNodes.minByOrNull { dist(GeoPoint(it.lat, it.lon), nearPlayer) }
        
        if (nearestNode != null) {
            android.util.Log.d("Prankedy", "No hay calles en radio ideal, usando la más cercana")
            return GeoPoint(nearestNode.lat, nearestNode.lon)
        }

        // Fallback final: spawn a ~30 m en dirección aleatoria
        android.util.Log.d("Prankedy", "Fallback final (sin calles encontradas)")
        val angle = Random.nextDouble() * 2 * Math.PI
        return GeoPoint(
            nearPlayer.latitude  + sin(angle) * PRANKEDY_SPAWN_RADIUS,
            nearPlayer.longitude + cos(angle) * PRANKEDY_SPAWN_RADIUS
        )
    }
}
