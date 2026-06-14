// features/interiores/core/ui/WaypointDesignerLayer.kt
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
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import kotlin.math.abs

/**
 * Capa de edición de WAYPOINTS (puertas) del Modo Diseñador del minijuego de
 * zombis. Usa exactamente la misma transformación mundo→pantalla que el resto
 * de entidades de ZombieGameScreen, de modo que las puertas quedan alineadas
 * con el fondo a cualquier zoom/desplazamiento.
 *
 *  - Toca una puerta para seleccionarla.
 *  - Arrastra para mover la puerta seleccionada (su centro sigue al dedo).
 *
 * Las puertas se guardan en waypoints.json desde la toolbar.
 */
@Composable
fun WaypointDesignerLayer(
    enabled: Boolean,
    doors: List<ZoneDoor>,
    selectedIndex: Int,
    worldWidth: Float,
    worldHeight: Float,
    camOffsetX: Float,
    camOffsetY: Float,
    camScale: Float,
    onSelectWorld: (Float, Float) -> Unit,
    onDragWorld: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled || worldWidth <= 0f || worldHeight <= 0f || camScale <= 0f) return

    fun toScreenX(wx: Float) = camOffsetX + wx * camScale
    fun toScreenY(wy: Float) = camOffsetY + wy * camScale
    fun toWorldX(sx: Float) = (sx - camOffsetX) / camScale
    fun toWorldY(sy: Float) = (sy - camOffsetY) / camScale

    Canvas(
        modifier = modifier
            .pointerInput(enabled, doors.size, camScale, camOffsetX, camOffsetY) {
                detectTapGestures { pos ->
                    onSelectWorld(toWorldX(pos.x), toWorldY(pos.y))
                }
            }
            .pointerInput(enabled, selectedIndex, camScale, camOffsetX, camOffsetY) {
                detectDragGestures(
                    onDragStart = { pos -> onSelectWorld(toWorldX(pos.x), toWorldY(pos.y)) }
                ) { change, _ ->
                    change.consume()
                    onDragWorld(toWorldX(change.position.x), toWorldY(change.position.y))
                }
            }
    ) {
        doors.forEachIndexed { i, door ->
            val r = door.hitboxFrac
            val tlX = toScreenX(r.left * worldWidth); val tlY = toScreenY(r.top * worldHeight)
            val brX = toScreenX(r.right * worldWidth); val brY = toScreenY(r.bottom * worldHeight)
            val topLeft = Offset(minOf(tlX, brX), minOf(tlY, brY))
            val size = Size(abs(brX - tlX), abs(brY - tlY))
            val selected = i == selectedIndex
            val base = when (door.kind) {
                DoorKind.TO_WORLD -> Color(0xFF00BCD4)
                DoorKind.TO_BUILDING -> Color(0xFFD4AF37)
                DoorKind.EXIT_NEXT, DoorKind.EXIT_PREV -> Color(0xFFFF9800)
                else -> Color(0xFF8BC34A)
            }
            drawRect(color = base.copy(alpha = if (selected) 0.55f else 0.28f), topLeft = topLeft, size = size)
            drawRect(
                color = if (selected) Color.White else base,
                topLeft = topLeft,
                size = size,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (selected) 5f else 2.5f)
            )
        }
    }
}