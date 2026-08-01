package ovh.gabrielhuav.pow.features.map_exterior.ui

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import platform.WebKit.WKWebView

/**
 * 🌉🍏 EL PUENTE KOTLIN → JAVASCRIPT DEL MAPA, EN iOS.
 *
 * ## Qué es
 *
 * El equivalente iOS de lo que Android hace en `WorldMapScreenWeb.kt`: empujar el estado del juego
 * al mapa Leaflet llamando a sus funciones JS.
 *
 * ```
 * Android:  webView.evaluateJavascript("updatePlayerMarker(...)", null)
 * iOS:      webView.evaluateJavaScript("updatePlayerMarker(...)", null)
 * ```
 *
 * ⚠️ **Este puente es de UNA sola dirección: Kotlin → JS.** La vuelta (JS → Kotlin, que en Android
 * es `@JavascriptInterface`) necesita `WKScriptMessageHandler`, y **todavía no está**. Por eso el
 * mapa aún no avisa de toques ni de que el jugador arrastró la vista.
 *
 * ## Por qué cada llamada lleva `if (typeof f === 'function')`
 *
 * El HTML puede no haber terminado de cargar cuando llega la primera actualización. Sin la guarda,
 * el JS lanza `ReferenceError`, **y en `WKWebView` ese error no se ve en ninguna parte**: no hay
 * excepción en Kotlin, no hay log. Simplemente el mapa se queda quieto y nadie sabe por qué.
 * Android usa exactamente la misma guarda, por lo mismo.
 */
class PuenteMapaIos(private val webView: WKWebView) {

    /** Mueve (o crea) el punto verde del jugador. */
    fun moverJugador(punto: GeoPoint) {
        // El tercer parámetro es `isInFreeNavigation`: en `false` el JS BORRA el marcador.
        // Aquí siempre va `true` porque la vista previa siempre quiere enseñar al jugador.
        llamar("updatePlayerMarker(${punto.latitude}, ${punto.longitude}, true)")
    }

    /** Recentra la vista del mapa. Es lo que hace que la cámara siga al jugador. */
    fun centrarEn(punto: GeoPoint, zoom: Int) {
        llamar("updateMapView(${punto.latitude}, ${punto.longitude}, $zoom)")
    }

    /** Mueve el foco de luz de la niebla de guerra con el jugador. */
    fun moverNiebla(punto: GeoPoint) {
        llamar("setPlayerFog(${punto.latitude}, ${punto.longitude})")
    }

    /** Enciende o apaga la niebla de guerra. */
    fun niebla(encendida: Boolean) {
        llamar("setFogEnabled($encendida)")
    }

    /**
     * Ejecuta [js] en el mapa, con la guarda de "existe la función" delante.
     *
     * ⚠️ [js] tiene que ser **una llamada**, `nombre(args)`. Se parte por el primer paréntesis para
     * sacar el nombre y comprobarlo antes.
     */
    private fun llamar(js: String) {
        val nombre = js.substringBefore('(')
        webView.evaluateJavaScript(
            "if (typeof $nombre === 'function') $js;",
            completionHandler = null,
        )
    }
}
