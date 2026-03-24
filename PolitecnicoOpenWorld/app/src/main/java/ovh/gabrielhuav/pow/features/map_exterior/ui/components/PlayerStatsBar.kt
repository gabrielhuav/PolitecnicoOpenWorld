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

@Composable
fun PlayerStatsBar(
    health: Float,
    hunger: Float,
    modifier: Modifier = Modifier
) {
    // 1. Animaciones para que la barra se mueva suavemente (dura medio segundo)
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

    // 2. Panel contenedor semitransparente
    Column(
        modifier = modifier
            .padding(10.dp)
            .fillMaxWidth(0.25f) // Un poco más ancho para acomodar los íconos
            .background(
                color = Color(0x99000000), // Negro al 60% de opacidad
                shape = RoundedCornerShape(12.dp)
            )
            .border(
                width = 1.dp,
                color = Color(0x4DFFFFFF), // Borde blanco muy sutil
                shape = RoundedCornerShape(12.dp)
            )
            .padding(10.dp) // Espaciado interno del panel
    ) {
        // ================= BARRA DE VIDA =================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Vida",
                tint = Color(0xFFFF4B4B), // Rojo vivo
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Barra personalizada con gradiente
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)) // Color de fondo de la barra vacía
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.icono_hambre1),
                contentDescription = "Hambre",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Barra personalizada con gradiente
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF333333)) // Color de fondo de la barra vacía
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedHunger)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFFB74D), Color(0xFFFF6D00))
                            )
                        )
                )
            }
        }
    }
}