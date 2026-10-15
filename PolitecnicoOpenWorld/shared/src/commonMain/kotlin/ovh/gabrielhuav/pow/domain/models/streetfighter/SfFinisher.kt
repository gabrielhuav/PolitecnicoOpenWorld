package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.math.PI
import kotlin.math.sin

// ────────────────────────────────────────────────────────────────────────────
// 🆕 REMATE FINAL estilo Mortal Kombat ("¡ACÁBALO!") — DOMINIO PURO (sin Android ni Compose).
//
// ⚠️ NO confundir con el FATALITY que ya existía (SfFighterState.FATALITY): ese es un
// "poder súper especial" que se lanza EN CUALQUIER MOMENTO de la pelea (correr + R2). El
// REMATE solo existe al FINAL del combate:
//   1. El golpe que decide el combate (2ª ronda ganada) NO tumba al rival: lo deja de pie,
//      mareado y con 0 de vida, y aparece "ACABALO" durante [SfFinisher.WINDOW_MS].
//   2. Si el ganador mete el COMANDO de su personaje a la DISTANCIA correcta, arranca una
//      cinemática guionizada (beats) con efectos procedurales (oscurecer, tintar, levitar…).
//   3. Si se acaba el tiempo o lo remata con un golpe normal → KO clásico, como siempre.
//
// Todo lo de este archivo es determinista y testeable en commonTest (ver SfFinisherTest).
// El VM solo orquesta (StreetFighterRemate.kt) y el renderer solo pinta (SfSceneRenderer.kt).
// No se agregó NINGÚN SfFighterState nuevo: la cinemática reutiliza arte que los 18 peleadores
// empacados ya tienen (TAUNT, SUPER_ART, FATALITY, STUN, HURT_*, KO, VICTORY).
// ────────────────────────────────────────────────────────────────────────────

object SfFinisher {
    /** Interruptor global (por si el profe quiere apagarlo sin borrar código). */
    const val ENABLED = true

    /** Cuánto dura el "ACABALO" para meter el comando. */
    const val WINDOW_MS = 5000L

    /** Ventana para encadenar las direcciones del comando antes del botón (tolerante al táctil). */
    const val INPUT_WINDOW_MS = 1800L

    /** Máximo de tokens de dirección que se recuerdan (suficiente para comandos de 3-4). */
    const val MAX_HISTORY = 12

    /** Mostrar la pista del comando en pantalla (en MK original no se muestra). */
    const val SHOW_COMMAND_HINT = true
}

/** Piezas de un comando. Las direcciones son RELATIVAS al encaramiento (ADELANTE = hacia el rival). */
enum class SfFinisherToken(val hudEs: String, val hudEn: String) {
    UP("ARRIBA", "UP"),
    DOWN("ABAJO", "DOWN"),
    FORWARD("ADELANTE", "FWD"),
    BACK("ATRAS", "BACK"),
    // Botones físicos del diamante: X / Y / B = puños; A = patada (cualquier fuerza).
    PUNCH_LIGHT("X", "X"),
    PUNCH_MEDIUM("Y", "Y"),
    PUNCH_HEAVY("B", "B"),
    KICK("A", "A"),
    ;

    val isDirection: Boolean get() = this == UP || this == DOWN || this == FORWARD || this == BACK
}

/** Distancia a la que hay que estar del rival para que el comando cuente (px de mundo). */
enum class SfFinisherRange(val minPx: Float, val maxPx: Float, val hudEs: String, val hudEn: String) {
    CERCA(0f, 105f, "CERCA", "CLOSE"),
    LEJOS(145f, Float.MAX_VALUE, "LEJOS", "FAR"),
    CUALQUIERA(0f, Float.MAX_VALUE, "", ""),
    ;

    fun contains(distancePx: Float): Boolean = distancePx >= minPx && distancePx <= maxPx

