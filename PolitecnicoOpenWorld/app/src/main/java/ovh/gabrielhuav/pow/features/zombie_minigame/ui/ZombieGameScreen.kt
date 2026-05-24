// features/zombie_minigame/ui/ZombieGameScreen.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.interior.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.ZombieGameViewModel

@Composable
fun ZombieGameScreen(
    onExitToWorld: () -> Unit,
    debugHitboxes: Boolean = false
) {
    val context = LocalContext.current
    val viewModel: ZombieGameViewModel = viewModel(
        factory = ZombieGameViewModel.Factory(context)
    )
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    LaunchedEffect(Unit) { viewModel.showInitialHealthBar() }

    LaunchedEffect(state.isExitingToWorld) {
        if (state.isExitingToWorld) {
            viewModel.consumeExit()
            onExitToWorld()
        }
    }

    val room = ZombieRoomCatalog.rooms[state.currentRoomIndex]
    val grid = ZombieRoomCatalog.collisionGrids[state.currentRoomIndex]
    var background by remember(room.backgroundAsset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(room.backgroundAsset) {
        background = withContext(Dispatchers.IO) {
            try {
                context.assets.open(room.backgroundAsset).use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ─── FONDO ─────────────────────────────────────────
        background?.let {
            Image(bitmap = it, contentDescription = room.displayName,
                contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        } ?: Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFD4AF37))
        }

        // ─── ENTIDADES + PUERTAS ───────────────────────────
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxW = maxWidth
            val maxH = maxHeight

            // Puertas: en el lobby las marcamos sutilmente; en debug todas en verde
            room.doors.forEach { door ->
                val r = door.hitbox
                Box(
                    modifier = Modifier
                        .offset(x = maxW * r.left, y = maxH * r.top)
                        .size(maxW * (r.right - r.left), maxH * (r.bottom - r.top))
                        .background(
                            if (debugHitboxes) Color(0x5500FF00)
                            else Color(0x33FFD54F) // dorado translúcido como indicador
                        )
                )
            }

            // Debug: pintar matriz de colisión
            if (debugHitboxes) {
                CollisionDebugOverlay(grid = grid, modifier = Modifier.fillMaxSize())
            }

            // Zombis
            state.zombies.forEach { z ->
                key(z.id) {
                    ZombieSprite(
                        zombie = z,
                        modifier = Modifier
                            .offset(x = maxW * z.x - 28.dp, y = maxH * z.y - 28.dp)
                            .size(56.dp)
                    )
                }
            }

            // Jugador
            val playerSize = 48.dp
            Box(
                modifier = Modifier
                    .offset(x = maxW * state.playerX - playerSize / 2, y = maxH * state.playerY - playerSize / 2)
                    .size(playerSize)
            ) {
                ZombiePlayerSprite(
                    action = state.playerAction,
                    facingRight = state.isPlayerFacingRight,
                    health = state.playerHealth,
                    showHealthBar = state.showPlayerHealthBar
                )
            }
        }

        // ─── HUD ───────────────────────────────────────────
        Row(
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HudChip("ZONA: ${room.displayName.uppercase()}")
            if (room.type == ZoneType.BUILDING) {
                HudChip("ZOMBIS: ${state.zombiesRemaining}/${state.totalZombies}", Color(0xFFD32F2F))
            }
        }

        // ─── PROMPT DE PUERTA ──────────────────────────────
        state.nearbyDoorLabel?.let { label ->
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 80.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = label.uppercase(),
                    color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp,
                    modifier = Modifier
                        .background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // ─── CONTROLES ─────────────────────────────────────
        if (!state.showVictoryScreen) {
            val sidePadding = if (isPortrait) 16.dp else 64.dp
            val bottomPadding = if (isPortrait) 48.dp else 32.dp
            val maxScale = if (isPortrait) 1.0f else 1.4f
            val scale = state.controlsScale.coerceAtMost(maxScale)

            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding)
                    .systemBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val movement = @Composable {
                    if (state.controlType == ControlType.DPAD)
                        DPadController(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                            onDirectionPressed = { viewModel.moveDirection(it) })
                    else
                        JoystickController(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                            onMove = { viewModel.moveByAngle(it) })
                }
                val actions = @Composable {
                    ActionButtonsController(
                        modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                        onActionChanged = { action, isPressed ->
                            when (action) {
                                GameAction.A -> viewModel.setRunning(isPressed)
                                GameAction.B -> if (isPressed) viewModel.performPlayerAttack()
                                else -> {}
                            }
                        },
                        onClaimCollectiblePressed = {}
                    )
                }
                if (state.swapControls) { actions(); movement() } else { movement(); actions() }
            }
        }

        // ─── PANTALLA DE VICTORIA (al limpiar un edificio) ─
        if (state.showVictoryScreen) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Congratulations",
                        color = Color(0xFFD4AF37),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Edificio despejado. Volviendo al lobby...",
                        color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CollisionDebugOverlay(grid: CollisionGrid, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellW = size.width / grid.cols
        val cellH = size.height / grid.rows
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                if (grid.grid[row][col] == 0) {
                    drawRect(
                        color = Color(0x55FF0000),
                        topLeft = Offset(col * cellW, row * cellH),
                        size = Size(cellW, cellH)
                    )
                }
            }
        }
    }
}

