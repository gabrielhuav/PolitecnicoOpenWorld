package ovh.gabrielhuav.pow.features.streetfighter.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId

/**
 * Regresión del hallazgo de QA (2026-10-01): en gama baja el renderer recortaba las hojas
 * COMPARTIDAS con el sheetScale de los atlas submuestreados (0.5) y el peleador se veía gigante.
 */
class SfSharedSheetsScaleTest {

    private val lowEndScale = 0.5f

    @Test
    fun `las hojas compartidas siempre se recortan a escala completa`() {
        SfFighterId.entries.filter { it.sharedSet != null }.forEach { id ->
            assertEquals("$id", 1f, SfSharedSheets.sheetScaleFor(id, lowEndScale), 0f)
        }
    }

    @Test
    fun `los atlas dedicados conservan el submuestreo de gama baja`() {
        SfFighterId.entries.filter { it.sharedSet == null }.forEach { id ->
            assertEquals("$id", lowEndScale, SfSharedSheets.sheetScaleFor(id, lowEndScale), 0f)
        }
    }

    @Test
    fun `en gama media y alta no cambia nada`() {
        SfFighterId.entries.forEach { id ->
            assertEquals("$id", 1f, SfSharedSheets.sheetScaleFor(id, 1f), 0f)
        }
    }
}
