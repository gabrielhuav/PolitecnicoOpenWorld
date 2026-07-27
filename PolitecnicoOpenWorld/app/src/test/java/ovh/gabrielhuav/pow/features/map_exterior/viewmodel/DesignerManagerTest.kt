package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

/**
 * Tests del PRIMER manager con sub-estado propio (Etapa 3 de CHECKPOINT_SENIOR_refactor.md).
 * Lógica pura sin Android: el manager es exactamente tan testeable como prometía el plan de
 * descomposición (PLAN_descomponer_WorldMapViewModel.md). Patrón para los managers siguientes.
 */
class DesignerManagerTest {

    private fun p(lat: Double, lon: Double) = GeoPoint(lat, lon)

    @Test
    fun overlay_and_tool_toggles_update_substate() {
        val m = DesignerManager()
        assertFalse(m.state.value.showInteriorDebugOverlay)
        m.toggleOverlay(true)
        assertTrue(m.state.value.showInteriorDebugOverlay)
        m.setTool(DebugEditTool.WALL)
        assertEquals(DebugEditTool.WALL, m.state.value.debugEditTool)
    }

    @Test
    fun commit_wall_stroke_creates_one_wall_per_consecutive_pair() {
        val m = DesignerManager()
        // 3 puntos → 2 segmentos de barda.
        m.commitStroke(DebugEditTool.WALL, listOf(p(0.0, 0.0), p(0.0, 1.0), p(1.0, 1.0)))
        assertEquals(2, m.state.value.debugEditWalls.size)
    }

    @Test
    fun commit_block_requires_at_least_three_points() {
        val m = DesignerManager()
        m.commitStroke(DebugEditTool.BLOCK, listOf(p(0.0, 0.0), p(0.0, 1.0)))
        assertEquals(0, m.state.value.debugEditBlocks.size)
        m.commitStroke(DebugEditTool.BLOCK, listOf(p(0.0, 0.0), p(0.0, 1.0), p(1.0, 1.0)))
        assertEquals(1, m.state.value.debugEditBlocks.size)
    }

    @Test
    fun commit_with_fewer_than_two_points_or_none_tool_is_ignored() {
        val m = DesignerManager()
        m.commitStroke(DebugEditTool.WALL, listOf(p(0.0, 0.0)))
        m.commitStroke(DebugEditTool.NONE, listOf(p(0.0, 0.0), p(1.0, 1.0)))
        assertEquals(DesignerEditState(), m.state.value)
    }

    @Test
    fun undo_removes_last_stroke_of_active_tool() {
        val m = DesignerManager()
        m.setTool(DebugEditTool.NAV_PED)
        m.commitStroke(DebugEditTool.NAV_PED, listOf(p(0.0, 0.0), p(0.0, 1.0)))
        m.commitStroke(DebugEditTool.NAV_PED, listOf(p(1.0, 0.0), p(1.0, 1.0)))
        m.undoLastShape()
        assertEquals(1, m.state.value.debugEditNavPed.size)
    }

    @Test
    fun clear_removes_all_edited_geometry_but_keeps_tool_and_overlay() {
        val m = DesignerManager()
        m.toggleOverlay(true)
        m.setTool(DebugEditTool.NAV_CAR)
        m.commitStroke(DebugEditTool.WALL, listOf(p(0.0, 0.0), p(0.0, 1.0)))
        m.commitStroke(DebugEditTool.NAV_CAR, listOf(p(0.0, 0.0), p(0.0, 1.0)))
        m.clearEdits()
        val s = m.state.value
        assertTrue(s.debugEditWalls.isEmpty() && s.debugEditBlocks.isEmpty() &&
            s.debugEditNavPed.isEmpty() && s.debugEditNavCar.isEmpty())
        // El clear NO apaga el modo edición (contrato del botón "Limpiar" del panel).
        assertTrue(s.showInteriorDebugOverlay)
        assertEquals(DebugEditTool.NAV_CAR, s.debugEditTool)
    }

    @Test
    fun setImported_replaces_geometry() {
        val m = DesignerManager()
        m.commitStroke(DebugEditTool.WALL, listOf(p(0.0, 0.0), p(0.0, 1.0)))
        m.setImported(emptyList(), emptyList(), listOf(listOf(p(2.0, 2.0), p(3.0, 3.0))), emptyList())
        val s = m.state.value
        assertEquals(0, s.debugEditWalls.size)   // reemplaza, no acumula
        assertEquals(1, s.debugEditNavPed.size)
    }
}
