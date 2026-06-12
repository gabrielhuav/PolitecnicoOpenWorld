package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import kotlin.math.roundToInt

/**
 * Gestor de sprites del NPC especial Prankedy.
 *
 * Assets en assets/assetsNPC/Prankedy/:
 *   p_idle/p_idle_#.webp         → IDLE (quieto esperando)
 *   p_walk/p_walk_#.webp         → caminar (siguiendo al jugador)
 *   p_run/p_run_#.webp           → correr (alcanzando al jugador)
 *   p_run_tanque/p_run_tanque_#.webp → correr con tanque (yendo a atacar)
 *   p_atack/p_atack_#.webp       → atacar (lanzamiento de objeto)
 *   p_objeto/p_ob_#.webp         → objeto en el aire (proyectil)
 */
object PrankedySpriteManager {

    enum class PrankedyAnim(
        val folder: String,
        val prefix: String,
        val frameCount: Int,
        val frameDurationMs: Long
    ) {
        IDLE("p_idle", "p_idle_", 3, 260L),
        WALK("p_walk", "p_walk_", 9, 180L),
        RUN("p_run", "p_run_", 8, 140L),
        RUN_TANK("p_run_tanque", "p_run_tanque_", 9, 140L),
        ATTACK("p_atack", "p_atack_", 5, 120L),
        PROJECTILE("p_objeto", "p_ob_", 3, 100L)
    }

    // Caché LRU por firma visual (animación + frame + escala + espejo).
    private val drawableCache = object : LruCache<String, BitmapDrawable>(64) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int =
            value.bitmap.allocationByteCount / 1024
    }

    /**
     * Obtiene el Drawable del frame correspondiente al instante [timeMs].
     * Devuelve null si el asset no existe.
     */
    @Synchronized
    fun getDrawable(
        context: Context,
        anim: PrankedyAnim,
        facingRight: Boolean,
        timeMs: Long,
        scale: Float
    ): BitmapDrawable? {
        val frameIndex = ((timeMs / anim.frameDurationMs) % anim.frameCount).toInt()
        val roundedScale = (scale * 20f).roundToInt() / 20f
        val cacheKey = "${anim.name}_${facingRight}_${frameIndex}_${roundedScale}"

        drawableCache.get(cacheKey)?.let { return it }

        val assetPath = "assetsNPC/Prankedy/${anim.folder}/${anim.prefix}${frameIndex + 1}.webp"

        return try {
            context.assets.open(assetPath).use { inputStream ->
                val originalBmp = BitmapFactory.decodeStream(inputStream) ?: return fallbackEmoji(context, cacheKey, scale)
                val matrix = android.graphics.Matrix().apply {
                    postScale(roundedScale, roundedScale)
                    if (!facingRight) postScale(-1f, 1f)
                }
                val scaledBmp = Bitmap.createBitmap(
                    originalBmp, 0, 0,
                    originalBmp.width, originalBmp.height,
                    matrix, true
                )
                val drawable = BitmapDrawable(context.resources, scaledBmp)
                drawableCache.put(cacheKey, drawable)
                drawable
            }
        } catch (_: Exception) {
            fallbackEmoji(context, cacheKey, scale)
        }
    }

    /**
     * Obtiene el Drawable del proyectil (objeto lanzado) en el frame dado por [timeMs].
     */
    @Synchronized
    fun getProjectileDrawable(
        context: Context,
        timeMs: Long,
        scale: Float
    ): BitmapDrawable? = getDrawable(context, PrankedyAnim.PROJECTILE, true, timeMs, scale)

    /**
     * Fallback: si el asset no existe, dibuja un emoji 🤡 para que Prankedy
     * SIEMPRE sea visible en el mapa, aunque falten sprites.
     */
    private fun fallbackEmoji(context: Context, cacheKey: String, scale: Float): BitmapDrawable {
        val size = (48 * scale).roundToInt().coerceAtLeast(24)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size * 0.78f
        }
        val fm = paint.fontMetrics
        canvas.drawText("🤡", size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint)
        val d = BitmapDrawable(context.resources, bmp)
        drawableCache.put(cacheKey, d)
        return d
    }

    @Synchronized
    fun clearCaches() {
        drawableCache.evictAll()
    }
}