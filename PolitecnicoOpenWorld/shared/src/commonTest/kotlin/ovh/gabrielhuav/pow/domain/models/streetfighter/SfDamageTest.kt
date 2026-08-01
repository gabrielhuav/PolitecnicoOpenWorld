package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests de CARACTERIZACIÓN del daño base (Fase 1 del refactor). Fija el mapeo actual: súper,
 * fatality y agarre tienen daño propio; el resto usa la fuerza. El plan marcó este cálculo porque
 * ya hubo un bug real de multiplayer aquí.
 */
class SfDamageTest {

    @Test
    fun `super fatality y agarre usan su dano propio - no la fuerza`() {
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
                assertEquals(st.damage, SfDamage.forAttack(state, st), "$state con $st")
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
    fun `ATTACK_META da fuerza y tipo y los no-ataques no estan`() {
        assertEquals(SfAttackStrength.LIGHT, SfDamage.ATTACK_META.getValue(SfFighterState.LIGHT_PUNCH).strength)
        assertEquals(SfAttackType.PUNCH, SfDamage.ATTACK_META.getValue(SfFighterState.LIGHT_PUNCH).type)
        assertEquals(SfAttackStrength.HEAVY, SfDamage.ATTACK_META.getValue(SfFighterState.SWEEP).strength)
        assertEquals(SfAttackType.KICK, SfDamage.ATTACK_META.getValue(SfFighterState.SWEEP).type)
        assertFalse(SfFighterState.IDLE in SfDamage.ATTACK_META)
        assertFalse(SfFighterState.WALK_FORWARD in SfDamage.ATTACK_META)
        assertFalse(SfFighterState.STUN in SfDamage.ATTACK_META)
    }

    @Test
    fun `bloqueo - los normales hacen 0 y el chip es base entre 6 - min 1`() {
        assertEquals(0, SfDamage.resolvedDamage(20, blocked = true, chipAttack = false, comboHits = 1))
        assertEquals(20 / 6, SfDamage.resolvedDamage(20, blocked = true, chipAttack = true, comboHits = 1))
        assertEquals(1, SfDamage.resolvedDamage(3, blocked = true, chipAttack = true, comboHits = 1))
    }

    @Test
    fun `combo escala el dano hacia abajo con piso 50 por ciento y minimo 1`() {
        assertEquals(100, SfDamage.resolvedDamage(100, blocked = false, chipAttack = false, comboHits = 1))
        assertEquals(90, SfDamage.resolvedDamage(100, blocked = false, chipAttack = false, comboHits = 2))
        assertEquals(50, SfDamage.resolvedDamage(100, blocked = false, chipAttack = false, comboHits = 10))
        assertEquals(1, SfDamage.resolvedDamage(1, blocked = false, chipAttack = false, comboHits = 20))
    }

    @Test
    fun `CHIP_ATTACK_STATES son especial super y fatality - no los normales`() {
        listOf(
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM,
            SfFighterState.SPECIAL_1_HEAVY, SfFighterState.SUPER_ART, SfFighterState.FATALITY,
        ).forEach { assertTrue(it in SfDamage.CHIP_ATTACK_STATES, "$it debe hacer chip") }

        listOf(
            SfFighterState.LIGHT_PUNCH, SfFighterState.HEAVY_KICK, SfFighterState.SWEEP,
            SfFighterState.OVERHEAD, SfFighterState.GRAB,
        ).forEach { assertFalse(it in SfDamage.CHIP_ATTACK_STATES, "$it NO debe hacer chip") }
    }
}
