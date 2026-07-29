package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHurtArea
import android.content.Context
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanDiscovery
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetTransport
import ovh.gabrielhuav.pow.features.streetfighter.data.SfWebRtcClient
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL de StreetFighterViewModel: 🌐 MULTIJUGADOR 1v1 (online / BT / LAN / P2P).
//
// Extraído de StreetFighterViewModel.kt (6220 líneas) en el refactor de tamaño de la Fase 5.
// Aquí vive TODO lo de red: conectar, lobby con aprobación, el listener del transporte, el
// manejo de mensajes entrantes, los snapshots del rival y el envío de estado/daño.
//
// ⚠️ Son EXTENSIONES del VM, no miembros: el estado (`_state`, los campos de simulación) sigue
// viviendo en la clase. NO recrees estas funciones como miembros — quedarían gemelas y ganaría
// el miembro en silencio (ver 09 §0, el gotcha miembro-vs-extensión que ya costó caro aquí).
// ─────────────────────────────────────────────────────────────────────────────

// ══════════════════════════════════════════════════════════════════
// 🆕 MULTIJUGADOR 1v1 — intents y manejo de red (relay puro)
// ══════════════════════════════════════════════════════════════════

/** Crea sala privada o se une con código. */
fun StreetFighterViewModel.startOnline(create: Boolean, code: String? = null) =
    connectOnline { c -> if (create) c.createRoom() else c.joinRoom(code.orEmpty()) }

/** SALA PÚBLICA: entra a la lista de espera; el server empareja al llegar otro. */
fun StreetFighterViewModel.startOnlineQuick() = connectOnline { c ->
    c.quickMatch()
    c.listRooms() // de paso, el resumen de partidas activas
}

/** Conecta (despertando el free tier de Render primero) y ejecuta la acción inicial. */
internal fun StreetFighterViewModel.connectOnline(onReady: (SfMatchClient) -> Unit) {
    if (isOnline) return
    _state.value = _state.value.copy(onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null)
    scope.launch(Dispatchers.IO) {
        val awake = SfMatchClient.warmupBlocking(
            environment.serverUrl,
            // 🆕 (2026-07-26) Solo si estaba dormido: la UI explica la espera en vez de
            // dejar al jugador mirando un "conectando…" durante un minuto sin motivo.
            onSleeping = {
                _state.value = _state.value.copy(onlineWaking = true)
            },
        )
        if (!awake) {
            _state.value = _state.value.copy(
                onlineStatus = SfOnlineStatus.OFF,
                onlineWaking = false,
                onlineError = "No se pudo despertar el servidor (plan gratis de Render). Intenta de nuevo.",
            )
            return@launch
        }
        _state.value = _state.value.copy(onlineWaking = false)
        val client = SfMatchClient()
        transport = client
        relayClient = client // 🆕 canal de señalización y respaldo del P2P
        client.connect(
            environment.serverUrl,
            object : SfNetTransport.Listener {
                override fun onOpen() {
                    onReady(client)
                }
                override fun onMessage(msg: SfNetMsg) {
                    // Llega en el hilo de OkHttp → se serializa con el tick en Main
                    scope.launch { handleNetMessage(msg) }
                }
                override fun onClosed() {
                    scope.launch { onNetDropped(null) }
                }
                override fun onFailure(reason: String) {
                    scope.launch { onNetDropped(reason) }
                }
            },
        )
    }
}

// ══════════════════════════════════════════════════════════════════
// 🆕 MULTIJUGADOR LOCAL por BLUETOOTH (SfBtClient; mismo flujo que online)
// Los permisos runtime (CONNECT/SCAN/ADVERTISE en Android 12+) los pide la
// View ANTES de llamar estos intents.
// ══════════════════════════════════════════════════════════════════

/**
 * 🆕 (2026-07-26) Intenta subir la pelea a una conexión DIRECTA teléfono-a-teléfono. A partir
 * de aquí Render solo hace de CUPIDO: intercambia el SDP y los candidatos ICE de los dos y se
 * aparta. La pelea deja de dar el rodeo hasta Oregón, que era de donde salía casi todo el lag.
 *
 * Solo ONLINE: en BT/LAN los teléfonos YA están conectados directo, no hay nada que mejorar.
 * El HOST hace la oferta y el invitado contesta (si ofrecieran los dos habría colisión).
 *
 * Si algo falla —WebRTC no arranca, el NAT es simétrico, el canal se cae a media pelea— no
 * pasa NADA visible: [SfWebRtcClient] reenvía por el relay de siempre. Por eso no hace falta
 * un TURN de pago y el online sigue siendo gratis.
 */
internal fun StreetFighterViewModel.maybeUpgradeToP2p(offerer: Boolean) {
    val s = _state.value
    if (s.btMode || s.lanMode) return
    (webRtc as? SfWebRtcClient)?.let {
        // Ya negociado. Si el que entra es un rival NUEVO (se fue uno y llegó otro), el canal
        // directo apunta al que se fue: se marca muerto para que todo salga por el relay.
        it.markPeerChanged()
        return
    }
    val relay = relayClient ?: return
    val client = SfWebRtcClient(appContext as Context, relay, isOfferer = offerer)
    webRtc = client
    transport = client
    client.start(makeNetListener())
    Log.d(StreetFighterViewModel.SF_NET_TAG, "P2P: negociando conexión directa (offerer=$offerer)")
}

