package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisher
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherBeat
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherCatalog
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherCpu
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherDef
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherInputTracker
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherVisual
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherVisuals
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import kotlin.math.abs
import kotlin.random.Random

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: ☠️ REMATE FINAL estilo Mortal Kombat ("¡ACÁBALO!")
//
// Flujo: NONE → WINDOW ("ACABALO", el ganador puede meter su comando) → CINEMATIC (guion del
// catálogo, sin física ni input) → DONE (endRound normal con outcome REMATE).
// Si la ventana vence, o el ganador remata con un golpe normal → KO clásico (DONE sin cinemática).
//
// Solo OFFLINE (VS CPU, arcade e IA vs IA). Online NO: cada teléfono simula a su peleador y la
// cinemática tendría que sincronizarse por red (ver "Siguientes pasos" en la entrega).
//
// Ganchos en StreetFighterViewModel.kt (buscar "REMATE"): endRound, tick, update(), updateTimer,
// watchStalemate, resetInternals y resetRound. El dominio puro vive en SfFinisher.kt.
//
// ⚠️ Son EXTENSIONES del VM, no miembros (ver 09 §0, gotcha miembro-vs-extensión).
// ────────────────────────────────────────────────────────────────────────────

internal enum class SfRematePhase { NONE, WINDOW, CINEMATIC, DONE }

/** Estado mutable del remate (vive en el VM como `remate`; los parciales solo tienen lógica). */
internal class SfRemateRuntime {
    var phase = SfRematePhase.NONE
    var winnerIdx = -1
    var victimIdx = -1
    var def: SfFinisherDef? = null
    var windowUntilMs = 0L
    /** Inicio de la fase actual (ventana o cinemática), en gameNow. */
    var startMs = 0L
    var nextBeat = 0
    /** Grado de ronda que se usa si el remate NO sucede (KO clásico). */
    var outcomeOnExpire = SfRoundOutcome.NORMAL
    var cpuWillFinish = false
    var cpuReadyAtMs = 0L
    val tracker = SfFinisherInputTracker()
    /** Último cuadro de la cinemática: se queda en pantalla tras terminar (víctima desaparecida). */
    var lastVisual: SfFinisherVisual? = null

    // ---- 🆕 EXAMEN EXTRAORDINARIO (modo práctica; ver StreetFighterExtraordinario.kt) ----
    // NO se borran en reset(): reset() se usa ENTRE intentos y estos sobreviven al intento.
    /** gameNow en que se reinicia el intento (0 = no hay reinicio pendiente). */
    var practiceResetAtMs = 0L
    /** "" | APROBADO | REPROBADO (mensaje del último intento). */
    var practiceFlash = ""

    /** Ventana o cinemática en curso: el reloj y el detector de estancamiento se pausan. */
    val isActive: Boolean get() = phase == SfRematePhase.WINDOW || phase == SfRematePhase.CINEMATIC

    fun reset() {
        phase = SfRematePhase.NONE
        winnerIdx = -1
        victimIdx = -1
        def = null
        windowUntilMs = 0L
        startMs = 0L
        nextBeat = 0
        outcomeOnExpire = SfRoundOutcome.NORMAL
        cpuWillFinish = false
        cpuReadyAtMs = 0L
        tracker.reset()
        lastVisual = null
    }

    /** reset() + lo del Examen Extraordinario (combate nuevo o salida del modo). */
    fun resetAll() {
        reset()
        practiceResetAtMs = 0L
        practiceFlash = ""
    }
}

// ------------------------------------------------------------------
// Gancho de endRound
// ------------------------------------------------------------------

/**
 * Lo llama `endRound` ANTES de cerrar la ronda. true = se abrió la ventana "ACABALO" y la ronda
 * todavía NO termina (endRound debe salir sin hacer nada).
 */
internal fun StreetFighterViewModel.remateOnRoundEnd(
    sim: StreetFighterViewModel.Sim,
    winnerIdx: Int,
    now: Long,
    outcome: SfRoundOutcome,
): Boolean {
    // 🆕 EXAMEN EXTRAORDINARIO: la ronda NUNCA se cierra; cada intento se califica y se reinicia.
    if (_state.value.extraordinarioActive) {
        onExtraordinarioRoundEnd(now)
        return true
    }
    when (remate.phase) {
        SfRematePhase.WINDOW -> {
            // Lo remató con un golpe NORMAL durante la ventana: KO clásico, se quita el velo.
            closeRemateAsClassicKo()
            return false
        }
        SfRematePhase.CINEMATIC, SfRematePhase.DONE -> return false
        SfRematePhase.NONE -> Unit
    }
    if (!remateEligible(sim, winnerIdx, outcome)) return false
    openRemateWindow(sim, winnerIdx, now, outcome)
    return true
}

