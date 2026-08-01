package ovh.gabrielhuav.pow.platform.assets

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 🍏 EL CONTRATO de [PowAssetsFuente], comprobado con una fuente falsa.
 *
 * Lo que se fija aquí NO es "sabe leer un archivo" — eso depende de la plataforma y se prueba en el
 * simulador. Lo que se fija es el CONTRATO que las dos implementaciones tienen que cumplir, porque
 * es donde Android e iOS se separan sin avisar: qué pasa al pedir algo que no existe, y si `listar`
 * de un directorio inexistente lanza o devuelve vacío. Si divergen ahí, el juego se rompe en una
 * sola plataforma y el compilador no dice nada.
 */
class PowAssetsTest {

    /** Fuente de mentira con un mapa detrás. Imita el contrato, no la plataforma. */
    private class FuenteFalsa(private val archivos: Map<String, ByteArray>) : PowAssetsFuente {
        override fun bytes(ruta: String): ByteArray =
            archivos[ruta] ?: throw PowAssetNoEncontrado(ruta)

        override fun listar(dir: String): List<String> =
            archivos.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }

        override fun existe(ruta: String): Boolean = ruta in archivos
    }

    private val fuente = FuenteFalsa(
        mapOf(
            "STREETFIGHTER/DATA/huelum.json" to """{"frames":{}}""".encodeToByteArray(),
            "STREETFIGHTER/IMAGES/fondo.webp" to byteArrayOf(1, 2, 3),
            "vacio.txt" to ByteArray(0),
        ),
    )

    @AfterTest
    fun limpia() = PowAssets.desinstalar()

    @Test
    fun `texto decodifica los bytes como UTF-8`() {
        assertEquals("""{"frames":{}}""", fuente.texto("STREETFIGHTER/DATA/huelum.json"))
    }

    @Test
    fun `pedir un asset que no existe lanza con la ruta en el mensaje`() {
        // La ruta en el mensaje es lo ÚNICO que sirve para depurar esto en un simulador.
        val e = assertFailsWith<PowAssetNoEncontrado> { fuente.bytes("no/existe.png") }
        assertTrue("no/existe.png" in (e.message ?: ""))
    }

    @Test
    fun `listar un directorio inexistente devuelve vacio y NO lanza`() {
        // Contrato heredado del código de Android (`assets.list` devolvía null y se trataba como
        // vacío). Si iOS lanzara aquí, la selección de escenario se caería solo en iOS.
        assertEquals(emptyList(), fuente.listar("directorio/que/no/esta"))
    }

    @Test
    fun `un asset vacio se lee sin reventar`() {
        // En iOS este caso pasa por `addressOf(0)`, que es inválido con tamaño 0.
        assertEquals(0, fuente.bytes("vacio.txt").size)
    }

    @Test
    fun `existe distingue lo que hay de lo que no`() {
        assertTrue(fuente.existe("STREETFIGHTER/IMAGES/fondo.webp"))
        assertFalse(fuente.existe("STREETFIGHTER/IMAGES/no.webp"))
    }

    @Test
    fun `PowAssets delega en la fuente instalada`() {
        PowAssets.instalar(fuente)
        assertEquals(byteArrayOf(1, 2, 3).toList(), PowAssets.bytes("STREETFIGHTER/IMAGES/fondo.webp").toList())
        assertTrue(PowAssets.existe("vacio.txt"))
    }
}
