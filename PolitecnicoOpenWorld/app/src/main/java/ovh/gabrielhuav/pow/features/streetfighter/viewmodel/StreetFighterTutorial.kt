package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.features.streetfighter.data.SfComboAction
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombos
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState

// ────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🎓 TUTORIAL de controles (lecciones, validación y errores del jugador)
//
// Extraído de StreetFighterViewModel.kt en el refactor de tamaño de la Fase 5.
// Carga cada lección, traduce la acción esperada a texto, valida lo que hace el jugador y
// gestiona los fallos con su cooldown.
// ⚠️ Comparte la traducción acción→input con la IA (inputForAction, en StreetFighterCpuAi.kt):
// es la ÚNICA fuente de verdad de 'cómo se hace' cada movimiento. Si cambias uno, cambian los dos.
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado sigue viviendo en la clase. NO recrees
// estas funciones como miembros — quedarían gemelas y ganaría el miembro EN SILENCIO
// (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro en este repo).
// ────────────────────────────────────────────────────────────────────────────


/**
 * Arranca el tutorial con el peleador elegido (de los DESBLOQUEADOS). El rival es el
 * muñeco: se usa el mismo peleador para no depender de otro set de assets.
 */
fun StreetFighterViewModel.startTutorial(playerId: SfFighterId) {
    val lang = java.util.Locale.getDefault().language
    // Currículum COMPLETO: primero los BÁSICOS (un movimiento por lección) y después
    // los combos. Se filtran los que este peleador no puede hacer (sin arte propia).
    // Se filtra por ARTE, no por recursos: el medidor de la súper se llena durante la
    // propia lección pegándole al muñeco (con `canPerform` estas lecciones se caían).
    val probe = _state.value.player.copy(id = playerId)
    tutorialCombos = SfCombos.curriculum(appContext, playerId)
        .filter { combo -> combo.steps.all { hasArtFor(probe, it) } }
        .ifEmpty { SfCombos.basics(appContext) }
    resetInternals()
    val base = StreetFighterState()
    _state.value = base.copy(
        player = base.player.copy(id = playerId),
        cpu = base.cpu.copy(id = playerId),
        inCharacterSelect = false,
        tutorialActive = true,
        tutorialTotal = tutorialCombos.size,
        // El muñeco no debe morir: la vida no baja en tutorial (ver applyAttackHit)
        displayTime = SfConstants.BATTLE_TIME,
    )
    loadTutorialLesson(0, lang)
}

/** Carga la lección `index` (o marca completado si se acabaron). */
internal fun StreetFighterViewModel.loadTutorialLesson(index: Int, langTag: String) {
    val combo = tutorialCombos.getOrNull(index)
    if (combo == null) {
        _state.update { it.copy(tutorialCompleted = true, tutorialFlash = "") }
        return
    }
    _state.update {
        it.copy(
            tutorialLesson = index,
            tutorialTitle = combo.name(langTag),
            tutorialHint = combo.hint(langTag),
            tutorialSteps = combo.steps.map(::actionLabel),
            tutorialStepIndex = 0,
            tutorialFlash = "",
            tutorialCompleted = false,
        )
    }
}

