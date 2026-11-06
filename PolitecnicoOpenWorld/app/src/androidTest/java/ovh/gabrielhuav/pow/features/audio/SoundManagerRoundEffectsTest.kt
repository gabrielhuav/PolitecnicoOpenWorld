package ovh.gabrielhuav.pow.features.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundManagerRoundEffectsTest {
    @Test(timeout = 30_000L)
    fun coldRoundEffectsWaitForLoadingAndStartRealStreams() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            // SoundPool entrega su callback en este Looper. No cederlo entre constructor
            // y playStartRound fuerza la espera de carga real, sin Activity ni pausas.
            val soundManager = SoundManager.getInstance(context)
            try {
                assertTrue("El inicio debe esperar la carga y obtener un stream", soundManager.playStartRound())
                soundManager.stopRoundSounds()
                assertTrue("Victoria debe obtener un stream válido", soundManager.playVictory())
                soundManager.stopRoundSounds()
                assertTrue("Derrota debe obtener un stream válido", soundManager.playLose())
            } finally {
                soundManager.stopRoundSounds()
                soundManager.release()
            }
        }
    }
}
