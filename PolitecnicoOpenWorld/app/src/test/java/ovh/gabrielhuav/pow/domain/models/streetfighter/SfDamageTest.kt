package ovh.gabrielhuav.pow.domain.models.streetfighter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de CARACTERIZACIÓN del daño base (Fase 1 del refactor). Fija el mapeo actual: súper,
 * fatality y agarre tienen daño propio; el resto usa la fuerza. El plan marcó este cálculo porque
 * ya hubo un bug real de multiplayer aquí.
 */
class SfDamageTest {

    @Test
    fun `super, fatality y agarre usan su dano propio (no la fuerza)`() {
        assertEquals(SfConstants.SUPER_ART_DAMAGE, SfDamage.forAttack(SfFighterState.SUPER_ART, SfAttackStrength.LIGHT))
        assertEquals(SfConstants.FATALITY_DAMAGE, SfDamage.forAttack(SfFighterState.FATALITY, SfAttackStrength.LIGHT))
        assertEquals(SfConstants.THROW_DAMAGE, SfDamage.forAttack(SfFighterState.GRAB, SfAttackStrength.HEAVY))
    }

    @Test
    fun `los golpes normales usan el dano de su fuerza`() {
        val normales = listOf(
            SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.LIGHT_KICK, SfFighterState.SWEEP, SfFighterState.OVERHEAD,
            SfFighterState.AIR_PUNCH, SfFighterState.LONG_KICK,
        )
        normales.forEach { state ->
            SfAttackStrength.entries.forEach { st ->
                assertEquals("$state con $st", st.damage, SfDamage.forAttack(state, st))
            }
        }
    }

    @Test
    fun `el dano de las fuerzas es el esperado`() {
        assertEquals(12, SfAttackStrength.LIGHT.damage)
        assertEquals(20, SfAttackStrength.MEDIUM.damage)
        assertEquals(28, SfAttackStrength.HEAVY.damage)
    }

    @Test
    fun `CHIP_ATTACK_STATES son especial, super y fatality (no los normales)`() {
        listOf(
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM,
            SfFighterState.SPECIAL_1_HEAVY, SfFighterState.SUPER_ART, SfFighterState.FATALITY,
        ).forEach { assertTrue("$it debe hacer chip", it in SfDamage.CHIP_ATTACK_STATES) }

        listOf(
            SfFighterState.LIGHT_PUNCH, SfFighterState.HEAVY_KICK, SfFighterState.SWEEP,
            SfFighterState.OVERHEAD, SfFighterState.GRAB,
        ).forEach { assertFalse("$it NO debe hacer chip", it in SfDamage.CHIP_ATTACK_STATES) }
    }
}
