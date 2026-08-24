package ovh.gabrielhuav.pow.features.map_exterior.ui

import ovh.gabrielhuav.pow.features.map_exterior.ui.components.tintarCarroceria

/**
 * 🎨🍏 EL COCHE PINTADO, PARA EL `WKWebView`.
 *
 * ## Por qué no basta con CSS
 *
 * Se podría teñir la imagen en el HTML con un `filter: hue-rotate(...)`, pero eso pinta el sprite
 * ENTERO: las luces traseras y los intermitentes dejarían de ser rojos y ámbar, y los rines y los
 * faros cogerían el color de la carrocería. El repintado de verdad es selectivo y va píxel a píxel
 * — el mismo `tintarCarroceria` de `commonMain` que usa Android, para que un coche rojo sea el
 * mismo rojo en las dos plataformas.
 *
 * ## Por qué se sirve como URL y no como base64
 *
 * En Android el sprite tintado viaja al WebView como un `data:` en base64 dentro de una llamada
 * JS, porque allí no hay forma de servir archivos. En iOS **sí la hay** (`AssetsWebIos`), así que
 * el mapa pide `pow-asset:///__tinte/ff3b30/SPRITES/VEHICLES/…webp` y esto lo resuelve. La
 * diferencia no es estética: son ~48 frames por modelo y por color, y meterlos en base64 por
 * `evaluateJavaScript` serían cientos de kilobytes de texto por coche.
 *
 * ⚠️ **El PNG se genera una vez por (sprite, color) y se guarda**, porque el mapa vuelve a pedir la
 * misma URL en cada giro del coche. Sin la caché, un NPC dando una vuelta redonda haría 48
 * decodificaciones + 48 codificaciones PNG por segundo.
 */
internal object TintadoWebIos {

    /** Prefijo que marca "sírveme este asset, pero repintado". */
    const val PREFIJO: String = "__tinte/"

    private val cache = mutableMapOf<String, ByteArray>()

    /**
     * Resuelve una ruta `__tinte/<rrggbb>/<ruta del asset>`.
     *
     * Devuelve el PNG ya repintado, o `null` si la ruta no tiene esa forma o el asset no existe —
     * y entonces `AssetsWebIos` responde su 404 de siempre.
     */
    fun resolver(ruta: String): ByteArray? {
        if (!ruta.startsWith(PREFIJO)) return null
        cache[ruta]?.let { return it }

        val resto = ruta.removePrefix(PREFIJO)
        val corte = resto.indexOf('/')
        if (corte <= 0) return null
        // El color viaja en hexadecimal SIN alfa: es lo único que necesita `tintarCarroceria` y
        // así la URL se lee de un vistazo cuando algo sale del color que no era.
        val color = resto.substring(0, corte).toIntOrNull(16) ?: return null
        val rutaAsset = resto.substring(corte + 1)

        val png = runCatching { pintar(rutaAsset, color) }.getOrNull() ?: return null
        cache[ruta] = png
        return png
    }

    private fun pintar(rutaAsset: String, colorSinAlfa: Int): ByteArray {
        val lienzo = ImagenWebIos.pixeles(rutaAsset)
        tintarCarroceria(lienzo.pixeles, colorSinAlfa or ALFA_OPACO)
        return ImagenWebIos.aPng(lienzo.pixeles, lienzo.ancho, lienzo.alto)
    }
}

/** El color de la URL viene sin alfa; el repintado espera un ARGB completo. */
private const val ALFA_OPACO = 0xFF shl 24