    /**
     * 🆕 EXAMEN EXTRAORDINARIO: tramo del PISO (x de mundo) donde tiene que pararse el
     * atacante para que el comando cuente — es donde se tiende la jerga. Se recorta al
     * escenario y nunca se mete debajo de la víctima ([SfFinisherPractice.MIN_GAP_PX]).
     */
    fun zone(victimX: Float, attackerOnLeft: Boolean, stageMin: Float, stageMax: Float): SfFinisherZone {
        val near = maxOf(minPx, SfFinisherPractice.MIN_GAP_PX)
        val far = minOf(maxPx, stageMax - stageMin)
        val (a, b) = if (attackerOnLeft) (victimX - far) to (victimX - near) else (victimX + near) to (victimX + far)
        val lo = a.coerceIn(stageMin, stageMax)
        val hi = b.coerceIn(stageMin, stageMax)
        return SfFinisherZone(minX = minOf(lo, hi), maxX = maxOf(lo, hi))
    }
}

/** 🆕 Tramo del piso [minX, maxX] (x de mundo) donde se tiende la jerga. */
data class SfFinisherZone(val minX: Float, val maxX: Float) {
    val width: Float get() = maxX - minX
    fun contains(x: Float): Boolean = x in minX..maxX
}

data class SfFinisherCommand(
    val directions: List<SfFinisherToken>,
    val button: SfFinisherToken,
    val range: SfFinisherRange,
) {
    init {
        require(directions.all { it.isDirection }) { "Las direcciones del comando deben ser direcciones" }
        require(!button.isDirection) { "El remate termina con un BOTÓN" }
    }

    /**
     * 🆕 EXAMEN EXTRAORDINARIO: el comando con FLECHAS FÍSICAS para el panel de pasos.
     * El input ya es relativo (ADELANTE = hacia el rival), así que el comando funciona igual
     * de los dos lados; lo único que cambia es CÓMO SE DIBUJA: si el peleador mira a la
     * IZQUIERDA (está a la derecha del rival), ADELANTE se pinta ← y ATRÁS →.
     * El último elemento es la letra del botón.
     */
    fun physicalSteps(facingRight: Boolean): List<String> =
        directions.map { d ->
            when (d) {
                SfFinisherToken.UP -> "↑"
                SfFinisherToken.DOWN -> "↓"
                SfFinisherToken.FORWARD -> if (facingRight) "→" else "←"
                SfFinisherToken.BACK -> if (facingRight) "←" else "→"
                else -> ""
            }
        } + button.hudEs

    /** Pista para la fuente arcade del HUD (solo A-Z, 0-9 y espacio): "ABAJO ABAJO ATRAS B CERCA". */
    fun hudHint(english: Boolean): String {
        val parts = directions.map { if (english) it.hudEn else it.hudEs } +
            (if (english) button.hudEn else button.hudEs) +
            (if (english) range.hudEn else range.hudEs)
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }
}

/** Estilo visual de la cinemática (decide colores, curvas y partículas). */
enum class SfFinisherFx {
    /** La Tzitzimime: se apaga el sol, la víctima levita y las estrellas la devoran. */
    NOCHE_SIN_SOL,

    /** La Llorona: el agua la jala hacia abajo hasta desaparecer. */
    RIO,

    /** El Charro Negro: la víctima arde y queda hecha ceniza. */
    PACTO,
}

/**
 * Un "golpe de guion" de la cinemática. En [atMs] (desde que arranca) se aplican los cambios
 * no nulos. Los estados se FUERZAN (sin validFrom); si el peleador no tiene el arte, el VM
 * cae a un estado de respaldo — nunca se congela sin animación.
 */
data class SfFinisherBeat(
    val atMs: Long,
    val attackerState: SfFighterState? = null,
    val victimState: SfFighterState? = null,
    /** Clip de STREETFIGHTER/SOUNDS (sin extensión) con la voz del atacante. */
    val attackerVoice: String? = null,
    /** SFX genérico del tema (heavy-punch-hit, hadouken, land…). */
    val sfx: String? = null,
    /** La víctima se queja (voz de daño de su pack). */
    val victimHurtVoice: Boolean = false,
)

