package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del SEXTO manager con sub-estado propio (Etapa 3, manager 6/6 · PARTE A —
 * ver CHECKPOINT_SENIOR_refactor.md). Lógica pura sin Android: fija el contrato del REGISTRO
 * de misiones (showMissionLog + completedMissions) que la fachada combine vuelca en WorldMapState.
 */
class CampaignManagerTest {

    @Test
    fun initial_substate_is_empty() {
        val m = CampaignManager()
        assertFalse(m.state.value.showMissionLog)
        assertTrue(m.state.value.completedMissions.isEmpty())
    }

    @Test
    fun toggle_mission_log() {
        val m = CampaignManager()
        m.setShowMissionLog(true)
        assertTrue(m.state.value.showMissionLog)
        m.setShowMissionLog(false)
        assertFalse(m.state.value.showMissionLog)
    }

    @Test
    fun mark_completed_is_idempotent() {
        val m = CampaignManager()
        m.markCompleted("mission_1")
        m.markCompleted("mission_1")   // idempotente: no duplica
        assertEquals(listOf("mission_1"), m.state.value.completedMissions)
        m.markCompleted("mission_2")
        assertEquals(listOf("mission_1", "mission_2"), m.state.value.completedMissions)
    }

    @Test
    fun is_completed_reflects_state() {
        val m = CampaignManager()
        assertFalse(m.isCompleted("mission_1"))
        m.markCompleted("mission_1")
        assertTrue(m.isCompleted("mission_1"))
        assertFalse(m.isCompleted("mission_2"))
    }

    @Test
    fun set_completed_missions_replaces_the_set() {
        val m = CampaignManager()
        m.markCompleted("mission_1")
        // Restaurar un guardado REEMPLAZA el conjunto (no acumula sobre lo previo).
        m.setCompletedMissions(listOf("mission_2", "mission_3"))
        assertEquals(listOf("mission_2", "mission_3"), m.state.value.completedMissions)
        assertFalse(m.isCompleted("mission_1"))
    }
}
