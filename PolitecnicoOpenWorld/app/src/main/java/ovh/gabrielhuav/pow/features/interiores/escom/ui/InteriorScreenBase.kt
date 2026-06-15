package ovh.gabrielhuav.pow.features.interiores.escom.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorState
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

/**
 * Composable base reutilizado por las 6 screens de interior.
 *
 * Cada edificio le pasa:
 *  - su ViewModel (con la matriz de colisión inyectada)
 *  - la ruta del asset de fondo
 *  - el título visible
 *  - un slot 'zombieContent' donde más adelante se montarán las mecánicas
 *    zombie propias del edificio. Por ahora todas las screens lo dejan vacío.
 */
@Composable
fun InteriorScreenBase(
    viewModel: InteriorViewModel,
    backgroundAssetPath: String,
    title: String,
    onExit: () -> Unit,
    zombieContent: @Composable BoxScope.() -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // Cargar imagen de fondo en IO
    var background by remember(backgroundAssetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(backgroundAssetPath) {
        background = withContext(Dispatchers.IO) {
            try {
                context.assets.open(backgroundAssetPath).use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    // Back físico → salir
    BackHandler { onExit() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ─── FONDO ──────────────────────────────────────────────────
        val bg = background
        if (bg != null) {
            Image(
                bitmap = bg,
                contentDescription = title,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFD4AF37))
            }
        }

        // ─── PERSONAJE ──────────────────────────────────────────────
        val playerSizeDp = 48.dp
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxW = maxWidth
            val maxH = maxHeight

            Box(
                modifier = Modifier
                    .offset(
                        x = maxW * state.playerX - playerSizeDp / 2,
                        y = maxH * state.playerY - playerSizeDp / 2
                    )
                    .size(playerSizeDp)
            ) {
                InteriorPlayerSprite(state)
            }
        }

        // ─── SLOT PARA MECÁNICAS ZOMBIE FUTURAS ─────────────────────
        zombieContent()

        // ─── HUD: BOTÓN VOLVER + TÍTULO ─────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onExit,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Salir", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                color = Color(0xFFD4AF37),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // ─── CONTROLES (solo movimiento) ────────────────────────────
        val sidePadding = if (isPortrait) 16.dp else 64.dp
        val bottomPadding = if (isPortrait) 48.dp else 32.dp
        val maxScale = if (isPortrait) 1.0f else 1.4f
        val effectiveScale = state.controlsScale.coerceAtMost(maxScale)

        Box(
            modifier = Modifier
                .align(if (state.swapControls) Alignment.BottomEnd else Alignment.BottomStart)
                .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding)
                .systemBarsPadding()
        ) {
            if (state.controlType == ControlType.DPAD) {
                DPadController(
                    modifier = Modifier.scale(effectiveScale),
                    onDirectionPressed = { viewModel.moveDirection(it) }
                )
            } else {
                JoystickController(
                    modifier = Modifier.scale(effectiveScale),
                    onMove = { viewModel.moveByAngle(it) }
                )
            }
        }
    }
}

/**
 * Sprite del personaje principal en el interior. Reusa los mismos assets
 * (lazaroIdle / lazaroWalk / lazaroRun) para verse idéntico al jugador del
 * open world.
 */
@Composable
private fun InteriorPlayerSprite(state: InteriorState) {
    val context = LocalContext.current
    val action = state.playerAction
    val isFacingRight = state.isFacingRight

    var currentFrame by remember { mutableIntStateOf(1) }
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val bitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }

    LaunchedEffect(action) {
        currentFrame = 1
        while (true) {
            val maxFrames = when (action) {
                PlayerAction.IDLE -> 6
                PlayerAction.WALK -> 6
                PlayerAction.SPECIAL -> 8
                PlayerAction.RUN -> 6
            }
            val assetPath = when (action) {
                PlayerAction.IDLE    -> "MAIN/lazaroIdle/lazaro_i_$currentFrame.webp"
                PlayerAction.WALK    -> "MAIN/lazaroWalk/lazaro_w_$currentFrame.webp"
                PlayerAction.SPECIAL -> "MAIN/lazaroSpecial/lazaro_s_$currentFrame.webp"
                PlayerAction.RUN     -> "MAIN/lazaroRun/lazaro_r_$currentFrame.webp"
            }
            if (!bitmapCache.containsKey(assetPath)) {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.assets.open(assetPath).use {
                            BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    } catch (e: Exception) { null }
                }
                bitmapCache[assetPath] = bmp
            }
            currentImage = bitmapCache[assetPath]

            val frameDelay = when (action) {
                PlayerAction.IDLE -> 1000L
                PlayerAction.WALK -> 100L
                PlayerAction.RUN  -> 100L
                PlayerAction.SPECIAL -> 300L
            }
            delay(frameDelay)
            currentFrame = (currentFrame % maxFrames) + 1
        }
    }

    val img = currentImage
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_character),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (isFacingRight) 1f else -1f
                }
        )
    }
}