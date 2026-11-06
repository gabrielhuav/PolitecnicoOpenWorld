package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfExtraordinarioHud
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherCatalog
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherPractice
import kotlin.math.abs

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🎓 EXAMEN EXTRAORDINARIO (práctica de Extraordinarios)
//
// Reutiliza TODA la mecánica del remate (StreetFighterRemate.kt) con tres diferencias:
//   • Arranca directo en "ACABALO": sin intro de ronda, sin reloj y la ventana NO vence.
//   • El rival (al azar, lo elige la pantalla) es un muñeco: no actúa.
//   • La ronda nunca se cierra: cada intento se califica (APROBADO = Extraordinario completo;
//     REPROBADO = lo remató con un golpe normal) y se reinicia solo tras unos segundos.
// Solo entran peleadores con entrada en SfFinisherCatalog.
// ────────────────────────────────────────────────────────────────────────────

/** Peleadores que tienen Extraordinario (los únicos que se ofrecen en el examen). */
fun StreetFighterViewModel.extraordinarioFighters(): List<SfFighterId> =
    SfFinisherCatalog.all.map { it.fighter }

/**
 * Arranca el examen con [playerId] (debe tener Extraordinario) contra [rivalId].
 * El escenario lo decide la pantalla (al azar), igual que en IA vs IA.
 */
fun StreetFighterViewModel.startExtraordinario(playerId: SfFighterId, rivalId: SfFighterId) {
    if (SfFinisherCatalog.forFighter(playerId) == null) return
    resetInternals()
    roundIntroUntilMs = 0L // sin "RONDA 1 / PELEA": el rival ya está reprobado
    val base = StreetFighterState()
    _state.value = base.copy(
        player = base.player.copy(id = playerId),
        cpu = base.cpu.copy(id = rivalId),
        inCharacterSelect = false,
        showRoundIntro = false,
        cpuDifficulty = SfCpuDifficulty.NORMAL,
        extraordinarioActive = true,
    )
    // La ventana se abre en el primer tick (hace falta la Sim): ver tickExtraordinario.
}

/** Lo llama tick() justo después de armar la Sim. Fuera del examen no hace nada. */
internal fun StreetFighterViewModel.tickExtraordinario(sim: StreetFighterViewModel.Sim, now: Long) {
    if (!_state.value.extraordinarioActive) return
    val resetAt = remate.practiceResetAtMs
    if (resetAt > 0L) {
        if (now < resetAt) return
        restartExtraordinarioAttempt(sim, now)
        return
    }
    if (remate.phase == SfRematePhase.NONE) {
        openRemateWindow(sim, winnerIdx = 0, now = now, outcome = SfRoundOutcome.NORMAL, practice = true)
    }
}

/**
 * Lo llama remateOnRoundEnd en lugar de cerrar la ronda. Si la cinemática terminó (fase DONE
 * con el último cuadro guardado) fue un Extraordinario completo; cualquier otro KO fue un golpe
 * normal. Solo califica UNA vez por intento.
 */
internal fun StreetFighterViewModel.onExtraordinarioRoundEnd(now: Long) {
    if (remate.practiceResetAtMs > 0L) return
    val aprobado = remate.phase == SfRematePhase.DONE && remate.lastVisual != null
    if (aprobado) {
        remate.practiceFlash = SfFinisherPractice.FLASH_APROBADO
        remate.practiceResetAtMs = now + SfFinisherPractice.RESET_AFTER_SUCCESS_MS
    } else {
        // Golpe normal durante "ACABALO": el rival cae (KO clásico) y se quita el velo.
        if (remate.victimIdx in 0..1) stunUntilMs[remate.victimIdx] = 0L
        remate.phase = SfRematePhase.DONE
        remate.lastVisual = null
        remate.practiceFlash = SfFinisherPractice.FLASH_REPROBADO
        remate.practiceResetAtMs = now + SfFinisherPractice.RESET_AFTER_FAIL_MS
    }
}

/** Nuevo intento: ambos a su posición inicial y el rival otra vez de pie, mareado y en 0. */
private fun StreetFighterViewModel.restartExtraordinarioAttempt(sim: StreetFighterViewModel.Sim, now: Long) {
    remate.reset()
    remate.practiceResetAtMs = 0L
    remate.practiceFlash = ""
    val base = StreetFighterState()
    sim.setFighter(0, base.player.copy(id = sim.fighter(0).id))
    sim.setFighter(1, base.cpu.copy(id = sim.fighter(1).id))
    stunUntilMs[0] = 0L
    stunUntilMs[1] = 0L
    sim.fireballs.clear()
    sim.splashes.clear()
    hurtFreezeUntilMs = 0L
    openRemateWindow(sim, winnerIdx = 0, now = now, outcome = SfRoundOutcome.NORMAL, practice = true)
}

/** Lo que la pantalla necesita del examen (null fuera del modo). */
internal fun StreetFighterViewModel.extraordinarioHud(sim: StreetFighterViewModel.Sim, now: Long): SfExtraordinarioHud? {
    if (!_state.value.extraordinarioActive) return null
    val me = sim.fighter(0)
    val foe = sim.fighter(1)
    val def = SfFinisherCatalog.forFighter(me.id) ?: return null
    val command = def.command
    val steps = command.physicalSteps(facingRight = me.direction == SfDirection.RIGHT)
    val attackerOnLeft = me.x <= foe.x
    val zone = command.range.zone(
        victimX = foe.x,
        attackerOnLeft = attackerOnLeft,
        stageMin = StreetFighterViewModel.STAGE_X_MIN,
        stageMax = StreetFighterViewModel.STAGE_X_MAX,
    )
    val inRange = command.range.contains(abs(me.x - foe.x))
    val stepIndex = when {
        remate.practiceFlash == SfFinisherPractice.FLASH_APROBADO -> steps.size
        remate.phase == SfRematePhase.CINEMATIC -> steps.size
        remate.phase == SfRematePhase.WINDOW -> remate.tracker.progress(command, now)
        else -> 0
    }
    return SfExtraordinarioHud(
        moveName = remateMoveNameFor(def),
        rangeLabel = if (environment.languageTag.startsWith("en")) command.range.hudEn else command.range.hudEs,
        steps = steps,
        stepIndex = stepIndex,
        zone = zone,
        inRange = inRange,
        showZone = remate.phase == SfRematePhase.WINDOW,
        flash = remate.practiceFlash,
    )
}
