package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuItem
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionsMenu
import ovh.gabrielhuav.pow.features.interiores.core.ui.CollisionMatrixDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.ui.WaypointDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerView          // vista de jugador compartida (core)
import ovh.gabrielhuav.pow.features.interiores.core.ui.RemotePlayerView    // vista de jugador remoto/civil (core)
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieGameViewModel
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.map_exterior.ui.ZombiVideoPlayer
import kotlin.math.max
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import ovh.gabrielhuav.pow.features.map_exterior.ui.SkinSelectorDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

private const val ZOMBIE_SPRITE_BASE = 60f
private const val PLAYER_SPRITE_BASE = 56f

// Colores de las auras de luz. Constantes top-level para no asignar la lista
// en cada drawCircle de cada frame (presión de GC en gama baja).
private val PLAYER_LIGHT_COLORS = listOf(Color(0x80FFF59D), Color(0x33FFEB3B), Color.Transparent)
private val ZOMBIE_LIGHT_COLORS = listOf(Color(0x6676FF03), Color(0x2664DD17), Color.Transparent)
private val PLAYER_LIGHT_RADIUS = PLAYER_SPRITE_BASE * 2.5f
private val ZOMBIE_LIGHT_RADIUS = ZOMBIE_SPRITE_BASE * 2f

