package ovh.gabrielhuav.pow.domain.streetfighter

/**
 * Sintaxis IPv4 del campo para unirse por LAN, compartida y sin dependencias de plataforma.
 * Consolida PR #172 de jesusGoliat y PR #176 de LuisRan: filtro, validación y error al completar.
 * No comprueba conectividad, nombres de host, IPv6 ni puertos personalizados.
 */
object SfLanIp {
    const val LONGITUD_MAXIMA = 15
    private const val OCTETOS = 4
    private const val DIGITOS_MAX_OCTETO = 3
    private const val VALOR_MAX_OCTETO = 255

    /** Cuatro octetos decimales ASCII de 0..255, sin espacios ni ceros iniciales ambiguos. */
    fun esIpv4Valida(texto: String): Boolean {
        val partes = texto.split('.')
        return partes.size == OCTETOS && partes.all(::esOctetoValido)
    }

    /** Conserva los caracteres admitidos mientras se escribe, aunque la IP aún esté incompleta. */
    fun filtrarEntrada(texto: String): String =
        texto.filter { it in '0'..'9' || it == '.' }.take(LONGITUD_MAXIMA)

    /** No anticipa un error mientras faltan octetos o el usuario acaba de escribir un punto. */
    fun debeMostrarError(texto: String): Boolean =
        texto.count { it == '.' } >= OCTETOS - 1 && !texto.endsWith('.') && !esIpv4Valida(texto)

    private fun esOctetoValido(octeto: String): Boolean =
        octeto.length in 1..DIGITOS_MAX_OCTETO &&
            octeto.all { it in '0'..'9' } &&
            (octeto.length == 1 || octeto[0] != '0') &&
            octeto.toInt() <= VALOR_MAX_OCTETO
}
