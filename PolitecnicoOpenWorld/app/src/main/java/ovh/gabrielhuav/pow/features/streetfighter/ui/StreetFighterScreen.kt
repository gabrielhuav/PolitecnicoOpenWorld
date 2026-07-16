package ovh.gabrielhuav.pow.features.streetfighter.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButton
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PowButton
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel

// View Compose PURA del modo de pelea 1v1: observa el estado con collectAsState() y solo
// emite intenciones al VM (contrato MVVM, README for IAS 01/09).
//
// SEPARACIÓN DE CAPAS: esta View NO conoce recortes ni rutas de assets — todo viene del
// SfTheme (data/SfTheme.kt). Hoy usa SF_CLASSIC_THEME (assets del clon SF, temporales);
// la migración a assets propios de POW = crear otro SfTheme + JSONs de personajes, sin
// tocar esta View ni el VM. Ver README for IAS/ASSETS_STREETFIGHTER_MIGRACION.md.
//
// CONTROLES = los de POW: joystick (←→ caminar, ↑ saltar, ↓ agacharse; ↓↘→ + puño =
// especial) + el MISMO diamante Xbox A/B/X/Y de ActionButtonsController:
//   X (izq, azul) = puño ligero · Y (arriba, amarillo) = puño medio ·
//   B (der, rojo) = puño fuerte · A (abajo, verde) = patada (fuerza según el joystick:
//   neutro = ligera, adelante = media, atrás = fuerte).
// (2026-07-15) El modo es PÚBLICO; el Modo Desarrollador solo desbloquea a RYU/KEN
// (roster gateado por el VM: selectableFighters).

