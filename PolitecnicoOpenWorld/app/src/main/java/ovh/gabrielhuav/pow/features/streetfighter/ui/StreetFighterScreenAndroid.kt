package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.AndroidStreetFighterViewModel

/* Importaciones para la implementación botón "Back" nativo de Android */
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/** Entrada Android: Hilt queda fuera de la pantalla común. */
@Composable
fun StreetFighterScreen(
    onExitToMap: () -> Unit,
    viewModel: AndroidStreetFighterViewModel = hiltViewModel(),
) {
    val controller = remember(viewModel) { AndroidStreetFighterController(viewModel) }

    /* Implementación de ir atrás del sistema, implemntando el dialogo de salida para confirmación,
    * en vez de sacar al jugador al menu principal por defecto. Fuera del combate se deja el Atrás normal*/
    val sfState by controller.state.collectAsState()
    val backAction = sfSystemBackAction(sfState.inCharacterSelect, sfState.showExitDialog)
    BackHandler(enabled = backAction != SfSystemBack.SISTEMA) {
        when (backAction) {
            SfSystemBack.ABRIR_DIALOGO -> controller.requestExit()
            SfSystemBack.CERRAR_DIALOGO -> controller.dismissExitDialog()
            SfSystemBack.SISTEMA -> Unit
        }
    }

    StreetFighterScreenCommon(
        onExitToMap = onExitToMap,
        controller = controller,
        onlineStatusContent = { state, theme, lowEnd, uiController ->
            AndroidSfOnlineStatusContent(state, theme, lowEnd, uiController)
        },
        onlinePlatformOverlays = { state, visible, onVisibleChange, uiController ->
            AndroidSfOnlinePlatformOverlays(
                state = state,
                showOnlineMenu = visible,
                onShowOnlineMenuChange = onVisibleChange,
                controller = uiController,
            )
        },
    )
}
