package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

object VehicleSpriteManager {
    private val sedanFrames = arrayOfNulls<Bitmap>(48)

    // Cache key: (frameIndex, colorInt, discretizedZoom) -> tinted+scaled BitmapDrawable
    // Zoom is discretized to steps of 0.05 to bound cache size while avoiding per-tick allocations.
    private data class CacheKey(val frameIndex: Int, val colorInt: Int, val discretizedZoomStep: Int)

    // 48 frames × ~4 common car colors × ~a few zoom levels fits well within 256 entries,
    // while keeping memory usage bounded (each entry holds a single small scaled Bitmap).
    private val drawableCache: LruCache<CacheKey, BitmapDrawable> = LruCache(256)

    fun getTintedCarNpc(context: Context, headingAngle: Float, colorInt: Int, zoomScale: Float): Drawable? {
        var angle = headingAngle % 360f
        if (angle < 0) angle += 360f
        val frameIndex = (angle / 7.5f).roundToInt() % 48

        // Discretize zoom to steps of 0.05 to limit cache entries while avoiding per-tick re-allocs
        val discretizedZoomStep = (zoomScale / 0.05f).roundToInt()
        val key = CacheKey(frameIndex, colorInt, discretizedZoomStep)

        drawableCache.get(key)?.let { return it }

        // Cargar el bitmap original en caché si no existe (sin escalar aún)
        if (sedanFrames[frameIndex] == null) {
            val indexStr = frameIndex.toString().padStart(3, '0')
            val fileName = "VEHICLES/WHITE_SEDAN/White_SEDAN_CLEAN_All_$indexStr.webp"
            try {
                // Usamos inputStream en lugar de is para evitar conflictos con Kotlin
                context.assets.open(fileName).use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, fileName)
                    if (drawable is BitmapDrawable) {
                        sedanFrames[frameIndex] = drawable.bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        val baseBitmap = sedanFrames[frameIndex] ?: return null

        // Aplicar el nuevo tamaño dinámico basado en el zoom.
        // filter=false preserves pixel-art sharpness (nearest-neighbour scaling).
        val finalWidth = (baseBitmap.width * zoomScale).roundToInt().coerceAtLeast(1)
        val finalHeight = (baseBitmap.height * zoomScale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(baseBitmap, finalWidth, finalHeight, false)

        val result = BitmapDrawable(context.resources, scaledBitmap).apply {
            // MAGIA DEL COLOR: Hacemos el color un 80% opaco (200/255)
            val translucentColor = ColorUtils.setAlphaComponent(colorInt, 200)

            // SRC_ATOP pinta encima de los píxeles no transparentes,
            // y al tener opacidad 200, deja ver los faros blancos y llantas negras originales.
            colorFilter = PorterDuffColorFilter(translucentColor, PorterDuff.Mode.SRC_ATOP)
        }
        drawableCache.put(key, result)
        return result
    }
}