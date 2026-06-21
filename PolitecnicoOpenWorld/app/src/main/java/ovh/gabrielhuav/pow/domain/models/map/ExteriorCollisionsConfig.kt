package ovh.gabrielhuav.pow.domain.models.map

data class ExteriorCollisionsConfig(
    val polygons: List<CollisionPolygon> = emptyList(),
    val walls: List<CollisionWall> = emptyList()
)

data class CollisionPolygon(
    val name: String,
    val nodes: List<GeoNode>
) {
    // 🧠 ALGORITMO RAY-CASTING: Verifica si una coordenada está DENTRO del polígono
    fun contains(lat: Double, lon: Double): Boolean {
        var isInside = false
        var j = nodes.size - 1
        for (i in nodes.indices) {
            val xi = nodes[i].lon
            val yi = nodes[i].lat
            val xj = nodes[j].lon
            val yj = nodes[j].lat

            val intersect = ((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            if (intersect) isInside = !isInside
            j = i
        }
        return isInside
    }
}

data class CollisionWall(
    val name: String,
    val lat1: Double, val lon1: Double,
    val lat2: Double, val lon2: Double
) {
    // 🧠 ALGORITMO INTERSECCIÓN: Verifica si el paso que dio la entidad "cruza" la barda
    fun didHitWall(oldLat: Double, oldLon: Double, newLat: Double, newLon: Double): Boolean {
        val s1x = newLon - oldLon
        val s1y = newLat - oldLat
        val s2x = lon2 - lon1
        val s2y = lat2 - lat1

        val denominator = (-s2x * s1y + s1x * s2y)
        if (denominator == 0.0) return false // Son líneas paralelas

        val s = (-s1y * (oldLon - lon1) + s1x * (oldLat - lat1)) / denominator
        val t = ( s2x * (oldLat - lat1) - s2y * (oldLon - lon1)) / denominator

        // Si S y T están entre 0 y 1, significa que los vectores chocaron
        return s in 0.0..1.0 && t in 0.0..1.0
    }
}

data class GeoNode(val lat: Double, val lon: Double)