package ovh.gabrielhuav.pow.ui.components

import androidx.compose.runtime.Composable

/** Retroalimentación táctil/sonora al pulsar un control de juego. */
interface InputFeedback {
    fun tap()
}

@Composable
expect fun rememberInputFeedback(): InputFeedback
