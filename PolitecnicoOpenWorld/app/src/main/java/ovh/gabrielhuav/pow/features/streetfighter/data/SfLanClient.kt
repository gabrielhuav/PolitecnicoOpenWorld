package ovh.gabrielhuav.pow.features.streetfighter.data

import android.util.Log
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
                // 🆕 (2026-07-25) SO_REUSEADDR + bind explícito: sin esto, re-hospedar tras un cierre
                // sucio tira "Address already in use" (el puerto queda en TIME_WAIT) → el host mostraba
                // su IP pero el ServerSocket nunca aceptaba ("conecta pero nunca empieza"). Con reuse,
                // el bind vuelve a tomar el puerto de inmediato.
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(SF_LAN_PORT))
                serverSocket = ss
                Log.d(SF_NET_TAG, "servidor LAN escuchando en :$SF_LAN_PORT (IPs: ${localIpAddresses()})")
                listener.onOpen()
                // Como el ROOM_CREATED del relay: el VM pasa a "esperando rival" (host = p1)
                deliver(SfNetMsg(type = "ROOM_CREATED", code = LAN_ROOM_CODE, playerIndex = 1))
                while (running) {
                    val s = ss.accept() // bloquea hasta que un rival conecta
                    if (!running) { runCatching { s.close() }; break }
                    s.tcpNoDelay = true // estado de pelea cada ~66 ms: sin Nagle
                    s.keepAlive = true  // detecta enlaces muertos por power-save de Wi-Fi
                    socket = s
                    Log.d(SF_NET_TAG, "rival LAN conectó desde ${s.inetAddress?.hostAddress}")
                    // OPPONENT_JOINED se entrega al recibir el HELLO (handshake, en la base)
                    runPeerSession(s.getInputStream(), s.getOutputStream())
                    socket = null
                }
            } catch (t: Throwable) {
                Log.e(SF_NET_TAG, "servidor LAN falló", t)
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
                        candidate.keepAlive = true // detecta enlaces muertos por power-save de Wi-Fi
                        candidate
                    } catch (t: Throwable) {
                        Log.w(SF_NET_TAG, "intento $attempt de conectar a $hostAddress:$SF_LAN_PORT falló: ${t.message}")
                        lastError = t
                        runCatching { candidate.close() }
                        null
                    }
                    if (s != null) break
                    if (attempt < CONNECT_ATTEMPTS) Thread.sleep(CONNECT_RETRY_PAUSE_MS)
                }
                val sock = s ?: throw (lastError ?: error("sin socket"))
                socket = sock
                Log.d(SF_NET_TAG, "conectado a $hostAddress:$SF_LAN_PORT → handshake")
                listener.onOpen()
                runPeerSession(sock.getInputStream(), sock.getOutputStream())
                socket = null
            } catch (t: Throwable) {
                Log.e(SF_NET_TAG, "connect LAN falló", t)
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
        private const val SF_NET_TAG = "SF-NET"

        /** Puerto FIJO del servidor local (debe coincidir en ambos teléfonos). */
        const val SF_LAN_PORT = 47645
        /** Código de "sala" simbólico para reusar el flujo del VM (roomCode = "LAN"). */
        const val LAN_ROOM_CODE = "LAN"
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_RETRY_PAUSE_MS = 1000L
        private const val CONNECT_TIMEOUT_MS = 4000

        /**
         * 🆕 (2026-07-25) TODAS las IPv4 site-local de las interfaces activas (Wi-Fi/hotspot
         * primero). La UI del host las muestra TODAS para que el rival pruebe la correcta: si el
         * teléfono tiene varias interfaces (Wi-Fi + hotspot + VPN), mostrar solo una podía ser la
         * inalcanzable ("conecta pero nunca empieza"). No requiere permisos.
         */
        fun localIpAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
                .sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("ap") }
                .flatMap { ni ->
                    ni.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { it.isSiteLocalAddress }
                        .mapNotNull { it.hostAddress }
                }
                .distinct()
        }.getOrDefault(emptyList())

        /**
         * IP local del teléfono para MOSTRARLA al host ("comparte esta dirección"): la primera de
         * [localIpAddresses]. null = sin red local (la UI pide Wi-Fi o encender el hotspot).
         */
        fun localIpAddress(): String? = localIpAddresses().firstOrNull()
    }
}
