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
    OPEN_TOPO("OpenTopoMap (Web)");

    val isWebProvider: Boolean get() = this != OSM
}

enum class RoadSource { LOADING, LOCAL_DB, NETWORK }
enum class TileSource  { LOCAL_OSM, LOCAL_CACHE, NETWORK }

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = ZOOM_LOADING,
    val mapProvider: MapProvider = MapProvider.OSM,
    val showSettingsDialog: Boolean = false,
    val npcs: List<Npc> = emptyList(),
    val isRoadNetworkReady: Boolean = false,
    val roadSource: RoadSource = RoadSource.LOADING,
    val tileSource: TileSource = TileSource.NETWORK,
    val showCacheWidget: Boolean = true
) {
    companion object {
        const val ZOOM_LOADING        = 17.0   // Zoom amplio durante carga
        const val ZOOM_GAMEPLAY_OSM   = 21.0   // Zoom GTA para OSM nativo
        const val ZOOM_GAMEPLAY_WEB   = 18.0   // Zoom más alejado para proveedores web
        // (tiles web son más lentos de cargar)
    }
}