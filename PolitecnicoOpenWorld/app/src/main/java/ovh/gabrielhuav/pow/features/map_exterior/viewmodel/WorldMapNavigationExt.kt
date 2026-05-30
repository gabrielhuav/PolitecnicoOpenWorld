package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MapWay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos

// ─── Segmento de carretera para el índice espacial ───────────────────────────

internal data class Seg(
    val s: GeoPoint, val e: GeoPoint,
    val minLat: Double, val maxLat: Double,
    val minLon: Double, val maxLon: Double
)

// ─── Índice espacial (snap-to-road) ──────────────────────────────────────────

internal fun WorldMapViewModel.ensureIndex() {
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

internal fun WorldMapViewModel.candidates(loc: GeoPoint): List<Seg> {
    val r = cell(loc.latitude); val c = cell(loc.longitude)
    val res = LinkedHashSet<Seg>()
    for (dr in -1..1) for (dc in -1..1) grid[pack(r + dr, c + dc)]?.let { res.addAll(it) }
    return if (res.isNotEmpty()) res.toList() else segs
}

internal fun WorldMapViewModel.pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
internal fun WorldMapViewModel.cell(v: Double): Int = floor(v / CELL).toInt()

internal fun WorldMapViewModel.getNearestPointOnNetwork(t: GeoPoint): GeoPoint {
    ensureIndex()
    val cands = candidates(t); if (cands.isEmpty()) return t
    var best = Double.MAX_VALUE; var pt = t
    for (seg in cands) {
        val p = project(t, seg.s, seg.e); val d = distance(t, p)
        if (d < best) { best = d; pt = p }
    }
    return pt
}

internal fun WorldMapViewModel.project(p: GeoPoint, v: GeoPoint, w: GeoPoint): GeoPoint {
    val l2 = (w.latitude - v.latitude).pow(2) + (w.longitude - v.longitude).pow(2)
    if (l2 == 0.0) return v
    val t = max(0.0, min(1.0, ((p.latitude - v.latitude) * (w.latitude - v.latitude) +
            (p.longitude - v.longitude) * (w.longitude - v.longitude)) / l2))
    return GeoPoint(v.latitude + t * (w.latitude - v.latitude),
        v.longitude + t * (w.longitude - v.longitude))
}

internal fun WorldMapViewModel.distance(a: GeoPoint, b: GeoPoint): Double =
    sqrt((a.latitude - b.latitude).pow(2) + (a.longitude - b.longitude).pow(2))

// ─── Grid de nodos de la red de carreteras ───────────────────────────────────

internal fun WorldMapViewModel.rebuildRoadNodeGrid(network: List<MapWay>) {
    val uniqueNodes = linkedMapOf<String, GeoPoint>()
    network.forEach { way ->
        way.nodes.forEach { node ->
            val key = "${node.lat},${node.lon}"
            if (!uniqueNodes.containsKey(key)) uniqueNodes[key] = GeoPoint(node.lat, node.lon)
        }
    }
    roadNetworkNodeGrid = uniqueNodes.values.groupBy { point ->
        val latCell = floor(point.latitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
        val lonCell = floor(point.longitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
        latCell to lonCell
    }
}

internal fun WorldMapViewModel.nearbyRoadNodes(point: GeoPoint): List<GeoPoint> {
    if (roadNetworkNodeGrid.isEmpty()) return emptyList()
    val latCell = floor(point.latitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
    val lonCell = floor(point.longitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
    val nearby = mutableListOf<GeoPoint>()
    for (latOffset in -1..1) {
        for (lonOffset in -1..1) {
            roadNetworkNodeGrid[(latCell + latOffset) to (lonCell + lonOffset)]?.let { nearby.addAll(it) }
        }
    }
    if (nearby.isNotEmpty()) return nearby
    return roadNetworkNodeGrid.values.flatten()
}

// ─── Cálculo de ruta greedy sobre el grafo de calles ─────────────────────────

internal fun WorldMapViewModel.calculateRouteOnNetwork(
    from: GeoPoint, to: GeoPoint, network: List<MapWay>
): List<GeoPoint> {
    if (network.isEmpty()) return listOf(from, to)
    val route = mutableListOf<GeoPoint>()
    route.add(from)
    val startPoint = getNearestPointOnNetwork(from)
    val endPoint   = getNearestPointOnNetwork(to)
    var current    = startPoint
    val visitedNodes = mutableSetOf<String>()
    val maxSteps = 20
    for (step in 0 until maxSteps) {
        val distToTarget = distance(current, endPoint)
        if (distToTarget < 0.0005) break
        var bestNext: GeoPoint? = null
        var bestDist = distToTarget
        val candidateNodes = nearbyRoadNodes(current)
        for (nodePt in candidateNodes) {
            val nodeKey = "${nodePt.latitude},${nodePt.longitude}"
            if (visitedNodes.contains(nodeKey)) continue
            val dFromCurrent = distance(current, nodePt)
            if (dFromCurrent < 0.003) {
                val dToTarget = distance(nodePt, endPoint)
                if (dToTarget < bestDist) { bestDist = dToTarget; bestNext = nodePt }
            }
        }
        if (bestNext != null) {
            current = bestNext
            visitedNodes.add("${current.latitude},${current.longitude}")
            route.add(current)
        } else break
    }
    route.add(endPoint)
    route.add(to)
    return route.distinctBy { "${it.latitude},${it.longitude}" }
}

// ─── Waypoints y marcador de destino ─────────────────────────────────────────

fun WorldMapViewModel.toggleWaypointTargeting(active: Boolean) {
    _uiState.update { it.copy(isTargetingWaypoint = active) }
}

fun WorldMapViewModel.placeDestinationMarker(latitude: Double, longitude: Double) {
    Log.d("Navigation", "Colocando waypoint en: $latitude, $longitude")
    routeRetryJob?.cancel()
    routeCalculationJob?.cancel()
    val newDestination = GeoPoint(latitude, longitude)
    _uiState.update {
        it.copy(destinationMarker = newDestination, isTargetingWaypoint = false, routeWaypoints = emptyList())
    }
    updateDestinationRoute()
}

fun WorldMapViewModel.clearDestinationMarker() {
    routeRetryJob?.cancel()
    routeCalculationJob?.cancel()
    _uiState.update {
        it.copy(destinationMarker = null, isTargetingWaypoint = false, routeWaypoints = emptyList())
    }
}

fun WorldMapViewModel.toggleDestinationRoute(show: Boolean) {
    _uiState.update { it.copy(showDestinationRoute = show) }
}

internal fun WorldMapViewModel.updateDestinationRoute() {
    val destination = _uiState.value.destinationMarker ?: return
    val currentLoc  = _uiState.value.currentLocation   ?: return
    if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) {
        if (routeRetryJob?.isActive == true) return
        routeRetryJob = viewModelScope.launch {
            delay(1000)
            routeRetryJob = null
            if (_uiState.value.destinationMarker != null) updateDestinationRoute()
        }
        return
    }
    if (routeCalculationJob?.isActive == true) return
    routeRetryJob?.cancel()
    routeRetryJob = null
    routeCalculationJob = viewModelScope.launch(Dispatchers.Default) {
        try {
            Log.d("Navigation", "Calculando ruta...")
            val route = calculateRouteOnNetwork(currentLoc, destination, roadNetwork)
            Log.d("Navigation", "Ruta calculada con ${route.size} puntos")
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(routeWaypoints = if (route.isNotEmpty()) route else listOf(currentLoc, destination))
                }
                val distToDestinationMeters = currentLoc.distanceToAsDouble(destination)
                if (distToDestinationMeters <= _uiState.value.destinationArrivalThreshold) clearDestinationMarker()
            }
        } catch (e: Exception) {
            Log.e("Navigation", "Error calculando ruta: ${e.message}")
        } finally {
            routeCalculationJob = null
        }
    }
}

fun WorldMapViewModel.checkDestinationArrival() {
    val destination = _uiState.value.destinationMarker ?: return
    val currentLoc  = _uiState.value.currentLocation   ?: return
    val distToDestinationMeters = currentLoc.distanceToAsDouble(destination)
    if (distToDestinationMeters <= _uiState.value.destinationArrivalThreshold) clearDestinationMarker()
}
