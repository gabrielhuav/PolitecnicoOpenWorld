package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState

internal enum class SfMatchSound { VICTORY, LOSE }

/** Una ronda activa, después del intro; el selector también tiene roundNumber=1. */
internal fun sfRoundSoundKey(state: StreetFighterState): String? {
    if (state.inCharacterSelect || state.battleEnded || state.showEndMenu || state.isPaused) return null
    if (state.waitingForOpponentReady || state.showRoundIntro) return null
    if (state.showcaseRunning || state.audioShowcaseRunning || state.tutorialActive) return null
    if (state.onlineStatus != SfOnlineStatus.OFF && state.onlineStatus != SfOnlineStatus.FIGHTING) return null
    return "${state.player.id}:${state.cpu.id}:${state.roundNumber}:" +
        "${state.playerRoundWins}:${state.cpuRoundWins}:${state.arcadeStep}"
}

/** battleEnded acaba cada ronda; showEndMenu identifica el resultado del combate completo. */
internal fun sfMatchSound(state: StreetFighterState): SfMatchSound? {
    if (state.inCharacterSelect || !state.showEndMenu) return null
    val winner = state.winnerIndex?.takeIf { it in 0..1 } ?: return null
    // Offline / IA vs IA: perspectiva P1. Online: el invitado controla P2.
    val localPlayer = if (state.onlineStatus == SfOnlineStatus.OFF || state.isHost) 0 else 1
    return if (winner == localPlayer) SfMatchSound.VICTORY else SfMatchSound.LOSE
}
