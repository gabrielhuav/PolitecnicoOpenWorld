package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import kotlin.math.roundToInt

object VehicleSpriteManager {
    // Mapa para almacenar los frames base de cada modelo de vehículo.
    // El tamaño del buffer es POR MODELO (carModel.frameCount), así un asset con otro
    // número de frames de rotación funciona sin tocar este manager.
    private val carFrames = CarModel.entries.associateWith { model -> arrayOfNulls<Bitmap>(model.frameCount) }

    // Cache key actualizada para incluir el CarModel
    private data class CacheKey(val frameIndex: Int, val colorInt: Int, val discretizedZoomStep: Int, val carModel: CarModel)

    // Se aumenta ligeramente el caché para soportar las variaciones de modelos
    private val drawableCache: LruCache<CacheKey, BitmapDrawable> = LruCache(512)

    @Synchronized
    fun getTintedCarNpc(context: Context, headingAngle: Float, colorInt: Int, zoomScale: Float, carModel: CarModel): Drawable? {
        var angle = headingAngle % 360f
        if (angle < 0) angle += 360f
        // Frame de rotación según el nº de frames del MODELO (no fijo a 48): paso = 360/frameCount.
        val frameCount = carModel.frameCount
        val frameIndex = (angle / (360f / frameCount)).roundToInt() % frameCount

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
                android.util.Log.e("DetektFix", "Error atrapado", e)
                return null
            }
        }

        val baseBitmap = modelFrames[frameIndex] ?: return null

        val finalWidth = (baseBitmap.width * zoomScale).roundToInt().coerceAtLeast(1)
        val finalHeight = (baseBitmap.height * zoomScale).roundToInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(baseBitmap, finalWidth, finalHeight, false)

        // Assets PRE-COLOREADOS (tintable=false): se dibujan tal cual, sin palette swap (ignora colorInt).
        if (!carModel.tintable) {
            val result = BitmapDrawable(context.resources, scaledBitmap)
            drawableCache.put(key, result)
            return result
        }

        // --- REPINTADO DE CARROCERÍA (solo modelos de base BLANCA repintable) ---
        // ⚠️ El algoritmo VIVE EN `commonMain` (`TintadoVehiculo.kt`) y lo comparte iOS. Aquí solo
        // queda el ir y venir de píxeles con `Bitmap`, que sí es de Android. Si hay que tocar los
        // umbrales, se tocan allá: si se duplican, los coches acaban de distinto color en cada
        // plataforma y no lo caza ningún test de aquí.
        val resultBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(finalWidth * finalHeight)
        scaledBitmap.getPixels(pixels, 0, finalWidth, 0, 0, finalWidth, finalHeight)

        tintarCarroceria(pixels, colorInt)

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