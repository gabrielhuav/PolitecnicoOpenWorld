package ovh.gabrielhuav.pow.domain.models.campaign

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.campaign.mission1.Mission1

/**
 * Tests de lógica PURA (sin Android) para el catálogo de misiones de la campaña.
 *
 * Los `titleRes`/`descriptionRes` son ints de `R` (constantes generadas), así que el catálogo se
 * construye y consulta sin tocar el framework. Estos tests fijan la API (`byId`/`first`) y BLINDAN
 * varias decisiones que fueron bugfixes reales (radios de llegada R9) para que no regresen.
 */
class MissionCatalogTest {

    @Test
    fun byId_returns_matching_objective() {
        assertSame(MissionCatalog.IR_ENCB, MissionCatalog.byId("ir_encb"))
        assertEquals("escoltar_prankedy", MissionCatalog.byId("escoltar_prankedy")?.id)
    }

    @Test
    fun byId_null_or_unknown_returns_null() {
        assertNull(MissionCatalog.byId(null))
        assertNull(MissionCatalog.byId("no_existe"))
    }

    @Test
    fun first_objective_is_ir_encb() {
        assertSame(Mission1.IR_ENCB, MissionCatalog.first)
        assertEquals("ir_encb", MissionCatalog.first.id)
    }

    @Test
    fun mission1_has_four_objectives_in_order() {
        val ids = Mission1.objectives.map { it.id }
        assertEquals(
            listOf("ir_encb", "escoltar_prankedy", "ingresar_escom", "buscar_pistas_escom"),
            ids
        )
    }

    @Test
    fun escort_arrive_radius_locked_at_25m() {
        // R9: 12 m era demasiado estricto (la misión casi nunca se marcaba cumplida) → 25 m.
        assertEquals(25.0, MissionCatalog.ESCOLTAR_PRANKEDY.arriveRadiusMeters, 0.0)
    }

    @Test
    fun arrival_only_objectives_have_zero_radius() {
        // INGRESAR/BUSCAR no se cumplen por cercanía (radio 0): los marca otra lógica.
        assertEquals(0.0, MissionCatalog.INGRESAR_ESCOM.arriveRadiusMeters, 0.0)
        assertEquals(0.0, MissionCatalog.BUSCAR_PISTAS_ESCOM.arriveRadiusMeters, 0.0)
    }

    @Test
    fun ir_encb_uses_default_arrive_radius() {
        // IR_ENCB no fija radio → usa el default del modelo (60 m).
        assertEquals(60.0, MissionCatalog.IR_ENCB.arriveRadiusMeters, 0.0)
    }

    @Test
    fun escom_door_constants_are_shared_by_objectives() {
        assertEquals(19.50490, MissionCatalog.ESCOM_DOOR_LAT, 0.0)
        assertEquals(-99.14674, MissionCatalog.ESCOM_DOOR_LON, 0.0)
        assertEquals(MissionCatalog.ESCOM_DOOR_LAT, MissionCatalog.ESCOLTAR_PRANKEDY.targetLat, 0.0)
        assertEquals(MissionCatalog.ESCOM_DOOR_LON, MissionCatalog.ESCOLTAR_PRANKEDY.targetLon, 0.0)
    }
}
