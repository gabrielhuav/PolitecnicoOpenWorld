package ovh.gabrielhuav.pow.features.streetfighter.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
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
// TODO i18n: migrar strings a res/values(-en) (feature dev-gated por ahora).

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
        (theme.imageFiles + listOf(playerId, cpuId).map { it.spriteAsset.substringAfterLast('/') })
            .distinct()
            .associateWith { name ->
                context.assets.open(theme.imagesDir + name).use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            }
    }
    val playerData = remember(playerId) { SfFrameCatalog.load(context, playerId) }
    val cpuData = remember(cpuId) { SfFrameCatalog.load(context, cpuId) }

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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ---- Escena completa (mundo + HUD) en un Canvas ----
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScene(theme, state, images, playerData, cpuData)
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

        // ---- Selector de personaje (antes de pelear) ----
        if (state.inCharacterSelect) {
            CharacterSelectOverlay(onSelect = viewModel::selectCharacter)
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = viewModel::restartBattle) { Text("Revancha") }
                    OutlinedButton(onClick = viewModel::backToCharacterSelect) { Text("Cambiar personaje") }
                    OutlinedButton(onClick = onExitToMap) { Text("Volver al menú") }
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
                        text = "PAUSA",
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = viewModel::togglePause) { Text("Continuar") }
                }
            }
        }

        // Diálogo de salida
        if (state.showExitDialog) {
            AlertDialog(
                onDismissRequest = viewModel::dismissExitDialog,
                title = { Text("Salir de la pelea") },
                text = { Text("¿Abandonar la pelea y volver al menú principal?") },
                confirmButton = { TextButton(onClick = onExitToMap) { Text("Salir") } },
                dismissButton = { TextButton(onClick = viewModel::dismissExitDialog) { Text("Seguir peleando") } },
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
private fun CharacterSelectOverlay(onSelect: (SfFighterId) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xE6101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "ELIGE A TU PELEADOR",
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
                SfFighterId.entries.forEach { id ->
                    CharacterCard(id = id, onSelect = onSelect)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "ALPHA = peleador de POW en desarrollo (poses aproximadas)",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
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

/** Preview del peleador: recorte idle-1 de su sheet, recortado a su bbox opaco. */
@Composable
private fun rememberFighterPreview(id: SfFighterId): ImageBitmap? {
    val context = LocalContext.current
    return remember(id) {
        runCatching {
            val data = SfFrameCatalog.load(context, id)
            val src = data.frames.getValue("idle-1").src
            val decoder = context.assets.open(id.spriteAsset).use { ins ->
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(ins, false)
            } ?: return@runCatching null
            val region = decoder.decodeRegion(Rect(src[0], src[1], src[0] + src[2], src[1] + src[3]), null)
            decoder.recycle()
            region?.let { trimTransparent(it).asImageBitmap() }
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

    // ---- Fondo (parallax por capas) ----
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

    // ---- Primer plano ----
    drawSprite(ctx, stage, theme.ballardLarge, SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING - 147f - ctx.camX / 0.958f, 200f - ctx.camY)
    drawSprite(ctx, stage, theme.ballardLarge, SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING + 147f - ctx.camX / 0.958f, 200f - ctx.camY)

    // ---- HUD ----
    drawHud(ctx, theme, images.getValue(theme.hudImage), state)

    // ---- Texto de ganador (fila por PERSONAJE en el tema; sin fila = no se dibuja) ----
    state.winnerIndex?.let { winner ->
        if (state.battleEnded) {
            val winnerFighter = if (winner == 0) state.player else state.cpu
            theme.winnerRows[winnerFighter.id]?.let { row ->
                drawSpriteScaled(
                    ctx, images.getValue(theme.winnerImage),
                    listOf(0, theme.winnerRowStride * row, theme.winnerSrcWidth, theme.winnerSrcHeight),
                    120f, 60f, 140f, 30f,
                )
            }
        }
    }
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
) {
    val anchorSx = ctx.ox + (worldX - ctx.camX) * ctx.scale
    val anchorSy = ctx.oy + (worldY - ctx.camY) * ctx.scale
    val dstX = anchorSx - origin[0] * ctx.scale + shakeX * ctx.scale
    val dstY = anchorSy - origin[1] * ctx.scale
    val draw: DrawScope.() -> Unit = {
        drawImage(
            image = image,
            srcOffset = IntOffset(src[0], src[1]),
            srcSize = IntSize(src[2], src[3]),
            dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
            dstSize = IntSize((src[2] * ctx.scale).toInt(), (src[3] * ctx.scale).toInt()),
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
    val hurting = f.state.name.startsWith("HURT_") && f.animationFrame == 0
    val shake = if (hurting) (if ((t / 32) % 2 == 0L) 2f else -2f) else 0f
    drawSpriteAnchored(ctx, sheet, frame.src, frame.origin, f.x, f.y, f.direction, shakeX = shake)
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
    val leftDamage = (144f * (maxHp - state.player.hitPoints) / maxHp)
    if (leftDamage > 0f) {
        drawRect(
            color = damageColor,
            topLeft = Offset(ctx.ox + 32f * ctx.scale, ctx.oy + 21f * ctx.scale),
            size = Size(leftDamage * ctx.scale, 9f * ctx.scale),
        )
    }
    val rightDamage = (144f * (maxHp - state.cpu.hitPoints) / maxHp)
    if (rightDamage > 0f) {
        val rx = 208f + (144f * state.cpu.hitPoints / maxHp)
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

    // Nombres (tag por personaje, del tema)
    theme.nameTags[state.player.id]?.let { drawSprite(ctx, hud, it, 32f, 33f) }
    theme.nameTags[state.cpu.id]?.let { drawSprite(ctx, hud, it, 322f, 33f) }

    // Marcadores P1 / P2
    drawScoreLabel(ctx, theme, hud, "P1", 4f)
    drawScoreNumber(ctx, theme, hud, state.playerScore, 45f)
    drawScoreLabel(ctx, theme, hud, "P2", 269f)
    drawScoreNumber(ctx, theme, hud, state.cpuScore, 309f)
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

private fun DrawScope.drawScoreLabel(ctx: SceneCtx, theme: SfTheme, hud: ImageBitmap, label: String, x: Float) {
    label.forEachIndexed { index, ch ->
        val src = if (ch.isDigit()) theme.scoreDigits.digit(ch - '0') else theme.scoreLetterP
        drawSprite(ctx, hud, src, x + 12f * index, 1f)
    }
}

private fun DrawScope.drawScoreNumber(ctx: SceneCtx, theme: SfTheme, hud: ImageBitmap, score: Int, x: Float) {
    val str = score.toString()
    val padding = 6 * 12f - str.length * 12f
    drawScoreLabel(ctx, theme, hud, str, x + padding)
}
