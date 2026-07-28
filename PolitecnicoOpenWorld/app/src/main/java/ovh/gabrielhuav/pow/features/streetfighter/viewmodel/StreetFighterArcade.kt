package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🕹️ MODO ARCADE (escalera, dificultad por escalón, sesión guardada)
//
// Extraído de StreetFighterViewModel.kt en el refactor de tamaño de la Fase 5.
// Aquí vive la escalera de 15 rivales: arrancar, avanzar de escalón, la dificultad que sube en
// los jefes, continuar/reintentar/salir, y la SESIÓN a medias que se guarda al pausar o minimizar.
// ⚠️ La sesión se guarda SOLO en forcePause y al salir, nunca por frame: en gama baja un write
// por tick se nota. Lo que se persiste es mínimo (ids + marcador), no la simulación.
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado sigue viviendo en la clase. NO recrees
// estas funciones como miembros — quedarían gemelas y ganaría el miembro EN SILENCIO
// (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro en este repo).
// ────────────────────────────────────────────────────────────────────────────

internal fun StreetFighterViewModel.persistArcadeSession(s: StreetFighterState = _state.value) {
    if (!s.arcadeActive || arcadeLadder.isEmpty()) return
    arcadeRepo.saveSession(
        SfArcadeRepository.ArcadeSession(
            playerId = arcadePlayer.name,
            step = s.arcadeStep,
            total = s.arcadeTotal,
            ladderRivals = arcadeLadder.map { it.rival.name },
            mapFile = s.arcadeMapFile,
            playerRoundWins = s.playerRoundWins,
            cpuRoundWins = s.cpuRoundWins,
            difficulty = s.cpuDifficulty.name,
            baseDifficulty = arcadeChosenDifficulty.name,
            paused = true,
        ),
    )
}

/**
 * 🆕 (2026-07-20) CHECKPOINT ENTRE PELEAS: al decidirse un combate se guarda la campaña
 * apuntando al SIGUIENTE escalón a jugar (ganó → +1; perdió → −1, piso 1) con marcador
 * 0-0. Antes aquí se BORRABA la sesión → cerrar la app en la pantalla GANASTE/PERDISTE
 * perdía la campaña entera (escalera re-aleatorizada desde el escalón 1).
 */
internal fun StreetFighterViewModel.persistArcadeCheckpoint(nextStep: Int) {
    if (arcadeLadder.isEmpty()) return
    val idx = nextStep.coerceIn(1, arcadeLadder.size)
    val stepData = arcadeLadder[idx - 1]
    arcadeRepo.saveSession(
        SfArcadeRepository.ArcadeSession(
            playerId = arcadePlayer.name,
            step = idx,
            total = arcadeLadder.size,
            ladderRivals = arcadeLadder.map { it.rival.name },
            mapFile = stepData.mapFile,
            playerRoundWins = 0,
            cpuRoundWins = 0,
            difficulty = arcadeDifficultyForStep(stepData).name,
            baseDifficulty = arcadeChosenDifficulty.name,
            paused = true,
        ),
    )
}

/**
 * Retoma la pelea arcade guardada (si hay). Devuelve true si se restauró.
 * Reconstruye la escalera desde los ids guardados (sin re-aleatorizar).
 */
fun StreetFighterViewModel.resumeArcadeSession(): Boolean {
    val ses = arcadeRepo.loadSession() ?: return false
    val player = runCatching { SfFighterId.valueOf(ses.playerId) }.getOrNull() ?: return false
    val rivals = ses.ladderRivals.mapNotNull { n ->
        runCatching { SfFighterId.valueOf(n) }.getOrNull()
    }
    if (rivals.isEmpty() || ses.step !in 1..rivals.size) return false
    arcadePlayer = player
    val savedDiff = runCatching { SfCpuDifficulty.valueOf(ses.difficulty) }
        .getOrDefault(SfCpuDifficulty.NORMAL)
    // 🆕 (2026-07-20) La BASE elegida viaja en la sesión (v2). Solo las sesiones viejas
    // caen a la inferencia (con pérdida: un guardado en jefe inflaba la base y cambiaba
    // las reglas de desbloqueo y la iluminación de los mapas).
    arcadeChosenDifficulty = runCatching { SfCpuDifficulty.valueOf(ses.baseDifficulty) }
        .getOrNull()
        ?: when (savedDiff) {
            SfCpuDifficulty.PESADILLA -> SfCpuDifficulty.AVANZADA
            else -> savedDiff
        }
    arcadeLadder = rivals.mapIndexed { i, rival ->
        val n = i + 1
        SfArcadeLadder.Step(
            index = n,
            rival = rival,
            mapFile = SfStageCatalog.mapForRival(rival, arcadeChosenDifficulty),
            isBoss = n >= rivals.size - 2,
            isFinal = n == rivals.size,
        )
    }
    arcadeMapCurrent = ses.mapFile
        ?: arcadeLadder.getOrNull(ses.step - 1)?.mapFile
        ?: SfArcadeLadder.MAP_FIRST
    val stepData = arcadeLadder[ses.step - 1]
    val diff = savedDiff
    resetInternals()
    cpuIntensity = if (arcadeLadder.size > 1) {
        0.20f + 0.80f * (ses.step - 1).toFloat() / (arcadeLadder.size - 1)
    } else {
        1f
    }
    roundIntroUntilMs = StreetFighterViewModel.ROUND_INTRO_MS
    val base = StreetFighterState()
    _state.value = base.copy(
        player = base.player.copy(id = player),
        cpu = base.cpu.copy(id = stepData.rival),
        inCharacterSelect = false,
        showRoundIntro = true,
        isPaused = true, // reanuda con overlay PAUSA (Continuar)
        cpuDifficulty = diff,
        arcadeActive = true,
        arcadeStep = ses.step,
        arcadeTotal = ses.total.coerceAtLeast(rivals.size),
        arcadeRival = stepData.rival,
        arcadeMapFile = arcadeMapCurrent,
        playerRoundWins = ses.playerRoundWins.coerceIn(0, StreetFighterViewModel.ROUNDS_TO_WIN),
        cpuRoundWins = ses.cpuRoundWins.coerceIn(0, StreetFighterViewModel.ROUNDS_TO_WIN),
        arcadeOutcome = SfArcadeOutcome.NONE,
    )
    return true
}

