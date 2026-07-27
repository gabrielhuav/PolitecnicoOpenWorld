package ovh.gabrielhuav.pow.features.streetfighter.data

import kotlinx.serialization.Serializable

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
@Serializable
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
    // 🆕 (2026-07-25) GRADO de la ronda (ROUND_ENDED): SfRoundOutcome.name (PERFECT/COMBO/SUPER/TIME).
    val outcome: String? = null,
    // Estado del peleador (PLAYER_STATE / OPPONENT_STATE)
    val x: Float? = null,
    val y: Float? = null,
    val state: String? = null,       // SfFighterState.name
    val frame: Int? = null,
    val dir: Int? = null,            // +1 derecha / -1 izquierda
    val hp: Int? = null,
    // 🆕 (2026-07-21) MEDIDOR DE SÚPER del rival (0..SUPER_METER_MAX). Sin esto la barra
    // dorada del oponente se veía siempre vacía en línea. Es OPCIONAL: un cliente viejo no
    // lo manda y el receptor conserva el valor que ya tenía.
    val meter: Int? = null,
    // 🆕 (2026-07-26) AUDIO SINCRONIZADO: claves de los clips de VOZ que el emisor acaba de
    // reproducir, para que su rival oiga EXACTAMENTE lo mismo (antes cada quien solo oía a su
    // propio peleador). Solo viajan las voces de los packs, porque se eligen con `.random()`:
    // si cada teléfono sorteara por su cuenta, ambos oirían un clip DISTINTO del mismo evento.
    // Los SFX DETERMINISTAS (whoosh del golpe, aterrizaje, impacto) NO viajan — el receptor los
    // deriva del `state` del snapshot, que ya viene. Opcional: un cliente viejo no lo manda.
    val audio: List<String>? = null,
    // 🆕 SINCRONÍA DEL TIMER: solo lo manda el HOST (autoridad del reloj); el invitado lo adopta
    val timer: Int? = null,
    val fireballs: List<SfNetFireball>? = null,
    // Daño (PLAYER_DAMAGE)
    val damage: Int? = null,
    val strength: String? = null,    // SfAttackStrength.name
    val atkType: String? = null,     // SfAttackType.name
    // 🆕 (2026-07-26) SEÑALIZACIÓN WebRTC (SIGNAL_OFFER / SIGNAL_ANSWER / SIGNAL_ICE). El
    // servidor los reenvía CIEGO al otro jugador de la sala: solo hace de cupido para que los
    // dos teléfonos abran una conexión DIRECTA. Nada de esto toca el gameplay — si la conexión
    // directa no se logra, la pelea sigue por el relay de siempre.
    val sdp: String? = null,             // oferta/respuesta (SDP)
    val candidate: String? = null,       // candidato ICE
    val sdpMid: String? = null,          // pista a la que pertenece el candidato
    val sdpMLineIndex: Int? = null,      // índice de la línea m= del candidato
    // Sala pública + resumen de partidas
    val position: Int? = null,       // QUEUED: lugar en la lista de espera
    val rooms: List<SfRoomSummary>? = null,
    val queue: Int? = null,          // jugadores esperando sala pública
)

@Serializable
data class SfRoomSummary(val code: String = "", val players: Int = 0, val phase: String = "")

@Serializable
data class SfNetFireball(
    val x: Float,
    val y: Float,
    val dir: Int,
    val strength: String,
    val state: String,               // SfFireballState.name
    val frame: Int,
)

