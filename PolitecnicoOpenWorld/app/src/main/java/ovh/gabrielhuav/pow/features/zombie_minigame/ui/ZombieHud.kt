package ovh.gabrielhuav.pow.features.zombie_minigame.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieType
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
    onSecondaryPressed: () -> Unit,
    onSecondaryReleased: () -> Unit,
    onSelectMode: (CombatMode) -> Unit,
    onDismissWeaponMenu: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    Box(Modifier.fillMaxSize()) {

        LowHealthAura(health = state.playerHealth)

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
                // Indicador de jugadores conectados en la sala (multijugador)
                if (state.remotePlayers.isNotEmpty()) {
                    Text("JUGADORES: ${state.remotePlayers.size + 1}", color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            PlayerHealthBarFixed(health = state.playerHealth)
            Text(
                text = "MODO: ${if (state.combatMode == CombatMode.MELEE) "GOLPE" else "ARMA"}  (mantén Y)",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xFF2A1C21).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
            // Chips de efectos activos
            if (state.activeEffects.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.activeEffects.forEach { ae ->
                        Text(
                            ae.effect.displayName,
                            color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(
                                    if (ae.effect.isTrap) Color(0xFFE57373) else Color(0xFF81C784),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // ─── CONTROLES ─────────────────────────────────────
        val sidePadding = if (isPortrait) 8.dp else 32.dp
        val bottomPadding = if (isPortrait) 32.dp else 20.dp
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
            val actions = @Composable {
                ActionButtonsController(
                    modifier = Modifier.scale(scale),
                    onActionChanged = { action, pressed ->
                        when (action) {
                            GameAction.A -> onRun(pressed)
                            GameAction.X -> if (pressed) onInteract()
                            GameAction.B -> onSpecial(pressed)
                            GameAction.Y -> if (pressed) onSecondaryPressed() else onSecondaryReleased()
                        }
                    },
                    onClaimCollectiblePressed = { onInteract() }
                )
            }
            if (state.swapControls) { actions(); movement() } else { movement(); actions() }
        }

        // ─── MENÚ DE ARMAS ─────────────────────────────────
        if (state.showWeaponMenu) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x88000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .background(Color(0xFF1E1E24), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Text("MODO DE COMBATE", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    WeaponMenuButton("GOLPE", state.combatMode == CombatMode.MELEE) { onSelectMode(CombatMode.MELEE) }
                    WeaponMenuButton("ARMA", state.combatMode == CombatMode.RANGED) { onSelectMode(CombatMode.RANGED) }
                    TextButton(onClick = onDismissWeaponMenu) { Text("Cerrar", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun LowHealthAura(health: Float) {
    if (health > 35f) return

    val infiniteTransition = rememberInfiniteTransition(label = "lowHealthAura")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // A medida que la vida baja de 35 a 0, el efecto es más pronunciado
    val intensity = (1f - (health / 35f)).coerceIn(0f, 1f)
    val currentAlpha = alpha * intensity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1.0f to Color.Red.copy(alpha = currentAlpha),
                )
            )
    )
}

@Composable
private fun WeaponMenuButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF6B1C3A) else Color(0xFF2A1C21)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(180.dp).height(48.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

/**
 * REQUERIMIENTO 1: Indicador de puerta con etiqueta SIEMPRE en una sola línea.
 * El contenedor usa wrapContentSize y el Text usa maxLines=1 + softWrap=false +
 * wrapContentWidth(unbounded=true) para permitir que la etiqueta exceda el ancho
 * del indicador circular sin partirse en varias líneas.
 */
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

    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicador circular pulsante
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.scale(pulse).size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)))
            Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.85f)).border(2.dp, Color.White, CircleShape))
        }

        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .wrapContentWidth(unbounded = true)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * REQUERIMIENTO 2: Item de habilidad en el suelo, renderizado EXCLUSIVAMENTE
 * con Canvas. No carga ningún asset .webp; todos los íconos son vectoriales:
 *   - RELOJ_ARENA: reloj de arena estilizado (dos triángulos opuestos + tapas).
 *   - Trampas (ADRENALINA / FURIA): triángulo rojo invertido con signo "!".
 *   - Buffs (CURA_TOTAL / DEBILIDAD / FUERZA_BRUTA): círculo verde con cruz blanca.
 */
