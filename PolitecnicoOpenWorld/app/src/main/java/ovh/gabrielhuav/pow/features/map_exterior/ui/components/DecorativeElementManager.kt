package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.abs

object DecorativeElementManager {

    enum class Type(val emoji: String, val sizeDp: Int, val weight: Int) {
        TREE("🌳", 28, 30),
        BUSH("🌿", 22, 25),
        FLOWER("🌸", 16, 20),
        ROCK("🪨", 16, 15),
        SQUIRREL("🐿️", 14, 4),
        BIRD("🐦", 14, 4),
        CAT("🐈", 16, 2);

        companion object {
            val weighted: List<Type> by lazy {
                values().flatMap { t -> List(t.weight) { t } }
            }
        }
    }

    data class Element(val id: String, val lat: Double, val lon: Double, val type: Type)

    class LayerState {
        val markerCache = mutableMapOf<String, org.osmdroid.views.overlay.Marker>()
        var lastLat = Double.NaN
        var lastLon = Double.NaN
        var elements: List<Element> = emptyList()
    }

    private val drawableCache = mutableMapOf<String, Drawable>()

    fun getDrawable(context: Context, type: Type, sizePx: Int): Drawable {
        val key = "${type.name}_$sizePx"
        return drawableCache.getOrPut(key) { buildEmojiDrawable(context, type.emoji, sizePx) }
    }

    private fun buildEmojiDrawable(context: Context, emoji: String, sizePx: Int): Drawable {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.82f
        }
        val bounds = Rect()
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        canvas.drawText(emoji, sizePx / 2f, sizePx / 2f - bounds.exactCenterY(), paint)
        return BitmapDrawable(context.resources, bmp)
    }

    private const val CELL = 0.001
    private const val RADIUS = 4
    private const val MAX_ELEMENTS = 50
    private const val REGEN_THRESHOLD = 0.0015

    fun needsRegen(state: LayerState, lat: Double, lon: Double): Boolean =
        state.lastLat.isNaN() ||
            abs(lat - state.lastLat) > REGEN_THRESHOLD ||
            abs(lon - state.lastLon) > REGEN_THRESHOLD

    fun generate(playerLat: Double, playerLon: Double): List<Element> {
        val cx = (playerLon / CELL).toLong()
        val cy = (playerLat / CELL).toLong()
        val result = mutableListOf<Element>()

        for (ix in (cx - RADIUS)..(cx + RADIUS)) {
            for (iy in (cy - RADIUS)..(cy + RADIUS)) {
                val seed = ix * 397L xor (iy * 1_000_003L)
                val rng = java.util.Random(seed)
                val count = when (rng.nextInt(10)) {
                    in 0..3 -> 0
                    in 4..6 -> 1
                    in 7..8 -> 2
                    else    -> 3
                }
                repeat(count) { i ->
                    val lat = iy * CELL + rng.nextDouble() * CELL
                    val lon = ix * CELL + rng.nextDouble() * CELL
                    val type = Type.weighted[rng.nextInt(Type.weighted.size)]
                    result.add(Element("DEC_${ix}_${iy}_$i", lat, lon, type))
                }
            }
        }

        return result
            .sortedBy { e ->
                val dLat = e.lat - playerLat
                val dLon = e.lon - playerLon
                dLat * dLat + dLon * dLon
            }
            .take(MAX_ELEMENTS)
    }
}
