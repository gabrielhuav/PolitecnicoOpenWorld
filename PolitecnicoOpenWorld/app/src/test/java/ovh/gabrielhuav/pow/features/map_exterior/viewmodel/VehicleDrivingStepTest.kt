package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CollisionPolygon
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig
import ovh.gabrielhuav.pow.domain.models.map.GeoNode
import ovh.gabrielhuav.pow.domain.models.map.VehicleDimensions
import ovh.gabrielhuav.pow.domain.models.map.VehicleFootprint
import ovh.gabrielhuav.pow.domain.models.map.obstacleAt
import ovh.gabrielhuav.pow.domain.models.map.offsetMeters

/**
 * Coche encimado en un edificio con la calle DETRÁS (caso DEV-08, 2026-11-21): el destino ajustado a la
 * calle apunta hacia dentro y antes dejaba al coche clavado; el paso del rumbo debe poder sacarlo, sin
 * meterlo nunca más adentro ni saltarse la aduana de calle cuando el coche está libre.
 */
class VehicleDrivingStepTest {
    private val origin = GeoPoint(19.5040, -99.1460)
    private val car = VehicleDimensions(lengthMeters = 4.0, widthMeters = 2.0)
    private val west = 270f
    private val east = 90f

    /** Edificio de 30 x 20 m cuyo borde OESTE está 1.5 m al oeste del origen: el origen queda dentro. */
    private val building = ExteriorCollisionsConfig(
        polygons = listOf(
            CollisionPolygon(
                "EDIFICIO_QA",
                listOf(
                    origin.offsetMeters(-10.0, -1.5),
                    origin.offsetMeters(10.0, -1.5),
                    origin.offsetMeters(10.0, 28.5),
                    origin.offsetMeters(-10.0, 28.5),
                ).map { GeoNode(it.latitude, it.longitude) },
            ),
        ),
    )

    /** La calle está al ESTE, detrás del edificio: el ajuste a la calle jala hacia dentro. */
    private val roadPullInward = origin.offsetMeters(0.0, 1.0)

    @Test fun precondicionElCocheArrancaEncimado() {
        assertNotNull(building.obstacleAt(VehicleFootprint(origin, west, car)))
    }

    @Test fun conLaCalleDetrasSaleSiguiendoSuRumbo() {
        val headingStep = origin.offsetMeters(0.0, -0.5) // mira al oeste, hacia el borde más cercano
        val result = building.resolveVehicleDrivingStep(origin, headingStep, roadPullInward, west, car)
        assertTrue("el coche debía poder salir por el borde oeste", result.reachedTarget)
        assertEquals(headingStep, result.position)
    }

    @Test fun elReintentoNuncaLoMeteMasAlEdificio() {
        val headingStep = origin.offsetMeters(0.0, 0.5) // mira al este: hacia dentro
        val result = building.resolveVehicleDrivingStep(origin, headingStep, roadPullInward, east, car)
        assertFalse(result.reachedTarget)
        assertNotNull(result.blockedBy)
    }

    @Test fun fueraDelEdificioSeRespetaLaAduanaDeLaCalle() {
        // 6.5 m al oeste del borde, sin solape: si el destino de la calle choca, NO se usa el atajo del rumbo.
        val outside = origin.offsetMeters(0.0, -8.0)
        assertNull(building.obstacleAt(VehicleFootprint(outside, west, car)))
        val roadIntoBuilding = origin.offsetMeters(0.0, 2.0)
        val headingStep = outside.offsetMeters(0.0, -0.5)
        val result = building.resolveVehicleDrivingStep(outside, headingStep, roadIntoBuilding, west, car)
        assertFalse(result.reachedTarget)
    }

    @Test fun sinColisionesLlegaAlDestinoDeLaCalle() {
        val noConfig: ExteriorCollisionsConfig? = null
        val result = noConfig.resolveVehicleDrivingStep(origin, origin.offsetMeters(0.0, -0.5), roadPullInward, west, car)
        assertTrue(result.reachedTarget)
        assertEquals(roadPullInward, result.position)
    }
}