@Composable
private fun HudChip(text: String, color: Color = Color(0xFF6B1C3A)) {
    Text(
        text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun ZombieSprite(zombie: ZombieEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame = remember(zombie.frameIndex) {
        ZombieSpriteManager.getFrame(context, zombie.frameIndex)
    }
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        if (zombie.health < 100f && !zombie.isDying) {
            Box(
                modifier = Modifier.offset(y = (-6).dp).width(30.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.4f))
            ) {
                LinearProgressIndicator(
                    progress = zombie.health / 100f,
                    modifier = Modifier.fillMaxSize(),
                    color = if (zombie.health > 50f) Color(0xFF8BC34A) else Color(0xFFF44336),
                    trackColor = Color.Transparent
                )
            }
        }
        frame?.let {
            Image(
                bitmap = it,
                contentDescription = "Zombi",
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = if (zombie.facingRight) 1f else -1f
                    alpha = if (zombie.isDying) 0.35f else 1f
                }
            )
        }
    }
}

@Composable
private fun ZombiePlayerSprite(
    action: PlayerAction, facingRight: Boolean,
    health: Float, showHealthBar: Boolean
) {
    val context = LocalContext.current
    var currentFrame by remember { mutableIntStateOf(1) }
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val bitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }

    LaunchedEffect(action) {
        currentFrame = 1
        while (true) {
            val maxFrames = when (action) {
                PlayerAction.SPECIAL -> 8; else -> 6
            }
            val path = when (action) {
                PlayerAction.IDLE -> "PRINCIPAL/lazaroIdle/lazaro_i_$currentFrame.webp"
                PlayerAction.WALK -> "PRINCIPAL/lazaroWalk/lazaro_w_$currentFrame.webp"
                PlayerAction.SPECIAL -> "PRINCIPAL/lazaroSpecial/lazaro_s_$currentFrame.webp"
                PlayerAction.RUN -> "PRINCIPAL/lazaroRun/lazaro_r_$currentFrame.webp"
            }
            if (!bitmapCache.containsKey(path)) {
                bitmapCache[path] = withContext(Dispatchers.IO) {
                    try { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
                    catch (e: Exception) { null }
                }
            }
            currentImage = bitmapCache[path]
            delay(if (action == PlayerAction.IDLE) 1000L else 100L)
            currentFrame = (currentFrame % maxFrames) + 1
        }
    }

    Box(contentAlignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize()) {
        if (showHealthBar) {
            Box(
                modifier = Modifier.offset(y = (-10).dp).width(40.dp).height(6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(Color.Black.copy(alpha = 0.5f))
            ) {
                LinearProgressIndicator(
                    progress = health / 100f,
                    modifier = Modifier.fillMaxSize(),
                    color = when { health > 60f -> Color(0xFF4CAF50); health > 30f -> Color(0xFFFFEB3B); else -> Color(0xFFF44336) },
                    trackColor = Color.Transparent
                )
            }
        }
        currentImage?.let {
            Image(bitmap = it, contentDescription = "Jugador",
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (facingRight) 1f else -1f })
        }
    }
}