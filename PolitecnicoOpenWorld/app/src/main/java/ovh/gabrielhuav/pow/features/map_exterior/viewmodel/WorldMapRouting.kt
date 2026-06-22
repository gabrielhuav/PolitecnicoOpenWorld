package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation

private typealias Seg = WorldMapViewModel.Seg

internal fun WorldMapViewModel.ensureIndex() {
        if (indexedRef === roadNetwork) return
        val newSegs = ArrayList<Seg>(roadNetwork.sumOf { it.nodes.size }.toInt())
        val newGrid = HashMap<Long, MutableList<Seg>>()
        for (way in roadNetwork) {
            for (i in 0 until way.nodes.size - 1) {
                val a = way.nodes[i]; val b = way.nodes[i + 1]
                val seg = Seg(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon),
                    min(a.lat, b.lat), max(a.lat, b.lat), min(a.lon, b.lon), max(a.lon, b.lon))
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

internal fun WorldMapViewModel.getNearestPointOnNetwork(t: GeoPoint): GeoPoint {
        // SINCRONIZADO con la versión que vivía como miembro en WorldMapViewModel (que era
        // la canónica): dentro de un landmark (rectángulo rotado) el movimiento es LIBRE.
        // Esta extensión es ahora la ÚNICA implementación (el miembro duplicado se eliminó).
        val insideLandmark = _uiState.value.landmarks.any { landmark ->
            landmark.contains(t)
        }
        if (insideLandmark) {
            return t // Eres 100% libre solo si estás tocando un píxel de la imagen
        }

        ensureIndex()
        val cands = candidates(t); if (cands.isEmpty()) return t
        var best = Double.MAX_VALUE; var pt = t
        for (seg in cands) {
            val p = project(t, seg.s, seg.e); val d = distance(t, p)
            if (d < best) { best = d; pt = p }
        }
        return pt
    }

// ─── ÍNDICE PARA VÍAS DE AUTOMÓVIL ─────────────────────────────────────────────
// Duplicado del índice espacial genérico, pero filtrando SOLO las vías donde
// isForCars == true. Así el coche no puede circular por banquetas, ciclovías ni
// andadores peatonales.

internal fun WorldMapViewModel.ensureIndexForCars() {
    if (indexedRefForCars === roadNetwork) return
    val carRoads = roadNetwork.filter { it.isForCars }
    val newSegs = ArrayList<Seg>(carRoads.sumOf { it.nodes.size }.toInt())
    val newGrid = HashMap<Long, MutableList<Seg>>()
    for (way in carRoads) {
        for (i in 0 until way.nodes.size - 1) {
            val a = way.nodes[i]; val b = way.nodes[i + 1]
            val seg = Seg(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon),
                min(a.lat, b.lat), max(a.lat, b.lat), min(a.lon, b.lon), max(a.lon, b.lon))
            newSegs.add(seg)
            for (r in cell(seg.minLat)..cell(seg.maxLat))
                for (c in cell(seg.minLon)..cell(seg.maxLon))
                    newGrid.getOrPut(pack(r, c)) { mutableListOf() }.add(seg)
        }
    }
    indexedRefForCars = roadNetwork; segsForCars = newSegs; gridForCars = newGrid
}

internal fun WorldMapViewModel.candidatesForCars(loc: GeoPoint): List<Seg> {
    val r = cell(loc.latitude); val c = cell(loc.longitude)
    val res = LinkedHashSet<Seg>()
    for (dr in -1..1) for (dc in -1..1) gridForCars[pack(r + dr, c + dc)]?.let { res.addAll(it) }
    return if (res.isNotEmpty()) res.toList() else segsForCars
}

// Versión para automóviles de getNearestPointOnNetwork.
// A diferencia de la versión peatonal, NO otorga paso libre sobre landmarks
// (el coche no debe atravesar edificios) y solo considera vías con isForCars == true.
internal fun WorldMapViewModel.getNearestCarRoadPoint(t: GeoPoint): GeoPoint {
    ensureIndexForCars()
    val cands = candidatesForCars(t)
    if (cands.isEmpty()) return t
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

internal fun WorldMapViewModel.updateDestinationRoute() {
        val destination = _uiState.value.destinationMarker ?: return
        val currentLoc = _uiState.value.currentLocation ?: return
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
                    _uiState.update { it.copy(routeWaypoints = if (route.isNotEmpty()) route else listOf(currentLoc, destination)) }
                    val distToDestinationMeters = currentLoc.distanceToAsDouble(destination)
                    if (distToDestinationMeters <= _uiState.value.destinationArrivalThreshold) clearDestinationMarker()
                }
            } catch (e: Exception) { Log.e("Navigation", "Error calculando ruta: ${e.message}") }
            finally { routeCalculationJob = null }
        }
    }

internal fun WorldMapViewModel.calculateRouteOnNetwork(from: GeoPoint, to: GeoPoint, network: List<MapWay>): List<GeoPoint> {
        if (network.isEmpty()) return listOf(from, to)
        val route = mutableListOf<GeoPoint>()
        route.add(from)
        val startPoint = getNearestPointOnNetwork(from)
        val endPoint = getNearestPointOnNetwork(to)
        var current = startPoint
        // Pair<lat, lon> en lugar de String concatenado: evita allocs de String y
        // presión de GC en cada paso del routing.
        val visitedNodes = mutableSetOf<Pair<Double, Double>>()
        val maxSteps = 20
        for (step in 0 until maxSteps) {
            val distToTarget = distance(current, endPoint)
            if (distToTarget < 0.0005) break
            var bestNext: GeoPoint? = null
            var bestDist = distToTarget
            val candidateNodes = nearbyRoadNodes(current)
            for (nodePt in candidateNodes) {
                val nodeKey = nodePt.latitude to nodePt.longitude
                if (visitedNodes.contains(nodeKey)) continue
                val dFromCurrent = distance(current, nodePt)
                if (dFromCurrent < 0.003) {
                    val dToTarget = distance(nodePt, endPoint)
                    if (dToTarget < bestDist) {
                        bestDist = dToTarget
                        bestNext = nodePt
                    }
                }
            }
            if (bestNext != null) {
                current = bestNext
                visitedNodes.add(current.latitude to current.longitude)
                route.add(current)
            } else break
        }
        route.add(endPoint)
        route.add(to)
        return route.distinctBy { it.latitude to it.longitude }
    }

