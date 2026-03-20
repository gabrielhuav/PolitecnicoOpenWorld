package ovh.gabrielhuav.pow.domain

import org.osmdroid.util.GeoPoint

data class NpcModel(
    val id: String,
    val position: GeoPoint,
    val type: NpcType = NpcType.CAR,
    val rotation: Float = 0f,
    val spriteType: Int = 1 //1 para el auto original, 2 para el nuevo
)

enum class NpcType { CAR, PEDESTRIAN }