package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash

/**
 * Vista común e inmutable del estado que consume el renderer.
 *
 * Evita que el Canvas multiplataforma dependa de los campos online/Android del ViewModel.
 */
interface SfSceneState {
    val player: SfFighter
    val cpu: SfFighter
    val fireballs: List<SfFireball>
    val splashes: List<SfHitSplash>
    val cameraX: Float
    val cameraY: Float
    val displayTime: Int
    val timeFlashing: Boolean
    val playerScore: Int
    val cpuScore: Int
    val battleEnded: Boolean
    val winnerIndex: Int?
    val koFlash: Boolean
    val displayHp0: Float
    val displayHp1: Float
    val comboCount: Int
    val comboPlayerId: Int
    val playerRoundWins: Int
    val cpuRoundWins: Int
    val showRoundIntro: Boolean
    val roundIntroCountdown: Int
    val roundResultLabel: String
    val roundResultWinnerIdx: Int
    val gameTimeMs: Long
    val specialSubtitleHud: String?
    val specialSubtitleUntilMs: Long
    val specialSubtitleStartMs: Long
}
