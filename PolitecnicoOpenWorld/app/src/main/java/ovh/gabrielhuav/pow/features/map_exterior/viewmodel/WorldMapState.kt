package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.NpcModel

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = 18.0,

    val npcs: List<NpcModel> = emptyList()
)