data class SfFinisherDef(
    val fighter: SfFighterId,
    val nameEs: String,
    val nameEn: String,
    val command: SfFinisherCommand,
    val fx: SfFinisherFx,
    val durationMs: Long,
    val beats: List<SfFinisherBeat>,
    /** Si no es null, al arrancar el atacante se coloca a esta distancia de la víctima. */
    val attackerGapPx: Float? = null,
) {
    init {
        require(beats.zipWithNext().all { (a, b) -> a.atMs <= b.atMs }) { "Beats fuera de orden en $fighter" }
        require(beats.all { it.atMs in 0L..durationMs }) { "Beat fuera de la duración en $fighter" }
    }
}

/**
 * Catálogo de remates. Para AGREGAR uno nuevo: añade una entrada aquí (comando + guion + fx);
 * el motor y el renderer ya lo soportan. Peleadores sin entrada → KO clásico (sin ventana).
 */
object SfFinisherCatalog {

    val all: List<SfFinisherDef> = listOf(
        // ── La Tzitzimime ("la Muerte"): las tzitzimime son las estrellas-demonio que bajan a
        //    devorar a la gente cuando el sol se apaga. Comando: ↓ ↓ ← + B, CERCA.
        SfFinisherDef(
            fighter = SfFighterId.LA_TZITZIMIME,
            nameEs = "NOCHE SIN SOL",
            nameEn = "SUNLESS NIGHT",
            command = SfFinisherCommand(
                directions = listOf(SfFinisherToken.DOWN, SfFinisherToken.DOWN, SfFinisherToken.BACK),
                button = SfFinisherToken.PUNCH_HEAVY,
                range = SfFinisherRange.CERCA,
            ),
            fx = SfFinisherFx.NOCHE_SIN_SOL,
            durationMs = 5600L,
            attackerGapPx = 70f,
            beats = listOf(
                SfFinisherBeat(0L, attackerState = SfFighterState.TAUNT, victimState = SfFighterState.STUN,
                    attackerVoice = "special_la_tzitzimime_power"),
                SfFinisherBeat(900L, attackerState = SfFighterState.SUPER_ART,
                    victimState = SfFighterState.HURT_BODY_HEAVY, sfx = "heavy-punch-hit", victimHurtVoice = true),
                SfFinisherBeat(1600L, victimState = SfFighterState.STUN, sfx = "hadouken"),
                SfFinisherBeat(3200L, attackerState = SfFighterState.FATALITY,
                    victimState = SfFighterState.HURT_HEAD_HEAVY, sfx = "heavy-kick-hit"),
                SfFinisherBeat(4300L, victimState = SfFighterState.KO),
                SfFinisherBeat(4500L, attackerState = SfFighterState.VICTORY,
                    attackerVoice = "special_la_tzitzimime_win"),
            ),
        ),
        // ── La Llorona: te arrastra al río. Comando: ← → ← + A (patada), LEJOS.
        SfFinisherDef(
            fighter = SfFighterId.LA_LLORONA,
            nameEs = "EL RIO TE LLEVA",
            nameEn = "THE RIVER TAKES YOU",
            command = SfFinisherCommand(
                directions = listOf(SfFinisherToken.BACK, SfFinisherToken.FORWARD, SfFinisherToken.BACK),
                button = SfFinisherToken.KICK,
                range = SfFinisherRange.LEJOS,
            ),
            fx = SfFinisherFx.RIO,
            durationMs = 5200L,
            beats = listOf(
                SfFinisherBeat(0L, attackerState = SfFighterState.SPECIAL_1_HEAVY, victimState = SfFighterState.STUN,
                    attackerVoice = "special_llorona_power"),
                SfFinisherBeat(700L, victimState = SfFighterState.HURT_HEAD_HEAVY, sfx = "hadouken",
                    victimHurtVoice = true),
                SfFinisherBeat(1500L, attackerState = SfFighterState.SUPER_ART, victimState = SfFighterState.STUN),
                SfFinisherBeat(3500L, victimState = SfFighterState.KO, sfx = "land"),
                SfFinisherBeat(3700L, attackerState = SfFighterState.TAUNT),
            ),
        ),
        // ── El Charro Negro: cobra el pacto. Comando: → ↓ → + Y, CERCA.
        SfFinisherDef(
            fighter = SfFighterId.CHARRO_NEGRO,
            nameEs = "PACTO COBRADO",
            nameEn = "DEBT COLLECTED",
            command = SfFinisherCommand(
                directions = listOf(SfFinisherToken.FORWARD, SfFinisherToken.DOWN, SfFinisherToken.FORWARD),
                button = SfFinisherToken.PUNCH_MEDIUM,
                range = SfFinisherRange.CERCA,
            ),
            fx = SfFinisherFx.PACTO,
            durationMs = 5200L,
            attackerGapPx = 62f,
            beats = listOf(
                // Abre con la BURLA, no con TALK: los cuadros de TALK de TODOS los packs traen el
                // origen en y=128 (los pies están en y=224) y el sprite se hunde 96 px en el piso.
                // Defecto preexistente de datos; ver el test "ningun Extraordinario usa TALK".
                SfFinisherBeat(0L, attackerState = SfFighterState.TAUNT, victimState = SfFighterState.STUN,
                    attackerVoice = "special_charro_negro"),
                SfFinisherBeat(1000L, attackerState = SfFighterState.HEAVY_PUNCH,
                    victimState = SfFighterState.HURT_BODY_HEAVY, sfx = "heavy-punch-hit", victimHurtVoice = true),
                SfFinisherBeat(1600L, attackerState = SfFighterState.SUPER_ART, victimState = SfFighterState.STUN,
                    sfx = "hadouken"),
                SfFinisherBeat(3900L, victimState = SfFighterState.KO),
                SfFinisherBeat(4200L, attackerState = SfFighterState.VICTORY),
            ),
        ),
    )

