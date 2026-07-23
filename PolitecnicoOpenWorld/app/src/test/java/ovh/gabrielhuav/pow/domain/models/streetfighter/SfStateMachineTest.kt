package ovh.gabrielhuav.pow.domain.models.streetfighter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de CARACTERIZACIÓN de la máquina de estados (Fase 1 del refactor del motor). Fijan lo que
 * HOY hace `SfStateMachine.VALID_FROM` para DETECTAR si una extracción/refactor posterior la rompe.
 * No prueban "lo correcto", prueban "lo actual".
 */
class SfStateMachineTest {

    @Test
    fun `canEnter concuerda con la tabla VALID_FROM`() {
        for (to in SfFighterState.entries) {
            val expected = SfStateMachine.VALID_FROM[to] ?: emptySet()
            for (from in SfFighterState.entries) {
                assertEquals(
                    "canEnter($from -> $to)",
                    from in expected,
                    SfStateMachine.canEnter(from, to),
                )
            }
        }
    }

    @Test
    fun `ningun conjunto de origen esta vacio (todo destino es alcanzable)`() {
        SfStateMachine.VALID_FROM.forEach { (to, from) ->
            assertTrue("VALID_FROM[$to] no debe estar vacío", from.isNotEmpty())
        }
    }

    @Test
    fun `desde IDLE se puede caminar, saltar, agacharse y golpear`() {
        // Nota (caracterización): los saltos DIAGONALES (JUMP_FORWARD/BACKWARD) NO salen directo
        // de IDLE — pasan por JUMP_START o desde caminar. IDLE solo entra directo a JUMP_UP.
        listOf(
            SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
            SfFighterState.JUMP_START, SfFighterState.JUMP_UP, SfFighterState.CROUCH_DOWN,
            SfFighterState.LIGHT_PUNCH, SfFighterState.HEAVY_PUNCH, SfFighterState.LIGHT_KICK,
        ).forEach {
            assertTrue("IDLE debe poder entrar a $it", SfStateMachine.canEnter(SfFighterState.IDLE, it))
        }
        // Y los diagonales NO (van por JUMP_START):
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.JUMP_FORWARD))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.JUMP_BACKWARD))
    }

    @Test
    fun `STUN, KO y VICTORY se pueden forzar desde CUALQUIER estado`() {
        listOf(SfFighterState.STUN, SfFighterState.KO, SfFighterState.VICTORY).forEach { forced ->
            SfFighterState.entries.forEach { from ->
                assertTrue("$forced desde $from", SfStateMachine.canEnter(from, forced))
            }
        }
    }

    @Test
    fun `se puede atacar saliendo de la CARRERA (RUN)`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.RUN, SfFighterState.LIGHT_PUNCH))
        assertTrue(SfStateMachine.canEnter(SfFighterState.RUN, SfFighterState.HEAVY_KICK))
    }

    @Test
    fun `la CARRERA solo continua un dash, nunca desde parado`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.DASH_FORWARD, SfFighterState.RUN))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.RUN))
        assertFalse(SfStateMachine.canEnter(SfFighterState.WALK_FORWARD, SfFighterState.RUN))
    }

    @Test
    fun `OVERHEAD y PATADA LARGA salen caminando adelante`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.WALK_FORWARD, SfFighterState.OVERHEAD))
        assertTrue(SfStateMachine.canEnter(SfFighterState.WALK_FORWARD, SfFighterState.LONG_KICK))
    }

    @Test
    fun `la BARRIDA solo sale agachado`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.CROUCH, SfFighterState.SWEEP))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.SWEEP))
    }

    @Test
    fun `AGARRE y PARRY alto salen solo de neutro de pie`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.GRAB))
        assertFalse(SfStateMachine.canEnter(SfFighterState.CROUCH, SfFighterState.GRAB))
        assertFalse(SfStateMachine.canEnter(SfFighterState.JUMP_UP, SfFighterState.PARRY_HIGH))
    }

    @Test
    fun `los ataques aereos solo salen en el aire`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.JUMP_UP, SfFighterState.AIR_PUNCH))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.AIR_PUNCH))
    }

    @Test
    fun `el FATALITY solo sale en carrera o dash`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.RUN, SfFighterState.FATALITY))
        assertTrue(SfStateMachine.canEnter(SfFighterState.DASH_FORWARD, SfFighterState.FATALITY))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.FATALITY))
    }
}
