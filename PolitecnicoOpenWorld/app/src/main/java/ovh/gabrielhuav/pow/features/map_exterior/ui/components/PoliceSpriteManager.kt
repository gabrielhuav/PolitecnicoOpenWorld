package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlin.math.roundToInt

// Sprite manager de la PATRULLA. Mismo esquema top-down de 48 frames que los demás
// vehículos (VehicleSpriteManager), PERO sin repintado: el asset de policía ya tiene
// su diseño especial, así que solo lo escalamos según el zoom.
object PoliceSpriteManager {
    private const val DIR_NAME = "VEHICLES/POLICE_TOPDOWN"
    private const val PREFIX = "POLICE_CLEAN_ALLD"
    private const val FRAME_COUNT = 48

    private val frames = arrayOfNulls<Bitmap>(FRAME_COUNT)

    private data class CacheKey(val frameIndex: Int, val discretizedZoomStep: Int)
    private val drawableCache: LruCache<CacheKey, BitmapDrawable> = LruCache(192)

    @Synchronized
    fun getPoliceCar(context: Context, headingAngle: Float, zoomScale: Float): Drawable? {
        var angle = headingAngle % 360f
        if (angle < 0) angle += 360f
        val frameIndex = (angle / 7.5f).roundToInt() % FRAME_COUNT

        val discretizedZoomStep = (zoomScale / 0.05f).roundToInt()
        val key = CacheKey(frameIndex, discretizedZoomStep)
        drawableCache.get(key)?.let { return it }

        if (frames[frameIndex] == null) {
            // Padding de 4 dígitos: POLICE_CLEAN_ALLD0000.webp .. 0047.webp
            val indexStr = frameIndex.toString().padStart(4, '0')
            val fileName = "$DIR_NAME/$PREFIX$indexStr.webp"
            try {
                context.assets.open(fileName).use { inputStream ->
                    val drawable = Drawable.createFromStream(inputStream, fileName)
                    if (drawable is BitmapDrawable) frames[frameIndex] = drawable.bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        val baseBitmap = frames[frameIndex] ?: return null
        val finalWidth = (baseBitmap.width * zoomScale).roundToInt().coerceAtLeast(1)
        val finalHeight = (baseBitmap.height * zoomScale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(baseBitmap, finalWidth, finalHeight, false)

        val result = BitmapDrawable(context.resources, scaled)
        drawableCache.put(key, result)
        return result
    }

    // OPT memoria gama baja (≤2 GB): libera variantes escaladas y frames base bajo
    // presión de memoria (MainActivity.onTrimMemory). Se redecodifican bajo demanda.
    @Synchronized
    fun clearCaches() {
        drawableCache.evictAll()
        frames.fill(null)
    }
}
