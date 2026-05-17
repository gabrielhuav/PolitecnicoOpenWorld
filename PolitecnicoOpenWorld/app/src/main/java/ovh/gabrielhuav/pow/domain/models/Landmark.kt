package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

/**
 * Representación de dominio de un edificio/asset estático del mapa.
 * El id es Long (coincide con la PK de Room) para facilitar updates.
 */
data class Landmark(
    val id: Long,
    val name: String,
    val location: GeoPoint,
    val rotationAngle: Float = 0f,
    val assetPath: String,
    val scaleFactor: Float = 1.0f
)