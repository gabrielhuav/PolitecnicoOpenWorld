import SwiftUI
import WebKit
import Shared

/// Pantalla única: el mapa del juego a pantalla completa.
///
/// Las coordenadas NO son inventadas: son las de la ESCOM del IPN, la escuela que el juego trae
/// como disponible en `SchoolCatalog.kt` (`CampaignSchool("escom", "IPN", 19.504603, -99.145985)`).
/// Zoom 16, que es la escala a la que se juega el mundo abierto.
struct ContentView: View {

    private let lat = 19.504603
    private let lng = -99.145985
    private let zoom: Int32 = 16

    var body: some View {
        ZStack(alignment: .top) {
            MapWebView(lat: lat, lng: lng, zoom: zoom)
                .ignoresSafeArea()

            Text("POW · mapa Leaflet servido por :shared")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(.black.opacity(0.65), in: Capsule())
                .padding(.top, 8)
        }
    }
}

/// Envuelve un `WKWebView` para SwiftUI y le carga el mapa.
///
/// ⚠️ **El HTML viene de Kotlin, no de aquí.** `WorldMapLeafletHtmlKt.buildHtml(...)` es la función
/// del módulo `:shared` — exactamente la misma que llama `WorldMapScreenWeb.kt` en Android. Solo
/// existe UNA copia del mapa: si cambia, cambia para las dos plataformas a la vez.
///
/// El equivalente de esto en Android es `WebView.loadDataWithBaseURL(...)`.
///
/// ⚠️ Vive en este mismo fichero A PROPÓSITO. Añadir un `.swift` nuevo obliga a editar a mano las
/// listas de ficheros del `project.pbxproj`, que es justo donde estos proyectos se corrompen.
/// Cuando el proyecto se abra en Xcode y se añadan ficheros desde el IDE, esto se puede separar.
struct MapWebView: UIViewRepresentable {

    let lat: Double
    let lng: Double
    let zoom: Int32

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        // El mapa necesita JS: es Leaflet entero.
        config.defaultWebpagePreferences.allowsContentJavaScript = true

        let webView = WKWebView(frame: .zero, configuration: config)
        // Sin esto el mapa "rebota" al llegar al borde y se siente roto para un juego.
        webView.scrollView.bounces = false
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let html = WorldMapLeafletHtmlKt.buildHtml(lat: lat, lng: lng, zoom: zoom)
        // baseURL nil: todo lo que carga el HTML (Leaflet desde unpkg, teselas de OSM) son URLs
        // absolutas HTTPS, así que no hay nada relativo que resolver.
        webView.loadHTMLString(html, baseURL: nil)
    }
}
