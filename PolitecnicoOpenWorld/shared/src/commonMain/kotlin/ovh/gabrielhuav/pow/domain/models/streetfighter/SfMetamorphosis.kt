package ovh.gabrielhuav.pow.domain.models.streetfighter

/** Reglas puras de la segunda vida Presidenta ↔ Yoalli. */
object SfMetamorphosis {

    data class Plan(
        val powerState: SfFighterState,
        val targetId: SfFighterId,
    )

    /**
     * Ambos jefes se transforman una sola vez al llegar a 25% de vida y únicamente en ronda 2.
     * [SfFighter.metamorphosed] impide Presidenta→Yoalli→Presidenta en la misma pelea.
     */
    fun planFor(fighter: SfFighter, roundNumber: Int): Plan? {
        if (roundNumber != 2 || fighter.metamorphosed || fighter.metamorphosing) return null
        if (fighter.hitPoints > SfConstants.HEALTH_MAX_HIT_POINTS / 4) return null
        return when (fighter.id) {
            SfFighterId.LA_PRESIDENTA -> Plan(
                powerState = SfFighterState.BONUS_POWER_11,
                targetId = SfFighterId.YOALLI_EHECATL,
            )
            SfFighterId.YOALLI_EHECATL -> Plan(
                powerState = SfFighterState.BONUS_POWER_10,
                targetId = SfFighterId.LA_PRESIDENTA,
            )
            else -> null
        }
    }

    fun isTransformationPower(id: SfFighterId, state: SfFighterState): Boolean =
        (id == SfFighterId.LA_PRESIDENTA && state == SfFighterState.BONUS_POWER_11) ||
            (id == SfFighterId.YOALLI_EHECATL && state == SfFighterState.BONUS_POWER_10)
}
