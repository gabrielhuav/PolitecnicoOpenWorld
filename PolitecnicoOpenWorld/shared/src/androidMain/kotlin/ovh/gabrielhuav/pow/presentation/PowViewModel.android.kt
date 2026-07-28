package ovh.gabrielhuav.pow.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

/**
 * En Android NO se inventa nada: se hereda del `ViewModel` de androidx, así que Hilt y el ciclo de
 * vida no notan el cambio, y [scope] es literalmente `viewModelScope`.
 */
actual abstract class PowViewModel actual constructor() : ViewModel() {

    actual val scope: CoroutineScope get() = viewModelScope

    protected actual open fun alLimpiar() = Unit

    /** El framework ya cancela `viewModelScope`; aquí solo se encadena el gancho común. */
    final override fun onCleared() {
        alLimpiar()
        super.onCleared()
    }

    /** En Android esto es un no-op salvo que alguien lo fuerce: manda `onCleared()`. */
    actual fun clear() = onCleared()
}