class SfMatchClient(private val gson: Gson = Gson()) : SfNetTransport {

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS) // mantiene vivo el WS (Render free duerme sin tráfico)
        .build()
    private var ws: WebSocket? = null
    private var heartbeatTimer: Timer? = null

    fun connect(wsUrl: String, listener: SfNetTransport.Listener) {
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

    /**
     * 🆕 (2026-07-26) Manda un mensaje de SEÑALIZACIÓN WebRTC al rival de la sala (el server lo
     * reenvía ciego). No es parte de [SfNetTransport] a propósito: solo el transporte ONLINE
     * hace de canal de señalización — en BT/LAN los teléfonos ya están conectados directo.
     */
    fun sendSignal(
        type: String,
        sdp: String? = null,
        candidate: String? = null,
        sdpMid: String? = null,
        sdpMLineIndex: Int? = null,
    ) = send(
        mapOf(
            "type" to type, "sdp" to sdp, "candidate" to candidate,
            "sdpMid" to sdpMid, "sdpMLineIndex" to sdpMLineIndex,
        ),
    )

    override fun createRoom() = send(mapOf("type" to "CREATE_ROOM"))
    override fun joinRoom(code: String) = send(mapOf("type" to "JOIN_ROOM", "code" to code.uppercase()))
    override fun quickMatch() = send(mapOf("type" to "QUICK_MATCH"))
    override fun cancelQueue() = send(mapOf("type" to "CANCEL_QUEUE"))
    override fun listRooms() = send(mapOf("type" to "LIST_ROOMS"))
    override fun leaveRoom() = send(mapOf("type" to "LEAVE_ROOM"))

    // Lobby con APROBACIÓN (estilo AoE2): pedir unirse / responder como host
    override fun requestJoin(code: String) = send(mapOf("type" to "REQUEST_JOIN", "code" to code.uppercase()))
    override fun respondJoin(accept: Boolean) = send(mapOf("type" to "RESPOND_JOIN", "accept" to accept))

    override fun selectCharacter(name: String) = send(mapOf("type" to "SELECT_CHARACTER", "character" to name))
    override fun selectMap(file: String) = send(mapOf("type" to "SELECT_MAP", "map" to file))
    override fun requestRematch() = send(mapOf("type" to "REQUEST_REMATCH"))
    override fun sendReady() = send(mapOf("type" to "PLAYER_READY"))
    override fun sendRoundEnded(winner: String, outcome: String) =
        send(mapOf("type" to "ROUND_ENDED", "winner" to winner, "outcome" to outcome))
    override fun sendMatchEnded(winner: String) = send(mapOf("type" to "MATCH_ENDED", "winner" to winner))

    override fun sendDamage(damage: Int, strength: String, atkType: String) =
        send(mapOf("type" to "PLAYER_DAMAGE", "damage" to damage, "strength" to strength, "atkType" to atkType))

    @Suppress("LongParameterList")
    override fun sendPlayerState(
        x: Float,
        y: Float,
        state: String,
        frame: Int,
        dir: Int,
        hp: Int,
        timer: Int?,
        fireballs: List<SfNetFireball>,
        meter: Int,
        audio: List<String>,
    ) =
        send(
            mapOf(
                "type" to "PLAYER_STATE", "x" to x, "y" to y, "state" to state,
                "frame" to frame, "dir" to dir, "hp" to hp, "timer" to timer,
                "fireballs" to fireballs, "meter" to meter,
                // Vacío → null → `send` lo filtra: no engorda el snapshot de ~15 Hz.
                // El relay de Render lo reenvía solo (hace `{...msg}`), sin redeploy.
                "audio" to audio.ifEmpty { null },
            ),
        )

    override fun close() {
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
        fun warmupBlocking(
            wsUrl: String,
            maxSeconds: Int = 90,
            /**
             * 🆕 (2026-07-26) Se llama UNA vez si el primer sondeo falla, o sea si el servicio
             * estaba DORMIDO y toca esperar a que levante. Sirve para explicarle la espera al
             * jugador. Si ya estaba despierto NO se llama: el primer sondeo acierta y no hay
             * nada que avisar.
             */
            onSleeping: () -> Unit = {},
        ): Boolean {
            val statusUrl = wsUrl.replace("wss://", "https://").replace("ws://", "http://")
                .trimEnd('/') + "/status"
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val deadline = System.currentTimeMillis() + maxSeconds * 1000L
            var notified = false
            while (System.currentTimeMillis() < deadline) {
                val ok = runCatching {
                    client.newCall(Request.Builder().url(statusUrl).build()).execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (ok) return true
                if (!notified) {
                    notified = true
                    runCatching { onSleeping() }
                }
                Thread.sleep(4000)
            }
            return false
        }
    }
}
