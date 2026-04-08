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
    // Arrancamos en zoom 17 para que osmdroid descargue suficientes tiles alrededor.
    // Una vez que la red de calles esté lista, el ViewModel hace zoom-in automático a 21.
    val zoomLevel: Double = ZOOM_LOADING,
    val mapProvider: MapProvider = MapProvider.OSM,
    val showSettingsDialog: Boolean = false,
    val npcs: List<Npc> = emptyList(),
    val isRoadNetworkReady: Boolean = false
) {
    companion object {
        const val ZOOM_LOADING  = 17.0  // Zoom amplio — osmdroid descarga más tiles del área
        const val ZOOM_GAMEPLAY = 21.0  // Zoom GTA una vez que todo está listo
    }
}