/** Listener común de red para los transportes que no necesitan acción al abrir (BT). */
internal fun StreetFighterViewModel.makeNetListener() = object : SfNetTransport.Listener {
    override fun onOpen() = Unit
    override fun onMessage(msg: SfNetMsg) {
        scope.launch { handleNetMessage(msg) }
    }
    override fun onClosed() {
        scope.launch { onNetDropped(null) }
    }
    override fun onFailure(reason: String) {
        scope.launch { onNetDropped(reason) }
    }
}

/** ANFITRIÓN Bluetooth: visible + accept; el flujo sigue como online (ROOM_CREATED "BT"). */
fun StreetFighterViewModel.startBtHost() {
    if (isOnline) return
    stopBtScanInternal()
    _state.value = _state.value.copy(
        onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null, btMode = true,
        btError = null, btRetryAddress = null, btHandshaking = false,
        lanMode = false, lanLocalIp = null, lanHostAddress = null,
    )
    val client = SfBtClient(appContext as Context)
    transport = client
    client.startHost(makeNetListener())
}

/** BUSCAR RIVAL: abre el selector y llena btDevices (emparejados + discovery). */
fun StreetFighterViewModel.startBtScan() {
    if (isOnline) return
    stopBtScanInternal()
    _state.value = _state.value.copy(
        btPicking = true, btMode = true, btDevices = emptyList(), onlineError = null,
    )
    val scanner = SfBtClient(appContext as Context)
    btScanner = scanner
    val ok = scanner.startScan { dev ->
        _state.update { s ->
            if (s.btDevices.any { it.address == dev.address }) s
            else s.copy(btDevices = s.btDevices + dev)
        }
    }
    if (!ok) {
        stopBtScanInternal()
        _state.value = _state.value.copy(
            btPicking = false, btMode = false,
            onlineError = "Bluetooth apagado o no disponible: enciéndelo e intenta de nuevo.",
        )
    }
}

/** Cierra el selector de dispositivos sin conectar. */
fun StreetFighterViewModel.cancelBtScan() {
    stopBtScanInternal()
    _state.value = _state.value.copy(btPicking = false, btMode = false, btDevices = emptyList())
}

/** INVITADO Bluetooth: conecta al host elegido (el flujo sigue como online). */
fun StreetFighterViewModel.connectBtDevice(address: String) {
    if (isOnline) return
    stopBtScanInternal()
    _state.value = _state.value.copy(
        btPicking = false, onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null,
        btMode = true, btError = null, btRetryAddress = address, btHandshaking = false,
        lanMode = false, lanLocalIp = null, lanHostAddress = null,
    )
    val client = SfBtClient(appContext as Context)
    transport = client
    client.connectToHost(address, makeNetListener())
}

// ══════════════════════════════════════════════════════════════════
// 🆕 SERVIDOR LOCAL (LAN/Wi-Fi): el jugador hostea su propia sala, estilo LAN party.
// Sin permisos nuevos (solo INTERNET) ni cambios de Play Console.
// ══════════════════════════════════════════════════════════════════

/** HOST LAN: abre el servidor y muestra la IP a compartir (misma red Wi-Fi/hotspot). */
fun StreetFighterViewModel.startLanHost() {
    if (isOnline) return
    stopBtScanInternal()
    (lanDiscovery as? SfLanDiscovery)?.close() // cierra cualquier escucha previa antes de emitir la baliza
    val ips = SfLanClient.localIpAddresses()
    _state.value = _state.value.copy(
        onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null,
        btMode = false, lanMode = true,
        lanLocalIp = ips.firstOrNull(), lanLocalIps = ips, lanHostAddress = null,
        btError = null, btRetryAddress = null, btHandshaking = false,
    )
    val client = SfLanClient(appContext as Context)
    transport = client
    client.startHost(makeNetListener())
    // 🆕 (2026-07-26) Emite la baliza para que el invitado encuentre esta sala sin teclear IP.
    lanDiscovery = SfLanDiscovery(appContext as Context).also { it.startBeacon(android.os.Build.MODEL ?: "POW") }
}

/**
 * 🆕 (2026-07-26) INVITADO: escucha balizas LAN y va llenando `lanDiscovered` (tarjetas
 * tocables). Se llama al abrir la sección UNIRSE de LAN; `stopLanDiscovery` al salir/unirse.
 */
fun StreetFighterViewModel.startLanDiscovery() {
    if (isOnline) return
    (lanDiscovery as? SfLanDiscovery)?.close()
    _state.value = _state.value.copy(lanDiscovered = emptyList())
    lanDiscovery = SfLanDiscovery(appContext as Context).also { disc ->
        disc.startListening { game ->
            // Llega en hilo de fondo → re-postear a Main y deduplicar por IP.
            scope.launch {
                val cur = _state.value.lanDiscovered
                if (cur.none { it.ip == game.ip }) {
                    _state.value = _state.value.copy(lanDiscovered = cur + game)
                }
            }
        }
    }
}

