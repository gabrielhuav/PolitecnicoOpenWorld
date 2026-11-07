package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

/** Tamaño físico de la zona sólida de un vehículo; es ligeramente menor que su sprite. */
data class VehicleDimensions(
    val lengthMeters: Double,
    val widthMeters: Double,
)

enum class VehicleObstacle { BUILDING, WALL }

data class VehicleOffset(val northMeters: Double, val eastMeters: Double)

/** Resultado determinista de un choque coche-coche arcade: un solo impulso y vuelta al último punto seguro. */
data class TrafficImpactResolution(
    val playerCenter: GeoPoint,
    val playerSpeedMultiplier: Double,
    val trafficOffset: VehicleOffset,
)

/**
 * Resuelve un contacto dinámico sin dejar que el jugador atraviese al tráfico durante el siguiente
 * tick. El tráfico se separa una vez del cuerpo que alcanzó a tocarse y el jugador conserva su
 * última posición válida.
 */
fun resolveTrafficImpact(
    previousPlayerCenter: GeoPoint,
    playerAtImpact: VehicleFootprint,
    traffic: VehicleFootprint,
): TrafficImpactResolution? {
    if (!playerAtImpact.intersects(traffic)) return null
    return TrafficImpactResolution(
        playerCenter = previousPlayerCenter,
        playerSpeedMultiplier = 0.35,
        trafficOffset = traffic.separatingOffsetFrom(playerAtImpact),
    )
}

/** Collider circular para avatares a pie, civiles, enemigos y zombis. */
data class CircleCollider(val radiusMeters: Double) {
    init { require(radiusMeters > 0.0) }
}

/** Vehículo dinámico identificado: su orden por [id] hace reproducible el primer contacto. */
data class DynamicVehicleCollider(
    val id: String,
    val footprint: VehicleFootprint,
    val movable: Boolean,
)

/**
 * Devuelve el primer vehículo que cruza el disco del actor durante todo su trayecto. Los extremos
 * pueden estar libres y aun así atravesar un auto, por eso no basta comprobar el destino.
 */
fun firstVehicleCircleContact(
    from: GeoPoint,
    to: GeoPoint,
    radiusMeters: Double,
    vehicles: Collection<DynamicVehicleCollider>,
): DynamicVehicleCollider? {
    val samples = max(1, ceil(from.distanceMetersTo(to) / CIRCLE_SWEEP_STEP_METERS).toInt())
    return vehicles.sortedBy { it.id }.firstOrNull { vehicle ->
        (0..samples).any { step ->
            val fraction = step.toDouble() / samples
            val point = GeoPoint(
                latitude = from.latitude + (to.latitude - from.latitude) * fraction,
                longitude = from.longitude + (to.longitude - from.longitude) * fraction,
            )
            vehicle.footprint.intersectsCircle(point, radiusMeters)
        }
    }
}

enum class CollisionReason { BUILDING, WALL }

/** Último punto seguro de un barrido; [blockedBy] nulo significa que se alcanzó el destino. */
data class CollisionResolution(val position: GeoPoint, val blockedBy: CollisionReason?) {
    val reachedTarget: Boolean get() = blockedBy == null
}

