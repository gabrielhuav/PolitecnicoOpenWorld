package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import ovh.gabrielhuav.pow.domain.models.CarModel
import kotlin.math.roundToInt

object VehicleSpriteManager {
    // Mapa para almacenar los frames base de cada modelo de vehículo
    private val carFrames = CarModel.entries.associateWith { arrayOfNulls<Bitmap>(48) }

    // Cache key actualizada para incluir el CarModel
    private data class CacheKey(val frameIndex: Int, val colorInt: Int, val discretizedZoomStep: Int, val carModel: CarModel)

    // Se aumenta ligeramente el caché para soportar las variaciones de modelos
    private val drawableCache: LruCache<CacheKey, BitmapDrawable> = LruCache(512)

    fun getTintedCarNpc(context: Context, headingAngle: Float, colorInt: Int, zoomScale: Float, carModel: CarModel): Drawable? {
        var angle = headingAngle % 360f
        if (angle < 0) angle += 360f
        val frameIndex = (angle / 7.5f).roundToInt() % 48

        val discretizedZoomStep = (zoomScale / 0.05f).roundToInt()
        val key = CacheKey(frameIndex, colorInt, discretizedZoomStep, carModel)

        drawableCache.get(key)?.let { return it }

        val modelFrames = carFrames[carModel] ?: return null

        if (modelFrames[frameIndex] == null) {
            val indexStr = frameIndex.toString().padStart(3, '0')
            val fileName = "VEHICLES/${carModel.dirName}/${carModel.prefix}$indexStr.webp"
            try {
                context.assets.open(fileName).use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, fileName)
                    if (drawable is BitmapDrawable) {
                        modelFrames[frameIndex] = drawable.bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        val baseBitmap = modelFrames[frameIndex] ?: return null

        val finalWidth = (baseBitmap.width * zoomScale).roundToInt().coerceAtLeast(1)
        val finalHeight = (baseBitmap.height * zoomScale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(baseBitmap, finalWidth, finalHeight, false)

        val result = BitmapDrawable(context.resources, scaledBitmap).apply {
            val translucentColor = ColorUtils.setAlphaComponent(colorInt, 200)
            colorFilter = PorterDuffColorFilter(translucentColor, PorterDuff.Mode.SRC_ATOP)
        }
        drawableCache.put(key, result)
        return result
    }
}