@Composable
fun StreetFighterScreen(
    onExitToMap: () -> Unit,
    viewModel: StreetFighterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val theme = remember { SF_CLASSIC_THEME }

    // ---- Bitmaps del tema + sheets de los peleadores ACTUALES (decodificados una vez) ----
    val playerId = state.player.id
    val cpuId = state.cpu.id
    val images = remember(theme, playerId, cpuId) {
        val m = theme.imageFiles.associateWith { name ->
            context.assets.open(theme.imagesDir + name).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
        }.toMutableMap()
        // Hojas de los peleadores: EMPAQUETADAS (Ryu/Ken/Prankedy) o COMPARTIDAS con el mundo
        // (armadas en runtime desde SPRITES/* por SfSharedSheets); key = nombre del spriteAsset
        listOf(playerId, cpuId).distinct().forEach { id ->
            m[id.spriteAsset.substringAfterLast('/')] = SfSharedSheets.sheetFor(context, id).asImageBitmap()
        }
        m
    }
    val playerData = remember(playerId) { SfFrameCatalog.load(context, playerId) }
    val cpuData = remember(cpuId) { SfFrameCatalog.load(context, cpuId) }

    // ---- Selección en 2 pasos: PELEADOR → MAPA (el mapa lo elige el jugador) ----
    var pendingFighter by remember { mutableStateOf<SfFighterId?>(null) }
    var pendingRival by remember { mutableStateOf<SfFighterId?>(null) } // 🆕 rival elegible offline
    var chosenBgFile by remember { mutableStateOf(theme.fullBackgrounds.firstOrNull()?.file) }
    LaunchedEffect(state.inCharacterSelect) {
        if (state.inCharacterSelect) { pendingFighter = null; pendingRival = null }
    }
    // ONLINE: manda el mapa que eligió el ANFITRIÓN (viene por red en el estado)
    val effectiveBgFile = if (state.onlineStatus != SfOnlineStatus.OFF && state.onlineMapFile != null) {
        state.onlineMapFile
    } else {
        chosenBgFile
    }
    val bgImage = remember(effectiveBgFile) {
        effectiveBgFile?.let { name ->
            runCatching {
                context.assets.open(theme.imagesDir + name).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            }.getOrNull()
        }
    }

    // ---- Sonidos del tema (SoundPool efectos + MediaPlayer música) ----
    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }
    val soundIds = remember(theme) {
        theme.soundKeys.associateWith { key ->
            context.assets.openFd("${theme.soundsDir}$key.ogg").use { soundPool.load(it, 1) }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.soundEvents.collect { key ->
            soundIds[key]?.let { soundPool.play(it, 1f, 1f, 1, 0, 1f) }
        }
    }
    val musicPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        runCatching {
            context.assets.openFd(theme.soundsDir + theme.musicFile).use { fd ->
                musicPlayer.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            musicPlayer.isLooping = true
            musicPlayer.setVolume(theme.musicVolume, theme.musicVolume)
            musicPlayer.prepare()
            musicPlayer.start()
        }
        onDispose {
            runCatching { musicPlayer.stop() }
            musicPlayer.release()
            soundPool.release()
        }
    }

    // PAUSA AUTOMÁTICA al bloquear el celular / minimizar la app: el juego queda en
    // PAUSA (overlay con "Continuar") y la música se silencia; al volver, la música
    // regresa pero la pelea sigue pausada hasta que el jugador continúe.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    viewModel.forcePause()
                    runCatching { if (musicPlayer.isPlaying) musicPlayer.pause() }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    runCatching { musicPlayer.start() }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Textos del banner de RONDA (i18n; la fuente arcade solo tiene A-Z/0-9)
    val roundBannerText = stringResource(R.string.sf_round_banner, state.roundNumber)
    val fightBannerText = stringResource(R.string.sf_fight_banner)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ---- Escena completa (mundo + HUD) en un Canvas ----
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScene(theme, state, images, playerData, cpuData, bgImage, roundBannerText, fightBannerText)
        }

        // ---- Controles de POW: joystick + diamante Xbox (ocultos durante la selección) ----
        if (!state.inCharacterSelect) {
            JoystickController(
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                onMove = viewModel::onJoystickMove,
            )
            // OJO: padding-end grande a propósito — en landscape la barra de navegación/gestos
            // del sistema vive en el borde DERECHO y se comía los toques del botón B (los combos
            // "no salían" porque esos taps nunca llegaban a la app). Separado del borde, todos
            // los toques caen dentro del juego.
            FighterXboxButtons(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 16.dp),
                onPunch = { strength -> viewModel.onAttackPressed(strength, SfAttackType.PUNCH) },
                onKick = viewModel::onKickPressed,
            )
        }

        // ---- Selección pre-pelea: paso 1 PELEADOR, paso 2 MAPA (offline y online) ----
        var showOnlineMenu by remember { mutableStateOf(false) }
        if (state.inCharacterSelect) {
            when (state.onlineStatus) {
                SfOnlineStatus.CONNECTING -> OnlineInfoOverlay(
                    title = stringResource(R.string.sf_mp_connecting_title),
                    // BT/LAN en 2 etapas para que se ENTIENDA qué pasa: conectando → verificando
                    subtitle = stringResource(
                        when {
                            (state.btMode || state.lanMode) && state.btHandshaking -> R.string.sf_bt_handshake_sub
                            state.lanMode -> R.string.sf_lan_connecting_sub
                            state.btMode -> R.string.sf_bt_connecting_sub
                            else -> R.string.sf_mp_connecting_sub
                        },
                    ),
                    onCancel = { viewModel.cancelOnline() },
                )
                SfOnlineStatus.WAITING_OPPONENT -> if (state.lanMode) {
                    // SERVIDOR LOCAL: mostrar la IP a compartir (misma red Wi-Fi/hotspot)
                    OnlineInfoOverlay(
                        title = stringResource(R.string.sf_lan_host_title),
                        subtitle = state.lanLocalIp?.let { stringResource(R.string.sf_lan_host_sub, it) }
                            ?: stringResource(R.string.sf_lan_no_ip),
                        onCancel = { viewModel.cancelOnline() },
                    )
                } else if (state.btMode) {
                    // ANFITRIÓN Bluetooth: visible + esperando que el rival conecte
                    OnlineInfoOverlay(
                        title = stringResource(R.string.sf_bt_host_title),
                        subtitle = stringResource(R.string.sf_bt_host_sub),
                        onCancel = { viewModel.cancelOnline() },
                    )
                } else if (state.roomCode != null) {
                    OnlineInfoOverlay(
                        title = stringResource(R.string.sf_mp_room, state.roomCode!!),
                        subtitle = stringResource(R.string.sf_mp_waiting_sub),
                        onCancel = { viewModel.cancelOnline() },
                    )
                } else {
                    // SALA PÚBLICA (lista de espera): resumen + salas activas como tarjetas;
                    // tocar una en 'waiting' SOLICITA unirse (el anfitrión decide, estilo AoE2)
                    PublicQueueOverlay(
                        rooms = state.activeRooms,
                        queueCount = state.queueCount,
                        awaitingHost = state.awaitingJoinOk,
                        notice = state.queueNotice,
                        onJoinRoom = viewModel::requestJoinRoom,
                        onCancel = { viewModel.cancelOnline() },
                    )
                }
                SfOnlineStatus.SELECTING -> CharacterSelectOverlay(
                    fighters = viewModel.selectableFighters,
                    subtitle = stringResource(R.string.sf_mp_pick_sub, state.roomCode ?: ""),
                    onSelect = viewModel::selectCharacter,
                )
                SfOnlineStatus.WAITING_MAP -> if (state.isHost) {
                    StageSelectOverlay(theme = theme, onSelect = viewModel::chooseMapOnline, onBack = null)
                } else {
                    OnlineInfoOverlay(
                        title = stringResource(R.string.sf_mp_room, state.roomCode ?: ""),
                        subtitle = stringResource(R.string.sf_mp_host_choosing_map),
                        onCancel = { viewModel.cancelOnline() },
                    )
                }
                SfOnlineStatus.COUNTDOWN -> Unit // el número gigante se dibuja abajo
                else -> {
                    // OFFLINE: flujo de 3 pasos — TU peleador → el RIVAL (🆕) → el mapa
                    val fighter = pendingFighter
                    val rival = pendingRival
                    when {
                        fighter == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters,
                            subtitle = state.onlineError,
                            onSelect = { pendingFighter = it },
                            onOnline = { showOnlineMenu = true },
                        )
                        rival == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters,
                            subtitle = stringResource(R.string.sf_choose_rival),
                            onSelect = { pendingRival = it },
                        )
                        else -> StageSelectOverlay(
                            theme = theme,
                            onSelect = { file ->
                                chosenBgFile = file ?: theme.fullBackgrounds.randomOrNull()?.file
                                viewModel.selectCharacter(fighter, rival)
                            },
                            onBack = { pendingFighter = null; pendingRival = null },
                        )
                    }
                }
            }
        }

        // ---- Permisos BT runtime (solo Android 12+; en ≤11 son permisos normales y el
        // discovery usa la ubicación que la app YA tiene por los mapas — no se pide nada) ----
        // CADENA COMPLETA de "listo para BT": permisos → BT ENCENDIDO → acción. Si el BT está
        // apagado SIEMPRE se pide encenderlo (diálogo del sistema); si el jugador lo niega, el
        // SIGUIENTE intento (tocar de nuevo / REINTENTAR) lo vuelve a pedir — igual los permisos.
        var pendingBtAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        val btEnableLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val act = pendingBtAction
            pendingBtAction = null
            if (result.resultCode == Activity.RESULT_OK) act?.invoke() // BT encendido → sigue
            // denegado: no hacemos nada; el próximo toque vuelve a pedirlo
        }
        val whenBtEnabled: (() -> Unit) -> Unit = { action ->
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            when {
                adapter == null -> Unit // hardware sin Bluetooth: no hay nada que encender
                adapter.isEnabled -> action()
                else -> {
                    pendingBtAction = action
                    runCatching { btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                }
            }
        }
        val btPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            // OJO: capturar y limpiar ANTES de invocar — la acción puede re-encolar
            // pendingBtAction (paso "encender BT") y un null posterior lo rompería
            val act = pendingBtAction
            pendingBtAction = null
            if (grants.values.all { it }) act?.invoke()
        }
        val withBtPerms: (Array<String>, () -> Unit) -> Unit = { perms, action ->
            val granted = Build.VERSION.SDK_INT < 31 || perms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (granted) {
                whenBtEnabled(action)
            } else {
                pendingBtAction = { whenBtEnabled(action) }
                btPermLauncher.launch(perms)
            }
        }

        // Menú de multijugador (crear sala / unirse con código / Bluetooth local)
        if (showOnlineMenu && state.onlineStatus == SfOnlineStatus.OFF) {
            OnlineMenuOverlay(
                onCreate = {
                    showOnlineMenu = false
                    viewModel.startOnline(create = true)
                },
                onJoin = { code ->
                    showOnlineMenu = false
                    viewModel.startOnline(create = false, code = code)
                },
                onQuickMatch = {
                    showOnlineMenu = false
                    viewModel.startOnlineQuick()
                },
                onBtHost = {
                    showOnlineMenu = false
                    withBtPerms(btHostPerms()) {
                        // Hacerse VISIBLE por Bluetooth (diálogo del sistema) + aceptar rivales.
                        // 300 s: margen de sobra para que el rival escanee/empareje (con 120 s
                        // el host dejaba de ser visible a media búsqueda; emparejados conectan igual).
                        runCatching {
                            context.startActivity(
                                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                            )
                        }
                        viewModel.startBtHost()
                    }
                },
                onBtScan = {
                    showOnlineMenu = false
                    withBtPerms(btScanPerms()) { viewModel.startBtScan() }
                },
                // SERVIDOR LOCAL (LAN): sin permisos nuevos — directo al VM
                onLanHost = {
                    showOnlineMenu = false
                    viewModel.startLanHost()
                },
                onLanJoin = { ip ->
                    showOnlineMenu = false
                    viewModel.connectLanHost(ip)
                },
                onDismiss = { showOnlineMenu = false },
            )
        }

        // Selector de anfitrión Bluetooth (emparejados + discovery)
        if (state.btPicking) {
            BtDevicePickerOverlay(
                devices = state.btDevices,
                onPick = viewModel::connectBtDevice,
                onCancel = viewModel::cancelBtScan,
            )
        }

        // (HOST) Solicitud de unión pendiente: ACEPTAR / RECHAZAR (lobby estilo AoE2)
        if (state.joinRequestPending) {
            JoinRequestOverlay(
                onAccept = { viewModel.respondJoin(true) },
                onReject = { viewModel.respondJoin(false) },
            )
        }

        // Falla de conexión BT → overlay BLOQUEANTE con REINTENTAR: elegiste jugar por BT,
        // así que NUNCA se cae en silencio al selector (nada de pelear contra la IA sin
        // conexión); reintentar VUELVE A PEDIR los permisos si hicieran falta.
        if (state.btError != null && state.onlineStatus == SfOnlineStatus.OFF) {
            BtRetryOverlay(
                error = state.btError!!,
                hintRes = if (state.lanMode) R.string.sf_lan_error_hint else R.string.sf_bt_error_hint,
                onRetry = {
                    val lanAddr = state.lanHostAddress
                    val addr = state.btRetryAddress
                    when {
                        // LAN: repite exactamente lo que hacías (unirte a esa IP u hostear)
                        state.lanMode && lanAddr != null -> viewModel.connectLanHost(lanAddr)
                        state.lanMode -> viewModel.startLanHost()
                        addr != null -> withBtPerms(btScanPerms()) { viewModel.connectBtDevice(addr) }
                        else -> withBtPerms(btHostPerms()) {
                            runCatching {
                                context.startActivity(
                                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
                                )
                            }
                            viewModel.startBtHost()
                        }
                    }
                },
                onCancel = viewModel::dismissBtError,
            )
        }

        // Countdown 3-2-1 sincronizado por el servidor
        if (state.onlineStatus == SfOnlineStatus.COUNTDOWN) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.onlineCountdown.toString(),
                    color = Color(0xFFFFD54F),
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        // Botón de salida
        TextButton(
            onClick = viewModel::requestExit,
            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
        ) {
            Text("✕", color = Color.White, fontSize = 18.sp)
        }

        // Menú de fin de pelea
        if (state.showEndMenu) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Aviso online: el rival ya pidió revancha
                if (state.opponentWantsRematch && state.onlineStatus == SfOnlineStatus.FIGHTING) {
                    Text(
                        text = stringResource(R.string.sf_mp_opp_wants_rematch),
                        color = Color(0xFFFFB74D), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Online: la revancha se PIDE (arranca cuando la pidan los dos); el rival
                    // que abandonó (OPPONENT_LEFT) ya no puede aceptar → sin botón de revancha.
                    if (state.onlineStatus != SfOnlineStatus.OPPONENT_LEFT) {
                        PowButton(text = stringResource(R.string.sf_rematch), onClick = viewModel::restartBattle)
                    }
                    if (state.onlineStatus == SfOnlineStatus.OFF) {
                        PowButton(text = stringResource(R.string.sf_change_character), onClick = viewModel::backToCharacterSelect)
                    } else {
                        PowButton(text = stringResource(R.string.sf_mp_leave_room), onClick = { viewModel.cancelOnline() })
                    }
                    PowButton(text = stringResource(R.string.sf_back_to_menu), onClick = onExitToMap)
                }
            }
        }

        // Overlay de PAUSA (auto al bloquear/minimizar; "Continuar" reanuda)
        if (state.isPaused && !state.inCharacterSelect) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.sf_paused),
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PowButton(text = stringResource(R.string.sf_continue), onClick = viewModel::togglePause)
                }
            }
        }

        // Diálogo de salida
        if (state.showExitDialog) {
            // Diálogo alineado al tema vino/dorado del modo (no el M3 default)
            AlertDialog(
                onDismissRequest = viewModel::dismissExitDialog,
                containerColor = Color(0xFF1A1016),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                title = { Text(stringResource(R.string.sf_exit_title)) },
                text = { Text(stringResource(R.string.sf_exit_message)) },
                confirmButton = { TextButton(onClick = onExitToMap) { Text(stringResource(R.string.sf_exit_confirm), color = Color(0xFFD4AF37)) } },
                dismissButton = { TextButton(onClick = viewModel::dismissExitDialog) { Text(stringResource(R.string.sf_keep_fighting), color = Color.White.copy(alpha = 0.7f)) } },
            )
        }
    }
}

