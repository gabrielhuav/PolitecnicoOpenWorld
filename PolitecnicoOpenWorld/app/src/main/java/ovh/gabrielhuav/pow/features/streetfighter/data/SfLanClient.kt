package ovh.gabrielhuav.pow.features.streetfighter.data

import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

// 🆕 SERVIDOR LOCAL (LAN/Wi-Fi) del modo pelea "HUELUM VS. GOYA" (2026-07-16): el JUGADOR
// hostea su propia "sala" estilo LAN party — un teléfono abre un ServerSocket TCP en la red
// local (Wi-Fi u hotspot de uno de los dos) y el rival se une tecleando su IP (se muestra en
// la pantalla del host). SIN servidor de Render de por medio.
//
// Toda la lógica de sesión (relay puro/autoridad del receptor, handshake HELLO→WELCOME,
// heartbeat, "server" local del host) viene de la base SfStreamPeer (compartida con
// SfBtClient): aquí solo vive el transporte TCP.
//
// ⚠️ PLAY STORE: este transporte NO usa permisos nuevos (solo INTERNET, que la app ya
// declara) ni servicios en primer plano, y el tráfico es dispositivo-a-dispositivo efímero
// → NO cambia Data Safety ni ningún formulario de la consola. Mantenerlo así.

class SfLanClient : SfStreamPeer() {

    override val roomCode: String = LAN_ROOM_CODE
    override val handshakeFailMessage: String =
        "Red local: el servidor no respondió (¿el anfitrión sigue en CREAR SERVIDOR y están en la misma red?)"

    private var serverSocket: ServerSocket? = null
    @Volatile private var socket: Socket? = null

    // ══════════════════════════════ CONEXIÓN ══════════════════════════════

    /** HOST: abre el ServerSocket en SF_LAN_PORT y acepta rivales (uno a la vez). */
    fun startHost(listener: SfNetTransport.Listener) {
        this.listener = listener
        isHostRole = true
        running = true
        Thread({
            try {
                val ss = ServerSocket(SF_LAN_PORT)
                serverSocket = ss
                listener.onOpen()
                // Como el ROOM_CREATED del relay: el VM pasa a "esperando rival" (host = p1)
                deliver(SfNetMsg(type = "ROOM_CREATED", code = LAN_ROOM_CODE, playerIndex = 1))
                while (running) {
                    val s = ss.accept() // bloquea hasta que un rival conecta
                    if (!running) { runCatching { s.close() }; break }
                    s.tcpNoDelay = true // estado de pelea cada ~66 ms: sin Nagle
                    socket = s
                    // OPPONENT_JOINED se entrega al recibir el HELLO (handshake, en la base)
                    runPeerSession(s.getInputStream(), s.getOutputStream())
                    socket = null
                }
            } catch (t: Throwable) {
                if (running) listener.onFailure("Red local: ${t.message ?: "no se pudo abrir el servidor"}")
            }
        }, "SfLanHost").start()
    }

    /** INVITADO: conecta a la IP del host (misma red). Reintenta como el BT. */
    fun connectToHost(hostAddress: String, listener: SfNetTransport.Listener) {
        this.listener = listener
        isHostRole = false
        running = true
        Thread({
            try {
                var s: Socket? = null
                var lastError: Throwable? = null
                for (attempt in 1..CONNECT_ATTEMPTS) {
                    if (!running) return@Thread
                    val candidate = Socket()
                    s = try {
                        candidate.connect(InetSocketAddress(hostAddress.trim(), SF_LAN_PORT), CONNECT_TIMEOUT_MS)
                        candidate.tcpNoDelay = true
                        candidate
                    } catch (t: Throwable) {
                        lastError = t
                        runCatching { candidate.close() }
                        null
                    }
                    if (s != null) break
                    if (attempt < CONNECT_ATTEMPTS) Thread.sleep(CONNECT_RETRY_PAUSE_MS)
                }
                val sock = s ?: throw (lastError ?: error("sin socket"))
                socket = sock
                listener.onOpen()
                runPeerSession(sock.getInputStream(), sock.getOutputStream())
                socket = null
            } catch (t: Throwable) {
                if (running) listener.onFailure("Red local: no se pudo conectar (${t.message ?: "?"})")
            }
        }, "SfLanJoin").start()
    }

    override fun closePeerSocket() {
        runCatching { socket?.close() }
        socket = null
    }

    override fun closeTransport() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    companion object {
        /** Puerto FIJO del servidor local (debe coincidir en ambos teléfonos). */
        const val SF_LAN_PORT = 47645
        /** Código de "sala" simbólico para reusar el flujo del VM (roomCode = "LAN"). */
        const val LAN_ROOM_CODE = "LAN"
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_RETRY_PAUSE_MS = 1000L
        private const val CONNECT_TIMEOUT_MS = 4000

        /**
         * IP local del teléfono para MOSTRARLA al host ("comparte esta dirección"): la
         * primera IPv4 site-local de las interfaces activas (wlan primero — Wi-Fi/hotspot).
         * null = sin red local (la UI pide conectarse a Wi-Fi o encender el hotspot).
         * No requiere permisos.
         */
        fun localIpAddress(): String? = runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("ap") }
            interfaces.firstNotNullOfOrNull { ni ->
                ni.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress }
                    ?.hostAddress
            }
        }.getOrNull()
    }
}
