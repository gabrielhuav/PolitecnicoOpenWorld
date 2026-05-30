package ovh.gabrielhuav.pow.features.shinecto.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.ShineCTOFloor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.*
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.shinecto.viewmodel.ShineCTOInteractable
import ovh.gabrielhuav.pow.features.shinecto.viewmodel.ShineCTOViewModel
import ovh.gabrielhuav.pow.features.zombie_minigame.ui.PlayerView
import kotlin.math.max

// Zoom that creates the "large venue" feeling (lower = more zoomed-out)
private const val INTERIOR_ZOOM = 1.3f

@Composable
fun ShineCTOScreen(onExitToWorld: () -> Unit) {
    val context = LocalContext.current
    val vm: ShineCTOViewModel = viewModel(factory = ShineCTOViewModel.Factory(context))
    val state by vm.state.collectAsState()
    val density = LocalDensity.current

    // Back button exits to world
    BackHandler { onExitToWorld() }

    // ── Background asset per floor ───────────────────────────────────────────
    val assetPath = if (state.floor == ShineCTOFloor.GROUND)
        "LUGARES/shineCTO/s_pbaja.webp"
    else
        "LUGARES/shineCTO/s_palta.webp"

    var background by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        background = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            } catch (e: Exception) { null }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }

            // World dimensions come from the loaded bitmap (or sensible fallback)
            val bg = background
            val worldW = bg?.width?.toFloat() ?: 1920f
            val worldH = bg?.height?.toFloat() ?: 1080f

            // Camera: same algorithm as ZombieGameScreen.computeCamera
            val fitScale = max(viewW / worldW, viewH / worldH)
            val camScale = fitScale * INTERIOR_ZOOM
            val scaledW = worldW * camScale
            val scaledH = worldH * camScale

            val rawOffX = viewW / 2f - state.playerX * worldW * camScale
            val rawOffY = viewH / 2f - state.playerY * worldH * camScale
            val offsetX = if (scaledW <= viewW) (viewW - scaledW) / 2f
            else rawOffX.coerceIn(viewW - scaledW, 0f)
            val offsetY = if (scaledH <= viewH) (viewH - scaledH) / 2f
            else rawOffY.coerceIn(viewH - scaledH, 0f)

            // ── World layer: background ──────────────────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (bg == null) return@Canvas
                translate(offsetX, offsetY) {
                    scale(camScale, camScale, pivot = Offset.Zero) {
                        drawImage(
                            image = bg,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(worldW.toInt(), worldH.toInt())
                        )
                    }
                }
            }

            // ── Interactable zone indicators (subtle colored rects in debug; dots in play) ──
            InteractableDots(
                floor = state.floor,
                drinks = state.drinks,
                offsetX = offsetX,
                offsetY = offsetY,
                camScale = camScale,
                worldW = worldW,
                worldH = worldH
            )

            // ── Player sprite ────────────────────────────────────────────────
            val pSizePx = 56f * camScale
            val pSizeDp = with(density) { pSizePx.toDp() }
            val pScreenX = offsetX + state.playerX * worldW * camScale
            val pScreenY = offsetY + state.playerY * worldH * camScale

            PlayerView(
                action = state.playerAction,
                facingRight = state.isFacingRight,
                damagePulse = 0,
                sizePx = pSizePx,
                modifier = Modifier.absoluteOffset(
                    x = with(density) { (pScreenX - pSizePx / 2f).toDp() },
                    y = with(density) { (pScreenY - pSizePx / 2f).toDp() }
                )
            )
        }

        if (background == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFD4AF37))
            }
        }

        // ── HUD ─────────────────────────────────────────────────────────────
        ShineCTOHud(
            state = state,
            onMoveDir = vm::moveDirection,
            onMoveAngle = vm::moveByAngle,
            onInteract = {
                val shouldExit = vm.onInteract()
                if (shouldExit) onExitToWorld()
            },
            onRun = vm::setRunning,
            onSpecial = vm::setSpecial,
            onBack = onExitToWorld
        )
    }
}

