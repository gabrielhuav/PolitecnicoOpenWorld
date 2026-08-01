package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SfSuperArtTest {

    private fun fighter(
        frame: Int,
        struck: Boolean = false,
        x: Float = 0f,
        state: SfFighterState = SfFighterState.SUPER_ART,
    ) = SfFighter(
        id = SfFighterId.PRANKEDY,
        playerIndex = 0,
        x = x,
        direction = SfDirection.RIGHT,
        state = state,
        animationFrame = frame,
        attackStruck = struck,
        superMeter = SfConstants.SUPER_METER_MAX,
    )

    @Test
    fun `los nueve cuadros de super impactan una vez en el cuadro central`() {
        assertEquals(4, SfSuperArt.impactFrameIndex(animationSize = 9))
        assertFalse(SfSuperArt.shouldAutoConnect(fighter(frame = 3), animationSize = 9))
        assertTrue(SfSuperArt.shouldAutoConnect(fighter(frame = 4), animationSize = 9))
        assertFalse(SfSuperArt.shouldAutoConnect(fighter(frame = 4, struck = true), animationSize = 9))
    }

    @Test
    fun `la super solo conecta a rango y se puede esquivar saltando`() {
        val attacker = fighter(frame = 4, x = 100f)
        val grounded = fighter(frame = 0, x = 100f + SfSuperArt.HIT_RANGE, state = SfFighterState.IDLE)
        val tooFar = grounded.copy(x = grounded.x + 1f)
        val jumping = grounded.copy(state = SfFighterState.JUMP_UP, velocityY = -100f)

        assertTrue(SfSuperArt.canAutoConnect(attacker, grounded, animationSize = 9))
        assertFalse(SfSuperArt.canAutoConnect(attacker, tooFar, animationSize = 9))
        assertFalse(SfSuperArt.canAutoConnect(attacker, jumping, animationSize = 9))
    }

    @Test
    fun `golpear al atacante cancela la super antes del impacto`() {
        val interrupted = fighter(frame = 4, state = SfFighterState.HURT_BODY_LIGHT)
        val defender = fighter(frame = 0, x = 60f, state = SfFighterState.IDLE)

        assertFalse(SfSuperArt.canAutoConnect(interrupted, defender, animationSize = 9))
        assertTrue(
            SfStateMachine.canEnter(SfFighterState.SUPER_ART, SfFighterState.HURT_BODY_LIGHT),
        )
    }

    @Test
    fun `la super dura el doble y no ralentiza otros movimientos`() {
        assertEquals(2f, SfSuperArt.animationDurationMultiplier(SfFighterState.SUPER_ART))
        assertEquals(1f, SfSuperArt.animationDurationMultiplier(SfFighterState.HEAVY_PUNCH))
    }

    @Test
    fun `arrancar la super consume toda la barra`() {
        assertEquals(0, SfSuperArt.consumeMeter(fighter(frame = 0)).superMeter)
    }
}
