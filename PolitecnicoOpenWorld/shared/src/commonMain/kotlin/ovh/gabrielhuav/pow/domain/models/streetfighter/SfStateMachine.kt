package ovh.gabrielhuav.pow.domain.models.streetfighter

/**
 * 🆕 (2026-07-22, Fase 1 del refactor del motor) MÁQUINA DE ESTADOS del peleador, extraída del
 * `StreetFighterViewModel` a datos PUROS (sin Android) para poder testearla en JVM. El VM la
 * referencia por alias, así que su comportamiento no cambia: aquí vive la MISMA tabla `validFrom`
 * de siempre.
 *
 * `VALID_FROM[destino]` = conjunto de estados desde los que se PUEDE entrar a `destino` por input.
 * Algunos estados (derribos, aterrizajes, bloqueo, mareo, KO/VICTORY) los FUERZA la lógica y por
 * eso admiten "cualquier estado" o llegan por caminos aparte (`forceState`).
 */
object SfStateMachine {

    /** Estados que DERRIBAN al defensor (pasa a THROWN y luego GET_UP). */
    val KNOCKDOWN_STATES: Set<SfFighterState> = setOf(
        SfFighterState.SWEEP, SfFighterState.SUPER_ART, SfFighterState.FATALITY,
    )

    /** Orígenes de los SPECIALS clásicos (incluye cancel desde golpes, vía tryChainCancel). */
    val SPECIAL_VALID_FROM: Set<SfFighterState> = setOf(
        SfFighterState.IDLE, SfFighterState.IDLE_TURN, SfFighterState.WALK_FORWARD,
        SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
        SfFighterState.CROUCH_UP, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH,
        SfFighterState.CROUCH_TURN, SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH,
        SfFighterState.HEAVY_PUNCH,
        SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
    )

    /** La SUPER ART normal puede cortar la carrera solo para la regla forzada de IA vs IA. */
    val SUPER_ART_VALID_FROM: Set<SfFighterState> = SPECIAL_VALID_FROM + SfFighterState.RUN

    /** Orígenes de los golpes normales de pie (incluye chain cancel, exige attackStruck aparte). */
    val ATTACK_VALID_FROM: Set<SfFighterState> = setOf(
        SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        SfFighterState.IDLE_TURN, SfFighterState.JUMP_LAND, SfFighterState.CROUCH_UP,
        SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
        SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
        SfFighterState.RUN,
    )

    /** Neutro DE PIE: dash, parry alto, agarre y burla salen solo de aquí. */
    val NEUTRAL_GROUND: Set<SfFighterState> = setOf(
        SfFighterState.IDLE, SfFighterState.IDLE_TURN,
        SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        SfFighterState.JUMP_LAND, SfFighterState.CROUCH_UP,
    )

    /** Ataques AGACHADO: desde cuclillas + desde el propio golpe agachado (chain cancel). */
    val CROUCH_ATTACK_VALID_FROM: Set<SfFighterState> = setOf(
        SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_TURN,
        SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
        SfFighterState.CROUCH_HEAVY_PUNCH,
    )

    /** Estados bajos que, al completar, deben poder recuperar a cuclillas o de pie. */
    val CROUCH_RECOVERY_FROM: Set<SfFighterState> = setOf(
        SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
        SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
        SfFighterState.HURT_CROUCH, SfFighterState.PARRY_LOW,
    )

    /** Ambos dashes deben recuperar a neutro al terminar su animación. */
    val DASH_RECOVERY_FROM: Set<SfFighterState> = setOf(
        SfFighterState.DASH_FORWARD, SfFighterState.DASH_BACKWARD,
    )

    /** Ataques AÉREOS: solo mientras se está en el aire. */
    val AIR_ATTACK_VALID_FROM: Set<SfFighterState> = setOf(
        SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
    )

