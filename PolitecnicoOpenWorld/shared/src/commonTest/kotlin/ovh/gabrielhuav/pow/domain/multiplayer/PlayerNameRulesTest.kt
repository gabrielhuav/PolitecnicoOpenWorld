package ovh.gabrielhuav.pow.domain.multiplayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reglas del nombre de jugador de Multijugador (ver [PlayerNameRules]).
 * Lógica pura: corre en la JVM con `:shared:testAndroidHostTest`.
 */
class PlayerNameRulesTest {

    private val respaldo = { "Jugador_1234" }

    // ── sanitizeInput: lo que se ve en el campo mientras se escribe ─────────────────────────

    @Test
    fun `un nombre normal se conserva tal cual`() {
        assertEquals("Diego Ray", PlayerNameRules.sanitizeInput("Diego Ray"))
    }

    @Test
    fun `se corta exactamente en 16 caracteres`() {
        val largo = "NoTieneLimiteNoTieneLimite"
        val limpio = PlayerNameRules.sanitizeInput(largo)
        assertEquals(PlayerNameRules.MAX_LENGTH, limpio.length)
        assertEquals("NoTieneLimiteNoT", limpio)
    }

    @Test
    fun `16 caracteres exactos no se recortan`() {
        assertEquals("YaEstaLimitadoYa", PlayerNameRules.sanitizeInput("YaEstaLimitadoYa"))
    }

    @Test
    fun `texto pegado con saltos de linea y tabuladores queda en una sola linea`() {
        assertEquals("Ana Luisa Perez", PlayerNameRules.sanitizeInput("Ana\nLuisa\tPerez"))
    }

    @Test
    fun `no se permiten espacios al inicio ni espacios dobles`() {
        assertEquals("Ana Luisa", PlayerNameRules.sanitizeInput("   Ana    Luisa"))
    }

    @Test
    fun `el espacio final se conserva para seguir escribiendo`() {
        assertEquals("Ana ", PlayerNameRules.sanitizeInput("Ana "))
    }

    @Test
    fun `se descartan caracteres de control invisibles`() {
        assertEquals("AnaLuisa", PlayerNameRules.sanitizeInput("Ana\u0000Luisa\u0007"))
    }

    // ── validate: cuándo se habilita el botón Conectar ──────────────────────────────────────

    @Test
    fun `vacio es valido porque se usa el nombre de respaldo`() {
        assertNull(PlayerNameRules.validate(""))
        assertNull(PlayerNameRules.validate("   "))
    }

    @Test
    fun `letras con acentos - numeros y simbolos permitidos son validos`() {
        assertNull(PlayerNameRules.validate("Peña_07"))
        assertNull(PlayerNameRules.validate("José-Luis.ESCOM"))
        assertNull(PlayerNameRules.validate("Ana 3"))
    }

    @Test
    fun `tres caracteres es el minimo valido`() {
        assertNull(PlayerNameRules.validate("Leo"))
    }

    @Test
    fun `menos de tres caracteres es demasiado corto`() {
        assertEquals(PlayerNameRules.Problem.TOO_SHORT, PlayerNameRules.validate("Al"))
        assertEquals(PlayerNameRules.Problem.TOO_SHORT, PlayerNameRules.validate(" A "))
    }

    @Test
    fun `simbolos y emojis no permitidos se rechazan`() {
        assertEquals(PlayerNameRules.Problem.INVALID_CHARS, PlayerNameRules.validate("Ana<script>"))
        assertEquals(PlayerNameRules.Problem.INVALID_CHARS, PlayerNameRules.validate("Hola!!"))
        assertEquals(PlayerNameRules.Problem.INVALID_CHARS, PlayerNameRules.validate("Ana 😀"))
    }

    // ── finalName: lo que se guarda y se manda al servidor ──────────────────────────────────

    @Test
    fun `el nombre final no lleva espacios al final`() {
        assertEquals("Ana", PlayerNameRules.finalName("Ana ", respaldo))
    }

    @Test
    fun `nombre vacio usa el respaldo`() {
        assertEquals("Jugador_1234", PlayerNameRules.finalName("", respaldo))
        assertEquals("Jugador_1234", PlayerNameRules.finalName("  \n ", respaldo))
    }

    @Test
    fun `un nombre guardado antiguo de mas de 16 caracteres se recorta al conectar`() {
        assertEquals("NoTieneLimiteNoT", PlayerNameRules.finalName("NoTieneLimiteNoTieneLimite", respaldo))
    }

    @Test
    fun `el nombre de respaldo cumple las reglas`() {
        assertNull(PlayerNameRules.validate(respaldo()))
    }

    // ── forDisplay: nombres que llegan de OTROS jugadores ───────────────────────────────────

    @Test
    fun `un nombre remoto corto se muestra igual`() {
        assertEquals("Julio", PlayerNameRules.forDisplay("Julio", "Player_ab12"))
    }

    @Test
    fun `un nombre remoto largo se corta a 16 con puntos suspensivos`() {
        val mostrado = PlayerNameRules.forDisplay("NoTieneLimiteNoTieneLimite", "Player_ab12")
        assertEquals("NoTieneLimiteNo…", mostrado)
        assertEquals(PlayerNameRules.MAX_LENGTH, mostrado.length)
    }

    @Test
    fun `un nombre remoto vacio o nulo usa el respaldo`() {
        assertEquals("Player_ab12", PlayerNameRules.forDisplay(null, "Player_ab12"))
        assertEquals("Player_ab12", PlayerNameRules.forDisplay(" \n ", "Player_ab12"))
    }

    @Test
    fun `un nombre remoto con saltos de linea se muestra en una sola linea`() {
        assertEquals("Ana Luisa", PlayerNameRules.forDisplay("Ana\n\nLuisa", "Player_ab12"))
    }
}
