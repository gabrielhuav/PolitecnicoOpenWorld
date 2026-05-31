package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
internal fun drawHealthBarOnDrawable(context: Context, original: android.graphics.drawable.Drawable?, health: Float, isDying: Boolean): android.graphics.drawable.Drawable? {
    if (original !is android.graphics.drawable.BitmapDrawable || health >= 100f || isDying) return original
    val mutableBitmap = original.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(mutableBitmap)
    val paint = android.graphics.Paint()
    val barWidth = mutableBitmap.width * 0.95f
    val barHeight = 10f
    val left = (mutableBitmap.width - barWidth) / 2f
    val top = 0f
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(left, top, left + barWidth, top + barHeight, paint)
    paint.color = when { health > 60f -> android.graphics.Color.GREEN; health > 30f -> android.graphics.Color.YELLOW; else -> android.graphics.Color.RED }
    val healthWidth = (barWidth - 6f) * (health / 100f)
    if (healthWidth > 0) canvas.drawRect(left + 3f, top + 3f, left + 3f + healthWidth, top + barHeight - 3f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, mutableBitmap)
}

internal fun buildDoorEffectBitmap(src: android.graphics.Bitmap, ctx: android.content.Context): android.graphics.Bitmap {
    val t = System.currentTimeMillis(); val cycle = (t % 2200L) / 2200f
    val bw = src.width.toFloat(); val bh = src.height.toFloat()
    val out = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out); cv.drawBitmap(src, 0f, 0f, null)
    val sx = cycle * (bw * 1.7f) - bw * 0.35f
    cv.drawRect(0f, 0f, bw, bh, android.graphics.Paint().apply {
        isAntiAlias = true
        shader = android.graphics.LinearGradient(sx, 0f, sx + bw * 0.25f, bh,
            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(215, 255, 225, 70), android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
    })
    val sd = ctx.resources.displayMetrics.density; val sw = 3.5f * sd
    val ga = (125 + (130 * Math.sin(t / 360.0)).toInt()).coerceIn(0, 255)
    cv.drawRect(sw / 2f, sw / 2f, bw - sw / 2f, bh - sw / 2f, android.graphics.Paint().apply {
        isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = sw
        color = android.graphics.Color.argb(ga, 255, 200, 0)
        maskFilter = android.graphics.BlurMaskFilter(sw * 2.8f, android.graphics.BlurMaskFilter.Blur.OUTER)
    })
    return out
}

internal class ExactSizeDrawable(
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
    @Deprecated("Deprecated in Java") override fun getOpacity() = base.opacity
}

fun getAssetFile(context: Context, assetPath: String, fileName: String): java.io.File {
    val file = java.io.File(context.cacheDir, fileName)
    if (!file.exists()) {
        context.assets.open(assetPath).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return file
}
