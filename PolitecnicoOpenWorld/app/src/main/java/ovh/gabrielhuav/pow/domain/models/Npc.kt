package ovh.gabrielhuav.pow.domain.models

import org.osmdroid.util.GeoPoint

// Clase agnóstica para no depender de GeoPoint (OSM) o LatLng (Google) en el ViewModel
data class MapLocation(val latitude: Double, val longitude: Double)

data class Npc(
    val id: String,
    val name: String,
    val currentLocation: GeoPoint,
    val path: List<GeoPoint> = emptyList(),
    val currentPathIndex: Int = 0,
    val speed: Double = 0.000008, // Velocidad de caminata
    val isPlanningRoute: Boolean = false // <-- NUEVO: Evita spam de peticiones
)