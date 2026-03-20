package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PoliceStars(
    searchLevel: Int,
    modifier: Modifier = Modifier
) {
    // Solo dibujamos si el nivel es mayor a 0, o puedes dejarlo fijo
    Row(
        modifier = modifier
            .padding(top = 24.dp), // Espacio desde el borde superior de la pantalla
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(5) { index ->
            // Si el índice es menor al nivel de búsqueda, la estrella se ilumina
            val isFilled = index < searchLevel
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Nivel de búsqueda",
                tint = if (isFilled) Color(0xFFFFD700) else Color.Gray.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(40.dp)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}