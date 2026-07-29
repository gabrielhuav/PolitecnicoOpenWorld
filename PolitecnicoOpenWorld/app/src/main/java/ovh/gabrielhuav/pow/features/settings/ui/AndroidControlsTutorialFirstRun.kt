package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.tutorial_ext_title
import ovh.gabrielhuav.pow.shared.recursos.tutorial_int_title

/** Puente Android para los flags de primera visita de los modos que aún son Android-only. */
@Composable
fun ControlsTutorialFirstRun(interior: Boolean) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    var showPrompt by remember {
        mutableStateOf(
            if (interior) {
                !repository.getTutorialInteriorSeen()
            } else {
                !repository.getTutorialExteriorSeen()
            },
        )
    }
    var showTutorial by remember { mutableStateOf(false) }
    val markSeen = {
        if (interior) repository.saveTutorialInteriorSeen()
        else repository.saveTutorialExteriorSeen()
    }
    if (showPrompt) {
        ControlsTutorialPrompt(
            onAccept = {
                showPrompt = false
                showTutorial = true
                markSeen()
            },
            onDecline = {
                showPrompt = false
                markSeen()
            },
        )
    }
    if (showTutorial) {
        ControlsTutorialOverlay(
            titleRes = if (interior) Res.string.tutorial_int_title else Res.string.tutorial_ext_title,
            pages = if (interior) interiorTutorialPages() else exteriorTutorialPages(),
            onDismiss = { showTutorial = false },
        )
    }
}
