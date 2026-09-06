package ovh.gabrielhuav.pow.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Botón con el ESTILO POW (mismo lenguaje visual que MenuButton del menú principal:
// esquinas cortadas topStart/bottomEnd + vino #6B1C3A + texto bold espaciado), tamaño
// compacto. COMPARTIDO entre modos (nació en el modo pelea "TITULACIÓN POR COMBATE"; se movió
// aquí para que cualquier feature lo use — pendiente 4 de AUDIT_SF_MULTIPLAYER.md).

@Composable
fun PowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color(0xFF6B1C3A),
) {
    val shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A1C21),
            disabledContentColor = Color.Gray,
        ),
        modifier = modifier.height(44.dp),
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp) }
}