/** Detiene la escucha/baliza LAN y limpia la lista de partidas halladas. */
fun StreetFighterViewModel.stopLanDiscovery() {
    (lanDiscovery as? SfLanDiscovery)?.close()
    lanDiscovery = null
    if (_state.value.lanDiscovered.isNotEmpty()) {
        _state.value = _state.value.copy(lanDiscovered = emptyList())
    }
}

/** INVITADO LAN: conecta a la IP que muestra la pantalla del host (tecleada o autodescubierta). */
fun StreetFighterViewModel.connectLanHost(addressRaw: String) {
    if (isOnline) return
    val address = addressRaw.trim()
    if (address.isEmpty()) return
    stopBtScanInternal()
    stopLanDiscovery() // ya elegiste una sala: deja de escuchar balizas
    _state.value = _state.value.copy(
        onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null,
        btMode = false, lanMode = true,
        lanLocalIp = null, lanHostAddress = address,
        btError = null, btRetryAddress = null, btHandshaking = false,
    )
    val client = SfLanClient(appContext as Context)
    transport = client
    client.connectToHost(address, makeNetListener())
}

/** Cierra el overlay de error BT/LAN y regresa (EXPLÍCITAMENTE) al selector offline. */
fun StreetFighterViewModel.dismissBtError() {
    _state.value = _state.value.copy(
        btError = null, btRetryAddress = null, btMode = false,
        lanMode = false, lanLocalIp = null, lanHostAddress = null,
    )
}

/**
 * Falla del enlace LOCAL (BT o LAN) ANTES de pelear → overlay bloqueante con REINTENTAR.
 * Regla: elegiste jugar por BT/LAN, así que JAMÁS se cae en silencio al selector offline
 * (nada de terminar peleando contra la IA creyendo que era tu rival); se reintenta
 * hasta que la conexión esté VERIFICADA o el jugador cancele explícitamente.
 */
internal fun StreetFighterViewModel.onLocalLinkFailed(reason: String?) {
    val s = _state.value
    (lanDiscovery as? SfLanDiscovery)?.close()
    lanDiscovery = null
    transport?.close() // si es el P2P, su close() cierra también el relay que decora
    transport = null
    webRtc = null
    relayClient = null
    remoteSnapshot = null
    netDamageQueue.clear()
    myOnlineChar = null
    oppOnlineChar = null
    onlineEndSent = false
    resetInternals()
    _state.value = StreetFighterState(
        btMode = s.btMode,
        lanMode = s.lanMode,
        btError = reason ?: "No se pudo conectar",
        btRetryAddress = s.btRetryAddress,
        lanHostAddress = s.lanHostAddress,
    )
}

// Detiene los descubrimientos LOCALES en curso (scan BT + baliza/escucha LAN). Se llama en
// todos los teardown/reinicio de sesión (cancelOnline, onCleared, y al arrancar host/join).
internal fun StreetFighterViewModel.stopBtScanInternal() {
    (btScanner as? SfBtClient)?.stopScan()
    (btScanner as? SfBtClient)?.close()
    btScanner = null
    (lanDiscovery as? SfLanDiscovery)?.close()
    lanDiscovery = null
}

/**
 * 🆕 Lobby estilo AoE2: al tocar una sala en 'waiting' se SOLICITA unirse (REQUEST_JOIN);
 * el ANFITRIÓN decide (ACEPTAR → ROOM_JOINED / RECHAZAR → JOIN_REJECTED y de regreso a
 * la lista de espera). El server saca al solicitante de la cola mientras el host decide.
 */
fun StreetFighterViewModel.requestJoinRoom(code: String) {
    val s = _state.value
    if (s.onlineStatus != SfOnlineStatus.WAITING_OPPONENT || s.roomCode != null || s.awaitingJoinOk) return
    transport?.requestJoin(code)
    _state.value = s.copy(awaitingJoinOk = true, queueNotice = null)
}

/** (HOST) Responde la solicitud de unión pendiente: aceptar mete al rival a la sala. */
fun StreetFighterViewModel.respondJoin(accept: Boolean) {
    transport?.respondJoin(accept)
    _state.value = _state.value.copy(joinRequestPending = false)
}

/** Sale de la sala y vuelve al selector offline (con error opcional a mostrar). */
fun StreetFighterViewModel.cancelOnline(errorMsg: String? = null) {
    countdownJob?.cancel()
    roomsRefreshJob?.cancel()
    // Lo fino es AVISAR antes de cerrar el WS: CANCEL_QUEUE saca de la lista de espera
    // (el server también limpia la cola en close, pero así no queda ventana) y LEAVE_ROOM
    // libera la sala; el server ignora el que no aplique.
    transport?.cancelQueue()
    transport?.leaveRoom()
    transport?.close() // si es el P2P, su close() cierra también el relay que decora
    transport = null
    webRtc = null
    relayClient = null
    stopBtScanInternal()
    remoteSnapshot = null
    netDamageQueue.clear()
    myOnlineChar = null
    oppOnlineChar = null
    onlineEndSent = false
    resetInternals()
    _state.value = StreetFighterState(onlineError = errorMsg)
}

