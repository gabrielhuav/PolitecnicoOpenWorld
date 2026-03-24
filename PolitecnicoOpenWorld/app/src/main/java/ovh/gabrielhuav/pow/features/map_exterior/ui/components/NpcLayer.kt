package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import kotlin.math.roundToInt

@Composable
fun NpcLayer(
    npcs: List<Npc>,
    playerLocation: GeoPoint,
    zoomLevel: Double
) {
    val context = LocalContext.current

    // Factor de escala: Determina cuántos píxeles representan un grado geográfico.
    // Aumenta exponencialmente con el zoom para mantener coherencia.
    val scaleFactor = Math.pow(2.0, zoomLevel) * 256 / 360.0

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        npcs.forEach { npc ->
            // Calcular diferencia en coordenadas
            val deltaLat = npc.location.latitude - playerLocation.latitude
            val deltaLon = npc.location.longitude - playerLocation.longitude

            // Convertir diferencia geográfica a desplazamiento en píxeles (Eje Y invertido)
            val offsetX = (deltaLon * scaleFactor).roundToInt()
            val offsetY = (-deltaLat * scaleFactor).roundToInt()

            // Intentar obtener el recurso por nombre
            val resourceId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)

            if (resourceId != 0) {
                Image(
                    painter = painterResource(id = resourceId),
                    contentDescription = "NPC ${npc.type.name}",
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .size(if (npc.type == NpcType.CAR) 48.dp else 24.dp)
                        .rotate(npc.rotationAngle) // Gira el vehículo/persona
                )
            }
        }
    }
}