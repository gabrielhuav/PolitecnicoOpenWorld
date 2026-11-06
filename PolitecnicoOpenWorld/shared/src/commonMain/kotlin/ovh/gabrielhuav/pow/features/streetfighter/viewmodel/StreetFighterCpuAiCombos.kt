package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_PARRY_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombo
import ovh.gabrielhuav.pow.features.streetfighter.data.SfComboAction
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🤖 IA DE LA CPU — ejecución de combos.
//
// Traducción acción→input del catálogo (`assets/DATA/combos.json`), validación de estado,
// cola de rutas y las primitivas de ataque.
//
// Separado de StreetFighterCpuAi.kt (2026-09-23): aquel archivo llegó a 1094 líneas y 09 §0
// pide partir por encima de 1000. SOLO se movieron líneas; ninguna función cambió.
//
// ⚠️ Son EXTENSIONES del VM, no miembros. Los CAMPOS de estado de la IA (`cpuComboQueue`,
// `cpuAttackHistory`, `comboCatalog`…) siguen en la clase a propósito: aquí solo vive la LÓGICA.
// NO recrees estas funciones como miembros — quedarían gemelas y ganaría el miembro en silencio
// (ver 09 §0).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Traduce una acción del catálogo al `SfInput` que la dispara. Es la ÚNICA fuente de
 * verdad de "cómo se hace" cada movimiento: la usan la IA y el tutorial.
 */
internal fun StreetFighterViewModel.inputForAction(action: SfComboAction): SfInput = when (action) {
    SfComboAction.LIGHT_PUNCH -> SfInput(lightPunch = true)
    SfComboAction.MEDIUM_PUNCH -> SfInput(mediumPunch = true)
    SfComboAction.HEAVY_PUNCH -> SfInput(heavyPunch = true)
    SfComboAction.LIGHT_KICK -> SfInput(lightKick = true)
    SfComboAction.MEDIUM_KICK -> SfInput(mediumKick = true)
    SfComboAction.HEAVY_KICK -> SfInput(heavyKick = true)
    SfComboAction.CROUCH_PUNCH -> SfInput(down = true, lightPunch = true)
    SfComboAction.CROUCH_KICK -> SfInput(down = true, lightKick = true)
    SfComboAction.CROUCH_HEAVY_PUNCH -> SfInput(down = true, heavyPunch = true)
    SfComboAction.SWEEP -> SfInput(down = true, heavyKick = true)
    SfComboAction.LONG_KICK -> SfInput(forward = true, heavyKick = true)
    SfComboAction.OVERHEAD -> SfInput(forward = true, mediumPunch = true)
    SfComboAction.AIR_PUNCH -> SfInput(mediumPunch = true)
    SfComboAction.AIR_KICK -> SfInput(mediumKick = true)
    SfComboAction.DASH_FORWARD -> SfInput(dashForward = true)
    SfComboAction.DASH_BACKWARD -> SfInput(dashBackward = true)
    SfComboAction.PARRY -> SfInput(parry = true)
    SfComboAction.GRAB -> SfInput(grab = true)
    SfComboAction.TAUNT -> SfInput(taunt = true)
    SfComboAction.COUNTER -> SfInput(counter = true)
    SfComboAction.POWER_THROW -> SfInput(powerThrow = true)
    SfComboAction.SPECIAL -> SfInput(special = SfAttackStrength.MEDIUM)
    SfComboAction.SUPER_ART -> SfInput(superArt = true)
    SfComboAction.JUMP -> SfInput(up = true)
    SfComboAction.CROUCH -> SfInput(down = true)
    SfComboAction.WALK_FORWARD -> SfInput(forward = true)
    SfComboAction.RUN -> SfInput(forward = true)
    SfComboAction.BLOCK_HIGH -> SfInput(backward = true)
    // El fatality se pide EN CARRERA: adelante sostenido + súper.
    SfComboAction.FATALITY -> SfInput(forward = true, superArt = true)
}

