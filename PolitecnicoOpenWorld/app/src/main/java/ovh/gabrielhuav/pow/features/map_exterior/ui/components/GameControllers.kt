package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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


// constante compartida
private val ControllerBaseSize = 180.dp

// JoystickController
@Composable
fun JoystickController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.4f,
    onMove: (angleRad: Double) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val latestOffset by rememberUpdatedState(offset)
    val feedback = rememberInputFeedback()

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
            .size(ControllerBaseSize) // uso de constante
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true; feedback.tap() },
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
        // Círculo interior (El "pulgar" del joystick). Se ACLARA mientras se arrastra (resalte visual).
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (isDragging) Color.LightGray.copy(alpha = 0.95f)
                    else Color.DarkGray.copy(alpha = 0.8f)
                )
        )
    }
}
// ==========================================
// JOYSTICK DE CONDUCCIÓN (solo dirige IZQ/DER)
// ==========================================
// Variante del joystick para el MODO MANEJO: el eje X dirige (izquierda/derecha) llamando a
// steerLeft/steerRight (press/release). Arriba/abajo no se usan (gas/freno viven en el diamante
// A/B/X/Y). Así el modo conducción también respeta la preferencia de JOYSTICK (antes solo D-pad).
@Composable
fun VehicleJoystickController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.4f,
    onSteerLeft: (Boolean) -> Unit,
    onSteerRight: (Boolean) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Estado de dirección actual, para emitir press/release solo en los cambios.
    var steering by remember { mutableStateOf(0) } // -1 izq, 0 centro, +1 der
    val feedback = rememberInputFeedback()

    fun setSteer(dir: Int) {
        if (dir == steering) return
        // Suelta la dirección anterior y presiona la nueva.
        if (steering < 0) onSteerLeft(false)
        if (steering > 0) onSteerRight(false)
        if (dir < 0) onSteerLeft(true)
        if (dir > 0) onSteerRight(true)
        if (dir != 0) feedback.tap()
        steering = dir
    }

    Box(
        modifier = modifier
            .size(ControllerBaseSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)))
            .pointerInput(Unit) {
                val deadzone = 18.dp.toPx()
                detectDragGestures(
                    onDragEnd = { offset = Offset.Zero; setSteer(0) },
                    onDragCancel = { offset = Offset.Zero; setSteer(0) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = offset + dragAmount
                        val maxRadius = (size.width / 2f) - 24.dp.toPx()
                        val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        offset = if (distance > maxRadius) newOffset * (maxRadius / distance) else newOffset
                        setSteer(if (offset.x < -deadzone) -1 else if (offset.x > deadzone) 1 else 0)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
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
    // Agregamos opacidad como parámetro para conectarlo al menú
    backgroundAlpha: Float = 0.6f,
    onDirectionPressed: (Direction) -> Unit
) {
    Box(
        modifier = modifier
            .size(ControllerBaseSize) // uso de constante
            .clip(CircleShape)
            // se usa COERCEIN para seguridad
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f))),
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
private fun DPadButton(icon: ImageVector, onClick: () -> Unit) {
    val feedback = rememberInputFeedback()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(if (pressed) 0.88f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray.copy(alpha = if (pressed) 1f else 0.8f))
            .repeatingClickable(
                onPress = { p -> pressed = p; if (p) feedback.tap() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

// ==========================================
// BOTONES DE ACCIÓN (XBOX STYLE) — MODO A PIE
// ==========================================


@Composable
fun ActionButtonsController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.6f,
    onActionChanged: (GameAction, Boolean) -> Unit,
    onClaimCollectiblePressed: () -> Unit // <--- Parámetro recibido
) {
    Box(
        modifier = modifier
            .size(ControllerBaseSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f))),
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
                    onHoldEvent = { isPressed ->
                        // El ViewModel ya centraliza TODA la lógica de interacción (coches, gatos,
                        // vendedores y coleccionables) al recibir GameAction.X.
                        onActionChanged(GameAction.X, isPressed)
                    }
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
    val feedback = rememberInputFeedback()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(48.dp)
            .scale(if (pressed) 0.88f else 1f)
            .clip(CircleShape)
            .background(if (pressed) color.copy(alpha = 0.7f) else color)
            //  INYECTAMOS LA DETECCIÓN DE MANTENER PRESIONADO
            .detectHoldEvent { isPressed ->
                pressed = isPressed
                if (isPressed) feedback.tap()
                onHoldEvent(isPressed)
            },
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
// CONTROLES DE VEHÍCULO (MODO CONDUCCIÓN — MISMO DIAMANTE XBOX QUE A PIE)
// ==========================================
//
// UN SOLO CONTROL para todo el juego: el diamante de conducción usa las MISMAS letras,
// colores, tamaños y posiciones Xbox (Y arriba · X izquierda · B derecha · A abajo) que el
// modo a pie (ActionButtonsController), reutilizando el MISMO composable ActionButton.
// (Antes era un diamante estilo PS4 con símbolos △ ○ ✕ □; se unificó a Xbox — 2026-07-03.)
//
// Mapeo:
//   D-pad → ARRIBA: gas · ABAJO: freno · IZQUIERDA/DERECHA: girar.
//   Diamante → Y (arriba): SALIR (mantener → teletransporte) · A (abajo): gas ·
//              B (derecha): freno · X (izquierda): freno de mano.
//
// Nota: gas/freno están disponibles tanto en el D-pad como en el diamante
// (redundancia intencional para poder conducir con cualquier mano).

@Composable
fun VehicleDPadController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.6f,
    onUp: (Boolean) -> Unit,      // gas
    onDown: (Boolean) -> Unit,    // freno
    onLeft: (Boolean) -> Unit,    // girar izquierda
    onRight: (Boolean) -> Unit    // girar derecha
) {
    Box(
        modifier = modifier
            .size(ControllerBaseSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VehicleDpadButton(icon = Icons.Default.KeyboardArrowUp, onHold = onUp)
            Row {
                VehicleDpadButton(icon = Icons.Default.KeyboardArrowLeft, onHold = onLeft)
                Spacer(modifier = Modifier.size(48.dp))
                VehicleDpadButton(icon = Icons.Default.KeyboardArrowRight, onHold = onRight)
            }
            VehicleDpadButton(icon = Icons.Default.KeyboardArrowDown, onHold = onDown)
        }
    }
}

@Composable
private fun VehicleDpadButton(icon: ImageVector, onHold: (Boolean) -> Unit) {
    val feedback = rememberInputFeedback()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(if (pressed) 0.88f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray.copy(alpha = if (pressed) 1f else 0.8f))
            .detectHoldEvent { isPressed -> // press/release (no repetición discreta)
                pressed = isPressed
                if (isPressed) feedback.tap()
                onHold(isPressed)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun VehicleActionButtonsController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.6f,
    onAccelerate: (Boolean) -> Unit,  // A (abajo) — gas
    onBrake: (Boolean) -> Unit,       // B (derecha) — freno
    onHandbrake: (Boolean) -> Unit,   // X (izquierda) — freno de mano
    onExit: (Boolean) -> Unit         // Y (arriba) — salir (mantener → menú teletransporte)
) {
    Box(
        modifier = modifier
            .size(ControllerBaseSize)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center
    ) {
        // MISMO diamante Xbox que a pie (mismas letras/colores/posiciones, mismo ActionButton):
        // solo cambia QUÉ HACE cada botón al conducir.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Y arriba — SALIR (amarillo, igual que a pie)
            ActionButton(text = "Y", color = Color(0xFFF1C40F), onHoldEvent = onExit)

            Row(verticalAlignment = Alignment.CenterVertically) {
                // X izquierda — FRENO DE MANO (azul, igual que a pie)
                ActionButton(text = "X", color = Color(0xFF3498DB), onHoldEvent = onHandbrake)

                Spacer(modifier = Modifier.size(48.dp))

                // B derecha — FRENO (rojo, igual que a pie)
                ActionButton(text = "B", color = Color(0xFFE74C3C), onHoldEvent = onBrake)
            }

            // A abajo — GAS (verde, igual que a pie)
            ActionButton(text = "A", color = Color(0xFF2ECC71), onHoldEvent = onAccelerate)
        }
    }
}

// ==========================================
// MODIFICADORES
// ==========================================
// Este código permite que al mantener apretado, la acción se repita cada X milisegundos
fun Modifier.repeatingClickable(
    initialDelay: Long = 10,
    delayBetweenClicks: Long = 40, // 40ms = ~25 fps de actualización de movimiento
    onPress: (Boolean) -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val currentClickListener by rememberUpdatedState(onClick)
    val currentPressListener by rememberUpdatedState(onPress)
    val coroutineScope = rememberCoroutineScope()
    var job: Job? by remember { mutableStateOf(null) }

    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            currentPressListener(true)
            job = coroutineScope.launch {
                currentClickListener() // Disparo inicial
                delay(initialDelay)
                while (true) {
                    currentClickListener() // Disparo continuo
                    delay(delayBetweenClicks)
                }
            }
            try {
                waitForUpOrCancellation()
            } finally {
                job?.cancel() // Se detiene cuando sueltas el dedo (o se cancela el gesto)
                currentPressListener(false)
            }
        }
    }
}

