package ovh.gabrielhuav.pow.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Los respaldos de Overpass: si esto falla, una instalación nueva vuelve a quedarse en el 25 %
 * de la carga del mundo abierto cuando el servidor principal está caído o nos limita.
 */
class OverpassServidoresTest {

    private val urls = OverpassServidores.URLS

    @Test
    fun `hay varios servidores y el principal va primero`() {
        assertTrue(urls.size >= 3, "se esperaban varios respaldos, hay ${urls.size}")
        assertEquals("https://overpass-api.de/api/interpreter", urls.first())
        assertEquals(urls.size, urls.toSet().size, "no debe haber servidores repetidos")
    }

    @Test
    fun `el orden desde cero es la lista tal cual`() {
        assertEquals(urls, OverpassServidores.ordenDesde(0))
    }

    @Test
    fun `el orden empieza por el ultimo bueno y da la vuelta sin perder ninguno`() {
        val orden = OverpassServidores.ordenDesde(2)
        assertEquals(urls[2], orden.first())
        assertEquals(urls[1], orden.last())
        assertEquals(urls.toSet(), orden.toSet())
    }

    @Test
    fun `un indice fuera de rango vuelve al principal`() {
        assertEquals(urls, OverpassServidores.ordenDesde(-1))
        assertEquals(urls, OverpassServidores.ordenDesde(urls.size))
    }

    @Test
    fun `una respuesta normal no es error aunque venga sin calles`() {
        assertFalse(OverpassServidores.esErrorDelServidor("""{"version":0.6,"elements":[]}"""))
    }

    @Test
    fun `un 200 con remark de timeout o memoria cuenta como error`() {
        val timeout = """{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 3"}"""
        val memoria = """{"elements":[],"remark":"runtime error: Query run out of memory"}"""
        assertTrue(OverpassServidores.esErrorDelServidor(timeout))
        assertTrue(OverpassServidores.esErrorDelServidor(memoria))
    }

    @Test
    fun `una pagina HTML en vez de JSON cuenta como error`() {
        assertTrue(OverpassServidores.esErrorDelServidor("<html><body>502 Bad Gateway</body></html>"))
    }
}