/** Estado en el que DEBE entrar el peleador si la acción salió bien (validación). */
internal fun StreetFighterViewModel.stateForAction(action: SfComboAction): Set<SfFighterState> = when (action) {
    SfComboAction.LIGHT_PUNCH -> setOf(SfFighterState.LIGHT_PUNCH)
    SfComboAction.MEDIUM_PUNCH -> setOf(SfFighterState.MEDIUM_PUNCH)
    SfComboAction.HEAVY_PUNCH -> setOf(SfFighterState.HEAVY_PUNCH)
    SfComboAction.LIGHT_KICK -> setOf(SfFighterState.LIGHT_KICK)
    SfComboAction.MEDIUM_KICK -> setOf(SfFighterState.MEDIUM_KICK)
    SfComboAction.HEAVY_KICK -> setOf(SfFighterState.HEAVY_KICK)
    SfComboAction.CROUCH_PUNCH -> setOf(SfFighterState.CROUCH_PUNCH)
    SfComboAction.CROUCH_KICK -> setOf(SfFighterState.CROUCH_KICK)
    SfComboAction.CROUCH_HEAVY_PUNCH -> setOf(SfFighterState.CROUCH_HEAVY_PUNCH)
    SfComboAction.SWEEP -> setOf(SfFighterState.SWEEP)
    SfComboAction.LONG_KICK -> setOf(SfFighterState.LONG_KICK)
    SfComboAction.OVERHEAD -> setOf(SfFighterState.OVERHEAD)
    SfComboAction.AIR_PUNCH -> setOf(SfFighterState.AIR_PUNCH)
    SfComboAction.AIR_KICK -> setOf(SfFighterState.AIR_KICK)
    SfComboAction.DASH_FORWARD -> setOf(SfFighterState.DASH_FORWARD)
    SfComboAction.DASH_BACKWARD -> setOf(SfFighterState.DASH_BACKWARD)
    SfComboAction.PARRY -> SF_PARRY_STATES
    SfComboAction.GRAB -> setOf(SfFighterState.GRAB, SfFighterState.THROW)
    SfComboAction.TAUNT -> setOf(SfFighterState.TAUNT)
    // 🆕 (2026-08-29) Igual que el parry: entrar a la ventana ya cuenta (el muñeco del tutorial
    // nunca ataca, así que validar por un contraataque REAL nunca podría completarse).
    SfComboAction.COUNTER -> setOf(SfFighterState.COUNTER)
    SfComboAction.POWER_THROW -> setOf(SfFighterState.POWER_GRAB, SfFighterState.POWER_THROW)
    SfComboAction.SPECIAL -> setOf(
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM,
        SfFighterState.SPECIAL_1_HEAVY,
    )
    SfComboAction.SUPER_ART -> setOf(SfFighterState.SUPER_ART)
    SfComboAction.JUMP -> setOf(
        SfFighterState.JUMP_START, SfFighterState.JUMP_UP,
        SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
    )
    SfComboAction.CROUCH -> setOf(SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN)
    SfComboAction.WALK_FORWARD -> setOf(SfFighterState.WALK_FORWARD)
    SfComboAction.RUN -> setOf(SfFighterState.RUN)
    SfComboAction.BLOCK_HIGH -> setOf(
        SfFighterState.BLOCK_HIGH, SfFighterState.BLOCK_LOW, SfFighterState.WALK_BACKWARD,
    )
    SfComboAction.FATALITY -> setOf(SfFighterState.FATALITY)
}

/**
 * ¿El peleador tiene ARTE para esta acción? (independiente de recursos como el medidor).
 * Es lo que decide si una lección/combo se puede ENSEÑAR: el medidor se llena durante
 * la propia lección pegándole al muñeco.
 */
internal fun StreetFighterViewModel.hasArtFor(f: SfFighter, action: SfComboAction): Boolean {
    val states = stateForAction(action)
    return states.none { it in SF_NEW_MOVE_STATES } || states.any { hasAnim(f, it) }
}

/**
 * ¿Puede ejecutarla AHORA MISMO? Añade el requisito de RECURSO (medidor lleno para la
 * súper y el fatality). Lo usa la IA al elegir una ruta; el tutorial NO, porque si no
 * las lecciones de súper/fatality se filtraban al arrancar con el medidor a cero y
 * nunca se enseñaban.
 */
internal fun StreetFighterViewModel.canPerform(f: SfFighter, action: SfComboAction): Boolean {
    val needsMeter = action == SfComboAction.SUPER_ART || action == SfComboAction.FATALITY
    if (needsMeter && !f.superReady) return false
    // 🆕 (2026-08-29) DERRIBO CON PODER solo necesita un TRAMO del medidor, no lleno.
    if (action == SfComboAction.POWER_THROW && f.superMeter < SfConstants.POWER_THROW_METER_COST) {
        return false
    }
    return hasArtFor(f, action)
}

/**
 * 🆕 Elige una RUTA de combo ejecutable y la encola. La IA prefiere el combo de FIRMA
 * del peleador y, si no puede, uno universal de su nivel de dificultad hacia abajo.
 */
