package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.interiores.core.ui.CollisionMatrixDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.ui.InteriorNpcView
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerView
import ovh.gabrielhuav.pow.features.interiores.core.ui.RemotePlayerView
import ovh.gabrielhuav.pow.features.interiores.core.ui.WaypointDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.DesignerBrush
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieInteriorViewModel
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.exportMatricesToUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.exportWaypointsToUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.importMatricesFromUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.importWaypointsFromUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.moveSelectedDoorToWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.paintCellAtWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resetDesignerMatrix
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resetDesignerWaypoints
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resizeDesignerMatrixBy
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.saveDesignerMatrix
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.saveDesignerWaypoints
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.selectDoorAtWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.setDesignerBrush
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.setDesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.toggleDesignerMode
import ovh.gabrielhuav.pow.features.map_exterior.ui.SkinSelectorDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.ZombiVideoPlayer
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuItem
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionsMenu
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// 🧟 PIEZAS de la escena zombi: sprites de suelo, cámara y oclusores
//
// Trozos de render reutilizables de la sala: el sprite de historia en el suelo, la llave
// recogible, el cálculo de qué celdas TAPAN al jugador y el encuadre de la cámara.
// ⚠️ computeOccluders y computeCamera se llaman POR FRAME: mantenlos baratos. Ver 09 §6 (reglas
// de gama baja) antes de meter aquí allocations por frame.
//
// ExtraÃ­do de StreetFighterScreen.kt (4029 lÃ­neas) en el refactor de tamaÃ±o de la Fase 5.
// Son composables/helpers TOP-LEVEL del mismo paquete: `internal` en vez de `private` para
// que la Screen los siga viendo. Sin cambios de comportamiento.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
internal fun StoryGroundSprite(
    assetPath: String,
    sizePx: Float,
    fallbackEmoji: String,
    modifier: Modifier = Modifier,
    contentAlpha: Float = 1f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bmp by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        bmp = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                    android.graphics.BitmapFactory.decodeStream(it, null, o)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    val img = bmp
    if (img != null) {
        Image(
            img,
            contentDescription = null,
            modifier = modifier.size(with(density) { sizePx.toDp() }).alpha(contentAlpha)
        )
    } else {
        Text(
            text = fallbackEmoji,
            fontSize = with(density) { sizePx.toSp() },
            modifier = modifier.alpha(contentAlpha)
        )
    }
}

// Llave del puzzle (ENCB_lab1) dibujada en el suelo. Carga el PNG del asset (submuestreado para
// no gastar memoria en gama baja) y, si el jugador está sobre ella, la resalta con un aro dorado.
@Composable
internal fun KeyGroundItem(assetPath: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bmp by remember(assetPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(assetPath) {
        bmp = withContext(Dispatchers.IO) {
            try {
                context.assets.open(assetPath).use {
                    val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                    android.graphics.BitmapFactory.decodeStream(it, null, o)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (highlighted) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(Color(0x66FFD54F))
                    .border(2.dp, Color(0xFFFFD54F), CircleShape)
            )
        }
        val img = bmp
        if (img != null) {
            Image(img, contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_key), modifier = Modifier.size(if (highlighted) 40.dp else 34.dp))
        } else {
            Text("🔑", fontSize = 26.sp)
        }
    }
}

/** Celda '^' lista para redibujar: col/fila + la Y-base (inferior, mundo) del OBJETO al que pertenece. */
internal class OccluderCell(val col: Int, val row: Int, val anchorBottomY: Float)

/** Agrupa las celdas '^' contiguas (4-conexo) en objetos y devuelve cada celda con la Y-base de su
 *  objeto. Asi un mueble alto ocluye como un todo segun su base. Se llama 1 vez por matriz (remember). */
internal fun computeOccluders(rows: List<String>, worldW: Float, worldH: Float): List<OccluderCell> {
    if (rows.isEmpty() || worldW <= 0f || worldH <= 0f) return emptyList()
    val numRows = rows.size
    val numCols = rows.maxOf { it.length }.coerceAtLeast(1)
    fun isOcc(r: Int, c: Int) = c < rows[r].length && rows[r][c] == '^'
    val comp = Array(numRows) { IntArray(numCols) { -1 } }
    val compMaxRow = ArrayList<Int>()
    var nextComp = 0
    for (r in 0 until numRows) {
        for (c in 0 until numCols) {
            if (!isOcc(r, c) || comp[r][c] != -1) continue
            val id = nextComp++
            var maxRow = r
            val stack = ArrayDeque<Int>()
            comp[r][c] = id
            stack.addLast(r * numCols + c)
            while (stack.isNotEmpty()) {
                val cell = stack.removeLast()
                val cr = cell / numCols; val cc = cell % numCols
                if (cr > maxRow) maxRow = cr
                val neigh = intArrayOf(cr - 1, cc, cr + 1, cc, cr, cc - 1, cr, cc + 1)
                var i = 0
                while (i < neigh.size) {
                    val nr = neigh[i]; val nc = neigh[i + 1]; i += 2
                    if (nr in 0 until numRows && nc in 0 until numCols && isOcc(nr, nc) && comp[nr][nc] == -1) {
                        comp[nr][nc] = id
                        stack.addLast(nr * numCols + nc)
                    }
                }
            }
            compMaxRow.add(maxRow)
        }
    }
    if (nextComp == 0) return emptyList()
    val cellH = worldH / numRows
    val out = ArrayList<OccluderCell>()
    for (r in 0 until numRows) {
        for (c in 0 until numCols) {
            val id = comp[r][c]
            if (id < 0) continue
            out.add(OccluderCell(c, r, (compMaxRow[id] + 1) * cellH))
        }
    }
    return out
}

internal fun computeCamera(
    playerX: Float, playerY: Float, worldW: Float, worldH: Float,
    viewW: Float, viewH: Float, zoom: Float
): CameraTransform {
    if (viewW <= 0f || viewH <= 0f) return CameraTransform(0f, 0f, 1f)
    val fitScale = max(viewW / worldW, viewH / worldH)
    val scale = fitScale * zoom
    val scaledW = worldW * scale
    val scaledH = worldH * scale
    var offsetX = viewW / 2f - playerX * scale
    var offsetY = viewH / 2f - playerY * scale
    offsetX = if (scaledW <= viewW) (viewW - scaledW) / 2f else offsetX.coerceIn(viewW - scaledW, 0f)
    offsetY = if (scaledH <= viewH) (viewH - scaledH) / 2f else offsetY.coerceIn(viewH - scaledH, 0f)
    return CameraTransform(offsetX, offsetY, scale)
}


