package ovh.gabrielhuav.pow.features.map_exterior.ui

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Overlay
import kotlin.math.sqrt

/**
 * Overlay de osmdroid que pinta la neblina (fog of war) centrada en la posición
 * GEOGRÁFICA del jugador. Al proyectarla en cada frame, la zona despejada sigue
 * al jugador durante scroll y zoom en vez de quedarse fija al centro de pantalla.
 * Extraído de NativeOsmMap.kt (mismo paquete `ui`). OPT FPS: a pie dibuja solo el rect
 * EXACTO de pantalla; solo al conducir (lienzo rotado) se sobredimensiona a la diagonal.
 */
internal class FogOverlay : Overlay() {
    var player: GeoPoint? = null
    var revealPx: Float = 300f
    var rotated: Boolean = false
    private val paint = android.graphics.Paint().apply { isAntiAlias = true }
    // OPT FPS/GC: arrays del gradiente reutilizados entre frames (RadialGradient COPIA su
    // contenido al construirse, así que mutar `fogStops[1]` cada frame es seguro). Antes se
    // asignaban un IntArray y un FloatArray nuevos en CADA draw (~30 Hz) → presión de GC en gama baja.
    private val fogColors = intArrayOf(0x00000000, 0x00000000, 0x80222A33.toInt())
    private val fogStops = floatArrayOf(0f, 0f, 1f)

    override fun draw(c: android.graphics.Canvas, pProjection: org.osmdroid.views.Projection) {
        val pl = player ?: return
        val pt = pProjection.toPixels(pl, null)
        val maxReveal = Math.min(c.width, c.height) * 0.40f
        val reveal = revealPx.coerceIn(40f, if (maxReveal > 40f) maxReveal else 40f)
        val outer = reveal * 1.8f
        val stop = (reveal / outer).coerceIn(0f, 0.99f)
        fogStops[1] = stop
        paint.shader = android.graphics.RadialGradient(
            pt.x.toFloat(), pt.y.toFloat(), outer,
            fogColors,
            fogStops,
            android.graphics.Shader.TileMode.CLAMP
        )
        // OPT FPS: a pie (sin rotación) basta el rect EXACTO de pantalla. Antes se dibujaba
        // SIEMPRE un rect de ~2×diagonal por lado (≈10× el área de pantalla) con un
        // RadialGradient — un overdraw enorme en CADA frame. Solo al CONDUCIR el lienzo está
        // rotado y necesita el sobredimensionado a la diagonal para no dejar huecos al girar.
        val w = c.width.toFloat(); val h = c.height.toFloat()
        if (!rotated) {
            c.drawRect(0f, 0f, w, h, paint)
        } else {
            val diag = sqrt(w * w + h * h)
            val cx = w / 2f; val cy = h / 2f
            c.drawRect(cx - diag, cy - diag, cx + diag, cy + diag, paint)
        }
    }
}
