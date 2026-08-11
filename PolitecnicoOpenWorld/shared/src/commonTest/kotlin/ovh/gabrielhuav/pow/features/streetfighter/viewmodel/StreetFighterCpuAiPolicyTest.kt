package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HITSTUN_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfSuperArt
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombo
import ovh.gabrielhuav.pow.features.streetfighter.data.SfComboAction
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
    fun `IA contra IA fuerza la super al llenar la barra sin esperar otra decision`() {
        val forced = forcedAiVsAiSuperInput(aiVsAi = true, superReady = true)
        assertEquals(SfInput(superArt = true), forced)

        val ready = ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter(
            id = ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId.PRANKEDY,
            playerIndex = 0,
            x = 0f,
            direction = ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection.RIGHT,
            state = SfFighterState.SUPER_ART,
            superMeter = SfConstants.SUPER_METER_MAX,
        )
        assertEquals(0, SfSuperArt.consumeMeter(ready).superMeter)
    }

    @Test
    fun `la regla absoluta de super no altera ningun otro modo ni una barra incompleta`() {
        assertEquals(null, forcedAiVsAiSuperInput(aiVsAi = false, superReady = true))
        assertEquals(null, forcedAiVsAiSuperInput(aiVsAi = true, superReady = false))
    }

    @Test
    fun `la super rival se puede interrumpir de cerca antes de impactar`() {
        assertEquals(
            CpuSuperDefense.INTERRUPT,
            cpuSuperDefensePlan(SfCpuDifficulty.AVANZADA, distance = 70f, beforeImpact = true),
        )
        assertEquals(
            CpuSuperDefense.INTERRUPT,
            cpuSuperDefensePlan(SfCpuDifficulty.PESADILLA, distance = 45f, beforeImpact = true),
        )
    }

    @Test
    fun `la IA esquiva una super a alcance y no reacciona si ya esta fuera`() {
        assertEquals(
            CpuSuperDefense.JUMP_BACK,
            cpuSuperDefensePlan(SfCpuDifficulty.NORMAL, distance = 100f, beforeImpact = true),
        )
        assertEquals(
            CpuSuperDefense.BACKDASH,
            cpuSuperDefensePlan(SfCpuDifficulty.PESADILLA, distance = 100f, beforeImpact = false),
        )
        assertEquals(
            null,
            cpuSuperDefensePlan(
                SfCpuDifficulty.PESADILLA,
                distance = SfSuperArt.HIT_RANGE + 1f,
                beforeImpact = true,
            ),
        )
    }

    @Test
    fun `la dificultad basica conserva la ventana didactica ante la super`() {
        assertEquals(
            null,
            cpuSuperDefensePlan(SfCpuDifficulty.BASICA, distance = 40f, beforeImpact = true),
        )
    }

    @Test
    fun `la dificultad desbloquea combos avanzados incluso sin intensidad de campana`() {
        val levels = SfCpuDifficulty.entries.map { cpuComboMaxLevel(it, intensity = 0f) }
        assertEquals(listOf(1, 2, 3, 4), levels)
        assertEquals(4, cpuComboMaxLevel(SfCpuDifficulty.AVANZADA, intensity = 0.8f))
    }

    @Test
    fun `pesadilla conserva alta probabilidad de combo incluso para un zoner`() {
        val chances = SfCpuDifficulty.entries.map {
            cpuComboStartChance(it, intensity = 0f, styleBias = 1f)
        }
        assertTrue(chances.zipWithNext().all { (easier, harder) -> easier < harder })
        assertTrue(cpuComboStartChance(SfCpuDifficulty.PESADILLA, 0f, 0.6f) >= 0.72f)
    }

    @Test
    fun `solo rutas de dos o mas acciones cuentan como combo`() {
        val single = SfCombo("single", "", "", "", "", 1, listOf(SfComboAction.LIGHT_PUNCH))
        val chain = single.copy(
            id = "chain",
            steps = listOf(SfComboAction.LIGHT_PUNCH, SfComboAction.MEDIUM_PUNCH),
        )
        assertFalse(isCpuComboRoute(single, maxLevel = 4))
        assertTrue(isCpuComboRoute(chain, maxLevel = 4))
        assertFalse(isCpuComboRoute(chain.copy(level = 4), maxLevel = 3))
    }

    @Test
    fun `las rutas complejas reciben mas tiempo al subir dificultad`() {
        val timeouts = SfCpuDifficulty.entries.map(::cpuComboRouteTimeoutMs)
        assertTrue(timeouts.zipWithNext().all { (shorter, longer) -> shorter < longer })
    }

    @Test
    fun `la garantia de super se vuelve mas rapida al subir dificultad`() {
        val waits = SfCpuDifficulty.entries.map { cpuSuperCommitDelayMs(it, aiVs = true) }
        assertEquals(listOf(700L, 450L, 250L, 120L), waits)
        assertTrue(waits.zipWithNext().all { (slower, faster) -> slower > faster })
    }

    @Test
    fun `fuera de IA contra IA la super se compromete en menos de segundo y medio`() {
        val waits = SfCpuDifficulty.entries.map { cpuSuperCommitDelayMs(it, aiVs = false) }
        assertEquals(listOf(1400L, 900L, 500L, 250L), waits)
        assertTrue(waits.zipWithNext().all { (slower, faster) -> slower > faster })
    }

    // ─── REGRESIÓN: la CPU se paralizaba al llenar su medidor ────────────────────────────────
    // `SF_HURT_STATES` significa "PUEDE ser golpeado", no "está aturdido": incluye IDLE, caminar,
    // agacharse y los seis normales. La IA lo usaba como "estoy interrumpido", así que de pie daba
    // `true` casi siempre y los tres sitios que lo consultan devolvían un input VACÍO. Al llenarse
    // la barra, `buildCpuInput` corta por `maybeCpuSuperArtInput` antes que nada → la CPU se
    // quedaba tiesa para siempre (el medidor solo se vacía al lanzar la súper) y la pelea se
    // ganaba sola. Estos dos tests fijan la diferencia entre los dos conjuntos.

    @Test
    fun `estar de pie o atacando no cuenta como interrumpido`() {
        val neutros = listOf(
            SfFighterState.IDLE,
            SfFighterState.WALK_FORWARD,
            SfFighterState.WALK_BACKWARD,
            SfFighterState.CROUCH,
            SfFighterState.LIGHT_PUNCH,
            SfFighterState.HEAVY_KICK,
        )
        neutros.forEach { state ->
            assertFalse(
                cpuIsInterrupted(state, isAirborne = false, downed = false),
                "la CPU debe poder actuar en ${state.name}",
            )
            // Y siguen siendo golpeables: es justo la ambigüedad que causó el bug.
            assertTrue(state in SF_HURT_STATES, "${state.name} sigue teniendo hurtbox activa")
            assertFalse(state in SF_HITSTUN_STATES, "${state.name} no es hitstun")
        }
    }

    @Test
    fun `la CPU se considera interrumpida en hitstun mareo aire y derribo`() {
        val bloqueados = listOf(
            SfFighterState.HURT_HEAD_HEAVY,
            SfFighterState.HURT_BODY_LIGHT,
            SfFighterState.HURT_CROUCH,
            SfFighterState.STUN,
        )
        bloqueados.forEach { state ->
            assertTrue(
                cpuIsInterrupted(state, isAirborne = false, downed = false),
                "la CPU no debe actuar en ${state.name}",
            )
        }
        assertTrue(cpuIsInterrupted(SfFighterState.IDLE, isAirborne = true, downed = false))
        assertTrue(cpuIsInterrupted(SfFighterState.IDLE, isAirborne = false, downed = true))
        assertTrue(
            cpuIsInterrupted(
                SfFighterState.IDLE,
                isAirborne = false,
                downed = false,
                metamorphosing = true,
            ),
        )
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
