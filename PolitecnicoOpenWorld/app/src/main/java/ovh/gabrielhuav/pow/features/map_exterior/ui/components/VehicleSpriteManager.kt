package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import kotlin.math.roundToInt

object VehicleSpriteManager {
    // Mapa para almacenar los frames base de cada modelo de vehículo
    private val carFrames = CarModel.entries.associateWith { arrayOfNulls<Bitmap>(48) }

    // Cache key actualizada para incluir el CarModel
    private data class CacheKey(val frameIndex: Int, val colorInt: Int, val discretizedZoomStep: Int, val carModel: CarModel)

    // Se aumenta ligeramente el caché para soportar las variaciones de modelos
    private val drawableCache: LruCache<CacheKey, BitmapDrawable> = LruCache(512)

    @Synchronized
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
            val fileName = "SPRITES/VEHICLES/${carModel.dirName}/${carModel.prefix}$indexStr.webp"
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

        // --- MAGIA A NIVEL DE PÍXEL ---
        val resultBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(finalWidth * finalHeight)
        scaledBitmap.getPixels(pixels, 0, finalWidth, 0, 0, finalWidth, finalHeight)

        val targetR = (colorInt ushr 16) and 0xFF
        val targetG = (colorInt ushr 8) and 0xFF
        val targetB = colorInt and 0xFF

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            if (a == 0) continue // Ignorar pixeles transparentes

            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF

            // 1. SATURACIÓN: Si el pixel ya tiene un color fuerte (luces rojas/intermitentes), lo conservamos
            val maxColor = maxOf(r, g, b)
            val minColor = minOf(r, g, b)
            val sat = if (maxColor == 0) 0f else (maxColor - minColor) / maxColor.toFloat()

            if (sat > 0.15f) {
                pixels[i] = p
                continue
            }

            // 2. LUMINOSIDAD
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // 3. UMBRALES
            var factor = 0f
            if (lum in 80f..245f) {
                factor = if (lum < 130f) {
                    (lum - 80f) / 50f // Transición rines
                } else if (lum > 235f) {
                    (245f - lum) / 10f // Transición faros
                } else {
                    1f // Carrocería plena
                }
            }

            if (factor > 0f) {
                // --- TRUCO DE VIVACIDAD (NUEVO) ---
                // Forzamos al color a brillar con intensidad pura
                val baseLum = 165f
                val colorMultiplier = (lum / baseLum)

                val multR = (targetR * colorMultiplier).toInt().coerceIn(0, 255)
                val multG = (targetG * colorMultiplier).toInt().coerceIn(0, 255)
                val multB = (targetB * colorMultiplier).toInt().coerceIn(0, 255)

                // Mezclamos respetando el factor de interpolación
                val finalR = (r + factor * (multR - r)).toInt()
                val finalG = (g + factor * (multG - g)).toInt()
                val finalB = (b + factor * (multB - b)).toInt()

                pixels[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
            } else {
                pixels[i] = p
            }
        }
        resultBitmap.setPixels(pixels, 0, finalWidth, 0, 0, finalWidth, finalHeight)

        val result = BitmapDrawable(context.resources, resultBitmap)
        drawableCache.put(key, result)
        return result
    }

    // OPT memoria gama baja (≤2 GB): libera las variantes escaladas/tintadas y los frames
    // base bajo presión de memoria (MainActivity.onTrimMemory). Se vuelven a decodificar
    // bajo demanda. @Synchronized para no competir con getTintedCarNpc (mismo monitor).
    @Synchronized
    fun clearCaches() {
        drawableCache.evictAll()
        for (model in CarModel.entries) carFrames[model]?.fill(null)
    }
}