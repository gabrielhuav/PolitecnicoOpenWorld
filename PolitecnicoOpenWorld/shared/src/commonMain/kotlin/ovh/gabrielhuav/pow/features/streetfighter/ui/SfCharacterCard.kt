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
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfSharedSheets
import ovh.gabrielhuav.pow.platform.imagen.PowImagen


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
    /** Gama baja: la vista previa se decodifica reducida. Ver [previewSampleSize]. */
    lowEnd: Boolean = false,
) {
    val preview = rememberFighterPreview(
        id,
        animate = animate && (reveal || (!locked && !silhouette)),
        gamaBaja = lowEnd,
    )
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
/**
 * Vista previa del peleador, **sin construirla en el hilo de UI**.
 *
 * ⚠️ Antes todo el armado (cargar assets, decodificar el atlas, recortar cada celda a su bbox
 * opaco) vivía dentro de un `remember { }`, o sea DURANTE LA COMPOSICIÓN. En la rejilla del roster
 * eso son 18 peleadores contra una caché de 3 hojas: se reconstruía sin parar, y en gama baja se
 * notaba al abrir el selector y al hacer scroll. Ahora lo hace [SfPreviewCache] en segundo plano.
 *
 * Si la vista previa YA está en memoria se devuelve en la misma composición, sin pasar por `null`:
 * así al volver a subir en la lista la card no parpadea.
 */
@Composable
private fun rememberFighterPreview(
    id: SfFighterId,
    animate: Boolean,
    gamaBaja: Boolean,
): ImageBitmap? {
    // La card conserva el ultimo frame que realmente alcanzo a pintar. La cache LRU guarda solo
    // ocho variantes para un roster mayor, por lo que la variante estatica podia ser expulsada
    // justo al enfocar (estatica -> animada) y el relleno de cache de abajo volvia a ser null.
    var ultimoFrame by remember(id, gamaBaja) {
        mutableStateOf(
            SfPreviewCache.enMemoria(id, animate, gamaBaja)?.frames?.firstOrNull()
                ?: SfPreviewCache.enMemoria(id, !animate, gamaBaja)?.frames?.firstOrNull(),
        )
    }
    var animation by remember(id, animate) {
        mutableStateOf(SfPreviewCache.enMemoria(id, animate, gamaBaja))
    }
    LaunchedEffect(id, animate) {
        if (animation == null) {
            // `Dispatchers.Default` (no `IO`: no existe en Kotlin/Native). Mismo dispatcher que
            // usa StreetFighterScreen para cargar los atlas de la pelea.
            animation = withContext(Dispatchers.Default) {
                SfPreviewCache.cargar(id, animate, gamaBaja)
            }
        }
    }
    // ⚠️ RELLENO MIENTRAS CARGA — sin esto sale un "?" AL SELECCIONAR, que es lo que se veía.
    //
    // La caché lleva `animate` en la clave, así que enfocar una card (estático → animado) es un
    // fallo de caché y `animation` se va a `null` hasta que el hilo de fondo termina. Cuando la
    // vista previa se construía DURANTE la composición ese hueco no existía; desde que se hace
    // fuera del hilo de UI (que está bien: quitó un tirón real), el hueco se pinta.
    //
    // La OTRA variante del mismo peleador casi siempre está ya en memoria —es la que se estaba
    // viendo— y su primer cuadro es el mismo idle, así que sirve de relleno exacto. Si tampoco
    // está (primerísima pintada, aún nada en caché), se cae al "?" de siempre.
    val relleno = remember(id, animate, animation, gamaBaja) {
        if (animation != null) null
        else SfPreviewCache.enMemoria(id, !animate, gamaBaja)?.frames?.firstOrNull()
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
    val frameActual = animation?.frames?.getOrNull(frameIndex) ?: relleno
    LaunchedEffect(frameActual) {
        if (frameActual != null) ultimoFrame = frameActual
    }
    return frameActual ?: ultimoFrame
}
