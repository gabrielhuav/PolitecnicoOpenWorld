package ovh.gabrielhuav.pow.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * Tests de lógica PURA (sin Android) para [CalculateLocalCoordinatesUseCase].
 *
 * Verifica la proyección GPS→coordenada local de un edificio. La matemática es crítica: cualquier
 * cambio mueve TODOS los nodos ya capturados/exportados (ver el aviso en la clase). Estos tests
 * fijan invariantes geométricas y sirven de red ante refactors.
 *
 * NO requiere Android: la clase solo usa kotlin.math / java.lang.Math.
 */
class CalculateLocalCoordinatesUseCaseTest {

    private val useCase = CalculateLocalCoordinatesUseCase()

    // Centro de referencia (puerta de la ESCOM, aprox.) + tamaño del edificio en metros.
    private val centerLat = 19.50490
    private val centerLon = -99.14674
    private val width = 80f
    private val height = 50f

    private companion object {
        const val EPS = 1e-4
        const val EARTH_RADIUS = 6378137.0
    }

    /** Inverso de la proyección del caso de uso: offset en metros (este, norte) → (lat, lon). */
    private fun offsetMeters(eastM: Double, northM: Double): Pair<Double, Double> {
        val latRad = centerLat * (Math.PI / 180.0)
        val dLat = (northM / EARTH_RADIUS) * (180.0 / Math.PI)
        val dLon = (eastM / (EARTH_RADIUS * cos(latRad))) * (180.0 / Math.PI)
        return Pair(centerLat + dLat, centerLon + dLon)
    }

    @Test
    fun center_maps_to_middle() {
        val r = useCase(centerLat, centerLon, centerLat, centerLon, 0f, width, height)
        assertEquals(0.5, r.x.toDouble(), EPS)
        assertEquals(0.5, r.y.toDouble(), EPS)
    }

    @Test
    fun half_width_east_maps_to_right_edge_no_rotation() {
        val (lat, lon) = offsetMeters(eastM = (width / 2f).toDouble(), northM = 0.0)
        val r = useCase(lat, lon, centerLat, centerLon, 0f, width, height)
        assertEquals(1.0, r.x.toDouble(), EPS)
        assertEquals(0.5, r.y.toDouble(), EPS)
    }

    @Test
    fun half_height_north_maps_to_top_no_rotation() {
        // Norte geográfico = ARRIBA en la textura (Y=0), por la inversión del eje Y.
        val (lat, lon) = offsetMeters(eastM = 0.0, northM = (height / 2f).toDouble())
        val r = useCase(lat, lon, centerLat, centerLon, 0f, width, height)
        assertEquals(0.5, r.x.toDouble(), EPS)
        assertEquals(0.0, r.y.toDouble(), EPS)
    }

    @Test
    fun rotation_90_swaps_axes() {
        // Con el edificio rotado 90°, un punto al ESTE cae sobre el eje Y de la textura.
        val (lat, lon) = offsetMeters(eastM = (width / 2f).toDouble(), northM = 0.0)
        val r = useCase(lat, lon, centerLat, centerLon, 90f, width, height)
        assertEquals(0.5, r.x.toDouble(), EPS)
        assertEquals(1.3, r.y.toDouble(), EPS)
    }

    @Test
    fun scale_widens_the_normalized_extent() {
        // Con scaleX=2, el mismo offset físico cae a la mitad de distancia del centro normalizado.
        val (lat, lon) = offsetMeters(eastM = (width / 2f).toDouble(), northM = 0.0)
        val r = useCase(lat, lon, centerLat, centerLon, 0f, width, height, scaleX = 2.0f, scaleY = 1.0f)
        assertEquals(0.75, r.x.toDouble(), EPS) // 0.5 + (0.5 / 2)
        assertEquals(0.5, r.y.toDouble(), EPS)
    }

    @Test
    fun is_valid_respects_default_and_custom_margin() {
        assertTrue(LocalCoordinate(0.5f, 0.5f).isValid())
        assertTrue(LocalCoordinate(-0.10f, 0.5f).isValid()) // dentro del margen 0.15 por defecto
        assertFalse(LocalCoordinate(-0.10f, 0.5f).isValid(margin = 0.05f))
        assertFalse(LocalCoordinate(1.30f, 0.5f).isValid()) // fuera incluso con margen
    }
}
