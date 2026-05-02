package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.Npc
import kotlin.math.max
import kotlin.math.roundToInt

object PedestrianSpriteManager {

    private data class PedestrianCacheKey(
        val bodyType: Int,
        val hairType: Int,
        val hairColor: Int,
        val shirtColor: Int,
        val isWalking: Boolean,
        val isFacingLeft: Boolean,
        val frameIndex: Int,
        val discretizedScale: Int // Nueva clave para el caché de escala
    )

    private val rawFramesCache = mutableMapOf<String, Bitmap>()
    private val compositeCache: LruCache<PedestrianCacheKey, Bitmap> = LruCache(256)

    // Ajuste de velocidad: de 150ms a 250ms para que sea más lento
    private const val BASE_SCALE = 0.20f
    private const val FRAME_DURATION_MS = 250L

    fun getPedestrianBitmap(context: Context, npc: Npc, zoomScale: Float): Bitmap? {
        val visuals = npc.visuals
        val time = System.currentTimeMillis()

        val frameCount = if (npc.isWalking) 4 else 2
        val frameIndex = ((time / FRAME_DURATION_MS) % frameCount).toInt() + 1

        // Discretizamos la escala para no saturar el caché con cada pequeño cambio de zoom
        val discretizedScale = (zoomScale * 20f).roundToInt()

        val key = PedestrianCacheKey(
            visuals.bodyType, visuals.hairType, visuals.hairColor, visuals.shirtColor,
            npc.isWalking, npc.isFacingLeft, frameIndex, discretizedScale
        )

        compositeCache.get(key)?.let { return it }

        val action = if (npc.isWalking) "walk" else "idle"
        val bodyPath = "assetsNPC/npc_${action}_${visuals.bodyType}/npc_${action}_${visuals.bodyType}_$frameIndex.webp"
        val hairPath = "assetsNPC/npc_hair/hair_${visuals.hairType}.webp"


        val baseBody = loadRawBitmap(context, bodyPath) ?: return null
        val hair = loadRawBitmap(context, hairPath)

        // AJUSTE 2: Calcular dimensiones máximas para evitar recortes
        // Añadimos un pequeño margen extra (padding) de seguridad
        val rawWidth = max(baseBody.width, hair?.width ?: 0)
        val rawHeight = max(baseBody.height, hair?.height ?: 0)
        val padding = 4 // Pixeles de margen

        val finalWidth = ((rawWidth + padding) * zoomScale * BASE_SCALE).roundToInt().coerceAtLeast(1)
        val finalHeight = ((rawHeight + padding) * zoomScale * BASE_SCALE).roundToInt().coerceAtLeast(1)

        val resultBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)

        // AJUSTE 3 : Escalado y centrado exacto sin pivote dinámico
        val scaleXFactor = finalWidth.toFloat() / (rawWidth + padding)
        val scaleYFactor = finalHeight.toFloat() / (rawHeight + padding)

        if (npc.isFacingLeft) {
            // Invertimos la escala en X para espejear
            canvas.scale(-scaleXFactor, scaleYFactor)
            // Al espejear, el canvas dibuja hacia los negativos.
            // Lo empujamos de vuelta a la zona visible usando el tamaño original (sin escalar)
            canvas.translate(-(rawWidth + padding).toFloat(), 0f)
        } else {
            // Escala normal
            canvas.scale(scaleXFactor, scaleYFactor)
        }

        // Dibujar desplazado por el padding para que la cabeza no toque el borde 0
        val drawX = padding / 2f
        val drawY = padding / 2f

        val bodyWithShirt = tintWhitePixels(baseBody, npc.visuals.shirtColor)
        val finalBodyBitmap = tintPantsPixels(bodyWithShirt, npc.visuals.pantsColor)

        canvas.drawBitmap(finalBodyBitmap, drawX, drawY, null)
        if (hair != null) {
            val hairPaint = Paint().apply {
                colorFilter = PorterDuffColorFilter(npc.visuals.hairColor, PorterDuff.Mode.MULTIPLY)
            }
            canvas.drawBitmap(hair, drawX, drawY, hairPaint)
        }

