package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context

// Convierte un EMOJI (texto) en un Drawable cuadrado de tamaño dado. Se usa para los
// policías a pie, que no tienen asset de sprite propio.
internal fun emojiToDrawable(context: Context, emoji: String, sizePx: Int): android.graphics.drawable.Drawable {
    val size = sizePx.coerceAtLeast(8)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = size * 0.82f
    }
    val fm = paint.fontMetrics
    val y = size / 2f - (fm.ascent + fm.descent) / 2f
    canvas.drawText(emoji, size / 2f, y, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

// Pequeño círculo relleno (con borde) para dibujar la "bala" de la policía.
internal fun dotDrawable(context: Context, colorInt: Int, sizePx: Int): android.graphics.drawable.Drawable {
    val size = sizePx.coerceAtLeast(6)
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val r = size / 2f
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = colorInt }
    canvas.drawCircle(r, r, r - 1f, fill)
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = size * 0.12f
        color = android.graphics.Color.argb(220, 60, 30, 0)
    }
    canvas.drawCircle(r, r, r - 1f, stroke)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

internal fun drawHealthBarOnDrawable(context: Context, original: android.graphics.drawable.Drawable?, health: Float, isDying: Boolean): android.graphics.drawable.Drawable? {
    if (original !is android.graphics.drawable.BitmapDrawable || health >= 100f || isDying) return original
    val mutableBitmap = original.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(mutableBitmap)
    val paint = android.graphics.Paint().apply { isAntiAlias = true }
    // Tamaño PROPORCIONAL al sprite (ancho completo, alto ~14% del alto) para que, tras
    // escalar el sprite a su tamaño final, la barra se vea con un grosor similar al de la
    // barra del jugador (antes era un alto fijo de 18 px que al reducir el sprite quedaba
    // casi imperceptible).
    val barWidth = mutableBitmap.width.toFloat()
    val barHeight = (mutableBitmap.height * 0.14f).coerceAtLeast(8f)
    val pad = barHeight * 0.2f
    val left = 0f
    val top = 0f
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(left, top, left + barWidth, top + barHeight, paint)
    paint.color = when { health > 60f -> android.graphics.Color.GREEN; health > 30f -> android.graphics.Color.YELLOW; else -> android.graphics.Color.RED }
    val healthWidth = (barWidth - 2f * pad) * (health / 100f)
    if (healthWidth > 0) canvas.drawRect(left + pad, top + pad, left + pad + healthWidth, top + barHeight - pad, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, mutableBitmap)
}

// OPT GC gama baja: el efecto de "puerta brillante" se redibujaba creando un Bitmap
// ARGB_8888 NUEVO (+2 Paint) en CADA frame (~30 Hz por puerta) — una fuente fuerte de
// basura/GC. Reutilizamos un Bitmap/Canvas/Paint por bitmap-fuente; el shimmer sigue
// animándose (depende del tiempo): solo se redibuja sobre el MISMO lienzo, sin asignar
// un bitmap por frame. (Los shaders sí se recrean porque sus parámetros se animan, pero
// son baratos comparados con un bitmap completo.)
private class DoorFx(val out: android.graphics.Bitmap) {
    val canvas = android.graphics.Canvas(out)
    val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }
}

// Caché acotada fuente→DoorFx (las puertas activas son pocas). Acceso solo desde el hilo
// de UI (render de osmdroid), por eso no necesita sincronización.
private val doorFxCache = object : LinkedHashMap<android.graphics.Bitmap, DoorFx>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<android.graphics.Bitmap, DoorFx>?): Boolean = size > 6
}

internal fun buildDoorEffectBitmap(src: android.graphics.Bitmap, ctx: android.content.Context): android.graphics.Bitmap {
    val t = System.currentTimeMillis(); val cycle = (t % 2200L) / 2200f
    val bw = src.width.toFloat(); val bh = src.height.toFloat()
    // Reutiliza el DoorFx de esta fuente (si cambió de tamaño, se recrea).
    val fx = doorFxCache[src]?.takeIf { it.out.width == src.width && it.out.height == src.height }
        ?: DoorFx(android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888))
            .also { doorFxCache[src] = it }
    val out = fx.out
    val cv = fx.canvas
    out.eraseColor(android.graphics.Color.TRANSPARENT)
    cv.drawBitmap(src, 0f, 0f, null)
    val sx = cycle * (bw * 1.7f) - bw * 0.35f
    fx.fillPaint.shader = android.graphics.LinearGradient(sx, 0f, sx + bw * 0.25f, bh,
        intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(215, 255, 225, 70), android.graphics.Color.TRANSPARENT),
        floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
    fx.fillPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
    cv.drawRect(0f, 0f, bw, bh, fx.fillPaint)
    val sd = ctx.resources.displayMetrics.density; val sw = 3.5f * sd
    val ga = (125 + (130 * Math.sin(t / 360.0)).toInt()).coerceIn(0, 255)
    fx.strokePaint.strokeWidth = sw
    fx.strokePaint.color = android.graphics.Color.argb(ga, 255, 200, 0)
    fx.strokePaint.maskFilter = android.graphics.BlurMaskFilter(sw * 2.8f, android.graphics.BlurMaskFilter.Blur.OUTER)
    cv.drawRect(sw / 2f, sw / 2f, bw - sw / 2f, bh - sw / 2f, fx.strokePaint)
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
