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
        else -> strength.damage
    }
}
