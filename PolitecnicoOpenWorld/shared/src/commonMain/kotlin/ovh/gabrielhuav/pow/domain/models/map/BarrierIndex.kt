package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.max
import kotlin.math.min

/**
 * Índice de solo lectura de las barreras físicas (polígonos BARRIER y bardas con rol efectivo BARRIER).
 * Sirve únicamente como DESCARTE previo por caja envolvente en grados: el resultado de la colisión lo
 * sigue decidiendo la geometría exacta de [obstacleAt]. Se construye una vez por configuración, así el
 * barrido no filtra por rol (ni compara nombres) en cada muestra ni asigna listas lejos de todo edificio.
 */
internal class BarrierIndex private constructor(
    val polygons: Array<IndexedPolygon>,
    val walls: Array<IndexedWall>,
) {
    val isEmpty: Boolean = polygons.isEmpty() && walls.isEmpty()
    private var globalMinLat = Double.POSITIVE_INFINITY
    private var globalMaxLat = Double.NEGATIVE_INFINITY
    private var globalMinLon = Double.POSITIVE_INFINITY
    private var globalMaxLon = Double.NEGATIVE_INFINITY

    init {
        for (p in polygons) extend(p.minLat, p.maxLat, p.minLon, p.maxLon)
        for (w in walls) extend(w.minLat, w.maxLat, w.minLon, w.maxLon)
    }

    private fun extend(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        globalMinLat = min(globalMinLat, minLat); globalMaxLat = max(globalMaxLat, maxLat)
        globalMinLon = min(globalMinLon, minLon); globalMaxLon = max(globalMaxLon, maxLon)
    }

    /** ¿La caja dada toca la caja que envuelve TODAS las barreras? (rechazo sin asignar memoria). */
    fun mayTouch(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): Boolean =
        !isEmpty && maxLat >= globalMinLat && minLat <= globalMaxLat && maxLon >= globalMinLon && minLon <= globalMaxLon

    companion object {
        fun build(config: ExteriorCollisionsConfig): BarrierIndex = BarrierIndex(
            polygons = config.polygons
                .filter { it.role == CollisionRole.BARRIER && it.nodes.size >= 3 }
                .map { IndexedPolygon(it) }
                .toTypedArray(),
            walls = config.walls
                .filter { it.effectiveRole == CollisionRole.BARRIER }
                .map { IndexedWall(it) }
                .toTypedArray(),
        )
    }
}

internal class IndexedPolygon(val polygon: CollisionPolygon) {
    val minLat: Double = polygon.nodes.minOf { it.lat }
    val maxLat: Double = polygon.nodes.maxOf { it.lat }
    val minLon: Double = polygon.nodes.minOf { it.lon }
    val maxLon: Double = polygon.nodes.maxOf { it.lon }

    fun overlaps(boxMinLat: Double, boxMaxLat: Double, boxMinLon: Double, boxMaxLon: Double): Boolean =
        boxMaxLat >= minLat && boxMinLat <= maxLat && boxMaxLon >= minLon && boxMinLon <= maxLon
}

internal class IndexedWall(val wall: CollisionWall) {
    val minLat: Double = min(wall.lat1, wall.lat2)
    val maxLat: Double = max(wall.lat1, wall.lat2)
    val minLon: Double = min(wall.lon1, wall.lon2)
    val maxLon: Double = max(wall.lon1, wall.lon2)

    fun overlaps(boxMinLat: Double, boxMaxLat: Double, boxMinLon: Double, boxMaxLon: Double): Boolean =
        boxMaxLat >= minLat && boxMinLat <= maxLat && boxMaxLon >= minLon && boxMinLon <= maxLon
}
