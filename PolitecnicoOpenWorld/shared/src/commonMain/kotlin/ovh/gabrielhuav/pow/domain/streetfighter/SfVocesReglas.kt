package ovh.gabrielhuav.pow.domain.streetfighter

/**
 * 🔊 QUÉ VOZ INTERRUMPE A CUÁL, en "Titulación por Combate".
 *
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 * POR QUÉ ESTO ESTÁ AQUÍ Y NO DENTRO DE LA PANTALLA
 *
 * Estas reglas se afinaron **de oído**, a lo largo de varios días de 2026-07, y hasta ahora no
 * había NI UN test que las cubriera: vivían mezcladas con `MediaPlayer` dentro de `playSfSpecial`,
 * así que para comprobarlas había que oírlas. Eso significa que cualquiera podía romperlas al
 * portar el audio y **el juego solo "sonaría raro"**, sin que se pusiera rojo nada.
 *
 * Pero mirándolas de cerca no son audio: son **decisiones sobre un conjunto de nombres de
 * archivo**. Sacadas aquí se prueban sin reproducir nada, corren igual en Android y en iOS, y el
 * port del audio ya no puede alterarlas por accidente.
 * ═══════════════════════════════════════════════════════════════════════════════════════════
 *
 * LAS CUATRO REGLAS, con su fecha original:
 *
 * 1. (07-19b) **Un HURT en curso NO se corta por un ATAQUE del mismo peleador.** Antes, el
 *    contraataque al recuperarse cortaba su propio quejido y el hurt "no sonaba". Solo otro HURT
 *    lo reinicia (= te han vuelto a pegar).
 * 2. (07-19c) **Un ataque especial en curso NUNCA se detiene** por otras acciones.
 * 3. (07-22) **La INTRO (~15 s) tiene que terminar**: ninguna otra voz del mismo peleador la
 *    corta, y mientras suena las voces nuevas de ESE peleador **se saltan** (para que el grito de
 *    ataque no se encime). Otra intro sí la reemplaza (ronda nueva).
 * 4. (07-18r) **El MISMO clip re-disparado se corta y se relanza** desde el inicio. Antes se
 *    ignoraba mientras sonaba y "no se repetía" al volver a atacar.
 */
object SfVocesReglas {

    /**
     * Qué hacer con una voz que se quiere lanzar.
     *
     * @param saltar si `true`, la voz **no se reproduce** (regla 3).
     * @param aDetener rutas que hay que parar y soltar antes de lanzar la nueva.
     */
    data class Decision(
        val saltar: Boolean,
        val aDetener: Set<String>,
    )

    /** Decisión que corresponde a lanzar [rutaNueva] estando [sonando] en curso. */
    fun decidir(rutaNueva: String, sonando: Set<String>): Decision {
        val nombreNuevo = rutaNueva.substringAfterLast('/')
        val prefijoNuevo = prefijoDePeleador(nombreNuevo)
        val nuevaEsHurt = nombreNuevo.contains("_hurt")
        val nuevaEsIntro = nombreNuevo.contains("_intro")

        // Regla 3a: si este peleador tiene una intro sonando y lo nuevo NO es otra intro, se salta.
        if (!nuevaEsIntro) {
            val intoSuena = sonando.any { ruta ->
                val nombre = ruta.substringAfterLast('/')
                nombre.contains("_intro") && prefijoDePeleador(nombre) == prefijoNuevo
            }
            if (intoSuena) return Decision(saltar = true, aDetener = emptySet())
        }

        val aDetener = sonando.filterTo(mutableSetOf()) { ruta ->
            val nombre = ruta.substringAfterLast('/')
            // Regla 2: los especiales no se tocan. Regla 3b: las intros tampoco.
            !esAudioDePoderEspecial(nombre) &&
                !nombre.contains("_intro") &&
                // Solo se calla al MISMO peleador; los demás siguen sonando.
                prefijoDePeleador(nombre) == prefijoNuevo &&
                // Regla 1: un hurt en curso solo lo corta otro hurt.
                (nuevaEsHurt || !nombre.contains("_hurt"))
        }

        // Regla 4: el mismo clip siempre se relanza desde el principio, aunque las reglas de
        // arriba lo hubieran protegido (p. ej. un especial re-disparado).
        if (rutaNueva in sonando) aDetener += rutaNueva

        return Decision(saltar = false, aDetener = aDetener)
    }

    /**
     * Peleador al que pertenece un clip, deducido del nombre del archivo.
     *
     * Quita la extensión, los dígitos y guiones bajos finales (`_hurt_2` → `_hurt`), UNO de los
     * sufijos conocidos, y los guiones bajos que queden.
     *
     * ⚠️ Port LITERAL de `getFighterPrefix`. El orden importa: los dígitos se quitan ANTES que el
     * sufijo, o `goya_hurt_2` no llegaría nunca a terminar en `hurt`. Y solo se quita **un**
     * sufijo (hay `break`).
     */
    fun prefijoDePeleador(clave: String): String {
        var limpio = clave.removeSuffix(".ogg").removeSuffix(".mp3")
        while (limpio.isNotEmpty() && (limpio.last().isDigit() || limpio.last() == '_')) {
            limpio = limpio.dropLast(1)
        }
        for (s in SUFIJOS_DE_ACCION) {
            if (limpio.endsWith(s)) {
                limpio = limpio.removeSuffix(s)
                break
            }
        }
        while (limpio.isNotEmpty() && limpio.last() == '_') {
            limpio = limpio.dropLast(1)
        }
        return limpio
    }

    /**
     * ¿Es un audio de poder especial, de los que no se interrumpen?
     *
     * ⚠️ Port LITERAL de `isSpecialPowerAudio`, incluida su parte contraintuitiva: **lo que NO
     * es hurt/attack/intro cuenta como especial**, o sea que la lista de abajo es de EXCLUSIONES,
     * no de inclusiones. Por eso `win` quedó protegido (07-19): así las voces de victoria no las
     * cortan los gritos ni los golpes.
     */
    fun esAudioDePoderEspecial(clave: String): Boolean {
        val limpio = clave.removeSuffix(".ogg").removeSuffix(".mp3").lowercase()
        if (limpio.contains("power") || limpio.contains("electricity")) return true
        for (s in SUFIJOS_INTERRUMPIBLES) {
            if (limpio.endsWith(s) || limpio.contains("_$s")) return false
        }
        return true
    }

    /** Sufijos que [prefijoDePeleador] recorta. ⚠️ Incluye `win` y `power`; el de abajo NO. */
    private val SUFIJOS_DE_ACCION = listOf("hurt", "attack", "win", "power", "intro")

    /** Lo que SÍ se puede interrumpir. ⚠️ `win` NO está aquí a propósito (ver arriba). */
    private val SUFIJOS_INTERRUMPIBLES = listOf("hurt", "attack", "intro")
}
