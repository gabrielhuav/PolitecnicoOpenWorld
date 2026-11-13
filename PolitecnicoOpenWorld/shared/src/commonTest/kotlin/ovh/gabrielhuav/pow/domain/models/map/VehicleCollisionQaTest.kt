package ovh.gabrielhuav.pow.domain.models.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

/** QA PR #2: tolerancia de 5 cm, salida por vector mínimo y salida de solapes ya existentes. */
class VehicleCollisionQaTest {
    private val origin = GeoPoint(19.5040, -99.1460)
    private val car = VehicleDimensions(lengthMeters = 4.0, widthMeters = 2.0)

    private fun footprint(center: GeoPoint) = VehicleFootprint(center, 0f, car)

    // ── resolveTrafficImpact ────────────────────────────────────────────────

    @Test fun penetracionMenorA5cmNoEsChoque() {
        // Ambos de 2 m de ancho, separados 1.97 m en este-oeste => penetración 3 cm.
        val player = footprint(origin)
        val traffic = footprint(origin.offsetMeters(0.0, 1.97))
        assertTrue(player.intersects(traffic), "precondición: deben solaparse")
        assertNull(resolveTrafficImpact(origin, player, traffic))
    }

    @Test fun choqueRealDevuelveResolucionConMultiplicador035() {
        val previous = origin.offsetMeters(0.0, -3.0) // punto previo libre
        val player = footprint(origin)
        val traffic = footprint(origin.offsetMeters(0.0, 1.5)) // penetración 50 cm
        val result = resolveTrafficImpact(previous, player, traffic)
        assertNotNull(result)
        assertEquals(0.35, result.playerSpeedMultiplier)
        assertEquals(previous, result.playerCenter, "con previo libre vuelve al último punto seguro")
    }

    @Test fun puntoPrevioSolapadoSaleporVectorMinimo() {
        val player = footprint(origin)
        val traffic = footprint(origin.offsetMeters(0.0, 1.5))
        val previous = origin.offsetMeters(0.0, 0.2) // también solapa con el tráfico
        val result = resolveTrafficImpact(previous, player, traffic)
        assertNotNull(result)
        assertNotEquals(previous, result.playerCenter)
        assertTrue(
            !footprint(result.playerCenter).intersects(traffic) ||
                footprint(result.playerCenter).separatingOffsetFrom(traffic).let { it.eastMeters * it.eastMeters + it.northMeters * it.northMeters } < 0.2,
            "el jugador debe quedar fuera (o al límite) del tráfico",
        )
    }

    // ── resolveVehiclePath ──────────────────────────────────────────────────

    /** Edificio de 20 m de ancho cuyo borde sur está [southEdgeNorthMeters] al norte del origen. */
    private fun buildingNorth(southEdgeNorthMeters: Double): ExteriorCollisionsConfig {
        val nodes = listOf(
            origin.offsetMeters(southEdgeNorthMeters, -10.0),
            origin.offsetMeters(southEdgeNorthMeters, 10.0),
            origin.offsetMeters(southEdgeNorthMeters + 15.0, 10.0),
            origin.offsetMeters(southEdgeNorthMeters + 15.0, -10.0),
        ).map { GeoNode(it.latitude, it.longitude) }
        return ExteriorCollisionsConfig(polygons = listOf(CollisionPolygon("BARDA_QA", nodes)))
    }

    @Test fun cocheSolapadoPuedeAlejarse() {
        val config = buildingNorth(southEdgeNorthMeters = 1.5) // el frente (2 m) ya penetra 50 cm
        assertNotNull(config.obstacleAt(footprint(origin)), "precondición: arranca solapando")
        val to = origin.offsetMeters(-3.0, 0.0)
        val result = config.resolveVehiclePath(origin, to, 0f, car)
        assertNull(result.blockedBy)
        assertEquals(to, result.position)
    }

    @Test fun cocheSolapadoNoPuedeAvanzarHaciaDentro() {
        val config = buildingNorth(southEdgeNorthMeters = 1.5)
        val result = config.resolveVehiclePath(origin, origin.offsetMeters(3.0, 0.0), 0f, car)
        assertEquals(CollisionReason.BUILDING, result.blockedBy)
        assertEquals(origin, result.position, "no debe entrar ni un paso más")
    }

    @Test fun cocheLibreSigueBloqueadoPorLaBarda() {
        val config = buildingNorth(southEdgeNorthMeters = 5.0)
        val result = config.resolveVehiclePath(origin, origin.offsetMeters(10.0, 0.0), 0f, car)
        assertEquals(CollisionReason.BUILDING, result.blockedBy)
        assertTrue(result.position.latitude < origin.offsetMeters(3.5, 0.0).latitude + 1e-9)
    }

    /** Edificio de 40x40 m con el origen [southEdgeSouthMeters] m DENTRO (centro del coche dentro del edificio). */
    private fun buildingContainingCenter(southEdgeSouthMeters: Double): ExteriorCollisionsConfig {
        val nodes = listOf(
            origin.offsetMeters(-southEdgeSouthMeters, -20.0),
            origin.offsetMeters(-southEdgeSouthMeters, 20.0),
            origin.offsetMeters(40.0 - southEdgeSouthMeters, 20.0),
            origin.offsetMeters(40.0 - southEdgeSouthMeters, -20.0),
        ).map { GeoNode(it.latitude, it.longitude) }
        return ExteriorCollisionsConfig(polygons = listOf(CollisionPolygon("EDIF_QA", nodes)))
    }

    @Test fun centroDentroDelEdificioNoPuedeAvanzarHaciaElInterior() {
        val config = buildingContainingCenter(southEdgeSouthMeters = 3.0)
        assertNotNull(config.obstacleAt(footprint(origin)), "precondición: arranca dentro")
        val result = config.resolveVehiclePath(origin, origin.offsetMeters(10.0, 0.0), 0f, car)
        assertEquals(CollisionReason.BUILDING, result.blockedBy)
        assertEquals(origin, result.position, "no debe adentrarse más")
    }

    @Test fun centroDentroDelEdificioPuedeSalirPorElBordeMasCercano() {
        val config = buildingContainingCenter(southEdgeSouthMeters = 3.0)
        val to = origin.offsetMeters(-6.0, 0.0) // el borde sur está a 3 m: sale por ahí
        val result = config.resolveVehiclePath(origin, to, 0f, car)
        assertNull(result.blockedBy)
        assertEquals(to, result.position)
    }
}