/** Rectángulo orientado en coordenadas geográficas, con el rumbo usado por el movimiento del mapa. */
data class VehicleFootprint(
    val center: GeoPoint,
    val headingDegrees: Float,
    val dimensions: VehicleDimensions,
) {
    init {
        require(dimensions.lengthMeters > 0.0)
        require(dimensions.widthMeters > 0.0)
    }

    fun intersects(other: VehicleFootprint): Boolean {
        val referenceLatitude = (center.latitude + other.center.latitude) / 2.0
        return polygonsIntersect(cornersInMeters(referenceLatitude), other.cornersInMeters(referenceLatitude))
    }

    fun contains(point: GeoPoint): Boolean {
        val referenceLatitude = center.latitude
        val localPoint = point.toMeters(referenceLatitude)
        return pointInPolygon(localPoint, cornersInMeters(referenceLatitude))
    }

    fun intersectsCircle(center: GeoPoint, radiusMeters: Double): Boolean {
        if (contains(center)) return true
        val referenceLatitude = (this.center.latitude + center.latitude) / 2.0
        val circleCenter = center.toMeters(referenceLatitude)
        return edges(cornersInMeters(referenceLatitude)).any { (from, to) ->
            pointToSegmentDistance(circleCenter, from, to) <= radiusMeters
        }
    }

    /** Vector mínimo que aleja esta carrocería de [other], con una holgura visual de 10 cm. */
    fun separatingOffsetFrom(other: VehicleFootprint): VehicleOffset {
        val referenceLatitude = (center.latitude + other.center.latitude) / 2.0
        val self = cornersInMeters(referenceLatitude)
        val target = other.cornersInMeters(referenceLatitude)
        var smallestOverlap = Double.POSITIVE_INFINITY
        var separationAxis = MeterPoint(0.0, 0.0)
        val selfCenter = center.toMeters(referenceLatitude)
        val targetCenter = other.center.toMeters(referenceLatitude)

        for (rawAxis in axes(self) + axes(target)) {
            val length = sqrt(rawAxis.east * rawAxis.east + rawAxis.north * rawAxis.north)
            if (length <= 1e-9) continue
            val axis = MeterPoint(rawAxis.east / length, rawAxis.north / length)
            val overlap = overlapAmount(self, target, axis)
            if (overlap < smallestOverlap) {
                val pointsAway = (selfCenter.east - targetCenter.east) * axis.east +
                    (selfCenter.north - targetCenter.north) * axis.north >= 0.0
                separationAxis = if (pointsAway) axis else MeterPoint(-axis.east, -axis.north)
                smallestOverlap = overlap
            }
        }
        if (!smallestOverlap.isFinite()) return VehicleOffset(0.0, 0.0)
        return VehicleOffset(
            northMeters = separationAxis.north * (smallestOverlap + VEHICLE_SEPARATION_PADDING_METERS),
            eastMeters = separationAxis.east * (smallestOverlap + VEHICLE_SEPARATION_PADDING_METERS),
        )
    }

    fun movedBy(offset: VehicleOffset): VehicleFootprint = copy(
        center = center.offsetMeters(offset.northMeters, offset.eastMeters),
    )

    internal fun corners(): List<GeoPoint> {
        val halfLength = dimensions.lengthMeters / 2.0
        val halfWidth = dimensions.widthMeters / 2.0
        val radians = headingDegrees.toDouble() * PI / 180.0
        val forwardNorth = cos(radians)
        val forwardEast = sin(radians)
        val rightNorth = -forwardEast
        val rightEast = forwardNorth

        return listOf(
            center.offsetMeters(forwardNorth * halfLength + rightNorth * halfWidth, forwardEast * halfLength + rightEast * halfWidth),
            center.offsetMeters(forwardNorth * halfLength - rightNorth * halfWidth, forwardEast * halfLength - rightEast * halfWidth),
            center.offsetMeters(-forwardNorth * halfLength - rightNorth * halfWidth, -forwardEast * halfLength - rightEast * halfWidth),
            center.offsetMeters(-forwardNorth * halfLength + rightNorth * halfWidth, -forwardEast * halfLength + rightEast * halfWidth),
        )
    }

    private fun cornersInMeters(referenceLatitude: Double): List<MeterPoint> =
        corners().map { it.toMeters(referenceLatitude) }
}

/** Evita crear un vehículo estacionado que cubra la posición actual del jugador. */
fun canPlaceVehicleWithoutCoveringPoint(
    center: GeoPoint,
    headingDegrees: Float,
    dimensions: VehicleDimensions,
    occupiedPoint: GeoPoint,
): Boolean = !VehicleFootprint(center, headingDegrees, dimensions).contains(occupiedPoint)

/** Evalúa el obstáculo que toca la carrocería completa, no solo su punto central. */
fun ExteriorCollisionsConfig.obstacleAt(footprint: VehicleFootprint): VehicleObstacle? {
    val corners = footprint.corners()
    val referenceLatitude = footprint.center.latitude
    val car = corners.map { it.toMeters(referenceLatitude) }

    for (polygon in polygons) {
        if (polygon.role != CollisionRole.BARRIER) continue
        if (polygon.nodes.size < 3) continue
        if (corners.any { polygon.contains(it.latitude, it.longitude) }) return VehicleObstacle.BUILDING
        if (polygon.nodes.any { footprint.contains(GeoPoint(it.lat, it.lon)) }) return VehicleObstacle.BUILDING
        val building = polygon.nodes.map { GeoPoint(it.lat, it.lon).toMeters(referenceLatitude) }
        if (polygonsIntersect(car, building)) return VehicleObstacle.BUILDING
    }

    for (wall in walls) {
        if (wall.effectiveRole != CollisionRole.BARRIER) continue
        val start = GeoPoint(wall.lat1, wall.lon1)
        val end = GeoPoint(wall.lat2, wall.lon2)
        if (footprint.contains(start) || footprint.contains(end)) return VehicleObstacle.WALL
        val wallStart = start.toMeters(referenceLatitude)
        val wallEnd = end.toMeters(referenceLatitude)
        if (edges(car).any { segmentsIntersect(it.first, it.second, wallStart, wallEnd) }) return VehicleObstacle.WALL
    }
    return null
}

