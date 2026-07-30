package ovh.gabrielhuav.pow.platform.imagen

import androidx.compose.ui.graphics.ImageBitmap
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.concurrencia.PowCerrojo

/**
 * 🖼️ CACHÉ ACOTADA DE IMÁGENES DE ASSETS — pensada para gama baja.
 *
 * ## Por qué existe
 *
 * Las pantallas que pintan arte desde `assets/` (Coleccionables, retratos de peleador…) lo hacían
 * decodificando **dentro de `remember`**, o sea **en el hilo de composición**. Eso tiene tres
 * costes que en un teléfono flojo se notan:
 *
 * 1. **Decodifica en el hilo de UI.** Abrir la rejilla de coleccionables decodificaba 7 imágenes
 *    seguidas antes de poder pintar el primer frame.
 * 2. **Se repite al hacer scroll.** `LazyVerticalGrid` DESTRUYE los items que salen de pantalla, y
 *    con ellos su `remember` → al volver a subir, se vuelve a decodificar todo.
 * 3. **Decodifica más píxeles de los que se ven.** Medido: los webp de `SPRITES/COLLECTIBLES` son
 *    de ~600×420 y se pintan a **64 dp**. En ARGB_8888 son **~1 MB cada uno, 6,7 MB los siete**.
 *
 * ⚠️ (Nota al margen, que cuesta un ciclo de compilación aprender: en Kotlin los comentarios de
 * bloque **se anidan**, así que escribir una ruta con comodín tipo `CARPETA` + barra + asterisco
 * dentro de un KDoc abre un comentario que nunca cierra. Por eso arriba no lleva comodín.)
 *
 * ## Qué hace
 *
 * Guarda hasta [MAX_ENTRADAS] imágenes ya decodificadas, con desalojo **LRU por orden de acceso**.
 *
 * ⚠️ **El tope NO es decorativo.** Es la misma regla que `nativeDrawableCache` del mapa (09 §6): un
 * mapa sin tope cuyas claves incluyen parámetros (aquí, el factor de reducción) crece hasta el OOM.
 * **Nunca lo cambies por un `mutableMapOf` sin límite.**
 *
 * ⚠️ [cargar] **DECODIFICA: no lo llames desde el hilo de UI.** Para eso está
 * `rememberImagenDeAsset`, que ya lo saca a un hilo de fondo.
 */
object PowImagenCache {

    /**
     * Con `reduccion = 2` cada coleccionable ocupa ~0,25 MB, así que 12 entradas son ~3 MB en el
     * peor caso. Cubre los 8 coleccionables del juego más algún retrato, sin dejar la caché
     * inservible por desalojos constantes.
     */
    private const val MAX_ENTRADAS = 12

    private data class Clave(val ruta: String, val reduccion: Int)

    private val cache = mutableMapOf<Clave, ImageBitmap>()

    // El orden de acceso se lleva aparte porque `LinkedHashMap` con `accessOrder` es de la JVM y
    // aquí el código tiene que compilar también para iOS. Con 12 entradas, el coste de
    // `remove`/`add` sobre la lista es irrelevante.
    private val usoReciente = mutableListOf<Clave>()

    private val cerrojo = PowCerrojo()

    /**
     * Devuelve la imagen **solo si ya está decodificada**. No toca disco ni decodifica, así que es
     * seguro llamarlo desde composición.
     *
     * Sirve para que al volver a hacer scroll el item aparezca YA, sin un frame en blanco.
     */
    fun enMemoria(ruta: String, reduccion: Int = 1): ImageBitmap? = cerrojo.ejecutar {
        val clave = Clave(ruta, reduccion)
        cache[clave]?.also { tocar(clave) }
    }

    /**
     * Devuelve la imagen, decodificándola si hace falta. `null` si el asset no existe o no se pudo
     * decodificar — el llamante pinta su hueco y el juego sigue.
     *
     * ⚠️ **LLAMAR FUERA DEL HILO DE UI.**
     *
     * @param reduccion 1 = tamaño original, 2 = mitad de ancho y alto (¼ de memoria)… Elige el
     *   menor factor que siga dando más píxeles que el tamaño en pantalla; ver `decodificarReducido`.
     */
    fun cargar(ruta: String, reduccion: Int = 1): ImageBitmap? {
        enMemoria(ruta, reduccion)?.let { return it }
        // La decodificación va FUERA del cerrojo a propósito: es la parte lenta, y bloquear aquí
        // dejaría a otra pantalla esperando por una imagen que no le interesa. El precio es que dos
        // llamantes simultáneos pueden decodificar lo mismo a la vez; con imágenes de menú, sale
        // más barato que serializar todas las cargas del juego.
        val decodificada = runCatching {
            decodificarReducido(PowAssets.bytes(ruta), reduccion)
        }.getOrNull() ?: return null
        return cerrojo.ejecutar {
            val clave = Clave(ruta, reduccion)
            // Si otro hilo se adelantó, se queda la suya: así dos cards de la misma imagen
            // terminan compartiendo el MISMO bitmap en vez de tener uno cada una.
            cache[clave]?.let { yaEstaba ->
                tocar(clave)
                return@ejecutar yaEstaba
            }
            cache[clave] = decodificada
            tocar(clave)
            while (usoReciente.size > MAX_ENTRADAS) {
                // El primero de la lista es el que hace más tiempo que no se usa.
                cache.remove(usoReciente.removeAt(0))
            }
            decodificada
        }
    }

    /**
     * Suelta todo. Lo llama `MainActivity.onTrimMemory` bajo presión de memoria, igual que los
     * `clearCaches()` de los sprite managers (09 §6).
     */
    fun limpiar() = cerrojo.ejecutar {
        cache.clear()
        usoReciente.clear()
    }

    /** Entradas vivas. Solo para tests y diagnóstico. */
    fun tamano(): Int = cerrojo.ejecutar { cache.size }

    private fun tocar(clave: Clave) {
        usoReciente.remove(clave)
        usoReciente.add(clave)
    }
}
