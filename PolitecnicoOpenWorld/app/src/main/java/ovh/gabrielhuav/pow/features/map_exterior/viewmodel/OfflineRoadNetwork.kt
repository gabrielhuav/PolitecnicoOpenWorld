package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.MapNode
import ovh.gabrielhuav.pow.domain.models.map.MapWay

/**
 * Red mínima local para que Mundo libre siga siendo jugable si Overpass no responde.
 * No se persiste: la siguiente recarga de zona puede sustituirla por calles reales.
 */
internal fun offlineRoadNetwork(center: GeoPoint): List<MapWay> {
    val offset = 0.0008

    fun node(id: Long, latOffset: Double, lonOffset: Double) = MapNode(
        id = id,
        lat = center.latitude + latOffset,
        lon = center.longitude + lonOffset,
    )

    val origin = node(-1, 0.0, 0.0)
    fun corridor(id: Long, start: MapNode, end: MapNode) = MapWay(
        id = id,
        nodes = listOf(start, origin, end),
        isForCars = true,
        isForPeople = true,
    )

    return listOf(
        corridor(-10, node(-2, 0.0, -offset), node(-3, 0.0, offset)),
        corridor(-11, node(-4, -offset, 0.0), node(-5, offset, 0.0)),
        corridor(-12, node(-6, -offset, -offset), node(-7, offset, offset)),
        corridor(-13, node(-8, -offset, offset), node(-9, offset, -offset)),
    )
}
