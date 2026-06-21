package ovh.gabrielhuav.pow.domain.models.map

/**
 * Catálogo DATA-DRIVEN de entradas a interiores: mapea una puerta (landmark `escom_door_*`)
 * a la ruta de navegación de su interior, por PATRÓN del nombre del landmark.
 *
 * EXPANDIBLE: para hacer enterable un edificio nuevo del mapa exterior basta con:
 *   1) Añadir UNA `Entry` aquí (patrones de nombre → ruta).
 *   2) Registrar esa `route` como `composable(...)` en `MainActivity`.
 *   3) Crear su Screen/ViewModel (o reusar el motor de Interiores con `startRoom`).
 * Antes esto vivía hardcodeado en `WorldMapViewModel.handleInteraction` (un `when` con
 * `name.contains(...)`); centralizarlo evita tocar el VM al añadir edificios. Ver 04/06.
 */
object InteriorEntryCatalog {

    /** Una entrada del catálogo: si el nombre del landmark contiene alguno de
     *  `nameContains` (ignorando mayúsculas/acentos triviales), navega a `route`. */
    data class Entry(val nameContains: List<String>, val route: String)

    // El ORDEN importa: gana la PRIMERA coincidencia. El default (puertas ESCOM
    // Norte/Sur, etc.) NO va aquí; es [DEFAULT_ROUTE].
    val entries: List<Entry> = listOf(
        Entry(listOf("Béisbol", "Beisbol"), "interior_deportivo_beis"),
        Entry(listOf("Fútbol", "Futbol"), "interior_deportivo_futbol"),
        // FES Aragón usa el MOTOR DE INTERIORES pero arranca en su propia sala del catálogo.
        Entry(listOf("FES"), "interiores_zombies?startRoom=fes_interior"),
    )

    // Puertas ESCOM (Norte/Sur, etc.) → lobby de ESCOM (sin arg = default).
    const val DEFAULT_ROUTE = "interiores_zombies"

    /** Ruta del interior para una puerta dada por el NOMBRE de su landmark. */
    fun routeForDoorName(name: String): String =
        entries.firstOrNull { e -> e.nameContains.any { name.contains(it, ignoreCase = true) } }
            ?.route ?: DEFAULT_ROUTE
}
