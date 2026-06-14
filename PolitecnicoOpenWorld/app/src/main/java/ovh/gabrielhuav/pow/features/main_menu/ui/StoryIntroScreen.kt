package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.domain.models.CampaignSchool
import ovh.gabrielhuav.pow.domain.models.StoryComicCatalog

/**
 * Intro del Modo Historia como CÓMIC: muestra los paneles (imágenes de assets/story/) que
 * narran el prólogo. El texto de la historia se dibuja sobre el RECUADRO BLANCO inferior de
 * cada imagen. Navegas tocando la mitad derecha (siguiente) / izquierda (anterior); en el
 * último panel, tocar → INICIAR (MainActivity guarda la partida y arranca el mundo en ESCOM).
 * Si una imagen aún no existe en assets, se muestra un panel oscuro con el texto (no crashea).
 */
@Composable
fun StoryIntroScreen(
    school: CampaignSchool,
    onBegin: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val panels = remember(school.id) { StoryComicCatalog.forSchool(school.id) }
    var index by remember { mutableIntStateOf(0) }

    // Sin paneles configurados: arranca directo (evita pantalla vacía).
    if (panels.isEmpty()) {
        onBegin()
        return
    }

    val panel = panels[index]
    val isLast = index >= panels.size - 1

    // Carga la imagen del panel desde assets (con fallback si falta).
    val image = remember(panel.assetPath) {
        try {
            context.assets.open(panel.assetPath).use {
                android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {
        // Imagen del cómic (rellena la altura; el recuadro blanco queda abajo).
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback: panel oscuro para que el texto siga siendo legible sin la imagen.
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
                )
            )
        }

        // Navegación por toque: mitad izquierda = anterior, mitad derecha = siguiente/INICIAR.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { if (index > 0) index-- }
            )
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { if (isLast) onBegin() else index++ }
            )
        }

        // Texto de la historia, sobre el recuadro blanco inferior de la imagen.
        // Color OSCURO (el recuadro es blanco). Si no hay imagen, el fondo oscuro del
        // fallback lo cubre un panel translúcido claro para mantener el contraste.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp),
            contentAlignment = Alignment.Center
        ) {
            val onLight = image != null
            Box(
                modifier = if (onLight) Modifier.fillMaxWidth()
                else Modifier.fillMaxWidth().background(Color(0xF2FFFFFF), RoundedCornerShape(10.dp)).padding(12.dp)
            ) {
                Text(
                    text = panel.text,
                    color = Color(0xFF14141A),
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Barra superior: VOLVER + contador + SALTAR.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton("‹ Volver") { onBack() }
            Text(
                "${index + 1}/${panels.size}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x88000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            PillButton(if (isLast) "Iniciar ▶" else "Saltar »") { onBegin() }
        }

        // Pista de navegación abajo.
        Text(
            text = if (isLast) "Toca la pantalla para INICIAR" else "Toca a la derecha para continuar",
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(Color(0xAA4A1226), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
