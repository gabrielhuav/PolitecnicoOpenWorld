package ovh.gabrielhuav.pow.domain.models.ai

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Manager de IA del NPC especial Prankedy.
 *
 * Patrón: análogo a [PoliceManager]. Mantiene todo el estado interno; el ViewModel
 * llama a [tick] en cada ciclo del game loop y aplica los efectos ([PrankedyTickResult]).
 *
 * COMPORTAMIENTO (NPC HOSTIL, NO contratable):
 *  - Por defecto persigue al JUGADOR y le lanza el tanque (le baja vida).
 *  - Si detecta a OTRO NPC agrediendo al jugador (aggro / zombi) cerca de él, se
 *    "pone de tu lado" y ataca a ese NPC en su lugar.
 *  - Puede recibir daño y morir; tras un tiempo reaparece.
 */
class PrankedyManager {

    /**
     * Resultado de un tick de IA de Prankedy.
     * @param hitNpcId  ID del NPC al que golpeó el proyectil (null = sin golpe a NPC este tick).
     * @param projectileDamage Daño del proyectil.
     * @param justDied true en el tick en que Prankedy muere.
     * @param hitPlayer true si el proyectil golpeó AL JUGADOR este tick (el VM le baja vida).
     */
    data class PrankedyTickResult(
        val hitNpcId: String? = null,
        val projectileDamage: Float = ATTACK_DAMAGE_PROJECTILE,
        val justDied: Boolean = false,
        val hitPlayer: Boolean = false
    )

