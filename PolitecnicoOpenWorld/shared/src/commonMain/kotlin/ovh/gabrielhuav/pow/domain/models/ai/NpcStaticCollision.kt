package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CircleCollider
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.isInsideBarrier
import ovh.gabrielhuav.pow.domain.models.map.resolveCirclePath
import ovh.gabrielhuav.pow.domain.models.map.resolveNpcPath

// Colisión ESTÁTICA de los NPC que siguen el navGraph local del campus (`moveLocalNpc`): peatones, gatos y
// coches que salen del estacionamiento. Esas vías se dibujaron sobre el mapa sin conocer los edificios de OSM
// (en el ESCOM ~126 m de las vías peatonales y 14 de 158 nodos caen DENTRO de edificios), y `moveLocalNpc` no
// llamaba al resolvedor: el 46 % de las observaciones de NPC caían dentro de un edificio. Ver 04_MAP_EXTERIOR.md.

private const val LOCAL_NPC_RADIUS_METERS = 0.4

/** ¿El tramo a→b está bloqueado? Cuenta como bloqueado si un extremo cae DENTRO de una barrera. */
internal fun ExteriorCollisionsConfig.segmentBlocked(a: GeoPoint, b: GeoPoint, radiusMeters: Double = LOCAL_NPC_RADIUS_METERS): Boolean =
    isInsideBarrier(a) || isInsideBarrier(b) || !resolveCirclePath(a, b, CircleCollider(radiusMeters)).reachedTarget

/** ¿El nodo local cae dentro de una barrera (edificio)? */
internal fun ExteriorCollisionsConfig.localNodeBlocked(landmark: Landmark, node: LocalNode): Boolean =
    isInsideBarrier(landmark.toGlobalGeoPoint(node.localX, node.localY))

/** ¿Algún nodo o tramo de la vía local toca una barrera? Las vías bloqueadas no se usan para caminar. */
internal fun ExteriorCollisionsConfig.localWayBlocked(landmark: Landmark, way: LocalWay): Boolean {
    var previous: GeoPoint? = null
    for (node in way.nodes) {
        val point = landmark.toGlobalGeoPoint(node.localX, node.localY)
        if (isInsideBarrier(point)) return true
        val from = previous
        if (from != null && segmentBlocked(from, point)) return true
        previous = point
    }
    return false
}

/** Las vías de [ways] que no tocan ningún edificio; sin colisiones cargadas devuelve todas. */
internal fun NpcAiManager.walkableLocalWays(landmark: Landmark, ways: List<LocalWay>): List<LocalWay> {
    val collisions = exteriorCollisions ?: return ways
    return ways.filter { !collisions.localWayBlocked(landmark, it) }
}

/** ¿El nodo es un buen sitio para aparecer (fuera de edificios)? */
internal fun NpcAiManager.localNodeFree(landmark: Landmark, node: LocalNode): Boolean =
    exteriorCollisions?.localNodeBlocked(landmark, node) != true

/**
 * Aplica la colisión estática (edificios y bardas) al resultado de un movimiento directo en línea recta
 * (perseguir, huir, esquivar): si el paso de [from] a [moved] cruzaría una barrera, se queda en el último punto
 * seguro. Los movimientos por vías ya pasan por `resolveNpcPath`; estos (aggro, zombis, cazadores y esquiva)
 * iban "en beeline" y atravesaban los edificios.
 */
internal fun NpcAiManager.collided(from: Npc, moved: Npc): Npc {
    val collisions = exteriorCollisions ?: return moved
    if (moved.location == from.location) return moved
    val resolution = collisions.resolveNpcPath(
        type = moved.type,
        carModel = moved.carModel,
        from = from.location,
        to = moved.location,
        headingDegrees = (moved.rotationAngle + 90f) % 360f,
    )
    return if (resolution.reachedTarget) moved else moved.copy(location = resolution.position)
}
