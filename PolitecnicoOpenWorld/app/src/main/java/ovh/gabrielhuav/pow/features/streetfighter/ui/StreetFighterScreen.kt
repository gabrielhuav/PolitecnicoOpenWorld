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
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButton
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PowButton
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfArcadeOutcome
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SF_STOP_SPECIALS_EVENT
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

private fun getFighterPrefix(key: String): String {
    var clean = key.removeSuffix(".ogg").removeSuffix(".mp3")
    while (clean.isNotEmpty() && (clean.last().isDigit() || clean.last() == '_')) {
        clean = clean.dropLast(1)
    }
    val suffixes = listOf("hurt", "attack", "win", "power", "intro")
    for (s in suffixes) {
        if (clean.endsWith(s)) {
            clean = clean.removeSuffix(s)
            break
        }
    }
    while (clean.isNotEmpty() && clean.last() == '_') {
        clean = clean.dropLast(1)
    }
    return clean
}

private fun isSpecialPowerAudio(key: String): Boolean {
    val clean = key.removeSuffix(".ogg").removeSuffix(".mp3").lowercase()
    if (clean.contains("power") || clean.contains("electricity")) return true
    // 🆕 (2026-07-19) Se excluye 'win' para proteger las voces de victoria y evitar que se corten por gritos o daños comunes
    val suffixes = listOf("hurt", "attack", "intro")
    for (s in suffixes) {
        if (clean.endsWith(s) || clean.contains("_$s")) return false
    }
    return true
}

/** Reproduce una voz o pieza larga completa; SoundPool puede truncar archivos extensos. */
private fun playSfSpecial(
    context: Context,
    assetPath: String,
    activePlayers: MutableMap<String, MediaPlayer>,
): Boolean {
    // 🆕 Interrumpir cualquier audio del mismo personaje que ya se esté reproduciendo.
    // 🆕 (2026-07-19b) FIX: un HURT en curso NO se corta por un ATAQUE del mismo peleadór (antes
    // el contraataque tras recuperarse cortaba su propio quejido → "el hurt no suena"). Solo otro
    // HURT lo reinicia (= te pegaron otra vez).
    // 🆕 (2026-07-19c) Un ataque especial en curso (isSpecialPowerAudio) NUNCA se detiene por otras acciones.
    // 🆕 (2026-07-22) La INTRO ("Está prohibido beber en vía pública", ~15 s) DEBE terminar:
    // (a) nunca la corta otra voz del mismo peleadór, y (b) mientras suena, las voces nuevas
    // de ESE peleadór se SALTAN (no se encima el grito de ataque). Otro clip de intro sí la
    // reemplaza (re-disparo de ronda nueva).
    val newName = assetPath.substringAfterLast('/')
    val newPrefix = getFighterPrefix(newName)
    val newIsHurt = newName.contains("_hurt")
    val newIsIntro = newName.contains("_intro")
    if (!newIsIntro) {
        val introPlaying = activePlayers.keys.any { key ->
            val kf = key.substringAfterLast('/')
            kf.contains("_intro") && getFighterPrefix(kf) == newPrefix
        }
        if (introPlaying) return false
    }
    val keysToStop = activePlayers.keys.filter { key ->
        val kf = key.substringAfterLast('/')
        !isSpecialPowerAudio(kf) && !kf.contains("_intro") &&
        getFighterPrefix(kf) == newPrefix && (newIsHurt || !kf.contains("_hurt"))
    }
    keysToStop.forEach { key ->
        activePlayers.remove(key)?.let { current ->
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.release() }
        }
    }

    // 🆕 (2026-07-18r) RE-DISPARO: si el MISMO clip ya suena, lo cortamos y lo volvemos a
    // lanzar desde el inicio (antes se ignoraba mientras sonaba → "no se repetía" al re-atacar).
    activePlayers.remove(assetPath)?.let { current ->
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.release() }
    }
    val player = MediaPlayer()
    val result = runCatching {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        context.assets.openFd(assetPath).use { fd ->
            player.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
        }
        player.setOnCompletionListener { completed ->
            activePlayers.remove(assetPath, completed)
            completed.release()
        }
        player.setOnErrorListener { failed, _, _ ->
            activePlayers.remove(assetPath, failed)
            failed.release()
            true
        }
        activePlayers[assetPath] = player
        player.prepare()
        player.start()
    }
    if (result.isFailure) {
        activePlayers.remove(assetPath, player)
        runCatching { player.release() }
    }
    return result.isSuccess
}

