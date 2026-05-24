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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.ZombieGameViewModel
import kotlin.math.max

private const val ZOOM = 2.2f   // nivel de zoom del minijuego

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
            catch (e: Exception) { null }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportWpx = with(density) { maxWidth.toPx() }
            val viewportHpx = with(density) { maxHeight.toPx() }

            // ─── CÁLCULO DE CÁMARA (fit + zoom + center + CLAMP) ───
            val cam = remember(state.playerX, state.playerY, viewportWpx, viewportHpx, room.id) {
                computeCamera(
                    playerX = state.playerX, playerY = state.playerY,
                    worldW = room.worldWidth, worldH = room.worldHeight,
                    viewW = viewportWpx, viewH = viewportHpx, zoom = ZOOM
                )
            }

            // ─── CAPA DEL MUNDO (se mueve con la cámara) ───────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bg = background ?: return@Canvas
                // Aplicamos la transformación de cámara a todo el mundo
                translate(cam.offsetX, cam.offsetY) {
                    scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                        // Fondo a tamaño de mundo (sin deformar: scale uniforme)
                        drawImage(
                            image = bg,
                            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            dstSize = androidx.compose.ui.unit.IntSize(room.worldWidth.toInt(), room.worldHeight.toInt())
                        )

                        // Debug: hitboxes de puertas y blockers
                        if (debugHitboxes) {
                            room.doors.forEach { d ->
                                val r = d.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                                drawRect(Color(0x5500FF00), Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top))
                            }
                            room.collisionGridFrac.forEach { f ->
                                val r = f.toWorldRect(room.worldWidth, room.worldHeight)
                                drawRect(Color(0x55FF0000), Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top))
                            }
                        }
                    }
                }
            }

            // ─── ENTIDADES (composables posicionados en pantalla) ─
            // Convertimos coordenadas de mundo → pantalla con la misma cámara.
            fun worldToScreenX(wx: Float) = cam.offsetX + wx * cam.scale
            fun worldToScreenY(wy: Float) = cam.offsetY + wy * cam.scale

            // Indicadores de puertas (brillo/pulsación) sobre cada hitbox
            room.doors.forEach { door ->
                val r = door.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                val sx = worldToScreenX(r.centerX())
                val sy = worldToScreenY(r.centerY())
                DoorIndicator(
                    label = door.label,
                    kind = door.kind,
                    modifier = Modifier.absoluteOffset(
                        x = with(density) { sx.toDp() } - 40.dp,
                        y = with(density) { sy.toDp() } - 40.dp
                    )
                )
            }

            // Items en el suelo
            state.items.forEach { item ->
                val sx = worldToScreenX(item.x); val sy = worldToScreenY(item.y)
                GroundItem(
                    assetPath = item.assetPath,
                    highlighted = state.nearbyItemId == item.id,
                    modifier = Modifier.absoluteOffset(
                        x = with(density) { sx.toDp() } - 16.dp,
                        y = with(density) { sy.toDp() } - 16.dp
                    )
                )
            }

            // Zombis (sprite + barra de vida flotante anclada a su posición)
            val zombieSizePx = ZOMBIE_SPRITE_BASE * cam.scale
            state.zombies.forEach { z ->
                key(z.id) {
                    val sx = worldToScreenX(z.x); val sy = worldToScreenY(z.y)
                    ZombieView(
                        frameIndex = z.frameIndex,
                        facingRight = z.facingRight,
                        isDying = z.isDying,
                        health = z.health,
                        maxHealth = z.maxHealth,
                        sizePx = zombieSizePx,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { sx.toDp() } - with(density) { (zombieSizePx / 2).toDp() },
                            y = with(density) { sy.toDp() } - with(density) { (zombieSizePx / 2).toDp() }
                        )
                    )
                }
            }

            // Jugador (centrado por la cámara, pero lo posicionamos por seguridad)
            val playerSizePx = PLAYER_SPRITE_BASE * cam.scale
            val psx = worldToScreenX(state.playerX); val psy = worldToScreenY(state.playerY)
            PlayerView(
                action = state.playerAction,
                facingRight = state.isPlayerFacingRight,
                damagePulse = state.damagePulseTrigger,
                sizePx = playerSizePx,
                modifier = Modifier.absoluteOffset(
                    x = with(density) { psx.toDp() } - with(density) { (playerSizePx / 2).toDp() },
                    y = with(density) { psy.toDp() } - with(density) { (playerSizePx / 2).toDp() }
                )
            )
        }

        if (background == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFD4AF37)) }
        }

        // ═══════════════════════════════════════════════════════
        // HUD (FIJO — no se mueve con la cámara)
        // ═══════════════════════════════════════════════════════
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

        // Prompt de puerta / item
        (state.nearbyDoorLabel ?: state.pickupToast)?.let { prompt ->
            Box(Modifier.fillMaxSize().padding(top = 90.dp), Alignment.TopCenter) {
                Text(
                    prompt.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                    modifier = Modifier.background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }

        // Victoria
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

private const val ZOMBIE_SPRITE_BASE = 60f   // px de mundo del sprite del zombi
private const val PLAYER_SPRITE_BASE = 56f

/**
 * Calcula la transformación de cámara:
 *  1. fitScale: escala mínima para que la imagen CUBRA el viewport sin franjas
 *     (cover) preservando aspect ratio.
 *  2. scale = fitScale * zoom.
 *  3. Centrar en el jugador: offset = viewportCenter - playerWorld * scale.
 *  4. CLAMP: el offset se limita para que el borde del mapa nunca entre en el
 *     viewport (no se ve fuera de la imagen).
 */
private fun computeCamera(
    playerX: Float, playerY: Float,
    worldW: Float, worldH: Float,
    viewW: Float, viewH: Float, zoom: Float
): CameraTransform {
    if (viewW <= 0f || viewH <= 0f) return CameraTransform(0f, 0f, 1f)

    // "cover": llena el viewport sin deformar (usa el mayor de los ratios)
    val fitScale = max(viewW / worldW, viewH / worldH)
    val scale = fitScale * zoom

    val scaledW = worldW * scale
    val scaledH = worldH * scale

    // Centrar en jugador
    var offsetX = viewW / 2f - playerX * scale
    var offsetY = viewH / 2f - playerY * scale

    // CLAMP: si el mapa escalado es mayor que el viewport, limitar el paneo
    // para no descubrir áreas fuera de la imagen. minOffset es negativo.
    val minOffsetX = viewW - scaledW
    val minOffsetY = viewH - scaledH
    offsetX = if (scaledW <= viewW) (viewW - scaledW) / 2f else offsetX.coerceIn(minOffsetX, 0f)
    offsetY = if (scaledH <= viewH) (viewH - scaledH) / 2f else offsetY.coerceIn(minOffsetY, 0f)

    return CameraTransform(offsetX, offsetY, scale)
}