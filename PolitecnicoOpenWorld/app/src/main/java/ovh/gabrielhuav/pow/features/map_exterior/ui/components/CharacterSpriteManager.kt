package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import java.io.InputStream

object CharacterSpriteManager {
    private const val TAG = "CharacterSpriteMgr"

    private val animationCache = object : LruCache<String, List<ImageBitmap>>(24) {}
    private val hairCache = object : LruCache<Int, ImageBitmap>(12) {}
    private val assembledCache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }
    private val drawableCache = object : LruCache<String, BitmapDrawable>(12 * 1024) {
        override fun sizeOf(key: String, value: BitmapDrawable): Int = value.bitmap.allocationByteCount / 1024
    }

    @Synchronized
    fun getAnimationFrames(context: Context, folder: String, prefix: String, frameCount: Int = 8): List<ImageBitmap> {
        val cacheKey = "${folder}_${prefix}"
        animationCache.get(cacheKey)?.let { return it }

        val frames = mutableListOf<ImageBitmap>()
        for (i in 1..frameCount) {
            try {
                val inputStream: InputStream = context.assets.open("SPRITES/NPC/$folder/${prefix}${i}.webp")
                frames.add(BitmapFactory.decodeStream(inputStream).asImageBitmap())
                inputStream.close()
            } catch (e: Exception) { break }
        }
        animationCache.put(cacheKey, frames)
        return frames
    }

    @Synchronized
    fun getHairSprite(context: Context, hairId: Int): ImageBitmap? {
        hairCache.get(hairId)?.let { return it }
        return try {
            val inputStream: InputStream = context.assets.open("SPRITES/NPC/hair/hair_$hairId.webp")
            val bmp = BitmapFactory.decodeStream(inputStream).asImageBitmap()
            hairCache.put(hairId, bmp)
            inputStream.close()
            bmp
        } catch (e: Exception) { null }
    }

    fun getFrameIndex(
        context: Context,
        visualConfig: CharacterVisualConfig,
        isMoving: Boolean,
        timeMs: Long
    ): Int? {
        val frames = getAnimationFrames(context, visualConfig.bodyFolder, visualConfig.bodyPrefix)
        if (frames.isEmpty()) return null
        return computeFrameIndex(visualConfig.bodyPrefix, frames.size, isMoving, timeMs)
    }

    @Synchronized
    fun generateAssembledBitmap(
        context: Context, visualConfig: CharacterVisualConfig, isMoving: Boolean, timeMs: Long
    ): Bitmap? = try {
        val frames = getAnimationFrames(context, visualConfig.bodyFolder, visualConfig.bodyPrefix)
        if (frames.isEmpty()) {
            null
        } else {
            val frameIndex = computeFrameIndex(visualConfig.bodyPrefix, frames.size, isMoving, timeMs)

            val cacheKey = "${visualConfig.bodyFolder}_${visualConfig.bodyPrefix}_${visualConfig.hairId}_${visualConfig.shirtColor.value}_${visualConfig.pantsColor.value}_${visualConfig.hairColor.value}_${frameIndex}"
            val cached = assembledCache.get(cacheKey)
            if (cached != null) {
                cached
            } else {
                val baseBitmap = frames[frameIndex].asAndroidBitmap()

                // 1. Pintamos el cuerpo de forma inteligente
                val tintedBody = tintSmartPixels(baseBitmap, visualConfig.shirtColor.toArgb(), visualConfig.pantsColor.toArgb())

                val resultBitmap = Bitmap.createBitmap(tintedBody.width, tintedBody.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(resultBitmap)
                canvas.drawBitmap(tintedBody, 0f, 0f, null)

                // 2. Pintamos y agregamos el cabello
                getHairSprite(context, visualConfig.hairId)?.let {
                    val tintedHair = tintSmartPixels(it.asAndroidBitmap(), visualConfig.hairColor.toArgb(), null)
                    canvas.drawBitmap(tintedHair, 0f, 0f, null)
                }

                assembledCache.put(cacheKey, resultBitmap)
                resultBitmap
            }
        }
    } catch (e: Exception) {
        // Si algún color viene corrupto (p.ej. deserialización defectuosa desde la red),
        // toArgb() puede explotar con ArrayIndexOutOfBoundsException. Atrapamos para que
        // un NPC malo no tire toda la app.
        Log.e(TAG, "Error generando bitmap del personaje: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    private fun applyMultiply(basePixel: Int, tintColor: Int): Int {
        val a = basePixel ushr 24
        val r = (((basePixel ushr 16) and 0xFF) * ((tintColor ushr 16) and 0xFF)) / 255
        val g = (((basePixel ushr 8) and 0xFF) * ((tintColor ushr 8) and 0xFF)) / 255
        val b = ((basePixel and 0xFF) * (tintColor and 0xFF)) / 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Genera un Drawable escalado y espejeado nativamente para los marcadores de OSMDroid.
     */
    @Synchronized
    fun getModularNpcDrawable(
        context: Context,
        visualConfig: CharacterVisualConfig,
        isMoving: Boolean,
        isFacingRight: Boolean,
        timeMs: Long,
        scale: Float,
        displayName: String? = null
    ): android.graphics.drawable.Drawable? = try {
        val baseBitmap = generateAssembledBitmap(context, visualConfig, isMoving, timeMs)
        if (baseBitmap == null) {
            null
        } else {
            val roundedScale = Math.round(scale * 20f) / 20f

            val frameIndex = getFrameIndex(context, visualConfig, isMoving, timeMs)
            if (frameIndex == null) {
                null
            } else {
                val cacheKey = "${visualConfig.bodyFolder}_${visualConfig.bodyPrefix}_${visualConfig.hairId}_${visualConfig.hairColor.value}_${visualConfig.shirtColor.value}_${visualConfig.pantsColor.value}_${frameIndex}_${isFacingRight}_${roundedScale}_${displayName ?: "none"}"

                val cachedDrawable = drawableCache.get(cacheKey)
                if (cachedDrawable != null) {
                    cachedDrawable
                } else {
                    // 1. Matriz para el personaje (Escala y Espejeo)
                    val matrix = android.graphics.Matrix()
                    matrix.postScale(roundedScale, roundedScale)
                    if (!isFacingRight) {
                        matrix.postScale(-1f, 1f)
                    }

                    val scaledBmp = Bitmap.createBitmap(
                        baseBitmap, 0, 0, baseBitmap.width, baseBitmap.height, matrix, true
                    )

                    // 2. Dibujo Combinado: Texto sobre el Personaje
                    val finalBmp = if (!displayName.isNullOrBlank()) {
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.BLACK
                            textSize = 50f * roundedScale.coerceAtLeast(0.4f)
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            setShadowLayer(4f, 1f, 1f, android.graphics.Color.YELLOW)
                        }

                        val textHeight = textPaint.descent() - textPaint.ascent()
                        val textWidth = textPaint.measureText(displayName)

                        val totalWidth = maxOf(scaledBmp.width, textWidth.toInt() + 20)
                        val paddingY = 8
                        val totalHeight = scaledBmp.height + textHeight.toInt() + paddingY

                        val compositeBmp = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(compositeBmp)

                        val textX = totalWidth / 2f
                        val textY = textHeight - textPaint.descent()
                        canvas.drawText(displayName, textX, textY, textPaint)

                        val charX = (totalWidth - scaledBmp.width) / 2f
                        val charY = textHeight + paddingY
                        canvas.drawBitmap(scaledBmp, charX, charY, null)

                        compositeBmp
                    } else {
                        scaledBmp
                    }

                    val drawable = BitmapDrawable(context.resources, finalBmp)
                    drawableCache.put(cacheKey, drawable)
                    drawable
                }
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error generando drawable NPC modular: ${e.javaClass.simpleName}: ${e.message}")
        null
    }

    fun clearCaches() {
        animationCache.evictAll()
        hairCache.evictAll()
        assembledCache.evictAll()
        drawableCache.evictAll()
    }

    private fun computeFrameIndex(bodyPrefix: String, frameCount: Int, isMoving: Boolean, timeMs: Long): Int {
        return if (isMoving) {
            ((timeMs / 220L) % frameCount).toInt()
        } else {
            if (bodyPrefix == "p_mult_" && frameCount >= 7) 6 else 0
        }
    }

    private fun tintSmartPixels(baseBitmap: Bitmap, color1: Int, color2: Int?): Bitmap {
        val width = baseBitmap.width
        val height = baseBitmap.height
        val pixels = IntArray(width * height)
        baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = pixel ushr 24
            if (a < 50) continue // Ignorar píxeles casi transparentes

            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF

            // 1. FILTRO DE GRISES: Para NO pintar la piel (que tiene tonos rojizos/cálidos)
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val diff = maxC - minC
            if (diff > 15) continue // Si tiene "color" (como la piel), NO LO PINTES

            // 2. SEPARACIÓN POR BRILLO (Luminancia)
            val brightness = (r + g + b) / 3

            if (brightness > 160) {
                // Blanco/Gris claro -> PLAYERA o CABELLO
                pixels[i] = applyMultiply(pixel, color1)
            } else if (brightness in 45..130 && color2 != null) {
                // Gris medio/oscuro -> PANTALONES
                pixels[i] = applyMultiply(pixel, color2)
            }
            // Los bordes (menor a 45) se quedan negros.
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}