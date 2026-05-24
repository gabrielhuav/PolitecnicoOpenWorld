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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
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

        // ─── BARRA SUPERIOR ────────────────────────────────
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
            PlayerHealthBarFixed(health = state.playerHealth)
        }

        // ─── CONTROLES: cruceta IZQ + acciones DER ──────────
        // Clave de la corrección: usamos Modifier.scale (que NO rompe el layout
        // como graphicsLayer al posicionar) y reducimos el padding lateral para
        // que AMBOS controles quepan siempre dentro de la pantalla.
        val sidePadding = if (isPortrait) 8.dp else 32.dp
        val bottomPadding = if (isPortrait) 32.dp else 20.dp
        // Escala segura: en vertical no agrandamos para que el lado derecho no
        // se salga; el usuario puede subirla desde Ajustes hasta el límite.
        val maxScale = if (isPortrait) 0.95f else 1.3f
        val scale = state.controlsScale.coerceIn(0.6f, maxScale)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding)
                .systemBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val movement = @Composable {
                if (state.controlType == ControlType.DPAD)
                    DPadController(modifier = Modifier.scale(scale), onDirectionPressed = onMoveDir)
                else
                    JoystickController(modifier = Modifier.scale(scale), onMove = onMoveAngle)
            }
            // ── BOTONES DE ACCIÓN ESTILO XBOX (A / X / B / Y) ──
            val actions = @Composable {
                ActionButtonsController(
                    modifier = Modifier.scale(scale),
                    onActionChanged = { action, pressed ->
                        when (action) {
                            GameAction.A -> onRun(pressed)             // A = correr
                            GameAction.X -> if (pressed) onInteract()  // X = interactuar
                            GameAction.B -> onSpecial(pressed)         // B = golpear/especial
                            GameAction.Y -> if (pressed) onSecondary() // Y = secundaria
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
        modifier = Modifier.width(180.dp).height(18.dp).clip(RoundedCornerShape(9.dp))
            .background(Color.Black.copy(alpha = 0.6f)).border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(9.dp))
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

@Composable
fun DoorIndicator(label: String, kind: DoorKind, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "door")
    val pulse by infinite.animateFloat(0.7f, 1.25f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val color = when (kind) {
        DoorKind.TO_WORLD -> Color(0xFF2196F3)
        DoorKind.EXIT_NEXT, DoorKind.EXIT_PREV -> Color(0xFFFF9800)
        else -> Color(0xFFD4AF37)
    }
    Box(modifier = modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.scale(pulse).size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)))
        Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.85f)).border(2.dp, Color.White, CircleShape))
        if (label.isNotEmpty()) {
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = 26.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun GroundItem(assetPath: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp = remember(assetPath) {
        try { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
        catch (e: Exception) { null }
    }
    val infinite = rememberInfiniteTransition(label = "item")
    val glow by infinite.animateFloat(0.8f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    Box(modifier = modifier.size(32.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.scale(if (highlighted) glow else 1f).size(28.dp).clip(CircleShape).background(Color(0x88FFEB3B)))
        bmp?.let { Image(it, "item", modifier = Modifier.size(20.dp)) }
            ?: Box(Modifier.size(18.dp).clip(CircleShape).background(Color(0xFFFFC107)))
    }
}

@Composable
fun ZombieView(
    frameIndex: Int, facingRight: Boolean, isDying: Boolean,
    health: Float, maxHealth: Float, sizePx: Float, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val frame = remember(frameIndex) { ZombieSpriteManager.getFrame(context, frameIndex) }
    val sizeDp = with(density) { sizePx.toDp() }

    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.TopCenter) {
        // Barra de vida flotante
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
        // Sprite o FALLBACK VISIBLE (círculo verde con borde) si el asset no carga
        if (frame != null) {
            Image(frame, "Zombi", modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = if (facingRight) 1f else -1f
                alpha = if (isDying) 0.35f else 1f
            })
        } else {
            Box(
                modifier = Modifier.fillMaxSize().padding(top = 6.dp).clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = if (isDying) 0.35f else 0.9f))
                    .border(2.dp, Color(0xFF1B5E20), CircleShape)
            )
        }
    }
}

@Composable
fun PlayerView(
    action: PlayerAction, facingRight: Boolean, damagePulse: Int,
    sizePx: Float, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    var frame by remember { mutableIntStateOf(1) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val cache = remember { mutableMapOf<String, ImageBitmap?>() }

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
        if (image != null) {
            Image(image!!, "Jugador", modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (facingRight) 1f else -1f })
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFD91B5B)).border(2.dp, Color.White, CircleShape))
        }
    }
}