private fun StreetFighterViewModel.remateEligible(
    sim: StreetFighterViewModel.Sim,
    winnerIdx: Int,
    outcome: SfRoundOutcome,
): Boolean {
    if (!SfFinisher.ENABLED) return false
    if (outcome == SfRoundOutcome.TIME) return false // por tiempo no hay "acábalo"
    if (isOnline || showcaseMode || gauntletActive || inTutorial) return false
    val s = _state.value
    // Solo en la ronda que DECIDE el combate (como en MK): con esta, el ganador llega a 2.
    val wins = if (winnerIdx == 0) s.playerRoundWins else s.cpuRoundWins
    if (wins + 1 < StreetFighterViewModel.ROUNDS_TO_WIN) return false
    return SfFinisherCatalog.forFighter(sim.fighter(winnerIdx).id) != null
}

/**
 * Abre la ventana "ACABALO". Con [practice] (Examen Extraordinario) la ventana no vence y la
 * CPU no participa: el humano (índice 0) es siempre quien remata.
 */
internal fun StreetFighterViewModel.openRemateWindow(
    sim: StreetFighterViewModel.Sim,
    winnerIdx: Int,
    now: Long,
    outcome: SfRoundOutcome,
    practice: Boolean = false,
) {
    val victimIdx = 1 - winnerIdx
    val s = _state.value
    remate.reset()
    remate.phase = SfRematePhase.WINDOW
    remate.winnerIdx = winnerIdx
    remate.victimIdx = victimIdx
    remate.def = SfFinisherCatalog.forFighter(sim.fighter(winnerIdx).id)
    remate.startMs = now
    remate.windowUntilMs = if (practice) Long.MAX_VALUE else now + SfFinisher.WINDOW_MS
    remate.outcomeOnExpire = outcome

    // ¿La CPU ganadora va a rematar? (el humano decide con su comando)
    val cpuWinner = s.aiVsAi || winnerIdx == 1
    val difficulty = if (s.aiVsAi) SfCpuDifficulty.PESADILLA else s.cpuDifficulty
    remate.cpuWillFinish = !practice && cpuWinner && Random.nextFloat() < SfFinisherCpu.chance(difficulty)
    remate.cpuReadyAtMs = now + SfFinisherCpu.THINK_MS

    // El ganador todavía NO celebra (con victory=true el IDLE lo mandaría a VICTORY y no podría moverse).
    sim.setFighter(winnerIdx, sim.fighter(winnerIdx).copy(victory = false))

    // La víctima queda DE PIE, mareada y con 0 de vida (el KO ya había sonado su voz de derrota).
    forceState(sim, victimIdx, SfFighterState.STUN, now)
    sim.setFighter(
        victimIdx,
        sim.fighter(victimIdx).copy(
            hitPoints = 0,
            y = SfConstants.STAGE_FLOOR,
            velocityX = 0f,
            velocityY = 0f,
            slideVelocity = 0f,
            slideFriction = 0f,
            downed = false,
            dizzyMeter = 0,
        ),
    )
    stunUntilMs[victimIdx] = Long.MAX_VALUE // no se despierta sola durante la ventana
    sim.fireballs.clear() // un proyectil en vuelo no debe cambiar el resultado
    _soundEvents.tryEmit("land")
}

private fun StreetFighterViewModel.closeRemateAsClassicKo() {
    if (remate.victimIdx in 0..1) stunUntilMs[remate.victimIdx] = 0L
    remate.phase = SfRematePhase.DONE
    remate.lastVisual = null
}

// ------------------------------------------------------------------
// Fase WINDOW: filtro de input (lo llama tick para cada índice)
// ------------------------------------------------------------------

/**
 * Filtra el input de [idx] durante la ventana. Fuera de ella devuelve [raw] tal cual (cero
 * cambios de comportamiento). La víctima no actúa; el ganador humano puede meter el comando;
 * la CPU ganadora camina a la distancia correcta y lo ejecuta tras una pausa dramática.
 */