    private val byFighter: Map<SfFighterId, SfFinisherDef> = all.associateBy { it.fighter }

    fun forFighter(id: SfFighterId): SfFinisherDef? = byFighter[id]
}

/**
 * Lector del comando. Se alimenta con el [SfInput] YA RESUELTO de cada tick (el mismo que usa la
 * máquina de estados), así funciona igual con joystick táctil, gamepad o teclado.
 *
 * TOLERANCIA TÁCTIL: las direcciones solo cuentan al ENTRAR a una dirección cardinal (flanco),
 * las diagonales no emiten token, y el comando se busca como SUBSECUENCIA ordenada dentro de
 * [windowMs] (se permite "ruido" entre medio). El botón debe llegar al final.
 */
class SfFinisherInputTracker(private val windowMs: Long = SfFinisher.INPUT_WINDOW_MS) {

    private val history = ArrayDeque<Pair<SfFinisherToken, Long>>()
    private var lastZone: Zone = Zone.NEUTRAL

    private enum class Zone { NEUTRAL, UP, DOWN, FORWARD, BACK, DIAGONAL }

    fun reset() {
        history.clear()
        lastZone = Zone.NEUTRAL
    }

    /**
     * 🆕 EXAMEN EXTRAORDINARIO: cuántas direcciones de [command] ya están metidas EN ORDEN
     * dentro de la ventana (para palomear el panel de pasos). No consume nada.
     */
    fun progress(command: SfFinisherCommand, now: Long): Int {
        var j = 0
        for ((token, at) in history) {
            if (now - at > windowMs) continue
            if (j < command.directions.size && token == command.directions[j]) j++
        }
        return j
    }

