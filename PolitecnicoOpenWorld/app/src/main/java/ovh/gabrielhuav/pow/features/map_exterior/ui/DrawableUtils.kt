package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

internal fun drawHealthBarOnDrawable(
    context: Context,
    original: Drawable?,
    health: Float,
    isDying: Boolean
): Drawable? {
    if (original !is BitmapDrawable || health >= 100f || isDying) return original

    val originalBitmap = original.bitmap
    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)
    val paint = Paint()

    val barWidth = mutableBitmap.width * 0.95f
    val barHeight = 100f
    val left = (mutableBitmap.width - barWidth) / 2f
    val top = 0f

    paint.color = Color.BLACK
    canvas.drawRect(left, top, left + barWidth, top + barHeight, paint)

    paint.color = when {
        health > 60f -> Color.GREEN
        health > 30f -> Color.YELLOW
        else         -> Color.RED
    }

    val healthWidth = (barWidth - 6f) * (health / 100f)
    if (healthWidth > 0) {
        canvas.drawRect(left + 3f, top + 3f, left + 3f + healthWidth, top + barHeight - 3f, paint)
    }

    return BitmapDrawable(context.resources, mutableBitmap)
}