internal fun StreetFighterViewModel.remateGateInput(
    sim: StreetFighterViewModel.Sim,
    idx: Int,
    raw: SfInput,
    now: Long,
    humanControlled: Boolean,
): SfInput {
    if (remate.phase != SfRematePhase.WINDOW) return raw
    if (idx == remate.victimIdx) return SfInput()
    val def = remate.def ?: return raw
    val me = sim.fighter(idx)
    val foe = sim.fighter(remate.victimIdx)
    val dist = abs(me.x - foe.x)
    val grounded = !me.isAirborne

    if (humanControlled) {
        val completed = remate.tracker.feed(raw, now, def.command)
        if (completed && grounded && def.command.range.contains(dist)) {
            startRemateCinematic(sim, now)
            return SfInput()
        }
        return raw // comando incompleto: si el botón conecta, es un KO clásico
    }

    if (!remate.cpuWillFinish) return raw // la IA remata a golpes (KO clásico)
    if (!grounded) return SfInput()
    val range = def.command.range
    val desperate = now >= remate.windowUntilMs - SfFinisherCpu.DESPERATION_MS
    return when {
        !desperate && dist < range.minPx -> SfInput(backward = true)
        !desperate && dist > range.maxPx -> SfInput(forward = true)
        now < remate.cpuReadyAtMs -> SfInput()
        else -> {
            startRemateCinematic(sim, now)
            SfInput()
        }
    }
}

/** Vence la ventana sin remate → KO clásico. Se llama cada tick después de mover a los peleadores. */
internal fun StreetFighterViewModel.tickRemateWindow(sim: StreetFighterViewModel.Sim, now: Long) {
    if (remate.phase != SfRematePhase.WINDOW || now < remate.windowUntilMs) return
    if (_state.value.extraordinarioActive) return // en el examen no hay límite de tiempo
    val w = remate.winnerIdx
    val v = remate.victimIdx
    val outcome = remate.outcomeOnExpire
    closeRemateAsClassicKo()
    forceState(sim, v, SfFighterState.KO, now)
    sim.setFighter(w, sim.fighter(w).copy(victory = true))
    endRound(sim, w, now, outcome)
}

// ------------------------------------------------------------------
// Fase CINEMATIC
// ------------------------------------------------------------------

private fun StreetFighterViewModel.startRemateCinematic(sim: StreetFighterViewModel.Sim, now: Long) {
    val def = remate.def ?: return
    val w = remate.winnerIdx
    val v = remate.victimIdx
    remate.phase = SfRematePhase.CINEMATIC
    remate.startMs = now
    remate.nextBeat = 0
    hurtFreezeUntilMs = 0L

    // Colocación: algunos remates "aparecen" junto a la víctima.
    var me = sim.fighter(w)
    var foe = sim.fighter(v)
    def.attackerGapPx?.let { gap ->
        val side = if (me.x <= foe.x) -1f else 1f
        var target = foe.x + side * gap
        if (target < StreetFighterViewModel.STAGE_X_MIN || target > StreetFighterViewModel.STAGE_X_MAX) {
            // Contra la pared: se mueve la VÍCTIMA para dejar el hueco.
            target = target.coerceIn(StreetFighterViewModel.STAGE_X_MIN, StreetFighterViewModel.STAGE_X_MAX)
            foe = foe.copy(x = (target - side * gap).coerceIn(StreetFighterViewModel.STAGE_X_MIN, StreetFighterViewModel.STAGE_X_MAX))
        }
        me = me.copy(x = target)
    }
    val attackerLeft = me.x <= foe.x
    sim.setFighter(
        w,
        me.copy(
            direction = if (attackerLeft) SfDirection.RIGHT else SfDirection.LEFT,
            velocityX = 0f, velocityY = 0f, slideVelocity = 0f, y = SfConstants.STAGE_FLOOR,
        ),
    )
    sim.setFighter(
        v,
        foe.copy(
            direction = if (attackerLeft) SfDirection.LEFT else SfDirection.RIGHT,
            velocityX = 0f, velocityY = 0f, slideVelocity = 0f,
        ),
    )
    // Calificación estilo SF III: un remate vale como dos súpers conectadas.
    if (gradeTrackingOn && w == 0) gradeSupers += 2
}

/** Avanza la cinemática: dispara los beats vencidos y anima SIN física ni colisiones. */
internal fun StreetFighterViewModel.tickRemateCinematic(sim: StreetFighterViewModel.Sim, now: Long) {
    val def = remate.def ?: return
    val elapsed = now - remate.startMs
    while (remate.nextBeat < def.beats.size && def.beats[remate.nextBeat].atMs <= elapsed) {
        applyRemateBeat(sim, def.beats[remate.nextBeat], now)
        remate.nextBeat++
    }
    for (i in 0..1) {
        val f = sim.fighter(i)
        // Las poses se SOSTIENEN en su último cuadro (no vuelven a empezar); el mareo sí cicla.
        if (f.state == SfFighterState.STUN || !isAnimationCompleted(f)) {
            sim.setFighter(i, updateAnimation(f, now))
        }
    }
    if (elapsed >= def.durationMs) finishRemate(sim, now)
}

