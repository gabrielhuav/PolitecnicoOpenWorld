package ovh.gabrielhuav.pow.domain.multiplayer

/**
 * Reglas PURAS (sin Compose ni Android) del nombre de jugador de Multijugador.
 *
 * El nombre se dibuja encima del personaje en la pantalla de los DEMÁS jugadores, así que:
 * - se limita a [MAX_LENGTH] caracteres (antes no tenía límite y cubría la pantalla);
 * - al teclear o pegar se quitan saltos de línea, tabuladores, espacios iniciales y dobles;
 * - un nombre escrito debe tener al menos [MIN_LENGTH] caracteres y solo letras (con acentos),
 *   números, espacios y los símbolos de [ALLOWED_SYMBOLS];
 * - vacío sigue siendo válido: se usa el nombre de respaldo `Jugador_XXXX`.
 *
 * Como vive en `commonMain`, la misma regla se prueba en la JVM (`PlayerNameRulesTest`).
 */
object PlayerNameRules {

    /** Máximo de caracteres del nombre propio y de los nombres remotos que se dibujan. */
    const val MAX_LENGTH = 16

    /** Mínimo de caracteres de un nombre escrito (vacío = nombre de respaldo). */
    const val MIN_LENGTH = 3

    /** Símbolos permitidos además de letras y dígitos. */
    const val ALLOWED_SYMBOLS = " _-."

    private const val ELLIPSIS = '…'

    /** Motivo por el que un nombre escrito no se acepta. */
    enum class Problem { TOO_SHORT, INVALID_CHARS }

    /**
     * Normaliza lo que el usuario teclea o pega en el campo, SIN rechazarlo:
     * convierte cualquier espacio en blanco (`\n`, `\t`…) en un espacio, descarta caracteres de
     * control, no deja espacios al inicio ni repetidos, y corta a [MAX_LENGTH].
     * El espacio final se conserva para poder seguir escribiendo una segunda palabra.
     */
    fun sanitizeInput(raw: String): String = normalize(raw).take(MAX_LENGTH)

    /** Devuelve el problema del nombre o `null` si se puede usar (vacío también es válido). */
    fun validate(name: String): Problem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> null
            trimmed.any { !it.isLetterOrDigit() && it !in ALLOWED_SYMBOLS } -> Problem.INVALID_CHARS
            trimmed.length < MIN_LENGTH -> Problem.TOO_SHORT
            else -> null
        }
    }

    /** Nombre que se envía al servidor y se guarda: normalizado, sin espacios finales. */
    fun finalName(typed: String, fallback: () -> String): String =
        sanitizeInput(typed).trim().ifEmpty(fallback)

    /**
     * Nombre de OTRO jugador listo para dibujarse. Un cliente anterior a este cambio puede
     * mandar un nombre sin límite: se normaliza y, si excede [MAX_LENGTH], se corta con "…".
     */
    fun forDisplay(remote: String?, fallback: String): String {
        val clean = normalize(remote.orEmpty()).trim()
        return when {
            clean.isEmpty() -> fallback
            clean.length > MAX_LENGTH -> clean.take(MAX_LENGTH - 1).trimEnd() + ELLIPSIS
            else -> clean
        }
    }

    private fun normalize(raw: String): String {
        val out = StringBuilder(raw.length)
        for (c in raw) {
            val ch = if (c.isWhitespace()) ' ' else c
            val skip = ch.isISOControl() || (ch == ' ' && (out.isEmpty() || out.last() == ' '))
            if (!skip) out.append(ch)
        }
        return out.toString()
    }
}