/** El ANFITRIÓN elige el mapa (null = al azar entre DESBLOQUEADOS); el server lo replica. */
fun StreetFighterViewModel.chooseMapOnline(file: String?) {
    val unlocked = unlockedMaps()
    val pool = SF_CLASSIC_THEME.fullBackgrounds.map { it.file }.let { all ->
        if (devUnlockAll()) all else all.filter { it in unlocked }
    }
    val resolved = file ?: pool.randomOrNull() ?: return
    // No permitir hostear un mapa bloqueado (salvo Modo Dev)
    if (!devUnlockAll() && resolved !in unlocked && file != null) return
    transport?.selectMap(resolved)
}

internal fun StreetFighterViewModel.handleNetMessage(msg: SfNetMsg) {
    // 🆕 (2026-07-26) SEÑALIZACIÓN WebRTC: SIGNAL_OFFER/ANSWER/ICE son plomería para abrir
    // la conexión directa, no gameplay. Se los queda el transporte P2P y NO llegan al when.
    if ((webRtc as? SfWebRtcClient)?.consumeSignaling(msg) == true) return
    val s = _state.value
    when (msg.type) {
        "ROOM_CREATED" -> _state.value = s.copy(
            onlineStatus = SfOnlineStatus.WAITING_OPPONENT, roomCode = msg.code, isHost = true,
        )
        "ROOM_JOINED" -> {
            _state.value = s.copy(
                onlineStatus = SfOnlineStatus.SELECTING, roomCode = msg.code, isHost = false,
                awaitingJoinOk = false, queueNotice = null,
            )
            // 🆕 Ya somos 2 en la sala: a partir de aquí se puede negociar el P2P.
            maybeUpgradeToP2p(offerer = false)
        }
        "OPPONENT_JOINED" -> {
            (lanDiscovery as? SfLanDiscovery)?.stopBeacon() // 🆕 sala llena → deja de anunciarse por UDP
            if (s.battleEnded || !s.inCharacterSelect) {
                // Un rival NUEVO entró cuando la pelea anterior ya corrió/terminó (p. ej.
                // en BT el host sigue aceptando tras un abandono): sala en limpio, como
                // en REMATCH_ACCEPTED — sin esto quedaba SELECTING sobre el fin de pelea.
                resetInternals()
                onlineEndSent = false
                remoteSnapshot = null
                netDamageQueue.clear()
                myOnlineChar = null
                oppOnlineChar = null
                _state.value = StreetFighterState(
                    onlineStatus = SfOnlineStatus.SELECTING,
                    roomCode = s.roomCode,
                    isHost = s.isHost,
                    btMode = s.btMode,
                    lanMode = s.lanMode,
                    lanLocalIp = s.lanLocalIp,
                    lanLocalIps = s.lanLocalIps,
                )
            } else {
                _state.value = s.copy(
                    onlineStatus = SfOnlineStatus.SELECTING, joinRequestPending = false,
                )
            }
            // 🆕 El HOST hace la OFERTA en cuanto entra el rival (el invitado contesta).
            maybeUpgradeToP2p(offerer = true)
        }
        // Sala pública: en lista de espera (roomCode null → la UI muestra "buscando rival")
        "QUEUED" -> {
            _state.value = s.copy(
                onlineStatus = SfOnlineStatus.WAITING_OPPONENT, roomCode = null,
                awaitingJoinOk = false,
            )
            startRoomsRefresh()
        }
        // (BT) Socket conectado; verificando con el anfitrión (progreso en la UI)
        "BT_HANDSHAKE" -> _state.value = s.copy(btHandshaking = true)
        // ─── 🆕 Lobby con aprobación ───
        // (HOST) alguien pide unirse → la View muestra ACEPTAR/RECHAZAR
        "JOIN_REQUESTED" -> _state.value = s.copy(joinRequestPending = true)
        "JOIN_REQUEST_CANCELLED" -> _state.value = s.copy(joinRequestPending = false)
        // (INVITADO) rechazado/sala llena → vuelve a la lista de espera con el aviso
        "JOIN_REJECTED" -> {
            transport?.quickMatch() // re-entra a la cola pública
            _state.value = s.copy(awaitingJoinOk = false, queueNotice = msg.message)
        }
        "ROOMS_LIST" -> _state.value = s.copy(
            activeRooms = msg.rooms ?: emptyList(),
            queueCount = msg.queue ?: 0,
        )
        "ERROR" -> cancelOnline(msg.message ?: "Error del servidor")
        "CHARACTERS_SELECTED" -> {
            val oppName = if (s.isHost) msg.char2 else msg.char1
            // Parse defensivo: un id inválido/eliminado (p. ej. "RYU"/"KEN" de un cliente viejo) → PRANKEDY.
            oppOnlineChar = oppName?.let { n -> runCatching { SfFighterId.valueOf(n) }.getOrNull() }
                ?: SfFighterId.PRANKEDY
            _state.value = s.copy(onlineStatus = SfOnlineStatus.WAITING_MAP)
        }
        "MAP_SELECTED" -> {
            _state.value = s.copy(
                onlineStatus = SfOnlineStatus.COUNTDOWN,
                onlineMapFile = msg.map,
                onlineCountdown = 3,
            )
            countdownJob?.cancel()
            countdownJob = scope.launch {
                for (n in 2 downTo 1) {
                    delay(1000)
                    _state.value = _state.value.copy(onlineCountdown = n)
                }
            }
        }
        "FIGHT_START" -> startOnlineBattle()
        // 🆕 (2026-07-25) El rival ya cargó sus atlas: parte de la barrera "ambos listos".
        "PLAYER_READY" -> {
            Log.d(StreetFighterViewModel.SF_NET_TAG, "PLAYER_READY del rival recibido")
            peerReady = true
            maybeStartAfterReady()
        }
        "OPPONENT_STATE" -> remoteSnapshot = msg
        "PLAYER_DAMAGE" -> netDamageQueue.add(msg)
        "ROUND_ENDED" -> roundEndedFromNet(msg.winner, msg.outcome) // 🆕 fin de RONDA intermedia
        "MATCH_ENDED" -> endFromNet(msg.winner)
        "REMATCH_REQUESTED" -> _state.value = s.copy(opponentWantsRematch = true)
        "REMATCH_ACCEPTED" -> {
            resetInternals()
            onlineEndSent = false
            remoteSnapshot = null
            netDamageQueue.clear()
            myOnlineChar = null
            oppOnlineChar = null
            _state.value = StreetFighterState(
                onlineStatus = SfOnlineStatus.SELECTING,
                roomCode = s.roomCode,
                isHost = s.isHost,
                btMode = s.btMode, // la revancha BT/LAN sigue en su transporte
                lanMode = s.lanMode,
                lanLocalIp = s.lanLocalIp,
                lanLocalIps = s.lanLocalIps,
            )
        }
        "OPPONENT_LEFT", "OPPONENT_DISCONNECTED" -> {
            if (s.onlineStatus == SfOnlineStatus.FIGHTING && !s.battleEnded) {
                // Victoria por abandono (decide el COMBATE, no solo la ronda)
                onlineEndSent = true
                matchOver = true
                endMenuAtMs = 0L
                roundResetAtMs = 0L
                _state.value = s.copy(
                    battleEnded = true, winnerIndex = 0, showEndMenu = true,
                    onlineStatus = SfOnlineStatus.OPPONENT_LEFT,
                    playerRoundWins = StreetFighterViewModel.ROUNDS_TO_WIN,
                )
            } else if (s.isHost) {
                // El invitado se fue en la antesala: la sala sigue viva esperando a otro
                _state.value = s.copy(
                    onlineStatus = SfOnlineStatus.WAITING_OPPONENT, opponentWantsRematch = false,
                )
            } else if (s.btMode || s.lanMode) {
                // BT/LAN: se perdió al anfitrión en la antesala → overlay de REINTENTAR
                onLocalLinkFailed("Se perdió la conexión con el anfitrión")
            } else {
                cancelOnline("El anfitrión cerró la sala")
            }
        }
    }
}

