package ovh.gabrielhuav.pow.features.settings.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pruebas unitarias para la configuración y catálogo de páginas del tutorial
 * de controles en mundo abierto e interiores (commonMain KMP).
 */
class ControlsTutorialTest {

    @Test
    fun `exteriorTutorialPages tiene 6 paginas configuradas`() {
        val pages = exteriorTutorialPages()
        assertEquals(6, pages.size, "El tutorial de mundo exterior debe tener exactamente 6 páginas")
    }

    @Test
    fun `interiorTutorialPages tiene 5 paginas configuradas`() {
        val pages = interiorTutorialPages()
        assertEquals(5, pages.size, "El tutorial de interiores debe tener exactamente 5 páginas")
    }

    @Test
    fun `todas las paginas del tutorial exterior tienen recursos e identificadores validos`() {
        val pages = exteriorTutorialPages()
        for (page in pages) {
            val hasBadge = page.buttonLetter != null && page.buttonLetter.isNotBlank()
            val hasEmoji = page.emoji.isNotBlank()
            assertTrue(hasBadge || hasEmoji, "Cada página debe tener una letra de botón o un emoji válido")
        }
    }

    @Test
    fun `todas las paginas del tutorial interior tienen recursos e identificadores validos`() {
        val pages = interiorTutorialPages()
        for (page in pages) {
            val hasBadge = page.buttonLetter != null && page.buttonLetter.isNotBlank()
            val hasEmoji = page.emoji.isNotBlank()
            assertTrue(hasBadge || hasEmoji, "Cada página debe tener una letra de botón o un emoji válido")
        }
    }

    @Test
    fun `los botones principales A B X e Y estan presentes en el tutorial exterior`() {
        val pages = exteriorTutorialPages()
        val letters = pages.mapNotNull { it.buttonLetter }
        assertTrue(letters.contains("A"), "Debe incluir el botón A (correr)")
        assertTrue(letters.contains("B"), "Debe incluir el botón B (golpear)")
        assertTrue(letters.contains("X"), "Debe incluir el botón X (interactuar)")
        assertTrue(letters.contains("Y"), "Debe incluir el botón Y (conducir)")
    }
}