private fun releaseSfSpecials(activePlayers: MutableMap<String, MediaPlayer>) {
    val players = activePlayers.values.toList()
    activePlayers.clear()
    players.forEach { player ->
        runCatching { if (player.isPlaying) player.stop() }
        runCatching { player.release() }
    }
}

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
    // 🆕 (2026-07-21) GAMA BAJA: submuestreo de los atlas de peleador. Con las hojas 20-29
    // los atlas llegaron a 2560×7168 (≈73 MB en ARGB_8888 por peleador, ×2 en pantalla):
    // demasiado para gama baja y para GPUs con tope de textura de 2048. A 1/2 quedan en
    // ~18 MB y 1280×3584. Todas las coordenadas del JSON se dividen por este factor.
    val sheetSample = remember { if (viewModel.isLowEndDevice()) 2 else 1 }
    val sheetSampleScale = remember(sheetSample) { 1f / sheetSample }
    // 🆕 Hojas pesadas SOLO en pelea (no en selector → menos RAM/lag al abrir el modo).
    // En selector solo se usan thumbs de region-decoder por card.
    // Tema (HUD/sombra/splash): livianos, se decodifican una vez en composición.
    val themeImages = remember(theme) {
        theme.imageFiles.associateWith { name ->
            // HUD/sombra: siempre; kenstage puede faltar
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            runCatching {
                context.assets.open(theme.imagesDir + name).use {
                    BitmapFactory.decodeStream(it, null, opts)
                }?.asImageBitmap()
            }.getOrNull()
        }.filterValues { it != null }.mapValues { it.value!! }
    }
    val playerData = remember(playerId) { SfFrameCatalog.load(context, playerId) }
    val cpuData = remember(cpuId) { SfFrameCatalog.load(context, cpuId) }
    val lowEnd = remember { viewModel.isLowEndDevice() }
    // 🆕 (2026-07-22, Bloque B) IDs de la pelea INCLUYENDO ambas identidades de una posible
    // metamorfosis. Es un SET (igualdad por contenido): cuando La Presidenta se transforma
    // a media pelea el set NO cambia → NO se re-decodifica nada (antes el remember se
    // recomputaba con el id nuevo y el juego "se trababa unos segundos" al transformarse).
    val fightIds = remember(playerId, cpuId) {
        buildSet {
            add(playerId)
            add(cpuId)
            if (playerId == SfFighterId.LA_PRESIDENTA || cpuId == SfFighterId.LA_PRESIDENTA) {
                add(SfFighterId.YOALLI_EHECATL)
            }
            if (playerId == SfFighterId.YOALLI_EHECATL || cpuId == SfFighterId.YOALLI_EHECATL) {
                add(SfFighterId.LA_PRESIDENTA)
            }
        }
    }
    // 🆕 (2026-07-22, Bloque B) TODO lo PESADO de la pelea (atlas de peleadores a
    // `sheetSample`, atlas ALPHA y el escaneo de alturas de contenido) se decodifica en
    // Dispatchers.IO bajo el overlay CARGANDO. Antes iba en remember{} EN EL HILO DE UI
    // (el "se traba unos segundos al cargar") y sheetFor podía lanzar error()/OOM SIN
    // atrapar (crash P0 de La Llorona: su pelea es la ÚNICA que suma un 3er atlas ALPHA).
    var fightAssets by remember { mutableStateOf<SfFightAssets?>(null) }
    LaunchedEffect(fightIds, state.inCharacterSelect, sheetSample, lowEnd) {
        if (state.inCharacterSelect) {
            fightAssets = null
            return@LaunchedEffect
        }
        fightAssets = null
        fightAssets = withContext(kotlinx.coroutines.Dispatchers.IO) {
            // BLINDAJE P0: un atlas que no decodifica (OOM/IO) NO tumba el juego — se
            // omite y drawFighter cae a la primera hoja disponible (feo pero jugable).
            val sheets = buildMap {
                fightIds.forEach { id ->
                    runCatching { SfSharedSheets.sheetFor(context, id, sheetSample).asImageBitmap() }
                        .getOrNull()
                        ?.let { put(id.spriteAsset.substringAfterLast('/'), it) }
                }
            }
            // PLACEHOLDER ALPHA (hoy solo La Llorona: sin PATADA LARGA/OVERHEAD). Se pinta
            // como SILUETA NEGRA → media resolución NO se nota y evita el pico de RAM del
            // 3er atlas (~63 → ~16 MB en gama alta, ~4 MB en baja): principal sospechoso
            // del crash por OOM de sus peleas. Lleva SU escala en sheetScale.
            val needyId = fightIds.firstOrNull { id ->
                val d = SfFrameCatalog.load(context, id)
                SF_NEW_MOVE_STATES.any { d.animations[it.jsKey].isNullOrEmpty() }
            }
            val alpha = needyId?.let { needy ->
                val fallbackId = viewModel.alphaFallbackId(needy)
                val alphaSample = if (sheetSample > 1) sheetSample * 2 else 2
                runCatching {
                    AlphaFallback(
                        data = SfFrameCatalog.load(context, fallbackId),
                        sheetKey = fallbackId.spriteAsset.substringAfterLast('/'),
                        bitmap = context.assets.open(fallbackId.spriteAsset).use {
                            BitmapFactory.decodeStream(
                                it,
                                null,
                                BitmapFactory.Options().apply { inSampleSize = alphaSample },
                            )
                        }?.asImageBitmap(),
                        sheetScale = 1f / alphaSample,
                    )
                }.getOrNull()?.takeIf { it.bitmap != null }
            }
            // Alturas de CONTENIDO opaco POR IDENTIDAD: en GAMA BAJA se OMITEN (scan caro);
            // en media/alta se miden AQUÍ (IO), ya no en el hilo de UI.
            val contentH = if (lowEnd) {
                emptyMap()
            } else {
                fightIds.associateWith { id ->
                    val sheet = sheets[id.spriteAsset.substringAfterLast('/')]
                    if (sheet != null) {
                        measureFrameContentHeights(sheet, SfFrameCatalog.load(context, id).frames)
                    } else {
                        emptyMap()
                    }
                }
            }
            SfFightAssets(sheets, alpha, contentH)
        }
    }
    val images = remember(themeImages, fightAssets) {
        themeImages + (fightAssets?.sheets ?: emptyMap())
    }
    val alphaFallback = fightAssets?.alpha
    val playerContentH = fightAssets?.contentH?.get(playerId) ?: emptyMap()
    val cpuContentH = fightAssets?.contentH?.get(cpuId) ?: emptyMap()

    // ---- Selección offline en 4 pasos: PELEADOR → RIVAL → DIFICULTAD → MAPA ----
    var pendingFighter by remember { mutableStateOf<SfFighterId?>(null) }
    var pendingRival by remember { mutableStateOf<SfFighterId?>(null) } // 🆕 rival elegible offline
    var pendingDifficulty by remember { mutableStateOf<SfCpuDifficulty?>(null) } // 🆕 dificultad CPU
    var chosenBgFile by remember { mutableStateOf(theme.fullBackgrounds.firstOrNull()?.file) }
    LaunchedEffect(state.inCharacterSelect) {
        if (state.inCharacterSelect) { pendingFighter = null; pendingRival = null; pendingDifficulty = null }
    }
    // Fondo del combate: AUTOJUEGO/SHOWCASE manda el hogar del peleadór en turno; ARCADE su
    // mapa (ligado al rival); ONLINE el del ANFITRIÓN; si no, el elegido offline en el selector.
    val effectiveBgFile = when {
        state.gauntletRunning && state.gauntletMapFile != null -> state.gauntletMapFile
        (state.arcadeActive || state.aiVsAi) && state.arcadeMapFile != null -> state.arcadeMapFile
        state.onlineStatus != SfOnlineStatus.OFF && state.onlineMapFile != null -> state.onlineMapFile
        else -> chosenBgFile
    }
    // Fondo del combate: ANIMADO (atlas) o estático. RGB_565; en gama baja inSampleSize=2.
    // Carga en IO + overlay CARGANDO (evita freeze de UI al decodificar ~6 MB de atlas).
    var assetsLoading by remember { mutableStateOf(false) }
    var stageBg by remember { mutableStateOf<SfStageBackground?>(null) }
    LaunchedEffect(effectiveBgFile, state.inCharacterSelect, lowEnd) {
        if (state.inCharacterSelect) {
            stageBg = null
            assetsLoading = false
            return@LaunchedEffect
        }
        assetsLoading = true
        stageBg = withContext(kotlinx.coroutines.Dispatchers.IO) {
            effectiveBgFile?.let {
                loadStageBackground(context, theme.imagesDir, it, lowEnd = lowEnd)
            }
        }
        assetsLoading = false
    }

    // ---- Sonidos del tema (SoundPool efectos + MediaPlayer música) ----
    val soundPool = remember {
        SoundPool.Builder()
            // Más streams: golpes + specials por personaje a la vez (IA vs IA)
            .setMaxStreams(8)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .build()
    }
    // Base theme SFX + special_<fighter> por los peleadores del match (y Yoalli si hay metamorfosis).
    // Faltantes se omiten; el collect cae a "hadouken" si no hay special del id.
    val soundIds = remember(theme) {
        theme.soundKeys.distinct().mapNotNull { key ->
            runCatching {
                context.assets.openFd("${theme.soundsDir}$key.ogg").use { fd ->
                    key to soundPool.load(fd, 1)
                }
            }.getOrNull()
        }.toMap()
    }
    val activeSpecialPlayers = remember { mutableMapOf<String, MediaPlayer>() }
    val activeStreams = remember { mutableMapOf<String, Int>() }
    LaunchedEffect(soundIds) {
        viewModel.soundEvents.collect { key ->
            if (key == SF_STOP_SPECIALS_EVENT) {
                releaseSfSpecials(activeSpecialPlayers)
            } else if (key.startsWith("special_")) {
                val played = playSfSpecial(
                    context = context,
                    assetPath = "${theme.soundsDir}$key.ogg",
                    activePlayers = activeSpecialPlayers,
                )
                if (!played) {
                    val fallbackId = soundIds["hadouken"]
                    fallbackId?.let { pid ->
                        activeStreams["hadouken"]?.let { lastStream ->
                            soundPool.stop(lastStream)
                        }
                        val streamId = soundPool.play(pid, 1f, 1f, 1, 0, 1f)
                        activeStreams["hadouken"] = streamId
                    }
                }
            } else {
                val poolId = soundIds[key] ?: soundIds["hadouken"]
                poolId?.let { pid ->
                    val finalKey = if (soundIds.containsKey(key)) key else "hadouken"
                    activeStreams[finalKey]?.let { lastStream ->
                        soundPool.stop(lastStream)
                    }
                    val streamId = soundPool.play(pid, 1f, 1f, 1, 0, 1f)
                    activeStreams[finalKey] = streamId
                }
            }
        }
    }
    // 🆕 (2026-07-18) MÚSICA POR PROGRESIÓN: lobby en el selector; en pelea, la pista escala con
    // el NIVEL (arcade = avance de la escalera; práctica = dificultad de la CPU; IA vs IA = la más
    // dura). Al cambiar de pista (entrar a pelea, subir de escalón…) el MediaPlayer se recarga.
    val musicFileForState = remember(
        state.inCharacterSelect, state.aiVsAi, state.arcadeActive,
        state.arcadeStep, state.arcadeTotal, state.cpuDifficulty,
    ) {
        val battle = theme.battleMusic
        when {
            theme.lobbyMusic.isBlank() && battle.isEmpty() -> theme.musicFile // legado: pista única
            state.inCharacterSelect -> theme.lobbyMusic.ifBlank { theme.musicFile }
            battle.isEmpty() -> theme.lobbyMusic.ifBlank { theme.musicFile }
            else -> {
                val n = battle.size
                val frac = when {
                    state.aiVsAi -> 1f
                    state.arcadeActive && state.arcadeTotal > 1 ->
                        (state.arcadeStep - 1).toFloat() / (state.arcadeTotal - 1)
                    else -> when (state.cpuDifficulty) {
                        SfCpuDifficulty.BASICA -> 0f
                        SfCpuDifficulty.NORMAL -> 0.34f
                        SfCpuDifficulty.AVANZADA -> 0.67f
                        SfCpuDifficulty.PESADILLA -> 1f
                    }
                }
                // índice = round(frac*(n-1)) sin roundToInt (aritmética entera)
                battle[((frac * (n - 1)) + 0.5f).toInt().coerceIn(0, n - 1)]
            }
        }
    }
    val musicPlayer = remember { MediaPlayer() }
    // (Re)carga y arranca la pista cuando cambia la selección (lobby ⇄ batalla / nivel).
    LaunchedEffect(musicFileForState) {
        runCatching {
            musicPlayer.reset()
            context.assets.openFd(theme.soundsDir + musicFileForState).use { fd ->
                musicPlayer.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            musicPlayer.isLooping = true
            musicPlayer.setVolume(theme.musicVolume, theme.musicVolume)
            musicPlayer.prepare()
            musicPlayer.start()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { musicPlayer.stop() }
            musicPlayer.release()
            releaseSfSpecials(activeSpecialPlayers)
            soundPool.release()
        }
    }

    // PAUSA AUTOMÁTICA al bloquear el celular / minimizar: pausa + guarda sesión arcade
    // (forcePause → putString async, sin lag). Al volver, música reanuda; pelea sigue en PAUSA.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    viewModel.forcePause()
                    runCatching { if (musicPlayer.isPlaying) musicPlayer.pause() }
                    releaseSfSpecials(activeSpecialPlayers)
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

    // 🆕 Retomar pelea arcade a medias (si saliste / minimizaste)
    var showResumeDialog by remember { mutableStateOf(viewModel.hasArcadeSession()) }

    // Textos del banner de RONDA (i18n; la fuente arcade solo tiene A-Z/0-9)
    val roundBannerText = stringResource(R.string.sf_round_banner, state.roundNumber)
    val fightBannerText = stringResource(R.string.sf_fight_banner)
    // 🆕 (2026-07-20) Etiqueta del contador de COMBO ("GOLPES"/"HITS")
    val comboHitsLabel = stringResource(R.string.sf_combo_hits)
    // 🆕 (2026-07-21) ¿El peleador elegido tiene el moveset nuevo? (botones extra)
    val hasNewMoves = remember(state.player.id) { viewModel.playerHasNewMoves() }
    // 🆕 Ajustes → "Mostrar hitboxes" (se lee al entrar al modo)
    val showHitboxes = remember { viewModel.showHitboxes() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ---- Escena completa (mundo + HUD) en un Canvas ----
        // En selector no hace falta el Canvas de pelea (ahorra GPU en gama baja).
        // 🆕 (2026-07-22) Y tampoco se dibuja hasta tener los atlas de peleador (fightAssets):
        // sin ellos drawFighter caería a la hoja del HUD (basura visual bajo el overlay).
        if (!state.inCharacterSelect && fightAssets != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawScene(
                    theme, state, images, playerData, cpuData, stageBg,
                    roundBannerText, fightBannerText, comboHitsLabel, showHitboxes,
                    playerContentH, cpuContentH, effectiveBgFile,
                    playerSilhouette = !viewModel.isFighterActuallyUnlocked(state.player.id),
                    alphaFallback = alphaFallback,
                    sheetScale = sheetSampleScale,
                )
            }
        }
        // 🆕 Pantalla CARGANDO (fuente POW del HUD) mientras se decodifican atlas/hojas
        // (fondo Y ahora también los atlas de peleador, ver fightAssets arriba).
        if (!state.inCharacterSelect &&
            (assetsLoading || fightAssets == null || stageBg == null && effectiveBgFile != null)
        ) {
            SfLoadingOverlay(theme = theme)
        }

        // ---- Controles de POW: joystick + diamante Xbox (ocultos en selección y en IA vs IA) ----
        // IA vs IA: ambos los controla la CPU → solo botón "Salir" al menú (sin joystick/botones).
        if (!state.inCharacterSelect && !state.aiVsAi) {
            // 🆕 (2026-07-22) TUTORIAL: botón físico que pide el PASO ACTUAL (para el glow).
            val tutorialButton = if (state.tutorialActive && !state.tutorialCompleted) {
                state.tutorialSteps.getOrNull(state.tutorialStepIndex)?.let(::sfButtonForLabel)
            } else {
                null
            }
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
                bonusPowerCount = state.player.id.bonusPowerCount,
                onBonusPower = viewModel::onBonusPowerPressed,
                highlight = tutorialButton,
            )
            // 🆕 (2026-07-21) Botones del moveset 3rd Strike. Solo se muestran si el
            // peleador TIENE ese arte (los compartidos/ALPHA no los tienen).
            if (hasNewMoves) {
                FighterNewMoveButtons(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 190.dp),
                    superReady = state.player.superReady,
                    onParry = viewModel::onParryPressed,
                    onGrab = viewModel::onGrabPressed,
                    onTaunt = viewModel::onTauntPressed,
                    onSuper = viewModel::onSuperArtPressed,
                    highlight = tutorialButton,
                )
            }
            // 🆕 (2026-07-21) TUTORIAL: HUD guía encima de la pelea (el jugador usa los
            // controles normales; el rival es un muñeco inerte).
            if (state.tutorialActive) {
                SfTutorialOverlay(
                    lesson = state.tutorialLesson,
                    total = state.tutorialTotal,
                    title = state.tutorialTitle,
                    hint = state.tutorialHint,
                    steps = state.tutorialSteps,
                    stepIndex = state.tutorialStepIndex,
                    flash = state.tutorialFlash,
                    error = state.tutorialError,
                    completed = state.tutorialCompleted,
                    onSkip = viewModel::tutorialSkipLesson,
                    onRestart = viewModel::tutorialRestartLesson,
                    // `exitTutorial` devuelve el estado a selección de personaje; el
                    // LaunchedEffect(inCharacterSelect) reabre el flujo normal del modo.
                    onExit = viewModel::exitTutorial,
                )
            }
            if (!state.isPaused && !state.showEndMenu && !state.tutorialActive) {
                Text(
                    // 🆕 (2026-07-21) Con moveset nuevo se explica ESE (dash/parry/agarre/
                    // súper/barrida): es lo que el jugador no puede adivinar.
                    text = stringResource(
                        if (hasNewMoves) R.string.sf_controls_hint_new else R.string.sf_controls_hint,
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        // IA vs IA: botón "Salir" al menú de modos (sin controles táctiles de pelea).
        // Durante el AUTOJUEGO se oculta: ahí manda el botón DETENER de abajo.
        if (!state.inCharacterSelect && state.aiVsAi && !state.showEndMenu && !state.gauntletRunning) {
            PowButton(
                text = stringResource(R.string.sf_exit),
                onClick = viewModel::backToCharacterSelect,
                color = Color(0xFF8B1538),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth(0.36f),
            )
        }

        // 🆕 AUTOJUEGO (gauntlet): progreso + DETENER mientras corre; reporte de assets al terminar.
        if (state.gauntletRunning && !state.gauntletFinished) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "AUTOJUEGO ${state.gauntletProgress}",
                    color = Color(0xFFFFD54A),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_stop),
                    onClick = viewModel::stopGauntlet,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.32f),
                )
                // SALTAR (solo showcase): termina el peleador actual y avanza sin esperar el timer.
                if (state.showcaseRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PowButton(
                            text = stringResource(R.string.sf_showcase_prev_animation),
                            onClick = viewModel::goToPreviousShowcaseAnimation,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.width(156.dp),
                        )
                        PowButton(
                            text = stringResource(R.string.sf_showcase_next_animation),
                            onClick = viewModel::skipToNextShowcaseAnimation,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.width(156.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PowButton(
                            text = stringResource(R.string.sf_showcase_next_fighter),
                            onClick = viewModel::skipShowcaseFighter,
                            color = Color(0xFF7B3F00),
                            modifier = Modifier.width(156.dp),
                        )
                        PowButton(
                            text = stringResource(
                                R.string.sf_showcase_speed,
                                "${state.showcaseSpeed.toInt()}x",
                            ),
                            onClick = viewModel::cycleShowcaseSpeed,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.width(156.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PowButton(
                            text = stringResource(R.string.sf_showcase_replay_audio),
                            onClick = viewModel::replayCurrentShowcaseAudio,
                            color = Color(0xFF6A1B9A),
                            modifier = Modifier.width(156.dp),
                        )
                    }
                } else {
                    // 🆕 Para el Autojuego todos contra todos y Auditoría de campañas, mostramos el control de velocidad
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(
                        text = stringResource(
                            R.string.sf_showcase_speed,
                            "${state.showcaseSpeed.toInt()}x",
                        ),
                        onClick = viewModel::cycleShowcaseSpeed,
                        color = Color(0xFF1565C0),
                        modifier = Modifier.width(156.dp),
                    )
                }
            }
        }
        if (state.gauntletFinished) {
            GauntletReportOverlay(
                progress = state.gauntletProgress,
                report = state.gauntletReport,
                path = state.gauntletReportPath,
                onClose = viewModel::dismissGauntletReport,
            )
        }

        // ---- Selección pre-pelea: paso 1 PELEADOR, paso 2 MAPA (offline y online) ----
        var showOnlineMenu by remember { mutableStateOf(false) }
        // 🆕 ARCADE es el modo POR DEFECTO al entrar (se ven los personajes, más llamativo).
        var arcadeSetup by remember { mutableStateOf(true) }
        // Menú de MODOS (PRÁCTICA / IA VS IA / MULTIJUGADOR) = SECUNDARIO, se abre con "Otros modos".
        var sfMenu by remember { mutableStateOf(false) }
        // 🆕 Flujo IA vs IA: elige peleador A → peleaador B → startAiVsAi (PESADILLA, sin mapa).
        var aiVsAiSetup by remember { mutableStateOf(false) }
        // 🆕 (2026-07-21) HOJA DE COMBOS: lista de controles y combos + "PROBAR" (tutorial).
        var showComboSheet by remember { mutableStateOf(false) }
        var comboFighter by remember { mutableStateOf<SfFighterId?>(null) }
        // 🆕 (2026-07-22) MEMORIA DEL MODO: al salir de una pelea se vuelve al selector DEL
        // MISMO modo (antes SIEMPRE forzaba el selector de Arcade). Se registra al LANZAR
        // cada modo; "menu" = gauntlet/showcase (no tienen selector propio) → menú de modos.
        var lastLaunchedMode by remember { mutableStateOf("arcade") }
        LaunchedEffect(state.inCharacterSelect) {
            if (state.inCharacterSelect) {
                arcadeSetup = false
                sfMenu = false
                aiVsAiSetup = false
                when (lastLaunchedMode) {
                    "arcade" -> arcadeSetup = true
                    "aivsai" -> aiVsAiSetup = true
                    "practice" -> Unit // el selector de práctica es la rama default
                    "combos" -> showComboSheet = true // vuelve a la hoja del peleador probado
                    else -> sfMenu = true
                }
            }
        }
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
                    fighters = viewModel.selectableFighters(),
                    lockedFighters = viewModel.lockedFighters(),
                    subtitle = stringResource(R.string.sf_mp_pick_sub, state.roomCode ?: ""),
                    onSelect = viewModel::selectCharacter,
                    lowEnd = lowEnd,
                    isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                )
                SfOnlineStatus.WAITING_MAP -> if (state.isHost) {
                    StageSelectOverlay(
                        theme = theme,
                        unlockedMaps = if (viewModel.devUnlockAll()) null else viewModel.unlockedMaps(),
                        onSelect = viewModel::chooseMapOnline,
                        onBack = null,
                        lowEnd = lowEnd,
                    )
                } else {
                    OnlineInfoOverlay(
                        title = stringResource(R.string.sf_mp_room, state.roomCode ?: ""),
                        subtitle = stringResource(R.string.sf_mp_host_choosing_map),
                        onCancel = { viewModel.cancelOnline() },
                    )
                }
                SfOnlineStatus.COUNTDOWN -> Unit // el número gigante se dibuja abajo
                else -> {
                    val fighter = pendingFighter
                    val rival = pendingRival
                    val difficulty = pendingDifficulty
                    when {
                        // 🆕 (2026-07-21) HOJA DE COMBOS + TUTORIAL. Si aún no hay peleador
                        // elegido, primero se elige de entre los DESBLOQUEADOS.
                        showComboSheet && comboFighter == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters(),
                            subtitle = stringResource(R.string.sf_combos_pick_fighter),
                            lockedFighters = viewModel.lockedFighters(),
                            isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                            lowEnd = lowEnd,
                            onSelect = { comboFighter = it },
                            onBack = { showComboSheet = false; sfMenu = true },
                        )
                        showComboSheet -> SfComboSheetOverlay(
                            fighterId = comboFighter!!,
                            combos = remember(comboFighter) { viewModel.comboSheet(comboFighter!!) },
                            onTry = {
                                showComboSheet = false
                                lastLaunchedMode = "combos" // 🆕 al salir del tutorial: la hoja
                                viewModel.startTutorial(comboFighter!!)
                            },
                            onChangeFighter = { comboFighter = null },
                            onBack = {
                                showComboSheet = false
                                comboFighter = null
                                sfMenu = true
                            },
                        )
                        // 🆕 MENÚ DE MODOS (estilo POW): ARCADE principal, PRÁCTICA, IA VS IA, MULTIJUGADOR
                        sfMenu -> SfModeMenuOverlay(
                            devMode = viewModel.devUnlockAll(),
                            audioShowcaseRunning = state.audioShowcaseRunning,
                            audioShowcaseIndex = state.audioShowcaseIndex,
                            audioShowcaseTotal = state.audioShowcaseTotal,
                            audioShowcaseFighter = state.audioShowcaseFighter,
                            audioShowcasePhrase = state.audioShowcasePhrase,
                            onArcade = {
                                viewModel.stopAudioShowcase()
                                sfMenu = false
                                arcadeSetup = true
                                aiVsAiSetup = false
                                pendingFighter = null
                                pendingRival = null
                                pendingDifficulty = null
                            },
                            onPractice = {
                                viewModel.stopAudioShowcase()
                                sfMenu = false
                                arcadeSetup = false
                                aiVsAiSetup = false
                                pendingFighter = null
                                pendingRival = null
                                pendingDifficulty = null
                            },
                            onAiVsAi = {
                                viewModel.stopAudioShowcase()
                                sfMenu = false
                                arcadeSetup = false
                                aiVsAiSetup = true
                                pendingFighter = null
                                pendingRival = null
                                pendingDifficulty = null
                            },
                            onCombos = {
                                viewModel.stopAudioShowcase()
                                showComboSheet = true
                            },
                            onMultiplayer = {
                                viewModel.stopAudioShowcase()
                                showOnlineMenu = true
                            },
                            onGauntletAll = {
                                viewModel.stopAudioShowcase()
                                sfMenu = false
                                lastLaunchedMode = "menu" // 🆕 sin selector propio → menú
                                viewModel.startGauntletRoundRobin()
                            },
                            onGauntletArcade = {
                                viewModel.stopAudioShowcase()
                                sfMenu = false
                                lastLaunchedMode = "menu"
                                viewModel.startGauntletArcade()
                            },
                            onGauntletShowcase = {
                                viewModel.stopAudioShowcase()
                                sfMenu = false
                                lastLaunchedMode = "menu"
                                viewModel.startShowcase()
                            },
                            onAudioShowcaseStop = viewModel::stopAudioShowcase,
                            onBack = onExitToMap,
                        )
                        // ARCADE: peleadór → Fácil/Medio/Difícil (día / noche / apocalipsis)
                        arcadeSetup && fighter == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters(),
                            lockedFighters = viewModel.lockedFighters(),
                            subtitle = stringResource(R.string.sf_arcade_pick_you),
                            onSelect = { pendingFighter = it },
                            onBack = { arcadeSetup = false; sfMenu = true },
                            backText = stringResource(R.string.sf_other_modes),
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                        )
                        arcadeSetup && difficulty == null -> ArcadeDifficultyOverlay(
                            onSelect = { d ->
                                val p = fighter ?: return@ArcadeDifficultyOverlay
                                lastLaunchedMode = "arcade" // 🆕 volver = selector de Arcade
                                viewModel.startArcade(p, d)
                                pendingFighter = null
                                pendingDifficulty = null
                                arcadeSetup = false
                            },
                            onBack = { pendingFighter = null },
                        )
                        // 🆕 IA VS IA: dos peleadores (CPU vs CPU a PESADILLA) → startAiVsAi
                        // Roster = selectableFighters() (completo si Modo Desarrollador activo).
                        aiVsAiSetup && fighter == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters(),
                            lockedFighters = viewModel.lockedFighters(),
                            subtitle = stringResource(R.string.sf_ai_vs_ai_pick_a),
                            onSelect = { pendingFighter = it },
                            onBack = { aiVsAiSetup = false; sfMenu = true },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                        )
                        aiVsAiSetup && rival == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters(),
                            lockedFighters = viewModel.lockedFighters(),
                            subtitle = stringResource(R.string.sf_ai_vs_ai_pick_b),
                            allyId = fighter,        // 🆕 flecha AZUL sobre el P1 elegido
                            showPickArrow = true,    // 🆕 flecha ROJA sobre el P2 resaltado
                            onSelect = { b ->
                                val a = fighter!!
                                // Fondo al azar entre mapas DESBLOQUEADOS (o todos en Modo Dev)
                                if (chosenBgFile == null) {
                                    val unlocked = viewModel.unlockedMaps()
                                    val pool = if (viewModel.devUnlockAll()) {
                                        theme.fullBackgrounds.map { it.file }
                                    } else {
                                        theme.fullBackgrounds.map { it.file }.filter { it in unlocked }
                                    }
                                    chosenBgFile = pool.randomOrNull()
                                        ?: theme.fullBackgrounds.firstOrNull()?.file
                                }
                                lastLaunchedMode = "aivsai" // 🆕 volver = selector de IA vs IA
                                viewModel.startAiVsAi(a, b)
                                pendingFighter = null
                                pendingRival = null
                                aiVsAiSetup = false
                            },
                            onBack = { pendingFighter = null },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                        )
                        // PRÁCTICA (versus): peleador → RIVAL → DIFICULTAD → mapa
                        fighter == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters(),
                            lockedFighters = viewModel.lockedFighters(),
                            subtitle = state.onlineError,
                            onSelect = { pendingFighter = it },
                            onBack = { sfMenu = true },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                        )
                        rival == null -> CharacterSelectOverlay(
                            fighters = viewModel.selectableFighters(),
                            lockedFighters = viewModel.lockedFighters(),
                            subtitle = stringResource(R.string.sf_choose_rival),
                            allyId = fighter,        // 🆕 flecha AZUL sobre el P1 elegido
                            showPickArrow = true,    // 🆕 flecha ROJA sobre el P2 resaltado
                            onSelect = { pendingRival = it },
                            onBack = { pendingFighter = null },
                            lowEnd = lowEnd,
                            isActuallyUnlocked = { viewModel.isFighterActuallyUnlocked(it) },
                        )
                        difficulty == null -> DifficultySelectOverlay(
                            onSelect = { pendingDifficulty = it },
                            onBack = { pendingFighter = null; pendingRival = null },
                        )
                        else -> StageSelectOverlay(
                            theme = theme,
                            unlockedMaps = if (viewModel.devUnlockAll()) null else viewModel.unlockedMaps(),
                            onSelect = { file ->
                                val unlocked = viewModel.unlockedMaps()
                                val pool = if (viewModel.devUnlockAll()) {
                                    theme.fullBackgrounds.map { it.file }
                                } else {
                                    theme.fullBackgrounds.map { it.file }.filter { it in unlocked }
                                }
                                chosenBgFile = file
                                    ?: pool.randomOrNull()
                                    ?: theme.fullBackgrounds.firstOrNull()?.file
                                lastLaunchedMode = "practice" // 🆕 volver = selector de práctica
                                viewModel.selectCharacter(fighter, rival, difficulty)
                            },
                            onBack = { pendingFighter = null; pendingRival = null; pendingDifficulty = null },
                            lowEnd = lowEnd,
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

        // 🆕 Fin de pelea en ARCADE: ganar (CONTINUAR) / perder (REINTENTAR) / campeón. Reemplaza
        // el menú normal mientras haya una escalera en curso.
        if (state.showEndMenu && state.arcadeActive) {
            ArcadeResultOverlay(
                outcome = state.arcadeOutcome,
                step = state.arcadeStep,
                total = state.arcadeTotal,
                onContinue = viewModel::arcadeContinue,
                onRetry = viewModel::arcadeRetry,
                onExit = viewModel::arcadeExit,
            )
        }

        // Menú de fin de pelea (VERSUS / online; en arcade lo sustituye ArcadeResultOverlay)
        if (state.showEndMenu && !state.arcadeActive) {
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
                    Text(
                        text = stringResource(R.string.sf_controls_help),
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PowButton(text = stringResource(R.string.sf_continue), onClick = viewModel::togglePause)
                }
            }
        }

        // 🆕 Retomar pelea arcade guardada (tras minimizar / salir a medias)
        if (showResumeDialog && state.inCharacterSelect) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.discardArcadeSession()
                    showResumeDialog = false
                },
                containerColor = Color(0xFF1A1016),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f),
                title = { Text(stringResource(R.string.sf_resume_title)) },
                text = { Text(stringResource(R.string.sf_resume_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (viewModel.resumeArcadeSession()) showResumeDialog = false
                        else showResumeDialog = false
                    }) { Text(stringResource(R.string.sf_resume_yes), color = Color(0xFFD4AF37)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.discardArcadeSession()
                        showResumeDialog = false
                    }) { Text(stringResource(R.string.sf_resume_no), color = Color.White.copy(alpha = 0.7f)) }
                },
            )
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
                confirmButton = {
                    // Al salir a menú con arcade activo, forcePause ya guardó; aquí re-guarda por si acaso
                    TextButton(onClick = {
                        viewModel.forcePause()
                        onExitToMap()
                    }) { Text(stringResource(R.string.sf_exit_confirm), color = Color(0xFFD4AF37)) }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissExitDialog) { Text(stringResource(R.string.sf_keep_fighting), color = Color.White.copy(alpha = 0.7f)) } },
            )
        }
    }
}

