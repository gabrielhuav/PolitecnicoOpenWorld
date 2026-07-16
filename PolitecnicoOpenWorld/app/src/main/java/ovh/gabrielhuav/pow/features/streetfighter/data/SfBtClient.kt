package ovh.gabrielhuav.pow.features.streetfighter.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.UUID

// MULTIJUGADOR LOCAL por BLUETOOTH del modo pelea "HUELUM VS. GOYA" (sin internet).
//
// MISMA arquitectura que el online (AUDIT_SF_MULTIPLAYER.md §3): relay puro con
// autoridad del RECEPTOR — cada teléfono simula a SU peleador y viajan los MISMOS
// mensajes JSON (SfNetMsg) que con MultiplayerSF/, solo cambia el TRANSPORTE:
// BluetoothSocket RFCOMM con UUID fijo (SF_BT_UUID, igual en ambos lados).
//
// El HOST hace de "server": acepta 1 conexión y GENERA LOCALMENTE los mensajes
// que en online manda el relay (OPPONENT_JOINED, CHARACTERS_SELECTED, MAP_SELECTED,
// FIGHT_START tras el countdown, REMATCH_ACCEPTED cuando la piden los dos).
// El INVITADO manda sus mensajes crudos y el host los agrega.
// PLAYER_STATE entrante se convierte a OPPONENT_STATE en el receptor (ambos lados).
//
// SIN foreground service (prohibido: con targetSdk 34 obligaría a declaración con
// video en Play Console): la conexión vive con la Activity, como el WebSocket, y
// close() cierra los sockets al salir. Los permisos runtime los pide la UI ANTES
// de llamar aquí (por eso el @SuppressLint("MissingPermission") de la clase).

/** Dispositivo visible/emparejado para el selector "BUSCAR RIVAL". */
data class SfBtDevice(val name: String, val address: String)

