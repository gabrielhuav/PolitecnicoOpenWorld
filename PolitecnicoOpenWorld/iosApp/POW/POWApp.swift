import SwiftUI

/// 🍏 App iOS MÍNIMA de POW — Fase 1.5 de `PLAN_MIGRACION_KMP.md`.
///
/// ⚠️ **Esto NO es el juego.** Es la prueba de la suposición más cara de todo el plan de migración:
/// que iOS puede reutilizar el mapa Leaflet del juego TAL CUAL, metiéndolo en un `WKWebView`, sin
/// escribir un renderer de mapas nativo.
///
/// El HTML no se duplica ni se traduce: se le pide al módulo compartido `:shared`, que es el MISMO
/// código Kotlin que usa Android (`WorldMapLeafletHtml.kt`, 929 líneas, cero imports).
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
