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
        var velocityX = f.velocityX
        var velocityY = f.velocityY
        if (x.isNaN() || x.isInfinite()) x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING
        if (y.isNaN() || y.isInfinite()) y = SfConstants.STAGE_FLOOR
        if (velocityX.isNaN() || velocityX.isInfinite()) velocityX = 0f
        if (velocityY.isNaN() || velocityY.isInfinite()) velocityY = 0f
        x = x.coerceIn(SfConstants.STAGE_X_MIN, SfConstants.STAGE_X_MAX)
        // No permitir caer bajo el piso; el salto puede subir pero con tope de aire
        y = y.coerceIn(SfConstants.STAGE_FLOOR - STAGE_AIR_CEILING, SfConstants.STAGE_FLOOR)
        return if (x != f.x || y != f.y || velocityX != f.velocityX || velocityY != f.velocityY) {
            f.copy(x = x, y = y, velocityX = velocityX, velocityY = velocityY)
        } else {
            f
        }
    }

    /** Un estado aéreo que ya cruzó el piso debe aterrizar en este mismo tick. */
    fun shouldLand(f: SfFighter): Boolean =
        f.isAirborne && f.y >= SfConstants.STAGE_FLOOR && f.velocityY >= 0f

    /**
     * 🆕 (2026-07-27, Fase 2c del refactor del motor) EMPUJE DE PUSHBOXES: resultado de un tick de
     * `updateStageConstraints`, ya sin Android y sin ViewModel.
     *
     * POR QUÉ ES PURO: el empuje solo necesita las DOS posiciones, la cámara, las dos pushboxes y
     * el `dt`. No necesita el estado del VM, ni assets, ni red. Por eso puede vivir en `:shared` y
     * correr igual en iOS — y por eso se puede TESTEAR sin dispositivo.
     *
     * Los estados EMPUJABLES incluyen caminar a propósito: si no, dos peleadores que chocan en
     * WALK se quedan "congelados" empujándose sin resolver nunca el solape.
     */
    val PUSHABLE_STATES: Set<SfFighterState> = setOf(
        SfFighterState.IDLE, SfFighterState.CROUCH, SfFighterState.JUMP_UP,
        SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_FORWARD,
        SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        SfFighterState.IDLE_TURN, SfFighterState.CROUCH_TURN,
    )

    /** Las dos posiciones ya resueltas tras un tick de empuje. */
    data class PushResult(val self: SfFighter, val opponent: SfFighter)

    /**
     * Mete al peleador dentro del VIEWPORT (contra la cámara) sin sacarlo del escenario.
     * Es el paso previo al empuje; separado porque también se testea solo.
     */
    fun clampToViewport(f: SfFighter, camX: Float): SfFighter {
        val margin = SfConstants.FIGHTER_DEFAULT_WIDTH
        var nf = clampToStage(f)
        nf = when {
            nf.x - camX + margin > SfConstants.SCENE_WIDTH ->
                nf.copy(x = camX + SfConstants.SCENE_WIDTH - margin)
            nf.x - camX - margin < 0f -> nf.copy(x = camX + margin)
            else -> nf
        }
        return clampToStage(nf)
    }

    /**
     * Resuelve el solape de pushboxes entre `self` y `opponent`.
     *
     * `selfPush` / `oppPush` son las pushbox del FRAME ACTUAL de cada uno **en coordenadas de
     * frame** (sin transformar a mundo): quien llama las saca del atlas, que es lo único que
     * necesita Android. Así esta función se queda pura.
     *
     * Si no hay solape devuelve las posiciones ya clampadas, sin tocar nada más.
     */
    fun resolvePushboxes(
        self: SfFighter,
        opponent: SfFighter,
        selfPush: SfBox,
        oppPush: SfBox,
        camX: Float,
        dt: Float,
        overlapping: Boolean,
    ): PushResult {
        val f0 = clampToViewport(self, camX)
        if (!overlapping) return PushResult(f0, opponent)

        var f = f0
        var opp = opponent
        if (f.x <= opp.x) {
            val nx = opp.x + oppPush.x - (selfPush.x + selfPush.width)
            f = f.copy(x = nx.coerceIn(SfConstants.STAGE_X_MIN, SfConstants.STAGE_X_MAX))
            if (opp.state in PUSHABLE_STATES) {
                opp = clampToStage(opp.copy(x = opp.x + SfConstants.FIGHTER_PUSH_FRICTION * dt))
            }
        } else {
            val nx = minOf(
                camX + SfConstants.SCENE_WIDTH - selfPush.width.coerceAtLeast(1f),
                opp.x + oppPush.width,
            )
            f = f.copy(x = nx.coerceIn(SfConstants.STAGE_X_MIN, SfConstants.STAGE_X_MAX))
            if (opp.state in PUSHABLE_STATES) {
                opp = clampToStage(opp.copy(x = opp.x - SfConstants.FIGHTER_PUSH_FRICTION * dt))
            }
        }
        return PushResult(clampToStage(f), clampToStage(opp))
    }
}
