package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HITSTUN_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfSuperArt
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombo
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🤖 IA DE LA CPU — política y tuning.
//
// Reglas PURAS de dificultad (niveles y probabilidad de combo, plazos de súper, lectura de
// interrupción) y la defensa contra la súper rival.
//
// Separado de StreetFighterCpuAi.kt (2026-09-23): aquel archivo llegó a 1094 líneas y 09 §0
// pide partir por encima de 1000. SOLO se movieron líneas; ninguna función cambió.
//
// ⚠️ Son EXTENSIONES del VM, no miembros. Los CAMPOS de estado de la IA (`cpuComboQueue`,
// `cpuAttackHistory`, `comboCatalog`…) siguen en la clase a propósito: aquí solo vive la LÓGICA.
// NO recrees estas funciones como miembros — quedarían gemelas y ganaría el miembro en silencio
// (ver 09 §0).
// ─────────────────────────────────────────────────────────────────────────────

/** Regla absoluta del modo IA vs IA; fuera de él no modifica la política de la CPU. */
internal fun forcedAiVsAiSuperInput(aiVsAi: Boolean, superReady: Boolean): SfInput? =
    if (aiVsAi && superReady) SfInput(superArt = true) else null

/** Nivel máximo real del catálogo: la dificultad manda aunque la intensidad de campaña sea 0. */
internal fun cpuComboMaxLevel(difficulty: SfCpuDifficulty, intensity: Float): Int = when (difficulty) {
    SfCpuDifficulty.BASICA -> 1
    SfCpuDifficulty.NORMAL -> 2
    SfCpuDifficulty.AVANZADA -> if (intensity > 0.55f) 4 else 3
    SfCpuDifficulty.PESADILLA -> 4
}

/** Probabilidad de abrir una ruta cuando existe rango; acotada para respetar estilos. */
internal fun cpuComboStartChance(
    difficulty: SfCpuDifficulty,
    intensity: Float,
    styleBias: Float,
): Float {
    val base = when (difficulty) {
        SfCpuDifficulty.BASICA -> 0f
        SfCpuDifficulty.NORMAL -> 0.30f + 0.12f * intensity
        SfCpuDifficulty.AVANZADA -> 0.58f + 0.22f * intensity
        SfCpuDifficulty.PESADILLA -> 0.88f
    }
    val minimum = when (difficulty) {
        SfCpuDifficulty.BASICA -> 0f
        SfCpuDifficulty.NORMAL -> 0.20f
        SfCpuDifficulty.AVANZADA -> 0.48f
        SfCpuDifficulty.PESADILLA -> 0.72f
    }
    return (base * styleBias).coerceIn(minimum, 0.95f)
}

/** Las rutas de mayor nivel incluyen salto/carrera y necesitan más tiempo de animación. */
internal fun cpuComboRouteTimeoutMs(difficulty: SfCpuDifficulty): Long = when (difficulty) {
    SfCpuDifficulty.BASICA -> StreetFighterViewModel.COMBO_ROUTE_TIMEOUT_MS
    SfCpuDifficulty.NORMAL -> 2800L
    SfCpuDifficulty.AVANZADA -> 3600L
    SfCpuDifficulty.PESADILLA -> 4200L
}

/** Una ruta ofensiva debe encadenar al menos dos acciones; un solo golpe no cuenta como combo. */
internal fun isCpuComboRoute(combo: SfCombo, maxLevel: Int): Boolean =
    combo.level <= maxLevel && combo.steps.size >= 2

/**
 * ¿La CPU está INTERRUMPIDA ahora mismo, o sea sin poder empezar una acción nueva?
 *
 * ⚠️ Pregunta por [SF_HITSTUN_STATES], **NUNCA** por `SF_HURT_STATES`. El nombre de aquel engaña:
 * es "PUEDE ser golpeado" (hurtbox activa) e incluye IDLE, caminar, agacharse y los seis normales.
 *
 * Con `SF_HURT_STATES` esto era `true` casi siempre estando de pie, y como los tres sitios que lo
 * consultan devuelven un `SfInput()` VACÍO cuando da `true`, la CPU se quedaba tiesa: al llenarse
 * su medidor `buildCpuInput` cortaba por [maybeCpuSuperArtInput] antes que nada y ya no emitía ni
 * súper, ni golpes, ni movimiento — para siempre, porque el medidor solo se vacía al lanzar la
 * súper. El resto de esa función era código muerto. Se ganaba la pelea sin pelear.
 */
