package ovh.gabrielhuav.pow.domain.models

/**
 * Representación agnóstica de coordenadas.
 * Evita acoplar la capa de dominio a librerías de UI/Mapas como OSMDroid (GeoPoint) o Google/Leaflet.
 */
data class MapLocation(
    val latitude: Double,
    val longitude: Double
)