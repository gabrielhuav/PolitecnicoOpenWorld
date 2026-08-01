package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.ui.graphics.ImageBitmap
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.platform.concurrencia.PowCerrojo
import ovh.gabrielhuav.pow.platform.imagen.PowImagen

/**
 * Muestreo del atlas DEDICADO al construir una vista previa.
 *
 * ⚠️ **Esto decide cómo de nítido se ve el selector, y ya se equivocó una vez.** En la app
 * publicada el recorte salía de `BitmapRegionDecoder.decodeRegion()`, o sea **a resolución
 * completa y sin materializar el atlas**. Al portar a KMP no hay region decoder, así que se pasó a
 * "decodifica el atlas entero reducido ×4 y recorta": la vista previa quedó a **1/16 de los
 * píxeles** y estirada a una card de 86 dp se veía a cuadros. Se notaba en los 15 peleadores de
 * atlas dedicado (los 3 de set compartido se arman desde sprites chicos y nunca cambiaron).
 *
 * El ×4 no era capricho: los atlas croma son 2560×7168 y a resolución completa son ~73 MB en
 * ARGB_8888. Por eso el muestreo **depende de la gama**, con la MISMA regla que la pelea ya usa
 * hoy en producción (`sheetSample = if (lowEnd) 2 else 1`, en `StreetFighterScreen`):
 *
 * - **Gama normal → 1.** Idéntico a lo publicado. El pico de 73 MB no es nuevo: la pelea ya lo
 *   paga en estos mismos teléfonos, y `SfSharedSheets.sheetFor` decodifica bajo cerrojo, así que
 *   nunca hay dos atlas a la vez por mucho que la rejilla pinte 18 cards.
 * - **Gama baja → 4.** Se queda como está: ahí el riesgo de OOM manda sobre la nitidez.
 */
private const val PREVIEW_SAMPLE_SIZE_GAMA_NORMAL = 1
private const val PREVIEW_SAMPLE_SIZE_GAMA_BAJA = 4

internal fun previewSampleSize(gamaBaja: Boolean): Int =
    if (gamaBaja) PREVIEW_SAMPLE_SIZE_GAMA_BAJA else PREVIEW_SAMPLE_SIZE_GAMA_NORMAL

private const val PREVIEW_SLOWDOWN = 1.8f
private const val PREVIEW_MIN_MS = 95L
private const val PREVIEW_SHARED_MS = 170L

/** Cuadros ya recortados de la vista previa de un peleador, con su ritmo. */
internal data class FighterPreviewAnimation(
    val frames: List<ImageBitmap>,
    val delaysMs: List<Long>,
)

/**
 * 🥊🐢 VISTAS PREVIAS DEL SELECTOR DE PELEADORES — construidas FUERA del hilo de UI y cacheadas.
 *
 * ## Por qué existe
 *
 * Construir una vista previa **no es barato**:
 *
 * - Peleador con **set compartido**: `previewFramesFor` carga todos los cuadros de Idle desde
 *   assets, recorta cada uno a su bbox opaco y normaliza la animación entera.
 * - Peleador con **atlas dedicado**: decodifica el atlas (reducido ×4) y recorta N celdas, cada
 *   una con otro recorte a bbox opaco encima.
 *
 * Todo eso vivía dentro de un `remember { }` de `rememberFighterPreview`, o sea **en el hilo de
 * composición**. Y el propio `SfSharedSheets.sheetFor` lo avisa en su KDoc: *"llamar fuera del hilo
 * de dibujo si se puede"*.
 *
 * El selector pinta la rejilla del roster (**18 peleadores**) mientras `SfSharedSheets` solo guarda
 * **3** hojas: recorrer la lista desalojaba y reconstruía hojas una y otra vez, en el hilo de UI.
 * En gama baja eso es el tirón clásico al abrir el selector y al hacer scroll.
 *
 * ## Qué cambia
 *
 * La construcción pasa a [construir], que se llama desde un hilo de fondo, y el resultado se guarda
 * aquí con tope LRU. Volver a ver un peleador ya visto **no reconstruye nada**.
 *
 * ⚠️ [construir] **DECODIFICA: no lo llames desde composición.** Usa `rememberFighterPreview`.
 *
 * ⚠️ El tope no es decorativo: sin él, recorrer el roster deja las 18 vistas previas vivas para
 * siempre. Es la misma regla que `nativeDrawableCache` del mapa (09 §6).
 */
internal object SfPreviewCache {

    /**
     * Cubre de sobra lo que cabe en pantalla y el scroll cercano, sin retener el roster entero.
     * Las vistas previas son sprites recortados (~100 px de alto), no atlas: cada entrada es chica.
     */
    private const val MAX_ENTRADAS = 8

