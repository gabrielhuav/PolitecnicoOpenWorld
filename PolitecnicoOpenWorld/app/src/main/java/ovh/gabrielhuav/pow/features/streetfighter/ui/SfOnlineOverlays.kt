package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cycleShowcaseSpeed
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.dismissGauntletReport
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.goToPreviousShowcaseAnimation
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.skipShowcaseFighter
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.skipToNextShowcaseAnimation
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startAiVsAi
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startGauntletArcade
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startGauntletRoundRobin
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startShowcase
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopGauntlet
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.replayCurrentShowcaseAudio
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopAudioShowcase
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeContinue
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeExit
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeRetry
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.comboSheet
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.exitTutorial
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.resumeArcadeSession
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startArcade
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startTutorial
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.tutorialRestartLesson
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.tutorialSkipLesson
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cancelBtScan
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cancelOnline
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.chooseMapOnline
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.connectBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.connectLanHost
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.dismissBtError
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.requestJoinRoom
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.respondJoin
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startBtHost
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startBtScan
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startLanDiscovery
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startLanHost
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startOnline
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startOnlineQuick
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopLanDiscovery
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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFrameDef
import ovh.gabrielhuav.pow.ui.components.ActionButton
import ovh.gabrielhuav.pow.ui.components.JoystickController
import ovh.gabrielhuav.pow.ui.components.PowButton
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfArcadeOutcome
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SF_STOP_SPECIALS_EVENT
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel

// ────────────────────────────────────────────────────────────────────────────
// 🌐 OVERLAYS de MULTIJUGADOR (online, sala pública, Bluetooth y LAN)
//
// Menús y diálogos de conexión: crear/unirse a sala, cola pública con su lista de partidas,
// petición de unirse con aprobación del host, reintento de Bluetooth y selector de dispositivo.
// ⚠️ Los permisos de BT dependen de la versión de Android (btHostPerms/btScanPerms): en 12+ hasta
// CANCELAR el discovery exige BLUETOOTH_SCAN. Ver el gotcha de 09 §12.
//
// Extraído de StreetFighterScreen.kt (4029 líneas) en el refactor de tamaño de la Fase 5.
// Son composables/helpers TOP-LEVEL del mismo paquete: `internal` en vez de `private` para
// que la Screen los siga viendo. Sin cambios de comportamiento.
// ────────────────────────────────────────────────────────────────────────────

internal fun btHostPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
} else {
    emptyArray()
}

/** Permisos runtime de BUSCAR RIVAL (Android 12+): escanear + conectar. */
internal fun btScanPerms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    emptyArray()
}

@Composable
internal fun OnlineMenuOverlay(
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onQuickMatch: () -> Unit,
    onBtHost: () -> Unit,
    onBtScan: () -> Unit,
    onLanHost: () -> Unit,
    onLanJoin: (String) -> Unit,
    lanDiscovered: List<SfLanGame>,
    onLanScanStart: () -> Unit,
    onLanScanStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var lanIp by remember { mutableStateOf("") }
    // 🆕 (2026-07-26) Mientras el menú online está abierto, escucha balizas LAN (autodescubrimiento).
    DisposableEffect(Unit) {
        onLanScanStart()
        onDispose { onLanScanStop() }
    }
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
            // 🆕 (2026-07-26) AUTODESCUBRIMIENTO: partidas encontradas en la MISMA red Wi-Fi (sin
            // teclear IP). Tocar una tarjeta se une directo por la IP de su baliza.
            Text(
                text = stringResource(R.string.sf_lan_discovered_label),
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (lanDiscovered.isEmpty()) {
                Text(
                    text = stringResource(R.string.sf_lan_searching),
                    color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
                )
            } else {
                lanDiscovered.forEach { game ->
                    PowButton(text = "▶  ${game.name}", onClick = { onLanJoin(game.ip) })
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sf_lan_or_ip),
                color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
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
internal fun OnlineInfoOverlay(title: String, subtitle: String, onCancel: () -> Unit) {
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
internal fun PublicQueueOverlay(
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
internal fun RoomCard(room: SfRoomSummary, enabled: Boolean, onJoin: (String) -> Unit) {
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
internal fun JoinRequestOverlay(onAccept: () -> Unit, onReject: () -> Unit) {
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
internal fun BtRetryOverlay(titleRes: Int, error: String, hintRes: Int, onRetry: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0101018))
            .clickable(enabled = true, onClick = {}), // bloquea los toques hacia abajo
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(titleRes),
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
internal fun BtDevicePickerOverlay(
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

/** Reduce el bitmap a `targetW` px (nearest, sin filtro) → efecto PIXELADO al re-escalarlo grande. */

