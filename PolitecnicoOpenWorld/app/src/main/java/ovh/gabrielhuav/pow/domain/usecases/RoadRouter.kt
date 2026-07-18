package ovh.gabrielhuav.pow.domain.usecases

import ovh.gabrielhuav.pow.domain.models.map.MapWay
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// ROUTING PURO (sin Android/osmdroid) — extracción del algoritmo canónico de navegación.
//
// ORIGEN (golden master): los cuerpos se COPIARON 1:1 del MIEMBRO vivo de
// `WorldMapViewModel.kt` (`calculateRouteOnNetwork`/`rebuildRoadNodeGrid`/`nearbyRoadNodes`)
// y de la extensión ÚNICA `WorldMapRouting.getNearestPointOnNetwork` (solo su parte
// GEOMÉTRICA: el pase-libre por landmarks es estado del VM y se queda en el wrapper del VM).
// Ver `PLAN_dedup_routing.md` §2.1: esta clase existe para (a) poder TESTEAR el algoritmo en
// JVM puro y (b) ser el destino del de-dup de gemelos (§3), donde el VM pasará a delegar aquí.
//
// ⚠️ REGLA: mientras el de-dup (Etapa 2 de CHECKPOINT_SENIOR_refactor.md) no termine, este
// archivo debe permanecer IDÉNTICO en comportamiento al miembro. NO "mejorar" el algoritmo
// aquí: primero de-dup con tests en verde; optimizaciones después, como cambio separado.
// ─────────────────────────────────────────────────────────────────────────────

/** Punto geográfico puro (equivalente a GeoPoint pero sin depender de osmdroid). */
data class LatLng(val lat: Double, val lon: Double)

/** Rejilla espacial de NODOS de la red vial: celda (latCell, lonCell) → nodos dentro. */
typealias NodeGrid = Map<Pair<Int, Int>, List<LatLng>>

class RoadRouter {

    companion object {
        // Valores COPIADOS del VM (no cambiar sin re-calibrar los tests golden-master):
        /** Tamaño de celda de la rejilla de SEGMENTOS (grados) — `WorldMapViewModel.CELL`. */
        const val SEG_CELL_DEG = 0.0025
        /** Tamaño de celda de la rejilla de NODOS — `ROAD_NODE_GRID_SIZE_DEG`. */
        const val NODE_GRID_SIZE_DEG = 0.001
        /** Umbral de llegada al punto final durante la búsqueda (grados ≈ 55 m). */
        const val ARRIVE_EPS_DEG = 0.0005
        /** Salto máximo entre nodos consecutivos de la ruta (grados ≈ 330 m). */
        const val MAX_HOP_DEG = 0.003
        /** Tope de pasos de la búsqueda voraz. */
        const val MAX_STEPS = 20
    }