// ------------------------------------------------------------------
// 🆕 Menú de MODOS del modo pelea (estilo POW): al entrar, en vez del selector directo,
// se muestra ARCADE (principal), PRÁCTICA y MULTIJUGADOR. Arcade = solo eliges peleador.
// ------------------------------------------------------------------

@Composable
private fun GauntletReportOverlay(
    progress: String,
    report: List<String>,
    path: String?,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF0101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_gauntlet_report_title),
                color = Color(0xFFD4AF37),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$progress · ${report.size}",
                color = Color.White,
                fontSize = 12.sp,
            )
            if (path != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = path,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (report.isEmpty()) {
                Text(
                    text = stringResource(R.string.sf_gauntlet_no_issues),
                    color = Color(0xFF8BC34A),
                    fontSize = 13.sp,
                )
            } else {
                report.forEach { line ->
                    Text(
                        text = "• $line",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            PowButton(
                text = stringResource(R.string.sf_close),
                onClick = onClose,
                color = Color(0xFF1C4A6B),
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }
    }
}

@Composable
private fun SfModeMenuOverlay(
    // 🆕 (2026-07-18) Autojuego/Showcase son EXCLUSIVOS del Modo Desarrollador (QA interno).
    devMode: Boolean,
    audioShowcaseRunning: Boolean,
    audioShowcaseIndex: Int,
    audioShowcaseTotal: Int,
    audioShowcaseFighter: SfFighterId?,
    audioShowcasePhrase: String,
    onArcade: () -> Unit,
    onPractice: () -> Unit,
    onAiVsAi: () -> Unit,
    onCombos: () -> Unit, // 🆕 (2026-07-21) hoja de combos + tutorial interactivo
    onMultiplayer: () -> Unit,
    onGauntletAll: () -> Unit,
    onGauntletArcade: () -> Unit,
    onGauntletShowcase: () -> Unit,
    onAudioShowcaseStop: () -> Unit,
    onBack: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.menu_street_fighter),
                color = Color(0xFFD4AF37),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            // ARCADE — modalidad PRINCIPAL (grande y destacada, ahora ANIMADO como en el menú principal)
            FeaturedArcadeButton(
                text = stringResource(R.string.sf_mode_arcade),
                tag = stringResource(R.string.sf_mode_arcade_desc),
                onClick = onArcade,
                enabled = true
            )
            Spacer(modifier = Modifier.height(18.dp))
            // Otras modalidades (ahora respetando el color guinda del menú principal)
            PowButton(
                text = stringResource(R.string.sf_mode_practice),
                onClick = onPractice,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 🆕 IA VS IA (CPU vs CPU a PESADILLA; para grabar en video)
            PowButton(
                text = stringResource(R.string.sf_mode_ai_vs_ai),
                onClick = onAiVsAi,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sf_mode_ai_vs_ai_desc),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            // 🆕 (2026-07-21) HOJA DE COMBOS + TUTORIAL interactivo. Visible SIEMPRE (no es
            // una herramienta de QA: es como se aprende a jugar el modo).
            Spacer(modifier = Modifier.height(10.dp))
            PowButton(
                text = stringResource(R.string.sf_mode_combos),
                onClick = onCombos,
                color = Color(0xFF1C6B4A),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sf_mode_combos_desc),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            // 🆕 AUTOJUEGO / SHOWCASE: QA interno, SOLO con Modo Desarrollador (Ajustes).
            if (devMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sf_dev_tools_header),
                    color = Color(0xFFFFD54A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_all),
                    onClick = onGauntletAll,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_arcade),
                    onClick = onGauntletArcade,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_showcase),
                    onClick = onGauntletShowcase,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
                if (audioShowcaseRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(
                        text = stringResource(R.string.sf_audio_showcase_stop),
                        onClick = onAudioShowcaseStop,
                        color = Color(0xFF8B1538),
                        modifier = Modifier.fillMaxWidth(0.68f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.sf_audio_showcase_progress,
                            audioShowcaseIndex,
                            audioShowcaseTotal,
                            audioShowcaseFighter?.shortName ?: "",
                        ),
                        color = Color(0xFFFFD54A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = audioShowcasePhrase,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.82f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PowButton(
                text = stringResource(R.string.sf_mode_multiplayer),
                onClick = onMultiplayer,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.sf_back), color = Color(0xFFD4AF37))
            }
        }
    }
}

