package ovh.gabrielhuav.pow.features.streetfighter.data

import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Timer
import java.util.TimerTask

// BASE COMÚN de los transportes LOCALES "de stream" del modo pelea "HUELUM VS. GOYA":
// Bluetooth RFCOMM (SfBtClient) y Servidor LAN por Wi-Fi (SfLanClient). Extraída el
// 2026-07-16 para no duplicar la lógica (regla anti-gemelos del 09): las subclases solo
// saben CONECTAR (accept/connect de su tipo de socket) y cerrar; todo lo demás vive aquí.
//
// MISMA arquitectura que el online (AUDIT_SF_MULTIPLAYER.md §3): relay puro con autoridad
// del RECEPTOR — cada teléfono simula a SU peleador y viajan los MISMOS mensajes JSON
// (SfNetMsg) que con MultiplayerSF/. El HOST hace de "server" local: agrega la selección/
// revancha del invitado y GENERA los mensajes que en online manda el relay (OPPONENT_JOINED,
// CHARACTERS_SELECTED, MAP_SELECTED, FIGHT_START tras el countdown, REMATCH_ACCEPTED).
//
// CONEXIÓN VERIFICADA (handshake): el invitado manda BT_HELLO al conectar y el host contesta
// BT_WELCOME (nombres históricos del transporte BT; aplican igual en LAN) — ROOM_JOINED/
// OPPONENT_JOINED solo se entregan con datos fluyendo en AMBOS sentidos. KEEPALIVE de 10 s:
// si una escritura falla se cierra el socket para destrabar el readLoop YA (abandono en
// segundos). SIN foreground service: la conexión vive con la Activity (close() al salir).

