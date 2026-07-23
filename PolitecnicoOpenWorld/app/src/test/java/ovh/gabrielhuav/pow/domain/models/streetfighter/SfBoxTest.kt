package ovh.gabrielhuav.pow.domain.models.streetfighter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de CARACTERIZACIÓN de la geometría de cajas (Fase 1 del refactor). `SfBox` ya era puro
 * (colisiones), pero no tenía red: aquí se fija su `overlaps`/`toWorld`/`fromList` para que la
 * extracción del motor no la rompa sin avisar.
 */
class SfBoxTest {

    @Test
    fun `overlaps es true cuando se solapan y false con hueco`() {
        val a = SfBox(0f, 0f, 10f, 10f)
        assertTrue(a.overlaps(SfBox(5f, 5f, 10f, 10f)))
        assertFalse("hueco en X", a.overlaps(SfBox(20f, 0f, 10f, 10f)))
        assertFalse("hueco en Y", a.overlaps(SfBox(0f, 20f, 10f, 10f)))
    }

    @Test
    fun `tocarse en el borde NO cuenta como solape (comparacion estricta)`() {
        val a = SfBox(0f, 0f, 10f, 10f)
        assertFalse(a.overlaps(SfBox(10f, 0f, 10f, 10f))) // pegadas en X
        assertFalse(a.overlaps(SfBox(0f, 10f, 10f, 10f))) // pegadas en Y
    }

    @Test
    fun `overlaps es simetrico`() {
        val a = SfBox(0f, 0f, 10f, 10f)
        val b = SfBox(5f, 5f, 10f, 10f)
        assertEquals(a.overlaps(b), b.overlaps(a))
    }

    @Test
    fun `toWorld mirando a la DERECHA desplaza sin espejar`() {
        val w = SfBox(2f, 3f, 10f, 5f).toWorld(100f, 200f, SfDirection.RIGHT)
        assertEquals(SfBox(102f, 203f, 10f, 5f), w)
    }

    @Test
    fun `toWorld mirando a la IZQUIERDA espeja en X alrededor del origen`() {
        val w = SfBox(2f, 3f, 10f, 5f).toWorld(100f, 200f, SfDirection.LEFT)
        // x1 = 100 - 2 = 98 ; x2 = 98 - 10 = 88 ; minOf = 88
        assertEquals(SfBox(88f, 203f, 10f, 5f), w)
    }

    @Test
    fun `fromList degrada a caja cero si es null o corta`() {
        assertEquals(SfBox(0f, 0f, 0f, 0f), SfBox.fromList(null))
        assertEquals(SfBox(0f, 0f, 0f, 0f), SfBox.fromList(listOf(1, 2, 3)))
        assertEquals(SfBox(1f, 2f, 3f, 4f), SfBox.fromList(listOf(1, 2, 3, 4)))
    }
}
