package ovh.gabrielhuav.pow.features.streetfighter.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState

/**
 * Contrato UI del modo pelea.
 *
 * La pantalla común no conoce Hilt, Context ni el transporte Android. Android lo adapta al
 * ViewModel existente; una implementación iOS puede reutilizar exactamente la misma pantalla.
 */
interface StreetFighterController {
    val state: StateFlow<StreetFighterState>
    val soundEvents: Flow<String>

    fun isLowEndDevice(): Boolean
    fun alphaFallbackId(id: SfFighterId): SfFighterId
    fun forcePause()
    fun hasArcadeSession(): Boolean
    fun playerHasNewMoves(): Boolean
    // 🆕 (2026-08-29) CONTRAATAQUE + DERRIBO CON PODER: gates POR MOVIMIENTO (mirror de
    // playerHasNewMoves, pero por hoja concreta) para no mostrar un botón muerto.
    fun playerHasCounterMove(): Boolean
    fun playerHasPowerThrowMove(): Boolean
    fun showHitboxes(): Boolean
    fun showSfFps(): Boolean
    fun isFighterActuallyUnlocked(id: SfFighterId): Boolean
    fun setAssetsLoadingUi(loading: Boolean)

    fun onJoystickMove(angleRad: Double)
    fun onJoystickRelease()
    fun onAttackPressed(strength: SfAttackStrength, type: SfAttackType)
    fun onKickPressed()
    fun onBonusPowerPressed()
    fun onParryPressed()
    fun onGrabPressed()
    fun onTauntPressed()
    fun onSuperArtPressed()
    fun onCounterPressed()
    fun onPowerThrowPressed()

    fun tutorialSkipLesson()
    fun tutorialRestartLesson()
    fun exitTutorial()
    fun startTutorial(id: SfFighterId)
    fun comboSheet(id: SfFighterId): List<Triple<String, String, List<String>>>

    fun backToCharacterSelect()
    fun stopGauntlet()
    fun goToPreviousShowcaseAnimation()
    fun skipToNextShowcaseAnimation()
    fun skipShowcaseFighter()
    fun cycleShowcaseSpeed()
    fun replayCurrentShowcaseAudio()
    fun dismissGauntletReport()
    fun startGauntletRoundRobin()
    fun startGauntletArcade()
    fun startShowcase()
    fun stopAudioShowcase()
    fun startAiVsAi(
        a: SfFighterId,
        b: SfFighterId,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.PESADILLA,
        intensity: Float = 1f,
    )

    fun selectableFighters(): List<SfFighterId>
    fun lockedFighters(): List<SfFighterId>
    fun unlockedMaps(): Set<String>
    fun devUnlockAll(): Boolean
    fun selectCharacter(
        id: SfFighterId,
        rivalId: SfFighterId? = null,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,
    )
    fun startArcade(
        playerId: SfFighterId,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,
    )

    fun startOnline(create: Boolean, code: String? = null)
    fun startOnlineQuick()
    fun cancelOnline()
    fun requestJoinRoom(code: String)
    fun chooseMapOnline(file: String?)
    fun startBtHost()
    fun startBtScan()
    fun cancelBtScan()
    fun connectBtDevice(address: String)
    fun startLanHost()
    fun startLanDiscovery()
    fun stopLanDiscovery()
    fun connectLanHost(address: String)
    fun respondJoin(accept: Boolean)
    fun dismissBtError()

    fun requestExit()
    fun restartBattle()
    fun togglePause()
    fun dismissExitDialog()
    fun discardArcadeSession()
    fun resumeArcadeSession(): Boolean
    fun arcadeContinue()
    fun arcadeRetry()
    fun arcadeExit()
}
