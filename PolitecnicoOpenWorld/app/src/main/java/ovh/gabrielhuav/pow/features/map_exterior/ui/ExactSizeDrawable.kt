package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable

internal class ExactSizeDrawable(
    private val base: Drawable,
    private val exactWidthPx: Int,
    private val exactHeightPx: Int
) : Drawable() {
    override fun getIntrinsicWidth() = exactWidthPx
    override fun getIntrinsicHeight() = exactHeightPx
    override fun draw(canvas: Canvas) {
        val b = getBounds()
        base.setBounds(b.left, b.top, b.right, b.bottom)
        base.draw(canvas)
    }
    override fun setAlpha(alpha: Int) { base.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { base.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = base.opacity
}
