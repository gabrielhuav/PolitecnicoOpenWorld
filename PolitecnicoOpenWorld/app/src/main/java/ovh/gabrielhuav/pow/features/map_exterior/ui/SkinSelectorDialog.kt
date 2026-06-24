// ════════════════════════════════════════════════════════════════════════════
// FRAGMENTOS PARA AGREGAR A WorldMapScreen.kt
// ════════════════════════════════════════════════════════════════════════════
// Instrucciones:
//   1. Copia SkinSelectorDialog al final del archivo (fuera de WorldMapScreen).
//   2. En WorldMapScreen, agrega el botón de skin en la Column de botones top-right.
//   3. Agrega la invocación del diálogo justo antes del cierre del Box principal.
// ════════════════════════════════════════════════════════════════════════════

// ── 1. IMPORT NUEVO (agrega a la sección de imports) ─────────────────────
// import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
// (ya que PlayerSkin está en ese package, debería resolverse automáticamente)

// ── 2. BOTÓN EN LA Column(Alignment.TopEnd) ──────────────────────────────
//    Agrega esto ANTES del botón de Ajustes (el primero de la columna):
//
//    IconButton(
//        onClick = { viewModel.toggleSkinSelector(true) },
//        modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape)
//    ) {
//        Icon(Icons.Default.Person, "Cambiar skin", tint = Color(0xFFD91B5B))
//    }

// ── 3. INVOCACIÓN DEL DIÁLOGO ─────────────────────────────────────────────
//    Agrega esto justo antes de la llave de cierre del Box principal,
//    junto a los otros diálogos (showTeleportMenu, showAssetPicker, etc.):
//
//    if (uiState.showSkinSelector) {
//        SkinSelectorDialog(
//            currentSkin = uiState.selectedSkin,
//            context     = context,
//            onSkinSelected = { viewModel.selectSkin(it) },
//            onDismiss   = { viewModel.toggleSkinSelector(false) }
//        )
//    }

// ════════════════════════════════════════════════════════════════════════════
// COMPOSABLE — pegar al final del archivo WorldMapScreen.kt
// ════════════════════════════════════════════════════════════════════════════

package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

@Composable
fun SkinSelectorDialog(
    currentSkin: PlayerSkin,
    context: Context,
    onSkinSelected: (PlayerSkin) -> Unit,
    onDismiss: () -> Unit,
    // LÁZARO solo se muestra en Modo Desarrollador.
    developerMode: Boolean = false
) {
    // Skins SOLO de desarrollador (test): Lázaro + los personajes nuevos (NPC). Ocultas salvo en Modo Dev.
    val devOnlySkins = setOf(
        PlayerSkin.LAZARO, PlayerSkin.SENOR_TIENDA, PlayerSkin.REY_BROMAS,
        PlayerSkin.PEPE_REY, PlayerSkin.PRANKEDY
    )
    val selectableSkins = remember(developerMode) {
        PlayerSkin.entries.filter { it !in devOnlySkins || developerMode }
    }
    // Pre-carga miniaturas (idle frame 1 de cada skin)
    val previews = remember { mutableStateMapOf<PlayerSkin, ImageBitmap?>() }

    LaunchedEffect(Unit) {
        selectableSkins.forEach { skin ->
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    context.assets.open(skin.idlePath(1)).use {
                        // Recorta al contenido OPACO para que la miniatura LLENE el recuadro por igual:
                        // antes las skins con mucho margen transparente (Lázaro/escomboy) se veían chiquitas.
                        BitmapFactory.decodeStream(it)?.let { bmp -> trimToOpaque(bmp).asImageBitmap() }
                    }
                } catch (e: Exception) {
                    null
                }
            }
            previews[skin] = bitmap
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_choose_character),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_select_skin),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(selectableSkins) { skin ->
                        SkinCard(
                            skin       = skin,
                            preview    = previews[skin],
                            isSelected = skin == currentSkin,
                            onClick    = { onSkinSelected(skin) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_close)) }
        }
    )
}

@Composable
private fun SkinCard(
    skin: PlayerSkin,
    preview: ImageBitmap?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFD91B5B) else Color.Transparent
    val bgColor     = if (isSelected) Color(0xFFD91B5B).copy(alpha = 0.08f)
                      else            MaterialTheme.colorScheme.surfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = skin.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder mientras carga
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = skin.displayName,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFFD91B5B)
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )

        if (isSelected) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Color(0xFFD91B5B), androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

/**
 * Recorta el bitmap a su CONTENIDO OPACO (bbox alpha) para que TODAS las miniaturas del selector
 * llenen el recuadro por igual. Antes, las skins con mucho margen transparente (Lázaro/escomboy) se
 * veían chiquitas dentro del cuadro. Lee los píxeles una vez (getPixels) y recorta con un margen mínimo.
 */
private fun trimToOpaque(src: android.graphics.Bitmap): android.graphics.Bitmap {
    val w = src.width; val h = src.height
    if (w <= 0 || h <= 0) return src
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    var top = h; var bottom = -1; var left = w; var right = -1
    var i = 0
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((px[i] ushr 24) and 0xFF > 16) {
                if (y < top) top = y
                if (y > bottom) bottom = y
                if (x < left) left = x
                if (x > right) right = x
            }
            i++
        }
    }
    if (right < left || bottom < top) return src
    val pad = ((bottom - top) * 0.04f).toInt()
    val l = (left - pad).coerceAtLeast(0)
    val t = (top - pad).coerceAtLeast(0)
    val r = (right + pad).coerceAtMost(w - 1)
    val b = (bottom + pad).coerceAtMost(h - 1)
    return try {
        android.graphics.Bitmap.createBitmap(src, l, t, (r - l + 1), (b - t + 1))
    } catch (e: Exception) { src }
}