abstract class SfStreamPeer(
    protected val gson: Gson = Gson(),
) : SfNetTransport {

    protected var listener: SfNetTransport.Listener? = null
    @Volatile protected var running = false
    protected var isHostRole = false

    /** Código de "sala" simbólico para reusar el flujo del VM ("BT" / "LAN"). */
    protected abstract val roomCode: String

    /** Mensaje del invitado cuando el host nunca contestó el handshake (reintentable). */
    protected abstract val handshakeFailMessage: String

    /** Cierra SOLO el socket del peer actual (debe destrabar el readLoop). */
    protected abstract fun closePeerSocket()

    /** Cierra el resto del transporte (server socket, discovery…). */
    protected abstract fun closeTransport()

    @Volatile private var out: OutputStream? = null

    // ── "server" local del HOST (lo que en online guarda la sala del relay) ──
    @Volatile private var char1: String? = null
    @Volatile private var char2: String? = null
    @Volatile private var rematch1 = false
    @Volatile private var rematch2 = false
    @Volatile private var peerLostNotified = false
    @Volatile private var handshaken = false

    private var heartbeatTimer: Timer? = null

    // ══════════════════════ sesión con un peer conectado ══════════════════════

    /**
     * Corre la SESIÓN con un peer ya conectado (BLOQUEA hasta perderlo): resetea la "sala"
     * local (incl. char1 — un rival nuevo no debe disparar CHARACTERS_SELECTED viejo),
     * arranca el keepalive y, si somos INVITADO, inicia el handshake con su watchdog.
     * El host que la llama en loop puede volver a accept() al regresar.
     */
    protected fun runPeerSession(input: InputStream, output: OutputStream) {
        out = output
        peerLostNotified = false
        handshaken = false
        char1 = null
        char2 = null
        rematch1 = false
        rematch2 = false
        startHeartbeat()
        if (!isHostRole) {
            // Aviso de progreso a la UI ("verificando la conexión…") + HELLO del handshake
            deliver(SfNetMsg(type = "BT_HANDSHAKE"))
            sendRaw(mapOf("type" to "BT_HELLO"))
            startWelcomeWatchdog()
        }
        readLoop(input)
    }

    /** Si el WELCOME no llega a tiempo, se cierra el socket (conexión NO verificada). */
    private fun startWelcomeWatchdog() {
        Thread({
            runCatching { Thread.sleep(HANDSHAKE_TIMEOUT_MS) }
            if (running && !handshaken) {
                runCatching { closePeerSocket() } // destraba el readLoop → onFailure reintentable
            }
        }, "SfPeerWelcome").start()
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer(true).also { t ->
            t.schedule(
                object : TimerTask() {
                    override fun run() { sendRaw(mapOf("type" to "HEARTBEAT")) }
                },
                HEARTBEAT_MS, HEARTBEAT_MS,
            )
        }
    }

    /** Lee líneas JSON hasta que el peer se desconecta. Corre en el hilo del transporte. */
    private fun readLoop(input: InputStream) {
        try {
            val reader = BufferedReader(InputStreamReader(input))
            while (running) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                onLine(line)
            }
        } catch (_: Throwable) {
            // caída del socket → abajo se notifica según el estado del handshake
        }
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        out = null
        if (running && !peerLostNotified) {
            peerLostNotified = true
            when {
                // Con handshake COMPLETO, como en online: el VM decide (pelea → victoria por
                // abandono; antesala del host → volver a esperar; invitado → salir de la sala).
                handshaken -> deliver(SfNetMsg(type = "OPPONENT_DISCONNECTED", winner = "abandon"))
                // Invitado SIN handshake: la conexión nunca se verificó → error reintentable
                // (la UI muestra REINTENTAR; jamás se da la sala por buena).
                !isHostRole -> listener?.onFailure(handshakeFailMessage)
                // Host sin handshake: el intento murió a medias; se sigue aceptando.
                else -> Unit
            }
        }
    }

    /** Enruta un mensaje entrante (el HOST además AGREGA lo que en online hace el relay). */
    private fun onLine(raw: String) {
        val msg = runCatching { gson.fromJson(raw, SfNetMsg::class.java) }.getOrNull() ?: return
        when (msg.type) {
            // ── HANDSHAKE (conexión verificada) ──
            "BT_HELLO" -> if (isHostRole && !handshaken) {
                handshaken = true
                sendRaw(mapOf("type" to "BT_WELCOME"))
                deliver(SfNetMsg(type = "OPPONENT_JOINED", playerIndex = 2))
            }
            "BT_WELCOME" -> if (!isHostRole && !handshaken) {
                handshaken = true
                deliver(SfNetMsg(type = "ROOM_JOINED", code = roomCode, playerIndex = 2))
            }
            // Keepalive del enlace: NO sube al VM
            "HEARTBEAT" -> Unit
            // Autoridad del receptor: mi rival me manda SU estado; para MÍ es el oponente
            "PLAYER_STATE" -> deliver(msg.copy(type = "OPPONENT_STATE"))
            // El host agrega la selección del invitado (char2) y publica cuando están ambos
            "SELECT_CHARACTER" -> if (isHostRole) {
                char2 = msg.character
                maybeCharactersSelected()
            }
            // El host agrega la revancha (la piden LOS DOS, como el relay)
            "REQUEST_REMATCH" -> if (isHostRole) {
                rematch2 = true
                deliver(SfNetMsg(type = "REMATCH_REQUESTED"))
                maybeRematchAccepted()
            }
            // Todo lo demás viaja tal cual (PLAYER_DAMAGE, MATCH_ENDED, CHARACTERS_SELECTED,
            // MAP_SELECTED, FIGHT_START, REMATCH_REQUESTED/ACCEPTED generados por el host…)
            else -> deliver(msg)
        }
    }

    // ══════════════ SfNetTransport: flujo de pelea (mismos mensajes) ══════════════

    override fun selectCharacter(name: String) {
        if (isHostRole) {
            char1 = name
            maybeCharactersSelected()
        } else {
            sendRaw(mapOf("type" to "SELECT_CHARACTER", "character" to name))
        }
    }

    /** Solo lo llama el HOST (el VM ya lo gatea con isHost, igual que online). */
    override fun selectMap(file: String) {
        if (!isHostRole) return
        sendRaw(mapOf("type" to "MAP_SELECTED", "map" to file, "countdownMs" to COUNTDOWN_MS))
        deliver(SfNetMsg(type = "MAP_SELECTED", map = file, countdownMs = COUNTDOWN_MS))
        // El countdown que en online corre el server, aquí lo corre el host
        Thread({
            runCatching { Thread.sleep(COUNTDOWN_MS.toLong()) }
            if (running && out != null) {
                sendRaw(mapOf("type" to "FIGHT_START"))
                deliver(SfNetMsg(type = "FIGHT_START"))
            }
        }, "SfPeerCountdown").start()
    }

    override fun requestRematch() {
        if (isHostRole) {
            rematch1 = true
            sendRaw(mapOf("type" to "REMATCH_REQUESTED")) // avisa al rival (como el relay)
            maybeRematchAccepted()
        } else {
            sendRaw(mapOf("type" to "REQUEST_REMATCH")) // el host agrega
        }
    }

    override fun sendRoundEnded(winner: String) {
        // Como MATCH_ENDED: el "relay" local lo difunde a AMBOS (peer + yo mismo)
        sendRaw(mapOf("type" to "ROUND_ENDED", "winner" to winner))
        deliver(SfNetMsg(type = "ROUND_ENDED", winner = winner))
    }

    override fun sendMatchEnded(winner: String) {
        // El relay lo difunde a AMBOS: aquí = mandar al peer + entregármelo a mí mismo
        sendRaw(mapOf("type" to "MATCH_ENDED", "winner" to winner))
        deliver(SfNetMsg(type = "MATCH_ENDED", winner = winner))
    }

    override fun sendDamage(damage: Int, strength: String, atkType: String) =
        sendRaw(mapOf("type" to "PLAYER_DAMAGE", "damage" to damage, "strength" to strength, "atkType" to atkType))

    override fun sendPlayerState(x: Float, y: Float, state: String, frame: Int, dir: Int, hp: Int, fireballs: List<SfNetFireball>) =
        sendRaw(
            mapOf(
                "type" to "PLAYER_STATE", "x" to x, "y" to y, "state" to state,
                "frame" to frame, "dir" to dir, "hp" to hp, "fireballs" to fireballs,
            ),
        )

    // ── Salas/cola/lobby: solo tienen sentido con el server online → no-op local ──
    override fun createRoom() = Unit
    override fun joinRoom(code: String) = Unit
    override fun quickMatch() = Unit
    override fun cancelQueue() = Unit
    override fun listRooms() = Unit
    override fun leaveRoom() = Unit // el peer detecta el cierre del socket (abandono)
    override fun requestJoin(code: String) = Unit
    override fun respondJoin(accept: Boolean) = Unit

    override fun close() {
        running = false
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        runCatching { closePeerSocket() }
        out = null
        runCatching { closeTransport() }
        listener = null
    }

    // ══════════════════════════ internos ══════════════════════════

    /** El host publica CHARACTERS_SELECTED cuando ya eligieron LOS DOS (como el relay). */
    private fun maybeCharactersSelected() {
        val c1 = char1 ?: return
        val c2 = char2 ?: return
        sendRaw(mapOf("type" to "CHARACTERS_SELECTED", "char1" to c1, "char2" to c2))
        deliver(SfNetMsg(type = "CHARACTERS_SELECTED", char1 = c1, char2 = c2))
    }

    /** El host publica REMATCH_ACCEPTED cuando la revancha la pidieron LOS DOS. */
    private fun maybeRematchAccepted() {
        if (!rematch1 || !rematch2) return
        rematch1 = false
        rematch2 = false
        char1 = null
        char2 = null
        sendRaw(mapOf("type" to "REMATCH_ACCEPTED"))
        deliver(SfNetMsg(type = "REMATCH_ACCEPTED"))
    }

    /**
     * Escribe una línea JSON al peer. Si la ESCRITURA falla, el enlace está muerto: se
     * cierra el socket del peer para DESTRABAR el readLoop de inmediato (quien notifica).
     * El HEARTBEAT de 10 s garantiza que siempre haya escrituras que puedan fallar.
     */
    protected fun sendRaw(payload: Map<String, Any?>) {
        val line = gson.toJson(payload.filterValues { it != null }) + "\n"
        val ok = runCatching {
            out?.let { o ->
                synchronized(o) {
                    o.write(line.toByteArray())
                    o.flush()
                }
            }
        }.isSuccess
        if (!ok) runCatching { closePeerSocket() }
    }

    protected fun deliver(msg: SfNetMsg) {
        listener?.onMessage(msg)
    }

    protected companion object {
        const val COUNTDOWN_MS = 3000
        const val HEARTBEAT_MS = 10_000L
        const val HANDSHAKE_TIMEOUT_MS = 6000L
    }
}