internal fun cpuIsInterrupted(
    state: SfFighterState,
    isAirborne: Boolean,
    downed: Boolean,
    metamorphosing: Boolean = false,
): Boolean = isAirborne || downed || metamorphosing || state in SF_HITSTUN_STATES

internal enum class CpuSuperDefense { INTERRUPT, BACKDASH, JUMP_BACK }

/** Política pura: la dificultad básica conserva una ventana didáctica; las demás reaccionan. */
internal fun cpuSuperDefensePlan(
    difficulty: SfCpuDifficulty,
    distance: Float,
    beforeImpact: Boolean,
): CpuSuperDefense? = when {
    distance > SfSuperArt.HIT_RANGE -> null
    difficulty == SfCpuDifficulty.BASICA -> null
    beforeImpact && distance <= StreetFighterViewModel.CPU_CLINCH_DIST + 16f &&
        difficulty in setOf(SfCpuDifficulty.AVANZADA, SfCpuDifficulty.PESADILLA) ->
        CpuSuperDefense.INTERRUPT
    difficulty == SfCpuDifficulty.PESADILLA -> CpuSuperDefense.BACKDASH
    else -> CpuSuperDefense.JUMP_BACK
}

/** Traduce la lectura del arranque rival a un input defensivo que puede evitarlo o cancelarlo. */
internal fun StreetFighterViewModel.cpuDefenseAgainstSuper(
    me: SfFighter,
    foe: SfFighter,
    difficulty: SfCpuDifficulty,
): SfInput? {
    if (foe.state != SfFighterState.SUPER_ART ||
        cpuIsInterrupted(me.state, me.isAirborne, me.downed)
    ) return null
    val animationSize = dataFor(foe).animations[SfFighterState.SUPER_ART.jsKey]?.size ?: 0
    val beforeImpact = foe.animationFrame < SfSuperArt.impactFrameIndex(animationSize)
    return when (cpuSuperDefensePlan(difficulty, abs(me.x - foe.x), beforeImpact)) {
        CpuSuperDefense.INTERRUPT -> SfInput(lightPunch = true)
        CpuSuperDefense.BACKDASH -> if (hasAnim(me, SfFighterState.DASH_BACKWARD)) {
            cpuRetreatFlags(me, foe).copy(dashBackward = true)
        } else {
            cpuJumpBack(me, foe)
        }
        CpuSuperDefense.JUMP_BACK -> cpuJumpBack(me, foe)
        null -> null
    }
}

/** Conserva solo direcciones sostenidas; cada botón de la CPU dura exactamente un tick. */
internal fun SfInput.withCpuOneShotsReleased(): SfInput = copy(
    up = false,
    lightPunch = false, mediumPunch = false, heavyPunch = false,
    lightKick = false, mediumKick = false, heavyKick = false,
    special = null, bonusPower = null,
    dashForward = false, dashBackward = false,
    parry = false, grab = false, taunt = false, superArt = false,
)

/** Plazo máximo antes de que una CPU deje el azar y garantice su SUPER ART. */
internal fun cpuSuperCommitDelayMs(difficulty: SfCpuDifficulty, aiVs: Boolean): Long =
    when (difficulty) {
        SfCpuDifficulty.BASICA -> if (aiVs) 700L else 1400L
        SfCpuDifficulty.NORMAL -> if (aiVs) 450L else 900L
        SfCpuDifficulty.AVANZADA -> if (aiVs) 250L else 500L
        SfCpuDifficulty.PESADILLA -> if (aiVs) 120L else 250L
    }