// ------------------------------------------------------------------
// Selector de personaje (pre-pelea): tarjetas con preview del sprite
// (BitmapRegionDecoder: decodifica SOLO el recorte idle-1 de cada sheet,
// no los 7 sheets completos — cuidado con la RAM en gama baja, ver 09 §6)
// y aviso ALPHA para los peleadores POW con poses aproximadas.
// ------------------------------------------------------------------

@Composable
private fun CharacterSelectOverlay(
    fighters: List<SfFighterId>, // roster gateado por el VM (RYU/KEN solo con Modo Desarrollador)
    onSelect: (SfFighterId) -> Unit,
    subtitle: String? = null,
    onOnline: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE6101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_choose_fighter),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            subtitle?.let {
                Text(text = it, color = Color(0xFF90CAF9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                fighters.forEach { id ->
                    CharacterCard(id = id, onSelect = onSelect)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.sf_alpha_note),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
            onOnline?.let {
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(text = stringResource(R.string.sf_mp_button), onClick = it)
            }
        }
    }
}

// ------------------------------------------------------------------
// Overlays del MULTIJUGADOR: menú crear/unir/pública + pantallas de espera.
// (PowButton, el botón estilo POW, ahora vive COMPARTIDO en
// map_exterior/ui/components/PowButton.kt — pendiente 4 del AUDIT.)
// ------------------------------------------------------------------

