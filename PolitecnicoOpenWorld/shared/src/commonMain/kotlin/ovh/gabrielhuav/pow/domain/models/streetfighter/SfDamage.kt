package ovh.gabrielhuav.pow.domain.models.streetfighter

/**
 * 🆕 (2026-07-22, Fase 1 del refactor del motor) DAÑO BASE de un golpe, extraído del
 * `StreetFighterViewModel` a lógica PURA (sin Android) para testearlo. El VM delega aquí, así que
 * el comportamiento no cambia. Aquí NO va la reducción por bloqueo ni la escala de combo (esas
 * dependen del estado de la pelea; se resolverán en una fase posterior).
 *
 * El plan del refactor marca este cálculo como sensible: **ya hubo un bug real en multiplayer**
 * (el daño no viajaba), así que fijarlo con tests es valioso.
 */
object SfDamage {

    /** Estados de ataque que hacen CHIP al bloquearse (los normales bloqueados hacen 0). */
    val CHIP_ATTACK_STATES: Set<SfFighterState> = setOf(
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM,
        SfFighterState.SPECIAL_1_HEAVY, SfFighterState.SUPER_ART, SfFighterState.FATALITY,
    )

    /**
     * Daño base según QUIÉN pega: la súper, el fatality y el agarre tienen daño propio; el resto
     * usa la fuerza del golpe (ligero/medio/fuerte). Espejo exacto de `damageForAttack`.
     */
    fun forAttack(state: SfFighterState, strength: SfAttackStrength): Int = when (state) {
        SfFighterState.SUPER_ART -> SfConstants.SUPER_ART_DAMAGE
        SfFighterState.FATALITY -> SfConstants.FATALITY_DAMAGE
        SfFighterState.GRAB -> SfConstants.THROW_DAMAGE
        // 🆕 (2026-08-29) CONTRAATAQUE + DERRIBO CON PODER: daño propio, no el de la fuerza.
        SfFighterState.COUNTER -> SfConstants.COUNTER_THROW_DAMAGE
        SfFighterState.POWER_GRAB -> SfConstants.POWER_THROW_DAMAGE
        else -> strength.damage
    }

    /**
     * Daño FINAL a partir del base: si está BLOQUEADO, los normales hacen 0 y los que hacen chip
     * (especial/súper/fatality) pegan `/6`; si conecta LIMPIO, escala hacia abajo por combo
     * (-10% por golpe encadenado, piso 50%). `comboHits` = golpes encadenados (1 = el primero).
     */
    fun resolvedDamage(base: Int, blocked: Boolean, chipAttack: Boolean, comboHits: Int): Int {
        if (blocked) return if (chipAttack) maxOf(1, base / 6) else 0
        val scale = (1f - SfConstants.COMBO_DAMAGE_SCALE_STEP * (comboHits - 1))
            .coerceAtLeast(SfConstants.COMBO_DAMAGE_SCALE_MIN)
        return maxOf(1, (base * scale).toInt())
    }

    /** Metadatos (fuerza + tipo) de un estado de ATAQUE, como el `states{}` del JS. */
    data class SfAttackMeta(val strength: SfAttackStrength, val type: SfAttackType)

    /** Qué estados SON un ataque y con qué fuerza/tipo pegan. Un estado que NO está aquí no golpea. */
    val ATTACK_META: Map<SfFighterState, SfAttackMeta> = mapOf(
        SfFighterState.LIGHT_PUNCH to SfAttackMeta(SfAttackStrength.LIGHT, SfAttackType.PUNCH),
        SfFighterState.MEDIUM_PUNCH to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
        SfFighterState.HEAVY_PUNCH to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
        SfFighterState.LIGHT_KICK to SfAttackMeta(SfAttackStrength.LIGHT, SfAttackType.KICK),
        SfFighterState.MEDIUM_KICK to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.KICK),
        SfFighterState.HEAVY_KICK to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.KICK),
        SfFighterState.SPECIAL_1_LIGHT to SfAttackMeta(SfAttackStrength.LIGHT, SfAttackType.PUNCH),
        SfFighterState.SPECIAL_1_MEDIUM to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
        SfFighterState.SPECIAL_1_HEAVY to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
        SfFighterState.CROUCH_PUNCH to SfAttackMeta(SfAttackStrength.LIGHT, SfAttackType.PUNCH),
        SfFighterState.CROUCH_KICK to SfAttackMeta(SfAttackStrength.LIGHT, SfAttackType.KICK),
        SfFighterState.CROUCH_HEAVY_PUNCH to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
        SfFighterState.SWEEP to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.KICK),
        SfFighterState.AIR_PUNCH to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
        SfFighterState.AIR_KICK to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.KICK),
        SfFighterState.LONG_KICK to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.KICK),
        SfFighterState.OVERHEAD to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
        SfFighterState.GRAB to SfAttackMeta(SfAttackStrength.LIGHT, SfAttackType.PUNCH),
        SfFighterState.SUPER_ART to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
        SfFighterState.FATALITY to SfAttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
        // 🆕 (2026-08-29) POWER_GRAB SÍ tiene hitbox propia (como GRAB); el daño real lo fija
        // forAttack(), esto solo alimenta el sonido de ataque en changeState(). COUNTER y
        // POWER_THROW no van aquí: son reactivos/de remate, sin hit-check propio (como PARRY_*/THROW).
        SfFighterState.POWER_GRAB to SfAttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
    )
}
