package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = 18.0,
    // variabels de policias
    val searchLevel: Int = 0, // 0 a 5 estrellas
    val policeNPCs: List<PoliceNPC> = emptyList(),
    val isInVehicle: Boolean = true,
    val lastTimeInSight: Long? = null, // Para el temporizador de 60s
    val timerSeconds: Int = 0
)

data class PoliceNPC(
    val id: String,
    val location: GeoPoint,
    val isChasing: Boolean = false
)