/** Permisos runtime del ANFITRIÓN BT (Android 12+): aceptar conexiones + ser visible. */
private fun btHostPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
} else {
    emptyArray()
}

/** Permisos runtime de BUSCAR RIVAL (Android 12+): escanear + conectar. */
private fun btScanPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    emptyArray()
}

@Composable
private fun OnlineMenuOverlay(
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onQuickMatch: () -> Unit,
    onBtHost: () -> Unit,
    onBtScan: () -> Unit,
    onLanHost: () -> Unit,
    onLanJoin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var lanIp by remember { mutableStateOf("") }
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF0101018)),
        contentAlignment = Alignment.Center,
    ) {
        // Con 3 secciones (online/BT/LAN) el menú puede exceder el alto en landscape → scroll
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_mp_title),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            PowButton(text = stringResource(R.string.sf_mp_public), onClick = onQuickMatch)
            Spacer(modifier = Modifier.height(10.dp))
            PowButton(text = stringResource(R.string.sf_mp_create), onClick = onCreate)
            Spacer(modifier = Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(4) },
                    label = { Text(stringResource(R.string.sf_mp_code_label)) },
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                    // Paridad con el tema vino/dorado del modo (no el M3 default morado)
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD4AF37),
                        unfocusedBorderColor = Color(0xFF6B1C3A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFD4AF37),
                        focusedLabelColor = Color(0xFFD4AF37),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    ),
                )
                PowButton(
                    text = stringResource(R.string.sf_mp_join),
                    onClick = { if (code.length == 4) onJoin(code) },
                    enabled = code.length == 4,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // ─── BLUETOOTH (sin internet): anfitrión visible / buscar al anfitrión ───
            Text(
                text = stringResource(R.string.sf_bt_section),
                color = Color(0xFFD4AF37).copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PowButton(text = stringResource(R.string.sf_bt_host), onClick = onBtHost)
                PowButton(text = stringResource(R.string.sf_bt_scan), onClick = onBtScan)
            }
            Spacer(modifier = Modifier.height(16.dp))
            // ─── 🆕 SERVIDOR LOCAL (LAN): el jugador hostea su sala en la misma red Wi-Fi ───
            Text(
                text = stringResource(R.string.sf_lan_section),
                color = Color(0xFFD4AF37).copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            PowButton(text = stringResource(R.string.sf_lan_host), onClick = onLanHost)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lanIp,
                    onValueChange = { lanIp = it.trim() },
                    label = { Text(stringResource(R.string.sf_lan_ip_label)) },
                    singleLine = true,
                    modifier = Modifier.width(180.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFD4AF37),
                        unfocusedBorderColor = Color(0xFF6B1C3A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFD4AF37),
                        focusedLabelColor = Color(0xFFD4AF37),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                    ),
                )
                PowButton(
                    text = stringResource(R.string.sf_mp_join),
                    onClick = { if (lanIp.contains('.')) onLanJoin(lanIp) },
                    enabled = lanIp.count { it == '.' } == 3, // IPv4 completa
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sf_mp_cancel), color = Color(0xFFD4AF37)) }
        }
    }
}

@Composable
private fun OnlineInfoOverlay(title: String, subtitle: String, onCancel: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF0101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color(0xFFD4AF37),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(300.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.sf_mp_cancel), color = Color(0xFFD4AF37)) }
        }
    }
}

// ------------------------------------------------------------------
// LISTA DE ESPERA de la sala pública: resumen de partidas + salas activas
// como tarjetas; las que están en 'waiting' (falta rival) son TOCABLES y
// te unen directo (pendientes 2 y 3 del AUDIT: lista rica + refresh 5 s).
// ------------------------------------------------------------------

@Composable
private fun PublicQueueOverlay(
    rooms: List<SfRoomSummary>,
    queueCount: Int?,
    awaitingHost: Boolean,
    notice: String?,
    onJoinRoom: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF0101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_mp_queue_title),
                color = Color(0xFFD4AF37),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (awaitingHost) R.string.sf_mp_awaiting_host else R.string.sf_mp_queue_sub,
                ),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(300.dp),
            )
            // Aviso del lobby (p. ej. "el anfitrión rechazó tu solicitud")
            notice?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = it, color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            // Resumen (LIST_ROOMS): null = aún sin datos del servidor
            queueCount?.let { q ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sf_mp_rooms_summary, rooms.size, q),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                )
            }
            if (rooms.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rooms.forEach { room ->
                        RoomCard(room = room, enabled = !awaitingHost, onJoin = onJoinRoom)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.sf_mp_cancel), color = Color(0xFFD4AF37)) }
        }
    }
}

@Composable
private fun RoomCard(room: SfRoomSummary, enabled: Boolean, onJoin: (String) -> Unit) {
    // Solo se puede SOLICITAR entrar a salas en 'waiting' con hueco (el anfitrión decide)
    val joinable = enabled && room.phase == "waiting" && room.players < 2
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (joinable) Color(0xFF6B1C3A) else Color(0xFF23233A))
            .let { m -> if (joinable) m.clickable { onJoin(room.code) } else m }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = room.code,
            color = if (joinable) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.6f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Text(
            text = stringResource(if (joinable) R.string.sf_mp_room_waiting else R.string.sf_mp_room_busy),
            color = Color.White.copy(alpha = if (joinable) 0.9f else 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ------------------------------------------------------------------
// (HOST) Solicitud de unión pendiente — lobby estilo AoE2: el anfitrión
// decide si el solicitante entra a la sala (ACEPTAR / RECHAZAR).
// ------------------------------------------------------------------

@Composable
private fun JoinRequestOverlay(onAccept: () -> Unit, onReject: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1016))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_mp_join_request),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(280.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PowButton(text = stringResource(R.string.sf_mp_accept), onClick = onAccept)
                PowButton(
                    text = stringResource(R.string.sf_mp_reject),
                    onClick = onReject,
                    color = Color(0xFF2A1C21),
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Falla de conexión BLUETOOTH: overlay BLOQUEANTE (consume los toques —
// nada de tocar el selector de abajo por accidente) con el error claro
// y REINTENTAR / CANCELAR. Cancelar es la ÚNICA salida al selector.
// ------------------------------------------------------------------

@Composable
private fun BtRetryOverlay(error: String, hintRes: Int, onRetry: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0101018))
            .clickable(enabled = true, onClick = {}), // bloquea los toques hacia abajo
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_bt_error_title),
                color = Color(0xFFFFB74D),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(320.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(hintRes),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(320.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            PowButton(text = stringResource(R.string.sf_bt_retry), onClick = onRetry)
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.sf_mp_cancel), color = Color(0xFFD4AF37))
            }
        }
    }
}

