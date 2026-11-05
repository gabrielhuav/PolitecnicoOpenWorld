package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.AndroidStreetFighterViewModel
import androidx.compose.ui.platform.LocalContext
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.audio.SoundManager

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
    val context = LocalContext.current
    val settings = remember(context) { SettingsRepository(context) }
    val controlsScale = settings.getControlsScale()

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

    val soundManager = remember(context) { SoundManager.getInstance(context) }
    val roundSoundKey = sfRoundSoundKey(sfState)
    var playedRoundKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(roundSoundKey, sfState.inCharacterSelect) {
        if (sfState.inCharacterSelect) playedRoundKey = null
        if (roundSoundKey != null && roundSoundKey != playedRoundKey && soundManager.playStartRound()) {
            playedRoundKey = roundSoundKey
        }
    }

    val matchSound = sfMatchSound(sfState)
    var playedMatchResult by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(matchSound) {
        if (matchSound == null) {
            playedMatchResult = false
        } else if (!playedMatchResult) {
            playedMatchResult = when (matchSound) {
                SfMatchSound.VICTORY -> soundManager.playVictory()
                SfMatchSound.LOSE -> soundManager.playLose()
            }
        }
    }
    DisposableEffect(soundManager) {
        onDispose { soundManager.stopRoundSounds() }
    }

    StreetFighterScreenCommon(
        onExitToMap = onExitToMap,
        controller = controller,
        controlsScale = controlsScale,
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
