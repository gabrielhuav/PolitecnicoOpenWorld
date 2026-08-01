package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.platform.imagen.PowImagen

/**
 * 🆕 (2026-07-21) TEXTO con la FUENTE ARCADE del modo pelea (la del HUD, `sf_hud_pow.webp`)
 * usable FUERA del Canvas de combate.
 *
 * El dueño pidió que los coleccionables de PELEADOR lleven "las mismas letras del SF". Esa
 * fuente no es un `Typeface`: es un ATLAS de recortes por carácter (`SfTheme.letterFont`),
 * así que no se puede pasar a un `Text` normal — hay que pintarla glifo a glifo.
 *
 * ⚠️ Solo existen glifos A-Z, 0-9 y espacio: el texto se sanea antes (igual que los
 * subtítulos del HUD) y lo que no se puede pintar se descarta.
 */

/** Deja solo lo que la fuente sabe pintar (A-Z, 0-9, espacio), sin acentos ni signos. */
fun sfFontSanitize(text: String): String = text.uppercase()
    .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
    .replace('Ó', 'O').replace('Ú', 'U').replace('Ü', 'U').replace('Ñ', 'N')
    .replace(Regex("[^A-Z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

@Composable
fun SfBitmapText(
    text: String,
    modifier: Modifier = Modifier,
    /** Alto del glifo; el avance por carácter es 12/16 de ese alto (como en el HUD). */
    glyphHeight: Dp = 14.dp,
) {
    val theme = remember { SF_CLASSIC_THEME }
    // El atlas del HUD es pequeño y se cachea por composición: barato incluso en gama baja.
    // 🍏 `PowImagen.deAsset` en vez de `context.assets` + `BitmapFactory`: ya no hace falta el
    // `LocalContext`, que era lo único que ataba este Composable a Android.
    val hud: ImageBitmap? = remember {
        runCatching { PowImagen.deAsset(theme.imagesDir + theme.hudImage) }.getOrNull()
    }
    val clean = remember(text) { sfFontSanitize(text) }
    if (hud == null || clean.isEmpty()) return

    val advance = glyphHeight * (12f / 16f)
    Canvas(modifier = modifier.size(width = advance * clean.length, height = glyphHeight)) {
        drawSfText(theme.letterFont, hud, clean, size.height)
    }
}

/** Pinta la cadena glifo a glifo desde el atlas del HUD. */
private fun DrawScope.drawSfText(
    letterFont: Map<Char, List<Int>>,
    hud: ImageBitmap,
    text: String,
    glyphPx: Float,
) {
    var cx = 0f
    val advance = glyphPx * (12f / 16f)
    text.forEach { ch ->
        letterFont[ch]?.let { src ->
            val scale = glyphPx / src[3].toFloat()
            drawImage(
                image = hud,
                srcOffset = IntOffset(src[0], src[1]),
                srcSize = IntSize(src[2], src[3]),
                dstOffset = IntOffset(cx.toInt(), 0),
                dstSize = IntSize((src[2] * scale).toInt(), glyphPx.toInt()),
                filterQuality = FilterQuality.None,
            )
        }
        cx += advance
    }
}
