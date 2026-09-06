package ovh.gabrielhuav.pow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt
import kotlin.math.sqrt

// CONTROLES DE JUEGO COMPARTIDOS — no pertenecen a NINGUN modo.
//
// 🆕 (2026-07-26) Extraidos de features/map_exterior/ui/components/GameControllers.kt. Vivian
// dentro del MUNDO ABIERTO, asi que el modo pelea "Titulación por Combate" y los INTERIORES (zombis,
// ESCOM, metro, ShineCTO) tenian que importar de una feature ajena solo para dibujar un joystick.
//
// REGLA: aqui SOLO va lo GENERICO de verdad. Estos composables no conocen `Direction` ni
// `GameAction` (tipos del ViewModel del mundo abierto). Los que SI dependen de esos tipos
// —DPadController, ActionButtonsController y todo lo de vehiculos— se quedan en map_exterior,
// que es a donde pertenecen. Si un control necesita un tipo de una feature, NO va aqui.

// constante compartida
val ControllerBaseSize = 180.dp

/** Diámetro por defecto de un botón de acción (A/B/X/Y). El de Android de toda la vida. */
val TamanoBotonAccion = 48.dp

/**
 * Radio del stick interior como fracción del diámetro del joystick: 24 dp sobre 180 dp.
 * Al ser relativo, el joystick se puede encoger **sin cambiar el tacto**: la zona muerta y el
 * recorrido máximo siguen siendo proporcionalmente los mismos que en Android.
 */
private const val RADIO_STICK_RELATIVO = 24f / 180f

// JoystickController
@Composable
fun JoystickController(
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.4f,
    /**
     * Diámetro REAL del joystick. Por defecto [ControllerBaseSize], que es lo que usa Android.
     *
     * ⚠️ **Es un tamaño de verdad, no un `Modifier.scale`.** `scale()` es una transformación de
     * DIBUJO: encoge lo que se ve pero **no mueve el área táctil**, así que el control responde
     * donde ya no está. Se probó en iOS el 2026-07-31 y los botones no reaccionaban. Si necesitas
     * un joystick más pequeño (iOS en vertical), pásalo por aquí.
     */
    tamano: Dp = ControllerBaseSize,
    // 🆕 (2026-07-22) Aviso de SOLTAR: sin esto, quien lee el joystick solo detecta la liberación
    // por un timer de inactividad (~100 ms), lo que hacía sentir "pegado" (p.ej. quedarse
    // agachado un instante tras soltar ↓). Opcional: los modos que no lo pasan no cambian.
    onRelease: () -> Unit = {},
    // 🆕 (2026-07-25) RESPUESTA INMEDIATA al TOQUE (joystick virtual estándar): con detectDragGestures
    // un TAP puro (tocar y soltar sin arrastrar) se IGNORA y tocar-y-mantener no registra input hasta
    // cruzar el touch-slop → los controles se sentían "no instantáneos / pegados" (agacharse el más
    // notorio). Con true, la dirección se toma de la POSICIÓN del toque desde el centro y dispara YA.
    // Default false = comportamiento de ARRASTRE de siempre (mundo abierto sin cambios).
    respondToTouchDown: Boolean = false,
    onMove: (angleRad: Double) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    val latestOffset by rememberUpdatedState(offset)
    // 🆕 (2026-07-22) radio máximo actual del stick, para calcular la ZONA MUERTA en el bucle.
    var maxRadiusPx by remember { mutableStateOf(1f) }
    val feedback = rememberInputFeedback()

    // Bucle continuo de movimiento a ~30 fps mientras se mantiene arrastrado (lee 'latestOffset').
    LaunchedEffect(isDragging) {
        if (isDragging) {
            while (isActive) {
                val o = latestOffset
                val mag = sqrt(o.x * o.x + o.y * o.y)
                // 🆕 (2026-07-22) ZONA MUERTA: el stick debe estar CLARAMENTE desviado (>28% del
                // radio) para contar como dirección; cerca del centro = NEUTRO → onRelease. Antes,
                // sin zona muerta, un residual mínimo hacia ↓ te dejaba "agachado" al deslizar el
                // pulgar de vuelta al centro (no todos sueltan LEVANTANDO limpio el dedo).
                if (mag > maxRadiusPx * 0.28f) {
                    // Compose: la 'Y' crece hacia abajo → invertimos la 'y' para el ángulo.
                    onMove(kotlin.math.atan2(-o.y.toDouble(), o.x.toDouble()))
                } else {
                    onRelease()
                }
                delay(33) // ~30 fps
            }
        }
    }

    val pointerModifier = if (respondToTouchDown) {
        // 🆕 Joystick virtual con RESPUESTA INMEDIATA: la deflexión = posición del toque respecto al
        // CENTRO, y se dispara YA (sin touch-slop ni esperar el bucle) → un tap/pica ya responde.
        Modifier.pointerInput(Unit) {
            val innerRadiusPx = (tamano * RADIO_STICK_RELATIVO).toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxRadius = ((size.width / 2f) - innerRadiusPx).coerceAtLeast(1f)
                maxRadiusPx = maxRadius
                isDragging = true
                feedback.tap()
                fun place(pos: Offset) {
                    val raw = pos - center
                    val dist = sqrt(raw.x * raw.x + raw.y * raw.y)
                    offset = if (dist > maxRadius) raw * (maxRadius / dist) else raw
                }
                place(down.position)
                // Respuesta INMEDIATA del primer toque (el bucle de 33 ms mantiene el HOLD después).
                val m0 = sqrt(offset.x * offset.x + offset.y * offset.y)
                if (m0 > maxRadius * 0.28f) {
                    onMove(kotlin.math.atan2(-offset.y.toDouble(), offset.x.toDouble()))
                } else {
                    onRelease()
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    place(change.position)
                }
                isDragging = false
                offset = Offset.Zero
                onRelease()
            }
        }
    } else {
        // Comportamiento de ARRASTRE de siempre (mundo abierto): la deflexión acumula el drag.
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { isDragging = true; feedback.tap() },
                onDragEnd = { isDragging = false; offset = Offset.Zero; onRelease() },
                onDragCancel = { isDragging = false; offset = Offset.Zero; onRelease() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val newOffset = offset + dragAmount
                    val maxRadius = (size.width / 2f) - (tamano * RADIO_STICK_RELATIVO).toPx()
                    maxRadiusPx = maxRadius // 🆕 para la zona muerta del bucle
                    val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)

                    offset = if (distance > maxRadius) {
                        newOffset * (maxRadius / distance)
                    } else {
                        newOffset
                    }
                }
            )
        }
    }

    Box(
        modifier = modifier
            .size(tamano)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = backgroundAlpha.coerceIn(0f, 1f)))
            .then(pointerModifier),
        contentAlignment = Alignment.Center
    ) {
        // Círculo interior (El "pulgar" del joystick). Se ACLARA mientras se arrastra (resalte visual).
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                .size(tamano * RADIO_STICK_RELATIVO * 2)
                .clip(CircleShape)
                .background(
                    if (isDragging) Color.LightGray.copy(alpha = 0.95f)
                    else Color.DarkGray.copy(alpha = 0.8f)
                )
        )
    }
}

/**
 * Componente individual del botón actualizado con el detector de gestos.
 */
@Composable
fun ActionButton(
    text: String,
    color: Color,
    /** Diámetro REAL del botón. Ver la nota de [JoystickController.tamano]: nada de `scale()`. */
    tamano: Dp = TamanoBotonAccion,
    onHoldEvent: (Boolean) -> Unit
) {
    val feedback = rememberInputFeedback()
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .padding(tamano * 0.083f)
            .size(tamano)
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
