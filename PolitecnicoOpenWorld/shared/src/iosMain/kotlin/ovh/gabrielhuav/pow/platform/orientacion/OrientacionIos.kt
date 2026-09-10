package ovh.gabrielhuav.pow.platform.orientacion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations

/**
 * 🔄🍏 ORIENTACIÓN DE PANTALLA EN iOS — el equivalente del `requestedOrientation` de Android.
 *
 * ## Por qué existe
 *
 * Android tiene UNA regla, en `AppNavGraph.kt`: **el juego va en horizontal y solo los menús
 * permiten vertical**. iOS no tenía nada, así que el mundo abierto salía en vertical y el HUD tenía
 * que encogerse a 100 dp para caber — y entonces **los controles ya no eran los mismos** que en
 * Android y que en el modo pelea. Con esta costura el HUD vuelve a `ControllerBaseSize` y hay UN
 * solo control para las dos plataformas.
 *
 * ## Hacen falta DOS mitades, y con una sola no pasa nada
 *
 * 1. **DECLARAR** la orientación: el `UIViewController` raíz tiene que devolverla en
 *    `supportedInterfaceOrientations`. **Eso NO se puede hacer desde Kotlin** (ver abajo) → lo hace
 *    `PowAppDelegate`, en `iosApp/POW/POWApp.swift`, que lee [esHorizontal]. Va en el AppDelegate y
 *    no en un `UIViewController` propio porque con SwiftUI el raíz es un `UIHostingController`, que
 *    **no** consulta a sus hijos.
 * 2. **PEDIRLA**: `requestGeometryUpdate` sobre la `UIWindowScene`, que es lo que hace girar la
 *    pantalla en el momento. Eso sí es Kotlin y está aquí, en [aplicar].
 *
 * Sin (1) el usuario puede volver a girar a vertical enseguida; sin (2) la orientación nueva no se
 * aplica hasta que el usuario gire el teléfono.
 *
 * > ### ⚠️ MEDIDO (2026-08-17): `supportedInterfaceOrientations` NO se puede sobreescribir en
 * > Kotlin/Native
 * >
 * > Se intentó un contenedor `UIViewController` escrito en Kotlin y el compilador corta con
 * > **`'supportedInterfaceOrientations' overrides nothing`**, tanto en forma de `fun` como de `val`.
 * > El motivo: en UIKit ese miembro vive en la **categoría** `UIViewController (UIViewControllerRotation)`,
 * > y Kotlin/Native expone las categorías como **extensiones**, que no son sobreescribibles.
 * > `setNeedsUpdateOfSupportedInterfaceOrientations()` es de la misma categoría, pero ESA sí se
 * > puede **llamar** (con su `import` explícito, que es lo que faltaba). **No vuelvas a intentar el
 * > contenedor en Kotlin**: la mitad que declara la orientación es Swift por obligación, no por gusto.
 *
 * ⚠️ **No se usa el truco viejo** `UIDevice.setValue(_:forKey:"orientation")`: es API privada y la
 * App Store la rechaza. `requestGeometryUpdate` es la vía pública desde iOS 16, y el target mínimo
 * del proyecto **es 16.0** (`IPHONEOS_DEPLOYMENT_TARGET`), así que no hay que gatear por versión.
 *
 * ⚠️ **El estado lo lee UIKit en el hilo principal** y lo escribe un efecto de Compose, que corre
 * ahí mismo. Por eso un `var` normal basta y no hace falta cerrojo.
 */
object OrientacionPow {

    /**
     * `true` = la app debe ir en horizontal. **Lo lee Swift** (`PowAppDelegate`, en `POWApp.swift`).
     *
     * Es un `Boolean` y no la máscara de UIKit a propósito: cruzar un `UIInterfaceOrientationMask`
     * (un option-set de `NSUInteger`) por el puente a Swift obliga a convertir tipos en el lado
     * Swift; un booleano no tiene ambigüedad y deja la traducción a UIKit en un solo sitio.
     */
    var esHorizontal: Boolean = false
        private set

    /**
     * El controlador raíz, registrado por Swift en su `viewDidLoad`.
     *
     * ⚠️ Si fuera `null` (nadie montó el host), [aplicar] no hace nada en vez de fallar: es
     * preferible que la app se quede como estaba a que se caiga por una pantalla que gira mal.
     */
    var contenedor: UIViewController? = null

    /** Cambia la orientación permitida y la aplica ya. No-op si no cambió. */
    fun fijar(horizontal: Boolean) {
        if (esHorizontal == horizontal) return
        esHorizontal = horizontal
        aplicar()
    }

    private val mascara: UIInterfaceOrientationMask
        get() = if (esHorizontal) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskAll

    @OptIn(ExperimentalForeignApi::class)
    private fun aplicar() {
        // 1) Que iOS vuelva a preguntarle al host cuáles son las orientaciones válidas.
        contenedor?.setNeedsUpdateOfSupportedInterfaceOrientations()
        // 2) Y que gire AHORA, sin esperar a que el usuario mueva el teléfono.
        val escena = UIApplication.sharedApplication.connectedScenes
            .firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return
        escena.requestGeometryUpdateWithPreferences(
            UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mascara),
            errorHandler = null,
        )
    }
}

/**
 * Mientras esta pantalla esté en el árbol, la app va en **horizontal**; al salir, se libera.
 *
 * Es el equivalente exacto del `DisposableEffect` que Android tiene en `AppNavGraph`: la orientación
 * la fija la PANTALLA, no un ajuste global, para que no queden estados colgados al navegar.
 */
@Composable
fun ForzarHorizontal() {
    DisposableEffect(Unit) {
        OrientacionPow.fijar(horizontal = true)
        onDispose { OrientacionPow.fijar(horizontal = false) }
    }
}