// ------------------------------------------------------------------
// Selector de anfitrión BLUETOOTH: emparejados + hallados por discovery.
// ------------------------------------------------------------------

@Composable
private fun BtDevicePickerOverlay(
    devices: List<SfBtDevice>,
    onPick: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF0101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_bt_pick_title),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.sf_bt_pick_sub),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(300.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.sf_bt_none),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    devices.forEach { dev ->
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF6B1C3A))
                                .clickable { onPick(dev.address) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = dev.name,
                                color = Color(0xFFD4AF37),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = dev.address,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.sf_mp_cancel), color = Color(0xFFD4AF37)) }
        }
    }
}

@Composable
private fun CharacterCard(id: SfFighterId, onSelect: (SfFighterId) -> Unit) {
    val preview = rememberFighterPreview(id)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF23233A))
            .clickable { onSelect(id) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(86.dp), contentAlignment = Alignment.BottomCenter) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = id.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Text("?", color = Color.White, fontSize = 40.sp)
            }
            if (id.isAlpha) {
                Text(
                    text = "ALPHA",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFB300))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = id.displayName,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(90.dp),
        )
    }
}

// ------------------------------------------------------------------
// Selector de MAPA (paso 2): miniaturas de los fondos POW + "Al azar".
// Las miniaturas se decodifican submuestreadas (inSampleSize=8, ~248×110)
// para no cargar los 6 fondos completos (RAM de gama baja, ver 09 §6).
// ------------------------------------------------------------------

@Composable
private fun StageSelectOverlay(
    theme: SfTheme,
    onSelect: (String?) -> Unit,   // null = al azar
    onBack: (() -> Unit)?,         // null (online): sin "cambiar peleador", ya se avisó al rival
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE6101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_choose_stage),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                theme.fullBackgrounds.forEach { bg ->
                    val thumb = remember(bg.file) {
                        runCatching {
                            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
                            context.assets.open(theme.imagesDir + bg.file).use {
                                BitmapFactory.decodeStream(it, null, opts)
                            }?.asImageBitmap()
                        }.getOrNull()
                    }
                    StageCard(name = bg.name, thumb = thumb) { onSelect(bg.file) }
                }
                StageCard(name = stringResource(R.string.sf_random), thumb = null, emoji = "🎲") { onSelect(null) }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (onBack != null) TextButton(onClick = onBack) {
                Text(stringResource(R.string.sf_change_fighter), color = Color(0xFFD4AF37))
            }
        }
    }
}

@Composable
private fun StageCard(name: String, thumb: ImageBitmap?, emoji: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF23233A))
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.width(132.dp).height(66.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF11111C)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                thumb != null -> Image(
                    bitmap = thumb,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                emoji != null -> Text(emoji, fontSize = 30.sp)
                else -> Text("?", color = Color.White, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(132.dp),
        )
    }
}

/**
 * Preview del peleador para el selector. Empaquetados: recorte idle-1 de su sheet
 * (BitmapRegionDecoder, no decodifica la hoja completa). COMPARTIDOS: 1er cuadro del
 * Idle del set del MUNDO (mismo asset que usa el mapa; con flip si aplica) — más barato
 * aún que armar la hoja runtime solo para una tarjeta.
 */
@Composable
private fun rememberFighterPreview(id: SfFighterId): ImageBitmap? {
    val context = LocalContext.current
    return remember(id) {
        runCatching {
            val shared = id.sharedSet
            if (shared != null) {
                SfSharedSheets.previewFor(context, shared)?.let { trimTransparent(it).asImageBitmap() }
            } else {
                val data = SfFrameCatalog.load(context, id)
                val src = data.frames.getValue("idle-1").src
                val decoder = context.assets.open(id.spriteAsset).use { ins ->
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(ins, false)
                } ?: return@runCatching null
                val region = decoder.decodeRegion(Rect(src[0], src[1], src[0] + src[2], src[1] + src[3]), null)
                decoder.recycle()
                region?.let { trimTransparent(it).asImageBitmap() }
            }
        }.getOrNull()
    }
}

/** Recorta el bitmap a su contenido opaco (los peleadores POW vienen en celdas 256² con aire). */
private fun trimTransparent(bmp: Bitmap): Bitmap {
    val w = bmp.width
    val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) != 0) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return bmp
    return Bitmap.createBitmap(bmp, minX, minY, maxX - minX + 1, maxY - minY + 1)
}

// ------------------------------------------------------------------
// Diamante Xbox de POW (mismas letras/colores/posiciones que
// ActionButtonsController: Y arriba · X izquierda · B derecha · A abajo).
// Solo cambia QUÉ HACE cada botón en este modo (como en conducción).
// ------------------------------------------------------------------

@Composable
private fun FighterXboxButtons(
    modifier: Modifier = Modifier,
    onPunch: (SfAttackStrength) -> Unit,
    onKick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Y arriba — PUÑO MEDIO (amarillo)
            ActionButton(text = "Y", color = Color(0xFFF1C40F), onHoldEvent = { pressed ->
                if (pressed) onPunch(SfAttackStrength.MEDIUM)
            })
            Row(verticalAlignment = Alignment.CenterVertically) {
                // X izquierda — PUÑO LIGERO (azul)
                ActionButton(text = "X", color = Color(0xFF3498DB), onHoldEvent = { pressed ->
                    if (pressed) onPunch(SfAttackStrength.LIGHT)
                })
                Spacer(modifier = Modifier.size(48.dp))
                // B derecha — PUÑO FUERTE (rojo)
                ActionButton(text = "B", color = Color(0xFFE74C3C), onHoldEvent = { pressed ->
                    if (pressed) onPunch(SfAttackStrength.HEAVY)
                })
            }
            // A abajo — PATADA (verde; fuerza según joystick: neutro/adelante/atrás)
            ActionButton(text = "A", color = Color(0xFF2ECC71), onHoldEvent = { pressed ->
                if (pressed) onKick()
            })
        }
    }
}

// ------------------------------------------------------------------
// Render de la escena (mundo virtual 382x224 con letterbox "contain").
// Todos los recortes/posiciones salen del SfTheme.
// ------------------------------------------------------------------

