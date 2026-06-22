package ovh.gabrielhuav.pow.features.interiores.core.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

// ─────────────────────────────────────────────────────────────────────────────
// Vistas COMPARTIDAS del jugador para el modo Interiores (umbrella `interiores`).
// Extraídas de ZombieHud.kt para que las usen tanto el minijuego de zombis
// (`interiores.zombies`) como otros interiores (p. ej. ShineCTO en
// `interiores.shinecto`) SIN depender del paquete de zombis.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun PlayerHealthBarFixed(health: Float) {
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
        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.interior_hp, health.toInt()), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlayerView — recibe skin: PlayerSkin y usa sus paths y frame counts.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PlayerView(
    action: PlayerAction,
    facingRight: Boolean,
    damagePulse: Int,
    sizePx: Float,
    skin: PlayerSkin = PlayerSkin.LAZARO,          // ← default = Lázaro
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    var frame by remember { mutableIntStateOf(1) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val cache = remember { mutableMapOf<String, ImageBitmap?>() }

    // AUDIO de pasos en interiores SIN motor de sonido propio (ESCOM simple / ShineCTO): su VM no toca
    // SoundManager, así que lo enrutamos aquí (vista del jugador LOCAL; la RemotePlayerView NO lleva esto
    // para no sonar por cada jugador remoto). El game loop del mapa global está GATEADO
    // (worldMapForeground=false en interiores), así que no interfiere. Al salir → onDispose para los pasos.
    val soundManager = remember { ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(context) }
    LaunchedEffect(action) {
        when (action) {
            PlayerAction.WALK -> { soundManager.playWalk(); soundManager.stopRun() }
            PlayerAction.RUN  -> { soundManager.playRun(); soundManager.stopWalk() }
            else -> { soundManager.stopWalk(); soundManager.stopRun() }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { soundManager.stopWalk(); soundManager.stopRun() }
    }

    val shake by animateFloatAsState(
        targetValue = if (damagePulse % 2 == 0) 0f else 8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh),
        label = "shake"
    )

    // Reacciona tanto a cambios de acción como de skin ← clave del fix
    LaunchedEffect(action, skin) {
        frame = 1
        while (true) {
            val maxFrames = when (action) {
                PlayerAction.IDLE    -> skin.idleFrames
                PlayerAction.WALK    -> skin.walkFrames
                PlayerAction.RUN     -> skin.runFrames
                PlayerAction.SPECIAL -> skin.specialFrames
            }
            val path = skin.playerViewPath(action, frame)
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
            Image(image!!, androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_player), modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (facingRight) 1f else -1f })
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFD91B5B)).border(2.dp, Color.White, CircleShape))
        }
    }
}

@Composable
fun RemotePlayerView(
    name: String,
    action: PlayerAction,
    facingRight: Boolean,
    sizePx: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizeDp = with(density) { sizePx.toDp() }
    var frame by remember { mutableIntStateOf(1) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val cache = remember { mutableMapOf<String, ImageBitmap?>() }

    LaunchedEffect(action) {
        frame = 1
        while (true) {
            val maxFrames = if (action == PlayerAction.SPECIAL) 8 else 6
            // Jugadores remotos siempre usan Lázaro (su skin propia no se transmite en red)
            val path = PlayerSkin.LAZARO.playerViewPath(action, frame)
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

    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.TopCenter) {
        if (name.isNotBlank()) {
            Text(text = name, color = Color(0xFF64B5F6), fontSize = 9.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible,
                modifier = Modifier.offset(y = (-12).dp).wrapContentWidth(unbounded = true)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp))
        }
        if (image != null) {
            Image(image!!, androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_player_remote),
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (facingRight) 1f else -1f })
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2196F3)).border(2.dp, Color.White, CircleShape))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Extensión privada: resuelve la ruta de asset delegando en PlayerSkin
// ─────────────────────────────────────────────────────────────────────────────
private fun PlayerSkin.playerViewPath(action: PlayerAction, frame: Int): String = when (action) {
    PlayerAction.IDLE    -> idlePath(frame)
    PlayerAction.WALK    -> walkPath(frame)
    PlayerAction.RUN     -> runPath(frame)
    PlayerAction.SPECIAL -> specialPath(frame)
}