/**
 * Botón principal animado estilo POW para el modo Arcade.
 * Presenta pulso de escala, barrido de brillo dorado, borde y sombra doradas latentes.
 */
@Composable
private fun FeaturedArcadeButton(text: String, tag: String, onClick: () -> Unit, enabled: Boolean) {
    val shape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
    val gold = Color(0xFFFFD54A)
    val tr = rememberInfiniteTransition(label = "sfArcadeFeatured")
    val scale by tr.animateFloat(
        1f, 1.05f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )
    val glow by tr.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    val shimmer by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "shimmer",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(76.dp)
            .graphicsLayer { scaleX = if (enabled) scale else 1f; scaleY = if (enabled) scale else 1f }
            .shadow(elevation = 18.dp, shape = shape, ambientColor = gold, spotColor = gold)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF8A0F32), Color(0xFFC4143C), Color(0xFF8A0F32))),
            )
            .border(BorderStroke(3.dp, gold.copy(alpha = if (enabled) glow else 0.5f)), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Barrido de brillo dorado (detrás del texto)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val w = size.width
                    val hl = w * 0.30f
                    val x = -hl + (w + hl) * shimmer
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to gold.copy(alpha = 0.40f),
                            1f to Color.Transparent,
                            startX = x,
                            endX = x + hl,
                        ),
                    )
                },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "★ $text ★",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tag,
                color = gold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
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

/**
 * 🆕 (2026-07-18) Cabecera de flechas sobre un card del selector:
 *  - AZUL "P1 ▼": el peleador YA elegido (jugador 1), visible al elegir al rival.
 *  - ROJA "P2 ▼": el card que estás resaltando al elegir al jugador 2.
 * Alto FIJO (reserva el espacio aunque no haya flecha) para que los cards no salten.
 */
