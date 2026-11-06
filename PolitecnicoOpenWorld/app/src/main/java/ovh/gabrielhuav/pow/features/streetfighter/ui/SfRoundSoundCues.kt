package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState

internal enum class SfMatchSound { VICTORY, LOSE }

internal sealed interface SfRoundSoundCue {
    data object Reset : SfRoundSoundCue
    data object None : SfRoundSoundCue
    data class Start(val key: String) : SfRoundSoundCue
}

/** El intro identifica una ronda nueva, incluso al reiniciar el primer combate. */
internal fun sfRoundSoundCue(state: StreetFighterState, playedKey: String?): SfRoundSoundCue {
    if (state.inCharacterSelect || state.showRoundIntro) return SfRoundSoundCue.Reset
    val key = sfRoundSoundKey(state) ?: return SfRoundSoundCue.None
    return if (key == playedKey) SfRoundSoundCue.None else SfRoundSoundCue.Start(key)
}

/** Una ronda activa, después del intro; el selector también tiene roundNumber=1. */
internal fun sfRoundSoundKey(state: StreetFighterState): String? {
    if (state.inCharacterSelect || state.battleEnded || state.showEndMenu || state.isPaused) return null
    if (state.waitingForOpponentReady || state.showRoundIntro) return null
    if (state.showcaseRunning || state.audioShowcaseRunning || state.tutorialActive) return null
    if (state.onlineStatus != SfOnlineStatus.OFF && state.onlineStatus != SfOnlineStatus.FIGHTING) return null
    // Los IDs pueden cambiar por metamorfosis dentro de una misma ronda.
    return "${state.roundNumber}:${state.playerRoundWins}:${state.cpuRoundWins}:${state.arcadeStep}"
}

/** battleEnded acaba cada ronda; showEndMenu identifica el resultado del combate completo. */
internal fun sfMatchSound(state: StreetFighterState): SfMatchSound? {
    if (state.inCharacterSelect || !state.showEndMenu) return null
    val winner = state.winnerIndex?.takeIf { it in 0..1 } ?: return null
    // networkIndexOf ya normaliza P1/P2: el índice 0 es local tanto para host como invitado.
    return if (winner == 0) SfMatchSound.VICTORY else SfMatchSound.LOSE
}
