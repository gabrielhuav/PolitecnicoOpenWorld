package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel

/** Entrada Android: Hilt queda fuera de la pantalla común. */
@Composable
fun StreetFighterScreen(
    onExitToMap: () -> Unit,
    viewModel: StreetFighterViewModel = hiltViewModel(),
) {
    val controller = remember(viewModel) { AndroidStreetFighterController(viewModel) }
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
