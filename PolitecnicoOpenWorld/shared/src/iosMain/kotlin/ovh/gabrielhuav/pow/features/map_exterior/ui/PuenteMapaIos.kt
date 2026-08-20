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
    fun actualizarNpcs(npcs: List<Npc>, usarEmoji: Boolean = true) {
        val datos = npcs.joinToString(",", prefix = "[", postfix = "]") { npc ->
            val esCoche = npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR
            val comun = """"id":"${npc.id}","lat":${npc.location.latitude},""" +
                """"lng":${npc.location.longitude},"health":${npc.health},""" +
                """"isDying":${npc.isDying}"""
            when {
                // 🚗🧍 EMOJI: `CAR`/`MODULAR` SIN `imageKey` → el JS cae en su respaldo de emoji.
                usarEmoji -> """{$comun,"type":"${if (esCoche) "CAR" else "MODULAR"}"}"""

                // 🚗 COCHE REAL: el sprite del ángulo que toca, servido del bundle.
                esCoche -> {
                    val clave = claveDeCoche(npc)
                    registrarImagen(clave, PREFIJO_ASSETS_POW + rutaDeCoche(npc))
                    """{$comun,"type":"CAR","imageKey":"$clave","width":1,"height":1}"""
                }

                // 🧍 PERSONA REAL: la TERCERA rama del JS, que pinta
                // `SPRITES/ICONS/<drawable>.svg` como archivo. No pasa por `imgCache`.
                else -> """{$comun,"type":"${npc.type.name}","drawable":"${npc.type.drawableName}"}"""
            }
        }
        llamar("updateNpcs($datos)")
    }

    /**
     * Mete una URL en el `imgCache` del HTML, que es de donde `updateNpcs` saca el sprite.
     *
     * ⚠️ **Android guarda ahí un `data:` en base64 y aquí va una URL `pow-asset://`, y eso NO es
     * un atajo**: el JS solo hace `img.src = imgCache[clave]`, así que le da igual el formato. En
     * Android hay que componer el bitmap (rotarlo y **tintarlo** con el color del coche) y por eso
     * acaba en base64; en iOS no hay compositor de sprites todavía, así que se sirve el archivo
     * tal cual y **los coches salen blancos**: `carColor` se ignora. Es la diferencia visible
     * entre las dos plataformas hoy, y está aquí para que se vea sin bucear.
     *
     * Solo se manda una vez por clave: son ~48 frames por modelo y reenviarlos en cada tic de
     * 33 ms sería kilobytes de JS por segundo para nada.
     */
    private fun registrarImagen(clave: String, url: String) {
        if (!imagenesRegistradas.add(clave)) return
        // `imgCache` lo crea Android desde Kotlin; aquí hay que crearlo igual antes de escribir.
        webView.evaluateJavaScript(
            "if(!window.imgCache) window.imgCache={}; window.imgCache['$clave']='$url';",
            completionHandler = null,
        )
    }

    private val imagenesRegistradas = mutableSetOf<String>()

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
     * ⚠️ **La guarda hace que una llamada que llega ANTES de que cargue el HTML se pierda en
     * silencio.** Para lo que se manda en bucle (jugador, NPCs) da igual, se reintenta solo; para
     * lo que se manda UNA vez, no: ver [CargaMapaIos], que es quien decide cuándo es seguro.
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

/**
 * En qué frame de rotación cae el coche. **La fórmula es la de Android** (`WorldMapScreenWeb.kt`):
 * 48 frames = uno cada 7.5°.
 */
private fun frameDeCoche(npc: Npc): Int {
    val cuantos = if (npc.isPoliceSkin || npc.type == NpcType.POLICE_CAR) 48 else npc.carModel.frameCount
    var angulo = npc.rotationAngle % 360f
    if (angulo < 0f) angulo += 360f
    val paso = 360f / cuantos
    return ((angulo / paso).toInt()) % cuantos
}

/**
 * Clave del `imgCache`. Lleva el modelo y el frame porque **cada ángulo es un archivo distinto**;
 * NO lleva el color, a diferencia de Android, porque aquí no se tinta (ver `registrarImagen`).
 */
private fun claveDeCoche(npc: Npc): String =
    if (npc.isPoliceSkin || npc.type == NpcType.POLICE_CAR) "POLICE_${frameDeCoche(npc)}"
    else "${npc.carModel.name}_${frameDeCoche(npc)}"

/**
 * Ruta del sprite dentro de `assets/`.
 *
 * ⚠️ **La patrulla NO está en `SPRITES/VEHICLES`**, sino en `SPRITES/VEHICLES/POLICE_TOPDOWN`. En
 * Android eso lo resuelve `PoliceSpriteManager`; aquí es una rama del `when` y si se olvida, la
 * patrulla cae al 404 y sale el icono de imagen rota.
 */
private fun rutaDeCoche(npc: Npc): String {
    val n = frameDeCoche(npc)
    if (npc.isPoliceSkin || npc.type == NpcType.POLICE_CAR) {
        // ⚠️ La patrulla usa CUATRO dígitos (`…ALLD0000.webp`) y los civiles TRES
        // (`…All_000.webp`). No es un descuido de este archivo: es como están los assets, y así
        // lo hacen también `PoliceSpriteManager` y `VehicleSpriteManager` en Android.
        return "SPRITES/VEHICLES/POLICE_TOPDOWN/$PREFIJO_PATRULLA${n.toString().padStart(4, '0')}.webp"
    }
    return "SPRITES/VEHICLES/${npc.carModel.dirName}/" +
        "${npc.carModel.prefix}${n.toString().padStart(3, '0')}.webp"
}

private const val PREFIJO_PATRULLA = "POLICE_CLEAN_ALLD"