    /** Direcciones registradas (para tests/depuración). */
    fun recordedDirections(): List<SfFinisherToken> = history.map { it.first }

    /** Alimenta UN tick. Devuelve true si en ESTE tick se completó [command] (sin checar distancia). */
    fun feed(input: SfInput, now: Long, command: SfFinisherCommand): Boolean {
        val zone = zoneOf(input)
        if (zone != lastZone) {
            tokenFor(zone)?.let { history.addLast(it to now) }
            lastZone = zone
        }
        while (history.isNotEmpty() && now - history.first().second > windowMs) history.removeFirst()
        while (history.size > SfFinisher.MAX_HISTORY) history.removeFirst()

        if (command.button !in buttonsOf(input)) return false
        if (!containsInOrder(history.map { it.first }, command.directions)) return false
        history.clear() // consumido: no se re-dispara en el siguiente tick
        return true
    }

    private fun zoneOf(i: SfInput): Zone {
        val vertical = i.up || i.down
        val horizontal = i.forward || i.backward
        return when {
            vertical && horizontal -> Zone.DIAGONAL
            i.up -> Zone.UP
            i.down -> Zone.DOWN
            i.forward -> Zone.FORWARD
            i.backward -> Zone.BACK
            else -> Zone.NEUTRAL
        }
    }

    private fun tokenFor(zone: Zone): SfFinisherToken? = when (zone) {
        Zone.UP -> SfFinisherToken.UP
        Zone.DOWN -> SfFinisherToken.DOWN
        Zone.FORWARD -> SfFinisherToken.FORWARD
        Zone.BACK -> SfFinisherToken.BACK
        Zone.NEUTRAL, Zone.DIAGONAL -> null
    }

    private fun buttonsOf(i: SfInput): Set<SfFinisherToken> {
        val out = HashSet<SfFinisherToken>(2)
        // Si el cuarto de círculo detectó un "hadouken", el puño sigue contando como su fuerza.
        val special = i.special
        if (i.lightPunch || special == SfAttackStrength.LIGHT) out += SfFinisherToken.PUNCH_LIGHT
        if (i.mediumPunch || special == SfAttackStrength.MEDIUM) out += SfFinisherToken.PUNCH_MEDIUM
        if (i.heavyPunch || special == SfAttackStrength.HEAVY) out += SfFinisherToken.PUNCH_HEAVY
        if (i.lightKick || i.mediumKick || i.heavyKick) out += SfFinisherToken.KICK
        return out
    }

    companion object {
        /** ¿[needle] aparece dentro de [hay] en el mismo orden (con huecos permitidos)? */
        fun containsInOrder(hay: List<SfFinisherToken>, needle: List<SfFinisherToken>): Boolean {
            var j = 0
            for (t in hay) {
                if (j < needle.size && t == needle[j]) j++
            }
            return j == needle.size
        }
    }
}

/** Probabilidad de que la CPU ganadora ejecute su remate (si no, remata con un golpe normal). */
object SfFinisherCpu {
    /** Pausa dramática antes de que la CPU lo ejecute. */
    const val THINK_MS = 900L

    /** Si no alcanza la distancia (pared), lo lanza igual cuando falte esto para cerrar la ventana. */
    const val DESPERATION_MS = 1200L

    fun chance(difficulty: SfCpuDifficulty): Float = when (difficulty) {
        SfCpuDifficulty.BASICA -> 0.30f
        SfCpuDifficulty.NORMAL -> 0.60f
        SfCpuDifficulty.AVANZADA -> 0.85f
        SfCpuDifficulty.PESADILLA -> 1.00f
    }
}

/**
 * Lo que el renderer necesita para pintar UN cuadro del remate. Colores en ARGB `Long`
 * (0xAARRGGBB) para no depender de Compose en el dominio.
 */
