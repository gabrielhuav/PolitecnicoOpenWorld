package ovh.gabrielhuav.pow.features.main_menu.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun decodeGalleryImage(bytes: ByteArray, maxDimension: Int, portraitCellSize: Int?): ImageBitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val width = portraitCellSize?.coerceAtMost(bounds.outWidth) ?: bounds.outWidth
    val height = portraitCellSize?.coerceAtMost(bounds.outHeight) ?: bounds.outHeight
    val options = BitmapFactory.Options().apply {
        inSampleSize = gallerySampleSize(width, height, maxDimension)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val bitmap = if (portraitCellSize != null) {
        // Compatible con minSdk 24; decodifica solo la celda, no el atlas completo de ~80 MB.
        @Suppress("DEPRECATION")
        val decoder = requireNotNull(BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false))
        try {
            decoder.decodeRegion(Rect(0, 0, width, height), options)
        } finally {
            decoder.recycle()
        }
    } else {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
    return requireNotNull(bitmap) { "No se pudo cargar la vista previa" }.asImageBitmap()
}
