package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

data class Waypoint(
    val id: Long = 0,
    val name: String,
    val location: GeoPoint,
    val createdAt: Long = System.currentTimeMillis()
)
