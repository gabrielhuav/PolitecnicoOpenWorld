package ovh.gabrielhuav.pow.features.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/** Espera cancelable: un id devuelto por SoundPool.load todavía puede estar decodificándose. */
internal class OneShotSoundLoadGate {
    private data class Loads(val results: Map<Int, Boolean> = emptyMap(), val released: Boolean = false)

    private val loads = MutableStateFlow(Loads())

    fun onLoadComplete(sampleId: Int, status: Int) {
        loads.update { current ->
            if (current.released) current else current.copy(results = current.results + (sampleId to (status == 0)))
        }
    }

    suspend fun awaitReady(sampleId: Int): Boolean {
        if (sampleId <= 0) return false
        return withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            val result = loads.first { it.released || sampleId in it.results }
            !result.released && result.results[sampleId] == true
        } ?: false
    }

    fun release() {
        loads.value = Loads(released = true)
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
    }
}
