package ovh.gabrielhuav.pow.features.streetfighter.ui

import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cycleShowcaseSpeed
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.dismissGauntletReport
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.goToPreviousShowcaseAnimation
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.skipShowcaseFighter
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.skipToNextShowcaseAnimation
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startAiVsAi
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startGauntletArcade
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startGauntletRoundRobin
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startShowcase
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopGauntlet
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.replayCurrentShowcaseAudio
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopAudioShowcase
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeContinue
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeExit
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.arcadeRetry
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.comboSheet
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.exitTutorial
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.resumeArcadeSession
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startArcade
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startTutorial
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.tutorialRestartLesson
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.tutorialSkipLesson
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cancelBtScan
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.cancelOnline
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.chooseMapOnline
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.connectBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.connectLanHost
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.dismissBtError
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.requestJoinRoom
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.respondJoin
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startBtHost
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startBtScan
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startLanDiscovery
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startLanHost
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startOnline
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.startOnlineQuick
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.stopLanDiscovery
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFrameDef
import ovh.gabrielhuav.pow.ui.components.ActionButton
import ovh.gabrielhuav.pow.ui.components.JoystickController
import ovh.gabrielhuav.pow.ui.components.PowButton
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfArcadeOutcome
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfOnlineStatus
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SF_STOP_SPECIALS_EVENT
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterState
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel

// ────────────────────────────────────────────────────────────────────────────
// 🎛️ OVERLAYS de MENÚ: modo de juego, selección de peleador, dificultad y resultado
//
// Todo lo que el jugador ve ANTES y DESPUÉS de pelear: el menú de modos, la parrilla de selección
// de peleador, los dos selectores de dificultad (VS y arcade), el resultado de la pelea arcade y
// el informe del gauntlet.
// ⚠️ El peleador por defecto se DECODIFICA al abrir el modo: debe ser SIEMPRE un peleador POW,
// nunca Ryu/Ken (que solo existen en el build debug). Ver 09 §12, assets por variante.
//
// Extraído de StreetFighterScreen.kt (4029 líneas) en el refactor de tamaño de la Fase 5.
// Son composables/helpers TOP-LEVEL del mismo paquete: `internal` en vez de `private` para
// que la Screen los siga viendo. Sin cambios de comportamiento.
// ────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GauntletReportOverlay(
    progress: String,
    report: List<String>,
    path: String?,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xF0101018)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_gauntlet_report_title),
                color = Color(0xFFD4AF37),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$progress · ${report.size}",
                color = Color.White,
                fontSize = 12.sp,
            )
            if (path != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = path,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (report.isEmpty()) {
                Text(
                    text = stringResource(R.string.sf_gauntlet_no_issues),
                    color = Color(0xFF8BC34A),
                    fontSize = 13.sp,
                )
            } else {
                report.forEach { line ->
                    Text(
                        text = "• $line",
                        color = Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 12.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            PowButton(
                text = stringResource(R.string.sf_close),
                onClick = onClose,
                color = Color(0xFF1C4A6B),
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }
    }
}

@Composable
internal fun SfModeMenuOverlay(
    // 🆕 (2026-07-18) Autojuego/Showcase son EXCLUSIVOS del Modo Desarrollador (QA interno).
    devMode: Boolean,
    audioShowcaseRunning: Boolean,
    audioShowcaseIndex: Int,
    audioShowcaseTotal: Int,
    audioShowcaseFighter: SfFighterId?,
    audioShowcasePhrase: String,
    onArcade: () -> Unit,
    onPractice: () -> Unit,
    onAiVsAi: () -> Unit,
    onCombos: () -> Unit, // 🆕 (2026-07-21) hoja de combos + tutorial interactivo
    onMultiplayer: () -> Unit,
    onGauntletAll: () -> Unit,
    onGauntletArcade: () -> Unit,
    onGauntletShowcase: () -> Unit,
    onAudioShowcaseStop: () -> Unit,
    onBack: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.menu_street_fighter),
                color = Color(0xFFD4AF37),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            // ARCADE — modalidad PRINCIPAL (grande y destacada, ahora ANIMADO como en el menú principal)
            FeaturedArcadeButton(
                text = stringResource(R.string.sf_mode_arcade),
                tag = stringResource(R.string.sf_mode_arcade_desc),
                onClick = onArcade,
                enabled = true
            )
            Spacer(modifier = Modifier.height(18.dp))
            // Otras modalidades (ahora respetando el color guinda del menú principal)
            PowButton(
                text = stringResource(R.string.sf_mode_practice),
                onClick = onPractice,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 🆕 IA VS IA (CPU vs CPU a PESADILLA; para grabar en video)
            PowButton(
                text = stringResource(R.string.sf_mode_ai_vs_ai),
                onClick = onAiVsAi,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sf_mode_ai_vs_ai_desc),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            // 🆕 (2026-07-21) HOJA DE COMBOS + TUTORIAL interactivo. Visible SIEMPRE (no es
            // una herramienta de QA: es como se aprende a jugar el modo).
            Spacer(modifier = Modifier.height(10.dp))
            PowButton(
                text = stringResource(R.string.sf_mode_combos),
                onClick = onCombos,
                color = Color(0xFF1C6B4A),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sf_mode_combos_desc),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
            // 🆕 AUTOJUEGO / SHOWCASE: QA interno, SOLO con Modo Desarrollador (Ajustes).
            if (devMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sf_dev_tools_header),
                    color = Color(0xFFFFD54A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_all),
                    onClick = onGauntletAll,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_arcade),
                    onClick = onGauntletArcade,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                PowButton(
                    text = stringResource(R.string.sf_gauntlet_showcase),
                    onClick = onGauntletShowcase,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.fillMaxWidth(0.68f),
                )
                if (audioShowcaseRunning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(
                        text = stringResource(R.string.sf_audio_showcase_stop),
                        onClick = onAudioShowcaseStop,
                        color = Color(0xFF8B1538),
                        modifier = Modifier.fillMaxWidth(0.68f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.sf_audio_showcase_progress,
                            audioShowcaseIndex,
                            audioShowcaseTotal,
                            audioShowcaseFighter?.shortName ?: "",
                        ),
                        color = Color(0xFFFFD54A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = audioShowcasePhrase,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.82f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            PowButton(
                text = stringResource(R.string.sf_mode_multiplayer),
                onClick = onMultiplayer,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.68f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.sf_back), color = Color(0xFFD4AF37))
            }
        }
    }
}