private class SceneCtx(
    val scale: Float,
    val ox: Float,   // offset del letterbox en px de pantalla
    val oy: Float,
    val camX: Float,
    val camY: Float,
)

private fun DrawScope.drawScene(
    theme: SfTheme,
    state: StreetFighterState,
    images: Map<String, ImageBitmap>,
    playerData: SfFighterData,
    cpuData: SfFighterData,
    bgImage: ImageBitmap?,
    roundBannerText: String,
    fightBannerText: String,
) {
    val scale = minOf(size.width / SfConstants.SCENE_WIDTH, size.height / SfConstants.SCENE_HEIGHT)
    val ctx = SceneCtx(
        scale = scale,
        ox = (size.width - SfConstants.SCENE_WIDTH * scale) / 2f,
        oy = (size.height - SfConstants.SCENE_HEIGHT * scale) / 2f,
        camX = state.cameraX,
        camY = state.cameraY,
    )
    val stage = images.getValue(theme.stageImage)
    val t = state.gameTimeMs

    if (bgImage != null) {
        // ---- FONDO POW a pantalla completa (elegido al azar) con parallax de cámara ----
        drawFullBackground(ctx, bgImage)
    } else {
        // ---- Fondo del escenario clásico (parallax por capas) ----
        val bob = theme.boatBob[((t / 366) % theme.boatBob.size).toInt()]
        drawSprite(ctx, stage, theme.stageBackground, 16f - ctx.camX / 2.157303f, -ctx.camY)
        val flag = theme.flagFrames[((t / 133) % theme.flagFrames.size).toInt()]
        drawSprite(ctx, stage, flag, 576f - ctx.camX / 2.157303f, 48f - ctx.camY)
        drawSprite(ctx, stage, theme.stageBoat, 150f - ctx.camX / 1.613445f, -3f - ctx.camY - bob)
        theme.stagePeople.forEach { prop ->
            drawSprite(ctx, stage, prop.src, prop.x - ctx.camX / 1.613445f, prop.y.toFloat() - bob - ctx.camY)
        }
        drawSprite(ctx, stage, theme.stageFloor, SfConstants.STAGE_PADDING - ctx.camX * 1.1f, 176f - ctx.camY)
        drawSprite(ctx, stage, theme.stageFloorBottom, SfConstants.STAGE_PADDING - ctx.camX * 1.1f, 232f - ctx.camY)
        drawSprite(ctx, stage, theme.ballardSmall, 468f - 92f - ctx.camX / 1.54f, 166f - ctx.camY)
        drawSprite(ctx, stage, theme.ballardSmall, 468f + 92f - ctx.camX / 1.54f, 166f - ctx.camY)
        drawSprite(ctx, stage, theme.sideBarrels, SfConstants.STAGE_PADDING + SfConstants.STAGE_WIDTH - 152f - ctx.camX, 120f - ctx.camY)
    }

    // ---- Sombras ----
    drawShadow(ctx, theme, images.getValue(theme.shadowImage), state.player)
    drawShadow(ctx, theme, images.getValue(theme.shadowImage), state.cpu)

    // ---- Peleadores (sheet según el personaje del snapshot) ----
    drawFighter(ctx, images, playerData, state.player, t)
    drawFighter(ctx, images, cpuData, state.cpu, t)

    // ---- Proyectiles especiales ----
    // Si el DUEÑO del proyectil trae sus propios frames "proj-*" en su JSON (Prankedy:
    // tanque de gas + estallido de confeti), se usan ESOS desde su sheet; si no, el
    // fireball del tema (hoy, el hadouken del clon).
    state.fireballs.forEach { fb ->
        val owner = if (fb.ownerIndex == 0) state.player else state.cpu
        val ownerData = if (fb.ownerIndex == 0) playerData else cpuData
        val ownerSheet = images[owner.id.spriteAsset.substringAfterLast('/')]
        if (ownerSheet != null && ownerData.frames.containsKey("proj-fly-1")) {
            val key = if (fb.state == SfFireballState.ACTIVE) {
                if (fb.animationFrame % 2 == 0) "proj-fly-1" else "proj-fly-2"
            } else {
                "proj-hit-${(fb.animationFrame + 1).coerceIn(1, 3)}"
            }
            ownerData.frames[key]?.let { fd ->
                drawSpriteAnchored(ctx, ownerSheet, fd.src, fd.origin, fb.x, fb.y, fb.direction)
            }
        } else {
            val frames = if (fb.state == SfFireballState.ACTIVE) theme.fireballActive else theme.fireballCollided
            val frame = frames[fb.animationFrame.coerceIn(0, frames.size - 1)]
            if (frame.src[2] > 0) {
                drawSpriteAnchored(ctx, images.getValue(theme.fireballImage), frame.src, frame.origin, fb.x, fb.y, fb.direction)
            }
        }
    }

    // ---- Splashes de impacto ----
    state.splashes.forEach { sp ->
        val rows = theme.splashFrames.getValue(sp.strength)
        val frame = rows[sp.playerId.coerceIn(0, 1)][sp.animationFrame.coerceIn(0, 3)]
        drawSprite(ctx, images.getValue(theme.splashImage), frame.src, sp.x - ctx.camX - frame.origin[0], sp.y - ctx.camY - frame.origin[1])
    }

    // ---- Primer plano (solo con el escenario clásico) ----
    if (bgImage == null) {
        drawSprite(ctx, stage, theme.ballardLarge, SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING - 147f - ctx.camX / 0.958f, 200f - ctx.camY)
        drawSprite(ctx, stage, theme.ballardLarge, SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING + 147f - ctx.camX / 0.958f, 200f - ctx.camY)
    }

    // ---- HUD ----
    drawHud(ctx, theme, images.getValue(theme.hudImage), state)

    // ---- Texto de ganador: "<PERSONAJE> WINS" con la FUENTE arcade del HUD ----
    // (funciona para CUALQUIER peleador; ya no depende de winnerText.png por filas)
    state.winnerIndex?.let { winner ->
        if (state.battleEnded) {
            val winnerFighter = if (winner == 0) state.player else state.cpu
            val text = "${winnerFighter.id.shortName} WINS"
            val sizeMul = 2f
            val textW = text.length * 12f * sizeMul
            drawFontText(ctx, theme, images.getValue(theme.hudImage), text, (SfConstants.SCENE_WIDTH - textW) / 2f, 58f, sizeMul)
        }
    }

    // ---- 🆕 Banner de RONDA ("RONDA N" + "PELEA"), fuente arcade, input congelado ----
    if (state.showRoundIntro) {
        val hud = images.getValue(theme.hudImage)
        val rw = roundBannerText.length * 12f * 2f
        drawFontText(ctx, theme, hud, roundBannerText, (SfConstants.SCENE_WIDTH - rw) / 2f, 76f, 2f)
        val fw = fightBannerText.length * 12f * 1.2f
        drawFontText(ctx, theme, hud, fightBannerText, (SfConstants.SCENE_WIDTH - fw) / 2f, 104f, 1.2f)
    }
}

