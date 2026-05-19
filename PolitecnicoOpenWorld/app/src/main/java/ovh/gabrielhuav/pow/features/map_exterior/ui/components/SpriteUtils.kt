package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context

// Función auxiliar puramente matemática para inyectar la barra de vida a los sprites en memoria
fun drawHealthBarOnDrawable(
    context: Context,
    original: android.graphics.drawable.Drawable?,
    health: Float,
    isDying: Boolean
): android.graphics.drawable.Drawable? {
    if (original !is android.graphics.drawable.BitmapDrawable || health >= 100f || isDying) {
        return original
    }

    val originalBitmap = original.bitmap
    val mutableBitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(mutableBitmap)
    val paint = android.graphics.Paint()

    // 🌟 NUEVO TAMAÑO: 95% del ancho y 24 píxeles de grosor para máxima visibilidad
    val barWidth = mutableBitmap.width * 0.95f
    val barHeight = 100f
    val left = (mutableBitmap.width - barWidth) / 2f
    val top = 0f // Pegada completamente al techo del sprite

    // Dibujamos el fondo negro (marco grueso)
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(left, top, left + barWidth, top + barHeight, paint)

    // Color según el nivel de vida
    paint.color = when {
        health > 60f -> android.graphics.Color.GREEN
        health > 30f -> android.graphics.Color.YELLOW
        else -> android.graphics.Color.RED
    }

    // 🌟 Borde interior: Restamos 6 píxeles al ancho y damos un offset de +3
    // para crear un contorno negro tipo RPG muy marcado
    val healthWidth = (barWidth - 6f) * (health / 100f)
    if (healthWidth > 0) {
        canvas.drawRect(
            left + 3f,
            top + 3f,
            left + 3f + healthWidth,
            top + barHeight - 3f,
            paint
        )
    }

    return android.graphics.drawable.BitmapDrawable(context.resources, mutableBitmap)
}

class ExactSizeDrawable(
    private val base: android.graphics.drawable.Drawable,
    private val exactWidthPx: Int,
    private val exactHeightPx: Int
) : android.graphics.drawable.Drawable() {
    override fun getIntrinsicWidth() = exactWidthPx
    override fun getIntrinsicHeight() = exactHeightPx
    override fun draw(canvas: android.graphics.Canvas) {
        val b = this.getBounds()
        base.setBounds(b.left, b.top, b.right, b.bottom)
        base.draw(canvas)
    }
    override fun setAlpha(alpha: Int) { base.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { base.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = base.opacity
}
