package ovh.gabrielhuav.pow.data.repository

import ovh.gabrielhuav.pow.data.cache.celdaDeCalles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contrato entre la app y la rama `datos-calles` (tools/calles/generar_celdas.py). Si la clave de
 * celda o el formato cambian de un solo lado, la app pide archivos que no existen y vuelve a
 * depender de Overpass en vivo, sin ningún error visible.
 */
class CallesEstaticasTest {

    @Test
    fun `la celda de ESCOM es la que escribe el generador`() {
        // Coordenadas reales del Logcat del 2026-10-29 (inicio del mundo abierto).
        assertEquals("975_-4958", celdaDeCalles(19.504603, -99.145985))
    }

    @Test
    fun `la celda usa floor tambien con longitudes negativas`() {
        assertEquals("975_-4958", celdaDeCalles(19.5199, -99.1599))
        assertEquals("976_-4957", celdaDeCalles(19.5201, -99.1399))
    }

    @Test
    fun `la url apunta a la rama datos-calles`() {
        assertEquals(
            "https://raw.githubusercontent.com/gabrielhuav/PolitecnicoOpenWorld/datos-calles/v1/celdas/975_-4958.json",
            CallesEstaticas.url(19.504603, -99.145985),
        )
    }

    @Test
    fun `una celda estatica se lee con el mismo parser que Overpass`() {
        // Recorte real de la salida de generar_celdas.py (formato compacto).
        val celda = """{"version":0.6,"generator":"POW generar_celdas.py","elements":[""" +
            """{"type":"node","id":1,"lat":19.5046,"lon":-99.146},""" +
            """{"type":"node","id":2,"lat":19.505,"lon":-99.145},""" +
            """{"type":"node","id":5,"lat":19.5046,"lon":-99.147},""" +
            """{"type":"node","id":6,"lat":19.5047,"lon":-99.1471},""" +
            """{"type":"way","id":100,"nodes":[1,2],"tags":{"highway":"residential"}},""" +
            """{"type":"way","id":300,"nodes":[5,6],"tags":{"highway":"footway"}}]}"""
        val ways = OverpassRepository().parseOverpassJson(celda)
        assertEquals(listOf(100L, 300L), ways.map { it.id })
        val calle = ways.first { it.id == 100L }
        assertEquals(2, calle.nodes.size)
        assertTrue(calle.isForCars && calle.isForPeople)
        val banqueta = ways.first { it.id == 300L }
        assertTrue(banqueta.isForPeople && !banqueta.isForCars)
    }
}
