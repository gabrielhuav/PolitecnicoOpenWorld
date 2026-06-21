package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache

/**
 * Gestor de sprites para los policías a pie (POLICE_COP).
 */
object PoliceNpcSpriteManager {

    private const val RUN_FOLDER = "SPRITES/POLICE"
    private const val RUN_PREFIX = "police_run_"
    private const val RUN_FRAMES = 6

    // Caché de Bitmaps crudos (carpeta/frame -> Bitmap)
    private val bitmapCache = LruCache<String, Bitmap?>(12)

    // Caché de Drawables finales (clave: "isAttacking_frame_roundedScale_facingRight")
    private val drawableCache = LruCache<String, BitmapDrawable?>(24)

    @Synchronized
    fun getDrawable(
        context: Context,
        isAttacking: Boolean,
        timeMs: Long,
        scale: Float,
        facingRight: Boolean
    ): BitmapDrawable? {
        val frameIndex = if (isAttacking) 1 else ((timeMs / 150L) % RUN_FRAMES).toInt() + 1
        val roundedScale = (Math.round(scale * 20f) / 20f).toFloat()
        val cacheKey = "${isAttacking}_${frameIndex}_${roundedScale}_${facingRight}"

        drawableCache.get(cacheKey)?.let { return it }

        val bitmap = loadFrame(context, RUN_FOLDER, RUN_PREFIX, frameIndex) ?: return null
        val drawable = buildDrawable(context, bitmap, roundedScale, facingRight)
        
        drawableCache.put(cacheKey, drawable)
        return drawable
    }

    @Synchronized
    fun clearCaches() {
        bitmapCache.evictAll()
        drawableCache.evictAll()
    }

    private fun loadFrame(context: Context, folder: String, prefix: String, index: Int): Bitmap? {
        val key = "${folder}_${prefix}_${index}"
        val cached = bitmapCache.get(key)
        if (cached != null) return cached

        return try {
            val path = "$folder/$prefix$index.webp"
            val bmp = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            bitmapCache.put(key, bmp)
            bmp
        } catch (e: Exception) {
            bitmapCache.put(key, null)
            null
        }
    }

    private fun buildDrawable(
        context: Context,
        bitmap: Bitmap,
        roundedScale: Float,
        facingRight: Boolean
    ): BitmapDrawable {
        val matrix = Matrix()
        matrix.postScale(roundedScale, roundedScale)
        if (!facingRight) {
            matrix.postScale(-1f, 1f, bitmap.width * roundedScale / 2f, 0f)
        }
        val scaled = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return BitmapDrawable(context.resources, scaled)
    }
}
