package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint
import java.util.UUID

data class Npc(
    val id: String = UUID.randomUUID().toString(),
    val type: NpcType,
    var location: GeoPoint,
    var rotationAngle: Float = 0f,
    val speed: Double,
    val currentWay: MapWay? = null,
    var targetNodeIndex: Int = 0,
    var moveDirection: Int = 1,
    // NUEVO: Propiedad para el color aleatorio del coche
    val carColor: Int = android.graphics.Color.WHITE
)