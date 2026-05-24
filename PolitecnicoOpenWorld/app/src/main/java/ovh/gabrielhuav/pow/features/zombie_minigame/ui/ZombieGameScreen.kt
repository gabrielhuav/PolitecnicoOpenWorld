// features/zombie_minigame/ui/ZombieGameScreen.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
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
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.ZombieGameViewModel
import kotlin.math.max

private const val ZOOM = 2.2f
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
                computeCamera(state.playerX, state.playerY, room.worldWidth, room.worldHeight, viewportWpx, viewportHpx, ZOOM)
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

            fun toScreenX(wx: Float) = cam.offsetX + wx * cam.scale
            fun toScreenY(wy: Float) = cam.offsetY + wy * cam.scale

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

            // Items en el suelo
            state.items.forEach { item ->
                GroundItem(
                    assetPath = item.assetPath,
                    highlighted = state.nearbyItemId == item.id,
                    modifier = Modifier.absoluteOffset(
                        x = with(density) { toScreenX(item.x).toDp() } - 16.dp,
                        y = with(density) { toScreenY(item.y).toDp() } - 16.dp
                    )
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
            onSecondary = viewModel::onSecondaryAction
        )

        (state.nearbyDoorLabel ?: state.pickupToast)?.let { prompt ->
            Box(Modifier.fillMaxSize().padding(top = 90.dp), Alignment.TopCenter) {
                Text(prompt.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                    modifier = Modifier.background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 18.dp, vertical = 9.dp))
            }
        }

        if (state.showVictoryScreen) {
            Box(Modifier.fillMaxSize().background(Color(0xCC000000)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Congratulations", color = Color(0xFFD4AF37), fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text("Edificio despejado. Volviendo al lobby...", color = Color.White, fontSize = 16.sp)
                }
            }
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