/** Etiqueta corta y legible de cada acción (la pinta la hoja de combos y el tutorial). */
// 🆕 (2026-07-22, pedido del dueño) Etiquetas referidas a los CONTROLES ACTUALES:
// cada paso nombra el BOTÓN físico (X/Y/B/A/P/G/T/S) y/o el gesto de joystick exacto.
// ⚠️ Los substrings "PUÑO LIGERO/MEDIO/FUERTE", "PATADA", "PARRY", "AGARRE", "SÚPER",
// "BURLA" y "FATALITY" los usan chipColor (color del chip) y sfButtonForLabel (glow
// del botón) — no los rompas al reformular.
internal fun StreetFighterViewModel.actionLabel(action: SfComboAction): String = when (action) {
    SfComboAction.LIGHT_PUNCH -> "PUÑO LIGERO (X)"
    SfComboAction.MEDIUM_PUNCH -> "PUÑO MEDIO (Y)"
    SfComboAction.HEAVY_PUNCH -> "PUÑO FUERTE (B)"
    SfComboAction.LIGHT_KICK -> "PATADA LIGERA (A)"
    SfComboAction.MEDIUM_KICK -> "PATADA MEDIA (→ + A)"
    SfComboAction.HEAVY_KICK -> "PATADA FUERTE (← + A)"
    SfComboAction.CROUCH_PUNCH -> "↓ + PUÑO (X)"
    SfComboAction.CROUCH_KICK -> "↓ + PATADA (A)"
    SfComboAction.CROUCH_HEAVY_PUNCH -> "↓ + PUÑO FUERTE (B)"
    SfComboAction.SWEEP -> "BARRIDA: ↓ ← + PATADA (A)"
    SfComboAction.LONG_KICK -> "→ + PATADA FUERTE"
    SfComboAction.OVERHEAD -> "→ + PUÑO MEDIO (Y)"
    SfComboAction.AIR_PUNCH -> "SALTA Y PUÑO (X)"
    SfComboAction.AIR_KICK -> "SALTA Y PATADA (A)"
    SfComboAction.DASH_FORWARD -> "DOBLE TOQUE →"
    SfComboAction.DASH_BACKWARD -> "DOBLE TOQUE ←"
    SfComboAction.PARRY -> "PARRY (L1)"
    SfComboAction.GRAB -> "AGARRE (R1, PEGADO)"
    SfComboAction.TAUNT -> "BURLA (L2)"
    SfComboAction.SPECIAL -> "↓ ↘ → + PUÑO"
    SfComboAction.SUPER_ART -> "SÚPER (R2, MEDIDOR LLENO)"
    SfComboAction.JUMP -> "SALTAR (JOYSTICK ↑)"
    SfComboAction.CROUCH -> "AGACHARSE (JOYSTICK ↓)"
    SfComboAction.WALK_FORWARD -> "CAMINAR ADELANTE (JOYSTICK →)"
    SfComboAction.RUN -> "TRAS EL DASH, SOSTÉN → (CORRER)"
    SfComboAction.BLOCK_HIGH -> "MANTENER ATRÁS (CUBRIRSE)"
    SfComboAction.FATALITY -> "FATALITY: CORRE Y PULSA R2"
}

/**
 * Valida el paso en curso: se llama cada tick con el estado real del jugador. Si entró
 * al estado que pedía la lección, avanza; al completar los pasos, pasa a la siguiente.
 */
