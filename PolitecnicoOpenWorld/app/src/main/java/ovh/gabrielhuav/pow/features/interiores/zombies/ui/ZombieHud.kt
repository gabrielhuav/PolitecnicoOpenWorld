package ovh.gabrielhuav.pow.features.interiores.zombies.ui

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
import androidx.compose.ui.res.stringResource
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
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin          // ← NUEVO
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieGameState
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerHealthBarFixed   // barra de vida compartida (core)
import ovh.gabrielhuav.pow.R

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
                Text(stringResource(R.string.zhud_zone, roomName.uppercase()), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Color(0xFF6B1C3A).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp))
                if (isBuilding && state.zombieModeActivated) {
                    Text(stringResource(R.string.zhud_zombies, state.zombiesRemaining, state.totalZombies), color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFFD32F2F).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
                if (state.remotePlayers.isNotEmpty()) {
                    Text(stringResource(R.string.zhud_players, state.remotePlayers.size + 1), color = Color.White, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0xFF2196F3).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            PlayerHealthBarFixed(health = state.playerHealth)
            Text(
                text = stringResource(R.string.zhud_mode, if (state.combatMode == CombatMode.MELEE) stringResource(R.string.zhud_mode_melee) else stringResource(R.string.zhud_mode_ranged)),
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xFF2A1C21).copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
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
                    Text(stringResource(R.string.zhud_combat_mode), color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    WeaponMenuButton(stringResource(R.string.zhud_mode_melee), state.combatMode == CombatMode.MELEE) { onSelectMode(CombatMode.MELEE) }
                    WeaponMenuButton(stringResource(R.string.zhud_mode_ranged), state.combatMode == CombatMode.RANGED) { onSelectMode(CombatMode.RANGED) }
                    TextButton(onClick = onDismissWeaponMenu) { Text(stringResource(R.string.zhud_close), color = Color.White) }
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
        initialValue = 0.05f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    val intensity = (1f - (health / 35f)).coerceIn(0f, 1f)
    val currentAlpha = alpha * intensity
    Box(modifier = Modifier.fillMaxSize().background(
        Brush.radialGradient(0.0f to Color.Transparent, 0.6f to Color.Transparent, 1.0f to Color.Red.copy(alpha = currentAlpha))
    ))
}

@Composable
private fun WeaponMenuButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0xFF6B1C3A) else Color(0xFF2A1C21)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(180.dp).height(48.dp)
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// PlayerHealthBarFixed se movió a features/interiores/core/ui/InteriorPlayerViews.kt (compartido).

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
    Column(modifier = modifier.wrapContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.scale(pulse).size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.25f)))
            Box(Modifier.size(30.dp).clip(CircleShape).background(color.copy(alpha = 0.85f)).border(2.dp, Color.White, CircleShape))
        }
        if (label.isNotEmpty()) {
            Text(text = label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Visible, textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-8).dp).wrapContentWidth(unbounded = true)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun SkillGroundItem(effect: SkillEffect, highlighted: Boolean, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "skill")
    val glow by infinite.animateFloat(0.85f, 1.2f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    Box(modifier = modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.scale(if (highlighted) glow else 1f).size(34.dp).clip(CircleShape)
            .background((if (effect.isTrap) Color(0xFFFF5252) else Color(0xFF69F0AE)).copy(alpha = 0.30f)))
        Canvas(modifier = Modifier.size(26.dp)) {
            val w = size.width; val h = size.height
            when {
                effect == SkillEffect.RELOJ_ARENA -> {
                    val sand = Color(0xFF5C6BC0)
                    val topTri = Path().apply { moveTo(w*.18f,h*.12f); lineTo(w*.82f,h*.12f); lineTo(w*.5f,h*.5f); close() }
                    val bottomTri = Path().apply { moveTo(w*.5f,h*.5f); lineTo(w*.18f,h*.88f); lineTo(w*.82f,h*.88f); close() }
                    drawPath(topTri, sand); drawPath(bottomTri, sand)
                    drawLine(Color.White, Offset(w*.15f,h*.12f), Offset(w*.85f,h*.12f), strokeWidth = w*.08f)
                    drawLine(Color.White, Offset(w*.15f,h*.88f), Offset(w*.85f,h*.88f), strokeWidth = w*.08f)
                }
                effect.isTrap -> {
                    val path = Path().apply { moveTo(w*.05f,h*.15f); lineTo(w*.95f,h*.15f); lineTo(w*.5f,h*.95f); close() }
                    drawPath(path, Color(0xFFD32F2F))
                    drawLine(Color.White, Offset(w*.5f,h*.30f), Offset(w*.5f,h*.58f), strokeWidth = w*.10f)
                    drawCircle(Color.White, radius = w*.05f, center = Offset(w*.5f,h*.70f))
                }
                else -> {
                    drawCircle(Color(0xFF2E7D32), radius = w*.48f, center = Offset(w*.5f,h*.5f))
                    val armT = w*.14f
                    drawLine(Color.White, Offset(w*.5f,h*.25f), Offset(w*.5f,h*.75f), strokeWidth = armT)
                    drawLine(Color.White, Offset(w*.25f,h*.5f), Offset(w*.75f,h*.5f), strokeWidth = armT)
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
    val frame = remember(type, frameIndex, isAttacking) { ZombieSpriteManager.getFrame(context, type, isAttacking, frameIndex) }
    val sizeDp = with(density) { sizePx.toDp() }
    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.TopCenter) {
        if (health < maxHealth && !isDying) {
            Box(modifier = Modifier.offset(y = (-6).dp).fillMaxWidth(0.7f).height(4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.5f))) {
                LinearProgressIndicator(
                    progress = (health / maxHealth).coerceIn(0f, 1f), modifier = Modifier.fillMaxSize(),
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
            Box(modifier = Modifier.fillMaxSize().padding(top = 6.dp).clip(CircleShape)
                .background(Color(0xFF4CAF50).copy(alpha = if (isDying) 0.35f else 0.9f))
                .border(2.dp, Color(0xFF1B5E20), CircleShape))
        }
    }
}
// PlayerView, RemotePlayerView y la extensión privada PlayerSkin.playerViewPath se
// movieron a features/interiores/core/ui/InteriorPlayerViews.kt (vistas compartidas).