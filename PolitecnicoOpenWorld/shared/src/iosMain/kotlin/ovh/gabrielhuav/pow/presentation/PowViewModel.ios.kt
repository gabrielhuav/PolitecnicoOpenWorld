package ovh.gabrielhuav.pow.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * En iOS no hay framework que gestione el ciclo de vida, así que el scope se crea y se cancela a
 * mano. Mismas piezas que `viewModelScope` en Android: dispatcher de UI + [SupervisorJob] (para que
 * el fallo de una corrutina no se lleve por delante a las hermanas).
 */
actual abstract class PowViewModel actual constructor() {

    actual val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    protected actual open fun alLimpiar() = Unit

    /** Idempotente a propósito: en iOS es fácil que la pantalla lo llame dos veces al cerrarse. */
    private var limpiado = false

    actual fun clear() {
        if (limpiado) return
        limpiado = true
        alLimpiar()
        scope.cancel()
    }
}
