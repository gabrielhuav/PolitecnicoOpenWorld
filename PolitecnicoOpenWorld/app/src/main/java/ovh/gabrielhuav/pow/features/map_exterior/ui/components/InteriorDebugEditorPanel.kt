package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.DebugEditTool

// ─── PANEL DEL EDITOR DEL DEBUG INTERIORES ────────────────────────────────────
// Panel flotante para EDITAR las líneas del overlay caminando con el jugador:
//  ROJO   = barda (WALL) / zona NO caminable (BLOCK)
//  VERDE  = camino peatonal (NAV_PED)
//  NARANJA= camino de autos / estacionamiento (NAV_CAR)
// Flujo: elige herramienta → "Capturar" en cada punto (caminando) → "Terminar forma".
// "Exportar" guarda todo a JSON (formato exterior_collisions + navPaths).
@Composable
fun InteriorDebugEditorPanel(
    tool: DebugEditTool,
    inProgressCount: Int,
    wallsCount: Int,
    blocksCount: Int,
    navPedCount: Int,
    navCarCount: Int,
    onSelectTool: (DebugEditTool) -> Unit,
    onCapture: () -> Unit,
    onUndo: () -> Unit,
    onFinish: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xE6101015), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("Editor de líneas (Debug)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Puntos en curso: $inProgressCount", color = Color(0xFFB0BEC5), fontSize = 11.sp)

        // Selector de herramienta (color/tipo).
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton("Barda", Color(0xFFD32F2F), tool == DebugEditTool.WALL, Modifier.weight(1f)) { onSelectTool(DebugEditTool.WALL) }
            ToolButton("Zona", Color(0xFFB71C1C), tool == DebugEditTool.BLOCK, Modifier.weight(1f)) { onSelectTool(DebugEditTool.BLOCK) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton("Peatonal", Color(0xFF4CAF50), tool == DebugEditTool.NAV_PED, Modifier.weight(1f)) { onSelectTool(DebugEditTool.NAV_PED) }
            ToolButton("Autos", Color(0xFFFF8F00), tool == DebugEditTool.NAV_CAR, Modifier.weight(1f)) { onSelectTool(DebugEditTool.NAV_CAR) }
        }

        // Acciones de captura.
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))) {
            Text("Capturar punto", fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onUndo, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))) {
                Text("Deshacer", fontSize = 11.sp)
            }
            Button(onClick = onFinish, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A))) {
                Text("Terminar", fontSize = 11.sp)
            }
        }

        Text("Editado → bardas: $wallsCount · zonas: $blocksCount · peatonal: $navPedCount · autos: $navCarCount",
            color = Color(0xFFB0BEC5), fontSize = 10.sp)

        // Guardar / cargar geometría.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onExport, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))) {
                Text("Exportar", fontSize = 11.sp)
            }
            Button(onClick = onImport, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))) {
                Text("Importar", fontSize = 11.sp)
            }
        }
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D6E63))) {
            Text("Limpiar todo", fontSize = 11.sp)
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else color.copy(alpha = 0.35f)
        )
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
