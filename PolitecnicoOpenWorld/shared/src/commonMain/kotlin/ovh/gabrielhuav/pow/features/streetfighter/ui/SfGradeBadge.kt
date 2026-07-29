package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.sf_grade_label

/** Insignia de la nota del combate (E..MS) estilo SF III. */
@Composable
fun SfGradeBadge(grade: String) {
    val color = when (grade) {
        "MS", "S" -> Color(0xFFFFD24A)
        "A", "B" -> Color(0xFF7CFF7C)
        "C" -> Color(0xFFD0D0D0)
        else -> Color(0xFFE08A7A)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.sf_grade_label),
            color = Color(0xFFD4AF37),
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = grade,
            color = color,
            fontSize = 46.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
    }
}
