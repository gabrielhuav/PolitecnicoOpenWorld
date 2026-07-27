package ovh.gabrielhuav.pow.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.auth.AuthSession

/**
 * 🍏 WebSocket del multijugador, ahora con **Ktor** en vez de OkHttp (Fase 4 de
 * `PLAN_MIGRACION_KMP.md`). Vive en `:shared`: el motor es OkHttp en Android y Darwin en iOS.
 *
 * ⚠️ **LA API PÚBLICA ES LA MISMA A PROPÓSITO** (`connect`/`sendMessage`/`disconnect`/
 * `isConnected`/`messagesFlow`): así los dos call-sites (mundo abierto y zombis) no se tocaron.
 *
 * ⚠️ **AJUSTES QUE NO SON DECORATIVOS Y VIENEN DEL CÓDIGO ANTERIOR** — se conservan porque
 * arreglaban problemas reales de red móvil:
 * - **Sin timeout de lectura/escritura.** OkHttp los tenía a 0 ("infinito") con el comentario
 *   *"Evita que Android cierre el socket si no hay tráfico"*. En Ktor eso es NO instalar
 *   `HttpTimeout` sobre el socket: si se pone, las partidas se caen en los ratos sin mensajes.
 * - **Ping cada 25 s.** Mantiene viva la conexión (Render duerme los servicios gratis sin
 *   tráfico y las NAT móviles cierran sockets ociosos).
 * - **Cabeceras de Firebase en el handshake.** El servidor verifica el token ANTES de aceptar
 *   (`verifyClient`). Sin sesión se conecta sin cabecera = modo anónimo, que el servidor en
 *   modo "suave" permite.
 */
class WebSocketManager(private val serverUrl: String) {

    private val alcance = CoroutineScope(SupervisorJob())
    private var sesion: io.ktor.websocket.WebSocketSession? = null
    private var trabajo: Job? = null

    private val cliente = HttpClient {
        install(WebSockets) {
            // Latido del cliente: el equivalente al `pingInterval(25, SECONDS)` de OkHttp.
            pingIntervalMillis = 25_000L
        }
        // OJO: NO se instala HttpTimeout — ver la nota de arriba sobre los timeouts a 0.
    }

    private val _messagesFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messagesFlow: kotlinx.coroutines.flow.SharedFlow<String> = _messagesFlow

    fun isConnected(): Boolean = sesion != null

    fun connect() {
        if (sesion != null) return // ya hay conexión activa
        trabajo = alcance.launch {
            runCatching {
                val s = cliente.webSocketSession(serverUrl) {
                    AuthSession.idToken?.let { header("Authorization", "Bearer $it") }
                    AuthSession.uid?.let { header("X-Player-Uid", it) }
                }
                sesion = s
                s.incoming.consumeEach { frame ->
                    if (frame is Frame.Text) _messagesFlow.tryEmit(frame.readText())
                }
            }
            // Al salir del bucle (cierre o fallo) la sesión deja de ser válida. Se refleja igual
            // que hacía el listener de OkHttp: dejando el socket en null para que se pueda
            // reconectar. El reintento lo decide el ViewModel, como antes.
            sesion = null
        }
    }

    fun sendMessage(message: String) {
        val s = sesion ?: return
        alcance.launch { runCatching { s.send(Frame.Text(message)) } }
    }

    fun disconnect() {
        val s = sesion
        sesion = null
        trabajo?.cancel()
        alcance.launch { runCatching { s?.close() } }
    }
}
