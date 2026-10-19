package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import ovh.gabrielhuav.pow.features.audio.SoundManager
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.AndroidStreetFighterViewModel

/** Entrada Android: Hilt queda fuera de la pantalla común. */
@Composable
fun StreetFighterScreen(
    onExitToMap: () -> Unit,
    viewModel: AndroidStreetFighterViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val soundManager = remember { SoundManager.getInstance(context) }
    val state by viewModel.state.collectAsState()
    val controller = remember(viewModel) { AndroidStreetFighterController(viewModel) }

    var previousRoundNumber by rememberSaveable { mutableStateOf(0) }
    
    // 1. Sonido al iniciar una ronda (dispara al cambiar el número de ronda)
    LaunchedEffect(state.roundNumber) {
        val currentRound = state.roundNumber
        if (state.roundNumber > 0) {
            soundManager.playStartRound()
            previousRoundNumber = currentRound
        }
    }

    // 2. Sonido al finalizar el combate completo (Victoria o Derrota)
    LaunchedEffect(state.battleEnded, state.winnerIndex) {
        if (state.battleEnded && state.winnerIndex != null) {
            if (state.winnerIndex == 0) {
                soundManager.playVictory()
            } else {
                soundManager.playLose()
            }
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