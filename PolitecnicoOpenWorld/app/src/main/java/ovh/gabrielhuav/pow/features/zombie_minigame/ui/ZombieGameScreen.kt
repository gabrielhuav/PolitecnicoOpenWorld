package ovh.gabrielhuav.pow.features.zombie_minigame.ui

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
import ovh.gabrielhuav.pow.features.zombie_minigame.ui.components.CollisionMatrixDesignerLayer
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.ZombieGameViewModel
import kotlin.math.max

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
    debugHitboxes: Boolean = false
) {
    val context = LocalContext.current
    val serverUrl = if (isMultiplayer) ovh.gabrielhuav.pow.BuildConfig.ZOMBIE_SERVER_URL else null
    val viewModel: ZombieGameViewModel = viewModel(
        factory = ZombieGameViewModel.Factory(context, serverUrl, playerName)
    )
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current

    // Export/Import del JSON de matrices (igual que el mapa principal con landmarks).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMatricesToUri(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMatricesFromUri(it) } }

    LaunchedEffect(state.isExitingToWorld) {
        if (state.isExitingToWorld) { viewModel.consumeExit(); onExitToWorld() }
    }

    val room = ZombieRoomCatalog.rooms[state.currentRoomIndex]
    var background by remember(room.backgroundAsset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(room.backgroundAsset) {
        background = withContext(Dispatchers.IO) {
            try { context.assets.open(room.backgroundAsset).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
            catch (e: Exception) {
                android.util.Log.e("ZombieGameScreen", "No se pudo cargar fondo ${room.backgroundAsset}: ${e.message}")
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

                // ─── CAPA DE NEBLINA (fog of war) centrada en el jugador ──────────
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

                // Jugador local
                val pSize = PLAYER_SPRITE_BASE * cam.scale
                PlayerView(
                    action = state.playerAction, facingRight = state.isPlayerFacingRight,
                    damagePulse = state.damagePulseTrigger, sizePx = pSize,
                    modifier = Modifier.absoluteOffset(
                        x = with(density) { toScreenX(state.playerX).toDp() } - with(density) { (pSize / 2).toDp() },
                        y = with(density) { toScreenY(state.playerY).toDp() } - with(density) { (pSize / 2).toDp() }
                    )
                )
            }
            // ─── Mano zombi fija en el lobby ────────────────────────────
            if (room.id == ZombieRoomCatalog.LOBBY_ID) {
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
                            context.assets.open("ZOMBIS_MOD/zombi_hand.webp")
                                .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                        } catch (e: Exception) { null }
                    }
                }
                handBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = "Mano Zombi",
                        modifier = Modifier
                            .absoluteOffset(
                                x = with(density) { (handScreenX - handSizePx / 2f).toDp() },
                                y = with(density) { (handScreenY - handSizePx / 2f).toDp() }
                            )
                            .size(handSizeDp)
                    )
                }
            }

            // ─── CAPA DEL MODO DISEÑADOR (rejilla editable) ─────
            CollisionMatrixDesignerLayer(
                enabled = state.designerMode,
                rows = state.designerRows,
                worldWidth = room.worldWidth,
                worldHeight = room.worldHeight,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onPaintWorld = viewModel::paintCellAtWorld,
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
                onDismissWeaponMenu = viewModel::dismissWeaponMenu
            )

            (state.nearbyDoorLabel ?: state.pickupToast ?: state.effectToast)?.let { prompt ->
                Box(Modifier.fillMaxSize().padding(top = 110.dp), Alignment.TopCenter) {
                    Text(prompt.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        modifier = Modifier.background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp))
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
                        Text("Volver al Lobby", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "¿Estás seguro de que quieres volver al lobby? Perderás el progreso de este edificio.",
                            color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.dismissExitToLobby() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("No", color = Color.White, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { viewModel.confirmExitToLobby() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text("Sí", color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ─── VICTORIA ───────────────────────────────────────
            if (state.showVictoryScreen) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Congratulations", color = Color(0xFFD4AF37), fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text("Edificio despejado. Usa las salidas EXIT para continuar.", color = Color.White, fontSize = 16.sp)
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

        // ─── BOTÓN DE MODO DISEÑADOR ────────────────────────────
        // Se declara AL FINAL (después del HUD) para quedar siempre encima y
        // recibir los toques. Siempre visible, no depende de debugHitboxes.
        IconButton(
            onClick = viewModel::toggleDesignerMode,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp)
                .background(
                    if (state.designerMode) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.85f),
                    CircleShape
                )
        ) {
            Icon(Icons.Default.Architecture, "Modo Diseñador", tint = Color.Black)
        }

        // ─── TOOLBAR DEL DISEÑADOR ──────────────────────────────
        if (state.designerMode) {
            DesignerToolbar(
                brushWall = state.designerBrushWall,
                dirty = state.designerDirty,
                roomName = room.displayName,
                onBrush = viewModel::setDesignerBrushWall,
                onSave = viewModel::saveDesignerMatrix,
                onReset = viewModel::resetDesignerMatrix,
                onExport = { exportLauncher.launch("collision_matrices.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onExit = viewModel::toggleDesignerMode,
                modifier = Modifier.align(Alignment.BottomCenter)
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
    brushWall: Boolean,
    dirty: Boolean,
    roomName: String,
    onBrush: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .systemBarsPadding()
            .padding(12.dp)
            .fillMaxWidth(0.96f)
            .background(Color(0xFF1E1E24).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "DISEÑADOR DE COLISIÓN · ${roomName.uppercase()}",
            color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        Text(
            "Toca o arrastra sobre la rejilla. Rojo = pared.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolButton("PARED", brushWall, Color(0xFFD32F2F), Modifier.weight(1f)) { onBrush(true) }
            ToolButton("BORRAR", !brushWall, Color(0xFF4CAF50), Modifier.weight(1f)) { onBrush(false) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (dirty) "GUARDAR*" else "GUARDAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("RESET", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("EXPORTAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("IMPORTAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onExit, modifier = Modifier.height(40.dp)) {
                Text("SALIR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
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