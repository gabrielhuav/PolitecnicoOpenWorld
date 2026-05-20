package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

data class Landmark(
    val id: Long,
    val name: String,
    val location: GeoPoint,
    val rotationAngle: Float = 0f,
    val assetPath: String,
    val scaleFactor: Float = 1.0f,
    val baseWidthMeters: Float, // Nueva propiedad
    val baseHeightMeters: Float // Nueva propiedad
)