/**
 * Mientras estás en la LISTA DE ESPERA pública, re-pide LIST_ROOMS cada StreetFighterViewModel.ROOMS_REFRESH_MS
 * (resumen + tarjetas de salas). Se auto-detiene al emparejarte/unirte/cancelar.
 */
internal fun StreetFighterViewModel.startRoomsRefresh() {
    roomsRefreshJob?.cancel()
    roomsRefreshJob = scope.launch {
        while (isActive) {
            delay(StreetFighterViewModel.ROOMS_REFRESH_MS)
            val st = _state.value
            if (st.onlineStatus != SfOnlineStatus.WAITING_OPPONENT || st.roomCode != null) break
            transport?.listRooms()
        }
    }
}

/** FIGHT_START: arranca la pelea online. El anfitrión pelea a la IZQUIERDA. */
internal fun StreetFighterViewModel.startOnlineBattle() {
    val s = _state.value
    Log.d(StreetFighterViewModel.SF_NET_TAG, "FIGHT_START recibido → cargando y esperando al rival")
    resetInternals()
    // 🆕 (2026-07-25) BARRERA "AMBOS LISTOS": no arrancar el intro/reloj hasta que AMBOS
    // teléfonos cargaron sus atlas. releaseReadyBarrier fija roundIntroUntilMs al liberarse.
    roundIntroUntilMs = 0L
    armReadyBarrier()
    onlineEndSent = false
    remoteSnapshot = null
    netDamageQueue.clear()
    val base = StreetFighterState()
    val my = myOnlineChar ?: SfFighterId.PRANKEDY
    val opp = oppOnlineChar ?: SfFighterId.PRANKEDY
    val leftX = base.player.x
    val rightX = base.cpu.x
    _state.value = base.copy(
        player = base.player.copy(
            id = my,
            x = if (s.isHost) leftX else rightX,
            direction = if (s.isHost) SfDirection.RIGHT else SfDirection.LEFT,
        ),
        cpu = base.cpu.copy(
            id = opp,
            x = if (s.isHost) rightX else leftX,
            direction = if (s.isHost) SfDirection.LEFT else SfDirection.RIGHT,
        ),
        inCharacterSelect = false,
        onlineStatus = SfOnlineStatus.FIGHTING,
        roomCode = s.roomCode,
        isHost = s.isHost,
        onlineMapFile = s.onlineMapFile,
        btMode = s.btMode, // conservar el transporte para overlays post-pelea
        lanMode = s.lanMode,
        lanLocalIp = s.lanLocalIp,
        lanLocalIps = s.lanLocalIps,
    )
}

