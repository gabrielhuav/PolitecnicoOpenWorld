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

    // Usamos Dispatcher por defecto pero aseguramos que acepte conexiones Cleartext
    private val client = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS))
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val _messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messagesFlow: SharedFlow<String> = _messagesFlow.asSharedFlow()

    fun connect() {
        if (webSocket != null) {
            Log.d("WebSocket", "Ya hay una conexión activa.")
            return
        }

        Log.d("WebSocket", "Intentando conectar a: $serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

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
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "🔌 Conexión CERRADA: $code / $reason")
                this@WebSocketManager.webSocket = null
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "❌ Error CRÍTICO: ${t.message}")
                this@WebSocketManager.webSocket = null
            }
        })
    }

    fun sendMessage(message: String) {
        webSocket?.send(message) ?: Log.w("WebSocket", "No se puede enviar, socket es null.")
    }

    fun disconnect() {
        webSocket?.close(1000, "Cierre por el usuario")
        webSocket = null
    }
}