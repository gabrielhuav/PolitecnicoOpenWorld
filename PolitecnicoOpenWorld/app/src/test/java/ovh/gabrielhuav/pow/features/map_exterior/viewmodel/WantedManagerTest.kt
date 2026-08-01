package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

/**
 * Tests del CUARTO manager con sub-estado propio (Etapa 3, manager 4/6 —
 * ver CHECKPOINT_SENIOR_refactor.md). Lógica pura sin Android: fija el contrato del
 * nivel de búsqueda (subida/decaimiento), la máquina de CARJACK y los disparos de policía
 * que la fachada combine vuelca en WorldMapState. Todos los métodos con tiempo reciben `now`
 * explícito → deterministas, sin reloj de pared.
 */
class WantedManagerTest {

    private fun shot(at: Long) = PoliceShot(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0), at)

    @Test
    fun initial_substate_is_empty() {
        val m = WantedManager()
        assertEquals(0, m.state.value.wantedLevel)
        assertNull(m.state.value.carjackWarning)
        assertTrue(m.state.value.policeShots.isEmpty())
    }

    @Test
    fun raise_bumps_and_caps_at_max() {
        val m = WantedManager()
        m.raiseWantedLevel(2, now = 1000L)
        assertEquals(2, m.state.value.wantedLevel)
        m.raiseWantedLevel(2, now = 2000L)
        assertEquals(4, m.state.value.wantedLevel)
        m.raiseWantedLevel(2, now = 3000L)   // 4+2 → tope 5
        assertEquals(WantedManager.MAX_WANTED_LEVEL, m.state.value.wantedLevel)
        m.raiseWantedLevel(1, now = 4000L)   // ya en el tope → no sube (current !< MAX)
        assertEquals(5, m.state.value.wantedLevel)
    }

    @Test
    fun decay_respects_grace_and_scales_with_level() {
        val m = WantedManager()
        m.raiseWantedLevel(2, now = 0L)          // nivel 2, lastCrimeTime = 0
        m.tickWantedDecay(now = 24_000L)         // dentro de la gracia (< 25 s) → no baja
        assertEquals(2, m.state.value.wantedLevel)
        m.tickWantedDecay(now = 40_000L)         // gracia OK + paso 15 s×2 cumplido → baja a 1
        assertEquals(1, m.state.value.wantedLevel)
        m.tickWantedDecay(now = 50_000L)         // paso 15 s×1 aún no (10 s) → sigue en 1
        assertEquals(1, m.state.value.wantedLevel)
        m.tickWantedDecay(now = 56_000L)         // 16 s desde la última baja → baja a 0
        assertEquals(0, m.state.value.wantedLevel)
        m.tickWantedDecay(now = 99_000L)         // en 0 no hace nada
        assertEquals(0, m.state.value.wantedLevel)
    }

    @Test
    fun set_wanted_level_coerces_to_valid_range() {
        val m = WantedManager()
        m.setWantedLevel(9)
        assertEquals(WantedManager.MAX_WANTED_LEVEL, m.state.value.wantedLevel)
        m.setWantedLevel(-3)
        assertEquals(0, m.state.value.wantedLevel)
        m.setWantedLevel(1)
        assertEquals(1, m.state.value.wantedLevel)
    }

    @Test
    fun set_max_wanted_marks_crime_and_delays_decay() {
        val m = WantedManager()
        m.setMaxWanted(now = 1000L)
        assertEquals(5, m.state.value.wantedLevel)
        // El delito recién marcado retrasa el decaimiento (gracia de 25 s desde now=1000).
        m.tickWantedDecay(now = 1000L + 24_000L)
        assertEquals(5, m.state.value.wantedLevel)
    }

    @Test
    fun clear_wanted_resets_level_and_carjack() {
        val m = WantedManager()
        m.raiseWantedLevel(3, now = 1000L)
        m.armCarjack(now = 1000L, warning = "aviso")
        assertEquals("aviso", m.state.value.carjackWarning)
        m.clearWanted()
        assertEquals(0, m.state.value.wantedLevel)
        assertNull(m.state.value.carjackWarning)
        // El timer de carjack también se reinició: volver a armar arranca desde cero.
        assertFalse(m.armCarjack(now = 5000L, warning = "aviso"))
        assertEquals("aviso", m.state.value.carjackWarning)
    }

    @Test
    fun carjack_arms_then_triggers_after_threshold() {
        val m = WantedManager()
        assertFalse(m.armCarjack(now = 1000L, warning = "aviso"))   // arranca timer, muestra aviso
        assertEquals("aviso", m.state.value.carjackWarning)
        assertFalse(m.armCarjack(now = 1000L + WantedManager.CARJACK_MS - 1, warning = "aviso"))
        assertTrue(m.armCarjack(now = 1000L + WantedManager.CARJACK_MS, warning = "aviso"))
        assertNull(m.state.value.carjackWarning)   // al disparar, se limpia el aviso
    }

    @Test
    fun clear_carjack_removes_warning() {
        val m = WantedManager()
        m.armCarjack(now = 1000L, warning = "aviso")
        m.clearCarjack()
        assertNull(m.state.value.carjackWarning)
    }

    @Test
    fun add_police_shots_appends() {
        val m = WantedManager()
        m.addPoliceShots(listOf(shot(100L)))
        m.addPoliceShots(listOf(shot(200L), shot(300L)))
        assertEquals(3, m.state.value.policeShots.size)
        m.addPoliceShots(emptyList())   // no-op
        assertEquals(3, m.state.value.policeShots.size)
    }

    @Test
    fun merge_and_prune_keeps_fresh_and_drops_old() {
        val m = WantedManager()
        m.addPoliceShots(listOf(shot(100L)))
        // now=500, ttl=450: el de at=100 sigue vivo (400≤450); entra el nuevo at=500.
        m.mergeAndPrunePoliceShots(listOf(shot(500L)), now = 500L, ttlMs = 450L)
        assertEquals(2, m.state.value.policeShots.size)
        // now=600: at=100 caduca (500>450), at=500 sobrevive (100≤450).
        m.mergeAndPrunePoliceShots(emptyList(), now = 600L, ttlMs = 450L)
        assertEquals(listOf(500L), m.state.value.policeShots.map { it.at })
    }
}
