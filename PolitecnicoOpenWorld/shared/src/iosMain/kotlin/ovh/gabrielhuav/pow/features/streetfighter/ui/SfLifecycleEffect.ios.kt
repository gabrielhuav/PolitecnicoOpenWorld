package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

@Composable
actual fun SfLifecycleEffect(
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val pauseActual = rememberUpdatedState(onPause)
    val resumeActual = rememberUpdatedState(onResume)
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val pauseObserver = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = null,
        ) { pauseActual.value() }
        val resumeObserver = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null,
        ) { resumeActual.value() }
        onDispose {
            center.removeObserver(pauseObserver)
            center.removeObserver(resumeObserver)
        }
    }
}
