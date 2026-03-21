package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

data class CarNpc(
    val id: String,
    val name: String,
    val currentLocation: GeoPoint,
    val path: List<GeoPoint> = emptyList(),
    val currentPathIndex: Int = 0,
    val speed: Double = 0.00006, // <-- Más rápido que los NPCs peatonales (0.000003)
    val isPlanningRoute: Boolean = false
)