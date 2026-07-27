package ovh.gabrielhuav.pow.features.streetfighter.data

import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.data.json.jsonOf

import kotlinx.serialization.Serializable

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.runBlocking
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

// ⚠️ TODOS los campos con DEFAULT a proposito (Fase 3): kotlinx.serialization LANZA
// EXCEPCION si el JSON no trae un campo sin default, mientras que Gson lo dejaba en
// null/0. Como esto llega de la RED (o de assets), un emisor viejo o un mensaje
// incompleto CRASHEARIA la app en vez de degradar. No quites los defaults.
@Serializable
data class SfNetFireball(
    val x: Float = 0f,
    val y: Float = 0f,
    val dir: Int = 1,
    val strength: String = "",
    val state: String = "",          // SfFireballState.name
    val frame: Int = 0,
)

// 🍏 Fase 4: transporte por **Ktor** (antes OkHttp). Ktor es multiplataforma — el motor es OkHttp
// en Android y Darwin en iOS — así que esta clase ya puede viajar a `:shared` cuando se migre el
// modo pelea (Fase 5). El PROTOCOLO no cambia ni un byte.
class SfMatchClient : SfNetTransport {

    private val alcance = CoroutineScope(SupervisorJob())
    private val http = HttpClient {
        install(WebSockets) {
            // Mantiene vivo el WS (Render free duerme sin tráfico). Era `pingInterval(20 s)`.
            pingIntervalMillis = 20_000L
        }
    }
    private var ws: DefaultClientWebSocketSession? = null
    private var latido: Job? = null

    fun connect(wsUrl: String, listener: SfNetTransport.Listener) {
        alcance.launch {
            val r = runCatching {
                val s = http.webSocketSession(wsUrl)
                ws = s
                // HEARTBEAT cada 45 s: mantiene viva la SALA en fases sin tráfico
                // (la limpieza del server expira salas a los 5 min sin mensajes)
                latido = alcance.launch {
                    while (isActive) {
                        delay(45_000L)
                        send(mapOf("type" to "HEARTBEAT"))
                    }
                }
                listener.onOpen()
                s.incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        runCatching { PowJson.decodeFromString<SfNetMsg>(frame.readText()) }
                            .getOrNull()?.let { listener.onMessage(it) }
                    }
                }
            }
            latido?.cancel()
            ws = null
            if (r.isFailure) {
                listener.onFailure(r.exceptionOrNull()?.message ?: "conexión perdida")
            } else {
                listener.onClosed()
            }
        }
    }

    private fun send(payload: Map<String, Any?>) {
        val s = ws ?: return
        val texto = jsonOf(payload)
        alcance.launch { runCatching { s.send(Frame.Text(texto)) } }
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
        latido?.cancel()
        latido = null
        val s = ws
        ws = null
        alcance.launch { runCatching { s?.close() } }
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
            // 🍏 Fase 4: sondeo con Ktor (antes OkHttp). Sigue siendo BLOQUEANTE a propósito —
            // los call-sites lo llaman ya en un hilo de IO — así que se envuelve en runBlocking.
            val client = HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000L
                    requestTimeoutMillis = 10_000L
                }
            }
            val deadline = System.currentTimeMillis() + maxSeconds * 1000L
            var notified = false
            while (System.currentTimeMillis() < deadline) {
                val ok = runCatching {
                    runBlocking { client.get(statusUrl).status.isSuccess() }
                }.getOrDefault(false)
                if (ok) { client.close(); return true }
                if (!notified) {
                    notified = true
                    runCatching { onSleeping() }
                }
                Thread.sleep(4000)
            }
            client.close()
            return false
        }
    }
}