@Composable
fun ZombieGameScreen(
    onExitToWorld: () -> Unit,
    isMultiplayer: Boolean,
    playerName: String,
    onNavigateToSettings: () -> Unit = {},
    debugHitboxes: Boolean = false,
    // Sala inicial de Interiores: por defecto el lobby de ESCOM; la puerta FES la fija a FES_ID.
    startRoomId: String = ZombieRoomCatalog.LOBBY_ID,
    // MODO HISTORIA: abre el selector de slots para guardar la partida (también en interiores).
    onRequestSaveGame: () -> Unit = {},
    // MODO HISTORIA: el waypoint final de ENCB_LAB2 pide reanudar la narrativa (cómic ENCB_OUTRO).
    onPlayStoryOutro: () -> Unit = {},
    // MODO HISTORIA: notifica la sala actual (id de ZombieRoomCatalog) al entrar y en cada
    // cambio de sala, para que el guardado sepa en qué interior estaba el jugador.
    onRoomChanged: (String) -> Unit = {},
    // MODO HISTORIA: objetivo a mostrar DENTRO del interior (p. ej. "Busca pistas en la ESCOM"
    // tras la Misión 1). null = no mostrar widget de objetivo. El objetivo del mapa exterior NO
    // se altera (allá sigue "Ingresa a la ESCOM, Cumplido").
    interiorObjective: ovh.gabrielhuav.pow.domain.models.CampaignObjective? = null,
    // INVENTARIO: estado restaurado al CARGAR partida dentro del interior (assetPaths de llaves +
    // progreso de ENCB_lab1) y callback para PERSISTIRLO (lo escribe MainActivity en el VM del mundo).
    initialInventoryKeys: List<String> = emptyList(),
    initialLab1KeyFound: Boolean = false,
    onInteriorProgress: (List<String>, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    // Modo Desarrollador: si está APAGADO se ocultan botones de prueba (Diseñador, y "Salir al mapa"
    // durante la Misión 1). Se lee una vez al entrar a la pantalla.
    val developerMode = remember { ovh.gabrielhuav.pow.data.repository.SettingsRepository(context).getDeveloperMode() }
    val serverUrl = if (isMultiplayer) ovh.gabrielhuav.pow.BuildConfig.INTERIORS_SERVER_URL else null
    val viewModel: ZombieGameViewModel = viewModel(
        factory = ZombieGameViewModel.Factory(context, serverUrl, playerName, startRoomId, initialInventoryKeys, initialLab1KeyFound)
    )
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current

    // Puente de PERSISTENCIA: cada cambio de inventario/progreso del puzzle se empuja al VM del
    // mundo (vía MainActivity) para que el guardado lo capture.
    LaunchedEffect(state.inventoryKeys, state.lab1KeyFound) {
        onInteriorProgress(state.inventoryKeys, state.lab1KeyFound)
    }

    // Export/Import del JSON de matrices (igual que el mapa principal con landmarks).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMatricesToUri(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMatricesFromUri(it) } }

    // Export/Import del JSON de waypoints (puertas).
    val exportWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportWaypointsToUri(it) } }
    val importWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importWaypointsFromUri(it) } }

    LaunchedEffect(state.isExitingToWorld) {
        if (state.isExitingToWorld) { viewModel.consumeExit(); onExitToWorld() }
    }
    // MODO HISTORIA: salida del motor de interiores hacia el cómic ENCB_OUTRO.
    LaunchedEffect(state.isExitingToStoryOutro) {
        if (state.isExitingToStoryOutro) { viewModel.consumeExit(); onPlayStoryOutro() }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.soundManager.stopWalk()
            viewModel.soundManager.stopRun()
        }
    }

    val room = ZombieRoomCatalog.rooms[state.currentRoomIndex]
    // Avisa la sala actual (entrada + cada transición interna) para el guardado de partida.
    LaunchedEffect(state.currentRoomIndex) { onRoomChanged(room.id) }
    val effectiveBgAsset = when {
        room.id == ZombieRoomCatalog.LOBBY_ID && state.zombieModeActivated ->
            "ZOMBIES_MOD/BUILDINGS_Z/building_escom_zombie.webp"
        room.type == ZoneType.BUILDING && !state.zombieModeActivated ->
            "INTERIORS/ESCOM/z_${room.id.removePrefix("za_")}.webp"
        else -> room.backgroundAsset
    }
    var background by remember(effectiveBgAsset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(effectiveBgAsset) {
        background = withContext(Dispatchers.IO) {
            try { context.assets.open(effectiveBgAsset).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
            catch (e: Exception) {
                android.util.Log.e("ZombieGameScreen", "No se pudo cargar fondo $effectiveBgAsset: ${e.message}")
                null
            }
        }
    }

    // ─── FEEDBACK DE DAÑO: screen shake + flash/viñeta roja ──────────────────
    // Screen shake disparado por cada incremento de damagePulseTrigger (recibir daño).
    var shakeX by remember { mutableStateOf(0f) }
    var shakeY by remember { mutableStateOf(0f) }
    // Flash rojo breve al recibir daño.
    var flashAlpha by remember { mutableStateOf(0f) }
    LaunchedEffect(state.damagePulseTrigger) {
        if (state.damagePulseTrigger > 0) {
            flashAlpha = 0.5f
            val steps = 9
            val intensity = 26f
            for (i in 0 until steps) {
                val decay = 1f - i / steps.toFloat()
                shakeX = (Random.nextFloat() * 2f - 1f) * intensity * decay
                shakeY = (Random.nextFloat() * 2f - 1f) * intensity * decay
                flashAlpha = 0.5f * decay
                delay(28)
            }
            shakeX = 0f; shakeY = 0f; flashAlpha = 0f
        }
    }
    // Pulso de vida baja: la viñeta roja late cuando el jugador está crítico.
    val lowHp = state.playerHealth <= 35f && state.playerHealth > 0f
    val lowHpTransition = rememberInfiniteTransition(label = "lowHp")
    val lowHpPulse by lowHpTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "lowHpPulse"
    )
    // La intensidad base de la viñeta escala con la vida perdida.
    val hpLossFactor = (1f - state.playerHealth / 100f).coerceIn(0f, 1f)
    val vignetteAlpha = (hpLossFactor * 0.32f +
            (if (lowHp) lowHpPulse else 0f) +
            flashAlpha).coerceIn(0f, 0.85f)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shakeX.roundToInt(), shakeY.roundToInt()) }
        ) {
            val viewportWpx = with(density) { maxWidth.toPx() }
            val viewportHpx = with(density) { maxHeight.toPx() }

            val cam = remember(state.playerX, state.playerY, viewportWpx, viewportHpx, room.id) {
                computeCamera(state.playerX, state.playerY, room.worldWidth, room.worldHeight, viewportWpx, viewportHpx, room.zoom)
            }

            // Brushes de luz reutilizables: centrados en (0,0) y dibujados con translate,
            // así un único shader sirve para todas las entidades (antes se creaba uno por
            // entidad por frame). 'remember' evita recrearlos en cada recomposición.
            val playerLightBrush = remember {
                Brush.radialGradient(PLAYER_LIGHT_COLORS, center = Offset.Zero, radius = PLAYER_LIGHT_RADIUS)
            }
            val zombieLightBrush = remember {
                Brush.radialGradient(ZOMBIE_LIGHT_COLORS, center = Offset.Zero, radius = ZOMBIE_LIGHT_RADIUS)
            }

            // Límites del mundo visibles (frustum culling). Solo dibujamos/recomponemos
            // entidades dentro de esta ventana + un margen, evitando trabajo fuera de pantalla.
            val cullMargin = ZOMBIE_SPRITE_BASE
            val viewLeft = (-cam.offsetX) / cam.scale - cullMargin
            val viewTop = (-cam.offsetY) / cam.scale - cullMargin
            val viewRight = (viewportWpx - cam.offsetX) / cam.scale + cullMargin
            val viewBottom = (viewportHpx - cam.offsetY) / cam.scale + cullMargin
            fun onScreen(wx: Float, wy: Float) =
                wx >= viewLeft && wx <= viewRight && wy >= viewTop && wy <= viewBottom

            // ─── CAPA DEL MUNDO (fondo) ─────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bg = background ?: return@Canvas
                translate(cam.offsetX, cam.offsetY) {
                    scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                        drawImage(
                            image = bg,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(room.worldWidth.toInt(), room.worldHeight.toInt())
                        )
                        if (debugHitboxes) {
                            room.doors.forEach { d ->
                                val r = d.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                                drawRect(Color(0x5500FF00), Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top))
                            }
                        }
                    }
                }
            }

            // ─── CAPA DE ILUMINACIÓN DINÁMICA (auras) ───────────
            if (room.type == ZoneType.BUILDING && !state.designerMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(cam.offsetX, cam.offsetY) {
                        scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                            // Un solo shader por tipo, posicionado con translate (sin recrear gradientes).
                            translate(state.playerX, state.playerY) {
                                drawCircle(playerLightBrush, PLAYER_LIGHT_RADIUS, Offset.Zero)
                            }
                            state.remotePlayers.forEach { rp ->
                                if (onScreen(rp.x, rp.y)) translate(rp.x, rp.y) {
                                    drawCircle(playerLightBrush, PLAYER_LIGHT_RADIUS, Offset.Zero)
                                }
                            }
                            state.zombies.forEach { z ->
                                if (!z.isDying && onScreen(z.x, z.y)) translate(z.x, z.y) {
                                    drawCircle(zombieLightBrush, ZOMBIE_LIGHT_RADIUS, Offset.Zero)
                                }
                            }
                        }
                    }
                }
            }

            fun toScreenX(wx: Float) = cam.offsetX + wx * cam.scale
            fun toScreenY(wy: Float) = cam.offsetY + wy * cam.scale

            // ─── LÍNEA PUNTEADA DE SALIDA ───────────────────────
            if (state.showExitGuide && !state.designerMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val px = toScreenX(state.playerX)
                    val py = toScreenY(state.playerY)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(24f, 18f), 0f)
                    room.doors.forEach { d ->
                        if (d.kind == DoorKind.EXIT_NEXT || d.kind == DoorKind.EXIT_PREV || d.kind == DoorKind.GENERIC) {
                            val r = d.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                            val ex = toScreenX(r.centerX())
                            val ey = toScreenY(r.centerY())
                            val color = when (d.kind) {
                                DoorKind.EXIT_NEXT, DoorKind.EXIT_PREV -> Color(0xFFFF9800)
                                else -> Color(0xFFD4AF37)
                            }
                            drawLine(color, Offset(px, py), Offset(ex, ey), strokeWidth = 6f, pathEffect = dash, cap = StrokeCap.Round)
                        }
                    }
                }
            }

            // Indicadores de puertas
            if (!state.designerMode) {
                room.doors.forEach { door ->
                    val r = door.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                    DoorIndicator(
                        label = door.label, kind = door.kind,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(r.centerX()).toDp() } - 40.dp,
                            y = with(density) { toScreenY(r.centerY()).toDp() } - 40.dp
                        )
                    )
                }

                // Items en el suelo
                state.items.forEach { item ->
                    if (!onScreen(item.x, item.y)) return@forEach
                    SkillGroundItem(
                        effect = item.effect,
                        highlighted = state.nearbyItemId == item.id,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(item.x).toDp() } - 18.dp,
                            y = with(density) { toScreenY(item.y).toDp() } - 18.dp
                        )
                    )
                }

                // Llaves del puzzle (Modo Historia · ENCB_lab1) en el suelo.
                state.keys.forEach { key ->
                    if (!onScreen(key.x, key.y)) return@forEach
                    KeyGroundItem(
                        assetPath = key.assetPath,
                        highlighted = state.nearbyKeyId == key.id,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(key.x).toDp() } - 22.dp,
                            y = with(density) { toScreenY(key.y).toDp() } - 22.dp
                        )
                    )
                }

                // Proyectiles
                val bulletSize = 10f * cam.scale
                state.projectiles.forEach { p ->
                    if (!onScreen(p.x, p.y)) return@forEach
                    Box(
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(p.x).toDp() } - with(density) { (bulletSize / 2).toDp() },
                            y = with(density) { toScreenY(p.y).toDp() } - with(density) { (bulletSize / 2).toDp() }
                        ).size(with(density) { bulletSize.toDp() })
                            .clip(CircleShape)
                            .background(Color(0xFFFFEB3B))
                            .border(1.dp, Color(0xFFFF6F00), CircleShape)
                    )
                }

                // Zombis
                val zSize = ZOMBIE_SPRITE_BASE * cam.scale
                state.zombies.forEach { z ->
                    if (!onScreen(z.x, z.y)) return@forEach
                    key(z.id) {
                        ZombieView(
                            type = z.type, frameIndex = z.frameIndex, facingRight = z.facingRight,
                            isAttacking = z.isAttacking, isDying = z.isDying,
                            health = z.health, maxHealth = z.maxHealth, sizePx = zSize,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(z.x).toDp() } - with(density) { (zSize / 2).toDp() },
                                y = with(density) { toScreenY(z.y).toDp() } - with(density) { (zSize / 2).toDp() }
                            )
                        )
                    }
                }

                // Jugadores remotos
                val rpSize = PLAYER_SPRITE_BASE * cam.scale
                state.remotePlayers.forEach { rp ->
                    if (!onScreen(rp.x, rp.y)) return@forEach
                    key(rp.id) {
                        RemotePlayerView(
                            name = rp.displayName,
                            action = rp.action,
                            facingRight = rp.facingRight,
                            sizePx = rpSize,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(rp.x).toDp() } - with(density) { (rpSize / 2).toDp() },
                                y = with(density) { toScreenY(rp.y).toDp() } - with(density) { (rpSize / 2).toDp() }
                            )
                        )
                    }
                }

                // NPCs civiles del interior (autoritativos del servidor): figuras humanas que
                // deambulan/huyen de los zombis. Reusan RemotePlayerView (sin nombre).
                state.interiorNpcs.forEach { npc ->
                    if (!onScreen(npc.x, npc.y)) return@forEach
                    key("civ_${npc.id}") {
                        RemotePlayerView(
                            name = "",
                            action = npc.action,
                            facingRight = npc.facingRight,
                            sizePx = rpSize,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(npc.x).toDp() } - with(density) { (rpSize / 2).toDp() },
                                y = with(density) { toScreenY(npc.y).toDp() } - with(density) { (rpSize / 2).toDp() }
                            )
                        )
                    }
                }

                // ─── CAPA DE NEBLINA (fog of war) centrada en el jugador ──────────
                // Se dibuja DENTRO de la capa del mundo (debajo del HUD y los
                // controles) para que SOLO afecte al mapa, nunca a la GUI.
                if (!state.designerMode) {
                    val fogCx = toScreenX(state.playerX)
                    val fogCy = toScreenY(state.playerY)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val reveal = size.minDimension * 0.50f
                        val outer = reveal * 1.9f
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    (reveal / outer) to Color.Transparent,
                                    0.86f to Color(0xC005060A),
                                    1.0f to Color(0xEE05060A)
                                ),
                                center = Offset(fogCx, fogCy),
                                radius = outer
                            )
                        )
                    }
                }

                // Jugador local. `room.playerScaleMul` agranda el sprite solo en salas que
                // lo necesitan (p. ej. ENCB_salon1, donde el fondo lo hacía ver diminuto).
                val pSize = PLAYER_SPRITE_BASE * cam.scale * room.playerScaleMul
                // MUERTE: al morir, el jugador queda como "fantasmita" (semitransparente),
                // igual que la animación de muerte de un NPC.
                val ghostAlpha = if (state.showWastedScreen) 0.3f else 1f
                PlayerView(
                    action = state.playerAction, facingRight = state.isPlayerFacingRight,
                    damagePulse = state.damagePulseTrigger, sizePx = pSize,
                    skin = state.selectedSkin,                         // ← NUEVO
                    modifier = Modifier
                        .absoluteOffset(
                            x = with(density) { toScreenX(state.playerX).toDp() } - with(density) { (pSize / 2).toDp() },
                            y = with(density) { toScreenY(state.playerY).toDp() } - with(density) { (pSize / 2).toDp() }
                        )
                        .alpha(ghostAlpha)
                )
            }
            // ─── Mano zombi fija en el lobby (desaparece tras activar el modo zombie) ──
            // Solo visible en Modo Desarrollador (Interfaz): es la que activa el modo zombi.
            if (developerMode && room.id == ZombieRoomCatalog.LOBBY_ID && !state.zombieModeActivated) {
                val handNx = 0.50f
                val handNy = 0.45f
                val handSizePx = 64f * cam.scale
                val handSizeDp = with(density) { handSizePx.toDp() }
                val handScreenX = cam.offsetX + handNx * room.worldWidth * cam.scale
                val handScreenY = cam.offsetY + handNy * room.worldHeight * cam.scale

                var handBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(Unit) {
                    handBitmap = withContext(Dispatchers.IO) {
                        try {
                            context.assets.open("ZOMBIES_MOD/zombie_hand.webp")
                                .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        } catch (e: Exception) { null }
                    }
                }
                handBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_zombie_hand),
                        modifier = Modifier
                            .absoluteOffset(
                                x = with(density) { (handScreenX - handSizePx / 2f).toDp() },
                                y = with(density) { (handScreenY - handSizePx / 2f).toDp() }
                            )
                            .size(handSizeDp)
                    )
                }
            }

            // ─── CAPA DEL MODO DISEÑADOR: MATRIZ (rejilla editable) ─────
            CollisionMatrixDesignerLayer(
                enabled = state.designerMode && state.designerTarget == DesignerTarget.MATRIX,
                rows = state.designerRows,
                worldWidth = room.worldWidth,
                worldHeight = room.worldHeight,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onPaintWorld = viewModel::paintCellAtWorld,
                modifier = Modifier.matchParentSize()
            )

            // ─── CAPA DEL MODO DISEÑADOR: WAYPOINTS (puertas) ─────
            WaypointDesignerLayer(
                enabled = state.designerMode && state.designerTarget == DesignerTarget.WAYPOINTS,
                doors = state.designerDoors,
                selectedIndex = state.selectedDoorIndex,
                worldWidth = room.worldWidth,
                worldHeight = room.worldHeight,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onSelectWorld = viewModel::selectDoorAtWorld,
                onDragWorld = viewModel::moveSelectedDoorToWorld,
                modifier = Modifier.matchParentSize()
            )
        }

        if (background == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFD4AF37)) }
        }

        // ─── VIÑETA / FLASH ROJO DE DAÑO ────────────────────────────────────
        // Capa no interactiva sobre el mundo: borde rojo radial cuya intensidad
        // escala con la vida perdida, late en vida baja y destella al recibir daño.
        if (vignetteAlpha > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Red.copy(alpha = vignetteAlpha)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = max(size.width, size.height) * 0.72f
                )
                drawRect(brush = brush)
            }
        }

        // IMPORTANTE (orden de capas / z-order en Compose):
        // El HUD de juego es un Box a pantalla completa. Si el botón del
        // diseñador se declarara ANTES del HUD, el HUD quedaría ENCIMA y
        // robaría los toques de la esquina (por eso "no aparecía" el botón).
        // Por eso primero pintamos el HUD y AL FINAL el botón + la toolbar,
        // garantizando que reciban los toques.

        if (!state.designerMode) {
            // ─── HUD DE JUEGO ───────────────────────────────────
            ZombieHud(
                state = state,
                roomName = room.displayName,
                isBuilding = room.type == ZoneType.BUILDING,
                onMoveDir = viewModel::moveDirection,
                onMoveAngle = viewModel::moveByAngle,
                onRun = viewModel::setRunning,
                onInteract = viewModel::onInteract,
                onSpecial = viewModel::setSpecial,
                onSecondaryPressed = viewModel::onSecondaryPressed,
                onSecondaryReleased = viewModel::onSecondaryReleased,
                onSelectMode = viewModel::selectCombatMode,
                onDismissInventory = viewModel::dismissInventory
            )

            // Aviso de llave (cuando el jugador está sobre una). keyMessage (resultado de probar /
            // puerta cerrada) tiene prioridad y es transitorio.
            val keyPrompt = if (state.nearbyKeyId != null)
                "🔑 Hay una llave — pulsa ACCIÓN para inspeccionarla" else null
            (state.keyMessage ?: state.nearbyDoorLabel ?: keyPrompt ?: state.pickupToast ?: state.effectToast)?.let { prompt ->
                Box(Modifier.fillMaxSize().padding(top = 110.dp), Alignment.TopCenter) {
                    Text(prompt.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        modifier = Modifier.background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }

            // ─── OBJETIVO (salas del Modo Historia ENCB) ────────────────────────
            // Banner superpuesto, siempre visible mientras el jugador esté en la cadena
            // lineal de la ENCB (lobby → salón → lab1 → lab2).
            if (room.id in ZombieRoomCatalog.ENCB_STORY_ROOM_IDS) {
                Box(
                    Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp),
                    Alignment.TopCenter
                ) {
                    Text(
                        "Objetivo: Investiga qué pasó",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .alpha(0.85f)   // difuminado para no chocar con los widgets
                            .background(Color(0x99000000), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ─── OBJETIVO DE CAMPAÑA EN INTERIORES (p. ej. ESCOM tras Misión 1) ──
            // Mismo widget que el mapa exterior, anclado arriba-centro. Sin distancia
            // (playerLocation=null) → muestra la descripción del objetivo.
            interiorObjective?.let { obj ->
                Box(
                    Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp),
                    Alignment.TopCenter
                ) {
                    ovh.gabrielhuav.pow.features.map_exterior.ui.components.ObjectivesWidget(
                        objective = obj,
                        done = false,
                        playerLocation = null
                    )
                }
            }

            // ─── DIÁLOGO DE CONFIRMACIÓN DE SALIDA ──────────────
            if (state.showExitToLobbyDialog) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .background(Color(0xFF1E1E24), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Text(stringResource(R.string.zgame_exit_lobby_title), color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            stringResource(R.string.zgame_exit_lobby_text),
                            color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.dismissExitToLobby() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_no), color = Color.White, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { viewModel.confirmExitToLobby() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_yes), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ─── VICTORIA ───────────────────────────────────────
            if (state.showVictoryScreen) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.zgame_victory_title), color = Color(0xFFD4AF37), fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.zgame_victory_text), color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            // ─── WASTED ─────────────────────────────────────────
            if (state.showWastedScreen) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    var wastedScale by remember { mutableFloatStateOf(0.5f) }
                    LaunchedEffect(Unit) {
                        animate(
                            initialValue = 0.5f,
                            targetValue = 1.3f,
                            animationSpec = tween(durationMillis = 3500, easing = LinearOutSlowInEasing)
                        ) { value, _ -> wastedScale = value }
                    }
                    Text(
                        text = "WASTED",
                        color = Color(0xFFD32F2F),
                        fontSize = 60.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 6.sp,
                        modifier = Modifier.scale(wastedScale)
                    )
                }
            }
        }

        // ─── BOTÓN DE CONFIGURACIÓN (siempre) + MENÚ DE OPCIONES ─
        // Arriba a la derecha: el botón de Ajustes SIEMPRE visible, y debajo el
        // menú desplegable con el resto de opciones (que no son controles). Se
        // declara AL FINAL (encima del HUD) para recibir los toques. En modo
        // diseñador la toolbar manda, así que solo dejamos Ajustes.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.background(Color.White.copy(alpha = 0.85f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, stringResource(R.string.zgame_cd_settings), tint = Color.Black)
            }
            // En MODO DISEÑADOR, botón de SALIR SIEMPRE visible: la toolbar inferior puede quedar
            // recortada en pantallas bajas (sobre todo en MATRIZ, que tiene más filas), así que sin
            // esto el usuario se quedaba "atrapado" en el modo diseñador.
            if (state.designerMode) {
                IconButton(
                    onClick = { viewModel.toggleDesignerMode() },
                    modifier = Modifier.background(Color(0xFFD32F2F).copy(alpha = 0.92f), CircleShape)
                ) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.ig_exit), tint = Color.White)
                }
            }
            if (!state.designerMode) {
                // "Elegir personaje" (selector de skin) vive en el menú de Opciones; el juego va
                // SIEMPRE en horizontal (este menú NO cambia la orientación).
                var optionsExpanded by remember { mutableStateOf(false) }
                OptionsMenu(
                    expanded = optionsExpanded,
                    onExpandedChange = { optionsExpanded = it },
                    openGroupId = null,
                    onOpenGroupChange = {},
                    entries = run {
                        // Misión 1 = cadena de salas ENCB del Modo Historia.
                        val inMission1 = room.id in ZombieRoomCatalog.ENCB_STORY_ROOM_IDS
                        val sChar = stringResource(R.string.wm_choose_character)
                        val sDesigner = stringResource(R.string.zgame_opt_designer)
                        val sExitMap = stringResource(R.string.zgame_opt_exit_map)
                        buildList {
                            // "Elegir personaje" (selector de skin), movido aquí desde el botón suelto.
                            add(OptionMenuItem(sChar, Icons.Default.Person, Color(0xFFD91B5B)) { viewModel.toggleSkinSelector(true) })
                            // "Diseñador": solo en Modo Desarrollador.
                            if (developerMode) add(OptionMenuItem(sDesigner, Icons.Default.Architecture) { viewModel.toggleDesignerMode() })
                            // MODO HISTORIA: guardar partida también desde interiores (selector de slots).
                            add(OptionMenuItem("Guardar partida", Icons.Default.Save) { onRequestSaveGame() })
                            // "Salir al mapa": en Misión 1 se oculta salvo en Modo Desarrollador.
                            if (developerMode || !inMission1) add(OptionMenuItem(sExitMap, Icons.Default.ExitToApp) { viewModel.exitToWorld() })
                        }
                    }
                )
            }
        }

        // ─── CINEMÁTICA ZOMBIE (se muestra al interactuar con la mano en el lobby) ──
        if (state.showZombieCinematic) {
            ZombiVideoPlayer(
                context = context,
                onDismiss = { viewModel.onZombieCinematicDismissed() }
            )
        }
        if (state.showSkinSelector) {
            SkinSelectorDialog(
                currentSkin    = state.selectedSkin,
                context        = context,
                onSkinSelected = { viewModel.selectSkin(it) },
                onDismiss      = { viewModel.toggleSkinSelector(false) }
            )
        }
        // ─── TOOLBAR DEL DISEÑADOR ──────────────────────────────
        if (state.designerMode) {
            val isWaypoints = state.designerTarget == DesignerTarget.WAYPOINTS
            val gridRows = state.designerRows.size
            val gridCols = state.designerRows.maxOfOrNull { it.length } ?: 0
            DesignerToolbar(
                target = state.designerTarget,
                brushWall = state.designerBrushWall,
                dirty = state.designerDirty,
                roomName = room.displayName,
                hasSelectedDoor = state.selectedDoorIndex >= 0,
                gridCols = gridCols,
                gridRows = gridRows,
                onResize = viewModel::resizeDesignerMatrixBy,
                onSelectTarget = viewModel::setDesignerTarget,
                onBrush = viewModel::setDesignerBrushWall,
                onSave = { if (isWaypoints) viewModel.saveDesignerWaypoints() else viewModel.saveDesignerMatrix() },
                onReset = { if (isWaypoints) viewModel.resetDesignerWaypoints() else viewModel.resetDesignerMatrix() },
                onExport = {
                    if (isWaypoints) exportWpLauncher.launch("waypoints.json")
                    else exportLauncher.launch("collision_matrices.json")
                },
                onImport = {
                    if (isWaypoints) importWpLauncher.launch(arrayOf("application/json", "*/*"))
                    else importLauncher.launch(arrayOf("application/json", "*/*"))
                },
                onExit = viewModel::toggleDesignerMode,
                // Esquina inferior-IZQUIERDA por defecto (no centrado): así NO tapa el centro del
                // mapa al pintar la matriz. Es arrástrable (asa ⠿) y escalable (−/+).
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

/**
 * Barra de herramientas del Modo Diseñador de la matriz de colisión.
 * Pinta paredes / borra, guarda (persiste en collision_matrices.json y aplica en
 * caliente), resetea, y exporta/importa el JSON por SAF para copiarlo al servidor.
 */
@Composable
private fun DesignerToolbar(
    target: DesignerTarget,
    brushWall: Boolean,
    dirty: Boolean,
    roomName: String,
    hasSelectedDoor: Boolean,
    gridCols: Int,
    gridRows: Int,
    onResize: (Int, Int) -> Unit,
    onSelectTarget: (DesignerTarget) -> Unit,
    onBrush: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWaypoints = target == DesignerTarget.WAYPOINTS
    // El panel del diseñador es intrusivo: se puede MOVER (asa, arrástrala) y CAMBIAR DE TAMAÑO
    // (botones −/+, escala 0.5–1) para que no tape la sala mientras editas.
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    // En pantallas BAJAS (landscape) el panel no cabía y se recortaban "Guardar"/"Exportar":
    // limitamos su alto y lo hacemos DESPLAZABLE (scroll) para que SIEMPRE se alcancen todos.
    val toolbarScroll = rememberScrollState()
    val maxToolbarH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Column(
        modifier = modifier
            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            .systemBarsPadding()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)   // encoge desde abajo-centro
            }
            .padding(12.dp)
            .heightIn(max = maxToolbarH)
            // Más ANGOSTO (antes 0.96 = casi toda la pantalla, tapaba el mapa de lado a lado).
            // Ocupa ~55% del ancho → deja libre la mayor parte del mapa para pintar la matriz.
            .fillMaxWidth(0.55f)
            .background(Color(0xFF1E1E24).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ─── ASA: arrastra para MOVER · toca para recentrar · −/+ cambia el TAMAÑO ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⠿ Mover (toca = recentrar)",
                color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { _, drag ->
                            offX += drag.x * scale
                            offY += drag.y * scale
                        }
                    }
                    .clickable { offX = 0f; offY = 0f }
                    .padding(vertical = 6.dp)
            )
            ToolButton("−", false, Color(0xFF37474F), Modifier.width(48.dp)) { scale = (scale - 0.1f).coerceIn(0.5f, 1f) }
            ToolButton("+", false, Color(0xFF37474F), Modifier.width(48.dp)) { scale = (scale + 0.1f).coerceIn(0.5f, 1f) }
        }
        // CONTENIDO DESPLAZABLE = TODA la herramienta (selector, pincel PARED/BORRAR, tamaño,
        // Guardar/Exportar/Salir). Scrollea junta; solo el asa "⠿ Mover" de arriba queda fija.
        // El panel está acotado a maxToolbarH y es angosto/movible, así que cabe o se scrollea.
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(toolbarScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        Text(
            androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_designer_room, roomName.uppercase()),
            color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        // Selector de objetivo: MATRIZ de colisión o WAYPOINTS (puertas).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolButton("MATRIZ", !isWaypoints, Color(0xFF3A86FF), Modifier.weight(1f)) { onSelectTarget(DesignerTarget.MATRIX) }
            ToolButton("WAYPOINTS", isWaypoints, Color(0xFFD4AF37), Modifier.weight(1f)) { onSelectTarget(DesignerTarget.WAYPOINTS) }
        }
        Text(
            if (isWaypoints)
                (if (hasSelectedDoor) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_drag_door)
                 else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_touch_door))
            else "Toca o arrastra sobre la rejilla. Rojo = pared.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
        )
        // ─── PINCEL + TAMAÑO DE LA MATRIZ (TODO dentro del MISMO scroll) ──────────────
        // PARED (inaccesible) / BORRAR (caminable) y el resize (COL/FIL). Toda la herramienta
        // scrollea JUNTA; solo el asa "⠿ Mover" de arriba queda fija para poder arrastrar siempre.
        if (!isWaypoints) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("PARED", brushWall, Color(0xFFD32F2F), Modifier.weight(1f)) { onBrush(true) }
                ToolButton("BORRAR", !brushWall, Color(0xFF4CAF50), Modifier.weight(1f)) { onBrush(false) }
            }
            Text(
                androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_size_grid, gridCols, gridRows),
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("COL −", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(-1, 0) }
                ToolButton("COL +", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(1, 0) }
                ToolButton("FIL −", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(0, -1) }
                ToolButton("FIL +", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(0, 1) }
            }
        }
        // ─── ACCIONES (Guardar/Reset · Exportar/Importar/Salir), dentro del mismo scroll ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (dirty) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_save_unsaved) else "GUARDAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_reset), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_export), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_import), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onExit, modifier = Modifier.height(40.dp)) {
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_exit), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        } // cierra el Column SCROLLABLE: toda la herramienta scrollea junta (salvo el asa de mover)
    }
}

