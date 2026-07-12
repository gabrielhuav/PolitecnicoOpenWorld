package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleDPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleJoystickController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.exitGlobalZombieMode
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.handleInteraction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.moveCharacter
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.moveCharacterByAngle
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onClaimCollectiblePressed
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onInteractButtonPressed
import ovh.gabrielhuav.pow.features.settings.models.ControlType

/**
 * Controles en pantalla del mundo abierto (extraído de WorldMapScreen.kt para reducir su
 * tamaño): vals de layout (escala/padding según orientación), botón "Salir del apocalipsis"
 * y la fila inferior de controles (D-pad/joystick de movimiento o conducción + el MISMO
 * diamante Xbox A/B/X/Y a pie y conduciendo). Es una extensión de [BoxScope] porque usa `align`.
 * MVVM: solo observa `uiState` y emite intenciones al VM.
 * TOMBSTONE (2026-07-12): el "mantener Y 3 s → menú de teletransporte" (`yButtonHoldJob`) se
 * RETIRÓ a petición del dueño — el teletransporte tiene su propio botón en el menú Mapa. NO
 * recrear la pulsación larga de Y.
 *
 * @param optionsExpanded si el menú de Opciones está abierto (desplaza el control de la
 *   derecha a la izquierda — en horizontal Y en vertical — para no taparlo).
 */
@Composable
fun BoxScope.WorldMapControls(
    uiState: WorldMapState,
    viewModel: WorldMapViewModel,
    optionsExpanded: Boolean
) {

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Misma altura/escala que los controles de INTERIORES (ZombieHud): se igualaron
    // estos valores + systemBarsPadding para que los controles queden a la misma altura
    // en el mundo global y en los interiores.
    val maxScale = if (isPortrait) 0.95f else 1.3f
    val effectiveScale = uiState.controlsScale.coerceAtMost(maxScale)
    val sidePadding = if (isPortrait) 8.dp else 32.dp
    val bottomPadding = if (isPortrait) 32.dp else 20.dp

    // Al abrir el menú de Opciones/Mapa (arriba a la derecha), sus entradas se extienden
    // hacia abajo y chocan con el control de la derecha (D-pad/diamante). Desplazamos ese
    // control hacia la izquierda mientras el menú está abierto para que el usuario pueda
    // usar el menú (con su scroll) sin que tape los botones. 🆕 2026-07-12: aplica TAMBIÉN
    // en VERTICAL (antes solo horizontal; en vertical el menú se encimaba con A/B/X/Y).
    val rightCtrlShift by animateDpAsState(
        targetValue = when {
            optionsExpanded && !isPortrait -> (-150).dp
            optionsExpanded && isPortrait -> (-120).dp
            else -> 0.dp
        },
        label = "rightCtrlShift"
    )
    val rightShiftMod = Modifier.offset(x = rightCtrlShift)

    if (uiState.globalZombieMode) {
        androidx.compose.material3.Button(
            onClick = { viewModel.exitGlobalZombieMode() },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 110.dp)
        ) {
            androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_exit_apocalypse), color = androidx.compose.ui.graphics.Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }

    if (!uiState.isDesignerMode && !uiState.showInteriorDebugOverlay) { // Oculta joystick y botones en modo diseñador y al editar el Debug Interiores
        Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = bottomPadding, start = sidePadding, end = sidePadding).systemBarsPadding(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (uiState.isDriving) {
            // D-pad de conducción: SOLO gira (IZQ/DER). Arriba/abajo quedan inertes
            // a propósito — gas y freno viven únicamente en el diamante PS4.
            val drivingDpad = @Composable { m: Modifier ->
                // Respeta la preferencia de control: JOYSTICK = joystick de dirección (izq/der);
                // D-pad = flechitas. Gas/freno siempre en el diamante A/B/X/Y (drivingActions).
                if (uiState.controlType == ControlType.JOYSTICK)
                    VehicleJoystickController(
                        modifier = m.scale(effectiveScale),
                        onSteerLeft = { viewModel.steerLeft(it) },
                        onSteerRight = { viewModel.steerRight(it) }
                    )
                else
                    VehicleDPadController(
                        modifier = m.scale(effectiveScale),
                        onUp = { /* sin uso en conducción */ },
                        onDown = { /* sin uso en conducción */ },
                        onLeft = { viewModel.steerLeft(it) },
                        onRight = { viewModel.steerRight(it) }
                    )
            }
            // MISMO diamante Xbox que a pie (control unificado, 2026-07-03):
            // Y SALIR del coche · A gas · B freno · X freno de mano.
            // 🆕 2026-07-12: se RETIRÓ el "mantener Y 3 s → menú de teletransporte" (petición del
            // dueño): el teletransporte ya tiene su propio botón en el menú Mapa. NO recrearlo.
            val drivingActions = @Composable { m: Modifier ->
                VehicleActionButtonsController(
                    modifier = m.scale(effectiveScale),
                    onAccelerate = { viewModel.accelerate(it) },
                    onBrake = { viewModel.brake(it) },
                    onHandbrake = { viewModel.brake(it) },
                    onExit = { isPressed ->
                        if (isPressed) viewModel.onInteractButtonPressed()
                    }
                )
            }
            // El control de la DERECHA (segundo) recibe el desplazamiento.
            if (uiState.swapControls) { drivingActions(Modifier); drivingDpad(rightShiftMod) } else { drivingDpad(Modifier); drivingActions(rightShiftMod) }
        } else {
                val movementComponent = @Composable { m: Modifier ->
                    if (uiState.controlType == ControlType.DPAD) DPadController(modifier = m.scale(effectiveScale), onDirectionPressed = { viewModel.moveCharacter(it) })
                    else JoystickController(modifier = m.scale(effectiveScale), onMove = { viewModel.moveCharacterByAngle(it) })
                }
                val actionComponent = @Composable { m: Modifier ->
                    ActionButtonsController(
                        modifier = m.scale(effectiveScale),
                        onActionChanged = { action, isPressed ->
                            if (action == GameAction.X && isPressed) {
                                viewModel.handleInteraction()
                            }
                            // 🆕 2026-07-12: Y = solo subir/bajar del coche. El "mantener Y 3 s
                            // → teletransporte" se RETIRÓ (el TP tiene su botón en el menú Mapa).
                            if (action == GameAction.Y && isPressed) {
                                viewModel.onInteractButtonPressed()
                            }
                            viewModel.updateActionState(action, isPressed)
                        },
                        onClaimCollectiblePressed = { viewModel.onClaimCollectiblePressed() }
                    )
                }
                // El control de la DERECHA (segundo) recibe el desplazamiento.
                if (uiState.swapControls) { actionComponent(Modifier); movementComponent(rightShiftMod) } else { movementComponent(Modifier); actionComponent(rightShiftMod) }
            }
        }
    }
}
