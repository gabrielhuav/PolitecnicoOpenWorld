package ovh.gabrielhuav.pow.platform.imagen

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.compose.resources.decodeToImageBitmap
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 🍏 MANIPULACIÓN DE IMÁGENES, IGUAL EN ANDROID Y EN iOS.
 *
 * POR QUÉ NO HAY `expect/actual` AQUÍ: resulta que no hace falta. `ImageBitmap`, `Canvas`,
 * `CanvasDrawScope` y `readPixels` **ya son multiplataforma** en Compose Multiplatform (en iOS por
 * debajo hay Skia). O sea que el muro de `android.graphics` se cruza TRADUCIENDO llamadas, sin
 * inventar una capa por plataforma. Todo lo de este archivo es código común.
 *
 * EQUIVALENCIAS con lo que había en Android:
 *
 * | Antes (`android.graphics`)                   | Ahora                |
 * |----------------------------------------------|----------------------|
 * | `BitmapFactory.decodeStream(...)`            | [decodificar]        |
 * | `Bitmap.createBitmap(w, h, ARGB_8888)`       | [lienzo]             |
 * | `Bitmap.createScaledBitmap(b, w, h, true)`   | [escalar]            |
 * | `Bitmap.createBitmap(b, x, y, w, h)`         | [recortar]           |
 * | `Matrix().postRotate(g)` + createBitmap      | [rotar]              |
 * | `Matrix().preScale(-1f, 1f)` + createBitmap  | [voltearHorizontal]  |
 * | `getPixels(...)` buscando alfa               | [bboxOpaco]          |
 *
 * ⚠️ Aquí NO hay `recycle()`. En Android el código liberaba bitmaps a mano; con `ImageBitmap` eso
 * no existe. **Al portar se BORRAN las llamadas a `recycle()`, no se les busca equivalente.**
 */
object PowImagen {

    /** Decodifica PNG/WebP/JPEG. Sirve en las dos plataformas (en iOS lo hace Skia). */
    fun decodificar(bytes: ByteArray): ImageBitmap = bytes.decodeToImageBitmap()

    /** Decodifica un asset por su ruta de siempre (`"STREETFIGHTER/..."`). */
    fun deAsset(ruta: String): ImageBitmap = decodificar(PowAssets.bytes(ruta))

    /**
     * Crea un lienzo transparente de [ancho]×[alto] y ejecuta [dibujo] sobre él.
     *
     * Sustituye a `Bitmap.createBitmap(...)` + `Canvas(bmp)`: en Compose hay que montar a mano el
     * [CanvasDrawScope], que es justo lo que se encapsula aquí.
     *
     * ⚠️ `Density(1f)` NO es un detalle: dentro del lienzo se trabaja en PÍXELES, no en dp. Con la
     * densidad del dispositivo, las coordenadas del atlas (que son píxeles del sheet) saldrían
     * multiplicadas por 2 o por 3 según el teléfono.
     */
    fun lienzo(ancho: Int, alto: Int, dibujo: DrawScope.() -> Unit): ImageBitmap {
        val destino = ImageBitmap(
            width = max(1, ancho),
            height = max(1, alto),
            config = ImageBitmapConfig.Argb8888,
            // Con alfa: los sprites lo necesitan (por eso tampoco se puede usar RGB_565).
            hasAlpha = true,
        )
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(destino),
            size = Size(destino.width.toFloat(), destino.height.toFloat()),
            block = dibujo,
        )
        return destino
    }

    /** Copia [origen] a un lienzo de [ancho]×[alto] (equivalente a `createScaledBitmap`). */
    fun escalar(origen: ImageBitmap, ancho: Int, alto: Int): ImageBitmap {
        val w = max(1, ancho)
        val h = max(1, alto)
        return lienzo(w, h) {
            drawImage(
                image = origen,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(origen.width, origen.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(w, h),
                // Bilineal, que es lo que hacía `createScaledBitmap(..., filter = true)`.
                filterQuality = FilterQuality.Low,
            )
        }
    }

    /** Sub-rectángulo de [origen] (equivalente a `Bitmap.createBitmap(b, x, y, w, h)`). */
    fun recortar(origen: ImageBitmap, x: Int, y: Int, ancho: Int, alto: Int): ImageBitmap {
        val w = max(1, ancho)
        val h = max(1, alto)
        return lienzo(w, h) {
            drawImage(
                image = origen,
                srcOffset = IntOffset(x, y),
                srcSize = IntSize(w, h),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(w, h),
                // Sin filtrar: un recorte es copia 1:1 y filtrar solo emborronaría el borde.
                filterQuality = FilterQuality.None,
            )
        }
    }

    /**
     * Rota [grados] en sentido **ANTIHORARIO**, como `Image.rotate` de PIL.
     *
     * ⚠️ OJO AL SIGNO: el `rotate` de Compose (igual que `Matrix.postRotate` de Android) es
     * HORARIO, así que aquí se niega el ángulo. El código original ya hacía esa negación porque el
     * generador de sprites es un script de PIL; se conserva la convención para que los cuadros
     * salgan IDÉNTICOS a los de Android.
     *
     * El lienzo destino crece a la diagonal para que las esquinas no se corten al girar.
     */
    fun rotar(origen: ImageBitmap, grados: Float): ImageBitmap {
        if (grados == 0f) return origen
        val lado = max(origen.width, origen.height)
        val destino = (lado * 1.4142f).roundToInt() + 1
        return lienzo(destino, destino) {
            rotate(degrees = -grados, pivot = Offset(destino / 2f, destino / 2f)) {
                translate(
                    left = (destino - origen.width) / 2f,
                    top = (destino - origen.height) / 2f,
                ) {
                    drawImage(origen, filterQuality = FilterQuality.Low)
                }
            }
        }
    }

    /** Espejo horizontal (equivalente a `Matrix().preScale(-1f, 1f)`). */
    fun voltearHorizontal(origen: ImageBitmap): ImageBitmap =
        lienzo(origen.width, origen.height) {
            scale(scaleX = -1f, scaleY = 1f, pivot = Offset(origen.width / 2f, origen.height / 2f)) {
                drawImage(origen, filterQuality = FilterQuality.None)
            }
        }

    /**
     * Rectángulo que encierra los píxeles NO transparentes — el `Image.getbbox()` de PIL, que es
     * con lo que se generaron los sprites.
     *
     * Devuelve `null` si la imagen es enteramente transparente; quien llama decide qué hacer (el
     * código original devolvía la imagen entera en ese caso).
     */
    fun bboxOpaco(origen: ImageBitmap): BboxOpaco? {
        val ancho = origen.width
        val alto = origen.height
        val pixeles = IntArray(ancho * alto)
        origen.readPixels(pixeles, 0, 0, ancho, alto)
        var minX = ancho
        var minY = alto
        var maxX = -1
        var maxY = -1
        for (y in 0 until alto) {
            val base = y * ancho
            for (x in 0 until ancho) {
                // El alfa va en el byte alto; `ushr` para no arrastrar el bit de signo.
                if ((pixeles[base + x] ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else BboxOpaco(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /** Recorta al [bboxOpaco]; si todo es transparente devuelve la imagen tal cual. */
    fun recortarAOpaco(origen: ImageBitmap): ImageBitmap {
        val caja = bboxOpaco(origen) ?: return origen
        return recortar(origen, caja.x, caja.y, caja.ancho, caja.alto)
    }
}

/** Rectángulo de píxeles opacos devuelto por [PowImagen.bboxOpaco]. */
data class BboxOpaco(val x: Int, val y: Int, val ancho: Int, val alto: Int)
