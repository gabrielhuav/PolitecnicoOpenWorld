package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeContinue
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeExit
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeRetry
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.comboSheet
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cycleShowcaseSpeed
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.dismissGauntletReport
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.exitTutorial
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.goToPreviousShowcaseAnimation
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.replayCurrentShowcaseAudio
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.resumeArcadeSession
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.skipShowcaseFighter
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.skipToNextShowcaseAnimation
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startAiVsAi
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startArcade
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startGauntletArcade
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startGauntletRoundRobin
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startShowcase
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startTutorial
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopAudioShowcase
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopGauntlet
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.tutorialRestartLesson
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.tutorialSkipLesson

/** Controlador real del motor offline, usado por iOS. */
class OfflineStreetFighterController(
    private val viewModel: StreetFighterViewModel = StreetFighterViewModel(),
) : StreetFighterController {
    override val state get() = viewModel.state
    override val soundEvents get() = viewModel.soundEvents
    override fun isLowEndDevice() = viewModel.isLowEndDevice()
    override fun alphaFallbackId(id: SfFighterId) = viewModel.alphaFallbackId(id)
    override fun forcePause() = viewModel.forcePause()
    override fun hasArcadeSession() = viewModel.hasArcadeSession()
    override fun playerHasNewMoves() = viewModel.playerHasNewMoves()
    override fun showHitboxes() = viewModel.showHitboxes()
    override fun showSfFps() = viewModel.showSfFps()
    override fun isFighterActuallyUnlocked(id: SfFighterId) = viewModel.isFighterActuallyUnlocked(id)
    override fun setAssetsLoadingUi(loading: Boolean) = viewModel.setAssetsLoadingUi(loading)
    override fun onJoystickMove(angleRad: Double) = viewModel.onJoystickMove(angleRad)
    override fun onJoystickRelease() = viewModel.onJoystickRelease()
    override fun onAttackPressed(strength: SfAttackStrength, type: SfAttackType) =
        viewModel.onAttackPressed(strength, type)
    override fun onKickPressed() = viewModel.onKickPressed()
    override fun onBonusPowerPressed() = viewModel.onBonusPowerPressed()
    override fun onParryPressed() = viewModel.onParryPressed()
    override fun onGrabPressed() = viewModel.onGrabPressed()
    override fun onTauntPressed() = viewModel.onTauntPressed()
    override fun onSuperArtPressed() = viewModel.onSuperArtPressed()
    override fun tutorialSkipLesson() = viewModel.tutorialSkipLesson()
    override fun tutorialRestartLesson() = viewModel.tutorialRestartLesson()
    override fun exitTutorial() = viewModel.exitTutorial()
    override fun startTutorial(id: SfFighterId) = viewModel.startTutorial(id)
    override fun comboSheet(id: SfFighterId) = viewModel.comboSheet(id)
    override fun backToCharacterSelect() = viewModel.backToCharacterSelect()
    override fun stopGauntlet() = viewModel.stopGauntlet()
    override fun goToPreviousShowcaseAnimation() = viewModel.goToPreviousShowcaseAnimation()
    override fun skipToNextShowcaseAnimation() = viewModel.skipToNextShowcaseAnimation()
    override fun skipShowcaseFighter() = viewModel.skipShowcaseFighter()
    override fun cycleShowcaseSpeed() = viewModel.cycleShowcaseSpeed()
    override fun replayCurrentShowcaseAudio() = viewModel.replayCurrentShowcaseAudio()
    override fun dismissGauntletReport() = viewModel.dismissGauntletReport()
    override fun startGauntletRoundRobin() = viewModel.startGauntletRoundRobin()
    override fun startGauntletArcade() = viewModel.startGauntletArcade()
    override fun startShowcase() = viewModel.startShowcase()
    override fun stopAudioShowcase() = viewModel.stopAudioShowcase()
    override fun startAiVsAi(a: SfFighterId, b: SfFighterId, difficulty: SfCpuDifficulty, intensity: Float) =
        viewModel.startAiVsAi(a, b, difficulty, intensity)
    override fun selectableFighters() = viewModel.selectableFighters()
    override fun lockedFighters() = viewModel.lockedFighters()
    override fun unlockedMaps() = viewModel.unlockedMaps()
    override fun devUnlockAll() = viewModel.devUnlockAll()
    override fun selectCharacter(id: SfFighterId, rivalId: SfFighterId?, difficulty: SfCpuDifficulty) =
        viewModel.selectCharacter(id, rivalId, difficulty)
    override fun startArcade(playerId: SfFighterId, difficulty: SfCpuDifficulty) =
        viewModel.startArcade(playerId, difficulty)

    override fun startOnline(create: Boolean, code: String?) = Unit
    override fun startOnlineQuick() = Unit
    override fun cancelOnline() = Unit
    override fun requestJoinRoom(code: String) = Unit
    override fun chooseMapOnline(file: String?) = Unit
    override fun startBtHost() = Unit
    override fun startBtScan() = Unit
    override fun cancelBtScan() = Unit
    override fun connectBtDevice(address: String) = Unit
    override fun startLanHost() = Unit
    override fun startLanDiscovery() = Unit
    override fun stopLanDiscovery() = Unit
    override fun connectLanHost(address: String) = Unit
    override fun respondJoin(accept: Boolean) = Unit
    override fun dismissBtError() = Unit

    override fun requestExit() = viewModel.requestExit()
    override fun restartBattle() = viewModel.restartBattle()
    override fun togglePause() = viewModel.togglePause()
    override fun dismissExitDialog() = viewModel.dismissExitDialog()
    override fun discardArcadeSession() = viewModel.discardArcadeSession()
    override fun resumeArcadeSession() = viewModel.resumeArcadeSession()
    override fun arcadeContinue() = viewModel.arcadeContinue()
    override fun arcadeRetry() = viewModel.arcadeRetry()
    override fun arcadeExit() = viewModel.arcadeExit()
}
