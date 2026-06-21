package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.domain.models.map.Npc

@Composable
fun NpcRenderWrapper(
    npc: Npc,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 🌟 ANIMACIÓN DE DIFUMINADO: Si isDying es true se va a 0f, si no, se mantiene opaco en 1f
    val alphaFade by animateFloatAsState(
        targetValue = if (npc.isDying) 0f else 1f,
        animationSpec = tween(durationMillis = 1000) // Duración idéntica al delay del ViewModel
    )

    // 🌟 SPAWN SUAVE: al aparecer un NPC (cada id es único, este wrapper se compone
    // de cero), difuminamos de 0→1 para que no "brote" de golpe. El estado vive por
    // NPC porque la lista se renderiza con key(npc.id).
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val spawnAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 700)
    )

    // Color dinámico de la micro-barra del NPC (Verde -> Amarillo -> Rojo)
    val barColor = when {
        npc.health > 60f -> Color(0xFF4CAF50)
        npc.health > 30f -> Color(0xFFFFEB3B)
        else -> Color(0xFFF44336)
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier.graphicsLayer {
            // Combina el fade-out de muerte con el fade-in de aparición.
            alpha = alphaFade * spawnAlpha
        }
    ) {
        // --- 📊 MICRO BARRA DE VIDA DISCRETA ---
        // Regla: Solo aparece si el NPC ha sido dañado (vida < 100) y sigue respirando (!isDying)
        if (npc.health < 100f && !npc.isDying) {
            Box(
                modifier = Modifier
                    .offset(y = (-8).dp) // Flota un poco más abajo que la de Lázaro para que sea discreta
                    .width(30.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                LinearProgressIndicator(
                    progress = npc.health / 100f,
                    modifier = Modifier.fillMaxSize(),
                    color = barColor,
                    trackColor = Color.Transparent
                )
            }
        }

        // --- RENDERIZADO DEL SPRITE ORIGINAL ---
        // Aquí adentro va tu lógica actual (el Image que renderiza al peatón o al auto teñido)
        content()
    }
}