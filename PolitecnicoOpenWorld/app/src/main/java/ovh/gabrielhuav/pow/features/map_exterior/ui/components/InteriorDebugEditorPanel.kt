package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.DebugEditTool

// ─── PANEL DEL EDITOR DEL DEBUG INTERIORES ────────────────────────────────────
// Barra HORIZONTAL compacta (abajo). Eliges una herramienta (color/tipo) y luego
// DIBUJAS con el dedo sobre el mapa (NativeOsmMap): arrastras para una línea
// (bardas/caminos) o un rectángulo (zonas rojas), igual que en Paint.
//  ROJO   = barda (WALL) / zona NO caminable (BLOCK)
//  VERDE  = camino peatonal (NAV_PED) · NARANJA = camino de autos (NAV_CAR)
@Composable
fun InteriorDebugEditorPanel(
    tool: DebugEditTool,
    wallsCount: Int,
    blocksCount: Int,
    navPedCount: Int,
    navCarCount: Int,
    onSelectTool: (DebugEditTool) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xE6101015), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val hint = if (tool == DebugEditTool.NONE)
            "Editor de líneas — elige una herramienta y dibuja con el dedo en el mapa"
        else
            "Dibuja con el dedo: arrastra para ${if (tool == DebugEditTool.BLOCK) "un rectángulo" else "una línea"}"
        Text(hint, color = Color(0xFFCFD8DC), fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth())

        // Fila 1: herramientas (color/tipo).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BarButton("Barda", Color(0xFFD32F2F), tool == DebugEditTool.WALL, Modifier.weight(1f)) { onSelectTool(DebugEditTool.WALL) }
            BarButton("Zona", Color(0xFFB71C1C), tool == DebugEditTool.BLOCK, Modifier.weight(1f)) { onSelectTool(DebugEditTool.BLOCK) }
            BarButton("Peatonal", Color(0xFF4CAF50), tool == DebugEditTool.NAV_PED, Modifier.weight(1f)) { onSelectTool(DebugEditTool.NAV_PED) }
            BarButton("Autos", Color(0xFFFF8F00), tool == DebugEditTool.NAV_CAR, Modifier.weight(1f)) { onSelectTool(DebugEditTool.NAV_CAR) }
        }

        // Fila 2: acciones.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BarButton("Deshacer", Color(0xFF455A64), false, Modifier.weight(1f)) { onUndo() }
            BarButton("Limpiar", Color(0xFF8D6E63), false, Modifier.weight(1f)) { onClear() }
            BarButton("Exportar", Color(0xFF2E7D32), false, Modifier.weight(1f)) { onExport() }
            BarButton("Importar", Color(0xFF00695C), false, Modifier.weight(1f)) { onImport() }
        }

        Text(
            "bardas $wallsCount · zonas $blocksCount · peatonal $navPedCount · autos $navCarCount",
            color = Color(0xFF90A4AE), fontSize = 10.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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