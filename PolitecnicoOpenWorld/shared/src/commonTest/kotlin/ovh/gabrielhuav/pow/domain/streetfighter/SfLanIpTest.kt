package ovh.gabrielhuav.pow.domain.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SfLanIpTest {
    @Test
    fun aceptaDireccionesDomesticasHotspotYEmulador() {
        listOf("192.168.1.5", "192.168.43.1", "10.0.2.2", "172.16.254.1").forEach {
            assertTrue(SfLanIp.esIpv4Valida(it), it)
        }
    }

    @Test
    fun aceptaLosLimitesEnCadaPosicion() {
        assertTrue(SfLanIp.esIpv4Valida("0.0.0.0"))
        assertTrue(SfLanIp.esIpv4Valida("255.255.255.255"))
        repeat(4) { index ->
            listOf("0", "255").forEach { limit ->
                val octets = mutableListOf("192", "168", "1", "5")
                octets[index] = limit
                assertTrue(SfLanIp.esIpv4Valida(octets.joinToString(".")))
            }
        }
    }

    @Test
    fun rechazaOctetosFueraDeRangoEnCadaPosicion() {
        repeat(4) { index ->
            listOf("256", "999").forEach { invalid ->
                val octets = mutableListOf("192", "168", "1", "5")
                octets[index] = invalid
                assertFalse(SfLanIp.esIpv4Valida(octets.joinToString(".")))
            }
        }
    }

    @Test
    fun rechazaCantidadesIncorrectasYOctetosVacios() {
        listOf("", "...", ".1.2.3", "192..1.5", "192.168.1.", "1.2.3", "1.2.3.4.5").forEach {
            assertFalse(SfLanIp.esIpv4Valida(it), it)
        }
    }

    @Test
    fun rechazaCerosInicialesEnCadaPosicion() {
        repeat(4) { index ->
            listOf("01", "000").forEach { invalid ->
                val octets = mutableListOf("192", "168", "1", "5")
                octets[index] = invalid
                assertFalse(SfLanIp.esIpv4Valida(octets.joinToString(".")))
            }
        }
    }

    @Test
    fun rechazaTextoSinNormalizarYDigitosNoAscii() {
        listOf("a.b.c.d", "192.168.1.x", " 192.168.1.5", "192.168.1.5 ", "+1.2.3.4",
            "-1.2.3.4", "１.2.3.4", "١.2.3.4").forEach {
            assertFalse(SfLanIp.esIpv4Valida(it), it)
        }
    }

    @Test
    fun rechazaOctetosDemasiadoLargosSinLanzarExcepciones() {
        assertFalse(SfLanIp.esIpv4Valida("1.2.3.0004"))
        assertFalse(SfLanIp.esIpv4Valida("9".repeat(1000) + ".1.1.1"))
    }

    @Test
    fun filtraSoloDigitosAsciiYPuntos() {
        assertEquals("192.168.1.5", SfLanIp.filtrarEntrada("ip: 192.168.1.5 "))
        assertEquals("...", SfLanIp.filtrarEntrada("a.b.c.d"))
        assertEquals("1.23.4", SfLanIp.filtrarEntrada("1.2-3.4"))
        assertEquals("", SfLanIp.filtrarEntrada("abc１２٣"))
    }

    @Test
    fun limitaLaEntradaSinRecortarLaIpv4MasLarga() {
        val longest = "255.255.255.255"
        assertEquals(SfLanIp.LONGITUD_MAXIMA, longest.length)
        assertEquals(longest, SfLanIp.filtrarEntrada(longest))
        assertEquals(longest, SfLanIp.filtrarEntrada(longest + "999"))
    }

    @Test
    fun filtrarUnaSegundaVezConservaLaEntrada() {
        listOf("", "192.168.1.", "ip: 10.0.2.2", "255.255.255.25599").forEach {
            val filtered = SfLanIp.filtrarEntrada(it)
            assertEquals(filtered, SfLanIp.filtrarEntrada(filtered))
        }
    }

    @Test
    fun ocultaElErrorMientrasLaEntradaEstaIncompletaOEsValida() {
        listOf("", "192", "192.168", "192.168.1", "192.168.1.", "...", "192.168.1.5",
            "0.0.0.0", "255.255.255.255").forEach {
            assertFalse(SfLanIp.debeMostrarError(it), it)
        }
    }

    @Test
    fun muestraErrorCuandoLaEntradaPareceCompletaPeroEsInvalida() {
        listOf("999.999.999.999", "192.168.01.5", "1.2.3.4.5", ".1.2.3", "192..1.5").forEach {
            assertTrue(SfLanIp.debeMostrarError(it), it)
        }
    }
}
