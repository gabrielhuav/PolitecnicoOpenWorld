package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.VehicleDimensions
import ovh.gabrielhuav.pow.domain.models.map.VehicleFootprint
import ovh.gabrielhuav.pow.domain.models.map.distanceMetersTo

/** QA PR #2: el carjack (salida forzada) SIEMPRE devuelve un punto. */
class VehicleExitPlanQaTest {
    private val a = GeoPoint(19.5001, -99.1001)
    private val b = GeoPoint(19.5002, -99.1002)
    private val c = GeoPoint(19.5003, -99.1003)
    private val candidates = listOf(a, b, c)

    @Test fun prefierePuntoCaminable() {
        assertEquals(c, selectForcedVehicleExit(candidates, { it == c }, { it == b }))
    }

    @Test fun sinCaminableUsaElPrimeroFueraDelCoche() {
        assertEquals(b, selectForcedVehicleExit(candidates, { false }, { it == b || it == c }))
    }

    @Test fun sinNingunoDevuelveElPrimero() {
        assertEquals(a, selectForcedVehicleExit(candidates, { false }, { false }))
    }

    private val center = GeoPoint(19.5040, -99.1460)
    private val dims = VehicleDimensions(lengthMeters = 3.9, widthMeters = 1.7)

    @Test fun elCarjackBuscaPuntosLejosDelCocheCuandoLosCercanosEstanBloqueados() {
        val candidates = forcedVehicleExitCandidates(center, 0f, dims)
        val farthest = candidates.maxOf { center.distanceMetersTo(it) }
        // Rings anteriores llegaban a ~3.85 m (baseRadius 2.85 + 1.0): con el coche rodeado hay que poder llegar más lejos.
        assertTrue("el anillo más lejano solo llega a $farthest m", farthest >= 6.0)
    }

    @Test fun todosLosCandidatosDelCarjackQuedanFueraDeLaCarroceria() {
        val footprint = VehicleFootprint(center, 0f, dims)
        val inside = forcedVehicleExitCandidates(center, 0f, dims).filter { footprint.contains(it) }
        assertEquals("candidatos dentro del coche: $inside", emptyList<GeoPoint>(), inside)
    }

    @Test fun losCandidatosCercanosSiguenPrimeroQueLosLejanos() {
        val candidates = forcedVehicleExitCandidates(center, 0f, dims)
        val manualCount = vehicleExitCandidates(center, 0f, dims).size
        assertEquals(vehicleExitCandidates(center, 0f, dims), candidates.take(manualCount))
        val rings = candidates.drop(manualCount).chunked(8).map { group -> group.map { center.distanceMetersTo(it) } }
        rings.zipWithNext().forEach { (near, far) ->
            assertTrue("un anillo lejano tiene puntos más cerca (${far.min()}) que el anterior (${near.max()})", far.min() >= near.max() - 0.05)
        }
    }
}
