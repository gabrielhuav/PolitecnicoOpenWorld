package ovh.gabrielhuav.pow.domain.models.streetfighter

/** Avance de proyectiles sin assets ni plataforma; el VM conserva la detección de cajas y el daño. */
internal object SfFireballs {
    private val activeDelays = listOf(5, 2, 5, 1)
    val collidedDelays = listOf(13, 3, 7)

    // Los callbacks quedan inline para no asignar lambdas capturadas en cada tick.
    internal inline fun advance(
        fireballs: MutableList<SfFireball>,
        now: Long,
        dt: Float,
        camX: Float,
        maxFireballs: Int,
        hitArea: (SfFireball) -> SfHurtArea?,
        onHit: (SfFireball, SfHurtArea) -> Unit,
    ) {
        // Tope global: en PESADILLA/IA-vs-IA se acumulaban decenas → lag + muro imbloqueable.
        if (fireballs.size > maxFireballs) {
            val keep = fireballs.sortedByDescending { it.state == SfFireballState.ACTIVE }.take(maxFireballs)
            fireballs.clear()
            fireballs.addAll(keep)
        }
        var index = 0
        while (index < fireballs.size) {
            var fb = fireballs[index]
            val delays = if (fb.state == SfFireballState.ACTIVE) activeDelays else collidedDelays
            if (now > fb.animationTimerMs) {
                var frame = fb.animationFrame + 1
                if (frame >= delays.size) {
                    frame = 0
                    if (fb.state == SfFireballState.COLLIDED) {
                        fireballs.removeAt(index)
                        continue
                    }
                }
                fb = fb.copy(
                    animationFrame = frame,
                    animationTimerMs = now + (delays[frame] * SfConstants.FRAME_TIME_MS).toLong(),
                )
            }
            fb = fb.copy(x = fb.x + fb.velocity * fb.direction.sign * dt)
            if (fb.x - camX >= SfConstants.SCENE_WIDTH + 56f || fb.x - camX <= -56f) {
                fireballs.removeAt(index)
                continue
            }
            val area = if (fb.state == SfFireballState.ACTIVE) hitArea(fb) else null
            if (area != null) {
                fb = fb.copy(
                    state = SfFireballState.COLLIDED,
                    animationFrame = 0,
                    velocity = fb.velocity * 0.33f,
                    animationTimerMs = now + (collidedDelays[0] * SfConstants.FRAME_TIME_MS).toLong(),
                )
            }
            fireballs[index] = fb
            if (area != null) {
                // Un impacto letal abre ACABALO y limpia esta misma lista desde onHit. No debe
                // quedar un ListIterator vivo ni escribirse de nuevo el proyectil tras ese cambio:
                // en iOS eso abortaba con ConcurrentModificationException (Charro vs Charro).
                onHit(fb, area)
                if (index >= fireballs.size || fireballs[index] !== fb) return
            }
            index++
        }
    }
}
