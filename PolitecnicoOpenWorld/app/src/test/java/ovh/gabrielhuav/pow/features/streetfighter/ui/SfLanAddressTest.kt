package ovh.gabrielhuav.pow.features.streetfighter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lógica PURA de validación de la IP de anfitrión LAN de Street Fighter (ver [SfLanAddress]). */
class SfLanAddressTest {

    @Test
    fun typical_private_addresses_are_valid() {
        assertTrue(SfLanAddress.isValidIpv4("192.168.1.5"))
        assertTrue(SfLanAddress.isValidIpv4("10.0.0.12"))
        assertTrue(SfLanAddress.isValidIpv4("172.16.254.1"))
    }

    @Test
    fun octet_boundaries_are_valid() {
        assertTrue(SfLanAddress.isValidIpv4("0.0.0.0"))
        assertTrue(SfLanAddress.isValidIpv4("255.255.255.255"))
    }

    @Test
    fun only_dots_is_invalid_even_with_three_dots() {
        // Regresión del defecto original: "..." tiene 3 puntos y habilitaba UNIRSE.
        assertFalse(SfLanAddress.isValidIpv4("..."))
    }

    @Test
    fun letters_are_invalid_even_with_three_dots() {
        // Regresión del defecto original: "a.b.c.d" tiene 3 puntos y habilitaba UNIRSE.
        assertFalse(SfLanAddress.isValidIpv4("a.b.c.d"))
        assertFalse(SfLanAddress.isValidIpv4("192.168.1.x"))
    }

    @Test
    fun out_of_range_octets_are_invalid() {
        assertFalse(SfLanAddress.isValidIpv4("256.1.1.1"))
        assertFalse(SfLanAddress.isValidIpv4("999.999.999.999"))
        assertFalse(SfLanAddress.isValidIpv4("1.1.1.300"))
    }

    @Test
    fun wrong_octet_count_is_invalid() {
        assertFalse(SfLanAddress.isValidIpv4(""))
        assertFalse(SfLanAddress.isValidIpv4("192.168.1"))
        assertFalse(SfLanAddress.isValidIpv4("1.2.3.4.5"))
        assertFalse(SfLanAddress.isValidIpv4("192.168.1."))
        assertFalse(SfLanAddress.isValidIpv4("192..1.5"))
    }

    @Test
    fun leading_zeros_and_whitespace_are_invalid() {
        assertFalse(SfLanAddress.isValidIpv4("192.168.01.5"))
        assertFalse(SfLanAddress.isValidIpv4(" 192.168.1.5"))
        assertFalse(SfLanAddress.isValidIpv4("192.168.1.5 "))
    }

    @Test
    fun sanitize_keeps_only_digits_and_dots() {
        assertEquals("192.168.1.5", SfLanAddress.sanitize("192.168.1.5"))
        assertEquals("192.168.1.5", SfLanAddress.sanitize("ip: 192.168.1.5 "))
        assertEquals("...", SfLanAddress.sanitize("a.b.c.d"))
    }

    @Test
    fun sanitize_truncates_to_max_length() {
        val clean = SfLanAddress.sanitize("255.255.255.255999")
        assertEquals(SfLanAddress.MAX_LENGTH, clean.length)
        assertEquals("255.255.255.255", clean)
    }

    @Test
    fun error_is_hidden_while_typing_and_for_valid_input() {
        assertFalse(SfLanAddress.shouldShowError(""))
        assertFalse(SfLanAddress.shouldShowError("192.168"))
        assertFalse(SfLanAddress.shouldShowError("192.168.1."))
        assertFalse(SfLanAddress.shouldShowError("192.168.1.5"))
    }

    @Test
    fun error_is_shown_for_complete_looking_but_invalid_input() {
        assertTrue(SfLanAddress.shouldShowError("999.999.999.999"))
        assertTrue(SfLanAddress.shouldShowError("1.2.3.4.5"))
        assertTrue(SfLanAddress.shouldShowError("192.168.01.5"))
    }
}
