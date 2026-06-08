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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Divider
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale

@Composable
fun DesignerPanel(
    landmark: Landmark,
    onMove: (Double, Double) -> Unit,
    onRotate: (Float) -> Unit,
    onScaleX: (Float) -> Unit,
    onScaleY: (Float) -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeselect: () -> Unit,
    isParkingMode: Boolean = false,
    onToggleParkingMode: (Boolean) -> Unit = {},
    onNewWay: () -> Unit = {},
    onDebugPoint: () -> Unit = {},
    onSpawnTestCar: () -> Unit = {},
    onRevert: () -> Unit = {}, // Nuevo parámetro inyectado
    modifier: Modifier = Modifier
) {
    val moveStep = 0.00001
    val scrollState = androidx.compose.foundation.rememberScrollState() // Definir estado de scroll

    Column(
        modifier = modifier
            .background(Color(0xFF1E1E24).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .verticalScroll(scrollState), // <-- Esto evita que se corten los botones
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // Header superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "EDICIÓN DE ASSET",
                color = Color(0xFFD4AF37),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            IconButton(onClick = onDeselect, modifier = Modifier.size(24.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Distribución en 2 secciones: Superior (2 columnas) e Inferior (ancho completo)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // SECCIÓN SUPERIOR: Movimiento y Sliders
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // COLUMNA 1: Movimiento y Estado del Asset
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MoveBtn("Izquierda", 34.dp) { onMove(0.0, -moveStep) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            MoveBtn("Arriba", 34.dp) { onMove(moveStep, 0.0) }
                            MoveBtn("Abajo", 34.dp) { onMove(-moveStep, 0.0) }
                        }
                        MoveBtn("Derecha", 34.dp) { onMove(0.0, moveStep) }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Button(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f).height(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "ELIMINAR",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onSave,
                            modifier = Modifier.weight(1f).height(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "GUARDAR",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onRevert,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5D4037)),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "REVERTIR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // COLUMNA 2: Sliders de Ajuste Geométrico con Botones (+ / -)
                Column(
                    modifier = Modifier.weight(1.3f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ANCHO
                    Text(
                        "ANCHO: ${String.format("%.2f", landmark.scaleX)}x",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Button(
                            onClick = { onScaleX((landmark.scaleX - 0.05f).coerceAtLeast(0.05f)) },
                            modifier = Modifier.size(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("-", color = Color.White, fontSize = 10.sp) }
                        Slider(
                            value = landmark.scaleX,
                            onValueChange = onScaleX,
                            valueRange = 0.05f..5.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD4AF37),
                                activeTrackColor = Color(0xFF6B1C3A)
                            ),
                            modifier = Modifier.weight(1f).height(14.dp)
                        )
                        Button(
                            onClick = { onScaleX((landmark.scaleX + 0.05f).coerceAtMost(5.0f)) },
                            modifier = Modifier.size(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("+", color = Color.White, fontSize = 10.sp) }
                    }

                    // ALTO
                    Text(
                        "ALTO: ${String.format("%.2f", landmark.scaleY)}x",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Button(
                            onClick = { onScaleY((landmark.scaleY - 0.05f).coerceAtLeast(0.05f)) },
                            modifier = Modifier.size(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("-", color = Color.White, fontSize = 10.sp) }
                        Slider(
                            value = landmark.scaleY,
                            onValueChange = onScaleY,
                            valueRange = 0.05f..5.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD4AF37),
                                activeTrackColor = Color(0xFF6B1C3A)
                            ),
                            modifier = Modifier.weight(1f).height(14.dp)
                        )
                        Button(
                            onClick = { onScaleY((landmark.scaleY + 0.05f).coerceAtMost(5.0f)) },
                            modifier = Modifier.size(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("+", color = Color.White, fontSize = 10.sp) }
                    }

                    // ROTACIÓN
                    Text(
                        "ROTACIÓN: ${landmark.rotationAngle.toInt()}°",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Button(
                            onClick = { onRotate((landmark.rotationAngle - 5f).let { if (it < 0f) it + 360f else it }) },
                            modifier = Modifier.size(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("-", color = Color.White, fontSize = 10.sp) }
                        Slider(
                            value = landmark.rotationAngle,
                            onValueChange = onRotate,
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFD4AF37),
                                activeTrackColor = Color(0xFF6B1C3A)
                            ),
                            modifier = Modifier.weight(1f).height(14.dp)
                        )
                        Button(
                            onClick = { onRotate((landmark.rotationAngle + 5f).let { if (it >= 360f) it - 360f else it }) },
                            modifier = Modifier.size(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) { Text("+", color = Color.White, fontSize = 10.sp) }
                    }
                }
            }

            // SECCIÓN INFERIOR: Herramientas de Rutas e Intercambio (Ancho completo)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Fila 1 inferior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).height(28.dp)
                    ) {
                        Checkbox(
                            checked = isParkingMode,
                            onCheckedChange = onToggleParkingMode,
                            modifier = Modifier.scale(0.7f)
                        )
                        Text("Cajón Estac.", fontSize = 10.sp, color = Color.White)
                    }
                    Button(
                        onClick = onNewWay,
                        modifier = Modifier.weight(0.8f).height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "NUEVO",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onDebugPoint,
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "CAPTURAR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Fila 2 inferior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSpawnTestCar,
                        modifier = Modifier.weight(1.2f).height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "SPAWN AUTO",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onExport,
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "EXPORTAR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onImport,
                        modifier = Modifier.weight(1f).height(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "IMPORTAR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

