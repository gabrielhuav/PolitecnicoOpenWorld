package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SfSuperArtTest {

    private fun fighter(frame: Int, struck: Boolean = false) = SfFighter(
        id = SfFighterId.PRANKEDY,
        playerIndex = 0,
        x = 0f,
        direction = SfDirection.RIGHT,
        state = SfFighterState.SUPER_ART,
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
    fun `la super dura el doble y no ralentiza otros movimientos`() {
        assertEquals(2f, SfSuperArt.animationDurationMultiplier(SfFighterState.SUPER_ART))
        assertEquals(1f, SfSuperArt.animationDurationMultiplier(SfFighterState.HEAVY_PUNCH))
    }

    @Test
    fun `arrancar la super consume toda la barra`() {
        assertEquals(0, SfSuperArt.consumeMeter(fighter(frame = 0)).superMeter)
    }
}
