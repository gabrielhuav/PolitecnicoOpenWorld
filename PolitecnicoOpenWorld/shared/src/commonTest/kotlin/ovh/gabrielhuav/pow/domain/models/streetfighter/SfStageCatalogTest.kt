package ovh.gabrielhuav.pow.domain.models.streetfighter

import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fija el comportamiento del nuevo escenario Biblioteca Nacional IPN y evita la regresión que
 * ocurrió durante su desarrollo: un escenario agregado a `SfStageCatalog.ALL_STAGES` pero
 * olvidado en `SF_CLASSIC_THEME.fullBackgrounds` (el selector real que ve el jugador) quedaba
 * invisible en el juego aunque el desbloqueo funcionara.
 */
class SfStageCatalogTest {

    @Test
    fun homeStageDePoliciaGranaderoMujerEsBibliotecaIpn() {
        assertEquals(SfStageCatalog.BIBLIOTECA_IPN, SfStageCatalog.homeStage(SfFighterId.POLICIA_GRANADERO_MUJER))
    }

    @Test
    fun unlockableMapsParaPoliciaGranaderoMujerSonLosTresDeBibliotecaIpn() {
        val archivos = SfStageCatalog.unlockableMapsForFighter(SfFighterId.POLICIA_GRANADERO_MUJER)
        assertEquals(
            setOf(
                "fondo_biblioteca_ipn_anim.webp",
                "fondo_biblioteca_ipn_noche_1_anim.webp",
                "fondo_biblioteca_ipn_noche_2_anim.webp",
            ),
            archivos,
        )
    }

    @Test
    fun cadaEscenarioDelCatalogoTieneSusTresLucesEnElSelectorDelTema() {
        val archivosDelSelector = SF_CLASSIC_THEME.fullBackgrounds.map { it.file }.toSet()
        for (stage in SfStageCatalog.ALL_STAGES) {
            for (archivo in SfStageCatalog.filesForStage(stage)) {
                assertTrue(
                    archivo in archivosDelSelector,
                    "El escenario '${stage.displayName}' tiene '$archivo' en SfStageCatalog pero falta en SF_CLASSIC_THEME.fullBackgrounds",
                )
            }
        }
    }

    @Test
    fun elCatalogoTieneDiecisieteEscenariosBase() {
        assertEquals(17, SfStageCatalog.ALL_STAGES.size)
    }
}
