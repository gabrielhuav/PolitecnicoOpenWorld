package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils
import kotlin.math.roundToInt

object VehicleSpriteManager {
    private val sedanFrames = arrayOfNulls<Bitmap>(48)

    fun getTintedCarNpc(context: Context, headingAngle: Float, colorInt: Int, zoomScale: Float): Drawable? {
        var angle = headingAngle % 360f
        if (angle < 0) angle += 360f
        val frameIndex = (angle / 7.5f).roundToInt() % 48

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

        // Aplicar el nuevo tamaño dinámico basado en el zoom
        val finalWidth = (baseBitmap.width * zoomScale).roundToInt().coerceAtLeast(1)
        val finalHeight = (baseBitmap.height * zoomScale).roundToInt().coerceAtLeast(1)

        val scaledBitmap = Bitmap.createScaledBitmap(baseBitmap, finalWidth, finalHeight, true)

        return BitmapDrawable(context.resources, scaledBitmap).apply {
            // MAGIA DEL COLOR: Hacemos el color un 80% opaco (200/255)
            val translucentColor = ColorUtils.setAlphaComponent(colorInt, 200)

            // SRC_ATOP pinta encima de los píxeles no transparentes,
            // y al tener opacidad 200, deja ver los faros blancos y llantas negras originales.
            colorFilter = PorterDuffColorFilter(translucentColor, PorterDuff.Mode.SRC_ATOP)
        }
    }
}