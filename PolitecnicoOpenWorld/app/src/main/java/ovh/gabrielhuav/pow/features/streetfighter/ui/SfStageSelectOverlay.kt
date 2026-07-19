package ovh.gabrielhuav.pow.features.streetfighter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PowButton
import ovh.gabrielhuav.pow.features.streetfighter.data.SfTheme

// ---------------------------------------------------------------------------
// Selector de MAPA (pre-pelea) — LÓGICA SEPARADA del draw loop del combate.
//
//  - Cada tarjeta: miniatura ESTÁTICA (`*_thumb.png`, ~256 px). NUNCA el atlas completo.
//  - Solo el mapa FOCUSED (tocado) se previsualiza animado si es `_anim.webp`:
//      se carga UN atlas submuestreado y se pinta UN frame a la vez (sub-rect),
//      no se muestra el filmstrip/spreadsheet entero.
//  - Confirmación explícita: tocar = previsualizar; "Elegir este mapa" = onSelect.
//  - "Al azar" = focus especial → onSelect(null).
// ---------------------------------------------------------------------------

/** Focus del carrusel: mapa concreto o "al azar". */
private sealed class StageFocus {
    data class Map(val file: String) : StageFocus()
    data object Random : StageFocus()
}

/**
 * Metadatos + atlas LIGERO del preview animado (solo el mapa seleccionado).
 * El atlas se submuestrea (inSampleSize) para caber en RAM de gama baja;
 * la UI recorta UN frame del grid, nunca dibuja el spreadsheet completo.
 */
private data class StageAnimPreview(
    val atlas: ImageBitmap,
    val frameW: Int,
    val frameH: Int,
    val cols: Int,
    val rows: Int,
    val frameCount: Int,
    val fps: Float,
)

/**
 * Overlay de elección de escenario.
 * @param onSelect null = al azar; String = archivo del fondo (p. ej. fondo_escom_anim.webp)
 * @param unlockedMaps null = todos; si no, los que no estén salen con 🔒
 */
@Composable
fun SfStageSelectOverlay(
    theme: SfTheme,
    onSelect: (String?) -> Unit,
    onBack: (() -> Unit)?,
    unlockedMaps: Set<String>? = null,
    /** Gama baja: no decodifica atlas de preview (solo thumbs estáticas). */
    lowEnd: Boolean = false,
) {
    val context = LocalContext.current
    var focus by remember { mutableStateOf<StageFocus?>(null) }

    // Solo el focused anima (y solo si es _anim y NO lowEnd). Se descarga al cambiar de focus.
    val focusedFile = (focus as? StageFocus.Map)?.file
    val animPreview = remember(focusedFile, lowEnd) {
        if (!lowEnd && focusedFile != null && focusedFile.endsWith("_anim.webp")) {
            loadStageAnimPreview(context, theme.imagesDir, focusedFile)
        } else {
            null
        }
    }
    DisposableEffect(animPreview) {
        onDispose { /* ImageBitmap GC; no hay recycle de ImageBitmap de Compose */ }
    }

    val powMenuBg = remember { Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))) }
    Box(
        modifier = Modifier.fillMaxSize().background(powMenuBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.sf_choose_stage),
                color = Color(0xFFD4AF37),
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sf_stage_tap_preview),
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                theme.fullBackgrounds.forEach { bg ->
                    val locked = unlockedMaps != null && bg.file !in unlockedMaps
                    val selected = focus is StageFocus.Map && (focus as StageFocus.Map).file == bg.file
                    // Estática SIEMPRE (thumb dedicada); la animación se superpone solo si selected
                    val staticThumb = remember(bg.file) {
                        loadStageStaticThumb(context, theme.imagesDir, bg.file)
                    }
                    StageCard(
                        name = bg.name,
                        staticThumb = staticThumb,
                        animPreview = if (selected) animPreview else null,
                        selected = selected,
                        locked = locked,
                        emoji = null,
                        onClick = {
                            if (!locked) focus = StageFocus.Map(bg.file)
                        },
                    )
                }
                val randomSelected = focus is StageFocus.Random
                StageCard(
                    name = stringResource(R.string.sf_random),
                    staticThumb = null,
                    animPreview = null,
                    selected = randomSelected,
                    locked = false,
                    emoji = "🎲",
                    onClick = { focus = StageFocus.Random },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            // Confirmar solo con focus válido
            PowButton(
                text = stringResource(R.string.sf_stage_confirm),
                onClick = {
                    when (val f = focus) {
                        is StageFocus.Map -> onSelect(f.file)
                        is StageFocus.Random -> onSelect(null)
                        null -> Unit
                    }
                },
                enabled = focus != null,
                color = Color(0xFFB8143A),
                modifier = Modifier.fillMaxWidth(0.55f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.sf_change_fighter), color = Color(0xFFD4AF37))
                }
            }
        }
    }
}

// ---- Alias de compatibilidad con el nombre anterior en StreetFighterScreen ----
@Composable
fun StageSelectOverlay(
    theme: SfTheme,
    onSelect: (String?) -> Unit,
    onBack: (() -> Unit)?,
    unlockedMaps: Set<String>? = null,
    lowEnd: Boolean = false,
) = SfStageSelectOverlay(theme, onSelect, onBack, unlockedMaps, lowEnd)

