package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun SfLifecycleEffect(
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val pauseActual = rememberUpdatedState(onPause)
    val resumeActual = rememberUpdatedState(onResume)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        // ⚠️ `addObserver` PONE AL DÍA al observador nuevo: si la Activity ya está RESUMED —y lo
        // está, porque la pantalla se compone estando delante— le entrega ON_CREATE/ON_START/
        // ON_RESUME de golpe. O sea que `onResume` disparaba UNA VEZ solo por montar la pantalla,
        // sin que la app se hubiera minimizado nunca.
        //
        // Con el `MediaPlayer` suelto de antes daba igual: `start()` sobre algo que ya suena es un
        // no-op. Pero `PowClip.reproducir` REBOBINA por contrato, así que ese resume de más hacía
        // que la música del SF arrancara y volviera a arrancar al entrar. Por eso reanudar se
        // empareja con una pausa previa: sin pausa antes, no hay nada que reanudar.
        //
        // En iOS no hace falta el emparejamiento — `UIApplicationDidBecomeActive` no se entrega
        // retroactivamente a un observador que llega tarde—, pero el contrato queda igual en las
        // dos plataformas, que es lo que la pantalla compartida espera.
        var pausado = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    pausado = true
                    pauseActual.value()
                }
                Lifecycle.Event.ON_RESUME -> if (pausado) {
                    pausado = false
                    resumeActual.value()
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
