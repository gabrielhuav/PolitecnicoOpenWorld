package ovh.gabrielhuav.pow.features.audio

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotSoundLoadGateTest {
    @Test
    fun `a cold sample waits until its decode completes`() = runBlocking {
        val gate = OneShotSoundLoadGate()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitReady(7) }
        assertFalse(waiting.isCompleted)

        gate.onLoadComplete(8, 0)
        yield()
        assertFalse(waiting.isCompleted)
        gate.onLoadComplete(7, 0)

        assertTrue(waiting.await())
    }

    @Test
    fun `an already loaded sample is ready on the first request`() = runBlocking {
        val gate = OneShotSoundLoadGate()
        gate.onLoadComplete(7, 0)
        assertTrue(gate.awaitReady(7))
    }

    @Test
    fun `a failed decode ends the wait without playing`() = runBlocking {
        val gate = OneShotSoundLoadGate()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitReady(7) }
        gate.onLoadComplete(7, -1)
        assertFalse(waiting.await())
    }

    @Test
    fun `leaving the screen cancels a pending cue even if loading later succeeds`() = runBlocking {
        val gate = OneShotSoundLoadGate()
        var played = false
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            if (gate.awaitReady(7)) played = true
        }
        waiting.cancelAndJoin()
        gate.onLoadComplete(7, 0)
        assertFalse(played)
    }

    @Test
    fun `releasing the pool ends pending requests without replay`() = runBlocking {
        val gate = OneShotSoundLoadGate()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) { gate.awaitReady(7) }
        gate.release()
        assertFalse(waiting.await())
        gate.onLoadComplete(7, 0)
        assertFalse(gate.awaitReady(7))
    }

    @Test
    fun `invalid sample ids never wait for a callback`() = runBlocking {
        val gate = OneShotSoundLoadGate()
        assertFalse(gate.awaitReady(0))
        assertFalse(gate.awaitReady(-1))
    }
}
