package ovh.gabrielhuav.pow.features.map_exterior.ui.components

/**
 * 🎨 EL REPINTADO DE CARROCERÍA, IGUAL EN ANDROID Y EN iOS.
 *
 * ## Por qué está aquí y no en cada plataforma
 *
 * Los sprites de coche son una base **blanca** y el color del NPC se aplica píxel a píxel. Ese
 * cálculo no toca ni una API de plataforma: entra un `IntArray` de ARGB y sale el mismo array
 * repintado. Vivía dentro de `VehicleSpriteManager` (Android) mezclado con `Bitmap`, y al llevar el
 * mapa a iOS habría acabado copiado — con dos coches del mismo color distinto en cada plataforma y
 * nadie mirando. Ahora hay **una sola copia** y la cubren tests de `commonTest`.
 *
 * ⚠️ **No es un `ColorFilter` ni un `hue-rotate`, y por eso no se puede hacer en CSS.** El filtro
 * de color pinta TODO el sprite; esto respeta tres cosas a propósito:
 *
 * 1. **Lo que ya tiene color se queda como está** (saturación > 0,15): las luces traseras rojas y
 *    los intermitentes ámbar no deben volverse del color de la carrocería.
 * 2. **Solo se repinta la franja de luminosidad de la chapa** (80–245). Por debajo están las
 *    sombras y los neumáticos, por encima los faros y los brillos especulares; pintarlos deja un
 *    coche de plástico.
 * 3. **Los bordes se interpolan** ([factorDeChapa]) en vez de cortar en seco, que es lo que evita
 *    el borde dentado entre carrocería y rin.
 */

/**
 * Repinta **en el sitio** la carrocería de [pixeles] (ARGB, sin premultiplicar) con [colorInt].
 *
 * Los píxeles totalmente transparentes se saltan: no aportan nada y el sprite es casi todo hueco.
 */
fun tintarCarroceria(pixeles: IntArray, colorInt: Int) {
    val objetivoR = (colorInt ushr 16) and 0xFF
    val objetivoG = (colorInt ushr 8) and 0xFF
    val objetivoB = colorInt and 0xFF

    for (i in pixeles.indices) {
        val p = pixeles[i]
        val a = p ushr 24
        if (a == 0) continue

        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF

        // 1. Lo que YA tiene color propio (luces, intermitentes) no se toca.
        val maximo = maxOf(r, g, b)
        val minimo = minOf(r, g, b)
        val saturacion = if (maximo == 0) 0f else (maximo - minimo) / maximo.toFloat()
        if (saturacion > SATURACION_QUE_SE_RESPETA) continue

        // 2. Luminosidad perceptual (los mismos pesos de siempre).
        val luz = LUZ_R * r + LUZ_G * g + LUZ_B * b

        // 3. Cuánto de este píxel es chapa repintable.
        val factor = factorDeChapa(luz)
        if (factor <= 0f) continue

        // El color se aplica CON la luminosidad original, para que el sprite conserve su
        // volumen (sombras y brillos) en vez de quedar plano.
        val multiplicador = luz / LUZ_DE_REFERENCIA
        val mezclaR = (objetivoR * multiplicador).toInt().coerceIn(0, 255)
        val mezclaG = (objetivoG * multiplicador).toInt().coerceIn(0, 255)
        val mezclaB = (objetivoB * multiplicador).toInt().coerceIn(0, 255)

        val finalR = (r + factor * (mezclaR - r)).toInt()
        val finalG = (g + factor * (mezclaG - g)).toInt()
        val finalB = (b + factor * (mezclaB - b)).toInt()

        pixeles[i] = (a shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
    }
}

/**
 * Cuánto se repinta un píxel según su luminosidad: 0 = nada, 1 = carrocería plena.
 *
 * Las dos rampas de los extremos son las que evitan el borde dentado contra el rin (abajo) y
 * contra el faro (arriba).
 */
private fun factorDeChapa(luz: Float): Float = when {
    luz < LUZ_MINIMA || luz > LUZ_MAXIMA -> 0f
    luz < LUZ_FIN_RAMPA_RINES -> (luz - LUZ_MINIMA) / (LUZ_FIN_RAMPA_RINES - LUZ_MINIMA)
    luz > LUZ_INICIO_RAMPA_FAROS -> (LUZ_MAXIMA - luz) / (LUZ_MAXIMA - LUZ_INICIO_RAMPA_FAROS)
    else -> 1f
}

/** Por encima de esto el píxel ya tiene color propio y se respeta. */
private const val SATURACION_QUE_SE_RESPETA = 0.15f

// Pesos de luminosidad perceptual (Rec. 601), los mismos que usaba Android.
private const val LUZ_R = 0.299f
private const val LUZ_G = 0.587f
private const val LUZ_B = 0.114f

private const val LUZ_MINIMA = 80f
private const val LUZ_FIN_RAMPA_RINES = 130f
private const val LUZ_INICIO_RAMPA_FAROS = 235f
private const val LUZ_MAXIMA = 245f

/**
 * Luminosidad que se considera "chapa a plena luz". El color objetivo se multiplica por
 * `luz / esto`, así que a 165 el color sale tal cual y por encima/debajo aclara u oscurece.
 */
private const val LUZ_DE_REFERENCIA = 165f
