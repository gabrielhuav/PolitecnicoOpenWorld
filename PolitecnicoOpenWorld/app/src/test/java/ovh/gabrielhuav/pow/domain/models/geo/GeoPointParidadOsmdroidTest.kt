package ovh.gabrielhuav.pow.domain.models.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random
import org.osmdroid.util.GeoPoint as OsmGeoPoint

/**
 * 🍏 PARIDAD con osmdroid — red de seguridad de la Fase 2 (`PLAN_MIGRACION_KMP.md`).
 *
 * POR QUÉ ESTE TEST: al sustituir `org.osmdroid.util.GeoPoint` por el nuestro, lo único que NO
 * verifica el compilador es que las MATEMÁTICAS sigan dando lo mismo. Y las distancias mandan en el
 * juego (aggro de NPCs, radios de interacción del metro/metrobús/coleccionables, rutas, colisiones):
 * si `distanceToAsDouble` se desvía un poco, el juego cambia de comportamiento SIN que nada falle.
 *
 * Este test vive en `:app` a propósito: es el único source set donde están LAS DOS
 * implementaciones (osmdroid solo existe en Android). Compara la nuestra contra la de verdad.
 *
 * ⚠️ Si algún día se borra osmdroid del proyecto, este test se va con él — pero para entonces ya
 * habrá cumplido su función, que es cubrir la MIGRACIÓN.
 */
class GeoPointParidadOsmdroidTest {

    /** Cero tolerancia: es un port literal, así que el resultado debe ser BIT A BIT el mismo. */
    private val exacto = 0.0

    private fun comparar(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        val esperado = OsmGeoPoint(lat1, lon1).distanceToAsDouble(OsmGeoPoint(lat2, lon2))
        val obtenido = GeoPoint(lat1, lon1).distanceToAsDouble(GeoPoint(lat2, lon2))
        assertEquals("($lat1,$lon1) -> ($lat2,$lon2)", esperado, obtenido, exacto)
    }

    @Test
    fun `mismo resultado que osmdroid en los puntos REALES del juego`() {
        // Coordenadas que el juego usa de verdad (ESCOM y alrededores del mapa de POW).
        val escomLat = 19.504603
        val escomLon = -99.145985
        comparar(escomLat, escomLon, escomLat, escomLon)                 // distancia 0
        comparar(escomLat, escomLon, 19.505000, -99.146500)              // unos metros
        comparar(escomLat, escomLon, 19.500000, -99.140000)              // cientos de metros
        comparar(escomLat, escomLon, 19.432608, -99.133209)              // Zócalo CDMX (~8 km)
        comparar(escomLat, escomLon, 19.319000, -99.184000)              // UNAM CU
    }

    @Test
    fun `mismo resultado que osmdroid en 2000 pares ALEATORIOS de todo el globo`() {
        // Semilla fija: si un dia falla, falla siempre igual y se puede depurar.
        val rnd = Random(20260727)
        repeat(2000) {
            comparar(
                rnd.nextDouble(-89.0, 89.0), rnd.nextDouble(-180.0, 180.0),
                rnd.nextDouble(-89.0, 89.0), rnd.nextDouble(-180.0, 180.0),
            )
        }
    }

    @Test
    fun `mismo resultado en los casos BORDE (polos, antimeridiano, puntos identicos)`() {
        comparar(0.0, 0.0, 0.0, 0.0)
        comparar(90.0, 0.0, -90.0, 0.0)          // polo a polo
        comparar(0.0, 179.999999, 0.0, -179.999999) // cruzando el antimeridiano
        comparar(19.5, -99.1, 19.5, -99.1)       // identicos: el min(1.0,..) evita NaN
        comparar(19.5, -99.1, 19.5000000001, -99.1) // casi identicos
    }

    @Test
    fun `una distancia conocida cuadra con la realidad (cordura, no solo paridad)`() {
        // 1 grado de latitud en el ecuador ~= 111.19 km con radio 6378137.
        val d = GeoPoint(0.0, 0.0).distanceToAsDouble(GeoPoint(1.0, 0.0))
        assertTrue("1 grado de lat deberia rondar 111 km, dio $d", d in 111_000.0..111_400.0)
        // Y el radio usado es el ECUATORIAL de osmdroid, no el medio (6371000).
        assertEquals(6378137.0, GeoPoint.RADIUS_EARTH_METERS, 0.0)
    }
}