/** Resolvedor único de barrido para todos los actores circulares. */
fun ExteriorCollisionsConfig.resolveCirclePath(
    from: GeoPoint,
    to: GeoPoint,
    collider: CircleCollider,
): CollisionResolution {
    val samples = max(1, ceil(from.distanceMetersTo(to) / CIRCLE_SWEEP_STEP_METERS).toInt())
    var lastSafe = from
    for (step in 1..samples) {
        val fraction = step.toDouble() / samples
        val point = GeoPoint(
            latitude = from.latitude + (to.latitude - from.latitude) * fraction,
            longitude = from.longitude + (to.longitude - from.longitude) * fraction,
        )
        circleObstacleAt(point, collider)?.let { return CollisionResolution(lastSafe, it) }
        lastSafe = point
    }
    return CollisionResolution(to, null)
}

/** Fachada del mismo resolvedor para vehículos, conservando el motivo de bloqueo. */
fun ExteriorCollisionsConfig.resolveVehiclePath(
    from: GeoPoint,
    to: GeoPoint,
    headingDegrees: Float,
    dimensions: VehicleDimensions,
): CollisionResolution {
    val samples = max(1, ceil(from.distanceMetersTo(to) / VEHICLE_SWEEP_STEP_METERS).toInt())
    var lastSafe = from
    for (step in 1..samples) {
        val fraction = step.toDouble() / samples
        val point = GeoPoint(
            latitude = from.latitude + (to.latitude - from.latitude) * fraction,
            longitude = from.longitude + (to.longitude - from.longitude) * fraction,
        )
        obstacleAt(VehicleFootprint(point, headingDegrees, dimensions))?.let { obstacle ->
            return CollisionResolution(lastSafe, obstacle.toCollisionReason())
        }
        lastSafe = point
    }
    return CollisionResolution(to, null)
}

/** Misma física estática para tráfico y patrullas: el tipo decide círculo o carrocería. */
fun ExteriorCollisionsConfig.resolveNpcPath(
    type: NpcType,
    carModel: CarModel,
    from: GeoPoint,
    to: GeoPoint,
    headingDegrees: Float,
): CollisionResolution = if (type.usesVehicleFootprint()) {
    resolveVehiclePath(from, to, headingDegrees, carModel.collisionDimensions)
} else {
    resolveCirclePath(from, to, CircleCollider(NPC_FOOT_COLLIDER_METERS))
}

/**
 * Barrido de la carrocería a intervalos máximos de medio metro. Evita atravesar una pared entre
 * dos ticks rápidos sin convertir el game loop en una simulación pesada.
 */
fun ExteriorCollisionsConfig.vehiclePathObstacle(
    from: GeoPoint,
    to: GeoPoint,
    headingDegrees: Float,
    dimensions: VehicleDimensions,
): VehicleObstacle? {
    return resolveVehiclePath(from, to, headingDegrees, dimensions).blockedBy?.toVehicleObstacle()
}

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
private const val VEHICLE_SWEEP_STEP_METERS = 0.5
private const val CIRCLE_SWEEP_STEP_METERS = 0.25
private const val VEHICLE_SEPARATION_PADDING_METERS = 0.1
private const val NPC_FOOT_COLLIDER_METERS = 0.3

private data class MeterPoint(val east: Double, val north: Double)

private fun GeoPoint.toMeters(referenceLatitude: Double): MeterPoint = MeterPoint(
    east = longitude * METERS_PER_DEGREE_LATITUDE * cos(referenceLatitude * PI / 180.0),
    north = latitude * METERS_PER_DEGREE_LATITUDE,
)

fun GeoPoint.offsetMeters(north: Double, east: Double): GeoPoint {
    val longitudeScale = METERS_PER_DEGREE_LATITUDE * cos(latitude * PI / 180.0)
    return GeoPoint(
        latitude = latitude + north / METERS_PER_DEGREE_LATITUDE,
        longitude = longitude + east / longitudeScale,
    )
}

fun GeoPoint.distanceMetersTo(other: GeoPoint): Double {
    val referenceLatitude = (latitude + other.latitude) / 2.0
    val a = toMeters(referenceLatitude)
    val b = other.toMeters(referenceLatitude)
    return sqrt((a.east - b.east) * (a.east - b.east) + (a.north - b.north) * (a.north - b.north))
}

private fun polygonsIntersect(a: List<MeterPoint>, b: List<MeterPoint>): Boolean {
    if (a.size < 3 || b.size < 3) return false
    return axes(a).plus(axes(b)).all { axis -> overlaps(a, b, axis) }
}

private fun pointInPolygon(point: MeterPoint, polygon: List<MeterPoint>): Boolean {
    var inside = false
    var previous = polygon.lastIndex
    for (index in polygon.indices) {
        val current = polygon[index]
        val last = polygon[previous]
        if ((current.north > point.north) != (last.north > point.north) &&
            point.east < (last.east - current.east) * (point.north - current.north) /
                (last.north - current.north) + current.east) {
            inside = !inside
        }
        previous = index
    }
    return inside
}