/**
 * Botón principal animado estilo POW para el modo Arcade.
 * Presenta pulso de escala, barrido de brillo dorado, borde y sombra doradas latentes.
 */
@Composable
internal fun FeaturedArcadeButton(text: String, tag: String, onClick: () -> Unit, enabled: Boolean) {
    val shape = CutCornerShape(topStart = 20.dp, bottomEnd = 20.dp)
    val gold = Color(0xFFFFD54A)
    val tr = rememberInfiniteTransition(label = "sfArcadeFeatured")
    val scale by tr.animateFloat(
        1f, 1.05f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )
    val glow by tr.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(850, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow",
    )
    val shimmer by tr.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "shimmer",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(76.dp)
            .graphicsLayer { scaleX = if (enabled) scale else 1f; scaleY = if (enabled) scale else 1f }
            .shadow(elevation = 18.dp, shape = shape, ambientColor = gold, spotColor = gold)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(Color(0xFF8A0F32), Color(0xFFC4143C), Color(0xFF8A0F32))),
            )
            .border(BorderStroke(3.dp, gold.copy(alpha = if (enabled) glow else 0.5f)), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Barrido de brillo dorado (detrás del texto)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val w = size.width
                    val hl = w * 0.30f
                    val x = -hl + (w + hl) * shimmer
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.5f to gold.copy(alpha = 0.40f),
                            1f to Color.Transparent,
                            startX = x,
                            endX = x + hl,
                        ),
                    )
                },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "★ $text ★",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = tag,
                color = gold,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------
// Selector de personaje (pre-pelea): tarjetas con preview del sprite
// (BitmapRegionDecoder: decodifica SOLO el recorte idle-1 de cada sheet,
// no los 7 sheets completos — cuidado con la RAM en gama baja, ver 09 §6)
// y aviso ALPHA para los peleadores POW con poses aproximadas.
// ------------------------------------------------------------------

/**
 * 🆕 (2026-07-18) Cabecera de flechas sobre un card del selector:
 *  - AZUL "P1 ▼": el peleador YA elegido (jugador 1), visible al elegir al rival.
 *  - ROJA "P2 ▼": el card que estás resaltando al elegir al jugador 2.
 * Alto FIJO (reserva el espacio aunque no haya flecha) para que los cards no salten.
 */
