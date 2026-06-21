package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignObjective

// ─── WIDGET DE OBJETIVOS (Modo Historia) ──────────────────────────────────────
// HUD pequeño SIEMPRE visible mientras haya un objetivo de campaña activo. Muestra el
// título del objetivo y la distancia al destino; al cumplirse, lo indica.
@Composable
fun ObjectivesWidget(
    objective: CampaignObjective,
    done: Boolean,
    playerLocation: GeoPoint?,
    modifier: Modifier = Modifier
) {
    val distM: Int? = playerLocation?.let { loc ->
        val dLat = (loc.latitude - objective.targetLat) * 111_320.0
        val dLon = (loc.longitude - objective.targetLon) * 111_320.0 *
            kotlin.math.cos(Math.toRadians(objective.targetLat))
        kotlin.math.sqrt(dLat * dLat + dLon * dLon).toInt()
    }
    // Difuminado: fondo translúcido + alpha global, y texto CENTRADO, para que NO choque
    // visualmente con los widgets de las esquinas (vida, estrellas, caché…). El caller lo
    // ancla arriba-centro.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .alpha(0.88f)
            .widthIn(max = 250.dp)
            .background(Color(0x99101015), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(stringResource(R.string.wm_objective_label), color = Color(0xFFFFCC80), fontSize = 10.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(objective.title, color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (done) {
            Text(stringResource(R.string.wm_objective_done), color = Color(0xFF7CE38B), fontSize = 11.sp, textAlign = TextAlign.Center)
        } else {
            val distText = when {
                distM == null -> objective.description
                distM >= 1000 -> stringResource(R.string.wm_dist_km, String.format("%.1f", distM / 1000.0))
                else -> stringResource(R.string.wm_dist_m, distM)
            }
            Text(distText, color = Color(0xFFB0BEC5), fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}
