package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CampaignObjective

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
    Column(
        modifier = modifier
            .widthIn(max = 230.dp)
            .background(Color(0xCC101015), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("🎯 OBJETIVO", color = Color(0xFFFFCC80), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(objective.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (done) {
            Text("✅ Cumplido", color = Color(0xFF7CE38B), fontSize = 11.sp)
        } else {
            val distText = when {
                distM == null -> objective.description
                distM >= 1000 -> "A %.1f km".format(distM / 1000.0)
                else -> "A $distM m"
            }
            Text(distText, color = Color(0xFFB0BEC5), fontSize = 11.sp)
        }
    }
}
