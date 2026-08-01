import SwiftUI

/// 🍏 POW para iOS.
///
/// Todo lo que hace Swift en esta app es esto: abrir una ventana y meter dentro el
/// `UIViewController` de Compose que devuelve `:shared`. **El juego es Kotlin**, el mismo código
/// que corre en Android; ver `ContentView.swift` y `PowAppIos.kt`.
///
/// El proyecto Xcode salió de la plantilla vacía de Xcode y se renombró, en vez de escribir un
/// `project.pbxproj` a mano, que es frágil y se rompe en cuanto Xcode lo vuelve a tocar.
@main
struct POWApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
