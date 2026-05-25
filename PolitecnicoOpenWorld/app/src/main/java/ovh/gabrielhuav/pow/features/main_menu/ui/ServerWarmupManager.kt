package ovh.gabrielhuav.pow.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Despierta servidores hospedados en el plan gratuito de Render (que se
 * suspenden tras minutos de inactividad y tardan hasta ~50 s en arrancar).
 *
 * Estrategia: hacer GET corto a /status con timeouts agresivos y reintentar
 * hasta que responda 200 OK o se agote el presupuesto total. Cachea el último
 * éxito para evitar pings redundantes cuando el server ya está caliente.
 *
 * Uso típico (desde un ViewModel):
 *
 *     val ok = ServerWarmupManager.warmup(
 *         wsUrl = BuildConfig.MULTIPLAYER_SERVER_URL,
 *         onProgress = { seconds -> _state.update { it.copy(warmupSeconds = seconds) } }
 *     )
 *     if (ok is WarmupResult.Ready) { /* dejar conectar */ }
 */
object ServerWarmupManager {

    private const val TAG = "ServerWarmup"

    // El server se considera caliente durante esta ventana después de un OK,
    // así que dentro de ese plazo no se vuelve a pingear.
    private const val WARM_CACHE_MS = 60_000L

    // Presupuesto total: Render free tarda hasta ~50 s en arrancar.
    private const val MAX_WAIT_MS = 60_000L

    // Cada intento espera como máximo esto. Si el server está dormido,
    // el read suele tirar SocketTimeoutException muy rápido.
    private const val PER_REQUEST_CONNECT_TIMEOUT_MS = 5_000
    private const val PER_REQUEST_READ_TIMEOUT_MS    = 5_000

    // Pausa entre intentos cuando todavía no responde.
    private const val POLL_INTERVAL_MS = 2_000L

    // Mapa: healthUrl -> última vez (ms) que respondió 200 OK
    private val lastSuccessByUrl = HashMap<String, Long>()

    sealed class WarmupResult {
        /** El server respondió antes del límite (o ya estaba caliente). */
        data object Ready : WarmupResult()
        /** Se agotó MAX_WAIT_MS sin respuesta. */
        data object Timeout : WarmupResult()
        /** El caller canceló la coroutine. */
        data object Cancelled : WarmupResult()
    }

    /**
     * Convierte una URL de WebSocket en su correspondiente endpoint HTTP de
     * health-check. Ejemplo:
     *   wss://politecnicoopenworld.onrender.com  →  https://politecnicoopenworld.onrender.com/status
     */
    fun healthUrlFromWs(wsUrl: String): String {
        val http = wsUrl
            .replace("wss://", "https://", ignoreCase = true)
            .replace("ws://",  "http://",  ignoreCase = true)
            .trimEnd('/')
        return "$http/status"
    }

    /**
     * Calienta el servidor cuya WS está en [wsUrl]. Bloquea hasta que
     * /status responda 200 OK, hasta que se agote el presupuesto, o hasta
     * que la coroutine sea cancelada.
     *
     * @param onProgress se invoca aproximadamente cada segundo con los
     * segundos transcurridos, para refrescar un spinner / contador en la UI.
     */
    suspend fun warmup(
        wsUrl: String,
        onProgress: ((secondsElapsed: Int) -> Unit)? = null
    ): WarmupResult = withContext(Dispatchers.IO) {
        val healthUrl = healthUrlFromWs(wsUrl)
        val now = System.currentTimeMillis()

        // Caché: si pingeamos OK hace muy poco, ahórrate la ida al server.
        val lastOk = lastSuccessByUrl[healthUrl]
        if (lastOk != null && now - lastOk < WARM_CACHE_MS) {
            Log.d(TAG, "Server caliente por caché ($healthUrl)")
            return@withContext WarmupResult.Ready
        }

        Log.d(TAG, "Iniciando warm-up de $healthUrl")
        val start = System.currentTimeMillis()
        var attempt = 0

        while (coroutineContext.isActive) {
            attempt++
            val elapsed = System.currentTimeMillis() - start
            onProgress?.invoke((elapsed / 1000).toInt())

            if (elapsed > MAX_WAIT_MS) {
                Log.w(TAG, "Warm-up timeout tras ${elapsed}ms y $attempt intentos")
                return@withContext WarmupResult.Timeout
            }

            if (ping(healthUrl)) {
                Log.d(TAG, "Server vivo en intento $attempt (${elapsed}ms)")
                lastSuccessByUrl[healthUrl] = System.currentTimeMillis()
                onProgress?.invoke((elapsed / 1000).toInt())
                return@withContext WarmupResult.Ready
            }

            // Pausa antes del próximo intento; cooperativa con la cancelación.
            try {
                kotlinx.coroutines.delay(POLL_INTERVAL_MS)
            } catch (_: kotlinx.coroutines.CancellationException) {
                return@withContext WarmupResult.Cancelled
            }
        }
        WarmupResult.Cancelled
    }

    /**
     * Un solo intento contra /status. Retorna true sólo si el server contestó
     * 200 OK dentro de los timeouts. Cualquier otra cosa (timeout, 5xx, red
     * caída) se trata como "todavía no está listo".
     */
    private fun ping(healthUrl: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(healthUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = PER_REQUEST_CONNECT_TIMEOUT_MS
                readTimeout    = PER_REQUEST_READ_TIMEOUT_MS
                // No queremos que Android cachee esta respuesta ni reuse keep-alive
                // de una conexión a un server que probablemente esté arrancando.
                useCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "PolitecnicoOpenWorld/1.0 (warmup)")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                // Drenamos el body para que el socket se libere limpio.
                conn.inputStream.use { it.readBytes() }
                true
            } else {
                Log.d(TAG, "ping → HTTP $code (aún no listo)")
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "ping fallido: ${e.javaClass.simpleName}: ${e.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    /** Invalida la caché manualmente. Útil al desconectar o cambiar de server. */
    fun invalidate(wsUrl: String) {
        lastSuccessByUrl.remove(healthUrlFromWs(wsUrl))
    }
}
