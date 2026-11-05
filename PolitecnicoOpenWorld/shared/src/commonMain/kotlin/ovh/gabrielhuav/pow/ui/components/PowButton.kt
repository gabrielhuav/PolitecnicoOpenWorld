package ovh.gabrielhuav.pow.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Botón con el ESTILO POW (mismo lenguaje visual que MenuButton del menú principal:
// esquinas cortadas topStart/bottomEnd + vino #6B1C3A + texto bold espaciado), tamaño
// compacto. COMPARTIDO entre modos (nació en el modo pelea "TITULACIÓN POR COMBATE"; se movió
// aquí para que cualquier feature lo use — pendiente 4 de AUDIT_SF_MULTIPLAYER.md).
//
// 🛠️ FIX (accesibilidad / recorte de texto): con fuente del sistema grande (accesibilidad)
// o etiquetas largas en pantallas angostas, el Text() de ancho fijo recortaba el label
// (ej. "COMBOS & TUTORIAL"). Se reemplaza por BasicText + TextAutoSize.StepBased para que
// el texto reduzca su tamaño automáticamente hasta caber en una sola línea, en vez de cortarse.
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
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = LocalContentColor.current,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = 13.sp,
                stepSize = 0.5.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
