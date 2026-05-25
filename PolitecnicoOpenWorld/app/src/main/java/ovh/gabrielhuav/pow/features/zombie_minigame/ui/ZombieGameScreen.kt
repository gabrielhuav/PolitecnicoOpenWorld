package ovh.gabrielhuav.pow.features.zombie_minigame.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.ZombieGameViewModel
import kotlin.math.max

private const val ZOMBIE_SPRITE_BASE = 60f
private const val PLAYER_SPRITE_BASE = 56f

@Composable
fun ZombieGameScreen(
    onExitToWorld: () -> Unit,
    debugHitboxes: Boolean = false
) {
    val context = LocalContext.current
    val viewModel: ZombieGameViewModel = viewModel(factory = ZombieGameViewModel.Factory(context))
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current

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

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportWpx = with(density) { maxWidth.toPx() }
            val viewportHpx = with(density) { maxHeight.toPx() }

            val cam = remember(state.playerX, state.playerY, viewportWpx, viewportHpx, room.id) {
                computeCamera(state.playerX, state.playerY, room.worldWidth, room.worldHeight, viewportWpx, viewportHpx, room.zoom)
            }

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
            // Se dibuja DESPUÉS del fondo y ANTES de las entidades, dentro de
            // las mismas transformaciones de cámara (translate + scale) para que
            // las auras se muevan junto con el zoom y el desplazamiento del mapa.
            // Solo en edificios (los cuartos "oscuros"); el lobby se deja claro.
            if (room.type == ZoneType.BUILDING) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(cam.offsetX, cam.offsetY) {
                        scale(cam.scale, cam.scale, pivot = Offset.Zero) {

                            // Aura del jugador: amarillo cálido, radio ~2.5x su tamaño base.
                            val playerLightRadius = PLAYER_SPRITE_BASE * 2.5f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x80FFF59D), // amarillo translúcido en el centro
                                        Color(0x33FFEB3B),
                                        Color.Transparent  // se desvanece en el borde
                                    ),
                                    center = Offset(state.playerX, state.playerY),
                                    radius = playerLightRadius
                                ),
                                radius = playerLightRadius,
                                center = Offset(state.playerX, state.playerY)
                            )

                            // Aura verde tóxico anclada a cada zombi vivo.
                            val zombieLightRadius = ZOMBIE_SPRITE_BASE * 2f
                            state.zombies.forEach { z ->
                                if (!z.isDying) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0x6676FF03), // verde tóxico translúcido
                                                Color(0x2664DD17),
                                                Color.Transparent
                                            ),
                                            center = Offset(z.x, z.y),
                                            radius = zombieLightRadius
                                        ),
                                        radius = zombieLightRadius,
                                        center = Offset(z.x, z.y)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            fun toScreenX(wx: Float) = cam.offsetX + wx * cam.scale
            fun toScreenY(wy: Float) = cam.offsetY + wy * cam.scale

            // ─── REQUERIMIENTO 5: LÍNEA PUNTEADA DE SALIDA ──────
            // Se dibuja del jugador a cada puerta EXIT. Visible solo los
            // primeros 2 s tras spawnear (state.showExitGuide).
            if (state.showExitGuide) {
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
                            drawLine(
                                color = color,
                                start = Offset(px, py),
                                end = Offset(ex, ey),
                                strokeWidth = 6f,
                                pathEffect = dash,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            // Indicadores de puertas
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

            // Items en el suelo (SkillItems con icono/fallback)
            state.items.forEach { item ->
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
                key(z.id) {
                    ZombieView(
                        frameIndex = z.frameIndex, facingRight = z.facingRight, isDying = z.isDying,
                        health = z.health, maxHealth = z.maxHealth, sizePx = zSize,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(z.x).toDp() } - with(density) { (zSize / 2).toDp() },
                            y = with(density) { toScreenY(z.y).toDp() } - with(density) { (zSize / 2).toDp() }
                        )
                    )
                }
            }

            // Jugador
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

        if (background == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFD4AF37)) }
        }

        // ─── HUD FIJO ───────────────────────────────────────
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

        // ─── REQUERIMIENTO 1: DIÁLOGO DE CONFIRMACIÓN DE SALIDA ──
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
}

private fun computeCamera(
    playerX: Float, playerY: Float, worldW: Float, worldH: Float,
    viewW: Float, viewH: Float, zoom: Float
): CameraTransform {
    if (viewW <= 0f || viewH <= 0f) return CameraTransform(0f, 0f, 1f)
    // fitScale = max(...) → equivalente matemático de ContentScale.Crop:
    // llena la pantalla recortando lo que sobre, SIN deformar el aspect ratio.
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