// ------------------------------------------------------------------
// 🆕 (2026-07-21) TUTORIAL INTERACTIVO (hoja de combos "PROBAR")
// Lecciones GUIADAS con validación: la pantalla pide un movimiento y solo se avanza
// cuando el jugador lo ejecuta. El rival es un MUÑECO inerte (no ataca ni se mueve),
// que es el estándar de los modos entrenamiento: deja concentrarse en la ejecución.
// ------------------------------------------------------------------

/** Lecciones de la sesión (universales + la de firma del peleador elegido). */

fun StreetFighterViewModel.startArcade(playerId: SfFighterId, difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL) {
    arcadePlayer = playerId
    arcadeLossStreak = 0 // 🆕 campaña nueva: racha de derrotas a cero
    // PESADILLA del selector de práctica se trata como Difícil (apocalipsis + IA dura)
    arcadeChosenDifficulty = when (difficulty) {
        SfCpuDifficulty.PESADILLA -> SfCpuDifficulty.AVANZADA
        else -> difficulty
    }
    arcadeLadder = SfArcadeLadder.build(playerId, arcadeChosenDifficulty)
    arcadeMapCurrent = arcadeLadder.firstOrNull()?.mapFile
        ?: SfStageCatalog.mapForRival(SfFighterId.PARAMEDICO_CRUZ_ROJA, arcadeChosenDifficulty)
    startArcadeStep(1)
}

/** Prepara y arranca la pelea del escalón `step` (1..TOTAL). */
internal fun StreetFighterViewModel.startArcadeStep(step: Int) {
    if (arcadeLadder.isEmpty()) return
    val idx = step.coerceIn(1, arcadeLadder.size)
    val stepData = arcadeLadder[idx - 1]
    // Mapa del rival (ya con iluminación según dificultad elegida)
    arcadeMapCurrent = stepData.mapFile
        ?: SfStageCatalog.mapForRival(stepData.rival, arcadeChosenDifficulty)
    resetInternals()
    // Intensidad sube por fase (piso 0.2) encima de la dificultad elegida
    cpuIntensity = SfArcadeLadder.intensityForStep(idx, arcadeLadder.size)
    roundIntroUntilMs = StreetFighterViewModel.ROUND_INTRO_MS // banner "RONDA 1 / PELEA"
    val base = StreetFighterState()
    _state.value = base.copy(
        player = base.player.copy(id = arcadePlayer),
        cpu = base.cpu.copy(id = stepData.rival),
        inCharacterSelect = false,
        showRoundIntro = true,
        cpuDifficulty = arcadeDifficultyForStep(stepData),
        arcadeActive = true,
        arcadeStep = idx,
        arcadeTotal = arcadeLadder.size,
        arcadeRival = stepData.rival,
        arcadeMapFile = arcadeMapCurrent,
        arcadeOutcome = SfArcadeOutcome.NONE,
    )
}

/**
 * Dificultad de la pelea = base elegida (Fácil/Medio/Difícil) + escalones en jefes.
 * Mapas ya se fijaron con [arcadeChosenDifficulty] al construir la escalera.
 */
internal fun StreetFighterViewModel.arcadeDifficultyForStep(step: SfArcadeLadder.Step): SfCpuDifficulty =
    SfArcadeLadder.difficultyForStep(arcadeChosenDifficulty, step)

