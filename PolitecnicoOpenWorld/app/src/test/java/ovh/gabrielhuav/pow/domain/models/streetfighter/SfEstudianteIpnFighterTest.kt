package ovh.gabrielhuav.pow.domain.models.streetfighter

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El Estudiante IPN es un peleador COMPARTIDO: reutiliza los sprites del NPC de interiores (Ipn3). */
class SfEstudianteIpnFighterTest {

    private val assetsRoot: File = listOf(
        File("src/main/assets"),
        File("app/src/main/assets"),
    ).firstOrNull(File::isDirectory) ?: error("No se encontró app/src/main/assets")

    private val set: SfSharedSet =
        SfFighterId.ESTUDIANTE_IPN.sharedSet ?: error("ESTUDIANTE_IPN debe ser un peleador compartido")

    @Test
    fun `el estudiante IPN usa la plantilla compartida y mira a la derecha`() {
        val id = SfFighterId.ESTUDIANTE_IPN
        assertTrue(id.isAlpha)
        assertEquals("STREETFIGHTER/DATA/sf_template.json", id.jsonAsset)
        assertTrue("Falta la plantilla", File(assetsRoot, id.jsonAsset).isFile)
        assertFalse("Los sprites del estudiante IPN ya miran a la derecha", set.flip)
    }

    @Test
    fun `el estudiante IPN tiene cuadros en todas las animaciones fuente`() {
        listOf("Idle", "Walk", "Run", "Special").forEach { action ->
            val dir = File(assetsRoot, "${set.basePath}${set.folder}$action")
            val frames = dir.listFiles { f -> f.name.startsWith(set.prefix) && f.extension == "webp" }
            assertTrue("Faltan cuadros en ${dir.path}", !frames.isNullOrEmpty())
        }
    }

    @Test
    fun `el estudiante IPN pelea en el escenario de ESCOM`() {
        assertEquals(SfStageCatalog.ESCOM, SfStageCatalog.homeStage(SfFighterId.ESTUDIANTE_IPN))
    }

    @Test
    fun `el estudiante IPN es solo de desarrollo y no aparece con candado en el arcade`() {
        assertTrue(SfFighterId.ESTUDIANTE_IPN in SfArcadeLadder.DEV_ONLY_FIGHTERS)
        assertFalse(SfFighterId.ESTUDIANTE_IPN in SfArcadeLadder.ALL_PARTICIPANTS)
    }
}
