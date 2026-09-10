package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El repintado de carrocería es matemática pura, así que se puede probar de verdad — y conviene,
 * porque **lo comparten Android y iOS** y una deriva aquí sale como "los coches de iOS son de otro
 * color" sin que nada falle.
 *
 * ⚠️ Los nombres van con ` - ` y sin paréntesis ni comas: Kotlin/Native no admite `(`, `)` ni `,`
 * en los nombres con tildes invertidas (09 §KMP nº4).
 */
class TintadoVehiculoTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun rojo(p: Int) = (p ushr 16) and 0xFF
    private fun verde(p: Int) = (p ushr 8) and 0xFF
    private fun azul(p: Int) = p and 0xFF
    private fun alfa(p: Int) = p ushr 24

    private val ROJO = 0xFFFF0000.toInt()

    @Test
    fun `el pixel transparente no se toca`() {
        val px = intArrayOf(argb(0, 200, 200, 200))
        tintarCarroceria(px, ROJO)
        assertEquals(argb(0, 200, 200, 200), px[0])
    }

    @Test
    fun `la chapa blanca toma el color objetivo`() {
        // Gris claro neutro (sin saturación), en plena franja de carrocería.
        val px = intArrayOf(argb(255, 200, 200, 200))
        tintarCarroceria(px, ROJO)
        assertTrue(rojo(px[0]) > verde(px[0]), "el rojo debe dominar: ${px[0].toString(16)}")
        assertTrue(rojo(px[0]) > azul(px[0]))
        assertEquals(255, alfa(px[0]), "el alfa no se toca")
    }

    @Test
    fun `una luz que YA tiene color propio se respeta`() {
        // Piloto trasero rojo saturado: no debe repintarse aunque tintemos de azul.
        val piloto = argb(255, 220, 20, 20)
        val px = intArrayOf(piloto)
        tintarCarroceria(px, 0xFF0000FF.toInt())
        assertEquals(piloto, px[0], "las luces no se repintan")
    }

    @Test
    fun `las sombras y los neumaticos se quedan fuera`() {
        // Luminosidad por debajo de 80: neumático / sombra.
        val sombra = argb(255, 30, 30, 30)
        val px = intArrayOf(sombra)
        tintarCarroceria(px, ROJO)
        assertEquals(sombra, px[0])
    }

    @Test
    fun `el brillo especular se queda fuera`() {
        // Luminosidad por encima de 245: faro / reflejo puro.
        val brillo = argb(255, 252, 252, 252)
        val px = intArrayOf(brillo)
        tintarCarroceria(px, ROJO)
        assertEquals(brillo, px[0])
    }

    @Test
    fun `el sprite conserva su volumen - mas luz da color mas claro`() {
        // Dos grises de la misma familia: el más claro debe salir más claro también repintado.
        val px = intArrayOf(argb(255, 150, 150, 150), argb(255, 220, 220, 220))
        tintarCarroceria(px, ROJO)
        assertTrue(
            rojo(px[1]) > rojo(px[0]),
            "el repintado debe conservar la sombra, no aplanarla: ${rojo(px[0])} vs ${rojo(px[1])}",
        )
    }

    @Test
    fun `el borde contra el rin se interpola en vez de cortar en seco`() {
        // Justo dentro de la rampa baja (80..130): el cambio debe ser PARCIAL.
        val original = argb(255, 90, 90, 90)
        val px = intArrayOf(original)
        tintarCarroceria(px, ROJO)
        assertTrue(px[0] != original, "dentro de la rampa sí se repinta algo")
        assertTrue(
            verde(px[0]) > 0,
            "a medio camino aún queda algo del gris: si el verde cayera a 0 el corte sería seco",
        )
    }

    @Test
    fun `tintar dos veces con el mismo color no sigue oscureciendo`() {
        // Idempotencia práctica: el resultado ya está saturado y la segunda pasada lo respeta.
        val px = intArrayOf(argb(255, 200, 200, 200))
        tintarCarroceria(px, ROJO)
        val unaVez = px[0]
        tintarCarroceria(px, ROJO)
        assertEquals(unaVez, px[0], "el resultado ya tiene color propio y queda protegido")
    }
}
