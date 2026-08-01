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
import java.util.UUID

// MULTIJUGADOR LOCAL por BLUETOOTH del modo pelea "HUELUM VS. GOYA" (sin internet).
//
// La lógica de sesión (relay puro con autoridad del receptor, handshake HELLO/WELCOME,
// heartbeat, "server" local del host) vive en la BASE COMÚN SfStreamPeer (compartida con
// el servidor LAN); aquí solo queda lo específico de Bluetooth: RFCOMM con UUID fijo
// (SF_BT_UUID, igual en ambos lados), accept del host, connect con reintentos del invitado
// y el discovery del selector "BUSCAR RIVAL".
//
// SIN foreground service (con targetSdk 34 obligaría a declaración con video en Play
// Console): la conexión vive con la Activity y close() cierra los sockets al salir.
// Los permisos runtime los pide la UI ANTES de llamar aquí (de ahí el @SuppressLint).

@SuppressLint("MissingPermission") // la UI pide CONNECT/SCAN/ADVERTISE antes de instanciar
class SfBtClient(
    private val context: Context,
) : SfStreamPeer() {

    override val roomCode: String = BT_ROOM_CODE
    override val handshakeFailMessage: String =
        "Bluetooth: el anfitrión no respondió (¿está en ANFITRIÓN y visible?)"

    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var socket: BluetoothSocket? = null
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
                val ad = adapter() ?: error("Sin Bluetooth")
                check(ad.isEnabled) { "Bluetooth apagado" }
                // ⚠️ cancelDiscovery exige BLUETOOTH_SCAN (Android 12+) y el flujo de HOST solo
                // pide CONNECT+ADVERTISE → SIEMPRE best-effort (sin él solo empeora el enlace).
                runCatching { ad.cancelDiscovery() }
                val ss = ad.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SF_BT_UUID)
                serverSocket = ss
                listener.onOpen()
                // Como el ROOM_CREATED del relay: el VM pasa a "esperando rival" (host = p1)
                deliver(SfNetMsg(type = "ROOM_CREATED", code = BT_ROOM_CODE, playerIndex = 1))
                while (running) {
                    val s = ss.accept() // bloquea hasta que un rival conecta
                    if (!running) { runCatching { s.close() }; break }
                    socket = s
                    // OPPONENT_JOINED se entrega al recibir el HELLO (handshake, en la base)
                    runPeerSession(s.inputStream, s.outputStream) // bloquea hasta perder al rival
                    socket = null
                }
            } catch (t: Throwable) {
                if (running) listener.onFailure("Bluetooth: ${t.message ?: "no disponible"}")
            }
        }, "SfBtHost").start()
    }

    /**
     * INVITADO: conecta al host elegido en el selector. Equivale a JOIN_ROOM.
     * REINTENTA el connect hasta 3 veces (el 1er intento suele fallar si el sistema
     * interpone el diálogo de emparejamiento); el handshake corre en la base.
     */
    fun connectToHost(address: String, listener: SfNetTransport.Listener) {
        this.listener = listener
        isHostRole = false
        running = true
        Thread({
            try {
                val ad = adapter() ?: error("Sin Bluetooth")
                check(ad.isEnabled) { "Bluetooth apagado" }
                runCatching { ad.cancelDiscovery() } // el discovery degrada RFCOMM (best-effort)
                val device = ad.getRemoteDevice(address)
                var s: BluetoothSocket? = null
                var lastError: Throwable? = null
                for (attempt in 1..CONNECT_ATTEMPTS) {
                    if (!running) return@Thread
                    val candidate = device.createRfcommSocketToServiceRecord(SF_BT_UUID)
                    s = try {
                        candidate.connect() // puede disparar el diálogo de emparejamiento
                        candidate
                    } catch (t: Throwable) {
                        lastError = t
                        runCatching { candidate.close() } // no filtrar sockets a medio crear
                        null
                    }
                    if (s != null) break
                    if (attempt < CONNECT_ATTEMPTS) Thread.sleep(CONNECT_RETRY_PAUSE_MS)
                }
                val sock = s ?: throw (lastError ?: error("sin socket"))
                socket = sock
                listener.onOpen()
                runPeerSession(sock.inputStream, sock.outputStream)
                socket = null
            } catch (t: Throwable) {
                if (running) listener.onFailure("Bluetooth: no se pudo conectar (${t.message ?: "?"})")
            }
        }, "SfBtJoin").start()
    }

    override fun closePeerSocket() {
        runCatching { socket?.close() }
        socket = null
    }

    override fun closeTransport() {
        stopScan()
        runCatching { serverSocket?.close() }
        serverSocket = null
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

    companion object {
        /** UUID FIJO del servicio RFCOMM (debe coincidir en ambos teléfonos). */
        val SF_BT_UUID: UUID = UUID.fromString("7f9b1e40-5c33-4b7a-9c1e-8d2f6a4b0c37")
        private const val SERVICE_NAME = "POW-HUELUM-VS-GOYA"
        /** Código de "sala" simbólico para reusar el flujo del VM (roomCode = "BT"). */
        const val BT_ROOM_CODE = "BT"
        // Reintentos del connect del invitado (el 1º suele morir con el diálogo de emparejamiento)
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_RETRY_PAUSE_MS = 1200L
    }
}