/**
 * Aplica el último OPPONENT_STATE al peleador remoto (índice 1).
 * 🆕 INTERPOLADO (SESIÓN 4): el snapshot llega a ~15 Hz; la posición se ALISA con un lerp
 * exponencial por tick (StreetFighterViewModel.NET_LERP_RATE) en vez de saltar cada 4 ticks. Si la distancia
 * supera StreetFighterViewModel.NET_SNAP_DIST (reset de ronda/teleport) se SNAPEA — no perseguirlo lerpeando.
 * Pose/frame/dirección/HP se aplican DIRECTO (interpolarlos falsearía la pelea).
 */
internal fun StreetFighterViewModel.applyRemoteSnapshot(sim: StreetFighterViewModel.Sim, now: Long, dt: Float) {
    val rs = remoteSnapshot ?: return
    if (rs !== lastSeenSnapshot) {
        lastSeenSnapshot = rs
        remoteSnapshotAtMs = now // edad del snapshot (para extrapolar sus proyectiles)
        // 🆕 (2026-07-26) VOCES DEL RIVAL. Las eligió SU teléfono (los packs sortean con
        // `.random()`) y viajan en el snapshot, así los dos oímos el MISMO clip y no dos
        // variantes distintas del mismo evento. Solo en el snapshot NUEVO: el objeto se
        // conserva entre ticks y aquí se re-entra ~30 veces por segundo.
        rs.audio?.forEach { _soundEvents.tryEmit(it) }
    }
    // Los estados NUEVOS viajan como enum.name; un cliente viejo que no los conozca
    // conserva el estado anterior en vez de romperse (parse defensivo ya existente).
    val st = rs.state?.let { n -> runCatching { SfFighterState.valueOf(n) }.getOrNull() } ?: sim.p1.state
    // 🆕 (2026-07-21) Medidor del rival (opcional: un cliente viejo no lo manda).
    val remoteMeter = rs.meter?.coerceIn(0, SfConstants.SUPER_METER_MAX) ?: sim.p1.superMeter
    val tx = rs.x ?: sim.p1.x
    val ty = rs.y ?: sim.p1.y
    val far = abs(tx - sim.p1.x) > StreetFighterViewModel.NET_SNAP_DIST || abs(ty - sim.p1.y) > StreetFighterViewModel.NET_SNAP_DIST
    val alpha = if (far) 1f else (dt * StreetFighterViewModel.NET_LERP_RATE).coerceAtMost(1f)
    sim.p1 = sim.p1.copy(
        x = sim.p1.x + (tx - sim.p1.x) * alpha,
        y = sim.p1.y + (ty - sim.p1.y) * alpha,
        state = st,
        animationFrame = rs.frame ?: 0,
        direction = if ((rs.dir ?: 1) >= 0) SfDirection.RIGHT else SfDirection.LEFT,
        hitPoints = rs.hp ?: sim.p1.hitPoints,
        superMeter = remoteMeter,
    )
    // 🆕 (2026-07-26) SFX del rival que se DEDUCEN de su pose (no hace falta mandarlos).
    // Aquí el estado se asigna DIRECTO, sin pasar por changeState — que es donde el dueño
    // del peleador emite su audio. Sin esto, el rival peleaba en silencio en tu teléfono.
    emitRemoteStateSfx(st)
    // 🆕 SINCRONÍA DEL TIMER: el HOST manda su reloj en PLAYER_STATE; el invitado lo
    // ADOPTA solo si el drift acumulado es >= StreetFighterViewModel.TIMER_RESYNC_DIFF (el conteo local sigue
    // bajando suave; esto solo re-ancla). GRACIA post-reset: un timer viejo en vuelo de
    // la ronda anterior NO debe pisar el 99 recién reseteado.
    if (!_state.value.isHost && !sim.battleEnded && now >= roundGraceUntilMs) {
        rs.timer?.let { t ->
            if (abs(time - t) >= StreetFighterViewModel.TIMER_RESYNC_DIFF) {
                time = t
                timeTimerMs = now
            }
        }
    }
    // Si su propio estado reporta 0 HP, gané la RONDA (él manda ROUND/MATCH_ENDED; esto
    // lo adelanta). GRACIA post-reset: ignora snapshots viejos en vuelo con hp=0.
    if ((rs.hp ?: 1) <= 0 && !sim.battleEnded && now >= roundGraceUntilMs) {
        // El KO lo simuló el rival (llega por snapshot): solo PERFECT es computable aquí
        // (mi HP al máximo); SUPER/COMBO viajan en el `outcome` de ROUND_ENDED si aplica.
        endRound(sim, winnerIdx = 0, now = now, computeRoundOutcome(sim, 0, koState = null, byTime = false))
    }
}