// ── Small animated dot markers for interactables ────────────────────────────
@Composable
private fun InteractableDots(
    floor: ShineCTOFloor,
    drinks: List<ovh.gabrielhuav.pow.features.shinecto.viewmodel.ActiveDrink>,
    offsetX: Float, offsetY: Float,
    camScale: Float,
    worldW: Float, worldH: Float
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Cargar asset de bebida una sola vez
    val drinkBitmap = remember {
        try {
            context.assets.open("LUGARES/shineCTO/s_bebidas.webp")
                .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (e: Exception) { null }
    }

    // Puntos fijos (EXIT y STAIRS) — sin bebidas, esas son dinámicas
    val fixedDots = if (floor == ShineCTOFloor.GROUND) listOf(
        Triple(0.90f, 0.50f, Color(0xFF2196F3)),  // EXIT
        Triple(0.50f, 0.89f, Color(0xFFD4AF37))   // STAIRS
    ) else listOf(
        Triple(0.50f, 0.89f, Color(0xFFD4AF37))   // STAIRS
    )

    fixedDots.forEach { (nx, ny, color) ->
        val sx = with(density) { (offsetX + nx * worldW * camScale).toDp() }
        val sy = with(density) { (offsetY + ny * worldH * camScale).toDp() }
        Box(
            modifier = Modifier
                .absoluteOffset(x = sx - 10.dp, y = sy - 10.dp)
                .size(20.dp)
                .background(color.copy(alpha = 0.65f), CircleShape)
        )
    }

    // Bebidas dinámicas con asset
    val drinkSizeDp = with(density) { (40f * camScale).toDp() }
    drinks.forEach { drink ->
        val sx = with(density) { (offsetX + drink.nx * worldW * camScale).toDp() }
        val sy = with(density) { (offsetY + drink.ny * worldH * camScale).toDp() }
        Box(
            modifier = Modifier
                .absoluteOffset(x = sx - drinkSizeDp / 2, y = sy - drinkSizeDp / 2)
                .size(drinkSizeDp)
        ) {
            if (drinkBitmap != null) {
                Image(
                    bitmap = drinkBitmap,
                    contentDescription = "Bebida",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback visual si el asset no carga
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF8BC34A).copy(alpha = 0.8f), CircleShape)
                )
            }
        }
    }
}

// ── HUD: top bar + prompt + controls ────────────────────────────────────────
@Composable
private fun ShineCTOHud(
    state: ovh.gabrielhuav.pow.features.shinecto.viewmodel.ShineCTOState,
    onMoveDir: (Direction) -> Unit,
    onMoveAngle: (Double) -> Unit,
    onInteract: () -> Unit,
    onRun: (Boolean) -> Unit,
    onSpecial: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)

    Box(modifier = Modifier.fillMaxSize()) {

        // ── Top-left info bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Salir", tint = Color.White)
            }

            val floorLabel = if (state.floor == ShineCTOFloor.GROUND) "Planta Baja" else "Planta Alta"
            Text(
                text = "SHINE CTO • $floorLabel",
                color = Color(0xFFD4AF37),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )

            if (state.drinkCount > 0) {
                Text(
                    text = "🍺 ×${state.drinkCount}  ${(state.speedMultiplier * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFF6B1C3A).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // ── Drink toast ──────────────────────────────────────────────────────
        if (state.showDrinkToast) {
            val msg = when {
                state.speedMultiplier <= ShineCTOState.MIN_SPEED ->
                    "¡Ya no puedes más! Velocidad mínima."
                state.drinkCount == 1 -> "¡Salud! La primera siempre entra bien."
                state.drinkCount == 2 -> "Dos bebidas… empiezas a sentirlo."
                else -> "¡${state.drinkCount} bebidas! Cada vez más lento…"
            }
            Box(
                Modifier.fillMaxSize().padding(top = 100.dp),
                Alignment.TopCenter
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color(0xFF3B0D1B).copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // ── Nearby interactable prompt ───────────────────────────────────────
        state.nearbyInteractable?.let { nearby ->
            Box(
                Modifier.fillMaxSize().padding(top = 68.dp),
                Alignment.TopCenter
            ) {
                Text(
                    text = nearby.label.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // ── Bottom controls ──────────────────────────────────────────────────
        val effectiveScale = state.controlsScale.coerceAtMost(1.0f)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val movement: @Composable () -> Unit = {
                if (state.controlType == ControlType.DPAD)
                    DPadController(modifier = Modifier.scale(effectiveScale), onDirectionPressed = onMoveDir)
                else
                    JoystickController(modifier = Modifier.scale(effectiveScale), onMove = onMoveAngle)
            }
            val actions: @Composable () -> Unit = {
                ActionButtonsController(
                    modifier = Modifier.scale(effectiveScale),
                    onActionChanged = { action, pressed ->
                        when (action) {
                            GameAction.X -> if (pressed) onInteract()
                            GameAction.A -> onRun(pressed)
                            GameAction.B -> onSpecial(pressed)
                            else -> {}
                        }
                    },
                    onClaimCollectiblePressed = { onInteract() }
                )
            }
            if (state.swapControls) { actions(); movement() }
            else { movement(); actions() }
        }
    }
}

// ── Alias so ShineCTOState companion is accessible without import conflict ──
private typealias ShineCTOState = ovh.gabrielhuav.pow.features.shinecto.viewmodel.ShineCTOState