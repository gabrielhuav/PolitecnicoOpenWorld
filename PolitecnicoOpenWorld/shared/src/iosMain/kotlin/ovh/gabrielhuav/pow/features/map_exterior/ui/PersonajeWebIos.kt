package ovh.gabrielhuav.pow.features.map_exterior.ui

import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.componerEncima
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.tintarPersonaje
import ovh.gabrielhuav.pow.platform.assets.PowAssets

/**
 * 🧍🍏 EL PEATÓN ARMADO, PARA EL `WKWebView`.
 *
 * Los NPCs del mundo no tienen un sprite propio: se **arman** con un cuerpo en escala de grises
 * (repintado con el color de la playera y del pantalón) y un pelo encima (repintado aparte). Es lo
 * mismo que hace `CharacterSpriteManager` en Android, y comparten el cálculo
 * (`TintadoPersonaje.kt`, en `commonMain`); lo que cambia es a dónde va el resultado — allí a un
 * `Drawable`, aquí a un PNG que sirve [AssetsWebIos].
 *
 * ## La URL
 *
 * ```
 * pow-asset:///__npc/<carpeta>/<prefijo>/<fotograma>/<peloId>/<pelo>/<playera>/<pantalon>
 * ```
 *
 * Los colores van en `rrggbb`. Se lee de un tirón cuando un NPC sale del color que no era, y como
 * es una URL, WebKit la cachea sola y el PNG se genera una vez por combinación.
 *
 * ⚠️ **Cuesta poco en disco**: el spawner compartido (`NpcAiManager.NPC_OUTFITS`) usa un solo
 * cuerpo (`npc_walk_1`, 356 KB con sus 8 fotogramas) y cinco peinados (68 KB). No hacen falta los
 * 70 MB de `SPRITES/NPC`, ni recortar ningún atlas: **los fotogramas ya son ficheros sueltos**.
 */
internal object PersonajeWebIos {

    /** Prefijo que marca "ármame este peatón". */
    const val PREFIJO: String = "__npc/"

    /** Cuántos fotogramas de caminar busca como mucho, igual que Android. */
    private const val FOTOGRAMAS_MAX = 8

    private val cachePng = mutableMapOf<String, ByteArray>()
    private val cacheFotogramas = mutableMapOf<String, Int>()

    /** Resuelve una ruta `__npc/…`. `null` = no tiene esa forma o falta el asset (→ 404). */
    fun resolver(ruta: String): ByteArray? {
        if (!ruta.startsWith(PREFIJO)) return null
        cachePng[ruta]?.let { return it }

        val partes = ruta.removePrefix(PREFIJO).split('/')
        if (partes.size != PARTES_ESPERADAS) return null
        val carpeta = partes[0]
        val prefijo = partes[1]
        val fotograma = partes[2].toIntOrNull() ?: return null
        val peloId = partes[3].toIntOrNull() ?: return null
        val colorPelo = partes[4].toIntOrNull(16) ?: return null
        val colorPlayera = partes[5].toIntOrNull(16) ?: return null
        val colorPantalon = partes[6].toIntOrNull(16) ?: return null

        val png = runCatching {
            armar(carpeta, prefijo, fotograma, peloId, colorPelo, colorPlayera, colorPantalon)
        }.getOrNull() ?: return null
        cachePng[ruta] = png
        return png
    }

    private fun armar(
        carpeta: String,
        prefijo: String,
        fotograma: Int,
        peloId: Int,
        colorPelo: Int,
        colorPlayera: Int,
        colorPantalon: Int,
    ): ByteArray {
        // Los ficheros van de 1 a N; el índice que manda el puente va de 0 a N-1.
        val cuerpo = ImagenWebIos.pixeles(rutaCuerpo(carpeta, prefijo, fotograma + 1), REDUCCION)
        tintarPersonaje(cuerpo.pixeles, colorPlayera or ALFA_OPACO, colorPantalon or ALFA_OPACO)

        // ⚠️ El pelo es OPCIONAL a propósito: si falta el fichero sale un NPC calvo, que es mucho
        // mejor que un 404 y un hueco en el mapa. Android hace lo mismo (`?.let`).
        val rutaPelo = "SPRITES/NPC/hair/hair_$peloId.webp"
        if (PowAssets.existe(rutaPelo)) {
            val pelo = ImagenWebIos.pixeles(rutaPelo, REDUCCION)
            if (pelo.pixeles.size == cuerpo.pixeles.size) {
                tintarPersonaje(pelo.pixeles, colorPelo or ALFA_OPACO, null)
                componerEncima(cuerpo.pixeles, pelo.pixeles)
            }
        }

        return ImagenWebIos.aPng(cuerpo.pixeles, cuerpo.ancho, cuerpo.alto)
    }

    private fun rutaCuerpo(carpeta: String, prefijo: String, numero: Int) =
        "SPRITES/NPC/$carpeta/$prefijo$numero.webp"

    /**
     * Cuántos fotogramas de caminar hay de verdad. Se cuenta UNA vez por cuerpo mirando el bundle,
     * igual que Android, que carga hasta que falla la apertura.
     */
    fun fotogramas(carpeta: String, prefijo: String): Int =
        cacheFotogramas.getOrPut("$carpeta/$prefijo") {
            var n = 0
            while (n < FOTOGRAMAS_MAX && PowAssets.existe(rutaCuerpo(carpeta, prefijo, n + 1))) n++
            n
        }

    /**
     * La URL del peatón para [config] en el [fotograma] dado.
     *
     * Es también la CLAVE del `imgCache`: dos NPCs con la misma ropa y el mismo fotograma comparten
     * imagen, que es justo lo que hace que 39 peatones no cuesten 39 PNG.
     */
    fun url(config: CharacterVisualConfig, fotograma: Int): String =
        PREFIJO + config.bodyFolder + "/" + config.bodyPrefix + "/" + fotograma + "/" +
            config.hairId + "/" + hex(config.hairColor.value) + "/" +
            hex(config.shirtColor.value) + "/" + hex(config.pantsColor.value)

    /**
     * `Color.value` es un `ULong` empaquetado de Compose, no un ARGB.
     *
     * ⚠️ Los 32 bits de arriba son los canales en punto flotante a medias: hay que quedarse con
     * los 8 bits de cada canal, que es lo que hace `toArgb()` en Android. Aquí se hace a mano
     * porque `toArgb()` sí existe en común, pero devuelve el alfa incluido y en la URL solo cabe
     * `rrggbb`.
     */
    private fun hex(valor: ULong): String {
        val argb = androidx.compose.ui.graphics.Color(valor).let {
            (it.red * 255).toInt().shl(16) or (it.green * 255).toInt().shl(8) or (it.blue * 255).toInt()
        }
        return argb.toString(16).padStart(6, '0')
    }

    private const val PARTES_ESPERADAS = 7

    /**
     * Los sprites son de 512×512 y en el mapa se ven a ~12 px, así que se decodifican a 1/4
     * (128×128): sigue siendo diez veces lo que se ve, y cuesta 16 veces menos generar el PNG.
     *
     * ⚠️ **El mismo divisor para el cuerpo y para el pelo.** Si se redujeran distinto dejarían de
     * encajar y `componerEncima` cortaría con su `require`.
     */
    private const val REDUCCION = 4
}

private const val ALFA_OPACO = 0xFF shl 24
