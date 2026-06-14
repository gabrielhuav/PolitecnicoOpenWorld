// features/interiores/core/ui/CollisionMatrixDesignerLayer.kt
package ovh.gabrielhuav.pow.features.interiores.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/**
 * Capa de render/edición de la matriz de colisión (Modo Diseñador del minijuego
 * de zombis), análoga al Modo Diseñador del mapa principal.
 *
 * Usa exactamente la misma transformación mundo→pantalla que el resto de
 * entidades de ZombieGameScreen:
 *
 *     screenX = camOffsetX + worldX * camScale
 *     screenY = camOffsetY + worldY * camScale
 *
 * de modo que la rejilla queda perfectamente alineada con el fondo a cualquier
 * zoom/desplazamiento. La inversa (pantalla→mundo) se usa para saber qué celda
 * pinta el dedo.
 */
@Composable
fun CollisionMatrixDesignerLayer(
    enabled: Boolean,
    rows: List<String>,
    worldWidth: Float,
    worldHeight: Float,
    camOffsetX: Float,
    camOffsetY: Float,
    camScale: Float,
    onPaintWorld: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled || rows.isEmpty() || worldWidth <= 0f || worldHeight <= 0f || camScale <= 0f) return

    val numRows = rows.size
    val numCols = rows[0].length
    if (numCols == 0) return

    fun toScreenX(wx: Float) = camOffsetX + wx * camScale
    fun toScreenY(wy: Float) = camOffsetY + wy * camScale
    fun toWorldX(sx: Float) = (sx - camOffsetX) / camScale
    fun toWorldY(sy: Float) = (sy - camOffsetY) / camScale

    Canvas(
        modifier = modifier
            .pointerInput(enabled, numRows, numCols, camScale, camOffsetX, camOffsetY) {
                detectTapGestures { pos ->
                    onPaintWorld(toWorldX(pos.x), toWorldY(pos.y))
                }
            }
            .pointerInput(enabled, numRows, numCols, camScale, camOffsetX, camOffsetY) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onPaintWorld(toWorldX(change.position.x), toWorldY(change.position.y))
                }
            }
    ) {
        val cellW = worldWidth / numCols
        val cellH = worldHeight / numRows

        // Celdas-pared (rojo semitransparente).
        for (r in 0 until numRows) {
            val rowStr = rows[r]
            for (c in 0 until numCols) {
                if (c >= rowStr.length || rowStr[c] != '#') continue
                val tlX = toScreenX(c * cellW); val tlY = toScreenY(r * cellH)
                val brX = toScreenX((c + 1) * cellW); val brY = toScreenY((r + 1) * cellH)
                drawRect(
                    color = Color(0x66FF3B30),
                    topLeft = Offset(minOf(tlX, brX), minOf(tlY, brY)),
                    size = Size(abs(brX - tlX), abs(brY - tlY))
                )
            }
        }

        // Rejilla.
        val gridColor = Color(0x553A86FF)
        for (c in 0..numCols) {
            drawLine(gridColor, Offset(toScreenX(c * cellW), toScreenY(0f)),
                Offset(toScreenX(c * cellW), toScreenY(worldHeight)), strokeWidth = 1.5f)
        }
        for (r in 0..numRows) {
            drawLine(gridColor, Offset(toScreenX(0f), toScreenY(r * cellH)),
                Offset(toScreenX(worldWidth), toScreenY(r * cellH)), strokeWidth = 1.5f)
        }
    }
}