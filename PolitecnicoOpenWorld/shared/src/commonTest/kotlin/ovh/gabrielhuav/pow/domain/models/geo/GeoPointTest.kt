package ovh.gabrielhuav.pow.domain.models.geo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 🍏 Test MULTIPLATAFORMA del punto lat/lon (Fase 2 de `PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ EXISTE, si ya está `GeoPointParidadOsmdroidTest`: aquel compara contra osmdroid y por eso
 * **solo puede correr en Android**. Éste corre TAMBIÉN en iOS, y su trabajo es cazar que las
 * matemáticas de Kotlin/Native se desvíen de las de la JVM. Los valores esperados están fijados
 * contra la implementación de osmdroid ya verificada.
 *
 * La tolerancia es de 1e-6 m (una micra) — no bit a bit: entre plataformas distintas la igualdad
 * exacta de `sin`/`asin`/`pow` NO está garantizada, y exigirla daría un test frágil. Una micra es
 * ~19 órdenes de magnitud menos que cualquier distancia que le importe al juego.
 */
class GeoPointTest {

    private val unaMicra = 1e-6

    @Test
    fun `distancias de referencia (fijadas contra osmdroid)`() {
        val escom = GeoPoint(19.504603, -99.145985)
        assertEquals(8125.845981694266, escom.distanceToAsDouble(GeoPoint(19.432608, -99.133209)), unaMicra)
        assertEquals(69.80955058413535, escom.distanceToAsDouble(GeoPoint(19.505, -99.1465)), unaMicra)
        assertEquals(111319.49079327357, GeoPoint(0.0, 0.0).distanceToAsDouble(GeoPoint(1.0, 0.0)), unaMicra)
        assertEquals(20037508.342789244, GeoPoint(90.0, 0.0).distanceToAsDouble(GeoPoint(-90.0, 0.0)), unaMicra)
    }

    @Test
    fun `mismo punto da 0 y nunca NaN (el min(1) del asin)`() {
        val p = GeoPoint(19.504603, -99.145985)
        assertEquals(0.0, p.distanceToAsDouble(p), 0.0)
        // Puntos casi idénticos: es donde el redondeo podría meter >1 en asin y devolver NaN.
        val d = p.distanceToAsDouble(GeoPoint(19.504603000000001, -99.145985))
        assertTrue(!d.isNaN(), "no debe ser NaN")
        assertTrue(d < 1.0, "deberia ser practicamente 0, dio $d")
    }

    @Test
    fun `la distancia es simetrica`() {
        val a = GeoPoint(19.504603, -99.145985)
        val b = GeoPoint(19.319, -99.184)
        assertEquals(a.distanceToAsDouble(b), b.distanceToAsDouble(a), unaMicra)
    }

    @Test
    fun `usa el radio ECUATORIAL de osmdroid, no el medio`() {
        // 6378137 (WGS84 ecuatorial), NO 6371000 (radio medio). Cambiarlo movería TODAS las
        // distancias del juego ~0.1%: radios de interacción, aggro de NPCs, rutas.
        assertEquals(6378137.0, GeoPoint.RADIUS_EARTH_METERS, 0.0)
    }

    @Test
    fun `es un valor por contenido (equals de data class)`() {
        assertEquals(GeoPoint(19.5, -99.1), GeoPoint(19.5, -99.1))
        assertEquals(GeoPoint(19.5, -99.1).hashCode(), GeoPoint(19.5, -99.1).hashCode())
    }
}
