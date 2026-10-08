package ovh.gabrielhuav.pow.features.streetfighter.ui

/**
 * Validación PURA (sin Android ni Compose) de la dirección IPv4 que el invitado teclea en el
 * menú online de Street Fighter para unirse a un servidor LAN (`OnlineMenuOverlay`).
 *
 * Antes la UI sólo contaba puntos (`count { it == '.' } == 3`), así que `...`, `a.b.c.d` o
 * `999.999.999.999` habilitaban el botón UNIRSE y lanzaban una conexión condenada a fallar
 * (3 intentos + pausas) antes de mostrar el error. Aquí se centraliza la regla para poder
 * probarla en JVM y reutilizarla tanto para habilitar el botón como para mostrar el mensaje.
 *
 * Fuera de alcance: IPv6 y nombres de host (la UI actual sólo ofrece IPv4 por IP del anfitrión).
 */
internal object SfLanAddress {
    /** Longitud máxima de una IPv4 escrita: `255.255.255.255`. */
    const val MAX_LENGTH = 15

    private const val OCTET_COUNT = 4
    private const val MAX_OCTET_VALUE = 255
    private const val MAX_OCTET_DIGITS = 3
    private const val DOTS_IN_FULL_ADDRESS = 3

    /** Conserva sólo dígitos ASCII y puntos, y recorta a [MAX_LENGTH]. */
    fun sanitize(raw: String): String =
        raw.filter { it in '0'..'9' || it == '.' }.take(MAX_LENGTH)

    /** `true` sólo para IPv4 decimal con 4 octetos 0..255, sin ceros a la izquierda ni espacios. */
    fun isValidIpv4(raw: String): Boolean {
        val octets = raw.split('.')
        return octets.size == OCTET_COUNT && octets.all { isValidOctet(it) }
    }

    /**
     * `true` cuando el texto ya "parece terminado" (>= 3 puntos y no acaba en punto) pero NO es
     * una IPv4 válida. Mientras el usuario aún escribe (`192.168.1.`) no se muestra error.
     */
    fun shouldShowError(raw: String): Boolean =
        raw.count { it == '.' } >= DOTS_IN_FULL_ADDRESS && !raw.endsWith('.') && !isValidIpv4(raw)

    private fun isValidOctet(part: String): Boolean {
        if (part.isEmpty() || part.length > MAX_OCTET_DIGITS) return false
        if (part.any { it !in '0'..'9' }) return false
        if (part.length > 1 && part.startsWith('0')) return false // "01" es ambiguo (octal en algunos parsers)
        return part.toInt() <= MAX_OCTET_VALUE
    }
}
