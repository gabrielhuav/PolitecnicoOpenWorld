package ovh.gabrielhuav.pow.data.network

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

class WebSocketManager(private val serverUrl: String) {

    private var webSocket: WebSocket? = null

    // Cuando es true, los cierres de socket no emiten disconnectedFlow
    // (evita que disconnect() manual dispare una reconexión).
    @Volatile private var intentionalDisconnect = false

    private val client = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val _messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messagesFlow: SharedFlow<String> = _messagesFlow.asSharedFlow()

    // Emite Unit cada vez que la conexión se pierde de forma inesperada.
    // El ViewModel lo observa para programar una reconexión con backoff.
    private val _disconnectedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val disconnectedFlow: SharedFlow<Unit> = _disconnectedFlow.asSharedFlow()

    fun isConnected(): Boolean = webSocket != null

    fun connect() {
        if (webSocket != null) {
            Log.d("WebSocket", "Ya hay una conexión activa.")
            return
        }
        intentionalDisconnect = false
        Log.d("WebSocket", "Intentando conectar a: $serverUrl")

        val request = Request.Builder().url(serverUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "✅ Conexión ABIERTA exitosamente con $serverUrl")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                _messagesFlow.tryEmit(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d("WebSocket", "Bytes recibidos: ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "⚠️ Servidor solicita cierre: $code / $reason")
                webSocket.close(1000, null)
                this@WebSocketManager.webSocket = null
                if (!intentionalDisconnect) _disconnectedFlow.tryEmit(Unit)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "🔌 Conexión CERRADA: $code / $reason")
                this@WebSocketManager.webSocket = null
                if (!intentionalDisconnect) _disconnectedFlow.tryEmit(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "❌ Error CRÍTICO: ${t.message}")
                this@WebSocketManager.webSocket = null
                if (!intentionalDisconnect) _disconnectedFlow.tryEmit(Unit)
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message) ?: Log.w("WebSocket", "No se puede enviar, socket es null.")
    }

    fun disconnect() {
        intentionalDisconnect = true
        webSocket?.close(1000, "Cierre por el usuario")
        webSocket = null
    }
}