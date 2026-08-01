package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.math.abs

/** Reglas puras de la SUPER ART, compartidas por Android e iOS. */
object SfSuperArt {

    /** Los 54 frames lógicos actuales pasan de ~0.9 s a ~1.8 s. */
    const val ANIMATION_SLOWDOWN = 2f

    /** Alcance horizontal del impacto sintético (los atlas no traen hitbox `super-*`). */
    const val HIT_RANGE = 120f

    fun animationDurationMultiplier(state: SfFighterState): Float =
        if (state == SfFighterState.SUPER_ART) ANIMATION_SLOWDOWN else 1f

    /** Los 18 atlas carecen de hitbox en sus cuadros `super-*`; el golpe ocurre al centro. */
    fun impactFrameIndex(animationSize: Int): Int =
        ((animationSize.coerceAtLeast(1) - 1) / 2).coerceAtLeast(0)

    fun shouldAutoConnect(fighter: SfFighter, animationSize: Int): Boolean =
        fighter.state == SfFighterState.SUPER_ART &&
            !fighter.attackStruck &&
            fighter.animationFrame == impactFrameIndex(animationSize)

    /**
     * El cuadro central solo conecta si el rival sigue a alcance y en el suelo. Así la SUPER ART
     * deja de ser un golpe global inevitable: saltar o crear espacio durante el arranque la hace
     * fallar; bloquear/parry se resuelven después por las reglas normales de combate.
     */
    fun canAutoConnect(attacker: SfFighter, defender: SfFighter, animationSize: Int): Boolean =
        shouldAutoConnect(attacker, animationSize) &&
            abs(attacker.x - defender.x) <= HIT_RANGE &&
            !defender.isAirborne &&
            !defender.downed

    /** Preparación atómica: al arrancar la SUPER ART la barra se vacía por completo. */
    fun consumeMeter(fighter: SfFighter): SfFighter = fighter.copy(
        velocityX = 0f,
        velocityY = 0f,
        attackStruck = false,
        superMeter = 0,
    )
}