@SuppressLint("MissingPermission") // la UI pide CONNECT/SCAN/ADVERTISE antes de instanciar
class SfBtClient(
    private val context: Context,
    private val gson: Gson = Gson(),
) : SfNetTransport {

    private var listener: SfNetTransport.Listener? = null
    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var running = false
    private var isHostRole = false

    // ── "server" local del HOST (lo que en online guarda la sala del relay) ──
    @Volatile private var char1: String? = null
    @Volatile private var char2: String? = null
    @Volatile private var rematch1 = false
    @Volatile private var rematch2 = false
    @Volatile private var peerLostNotified = false

    // KEEPALIVE de la conexión (persistencia): RFCOMM no siempre reporta rápido un enlace
    // muerto (rival fuera de alcance / app matada sin close). Un HEARTBEAT periódico fuerza
    // tráfico → si la escritura falla, se cierra el socket y el readLoop destraba YA con el
    // abandono, en vez de colgarse esperando al stack BT. (Mismo patrón que el WS online.)
    private var heartbeatTimer: Timer? = null

    private var discoveryReceiver: BroadcastReceiver? = null

    private fun adapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    // ══════════════════════════════ CONEXIÓN ══════════════════════════════

    /** HOST: escucha por RFCOMM y acepta rivales (uno a la vez). Equivale a CREATE_ROOM. */
    fun startHost(listener: SfNetTransport.Listener) {
        this.listener = listener
        isHostRole = true
        running = true
        Thread({
            try {
                val ad = adapter() ?: throw IllegalStateException("Sin Bluetooth")
                if (!ad.isEnabled) throw IllegalStateException("Bluetooth apagado")
                ad.cancelDiscovery()
                val ss = ad.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SF_BT_UUID)
                serverSocket = ss
                listener.onOpen()
                // Como el ROOM_CREATED del relay: el VM pasa a "esperando rival" (host = p1)
                deliver(SfNetMsg(type = "ROOM_CREATED", code = BT_ROOM_CODE, playerIndex = 1))
                while (running) {
                    val s = ss.accept() // bloquea hasta que un rival conecta
                    if (!running) { runCatching { s.close() }; break }
                    onPeerConnected(s)
                    deliver(SfNetMsg(type = "OPPONENT_JOINED", playerIndex = 2))
                    readLoop(s) // regresa cuando el rival se desconecta → volver a aceptar
                }
            } catch (t: Throwable) {
                if (running) listener.onFailure("Bluetooth: ${t.message ?: "no disponible"}")
            }
        }, "SfBtHost").start()
    }

    /** INVITADO: conecta al host elegido en el selector. Equivale a JOIN_ROOM. */
    fun connectToHost(address: String, listener: SfNetTransport.Listener) {
        this.listener = listener
        isHostRole = false
        running = true
        Thread({
            try {
                val ad = adapter() ?: throw IllegalStateException("Sin Bluetooth")
                ad.cancelDiscovery() // el discovery activo degrada la conexión RFCOMM
                val device = ad.getRemoteDevice(address)
                val s = device.createRfcommSocketToServiceRecord(SF_BT_UUID)
                s.connect() // dispara el diálogo de emparejamiento del sistema si hace falta
                onPeerConnected(s)
                listener.onOpen()
                deliver(SfNetMsg(type = "ROOM_JOINED", code = BT_ROOM_CODE, playerIndex = 2))
                readLoop(s)
            } catch (t: Throwable) {
                if (running) listener.onFailure("Bluetooth: no se pudo conectar (${t.message ?: "?"})")
            }
        }, "SfBtJoin").start()
    }

    private fun onPeerConnected(s: BluetoothSocket) {
        socket = s
        out = s.outputStream
        peerLostNotified = false
        // Sala "nueva" para esta conexión: TAMBIÉN char1 (si el host arrastrara su selección
        // anterior, un SELECT_CHARACTER del rival nuevo dispararía CHARACTERS_SELECTED viejo)
        char1 = null
        char2 = null
        rematch1 = false
        rematch2 = false
        // KEEPALIVE cada 10 s (el receptor lo ignora; ver comentario del campo)
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

    /** Lee líneas JSON hasta que el peer se desconecta. Corre en el hilo BT. */
    private fun readLoop(s: BluetoothSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(s.inputStream))
            while (running) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                onLine(line)
            }
        } catch (_: Throwable) {
            // caída del socket → abajo se notifica como abandono
        }
        heartbeatTimer?.cancel()
        heartbeatTimer = null
        socket = null
        out = null
        if (running && !peerLostNotified) {
            peerLostNotified = true
            // Como en online: el VM decide (pelea → victoria por abandono; antesala del
            // host → volver a esperar rival; invitado → salir de la "sala").
            deliver(SfNetMsg(type = "OPPONENT_DISCONNECTED", winner = "abandon"))
        }
    }

    /** Enruta un mensaje entrante (el HOST además AGREGA lo que en online hace el relay). */
    private fun onLine(raw: String) {
        val msg = runCatching { gson.fromJson(raw, SfNetMsg::class.java) }.getOrNull() ?: return
        when (msg.type) {
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

    // ══════════════════ ESCANEO (selector "BUSCAR RIVAL") ══════════════════

    /** Lista emparejados YA y arranca discovery; cada hallazgo llega por [onDevice]. */
    fun startScan(onDevice: (SfBtDevice) -> Unit): Boolean {
        val ad = adapter() ?: return false
        if (!ad.isEnabled) return false
        // Emparejados primero: aparecen sin esperar el discovery (~12 s)
        runCatching {
            ad.bondedDevices?.forEach { d -> onDevice(SfBtDevice(d.name ?: d.address, d.address)) }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_FOUND) return
                @Suppress("DEPRECATION")
                val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                runCatching { onDevice(SfBtDevice(d.name ?: d.address, d.address)) }
            }
        }
        discoveryReceiver = receiver
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        return runCatching { ad.startDiscovery() }.getOrDefault(false)
    }

    fun stopScan() {
        runCatching { adapter()?.cancelDiscovery() }
        discoveryReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        discoveryReceiver = null
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
        val payload = mapOf("type" to "MAP_SELECTED", "map" to file, "countdownMs" to COUNTDOWN_MS)
        sendRaw(payload)
        deliver(SfNetMsg(type = "MAP_SELECTED", map = file, countdownMs = COUNTDOWN_MS))
        // El countdown que en online corre el server, aquí lo corre el host
        Thread({
            runCatching { Thread.sleep(COUNTDOWN_MS.toLong()) }
            if (running && socket != null) {
                sendRaw(mapOf("type" to "FIGHT_START"))
                deliver(SfNetMsg(type = "FIGHT_START"))
            }
        }, "SfBtCountdown").start()
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

    // ── Salas/cola/lobby: solo tienen sentido con el server online → no-op en BT ──
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
        stopScan()
        runCatching { socket?.close() }
        socket = null
        out = null
        runCatching { serverSocket?.close() }
        serverSocket = null
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
     * Escribe una línea JSON al peer. Si la ESCRITURA falla, el enlace está muerto: se cierra
     * el socket para DESTRABAR el readLoop de inmediato (que es quien notifica el abandono) —
     * sin esto, una caída silenciosa podía dejar la pelea colgada hasta que el stack BT
     * reportara solo. El HEARTBEAT de 10 s garantiza que siempre haya escrituras que fallen.
     */
    private fun sendRaw(payload: Map<String, Any?>) {
        val line = gson.toJson(payload.filterValues { it != null }) + "\n"
        val ok = runCatching {
            out?.let { o ->
                synchronized(o) {
                    o.write(line.toByteArray())
                    o.flush()
                }
            }
        }.isSuccess
        if (!ok) runCatching { socket?.close() }
    }

    private fun deliver(msg: SfNetMsg) {
        listener?.onMessage(msg)
    }

    companion object {
        /** UUID FIJO del servicio RFCOMM (debe coincidir en ambos teléfonos). */
        val SF_BT_UUID: UUID = UUID.fromString("7f9b1e40-5c33-4b7a-9c1e-8d2f6a4b0c37")
        private const val SERVICE_NAME = "POW-HUELUM-VS-GOYA"
        /** Código de "sala" simbólico para reusar el flujo del VM (roomCode = "BT"). */
        const val BT_ROOM_CODE = "BT"
        private const val COUNTDOWN_MS = 3000
        private const val HEARTBEAT_MS = 10_000L
    }
}
