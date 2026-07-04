package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests del TERCER manager (Etapa 3, manager 3/6 — ver CHECKPOINT_SENIOR_refactor.md).
 * El grueso del estado son Compose mutableStateOf (holder), pero el THROTTLE del 💥 es lógica
 * pura y se testea con un reloj inyectable (sin depender del tiempo de pared).
 */
class CombatManagerTest {

    @Test
    fun initial_state_is_full_health_and_no_fx() {
        val m = CombatManager()
        assertEquals(100f, m.playerHealth, 0f)
        assertEquals(100f, m.maxPlayerHealth, 0f)
        assertFalse(m.showHealthBar)
        assertEquals(0, m.impactEffectTrigger)
        assertEquals(0, m.damagePulseTrigger)
    }

    @Test
    fun fire_impact_effect_is_throttled_within_the_window() {
        var t = 10_000L                       // reloj > throttle para que el 1er disparo pase
        val m = CombatManager(clockMs = { t })

        m.fireImpactEffect()
        assertEquals(1, m.impactEffectTrigger)

        // Segundo disparo SIN avanzar el reloj → throttled (mismo contador).
        m.fireImpactEffect()
        assertEquals(1, m.impactEffectTrigger)
    }

    @Test
    fun fire_impact_effect_fires_again_after_the_throttle_window() {
        var t = 10_000L
        val m = CombatManager(clockMs = { t })

        m.fireImpactEffect()
        assertEquals(1, m.impactEffectTrigger)

        // Justo al cumplirse la ventana → vuelve a disparar.
        t += CombatManager.IMPACT_THROTTLE_MS
        m.fireImpactEffect()
        assertEquals(2, m.impactEffectTrigger)
    }
}