data class SfFinisherVisual(
    val victimIdx: Int,
    val darkness: Float,
    val darkColor: Long,
    val victimTint: Long,
    val victimTintAmount: Float,
    val victimAlpha: Float,
    /** + = la víctima sube (levita); - = se hunde en el piso. px de mundo. */
    val victimLiftPx: Float,
    val victimScale: Float,
    val shakePx: Float,
    val particles: SfFinisherFx?,
    val particleProgress: Float,
    /** Texto grande (fuente arcade A-Z 0-9); "" = nada. */
    val headline: String,
    /** Texto chico bajo el grande (pista del comando o nombre del remate); "" = nada. */
    val subline: String,
    /** El texto grande parpadea (fase ACABALO). */
    val headlineBlink: Boolean,
)

/** Curvas de la cinemática. Funciones puras de (estilo, tiempo transcurrido). */
object SfFinisherVisuals {

    /** 0 antes de [a], 1 después de [b], lineal en medio. */
    fun ramp(t: Long, a: Long, b: Long): Float =
        if (b <= a) (if (t >= b) 1f else 0f) else ((t - a).toFloat() / (b - a)).coerceIn(0f, 1f)

    /** Suavizado (ease in-out) de una rampa 0..1. */
    fun smooth(x: Float): Float = x * x * (3f - 2f * x)

    /** Sacudida alterna de [amp] px mientras t ∈ [a, b), que se apaga hacia el final. */
    fun shake(t: Long, a: Long, b: Long, amp: Float): Float {
        if (t < a || t >= b) return 0f
        val decay = 1f - ramp(t, a, b)
        return (if ((t / 40L) % 2L == 0L) amp else -amp) * decay
    }

    /** Fase "ACABALO": velo oscuro que pulsa, rival mareado, texto que parpadea. */
    fun window(victimIdx: Int, elapsedMs: Long, headline: String, hint: String): SfFinisherVisual {
        val pulse = (sin(elapsedMs / 1000.0 * 2.0 * PI * 1.2).toFloat() + 1f) / 2f
        return SfFinisherVisual(
            victimIdx = victimIdx,
            darkness = 0.22f + 0.12f * pulse,
            darkColor = 0xFF1A0000L,
            victimTint = 0xFFB71C1CL,
            victimTintAmount = 0.10f + 0.12f * pulse,
            victimAlpha = 1f,
            victimLiftPx = 0f,
            victimScale = 1f,
            shakePx = 0f,
            particles = null,
            particleProgress = 0f,
            headline = headline,
            subline = hint,
            headlineBlink = true,
        )
    }