// ---------------------------------------------------------------------------
// Tarjeta de escenario
// ---------------------------------------------------------------------------

@Composable
private fun StageCard(
    name: String,
    staticThumb: ImageBitmap?,
    animPreview: StageAnimPreview?,
    selected: Boolean,
    locked: Boolean,
    emoji: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF23233A))
            .then(
                if (selected) Modifier.border(2.dp, Color(0xFFD4AF37), shape)
                else Modifier,
            )
            .clickable(enabled = !locked) { onClick() }
            .padding(8.dp)
            .alpha(if (locked) 0.45f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF11111C)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                // Preview animado: UN frame del atlas a la vez (no el filmstrip entero)
                animPreview != null -> StageAnimFrameView(preview = animPreview)
                staticThumb != null -> Image(
                    bitmap = staticThumb,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                emoji != null -> Text(emoji, fontSize = 30.sp)
                else -> Text("?", color = Color.White, fontSize = 24.sp)
            }
            if (locked) {
                Text(
                    text = "🔒",
                    fontSize = 24.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp),
                )
            }
            if (selected) {
                // Halo interior sutil
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            color = if (selected) Color(0xFFD4AF37) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(132.dp),
        )
    }
}

/**
 * Pinta UN frame del atlas (sub-rect) y avanza el índice a `fps`.
 * El ping-pong ya viene embebido en el atlas → loop simple 0→N-1.
 */
@Composable
private fun StageAnimFrameView(preview: StageAnimPreview) {
    var frameIdx by remember(preview) { mutableIntStateOf(0) }
    LaunchedEffect(preview) {
        val frameMs = (1000f / preview.fps.coerceAtLeast(1f)).toLong().coerceAtLeast(16L)
        while (true) {
            delay(frameMs)
            frameIdx = (frameIdx + 1) % preview.frameCount.coerceAtLeast(1)
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val idx = frameIdx.coerceIn(0, (preview.frameCount - 1).coerceAtLeast(0))
        val col = idx % preview.cols
        val row = idx / preview.cols
        // Solo el sub-rect del frame actual — NUNCA el spreadsheet completo
        drawImage(
            image = preview.atlas,
            srcOffset = IntOffset(col * preview.frameW, row * preview.frameH),
            srcSize = IntSize(preview.frameW, preview.frameH),
            dstOffset = IntOffset(0, 0),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}

// ---------------------------------------------------------------------------
// Carga de assets (solo UI del selector; el combate usa loadStageBackground)
// ---------------------------------------------------------------------------

/** Miniatura estática ~256 px (`*_thumb.png`); fallback submuestreado del WebP. */
private fun loadStageStaticThumb(context: Context, imagesDir: String, file: String): ImageBitmap? {
    return runCatching {
        val thumbName = file.substringBeforeLast('.') + "_thumb.png"
        val fromThumb = runCatching {
            context.assets.open(imagesDir + thumbName).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (fromThumb != null) return@runCatching fromThumb.asImageBitmap()
        // Fallback: no decodificar a full res (selector de 48 mapas)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 8
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        context.assets.open(imagesDir + file).use {
            BitmapFactory.decodeStream(it, null, opts)
        }?.asImageBitmap()
    }.getOrNull()
}

/**
 * Carga el atlas animado SOLO para el mapa focused, submuestreado (inSampleSize=4
 * → ~480×473, ~0.5 MB RGB_565) y metadatos del JSON hermano.
 * Devuelve null si no es anim o falla la carga.
 */
private fun loadStageAnimPreview(
    context: Context,
    imagesDir: String,
    file: String,
): StageAnimPreview? {
    if (!file.endsWith("_anim.webp")) return null
    return runCatching {
        val jsonName = file.substringBeforeLast('.') + ".json"
        val metaText = context.assets.open(imagesDir + jsonName).use {
            it.readBytes().decodeToString()
        }
        val o = JSONObject(metaText)
        val frameW = o.getInt("frameWidth")
        val frameH = o.getInt("frameHeight")
        val cols = o.getInt("cols")
        val rows = o.getInt("rows")
        val frameCount = o.getInt("frameCount")
        val fps = o.getDouble("fps").toFloat()
        // Submuestreo: 480→120 por celda; atlas ~480×473 — cabe en gama baja
        val opts = BitmapFactory.Options().apply {
            inSampleSize = 4
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = context.assets.open(imagesDir + file).use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        // Tras sample, el tamaño de celda real = atlas/cols (más fiable que frameW/sample)
        val cellW = (bmp.width / cols).coerceAtLeast(1)
        val cellH = (bmp.height / rows).coerceAtLeast(1)
        StageAnimPreview(
            atlas = bmp.asImageBitmap(),
            frameW = cellW,
            frameH = cellH,
            cols = cols,
            rows = rows,
            frameCount = frameCount.coerceAtLeast(1),
            fps = fps.coerceAtLeast(1f),
        )
    }.getOrNull()
}
