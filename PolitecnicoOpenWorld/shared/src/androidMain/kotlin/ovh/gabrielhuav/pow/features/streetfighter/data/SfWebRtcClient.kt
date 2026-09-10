package ovh.gabrielhuav.pow.features.streetfighter.data

import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.data.json.jsonOf

import android.content.Context
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Transporte P2P del MULTIJUGADOR 1v1 de "TITULACIÓN POR COMBATE" (2026-07-26).
//
// LA IDEA (modelo "GameRanger"): el servidor de Render deja de reenviar la pelea y solo hace de
// CUPIDO — empareja a los 2 jugadores e intercambia sus datos de conexión (SDP + candidatos ICE).
// A partir de ahí la pelea viaja DIRECTA teléfono-a-teléfono por un DataChannel de WebRTC. Para
// dos jugadores en México eso quita el rodeo hasta Oregón, que es de donde salía casi todo el lag.
//
// POR QUÉ NO ES "TODO O NADA" (decisión de diseño, importante):
// Este transporte NO reemplaza al relay: lo DECORA. Se reparte el trabajo así:
//   · CAMINO CALIENTE por P2P  → PLAYER_STATE (~15 Hz), PLAYER_DAMAGE, PLAYER_READY. Aquí vive
//     TODA la latencia que se siente al pelear, y son mensajes que el server solo reenviaba
//     ciego de un jugador al otro: moverlos no le quita información a nadie.
//   · PLANO DE CONTROL por el RELAY → salas, selección de peleador/mapa, revancha, ROUND_ENDED y
//     MATCH_ENDED. Estos NO se pueden mover: el server los AGREGA (publica CHARACTERS_SELECTED
//     cuando eligieron los dos), los DIFUNDE al emisor incluido, o cambian el estado de la sala
//     (MATCH_ENDED pone phase='ended'). Mandarlos por P2P dejaría al server ciego y rompería la
//     revancha y la limpieza de salas. Además son raros: su latencia no se siente.
//
// FALLBACK AUTOMÁTICO (por eso "gratis de por vida"): si el DataChannel no llega a abrirse
// —NAT simétrico, común en datos móviles, ~20-30% de las redes— o se cae a media pelea, TODO
// vuelve solo por el relay de Render, que ya funciona. No hace falta un TURN de pago: el respaldo
// es la infraestructura que ya existe. El jugador nunca ve un error, solo más o menos latencia.
//
// PLAY STORE: WebRTC cifra el DataChannel con DTLS, así que "cifrado en tránsito" sigue siendo
// cierto. No aparece ningún tipo de dato nuevo → el formulario de Seguridad de los datos NO se
// toca (ver README for IAS/PLAYSTORE_formulario_seguridad_datos.md).

