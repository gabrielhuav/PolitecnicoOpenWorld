package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = 18.0
)