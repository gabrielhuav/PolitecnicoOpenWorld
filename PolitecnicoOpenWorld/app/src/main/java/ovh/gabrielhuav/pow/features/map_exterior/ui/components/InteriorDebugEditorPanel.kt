package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.DebugEditTool
import kotlin.math.roundToInt

// PANEL DEL EDITOR DEL DEBUG INTERIORES
// Barra compacta (abajo). Eliges una herramienta (color/tipo) y luego DIBUJAS con
// el dedo sobre el mapa (NativeOsmMap): arrastras para una linea (bardas/caminos) o
// un rectangulo (zonas rojas), igual que en Paint.
//  ROJO   = barda (WALL) / zona NO caminable (BLOCK)
//  VERDE  = camino peatonal (NAV_PED) - NARANJA = camino de autos (NAV_CAR)
//
// El panel es INTRUSIVO, asi que (igual que el disenador de matrices de interiores):
//  - se puede MOVER (asa, arrastrala; toca para recentrar),
//  - se puede CAMBIAR DE TAMANO (botones -/+, escala 0.5-1),
//  - y tiene SCROLL (acotado al 90% de la pantalla) para alcanzar SIEMPRE todos los
//    botones (incluido SALIR) aunque la pantalla sea baja en horizontal.
@Composable
fun InteriorDebugEditorPanel(
    tool: DebugEditTool,
    wallsCount: Int,
    blocksCount: Int,
    navPedCount: Int,
    navCarCount: Int,
    routeNpcsActive: Boolean,
    onSelectTool: (DebugEditTool) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onToggleRouteNpcs: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    val contentScroll = rememberScrollState()
    val maxPanelH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp

    Column(
        modifier = modifier
            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .heightIn(max = maxPanelH)
            .background(Color(0xE6101015), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.dbg_move_recenter),
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { _, drag ->
                            offX += drag.x * scale
                            offY += drag.y * scale
                        }
                    }
                    .clickable {
                        offX = 0f
                        offY = 0f
                    }
                    .padding(vertical = 6.dp)
            )
            BarButton("-", Color(0xFF37474F), false, Modifier.width(44.dp)) { scale = (scale - 0.1f).coerceIn(0.5f, 1f) }
            BarButton("+", Color(0xFF37474F), false, Modifier.width(44.dp)) { scale = (scale + 0.1f).coerceIn(0.5f, 1f) }
            BarButton(stringResource(R.string.dbg_exit), Color(0xFF6B1C3A), false, Modifier.width(64.dp)) { onExit() }
        }

        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(contentScroll),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val drawingShape = if (tool == DebugEditTool.BLOCK) stringResource(R.string.dbg_shape_rect) else stringResource(R.string.dbg_shape_line)
            val hint = if (tool == DebugEditTool.NONE)
                stringResource(R.string.dbg_hint_idle)
            else
                stringResource(R.string.dbg_hint_draw, drawingShape)
            Text(
                hint,
                color = Color(0xFFCFD8DC),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BarButton(stringResource(R.string.dbg_tool_wall), Color(0xFFD32F2F), tool == DebugEditTool.WALL, Modifier.weight(1f)) { onSelectTool(DebugEditTool.WALL) }
                BarButton(stringResource(R.string.dbg_tool_zone), Color(0xFFB71C1C), tool == DebugEditTool.BLOCK, Modifier.weight(1f)) { onSelectTool(DebugEditTool.BLOCK) }
                BarButton(stringResource(R.string.dbg_tool_ped), Color(0xFF4CAF50), tool == DebugEditTool.NAV_PED, Modifier.weight(1f)) { onSelectTool(DebugEditTool.NAV_PED) }
                BarButton(stringResource(R.string.dbg_tool_car), Color(0xFFFF8F00), tool == DebugEditTool.NAV_CAR, Modifier.weight(1f)) { onSelectTool(DebugEditTool.NAV_CAR) }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BarButton(stringResource(R.string.dbg_undo), Color(0xFF455A64), false, Modifier.weight(1f)) { onUndo() }
                BarButton(stringResource(R.string.dbg_clear), Color(0xFF8D6E63), false, Modifier.weight(1f)) { onClear() }
                BarButton(stringResource(R.string.dbg_export), Color(0xFF2E7D32), false, Modifier.weight(1f)) { onExport() }
                BarButton(stringResource(R.string.dbg_import), Color(0xFF00695C), false, Modifier.weight(1f)) { onImport() }
            }

            val routeLabel = if (routeNpcsActive) stringResource(R.string.dbg_route_remove) else stringResource(R.string.dbg_route_seed)
            val routeColor = if (routeNpcsActive) Color(0xFFB71C1C) else Color(0xFF1565C0)
            BarButton(routeLabel, routeColor, routeNpcsActive, Modifier.fillMaxWidth()) { onToggleRouteNpcs() }

            val counts = stringResource(R.string.dbg_counts, wallsCount, blocksCount, navPedCount, navCarCount)
            Text(
                counts,
                color = Color(0xFF90A4AE),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BarButton(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else color.copy(alpha = 0.45f)
        )
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
