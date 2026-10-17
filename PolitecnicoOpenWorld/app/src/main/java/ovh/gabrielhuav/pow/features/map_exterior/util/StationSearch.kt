package ovh.gabrielhuav.pow.features.map_exterior.util

import java.text.Normalizer

/**
 * Búsqueda de estaciones (Metro/Metrobús) ignorando acentos — aislada de la UI para poder
 * probarla con un unit test normal (`:app:testDebugUnitTest`), sin Compose ni Android real.
 *
 * Bug real (examen QA): el buscador de estaciones en el menú de Teletransporte
 * (`WorldMapScreen.kt`) comparaba con `nombre.contains(texto, ignoreCase = true)`.
 * `ignoreCase` SOLO ignora mayúsculas/minúsculas, no acentos. Del catálogo real de 163
 * estaciones de Metro, 54 llevan acento o ñ (Pantitlán, Juárez, Coyoacán, Zócalo, Instituto
 * del Petróleo…), así que escribir "pantitlan" o "juarez" sin acento no encontraba nada.
 *
 * NO es solo quitar cualquier marca diacrítica: la ñ es una LETRA propia del español, no una
 * vocal con acento, así que "Peñón" no debe emparejar con una búsqueda "penon". Unicode
 * representa la ñ, en su forma descompuesta (NFD), como 'n' + un acento de tilde combinado
 * (U+0303) — por eso se excluye justo esa marca al quitar acentos, y sí se conserva como ñ.
 */
private val REMOVABLE_DIACRITICS = Regex("[\\p{Mn}&&[^\\u0303]]+")

/** Quita acentos (á→a, é→e, ü→u, …) preservando la ñ, y pasa a minúsculas. */
fun String.normalizeForSearch(): String =
    REMOVABLE_DIACRITICS.replace(Normalizer.normalize(this, Normalizer.Form.NFD), "").lowercase()

/**
 * ¿[stationName] coincide con lo que el usuario escribió en [query]? Ignora acentos y
 * mayúsculas/minúsculas; una búsqueda vacía (o solo espacios) coincide con todo, igual que antes.
 */
fun stationMatchesQuery(stationName: String, query: String): Boolean {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return true
    return stationName.normalizeForSearch().contains(trimmedQuery.normalizeForSearch())
}