/**
 * Fondo POW a pantalla completa: se escala para cubrir el ALTO de la escena (224) y el
 * ancho sobrante panea con la cámara (parallax 1:1 con el avance por el stage).
 */
private fun DrawScope.drawFullBackground(ctx: SceneCtx, bg: ImageBitmap) {
    val s = SfConstants.SCENE_HEIGHT / bg.height.toFloat()
    val scaledW = bg.width * s
    val camSpan = SfConstants.STAGE_WIDTH - SfConstants.SCENE_WIDTH
    val progress = ((ctx.camX - SfConstants.STAGE_PADDING) / camSpan).coerceIn(0f, 1f)
    val offsetX = (scaledW - SfConstants.SCENE_WIDTH).coerceAtLeast(0f) * progress
    drawImage(
        image = bg,
        srcOffset = IntOffset(0, 0),
        srcSize = IntSize(bg.width, bg.height),
        dstOffset = IntOffset((ctx.ox - offsetX * ctx.scale).toInt(), ctx.oy.toInt()),
        dstSize = IntSize((scaledW * ctx.scale).toInt(), (SfConstants.SCENE_HEIGHT * ctx.scale).toInt()),
        filterQuality = FilterQuality.Low, // foto: bilineal se ve mejor que None
    )
}

/** Dibuja un recorte del sheet en coords de ESCENA (sin espejo). */
private fun DrawScope.drawSprite(ctx: SceneCtx, image: ImageBitmap, src: List<Int>, sceneX: Float, sceneY: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset(src[0], src[1]),
        srcSize = IntSize(src[2], src[3]),
        dstOffset = IntOffset((ctx.ox + sceneX * ctx.scale).toInt(), (ctx.oy + sceneY * ctx.scale).toInt()),
        dstSize = IntSize((src[2] * ctx.scale).toInt(), (src[3] * ctx.scale).toInt()),
        filterQuality = FilterQuality.None,
    )
}

/** Dibuja un recorte con tamaño destino explícito (texto de ganador). */
private fun DrawScope.drawSpriteScaled(ctx: SceneCtx, image: ImageBitmap, src: List<Int>, sceneX: Float, sceneY: Float, dstW: Float, dstH: Float) {
    drawImage(
        image = image,
        srcOffset = IntOffset(src[0], src[1]),
        srcSize = IntSize(src[2], src[3]),
        dstOffset = IntOffset((ctx.ox + sceneX * ctx.scale).toInt(), (ctx.oy + sceneY * ctx.scale).toInt()),
        dstSize = IntSize((dstW * ctx.scale).toInt(), (dstH * ctx.scale).toInt()),
        filterQuality = FilterQuality.None,
    )
}

/** Dibuja un sprite anclado (con origen) en coords de MUNDO, espejado por dirección. */
private fun DrawScope.drawSpriteAnchored(
    ctx: SceneCtx,
    image: ImageBitmap,
    src: List<Int>,
    origin: List<Int>,
    worldX: Float,
    worldY: Float,
    direction: SfDirection,
    shakeX: Float = 0f,
    spriteScale: Float = 1f,   // parche de escala por-frame (p. ej. HURT de peleadores ALPHA)
) {
    val anchorSx = ctx.ox + (worldX - ctx.camX) * ctx.scale
    val anchorSy = ctx.oy + (worldY - ctx.camY) * ctx.scale
    // El origin (ancla) se escala junto con el sprite → la figura crece/encoge alrededor de sus
    // pies (origin), sin moverse de su punto de mundo.
    val s = ctx.scale * spriteScale
    val dstX = anchorSx - origin[0] * s + shakeX * ctx.scale
    val dstY = anchorSy - origin[1] * s
    val draw: DrawScope.() -> Unit = {
        drawImage(
            image = image,
            srcOffset = IntOffset(src[0], src[1]),
            srcSize = IntSize(src[2], src[3]),
            dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
            dstSize = IntSize((src[2] * s).toInt(), (src[3] * s).toInt()),
            filterQuality = FilterQuality.None,
        )
    }
    if (direction == SfDirection.LEFT) {
        // Espejo alrededor del ancla (equivale al context.scale(direction,1) del JS)
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(anchorSx, 0f)) { draw() }
    } else {
        draw()
    }
}

private fun DrawScope.drawFighter(ctx: SceneCtx, images: Map<String, ImageBitmap>, data: SfFighterData, f: SfFighter, t: Long) {
    val sheet = images[f.id.spriteAsset.substringAfterLast('/')] ?: return
    val anim = data.animations[f.state.jsKey] ?: return
    val frameKey = anim[f.animationFrame.coerceIn(0, anim.size - 1)].frameKey
    val frame = data.frames[frameKey] ?: return
    // Sacudida al recibir golpe (hurt shake del JS), solo durante el 1er frame de HURT
    val hurtState = f.state.name.startsWith("HURT_")
    val shake = if (hurtState && f.animationFrame == 0) (if ((t / 32) % 2 == 0L) 2f else -2f) else 0f
    // PARCHE ALPHA: los peleadores cuyas poses de golpe se generaron más chicas se reescalan SOLO
    // en HURT (hurtScale != 1f) para que no "encojan" al recibir daño.
    val spriteScale = if (hurtState) f.id.hurtScale else 1f
    drawSpriteAnchored(ctx, sheet, frame.src, frame.origin, f.x, f.y, f.direction, shakeX = shake, spriteScale = spriteScale)
}

