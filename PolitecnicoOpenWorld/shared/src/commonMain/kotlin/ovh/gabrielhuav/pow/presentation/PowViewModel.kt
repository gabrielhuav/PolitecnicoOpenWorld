package ovh.gabrielhuav.pow.presentation

import kotlinx.coroutines.CoroutineScope

/**
 * 🍏 CLASE BASE DE LOS VIEWMODEL QUE VIVEN EN `commonMain`.
 *
 * POR QUÉ NO SE USA LA LIBRERÍA "OFICIAL": existe
 * `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`, que hace justo esto. Pero la ÚNICA
 * versión con artefactos de iOS es la 2.11.0 (medido: 2.10.0 y 2.9.4 → HTTP 404), y esa arrastra
 * `androidx.lifecycle:*:2.11.0` en Android, que EXIGE `compileSdk 37`. Subir el compileSdk de una
 * app en producción para poder compartir una clase base no compensa. Esto cuesta ~20 líneas.
 *
 * QUÉ GARANTIZA:
 * - En **Android** un `PowViewModel` **ES** un `androidx.lifecycle.ViewModel` de toda la vida, así
 *   que Hilt (`@HiltViewModel`), `hiltViewModel()` y el ciclo de vida siguen funcionando igual, y
 *   [scope] es el `viewModelScope` de siempre (se cancela solo al destruirse).
 * - En **iOS** es una clase normal con su propio scope, que hay que cerrar llamando a [clear].
 *
 * ⚠️ USA [scope], NO `viewModelScope`: lo segundo solo existe en Android y no compila en iOS.
 *
 * ⚠️ Para limpiar recursos sobreescribe [alLimpiar], NO `onCleared()`: `onCleared` solo existe en
 * el lado de Android y en iOS nadie lo llamaría.
 */
expect abstract class PowViewModel() {

    /**
     * Scope atado a la vida del ViewModel. En Android es `viewModelScope` (main dispatcher +
     * SupervisorJob); en iOS, uno equivalente creado a mano.
     */
    val scope: CoroutineScope

    /** Gancho de limpieza. Se llama UNA vez, cuando el ViewModel deja de usarse. */
    protected open fun alLimpiar()

    /**
     * Cierra el ViewModel: cancela [scope] y llama a [alLimpiar].
     *
     * ⚠️ En Android **no lo llames a mano** — lo hace el framework. Es en **iOS** donde la pantalla
     * tiene que llamarlo al desaparecer; si no, el bucle del juego sigue corriendo.
     */
    fun clear()
}
