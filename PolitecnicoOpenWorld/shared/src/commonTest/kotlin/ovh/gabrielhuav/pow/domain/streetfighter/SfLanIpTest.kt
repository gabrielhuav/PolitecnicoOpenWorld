package ovh.gabrielhuav.pow.domain.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 🌐 LA IP TECLEADA PARA UNIRSE POR LAN.
 *
 * Antes bastaba con tres puntos para habilitar UNIRSE. Estos casos fijan qué cuenta como IPv4
 * válida (criterio de éxito) y qué debe dejar el botón deshabilitado (criterio límite).
 */
class SfLanIpTest {

    // ═══════════════════════════ IPs válidas · el botón se habilita ═══════════════════════════

    @Test
    fun `acepta una IP privada tipica de red domestica`() {
        assertTrue(SfLanIp.esIpv4Valida("192.168.0.10"))
    }

    @Test
    fun `acepta los extremos 0 y 255 en cada octeto`() {
        assertTrue(SfLanIp.esIpv4Valida("0.0.0.0"))
        assertTrue(SfLanIp.esIpv4Valida("255.255.255.255"))
    }

    @Test
    fun `acepta la IP del hotspot y la del host del emulador`() {
        assertTrue(SfLanIp.esIpv4Valida("192.168.43.1"))
        assertTrue(SfLanIp.esIpv4Valida("10.0.2.2"))
    }

    // ═══════════════════════════ IPs inválidas · el botón queda deshabilitado ═══════════════════

    @Test
    fun `rechaza el campo vacio`() {
        assertFalse(SfLanIp.esIpv4Valida(""))
    }

    @Test
    fun `rechaza tres puntos con octetos vacios`() {
        // Era el caso que antes habilitaba UNIRSE: sólo se contaban los puntos.
        assertFalse(SfLanIp.esIpv4Valida("1.2.3."))
        assertFalse(SfLanIp.esIpv4Valida("..."))
        assertFalse(SfLanIp.esIpv4Valida(".1.2.3"))
    }

    @Test
    fun `rechaza un octeto mayor a 255`() {
        assertFalse(SfLanIp.esIpv4Valida("256.1.1.1"))
        assertFalse(SfLanIp.esIpv4Valida("999.1.1.1"))
    }

    @Test
    fun `rechaza mas o menos de cuatro octetos`() {
        assertFalse(SfLanIp.esIpv4Valida("1.2.3"))
        assertFalse(SfLanIp.esIpv4Valida("1.2.3.4.5"))
    }

    @Test
    fun `rechaza ceros a la izquierda`() {
        assertFalse(SfLanIp.esIpv4Valida("01.2.3.4"))
        assertFalse(SfLanIp.esIpv4Valida("192.168.000.1"))
    }

    @Test
    fun `rechaza letras espacios y signos`() {
        assertFalse(SfLanIp.esIpv4Valida("a.b.c.d"))
        assertFalse(SfLanIp.esIpv4Valida("192.168.0.1 "))
        assertFalse(SfLanIp.esIpv4Valida("+1.2.3.4"))
    }

    @Test
    fun `rechaza un octeto de mas de tres digitos`() {
        assertFalse(SfLanIp.esIpv4Valida("1.2.3.0004"))
    }

    // ═══════════════════════════ filtro mientras se escribe ═══════════════════════════════════

    @Test
    fun `el filtro deja solo digitos y puntos`() {
        assertEquals("192.168.0.10", SfLanIp.filtrarEntrada(" 192.168.0.10 "))
        assertEquals("", SfLanIp.filtrarEntrada("abc"))
        assertEquals("1.23.4", SfLanIp.filtrarEntrada("1.2-3.4"))
    }

    @Test
    fun `el filtro conserva completa la IP mas larga y corta lo que sobra`() {
        assertEquals("255.255.255.255", SfLanIp.filtrarEntrada("255.255.255.255"))
        assertEquals("255.255.255.255", SfLanIp.filtrarEntrada("255.255.255.2559"))
    }
}
