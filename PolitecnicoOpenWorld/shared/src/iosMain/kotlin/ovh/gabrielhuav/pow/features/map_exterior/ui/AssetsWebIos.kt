package ovh.gabrielhuav.pow.features.map_exterior.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.ObjCSignatureOverride
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLResponse
import platform.Foundation.create
import platform.WebKit.WKURLSchemeHandlerProtocol
import platform.WebKit.WKURLSchemeTaskProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

/**
 * 🖼️🍏 LOS ASSETS DEL MUNDO DENTRO DEL `WKWebView`.
 *
 * ## Por qué existe
 *
 * El HTML del mapa es **el mismo que Android**, y allí las imágenes cuelgan de
 * `file:///android_asset/`, un esquema que en iOS **no existe**. En vez de bifurcar el HTML se
 * parametrizó el prefijo (`buildHtml(assetBaseUrl:)`), y esta clase resuelve el que usa iOS:
 * `pow-asset:///SPRITES/…` → el archivo dentro del bundle.
 *
 * ⚠️ **Esto ya existía en Swift** (`iosApp/POW/MapaWeb.swift`) para la vista de prueba, pero
 * `MapaMundoIos` construye su propia `WKWebViewConfiguration` **desde Kotlin** y nunca registró
 * ningún manejador: el HTML pedía las imágenes y `WKWebView` las descartaba sin decir nada. Por eso
 * el mapa salía sin un solo sprite. Está en Kotlin y no en Swift porque quien monta esa
 * configuración es Kotlin; duplicarlo en Swift obligaría a exportar la config al otro lado.
 *
 * ⚠️ **El esquema NO puede ser `file`, `http` ni `https`**: WebKit se los reserva y
 * `setURLSchemeHandler` lanza una excepción si se intenta registrar uno de ellos.
 *
 * ⚠️ **Un 404 tiene que ser un 404 de verdad.** Si la tarea no se completa, el `<img>` se queda
 * colgado para siempre y Leaflet no vuelve a intentarlo. Por eso el camino de "no está" también
 * llama a `didReceiveResponse` + `didFinish`, con un `NSHTTPURLResponse` de 404: así el navegador
 * pinta el icono de imagen rota, que es visible y depurable, en vez de un hueco silencioso.
 */
@OptIn(ExperimentalForeignApi::class)
class AssetsWebIos : NSObject(), WKURLSchemeHandlerProtocol {

    // ⚠️ `@ObjCSignatureOverride` NO es opcional: en Objective-C estos dos métodos son selectores
    // distintos (`webView:startURLSchemeTask:` y `webView:stopURLSchemeTask:`), pero en Kotlin los
    // dos quedan como `webView(WKWebView, WKURLSchemeTaskProtocol)` y el compilador corta con
    // *"Conflicting overloads"*. La anotación es la forma de decirle que el desempate lo pone el
    // selector, no la firma de Kotlin.
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, startURLSchemeTask: WKURLSchemeTaskProtocol) {
        val url = startURLSchemeTask.request.URL
        // 'pow-asset:///SPRITES/ICONS/foo.svg' -> 'SPRITES/ICONS/foo.svg'
        val ruta = (url?.path ?: "").trimStart('/')

        // 🎨 `__tinte/<rrggbb>/<ruta>` = el mismo asset, con la carrocería repintada. Ver
        // `TintadoWebIos`: se hace aquí y no con un `filter` de CSS porque el repintado es
        // selectivo (respeta luces, rines y faros) y un filtro pinta el sprite entero.
        val bytes = when {
            ruta.isEmpty() -> null
            ruta.startsWith(TintadoWebIos.PREFIJO) -> TintadoWebIos.resolver(ruta)
            // 🧍 `__npc/…` = un peatón armado (cuerpo repintado + pelo). Ver `PersonajeWebIos`.
            ruta.startsWith(PersonajeWebIos.PREFIJO) -> PersonajeWebIos.resolver(ruta)
            else -> runCatching { PowAssets.bytes(ruta) }.getOrNull()
        }

        if (bytes == null) {
            val resp = NSHTTPURLResponse(
                uRL = url!!,
                statusCode = 404,
                HTTPVersion = "HTTP/1.1",
                headerFields = null,
            )
            startURLSchemeTask.didReceiveResponse(resp)
            startURLSchemeTask.didFinish()
            return
        }

        val datos = bytes.aNSData()
        val resp = NSURLResponse(
            uRL = url!!,
            // El repintado SIEMPRE sale en PNG, aunque el asset de origen sea WebP.
            MIMEType = if (esGenerado(ruta)) "image/png" else mimeDe(ruta),
            expectedContentLength = bytes.size.toLong(),
            textEncodingName = null,
        )
        startURLSchemeTask.didReceiveResponse(resp)
        startURLSchemeTask.didReceiveData(datos)
        startURLSchemeTask.didFinish()
    }

    /** Lo que generamos al vuelo SIEMPRE sale en PNG, aunque el asset de origen sea WebP. */
    private fun esGenerado(ruta: String) =
        ruta.startsWith(TintadoWebIos.PREFIJO) || ruta.startsWith(PersonajeWebIos.PREFIJO)

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, stopURLSchemeTask: WKURLSchemeTaskProtocol) {
        // Nada que cancelar: los assets se resuelven de forma síncrona desde el bundle.
    }
}

/** Prefijo que se le pasa a `buildHtml`. El host va vacío: `pow-asset:///SPRITES/…`. */
const val ESQUEMA_ASSETS_POW: String = "pow-asset"

/** Lo mismo, ya montado como URL base. */
const val PREFIJO_ASSETS_POW: String = "$ESQUEMA_ASSETS_POW:///"

/**
 * ⚠️ **Sin un MIME correcto WebKit no pinta los SVG**: los trata como descarga y el `<img>` queda
 * en blanco, sin error en ninguna consola.
 */
private fun mimeDe(ruta: String): String = when (ruta.substringAfterLast('.', "").lowercase()) {
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    else -> "application/octet-stream"
}

/**
 * ⚠️ La guarda de vacío NO es decorativa: `addressOf(0)` sobre un array de tamaño 0 no es válido
 * (mismo motivo que en `PowAssets.ios.kt`).
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.aNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { fijado ->
        NSData.create(bytes = fijado.addressOf(0), length = size.toULong())
    }
}
