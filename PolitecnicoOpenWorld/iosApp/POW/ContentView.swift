import SwiftUI
import Shared

/// EL JUEGO EN iOS, entero. Un solo `UIViewController` de Compose que trae dentro el menú
/// principal, Ajustes, Coleccionables y Titulación por Combate.
///
/// ⚠️ **Toda la navegación vive en Kotlin** (`PowAppIos.kt`), no aquí. Si se repartiera entre
/// SwiftUI y Compose habría dos árboles que mantener en sincronía, y al volver de una pantalla
/// quedaría un host encima del otro. Para añadir una pantalla a iOS **no se toca este archivo**:
/// se toca `PowAppIos.kt`.
///
/// Qué modos aparecen lo decide `PowModos.kt`, que es el catálogo compartido: Mundo Libre, Modo
/// Historia y Multijugador no se pintan en iOS.
struct ContentView: View {
    var body: some View {
        PowAppTab()
            .ignoresSafeArea()
    }
}

/// El puente Swift ↔ Compose. Es literalmente lo único que hace SwiftUI en esta app.
struct PowAppTab: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let vc = PowAppIosKt.crearAppIos()
        // 🔄 Se lo prestamos a Kotlin SOLO para que pueda pedir
        // `setNeedsUpdateOfSupportedInterfaceOrientations()` al cambiar de pantalla. Quien decide
        // la orientación es `PowAppDelegate` (POWApp.swift); esto solo hace que iOS vuelva a
        // preguntársela en el momento, en vez de esperar a que el usuario gire el teléfono.
        OrientacionPow.shared.contenedor = vc
        return vc
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
