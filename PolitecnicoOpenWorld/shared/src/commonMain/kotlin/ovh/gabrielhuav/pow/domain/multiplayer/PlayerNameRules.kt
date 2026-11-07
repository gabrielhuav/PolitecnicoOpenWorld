package ovh.gabrielhuav.pow.domain.multiplayer

/**
 * Validacion compartida del nombre multijugador, adaptada del PR #167.
 *
 * El limite de 16 usa unidades UTF-16, igual que el campo original; los recortes
 * conservan pares sustitutos completos. Se admiten letras y digitos de Char,
 * incluidos acentos precompuestos. No se convierte NFC/NFD: las marcas combinantes
 * independientes siguen fuera de los caracteres permitidos por el PR.
 */
object PlayerNameRules {
    const val MAX_LENGTH = 16
    const val MIN_LENGTH = 3
    const val ALLOWED_SYMBOLS = " _-."

    private const val ELLIPSIS = '…'
    private const val DISPLAY_FALLBACK = "Player"

    enum class Problem { TOO_SHORT, TOO_LONG, INVALID_CHARS }

    /** Conserva un espacio final para poder escribir una segunda palabra. */
    fun sanitizeInput(raw: String): String = takeCompleteCharacters(normalize(raw), MAX_LENGTH)

    /** Blanco es valido: el servidor recibira el nombre de respaldo. */
    fun validate(name: String): Problem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> null
            trimmed.length > MAX_LENGTH -> Problem.TOO_LONG
            trimmed.any { !it.isLetterOrDigit() && it !in ALLOWED_SYMBOLS } -> Problem.INVALID_CHARS
            trimmed.length < MIN_LENGTH -> Problem.TOO_SHORT
            else -> null
        }
    }

    /**
     * Nombre normalizado para guardar/enviar. Un texto no vacio invalido nunca se
     * convierte en un respaldo silencioso; devuelve null y la UI conserva el campo.
     * El respaldo tambien se normaliza y valida antes de salir de esta API.
     */
    fun finalName(typed: String, fallback: () -> String): String? {
        val clean = sanitizeInput(typed).trimEnd()
        val result = if (clean.isEmpty()) sanitizeInput(fallback()).trimEnd() else clean
        return result.takeIf { it.isNotEmpty() && validate(it) == null }
    }

    /** Normaliza nombres antiguos/remotos y acota tambien el respaldo. */
    fun forDisplay(remote: String?, fallback: String): String {
        val clean = normalize(remote.orEmpty()).trimEnd()
        val result = clean.ifEmpty { normalize(fallback).trimEnd().ifEmpty { DISPLAY_FALLBACK } }
        return if (result.length > MAX_LENGTH) {
            takeCompleteCharacters(result, MAX_LENGTH - 1).trimEnd() + ELLIPSIS
        } else {
            result
        }
    }

    private fun takeCompleteCharacters(value: String, maxLength: Int): String {
        var end = minOf(value.length, maxLength)
        if (end > 0 && value[end - 1].isHighSurrogate()) end--
        return value.substring(0, end)
    }

    private fun normalize(raw: String): String {
        val out = StringBuilder(minOf(raw.length, MAX_LENGTH + 1))
        var index = 0
        while (index < raw.length) {
            val current = raw[index++]
            when {
                current.isHighSurrogate() && index < raw.length && raw[index].isLowSurrogate() -> {
                    out.append(current)
                    out.append(raw[index++])
                }
                current.isSurrogate() -> Unit
                current.isWhitespace() -> {
                    if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                }
                !current.isISOControl() && current.category != CharCategory.FORMAT -> out.append(current)
            }
        }
        return out.toString()
    }
}
