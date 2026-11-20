package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

private const val BOARDING_STEP_DEGREES = 15
private const val BOARDING_CLEAR_AHEAD_METERS = 3.0

/**
 * Rumbo con el que arranca un coche recién abordado. Se conserva [preferredHeading] si el coche puede
 * avanzar 3 m sin tocar una barrera; si apunta a un edificio pegado, se busca el rumbo libre más cercano
 * (girando de 15° en 15°, alternando lados). Sin ninguno libre, o sin configuración, se devuelve el preferido.
 */
fun ExteriorCollisionsConfig?.safeBoardingHeading(
    position: GeoPoint,
    preferredHeading: Float,
    dimensions: VehicleDimensions,
): Float {
    if (this == null) return preferredHeading
    if (isBoardingHeadingClear(position, preferredHeading, dimensions)) return preferredHeading
    for (step in 1..(180 / BOARDING_STEP_DEGREES)) {
        for (side in intArrayOf(1, -1)) {
            val candidate = normalizeHeading(preferredHeading + side * step * BOARDING_STEP_DEGREES)
            if (isBoardingHeadingClear(position, candidate, dimensions)) return candidate
        }
    }
    return preferredHeading
}

private fun ExteriorCollisionsConfig.isBoardingHeadingClear(
    position: GeoPoint,
    headingDegrees: Float,
    dimensions: VehicleDimensions,
): Boolean {
    if (obstacleAt(VehicleFootprint(position, headingDegrees, dimensions)) != null) return false
    val radians = headingDegrees.toDouble() * PI / 180.0
    val ahead = position.offsetMeters(cos(radians) * BOARDING_CLEAR_AHEAD_METERS, sin(radians) * BOARDING_CLEAR_AHEAD_METERS)
    return resolveVehiclePath(position, ahead, headingDegrees, dimensions).reachedTarget
}

private fun normalizeHeading(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f
