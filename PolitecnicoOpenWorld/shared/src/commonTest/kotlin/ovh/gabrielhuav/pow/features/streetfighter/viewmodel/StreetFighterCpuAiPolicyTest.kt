package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreetFighterCpuAiPolicyTest {

    @Test
    fun `el hold conserva direcciones pero libera todos los botones de un toque`() {
        val released = SfInput(
            down = true,
            forward = true,
            backward = true,
            up = true,
            lightPunch = true,
            mediumPunch = true,
            heavyPunch = true,
            lightKick = true,
            mediumKick = true,
            heavyKick = true,
            special = SfAttackStrength.HEAVY,
            bonusPower = 2,
            dashForward = true,
            dashBackward = true,
            parry = true,
            grab = true,
            taunt = true,
            superArt = true,
        ).withCpuOneShotsReleased()

        assertTrue(released.down)
        assertTrue(released.forward)
        assertTrue(released.backward)
        assertFalse(released.up)
        assertFalse(released.lightPunch)
        assertFalse(released.mediumPunch)
        assertFalse(released.heavyPunch)
        assertFalse(released.lightKick)
        assertFalse(released.mediumKick)
        assertFalse(released.heavyKick)
        assertEquals(null, released.special)
        assertEquals(null, released.bonusPower)
        assertFalse(released.dashForward)
        assertFalse(released.dashBackward)
        assertFalse(released.parry)
        assertFalse(released.grab)
        assertFalse(released.taunt)
        assertFalse(released.superArt)
    }

    @Test
    fun `cada dificultad garantiza la super antes en IA contra IA`() {
        SfCpuDifficulty.entries.forEach { difficulty ->
            assertTrue(
                cpuSuperCommitDelayMs(difficulty, aiVs = true) <
                    cpuSuperCommitDelayMs(difficulty, aiVs = false),
                difficulty.name,
            )
        }
    }

    @Test
    fun `la garantia de super se vuelve mas rapida al subir dificultad`() {
        val waits = SfCpuDifficulty.entries.map { cpuSuperCommitDelayMs(it, aiVs = true) }
        assertEquals(listOf(900L, 650L, 420L, 250L), waits)
        assertTrue(waits.zipWithNext().all { (slower, faster) -> slower > faster })
    }

    @Test
    fun `el show IA contra IA nunca degrada una CPU avanzada hasta basica`() {
        assertEquals(
            SfCpuDifficulty.AVANZADA,
            weakerAiVsDifficulty(SfCpuDifficulty.PESADILLA),
        )
        assertEquals(
            SfCpuDifficulty.NORMAL,
            weakerAiVsDifficulty(SfCpuDifficulty.AVANZADA),
        )
    }
}