@Composable
internal fun SelectArrowHeader(showAlly: Boolean, showPick: Boolean) {
    val blue = Color(0xFF2196F3)
    val red = Color(0xFFE53935)
    Box(
        modifier = Modifier.height(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // 🆕 (2026-07-18ñ) MISMO personaje para P1 y P2: se muestran AMBAS flechas.
            showAlly && showPick -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("P1 ▼", color = blue, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Text("P2 ▼", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
            showPick -> Text("P2 ▼", color = red, fontSize = 13.sp, fontWeight = FontWeight.Black)
            showAlly -> Text("P1 ▼", color = blue, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun CharacterSelectOverlay(
    fighters: List<SfFighterId>, // DESBLOQUEADOS (RYU/KEN solo con Modo Desarrollador)
    onSelect: (SfFighterId) -> Unit,
    subtitle: String? = null,
    lockedFighters: List<SfFighterId> = emptyList(), // 🆕 se pintan con candado 🔒 (no seleccionables)
    onOnline: (() -> Unit)? = null,
    onArcade: (() -> Unit)? = null,                  // 🆕 abre el flujo de ARCADE
    onBack: (() -> Unit)? = null,                    // 🆕 volver (p. ej. salir del setup de arcade)
    backText: String? = null,                        // 🆕 etiqueta del botón volver (default "← Volver")
    /** Gama baja: previews siempre estáticos (sin animar ningún card). */
    lowEnd: Boolean = false,
    // 🆕 (2026-07-18) Al elegir al PELEADOR 2/rival: FLECHA AZUL sobre el P1 ya elegido y
    // FLECHA ROJA sobre el que estás resaltando (además de la animación del card).
    allyId: SfFighterId? = null,      // P1 ya elegido → flecha AZUL ("P1")
    showPickArrow: Boolean = false,   // true en el paso de elegir P2 → flecha ROJA en el focused
    isActuallyUnlocked: (SfFighterId) -> Boolean = { true },
) {
    // 🆕 Solo el focused anima (y solo si NO es gama baja). 2.º toque confirma.
    var focusedId by remember(fighters) { mutableStateOf(fighters.firstOrNull()) }
    // 🆕 (2026-07-18ñ) Parpadeo azul⇄rojo del recuadro cuando P1 y P2 son el MISMO personaje.
    var flashRed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { delay(450); flashRed = !flashRed }
    }
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_choose_fighter),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            subtitle?.let {
                Text(text = it, color = Color(0xFF90CAF9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                fighters.forEach { id ->
                    val isAlly = id == allyId
                    val isPick = showPickArrow && id == focusedId
                    val both = isAlly && isPick // el resaltado (P2) es el MISMO que el P1 elegido
                    // Mismo personaje → recuadro PARPADEA azul⇄rojo; si no, rojo P2 o azul P1.
                    val hl = when {
                        both -> if (flashRed) Color(0xFFE53935) else Color(0xFF2196F3)
                        isPick -> Color(0xFFE53935)
                        isAlly -> Color(0xFF2196F3)
                        else -> null
                    }
                    // 🆕 Cada card con un espacio ARRIBA para las flechas P1 (azul) / P2 (roja).
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SelectArrowHeader(showAlly = isAlly, showPick = isPick)
                        CharacterCard(
                            id = id,
                            selected = id == focusedId,
                            animate = !lowEnd && id == focusedId,
                            highlightColor = hl,
                            silhouette = !isActuallyUnlocked(id),
                            onSelect = {
                                if (lowEnd || id == focusedId) onSelect(id)
                                else focusedId = id
                            },
                        )
                    }
                }
                // Bloqueados: estáticos (nunca animar)
                lockedFighters.forEach { id ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SelectArrowHeader(showAlly = false, showPick = false)
                        CharacterCard(id = id, onSelect = {}, locked = true, animate = false, selected = false)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.sf_alpha_note),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
            )
            // 🆕 Botón de CONFIRMAR el peleador resaltado (antes solo confirmaba el 2.º toque).
            focusedId?.let { fid ->
                Spacer(modifier = Modifier.height(8.dp))
                PowButton(
                    text = stringResource(R.string.sf_confirm),
                    onClick = { onSelect(fid) },
                    color = Color(0xFFB8143A),
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onArcade?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(text = stringResource(R.string.sf_arcade_button), onClick = it)
                }
                onOnline?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    PowButton(text = stringResource(R.string.sf_mp_button), onClick = it)
                }
            }
            onBack?.let {
                TextButton(onClick = it) {
                    Text(backText ?: stringResource(R.string.sf_back), color = Color(0xFFD4AF37))
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 🆕 Overlay de fin del MODO ARCADE: ganar (CONTINUAR) / perder (REINTENTAR) / campeón.
// ------------------------------------------------------------------

@Composable
internal fun ArcadeResultOverlay(
    outcome: SfArcadeOutcome,
    step: Int,
    total: Int,
    grade: String,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(powMenuBg)
            .clickable(enabled = true, onClick = {}), // bloquea toques al joystick de atrás
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // 🆕 (2026-07-25) NOTA del combate estilo SF III (solo cuando el jugador ganó el escalón)
            if (grade.isNotEmpty()) {
                SfGradeBadge(grade)
                Spacer(modifier = Modifier.height(12.dp))
            }
            when (outcome) {
                SfArcadeOutcome.COMPLETED -> {
                    Text(
                        text = stringResource(R.string.sf_arcade_champion),
                        color = Color(0xFFD4AF37), fontSize = 26.sp, fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sf_arcade_champion_sub),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    PowButton(text = stringResource(R.string.sf_arcade_finish), onClick = onExit)
                }
                SfArcadeOutcome.LOST -> {
                    Text(
                        text = stringResource(R.string.sf_arcade_lost),
                        color = Color(0xFFEF5350), fontSize = 24.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sf_arcade_lost_sub),
                        color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PowButton(text = stringResource(R.string.sf_arcade_retry), onClick = onRetry)
                        PowButton(text = stringResource(R.string.sf_arcade_quit), onClick = onExit)
                    }
                }
                else -> { // WON
                    Text(
                        text = stringResource(R.string.sf_arcade_won),
                        color = Color(0xFF81C784), fontSize = 24.sp, fontWeight = FontWeight.Black,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sf_arcade_progress, step, total),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PowButton(text = stringResource(R.string.sf_arcade_continue), onClick = onContinue)
                        PowButton(text = stringResource(R.string.sf_arcade_quit), onClick = onExit)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------
// 🆕 Selector de DIFICULTAD de la CPU (paso 3 del flujo offline, 2026-07-16):
// BÁSICA (aprender) / NORMAL (la clásica) / AVANZADA (casi imposible).
// Solo offline: online el rival es humano y este paso no existe.
// ------------------------------------------------------------------

@Composable
internal fun DifficultySelectOverlay(
    onSelect: (SfCpuDifficulty) -> Unit,
    onBack: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_choose_difficulty),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            DifficultyOption(
                title = stringResource(R.string.sf_diff_basic),
                desc = stringResource(R.string.sf_diff_basic_desc),
                onClick = { onSelect(SfCpuDifficulty.BASICA) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_diff_normal),
                desc = stringResource(R.string.sf_diff_normal_desc),
                onClick = { onSelect(SfCpuDifficulty.NORMAL) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_diff_advanced),
                desc = stringResource(R.string.sf_diff_advanced_desc),
                onClick = { onSelect(SfCpuDifficulty.AVANZADA) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_diff_nightmare),
                desc = stringResource(R.string.sf_diff_nightmare_desc),
                onClick = { onSelect(SfCpuDifficulty.PESADILLA) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.sf_change_fighter),
                    color = Color(0xFFD4AF37),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Dificultad del ARCADE: solo 3 niveles.
 * Fácil → mapas de día · Medio → noche · Difícil → noche apocalíptica (noche_2).
 */
@Composable
internal fun ArcadeDifficultyOverlay(
    onSelect: (SfCpuDifficulty) -> Unit,
    onBack: () -> Unit,
) {
    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.sf_choose_difficulty),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.sf_arcade_diff_maps_hint),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            DifficultyOption(
                title = stringResource(R.string.sf_arcade_diff_easy),
                desc = stringResource(R.string.sf_arcade_diff_easy_desc),
                onClick = { onSelect(SfCpuDifficulty.BASICA) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_arcade_diff_medium),
                desc = stringResource(R.string.sf_arcade_diff_medium_desc),
                onClick = { onSelect(SfCpuDifficulty.NORMAL) },
            )
            DifficultyOption(
                title = stringResource(R.string.sf_arcade_diff_hard),
                desc = stringResource(R.string.sf_arcade_diff_hard_desc),
                onClick = { onSelect(SfCpuDifficulty.AVANZADA) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.sf_change_fighter),
                    color = Color(0xFFD4AF37),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/** Botón de dificultad + su descripción corta debajo (mismo tema vino/dorado). */
@Composable
internal fun DifficultyOption(title: String, desc: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PowButton(text = title, onClick = onClick)
        Text(
            text = desc,
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
    }
}

// ------------------------------------------------------------------
// Overlays del MULTIJUGADOR: menú crear/unir/pública + pantallas de espera.
// (PowButton, el botón estilo POW, vive COMPARTIDO en ui/components/PowButton.kt —
// 🆕 2026-07-26: se movió ahí desde map_exterior, que no era su sitio.)
// ------------------------------------------------------------------

/** Permisos runtime del ANFITRIÓN BT (Android 12+): aceptar conexiones + ser visible. */

