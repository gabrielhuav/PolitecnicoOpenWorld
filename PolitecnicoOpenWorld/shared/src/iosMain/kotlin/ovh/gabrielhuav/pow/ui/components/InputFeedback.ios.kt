package ovh.gabrielhuav.pow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private object InputFeedbackIos : InputFeedback {
    override fun tap() = Unit
}

@Composable
actual fun rememberInputFeedback(): InputFeedback = remember { InputFeedbackIos }
