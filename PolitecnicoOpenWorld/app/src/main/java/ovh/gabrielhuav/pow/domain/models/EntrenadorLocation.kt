package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

object EntrenadorLocation {
    // NPC fijo cerca de la entrada principal de ESCOM (zona accesible sin colliders)
    const val LAT = 19.50378
    const val LON = -99.14694

    val geoPoint get() = GeoPoint(LAT, LON)

    const val MARKER_ID   = "entrenador_politecnico"
    const val MARKER_NAME = "Entrenador Politécnico"
    const val MARKER_ASSET = "collectibles/colec_3.webp"

    /** Radio de interacción (grados). ~22 m */
    const val TRIGGER_RADIUS_DEG = 0.00020

    // Línea de meta: zona norte del campus ESCOM (dentro del bbox de movimiento libre)
    // ESCOM bbox: lat [19.50356, 19.50556] | lon [-99.14774, -99.14574]
    const val FINISH_LAT = 19.50530
    const val FINISH_LON = -99.14650
    const val FINISH_MARKER_ID   = "race_finish_line"
    const val FINISH_MARKER_NAME = "Meta"
    const val FINISH_MARKER_ASSET = "collectibles/colec_1.webp"

    /**
     * Radio de llegada (grados). ~145 m.
     * La distancia del Entrenador (start) a la meta es ~175 m, así que el jugador
     * necesita recorrer al menos ~30 m antes de entrar en la zona de detección.
     */
    const val FINISH_RADIUS_DEG = 0.00130

    // ID del coleccionable especial que se otorga al ganar por primera vez
    const val REWARD_COLLECTIBLE_ID = "c_race_trophy"
}
