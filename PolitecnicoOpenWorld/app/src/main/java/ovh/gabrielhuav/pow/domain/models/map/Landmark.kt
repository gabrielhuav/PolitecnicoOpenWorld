package ovh.gabrielhuav.pow.domain.models.map

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import kotlin.math.cos
import kotlin.math.sin

data class Landmark(
    val id: Long,
    val name: String,
    val location: GeoPoint,
    val rotationAngle: Float = 0f,
    val assetPath: String,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val baseWidthMeters: Float,
    val baseHeightMeters: Float,
    val navGraph: LandmarkNavGraph? = null
) {
    fun toGlobalGeoPoint(localX: Float, localY: Float): GeoPoint {
        val dxMeters = (localX - 0.5f) * baseWidthMeters * scaleX
        val dyMeters = (0.5f - localY) * baseHeightMeters * scaleY

        val angleRad = Math.toRadians(rotationAngle.toDouble())
        val rotatedDx = dxMeters * cos(angleRad) - dyMeters * sin(angleRad)
        val rotatedDy = dxMeters * sin(angleRad) + dyMeters * cos(angleRad)

        val earthRadius = 6378137.0
        val dLat = (rotatedDy / earthRadius) * (180.0 / Math.PI)
        val dLon = (rotatedDx / (earthRadius * cos(Math.PI * location.latitude / 180.0))) * (180.0 / Math.PI)

        return GeoPoint(location.latitude + dLat, location.longitude + dLon)
    }

    fun contains(point: GeoPoint): Boolean {
        val dLat = point.latitude - location.latitude
        val dLon = point.longitude - location.longitude

        val earthRadius = 6378137.0
        val dyGlobalMeters = dLat * (Math.PI / 180.0) * earthRadius
        val dxGlobalMeters = dLon * (Math.PI / 180.0) * (earthRadius * Math.cos(Math.PI * location.latitude / 180.0))

        val angleRad = Math.toRadians(-rotationAngle.toDouble())
        val dxLocalMeters = dxGlobalMeters * Math.cos(angleRad) - dyGlobalMeters * Math.sin(angleRad)
        val dyLocalMeters = dxGlobalMeters * Math.sin(angleRad) + dyGlobalMeters * Math.cos(angleRad)

        val actualWidth = baseWidthMeters * scaleX
        val actualHeight = baseHeightMeters * scaleY

        val localX = (dxLocalMeters / actualWidth) + 0.5
        val localY = 0.5 - (dyLocalMeters / actualHeight)

        return localX in 0.0..1.0 && localY in 0.0..1.0
    }

    fun toLocalCoordinates(globalPoint: GeoPoint): Pair<Float, Float> {
        val earthRadius = 6378137.0

        val dLat = globalPoint.latitude - location.latitude
        val dLon = globalPoint.longitude - location.longitude

        val rotatedDy = dLat * (Math.PI / 180.0) * earthRadius
        val rotatedDx = dLon * (Math.PI / 180.0) * (earthRadius * cos(Math.PI * location.latitude / 180.0))

        val angleRad = Math.toRadians(-rotationAngle.toDouble())
        val dxMeters = rotatedDx * cos(angleRad) - rotatedDy * sin(angleRad)
        val dyMeters = rotatedDx * sin(angleRad) + rotatedDy * cos(angleRad)

        val localX = (dxMeters / (baseWidthMeters * scaleX)) + 0.5
        val localY = 0.5 - (dyMeters / (baseHeightMeters * scaleY))

        return Pair(localX.toFloat(), localY.toFloat())
    }
}