/**
 * 🆕 (2026-07-26) SFX del peleador REMOTO que NO viajan por red porque son DETERMINISTAS:
 * se deducen de su `state`, que ya viene en cada snapshot. Solo suenan en la TRANSICIÓN —
 * el mismo estado se repite en todos los snapshots mientras dura la animación, y sin este
 * filtro el whoosh sonaría ~15 veces por golpe.
 *
 * Lo que NO está aquí, a propósito:
 *  · las VOCES (packs): se eligen al azar, así que viajan en `SfNetMsg.audio`;
 *  · los IMPACTOS (`*-hit`): ya los emiten AMBOS lados (el atacante en applyAttackHit y
 *    el receptor al aplicar el daño de red), así que añadirlos aquí los duplicaría.
 */
internal fun StreetFighterViewModel.emitRemoteStateSfx(st: SfFighterState) {
    if (st == lastRemoteSfxState) return
    lastRemoteSfxState = st
    when (st) {
        // Golpe al aire: el mismo whoosh que emite su dueño al entrar al estado
        SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
        SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
        SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
        SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
        SfFighterState.LONG_KICK, SfFighterState.OVERHEAD, SfFighterState.GRAB,
        -> attackMeta[st]?.let {
            _soundEvents.tryEmit("${it.strength.name.lowercase()}-attack")
        }
        SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK -> _soundEvents.tryEmit("medium-attack")
        SfFighterState.STUN -> _soundEvents.tryEmit("land") // golpe seco al caer mareado
        // Aterrizaje: su dueño lo emite al ENTRAR a JUMP_LAND (ver runStateHandler), que es
        // justo la transición que se detecta aquí.
        SfFighterState.JUMP_LAND -> _soundEvents.tryEmit("land")
        else -> Unit
    }
}

/** Aplica a MI peleador el daño que me mandó el rival (yo decido bloqueo con MI estado). */
internal fun StreetFighterViewModel.processNetDamage(sim: StreetFighterViewModel.Sim, now: Long) {
    // Ronda terminada o gracia post-reset: el daño en vuelo del rival ya no cuenta
    if (sim.battleEnded || now < roundGraceUntilMs) {
        netDamageQueue.clear()
        return
    }
    while (true) {
        val m = netDamageQueue.removeFirstOrNull() ?: break
        val strength = m.strength?.let { n -> runCatching { SfAttackStrength.valueOf(n) }.getOrNull() }
            ?: SfAttackStrength.LIGHT
        val type = m.atkType?.let { n -> runCatching { SfAttackType.valueOf(n) }.getOrNull() }
            ?: SfAttackType.PUNCH
        val hitX = (sim.p0.x + sim.p1.x) / 2f
        val hitY = minOf(sim.p0.y, sim.p1.y) - 54f
        applyAttackHit(sim, attackerIdx = 1, strength, type, SfHurtArea.BODY, hitX to hitY, now)
    }
}

/** Manda MI estado al rival cada ~66 ms (posición, pose, frame, HP, 🆕 timer del host y mis proyectiles). */
internal fun StreetFighterViewModel.sendNetState(sim: StreetFighterViewModel.Sim, now: Long) {
    // 🆕 (2026-07-26) Las VOCES no pueden esperar a la ventana de 66 ms: un clip encolado
    // justo después de un envío se perdería hasta 66 ms, y si la ronda termina en medio se
    // perdería del todo. Si hay voces pendientes se manda YA (son eventos raros: un puñado
    // por pelea, no engordan el tráfico).
    if (now - lastNetSendMs < 66 && pendingNetAudio.isEmpty()) return
    lastNetSendMs = now
    val f = sim.p0
    transport?.sendPlayerState(
        x = f.x, y = f.y, state = f.state.name, frame = f.animationFrame,
        dir = f.direction.sign, hp = f.hitPoints,
        // 🆕 SINCRONÍA: solo el HOST es autoridad del reloj (el relay lo pasa tal cual)
        timer = if (_state.value.isHost) time else null,
        fireballs = sim.fireballs.filter { it.ownerIndex == 0 }.map {
            SfNetFireball(it.x, it.y, it.direction.sign, it.strength.name, it.state.name, it.animationFrame)
        },
        // 🆕 (2026-07-21) Medidor de súper: sin esto la barra dorada del rival se veía
        // siempre vacía en línea (y no se entendía cuándo podía soltar súper/fatality).
        meter = f.superMeter,
        // 🆕 (2026-07-26) Voces que emitió MI peleador desde el envío anterior.
        // ⚡ GAMA BAJA: `emptyList()` es un singleton — no se asigna una lista nueva en cada
        // envío (15 por segundo) solo para decir "no hay voces", que es el caso normal.
        audio = if (pendingNetAudio.isEmpty()) emptyList() else pendingNetAudio.toList(),
    )
    if (pendingNetAudio.isNotEmpty()) pendingNetAudio.clear()
}

/**
 * Añade los proyectiles del RIVAL (render-only; su dueño calcula las colisiones).
 * 🆕 EXTRAPOLADOS (SESIÓN 4): entre snapshots (~66 ms) los ACTIVE avanzan a su velocidad
 * nominal según la EDAD del snapshot (tope StreetFighterViewModel.NET_FB_MAX_AGE_S) — antes se congelaban 4 ticks.
 */
