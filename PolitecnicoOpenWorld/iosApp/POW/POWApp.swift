import SwiftUI
import UIKit
import Shared

/// 🍏 POW para iOS.
///
/// Todo lo que hace Swift en esta app es esto: abrir una ventana y meter dentro el
/// `UIViewController` de Compose que devuelve `:shared`. **El juego es Kotlin**, el mismo código
/// que corre en Android; ver `ContentView.swift` y `PowAppIos.kt`.
///
/// El proyecto Xcode salió de la plantilla vacía de Xcode y se renombró, en vez de escribir un
/// `project.pbxproj` a mano, que es frágil y se rompe en cuanto Xcode lo vuelve a tocar.
/// Por lo mismo, **el `AppDelegate` de abajo vive en este archivo y no en uno nuevo**: añadir un
/// `.swift` obliga a tocar el `project.pbxproj`.
@main
struct POWApp: App {
    /// Necesario SOLO para la orientación (ver [PowAppDelegate]). SwiftUI no expone ese gancho.
    @UIApplicationDelegateAdaptor(PowAppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/// 🔄 La mitad Swift de la costura de orientación. La otra mitad es `OrientacionIos.kt`.
///
/// ## Por qué esto tiene que ser Swift
///
/// Android fuerza horizontal en el juego (`AppNavGraph.kt`) y deja vertical solo en los menús. Para
/// hacer lo mismo en iOS hay que responder a `supportedInterfaceOrientations`, y **eso no se puede
/// escribir en Kotlin**: en UIKit vive en la categoría `UIViewController (UIViewControllerRotation)`,
/// que Kotlin/Native expone como *extensión*, y las extensiones no se sobreescriben. Está medido y
/// explicado en `OrientacionIos.kt` — el compilador corta con `'…' overrides nothing`.
///
/// ## Por qué en el AppDelegate y no en un `UIViewController`
///
/// Con SwiftUI el controlador raíz es un `UIHostingController`, y **no** consulta a sus hijos para
/// la orientación: un subcontrolador nuestro respondería y nadie le haría caso. iOS pregunta ANTES
/// a `application(_:supportedInterfaceOrientationsFor:)`, que es este gancho y manda sobre todo lo
/// demás.
///
/// Quien hace girar la pantalla en el momento es `requestGeometryUpdate`, y eso sí lo hace Kotlin.
/// Sin este delegado, la pantalla giraría y el usuario podría volver a girarla enseguida; sin la
/// parte de Kotlin, no giraría hasta que el usuario moviera el teléfono. **Hacen falta las dos.**
class PowAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        supportedInterfaceOrientationsFor window: UIWindow?
    ) -> UIInterfaceOrientationMask {
        // UIKit intersecta esto con el `Info.plist`, así que aquí no hay que repetir la lista.
        OrientacionPow.shared.esHorizontal ? .landscape : .all
    }
}