private fun DrawScope.drawShadow(ctx: SceneCtx, theme: SfTheme, shadowImg: ImageBitmap, f: SfFighter) {
    // Shadow.js: se encoge en el aire; specials/KO tienen escalas propias
    var scaleX = 1.2f
    var scaleY = 1.2f
    var offsetX = 0f
    if (f.y < SfConstants.STAGE_FLOOR) {
        val s = 1.2f - (200f - f.y) / 300f
        scaleX = s; scaleY = s
    } else when (f.state) {
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY -> {
            scaleX = 1.6f; scaleY = 1f; offsetX = 22f * f.direction.sign * -1f
        }
        SfFighterState.KO -> { scaleX = 2.4f; scaleY = 1f }
        else -> Unit
    }
    val src = theme.shadowFrame.src
    val originX = theme.shadowFrame.origin[0]
    val originY = theme.shadowFrame.origin[1]
    val x = ctx.ox + (f.x - ctx.camX - originX * scaleX - offsetX) * ctx.scale
    val y = ctx.oy + (SfConstants.STAGE_FLOOR - ctx.camY - originY * scaleY) * ctx.scale
    drawImage(
        image = shadowImg,
        srcOffset = IntOffset(src[0], src[1]),
        srcSize = IntSize(src[2], src[3]),
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize((src[2] * scaleX * ctx.scale).toInt(), (src[3] * scaleY * ctx.scale).toInt()),
        alpha = 0.5f,
        filterQuality = FilterQuality.None,
    )
}

private fun DrawScope.drawHud(ctx: SceneCtx, theme: SfTheme, hud: ImageBitmap, state: StreetFighterState) {
    // Barras de vida (la derecha espejada)
    drawSprite(ctx, hud, theme.healthBar, 31f, 20f)
    drawHudMirrored(ctx, hud, theme.healthBar, 353f, 20f)

    // Daño en rojo sobre la barra
    val maxHp = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    val damageColor = Color(0xFFF30000)
    // 🆕 Con el HP MOSTRADO (displayHp*, roll-up gradual del VM), no con el hitPoints real:
    // la barra "drena" al recibir daño en vez de saltar de golpe
    val leftDamage = (144f * (maxHp - state.displayHp0) / maxHp)
    if (leftDamage > 0f) {
        drawRect(
            color = damageColor,
            topLeft = Offset(ctx.ox + 32f * ctx.scale, ctx.oy + 21f * ctx.scale),
            size = Size(leftDamage * ctx.scale, 9f * ctx.scale),
        )
    }
    val rightDamage = (144f * (maxHp - state.displayHp1) / maxHp)
    if (rightDamage > 0f) {
        val rx = 208f + (144f * state.displayHp1 / maxHp)
        drawRect(
            color = damageColor,
            topLeft = Offset(ctx.ox + rx * ctx.scale, ctx.oy + 21f * ctx.scale),
            size = Size(rightDamage * ctx.scale, 9f * ctx.scale),
        )
    }

    // Icono KO (parpadea cuando alguien está crítico)
    val koSrc = if (state.koFlash) theme.koBlack else theme.koWhite
    drawSprite(ctx, hud, koSrc, 176f, if (state.koFlash) 17f else 18f)

    // Timer (2 dígitos, parpadea al final)
    val digits = if (state.timeFlashing) theme.timeDigitsFlash else theme.timeDigits
    val timeStr = state.displayTime.toString().padStart(2, '0')
    drawSprite(ctx, hud, digits.digit(timeStr[0] - '0'), 178f, 33f)
    drawSprite(ctx, hud, digits.digit(timeStr[1] - '0'), 194f, 33f)

    // Nombres con la FUENTE arcade (cualquier peleador; el derecho alineado a la derecha)
    drawFontText(ctx, theme, hud, state.player.id.shortName, 32f, 33f, 0.9f)
    val cpuName = state.cpu.id.shortName
    drawFontText(ctx, theme, hud, cpuName, 350f - cpuName.length * 12f * 0.9f, 33f, 0.9f)

    // Marcadores P1 / P2
    drawFontText(ctx, theme, hud, "P1", 4f, 1f)
    drawScoreNumber(ctx, theme, hud, state.playerScore, 45f)
    drawFontText(ctx, theme, hud, "P2", 269f, 1f)
    drawScoreNumber(ctx, theme, hud, state.cpuScore, 309f)

    // 🆕 RONDAS GANADAS (mejor de 3): cuadritos dorados bajo el nombre de cada lado
    val roundMark = Color(0xFFD4AF37)
    for (i in 0 until state.playerRoundWins.coerceAtMost(2)) {
        drawRect(
            color = roundMark,
            topLeft = Offset(ctx.ox + (32f + i * 11f) * ctx.scale, ctx.oy + 45f * ctx.scale),
            size = Size(7f * ctx.scale, 7f * ctx.scale),
        )
    }
    for (i in 0 until state.cpuRoundWins.coerceAtMost(2)) {
        drawRect(
            color = roundMark,
            topLeft = Offset(ctx.ox + (346f - i * 11f) * ctx.scale, ctx.oy + 45f * ctx.scale),
            size = Size(7f * ctx.scale, 7f * ctx.scale),
        )
    }
}

private fun DrawScope.drawHudMirrored(ctx: SceneCtx, hud: ImageBitmap, src: List<Int>, sceneX: Float, sceneY: Float) {
    val anchorSx = ctx.ox + sceneX * ctx.scale
    scale(scaleX = -1f, scaleY = 1f, pivot = Offset(anchorSx, 0f)) {
        drawImage(
            image = hud,
            srcOffset = IntOffset(src[0], src[1]),
            srcSize = IntSize(src[2], src[3]),
            dstOffset = IntOffset(anchorSx.toInt(), (ctx.oy + sceneY * ctx.scale).toInt()),
            dstSize = IntSize((src[2] * ctx.scale).toInt(), (src[3] * ctx.scale).toInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Texto con la FUENTE arcade del HUD (recortes A-Z/0-9 de `theme.letterFont`).
 * Avance fijo de 12 px por carácter (el espacio y los caracteres sin glifo dejan hueco).
 * Es la fuente "oficial" del modo: tags de nombre, marcadores y "<X> WINS".
 */
private fun DrawScope.drawFontText(
    ctx: SceneCtx,
    theme: SfTheme,
    hud: ImageBitmap,
    text: String,
    x: Float,
    y: Float,
    sizeMul: Float = 1f,
) {
    var cx = x
    text.uppercase().forEach { ch ->
        theme.letterFont[ch]?.let { src ->
            drawSpriteScaled(ctx, hud, src, cx, y, src[2] * sizeMul, src[3] * sizeMul)
        }
        cx += 12f * sizeMul
    }
}

private fun DrawScope.drawScoreNumber(ctx: SceneCtx, theme: SfTheme, hud: ImageBitmap, score: Int, x: Float) {
    val str = score.toString()
    val padding = 6 * 12f - str.length * 12f
    drawFontText(ctx, theme, hud, str, x + padding, 1f)
}
