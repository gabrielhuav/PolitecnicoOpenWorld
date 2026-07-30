package ovh.gabrielhuav.pow.platform.imagen

import ovh.gabrielhuav.pow.platform.assets.PowAssetNoEncontrado
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.assets.PowAssetsFuente
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 🖼️ El CONTRATO de [PowImagenCache].
 *
 * Lo que NO se prueba aquí es la decodificación: eso depende de la plataforma y se ve en el
 * emulador o en el simulador. Lo que sí se fija es lo que en gama baja se paga caro y el compilador
 * no ve: que la caché **tenga tope** y que un asset ausente **no reviente la pantalla**.
 *
 * ⚠️ Estos tests instalan una fuente de assets falsa. El `@AfterTest` la desinstala; sin eso,
 * contaminarían a `PowAssetsTest` y a cualquier test que corra después.
 */
class PowImagenCacheTest {

    /** Devuelve bytes que NO son una imagen: basta para el camino de error. */
    private class FuenteFalsa : PowAssetsFuente {
        var lecturas = 0
            private set

        override fun bytes(ruta: String): ByteArray {
            if (ruta.startsWith("no/")) throw PowAssetNoEncontrado(ruta)
            lecturas++
            return byteArrayOf(0, 1, 2, 3)
        }

        override fun listar(dir: String): List<String> = emptyList()
        override fun existe(ruta: String): Boolean = !ruta.startsWith("no/")
    }

    private val fuente = FuenteFalsa()

    @BeforeTest
    fun instala() {
        PowAssets.instalar(fuente)
        PowImagenCache.limpiar()
    }

    @AfterTest
    fun limpia() {
        PowImagenCache.limpiar()
        PowAssets.desinstalar()
    }

    @Test
    fun `un asset que no existe devuelve null y NO lanza`() {
        // Si esto lanzara, una card con el arte mal referenciado tumbaría toda la pantalla de
        // coleccionables en vez de dejar un hueco.
        assertNull(PowImagenCache.cargar("no/existe.webp"))
    }

    @Test
    fun `unos bytes que no son imagen tampoco lanzan`() {
        // Mismo motivo: un asset corrupto se degrada a hueco, no a crash.
        assertNull(PowImagenCache.cargar("basura.webp"))
    }

    @Test
    fun `enMemoria NO decodifica - es seguro llamarlo en composicion`() {
        // Es la propiedad que permite devolver la imagen en la misma composición al hacer scroll
        // hacia atrás. Si `enMemoria` tocara disco, sería otra lectura en el hilo de UI.
        val antes = fuente.lecturas
        assertNull(PowImagenCache.enMemoria("basura.webp"))
        assertEquals(antes, fuente.lecturas, "enMemoria no debe leer del disco")
    }

    @Test
    fun `limpiar deja la cache vacia`() {
        // Lo llama MainActivity.onTrimMemory bajo presión de memoria.
        PowImagenCache.limpiar()
        assertEquals(0, PowImagenCache.tamano())
    }

    @Test
    fun `la cache NUNCA crece sin tope`() {
        // ⚠️ La regla de 09 §6: un caché cuyas claves llevan parámetros (aquí el factor de
        // reducción) crece hasta el OOM si no se desaloja. Se piden muchas más entradas de las que
        // caben y se comprueba que el tamaño se queda acotado.
        for (i in 1..60) {
            PowImagenCache.cargar("arte_$i.webp", reduccion = 1 + (i % 3))
        }
        assertTrue(
            PowImagenCache.tamano() <= 12,
            "la cache crecio hasta ${PowImagenCache.tamano()} entradas: le falta el tope",
        )
    }
}
