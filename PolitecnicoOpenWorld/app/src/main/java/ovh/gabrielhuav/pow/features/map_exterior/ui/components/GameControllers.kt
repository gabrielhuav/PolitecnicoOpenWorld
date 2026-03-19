package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction

// ==========================================
// CONTROL DIRECCIONAL (D-PAD)
// ==========================================
@Composable
fun DPadController(
    modifier: Modifier = Modifier,
    onDirectionPressed: (Direction) -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DPadButton(icon = Icons.Default.KeyboardArrowUp, onClick = { onDirectionPressed(Direction.UP) })
            Row {
                DPadButton(icon = Icons.Default.KeyboardArrowLeft, onClick = { onDirectionPressed(Direction.LEFT) })
                Spacer(modifier = Modifier.size(48.dp))
                DPadButton(icon = Icons.Default.KeyboardArrowRight, onClick = { onDirectionPressed(Direction.RIGHT) })
            }
            DPadButton(icon = Icons.Default.KeyboardArrowDown, onClick = { onDirectionPressed(Direction.DOWN) })
        }
    }
}

@Composable
private fun DPadButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray.copy(alpha = 0.8f))
            .repeatingClickable(onClick = onClick), // Usamos nuestro modificador especial
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

// ==========================================
// BOTONES DE ACCIÓN (XBOX STYLE)
// ==========================================
@Composable
fun ActionButtonsController(
    modifier: Modifier = Modifier,
    onActionPressed: (GameAction) -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Y - Amarillo
            ActionButton(text = "Y", color = Color(0xFFF1C40F), onClick = { onActionPressed(GameAction.Y) })
            Row(verticalAlignment = Alignment.CenterVertically) {
                // X - Azul
                ActionButton(text = "X", color = Color(0xFF3498DB), onClick = { onActionPressed(GameAction.X) })
                Spacer(modifier = Modifier.size(48.dp))
                // B - Rojo
                ActionButton(text = "B", color = Color(0xFFE74C3C), onClick = { onActionPressed(GameAction.B) })
            }
            // A - Verde
            ActionButton(text = "A", color = Color(0xFF2ECC71), onClick = { onActionPressed(GameAction.A) })
        }
    }
}

@Composable
private fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.9f))
            // Usamos un tap normal, las acciones no se repiten como el caminar
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); onClick() } },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

// ==========================================
// MODIFICADOR: HOLD-TO-MOVE
// ==========================================
// Este código permite que al mantener apretado, la acción se repita cada X milisegundos
fun Modifier.repeatingClickable(
    initialDelay: Long = 10,
    delayBetweenClicks: Long = 40, // 40ms = ~25 fps de actualización de movimiento
    onClick: () -> Unit
): Modifier = composed {
    val currentClickListener by rememberUpdatedState(onClick)
    val coroutineScope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            job = coroutineScope.launch {
                currentClickListener() // Disparo inicial
                delay(initialDelay)
                while (true) {
                    currentClickListener() // Disparo continuo
                    delay(delayBetweenClicks)
                }
            }
            waitForUpOrCancellation()
            job?.cancel() // Se detiene cuando sueltas el dedo
        }
    }
}