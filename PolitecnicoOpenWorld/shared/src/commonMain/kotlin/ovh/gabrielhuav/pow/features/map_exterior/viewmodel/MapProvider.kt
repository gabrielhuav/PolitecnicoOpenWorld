package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

/** Modelo puro; Ajustes oculta esta sección donde Mundo Libre no está disponible. */
enum class MapProvider(val displayName: String) {
    OSM("OSMDroid (Nativo)"),
    GOOGLE_MAPS_NATIVE("Google Maps (Nativo)"),
    CARTO_VOYAGER("CARTO Voyager (Web)"),
    OSM_WEB("OpenStreetMap (Web)"),
    GOOGLE_MAPS("Google Maps (Web)"),
    CARTO_DB_DARK("CartoDB Oscuro (Web)"),
    CARTO_DB_LIGHT("CartoDB Claro (Web)"),
    ESRI("Esri World Street (Web)"),
    ESRI_SATELLITE("Esri Satélite (Web)"),
    OPEN_TOPO("OpenTopoMap (Web)");

    // CARTO now watermarks anonymous tiles (September 2026). Keep legacy enum
    // values for stored settings, but never request credentialed services anonymously.
    val keyFreeProvider: MapProvider get() = when (this) {
        CARTO_VOYAGER, CARTO_DB_DARK, CARTO_DB_LIGHT, GOOGLE_MAPS, GOOGLE_MAPS_NATIVE -> OSM_WEB
        else -> this
    }

    val webTileTemplate: String get() = when (keyFreeProvider) {
        ESRI -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
        ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        OPEN_TOPO -> "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
        else -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    }

    val tileMaxNativeZoom: Int get() = if (keyFreeProvider == OPEN_TOPO) 17 else 19

    val isWebProvider: Boolean get() = this != OSM && this != GOOGLE_MAPS_NATIVE
}
