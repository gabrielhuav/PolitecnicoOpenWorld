package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SfMetamorphosisTest {

    private fun fighter(
        id: SfFighterId,
        hp: Int = SfConstants.HEALTH_MAX_HIT_POINTS / 4,
        metamorphosed: Boolean = false,
    ) = SfFighter(
        id = id,
        playerIndex = 0,
        x = 100f,
        direction = SfDirection.RIGHT,
        hitPoints = hp,
        metamorphosed = metamorphosed,
    )

    @Test
    fun `Presidenta vuelve a metamorfosearse en Yoalli`() {
        assertEquals(
            SfMetamorphosis.Plan(SfFighterState.BONUS_POWER_11, SfFighterId.YOALLI_EHECATL),
            SfMetamorphosis.planFor(fighter(SfFighterId.LA_PRESIDENTA), roundNumber = 2),
        )
    }

    @Test
    fun `Yoalli conserva su metamorfosis en Presidenta`() {
        assertEquals(
            SfMetamorphosis.Plan(SfFighterState.BONUS_POWER_10, SfFighterId.LA_PRESIDENTA),
            SfMetamorphosis.planFor(fighter(SfFighterId.YOALLI_EHECATL), roundNumber = 2),
        )
    }

    @Test
    fun `no transforma con mas de un cuarto de vida ni fuera de ronda dos ni dos veces`() {
        val presidenta = fighter(SfFighterId.LA_PRESIDENTA)
        assertNull(SfMetamorphosis.planFor(presidenta.copy(hitPoints = 51), roundNumber = 2))
        assertNull(SfMetamorphosis.planFor(presidenta, roundNumber = 1))
        assertNull(SfMetamorphosis.planFor(presidenta, roundNumber = 3))
        assertNull(SfMetamorphosis.planFor(presidenta.copy(metamorphosed = true), roundNumber = 2))
    }
}
