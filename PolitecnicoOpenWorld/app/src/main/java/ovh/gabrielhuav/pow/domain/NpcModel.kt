package ovh.gabrielhuav.pow.domain

import org.osmdroid.util.GeoPoint

data class NpcModel(
    val id: String,
    val position: GeoPoint,
    val type: NpcType = NpcType.CAR
)

enum class NpcType { CAR, PEDESTRIAN }