    val VALID_FROM: Map<SfFighterState, Set<SfFighterState>> = mapOf(
        SfFighterState.IDLE to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
            SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
            SfFighterState.CROUCH_UP, SfFighterState.JUMP_LAND, SfFighterState.IDLE_TURN,
            SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
            SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
            SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
            SfFighterState.STUN,
            SfFighterState.BLOCK_HIGH, // 🆕 (2026-07-26) soltar la guardia alta vuelve a IDLE al instante
        ) + SF_BONUS_POWER_STATES.toSet() + CROUCH_RECOVERY_FROM + DASH_RECOVERY_FROM,
        SfFighterState.WALK_FORWARD to setOf(
            SfFighterState.IDLE, SfFighterState.JUMP_FORWARD, SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
        ),
        SfFighterState.WALK_BACKWARD to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_LAND,
            SfFighterState.BLOCK_HIGH, // 🆕 (2026-07-26) la guardia alta rebota a retroceder (responsivo)
        ),
        SfFighterState.JUMP_START to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
            SfFighterState.RUN,
        ),
        SfFighterState.JUMP_LAND to setOf(
            SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
        ),
        SfFighterState.JUMP_UP to setOf(SfFighterState.IDLE, SfFighterState.JUMP_START),
        SfFighterState.JUMP_FORWARD to setOf(SfFighterState.JUMP_START, SfFighterState.WALK_FORWARD),
        SfFighterState.JUMP_BACKWARD to setOf(SfFighterState.JUMP_START, SfFighterState.WALK_BACKWARD),
        SfFighterState.CROUCH_DOWN to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
            SfFighterState.BLOCK_HIGH, // 🆕 (2026-07-26) de guardia alta a agacharse/guardia baja
        ),
        SfFighterState.CROUCH to setOf(
            SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_TURN,
            SfFighterState.BLOCK_LOW, // 🆕 (2026-07-26) la guardia baja rebota a cuclillas (responsivo)
        ) + CROUCH_RECOVERY_FROM,
        SfFighterState.CROUCH_UP to setOf(SfFighterState.CROUCH, SfFighterState.BLOCK_LOW), // 🆕 soltar guardia baja
        SfFighterState.IDLE_TURN to setOf(
            SfFighterState.IDLE, SfFighterState.JUMP_LAND, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        ),
        SfFighterState.CROUCH_TURN to setOf(SfFighterState.CROUCH),
        SfFighterState.LIGHT_PUNCH to ATTACK_VALID_FROM,
        SfFighterState.MEDIUM_PUNCH to ATTACK_VALID_FROM,
        SfFighterState.HEAVY_PUNCH to ATTACK_VALID_FROM,
        SfFighterState.LIGHT_KICK to ATTACK_VALID_FROM,
        SfFighterState.MEDIUM_KICK to ATTACK_VALID_FROM,
        SfFighterState.HEAVY_KICK to ATTACK_VALID_FROM,
        SfFighterState.HURT_HEAD_LIGHT to SF_HURT_STATES,
        SfFighterState.HURT_HEAD_MEDIUM to SF_HURT_STATES,
        SfFighterState.HURT_HEAD_HEAVY to SF_HURT_STATES,
        SfFighterState.HURT_BODY_LIGHT to SF_HURT_STATES,
        SfFighterState.HURT_BODY_MEDIUM to SF_HURT_STATES,
        SfFighterState.HURT_BODY_HEAVY to SF_HURT_STATES,
        SfFighterState.SPECIAL_1_LIGHT to SPECIAL_VALID_FROM,
        SfFighterState.SPECIAL_1_MEDIUM to SPECIAL_VALID_FROM,
        SfFighterState.SPECIAL_1_HEAVY to SPECIAL_VALID_FROM,
        SfFighterState.VICTORY to SfFighterState.entries.toSet(),
        SfFighterState.KO to SfFighterState.entries.toSet(),
        SfFighterState.STUN to SfFighterState.entries.toSet(),
        SfFighterState.DASH_FORWARD to NEUTRAL_GROUND,
        SfFighterState.DASH_BACKWARD to NEUTRAL_GROUND,
        SfFighterState.BLOCK_HIGH to SF_HURT_STATES,
        SfFighterState.BLOCK_LOW to SF_HURT_STATES,
        SfFighterState.PARRY_HIGH to NEUTRAL_GROUND,
        SfFighterState.PARRY_LOW to setOf(SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN),
        SfFighterState.CROUCH_PUNCH to CROUCH_ATTACK_VALID_FROM,
        SfFighterState.CROUCH_KICK to CROUCH_ATTACK_VALID_FROM,
        SfFighterState.CROUCH_HEAVY_PUNCH to CROUCH_ATTACK_VALID_FROM,
        SfFighterState.SWEEP to CROUCH_ATTACK_VALID_FROM,
        SfFighterState.AIR_PUNCH to AIR_ATTACK_VALID_FROM,
        SfFighterState.AIR_KICK to AIR_ATTACK_VALID_FROM,
        SfFighterState.LONG_KICK to ATTACK_VALID_FROM,
        SfFighterState.OVERHEAD to ATTACK_VALID_FROM,
        SfFighterState.GRAB to NEUTRAL_GROUND,
        SfFighterState.THROW to setOf(SfFighterState.GRAB),
        SfFighterState.THROWN to SfFighterState.entries.toSet(),
        SfFighterState.GET_UP to setOf(SfFighterState.THROWN),
        SfFighterState.TAUNT to NEUTRAL_GROUND,
        SfFighterState.SUPER_ART to SUPER_ART_VALID_FROM,
        SfFighterState.RUN to setOf(SfFighterState.DASH_FORWARD, SfFighterState.RUN),
        SfFighterState.FATALITY to setOf(
            SfFighterState.RUN, SfFighterState.DASH_FORWARD,
        ),
        SfFighterState.IDLE_RELAXED to NEUTRAL_GROUND,
        SfFighterState.TALK to NEUTRAL_GROUND,
        SfFighterState.HURT_CROUCH to setOf(
            SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_UP,
            SfFighterState.CROUCH_TURN, SfFighterState.BLOCK_LOW, SfFighterState.HURT_CROUCH,
            SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
            SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
        ),
    ) + SF_BONUS_POWER_STATES.associateWith { SPECIAL_VALID_FROM }

    /**
     * ¿Se puede entrar a [to] desde [from] por INPUT (según la tabla)? Los estados que no son clave
     * en `VALID_FROM` solo llegan FORZADOS por la lógica (`forceState`), por eso devuelven `false`.
     */
    fun canEnter(from: SfFighterState, to: SfFighterState): Boolean =
        from in (VALID_FROM[to] ?: emptySet())
}
