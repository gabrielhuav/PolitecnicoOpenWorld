package ovh.gabrielhuav.pow.features.main_menu.gallery

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.platform.assets.PowAssets

/** Caché propia de la pantalla: 12 miniaturas de hasta 512², liberadas al salir. */
internal class GalleryImageLoader {
    private val cache = linkedMapOf<GalleryImage, ImageBitmap>()
    private val mutex = Mutex()

    suspend fun load(image: GalleryImage): ImageBitmap = withContext(Dispatchers.Default) {
        // Una decodificación a la vez limita también el pico de memoria al desplazar la rejilla.
        mutex.withLock {
            cache.remove(image)?.let { cached ->
                cache[image] = cached
                return@withLock cached
            }
            val bitmap = decodeGalleryImage(PowAssets.bytes(image.previewPath), 512, image.portraitCellSize)
            cache[image] = bitmap
            while (cache.size > 12) cache.remove(cache.keys.first())
            bitmap
        }
    }

    // Una sola imagen de detalle, fuera de la caché de miniaturas. Siempre usa el original,
    // no el _thumb del mapa ni el recorte del retrato. La pantalla la libera al cerrar.
    suspend fun loadDetail(image: GalleryImage): ImageBitmap = withContext(Dispatchers.Default) {
        mutex.withLock {
            decodeGalleryImage(PowAssets.bytes(image.assetPath), 4096, null)
        }
    }
}

internal expect fun decodeGalleryImage(bytes: ByteArray, maxDimension: Int, portraitCellSize: Int?): ImageBitmap

internal fun gallerySampleSize(width: Int, height: Int, maxDimension: Int): Int {
    require(width > 0 && height > 0 && maxDimension > 0)
    var sampleSize = 1
    // También acota el área: una imagen cuadrada de 4096² consumiría 64 MB.
    val maxPixels = 6L * 1024 * 1024
    while ((width - 1) / sampleSize >= maxDimension || (height - 1) / sampleSize >= maxDimension ||
        (((width - 1L) / sampleSize) + 1) * (((height - 1L) / sampleSize) + 1) > maxPixels
    ) {
        sampleSize *= 2
    }
    return sampleSize
}
