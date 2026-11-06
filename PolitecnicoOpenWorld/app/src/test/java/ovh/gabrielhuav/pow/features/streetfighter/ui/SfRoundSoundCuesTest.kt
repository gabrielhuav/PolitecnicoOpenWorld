package ovh.gabrielhuav.pow.features.streetfighter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
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
    fun `restarting the first round through its intro allows the same cue again`() {
        val first = sfRoundSoundCue(active, null) as SfRoundSoundCue.Start
        assertEquals(SfRoundSoundCue.None, sfRoundSoundCue(active, first.key))

        val restartIntro = active.copy(showRoundIntro = true, roundIntroCountdown = 3)
        assertEquals(SfRoundSoundCue.Reset, sfRoundSoundCue(restartIntro, first.key))
        // La pantalla limpia playedRoundKey al recibir Reset; los datos de ronda siguen en 1/0/0.
        assertEquals(first, sfRoundSoundCue(active, null))
    }

    @Test
    fun `pausing and resuming a played round do not reset its cue`() {
        val playedKey = (sfRoundSoundCue(active, null) as SfRoundSoundCue.Start).key
        assertEquals(SfRoundSoundCue.None, sfRoundSoundCue(active.copy(isPaused = true), playedKey))
        assertEquals(SfRoundSoundCue.None, sfRoundSoundCue(active, playedKey))
    }

    @Test
    fun `restoring the saved played key during a round does not replay its cue`() {
        val savedKey = (sfRoundSoundCue(active, null) as SfRoundSoundCue.Start).key
        val restoredState = active.copy(gameTimeMs = 900, displayTime = 89)
        assertEquals(SfRoundSoundCue.None, sfRoundSoundCue(restoredState, savedKey))
    }

    @Test
    fun `boss metamorphosis does not announce a second start during round two`() {
        val before = active.copy(
            roundNumber = 2,
            playerRoundWins = 1,
            player = active.player.copy(id = SfFighterId.LA_PRESIDENTA),
            cpu = active.cpu.copy(id = SfFighterId.YOALLI_EHECATL),
        )
        val playedKey = (sfRoundSoundCue(before, null) as SfRoundSoundCue.Start).key
        val transformed = before.copy(
            player = before.player.copy(id = SfFighterId.YOALLI_EHECATL),
            cpu = before.cpu.copy(id = SfFighterId.LA_PRESIDENTA),
        )
        assertEquals(playedKey, sfRoundSoundKey(transformed))
        assertEquals(SfRoundSoundCue.None, sfRoundSoundCue(transformed, playedKey))
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
    fun `online winner indices are already local for both guest and host`() {
        // VM.networkIndexOf("p2") devuelve 0 para el invitado; "p1" devuelve 1.
        val guest = active.copy(showEndMenu = true, onlineStatus = SfOnlineStatus.FIGHTING, isHost = false)
        assertEquals(SfMatchSound.VICTORY, sfMatchSound(guest.copy(winnerIndex = 0)))
        assertEquals(SfMatchSound.LOSE, sfMatchSound(guest.copy(winnerIndex = 1)))
        // En el host, "p1" es 0 y "p2" es 1: el contrato local sigue siendo el mismo.
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
