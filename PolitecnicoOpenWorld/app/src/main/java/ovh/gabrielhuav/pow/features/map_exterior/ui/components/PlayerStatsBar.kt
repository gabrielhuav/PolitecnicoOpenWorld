package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.R
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha

@Composable
fun PlayerStatsBar(
    health: Float,
    hunger: Float,
    modifier: Modifier = Modifier
) {
    // 1. Animaciones de llenado/vaciado suave
    val animatedHealth by animateFloatAsState(
        targetValue = health,
        animationSpec = tween(durationMillis = 500),
        label = "healthAnimation"
    )
    val animatedHunger by animateFloatAsState(
        targetValue = hunger,
        animationSpec = tween(durationMillis = 500),
        label = "hungerAnimation"
    )

    // ================= NUEVA LÓGICA DE PELIGRO =================
    val isStarving = hunger <= 0.2f // Peligro si el hambre es 20% o menos

    // Creamos un bucle infinito para el latido
    val infiniteTransition = rememberInfiniteTransition(label = "starving_pulse")

    // Animamos la opacidad (alpha) para que el ícono de la gordita parpadee
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isStarving) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse // Va de 1f a 0.3f y de regreso
        ),
        label = "alpha_pulse"
    )

    // Animamos el color de la barra: de naranja normal a rojo parpadeante
    val dangerColor by infiniteTransition.animateColor(
        initialValue = Color(0xFFFFB74D),
        targetValue = if (isStarving) Color.Red else Color(0xFFFFB74D),
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "color_pulse"
    )
    // ===========================================================

    // Panel contenedor semitransparente
    Column(
        modifier = modifier
            .padding(5.dp)
            .fillMaxWidth(0.30f)
            .background(
                color = Color(0x99000000),
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = if (isStarving) Color(0x80FF0000) else Color(0x4DFFFFFF), // El borde también se pone rojizo
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        // ================= BARRA DE VIDA =================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Vida",
                tint = Color(0xFFFF4B4B),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedHealth)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF5252), Color(0xFFD50000))
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ================= BARRA DE HAMBRE =================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().alpha(pulseAlpha) // Aplicamos el parpadeo a toda la fila
        ) {
            // Tu ícono personalizado de la gordita
            Image(
                painter = painterResource(id = R.drawable.icono_hambre1),
                contentDescription = "Hambre",
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedHunger)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                // Usamos el color de peligro que calculamos arriba
                                colors = listOf(dangerColor, Color(0xFFFF6D00))
                            )
                        )
                )
            }
        }
    }
}