/**
 * Fin del COMBATE en arcade (offline): si GANASTE, desbloquea según la DIFICULTAD ELEGIDA y
 * guarda el progreso; si perdiste, marca la derrota. El overlay lo dibuja la View según
 * `arcadeOutcome`. Lo llama endRound cuando alguien llega a StreetFighterViewModel.ROUNDS_TO_WIN.
 *
 * 🆕 (2026-07-18) REGLAS DE DESBLOQUEO por [arcadeChosenDifficulty] (decisión del dueño):
 *  - FÁCIL (BASICA)   → SOLO el MAPA del rival (día). El peleadór NO se desbloquea.
 *  - MEDIO (NORMAL)   → el PELEADÓR + su mapa (noche). Es el mínimo para tener al personaje.
 *  - DIFÍCIL (AVANZADA/apocalíptica) → NADA por ahora (próximamente: animaciones/poderes).
 * La escalera SIEMPRE avanza al ganar (independiente del desbloqueo).
 */
internal fun StreetFighterViewModel.handleArcadeMatchEnd(winnerIdx: Int) {
    val s = _state.value
    val step = arcadeLadder.getOrNull(s.arcadeStep - 1) ?: return
    val outcome = if (winnerIdx == 0) {
        when (arcadeChosenDifficulty) {
            SfCpuDifficulty.BASICA -> {
                // Solo el mapa del rival (variante de la pelea = día)
                step.mapFile?.let { arcadeRepo.unlockMap(it) }
            }
            SfCpuDifficulty.NORMAL -> {
                // Peleadór + su mapa (noche): mínimo para desbloquear al personaje
                arcadeRepo.unlockFighter(step.rival.name)
                step.mapFile?.let { arcadeRepo.unlockMap(it) }
            }
            else -> {
                // 🆕 (2026-07-21) DIFÍCIL (AVANZADA/PESADILLA): además del avance, gana
                // el COLECCIONABLE del rival (Menú principal → Coleccionables →
                // PELEADORES), con su historia. Antes esta dificultad no daba NADA.
                arcadeRepo.unlockFighter(step.rival.name)
                step.mapFile?.let { arcadeRepo.unlockMap(it) }
                viewModelScope.launch {
                    runCatching { collectibleRepo.unlockFighterCollectible(step.rival.name) }
                }
            }
        }
        arcadeRepo.setLadderStep(s.arcadeStep)
        arcadeLossStreak = 0 // 🆕 ganar CORTA la racha de derrotas
        if (s.arcadeStep >= arcadeLadder.size) SfArcadeOutcome.COMPLETED else SfArcadeOutcome.WON
    } else {
        arcadeLossStreak++ // 🆕 solo se retrocede a la 3ª derrota SEGUIDA (ver arcadeRetry)
        SfArcadeOutcome.LOST
    }
    // 🆕 (2026-07-20) La campaña SOBREVIVE el cierre de la app: checkpoint al siguiente
    // escalón (mismo que tomaría Continuar/Reintentar). Solo la final completada limpia.
    when (outcome) {
        SfArcadeOutcome.COMPLETED -> arcadeRepo.clearSession()
        SfArcadeOutcome.WON -> persistArcadeCheckpoint(s.arcadeStep + 1)
        // 🆕 (2026-07-22) Retrocede el checkpoint SOLO a la 3ª derrota seguida; si no, se queda.
        else -> persistArcadeCheckpoint(
            if (arcadeLossStreak >= 3) (s.arcadeStep - 1).coerceAtLeast(1) else s.arcadeStep,
        )
    }
    _state.value = _state.value.copy(arcadeOutcome = outcome)
}

/** CONTINUAR tras ganar un escalón → siguiente rival (o salir si era la final). */
fun StreetFighterViewModel.arcadeContinue() {
    val s = _state.value
    if (!s.arcadeActive) return
    if (s.arcadeOutcome == SfArcadeOutcome.COMPLETED) { arcadeExit(); return }
    startArcadeStep(s.arcadeStep + 1)
}

/**
 * REINTENTAR tras perder. 🆕 (2026-07-22) Solo retrocede un escalón tras **3 derrotas
 * SEGUIDAS**; si no, reintenta el MISMO rival. Ganar reinicia la racha.
 */
fun StreetFighterViewModel.arcadeRetry() {
    val s = _state.value
    if (!s.arcadeActive) return
    val target = if (arcadeLossStreak >= 3) {
        arcadeLossStreak = 0
        (s.arcadeStep - 1).coerceAtLeast(1)
    } else {
        s.arcadeStep
    }
    startArcadeStep(target)
}

/** Salir del arcade → volver al selector de personaje (fresco). */
fun StreetFighterViewModel.arcadeExit() {
    // Pelea A MEDIAS (sin resultado): guarda su snapshot por si el usuario vuelve.
    // 🆕 (2026-07-20) Con resultado ya decidido NO se re-guarda: el checkpoint correcto
    // (siguiente escalón, 0-0) lo dejó handleArcadeMatchEnd — re-guardar aquí metía el
    // marcador 2-x de la pelea terminada y al retomar acababa en una sola ronda.
    val s = _state.value
    if (s.arcadeActive && s.arcadeOutcome == SfArcadeOutcome.NONE && !s.showEndMenu) {
        persistArcadeSession(s)
    }
    arcadeLadder = emptyList()
    resetInternals()
    _state.value = StreetFighterState()
}

/** Reinicio de todos los relojes/colas internos (resetGameState del JS). */

