package ovh.gabrielhuav.pow.features.map_exterior.ui

import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

/**
 * ⏳🍏 AVISA CUANDO EL HTML DEL MAPA YA EXISTE.
 *
 * ## El fallo que arregla (medido en el simulador, 2026-08-20)
 *
 * `PuenteMapaIos` mete cada llamada detrás de `if (typeof f === 'function')` porque el HTML puede
 * no haber cargado todavía y **en `WKWebView` un `ReferenceError` no se ve en ninguna parte**. Esa
 * guarda es correcta, pero convierte "todavía no" en "nunca" para las llamadas que **solo se hacen
 * una vez**.
 *
 * El `DisposableEffect` del mapa empujaba el estado inicial justo después de `loadHTMLString`, o
 * sea con la página aún sin cargar. Se perdían las dos:
 *
 * | Llamada perdida | Se notaba en… | Se recuperaba sola |
 * |---|---|---|
 * | `updatePlayerMarker` | no había punto verde **hasta que dabas el primer paso** | sí, cada paso lo reintenta |
 * | `updateDestinationPlacingMode(true)` | **el toque en el mapa no hacía nada, nunca** | ❌ no |
 *
 * La segunda era un pescadilla-que-se-muerde-la-cola: el JS solo avisa del toque si el modo está
 * encendido, lo apaga tras cada toque, y el único sitio que lo volvía a encender era el manejador
 * del toque — que no llegaba a correr. Así que la vuelta del puente (`PuenteJsIos`) **nunca llegó
 * a armarse**, y parecía que el `WKScriptMessageHandler` estaba roto cuando no lo estaba.
 *
 * ## Por qué un delegado y no reintentos
 *
 * Reintentar "por si acaso" durante unos segundos también funcionaría, pero deja el arranque
 * lleno de llamadas a ciegas y no dice cuándo parar. `didFinishNavigation` es el momento exacto en
 * que las funciones del HTML existen, que es justo lo que hay que saber.
 *
 * ⚠️ **`navigationDelegate` es una referencia DÉBIL.** Si esto se crea al vuelo dentro del
 * `factory` del `UIKitView` y nadie más lo guarda, se libera enseguida y el callback no llega
 * nunca — con el mismo silencio que el error que vino a arreglar. En `MapaMundoIos` se mantiene
 * vivo con un `remember`.
 */
class CargaMapaIos(
    /** Se llama en el hilo principal cuando el documento terminó de cargar. */
    private val alCargar: () -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        alCargar()
    }
}