internal fun StreetFighterViewModel.tickTutorial(sim: StreetFighterViewModel.Sim, now: Long) {
    val s = _state.value
    if (!s.tutorialActive || s.tutorialCompleted) return
    // 🆕 (2026-07-22) Cuenta 3-2-1 entre lecciones: NO se revisa input (evita el "ESE NO ERA"
    // por seguir apretando del paso anterior). El número se muestra en tutorialFlash.
    if (now < tutorialLessonReadyMs) {
        val n = (((tutorialLessonReadyMs - now) + 999L) / 1000L).toInt()
        _state.update { it.copy(tutorialFlash = n.toString(), tutorialError = "") }
        return
    }
    if (s.tutorialFlash.isNotEmpty() && now > tutorialFlashUntilMs) {
        _state.update { it.copy(tutorialFlash = "", tutorialError = "") }
    }
    // 🆕 (2026-07-22) FIX: el "ESE NO ERA" (tutorialError) se pinta con flash VACÍO, así que la
    // línea de arriba nunca lo limpiaba y se quedaba fijo. Se borra por su propio cooldown.
    if (s.tutorialError.isNotEmpty() && now > tutorialErrorUntilMs) {
        _state.update { it.copy(tutorialError = "") }
    }
    val combo = tutorialCombos.getOrNull(s.tutorialLesson) ?: return
    val expected = combo.steps.getOrNull(s.tutorialStepIndex) ?: return
    val playerState = sim.p0.state
    if (playerState !in stateForAction(expected)) {
        // 🆕 (2026-07-21) FEEDBACK DE ERROR: si hizo OTRO movimiento reconocible, se le
        // dice cuál fue y cuál tocaba, en vez de dejarlo adivinando por qué no avanza.
        reportTutorialMistake(playerState, expected, now)
        return
    }
    // Paso acertado
    val nextStep = s.tutorialStepIndex + 1
    tutorialFlashUntilMs = now + StreetFighterViewModel.TUTORIAL_FLASH_MS
    if (nextStep < combo.steps.size) {
        _state.update {
            it.copy(tutorialStepIndex = nextStep, tutorialFlash = "OK", tutorialError = "")
        }
    } else {
        _state.update {
            it.copy(tutorialStepIndex = nextStep, tutorialFlash = "COMPLETO", tutorialError = "")
        }
        loadTutorialLesson(s.tutorialLesson + 1, java.util.Locale.getDefault().language)
        tutorialLessonReadyMs = now + StreetFighterViewModel.TUTORIAL_LESSON_COUNTDOWN_MS // 🆕 pausa 3-2-1 antes de la nueva
    }
}

/**
 * 🆕 (2026-07-21) Explica el ERROR: traduce el estado que SÍ ejecutó a su etiqueta y lo
 * contrasta con el que pedía la lección. Solo se avisa de acciones RECONOCIBLES (no de
 * estar quieto o caminando), y con cooldown para no llenar la pantalla de mensajes.
 */
internal fun StreetFighterViewModel.reportTutorialMistake(
    actual: SfFighterState,
    expected: SfComboAction,
    now: Long,
) {
    if (now < tutorialErrorUntilMs) return
    val didAction = SfComboAction.entries.firstOrNull { action ->
        action != expected && actual in stateForAction(action) &&
            action !in setOf(SfComboAction.WALK_FORWARD, SfComboAction.BLOCK_HIGH)
    } ?: return
    tutorialErrorUntilMs = now + StreetFighterViewModel.TUTORIAL_ERROR_COOLDOWN_MS
    tutorialFlashUntilMs = now + StreetFighterViewModel.TUTORIAL_ERROR_COOLDOWN_MS
    _state.update {
        it.copy(
            tutorialError = "${actionLabel(didAction)} → ${actionLabel(expected)}",
            tutorialFlash = "",
        )
    }
}

/** Saltar a la siguiente lección sin completarla. */
fun StreetFighterViewModel.tutorialSkipLesson() {
    if (!_state.value.tutorialActive) return
    loadTutorialLesson(
        _state.value.tutorialLesson + 1,
        java.util.Locale.getDefault().language,
    )
}

/** Repetir la lección en curso desde el primer paso. */
fun StreetFighterViewModel.tutorialRestartLesson() {
    if (!_state.value.tutorialActive) return
    loadTutorialLesson(_state.value.tutorialLesson, java.util.Locale.getDefault().language)
}

/** Salir del tutorial al menú de modos. */
fun StreetFighterViewModel.exitTutorial() {
    tutorialCombos = emptyList()
    resetInternals()
    _state.value = StreetFighterState()
}

/** Combos que se listan en la HOJA (para la View; ya resueltos al idioma). */
fun StreetFighterViewModel.comboSheet(id: SfFighterId): List<Triple<String, String, List<String>>> {
    val lang = java.util.Locale.getDefault().language
    return SfCombos.forFighter(appContext, id).map { combo ->
        Triple(combo.name(lang), combo.hint(lang), combo.steps.map(::actionLabel))
    }
}

/** Descarta la pelea a medias (el usuario elige "Nueva partida"). */

