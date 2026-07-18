package ovh.gabrielhuav.pow.domain.models.streetfighter

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auditoría reproducible de la campaña completa para cada personaje inicial y dificultad. */
class SfArcadeCampaignAuditTest {

    private val assetsRoot: File = listOf(
        File("src/main/assets"),
        File("app/src/main/assets"),
    ).firstOrNull(File::isDirectory) ?: error("No se encontró app/src/main/assets")

    @Test
    fun `las campañas completas tienen rivales mapas arte datos y audio`() {
        SfArcadeLadder.STARTERS.forEach { player ->
            SfCpuDifficulty.entries.forEach { difficulty ->
                repeat(RANDOM_ORDERS_TO_AUDIT) { seed ->
                    auditCampaign(player, difficulty, seed)
                }
            }
        }
    }

    @Test
    fun `la dificultad y la intensidad progresan hasta la final`() {
        val easy = SfArcadeLadder.build(SfFighterId.ESCOMBOY, SfCpuDifficulty.BASICA, Random(1))
        assertEquals(SfCpuDifficulty.BASICA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.BASICA, easy[0]))
        assertEquals(SfCpuDifficulty.NORMAL, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.BASICA, easy[12]))
        assertEquals(SfCpuDifficulty.AVANZADA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.BASICA, easy[14]))

        val medium = SfArcadeLadder.build(SfFighterId.ESCOMGIRL, SfCpuDifficulty.NORMAL, Random(2))
        assertEquals(SfCpuDifficulty.AVANZADA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.NORMAL, medium[12]))
        assertEquals(SfCpuDifficulty.PESADILLA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.NORMAL, medium[14]))

        assertEquals(0.20f, SfArcadeLadder.intensityForStep(1, SfArcadeLadder.TOTAL_FIGHTS), FLOAT_TOLERANCE)
        assertEquals(1f, SfArcadeLadder.intensityForStep(SfArcadeLadder.TOTAL_FIGHTS, SfArcadeLadder.TOTAL_FIGHTS), FLOAT_TOLERANCE)
    }

    @Test
    fun `el catalogo resuelve webp y migra mapas png guardados`() {
        val day = "fondo_escom_anim.webp"
        val night = "fondo_escom_noche_1_anim.webp"
        val apocalypse = "fondo_escom_noche_2_anim.webp"

        assertEquals(SfStageCatalog.ESCOM, SfStageCatalog.stageForFile(day))
        assertEquals(SfStageCatalog.ESCOM, SfStageCatalog.stageForFile(night))
        assertEquals(SfStageCatalog.ESCOM, SfStageCatalog.stageForFile(apocalypse))
        assertEquals(day, SfStageCatalog.normalizeFile("fondo_escom_anim.png"))
        assertEquals(night, SfStageCatalog.normalizeFile("fondo_escom_noche_1_anim.png"))
        assertTrue(SfStageCatalog.allBackgroundFiles().all { it.endsWith(".webp") })
    }

    private fun auditCampaign(player: SfFighterId, difficulty: SfCpuDifficulty, seed: Int) {
        val ladder = SfArcadeLadder.build(player, difficulty, Random(seed))
        assertEquals(SfArcadeLadder.TOTAL_FIGHTS, ladder.size)
        assertEquals((1..SfArcadeLadder.TOTAL_FIGHTS).toList(), ladder.map { it.index })
        assertEquals(SfFighterId.PARAMEDICO_CRUZ_ROJA, ladder.first().rival)
        assertEquals(SfFighterId.LA_PRESIDENTA, ladder.last().rival)
        assertFalse(ladder.any { it.rival == player })
        assertEquals(ladder.size, ladder.map { it.rival }.distinct().size)

        ladder.forEach { step ->
            val fighter = step.rival
            assertAsset(fighter.jsonAsset, "datos de $fighter")
            fighter.sharedSet ?: assertAsset(fighter.spriteAsset, "sprites de $fighter")
            assertAsset("STREETFIGHTER/IMAGES/${step.mapFile}", "mapa de $fighter en $difficulty")
            assertAsset(
                "STREETFIGHTER/SOUNDS/special_${fighter.name.lowercase()}.ogg",
                "audio especial de $fighter",
            )
        }
    }

    private fun assertAsset(relativePath: String, label: String) {
        val asset = File(assetsRoot, relativePath)
        assertTrue("Falta $label: ${asset.path}", asset.isFile)
        assertTrue("Está vacío $label: ${asset.path}", asset.length() > MIN_ASSET_BYTES)
    }

    private companion object {
        const val RANDOM_ORDERS_TO_AUDIT = 50
        const val MIN_ASSET_BYTES = 128L
        const val FLOAT_TOLERANCE = 0.0001f
    }
}
