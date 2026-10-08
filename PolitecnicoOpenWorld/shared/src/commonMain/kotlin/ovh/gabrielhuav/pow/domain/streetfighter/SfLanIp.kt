package ovh.gabrielhuav.pow.domain.streetfighter

/**
 * 🌐 VALIDACIÓN DE LA IP TECLEADA para "unirse por LAN" en "Titulación por Combate".
 *
 * Antes el botón UNIRSE se habilitaba con sólo contar tres puntos, así que `1.2.3.`, `...` o
 * `999.1.1.1` intentaban conectar y terminaban en el overlay de error genérico. Aquí vive la
 * regla como función pura: se prueba sin red y la pantalla sólo la consulta.
 */
object SfLanIp {

    /** "255.255.255.255" es la IPv4 más larga posible. */
    const val LONGITUD_MAXIMA = 15

    private const val OCTETOS = 4
    private const val DIGITOS_MAX_OCTETO = 3
    private const val VALOR_MAX_OCTETO = 255

    /**
     * `true` sólo para una IPv4 decimal completa: cuatro octetos de 1 a 3 dígitos, cada uno en
     * 0..255 y sin ceros a la izquierda (`01.2.3.4` se rechaza porque algunas pilas lo leen
     * como octal).
     */
    fun esIpv4Valida(texto: String): Boolean {
        val partes = texto.split('.')
        return partes.size == OCTETOS && partes.all(::esOctetoValido)
    }

    /** Lo que el campo acepta mientras se escribe: sólo dígitos y puntos, sin pasar de 15. */
    fun filtrarEntrada(texto: String): String =
        texto.filter { it in '0'..'9' || it == '.' }.take(LONGITUD_MAXIMA)

    private fun esOctetoValido(octeto: String): Boolean =
        octeto.length in 1..DIGITOS_MAX_OCTETO &&
            octeto.all { it in '0'..'9' } &&
            (octeto.length == 1 || octeto[0] != '0') &&
            octeto.toInt() <= VALOR_MAX_OCTETO
}
