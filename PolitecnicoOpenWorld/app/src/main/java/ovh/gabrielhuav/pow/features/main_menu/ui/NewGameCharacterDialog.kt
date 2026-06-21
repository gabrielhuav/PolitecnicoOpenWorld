package ovh.gabrielhuav.pow.features.main_menu.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

// Opción del selector de personaje de PARTIDA NUEVA: skin + etiqueta de rol mostrada al jugador.
private data class CharacterOption(val skin: PlayerSkin, val roleLabel: String)

/**
 * Diálogo de ELECCIÓN DE PERSONAJE al iniciar una partida nueva. Muestra primero (antes del
 * selector de slot/intro) las opciones jugables: Hombre (ESCOM BOY), Mujer (ESCOM GIRL) y
 * No binario (ROBOT). LÁZARO solo aparece si el Modo Desarrollador está activado (`includeLazaro`).
 * Al elegir, `onPick(skin)` fija la skin y continúa el flujo de partida nueva.
 */
@Composable
fun NewGameCharacterDialog(
    context: Context,
    includeLazaro: Boolean,
    onPick: (PlayerSkin) -> Unit,
    onDismiss: () -> Unit
) {
    val options = remember(includeLazaro) {
        buildList {
            add(CharacterOption(PlayerSkin.escomboy, "Hombre"))
            add(CharacterOption(PlayerSkin.escomgirl, "Mujer"))
            add(CharacterOption(PlayerSkin.robot, "No binario"))
            // LÁZARO: solo disponible en Modo Desarrollador.
            if (includeLazaro) add(CharacterOption(PlayerSkin.LAZARO, "Lázaro (Dev)"))
        }
    }

    // Pre-carga las miniaturas (idle frame 1 de cada personaje).
    val previews = remember { mutableStateMapOf<PlayerSkin, ImageBitmap?>() }
    LaunchedEffect(options) {
        options.forEach { opt ->
            val bmp = withContext(Dispatchers.IO) {
                try {
                    context.assets.open(opt.skin.idlePath(1))
                        .use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                } catch (e: Exception) {
                    null
                }
            }
            previews[opt.skin] = bmp
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Elige tu personaje", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Selecciona con quién quieres jugar la campaña.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(options) { opt ->
                        CharacterCard(
                            opt = opt,
                            preview = previews[opt.skin],
                            onClick = { onPick(opt.skin) }
                        )
                    }
                }
            }
        },
        // Sin botón de confirmar: elegir un personaje YA continúa el flujo.
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CharacterCard(
    opt: CharacterOption,
    preview: ImageBitmap?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = opt.roleLabel,
                    modifier = Modifier
                        .fillMaxSize()
                        // Normaliza el tamaño VISIBLE del personaje entre skins: escomboy ocupa solo
                        // ~41% de su lienzo (256²) y se veía más pequeño que las otras dos. Escala
                        // según su fracción opaca para que las 3 se vean del mismo tamaño.
                        .graphicsLayer {
                            val s = (0.85f / opt.skin.walkBodyFraction).coerceIn(0.7f, 2.3f)
                            scaleX = s; scaleY = s
                        }
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = opt.roleLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD91B5B),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = opt.skin.displayName,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
