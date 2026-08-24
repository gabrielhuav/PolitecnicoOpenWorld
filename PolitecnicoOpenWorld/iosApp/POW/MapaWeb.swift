import SwiftUI
import WebKit
import Shared

// ⚠️ ESTE ARCHIVO NO LO USA NINGUNA PANTALLA HOY, Y ES A PROPÓSITO.
//
// 🆕 (2026-08-20) OJO, esto YA NO es "el mundo abierto no existe en iOS": sí existe. MUNDO LIBRE
// abre `MapaMundoIos` (`PowAppIos.kt`, `Pantalla.MAPA`) — con NPCs, colisiones y el puente en las
// dos direcciones. Lo que pasa es que esa pantalla es **Compose + `UIKitView`** y monta su propia
// `WKWebViewConfiguration` desde Kotlin, así que **no pasa por este archivo**.
//
// Y por eso mismo el manejador de assets de abajo tuvo que reescribirse en Kotlin
// (`AssetsWebIos.kt`): el de aquí nunca llegó a registrarse en el WebView del mundo, y el mapa
// salía sin un solo sprite. **Si tocas uno, mira el otro** — hoy hay dos copias de la misma idea.
//
// Se conserva como la versión SwiftUI pura del mismo camino, útil para aislar si algo falla por
// culpa de Compose. Para el juego, **no hace falta tocarlo para nada**.

/// El mapa del juego a pantalla completa.
///
/// Las coordenadas NO son inventadas: son las de la ESCOM del IPN, la escuela que el juego trae
/// como disponible en `SchoolCatalog.kt` (`CampaignSchool("escom", "IPN", 19.504603, -99.145985)`).
/// Zoom 16, que es la escala a la que se juega el mundo abierto.
struct MapaTab: View {

    private let lat = 19.504603
    private let lng = -99.145985
    private let zoom: Int32 = 16

    var body: some View {
        MapWebView(lat: lat, lng: lng, zoom: zoom)
            .ignoresSafeArea()
    }
}

/// Envuelve un `WKWebView` para SwiftUI y le carga el mapa.
///
/// ⚠️ **El HTML viene de Kotlin, no de aquí.** `WorldMapLeafletHtmlKt.buildHtml(...)` es la función
/// del módulo `:shared` — exactamente la misma que llama `WorldMapScreenWeb.kt` en Android. Solo
/// existe UNA copia del mapa: si cambia, cambia para las dos plataformas a la vez.
///
/// El equivalente de esto en Android es `WebView.loadDataWithBaseURL(...)`.
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
/// ⚠️ **Hoy devuelve 404 para casi todo, y es lo correcto:** al bundle de iOS solo entran los
/// assets de las pantallas que existen (`STREETFIGHTER` y `SPRITES/COLLECTIBLES`, ver la fase
/// "Assets de SF al bundle" del proyecto Xcode). Los sprites del mundo abierto son ~104 MB y no se
/// empaquetan mientras el mundo abierto no exista en iOS. Un 404 limpio es mejor que el `file://`
/// de antes, que en iOS fallaba de formas raras y silenciosas.
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
