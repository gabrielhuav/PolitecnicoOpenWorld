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

    val isWebProvider: Boolean get() = this != OSM && this != GOOGLE_MAPS_NATIVE
}
