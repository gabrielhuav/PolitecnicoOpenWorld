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
// 🩹 (examen QA) FIX accesibilidad: antes el rótulo iba en `Text` a 13.sp FIJO, sin `maxLines`
// y con alto FIJO de 44.dp. Documentado como PREEXISTENTE y "SIGUE ABIERTO a propósito" en
// `README for IAS/_SESION_ACTUAL.md` (sección "PowButton no se autoajusta"): con la fuente del
// sistema en grande, en el submenú de pelea (`SfMenuOverlays.kt`) el rótulo "COMBOS Y TUTORIAL"
// perdía "TUTORIAL" (caía a una 2ª línea que el alto fijo recortaba) y "MULTIJUGADOR" perdía la
// última letra (se recortaba a lo ancho). Mismo síntoma, misma causa y mismo arreglo que ya se
// aplicó a `MenuButton` y `FeaturedStreetFighterButton` en `MainMenuScreen.kt`: `BasicText` con
// `TextAutoSize.StepBased` + `softWrap = false` (sin esta bandera los rótulos CON ESPACIO, como
// "COMBOS Y TUTORIAL", no encogen: Compose parte en el espacio, mide la línea que ya cabe y el
// autoajuste nunca detecta desbordamiento). El alto fijo (44.dp) NO se tocó.
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
