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
        // Este es el equivalente en iOS de `file:///android_asset/`: registramos un esquema propio
        // y nosotros resolvemos cada petición desde el bundle de la app.
        config.setURLSchemeHandler(PowAssetSchemeHandler(), forURLScheme: PowAssets.scheme)

        let webView = WKWebView(frame: .zero, configuration: config)
        // Sin esto el mapa "rebota" al llegar al borde y se siente roto para un juego.
        webView.scrollView.bounces = false
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        let html = WorldMapLeafletHtmlKt.buildHtml(
            lat: lat,
            lng: lng,
            zoom: zoom,
            assetBaseUrl: PowAssets.baseUrl
        )
        // baseURL nil: todo lo que carga el HTML (Leaflet desde unpkg, teselas de OSM) son URLs
        // absolutas HTTPS, así que no hay nada relativo que resolver.
        webView.loadHTMLString(html, baseURL: nil)
    }
}

/// Constantes del esquema de assets. Un solo sitio donde cambiarlo.
enum PowAssets {
    /// ⚠️ NO puede ser `file`, `http` ni `https`: WebKit se reserva los esquemas estándar y
    /// `setURLSchemeHandler` lanza una excepción si se intenta registrar uno de ellos.
    static let scheme = "pow-asset"
    /// Lo que se le inyecta a `buildHtml`. El host va vacío: `pow-asset:///SPRITES/…`.
    static let baseUrl = "\(scheme):///"
}

/// Sirve al `WKWebView` los assets del juego desde el bundle de la app.
///
/// **Por qué existe:** el HTML del mapa es compartido con Android, y allí las imágenes cuelgan de
/// `file:///android_asset/`, un esquema que en iOS **no existe**. En vez de bifurcar el HTML, se
/// parametrizó el prefijo (`buildHtml(assetBaseUrl:)`) y aquí se resuelve el que usa iOS.
///
/// ⚠️ **Hoy esto devuelve 404 para todo**, y es lo correcto: los 358 MB de assets del juego aún no
/// se empaquetan en el bundle iOS (eso es la Fase 6, con On-Demand Resources por el límite de
/// 200 MB por datos móviles). Lo que se arregla AHORA es el camino: cuando los assets existan,
/// funcionarán sin tocar el mapa. Un 404 limpio es además mejor que el `file://` de antes, que en
/// iOS fallaba de formas raras y silenciosas.
final class PowAssetSchemeHandler: NSObject, WKURLSchemeHandler {

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url else {
            urlSchemeTask.didFailWithError(URLError(.badURL))
            return
        }

        // 'pow-asset:///SPRITES/ICONS/foo.svg' -> 'SPRITES/ICONS/foo.svg'
        let relativePath = url.path.hasPrefix("/") ? String(url.path.dropFirst()) : url.path

        // `subdirectory:` mantiene la jerarquía de carpetas de los assets tal cual está en Android,
        // para que las rutas del HTML compartido sirvan en las dos plataformas sin traducción.
        let fileName = (relativePath as NSString).lastPathComponent
        let subdirectory = (relativePath as NSString).deletingLastPathComponent

        guard
            let fileUrl = Bundle.main.url(
                forResource: (fileName as NSString).deletingPathExtension,
                withExtension: (fileName as NSString).pathExtension,
                subdirectory: subdirectory.isEmpty ? nil : subdirectory
            ),
            let data = try? Data(contentsOf: fileUrl)
        else {
            // 404 explícito: el JS del mapa se limita a mostrar la imagen rota, no se cae.
            let response = HTTPURLResponse(
                url: url, statusCode: 404, httpVersion: "HTTP/1.1", headerFields: nil
            )!
            urlSchemeTask.didReceive(response)
            urlSchemeTask.didFinish()
            return
        }

        let response = URLResponse(
            url: url,
            mimeType: Self.mimeType(for: (fileName as NSString).pathExtension),
            expectedContentLength: data.count,
            textEncodingName: nil
        )
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(data)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {
        // Nada que cancelar: se resuelve de forma síncrona desde disco.
    }

    /// Sin un MIME correcto, WebKit no pinta los SVG (los trata como descarga).
    private static func mimeType(for ext: String) -> String {
        switch ext.lowercased() {
        case "svg":          return "image/svg+xml"
        case "png":          return "image/png"
        case "webp":         return "image/webp"
        case "jpg", "jpeg":  return "image/jpeg"
        case "gif":          return "image/gif"
        default:             return "application/octet-stream"
        }
    }
}
