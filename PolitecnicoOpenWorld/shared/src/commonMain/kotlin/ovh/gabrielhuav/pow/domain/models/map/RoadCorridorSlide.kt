package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.hypot
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

/**
 * Pared que desliza: si [target] queda a más de [maxRadiusDeg] (grados) de [roadPoint] (el punto de calle más
 * cercano a [target]) devuelve el punto del borde del corredor en esa misma dirección; si no, [target].
 * Como [roadPoint] se recalcula para el destino, el coche avanza a lo largo de la calle en vez de quedarse clavado.
 */
fun slideAlongCorridor(roadPoint: GeoPoint, target: GeoPoint, maxRadiusDeg: Double): GeoPoint {
    val dLat = target.latitude - roadPoint.latitude
    val dLon = target.longitude - roadPoint.longitude
    val dist = hypot(dLat, dLon)
    if (dist <= maxRadiusDeg || dist == 0.0) return target
    val k = maxRadiusDeg / dist
    return GeoPoint(roadPoint.latitude + dLat * k, roadPoint.longitude + dLon * k)
}