// ==========================================
// WIDGET DE COORDENADAS (X / Y / Z)
// ==========================================
// Muestra la posición actual del jugador. Reusable por el mundo global y los interiores.
//   X / Y = coordenadas (lon/lat en global, píxeles/normalizado en interiores).
//   Z     = "dónde está": GLOBAL en el mundo abierto, o el nombre de la sala en interiores.
// Se activa/desactiva en Ajustes → Interfaz.
// IMPORTANTE: se dibuja como un CHIP DE UNA SOLA LÍNEA con el MISMO estilo/tamaño que los demás
// widgets de Interfaz (CacheChip): mismo fondo, forma, padding, punto y tipografía, para que todos
// los widgets queden uniformes (antes era un bloque de 3 líneas, más alto que el resto).
@Composable
fun CoordsWidget(
    x: String,
    y: String,
    z: String,
    modifier: Modifier = Modifier
) {
    // Compacto para acercar su ANCHO al de los demás chips de Interfaz (tiene 3 valores → siempre algo
    // más ancho, pero con fuente 10sp + padding/espaciado reducidos queda mucho más angosto).
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF7FD1FF)))
        // UNA SOLA LÍNEA (sin wrap): así no se parte en 2 líneas desiguales como antes; igual que
        // los demás chips de Interfaz, que tampoco se envuelven.
        Text(
            text = "X $x Y $y Z $z",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
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

        // 2. Esperamos a que levante el dedo o deslice fuera del área.
        //    IMPORTANTE: el `finally` GARANTIZA que se notifique el "soltar" (false) AUNQUE la
        //    corrutina del gesto se cancele (recomposición, el botón sale de pantalla, otro
        //    pointerInput toma el evento…). Sin esto, el "release" se perdía y `playerAction`
        //    se quedaba en SPECIAL → el jugador "golpeaba todo el tiempo".
        try {
            waitForUpOrCancellation()
        } finally {
            onHoldEvent(false)
        }
    }
}