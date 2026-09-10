package ovh.gabrielhuav.pow.platform.imagen

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import kotlin.math.max

/**
 * En iOS decodifica y escala en UN paso de Skia, y el buffer grande queda del lado NATIVO.
 *
 * ⚠️ SIGUE SIN SER EL `inSampleSize` DE ANDROID, y conviene no venderlo como tal. Skiko no expone
 * decodificación submuestreada: su `Codec` de Kotlin solo tiene `readPixels`, sin sample size.
 * (`getScaledDimensions` SÍ aparece al buscar cadenas en el klib, pero es el símbolo C++ de
 * `SkCodecImageGenerator` dentro de Skia, no API que se pueda llamar desde Kotlin.) Así que el mapa
 * de bits completo SE MATERIALIZA igual — y sí sería evitable en teoría, porque los atlas son WebP
 * (no PNG) y el códec WebP de Skia sí sabe escalar al decodificar; lo que falta es la costura de
 * Skiko. Lo que cambia respecto a la versión anterior (`decodificar()` + [PowImagen.escalar]) es
 * DE QUIÉN es ese buffer y CUÁNTO vive:
 *
 * - Antes: el atlas completo se convertía en un `ImageBitmap` administrado por el GC de
 *   Kotlin/Native. Se volvía basura enseguida, pero su liberación esperaba a una pasada del
 *   recolector; con atlas de 2560×7168 (~73 MB) y dos peleadores en pantalla, esa espera es
 *   justo el momento de mayor presión de memoria.
 * - Ahora: el buffer completo lo posee la [Image] de Skia y se libera en su `close()`, de forma
 *   DETERMINISTA, apenas termina el escalado. Solo queda retenido el bitmap ya reducido.
 *
 * Si algún día hace falta bajar también el PICO (no solo su duración), no hay otra llamada de Skiko
 * que lo consiga: o se generan los atlas ya mipmapeados, o se baja a la API C de Skia por cinterop.
 */
actual fun decodificarReducido(bytes: ByteArray, reduccion: Int): ImageBitmap {
    val factor = reduccion.coerceAtLeast(1)
    val codificada = Image.makeFromEncoded(bytes)
    // `factor == 1` NO se atiende aparte a propósito: `scalePixels` a la misma medida es una copia
    // directa, así que hay un solo camino que probar y la `Image` se cierra siempre igual.
    val ancho = max(1, codificada.width / factor)
    val alto = max(1, codificada.height / factor)
    val destino = Bitmap()
    // N32 con alfa (`opaque = false`, el valor por omisión): equivale al ARGB_8888 de Android, que
    // es obligatorio aquí porque los sprites recortan por transparencia.
    destino.allocN32Pixels(ancho, alto)
    val pixeles = destino.peekPixels()
        ?: error("Skia no entregó el pixmap destino de ${ancho}x$alto")
    // Bilineal, el mismo filtrado que usaba `PowImagen.escalar` (FilterQuality.Low).
    val escalado = codificada.scalePixels(pixeles, SamplingMode.LINEAR, false)
    // Cerrar ANTES de comprobar el resultado: si el escalado falló, el buffer grande igual se libera.
    codificada.close()
    check(escalado) { "Skia no pudo escalar la imagen a ${ancho}x$alto (reducción $factor)" }
    destino.setImmutable()
    return destino.asComposeImageBitmap()
}
