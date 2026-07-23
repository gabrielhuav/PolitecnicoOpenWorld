package ovh.gabrielhuav.pow.domain.models.streetfighter

/**
 * 🆕 (2026-07-22, Fase 1 del refactor del motor) CINEMÁTICA de un tick, extraída del
 * `StreetFighterViewModel` a lógica PURA (sin Android, determinista) para testearla. El VM la
 * llama en `finishFighterUpdate`; el comportamiento no cambia.
 */
object SfPhysics {

    /**
     * Avanza un tick: posición += velocidad (menos el `slide`, según el encaramiento) y el `slide`
     * decae por su fricción. Espejo exacto del bloque de posición/slide del VM.
     */
    fun step(f: SfFighter, dt: Float): SfFighter {
        var nf = f.copy(
            x = f.x + (f.velocityX - f.slideVelocity) * f.direction.sign * dt,
            y = f.y + f.velocityY * dt,
        )
        if (nf.slideVelocity > 0f) {
            val slide = (nf.slideVelocity - nf.slideFriction * dt).coerceAtLeast(0f)
            nf = nf.copy(slideVelocity = slide, slideFriction = if (slide > 0f) nf.slideFriction else 0f)
        }
        return nf
    }
}
