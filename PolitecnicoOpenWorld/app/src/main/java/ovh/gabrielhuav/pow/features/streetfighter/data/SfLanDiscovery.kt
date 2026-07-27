package ovh.gabrielhuav.pow.features.streetfighter.data

import kotlinx.serialization.encodeToString
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.data.json.jsonOf

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

// 🆕 (2026-07-26) AUTODESCUBRIMIENTO LAN del modo pelea "HUELUM VS. GOYA": en vez de teclear la IP,
// el ANFITRIÓN emite una BALIZA por UDP broadcast en la red Wi-Fi y el INVITADO la escucha y ve la
// partida como una tarjeta tocable. Al tocar, se une por la IP que trae el paquete (origen).
//
// - Transporte del JUEGO sigue siendo TCP (SfLanClient); esto es SOLO para ENCONTRAR la sala.
// - Sin servidor: broadcast a 255.255.255.255:DISCOVERY_PORT.
// - El INVITADO adquiere un MulticastLock para que el Wi-Fi no filtre los paquetes broadcast
//   (requiere CHANGE_WIFI_MULTICAST_STATE, permiso NORMAL sin impacto en Play/Data Safety).
//
// ⚠️ La I/O corre en hilos propios (nunca en Main) — misma lección que el NetworkOnMainThreadException.

/** Una partida LAN descubierta (IP del anfitrión + nombre a mostrar). */
data class SfLanGame(val ip: String, val name: String)

class SfLanDiscovery(private val context: Context) {


    // ── HOST: baliza ──
    @Volatile private var beaconRunning = false
    private var beaconThread: Thread? = null

    // ── INVITADO: escucha ──
    @Volatile private var listenRunning = false
    private var listenThread: Thread? = null
    private var listenSocket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** HOST: emite la baliza cada BEACON_MS mientras esté hospedando y esperando rival. */
    fun startBeacon(hostName: String) {
        if (beaconRunning) return
        beaconRunning = true
        beaconThread = Thread({
            val payload = jsonOf(mapOf("app" to APP_TAG, "name" to hostName)).toByteArray()
            runCatching {
                DatagramSocket().use { sock ->
                    sock.broadcast = true
                    val dst = InetAddress.getByName("255.255.255.255")
                    while (beaconRunning) {
                        runCatching { sock.send(DatagramPacket(payload, payload.size, dst, DISCOVERY_PORT)) }
                        Thread.sleep(BEACON_MS)
                    }
                }
            }.onFailure { Log.w(TAG, "baliza LAN falló: ${it.message}") }
        }, "SfLanBeacon").apply { isDaemon = true; start() }
        Log.d(TAG, "baliza LAN emitiendo en :$DISCOVERY_PORT")
    }

    fun stopBeacon() {
        beaconRunning = false
        beaconThread = null
    }

    /**
     * INVITADO: escucha balizas y reporta cada partida hallada (deduplicada por IP) vía [onFound].
     * El [onFound] llega en un hilo de fondo → el VM debe re-postear a Main.
     */
    fun startListening(onFound: (SfLanGame) -> Unit) {
        if (listenRunning) return
        listenRunning = true
        acquireMulticastLock()
        listenThread = Thread({
            val seen = HashSet<String>()
            runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }.use { sock ->
                    listenSocket = sock
                    val buf = ByteArray(512)
                    while (listenRunning) {
                        val packet = DatagramPacket(buf, buf.size)
                        sock.receive(packet) // bloquea hasta recibir (o hasta close())
                        if (!listenRunning) break
                        val json = runCatching {
                            PowJson.decodeFromString<Map<String, String>>(String(packet.data, 0, packet.length))
                        }.getOrNull() ?: continue
                        if (json["app"] != APP_TAG) continue
                        val ip = packet.address?.hostAddress ?: continue
                        if (!seen.add(ip)) continue // ya reportada
                        val name = (json["name"] as? String).orEmpty().ifBlank { ip }
                        Log.d(TAG, "partida LAN hallada: $name ($ip)")
                        onFound(SfLanGame(ip, name))
                    }
                }
            }.onFailure { if (listenRunning) Log.w(TAG, "escucha LAN falló: ${it.message}") }
        }, "SfLanDiscover").apply { isDaemon = true; start() }
    }

    fun stopListening() {
        listenRunning = false
        runCatching { listenSocket?.close() } // destraba el receive()
        listenSocket = null
        listenThread = null
        releaseMulticastLock()
    }

    /** Detiene todo (baliza + escucha). */
    fun close() {
        stopBeacon()
        stopListening()
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            multicastLock = wm.createMulticastLock("POW-SF-LAN-DISCOVERY").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.w(TAG, "no se pudo adquirir el MulticastLock: ${it.message}") }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
    }

    companion object {
        private const val TAG = "SF-NET"
        private const val APP_TAG = "POW-SF-LAN"
        /** Puerto FIJO del descubrimiento (distinto del TCP del juego, SF_LAN_PORT=47645). */
        const val DISCOVERY_PORT = 47646
        private const val BEACON_MS = 1500L
    }
}