    private data class Clave(val id: SfFighterId, val animate: Boolean, val gamaBaja: Boolean)

    private val cache = mutableMapOf<Clave, FighterPreviewAnimation>()
    private val usoReciente = mutableListOf<Clave>()
    private val cerrojo = PowCerrojo()

    /** Solo lee lo ya construido. Seguro desde composición: evita el frame en blanco al volver. */
    fun enMemoria(
        id: SfFighterId,
        animate: Boolean,
        gamaBaja: Boolean,
    ): FighterPreviewAnimation? = cerrojo.ejecutar {
        val clave = Clave(id, animate, gamaBaja)
        cache[clave]?.also { tocar(clave) }
    }

    /**
     * Devuelve la vista previa, construyéndola si hace falta.
     *
     * ⚠️ **LLAMAR FUERA DEL HILO DE UI.**
     */
    fun cargar(id: SfFighterId, animate: Boolean, gamaBaja: Boolean): FighterPreviewAnimation? {
        enMemoria(id, animate, gamaBaja)?.let { return it }
        // Fuera del cerrojo: es la parte lenta y bloquear aquí congelaría a las demás cards.
        val construida = construir(id, animate, gamaBaja) ?: return null
        return cerrojo.ejecutar {
            val clave = Clave(id, animate, gamaBaja)
            cache[clave]?.let { yaEstaba ->
                tocar(clave)
                return@ejecutar yaEstaba
            }
            cache[clave] = construida
            tocar(clave)
            while (usoReciente.size > MAX_ENTRADAS) {
                cache.remove(usoReciente.removeAt(0))
            }
            construida
        }
    }

    /** Suelta todo. Lo llama `MainActivity.onTrimMemory`, como los sprite managers (09 §6). */
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

    // ═════════════════════════ construcción (port literal de lo que había) ═════════════════════

    /**
     * ⚠️ Esto es el MISMO código que vivía dentro del `remember` de `rememberFighterPreview`, movido
     * tal cual. No se cambió ni un número: los cuadros y los tiempos tienen que salir idénticos.
     */
    private fun construir(
        id: SfFighterId,
        animate: Boolean,
        gamaBaja: Boolean,
    ): FighterPreviewAnimation? =
        runCatching {
            val muestreo = previewSampleSize(gamaBaja)
            val shared = id.sharedSet
            if (shared != null) {
                val raw = SfSharedSheets.previewFramesFor(shared)
                val frames = if (animate) {
                    raw.map { PowImagen.recortarAOpaco(it) }
                } else {
                    listOfNotNull(raw.firstOrNull()?.let { PowImagen.recortarAOpaco(it) })
                }
                FighterPreviewAnimation(frames, List(frames.size) { PREVIEW_SHARED_MS })
            } else {
                val data = SfFrameCatalog.load(id)
                val sheet = SfSharedSheets.sheetFor(id, sampleSize = muestreo)

                fun decodeState(
                    stateKey: String,
                    maxFrames: Int = Int.MAX_VALUE,
                ): Pair<List<ImageBitmap>, List<Long>> {
                    val steps = data.animations[stateKey].orEmpty()
                        .filter { it.delay > 0 }
                        .take(maxFrames)
                    val frames = steps.mapNotNull { step ->
                        val src = data.frames[step.frameKey]?.src ?: return@mapNotNull null
                        val x = src[0] / muestreo
                        val y = src[1] / muestreo
                        val width = (src[2] / muestreo).coerceAtLeast(1)
                        val height = (src[3] / muestreo).coerceAtLeast(1)
                        PowImagen.recortarAOpaco(PowImagen.recortar(sheet, x, y, width, height))
                    }
                    val delays = steps.take(frames.size).map {
                        (it.delay * SfConstants.FRAME_TIME_MS * PREVIEW_SLOWDOWN).toLong()
                            .coerceAtLeast(PREVIEW_MIN_MS)
                    }
                    return frames to delays
                }

                if (!animate) {
                    val (idleFrames, idleDelays) =
                        decodeState(SfFighterState.IDLE.jsKey, maxFrames = 1)
                    FighterPreviewAnimation(
                        idleFrames,
                        idleDelays.ifEmpty { listOf(PREVIEW_MIN_MS) },
                    )
                } else {
                    val (idleFrames, idleDelays) = decodeState(SfFighterState.IDLE.jsKey)
                    val (walkFrames, walkDelays) = decodeState(SfFighterState.WALK_FORWARD.jsKey)
                    FighterPreviewAnimation(idleFrames + walkFrames, idleDelays + walkDelays)
                }
            }
        }.getOrNull()?.takeIf { it.frames.isNotEmpty() }
}
