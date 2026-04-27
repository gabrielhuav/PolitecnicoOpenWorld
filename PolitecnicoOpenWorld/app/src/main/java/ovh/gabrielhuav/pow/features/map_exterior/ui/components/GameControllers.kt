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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlin.math.sqrt



// JoystickController
@Composable
fun JoystickController(
    modifier: Modifier = Modifier,
    onMove: (angleRad: Double) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val latestOffset by rememberUpdatedState(offset)

    // Bucle continuo de movimiento a ~30 fps cuando se mantiene arrastrado.
    // La clave es sólo 'isDragging' para que el efecto NO se reinicie con cada cambio de offset;
    // leemos el offset más reciente a través de 'latestOffset' (rememberUpdatedState).
    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (isActive) {
                if (latestOffset != Offset.Zero) {
                    // En Compose 'Y' crece hacia abajo, pero en GeoPoint 'Latitud' crece hacia arriba, por eso invertimos la 'y'
                    val angle = kotlin.math.atan2(-latestOffset.y.toDouble(), latestOffset.x.toDouble())
                    onMove(angle)
                }
                delay(33) // ~30 fps
            }
        }
    }

    Box(
        modifier = modifier
            .size(120.dp) // Tamaño base, el scale() lo modificará
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false; offset = Offset.Zero },
                    onDragCancel = { isDragging = false; offset = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offset + dragAmount
                        val maxRadius = (size.width / 2f) - 24.dp.toPx() // 24 es el radio del botón interior
                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)

                        offset = if (distance > maxRadius) {
                            newOffset * (maxRadius / distance)
                        } else {
                            newOffset
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Círculo interior (El "pulgar" del joystick)
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.DarkGray.copy(alpha = 0.8f))
        )
    }
}
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
    onActionChanged: (GameAction, Boolean) -> Unit
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
            ActionButton(
                text = "Y",
                color = Color(0xFFF1C40F),
                onHoldEvent = { isPressed -> onActionChanged(GameAction.Y, isPressed) }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // X - Azul
                ActionButton(
                    text = "X",
                    color = Color(0xFF3498DB),
                    onHoldEvent = { isPressed -> onActionChanged(GameAction.X, isPressed) }
                )

                Spacer(modifier = Modifier.size(48.dp))

                // B - Rojo (Ataque Especial)
                ActionButton(
                    text = "B",
                    color = Color(0xFFE74C3C),
                    onHoldEvent = { isPressed -> onActionChanged(GameAction.B, isPressed) }
                )
            }

            // A - Verde (Correr)
            ActionButton(
                text = "A",
                color = Color(0xFF2ECC71),
                onHoldEvent = { isPressed -> onActionChanged(GameAction.A, isPressed) }
            )
        }
    }
}

/**
 * Componente individual del botón actualizado con el detector de gestos.
 */
@Composable
fun ActionButton(
    text: String,
    color: Color,
    onHoldEvent: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            //  INYECTAMOS LA DETECCIÓN DE MANTENER PRESIONADO
            .detectHoldEvent { isPressed -> onHoldEvent(isPressed) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
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

/**
 * Modificador personalizado que detecta el inicio y fin de una pulsación física.
 */
fun Modifier.detectHoldEvent(onHoldEvent: (isPressed: Boolean) -> Unit): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        // 1. El usuario toca la pantalla
        awaitFirstDown(requireUnconsumed = false)
        onHoldEvent(true)

        // 2. Esperamos a que levante el dedo o deslice fuera del área
        waitForUpOrCancellation()
        onHoldEvent(false)
    }
}