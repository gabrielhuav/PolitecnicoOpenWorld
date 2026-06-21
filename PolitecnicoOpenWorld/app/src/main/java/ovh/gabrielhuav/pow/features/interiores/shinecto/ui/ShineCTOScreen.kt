package ovh.gabrielhuav.pow.features.interiores.shinecto.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOFloor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.*
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel.ShineCTOInteractable
import ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel.ShineCTOViewModel
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerHealthBarFixed
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerView
import androidx.compose.ui.res.stringResource
import ovh.gabrielhuav.pow.R
import kotlin.math.max
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog

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

    LaunchedEffect(state.shouldExitToWorld) {
        if (state.shouldExitToWorld) onExitToWorld()
    }

    // ── Background asset per floor ───────────────────────────────────────────
    val assetPath = if (state.floor == ShineCTOFloor.GROUND)
        "PLACES/shine_cto/s_pbaja.webp"
    else
        "PLACES/shine_cto/s_palta.webp"

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
                shineCollectibleActive = state.shineCollectibleActive,
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

        if (state.showWastedScreen) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                var wastedScale by remember { mutableFloatStateOf(0.5f) }
                LaunchedEffect(Unit) {
                    animate(initialValue = 0.5f, targetValue = 1.3f,
                        animationSpec = tween(3500, easing = LinearOutSlowInEasing)
                    ) { v, _ -> wastedScale = v }
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
        state.showClaimedPopupFor?.let { collectible ->
            CollectibleClaimDialog(
                collectible = collectible,
                onDismiss = { vm.dismissShineClaimedPopup() }
            )
        }
    }
}

// ── Small animated dot markers for interactables ────────────────────────────
@Composable
private fun InteractableDots(
    floor: ShineCTOFloor,
    drinks: List<ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel.ActiveDrink>,
    shineCollectibleActive: Boolean,
    offsetX: Float, offsetY: Float,
    camScale: Float,
    worldW: Float, worldH: Float
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val shineBitmap = remember {
        try {
            context.assets.open("SPRITES/COLLECTIBLES/colec_shine.webp")
                .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (e: Exception) { null }
    }

    // Cargar asset de bebida una sola vez
    val drinkBitmap = remember {
        try {
            context.assets.open("PLACES/shine_cto/s_bebidas.webp")
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
    val drinkGlowAlpha by rememberInfiniteTransition(label = "drinkGlow").animateFloat(
        initialValue = 0.2f, targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drinkGlowAlpha"
    )
    val drinkSizeDp = with(density) { (40f * camScale).toDp() }
    drinks.forEach { drink ->
        val sx = with(density) { (offsetX + drink.nx * worldW * camScale).toDp() }
        val sy = with(density) { (offsetY + drink.ny * worldH * camScale).toDp() }
        Box(
            modifier = Modifier
                .absoluteOffset(x = sx - drinkSizeDp / 2, y = sy - drinkSizeDp / 2)
                .size(drinkSizeDp)
        ) {
            // Halo pulsante detrás del asset
            Box(
                modifier = Modifier.fillMaxSize().scale(1.55f)
                    .background(Color(0xFF8BC34A).copy(alpha = drinkGlowAlpha), CircleShape)
            )
            if (drinkBitmap != null) {
                Image(bitmap = drinkBitmap, contentDescription = stringResource(R.string.sc_cd_drink), modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF8BC34A).copy(alpha = 0.8f), CircleShape))
            }
        }
    }

    // Coleccionable Shine (aparece tras 10 refrescos, planta baja)
    if (shineCollectibleActive && floor == ShineCTOFloor.GROUND) {
        val snx = 0.35f; val sny = 0.45f
        val shineSizeDp = with(density) { (44f * camScale).toDp() }
        val ssx = with(density) { (offsetX + snx * worldW * camScale).toDp() }
        val ssy = with(density) { (offsetY + sny * worldH * camScale).toDp() }
        Box(
            modifier = Modifier
                .absoluteOffset(x = ssx - shineSizeDp / 2, y = ssy - shineSizeDp / 2)
                .size(shineSizeDp)
        ) {
            shineBitmap?.let {
                Image(bitmap = it, contentDescription = stringResource(R.string.sc_cd_collectible), modifier = Modifier.fillMaxSize())
            } ?: Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFD700).copy(alpha = 0.85f), CircleShape))
        }
    }
}

// ── HUD: top bar + prompt + controls ────────────────────────────────────────
@Composable
private fun ShineCTOHud(
    state: ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel.ShineCTOState,
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
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val floorLabel = if (state.floor == ShineCTOFloor.GROUND) stringResource(R.string.sc_floor_ground) else stringResource(R.string.sc_floor_upper)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, stringResource(R.string.sc_cd_exit), tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.sc_header, floorLabel),
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
                if (state.drinkCount > 0) {
                    Text(
                        text = stringResource(R.string.sc_drinks, state.drinkCount),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0xFF6B1C3A).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            PlayerHealthBarFixed(health = state.playerHealth)
        }


        // ── Drink toast ──────────────────────────────────────────────────────
        if (state.showDrinkToast) {
            Box(
                Modifier.fillMaxSize().padding(top = 100.dp),
                Alignment.TopCenter
            ) {
                Text(
                    text = state.drinkToastMessage,
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
private typealias ShineCTOState = ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel.ShineCTOState