    /** Un cuadro de la cinemática de [def] a los [elapsedMs]. */
    fun cinematic(
        def: SfFinisherDef,
        victimIdx: Int,
        elapsedMs: Long,
        headline: String,
        moveName: String,
    ): SfFinisherVisual {
        val t = elapsedMs.coerceIn(0L, def.durationMs)
        // El nombre del remate aparece en el último tramo (como el "FATALITY" de MK).
        val showName = t >= def.durationMs - 1300L
        return when (def.fx) {
            SfFinisherFx.NOCHE_SIN_SOL -> SfFinisherVisual(
                victimIdx = victimIdx,
                darkness = 0.35f + 0.55f * ramp(t, 0L, 700L),
                darkColor = 0xFF12051FL,
                victimTint = 0xFF2A0A3AL,
                victimTintAmount = 0.85f * ramp(t, 1600L, 3000L),
                victimAlpha = 1f - ramp(t, 3500L, 4300L),
                victimLiftPx = 48f * smooth(ramp(t, 1600L, 3000L)),
                victimScale = 1f - ramp(t, 3300L, 4300L),
                shakePx = shake(t, 900L, 1250L, 4f) + shake(t, 3200L, 3650L, 6f),
                particles = SfFinisherFx.NOCHE_SIN_SOL,
                particleProgress = ramp(t, 1400L, 4300L),
                headline = if (showName) headline else "",
                subline = if (showName) moveName else "",
                headlineBlink = false,
            )
            SfFinisherFx.RIO -> SfFinisherVisual(
                victimIdx = victimIdx,
                darkness = 0.30f + 0.50f * ramp(t, 0L, 800L),
                darkColor = 0xFF031526L,
                victimTint = 0xFF3FB6D9L,
                victimTintAmount = 0.70f * ramp(t, 700L, 1800L),
                victimAlpha = 1f - ramp(t, 2400L, 3500L),
                victimLiftPx = -70f * smooth(ramp(t, 1500L, 3400L)),
                victimScale = 1f,
                shakePx = shake(t, 700L, 1000L, 3f),
                particles = SfFinisherFx.RIO,
                particleProgress = ramp(t, 600L, 3800L),
                headline = if (showName) headline else "",
                subline = if (showName) moveName else "",
                headlineBlink = false,
            )
            SfFinisherFx.PACTO -> {
                // Primero ARDE (naranja) y luego queda hecho CENIZA (casi negro).
                val burning = t < 2600L
                SfFinisherVisual(
                    victimIdx = victimIdx,
                    darkness = 0.30f + 0.45f * ramp(t, 0L, 700L),
                    darkColor = 0xFF2A0500L,
                    victimTint = if (burning) 0xFFFF6A00L else 0xFF140C08L,
                    victimTintAmount = if (burning) 0.70f * ramp(t, 1000L, 1800L) else 0.70f + 0.25f * ramp(t, 2600L, 3000L),
                    victimAlpha = 1f - ramp(t, 3000L, 3900L),
                    victimLiftPx = 0f,
                    victimScale = 1f - 0.25f * ramp(t, 3000L, 3900L),
                    shakePx = shake(t, 1000L, 1300L, 6f) + shake(t, 1600L, 2000L, 4f),
                    particles = SfFinisherFx.PACTO,
                    particleProgress = ramp(t, 1500L, 4000L),
                    headline = if (showName) headline else "",
                    subline = if (showName) moveName else "",
                    headlineBlink = false,
                )
            }
        }
    }
}

/**
 * 🆕 EXAMEN EXTRAORDINARIO: modo para practicar los Extraordinarios. El rival ya está
 * "reprobado" (de pie, mareado, 0 de vida), sin reloj ni límite de tiempo.
 */
object SfFinisherPractice {
    /** Tras un Extraordinario completo, cuánto se espera antes de reiniciar. */
    const val RESET_AFTER_SUCCESS_MS = 2200L

    /** Tras rematar con un golpe normal (KO clásico), cuánto se espera antes de reiniciar. */
    const val RESET_AFTER_FAIL_MS = 1500L

    /** La jerga nunca se tiende debajo de la víctima: deja este hueco (px de mundo). */
    const val MIN_GAP_PX = 34f

    const val FLASH_APROBADO = "APROBADO"
    const val FLASH_REPROBADO = "REPROBADO"
}

/**
 * 🆕 Lo que la pantalla necesita del Examen Extraordinario en cada cuadro (lo arma el VM).
 * [steps] = flechas físicas + botón; [stepIndex] = cuántos lleva bien (== steps.size al
 * aprobar); [zone] = dónde va la jerga; [inRange] = el atacante está sobre ella (verde).
 */
data class SfExtraordinarioHud(
    val moveName: String,
    /** Distancia pedida ya traducida ("CERCA"/"LEJOS"); "" si da igual. */
    val rangeLabel: String,
    val steps: List<String>,
    val stepIndex: Int,
    val zone: SfFinisherZone,
    val inRange: Boolean,
    /** La jerga solo se tiende mientras se espera el comando (no en la cinemática). */
    val showZone: Boolean,
    /** "" | [SfFinisherPractice.FLASH_APROBADO] | [SfFinisherPractice.FLASH_REPROBADO] */
    val flash: String,
)
