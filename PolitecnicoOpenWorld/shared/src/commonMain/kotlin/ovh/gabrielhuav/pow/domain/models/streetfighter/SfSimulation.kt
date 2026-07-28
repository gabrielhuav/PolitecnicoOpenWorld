package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.math.floor

/**
 * 🥊 SIMULACIÓN PURA de la pelea — Fase 2c del refactor del motor compartido
 * (`SF/PLAN_refactor_motor_compartido.md`).
 *
 * POR QUÉ ESTO VIVE AQUÍ: cada pieza que sale del `StreetFighterViewModel` y llega a `:shared`
 * gana dos cosas de golpe — **corre en iOS** y **se puede testear sin dispositivo**. Dentro del
 * ViewModel (que es de Android y lleva `Context`) no tenía ni lo uno ni lo otro.
 *
 * ⚠️ REGLA: aquí solo entra lo que se puede calcular con datos. Nada que necesite assets del
 * atlas, audio, red o `Context`. Si una función necesita el frame actual del sprite, lo recibe
 * como parámetro; no lo va a buscar.
 *
 * ⚠️ Las CONSTANTES son las del juego, copiadas al pie de la letra. No las "redondees": mover
 * `DRAIN_PER_SEC` o los topes de la cámara cambia cómo se siente la pelea.
 */

/** Cámara que sigue a los dos peleadores, como el `updateCamera` del JS original. */
object SfCamera {

    /** Posición de cámara resuelta para un tick. */
    data class Pos(val x: Float, val y: Float)

    /**
     * Sigue a los dos peleadores. Dos comportamientos:
     * - Si están MÁS separados que el ancho útil, la cámara se centra entre ambos.
     * - Si no, empuja solo lo justo para que ninguno cruce el borde de scroll.
     *
     * La `y` sube un poco cuando alguien salta (se toma el MÁS alto de los dos) y se limita para
     * no enseñar por encima del fondo.
     */
    fun follow(p0: SfFighter, p1: SfFighter, camX: Float): Pos {
        val lowX = minOf(p0.x, p1.x)
        val highX = maxOf(p0.x, p1.x)
        var nueva = camX

        if (highX - lowX > SfConstants.SCENE_WIDTH - SfConstants.SCROLL_BOUNDARY * 2) {
            nueva = lowX + (highX - lowX) / 2f - SfConstants.SCENE_WIDTH / 2f
        } else {
            for (f in listOf(p0, p1)) {
                if (f.x < nueva + SfConstants.SCROLL_BOUNDARY) {
                    nueva = f.x - SfConstants.SCROLL_BOUNDARY
                } else if (f.x > nueva + SfConstants.SCENE_WIDTH - SfConstants.SCROLL_BOUNDARY) {
                    nueva = f.x + SfConstants.SCROLL_BOUNDARY - SfConstants.SCENE_WIDTH
                }
            }
        }
        val x = nueva.coerceIn(
            SfConstants.STAGE_PADDING,
            SfConstants.STAGE_WIDTH + SfConstants.STAGE_PADDING - SfConstants.SCENE_WIDTH,
        )
        val y = (-4f + floor(minOf(p0.y, p1.y) / 10f))
            .coerceIn(0f, SfConstants.STAGE_HEIGHT - SfConstants.SCENE_HEIGHT)
        return Pos(x, y)
    }
}

/** Chispazos de impacto: 4 cuadros y desaparecen. */
object SfSplashes {

    /** Cuántos cuadros tiene la animación de chispazo antes de retirarse. */
    const val FRAMES = 4

    /**
     * Avanza los chispazos en el sitio (la lista se MUTA, igual que hacía el VM) y retira los que
     * ya terminaron. Se le pasa `now` para que sea determinista y testeable.
     */
    fun advance(splashes: MutableList<SfHitSplash>, now: Long) {
        if (splashes.isEmpty()) return
        val stepMs = (4 * SfConstants.FRAME_TIME_MS).toLong()
        val it = splashes.listIterator()
        while (it.hasNext()) {
            val sp = it.next()
            if (sp.animationTimerMs + stepMs > now) continue
            if (sp.animationFrame + 1 >= FRAMES) it.remove()
            else it.set(sp.copy(animationFrame = sp.animationFrame + 1, animationTimerMs = now))
        }
    }
}

/** Barra de vida del HUD: el "roll-up" que hace que la vida baje deslizándose. */
object SfHealthBar {

    /**
     * Velocidad a la que DRENA la barra hacia la vida real (puntos por segundo).
     * ⚠️ Es el valor del juego (`HP_DRAIN_PER_SEC`). Cambiarlo altera la sensación del HUD.
     */
    const val DRAIN_PER_SEC = 200f

    /**
     * Un paso del roll-up. **Bajar es gradual** (a [DRAIN_PER_SEC]) y **subir es instantáneo**:
     * al curarse o empezar ronda la barra salta al valor nuevo sin animación.
     */
    fun rollUp(mostrado: Float, objetivo: Int, dt: Float): Float =
        if (objetivo >= mostrado) objetivo.toFloat()
        else maxOf(objetivo.toFloat(), mostrado - DRAIN_PER_SEC * dt)
}