class SfWebRtcClient(
    appContext: Context,
    private val relay: SfMatchClient,
    /** El HOST de la sala (p1) hace la OFERTA; el invitado contesta. Evita la colisión de glare. */
    private val isOfferer: Boolean,
) : SfNetTransport {

    /** Listener del VM. Recibe TANTO lo que llega por el relay como lo que llega por P2P. */
    private var listener: SfNetTransport.Listener? = null

    // ⚠️ @Volatile OBLIGATORIO: estos tres se ESCRIBEN en el hilo de trabajo (initOnWorker) o en
    // el hilo de señalización de WebRTC (onDataChannel), y se LEEN desde el hilo del juego
    // (sendHot, 15 veces por segundo) y desde Main (close). Sin volatile el hilo del juego podía
    // no ver nunca el canal ya abierto y el P2P quedaba mudo aunque hubiera conectado.
    @Volatile private var factory: PeerConnectionFactory? = null
    @Volatile private var peer: PeerConnection? = null
    @Volatile private var channel: DataChannel? = null

    @Volatile private var p2pOpen = false
    @Volatile private var closed = false

    /** Candidatos ICE que llegaron ANTES de la descripción remota (no se pueden añadir aún). */
    private val pendingCandidates = mutableListOf<IceCandidate>()
    @Volatile private var remoteDescriptionSet = false

    // 🆕 GAMA BAJA: WebRTC no está listo hasta que el arranque PESADO termine en su hilo. La
    // señalización que llegue mientras tanto se guarda aquí y se aplica al terminar; si se
    // descartara, en un teléfono lento se perdería la oferta y el P2P nunca conectaría.
    @Volatile private var ready = false
    private val pendingSignals = mutableListOf<SfNetMsg>()

    private val appCtx: Context = appContext.applicationContext

    // Misma lección que SfStreamPeer: NADA de I/O de red en el hilo principal. Este MISMO hilo
    // corre el arranque de WebRTC (ver start): así el orden queda garantizado sin cerrojos.
    private val sendExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "SfRtcSend").apply { isDaemon = true } }

    /** true = la pelea está yendo DIRECTA (para el HUD y para saber si el fallback está activo). */
    val isDirect: Boolean get() = p2pOpen

    /**
     * 🆕 El rival CAMBIÓ (se fue uno y entró otro en la misma sala). El canal directo apuntaba al
     * que se fue: se marca cerrado YA para que todo salga por el relay, sin esperar a que ICE
     * detecte la caída (tarda segundos, y en esa ventana los envíos se perderían en el vacío).
     */
    fun markPeerChanged() {
        p2pOpen = false
    }

    // ══════════════════════ arranque ══════════════════════

    /**
     * Engancha el transporte: se pone de listener del relay (para interceptar la señalización) y
     * abre la negociación P2P. El relay YA debe estar conectado y con la sala armada.
     */
    fun start(listener: SfNetTransport.Listener) {
        this.listener = listener
        // ⚡ GAMA BAJA: `PeerConnectionFactory.initialize` CARGA los ~11 MB de librería nativa y
        // levanta hilos. Hacerlo en el hilo principal congelaba la selección de peleador en un
        // teléfono lento. Va al mismo hilo que los envíos, que además garantiza el orden.
        runCatching {
            sendExecutor.execute { initOnWorker() }
        }.onFailure { Log.w(SF_RTC_TAG, "no se pudo encolar el arranque de WebRTC → relay") }
    }

    private fun initOnWorker() {
        if (closed) return // se salió de la pelea antes de que llegáramos a arrancar
        val f = runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appCtx)
                    .createInitializationOptions(),
            )
            PeerConnectionFactory.builder().createPeerConnectionFactory()
        }.getOrNull()
        if (f == null) {
            // Sin WebRTC: se juega por el relay y ya. No es un error para el usuario.
            Log.w(SF_RTC_TAG, "WebRTC no disponible → la pelea se queda en el relay")
            return
        }
        factory = f
        val config = PeerConnection.RTCConfiguration(STUN_SERVERS).apply {
            // Sin TURN a propósito: el respaldo cuando el hole punching falla es el RELAY que ya
            // existe, no un servidor de pago. Ver la cabecera del archivo.
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peer = runCatching { f.createPeerConnection(config, PeerObserver()) }.getOrNull()
        if (peer == null) {
            Log.w(SF_RTC_TAG, "no se pudo crear el PeerConnection → relay")
            return
        }
        if (isOfferer) {
            // El host crea el canal; el invitado lo recibe por onDataChannel.
            // ⚠️ FIABLE Y ORDENADO (el default). NO usar `maxRetransmits = 0`: PLAYER_DAMAGE es
            // un evento ÚNICO — si su paquete se pierde, el golpe no existe para el rival y los
            // dos teléfonos dejan de coincidir en la vida. Por el relay (TCP) eso era imposible,
            // así que el P2P no puede ser menos fiable. La ganancia de latencia viene de quitar
            // el rodeo a Oregón, no de perder paquetes.
            channel = peer?.createDataChannel(CHANNEL_LABEL, DataChannel.Init())
                ?.also { attachChannel(it) }
        }
        ready = true
        // Señalización que llegó mientras arrancábamos (teléfono lento / red rápida).
        val queued = synchronized(pendingSignals) {
            val copy = pendingSignals.toList()
            pendingSignals.clear()
            copy
        }
        queued.forEach { routeSignal(it) }
        if (!isOfferer) {
            // 🆕 El INVITADO avisa que ya puede negociar; el host ofrece SOLO al recibirlo.
            // Sin esto había una carrera real en PARTIDA RÁPIDA: el server manda OPPONENT_JOINED
            // al host ANTES que ROOM_JOINED al invitado, así que el host ofrecía cuando el
            // invitado todavía no existía y la oferta se perdía (el P2P nunca conectaba).
            relay.sendSignal("SIGNAL_READY")
        }
    }

    private fun createOffer() {
        peer?.createOffer(
            object : SimpleSdpObserver("createOffer") {
                override fun onCreateSuccess(desc: SessionDescription) {
                    peer?.setLocalDescription(SimpleSdpObserver("setLocalOffer"), desc)
                    relay.sendSignal("SIGNAL_OFFER", sdp = desc.description)
                }
            },
            MediaConstraints(),
        )
    }

    // ══════════════════════ señalización entrante ══════════════════════

    /**
     * Filtra un mensaje que llegó por el RELAY. Devuelve true si era señalización (y por tanto
     * NO debe subir al VM: es plomería, no gameplay).
     */
    fun consumeSignaling(msg: SfNetMsg): Boolean {
        val isSignal = msg.type == "SIGNAL_OFFER" || msg.type == "SIGNAL_ANSWER" ||
            msg.type == "SIGNAL_ICE" || msg.type == "SIGNAL_READY"
        if (!isSignal) return false
        // Llega en el hilo de OkHttp. Todo el trabajo de WebRTC se hace en NUESTRO hilo, y si
        // aún no terminó el arranque se guarda para aplicarlo después (ver initOnWorker).
        synchronized(pendingSignals) {
            if (!ready) {
                pendingSignals.add(msg)
                return true
            }
        }
        runCatching { sendExecutor.execute { routeSignal(msg) } }
        return true
    }

    /** Aplica un mensaje de señalización. SIEMPRE en [sendExecutor] (o tras el arranque). */
    private fun routeSignal(msg: SfNetMsg) {
        when (msg.type) {
            "SIGNAL_READY" -> if (isOfferer) createOffer() // el invitado ya puede negociar
            "SIGNAL_OFFER" -> onRemoteOffer(msg.sdp)
            "SIGNAL_ANSWER" -> onRemoteAnswer(msg.sdp)
            "SIGNAL_ICE" -> onRemoteCandidate(msg)
            else -> Unit
        }
    }

    private fun onRemoteOffer(sdp: String?) {
        if (sdp == null || isOfferer) return // el que ofrece no acepta ofertas (glare)
        val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peer?.setRemoteDescription(
            object : SimpleSdpObserver("setRemoteOffer") {
                override fun onSetSuccess() {
                    flushPendingCandidates()
                    peer?.createAnswer(
                        object : SimpleSdpObserver("createAnswer") {
                            override fun onCreateSuccess(desc: SessionDescription) {
                                peer?.setLocalDescription(SimpleSdpObserver("setLocalAnswer"), desc)
                                relay.sendSignal("SIGNAL_ANSWER", sdp = desc.description)
                            }
                        },
                        MediaConstraints(),
                    )
                }
            },
            desc,
        )
    }

    private fun onRemoteAnswer(sdp: String?) {
        if (sdp == null || !isOfferer) return
        peer?.setRemoteDescription(
            object : SimpleSdpObserver("setRemoteAnswer") {
                override fun onSetSuccess() = flushPendingCandidates()
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp),
        )
    }

    private fun onRemoteCandidate(msg: SfNetMsg) {
        val c = msg.candidate ?: return
        val candidate = IceCandidate(msg.sdpMid.orEmpty(), msg.sdpMLineIndex ?: 0, c)
        // Un candidato que llega antes que la descripción remota se REBOTA si se añade ya:
        // se guarda y se aplica cuando la descripción esté puesta.
        synchronized(pendingCandidates) {
            if (remoteDescriptionSet) peer?.addIceCandidate(candidate)
            else pendingCandidates.add(candidate)
        }
    }

    private fun flushPendingCandidates() {
        synchronized(pendingCandidates) {
            remoteDescriptionSet = true
            pendingCandidates.forEach { peer?.addIceCandidate(it) }
            pendingCandidates.clear()
        }
    }

    // ══════════════════════ DataChannel ══════════════════════

    private fun attachChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit

            override fun onStateChange() {
                val open = dc.state() == DataChannel.State.OPEN
                p2pOpen = open && !closed
                Log.d(SF_RTC_TAG, "DataChannel → ${dc.state()} (directo=$p2pOpen)")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                onPeerLine(String(bytes, StandardCharsets.UTF_8))
            }
        })
    }

    /** Un mensaje que llegó DIRECTO del rival. Mismo JSON que por el relay. */
    private fun onPeerLine(raw: String) {
        val msg = runCatching { PowJson.decodeFromString<SfNetMsg>(raw) }.getOrNull() ?: return
        // Autoridad del receptor, igual que en el relay: el estado que me manda mi rival es,
        // para mí, el del OPONENTE (el server hacía esta misma traducción).
        val out = if (msg.type == "PLAYER_STATE") msg.copy(type = "OPPONENT_STATE") else msg
        listener?.onMessage(out)
    }

    /**
     * Manda por el canal DIRECTO si está abierto; si no, [fallback] lo manda por el relay.
     * Este es el fallback automático: no hay que decidir nada arriba ni avisar al usuario.
     */
    private fun sendHot(payload: Map<String, Any?>, fallback: () -> Unit) {
        val dc = channel
        if (!p2pOpen || dc == null) {
            fallback()
            return
        }
        // `Map<String, Any?>` no tiene serializer seguro en kotlinx (y los fireballs son modelos).
        // `jsonOf` conserva el formato Gson del protocolo y recibe esos modelos ya como JsonElement.
        val line = jsonOf(payload)
        runCatching {
            sendExecutor.execute {
                val ok = runCatching {
                    dc.send(
                        DataChannel.Buffer(
                            ByteBuffer.wrap(line.toByteArray(StandardCharsets.UTF_8)),
                            false,
                        ),
                    )
                }.getOrDefault(false)
                if (!ok) {
                    // El canal murió: se marca cerrado y lo siguiente ya sale por el relay.
                    Log.w(SF_RTC_TAG, "envío directo falló → volviendo al relay")
                    p2pOpen = false
                }
            }
        }.onFailure { fallback() } // executor apagado (tras close)
    }

    // ══════════ SfNetTransport: PLANO DE CONTROL → siempre por el relay ══════════

    override fun createRoom() = relay.createRoom()
    override fun joinRoom(code: String) = relay.joinRoom(code)
    override fun quickMatch() = relay.quickMatch()
    override fun cancelQueue() = relay.cancelQueue()
    override fun listRooms() = relay.listRooms()
    override fun leaveRoom() = relay.leaveRoom()
    override fun requestJoin(code: String) = relay.requestJoin(code)
    override fun respondJoin(accept: Boolean) = relay.respondJoin(accept)
    override fun selectCharacter(name: String) = relay.selectCharacter(name)
    override fun selectMap(file: String) = relay.selectMap(file)
    override fun requestRematch() = relay.requestRematch()
    // El server los DIFUNDE a ambos y cambia la fase de la sala: no se pueden mover a P2P.
    override fun sendRoundEnded(winner: String, outcome: String) = relay.sendRoundEnded(winner, outcome)
    override fun sendMatchEnded(winner: String) = relay.sendMatchEnded(winner)

    // ══════════ SfNetTransport: CAMINO CALIENTE → P2P con respaldo ══════════

    override fun sendReady() = sendHot(mapOf("type" to "PLAYER_READY")) { relay.sendReady() }

    override fun sendDamage(damage: Int, strength: String, atkType: String) = sendHot(
        mapOf("type" to "PLAYER_DAMAGE", "damage" to damage, "strength" to strength, "atkType" to atkType),
    ) { relay.sendDamage(damage, strength, atkType) }

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
    ) = sendHot(
        sfPlayerStatePayload(x, y, state, frame, dir, hp, timer, fireballs, meter, audio),
    ) { relay.sendPlayerState(x, y, state, frame, dir, hp, timer, fireballs, meter, audio) }

    override fun close() {
        closed = true
        p2pOpen = false
        ready = false
        val dc = channel
        val pc = peer
        val f = factory
        channel = null
        peer = null
        factory = null
        listener = null
        // ⚡ GAMA BAJA: desmontar WebRTC también es pesado (libera hilos nativos y la librería).
        // Va al MISMO hilo de trabajo, que además lo ordena DESPUÉS de un arranque en curso —
        // liberar la fábrica mientras se está creando es un cierre nativo a medias.
        // `shutdown()` y no `shutdownNow()`: hay que dejar terminar el desmontaje.
        runCatching {
            sendExecutor.execute {
                runCatching { dc?.close() }
                runCatching { pc?.close() }
                runCatching { f?.dispose() }
            }
            sendExecutor.shutdown()
        }
        relay.close()
    }

    // ══════════════════════ observadores ══════════════════════

    private inner class PeerObserver : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            relay.sendSignal(
                "SIGNAL_ICE",
                candidate = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex,
            )
        }

        override fun onDataChannel(dc: DataChannel) {
            // Lado invitado: el canal lo abrió el host.
            channel = dc
            attachChannel(dc)
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.d(SF_RTC_TAG, "ICE → $state")
            if (state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.DISCONNECTED
            ) {
                // Hole punching fallido o enlace caído → todo sigue por el relay.
                p2pOpen = false
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    /** SdpObserver con los 4 métodos: solo se sobreescribe el que interesa en cada caso. */
    private open inner class SimpleSdpObserver(private val tag: String) : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            Log.w(SF_RTC_TAG, "$tag: createFailure $error")
        }
        override fun onSetFailure(error: String?) {
            Log.w(SF_RTC_TAG, "$tag: setFailure $error")
        }
    }

    companion object {
        const val SF_RTC_TAG = "SF-RTC"
        private const val CHANNEL_LABEL = "sf-fight"

        // Solo STUN (gratis y sin cuenta). NADA de TURN a propósito: cuando el hole punching
        // falla, el respaldo es el relay de Render que ya existe — así el online es gratis
        // de por vida y no depende de ningún servicio de pago.
        private val STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
    }
}
