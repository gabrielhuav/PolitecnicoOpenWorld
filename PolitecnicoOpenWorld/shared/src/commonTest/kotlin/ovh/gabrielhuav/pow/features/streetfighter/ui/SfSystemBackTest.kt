package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SfSystemBackTest {

    @Test
    fun `atras en combate abre el dialogo de salida`() {
        assertEquals(
            SfSystemBack.ABRIR_DIALOGO,
            sfSystemBackAction(inCharacterSelect = false, showExitDialog = false),
        )
    }

    @Test
    fun `atras con el dialogo abierto lo cierra`() {
        assertEquals(
            SfSystemBack.CERRAR_DIALOGO,
            sfSystemBackAction(inCharacterSelect = false, showExitDialog = true),
        )
    }

    @Test
    fun `atras en el selector conserva el comportamiento del sistema`() {
        assertEquals(
            SfSystemBack.SISTEMA,
            sfSystemBackAction(inCharacterSelect = true, showExitDialog = false),
        )
    }

    @Test
    fun `cambiar personaje solo se ofrece sin conexion`() {
        assertTrue(sfCanChangeCharacterFromExit(SfOnlineStatus.OFF))
        SfOnlineStatus.entries.filter { it != SfOnlineStatus.OFF }.forEach {
            assertFalse(sfCanChangeCharacterFromExit(it))
        }
    }
}