package ovh.gabrielhuav.pow.domain.models.streetfighter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de CARACTERIZACIÓN de los poderes bonus (Fase 1 del refactor). Fija el conteo lanzable y
 * el mapeo índice → estado.
 */
class SfBonusPowerTest {

    @Test
    fun `La Presidenta tiene un usable menos (el P11 es metamorfosis automatica)`() {
        assertEquals(
            SfFighterId.LA_PRESIDENTA.bonusPowerCount - 1,
            sfUsableBonusPowerCount(SfFighterId.LA_PRESIDENTA),
        )
    }

    @Test
    fun `el resto usa su bonusPowerCount tal cual, y nunca es negativo`() {
        SfFighterId.entries.forEach { id ->
            val usable = sfUsableBonusPowerCount(id)
            assertTrue("$id usable >= 0", usable >= 0)
            if (id != SfFighterId.LA_PRESIDENTA) {
                assertEquals("$id", id.bonusPowerCount, usable)
            }
        }
    }

    @Test
    fun `sfBonusPowerState mapea 1-based al estado y fuera de rango es null`() {
        assertEquals(SfFighterState.BONUS_POWER_1, sfBonusPowerState(1))
        assertEquals(SfFighterState.BONUS_POWER_11, sfBonusPowerState(11))
        assertNull(sfBonusPowerState(0))
        assertNull(sfBonusPowerState(12))
    }

    @Test
    fun `bonusPowerIndex es el inverso de sfBonusPowerState`() {
        (1..SF_BONUS_POWER_STATES.size).forEach { i ->
            assertEquals(i, sfBonusPowerState(i)!!.bonusPowerIndex())
        }
    }
}
