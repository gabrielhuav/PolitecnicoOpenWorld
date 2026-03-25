package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.Npc

enum class MapProvider(val displayName: String) {
    OSM("OSMDroid (Nativo)"),
    OSM_WEB("OpenStreetMap (Web)"),
    GOOGLE_MAPS("Google Maps (Web)"),
    CARTO_DB_DARK("CartoDB Oscuro (Web)"),
    CARTO_DB_LIGHT("CartoDB Claro (Web)"),
    ESRI("Esri World Street (Web)"),
    ESRI_SATELLITE("Esri Satélite (Web)"),
    OPEN_TOPO("OpenTopoMap (Web)")
}

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = 18.0,
    val mapProvider: MapProvider = MapProvider.OSM,
    val showSettingsDialog: Boolean = false,
    val npcs: List<Npc> = emptyList() // NUEVA PROPIEDAD
)