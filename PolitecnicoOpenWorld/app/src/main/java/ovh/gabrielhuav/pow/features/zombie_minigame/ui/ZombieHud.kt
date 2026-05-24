// features/zombie_minigame/ui/ZombieHud.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.ZombieGameState

@Composable
fun ZombieHud(
    state: ZombieGameState,
    roomName: String,
    isBuilding: Boolean,
    onMoveDir: (Direction) -> Unit,
    onMoveAngle: (Double) -> Unit,
    onRun: (Boolean) -> Unit,
    onInteract: () -> Unit,
    onSpecial: (Boolean) -> Unit,
    onSecondary: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Box(Modifier.fillMaxSize()) {

        // ─── BARRA SUPERIOR: vida del jugador + info de zona ───
        Column(
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ZONA: ${roomName.uppercase()}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xFF6B1C3A).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp))
                if (isBuilding) {
                    Text("ZOMBIS: ${state.zombiesRemaining}/${state.totalZombies}", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFFD32F2F).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            // Barra de vida del jugador FIJA en el HUD
            PlayerHealthBarFixed(health = state.playerHealth)
        }

        // ─── CONTROLES (cruceta izq / acciones der) ────────────
        val sidePadding = if (isPortrait) 16.dp else 48.dp
        val bottomPadding = if (isPortrait) 40.dp else 24.dp
        val maxScale = if (isPortrait) 1.0f else 1.4f
        val scale = state.controlsScale.coerceAtMost(maxScale)

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding).systemBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val movement = @Composable {
                if (state.controlType == ControlType.DPAD)
                    DPadController(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                        onDirectionPressed = onMoveDir)
                else
                    JoystickController(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                        onMove = onMoveAngle)
            }
            val actions = @Composable {
                ActionButtonsController(
                    modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale),
                    onActionChanged = { action, pressed ->
                        when (action) {
                            GameAction.A -> onRun(pressed)            // A = correr
                            GameAction.X -> if (pressed) onInteract() // X = interactuar
                            GameAction.B -> onSpecial(pressed)        // B = golpear/especial
                            GameAction.Y -> if (pressed) onSecondary()// Y = acción secundaria
                        }
                    },
                    onClaimCollectiblePressed = { onInteract() }
                )
            }
            if (state.swapControls) { actions(); movement() } else { movement(); actions() }
        }
    }
}

@Composable
private fun PlayerHealthBarFixed(health: Float) {
    Box(
        modifier = Modifier.width(180.dp).height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(9.dp))
    ) {
        LinearProgressIndicator(
            progress = (health / 100f).coerceIn(0f, 1f),
            modifier = Modifier.fillMaxSize(),
            color = when { health > 60f -> Color(0xFF4CAF50); health > 30f -> Color(0xFFFFEB3B); else -> Color(0xFFF44336) },
            trackColor = Color.Transparent
        )
        Text("${health.toInt()} HP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center))
    }
}

/** Indicador pulsante sobre cada puerta/hitbox del mapa. */
@Composable
fun DoorIndicator(label: String, kind: DoorKind, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "door")
    val pulse by infinite.animateFloat(
        initialValue = 0.7f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val color = when (kind) {
        DoorKind.TO_WORLD -> Color(0xFF2196F3)
        DoorKind.EXIT_NEXT, DoorKind.EXIT_PREV -> Color(0xFFFF9800)
        else -> Color(0xFFD4AF37)
    }
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        // halo pulsante
        Box(Modifier.scale(pulse).size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)))
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.85f))
            .border(2.dp, Color.White, CircleShape))
        if (label.isNotEmpty()) {
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = 26.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** Item en el suelo, con brillo cuando el jugador está cerca. */
@Composable
fun GroundItem(assetPath: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp = remember(assetPath) {
        try { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
        catch (e: Exception) { null }
    }
    val infinite = rememberInfiniteTransition(label = "item")
    val glow by infinite.animateFloat(0.8f, 1.2f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    Box(modifier = modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.scale(if (highlighted) glow else 1f).size(28.dp).clip(CircleShape)
            .background(Color(0x88FFEB3B)))
        bmp?.let { Image(it, "item", modifier = Modifier.size(20.dp)) }
    }
}

/** Zombi: sprite z_walk + barra de vida flotante anclada (ya viene en px de pantalla). */
@Composable
fun ZombieView(
    frameIndex: Int, facingRight: Boolean, isDying: Boolean,
    health: Float, maxHealth: Float, sizePx: Float, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val frame = remember(frameIndex) { ZombieSpriteManager.getFrame(context, frameIndex) }
    val sizeDp = with(density) { sizePx.toDp() }

    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.TopCenter) {
        // Barra de vida flotante (anclada arriba del sprite, escala con la cámara)
        if (health < maxHealth && !isDying) {
            Box(
                modifier = Modifier.offset(y = (-6).dp).fillMaxWidth(0.7f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.5f))
            ) {
                LinearProgressIndicator(
                    progress = (health / maxHealth).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxSize(),
                    color = if (health > maxHealth * 0.5f) Color(0xFF8BC34A) else Color(0xFFF44336),
                    trackColor = Color.Transparent
                )
            }
        }
        frame?.let {
            Image(it, "Zombi", modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = if (facingRight) 1f else -1f
                alpha = if (isDying) 0.35f else 1f
            })
        }
    }
}

/** Jugador: reutiliza assets lazaro* y cambia con PlayerAction en tiempo real. */
@Composable
fun PlayerView(
    action: PlayerAction, facingRight: Boolean, damagePulse: Int,
    sizePx: Float, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    var frame by remember { mutableIntStateOf(1) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val cache = remember { mutableMapOf<String, ImageBitmap?>() }

    // Sacudida al recibir daño
    val shake by animateFloatAsState(
        targetValue = if (damagePulse % 2 == 0) 0f else 8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh),
        label = "shake"
    )

    LaunchedEffect(action) {
        frame = 1
        while (true) {
            val maxFrames = if (action == PlayerAction.SPECIAL) 8 else 6
            val path = when (action) {
                PlayerAction.IDLE -> "PRINCIPAL/lazaroIdle/lazaro_i_$frame.webp"
                PlayerAction.WALK -> "PRINCIPAL/lazaroWalk/lazaro_w_$frame.webp"
                PlayerAction.SPECIAL -> "PRINCIPAL/lazaroSpecial/lazaro_s_$frame.webp"
                PlayerAction.RUN -> "PRINCIPAL/lazaroRun/lazaro_r_$frame.webp"
            }
            if (!cache.containsKey(path)) {
                cache[path] = withContext(Dispatchers.IO) {
                    try { context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
                    catch (e: Exception) { null }
                }
            }
            image = cache[path]
            delay(if (action == PlayerAction.IDLE) 1000L else 100L)
            frame = (frame % maxFrames) + 1
        }
    }

    Box(modifier = modifier.size(sizeDp).graphicsLayer { translationX = shake }, contentAlignment = Alignment.Center) {
        image?.let {
            Image(it, "Jugador", modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (facingRight) 1f else -1f })
        }
    }
}