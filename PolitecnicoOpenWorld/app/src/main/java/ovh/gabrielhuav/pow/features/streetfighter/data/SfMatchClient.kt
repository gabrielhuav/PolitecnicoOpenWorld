package ovh.gabrielhuav.pow.features.streetfighter.data

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit

// Cliente WebSocket del MULTIJUGADOR 1v1 del modo pelea (servidor MultiplayerSF/ en Render).
// RELAY PURO: cada cliente simula a SU peleador; aquí solo viajan mensajes JSON.
// El VM registra un Listener; los callbacks llegan en el hilo de OkHttp (el VM decide el hilo).

/** Mensaje de red laxo (Gson: campos ausentes → null), cliente ⇄ servidor. */
data class SfNetMsg(
    val type: String? = null,
    val code: String? = null,
    val playerIndex: Int? = null,
    val character: String? = null,
    val char1: String? = null,
    val char2: String? = null,
    val map: String? = null,
    val countdownMs: Int? = null,
    val message: String? = null,
    val winner: String? = null,
    // Estado del peleador (PLAYER_STATE / OPPONENT_STATE)
    val x: Float? = null,
    val y: Float? = null,
    val state: String? = null,       // SfFighterState.name
    val frame: Int? = null,
    val dir: Int? = null,            // +1 derecha / -1 izquierda
    val hp: Int? = null,
    val fireballs: List<SfNetFireball>? = null,
    // Daño (PLAYER_DAMAGE)
    val damage: Int? = null,
    val strength: String? = null,    // SfAttackStrength.name
    val atkType: String? = null,     // SfAttackType.name
    // Sala pública + resumen de partidas
    val position: Int? = null,       // QUEUED: lugar en la lista de espera
    val rooms: List<SfRoomSummary>? = null,
    val queue: Int? = null,          // jugadores esperando sala pública
)

data class SfRoomSummary(val code: String, val players: Int, val phase: String)

data class SfNetFireball(
    val x: Float,
    val y: Float,
    val dir: Int,
    val strength: String,
    val state: String,               // SfFireballState.name
    val frame: Int,
)

class SfMatchClient(private val gson: Gson = Gson()) {

    interface Listener {
        fun onOpen()
        fun onMessage(msg: SfNetMsg)
        fun onClosed()
        fun onFailure(reason: String)
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS) // mantiene vivo el WS (Render free duerme sin tráfico)
        .build()
    private var ws: WebSocket? = null
    private var heartbeatTimer: Timer? = null

    fun connect(wsUrl: String, listener: Listener) {
        val request = Request.Builder().url(wsUrl).build()
        ws = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // HEARTBEAT cada 45 s: mantiene viva la SALA en fases sin tráfico
                // (la limpieza del server expira salas a los 5 min sin mensajes)
                heartbeatTimer = Timer(true).also { t ->
                    t.schedule(object : TimerTask() {
                        override fun run() { send(mapOf("type" to "HEARTBEAT")) }
                    }, 45_000L, 45_000L)
                }
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { gson.fromJson(text, SfNetMsg::class.java) }
                    .getOrNull()?.let { listener.onMessage(it) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t.message ?: "conexión perdida")
            }
        })
    }

    private fun send(payload: Map<String, Any?>) {
        ws?.send(gson.toJson(payload.filterValues { it != null }))
    }

    fun createRoom() = send(mapOf("type" to "CREATE_ROOM"))
    fun joinRoom(code: String) = send(mapOf("type" to "JOIN_ROOM", "code" to code.uppercase()))
    fun quickMatch() = send(mapOf("type" to "QUICK_MATCH"))
    fun cancelQueue() = send(mapOf("type" to "CANCEL_QUEUE"))
    fun listRooms() = send(mapOf("type" to "LIST_ROOMS"))
    fun leaveRoom() = send(mapOf("type" to "LEAVE_ROOM"))
    fun selectCharacter(name: String) = send(mapOf("type" to "SELECT_CHARACTER", "character" to name))
    fun selectMap(file: String) = send(mapOf("type" to "SELECT_MAP", "map" to file))
    fun requestRematch() = send(mapOf("type" to "REQUEST_REMATCH"))
    fun sendMatchEnded(winner: String) = send(mapOf("type" to "MATCH_ENDED", "winner" to winner))

    fun sendDamage(damage: Int, strength: String, atkType: String) =
        send(mapOf("type" to "PLAYER_DAMAGE", "damage" to damage, "strength" to strength, "atkType" to atkType))

    fun sendPlayerState(x: Float, y: Float, state: String, frame: Int, dir: Int, hp: Int, fireballs: List<SfNetFireball>) =
        send(
            mapOf(
                "type" to "PLAYER_STATE", "x" to x, "y" to y, "state" to state,
                "frame" to frame, "dir" to dir, "hp" to hp, "fireballs" to fireballs,
            ),
        )

    fun close() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        ws?.close(1000, "bye")
        ws = null
    }

    companion object {
        /**
         * Despierta el servicio FREE de Render (duerme tras ~15 min sin tráfico y tarda
         * hasta ~1 min en levantar): GET /status con reintentos, BLOQUEANTE (llamar en IO).
         */
        fun warmupBlocking(wsUrl: String, maxSeconds: Int = 90): Boolean {
            val statusUrl = wsUrl.replace("wss://", "https://").replace("ws://", "http://")
                .trimEnd('/') + "/status"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val deadline = System.currentTimeMillis() + maxSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                val ok = runCatching {
                    client.newCall(Request.Builder().url(statusUrl).build()).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (ok) return true
                Thread.sleep(4000)
            }
            return false
        }
    }
}
