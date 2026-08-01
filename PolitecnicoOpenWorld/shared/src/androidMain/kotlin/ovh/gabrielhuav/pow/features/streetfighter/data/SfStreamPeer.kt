package ovh.gabrielhuav.pow.features.streetfighter.data

import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.data.json.jsonOf

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

abstract class SfStreamPeer : SfNetTransport {

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

    // 🆕 (2026-07-26) TODAS las escrituras al socket van por ESTE hilo. CAUSA REAL del "muere al
    // elegir peleador": `selectCharacter`/`sendPlayerState`/etc. se llamaban desde el HILO PRINCIPAL
    // (UI) y una escritura de red en Main lanza NetworkOnMainThreadException → sendRaw la atrapaba y
    // CERRABA el socket. (Los sockets Bluetooth están EXENTOS de esa regla → por eso BT sí servía;
    // TCP no.) Un executor de UN hilo lo mueve fuera de Main Y serializa las escrituras.
    private val writeExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "SfPeerWrite").apply { isDaemon = true } }

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
            Log.d(SF_NET_TAG, "peer conectado (invitado): enviando BT_HELLO")
            deliver(SfNetMsg(type = "BT_HANDSHAKE"))
            sendRaw(mapOf("type" to "BT_HELLO"))
        } else {
            Log.d(SF_NET_TAG, "peer conectado (host): esperando BT_HELLO")
        }
        // 🆕 (2026-07-25) Watchdog de handshake en AMBOS roles: si el saludo no se completa en
        // HANDSHAKE_TIMEOUT_MS se cierra el socket. Antes SOLO el invitado lo tenía; sin el del
        // HOST, un enlace TCP asimétrico (HELLO llega pero WELCOME no vuelve) dejaba al host en una
        // sesión a medio abrir (LAN "conecta pero nunca empieza"). Al cerrar, el host re-acepta.
        startHandshakeWatchdog()
        readLoop(input)
    }

    /** Si el handshake no se completa a tiempo, se cierra el socket (conexión NO verificada). */
    private fun startHandshakeWatchdog() {
        Thread({
            runCatching { Thread.sleep(HANDSHAKE_TIMEOUT_MS) }
            if (running && !handshaken) {
                Log.w(SF_NET_TAG, "handshake sin completar en ${HANDSHAKE_TIMEOUT_MS}ms → cerrando socket")
                runCatching { closePeerSocket() } // destraba el readLoop → onFailure/re-accept
            }
        }, "SfPeerHandshake").start()
    }

    private fun startHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = Timer(true).also { t ->
            t.schedule(
                object : TimerTask() {
                    override fun run() { sendRaw(mapOf("type" to "HEARTBEAT")) }
                },
                HEARTBEAT_FIRST_MS, HEARTBEAT_MS,
            )
        }
    }

    /** Lee líneas JSON hasta que el peer se desconecta. Corre en el hilo del transporte. */
    private fun readLoop(input: InputStream) {
        try {
            val reader = BufferedReader(InputStreamReader(input))
            while (running) {
                val line = reader.readLine()
                if (line == null) {
                    Log.w(SF_NET_TAG, "readLoop: EOF (el peer cerró la conexión)")
                    break
                }
                if (line.isBlank()) continue
                onLine(line)
            }
        } catch (t: Throwable) {
            // caída del socket → abajo se notifica según el estado del handshake
            Log.w(SF_NET_TAG, "readLoop: excepción de lectura (${t.javaClass.simpleName}: ${t.message})")
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
        val msg = runCatching { PowJson.decodeFromString<SfNetMsg>(raw) }.getOrNull() ?: return
        when (msg.type) {
            // ── HANDSHAKE (conexión verificada) ──
            "BT_HELLO" -> if (isHostRole && !handshaken) {
                handshaken = true
                Log.d(SF_NET_TAG, "BT_HELLO recibido (host) → BT_WELCOME + OPPONENT_JOINED")
                sendRaw(mapOf("type" to "BT_WELCOME"))
                deliver(SfNetMsg(type = "OPPONENT_JOINED", playerIndex = 2))
            }
            "BT_WELCOME" -> if (!isHostRole && !handshaken) {
                handshaken = true
                Log.d(SF_NET_TAG, "BT_WELCOME recibido (invitado) → ROOM_JOINED")
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
        Log.d(SF_NET_TAG, "MAP_SELECTED ($file) → countdown ${COUNTDOWN_MS}ms al FIGHT_START")
        // El countdown que en online corre el server, aquí lo corre el host
        Thread({
            runCatching { Thread.sleep(COUNTDOWN_MS.toLong()) }
            if (running && out != null) {
                Log.d(SF_NET_TAG, "FIGHT_START (host) → ambos empiezan a cargar")
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

    override fun sendReady() {
        // 🆕 (2026-07-25) BARRERA "AMBOS LISTOS": solo al PEER (mi propio VM ya sabe que cargué).
        // El peer lo recibe por readLoop → onLine → deliver (else) → su VM.
        sendRaw(mapOf("type" to "PLAYER_READY"))
    }

    override fun sendRoundEnded(winner: String, outcome: String) {
        // Como MATCH_ENDED: el "relay" local lo difunde a AMBOS (peer + yo mismo)
        sendRaw(mapOf("type" to "ROUND_ENDED", "winner" to winner, "outcome" to outcome))
        deliver(SfNetMsg(type = "ROUND_ENDED", winner = winner, outcome = outcome))
    }

    override fun sendMatchEnded(winner: String) {
        // El relay lo difunde a AMBOS: aquí = mandar al peer + entregármelo a mí mismo
        sendRaw(mapOf("type" to "MATCH_ENDED", "winner" to winner))
        deliver(SfNetMsg(type = "MATCH_ENDED", winner = winner))
    }

    override fun sendDamage(damage: Int, strength: String, atkType: String) =
        sendRaw(mapOf("type" to "PLAYER_DAMAGE", "damage" to damage, "strength" to strength, "atkType" to atkType))

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
        sendRaw(
            sfPlayerStatePayload(x, y, state, frame, dir, hp, timer, fireballs, meter, audio),
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
        runCatching { writeExecutor.shutdownNow() } // 🆕 detiene el hilo de escritura
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
     * Encola una línea JSON al peer. La escritura REAL corre SIEMPRE en [writeExecutor] (nunca en
     * el hilo que llama), por dos razones: (1) evita NetworkOnMainThreadException cuando el VM manda
     * desde Main (selectCharacter/sendPlayerState/…), que era lo que CERRABA el socket al elegir
     * peleador; (2) serializa las escrituras. Si la escritura falla, el enlace murió → se cierra el
     * socket para destrabar el readLoop (quien notifica).
     */
    protected fun sendRaw(payload: Map<String, Any?>) {
        // El JSON se serializa en el hilo que llama (no es red); la escritura va al executor.
        val line = jsonOf(payload) + "\n"
        val type = payload["type"]
        // execute puede lanzar RejectedExecutionException si el executor ya se apagó (tras close()).
        runCatching {
            writeExecutor.execute {
                val result = runCatching {
                    out?.let { o ->
                        synchronized(o) {
                            o.write(line.toByteArray())
                            o.flush()
                        }
                    }
                }
                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    Log.w(SF_NET_TAG, "escritura falló ($type) → enlace muerto: ${ex?.javaClass?.simpleName}: ${ex?.message}")
                    runCatching { closePeerSocket() }
                }
            }
        }
    }

    protected fun deliver(msg: SfNetMsg) {
        listener?.onMessage(msg)
    }

    protected companion object {
        const val COUNTDOWN_MS = 3000
        // Heartbeat: mantiene vivo el socket durante las pantallas OCIOSAS (selección de peleador,
        // espera) — durante la pelea el PLAYER_STATE ya genera tráfico constante. El "muere al elegir
        // peleador" NO era idle sino NetworkOnMainThreadException (ver sendRaw/writeExecutor); con eso
        // resuelto, un heartbeat moderado + WifiLock bastan. El receptor ignora "HEARTBEAT".
        const val HEARTBEAT_FIRST_MS = 2000L
        const val HEARTBEAT_MS = 4000L
        const val HANDSHAKE_TIMEOUT_MS = 6000L
        // 🆕 (2026-07-25) Tag de logcat para diagnosticar el enlace local (BT/LAN).
        const val SF_NET_TAG = "SF-NET"
    }
}
