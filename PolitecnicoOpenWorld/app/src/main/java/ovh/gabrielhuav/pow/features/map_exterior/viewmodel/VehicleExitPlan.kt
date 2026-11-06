package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlin.math.cos
import kotlin.math.sin
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.VehicleDimensions
import ovh.gabrielhuav.pow.domain.models.map.offsetMeters

private const val EXIT_PERSON_CLEARANCE_METERS = 0.75

/** Posiciones de descenso, en prioridad: conductor, pasajero y dos diagonales traseras. */
internal fun vehicleExitCandidates(
    center: GeoPoint,
    headingDegrees: Float,
    dimensions: VehicleDimensions,
): List<GeoPoint> {
    val radians = Math.toRadians(headingDegrees.toDouble())
    val forwardNorth = cos(radians)
    val forwardEast = sin(radians)
    val leftNorth = forwardEast
    val leftEast = -forwardNorth
    val sideDistance = dimensions.widthMeters / 2.0 + EXIT_PERSON_CLEARANCE_METERS
    val rearDistance = dimensions.lengthMeters / 2.0 + EXIT_PERSON_CLEARANCE_METERS

    fun offset(north: Double, east: Double) = center.offsetMeters(north, east)
    return listOf(
        offset(leftNorth * sideDistance, leftEast * sideDistance),
        offset(-leftNorth * sideDistance, -leftEast * sideDistance),
        offset(-forwardNorth * rearDistance + leftNorth * sideDistance * 0.5, -forwardEast * rearDistance + leftEast * sideDistance * 0.5),
        offset(-forwardNorth * rearDistance - leftNorth * sideDistance * 0.5, -forwardEast * rearDistance - leftEast * sideDistance * 0.5),
    )
}

internal fun selectVehicleExit(
    candidates: List<GeoPoint>,
    isWalkable: (GeoPoint) -> Boolean,
): GeoPoint? = candidates.firstOrNull(isWalkable)

/** Salida obligada: después de las puertas, abre un anillo alrededor del vehículo. */
internal fun forcedVehicleExitCandidates(
    center: GeoPoint,
    headingDegrees: Float,
    dimensions: VehicleDimensions,
): List<GeoPoint> {
    val manual = vehicleExitCandidates(center, headingDegrees, dimensions)
    val baseRadius = maxOf(dimensions.lengthMeters / 2.0, dimensions.widthMeters / 2.0) + 0.9
    val ring = buildList {
        for (radius in listOf(baseRadius, baseRadius + 1.0)) {
            for (step in 0 until 8) {
                val radians = Math.toRadians(headingDegrees.toDouble() + step * 45.0)
                add(center.offsetMeters(cos(radians) * radius, sin(radians) * radius))
            }
        }
    }
    return manual + ring
}
