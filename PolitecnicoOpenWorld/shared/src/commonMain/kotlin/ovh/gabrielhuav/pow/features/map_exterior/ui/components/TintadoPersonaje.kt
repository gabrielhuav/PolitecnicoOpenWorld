package ovh.gabrielhuav.pow.features.map_exterior.ui.components

/**
 * 🧍 EL PERSONAJE MODULAR, IGUAL EN ANDROID Y EN iOS.
 *
 * Un peatón del mundo no es un sprite: es un **cuerpo en escala de grises** al que se le repinta la
 * playera y el pantalón, más un **pelo** encima que se repinta aparte. Así, con ocho fotogramas y
 * cinco peinados, salen todos los NPCs del campus sin dibujar uno por uno.
 *
 * Igual que [tintarCarroceria], esto es matemática de píxeles pura y por eso vive en `commonMain`:
 * si se copiara, los peatones de iOS acabarían vestidos de otro color que los de Android sin que
 * fallara nada.
 *
 * ## El truco del repintado: qué es ropa y qué es piel
 *
 * No se puede pintar "todo lo claro", porque la cara y las manos también son claras. El filtro que
 * usa el juego es **la ausencia de color**: la piel tiene un tono cálido (el rojo domina sobre el
 * azul), así que un píxel con [DIFERENCIA_QUE_ES_PIEL] o más de diferencia entre su canal máximo y
 * el mínimo **no se toca**. Lo que queda —los grises— se reparte por brillo: lo claro es la prenda
 * de arriba, el gris medio es el pantalón, y lo muy oscuro son los contornos, que se quedan negros
 * para que la silueta siga leyéndose.
 */

/**
 * Repinta **en el sitio** la ropa de [pixeles] (ARGB sin premultiplicar).
 *
 * @param colorPrenda playera (o el pelo, cuando se tiñe el sprite de pelo).
 * @param colorPantalon pantalón. `null` = solo hay una prenda, que es el caso del pelo.
 */
fun tintarPersonaje(pixeles: IntArray, colorPrenda: Int, colorPantalon: Int?) {
    for (i in pixeles.indices) {
        val p = pixeles[i]
        // Casi transparente = borde suavizado del sprite; pintarlo deja un halo de color.
        if ((p ushr 24) < ALFA_MINIMO) continue

        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF

        // 1. Lo que tiene color propio es PIEL (o un detalle ya pintado): no se toca.
        if (maxOf(r, g, b) - minOf(r, g, b) > DIFERENCIA_QUE_ES_PIEL) continue

        // 2. El gris se reparte por brillo entre las dos prendas.
        val brillo = (r + g + b) / 3
        pixeles[i] = when {
            brillo > BRILLO_DE_LA_PRENDA_CLARA -> multiplicar(p, colorPrenda)
            colorPantalon != null && brillo in BRILLO_PANTALON_MIN..BRILLO_PANTALON_MAX ->
                multiplicar(p, colorPantalon)
            // Por debajo son los contornos: se quedan como están.
            else -> p
        }
    }
}

/**
 * Pega [encima] sobre [base] (los dos ARGB sin premultiplicar y **del mismo tamaño**), como haría
 * un `drawBitmap` en el origen. Es lo que junta el pelo con el cuerpo.
 *
 * ⚠️ El alfa se compone de verdad (`source-over`) en vez de copiar el píxel de arriba: los sprites
 * de pelo llevan borde semitransparente y una copia dura deja los pelos con dientes de sierra.
 */
fun componerEncima(base: IntArray, encima: IntArray) {
    require(base.size == encima.size) {
        "El sprite de encima mide ${encima.size} y la base ${base.size}: tienen que coincidir"
    }
    for (i in base.indices) {
        val arriba = encima[i]
        val alfaArriba = arriba ushr 24
        if (alfaArriba == 0) continue
        if (alfaArriba == 255) {
            base[i] = arriba
            continue
        }

        val abajo = base[i]
        val alfaAbajo = abajo ushr 24
        // outA = srcA + dstA * (1 - srcA), en enteros sobre 255.
        val alfaFinal = alfaArriba + alfaAbajo * (255 - alfaArriba) / 255
        if (alfaFinal == 0) {
            base[i] = 0
            continue
        }

        base[i] = (alfaFinal shl 24) or
            (canalCompuesto(arriba, abajo, alfaArriba, alfaAbajo, alfaFinal, 16) shl 16) or
            (canalCompuesto(arriba, abajo, alfaArriba, alfaAbajo, alfaFinal, 8) shl 8) or
            canalCompuesto(arriba, abajo, alfaArriba, alfaAbajo, alfaFinal, 0)
    }
}

/** Un canal de `source-over`, devuelto ya sin premultiplicar. */
private fun canalCompuesto(
    arriba: Int,
    abajo: Int,
    alfaArriba: Int,
    alfaAbajo: Int,
    alfaFinal: Int,
    desplazamiento: Int,
): Int {
    val cArriba = (arriba ushr desplazamiento) and 0xFF
    val cAbajo = (abajo ushr desplazamiento) and 0xFF
    val numerador = cArriba * alfaArriba + cAbajo * alfaAbajo * (255 - alfaArriba) / 255
    return (numerador / alfaFinal).coerceIn(0, 255)
}

/**
 * Multiplica el píxel por el color, que es lo que **conserva las sombras y los pliegues** de la
 * prenda: sobre un gris claro el color sale vivo y sobre uno oscuro sale apagado, en vez de quedar
 * un recorte plano del color.
 */
private fun multiplicar(pixel: Int, color: Int): Int {
    val a = pixel ushr 24
    val r = (((pixel ushr 16) and 0xFF) * ((color ushr 16) and 0xFF)) / 255
    val g = (((pixel ushr 8) and 0xFF) * ((color ushr 8) and 0xFF)) / 255
    val b = ((pixel and 0xFF) * (color and 0xFF)) / 255
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/** Por debajo de esto el píxel es el borde suavizado del sprite. */
private const val ALFA_MINIMO = 50

/** Diferencia entre canales a partir de la cual el píxel "tiene color" y por tanto es piel. */
private const val DIFERENCIA_QUE_ES_PIEL = 15

private const val BRILLO_DE_LA_PRENDA_CLARA = 160
private const val BRILLO_PANTALON_MIN = 45
private const val BRILLO_PANTALON_MAX = 130