@Composable
private fun SelectArrowHeader(showAlly: Boolean, showPick: Boolean) {
    val blue = Color(0xFF2196F3)
    val red = Color(0xFFE53935)
    Box(
        modifier = Modifier.height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // 🆕 (2026-07-18ñ) MISMO personaje para P1 y P2: se muestran AMBAS flechas.
            showAlly && showPick -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("P1 ▼", color = blue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("P2 ▼", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            showPick -> Text("P2 ▼", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black)
            showAlly -> Text("P1 ▼", color = blue, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CharacterSelectOverlay(
    fighters: List<SfFighterId>, // DESBLOQUEADOS (RYU/KEN solo con Modo Desarrollador)
    onSelect: (SfFighterId) -> Unit,
    subtitle: String? = null,
    lockedFighters: List<SfFighterId> = emptyList(), // 🆕 se pintan con candado 🔒 (no seleccionables)
    onOnline: (() -> Unit)? = null,
    onArcade: (() -> Unit)? = null,                  // 🆕 abre el flujo de ARCADE
    onBack: (() -> Unit)? = null,                    // 🆕 volver (p. ej. salir del setup de arcade)
    backText: String? = null,                        // 🆕 etiqueta del botón volver (default "← Volver")
    /** Gama baja: previews siempre estáticos (sin animar ningún card). */
    lowEnd: Boolean = false,
    // 🆕 (2026-07-18) Al elegir al PELEADOR 2/rival: FLECHA AZUL sobre el P1 ya elegido y
    // FLECHA ROJA sobre el que estás resaltando (además de la animación del card).
    allyId: SfFighterId? = null,      // P1 ya elegido → flecha AZUL ("P1")
    showPickArrow: Boolean = false,   // true en el paso de elegir P2 → flecha ROJA en el focused
    isActuallyUnlocked: (SfFighterId) -> Boolean = { true },
) {
    // 🆕 Solo el focused anima (y solo si NO es gama baja). 2.º toque confirma.
    var focusedId by remember(fighters) { mutableStateOf(fighters.firstOrNull()) }
    // 🆕 (2026-07-18ñ) Parpadeo azul⇄rojo del recuadro cuando P1 y P2 son el MISMO personaje.
    var flashRed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { delay(450); flashRed = !flashRed }
    }
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
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
                    val isAlly = id == allyId
                    val isPick = showPickArrow && id == focusedId
                    val both = isAlly && isPick // el resaltado (P2) es el MISMO que el P1 elegido
                    // Mismo personaje → recuadro PARPADEA azul⇄rojo; si no, rojo P2 o azul P1.
                    val hl = when {
                        both -> if (flashRed) Color(0xFFE53935) else Color(0xFF2196F3)
                        isPick -> Color(0xFFE53935)
                        isAlly -> Color(0xFF2196F3)
                        else -> null
                    }
                    // 🆕 Cada card con un espacio ARRIBA para las flechas P1 (azul) / P2 (roja).
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SelectArrowHeader(showAlly = isAlly, showPick = isPick)
                        CharacterCard(
                            id = id,
                            selected = id == focusedId,
                            animate = !lowEnd && id == focusedId,
                            highlightColor = hl,
                            silhouette = !isActuallyUnlocked(id),
                            onSelect = {
                                if (lowEnd || id == focusedId) onSelect(id)
                                else focusedId = id
                            },
                        )
                    }
                }
                // Bloqueados: estáticos (nunca animar)
                lockedFighters.forEach { id ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SelectArrowHeader(showAlly = false, showPick = false)
                        CharacterCard(id = id, onSelect = {}, locked = true, animate = false, selected = false)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.sf_alpha_note),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
            // 🆕 Botón de CONFIRMAR el peleador resaltado (antes solo confirmaba el 2.º toque).
            focusedId?.let { fid ->
                Spacer(modifier = Modifier.height(8.dp))
                PowButton(
                    text = stringResource(R.string.sf_confirm),
                    onClick = { onSelect(fid) },
                    color = Color(0xFFB8143A),
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onArcade?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(text = stringResource(R.string.sf_arcade_button), onClick = it)
                }
                onOnline?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(text = stringResource(R.string.sf_mp_button), onClick = it)
                }
            }
            onBack?.let {
                TextButton(onClick = it) {
                    Text(backText ?: stringResource(R.string.sf_back), color = Color(0xFFD4AF37))
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 🆕 Overlay de fin del MODO ARCADE: ganar (CONTINUAR) / perder (REINTENTAR) / campeón.
// ------------------------------------------------------------------

@Composable
private fun ArcadeResultOverlay(
    outcome: SfArcadeOutcome,
    step: Int,
    total: Int,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(powMenuBg)
            .clickable(enabled = true, onClick = {}), // bloquea toques al joystick de atrás
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (outcome) {
                SfArcadeOutcome.COMPLETED -> {
                    Text(
                        text = stringResource(R.string.sf_arcade_champion),
                        color = Color(0xFFD4AF37), fontSize = 26.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sf_arcade_champion_sub),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PowButton(text = stringResource(R.string.sf_arcade_finish), onClick = onExit)
                }
                SfArcadeOutcome.LOST -> {
                    Text(
                        text = stringResource(R.string.sf_arcade_lost),
                        color = Color(0xFFEF5350), fontSize = 24.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sf_arcade_lost_sub),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PowButton(text = stringResource(R.string.sf_arcade_retry), onClick = onRetry)
                        PowButton(text = stringResource(R.string.sf_arcade_quit), onClick = onExit)
                    }
                }
                else -> { // WON
                    Text(
                        text = stringResource(R.string.sf_arcade_won),
                        color = Color(0xFF81C784), fontSize = 24.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sf_arcade_progress, step, total),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PowButton(text = stringResource(R.string.sf_arcade_continue), onClick = onContinue)
                        PowButton(text = stringResource(R.string.sf_arcade_quit), onClick = onExit)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 🆕 Selector de DIFICULTAD de la CPU (paso 3 del flujo offline, 2026-07-16):
// BÁSICA (aprender) / NORMAL (la clásica) / AVANZADA (casi imposible).
// Solo offline: online el rival es humano y este paso no existe.
// ------------------------------------------------------------------

@Composable
private fun DifficultySelectOverlay(
    onSelect: (SfCpuDifficulty) -> Unit,
    onBack: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_choose_difficulty),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            DifficultyOption(
                title = stringResource(R.string.sf_diff_basic),
                desc = stringResource(R.string.sf_diff_basic_desc),
                onClick = { onSelect(SfCpuDifficulty.BASICA) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_diff_normal),
                desc = stringResource(R.string.sf_diff_normal_desc),
                onClick = { onSelect(SfCpuDifficulty.NORMAL) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_diff_advanced),
                desc = stringResource(R.string.sf_diff_advanced_desc),
                onClick = { onSelect(SfCpuDifficulty.AVANZADA) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_diff_nightmare),
                desc = stringResource(R.string.sf_diff_nightmare_desc),
                onClick = { onSelect(SfCpuDifficulty.PESADILLA) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.sf_change_fighter),
                    color = Color(0xFFD4AF37),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Dificultad del ARCADE: solo 3 niveles.
 * Fácil → mapas de día · Medio → noche · Difícil → noche apocalíptica (noche_2).
 */
@Composable
private fun ArcadeDifficultyOverlay(
    onSelect: (SfCpuDifficulty) -> Unit,
    onBack: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_choose_difficulty),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sf_arcade_diff_maps_hint),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            DifficultyOption(
                title = stringResource(R.string.sf_arcade_diff_easy),
                desc = stringResource(R.string.sf_arcade_diff_easy_desc),
                onClick = { onSelect(SfCpuDifficulty.BASICA) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_arcade_diff_medium),
                desc = stringResource(R.string.sf_arcade_diff_medium_desc),
                onClick = { onSelect(SfCpuDifficulty.NORMAL) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_arcade_diff_hard),
                desc = stringResource(R.string.sf_arcade_diff_hard_desc),
                onClick = { onSelect(SfCpuDifficulty.AVANZADA) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.sf_change_fighter),
                    color = Color(0xFFD4AF37),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Botón de dificultad + su descripción corta debajo (mismo tema vino/dorado). */
@Composable
private fun DifficultyOption(title: String, desc: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PowButton(text = title, onClick = onClick)
        Text(
            text = desc,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
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

/** Reduce el bitmap a `targetW` px (nearest, sin filtro) → efecto PIXELADO al re-escalarlo grande. */
private fun pixelateBitmap(src: ImageBitmap, targetW: Int): ImageBitmap {
    val bmp = src.asAndroidBitmap()
    val w = targetW.coerceAtLeast(1)
    val h = (w.toFloat() * bmp.height / bmp.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bmp, w, h, false).asImageBitmap()
}

@Composable
private fun CharacterCard(
    id: SfFighterId,
    onSelect: (SfFighterId) -> Unit,
    locked: Boolean = false,
    /** true = animar idle+walk; false = un solo frame estático (default en gama baja). */
    animate: Boolean = false,
    selected: Boolean = false,
    // 🆕 (2026-07-18) Recuadro translúcido + borde del color de la flecha (azul P1 / rojo P2)
    // para que se note quién es tu peleador y quién el rival. null = card normal.
    highlightColor: Color? = null,
    silhouette: Boolean = false, // 🆕 (2026-07-19)
    // 🆕 (2026-07-19) REVELAR a color: solo con sesión Google en Firebase + Modo Desarrollador.
    reveal: Boolean = false,
) {
    val preview = rememberFighterPreview(id, animate = animate && (reveal || (!locked && !silhouette)))
    // 🆕 BLOQUEADO / SILUETA: silueta pixelada negra (siempre estática). Con `reveal` se ve normal.
    val obscure = (locked || silhouette) && !reveal
    val shown = if (obscure && preview != null) remember(preview) { pixelateBitmap(preview, 12) } else preview
    val shape = RoundedCornerShape(10.dp)
    // El recuadro del color de la flecha manda sobre el fondo/borde normales.
    val cardBg = highlightColor?.copy(alpha = 0.28f) ?: Color(0xFF23233A)
    val cardBorder = when {
        highlightColor != null -> highlightColor
        selected && !locked -> Color(0xFFD4AF37)
        else -> null
    }
    Column(
        modifier = Modifier
            .clip(shape)
            .background(cardBg)
            .then(if (cardBorder != null) Modifier.border(3.dp, cardBorder, shape) else Modifier)
            .clickable(enabled = !locked) { onSelect(id) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(86.dp), contentAlignment = Alignment.BottomCenter) {
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = if (locked) "???" else id.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    colorFilter = if (obscure) ColorFilter.tint(Color(0xFF15151F)) else null,
                )
            } else {
                Text("?", color = Color.White, fontSize = 40.sp)
            }
            // 🆕 Candado del ARCADE en la esquina superior (motiva a desbloquear jugando)
            if (locked) {
                Text(
                    text = "🔒",
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (id.isAlpha && !locked) {
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
            text = if (locked && !reveal) "???" else id.displayName, // oculta la identidad hasta desbloquear
            color = if (locked && !reveal) Color(0xFFFFD54A) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(90.dp),
        )
    }
}

// Selector de MAPA: vive en SfStageSelectOverlay.kt (miniatura estática + preview animado
// SOLO del focused, un frame a la vez; confirmación explícita). No mezclar con el draw loop.

/**
 * Preview animado del selector de PERSONAJE. Para sheets dedicados recorta solo las regiones
 * de Idle (nunca decodifica la hoja completa); los compartidos usan el Idle del mundo.
 */
private data class FighterPreviewAnimation(
    val frames: List<ImageBitmap>,
    val delaysMs: List<Long>,
)

// 🆕 Ritmo del PREVIEW del selector (más lento que la pelea, para que no "vibre").
private const val PREVIEW_SLOWDOWN = 1.8f  // factor sobre los delays del JSON (dedicados)
private const val PREVIEW_MIN_MS = 95L     // mínimo por frame
private const val PREVIEW_SHARED_MS = 170L // frame fijo para peleadores compartidos (runtime)

/**
 * Preview del selector de personaje.
 * - animate=false (default / no focused / gama baja): 1 frame IDLE estático (barato).
 * - animate=true (solo el focused en media/alta): idle+walk en loop.
 * Nunca decodifica la hoja completa: BitmapRegionDecoder o previewFramesFor.
 */
@Composable
private fun rememberFighterPreview(id: SfFighterId, animate: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val animation = remember(id, animate) {
        runCatching {
            val shared = id.sharedSet
            if (shared != null) {
                val raw = SfSharedSheets.previewFramesFor(context, shared)
                val frames = if (animate) {
                    raw.map { trimTransparent(it).asImageBitmap() }
                } else {
                    listOfNotNull(raw.firstOrNull()?.let { trimTransparent(it).asImageBitmap() })
                }
                FighterPreviewAnimation(frames, List(frames.size) { PREVIEW_SHARED_MS })
            } else {
                val data = SfFrameCatalog.load(context, id)
                val decoder = context.assets.open(id.spriteAsset).use { ins ->
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(ins, false)
                } ?: return@runCatching null
                try {
                    fun decodeState(stateKey: String, maxFrames: Int = Int.MAX_VALUE): Pair<List<ImageBitmap>, List<Long>> {
                        val steps = data.animations[stateKey].orEmpty().filter { it.delay > 0 }.take(maxFrames)
                        val fr = steps.mapNotNull { step ->
                            val src = data.frames[step.frameKey]?.src ?: return@mapNotNull null
                            decoder.decodeRegion(
                                Rect(src[0], src[1], src[0] + src[2], src[1] + src[3]),
                                null,
                            )?.let { trimTransparent(it).asImageBitmap() }
                        }
                        val dl = steps.take(fr.size).map {
                            (it.delay * SfConstants.FRAME_TIME_MS * PREVIEW_SLOWDOWN).toLong()
                                .coerceAtLeast(PREVIEW_MIN_MS)
                        }
                        return fr to dl
                    }
                    if (!animate) {
                        // Solo 1 frame idle (estático)
                        val (idleF, idleD) = decodeState(SfFighterState.IDLE.jsKey, maxFrames = 1)
                        FighterPreviewAnimation(idleF, idleD.ifEmpty { listOf(PREVIEW_MIN_MS) })
                    } else {
                        val (idleF, idleD) = decodeState(SfFighterState.IDLE.jsKey)
                        val (walkF, walkD) = decodeState(SfFighterState.WALK_FORWARD.jsKey)
                        FighterPreviewAnimation(idleF + walkF, idleD + walkD)
                    }
                } finally {
                    decoder.recycle()
                }
            }
        }.getOrNull()?.takeIf { it.frames.isNotEmpty() }
    }
    var frameIndex by remember(id, animation, animate) { mutableStateOf(0) }
    LaunchedEffect(id, animation, animate) {
        frameIndex = 0
        val anim = animation ?: return@LaunchedEffect
        if (!animate || anim.frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(anim.delaysMs.getOrElse(frameIndex) { 100L })
            frameIndex = (frameIndex + 1) % anim.frames.size
        }
    }
    return animation?.frames?.getOrNull(frameIndex)
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
    bonusPowerCount: Int,
    onBonusPower: () -> Unit,
    // 🆕 (2026-07-22) TUTORIAL: letra del botón que TOCA presionar (brilla/pulsa) o null.
    highlight: String? = null,
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
            SfTutorialButtonGlow(active = highlight == "Y") {
                ActionButton(text = "Y", color = Color(0xFFF1C40F), onHoldEvent = { pressed ->
                    if (pressed) onPunch(SfAttackStrength.MEDIUM)
                })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // X izquierda — PUÑO LIGERO (azul)
                SfTutorialButtonGlow(active = highlight == "X") {
                    ActionButton(text = "X", color = Color(0xFF3498DB), onHoldEvent = { pressed ->
                        if (pressed) onPunch(SfAttackStrength.LIGHT)
                    })
                }
                Spacer(modifier = Modifier.size(48.dp))
                // B derecha — PUÑO FUERTE (rojo)
                SfTutorialButtonGlow(active = highlight == "B") {
                    ActionButton(text = "B", color = Color(0xFFE74C3C), onHoldEvent = { pressed ->
                        if (pressed) onPunch(SfAttackStrength.HEAVY)
                    })
                }
            }
            // A abajo — PATADA (verde; fuerza según joystick: neutro/adelante/atrás)
            SfTutorialButtonGlow(active = highlight == "A") {
                ActionButton(text = "A", color = Color(0xFF2ECC71), onHoldEvent = { pressed ->
                    if (pressed) onKick()
                })
            }
        }
        if (bonusPowerCount > 0) {
            // Centro del diamante: recorre P1..PN. El siguiente toque avanza al poder
            // siguiente; Yoalli tiene 9 y La Tzitzimime 5.
            ActionButton(text = "P", color = Color(0xFF8E44AD), onHoldEvent = { pressed ->
                if (pressed) onBonusPower()
            })
        }
    }
}

/**
 * 🆕 (2026-07-22) RESALTADO del botón que pide el paso ACTUAL del tutorial: pulsa de tamaño
 * y lleva un aro amarillo. Con active=false es transparente (no cambia el layout del botón).
 */
@Composable
private fun SfTutorialButtonGlow(active: Boolean, content: @Composable () -> Unit) {
    if (!active) {
        content()
        return
    }
    val pulse by rememberInfiniteTransition(label = "sfTutorialPulse").animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(340), RepeatMode.Reverse),
        label = "sfTutorialPulseF",
    )
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = pulse; scaleY = pulse }
            .border(3.dp, Color(0xFFFFF176), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 🆕 (2026-07-22) Botón físico que corresponde a la etiqueta de un paso del tutorial
 * (mismo truco de substring que chipColor en SfTutorialOverlay). null = va con el joystick.
 */
private fun sfButtonForLabel(label: String): String? = when {
    label.contains("FATALITY") || label.contains("SÚPER") -> "S"
    label.contains("PARRY") -> "P"
    label.contains("AGARRE") -> "G"
    label.contains("BURLA") -> "T"
    label.contains("PUÑO LIGERO") -> "X"
    label.contains("PUÑO MEDIO") -> "Y"
    label.contains("PUÑO FUERTE") -> "B"
    label.contains("PUÑO") -> "X" // ↓ + puño / puño en el aire / especial (remate con puño)
    label.contains("PATADA") -> "A"
    else -> null
}

// ------------------------------------------------------------------
// 🆕 (2026-07-21) Botones del MOVESET 3rd Strike: parry, agarre, burla y súper.
// Van en una columna aparte del diamante para no cambiar los controles de siempre.
// La SÚPER solo se ve activa con el medidor lleno.
// ------------------------------------------------------------------

@Composable
private fun FighterNewMoveButtons(
    modifier: Modifier = Modifier,
    superReady: Boolean,
    onParry: () -> Unit,
    onGrab: () -> Unit,
    onTaunt: () -> Unit,
    onSuper: () -> Unit,
    // 🆕 (2026-07-22) TUTORIAL: letra del botón que TOCA presionar (brilla/pulsa) o null.
    highlight: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Burla (gris, sin efecto en combate)
        SfTutorialButtonGlow(active = highlight == "T") {
            ActionButton(text = "T", color = Color(0xFF7F8C8D), onHoldEvent = { pressed ->
                if (pressed) onTaunt()
            })
        }
        Spacer(modifier = Modifier.size(6.dp))
        // Parry (cian): desvía el golpe si se aprieta a tiempo
        SfTutorialButtonGlow(active = highlight == "P") {
            ActionButton(text = "P", color = Color(0xFF1ABC9C), onHoldEvent = { pressed ->
                if (pressed) onParry()
            })
        }
        Spacer(modifier = Modifier.size(6.dp))
        // Agarre (naranja): lanza al rival pegado, atraviesa la guardia
        SfTutorialButtonGlow(active = highlight == "G") {
            ActionButton(text = "G", color = Color(0xFFE67E22), onHoldEvent = { pressed ->
                if (pressed) onGrab()
            })
        }
        Spacer(modifier = Modifier.size(6.dp))
        // Súper (dorado si está cargada, apagado si no)
        SfTutorialButtonGlow(active = highlight == "S") {
            ActionButton(
                text = "S",
                color = if (superReady) Color(0xFFFFD700) else Color(0xFF555555),
                onHoldEvent = { pressed -> if (pressed) onSuper() },
            )
        }
    }
}

// ------------------------------------------------------------------
// Render de la escena (mundo virtual 382x224 con letterbox "contain").
// Todos los recortes/posiciones salen del SfTheme.
// ------------------------------------------------------------------

/**
 * 🆕 (2026-07-21) Arte PRESTADA para el placeholder ALPHA: hoja del estudiante del mismo
 * género que se usa cuando al peleador le falta la hoja de un movimiento nuevo.
 */
private class AlphaFallback(
    val data: SfFighterData,
    val sheetKey: String,
    val bitmap: ImageBitmap?,
    /** 🆕 (2026-07-22) Submuestreo PROPIO del atlas ALPHA (silueta → media res gratis). */
    val sheetScale: Float = 1f,
)

/**
 * 🆕 (2026-07-22, Bloque B) Assets PESADOS de una pelea, decodificados en Dispatchers.IO
 * bajo el overlay CARGANDO: atlas por identidad (incluye ambas caras de una metamorfosis),
 * placeholder ALPHA y alturas de contenido opaco por identidad (vacío en gama baja).
 */
private class SfFightAssets(
    val sheets: Map<String, ImageBitmap>,
    val alpha: AlphaFallback?,
    val contentH: Map<SfFighterId, Map<String, Int>>,
)

/** Rótulo del placeholder (fuente arcade del HUD: solo A-Z y 0-9). */
private const val ALPHA_TAG = "ALPHA"

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
    bg: SfStageBackground?,
    roundBannerText: String,
    fightBannerText: String,
    comboHitsLabel: String,
    showHitboxes: Boolean = false,
    playerContentH: Map<String, Int> = emptyMap(),
    cpuContentH: Map<String, Int> = emptyMap(),
    bgFile: String? = null,
    playerSilhouette: Boolean = false,
    alphaFallback: AlphaFallback? = null,
    // 🆕 (2026-07-21) Submuestreo de los atlas de peleador en gama baja (1f = completo).
    sheetScale: Float = 1f,
) {
    val framing = framingForBg(bgFile) // 🆕 zoom/anclaje por escenario (Facultad de Medicina…)
    val scale = minOf(size.width / SfConstants.SCENE_WIDTH, size.height / SfConstants.SCENE_HEIGHT)
    val ctx = SceneCtx(
        scale = scale,
        ox = (size.width - SfConstants.SCENE_WIDTH * scale) / 2f,
        oy = (size.height - SfConstants.SCENE_HEIGHT * scale) / 2f,
        camX = state.cameraX,
        camY = state.cameraY,
    )
    val stage = images[theme.stageImage] // 🆕 nullable: kenstage.png se quitó (copyright)
    val t = state.gameTimeMs

    // 🆕 (2026-07-18) PARALLAX VERTICAL DE SALTO: en los mapas con zoom (headroom de cielo
    // recortado arriba) el fondo BAJA al brincar → se ve "más arriba" del mapa. Fracción según
    // la altura del peleador MÁS ALTO (apex de salto ≈ 90 px de mundo). En mapas sin zoom no hay
    // headroom → sin efecto (los 11 confirmados quedan igual).
    val highestY = minOf(state.player.y, state.cpu.y)
    val jumpFrac = ((SfConstants.STAGE_FLOOR - highestY) / 90f).coerceIn(0f, 1f)

    if (bg is SfStageBackground.Animated) {
        // ---- FONDO POW ANIMADO (atlas de frames) con parallax de cámara ----
        drawAnimatedBackground(ctx, bg, t, framing, jumpFrac)
    } else if (bg is SfStageBackground.Static) {
        // ---- FONDO POW a pantalla completa (foto fija) con parallax de cámara ----
        drawFullBackground(ctx, bg.image, framing, jumpFrac)
    } else if (stage != null) {
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
    } else {
        // Sin fondo POW ni escenario clásico (kenstage quitado) → relleno oscuro
        drawRect(
            color = Color(0xFF0E0E16),
            topLeft = Offset(ctx.ox, ctx.oy),
            size = Size(SfConstants.SCENE_WIDTH * scale, SfConstants.SCENE_HEIGHT * scale),
        )
    }

    // ---- Sombras ----
    drawShadow(ctx, theme, images.getValue(theme.shadowImage), state.player, bgFile)
    drawShadow(ctx, theme, images.getValue(theme.shadowImage), state.cpu, bgFile)

    // ---- Peleadores (sheet según el personaje del snapshot) ----
    // 🆕 (2026-07-21) PLACEHOLDER ALPHA: si al peleador le falta la hoja del movimiento en
    // curso, se dibuja con el arte del estudiante de su género en SILUETA NEGRA PIXELADA y
    // con el rótulo "ALPHA" encima (mismo lenguaje visual que los personajes bloqueados).
    listOf(
        Triple(state.player, playerData, playerContentH),
        Triple(state.cpu, cpuData, cpuContentH),
    ).forEachIndexed { side, (fighter, data, contentH) ->
        val alpha = alphaFallback?.takeIf { data.animations[fighter.state.jsKey].isNullOrEmpty() }
        if (alpha != null && alpha.bitmap != null) {
            drawFighter(
                ctx, images + (alpha.sheetKey to alpha.bitmap), alpha.data, fighter, t,
                showHitboxes, emptyMap(), silhouette = true, sheetKeyOverride = alpha.sheetKey,
                // ⚠️ El atlas ALPHA lleva SU propio submuestreo (no el global).
                sheetScale = alpha.sheetScale,
            )
            val hud = images.getValue(theme.hudImage)
            val w = ALPHA_TAG.length * 12f * 0.7f
            drawFontText(
                ctx, theme, hud, ALPHA_TAG,
                fighter.x - ctx.camX - w / 2f, fighter.y - ctx.camY - 118f, 0.7f,
            )
        } else {
            drawFighter(
                ctx, images, data, fighter, t, showHitboxes, contentH,
                silhouette = side == 0 && playerSilhouette,
                sheetScale = sheetScale,
            )
        }
    }

    // ---- 🆕 (2026-07-22) MAREO: estrellitas PROCEDURALES orbitando la cabeza ----
    // No hay sprite de estrellas: se dibujan en Canvas (círculos dorados + destello blanco)
    // sobre la pose stun-3. Órbita elíptica ~1.6 s/vuelta, 3 estrellas desfasadas 120°.
    listOf(state.player, state.cpu).forEach { f ->
        if (f.state != SfFighterState.STUN) return@forEach
        val headY = f.y - 104f
        for (i in 0 until 3) {
            val ang = t / 260f + i * 2.0944f // 2π/3 de desfase entre estrellas
            val sx = f.x + kotlin.math.cos(ang) * 16f
            val sy = headY + kotlin.math.sin(ang) * 5f
            val cxPx = ctx.ox + (sx - ctx.camX) * ctx.scale
            val cyPx = ctx.oy + (sy - ctx.camY) * ctx.scale
            drawCircle(color = Color(0xFFFFD700), radius = 2.4f * ctx.scale, center = Offset(cxPx, cyPx))
            drawCircle(color = Color(0xFFFFFDE7), radius = 1f * ctx.scale, center = Offset(cxPx, cyPx))
        }
    }

    // ---- Proyectiles especiales ----
    // Si el DUEÑO del proyectil trae sus propios frames "proj-*" en su JSON (Prankedy:
    // tanque de gas + estallido de confeti), se usan ESOS desde su sheet; si no, el
    // fireball del tema (hoy, el hadouken del clon).
    state.fireballs.forEach { fb ->
        val owner = if (fb.ownerIndex == 0) state.player else state.cpu
        val ownerData = if (fb.ownerIndex == 0) playerData else cpuData
        val ownerSheet = images[owner.id.spriteAsset.substringAfterLast('/')]
        // 🆕 (2026-07-21) PODERES DE PROYECTIL con efecto PROPIO: si el fireball viene de un
        // bonus power cuya hoja trae sus 3 cuadros de efecto (bonus-N-2 vuelo, -3 vuelo/impacto,
        // -4 disipación), se dibujan ESOS. Si no existen, cae a los proj-* compartidos.
        val bonusFly = "bonus-${fb.bonusPower}-2"
        val useBonusFx = fb.bonusPower > 0 && ownerData.frames.containsKey(bonusFly)
        if (ownerSheet != null && (useBonusFx || ownerData.frames.containsKey("proj-fly-1"))) {
            val key = if (useBonusFx) {
                if (fb.state == SfFireballState.ACTIVE) {
                    if (fb.animationFrame % 2 == 0) bonusFly else "bonus-${fb.bonusPower}-3"
                } else {
                    "bonus-${fb.bonusPower}-${(fb.animationFrame + 3).coerceIn(3, 4)}"
                }
            } else if (fb.state == SfFireballState.ACTIVE) {
                if (fb.animationFrame % 2 == 0) "proj-fly-1" else "proj-fly-2"
            } else {
                "proj-hit-${(fb.animationFrame + 1).coerceIn(1, 3)}"
            }
            ownerData.frames[key]?.let { fd ->
                val effectScale = ownerData.projectileEvents[fb.strength]?.visualScale ?: 1f
                drawSpriteAnchored(
                    ctx, ownerSheet, fd.src, fd.origin, fb.x, fb.y, fb.direction,
                    spriteScale = effectScale, sheetScale = sheetScale,
                )
            }
        }
        // (Antes había un fallback al hadouken de Ken.png; ELIMINADO por copyright — todos los
        // peleadores tienen sus propios frames proj-*. Si alguno no los trae, no se dibuja proyectil.)
    }

    // ---- Splashes de impacto ----
    state.splashes.forEach { sp ->
        val rows = theme.splashFrames.getValue(sp.strength)
        val frame = rows[sp.playerId.coerceIn(0, 1)][sp.animationFrame.coerceIn(0, 3)]
        drawSprite(ctx, images.getValue(theme.splashImage), frame.src, sp.x - ctx.camX - frame.origin[0], sp.y - ctx.camY - frame.origin[1])
    }

    // ---- Primer plano (solo con el escenario clásico) ----
    if (bg == null && stage != null) {
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

    // ---- 🆕 (2026-07-21) MEDIDOR DE SÚPER (3rd Strike): barra bajo cada nombre ----
    // Solo se pinta si el peleador tiene el moveset nuevo (si no, nunca carga y estorbaría).
    listOf(0 to state.player, 1 to state.cpu).forEach { (side, fighter) ->
        val data = if (side == 0) playerData else cpuData
        if (data.animations["superArt"].isNullOrEmpty()) return@forEach
        val frac = (fighter.superMeter / SfConstants.SUPER_METER_MAX.toFloat()).coerceIn(0f, 1f)
        val w = 96f
        val x = if (side == 0) 32f else SfConstants.SCENE_WIDTH - 32f - w
        val y = 44f
        val full = frac >= 1f
        // 🆕 (2026-07-22) BRILLO al llenarse: halo dorado + pulso del relleno (~8 Hz).
        if (full) {
            drawRect(
                color = Color(0x66FFD700),
                topLeft = Offset(ctx.ox + (x - 2f) * ctx.scale, ctx.oy + (y - 2f) * ctx.scale),
                size = Size((w + 4f) * ctx.scale, 9f * ctx.scale),
            )
        }
        drawRect( // marco
            color = Color(0xFF202020),
            topLeft = Offset(ctx.ox + x * ctx.scale, ctx.oy + y * ctx.scale),
            size = Size(w * ctx.scale, 5f * ctx.scale),
        )
        drawRect( // relleno (dorado PULSANTE al llenarse = súper lista)
            color = when {
                !full -> Color(0xFF3AA6FF)
                (t / 120) % 2 == 0L -> Color(0xFFFFD700)
                else -> Color(0xFFFFF59D)
            },
            topLeft = Offset(ctx.ox + (x + 1f) * ctx.scale, ctx.oy + (y + 1f) * ctx.scale),
            size = Size((w - 2f) * frac * ctx.scale, 3f * ctx.scale),
        )
        // 🆕 (2026-07-22) BARRA DE MAREO: debajo de la de súper, solo si hay mareo activo
        // (naranja→roja al acercarse al stun). El estado STUN la pinta llena y roja.
        val dFrac = (fighter.dizzyMeter / SfConstants.DIZZY_METER_MAX.toFloat()).coerceIn(0f, 1f)
        val stunned = fighter.state == SfFighterState.STUN
        if (dFrac > 0f || stunned) {
            val dy = y + 7f
            drawRect(
                color = Color(0xFF202020),
                topLeft = Offset(ctx.ox + x * ctx.scale, ctx.oy + dy * ctx.scale),
                size = Size(w * ctx.scale, 4f * ctx.scale),
            )
            drawRect(
                color = if (stunned || dFrac >= 0.8f) Color(0xFFE53935) else Color(0xFFFF9800),
                topLeft = Offset(ctx.ox + (x + 1f) * ctx.scale, ctx.oy + (dy + 1f) * ctx.scale),
                size = Size((w - 2f) * (if (stunned) 1f else dFrac) * ctx.scale, 2f * ctx.scale),
            )
        }
    }

    // ---- 🆕 (2026-07-20) Contador de COMBO (3rd Strike): "N GOLPES" del lado del atacante ----
    // El VM llena/expira comboCount (>=2 = mostrar); aquí SOLO se pinta con sombra.
    if (state.comboCount >= 2 && state.comboPlayerId in 0..1) {
        val hud = images.getValue(theme.hudImage)
        val comboText = "${state.comboCount} $comboHitsLabel"
        val sizeMul = 1.2f
        val tw = comboText.length * 12f * sizeMul
        val x = if (state.comboPlayerId == 0) 16f else SfConstants.SCENE_WIDTH - tw - 16f
        val y = 64f // debajo de las barras de vida, sin tapar a los peleadores
        drawFontText(ctx, theme, hud, comboText, x + 1f, y + 1f, sizeMul) // sombra
        drawFontText(ctx, theme, hud, comboText, x, y, sizeMul)
    }

    // ---- 🆕 Banner de RONDA ("RONDA N" + "PELEA"), fuente arcade, input congelado ----
    if (state.showRoundIntro) {
        val hud = images.getValue(theme.hudImage)
        val rw = roundBannerText.length * 12f * 2f
        drawFontText(ctx, theme, hud, roundBannerText, (SfConstants.SCENE_WIDTH - rw) / 2f, 76f, 2f)
        val fw = fightBannerText.length * 12f * 1.2f
        drawFontText(ctx, theme, hud, fightBannerText, (SfConstants.SCENE_WIDTH - fw) / 2f, 104f, 1.2f)
    }

    // ---- 🆕 Subtítulo del special (frase del personaje, fuente arcade POW, pequeño) ----
    val sub = state.specialSubtitleHud
    if (!sub.isNullOrBlank() &&
        state.specialSubtitleUntilMs > 0L &&
        state.gameTimeMs < state.specialSubtitleUntilMs
    ) {
        val hud = images.getValue(theme.hudImage)
        val sizeMul = 0.6f
        val maxChars = 24
        val maxLines = 3
        // 🆕 (2026-07-22) UN tramo '|' A LA VEZ, sincronizado con la voz: la ventana
        // [start,until] se reparte por igual entre los tramos y se pinta el tramo ACTUAL
        // (envuelto a ~24 chars si es largo). Ya NO se muestran todos a la vez.
        val segments = sub.split('|').filter { it.isNotBlank() }
        if (segments.isNotEmpty()) {
            val start = state.specialSubtitleStartMs
            val total = (state.specialSubtitleUntilMs - start).coerceAtLeast(1L)
            val elapsed = (state.gameTimeMs - start).coerceIn(0L, total - 1L)
            val segIndex = (elapsed * segments.size / total).toInt().coerceIn(0, segments.lastIndex)
            val words = segments[segIndex].split(' ').filter { it.isNotBlank() }
            val lines = ArrayList<String>(maxLines)
            val cur = StringBuilder()
            for (w in words) {
                when {
                    cur.isEmpty() -> cur.append(w)
                    cur.length + 1 + w.length <= maxChars -> cur.append(' ').append(w)
                    else -> {
                        lines.add(cur.toString()); cur.setLength(0); cur.append(w)
                        if (lines.size >= maxLines) break
                    }
                }
            }
            if (cur.isNotEmpty() && lines.size < maxLines) lines.add(cur.toString())
            val lineH = 12f * sizeMul + 3f
            val bottomBaseline = SfConstants.SCENE_HEIGHT - 10f
            lines.reversed().forEachIndexed { i, ln ->
                val w = ln.length * 12f * sizeMul
                val x = (SfConstants.SCENE_WIDTH - w) / 2f
                val y = bottomBaseline - i * lineH - 12f * sizeMul
                drawFontText(ctx, theme, hud, ln, x + 1f, y + 1f, sizeMul) // sombra
                drawFontText(ctx, theme, hud, ln, x, y, sizeMul)
            }
        }
    }
}

/**
 * Fondo POW a pantalla completa: se escala para cubrir el ALTO de la escena (224) y el
 * ancho sobrante panea con la cámara (parallax 1:1 con el avance por el stage).
 */
/**
 * Fondo de escenario POW: estático (una foto) o ANIMADO (atlas de frames tipo "filmstrip"
 * generado por tools/build_map_backgrounds.py). El atlas es UN solo bitmap; se anima
 * pintando un sub-rect distinto por frame (col = i % cols, row = i / cols). minSdk=24 → NO
 * usamos WebP animado (AnimatedImageDrawable es API 28+); el atlas funciona en todas.
 */
private sealed interface SfStageBackground {
    data class Static(val image: ImageBitmap) : SfStageBackground
    data class Animated(
        val atlas: ImageBitmap,
        val frameW: Int,
        val frameH: Int,
        val cols: Int,
        val rows: Int,
        val frameCount: Int,
        val fps: Float,
    ) : SfStageBackground
}

/**
 * 🆕 (2026-07-18) Encuadre por ESCENARIO: por defecto el fondo se escala para que su ALTO
 * completo entre en la escena (224). En algunos mapas eso deja a los peleadores "flotando"
 * (mucho cielo/edificio arriba y el piso muy abajo) y se ve cuadrado. [zoom] > 1 amplía el
 * fondo ANCLÁNDOLO AL PISO (recorta el cielo por arriba) → se ve panorámico y los peleadores
 * quedan sobre el suelo. [offsetY] (unidades de escena, + = baja la imagen) afina el anclaje.
 * NO regenera el asset: solo cambia cómo se dibuja.
 */
private data class SfBgFraming(val zoom: Float = 1f, val offsetY: Float = 0f)

/**
 * Ajustes por SUBSTRING del archivo de fondo (cubre las 3 luces: día/noche_1/noche_2, que
 * comparten la base `fondo_<slug>_...`). Sin entrada = sin ajuste (zoom 1, sin offset).
 */
private val SF_BG_FRAMING: List<Pair<String, SfBgFraming>> = listOf(
    // 🆕 (2026-07-18ñ) Técnica panorámica (zoom anclado al piso + parallax de salto) en los 16
    // mapas (día/noche_1/noche_2 por substring). Peleadores SIEMPRE sobre el suelo y al saltar se
    // ve más arriba. Valores tuneables por mapa en dispositivo (subir/bajar zoom u offsetY).
    "facultad_medicina" to SfBgFraming(zoom = 1.35f),
    "fes_aragon" to SfBgFraming(zoom = 1.30f),
    "piramidesol" to SfBgFraming(zoom = 1.30f),
    "uam_cuajimalpa" to SfBgFraming(zoom = 1.30f),
    "zocalo" to SfBgFraming(zoom = 1.30f),
    // Los 11 que estaban SIN zoom (antes "aprobados"): ahora también panorámicos por pedido del dueño.
    "escom" to SfBgFraming(zoom = 1.30f),
    "queso_ipn" to SfBgFraming(zoom = 1.30f),
    "esime_azc" to SfBgFraming(zoom = 1.30f),
    "cecyt_9" to SfBgFraming(zoom = 1.30f),
    "cecyt_2" to SfBgFraming(zoom = 1.30f),
    "unam_biblioteca_cu" to SfBgFraming(zoom = 1.30f),
    "fes_acatlan" to SfBgFraming(zoom = 1.30f),
    "uam_azcapo" to SfBgFraming(zoom = 1.30f),
    "islamunecas" to SfBgFraming(zoom = 1.30f),
    "mictlan" to SfBgFraming(zoom = 1.30f),
    "campos_agave_jalisco" to SfBgFraming(zoom = 1.30f),
)

private fun framingForBg(file: String?): SfBgFraming =
    file?.let { f -> SF_BG_FRAMING.firstOrNull { f.contains(it.first) }?.second } ?: SfBgFraming()

/**
 * Decodifica el fondo desde assets. Si el archivo termina en "_anim.webp" y existe su JSON
 * hermano, devuelve un fondo ANIMADO; si no, uno estático.
 * RGB_565 (sin alpha). En [lowEnd] usa inSampleSize=2 (~¼ de RAM de textura) y deriva
 * frameW/H del atlas real (no del JSON a full-res).
 */
private fun loadStageBackground(
    context: Context,
    imagesDir: String,
    file: String,
    lowEnd: Boolean = false,
): SfStageBackground? {
    return runCatching {
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            // Gama baja: 1/4 de lado (~1/16 texels, 1920→480). Sigue jugable y mucho menos lag.
            // Media: 1/2. Alta: full.
            inSampleSize = when {
                lowEnd -> 4
                else -> 1
            }
        }
        val bmp = context.assets.open(imagesDir + file).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val jsonName = file.substringBeforeLast('.') + ".json"
        val meta = runCatching {
            context.assets.open(imagesDir + jsonName).use { it.readBytes().decodeToString() }
        }.getOrNull()
        if (file.endsWith("_anim.webp") && meta != null) {
            val o = org.json.JSONObject(meta)
            val cols = o.getInt("cols").coerceAtLeast(1)
            val rows = o.getInt("rows").coerceAtLeast(1)
            // Tras sample, el tamaño de celda real = atlas/grid (más fiable que frameW del JSON)
            val cellW = (bmp.width / cols).coerceAtLeast(1)
            val cellH = (bmp.height / rows).coerceAtLeast(1)
            // Gama baja: bajar fps de anim del fondo (menos “trabajo” visual; sigue vivo)
            val fps = o.getDouble("fps").toFloat().let { if (lowEnd) (it * 0.66f).coerceAtLeast(6f) else it }
            SfStageBackground.Animated(
                atlas = bmp.asImageBitmap(),
                frameW = cellW,
                frameH = cellH,
                cols = cols,
                rows = rows,
                frameCount = o.getInt("frameCount").coerceAtLeast(1),
                fps = fps,
            )
        } else {
            SfStageBackground.Static(bmp.asImageBitmap())
        }
    }.getOrNull()
}

/**
 * Overlay CARGANDO con la fuente arcade POW (sf_hud_pow.png).
 * Se muestra al decodificar atlas/hojas en gama baja (entrada a pelea puede tardar).
 */
@Composable
private fun SfLoadingOverlay(theme: SfTheme) {
    val context = LocalContext.current
    val hud = remember(theme) {
        runCatching {
            context.assets.open(theme.imagesDir + theme.hudImage).use {
                BitmapFactory.decodeStream(it)
            }?.asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF0101018))
            .clickable(enabled = true, onClick = {}), // bloquea toques
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (hud != null) {
                // Dibuja "CARGANDO" con la fuente del HUD (glifos A-Z)
                Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    val scale = minOf(size.width / SfConstants.SCENE_WIDTH, size.height / 40f)
                    val ctx = SceneCtx(scale, (size.width - SfConstants.SCENE_WIDTH * scale) / 2f, 0f, 0f, 0f)
                    val text = "CARGANDO"
                    val tw = text.length * 12f * 2.2f
                    drawFontText(ctx, theme, hud, text, (SfConstants.SCENE_WIDTH - tw / scale) / 2f, 8f, 2.2f)
                }
            } else {
                Text(
                    text = stringResource(R.string.sf_loading),
                    color = Color(0xFFD4AF37),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sf_loading_sub),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Fondo animado: elige el frame según el tiempo de juego (el ping-pong ya viene embebido en
 * el atlas, así que basta un loop simple 0→N-1) y lo pinta con el mismo parallax que el fijo.
 */
private fun DrawScope.drawAnimatedBackground(
    ctx: SceneCtx,
    anim: SfStageBackground.Animated,
    timeMs: Long,
    framing: SfBgFraming = SfBgFraming(),
    jumpFrac: Float = 0f,
) {
    val frameMs = (1000f / anim.fps).coerceAtLeast(1f)
    val idx = ((timeMs / frameMs).toLong() % anim.frameCount).toInt().coerceIn(0, anim.frameCount - 1)
    val col = idx % anim.cols
    val row = idx / anim.cols
    // 🆕 zoom por escenario: >1 amplía y ANCLA AL PISO (scaledH = SCENE_HEIGHT * zoom).
    val s = (SfConstants.SCENE_HEIGHT / anim.frameH.toFloat()) * framing.zoom
    val scaledW = anim.frameW * s
    val scaledH = anim.frameH * s
    val camSpan = SfConstants.STAGE_WIDTH - SfConstants.SCENE_WIDTH
    val progress = ((ctx.camX - SfConstants.STAGE_PADDING) / camSpan).coerceIn(0f, 1f)
    val offsetX = (scaledW - SfConstants.SCENE_WIDTH).coerceAtLeast(0f) * progress
    // Base de la imagen al fondo de la escena (recorta cielo arriba); offsetY afina.
    // 🆕 Salto: baja la imagen hasta 'headroom' (cielo recortado) → revela lo de arriba.
    val headroom = (scaledH - SfConstants.SCENE_HEIGHT).coerceAtLeast(0f) * ctx.scale
    val dstY = ctx.oy + (SfConstants.SCENE_HEIGHT - scaledH + framing.offsetY) * ctx.scale +
        jumpFrac * headroom
    drawImage(
        image = anim.atlas,
        srcOffset = IntOffset(col * anim.frameW, row * anim.frameH),
        srcSize = IntSize(anim.frameW, anim.frameH),
        dstOffset = IntOffset((ctx.ox - offsetX * ctx.scale).toInt(), dstY.toInt()),
        dstSize = IntSize((scaledW * ctx.scale).toInt(), (scaledH * ctx.scale).toInt()),
        filterQuality = FilterQuality.Low,
    )
}

private fun DrawScope.drawFullBackground(
    ctx: SceneCtx,
    bg: ImageBitmap,
    framing: SfBgFraming = SfBgFraming(),
    jumpFrac: Float = 0f,
) {
    val s = (SfConstants.SCENE_HEIGHT / bg.height.toFloat()) * framing.zoom
    val scaledW = bg.width * s
    val scaledH = bg.height * s
    val camSpan = SfConstants.STAGE_WIDTH - SfConstants.SCENE_WIDTH
    val progress = ((ctx.camX - SfConstants.STAGE_PADDING) / camSpan).coerceIn(0f, 1f)
    val offsetX = (scaledW - SfConstants.SCENE_WIDTH).coerceAtLeast(0f) * progress
    val headroom = (scaledH - SfConstants.SCENE_HEIGHT).coerceAtLeast(0f) * ctx.scale
    val dstY = ctx.oy + (SfConstants.SCENE_HEIGHT - scaledH + framing.offsetY) * ctx.scale +
        jumpFrac * headroom
    drawImage(
        image = bg,
        srcOffset = IntOffset(0, 0),
        srcSize = IntSize(bg.width, bg.height),
        dstOffset = IntOffset((ctx.ox - offsetX * ctx.scale).toInt(), dstY.toInt()),
        dstSize = IntSize((scaledW * ctx.scale).toInt(), (scaledH * ctx.scale).toInt()),
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
    silhouette: Boolean = false,
    // 🆕 (2026-07-21) 1f = atlas a resolución completa; 0.5f = atlas submuestreado en gama
    // baja. SOLO afecta al RECORTE (las coordenadas del JSON son de la hoja original); el
    // tamaño de DESTINO no cambia, así que el sprite se ve igual de grande, solo más suave.
    sheetScale: Float = 1f,
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
            srcOffset = IntOffset((src[0] * sheetScale).toInt(), (src[1] * sheetScale).toInt()),
            srcSize = IntSize(
                (src[2] * sheetScale).toInt().coerceAtLeast(1),
                (src[3] * sheetScale).toInt().coerceAtLeast(1),
            ),
            dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
            dstSize = IntSize((src[2] * s).toInt(), (src[3] * s).toInt()),
            filterQuality = FilterQuality.None,
            colorFilter = if (silhouette) ColorFilter.tint(Color(0xFF15151F)) else null,
        )
    }
    if (direction == SfDirection.LEFT) {
        // Espejo alrededor del ancla (equivale al context.scale(direction,1) del JS)
        scale(scaleX = -1f, scaleY = 1f, pivot = Offset(anchorSx, 0f)) { draw() }
    } else {
        draw()
    }
}

/**
 * Altura de cuerpo OBJETIVO en px de la hoja (contenido opaco). El packer croma fija
 * poses erguidas a ~100 px; si un ataque/especial pinta más grande en la celda 256²
 * (p. ej. auras, brazos), se reescala en draw para NO "crecer" al golpear.
 * El pushbox NO bastaba: muchos JSON repiten push 78 en idle y special con contenido 100→168.
 */
private const val TARGET_BODY_CONTENT_H = 100f

/** Estados donde el cuerpo DEBE verse más bajo/tumbado: no forzar a 100 px. */
private fun SfFighterState.keepsNaturalHeight(): Boolean = when (this) {
    SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_UP,
    SfFighterState.CROUCH_TURN,
    SfFighterState.KO,
    -> true
    else -> name.startsWith("HURT_") // hurt puede aplastar; hurtScale aparte
}

/**
 * Mide, por frameKey, la altura de píxeles opacos en el recorte src de la hoja.
 * Se calcula UNA vez al cargar el personaje (no por tick).
 */
private fun measureFrameContentHeights(
    sheet: ImageBitmap,
    frames: Map<String, SfFrameDef>,
): Map<String, Int> {
    val bmp = sheet.asAndroidBitmap()
    val out = HashMap<String, Int>(frames.size)
    val wBmp = bmp.width
    val hBmp = bmp.height
    for ((key, fr) in frames) {
        if (key.startsWith("proj")) continue
        val src = fr.src
        if (src.size < 4) continue
        val x0 = src[0].coerceIn(0, wBmp - 1)
        val y0 = src[1].coerceIn(0, hBmp - 1)
        val x1 = (src[0] + src[2]).coerceIn(x0 + 1, wBmp)
        val y1 = (src[1] + src[3]).coerceIn(y0 + 1, hBmp)
        var minY = y1
        var maxY = y0 - 1
        // Muestreo cada 2 px (suficiente y más barato en gama baja)
        var y = y0
        while (y < y1) {
            var x = x0
            var rowHit = false
            while (x < x1) {
                if ((bmp.getPixel(x, y) ushr 24) > 16) {
                    rowHit = true
                    break
                }
                x += 2
            }
            if (rowHit) {
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            y += 2
        }
        out[key] = if (maxY >= minY) (maxY - minY + 1) else src[3]
    }
    return out
}

private fun DrawScope.drawFighter(
    ctx: SceneCtx,
    images: Map<String, ImageBitmap>,
    data: SfFighterData,
    f: SfFighter,
    t: Long,
    showHitboxes: Boolean = false,
    contentHeights: Map<String, Int> = emptyMap(),
    silhouette: Boolean = false,
    // 🆕 (2026-07-21) Placeholder ALPHA: dibuja con la hoja de OTRO peleador (el estudiante
    // del mismo género) sin cambiar la identidad lógica del que pelea.
    sheetKeyOverride: String? = null,
    // 🆕 (2026-07-21) Submuestreo del atlas en gama baja (1f = completo, 0.5f = mitad).
    sheetScale: Float = 1f,
) {
    // 🆕 Nunca “desaparecer”: si falta hoja/anim/frame, cae a IDLE-1 o al primer frame disponible.
    val sheetKey = sheetKeyOverride ?: f.id.spriteAsset.substringAfterLast('/')
    val sheet = images[sheetKey]
        ?: images.values.firstOrNull()
        ?: return
    val anim = data.animations[f.state.jsKey]
        ?: data.animations[SfFighterState.IDLE.jsKey]
        ?: data.animations.values.firstOrNull()
        ?: return
    val frameKey = anim[f.animationFrame.coerceIn(0, anim.lastIndex)].frameKey
    val frame = data.frames[frameKey]
        ?: data.frames["idle-1"]
        ?: data.frames.values.firstOrNull()
        ?: return
    // Sacudida al recibir golpe (hurt shake del JS), solo durante el 1er frame de HURT
    val hurtState = f.state.name.startsWith("HURT_")
    val shake = if (hurtState && f.animationFrame == 0) (if ((t / 32) % 2 == 0L) 2f else -2f) else 0f
    val hurtMul = if (hurtState) f.id.hurtScale else 1f
    // 🆕 Poderes/metamorfosis: NO reescalar por contenido (auras grandes → “cambio de skin”
    // o recortes raros). Solo idle/walk/golpes normales se normalizan a ~100 px.
    val isFxPose = f.state in SF_BONUS_POWER_STATES ||
        f.state == SfFighterState.SPECIAL_1_LIGHT ||
        f.state == SfFighterState.SPECIAL_1_MEDIUM ||
        f.state == SfFighterState.SPECIAL_1_HEAVY ||
        f.metamorphosing
    val contentH = contentHeights[frameKey]?.takeIf { it > 0 } ?: frame.src.getOrElse(3) { 100 }
    val bodyMul = when {
        isFxPose || f.state.keepsNaturalHeight() || contentH <= 0 -> 1f
        else -> (TARGET_BODY_CONTENT_H / contentH.toFloat()).coerceIn(0.70f, 1.25f)
    }
    val spriteScale = (hurtMul * bodyMul).coerceIn(0.70f, 1.35f)
    val drawDirection = if (frame.flipX) f.direction.opposite() else f.direction
    // Origen seguro (pies): si el JSON trae basura, anclar al centro-bajo de la celda
    val origin = if (frame.origin.size >= 2 && frame.origin[1] > 0) {
        frame.origin
    } else {
        listOf(frame.src.getOrElse(2) { 128 } / 2, frame.src.getOrElse(3) { 256 } * 7 / 8)
    }
    drawSpriteAnchored(
        ctx, sheet, frame.src, origin, f.x, f.y, drawDirection,
        shakeX = shake, spriteScale = spriteScale, silhouette = silhouette,
        sheetScale = sheetScale,
    )

    // 🆕 HITBOXES (Ajustes → "Mostrar hitboxes"): push/hurt/hit. También se reescalan
    // visualmente con bodyMul para alinear cajas al sprite dibujado.
    if (showHitboxes) {
        val boxScale = bodyMul
        fun scaleBox(b: SfBox): SfBox = SfBox(b.x * boxScale, b.y * boxScale, b.width * boxScale, b.height * boxScale)
        SfBox.fromList(frame.push).takeIf { it.width > 0f }
            ?.let { drawWorldBox(ctx, scaleBox(it).toWorld(f.x, f.y, f.direction), Color.White) }
        frame.hurt?.forEach { row ->
            SfBox.fromList(row).takeIf { it.width > 0f }
                ?.let { drawWorldBox(ctx, scaleBox(it).toWorld(f.x, f.y, f.direction), Color(0xFF29B6F6)) }
        }
        frame.hit?.let { hb ->
            SfBox.fromList(hb).takeIf { it.width > 0f }
                ?.let { drawWorldBox(ctx, scaleBox(it).toWorld(f.x, f.y, f.direction), Color(0xFFEF5350)) }
        }
    }
}

/** Dibuja una SfBox (en coords de MUNDO) como rectángulo hueco en pantalla (hitbox de debug). */
private fun DrawScope.drawWorldBox(ctx: SceneCtx, box: SfBox, color: Color) {
    val x = ctx.ox + (box.x - ctx.camX) * ctx.scale
    val y = ctx.oy + (box.y - ctx.camY) * ctx.scale
    drawRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(box.width * ctx.scale, box.height * ctx.scale),
        style = Stroke(width = 2f),
    )
}

private fun DrawScope.drawShadow(ctx: SceneCtx, theme: SfTheme, shadowImg: ImageBitmap, f: SfFighter, bgFile: String?) {
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

    // 🆕 Sombra sobre el agua / plataforma para Isla de las Muñecas
    val isIslaMunecas = bgFile != null && bgFile.contains("islamunecas")
    if (isIslaMunecas) {
        val platW = 100f
        val platH = 8f
        val platX = ctx.ox + (f.x - ctx.camX - platW / 2f) * ctx.scale
        val platY = ctx.oy + (SfConstants.STAGE_FLOOR - ctx.camY - 2f) * ctx.scale
        drawRoundRect(
            color = Color(0xCC3E2723), // Madera oscura semi-transparente
            topLeft = Offset(platX, platY),
            size = Size(platW * ctx.scale, platH * ctx.scale),
            cornerRadius = CornerRadius(3f * ctx.scale, 3f * ctx.scale)
        )
        drawRoundRect(
            color = Color(0xFF1D0F0B), // Borde madera oscuro
            topLeft = Offset(platX, platY),
            size = Size(platW * ctx.scale, platH * ctx.scale),
            cornerRadius = CornerRadius(3f * ctx.scale, 3f * ctx.scale),
            style = Stroke(width = 1f * ctx.scale)
        )
        // Línea intermedia para simular tablones
        drawLine(
            color = Color(0x441D0F0B),
            start = Offset(platX + 4f * ctx.scale, platY + 4f * ctx.scale),
            end = Offset(platX + (platW - 4f) * ctx.scale, platY + 4f * ctx.scale),
            strokeWidth = 1f * ctx.scale
        )
        
        // Sombra más prolongada
        scaleX *= 1.8f
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
