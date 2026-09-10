package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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
                    from in expected,
                    SfStateMachine.canEnter(from, to),
                    "canEnter($from -> $to)",
                )
            }
        }
    }

    @Test
    fun `ningun conjunto de origen esta vacio - todo destino es alcanzable`() {
        SfStateMachine.VALID_FROM.forEach { (to, from) ->
            assertTrue(from.isNotEmpty(), "VALID_FROM[$to] no debe estar vacío")
        }
    }

    @Test
    fun `desde IDLE se puede caminar saltar agacharse y golpear`() {
        // Nota (caracterización): los saltos DIAGONALES (JUMP_FORWARD/BACKWARD) NO salen directo
        // de IDLE — pasan por JUMP_START o desde caminar. IDLE solo entra directo a JUMP_UP.
        listOf(
            SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
            SfFighterState.JUMP_START, SfFighterState.JUMP_UP, SfFighterState.CROUCH_DOWN,
            SfFighterState.LIGHT_PUNCH, SfFighterState.HEAVY_PUNCH, SfFighterState.LIGHT_KICK,
        ).forEach {
            assertTrue(SfStateMachine.canEnter(SfFighterState.IDLE, it), "IDLE debe poder entrar a $it")
        }
        // Y los diagonales NO (van por JUMP_START):
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.JUMP_FORWARD))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.JUMP_BACKWARD))
    }

    @Test
    fun `STUN KO y VICTORY se pueden forzar desde CUALQUIER estado`() {
        listOf(SfFighterState.STUN, SfFighterState.KO, SfFighterState.VICTORY).forEach { forced ->
            SfFighterState.entries.forEach { from ->
                assertTrue(SfStateMachine.canEnter(from, forced), "$forced desde $from")
            }
        }
    }

    @Test
    fun `se puede atacar saliendo de la CARRERA - RUN`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.RUN, SfFighterState.LIGHT_PUNCH))
        assertTrue(SfStateMachine.canEnter(SfFighterState.RUN, SfFighterState.HEAVY_KICK))
    }

    @Test
    fun `la CARRERA solo continua un dash y nunca desde parado`() {
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
    fun `CONTRAATAQUE y el intento del DERRIBO CON PODER salen solo de neutro de pie`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.COUNTER))
        assertFalse(SfStateMachine.canEnter(SfFighterState.CROUCH, SfFighterState.COUNTER))
        assertFalse(SfStateMachine.canEnter(SfFighterState.JUMP_UP, SfFighterState.COUNTER))
        assertTrue(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.POWER_GRAB))
        assertFalse(SfStateMachine.canEnter(SfFighterState.CROUCH, SfFighterState.POWER_GRAB))
    }

    @Test
    fun `el remate del DERRIBO CON PODER solo sale de su propio intento - como THROW de GRAB`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.POWER_GRAB, SfFighterState.POWER_THROW))
        assertFalse(SfStateMachine.canEnter(SfFighterState.GRAB, SfFighterState.POWER_THROW))
        assertFalse(SfStateMachine.canEnter(SfFighterState.IDLE, SfFighterState.POWER_THROW))
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

    @Test
    fun `movimientos bajos recuperan a crouch o idle sin esperar al watchdog`() {
        SfStateMachine.CROUCH_RECOVERY_FROM.forEach { from ->
            assertTrue(SfStateMachine.canEnter(from, SfFighterState.CROUCH), "$from -> CROUCH")
            assertTrue(SfStateMachine.canEnter(from, SfFighterState.IDLE), "$from -> IDLE")
        }
    }

    @Test
    fun `ambos dashes recuperan inmediatamente a idle`() {
        SfStateMachine.DASH_RECOVERY_FROM.forEach { from ->
            assertTrue(SfStateMachine.canEnter(from, SfFighterState.IDLE), "$from -> IDLE")
        }
    }

    @Test
    fun `todas las animaciones que terminan en idle tienen permitida la recuperacion`() {
        // Lista independiente de los handlers de runStateHandler que llaman changeState(..., IDLE).
        // No usar IDLE_RECOVERY_FROM como entrada: esta prueba debe detectar si la tabla pierde
        // cualquiera de esas salidas y evitar otro personaje inmovil hasta el watchdog.
        val handlerIdleRecoveries = setOf(
            SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
            SfFighterState.JUMP_LAND, SfFighterState.CROUCH_UP, SfFighterState.IDLE_TURN,
            SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
            SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
            SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
            SfFighterState.BLOCK_HIGH, SfFighterState.PARRY_HIGH,
            SfFighterState.LONG_KICK, SfFighterState.OVERHEAD,
            SfFighterState.GRAB, SfFighterState.THROW, SfFighterState.TAUNT,
            SfFighterState.SUPER_ART, SfFighterState.RUN,
            SfFighterState.IDLE_RELAXED, SfFighterState.TALK,
            // 🆕 (2026-08-29) CONTRAATAQUE + DERRIBO CON PODER también recuperan a IDLE.
            SfFighterState.COUNTER, SfFighterState.POWER_GRAB, SfFighterState.POWER_THROW,
        ) + SF_BONUS_POWER_STATES + SfStateMachine.CROUCH_RECOVERY_FROM +
            SfStateMachine.DASH_RECOVERY_FROM

        handlerIdleRecoveries.forEach { from ->
            assertTrue(
                SfStateMachine.canEnter(from, SfFighterState.IDLE),
                "runStateHandler termina $from en IDLE, pero VALID_FROM lo prohibe",
            )
        }
    }

    @Test
    fun `las demas salidas automaticas del handler estan permitidas por la tabla`() {
        val transitions = listOf(
            SfFighterState.JUMP_START to SfFighterState.JUMP_UP,
            SfFighterState.JUMP_START to SfFighterState.JUMP_FORWARD,
            SfFighterState.JUMP_START to SfFighterState.JUMP_BACKWARD,
            SfFighterState.JUMP_UP to SfFighterState.JUMP_LAND,
            SfFighterState.JUMP_FORWARD to SfFighterState.JUMP_LAND,
            SfFighterState.JUMP_BACKWARD to SfFighterState.JUMP_LAND,
            SfFighterState.CROUCH_DOWN to SfFighterState.CROUCH,
            SfFighterState.CROUCH to SfFighterState.CROUCH_UP,
            SfFighterState.CROUCH_TURN to SfFighterState.CROUCH,
            SfFighterState.CROUCH_TURN to SfFighterState.CROUCH_UP,
            SfFighterState.DASH_FORWARD to SfFighterState.RUN,
            SfFighterState.BLOCK_HIGH to SfFighterState.CROUCH_DOWN,
            SfFighterState.BLOCK_HIGH to SfFighterState.WALK_BACKWARD,
            SfFighterState.BLOCK_LOW to SfFighterState.CROUCH,
            SfFighterState.BLOCK_LOW to SfFighterState.CROUCH_UP,
        ) + SfStateMachine.CROUCH_RECOVERY_FROM.map { it to SfFighterState.CROUCH }

        transitions.forEach { (from, to) ->
            assertTrue(
                SfStateMachine.canEnter(from, to),
                "runStateHandler necesita $from -> $to, pero VALID_FROM lo prohibe",
            )
        }
    }

    @Test
    fun `la SUPER ART es alcanzable desde todo origen que acepta poderes`() {
        SfStateMachine.SPECIAL_VALID_FROM.forEach { from ->
            assertTrue(
                SfStateMachine.canEnter(from, SfFighterState.SUPER_ART),
                "SUPER_ART debe aceptar el input desde $from",
            )
        }
    }

    @Test
    fun `la SUPER ART normal puede cortar la carrera de IA contra IA`() {
        assertTrue(SfStateMachine.canEnter(SfFighterState.RUN, SfFighterState.SUPER_ART))
    }
}
