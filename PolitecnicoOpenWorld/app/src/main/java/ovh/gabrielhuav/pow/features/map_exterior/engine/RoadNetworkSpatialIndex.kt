package ovh.gabrielhuav.pow.features.map_exterior.engine

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MapWay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class RoadNetworkSpatialIndex {

    private data class Seg(
        val s: GeoPoint, val e: GeoPoint,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    private val CELL = 0.0025
    private var indexedRef: List<MapWay>? = null
    private var segs: List<Seg> = emptyList()
    private var grid: Map<Long, List<Seg>> = emptyMap()

    private fun ensureIndex(roadNetwork: List<MapWay>) {
        if (indexedRef === roadNetwork) return
        val newSegs = ArrayList<Seg>(roadNetwork.sumOf { it.nodes.size })
        val newGrid = HashMap<Long, MutableList<Seg>>()
        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val a = way.nodes[i]; val b = way.nodes[i + 1]
                val seg = Seg(
                    GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon),
                    min(a.lat, b.lat), max(a.lat, b.lat),
                    min(a.lon, b.lon), max(a.lon, b.lon)
                )
                newSegs.add(seg)
                for (r in cell(seg.minLat)..cell(seg.maxLat))
                    for (c in cell(seg.minLon)..cell(seg.maxLon))
                        newGrid.getOrPut(pack(r, c)) { mutableListOf() }.add(seg)
            }
        }
        indexedRef = roadNetwork; segs = newSegs; grid = newGrid
    }

    private fun candidates(loc: GeoPoint): List<Seg> {
        val r = cell(loc.latitude); val c = cell(loc.longitude)
        val res = LinkedHashSet<Seg>()
        for (dr in -1..1) for (dc in -1..1) grid[pack(r + dr, c + dc)]?.let { res.addAll(it) }
        return if (res.isNotEmpty()) res.toList() else segs
    }

    private fun pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
    private fun cell(v: Double): Int = floor(v / CELL).toInt()

    fun nearestPoint(t: GeoPoint, roadNetwork: List<MapWay>): GeoPoint {
        ensureIndex(roadNetwork)
        val cands = candidates(t)
        if (cands.isEmpty()) return t
        var best = Double.MAX_VALUE
        var pt = t
        for (seg in cands) {
            val p = project(t, seg.s, seg.e)
            val d = distance(t, p)
            if (d < best) { best = d; pt = p }
        }
        return pt
    }

    fun project(p: GeoPoint, v: GeoPoint, w: GeoPoint): GeoPoint {
        val l2 = (w.latitude - v.latitude).pow(2) + (w.longitude - v.longitude).pow(2)
        if (l2 == 0.0) return v
        val t = max(0.0, min(1.0,
            ((p.latitude - v.latitude) * (w.latitude - v.latitude) +
             (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2
        ))
        return GeoPoint(
            v.latitude + t * (w.latitude - v.latitude),
            v.longitude + t * (w.longitude - v.longitude)
        )
    }

    fun distance(a: GeoPoint, b: GeoPoint): Double =
        sqrt((a.latitude - b.latitude).pow(2) + (a.longitude - b.longitude).pow(2))
}
