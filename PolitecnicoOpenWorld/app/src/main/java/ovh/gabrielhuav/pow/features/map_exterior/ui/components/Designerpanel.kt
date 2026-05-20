package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.domain.models.Landmark

@Composable
fun DesignerPanel(
    landmark: Landmark,
    onMove: (Double, Double) -> Unit,
    onRotate: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val moveStep = 0.0001

    Column(
        modifier = modifier
            .background(Color(0xFF1E1E24).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── HEADER ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MODO DISEÑADOR", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold)
            IconButton(
                onClick = onDeselect,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
            }
        }

        // ─── CONTROLES DE MOVIMIENTO ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoveBtn("Izquierda", 40.dp) { onMove(0.0, -moveStep) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MoveBtn("Arriba", 40.dp) { onMove(moveStep, 0.0) }
                MoveBtn("Abajo", 40.dp) { onMove(-moveStep, 0.0) }
            }
            MoveBtn("Derecha", 40.dp) { onMove(0.0, moveStep) }
        }

        // ─── SLIDERS (Escala y Rotación) ───
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "ESCALA: ${String.format("%.2f", landmark.scaleFactor)}x",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = landmark.scaleFactor,
                onValueChange = onScale,
                valueRange = 0.05f..3.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4AF37),
                    activeTrackColor = Color(0xFF6B1C3A)
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "ROTACIÓN: ${landmark.rotationAngle.toInt()}°",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = landmark.rotationAngle,
                onValueChange = onRotate,
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4AF37),
                    activeTrackColor = Color(0xFF6B1C3A)
                ),
                modifier = Modifier.fillMaxWidth().height(24.dp)
            )
        }

        // ─── BOTONES DE ACCIÓN ───
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ELIMINAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("GUARDAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onExport,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("EXPORTAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onImport,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("IMPORTAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MoveBtn(label: String, size: Dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = size + 4.dp, height = 32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        val icon = when (label) {
            "Arriba" -> Icons.Default.KeyboardArrowUp
            "Abajo" -> Icons.Default.KeyboardArrowDown
            "Izquierda" -> Icons.Default.KeyboardArrowLeft
            else -> Icons.Default.KeyboardArrowRight
        }
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}