private fun axes(polygon: List<MeterPoint>): List<MeterPoint> = edges(polygon).map { (from, to) ->
    MeterPoint(east = -(to.north - from.north), north = to.east - from.east)
}

private fun edges(points: List<MeterPoint>): List<Pair<MeterPoint, MeterPoint>> =
    points.indices.map { index -> points[index] to points[(index + 1) % points.size] }

private fun overlaps(a: List<MeterPoint>, b: List<MeterPoint>, axis: MeterPoint): Boolean {
    fun projection(points: List<MeterPoint>): Pair<Double, Double> {
        var low = Double.POSITIVE_INFINITY
        var high = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            val value = point.east * axis.east + point.north * axis.north
            low = min(low, value)
            high = max(high, value)
        }
        return low to high
    }
    val (aLow, aHigh) = projection(a)
    val (bLow, bHigh) = projection(b)
    return aLow <= bHigh && bLow <= aHigh
}

private fun overlapAmount(a: List<MeterPoint>, b: List<MeterPoint>, axis: MeterPoint): Double {
    fun projection(points: List<MeterPoint>): Pair<Double, Double> {
        var low = Double.POSITIVE_INFINITY
        var high = Double.NEGATIVE_INFINITY
        points.forEach { point ->
            val value = point.east * axis.east + point.north * axis.north
            low = min(low, value)
            high = max(high, value)
        }
        return low to high
    }
    val (aLow, aHigh) = projection(a)
    val (bLow, bHigh) = projection(b)
    return min(aHigh, bHigh) - max(aLow, bLow)
}

private fun segmentsIntersect(a: MeterPoint, b: MeterPoint, c: MeterPoint, d: MeterPoint): Boolean {
    fun cross(first: MeterPoint, second: MeterPoint, third: MeterPoint): Double =
        (second.east - first.east) * (third.north - first.north) -
            (second.north - first.north) * (third.east - first.east)

    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)
    return (abC == 0.0 || abD == 0.0 || abC > 0.0 != abD > 0.0) &&
        (cdA == 0.0 || cdB == 0.0 || cdA > 0.0 != cdB > 0.0)
}

private fun ExteriorCollisionsConfig.circleObstacleAt(
    center: GeoPoint,
    collider: CircleCollider,
): CollisionReason? {
    val referenceLatitude = center.latitude
    val point = center.toMeters(referenceLatitude)
    for (polygon in polygons) {
        if (polygon.role != CollisionRole.BARRIER || polygon.nodes.size < 3) continue
        if (polygon.contains(center.latitude, center.longitude)) return CollisionReason.BUILDING
        val vertices = polygon.nodes.map { GeoPoint(it.lat, it.lon).toMeters(referenceLatitude) }
        if (edges(vertices).any { (from, to) -> pointToSegmentDistance(point, from, to) <= collider.radiusMeters }) {
            return CollisionReason.BUILDING
        }
    }
    for (wall in walls) {
        if (wall.effectiveRole != CollisionRole.BARRIER) continue
        val from = GeoPoint(wall.lat1, wall.lon1).toMeters(referenceLatitude)
        val to = GeoPoint(wall.lat2, wall.lon2).toMeters(referenceLatitude)
        if (pointToSegmentDistance(point, from, to) <= collider.radiusMeters) return CollisionReason.WALL
    }
    return null
}

private fun pointToSegmentDistance(point: MeterPoint, from: MeterPoint, to: MeterPoint): Double {
    val east = to.east - from.east
    val north = to.north - from.north
    val lengthSquared = east * east + north * north
    if (lengthSquared <= 1e-12) return sqrt((point.east - from.east) * (point.east - from.east) + (point.north - from.north) * (point.north - from.north))
    val projection = (((point.east - from.east) * east + (point.north - from.north) * north) / lengthSquared).coerceIn(0.0, 1.0)
    val closestEast = from.east + east * projection
    val closestNorth = from.north + north * projection
    return sqrt((point.east - closestEast) * (point.east - closestEast) + (point.north - closestNorth) * (point.north - closestNorth))
}

private fun VehicleObstacle.toCollisionReason(): CollisionReason = when (this) {
    VehicleObstacle.BUILDING -> CollisionReason.BUILDING
    VehicleObstacle.WALL -> CollisionReason.WALL
}

private fun CollisionReason.toVehicleObstacle(): VehicleObstacle = when (this) {
    CollisionReason.BUILDING -> VehicleObstacle.BUILDING
    CollisionReason.WALL -> VehicleObstacle.WALL
}
