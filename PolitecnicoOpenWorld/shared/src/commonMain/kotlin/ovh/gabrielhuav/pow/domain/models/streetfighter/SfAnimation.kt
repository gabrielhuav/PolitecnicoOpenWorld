package ovh.gabrielhuav.pow.domain.models.streetfighter

/**
 * 🆕 (2026-07-22, Fase 2a del refactor del motor) AVANCE DE ANIMACIÓN por frames, extraído del
 * `StreetFighterViewModel` a lógica PURA (sin Android): opera sobre la lista de pasos
 * (`List<SfAnimFrame>`) y números, nada más. El VM conserva sus funciones como envoltorios que
 * pasan `animOf(f)`, así que el comportamiento no cambia.
 *
 * Semántica heredada del clon JS (y sus fixes):
 * - `delay > 0` = frames por paso; `delay == 0` = TRANSITION (no avanza sola);
 *   `delay == -1` = FREEZE/TERMINADOR (se queda ahí).
 * - Fijar un frame más allá del final hace WRAP a 0 (bucle de idle/walk).
 * - Una animación está COMPLETA en el frame `-1` o en el ÚLTIMO frame (fix 2026-07-18: varias
 *   hojas compartidas no traen el `-1` final y sin esto el peleador quedaba atascado).
 */
object SfAnimation {

    /** Índice EFECTIVO al fijar [frame]: wrap a 0 si se pasa del final (bucle). */
    fun frameIndex(anim: List<SfAnimFrame>, frame: Int): Int =
        if (frame >= anim.size) 0 else frame

    /** Vencimiento del frame [idx]: ahora + delay×FRAME_TIME_MS (espejo de withAnimationFrame). */
    fun frameTimerMs(
        anim: List<SfAnimFrame>,
        idx: Int,
        now: Long,
        durationMultiplier: Float = 1f,
    ): Long = now +
        (anim[idx].delay * SfConstants.FRAME_TIME_MS * durationMultiplier).toLong()

    /**
     * ¿Toca avanzar al siguiente frame? `false` si el frame actual es FREEZE/TRANSITION
     * (`delay <= 0`) o si su timer aún no vence (espejo exacto de updateAnimation).
     */
    fun shouldAdvance(anim: List<SfAnimFrame>, frame: Int, timerMs: Long, now: Long): Boolean {
        val delay = anim[frame.coerceIn(0, anim.size - 1)].delay
        return delay > 0 && now > timerMs
    }

    /**
     * ¿La animación TERMINÓ? En el frame TERMINADOR (`delay == -1`) o en el ÚLTIMO frame
     * (espejo exacto de isAnimationCompleted, incluido el fix de hojas sin `-1`).
     */
    fun isCompleted(anim: List<SfAnimFrame>, frame: Int): Boolean {
        val idx = frame.coerceIn(0, anim.size - 1)
        return anim[idx].delay == -1 || idx >= anim.size - 1
    }
}
