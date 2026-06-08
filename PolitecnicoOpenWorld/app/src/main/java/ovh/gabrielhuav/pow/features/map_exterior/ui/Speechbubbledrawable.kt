package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * Crea un Drawable con forma de globo de texto (speech bubble) que contiene
 * el texto dado. Se usa para el diálogo flotante de Prankedy sobre el mapa
 * nativo (osmdroid). El estilo es minimalista con fondo oscuro y texto naranja
 * para coincidir con la paleta del personaje.
 *
 * @param context    contexto Android
 * @param text       texto del diálogo
 * @param widthPx    ancho máximo del globo en píxeles
 */
internal fun createSpeechBubbleDrawable(
    context: Context,
    text: String,
    widthPx: Int
): Drawable {
    val density = context.resources.displayMetrics.density
    val padding = (8 * density).toInt()
    val cornerRadius = 12 * density
    val tailHeight = (6 * density).toInt()
    val maxWidth = widthPx.coerceAtLeast((60 * density).toInt())

    // Paint para el texto.
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 111, 0) // naranja Prankedy
        textSize = 11 * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }

    // Medir texto (word-wrap simple).
    val maxTextWidth = maxWidth - padding * 2
    val lines = wrapText(text, textPaint, maxTextWidth.toFloat())

    val lineHeight = textPaint.descent() - textPaint.ascent()
    val textBlockHeight = (lineHeight * lines.size).toInt()
    val bubbleWidth = maxWidth
    val bubbleHeight = textBlockHeight + padding * 2
    val totalHeight = bubbleHeight + tailHeight

    val bitmap = Bitmap.createBitmap(bubbleWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Fondo del globo (rectángulo redondeado oscuro).
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 26, 10, 46)  // morado oscuro
        style = Paint.Style.FILL
    }
    val bgRect = RectF(0f, 0f, bubbleWidth.toFloat(), bubbleHeight.toFloat())
    canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

    // Borde naranja.
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 111, 0)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, borderPaint)

    // Cola (triángulo apuntando abajo, centrado).
    val tailPath = Path().apply {
        val cx = bubbleWidth / 2f
        moveTo(cx - 6 * density, bubbleHeight.toFloat())
        lineTo(cx, totalHeight.toFloat())
        lineTo(cx + 6 * density, bubbleHeight.toFloat())
        close()
    }
    canvas.drawPath(tailPath, bgPaint)

    // Texto.
    var y = padding - textPaint.ascent()
    for (line in lines) {
        canvas.drawText(line, padding.toFloat(), y, textPaint)
        y += lineHeight
    }

    return BitmapDrawable(context.resources, bitmap)
}

/** Word-wrap básico: parte el texto en líneas que quepan en maxWidth. */
private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var current = StringBuilder()

    for (word in words) {
        val test = if (current.isEmpty()) word else "$current $word"
        if (paint.measureText(test) <= maxWidth) {
            current = StringBuilder(test)
        } else {
            if (current.isNotEmpty()) lines.add(current.toString())
            current = StringBuilder(word)
        }
    }
    if (current.isNotEmpty()) lines.add(current.toString())
    return lines.ifEmpty { listOf(text) }
}