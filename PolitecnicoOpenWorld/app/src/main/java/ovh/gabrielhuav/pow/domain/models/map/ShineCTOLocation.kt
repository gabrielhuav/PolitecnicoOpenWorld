package ovh.gabrielhuav.pow.domain.models.map

import org.osmdroid.util.GeoPoint

/**
 * Easter-egg recinto: ShineCTO.
 * Coordenadas del interactuable en el mapa exterior.
 */
object ShineCTOLocation {
    const val LAT = 19.459049
    const val LON = -99.163251

    /** Radio (grados) dentro del cual el jugador puede activar el easter egg. */
    const val TRIGGER_RADIUS = 0.00015

    val geoPoint get() = GeoPoint(LAT, LON)

    /** ID estable usado como clave en collectibles / markers. */
    const val MARKER_ID = "easter_shinecto"
    const val MARKER_NAME = "Lugar Misterioso"
    const val MARKER_ASSET = "PLACES/shine_cto/s_logo.webp"
}

enum class ShineCTOFloor {
    GROUND, // s_pbaja.webp
    UPPER   // s_palta.webp
}