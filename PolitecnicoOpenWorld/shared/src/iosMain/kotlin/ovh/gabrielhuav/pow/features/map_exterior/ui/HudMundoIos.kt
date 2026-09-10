package ovh.gabrielhuav.pow.features.map_exterior.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.ui.components.JoystickController

/**
 * 🎮🍏 EL HUD DEL MUNDO ABIERTO EN iOS — joystick + diamante A/B/X/Y.
 *
 * ## Son LOS MISMOS controles que Android
 *
 * `JoystickController` y `ActionButtonsController` salen de `commonMain`: mismos colores, mismas
 * letras, misma disposición Xbox (Y arriba · X izquierda · B derecha · A abajo) y el mismo tacto
 * (vibración incluida, vía `rememberInputFeedback`). **No hay una copia para iOS**, y no debe
 * haberla: si el HUD cambia, cambia para las dos plataformas.
 *
 * ## Van al TAMAÑO DE SIEMPRE, y eso es el contrato
 *
 * No se les pasa `tamano`: usan **`ControllerBaseSize` (180 dp)**, exactamente igual que Android y
 * que el modo pelea. **Los controles son iguales en las dos plataformas, siempre.**
 *
 * ⚠️ **Esto DEPENDE de que la pantalla vaya en horizontal.** Dos controles de 180 dp son 360 dp, y
 * un iPhone en vertical tiene ~390 de ancho: se tocarían y taparían el mapa. Por eso
 * `MapaMundoIos` llama a `ForzarHorizontal()` (`platform/orientacion/OrientacionIos.kt`), que es el
 * equivalente del `requestedOrientation` de Android. **Si algún día se quita ese bloqueo, esto se
 * rompe** — y la salida NO es encoger los controles otra vez, es recuperar el horizontal.
 *
 * > Histórico: hasta el 2026-08-17 iban a 100 dp porque iOS no forzaba orientación. Se quitó ese
 * > encogido al portar la regla de orientación de Android.
 *
 * ⚠️ **Si alguna vez hay que cambiarles el tamaño, se pasa `tamano`, NUNCA un `Modifier.scale`.**
 * Se intentó con `scale()` el 2026-07-31 y **los botones dejaron de responder**: `scale` transforma
 * el dibujo pero no el área táctil, así que el control quedaba donde ya no se veía. Los controles
 * aceptan `tamano` y ajustan sus `dp` de verdad — el tacto no cambia porque el stick y los botones
 * son **proporcionales** al diámetro.
 *
 * ## Los botones que todavía no hacen nada
 *
 * A/B/X/Y avisan con [alPulsarSinFuncion] en vez de quedarse mudos. La lógica de cada uno vive en
 * `WorldMapViewModel`, que sigue en `:app` (fase 5): un botón que no responde parece una app rota;
 * uno que dice por qué, no.
 */
@Composable
fun HudMundoIos(
    modifier: Modifier = Modifier,
    /** Dirección del joystick en radianes. Ya está conectado: mueve al jugador. */
    alMover: (angleRad: Double) -> Unit,
    /** Se llama al soltar el joystick, para que el jugador se pare en seco. */
    alSoltar: () -> Unit,
    /** Aviso de "esto aún no está" al pulsar un botón sin lógica todavía. */
    alPulsarSinFuncion: (GameAction) -> Unit,
) {
    Box(modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            JoystickController(
                backgroundAlpha = 0.35f,
                onRelease = alSoltar,
                // `true` = el joystick responde al TOQUE, sin esperar a que arrastres. Es lo que
                // usa el modo pelea; en un móvil en vertical, donde el pulgar tiene poco recorrido,
                // se nota mucho.
                respondToTouchDown = true,
                onMove = alMover,
            )

            ActionButtonsController(
                backgroundAlpha = 0.35f,
                // Solo interesa el flanco de bajada: avisar una vez por pulsación, no en bucle
                // mientras se mantiene.
                onActionChanged = { accion, pulsado -> if (pulsado) alPulsarSinFuncion(accion) },
            )
        }
    }
}

/** Cartel efímero de "todavía no". Aparece abajo, sin tapar los controles. */
@Composable
fun AvisoSinFuncion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color(0xFF6B1C3A).copy(alpha = 0.92f), CutCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// 🪦 TOMBSTONE (2026-08-17): aquí vivía `TAMANO_VERTICAL = 100.dp`, el encogido que hacía falta
// mientras iOS no forzaba horizontal. **No lo recrees**: el HUD debe usar `ControllerBaseSize`
// como Android y como el modo pelea. Si vuelve a verse apretado, el problema es la orientación
// (`ForzarHorizontal` en `MapaMundoIos`), no el tamaño.