private fun StreetFighterViewModel.applyRemateBeat(sim: StreetFighterViewModel.Sim, beat: SfFinisherBeat, now: Long) {
    val w = remate.winnerIdx
    val v = remate.victimIdx
    beat.attackerState?.let { forceWithFallback(sim, w, it, now) }
    beat.victimState?.let { forceWithFallback(sim, v, it, now) }
    beat.sfx?.let { _soundEvents.tryEmit(it) }
    beat.attackerVoice?.let { emitVoiceClip(it) }
    val victim = sim.fighter(v)
    if (beat.victimHurtVoice) emitHurtVoice(victim.id, v, 0, now)
    if (beat.victimState == SfFighterState.KO) emitLossVoice(victim.id, now)
}

/** Fuerza [state]; si el peleador no tiene esa hoja, cae a un respaldo que TODOS tienen. */
private fun StreetFighterViewModel.forceWithFallback(
    sim: StreetFighterViewModel.Sim,
    idx: Int,
    state: SfFighterState,
    now: Long,
) {
    val chain = listOf(state, SfFighterState.SPECIAL_1_HEAVY, SfFighterState.HEAVY_PUNCH, SfFighterState.IDLE)
    for (candidate in chain) {
        if (hasAnim(sim.fighter(idx), candidate) && forceState(sim, idx, candidate, now)) return
    }
}

private fun StreetFighterViewModel.finishRemate(sim: StreetFighterViewModel.Sim, now: Long) {
    val w = remate.winnerIdx
    val v = remate.victimIdx
    // El último cuadro (velo + víctima desaparecida + nombre) se queda hasta el menú de fin.
    remate.def?.let { def ->
        remate.lastVisual = SfFinisherVisuals.cinematic(
            def, v, def.durationMs, remateHeadline(), remateMoveName(def),
        )
    }
    remate.phase = SfRematePhase.DONE
    stunUntilMs[v] = 0L
    if (sim.fighter(v).state != SfFighterState.KO) forceState(sim, v, SfFighterState.KO, now)
    sim.setFighter(v, sim.fighter(v).copy(hitPoints = 0))
    sim.setFighter(w, sim.fighter(w).copy(victory = true))
    endRound(sim, w, now, SfRoundOutcome.REMATE)
}

// ------------------------------------------------------------------
// Visual que se publica en el estado (lo pinta SfSceneRenderer)
// ------------------------------------------------------------------

internal fun StreetFighterViewModel.remateMoveNameFor(def: SfFinisherDef): String = remateMoveName(def)

internal fun StreetFighterViewModel.remateVisual(sim: StreetFighterViewModel.Sim, now: Long): SfFinisherVisual? =
    when (remate.phase) {
        SfRematePhase.NONE -> null
        SfRematePhase.DONE -> remate.lastVisual
        SfRematePhase.WINDOW -> {
            val victim = sim.fighter(remate.victimIdx)
            val english = remateEnglish()
            val headline = when {
                english && victim.id in sfMaleFighters -> "FINISH HIM"
                english -> "FINISH HER"
                victim.id in sfMaleFighters -> "ACABALO"
                else -> "ACABALA"
            }
            // La pista solo se muestra si quien remata es el HUMANO (a la CPU no le hace falta).
            val humanWinner = remate.winnerIdx == 0 && !_state.value.aiVsAi
            // En el Examen Extraordinario los pasos van en su propio panel (botón PASOS).
            val practice = _state.value.extraordinarioActive
            val hint = if (SfFinisher.SHOW_COMMAND_HINT && humanWinner && !practice) {
                remate.def?.command?.hudHint(english).orEmpty()
            } else {
                ""
            }
            SfFinisherVisuals.window(remate.victimIdx, now - remate.startMs, headline, hint)
        }
        SfRematePhase.CINEMATIC -> remate.def?.let { def ->
            SfFinisherVisuals.cinematic(
                def, remate.victimIdx, now - remate.startMs, remateHeadline(), remateMoveName(def),
            )
        }
    }

private fun StreetFighterViewModel.remateEnglish(): Boolean = environment.languageTag.startsWith("en")

private fun StreetFighterViewModel.remateHeadline(): String =
    if (remateEnglish()) "EXTRAORDINARY" else "EXTRAORDINARIO"

private fun StreetFighterViewModel.remateMoveName(def: SfFinisherDef): String =
    if (remateEnglish()) def.nameEn else def.nameEs
