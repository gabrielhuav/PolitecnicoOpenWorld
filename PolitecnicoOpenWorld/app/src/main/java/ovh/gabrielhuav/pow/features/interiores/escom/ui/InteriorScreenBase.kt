package ovh.gabrielhuav.pow.features.interiores.escom.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorState
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.InteriorViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CoordsWidget
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.ui.components.JoystickController
import ovh.gabrielhuav.pow.ui.components.WithShoulderTriggers
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

            // ─── CAPA DE OCLUSION (profundidad) ──────────────────────────────
            // Celdas '2' = objetos que TAPAN: se redibuja su trozo del fondo ENCIMA del jugador
            // cuando el objeto esta DELANTE (su base al sur de los pies). Por defecto los interiores
            // usan emptyWithBorder() (sin '2') -> no dibuja nada; queda listo para matrices con objetos.
            val occBg = bg
            if (occBg != null) {
                val grid = viewModel.collisionGrid
                val occluders = remember(grid) { computeGridOccluders(grid) }
                if (occluders.isNotEmpty()) {
                    val gCols = grid.cols
                    val gRows = grid.rows
                    val feetY = state.playerY
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellWpx = size.width / gCols
                        val cellHpx = size.height / gRows
                        occluders.forEach { oc ->
                            val anchorBottomNorm = (oc.anchorBottomRow + 1).toFloat() / gRows
                            if (anchorBottomNorm <= feetY) return@forEach
                            val srcX = (oc.col.toFloat() / gCols * occBg.width).toInt().coerceIn(0, occBg.width - 1)
                            val srcY = (oc.row.toFloat() / gRows * occBg.height).toInt().coerceIn(0, occBg.height - 1)
                            val srcW = (occBg.width.toFloat() / gCols).toInt().coerceIn(1, occBg.width - srcX)
                            val srcH = (occBg.height.toFloat() / gRows).toInt().coerceIn(1, occBg.height - srcY)
                            drawImage(
                                image = occBg,
                                srcOffset = IntOffset(srcX, srcY),
                                srcSize = IntSize(srcW, srcH),
                                dstOffset = IntOffset((oc.col * cellWpx).toInt(), (oc.row * cellHpx).toInt()),
                                dstSize = IntSize(cellWpx.toInt() + 1, cellHpx.toInt() + 1)
                            )
                        }
                    }
                }
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
                Icon(Icons.Default.ArrowBack, androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_exit), tint = Color.White)
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

        // Widget de coordenadas (Ajustes → Interfaz): X/Y = posición normalizada en la
        // sala, Z = "dónde" estás (nombre del interior).
        if (state.showCoordsWidget) {
            CoordsWidget(
                x = "%.3f".format(state.playerX),
                y = "%.3f".format(state.playerY),
                z = title.uppercase(),
                modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(top = 64.dp, start = 12.dp)
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
            // 🆕 (2026-07-26) Gatillos L1/L2/R1/R2 opcionales (Ajustes → Interfaz, OFF por defecto).
            // Aquí solo hay control de MOVIMIENTO (este interior no tiene diamante), así que se
            // usa el lado donde está colocado: swapControls lo manda a la derecha.
            WithShoulderTriggers(
                enabled = state.showShoulderButtons,
                isLeft = !state.swapControls,
                scale = effectiveScale,
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
                PlayerAction.IDLE    -> "SPRITES/PLAYER/lazaroIdle/lazaro_i_$currentFrame.webp"
                PlayerAction.WALK    -> "SPRITES/PLAYER/lazaroWalk/lazaro_w_$currentFrame.webp"
                PlayerAction.SPECIAL -> "SPRITES/PLAYER/lazaroSpecial/lazaro_s_$currentFrame.webp"
                PlayerAction.RUN     -> "SPRITES/PLAYER/lazaroRun/lazaro_r_$currentFrame.webp"
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

/** Celda "objeto que tapa" (valor 2): col/fila + la FILA base (inferior) del objeto contiguo. */
private class GridOccluderCell(val col: Int, val row: Int, val anchorBottomRow: Int)

/** Agrupa las celdas '2' contiguas (4-conexo) en objetos y devuelve cada celda con la fila base
 *  de su objeto. Se llama 1 vez por matriz (remember). */
private fun computeGridOccluders(grid: CollisionGrid): List<GridOccluderCell> {
    val g = grid.grid
    val rows = grid.rows; val cols = grid.cols
    if (rows == 0 || cols == 0) return emptyList()
    val occ = CollisionGrid.OCCLUDER
    val comp = Array(rows) { IntArray(cols) { -1 } }
    val compMaxRow = ArrayList<Int>()
    var next = 0
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            if (g[r][c] != occ || comp[r][c] != -1) continue
            val id = next++
            var maxRow = r
            val stack = ArrayDeque<Int>()
            comp[r][c] = id
            stack.addLast(r * cols + c)
            while (stack.isNotEmpty()) {
                val cell = stack.removeLast()
                val cr = cell / cols; val cc = cell % cols
                if (cr > maxRow) maxRow = cr
                val neigh = intArrayOf(cr - 1, cc, cr + 1, cc, cr, cc - 1, cr, cc + 1)
                var i = 0
                while (i < neigh.size) {
                    val nr = neigh[i]; val nc = neigh[i + 1]; i += 2
                    if (nr in 0 until rows && nc in 0 until cols && g[nr][nc] == occ && comp[nr][nc] == -1) {
                        comp[nr][nc] = id
                        stack.addLast(nr * cols + nc)
                    }
                }
            }
            compMaxRow.add(maxRow)
        }
    }
    if (next == 0) return emptyList()
    val out = ArrayList<GridOccluderCell>()
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val id = comp[r][c]
            if (id < 0) continue
            out.add(GridOccluderCell(c, r, compMaxRow[id]))
        }
    }
    return out
}