internal fun StreetFighterViewModel.queueCombo(
    selfIndex: Int,
    me: SfFighter,
    now: Long,
    difficulty: SfCpuDifficulty,
): Boolean {
    val i = selfIndex.coerceIn(0, 1)
    if (cpuComboQueue[i].isNotEmpty()) return false
    val maxLevel = cpuComboMaxLevel(difficulty, cpuIntensity)
    val canRun: (SfCombo) -> Boolean = { combo ->
        isCpuComboRoute(combo, maxLevel) && combo.steps.all { canPerform(me, it) }
    }
    val signature = signatureCombo(me.id)?.takeIf(canRun)
    val universal = comboCatalog.filter(canRun)
    val signatureChance = when (difficulty) {
        SfCpuDifficulty.BASICA, SfCpuDifficulty.NORMAL -> 0f
        SfCpuDifficulty.AVANZADA -> 0.35f + 0.25f * cpuIntensity
        SfCpuDifficulty.PESADILLA -> 0.68f
    }
    val chosen = when {
        signature != null && (universal.isEmpty() || Random.nextFloat() < signatureChance) -> signature
        universal.isNotEmpty() -> universal.random()
        else -> signature
    } ?: return false
    cpuComboQueue[i].addAll(chosen.steps)
    cpuComboAwaiting[i] = null
    cpuComboUntilMs[i] = now + cpuComboRouteTimeoutMs(difficulty)
    return true
}

/** Siguiente paso de la ruta encolada (null si no hay o si expiró). */
internal fun StreetFighterViewModel.nextComboInput(selfIndex: Int, me: SfFighter, now: Long): SfInput? {
    val i = selfIndex.coerceIn(0, 1)
    if (cpuComboQueue[i].isEmpty()) { cpuComboAwaiting[i] = null; return null }
    if (now > cpuComboUntilMs[i]) {
        cpuComboQueue[i].clear()
        cpuComboAwaiting[i] = null
        return null
    }

    // Un paso solo sale de la cola DESPUÉS de observar el estado que demuestra que el motor
    // lo aceptó. Antes se eliminaba al pulsarlo: durante recovery/crouch/jump se perdían pasos
    // enteros y las rutas de combo casi nunca llegaban al especial o a la súper.
    cpuComboAwaiting[i]?.let { attempt ->
        val enteredExpectedState = me.state in stateForAction(attempt.action) &&
            (me.state != attempt.stateBefore || me.animationTimerMs != attempt.animationTimerBeforeMs)
        if (enteredExpectedState) {
            if (cpuComboQueue[i].firstOrNull() == attempt.action) cpuComboQueue[i].removeFirst()
            cpuComboAwaiting[i] = null
        }
    }
    val action = cpuComboQueue[i].firstOrNull() ?: return null
    if (cpuComboAwaiting[i] == null) {
        cpuComboAwaiting[i] = StreetFighterViewModel.CpuComboAttempt(
            action = action,
            stateBefore = me.state,
            animationTimerBeforeMs = me.animationTimerMs,
        )
    }
    return inputForAction(action)
}

/** Arma un SfInput de golpe (puño o patada) de la fuerza pedida. */
internal fun StreetFighterViewModel.cpuAttack(strength: SfAttackStrength, punch: Boolean): SfInput = if (punch) {
    when (strength) {
        SfAttackStrength.LIGHT -> SfInput(lightPunch = true)
        SfAttackStrength.MEDIUM -> SfInput(mediumPunch = true)
        SfAttackStrength.HEAVY -> SfInput(heavyPunch = true)
    }
} else {
    when (strength) {
        SfAttackStrength.LIGHT -> SfInput(lightKick = true)
        SfAttackStrength.MEDIUM -> SfInput(mediumKick = true)
        SfAttackStrength.HEAVY -> SfInput(heavyKick = true)
    }
}

/**
 * 🆕 (2026-07-18j) Golpe al azar SIN repetir el último (firma fuerza×tipo por peleadór).
 * Sustituye a randomCpuAttack(): con 6 combos y memoria de 1, la IA mezcla puños/patadas
 * y fuerzas en vez de encadenar el MISMO ataque una y otra vez.
 */
internal fun StreetFighterViewModel.variedCpuAttack(selfIndex: Int): SfInput {
    val i = selfIndex.coerceIn(0, 1)
    val history = cpuAttackHistory[i]
    val sig = (0 until 6).filterNot(history::contains).ifEmpty { (0 until 6).toList() }.random()
    history.addLast(sig)
    while (history.size > 3) history.removeFirst()
    return cpuAttack(SfAttackStrength.entries[sig / 2], punch = sig % 2 == 0)
}
