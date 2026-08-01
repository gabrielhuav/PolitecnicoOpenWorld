package ovh.gabrielhuav.pow.platform.imagen

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes an asset close to the size it will occupy on screen.
 *
 * Reading the bounds first avoids materializing the full bitmap only to shrink it afterwards.
 * Android's decoder accepts powers of two efficiently and keeps the decoded image at least as
 * large as the requested box, so small map icons remain sharp without retaining multi-megabyte
 * textures.
 */
internal fun AssetManager.decodeAssetSampled(
    assetPath: String,
    requestedWidth: Int,
    requestedHeight: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open(assetPath).use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            requestedWidth = requestedWidth,
            requestedHeight = requestedHeight,
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return open(assetPath).use { BitmapFactory.decodeStream(it, null, options) }
        ?.also(Bitmap::prepareToDraw)
}

/** Returns the largest power-of-two sample that still covers the requested dimensions. */
internal fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    requestedWidth: Int,
    requestedHeight: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || requestedWidth <= 0 || requestedHeight <= 0) {
        return 1
    }

    var sampleSize = 1
    while (
        sourceWidth / (sampleSize * 2) >= requestedWidth &&
        sourceHeight / (sampleSize * 2) >= requestedHeight
    ) {
        sampleSize *= 2
    }
    return sampleSize
}