// ─── GRAFO DE CALLES + A* (pathfinding de la policía) ───────────────────────────
// Construye, a partir de la red, la adyacencia por id de nodo (dos nodos consecutivos
// de una misma vía quedan conectados; las intersecciones comparten id de nodo en OSM,
// así que el grafo queda conectado), las posiciones por id y una rejilla id→celda para
// localizar el nodo más cercano a un punto. Se llama al cargar/recargar la red.
internal fun WorldMapViewModel.buildRoadGraph(network: List<MapWay>) {
    val adj = HashMap<Long, MutableSet<Long>>()
    val pos = HashMap<Long, GeoPoint>()
    val grid = HashMap<Pair<Int, Int>, MutableList<Long>>()
    for (way in network) {
        val ns = way.nodes
        for (i in ns.indices) {
            val n = ns[i]
            if (n.id !in pos) {
                pos[n.id] = GeoPoint(n.lat, n.lon)
                val cell = floor(n.lat / ROAD_NODE_GRID_SIZE_DEG).toInt() to floor(n.lon / ROAD_NODE_GRID_SIZE_DEG).toInt()
                grid.getOrPut(cell) { mutableListOf() }.add(n.id)
            }
            if (i > 0) {
                val p = ns[i - 1]
                adj.getOrPut(p.id) { mutableSetOf() }.add(n.id)
                adj.getOrPut(n.id) { mutableSetOf() }.add(p.id)
            }
        }
    }
    roadAdjacency = adj.mapValues { it.value.toList() }
    roadNodePos = pos
    roadNodeGridById = grid.mapValues { it.value.toList() }
}

private fun WorldMapViewModel.nearestGraphNode(p: GeoPoint): Long? {
    if (roadNodeGridById.isEmpty()) return null
    val latCell = floor(p.latitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
    val lonCell = floor(p.longitude / ROAD_NODE_GRID_SIZE_DEG).toInt()
    var best: Long? = null
    var bestD = Double.MAX_VALUE
    // Expande el anillo de celdas hasta encontrar candidatos (red dispersa incluida).
    for (ring in 0..6) {
        for (dLat in -ring..ring) for (dLon in -ring..ring) {
            if (ring > 0 && kotlin.math.abs(dLat) != ring && kotlin.math.abs(dLon) != ring) continue
            roadNodeGridById[(latCell + dLat) to (lonCell + dLon)]?.forEach { id ->
                val np = roadNodePos[id] ?: return@forEach
                val d = distance(p, np)
                if (d < bestD) { bestD = d; best = id }
            }
        }
        if (best != null) return best
    }
    return best
}

// A* sobre el grafo de calles. Garantiza una ruta CONECTADA POR CALLES de 'from' a 'to'
// (si existe). Si no hay conexión o no hay grafo, cae a la línea recta como último recurso.
internal fun WorldMapViewModel.findRoadRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
    val start = nearestGraphNode(from) ?: return listOf(from, to)
    val goal = nearestGraphNode(to) ?: return listOf(from, to)
    if (start == goal) return listOf(from, roadNodePos[goal] ?: to, to)

    val goalPos = roadNodePos[goal]!!
    val gScore = HashMap<Long, Double>().apply { put(start, 0.0) }
    val cameFrom = HashMap<Long, Long>()
    val closed = HashSet<Long>()
    // Cola por fScore = gScore + heurística (distancia euclídea al goal).
    val open = java.util.PriorityQueue<Pair<Long, Double>>(compareBy { it.second })
    open.add(start to distance(roadNodePos[start]!!, goalPos))

    var expansions = 0
    val maxExpansions = 6000
    var found = false
    while (open.isNotEmpty()) {
        val (cur, _) = open.poll()
        if (cur == goal) { found = true; break }
        if (!closed.add(cur)) continue
        if (++expansions > maxExpansions) break
        val curPos = roadNodePos[cur] ?: continue
        val curG = gScore[cur] ?: continue
        for (nb in roadAdjacency[cur] ?: emptyList()) {
            if (nb in closed) continue
            val nbPos = roadNodePos[nb] ?: continue
            val tentative = curG + distance(curPos, nbPos)
            if (tentative < (gScore[nb] ?: Double.MAX_VALUE)) {
                gScore[nb] = tentative
                cameFrom[nb] = cur
                open.add(nb to tentative + distance(nbPos, goalPos))
            }
        }
    }
    if (!found && cameFrom[goal] == null) return listOf(from, to) // sin camino → recurso final

    // Reconstrucción.
    val path = ArrayList<GeoPoint>()
    var node: Long? = goal
    while (node != null) {
        roadNodePos[node]?.let { path.add(it) }
        node = cameFrom[node]
    }
    path.reverse()
    val out = ArrayList<GeoPoint>(path.size + 2)
    out.add(from); out.addAll(path); out.add(to)
    return out
}

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
