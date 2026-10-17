package ovh.gabrielhuav.pow.features.map_exterior.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug real encontrado en el examen QA: el buscador de estaciones de Metro/Metrobús (menú de
 * Teletransporte, solo visible con Modo Desarrollador activo) no encontraba nombres con acento
 * si el usuario buscaba sin acento — p. ej. "pantitlan" no encontraba la estación real
 * "Pantitlán". Estos tests fijan el comportamiento correcto de [stationMatchesQuery].
 *
 * NO requiere Android: usa solo `java.text.Normalizer` (JVM estándar).
 */
class StationSearchTest {

    @Test
    fun `busqueda sin acento encuentra estacion con acento - RUTA FELIZ`() {
        assertTrue(stationMatchesQuery("Pantitlán", "pantitlan"))
        assertTrue(stationMatchesQuery("Juárez", "juarez"))
        assertTrue(stationMatchesQuery("Coyoacán", "coyoacan"))
        assertTrue(stationMatchesQuery("Instituto del Petróleo", "petroleo"))
    }

    @Test
    fun `busqueda CON acento tambien sigue funcionando - regresion`() {
        assertTrue(stationMatchesQuery("Pantitlán", "Pantitlán"))
        assertTrue(stationMatchesQuery("Juárez", "juárez"))
    }

    @Test
    fun `busqueda parcial en medio del nombre funciona`() {
        assertTrue(stationMatchesQuery("Zócalo - Tenochtitlán", "tenochtitlan"))
    }

    @Test
    fun `busqueda vacia o solo espacios no filtra nada - limite`() {
        assertTrue(stationMatchesQuery("Pantitlán", ""))
        assertTrue(stationMatchesQuery("Pantitlán", "   "))
    }

    @Test
    fun `busqueda que no existe en ninguna estacion no encuentra nada - limite`() {
        assertFalse(stationMatchesQuery("Pantitlán", "xyz123"))
    }

    @Test
    fun `mayusculas y minusculas no importan`() {
        assertTrue(stationMatchesQuery("Pantitlán", "PANTITLAN"))
        assertTrue(stationMatchesQuery("pantitlán", "Pantitlan"))
    }

    @Test
    fun `la enie NO se trata como un acento - es una letra distinta de la n`() {
        assertFalse(stationMatchesQuery("Peñón", "penon"))
        assertTrue(stationMatchesQuery("Peñón", "peñon"))
    }
}
