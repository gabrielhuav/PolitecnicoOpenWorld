package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

data class Landmark(
    val id: String,
    val name: String,
    var location: GeoPoint,
    var rotationAngle: Float = 0f,
    val assetPath: String,
    val scaleFactor: Float = 1.0f
)