    // Segmento indexado con su bounding box (copia de `WorldMapViewModel.Seg`).
    private data class Seg(
        val s: LatLng, val e: LatLng,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    // ── Cachés por IDENTIDAD de la red (mismo contrato que `ensureIndex`/`indexedRef` del VM:
    //    se reconstruyen solo cuando cambia la REFERENCIA de la lista) ──
    private var indexedRef: List<MapWay>? = null
    private var segs: List<Seg> = emptyList()
    private var grid: Map<Long, List<Seg>> = emptyMap()
    private var nodeGridRef: List<MapWay>? = null
    private var nodeGrid: NodeGrid = emptyMap()

    // ── Helpers geométricos (copias de pack/cell/distance/project del VM) ──
    private fun pack(r: Int, c: Int): Long = r.toLong() * 1_000_003L + c.toLong()
    private fun cell(v: Double): Int = floor(v / SEG_CELL_DEG).toInt()

    /** Distancia euclidiana simple en GRADOS (así la usa todo el juego a escala local). */
    fun distance(a: LatLng, b: LatLng): Double =
        sqrt((a.lat - b.lat).pow(2) + (a.lon - b.lon).pow(2))

    // Proyección de p sobre el segmento [v,w], acotada a los extremos.
    private fun project(p: LatLng, v: LatLng, w: LatLng): LatLng {
        val l2 = (w.lat - v.lat).pow(2) + (w.lon - v.lon).pow(2)
        if (l2 == 0.0) return v
        val t = max(0.0, min(1.0, ((p.lat - v.lat) * (w.lat - v.lat) +
                (p.lon - v.lon) * (w.lon - v.lon)) / l2))
        return LatLng(v.lat + t * (w.lat - v.lat), v.lon + t * (w.lon - v.lon))
    }

    // ── Índice de segmentos (copia de `ensureIndex`) ──
    private fun ensureIndex(network: List<MapWay>) {
        if (indexedRef === network) return
        val newSegs = ArrayList<Seg>(network.sumOf { it.nodes.size })
        val newGrid = HashMap<Long, MutableList<Seg>>()
        for (way in network) {
            for (i in 0 until way.nodes.size - 1) {
                val a = way.nodes[i]; val b = way.nodes[i + 1]
                val seg = Seg(
                    LatLng(a.lat, a.lon), LatLng(b.lat, b.lon),
                    min(a.lat, b.lat), max(a.lat, b.lat), min(a.lon, b.lon), max(a.lon, b.lon)
                )
                newSegs.add(seg)
                for (r in cell(seg.minLat)..cell(seg.maxLat))
                    for (c in cell(seg.minLon)..cell(seg.maxLon))
                        newGrid.getOrPut(pack(r, c)) { mutableListOf() }.add(seg)
            }
        }
        indexedRef = network; segs = newSegs; grid = newGrid
    }

    // Candidatos = segmentos de las celdas 3×3 alrededor; si no hay, TODOS (copia de `candidates`).
    private fun candidates(loc: LatLng): List<Seg> {
        val r = cell(loc.lat); val c = cell(loc.lon)
        val res = LinkedHashSet<Seg>()
        for (dr in -1..1) for (dc in -1..1) grid[pack(r + dr, c + dc)]?.let { res.addAll(it) }
        return if (res.isNotEmpty()) res.toList() else segs
    }

    /**
     * Punto de la red MÁS CERCANO a `t` (snap-to-road geométrico). Copia de la parte geométrica
     * de `WorldMapRouting.getNearestPointOnNetwork`. ⚠️ SIN el pase-libre por landmarks (eso es
     * estado del VM: su wrapper lo comprueba ANTES y delega aquí). Red vacía → devuelve `t`.
     */
    fun nearestPointOnNetwork(network: List<MapWay>, t: LatLng): LatLng {
        ensureIndex(network)
        val cands = candidates(t); if (cands.isEmpty()) return t
        var best = Double.MAX_VALUE; var pt = t
        for (seg in cands) {
            val p = project(t, seg.s, seg.e); val d = distance(t, p)
            if (d < best) { best = d; pt = p }
        }
        return pt
    }

    /** Rejilla de NODOS únicos de la red (copia de `rebuildRoadNodeGrid`). */
    fun buildNodeGrid(network: List<MapWay>): NodeGrid {
        val uniqueNodes = linkedMapOf<String, LatLng>()
        network.forEach { way ->
            way.nodes.forEach { node ->
                val key = "${node.lat},${node.lon}"
                if (!uniqueNodes.containsKey(key)) uniqueNodes[key] = LatLng(node.lat, node.lon)
            }
        }
        return uniqueNodes.values.groupBy { point ->
            val latCell = floor(point.lat / NODE_GRID_SIZE_DEG).toInt()
            val lonCell = floor(point.lon / NODE_GRID_SIZE_DEG).toInt()
            latCell to lonCell
        }
    }

    /**
     * Nodos cercanos a `point` (celdas 3×3 de la rejilla de nodos). Copia de `nearbyRoadNodes`,
     * incluido el FALLBACK: si las 9 celdas están vacías devuelve TODOS los nodos (contrato del
     * miembro; los tests lo fijan). Rejilla vacía → lista vacía.
     */
    fun nearbyNodes(grid: NodeGrid, point: LatLng): List<LatLng> {
        if (grid.isEmpty()) return emptyList()
        val latCell = floor(point.lat / NODE_GRID_SIZE_DEG).toInt()
        val lonCell = floor(point.lon / NODE_GRID_SIZE_DEG).toInt()
        val nearby = mutableListOf<LatLng>()
        for (latOffset in -1..1) {
            for (lonOffset in -1..1) {
                grid[(latCell + latOffset) to (lonCell + lonOffset)]?.let { nearby.addAll(it) }
            }
        }
        if (nearby.isNotEmpty()) return nearby
        return grid.values.flatten()
    }

    /**
     * Ruta de `from` a `to` sobre la red (búsqueda VORAZ nodo-a-nodo, copia 1:1 de
     * `calculateRouteOnNetwork` del VM). Contratos que fijan los tests:
     * - red vacía → `[from, to]` (línea recta);
     * - la ruta SIEMPRE empieza en `from` y termina en `[puntoRedMásCercanoAlDestino, to]`,
     *   aunque no exista conexión (el tramo sin red queda como línea recta);
     * - sin duplicados consecutivos (distinct por "lat,lon").
     * La rejilla de nodos se cachea por identidad de `network` (mismo contrato que el VM, que
     * la reconstruye al aplicar una red nueva).
     */
    fun route(network: List<MapWay>, from: LatLng, to: LatLng): List<LatLng> {
        if (network.isEmpty()) return listOf(from, to)
        if (nodeGridRef !== network) {
            nodeGrid = buildNodeGrid(network)
            nodeGridRef = network
        }
        val route = mutableListOf<LatLng>()
        route.add(from)
        val startPoint = nearestPointOnNetwork(network, from)
        val endPoint = nearestPointOnNetwork(network, to)
        var current = startPoint
        // Micro-opt heredada de la extensión retirada (2026-07-04): LatLng (data class) como key
        // directa en vez de Strings concatenados — cero allocs de String por paso, mismo
        // comportamiento (igualdad estructural lat/lon). Los tests golden-master siguen en verde.
        val visitedNodes = mutableSetOf<LatLng>()
        for (step in 0 until MAX_STEPS) {
            val distToTarget = distance(current, endPoint)
            if (distToTarget < ARRIVE_EPS_DEG) break
            var bestNext: LatLng? = null
            var bestDist = distToTarget
            val candidateNodes = nearbyNodes(nodeGrid, current)
            for (nodePt in candidateNodes) {
                if (visitedNodes.contains(nodePt)) continue
                val dFromCurrent = distance(current, nodePt)
                if (dFromCurrent < MAX_HOP_DEG) {
                    val dToTarget = distance(nodePt, endPoint)
                    if (dToTarget < bestDist) {
                        bestDist = dToTarget
                        bestNext = nodePt
                    }
                }
            }
            if (bestNext != null) {
                current = bestNext
                visitedNodes.add(current)
                route.add(current)
            } else break
        }
        route.add(endPoint)
        route.add(to)
        return route.distinct()
    }
}
