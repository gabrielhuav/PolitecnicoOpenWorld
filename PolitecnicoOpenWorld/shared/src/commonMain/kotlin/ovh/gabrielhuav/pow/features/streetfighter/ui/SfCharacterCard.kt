package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.platform.imagen.PowImagen

private const val PREVIEW_SAMPLE_SIZE = 4
private const val PREVIEW_SLOWDOWN = 1.8f
private const val PREVIEW_MIN_MS = 95L
private const val PREVIEW_SHARED_MS = 170L

private data class FighterPreviewAnimation(
    val frames: List<ImageBitmap>,
    val delaysMs: List<Long>,
)

private fun pixelateBitmap(src: ImageBitmap, targetW: Int): ImageBitmap {
    val width = targetW.coerceAtLeast(1)
    val height = (width.toFloat() * src.height / src.width).toInt().coerceAtLeast(1)
    return PowImagen.escalar(src, width, height)
}

@Composable
internal fun CharacterCard(
    id: SfFighterId,
    onSelect: (SfFighterId) -> Unit,
    locked: Boolean = false,
    /** true = animar idle+walk; false = un solo frame estático (default en gama baja). */
    animate: Boolean = false,
    selected: Boolean = false,
    highlightColor: Color? = null,
    silhouette: Boolean = false,
    reveal: Boolean = false,
) {
    val preview = rememberFighterPreview(id, animate = animate && (reveal || (!locked && !silhouette)))
    val obscure = (locked || silhouette) && !reveal
    val shown = if (obscure && preview != null) remember(preview) { pixelateBitmap(preview, 12) } else preview
    val shape = RoundedCornerShape(10.dp)
    val cardBg = highlightColor?.copy(alpha = 0.28f) ?: Color(0xFF23233A)
    val cardBorder = when {
        highlightColor != null -> highlightColor
        selected && !locked -> Color(0xFFD4AF37)
        else -> null
    }
    Column(
        modifier = Modifier
            .clip(shape)
            .background(cardBg)
            .then(if (cardBorder != null) Modifier.border(3.dp, cardBorder, shape) else Modifier)
            .clickable(enabled = !locked) { onSelect(id) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(86.dp), contentAlignment = Alignment.BottomCenter) {
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = if (locked) "???" else id.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    colorFilter = if (obscure) ColorFilter.tint(Color(0xFF15151F)) else null,
                )
            } else {
                Text("?", color = Color.White, fontSize = 40.sp)
            }
            if (locked) {
                Text(
                    text = "🔒",
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            if (id.isAlpha && !locked) {
                Text(
                    text = "ALPHA",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFB300))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (locked && !reveal) "???" else id.displayName,
            color = if (locked && !reveal) Color(0xFFFFD54A) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(90.dp),
        )
    }
}

/**
 * Preview del selector. Las hojas dedicadas se cargan reducidas 4× mediante la misma abstracción
 * multiplataforma del combate y todos los recortes ajustan sus coordenadas al mismo factor.
 */
@Composable
private fun rememberFighterPreview(id: SfFighterId, animate: Boolean): ImageBitmap? {
    val animation = remember(id, animate) {
        runCatching {
            val shared = id.sharedSet
            if (shared != null) {
                val raw = SfSharedSheets.previewFramesFor(shared)
                val frames = if (animate) {
                    raw.map { PowImagen.recortarAOpaco(it) }
                } else {
                    listOfNotNull(raw.firstOrNull()?.let { PowImagen.recortarAOpaco(it) })
                }
                FighterPreviewAnimation(frames, List(frames.size) { PREVIEW_SHARED_MS })
            } else {
                val data = SfFrameCatalog.load(id)
                val sheet = SfSharedSheets.sheetFor(id, sampleSize = PREVIEW_SAMPLE_SIZE)

                fun decodeState(
                    stateKey: String,
                    maxFrames: Int = Int.MAX_VALUE,
                ): Pair<List<ImageBitmap>, List<Long>> {
                    val steps = data.animations[stateKey].orEmpty().filter { it.delay > 0 }.take(maxFrames)
                    val frames = steps.mapNotNull { step ->
                        val src = data.frames[step.frameKey]?.src ?: return@mapNotNull null
                        val x = src[0] / PREVIEW_SAMPLE_SIZE
                        val y = src[1] / PREVIEW_SAMPLE_SIZE
                        val width = (src[2] / PREVIEW_SAMPLE_SIZE).coerceAtLeast(1)
                        val height = (src[3] / PREVIEW_SAMPLE_SIZE).coerceAtLeast(1)
                        PowImagen.recortarAOpaco(PowImagen.recortar(sheet, x, y, width, height))
                    }
                    val delays = steps.take(frames.size).map {
                        (it.delay * SfConstants.FRAME_TIME_MS * PREVIEW_SLOWDOWN).toLong()
                            .coerceAtLeast(PREVIEW_MIN_MS)
                    }
                    return frames to delays
                }

                if (!animate) {
                    val (idleFrames, idleDelays) =
                        decodeState(SfFighterState.IDLE.jsKey, maxFrames = 1)
                    FighterPreviewAnimation(
                        idleFrames,
                        idleDelays.ifEmpty { listOf(PREVIEW_MIN_MS) },
                    )
                } else {
                    val (idleFrames, idleDelays) = decodeState(SfFighterState.IDLE.jsKey)
                    val (walkFrames, walkDelays) = decodeState(SfFighterState.WALK_FORWARD.jsKey)
                    FighterPreviewAnimation(
                        idleFrames + walkFrames,
                        idleDelays + walkDelays,
                    )
                }
            }
        }.getOrNull()?.takeIf { it.frames.isNotEmpty() }
    }
    var frameIndex by remember(id, animation, animate) { mutableIntStateOf(0) }
    LaunchedEffect(id, animation, animate) {
        frameIndex = 0
        val anim = animation ?: return@LaunchedEffect
        if (!animate || anim.frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(anim.delaysMs.getOrElse(frameIndex) { 100L })
            frameIndex = (frameIndex + 1) % anim.frames.size
        }
    }
    return animation?.frames?.getOrNull(frameIndex)
}
