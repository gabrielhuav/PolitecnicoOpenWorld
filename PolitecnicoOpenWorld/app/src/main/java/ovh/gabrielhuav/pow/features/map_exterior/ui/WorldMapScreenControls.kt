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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.Ps4ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleDPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleJoystickController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// REFACTOR: extensión del VM (menú de teletransporte) → import explícito.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleTeleportMenu
import ovh.gabrielhuav.pow.features.settings.models.ControlType

/**
 * Controles en pantalla del mundo abierto (extraído de WorldMapScreen.kt para reducir su
 * tamaño): vals de layout (escala/padding según orientación), botón "Salir del apocalipsis"
 * y la fila inferior de controles (D-pad/joystick de movimiento o conducción + botones de
 * acción A/B/X/Y o diamante PS4). Es una extensión de [BoxScope] porque usa `align`.
 * MVVM: solo observa `uiState` y emite intenciones al VM. La pulsación larga de Y/△
 * (mantener 3 s → menú de teletransporte) se gestiona aquí con `yButtonHoldJob` local.
 *
 * @param optionsExpanded si el menú de Opciones está abierto (en horizontal desplaza el
 *   control de la derecha para no taparlo).
 */
@Composable
fun BoxScope.WorldMapControls(
    uiState: WorldMapState,
    viewModel: WorldMapViewModel,
    optionsExpanded: Boolean
) {
    val coroutineScope = rememberCoroutineScope()
    var yButtonHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    // Misma altura/escala que los controles de INTERIORES (ZombieHud): se igualaron
    // estos valores + systemBarsPadding para que los controles queden a la misma altura
    // en el mundo global y en los interiores.
    val maxScale = if (isPortrait) 0.95f else 1.3f
    val effectiveScale = uiState.controlsScale.coerceAtMost(maxScale)
    val sidePadding = if (isPortrait) 8.dp else 32.dp
    val bottomPadding = if (isPortrait) 32.dp else 20.dp

    // En HORIZONTAL, al abrir el menú de Opciones, este (arriba a la derecha) se
    // extiende hacia abajo y choca con el control de la derecha (D-pad/diamante).
    // Desplazamos ese control hacia la izquierda mientras el menú está abierto para
    // que el usuario pueda usar el menú (con su scroll) sin que tape los botones.
    val isMenuOpenLandscape = optionsExpanded && !isPortrait
    val rightCtrlShift by animateDpAsState(
        targetValue = if (isMenuOpenLandscape) (-150).dp else 0.dp,
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
                // D-pad = flechitas. Gas/freno siempre en el diamante PS4 (drivingActions).
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
            // Diamante estilo PS4: △ SALIR · ✕ gas · ○ freno · □ freno de mano.
            val drivingActions = @Composable { m: Modifier ->
                Ps4ActionButtonsController(
                    modifier = m.scale(effectiveScale),
                    onAccelerate = { viewModel.accelerate(it) },
                    onBrake = { viewModel.brake(it) },
                    onHandbrake = { viewModel.brake(it) },
                    onExit = { isPressed ->
                        if (isPressed) {
                            viewModel.onInteractButtonPressed()
                            yButtonHoldJob?.cancel()
                            yButtonHoldJob = coroutineScope.launch { kotlinx.coroutines.delay(3000); viewModel.toggleTeleportMenu(true) }
                        } else { yButtonHoldJob?.cancel() }
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
                            if (action == GameAction.Y) {
                                if (isPressed) {
                                    viewModel.onInteractButtonPressed()
                                    yButtonHoldJob?.cancel()
                                    yButtonHoldJob = coroutineScope.launch { kotlinx.coroutines.delay(3000); viewModel.toggleTeleportMenu(true) }
                                } else {
                                    yButtonHoldJob?.cancel()
                                }
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
