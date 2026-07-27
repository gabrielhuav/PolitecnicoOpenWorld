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

    // Peleadores cuyo ÚLTIMO poder es una metamorfosis automática (no lanzable): usable = count-1.
    // 🆕 (2026-07-25) Yoalli se suma a la lista (su P10 = metamorfosis → La Presidenta, jefe FINAL).
    private val metamorphFighters = setOf(SfFighterId.LA_PRESIDENTA, SfFighterId.YOALLI_EHECATL)

    @Test
    fun `Presidenta y Yoalli tienen un usable menos (su ultimo poder es metamorfosis)`() {
        metamorphFighters.forEach { id ->
            assertEquals("$id", id.bonusPowerCount - 1, sfUsableBonusPowerCount(id))
        }
    }

    @Test
    fun `el resto usa su bonusPowerCount tal cual, y nunca es negativo`() {
        SfFighterId.entries.forEach { id ->
            val usable = sfUsableBonusPowerCount(id)
            assertTrue("$id usable >= 0", usable >= 0)
            if (id !in metamorphFighters) {
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
