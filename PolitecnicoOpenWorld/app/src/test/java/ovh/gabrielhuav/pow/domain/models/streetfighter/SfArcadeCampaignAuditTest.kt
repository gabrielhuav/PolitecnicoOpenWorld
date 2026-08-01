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
        // 🆕 (2026-07-25) Rebalance: la IA del arcade juega un escalón POR ENCIMA de la etiqueta y
        // sube con el avance (peleas 1-4 +1, 5-9 +1, 10-12 +2, jefes +2, FINAL +3).
        val easy = SfArcadeLadder.build(SfFighterId.ESCOMBOY, SfCpuDifficulty.BASICA, Random(1))
        assertEquals(SfCpuDifficulty.NORMAL, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.BASICA, easy[0]))
        assertEquals(SfCpuDifficulty.AVANZADA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.BASICA, easy[12]))
        assertEquals(SfCpuDifficulty.PESADILLA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.BASICA, easy[14]))

        val medium = SfArcadeLadder.build(SfFighterId.ESCOMGIRL, SfCpuDifficulty.NORMAL, Random(2))
        assertEquals(SfCpuDifficulty.PESADILLA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.NORMAL, medium[12]))
        assertEquals(SfCpuDifficulty.PESADILLA, SfArcadeLadder.difficultyForStep(SfCpuDifficulty.NORMAL, medium[14]))

        assertEquals(0.35f, SfArcadeLadder.intensityForStep(1, SfArcadeLadder.TOTAL_FIGHTS), FLOAT_TOLERANCE)
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
        // 🆕 (2026-07-25) Orden invertido de jefes finales: La Presidenta (14) y YOALLI FINAL (15).
        assertEquals(SfFighterId.LA_PRESIDENTA, ladder[ladder.size - 2].rival)
        assertEquals(SfFighterId.YOALLI_EHECATL, ladder.last().rival)
        assertTrue(ladder.last().isFinal)
        assertFalse(ladder.any { it.rival == player })
        assertEquals(ladder.size, ladder.map { it.rival }.distinct().size)

        ladder.forEach { step ->
            val fighter = step.rival
            assertAsset(fighter.jsonAsset, "datos de $fighter")
            fighter.sharedSet ?: assertAsset(fighter.spriteAsset, "sprites de $fighter")
            assertAsset("STREETFIGHTER/IMAGES/${step.mapFile}", "mapa de $fighter en $difficulty")
            val audioFiles = getExpectedAudioFiles(fighter)
            audioFiles.forEach { audioFile ->
                assertAsset(
                    "STREETFIGHTER/SOUNDS/$audioFile",
                    "audio especial de $fighter: $audioFile",
                )
            }
        }
    }

    private fun getExpectedAudioFiles(fighter: SfFighterId): List<String> {
        return when (fighter) {
            SfFighterId.PRANKEDY -> listOf(
                "special_prankedy_attack_1.ogg", "special_prankedy_attack_2.ogg",
                "special_prankedy_attack_3.ogg", "special_prankedy_attack_4.ogg",
                "special_prankedy_hurt_1.ogg", "special_prankedy_hurt_2.ogg",
                "special_prankedy_hurt_3.ogg", "special_prankedy_hurt_4.ogg",
                "special_prankedy_power.ogg", "special_prankedy_win.ogg",
                "special_prankedy_loss.ogg", "special_prankedy_lowhp.ogg"
            )
            SfFighterId.SENOR_TIENDA -> listOf(
                "special_senor_tienda_attack_1.ogg", "special_senor_tienda_attack_2.ogg",
                "special_senor_tienda_hurt_1.ogg", "special_senor_tienda_hurt_2.ogg",
                "special_senor_tienda_win.ogg"
            )
            SfFighterId.PAPARAZZI_1 -> listOf(
                "special_papz1_hurt_1.ogg", "special_papz1_hurt_2.ogg", "special_papz1_hurt_3.ogg",
                "special_paparazzi_5.ogg"
            )
            SfFighterId.REY_GRUPERO -> listOf(
                "special_rey_grupero.ogg", "special_rey_grupero_attack.ogg",
                "special_rey_grupero_hurt.ogg", "special_rey_grupero_power.ogg"
            )
            SfFighterId.PAPARAZZI_5 -> listOf(
                "special_paparazzi_5_attack.ogg", "special_paparazzi_5_hurt.ogg"
            )
            SfFighterId.LAZARO -> emptyList()
            SfFighterId.ESCOMBOY -> listOf(
                "special_escomboy_attack.ogg", "special_power_electricity.ogg"
            )
            SfFighterId.ESCOMGIRL -> listOf(
                "special_escomgirl_attack.ogg", "special_escomgirl_hurt.ogg",
                "special_power_electricity.ogg", "special_escomgirl_loss.ogg"
            )
            SfFighterId.ROBOT -> listOf(
                "special_robot_attack.ogg", "special_robot_hurt.ogg", "special_robot_win.ogg"
            )
            SfFighterId.YOALLI_EHECATL -> listOf(
                "special_yoalli_ehecatl.ogg", "special_yoalli_ehecatl_attack_1.ogg",
                "special_yoalli_ehecatl_attack_2.ogg", "special_yoalli_ehecatl_hurt_1.ogg",
                "special_yoalli_ehecatl_hurt_2.ogg"
            )
            SfFighterId.CHARRO_NEGRO -> listOf(
                "special_charro_attack_1.ogg", "special_charro_attack_2.ogg",
                "special_charro_attack_3.ogg", "special_charro_hurt_1.ogg",
                "special_charro_hurt_2.ogg", "special_charro_hurt_3.ogg",
                "special_charro_negro.ogg"
            )
            SfFighterId.LA_LLORONA -> listOf(
                "special_llorona_attack.ogg", "special_llorona_hurt.ogg", "special_llorona_power.ogg"
            )
            SfFighterId.LA_TZITZIMIME -> listOf(
                "special_la_tzitzimime_attack.ogg", "special_la_tzitzimime_hurt.ogg",
                "special_la_tzitzimime_power.ogg", "special_la_tzitzimime_win.ogg"
            )
            SfFighterId.LA_PRESIDENTA -> listOf(
                "special_la_presidenta_attack.ogg", "special_la_presidenta_hurt.ogg",
                "special_la_presidenta_power.ogg", "special_la_presidenta_win.ogg"
            )
            SfFighterId.POLICIA_CDMX -> listOf(
                "special_pol_m_attack_1.ogg", "special_pol_m_attack_2.ogg",
                "special_pol_m_hurt.ogg", "special_policia_cdmx_mujer_win.ogg"
            )
            SfFighterId.POLICIA_CDMX_HOMBRE -> listOf(
                "special_pol_h_intro.ogg", "special_pol_h_attack.ogg",
                "special_pol_h_win.ogg", "special_policia_cdmx_hombre_power.ogg"
            )
            SfFighterId.PARAMEDICO_CRUZ_ROJA -> listOf(
                "special_paramedico_cruz_roja_win.ogg", "special_power_electricity.ogg"
            )
            SfFighterId.POLICIA_GRANADERO_HOMBRE -> listOf(
                "special_pol_h_intro.ogg", "special_pol_h_attack.ogg", "special_granadero_win.ogg"
            )
            SfFighterId.POLICIA_GRANADERO_MUJER -> listOf(
                "special_pol_m_attack_1.ogg", "special_pol_m_attack_2.ogg",
                "special_pol_m_hurt.ogg", "special_granadero_win.ogg"
            )
            SfFighterId.GRANADERO -> listOf(
                "special_pol_h_intro.ogg", "special_pol_h_attack.ogg", "special_granadero_win.ogg"
            )
            SfFighterId.PARAMEDICO -> emptyList()
        }.map { it.removeSuffix(".ogg") + ".m4a" }
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
