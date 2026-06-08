package ovh.gabrielhuav.pow.domain.models

/**
 * Máquina de estados del NPC especial Prankedy.
 *
 *  IDLE_WILD  → Spawneó en el mundo, no contratado. Se queda quieto.
 *  AVAILABLE  → Tras el roaming post-muerte, disponible para re-contratar.
 *  FOLLOW     → Contratado: sigue al jugador a velocidad normal (walk).
 *  SPRINT     → Contratado: muy lejos del jugador, corre para alcanzarlo.
 *  AGGRO_RUN  → Detectó un enemigo: corre hacia él con tanque (sprite especial).
 *  ATTACK     → Cerca del objetivo: lanza proyectil (broma).
 *  IN_VEHICLE → Jugador subió a un coche: Prankedy se oculta.
 *  DEAD       → Vida ≤ 0. Cooldown antes de reaparecer.
 *  ROAMING    → Tras respawn, camina por calles antes de ser re-contratado.
 */
enum class PrankedyPhase {
    IDLE_WILD,      // Spawneó — quieto, animación p_idle
    AVAILABLE,      // Tras roaming, disponible para re-contratar (también p_idle)
    FOLLOW,         // Contratado: sigue al jugador (p_walk)
    SPRINT,         // Contratado: lejos del jugador (p_run)
    AGGRO_RUN,      // Persiguiendo enemigo (p_run_tanque)
    ATTACK,         // Lanzando proyectil (p_atack)
    IN_VEHICLE,     // Oculto mientras el jugador conduce
    DEAD,           // Muerto, en cooldown
    ROAMING         // Post-respawn, deambula por calles (p_walk)
}

/**
 * Estado persistente del compañero Prankedy. Vive en el ViewModel; la IA lo
 * lee/escribe cada tick del game loop.
 */
data class PrankedyState(
    val phase: PrankedyPhase = PrankedyPhase.IDLE_WILD,
    val hired: Boolean = false,

    // Posición actual en el mapa (coordenadas GeoPoint-like).
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val rotationAngle: Float = 0f,
    val facingRight: Boolean = true,
    val isMoving: Boolean = false,

    // Vida
    val health: Float = 150f,
    val maxHealth: Float = 150f,

    // Animación
    val frameIndex: Int = 0,
    val lastFrameAdvanceMs: Long = 0L,

    // Combate
    val aggroTargetId: String? = null,  // id del NPC/policía que persigue
    val lastAttackMs: Long = 0L,

    // Cooldowns
    val deathTimeMs: Long = 0L,         // cuándo murió (para calcular respawn)
    val roamingStartMs: Long = 0L,      // cuándo empezó a deambular

    // Diálogo
    val showSpeechBubble: Boolean = false,
    val speechText: String = "",
    val nextDialogueMs: Long = 0L,       // cuándo toca soltar la siguiente frase (contratado)
    val dialogueExpiresMs: Long = 0L,    // cuándo se oculta el globo actual

    // Visibilidad general
    val spawned: Boolean = false
) {
    companion object {
        // ── Parámetros de gameplay ─────────────────────────────────────
        const val FOLLOW_SPEED = 0.0000018       // un poco más rápido que un peatón
        const val SPRINT_SPEED = 0.0000055        // velocidad de sprint (catch-up)
        const val AGGRO_SPEED = 0.0000045          // velocidad al perseguir enemigos

        const val FOLLOW_OFFSET = 0.00008          // ~9 m de separación mínima
        const val SPRINT_THRESHOLD = 0.00045       // ~50 m: si se aleja más, sprint
        const val AGGRO_DETECT_RADIUS = 0.00035    // ~38 m: detecta enemigos
        const val ATTACK_RANGE = 0.00018           // ~20 m: lanza proyectil
        const val SPEECH_RADIUS = 0.00015          // ~17 m: muestra globo de texto

        const val ATTACK_COOLDOWN_MS = 2200L
        const val DEATH_COOLDOWN_MS = 45_000L      // 45 s muerto antes de respawnear
        const val ROAMING_DURATION_MS = 30_000L    // 30 s deambulando antes de re-contratarse
        const val SPAWN_DISTANCE = 0.0005          // ~55 m del jugador al spawnear

        const val PROJECTILE_SPEED = 0.000012      // velocidad del proyectil
        const val PROJECTILE_DAMAGE = 25f
        const val PROJECTILE_LIFETIME_MS = 2500L
        const val PROJECTILE_HIT_RADIUS = 0.00004  // ~4.5 m

        const val FRAME_INTERVAL_MS = 160L

        // Intervalo entre diálogos aleatorios mientras está contratado.
        const val HIRED_DIALOGUE_MIN_MS = 12_000L   // mínimo 12 s entre frases
        const val HIRED_DIALOGUE_MAX_MS = 25_000L   // máximo 25 s entre frases
        const val HIRED_DIALOGUE_DURATION_MS = 4_000L // el globo dura 4 s visible

        val SPEECH_LINES = listOf(
            "¡Cuidado con la cámara, bro!",
            "¿Quieres ver algo chistoso?",
            "Esto va pa' YouTube…",
            "¡La broma del siglo, wey!",
            "No te enojes, es una broma social"
        )

        // Frases adicionales mientras sigue al jugador (contratado).
        val HIRED_SPEECH_LINES = listOf(
            "¿Ya viste a ese wey? Le voy a aventar algo…",
            "Tranqui, yo te cuido las espaldas.",
            "Oye, ¡a la próxima la broma te toca a ti!",
            "¿Sabes qué sería chistoso? Que me pagaras.",
            "Esto es contenido de ORO, créeme.",
            "Mi mamá no sabe que hago esto para vivir.",
            "¡Vámonos! La broma no se hace sola.",
            "Shhh… actúa natural.",
            "A ese de allá se la voy a aplicar bien.",
            "¿Tú crees que YouTube me monetice esto?"
        )
    }
}

/**
 * Proyectil lanzado por Prankedy (un objeto de broma que viaja hacia un NPC).
 */
data class PrankedyProjectile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val latitude: Double,
    val longitude: Double,
    val targetLat: Double,
    val targetLon: Double,
    val dirLat: Double,      // componente normalizado de dirección
    val dirLon: Double,
    val bornAtMs: Long,
    val frameIndex: Int = 0,
    val lastFrameMs: Long = 0L
)