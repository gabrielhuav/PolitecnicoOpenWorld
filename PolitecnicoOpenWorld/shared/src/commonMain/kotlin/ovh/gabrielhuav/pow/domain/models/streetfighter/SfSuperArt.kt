package ovh.gabrielhuav.pow.domain.models.streetfighter

/** Reglas puras de la SUPER ART, compartidas por Android e iOS. */
object SfSuperArt {

    /** Los 54 frames lógicos actuales pasan de ~0.9 s a ~1.8 s. */
    const val ANIMATION_SLOWDOWN = 2f

    fun animationDurationMultiplier(state: SfFighterState): Float =
        if (state == SfFighterState.SUPER_ART) ANIMATION_SLOWDOWN else 1f

    /** Los 18 atlas carecen de hitbox en sus cuadros `super-*`; el golpe ocurre al centro. */
    fun impactFrameIndex(animationSize: Int): Int =
        ((animationSize.coerceAtLeast(1) - 1) / 2).coerceAtLeast(0)

    fun shouldAutoConnect(fighter: SfFighter, animationSize: Int): Boolean =
        fighter.state == SfFighterState.SUPER_ART &&
            !fighter.attackStruck &&
            fighter.animationFrame == impactFrameIndex(animationSize)

    /** Preparación atómica: al arrancar la SUPER ART la barra se vacía por completo. */
    fun consumeMeter(fighter: SfFighter): SfFighter = fighter.copy(
        velocityX = 0f,
        velocityY = 0f,
        attackStruck = false,
        superMeter = 0,
    )
}