@Composable
private fun ToolButton(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) color else Color(0xFF2A1C21)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// Llave del puzzle (ENCB_lab1) dibujada en el suelo. Carga el PNG del asset (submuestreado para
// no gastar memoria en gama baja) y, si el jugador está sobre ella, la resalta con un aro dorado.
@Composable
private fun KeyGroundItem(assetPath: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        bmp = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(it, null, o)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (highlighted) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Color(0x66FFD54F))
                    .border(2.dp, Color(0xFFFFD54F), CircleShape)
            )
        }
        val img = bmp
        if (img != null) {
            Image(img, contentDescription = "Llave", modifier = Modifier.size(if (highlighted) 40.dp else 34.dp))
        } else {
            Text("🔑", fontSize = 26.sp)
        }
    }
}

private fun computeCamera(
    playerX: Float, playerY: Float, worldW: Float, worldH: Float,
    viewW: Float, viewH: Float, zoom: Float
): CameraTransform {
    if (viewW <= 0f || viewH <= 0f) return CameraTransform(0f, 0f, 1f)
    val fitScale = max(viewW / worldW, viewH / worldH)
    val scale = fitScale * zoom
    val scaledW = worldW * scale
    val scaledH = worldH * scale
    var offsetX = viewW / 2f - playerX * scale
    var offsetY = viewH / 2f - playerY * scale
    offsetX = if (scaledW <= viewW) (viewW - scaledW) / 2f else offsetX.coerceIn(viewW - scaledW, 0f)
    offsetY = if (scaledH <= viewH) (viewH - scaledH) / 2f else offsetY.coerceIn(viewH - scaledH, 0f)
    return CameraTransform(offsetX, offsetY, scale)
}
