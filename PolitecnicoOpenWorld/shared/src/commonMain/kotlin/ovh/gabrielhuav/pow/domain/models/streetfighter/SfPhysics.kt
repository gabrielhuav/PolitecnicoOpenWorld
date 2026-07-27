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

    /** Tope de AIRE por encima del piso (el salto no sube más que esto). */
    const val STAGE_AIR_CEILING = 220f

    /**
     * 🆕 (2026-07-22, Fase 2b) Mantener al peleador DENTRO del escenario (mundo) y nunca bajo el
     * piso; rescata coordenadas NaN/infinitas (empujes degenerados). Espejo exacto del
     * `clampFighterToStage` del VM; el VM delega aquí.
     */
    fun clampToStage(f: SfFighter): SfFighter {
        var x = f.x
        var y = f.y
        if (x.isNaN() || x.isInfinite()) x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING
        if (y.isNaN() || y.isInfinite()) y = SfConstants.STAGE_FLOOR
        x = x.coerceIn(SfConstants.STAGE_X_MIN, SfConstants.STAGE_X_MAX)
        // No permitir caer bajo el piso; el salto puede subir pero con tope de aire
        y = y.coerceIn(SfConstants.STAGE_FLOOR - STAGE_AIR_CEILING, SfConstants.STAGE_FLOOR)
        return if (x != f.x || y != f.y) f.copy(x = x, y = y) else f
    }
}