        compositeCache.put(key, resultBitmap)
        return resultBitmap
    }

    /**
     * Examina el bitmap buscando colores blancos/grises (baja saturación)
     * y los tiñe usando el targetColorInt, manteniendo las sombras (luminosidad).
     */
    private fun tintWhitePixels(baseBitmap: Bitmap, targetColorInt: Int): Bitmap {
        val width = baseBitmap.width
        val height = baseBitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetR = android.graphics.Color.red(targetColorInt)
        val targetG = android.graphics.Color.green(targetColorInt)
        val targetB = android.graphics.Color.blue(targetColorInt)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = android.graphics.Color.alpha(p)
            if (a == 0) continue // Ignorar transparencia pura

            val r = android.graphics.Color.red(p)
            val g = android.graphics.Color.green(p)
            val b = android.graphics.Color.blue(p)

            val maxColor = maxOf(r, g, b)
            val minColor = minOf(r, g, b)
            val sat = if (maxColor == 0) 0f else (maxColor - minColor) / maxColor.toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Si es un tono neutro (blanco/gris de la ropa) y medianamente luminoso
            // (Ajusta estos valores si ves que tiñe otras partes accidentalmente)
            if (sat < 0.20f && lum > 140f) {
                // Multiplicamos por la luminosidad original para preservar las sombras de las arrugas
                val lumFactor = lum / 255f
                val finalR = (targetR * lumFactor).toInt().coerceIn(0, 255)
                val finalG = (targetG * lumFactor).toInt().coerceIn(0, 255)
                val finalB = (targetB * lumFactor).toInt().coerceIn(0, 255)

                pixels[i] = android.graphics.Color.argb(a, finalR, finalG, finalB)
            } else {
                // Si es piel u otro color, se queda igual
                pixels[i] = p
            }
        }

        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }
    /**
     * Detecta píxeles en el rango de gris oscuro/fuerte y los tiñe.
     */
    private fun tintPantsPixels(baseBitmap: Bitmap, targetColorInt: Int): Bitmap {
        val width = baseBitmap.width
        val height = baseBitmap.height
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        baseBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val targetR = android.graphics.Color.red(targetColorInt)
        val targetG = android.graphics.Color.green(targetColorInt)
        val targetB = android.graphics.Color.blue(targetColorInt)

        for (i in pixels.indices) {
            val p = pixels[i]
            val a = android.graphics.Color.alpha(p)
            if (a == 0) continue

            val r = android.graphics.Color.red(p)
            val g = android.graphics.Color.green(p)
            val b = android.graphics.Color.blue(p)

            val maxColor = maxOf(r, g, b)
            val minColor = minOf(r, g, b)
            val sat = if (maxColor == 0) 0f else (maxColor - minColor) / maxColor.toFloat()
            val lum = 0.299f * r + 0.587f * g + 0.114f * b

            // Lógica para detectar el "gris fuerte" de los pantalones
            // Buscamos saturación baja (< 15%) y luminosidad entre 50 y 130
            if (sat < 0.15f && lum > 50f && lum < 135f) {
                val lumFactor = lum / 110f // Normalizamos base al gris medio
                val finalR = (targetR * lumFactor).toInt().coerceIn(0, 255)
                val finalG = (targetG * lumFactor).toInt().coerceIn(0, 255)
                val finalB = (targetB * lumFactor).toInt().coerceIn(0, 255)
                pixels[i] = android.graphics.Color.argb(a, finalR, finalG, finalB)
            } else {
                pixels[i] = p
            }
        }
        resultBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    private fun loadRawBitmap(context: Context, path: String): Bitmap? {
        if (rawFramesCache.containsKey(path)) return rawFramesCache[path]
        return try {
            context.assets.open(path).use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                rawFramesCache[path] = bitmap
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}