package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.MapLocation

enum class MapProvider { OSM, GOOGLE }

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = 18.0,
    val mapProvider: MapProvider = MapProvider.OSM,
    val showSettingsDialog: Boolean = false, // <-- Controla la visibilidad del menú
    val npcs: List<Npc> = emptyList()
)
