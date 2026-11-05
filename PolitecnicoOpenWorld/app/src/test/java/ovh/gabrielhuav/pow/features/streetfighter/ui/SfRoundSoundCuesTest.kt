package ovh.gabrielhuav.pow.features.streetfighter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState

class SfRoundSoundCuesTest {
    private val active = StreetFighterState(inCharacterSelect = false)

    @Test
    fun `the initial character selector is silent despite round number one`() {
        assertNull(sfRoundSoundKey(StreetFighterState()))
        assertNull(sfMatchSound(StreetFighterState()))
    }

    @Test
    fun `the first round cue waits until the intro has finished`() {
        assertNull(sfRoundSoundKey(active.copy(showRoundIntro = true, roundIntroCountdown = 3)))
        // selectCharacter publica showRoundIntro=true antes del primer tick que calcula la cuenta.
        assertNull(sfRoundSoundKey(active.copy(showRoundIntro = true, roundIntroCountdown = 0)))
        assertNotNull(sfRoundSoundKey(active))
    }

    @Test
    fun `later rounds have distinct keys while ordinary frame updates do not replay`() {
        val first = sfRoundSoundKey(active)
        assertEquals(first, sfRoundSoundKey(active.copy(gameTimeMs = 500, displayTime = 90)))
        assertNotEquals(first, sfRoundSoundKey(active.copy(roundNumber = 2, playerRoundWins = 1)))
    }

    @Test
    fun `paused waiting and ended rounds cannot announce fight`() {
        assertNull(sfRoundSoundKey(active.copy(isPaused = true)))
        assertNull(sfRoundSoundKey(active.copy(waitingForOpponentReady = true)))
        assertNull(sfRoundSoundKey(active.copy(battleEnded = true)))
        assertNull(sfRoundSoundKey(active.copy(showEndMenu = true)))
        assertNull(sfRoundSoundKey(active.copy(onlineStatus = SfOnlineStatus.WAITING_MAP)))
        assertNull(sfRoundSoundKey(active.copy(onlineStatus = SfOnlineStatus.COUNTDOWN)))
    }

    @Test
    fun `tutorial and showcase do not announce synthetic rounds`() {
        assertNull(sfRoundSoundKey(active.copy(tutorialActive = true)))
        assertNull(sfRoundSoundKey(active.copy(showcaseRunning = true)))
        assertNull(sfRoundSoundKey(active.copy(audioShowcaseRunning = true)))
    }

    @Test
    fun `winning one round is not a match victory`() {
        val roundEnd = active.copy(battleEnded = true, winnerIndex = 0, playerRoundWins = 1)
        assertNull(sfMatchSound(roundEnd))
        assertEquals(SfMatchSound.VICTORY, sfMatchSound(roundEnd.copy(showEndMenu = true, playerRoundWins = 2)))
        assertEquals(SfMatchSound.LOSE, sfMatchSound(roundEnd.copy(showEndMenu = true, winnerIndex = 1, cpuRoundWins = 2)))
    }

    @Test
    fun `online victory follows the guest or host local side`() {
        val guest = active.copy(showEndMenu = true, onlineStatus = SfOnlineStatus.FIGHTING, isHost = false)
        assertEquals(SfMatchSound.VICTORY, sfMatchSound(guest.copy(winnerIndex = 1)))
        assertEquals(SfMatchSound.LOSE, sfMatchSound(guest.copy(winnerIndex = 0)))
        val host = guest.copy(isHost = true)
        assertEquals(SfMatchSound.VICTORY, sfMatchSound(host.copy(winnerIndex = 0)))
        assertEquals(SfMatchSound.LOSE, sfMatchSound(host.copy(winnerIndex = 1)))
    }

    @Test
    fun `a missing winner or a menu state cannot announce a result`() {
        assertNull(sfMatchSound(active.copy(showEndMenu = true)))
        assertNull(sfMatchSound(active.copy(showEndMenu = true, winnerIndex = -1)))
        assertNull(sfMatchSound(active.copy(showEndMenu = true, winnerIndex = 2)))
        assertNull(sfMatchSound(StreetFighterState(showEndMenu = true, winnerIndex = 0)))
    }
}
