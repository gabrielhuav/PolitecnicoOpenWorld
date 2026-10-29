package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherZone

// ────────────────────────────────────────────────────────────────────────────
// 🆕 EXAMEN EXTRAORDINARIO: la JERGA que marca en el piso dónde pararse para el Extraordinario.
//
// Es PIXEL ART dibujado con rectángulos (1 píxel de jerga = 1 px de mundo, igual que los sprites),
// así se ve nítido a cualquier escala y no hace falta cargar otra imagen. El patrón se sacó de
// una foto de jerga: base de SARGA (tejido cruzado en diagonal) rosa/blanca, bandas rojas con
// hilos amarillos y oscuros, y líneas moradas. ROJA = fuera de rango; VERDE = bien parado.
// ────────────────────────────────────────────────────────────────────────────

/** Tipo de cada columna del mosaico (48 px; se repite a lo ancho). '.' = sarga base. */
private const val JERGA_TILE = "..RRYDDYRR.....P.D.......RYDYR........P..D......"

private const val JERGA_ROWS = 8

private class JergaPalette(val light: Color, val base: Color, val band: Color)

private val JERGA_RED = JergaPalette(light = Color(0xFFEEDEE0), base = Color(0xFFC24E5E), band = Color(0xFFB3263A))
private val JERGA_GREEN = JergaPalette(light = Color(0xFFEEDEE0), base = Color(0xFF5EAE6C), band = Color(0xFF2E8B3A))
private val JERGA_YELLOW = Color(0xFFE0B530)
private val JERGA_DARK = Color(0xFF3A1E22)
private val JERGA_PURPLE = Color(0xFF5B3C8C)

private fun jergaColor(p: JergaPalette, x: Int, y: Int): Color {
    val c = when (JERGA_TILE[x % JERGA_TILE.length]) {
        'R' -> p.band
        'Y' -> JERGA_YELLOW
        'D' -> JERGA_DARK
        'P' -> JERGA_PURPLE
        else -> if ((x + y) % 3 == 0) p.light else p.base // sarga en diagonal
    }
    // Canto inferior en sombra: le da volumen de trapo tendido.
    return if (y == JERGA_ROWS - 1) Color(c.red * 0.7f, c.green * 0.7f, c.blue * 0.7f, c.alpha) else c
}

/**
 * Tiende la jerga sobre [zone] (x de mundo), pegada al piso. Para no dibujar un rectángulo por
 * píxel, cada fila junta los píxeles contiguos del mismo color en un solo rectángulo.
 */
internal fun DrawScope.drawJerga(ctx: SceneCtx, zone: SfFinisherZone, inRange: Boolean) {
    val width = zone.width.toInt()
    if (width <= 2) return
    val pal = if (inRange) JERGA_GREEN else JERGA_RED
    val px = ctx.scale
    val left = ctx.ox + (zone.minX - ctx.camX) * px
    // Arriba del piso un par de px (se ve bajo los pies) y el resto hacia el frente.
    val top = ctx.oy + (SfConstants.STAGE_FLOOR - 3f - ctx.camY) * px

    for (y in 0 until JERGA_ROWS) {
        var runStart = 0
        var runColor = jergaColor(pal, 0, y)
        for (x in 1..width) {
            val c = if (x < width) jergaColor(pal, x, y) else Color.Transparent
            if (c != runColor) {
                drawRect(
                    color = runColor,
                    topLeft = Offset(left + runStart * px, top + y * px),
                    size = Size((x - runStart) * px + 0.5f, px + 0.5f), // +0.5: sin rendijas
                )
                runStart = x
                runColor = c
            }
        }
    }
    // Flecos deshilachados en los extremos (un píxel sí, uno no).
    for (y in 0 until JERGA_ROWS - 1 step 2) {
        drawRect(pal.light, Offset(left - px, top + y * px), Size(px, px))
        drawRect(pal.light, Offset(left + width * px, top + y * px), Size(px, px))
    }
}
