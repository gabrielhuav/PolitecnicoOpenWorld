package ovh.gabrielhuav.pow.features.map_exterior.ui

import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.darwin.NSObject

/**
 * 🌉🍏 LA VUELTA DEL PUENTE: JAVASCRIPT → KOTLIN, en iOS.
 *
 * `PuenteMapaIos` empuja estado al mapa (Kotlin → JS). Esto es la dirección contraria, la que hacía
 * falta para que el mapa avise de toques, zoom y arrastres. En Android eso es `MapJsBridge` con
 * `@JavascriptInterface`; en iOS es `WKScriptMessageHandler`, que es esta clase.
 *
 * ## El truco: NO se toca el HTML compartido
 *
 * El HTML del mapa es el MISMO para las dos plataformas y llama a `window.Android.notifyX(...)`.
 * En iOS `window.Android` no existe… hasta que se lo inyectamos: [PUENTE_JS_SHIM] define ese objeto como
 * una fachada que reenvía a `window.webkit.messageHandlers`, que es el mecanismo de WebKit.
 *
 * ```
 * HTML compartido → window.Android.notifyMapClick(lat, lng)     (sin cambios)
  * PUENTE_JS_SHIM       → webkit.messageHandlers.pow.postMessage(...)  (solo en iOS)
 * esta clase      → alTocarMapa(lat, lng)
 * ```
 *
 * Así **no hay una rama `if (iOS)` en el HTML**, que es justo lo que este proyecto evita: una sola
 * copia del mapa para Android y iOS.
 *
 * ## Por qué el mensaje es un STRING y no un objeto
 *
 * `postMessage` acepta objetos JS, pero llegan como `NSDictionary` con `NSNumber` dentro, y
 * convertirlos en Kotlin/Native es donde aparecen las sorpresas de tipos. Un string con separadores
 * cruza el puente sin ambigüedad y se parsea en una línea. Es menos elegante y mucho más difícil de
 * romper — que es lo que interesa en una costura que solo se puede probar en el simulador.
 *
 * ⚠️ **El nombre del canal ([PUENTE_JS_CANAL]) tiene que ser el mismo aquí y en [PUENTE_JS_SHIM].** Si no
 * coinciden, `postMessage` lanza en el JS y **`WKWebView` se traga el error sin log** — el mismo
 * silencio que documenta `PuenteMapaIos`. No lo cambies en un solo sitio.
 */
class PuenteJsIos(
    /** Toque en el mapa. Solo llega si el modo "colocar destino" está encendido en el JS. */
    private val alTocarMapa: (latitud: Double, longitud: Double) -> Unit = { _, _ -> },
    /** Zoom nuevo tras un pinch. ⚠️ Por debajo de 16.5 el JS no dibuja NPCs (ver `PuenteMapaIos`). */
    private val alCambiarZoom: (zoom: Double) -> Unit = {},
    /** `true` al empezar a arrastrar la vista, `false` al soltar. */
    private val alArrastrarMapa: (arrastrando: Boolean) -> Unit = {},
) : NSObject(), WKScriptMessageHandlerProtocol {

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        // Si algún día el mensaje deja de ser un string, se ignora en vez de reventar: un puente
        // roto debe dejar el mapa quieto, no tirar la app.
        val texto = didReceiveScriptMessage.body as? String ?: return
        val partes = texto.split(PUENTE_JS_SEPARADOR)
        when (partes.firstOrNull()) {
            "mapClick" -> {
                val lat = partes.getOrNull(1)?.toDoubleOrNull()
                val lon = partes.getOrNull(2)?.toDoubleOrNull()
                if (lat != null && lon != null) alTocarMapa(lat, lon)
            }
            "zoom" -> partes.getOrNull(1)?.toDoubleOrNull()?.let(alCambiarZoom)
            "panStart" -> alArrastrarMapa(true)
            "panEnd" -> alArrastrarMapa(false)
            // `lmSel`/`lmMov` son del Modo Diseñador, que en iOS no existe todavía: llegan y se
            // ignoran a propósito, para que el HTML no tenga que saber en qué plataforma corre.
            else -> Unit
        }
    }

}

/**
 * Nombre del canal de `webkit.messageHandlers`. Debe coincidir con el de [PUENTE_JS_SHIM].
 *
 * ⚠️ **Va a nivel de archivo y no en un `companion object` a propósito**: `PuenteJsIos` hereda de
 * `NSObject`, y Kotlin/Native corta con *"Fields are not supported for Companion of subclass of
 * ObjC type"*. No lo muevas dentro de la clase.
 */
const val PUENTE_JS_CANAL: String = "pow"

private const val PUENTE_JS_SEPARADOR: Char = '|'

/**
 * El `window.Android` de iOS. Se inyecta **al principio del documento** (`AtDocumentStart`): si
 * entrara después, el mapa ya habría intentado usarlo.
 *
 * ⚠️ **Sin `$` en este JS**: vive dentro de un string de Kotlin y la interpolación se lo comería
 * (09 §6 lo documenta para el HTML del mapa). Por eso el nombre del canal se concatena con `+`
 * desde [PUENTE_JS_CANAL] en vez de interpolarse dentro del bloque.
 */
val PUENTE_JS_SHIM: String = """
    (function () {
        function pow(mensaje) {
            try {
                window.webkit.messageHandlers['CANAL'].postMessage(mensaje);
            } catch (e) {
                // El canal no está montado. Mejor no hacer nada que romper el mapa entero.
            }
        }
        window.Android = {
            notifyMapClick: function (lat, lng) { pow('mapClick|' + lat + '|' + lng); },
            notifyCenterForWaypoint: function (lat, lng) { pow('mapClick|' + lat + '|' + lng); },
            notifyMapZoom: function (z) { pow('zoom|' + z); },
            notifyMapPanStart: function () { pow('panStart'); },
            notifyMapPanEnd: function () { pow('panEnd'); },
            notifyLandmarkSelected: function (id) { pow('lmSel|' + id); },
            notifyLandmarkMoved: function (id, lat, lng) { pow('lmMov|' + id + '|' + lat + '|' + lng); }
        };
    })();
""".trimIndent().replace("CANAL", PUENTE_JS_CANAL)
