package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SfLaPlanchadaTest {
    @Test
    fun comparteElPackCompletoDeLaLloronaSinCambiarElAudioDeOtrosPeleadores() {
        val pack = sfLegendVoicePacks.getValue(SfFighterId.LA_PLANCHADA)
        assertSame(sfLegendVoicePacks.getValue(SfFighterId.LA_LLORONA), pack)
        assertEquals(listOf("special_llorona_attack"), pack.attack.map { it.file })
        assertEquals(listOf("special_llorona_hurt"), pack.hurt.map { it.file })
        assertEquals(listOf("special_llorona_power"), pack.power.map { it.file })
        assertEquals(listOf("special_llorona_win"), pack.win.map { it.file })
        assertEquals("special_llorona_power", sfSpecialSfxKey(SfFighterId.LA_PLANCHADA))
        assertEquals("special_charro_negro", sfSpecialSfxKey(SfFighterId.CHARRO_NEGRO))
    }

    @Test
    fun apareceEntreLasLeyendasAntesDeLosTresJefesYSeDesbloqueaJugando() {
        assertFalse(SfFighterId.LA_PLANCHADA in SfArcadeLadder.STARTERS)
        assertFalse("LA_PLANCHADA" in DefaultStreetFighterEnvironment.arcade.unlockedFighters())
        assertTrue(SfFighterId.LA_PLANCHADA in SfArcadeLadder.ALL_PARTICIPANTS)
        val legends = setOf(SfFighterId.CHARRO_NEGRO, SfFighterId.LA_LLORONA, SfFighterId.LA_PLANCHADA)
        SfCpuDifficulty.entries.forEach { difficulty ->
            repeat(30) { seed ->
                val ladder = SfArcadeLadder.build(SfFighterId.ESCOMBOY, difficulty, Random(seed))
                assertEquals(16, ladder.size)
                assertEquals(legends, ladder.subList(10, 13).map { it.rival }.toSet())
                assertEquals(
                    listOf(SfFighterId.LA_TZITZIMIME, SfFighterId.LA_PRESIDENTA, SfFighterId.YOALLI_EHECATL),
                    ladder.takeLast(3).map { it.rival },
                )
                assertEquals(3, ladder.count { it.isBoss })
                assertEquals(listOf(ladder.last()), ladder.filter { it.isFinal })
                val step = ladder.single { it.rival == SfFighterId.LA_PLANCHADA }
                assertEquals(SfStageCatalog.mapForRival(SfFighterId.LA_PLANCHADA, difficulty), step.mapFile)
                assertEquals(SfStageCatalog.ESM, SfStageCatalog.stageForFile(requireNotNull(step.mapFile)))
            }
        }
    }

    @Test
    fun elPaqueteDeEscenariosDeLaPlanchadaIncluyeLasTresLucesDeEsm() {
        assertEquals(
            setOf("fondo_esm_anim.webp", "fondo_esm_noche_1_anim.webp", "fondo_esm_noche_2_anim.webp"),
            SfStageCatalog.unlockableMapsForFighter(SfFighterId.LA_PLANCHADA),
        )
    }
}