internal fun StreetFighterViewModel.appendRemoteFireballs(sim: StreetFighterViewModel.Sim, now: Long) {
    val fbs = remoteSnapshot?.fireballs ?: return
    val ageS = ((now - remoteSnapshotAtMs).coerceAtLeast(0L) / 1000f).coerceAtMost(StreetFighterViewModel.NET_FB_MAX_AGE_S)
    fbs.forEach { nf ->
        val strength = runCatching { SfAttackStrength.valueOf(nf.strength) }.getOrDefault(SfAttackStrength.LIGHT)
        val fbState = runCatching { SfFireballState.valueOf(nf.state) }.getOrDefault(SfFireballState.ACTIVE)
        val dir = if (nf.dir >= 0) SfDirection.RIGHT else SfDirection.LEFT
        // Solo los ACTIVOS vuelan; un COLLIDED se queda donde reventó
        val x = if (fbState == SfFireballState.ACTIVE) {
            nf.x + strength.fireballVelocity * dir.sign * ageS
        } else {
            nf.x
        }
        sim.fireballs.add(
            SfFireball(
                ownerIndex = 1,
                x = x, y = nf.y,
                direction = dir,
                strength = strength,
                velocity = 0f,
                state = fbState,
                animationFrame = nf.frame,
            ),
        )
    }
}

/**
 * 🆕 FIREBALL-VS-FIREBALL (SESIÓN 4): dos proyectiles ACTIVOS de DUEÑOS OPUESTOS que se
 * traslapan REVIENTAN los dos (pose COLLIDED, como al pegar). Offline cancela ambos de
 * verdad; online el del rival es render-only — aquí se revienta MI copia y el rival hará
 * lo propio con la suya en su lado (~66 ms; el parpadeo de su copia es aceptado).
 */
internal fun StreetFighterViewModel.collideFireballPairs(sim: StreetFighterViewModel.Sim, now: Long) {
    if (sim.fireballs.size < 2) return
    for (i in sim.fireballs.indices) {
        val a = sim.fireballs[i]
        if (a.state != SfFireballState.ACTIVE) continue
        for (j in i + 1 until sim.fireballs.size) {
            val b = sim.fireballs[j]
            if (b.state != SfFireballState.ACTIVE || b.ownerIndex == a.ownerIndex) continue
            val boxA = fireballBox.toWorld(a.x, a.y, a.direction)
            val boxB = fireballBox.toWorld(b.x, b.y, b.direction)
            if (!boxA.overlaps(boxB)) continue
            sim.fireballs[i] = collidedFireball(a, now)
            sim.fireballs[j] = collidedFireball(b, now)
            _soundEvents.tryEmit("light-punch-hit")
            return // a lo sumo un cruce por tick (2 pares simultáneos es rarísimo)
        }
    }
}

internal fun StreetFighterViewModel.collidedFireball(fb: SfFireball, now: Long): SfFireball = fb.copy(
    state = SfFireballState.COLLIDED,
    animationFrame = 0,
    velocity = fb.velocity * 0.33f,
    animationTimerMs = now + (fireballCollidedDelays[0] * SfConstants.FRAME_TIME_MS).toLong(),
)

/** Índice local → lado de red ("p1" = anfitrión), para ROUND/MATCH_ENDED. */
internal fun StreetFighterViewModel.sideOf(winnerIdx: Int): String {
    val iAmP1 = _state.value.isHost
    return if (winnerIdx == 0) (if (iAmP1) "p1" else "p2") else (if (iAmP1) "p2" else "p1")
}

/** Lado de red → índice local (reconciliación de ROUND/MATCH_ENDED entrantes). */
internal fun StreetFighterViewModel.idxOf(side: String?): Int = when (side) {
    "p1" -> if (_state.value.isHost) 0 else 1
    "p2" -> if (_state.value.isHost) 1 else 0
    else -> 0
}

// Puentes públicos mínimos para que el ViewModel Hilt de :app conecte los hooks del motor.
fun StreetFighterViewModel.androidApplyRemoteSnapshot(sim: StreetFighterViewModel.Sim, now: Long, dt: Float) =
    applyRemoteSnapshot(sim, now, dt)

fun StreetFighterViewModel.androidProcessNetDamage(sim: StreetFighterViewModel.Sim, now: Long) =
    processNetDamage(sim, now)

fun StreetFighterViewModel.androidAppendRemoteFireballs(sim: StreetFighterViewModel.Sim, now: Long) =
    appendRemoteFireballs(sim, now)

fun StreetFighterViewModel.androidSendNetState(sim: StreetFighterViewModel.Sim, now: Long) =
    sendNetState(sim, now)

fun StreetFighterViewModel.androidCancelOnline(errorMsg: String? = null) = cancelOnline(errorMsg)
fun StreetFighterViewModel.androidOnLocalLinkFailed(reason: String?) = onLocalLinkFailed(reason)
fun StreetFighterViewModel.androidStopBtScan() = stopBtScanInternal()
