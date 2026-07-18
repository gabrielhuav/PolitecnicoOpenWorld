package ovh.gabrielhuav.pow.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.map.MapNode
import ovh.gabrielhuav.pow.domain.models.map.MapWay

/**
 * Tests GOLDEN-MASTER del algoritmo de routing (RoadRouter, copia 1:1 del miembro canónico de
 * WorldMapViewModel — ver PLAN_dedup_routing.md §2.2). CONGELAN el comportamiento actual: si el
 * de-dup de la cadena de routing (Etapa 2 de CHECKPOINT_SENIOR_refactor.md) cambia algo
 * observable, estos tests se ponen rojos = regresión detectada ANTES de llegar al dispositivo.
 *
 * ⚠️ Si un test falla tras "mejorar" el algoritmo: NO ajustes el test a la ligera — el contrato
 * lo consume la navegación del juego (marcador de destino, snap-to-road, rescate de atasco).
 */
class RoadRouterTest {

    private var nextNodeId = 1L
    private fun n(lat: Double, lon: Double) = MapNode(nextNodeId++, lat, lon)
    private fun way(id: Long, vararg nodes: MapNode) =
        MapWay(id, nodes.toList(), isForCars = true, isForPeople = true)

    /** Calle recta de 5 nodos sobre el ecuador, cada 0.001° de longitud (una celda de nodo c/u). */
    private fun linearStreet(): List<MapWay> = listOf(
        way(
            1L,
            n(0.0, 0.0000), n(0.0, 0.0010), n(0.0, 0.0020), n(0.0, 0.0030), n(0.0, 0.0040)
        )
    )

    // ─── route() ────────────────────────────────────────────────────────────

    @Test
    fun route_on_empty_network_is_straight_line() {
        val router = RoadRouter()
        val from = LatLng(0.0, 0.0)
        val to = LatLng(1.0, 1.0)
        assertEquals(listOf(from, to), router.route(emptyList(), from, to))
    }

    @Test
    fun route_linear_street_visits_nodes_in_order() {
        val router = RoadRouter()
        val from = LatLng(0.0, -0.0005)
        val to = LatLng(0.0, 0.0045)
        val route = router.route(linearStreet(), from, to)
        // Empieza en from y termina en to.
        assertEquals(from, route.first())
        assertEquals(to, route.last())
        // Pasa por los nodos intermedios reales de la calle EN ORDEN (avance nodo a nodo).
        // (Se comparan NODOS exactos y no "longitudes ordenadas": el snap del extremo puede
        // diferir del nodo por un épsilon de punto flotante y eso no es una regresión.)
        val i1 = route.indexOf(LatLng(0.0, 0.0010))
        val i2 = route.indexOf(LatLng(0.0, 0.0020))
        val i3 = route.indexOf(LatLng(0.0, 0.0030))
        assertTrue(i1 in 1 until i2)
        assertTrue(i2 < i3)
    }

    @Test
    fun route_has_no_duplicate_points() {
        val router = RoadRouter()
        // from/to caen EXACTO sobre nodos → el endPoint coincide con el último nodo alcanzado
        // (el distinct del contrato elimina el duplicado).
        val from = LatLng(0.0, 0.0)
        val to = LatLng(0.0, 0.0040)
        val route = router.route(linearStreet(), from, to)
        assertEquals(route.map { "${it.lat},${it.lon}" }.distinct().size, route.size)
    }

    @Test
    fun route_in_T_network_picks_branch_toward_target() {
        val router = RoadRouter()
        // Tronco horizontal + rama vertical desde el nodo central (0, 0.002).
        val network = listOf(
            way(1L, n(0.0, 0.0000), n(0.0, 0.0010), n(0.0, 0.0020), n(0.0, 0.0030), n(0.0, 0.0040)),
            way(2L, n(0.0, 0.0020), n(0.0010, 0.0020), n(0.0020, 0.0020), n(0.0030, 0.0020))
        )
        val from = LatLng(0.0, 0.0)
        val to = LatLng(0.0030, 0.0020)   // punta de la rama vertical
        val route = router.route(network, from, to)
        assertEquals(to, route.last())
        // Sube por la rama (pasa por sus nodos intermedios)…
        assertTrue(route.contains(LatLng(0.0010, 0.0020)) || route.contains(LatLng(0.0020, 0.0020)))
        // …y NO sigue de largo por el tronco más allá de la bifurcación.
        assertTrue(!route.contains(LatLng(0.0, 0.0030)) && !route.contains(LatLng(0.0, 0.0040)))
    }

    @Test
    fun route_to_disconnected_target_still_ends_at_destination() {
        val router = RoadRouter()
        // Isla A cerca del origen; el destino queda a >MAX_HOP de cualquier nodo (sin conexión).
        val network = listOf(way(1L, n(0.0, 0.0000), n(0.0, 0.0010)))
        val from = LatLng(0.0, 0.0)
        val to = LatLng(0.0, 0.0500)
        val route = router.route(network, from, to)
        // Contrato actual: la ruta SIEMPRE cierra con [snap(to), to] aunque no haya camino
        // (el tramo sin red queda como línea recta que la UI dibuja igual).
        assertEquals(to, route.last())
        assertEquals(LatLng(0.0, 0.0010), route[route.size - 2]) // snap del destino a la red
        assertEquals(from, route.first())
    }

