package ovh.gabrielhuav.pow.platform

import ovh.gabrielhuav.pow.features.streetfighter.ui.SfPreviewCache
import ovh.gabrielhuav.pow.platform.imagen.PowImagenCache

/**
 * 🧹 PUNTO ÚNICO PARA SOLTAR LA MEMORIA RECICLABLE DE `:shared`.
 *
 * ## Para qué sirve
 *
 * `:shared` guarda arte ya decodificado (imágenes de menú, vistas previas del selector de
 * peleadores) para no volver a decodificarlo en cada scroll. Todo eso es **reconstruible**: cuando
 * el sistema avisa de que va justo de memoria, se puede tirar sin perder nada.
 *
 * ## Cómo se usa
 *
 * En Android, desde `MainActivity.onTrimMemory`:
 *
 * ```
 * override fun onTrimMemory(level: Int) {
 *     super.onTrimMemory(level)
 *     if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
 *         PowCaches.liberarTodo()
 *     }
 * }
 * ```
 *
 * En iOS, desde el aviso equivalente del sistema
 * (`UIApplicationDidReceiveMemoryWarningNotification`).
 *
 * ## Por qué existe en vez de llamar a cada caché
 *
 * Las cachés de `:shared` son `internal`: desde `:app` **no se ven** (son módulos distintos). Se
 * podrían hacer públicas, pero entonces cualquiera podría manipularlas desde fuera, y sobre todo:
 * cada caché nueva obligaría a acordarse de añadir una línea en `MainActivity`.
 *
 * ⚠️ **REGLA PARA QUIEN AÑADA UNA CACHÉ NUEVA EN `:shared`:** añade su `limpiar()` a
 * [liberarTodo] y no toques nada más. Ese es el único sitio que hay que tocar. Si en vez de eso la
 * haces pública y la llamas desde `MainActivity`, funciona igual **hoy** — y el día que alguien
 * añada la siguiente caché, se olvidará, y la fuga no dará la cara hasta que un teléfono con poca
 * RAM se quede sin memoria en una sesión larga.
 */
object PowCaches {

    /** Suelta TODA la memoria reciclable de `:shared`. Es seguro llamarlo en cualquier momento. */
    fun liberarTodo() {
        PowImagenCache.limpiar()
        SfPreviewCache.limpiar()
    }
}
