package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
// ⚠️ `java.lang.Math` NO existe en Kotlin/Native. `kotlin.math` da los MISMOS valores con la misma
// precisión (Double IEEE-754), así que la geometría del mundo no cambia ni un metro.
// `Math.toRadians(x)` se sustituyó por `x * PI / 180.0`, que es literalmente su implementación.
import kotlin.math.PI
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

        val angleRad = (rotationAngle.toDouble() * PI / 180.0)
        val rotatedDx = dxMeters * cos(angleRad) - dyMeters * sin(angleRad)
        val rotatedDy = dxMeters * sin(angleRad) + dyMeters * cos(angleRad)

        val earthRadius = 6378137.0
        val dLat = (rotatedDy / earthRadius) * (180.0 / PI)
        val dLon = (rotatedDx / (earthRadius * cos(PI * location.latitude / 180.0))) * (180.0 / PI)

        return GeoPoint(location.latitude + dLat, location.longitude + dLon)
    }

    fun contains(point: GeoPoint): Boolean {
        val dLat = point.latitude - location.latitude
        val dLon = point.longitude - location.longitude

        val earthRadius = 6378137.0
        val dyGlobalMeters = dLat * (PI / 180.0) * earthRadius
        val dxGlobalMeters = dLon * (PI / 180.0) * (earthRadius * cos(PI * location.latitude / 180.0))

        val angleRad = (-rotationAngle.toDouble() * PI / 180.0)
        val dxLocalMeters = dxGlobalMeters * cos(angleRad) - dyGlobalMeters * sin(angleRad)
        val dyLocalMeters = dxGlobalMeters * sin(angleRad) + dyGlobalMeters * cos(angleRad)

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

        val rotatedDy = dLat * (PI / 180.0) * earthRadius
        val rotatedDx = dLon * (PI / 180.0) * (earthRadius * cos(PI * location.latitude / 180.0))

        val angleRad = (-rotationAngle.toDouble() * PI / 180.0)
        val dxMeters = rotatedDx * cos(angleRad) - rotatedDy * sin(angleRad)
        val dyMeters = rotatedDx * sin(angleRad) + rotatedDy * cos(angleRad)

        val localX = (dxMeters / (baseWidthMeters * scaleX)) + 0.5
        val localY = 0.5 - (dyMeters / (baseHeightMeters * scaleY))

        return Pair(localX.toFloat(), localY.toFloat())
    }
}