    @Test
    fun route_does_not_hop_between_far_disconnected_nodes() {
        val router = RoadRouter()
        // Dos islas separadas 0.01° (>> MAX_HOP_DEG=0.003): el paso voraz no debe saltar.
        val network = listOf(
            way(1L, n(0.0, 0.0000), n(0.0, 0.0010)),
            way(2L, n(0.0, 0.0100), n(0.0, 0.0110))
        )
        val from = LatLng(0.0, 0.0)
        val to = LatLng(0.0, 0.0110)
        val route = router.route(network, from, to)
        // No puede contener el nodo 0.0100 como PASO INTERMEDIO alcanzado desde la isla A
        // por el límite de salto; el cierre va directo a snap(to)=0.0110 + to.
        assertTrue(!route.contains(LatLng(0.0, 0.0100)))
        assertEquals(to, route.last())
    }

    // ─── nearestPointOnNetwork() ────────────────────────────────────────────

    @Test
    fun nearest_point_projects_onto_segment() {
        val router = RoadRouter()
        val p = router.nearestPointOnNetwork(linearStreet(), LatLng(0.0005, 0.0015))
        // El pie de la perpendicular cae sobre la calle (lat 0), misma longitud.
        assertEquals(0.0, p.lat, 1e-12)
        assertEquals(0.0015, p.lon, 1e-12)
    }

    @Test
    fun nearest_point_clamps_to_segment_end() {
        val router = RoadRouter()
        // Punto MÁS ALLÁ del final de la calle → se acota al último nodo (no extrapola).
        // Con tolerancia: el clamp t=1 se calcula como v + 1·(w−v), que puede diferir del
        // nodo por un épsilon de doble precisión.
        val p = router.nearestPointOnNetwork(linearStreet(), LatLng(0.0, 0.0100))
        assertEquals(0.0, p.lat, 1e-12)
        assertEquals(0.0040, p.lon, 1e-9)
    }

    @Test
    fun nearest_point_on_empty_network_returns_input() {
        val router = RoadRouter()
        val t = LatLng(1.0, 2.0)
        assertEquals(t, router.nearestPointOnNetwork(emptyList(), t))
    }

    // ─── buildNodeGrid() / nearbyNodes() ────────────────────────────────────

    @Test
    fun node_grid_dedups_shared_nodes_between_ways() {
        val router = RoadRouter()
        // El nodo (0, 0.001) aparece en DOS vías (intersección) → cuenta UNA vez.
        val network = listOf(
            way(1L, n(0.0, 0.0000), n(0.0, 0.0010)),
            way(2L, n(0.0, 0.0010), n(0.0010, 0.0010))
        )
        val grid = router.buildNodeGrid(network)
        val allNodes = grid.values.flatten()
        assertEquals(3, allNodes.size)
        assertEquals(1, allNodes.count { it == LatLng(0.0, 0.0010) })
    }

    @Test
    fun nearby_nodes_returns_3x3_neighborhood_only() {
        val router = RoadRouter()
        // Nodo cercano (misma celda) y nodo LEJANO (a 10 celdas): solo el cercano.
        val network = listOf(way(1L, n(0.0, 0.0000), n(0.0, 0.0100)))
        val grid = router.buildNodeGrid(network)
        val nearby = router.nearbyNodes(grid, LatLng(0.0, 0.0001))
        assertTrue(nearby.contains(LatLng(0.0, 0.0000)))
        assertTrue(!nearby.contains(LatLng(0.0, 0.0100)))
    }

    @Test
    fun nearby_nodes_falls_back_to_all_nodes_when_neighborhood_empty() {
        val router = RoadRouter()
        val network = linearStreet()
        val grid = router.buildNodeGrid(network)
        // Punto lejísimos de la red: las 9 celdas están vacías → contrato actual = TODOS los
        // nodos (así el snap del arranque nunca se queda sin candidatos).
        val nearby = router.nearbyNodes(grid, LatLng(5.0, 5.0))
        assertEquals(5, nearby.size)
    }

    @Test
    fun nearby_nodes_on_empty_grid_is_empty() {
        val router = RoadRouter()
        assertEquals(emptyList<LatLng>(), router.nearbyNodes(emptyMap(), LatLng(0.0, 0.0)))
    }

    // ─── distance() ─────────────────────────────────────────────────────────

    @Test
    fun distance_is_euclidean_in_degrees() {
        val router = RoadRouter()
        assertEquals(5.0, router.distance(LatLng(0.0, 0.0), LatLng(3.0, 4.0)), 1e-12)
    }
}
