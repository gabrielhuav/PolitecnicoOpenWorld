package ovh.gabrielhuav.pow.features.map_exterior.ui

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
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
 * es `@JavascriptInterface`) vive desde el 2026-08-17 en **[PuenteJsIos]**, con
 * `WKScriptMessageHandler`. Los dos se montan juntos en `MapaMundoIos`.
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
     * Pinta los NPCs que simula [ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager].
     *
     * ⚠️ **El `type` que se manda NO es el `NpcType` del juego, y elegir mal no da ningún error.**
     * El JS de `updateNpcs` tiene tres ramas:
     *  - `"CAR"` / `"MODULAR"` → si hay base64 en `imgCache` pinta la imagen; **si NO lo hay, pinta
     *    un EMOJI** (🚗 / 🧍). Es el "FIX NPC invisible" del propio HTML.
     *  - cualquier otra cosa → `<img src="…/SPRITES/ICONS/<drawable>.svg">`, un ARCHIVO de assets.
     *
     * En iOS los sprites del mundo **no están en el bundle**, así que la tercera rama pinta el
     * icono de imagen rota (un "?" azul) — probado en el simulador el 2026-08-15, y por eso aquí
     * se manda siempre `CAR`/`MODULAR` **sin `imageKey`**: cae en el respaldo de emoji, que se ve
     * bien y no necesita ni un archivo. El día que los sprites viajen al bundle, se añade
     * `imageKey` y la misma rama pasa a pintar el sprite sin tocar nada más.
     *
     * ⚠️ **Y el JS no dibuja NADA por debajo de zoom 16.5** (`isZoomedIn` en `updateNpcs`): con el
     * mapa a 16 se manda todo correctamente y no aparece un solo NPC, sin ningún error. Por eso el
     * mundo de iOS va a 17.
     */
    fun actualizarNpcs(npcs: List<Npc>) {
        val datos = npcs.joinToString(",", prefix = "[", postfix = "]") { npc ->
            val tipo = if (npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR) "CAR" else "MODULAR"
            """{"id":"${npc.id}","lat":${npc.location.latitude},"lng":${npc.location.longitude},""" +
                """"type":"$tipo","health":${npc.health},"isDying":${npc.isDying}}"""
        }
        llamar("updateNpcs($datos)")
    }

    /**
     * Enciende el modo "el próximo toque coloca el destino".
     *
     * ⚠️ **Sin esto, el JS NO avisa de los toques**: `notifyMapClick` está detrás de
     * `if (isPlacingDestinationMarker …)` en el HTML. Es el mismo contrato que en Android.
     * El JS lo apaga solo tras el toque, así que hay que volver a encenderlo para el siguiente.
     */
    fun modoColocarDestino(activo: Boolean) {
        llamar("updateDestinationPlacingMode($activo)")
    }

    /** Pinta el marcador de destino donde el jugador tocó. */
    fun marcarDestino(punto: GeoPoint) {
        llamar("updateDestinationMarker(${punto.latitude}, ${punto.longitude})")
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
