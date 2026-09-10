package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El armado del peatón lo comparten Android y iOS, así que una deriva aquí sale como "los NPCs de
 * iOS van vestidos de otro color" — sin error y sin test rojo, si no existiera este archivo.
 */
class TintadoPersonajeTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun rojo(p: Int) = (p ushr 16) and 0xFF
    private fun verde(p: Int) = (p ushr 8) and 0xFF
    private fun azul(p: Int) = p and 0xFF
    private fun alfa(p: Int) = p ushr 24

    private val ROJO = argb(255, 255, 0, 0)
    private val AZUL = argb(255, 0, 0, 255)

    @Test
    fun `la playera clara toma el color de la prenda`() {
        val px = intArrayOf(argb(255, 200, 200, 200))
        tintarPersonaje(px, ROJO, AZUL)
        assertTrue(rojo(px[0]) > 0, "debe quedar componente roja")
        assertEquals(0, verde(px[0]), "el multiply con rojo puro apaga verde y azul")
        assertEquals(0, azul(px[0]))
    }

    @Test
    fun `el gris medio toma el color del pantalon`() {
        val px = intArrayOf(argb(255, 100, 100, 100))
        tintarPersonaje(px, ROJO, AZUL)
        assertTrue(azul(px[0]) > 0, "el pantalón va con el segundo color")
        assertEquals(0, rojo(px[0]))
    }

    @Test
    fun `la piel NO se pinta - tiene tono calido`() {
        // Piel: el rojo domina, así que la diferencia entre canales pasa del umbral.
        val piel = argb(255, 220, 180, 150)
        val px = intArrayOf(piel)
        tintarPersonaje(px, ROJO, AZUL)
        assertEquals(piel, px[0], "la cara y las manos no son ropa")
    }

    @Test
    fun `el contorno oscuro se queda negro`() {
        val contorno = argb(255, 20, 20, 20)
        val px = intArrayOf(contorno)
        tintarPersonaje(px, ROJO, AZUL)
        assertEquals(contorno, px[0], "la silueta tiene que seguir leyéndose")
    }

    @Test
    fun `el borde suavizado no se pinta - si no queda halo`() {
        val borde = argb(30, 200, 200, 200)
        val px = intArrayOf(borde)
        tintarPersonaje(px, ROJO, AZUL)
        assertEquals(borde, px[0])
    }

    @Test
    fun `sin color de pantalon solo se pinta la prenda clara - el caso del pelo`() {
        val grisMedio = argb(255, 100, 100, 100)
        val px = intArrayOf(argb(255, 200, 200, 200), grisMedio)
        tintarPersonaje(px, ROJO, null)
        assertTrue(rojo(px[0]) > 0, "lo claro sí se pinta")
        assertEquals(grisMedio, px[1], "sin segundo color el gris medio se respeta")
    }

    @Test
    fun `el multiply conserva los pliegues de la tela`() {
        val px = intArrayOf(argb(255, 170, 170, 170), argb(255, 250, 250, 250))
        tintarPersonaje(px, ROJO, null)
        assertTrue(
            rojo(px[1]) > rojo(px[0]),
            "la zona iluminada debe quedar más clara que la sombreada",
        )
    }

    @Test
    fun `componer pega el pelo opaco encima del cuerpo`() {
        val cuerpo = intArrayOf(argb(255, 10, 10, 10))
        val pelo = intArrayOf(argb(255, 90, 60, 30))
        componerEncima(cuerpo, pelo)
        assertEquals(argb(255, 90, 60, 30), cuerpo[0])
    }

    @Test
    fun `componer respeta lo transparente del pelo`() {
        val original = argb(255, 10, 20, 30)
        val cuerpo = intArrayOf(original)
        componerEncima(cuerpo, intArrayOf(0))
        assertEquals(original, cuerpo[0], "donde no hay pelo se ve el cuerpo")
    }

    @Test
    fun `componer mezcla de verdad el borde semitransparente`() {
        // Pelo negro al 50 % sobre cuerpo blanco: debe salir un gris intermedio, no uno de los dos.
        val cuerpo = intArrayOf(argb(255, 255, 255, 255))
        componerEncima(cuerpo, intArrayOf(argb(128, 0, 0, 0)))
        val resultado = rojo(cuerpo[0])
        assertTrue(
            resultado in 100..160,
            "un source-over real da un gris medio, salió $resultado",
        )
        assertEquals(255, alfa(cuerpo[0]), "sobre un fondo opaco el resultado sigue opaco")
    }

    @Test
    fun `componer sobre vacio conserva el alfa del pelo`() {
        val vacio = intArrayOf(0)
        componerEncima(vacio, intArrayOf(argb(128, 200, 100, 50)))
        assertEquals(128, alfa(vacio[0]))
        assertEquals(200, rojo(vacio[0]), "sin fondo, el color de arriba no se diluye")
    }
}