    companion object {
        // ── Distancias (en grados, aprox. plana) ────────────────────────────
        const val PRANKEDY_SPAWN_RADIUS    = 0.00035   // ~35 m del jugador al nacer
        const val PRANKEDY_INTERACT_RADIUS = 0.00012   // ~13 m (legado, sin uso activo)
        const val PRANKEDY_PROX_RADIUS     = 0.00018   // ~20 m → muestra burbuja
        const val PRANKEDY_FOLLOW_MIN_DIST = 0.00006   // ~7 m (legado)
        const val PRANKEDY_FOLLOW_MAX_DIST = 0.00035   // ~35 m (legado)

        // ── Velocidades ──────────────────────────────────────────────────────
        const val WALK_SPEED        = 0.0000018
        const val RUN_SPEED         = 0.000005
        const val RUN_TANQUE_SPEED  = 0.0000075
        // Acompañante (HIRED): el jugador corre a 0.000006 (> RUN_SPEED), así que sin esto Prankedy
        // NUNCA te alcanza. CATCH-UP = más rápido cuanto más lejos; WARP = si quedó demasiado lejos
        // (atascado/TP/te fuiste corriendo) se teletransporta a tu lado para SIEMPRE estar contigo.
        const val COMPANION_MAX_SPEED = 0.000013   // tope de alcance (~2.2× correr; supera al jugador)
        const val COMPANION_WARP_DIST = 0.0004     // ~44 m: más lejos que esto → se teletransporta

        // ── Acompañante (fase HIRED): umbrales de seguimiento al jugador ──────
        // Parada MUY cerca para que no se quede rezagado (antes ~10 m → ~3 m).
        const val FOLLOW_STOP_DIST  = 0.00003   // ~3.3 m: pegado al jugador → IDLE
        const val FOLLOW_WALK_DIST  = 0.00011   // ~12 m: camina (p_walk); más lejos corre (p_run)

        // ── Combate ──────────────────────────────────────────────────────────
        const val ATTACK_RADIUS            = 0.00007  // ~8 m → se acerca antes de lanzar el tanque
        const val ATTACK_DAMAGE_PROJECTILE = 22f
        const val ATTACK_COOLDOWN_MS       = 2800L
        const val ATTACK_ANIM_MS           = 800L     // duración de la animación de lanzamiento (p_attack)
        const val PROJECTILE_FLIGHT_MS     = 900L     // tiempo que tarda el proyectil en llegar
        const val IMPACT_RADIUS            = 0.00005  // ~5.5 m: si te alejas del punto de impacto, falla
        // Radio ALREDEDOR DEL JUGADOR para detectar a un NPC que lo esté agrediendo.
        const val AGGRO_DETECT_RADIUS      = 0.00045  // ~50 m
        // CORREA (leash): Prankedy SIEMPRE se mantiene cerca de ti. Si por perseguir a un
        // agresor (o cualquier motivo) quedó más lejos que esto del jugador, deja de
        // perseguir y regresa a tu lado. Evita que "se aleje" cuando llega la policía.
        const val LEASH_MAX                = 0.00030  // ~33 m
        // ANTI-TRABA: si el snap a la calle lo deja "pegado" sin avanzar hacia su objetivo
        // durante STUCK_TIME_MS (se movió menos de STUCK_EPS), se reubica cerca del jugador.
        const val STUCK_EPS                = 0.00002  // ~2 m
        const val STUCK_TIME_MS            = 1500L

        // ── Daño recibido por contacto del NPC que combate ───────────────────
        const val ENEMY_CONTACT_RADIUS      = 0.00004  // ~4.5 m
        const val ENEMY_CONTACT_DAMAGE      = 6f
        const val ENEMY_CONTACT_COOLDOWN_MS = 700L

        // ── Vida y respawn ───────────────────────────────────────────────────
        const val MAX_HEALTH               = 80f
        const val RESPAWN_COOLDOWN_MS      = 60_000L  // 60 s hasta reaparecer
        const val HIRE_PENALTY_MS          = 60_000L  // (legado)

        // ── Frases ───────────────────────────────────────────────────────────
        // Contexto A: Hostilidad / molestia (golpes del jugador, múltiples impactos)
        val HOSTILITY_PHRASES = listOf(
            "¿Otra vez vienes a chingar la madre, cabrón?",
            "Me agarraste de tu puerquito.",
            "Ya estoy hasta la madre aquí, ya parece pinche feria...",
            "La próxima vez que vengas, güey, voy a traer a mi banda... para que te rompan tu madre.",
            "¿Qué me ves la cara o qué, güey?",
            "¡Te gusta andar de pinche payaso, güey!",
            "¿Qué te pasa, ridículo?",
            "Ya me colmaron la pinche paciencia, ya estuvo bien.",
            "¿Qué onda, perros?",
            "Soy el doctor... me puedes decir morenazo de fuego para los cuates.",
            "Se me hace que tienes la mollera sumida.",
            "Soy tu ángel de la guarda y te vengo a cuidar."
        )

        // Contexto B: Interacción con vehículos (choques, subirse por la fuerza)
        val VEHICLE_PHRASES = listOf(
            "¡Se bajan de mi carro ahorita o les parto su madre de una!",
            "¡Ya me tienen hasta la madre los dos cabrones, vienen dando lata!",
            "¡Bájense ya de mi carro, me tienen harto!"
        )

        // Contexto D: Modo Broma / Huida (daño crítico o escapando)
        val FLEE_PHRASES = listOf(
            "¡Es una broma, es una broma! ¡Ahí está la cámara!",
            "Tranquilo, viejo. Relájate.",
            "¡Ah, caray! ¡Soy yo, soy yo!"
        )

        // Frases cuando te defiende de otro NPC (mantener algunas genéricas de apoyo):
        val HIRED_PHRASES = listOf(
            "¡Ese no te toca! 🎯",
            "¡Yo me encargo!",
            "¡Fuera de aquí! 💥",
            "Hoy estoy de tu lado.",
            "¡Nadie te ataca menos yo!",
            "¡Déjalo en paz!"
        )
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
    var respawnAt: Long = 0L       // cuando reaparecer (solo en fase DEAD)
        private set
    var hireableAt: Long = 0L      // (legado)
        private set

    // ── Diálogo flotante ─────────────────────────────────────────────────────
    var currentDialogue: String? = null
        private set
    var dialogueUntil: Long = 0L
        private set

    // ── Objetivo de ataque ───────────────────────────────────────────────────
    private var attackTargetId: String? = null
    private var attackTargetIsPlayer: Boolean = false
    private var attackCooldownUntil: Long = 0L
    private var lastHurtAt: Long = 0L   // cooldown de daño recibido por contacto
    private var snapToRoadFn: ((GeoPoint) -> GeoPoint)? = null  // engancha a la red viaria
    // ANTI-TRABA: última posición de referencia y momento del último avance real.
    private var lastStuckPos: GeoPoint? = null
    private var lastProgressMs: Long = 0L
    // Windup de ataque: primero se reproduce la animación p_attack y AL TERMINAR se lanza el tanque.
    private var attackAnimUntil: Long = 0L
    private var pendingLaunch: Boolean = false
    private var pendingTargetLoc: GeoPoint? = null
    private var pendingTargetId: String? = null
    private var pendingTargetIsPlayer: Boolean = false

    /** Ajusta un punto a la calle más cercana (si el VM proveyó el snapper). */
    private fun snap(p: GeoPoint): GeoPoint = snapToRoadFn?.invoke(p) ?: p

    // ── IA principal ─────────────────────────────────────────────────────────

    /**
     * Ejecuta un tick de IA (~30 Hz).
     * @param playerLoc    Posición actual del jugador.
     * @param npcs         Lista de NPCs presentes en el mundo.
     * @param isDriving    El jugador conduce (Prankedy "sube" y se oculta).
     * @param now          Timestamp actual en ms.
     * @param roadNetwork  Red de calles (para spawn sobre vía).
     * @param snapToRoad   Función para mantenerlo sobre las calles (la pone el VM).
     */
    fun tick(
        playerLoc: GeoPoint,
        npcs: List<Npc>,
        isDriving: Boolean,
        now: Long,
        roadNetwork: List<MapWay>,
        snapToRoad: ((GeoPoint) -> GeoPoint)? = null,
        // Acompañante (HIRED): si el jugador corre, Prankedy iguala la velocidad (p_run).
        playerRunning: Boolean = false,
        // catchup = true (acompañante): alcance rápido + warp a tu lado si quedó lejos. false: seguimiento
        // normal SIN catch-up/warp (lo usa la HUIDA de la Misión 2 a la puerta, que debe ser LENTA).
        catchup: Boolean = true
    ): PrankedyTickResult {
        snapToRoadFn = snapToRoad
        // Si el jugador va en coche, Prankedy desaparece visualmente (sin perder su estado).
        if (isDriving) {
            animState = PrankedyAnimState.IDLE
            return PrankedyTickResult()
        }

        if (phase == PrankedyPhase.DEAD) {
            if (now >= respawnAt) respawn(playerLoc, roadNetwork, now)
            return PrankedyTickResult()
        }

        val loc = location ?: return PrankedyTickResult()

        // ACOMPAÑANTE (HIRED): modo seguidor pacífico. Te sigue (camina/corre), sin atacarte
        // ni lanzar el tanque. Solo se usa en la campaña ENCB (ver maybeSpawnPrankedyCompanion).
        if (phase == PrankedyPhase.HIRED) return tickFollow(loc, playerLoc, now, playerRunning, catchup)

        // ANTI-TRABA: si está LEJOS del jugador (debería estar moviéndose) y no avanza en
        // STUCK_TIME_MS, se reubica cerca de ti sobre la calle. NO se cura (conserva vida y
        // estado): solo lo despega para que vuelva a hacerte algo en vez de quedarse trabado.
        if (dist(loc, playerLoc) > ATTACK_RADIUS) {
            if (lastStuckPos == null || dist(loc, lastStuckPos!!) > STUCK_EPS) {
                lastStuckPos = loc; lastProgressMs = now
            } else if (now - lastProgressMs > STUCK_TIME_MS) {
                relocateNear(playerLoc, roadNetwork)
                lastStuckPos = location; lastProgressMs = now
                return PrankedyTickResult()
            }
        } else {
            lastStuckPos = loc; lastProgressMs = now
        }

        return tickCombat(loc, playerLoc, npcs, now)
    }

    /** Reubica a Prankedy cerca del jugador sobre la calle SIN curarlo (anti-traba). */
    private fun relocateNear(playerLoc: GeoPoint, roadNetwork: List<MapWay>) {
        location = findSpawnPoint(playerLoc, roadNetwork)
        projectileActive = false
        attackAnimUntil = 0L
        pendingLaunch = false
        pendingTargetLoc = null
    }

    /**
     * Lógica HOSTIL: ataca al jugador; si hay un NPC agrediendo al jugador, lo ataca a él.
     */
    private fun tickCombat(
        loc: GeoPoint,
        playerLoc: GeoPoint,
        npcs: List<Npc>,
        now: Long
    ): PrankedyTickResult {
        // 1. Resolver proyectil en vuelo
        var hitNpcId: String? = null
        var hitPlayer = false
        if (projectileActive && now - projectileStartMs >= PROJECTILE_FLIGHT_MS) {
            projectileActive = false
            if (attackTargetIsPlayer) {
                // El tanque cae donde fue lanzado. Solo te pega si SIGUES cerca de ese
                // punto; si te moviste a tiempo, falla (lo esquivaste).
                val tgt = projectileTarget
                if (tgt != null && dist(playerLoc, tgt) <= IMPACT_RADIUS) hitPlayer = true
            } else {
                attackTargetId?.let { hitNpcId = it }
            }
        }

        // 1b. WINDUP: mientras corre la animación p_attack, se queda QUIETO (no se mueve ni
        //     re-evalúa objetivo). Así se ve la secuencia de lanzamiento completa.
        if (now < attackAnimUntil) {
            animState = PrankedyAnimState.ATTACK
            pendingTargetLoc?.let { facingRight = it.longitude >= loc.longitude }
            tickDialogue(now)
            return PrankedyTickResult(hitNpcId = hitNpcId, hitPlayer = hitPlayer)
        }

        // 1c. Terminó el windup → SOLTAR el tanque (p_objeto) hacia el objetivo capturado.
        if (pendingLaunch) {
            pendingLaunch = false
            val tgt = pendingTargetLoc
            if (tgt != null) {
                projectileStart = GeoPoint(loc.latitude, loc.longitude)
                projectileTarget = GeoPoint(tgt.latitude, tgt.longitude)
                projectileStartMs = now
                projectileActive = true
                attackTargetId = pendingTargetId
                attackTargetIsPlayer = pendingTargetIsPlayer
            }
        }

        // 2. ¿Hay un NPC AGREDIENDO al jugador? Si lo hay, Prankedy se pone de tu lado.
        //    LEASH: si Prankedy quedó lejos del jugador, IGNORA al agresor y vuelve a ti
        //    (así no "se aleja" persiguiendo, p. ej. cuando llega la policía).
        val defender = if (dist(loc, playerLoc) > LEASH_MAX) null
                       else findAggressorNearPlayer(playerLoc, npcs, now)

        // 2b. Daño RECIBIDO: si está peleando cuerpo a cuerpo con ese NPC, lo lastima.
        if (defender != null && now - lastHurtAt >= ENEMY_CONTACT_COOLDOWN_MS &&
            dist(loc, defender.location) <= ENEMY_CONTACT_RADIUS) {
            lastHurtAt = now
            if (takeDamage(ENEMY_CONTACT_DAMAGE, now)) {
                return PrankedyTickResult(justDied = true)
            }
        }

        // 3. Elegir objetivo: el agresor (defensa) o, por defecto, el JUGADOR.
        val targetLoc: GeoPoint
        val targetId: String?
        val targetIsPlayer: Boolean
        if (defender != null) {
            targetLoc = defender.location; targetId = defender.id; targetIsPlayer = false
        } else {
            targetLoc = playerLoc; targetId = null; targetIsPlayer = true
        }

        val distToTarget = dist(loc, targetLoc)
        when {
            distToTarget <= ATTACK_RADIUS && now >= attackCooldownUntil && !projectileActive -> {
                // ─ INICIAR WINDUP: primero la animación de lanzamiento (p_attack); el tanque
                //   (p_objeto) se suelta AL TERMINAR la animación (ver paso "3.b" arriba).
                animState = PrankedyAnimState.ATTACK
                attackAnimUntil = now + ATTACK_ANIM_MS
                attackCooldownUntil = now + ATTACK_COOLDOWN_MS
                pendingLaunch = true
                pendingTargetLoc = GeoPoint(targetLoc.latitude, targetLoc.longitude)
                pendingTargetId = targetId
                pendingTargetIsPlayer = targetIsPlayer
                facingRight = targetLoc.longitude >= loc.longitude
                val isCritical = health < MAX_HEALTH * 0.25f
                triggerDialogue(
                    pickContextualPhrase(targetIsPlayer, isInVehicleContext = false, isCriticalHealth = isCritical),
                    now, 2500L
                )
            }
            distToTarget > ATTACK_RADIUS -> {
                // ─ CORRER CON TANQUE hacia el objetivo
                animState = PrankedyAnimState.RUN_TANQUE
                // Paso recto hacia el objetivo y, por defecto, enganchado a la calle. PERO si
                // el snap NO acerca al objetivo (te pega a un nodo que no progresa → se traba),
                // usa el paso DIRECTO ese tick para no quedarte atorado en la red.
                val raw = stepToward(loc, targetLoc, RUN_TANQUE_SPEED)
                val snapped = snap(raw)
                location = if (dist(snapped, targetLoc) <= dist(loc, targetLoc) - RUN_TANQUE_SPEED * 0.25)
                    snapped else raw
                facingRight = targetLoc.longitude >= loc.longitude
            }
            else -> {
                // En rango pero recargando → IDLE
                animState = PrankedyAnimState.IDLE
            }
        }

        tickDialogue(now)
        return PrankedyTickResult(hitNpcId = hitNpcId, hitPlayer = hitPlayer)
    }

    /**
     * Lógica ACOMPAÑANTE (fase HIRED): Prankedy te sigue de forma pacífica. Nunca te ataca
     * ni lanza el tanque. CORRE (p_run, RUN_SPEED) si el jugador corre o si está lejos; si no,
     * CAMINA (p_walk); si ya está pegado (FOLLOW_STOP_DIST ~3 m) se queda IDLE. Su trayectoria
     * está ESTRICTAMENTE restringida a la red vial (snap a calle SIEMPRE, sin atajos por
     * césped/edificios). La orientación del sprite se calcula con el VECTOR DE MOVIMIENTO real
     * (no la posición del jugador) para que mire hacia donde avanza y no parezca caminar al revés.
     */
    private fun tickFollow(loc: GeoPoint, playerLoc: GeoPoint, now: Long, playerRunning: Boolean, catchup: Boolean): PrankedyTickResult {
        projectileActive = false
        val d = dist(loc, playerLoc)
        // SIEMPRE A TU LADO (solo acompañante, catchup=true): si quedó DEMASIADO lejos (atascado,
        // teletransporte, o te fuiste lejos), se teletransporta a tu lado en vez de tardar en alcanzarte.
        if (catchup && d > COMPANION_WARP_DIST) {
            location = snap(playerLoc)
            animState = PrankedyAnimState.RUN
            tickDialogue(now)
            return PrankedyTickResult()
        }
        if (d > FOLLOW_STOP_DIST) {
            // Corre si el jugador corre o si está lejos.
            val running = playerRunning || d > FOLLOW_WALK_DIST
            // ACOMPAÑANTE lejos → CATCH-UP (más rápido cuanto más lejos, por encima de tu correr) para
            // alcanzarte aunque corras o te subas a un coche. Sin catchup (huida Misión 2) → correr normal.
            val speed = when {
                catchup && d > FOLLOW_WALK_DIST -> {
                    val over = ((d - FOLLOW_WALK_DIST) / FOLLOW_WALK_DIST).toFloat().coerceIn(0f, 6f)
                    (RUN_SPEED * (1.6f + over)).coerceAtMost(COMPANION_MAX_SPEED)
                }
                running -> RUN_SPEED
                else -> WALK_SPEED
            }
            animState = if (running) PrankedyAnimState.RUN else PrankedyAnimState.WALK
            // ROAD-ONLY ESTRICTO: el siguiente punto se proyecta SIEMPRE sobre la red vial
            // (snap), de modo que Prankedy solo pisa calles/banquetas transitables.
            val newLoc = snap(stepToward(loc, playerLoc, speed))
            // Orientación por el ángulo del DESPLAZAMIENTO real: atan2(dLat, dLon). El sprite
            // solo se voltea en horizontal → mira a la derecha si avanzó hacia el este (cos>0).
            val moveLon = newLoc.longitude - loc.longitude
            val moveLat = newLoc.latitude - loc.latitude
            if (kotlin.math.abs(moveLon) > 1e-9 || kotlin.math.abs(moveLat) > 1e-9) {
                facingRight = kotlin.math.cos(kotlin.math.atan2(moveLat, moveLon)) >= 0.0
            }
            location = newLoc
        } else {
            animState = PrankedyAnimState.IDLE
        }
        tickDialogue(now)
        return PrankedyTickResult()   // acompañante: nunca golpea al jugador ni a NPCs
    }

    /** Limpia el diálogo si ya expiró. */
    private fun tickDialogue(now: Long) {
        if (currentDialogue != null && now > dialogueUntil) {
            currentDialogue = null
        }
    }

    /**
     * Busca el NPC que esté AGREDIENDO al jugador (con aggro o zombi) más cercano al
     * jugador. Si existe, Prankedy lo ataca en lugar de atacarte a ti.
     */
    private fun findAggressorNearPlayer(playerLoc: GeoPoint, npcs: List<Npc>, now: Long): Npc? {
        return npcs
            .filter { npc ->
                npc.displayName.isNullOrEmpty() &&   // solo NPCs reales, no jugadores remotos
                npc.type != NpcType.CAR &&
                npc.type != NpcType.POLICE_CAR &&
                npc.type != NpcType.POLICE_COP &&   // NO persigue a la policía (se alejaría de ti)
                npc.health > 0 &&
                !npc.isDying &&
                (npc.aggroUntil > now || npc.type == NpcType.ZOMBIE) &&  // está agrediendo al jugador
                dist(npc.location, playerLoc) <= AGGRO_DETECT_RADIUS
            }
            .minByOrNull { dist(it.location, playerLoc) }
    }

    // ── API pública ──────────────────────────────────────────────────────────

    /**
     * Coloca a Prankedy en el mundo, cerca del jugador, sobre la red viaria.
     * Se llama al iniciar la partida o tras un respawn.
     */
    fun spawn(nearPlayer: GeoPoint, roadNetwork: List<MapWay>, now: Long = System.currentTimeMillis()) {
        val spawnPoint = findSpawnPoint(nearPlayer, roadNetwork)
        location = spawnPoint
        health = MAX_HEALTH
        phase = PrankedyPhase.NOT_HIRED   // = activo / hostil
        animState = PrankedyAnimState.IDLE
        projectileActive = false
        attackTargetId = null
        attackTargetIsPlayer = false
        attackCooldownUntil = 0L
        attackAnimUntil = 0L
        pendingLaunch = false
        pendingTargetLoc = null
        currentDialogue = null
        dialogueUntil = 0L
    }

    /**
     * Spawnea a Prankedy como ACOMPAÑANTE (fase HIRED): aparece cerca del jugador, sobre la
     * calle, y empieza a seguirte sin atacarte. Lo usa SOLO la campaña ENCB
     * (ver WorldMapPrankedy.maybeSpawnPrankedyCompanion).
     */
    fun spawnCompanion(nearPlayer: GeoPoint, roadNetwork: List<MapWay>, now: Long = System.currentTimeMillis()) {
        spawn(nearPlayer, roadNetwork, now)
        phase = PrankedyPhase.HIRED
        animState = PrankedyAnimState.IDLE
    }

    /** Reubica a Prankedy en `loc` de inmediato (teletransporte del jugador / reintento de misión).
     *  `location` tiene private set, por eso se expone este método. tickFollow lo ajusta a la calle. */
    fun warpTo(loc: GeoPoint) { location = loc }

    /** (Legado, sin uso: ya no es contratable). */
    fun hire() { /* no-op: Prankedy ahora es un NPC hostil */ }

    /** Desactiva a Prankedy (lo quita del mapa). Reaparecerá al reactivarlo. */
    fun deactivate() {
        location = null
        phase = PrankedyPhase.NOT_HIRED
        projectileActive = false
        attackAnimUntil = 0L
        pendingLaunch = false
        pendingTargetLoc = null
        currentDialogue = null
    }

    /** Aplica daño a Prankedy. Devuelve true si acaba de morir. */
    fun takeDamage(amount: Float, now: Long = System.currentTimeMillis()): Boolean {
        if (phase == PrankedyPhase.DEAD) return false
        health = (health - amount).coerceAtLeast(0f)
        // Frase de pánico al entrar en salud crítica (< 25%)
        if (health > 0f && health < MAX_HEALTH * 0.25f) {
            triggerDialogue(FLEE_PHRASES.random(), now, 2500L)
        }
        if (health <= 0f) {
            phase = PrankedyPhase.DEAD
            respawnAt = now + RESPAWN_COOLDOWN_MS
            hireableAt = now + RESPAWN_COOLDOWN_MS + HIRE_PENALTY_MS
            location = null
            projectileActive = false
            return true
        }
        return false
    }

    /** Hook: interacción vehicular cerca de Prankedy (choque, carjack). */
    fun onVehicleInteraction(now: Long = System.currentTimeMillis()) {
        if (phase != PrankedyPhase.DEAD && currentDialogue == null) {
            triggerDialogue(VEHICLE_PHRASES.random(), now, 3000L)
        }
    }

    /** Hook: el jugador recibió daño (lo notifica el VM). Suelta una frase de defensa. */
    fun onPlayerDamaged() {
        if (phase != PrankedyPhase.DEAD) {
            triggerDialogue(HIRED_PHRASES.random(), System.currentTimeMillis(), 2000L)
        }
    }

    /** Determina qué frase decir según el contexto. */
    private fun pickContextualPhrase(
        targetIsPlayer: Boolean,
        isInVehicleContext: Boolean,
        isCriticalHealth: Boolean
    ): String {
        return when {
            isCriticalHealth -> FLEE_PHRASES.random()
            isInVehicleContext -> VEHICLE_PHRASES.random()
            targetIsPlayer -> HOSTILITY_PHRASES.random()
            else -> HIRED_PHRASES.random()
        }
    }

    /** Estado de la IA de combate. */
    fun isHireable(now: Long = System.currentTimeMillis()): Boolean = false

    /** (Legado) */
    fun hireableInSeconds(now: Long = System.currentTimeMillis()): Int = 0

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

    /** Reaparece cuando expira el timer de muerte. */
    private fun respawn(nearPlayer: GeoPoint, roadNetwork: List<MapWay>, now: Long) {
        spawn(nearPlayer, roadNetwork, now)
    }

    /**
     * Busca un punto de spawn sobre la red viaria cerca del jugador.
     * Si no encuentra calles, usa un offset fijo alrededor del jugador.
     */
    private fun findSpawnPoint(nearPlayer: GeoPoint, roadNetwork: List<MapWay>): GeoPoint {
        if (roadNetwork.isEmpty()) {
            val angle = Random.nextDouble() * 2 * Math.PI
            return GeoPoint(
                nearPlayer.latitude  + sin(angle) * PRANKEDY_SPAWN_RADIUS,
                nearPlayer.longitude + cos(angle) * PRANKEDY_SPAWN_RADIUS
            )
        }

        val nearby = roadNetwork.filter { way ->
            way.nodes.any { node ->
                val d = dist(GeoPoint(node.lat, node.lon), nearPlayer)
                d in PRANKEDY_SPAWN_RADIUS * 0.4..PRANKEDY_SPAWN_RADIUS * 1.6
            }
        }
        if (nearby.isNotEmpty()) {
            val way = nearby.random()
            val node = way.nodes.random()
            return GeoPoint(node.lat, node.lon)
        }

        val allNodes = roadNetwork.flatMap { it.nodes }
        val nearestNode = allNodes.minByOrNull { dist(GeoPoint(it.lat, it.lon), nearPlayer) }
        if (nearestNode != null) {
            return GeoPoint(nearestNode.lat, nearestNode.lon)
        }

        val angle = Random.nextDouble() * 2 * Math.PI
        return GeoPoint(
            nearPlayer.latitude  + sin(angle) * PRANKEDY_SPAWN_RADIUS,
            nearPlayer.longitude + cos(angle) * PRANKEDY_SPAWN_RADIUS
        )
    }
}
