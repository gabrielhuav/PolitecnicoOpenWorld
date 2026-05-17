package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.domain.models.Landmark

/**
 * Panel flotante que se muestra cuando hay un landmark seleccionado en modo diseñador.
 * Permite ajustar posición, rotación, escala y eliminar.
 */
@Composable
fun DesignerPanel(
    landmark: Landmark,
    onMove: (dLatMeters: Double, dLonMeters: Double) -> Unit,
    onRotate: (angle: Float) -> Unit,
    onScale: (scale: Float) -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.88f))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Encabezado: nombre + cerrar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📐 ${landmark.name}",
                color = Color(0xFFD4AF37),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = onDeselect,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, "Deseleccionar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // Coordenadas y rotación actuales
        Text(
            text = "Lat: ${"%.6f".format(landmark.location.latitude)}  Lon: ${"%.6f".format(landmark.location.longitude)}",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp
        )
        Text(
            text = "Rot: ${landmark.rotationAngle.toInt()}°   Escala: ${"%.2f".format(landmark.scaleFactor)}x",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp
        )

        // Cruz de movimiento. Cada toque mueve ~1 metro.
        // 1 metro ≈ 0.0000090° latitud, ≈ 0.0000095° longitud a la latitud de CDMX.
        Text("Mover", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mover en pasos de 1 metro
            MoveBtn("←", 24.dp) { onMove(0.0, -0.0000095) }
            MoveBtn("↑", 24.dp) { onMove(0.0000090, 0.0) }
            MoveBtn("↓", 24.dp) { onMove(-0.0000090, 0.0) }
            MoveBtn("→", 24.dp) { onMove(0.0, 0.0000095) }
            Spacer(Modifier.width(8.dp))
            // Pasos de 5 metros para movimientos más rápidos
            MoveBtn("←5", 32.dp) { onMove(0.0, -0.0000475) }
            MoveBtn("↑5", 32.dp) { onMove(0.0000450, 0.0) }
            MoveBtn("↓5", 32.dp) { onMove(-0.0000450, 0.0) }
            MoveBtn("→5", 32.dp) { onMove(0.0, 0.0000475) }
        }

        // Rotación
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rotación", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(70.dp))
            Slider(
                value = landmark.rotationAngle,
                onValueChange = onRotate,
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4AF37),
                    activeTrackColor = Color(0xFF6B1C3A)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Escala
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Escala", color = Color.White, fontSize = 11.sp, modifier = Modifier.width(70.dp))
            Slider(
                value = landmark.scaleFactor,
                onValueChange = onScale,
                valueRange = 0.05f..3.0f,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4AF37),
                    activeTrackColor = Color(0xFF6B1C3A)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        // Botón eliminar
        Button(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth().height(36.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("ELIMINAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MoveBtn(label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = size + 4.dp, height = 32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
        shape = RoundedCornerShape(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}