@Composable
fun SkillGroundItem(effect: SkillEffect, highlighted: Boolean, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "skill")
    val glow by infinite.animateFloat(0.85f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")

    Box(modifier = modifier.size(36.dp), contentAlignment = Alignment.Center) {
        // Halo
        Box(
            Modifier.scale(if (highlighted) glow else 1f).size(34.dp).clip(CircleShape)
                .background(
                    (if (effect.isTrap) Color(0xFFFF5252) else Color(0xFF69F0AE)).copy(alpha = 0.30f)
                )
        )

        Canvas(modifier = Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            when {
                effect == SkillEffect.RELOJ_ARENA -> {
                    // Reloj de arena: dos triángulos opuestos formando un moño.
                    val sand = Color(0xFF5C6BC0)
                    val topTri = Path().apply {
                        moveTo(w * 0.18f, h * 0.12f)
                        lineTo(w * 0.82f, h * 0.12f)
                        lineTo(w * 0.5f, h * 0.5f)
                        close()
                    }
                    val bottomTri = Path().apply {
                        moveTo(w * 0.5f, h * 0.5f)
                        lineTo(w * 0.18f, h * 0.88f)
                        lineTo(w * 0.82f, h * 0.88f)
                        close()
                    }
                    drawPath(topTri, sand)
                    drawPath(bottomTri, sand)
                    // Tapas blancas arriba y abajo
                    drawLine(Color.White, Offset(w * 0.15f, h * 0.12f), Offset(w * 0.85f, h * 0.12f), strokeWidth = w * 0.08f)
                    drawLine(Color.White, Offset(w * 0.15f, h * 0.88f), Offset(w * 0.85f, h * 0.88f), strokeWidth = w * 0.08f)
                }
                effect.isTrap -> {
                    // Triángulo rojo invertido (alerta) con signo de exclamación
                    val path = Path().apply {
                        moveTo(w * 0.05f, h * 0.15f)
                        lineTo(w * 0.95f, h * 0.15f)
                        lineTo(w * 0.5f, h * 0.95f)
                        close()
                    }
                    drawPath(path, Color(0xFFD32F2F))
                    drawLine(
                        Color.White,
                        start = Offset(w * 0.5f, h * 0.30f),
                        end = Offset(w * 0.5f, h * 0.58f),
                        strokeWidth = w * 0.10f
                    )
                    drawCircle(Color.White, radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.70f))
                }
                else -> {
                    // Buff / curación: círculo verde con cruz blanca
                    drawCircle(Color(0xFF2E7D32), radius = w * 0.48f, center = Offset(w * 0.5f, h * 0.5f))
                    val armT = w * 0.14f
                    drawLine(
                        Color.White,
                        start = Offset(w * 0.5f, h * 0.25f),
                        end = Offset(w * 0.5f, h * 0.75f),
                        strokeWidth = armT
                    )
                    drawLine(
                        Color.White,
                        start = Offset(w * 0.25f, h * 0.5f),
                        end = Offset(w * 0.75f, h * 0.5f),
                        strokeWidth = armT
                    )
                }
            }
        }
    }
}

@Composable
fun ZombieView(
    type: ZombieType, frameIndex: Int, facingRight: Boolean, isAttacking: Boolean,
    isDying: Boolean, health: Float, maxHealth: Float, sizePx: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val frame = remember(type, frameIndex, isAttacking) {
        ZombieSpriteManager.getFrame(context, type, isAttacking, frameIndex)
    }
    val sizeDp = with(density) { sizePx.toDp() }

    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.TopCenter) {
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

/**
 * Jugador remoto (multijugador). Reusa el mismo sprite del jugador principal,
 * pero le añade una etiqueta de nombre flotante y un borde de color distinto
 * para diferenciarlo del jugador local de un vistazo.
 */
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

    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.TopCenter) {
        // Etiqueta de nombre, siempre en una sola línea.
        if (name.isNotBlank()) {
            Text(
                text = name,
                color = Color(0xFF64B5F6),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                modifier = Modifier
                    .offset(y = (-12).dp)
                    .wrapContentWidth(unbounded = true)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
        if (image != null) {
            Image(image!!, "Jugador remoto",
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = if (facingRight) 1f else -1f })
        } else {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFF2196F3)).border(2.dp, Color.White, CircleShape))
        }
    }
}