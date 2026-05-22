package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

const val ZOOM_LOADING = 18.0
const val ZOOM_GAMEPLAY_OSM = 20.0  // Nivel de zoom para OSMDroid Nativo
const val ZOOM_GAMEPLAY_WEB = 19.0 // Nivel de zoom para los proveedores Web

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
    val showCacheWidget: Boolean = true,
    val showFpsWidget: Boolean = false,
    val controlType: ControlType = ControlType.DPAD,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,

    // Control de los estados del personaje principal
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isPlayerFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val isDriving: Boolean = false,
    val currentVehicleModel: CarModel? = null,
    val currentVehicleColor: Int? = null,
    val vehicleSpeed: Double = 0.0,
    val vehicleRotation: Float = 0f,
    val vehicleIsFirstTimeBoarded: Boolean = true,
    val landmarks: List<Landmark> = emptyList(),
    val showTeleportMenu: Boolean = false,

    // ─── MODO DISEÑADOR ──────────────────────────────────────────────────────
    val isDesignerMode: Boolean = false,
    val selectedLandmarkId: Long? = null,     // null = nada seleccionado
    val showAssetPicker: Boolean = false,      // diálogo para agregar nuevo asset

    // Coleccionables
    // Lista de objetos dibujados actualmente en el mapa
    val activeCollectibles: List<ActiveCollectible> = emptyList(),
    // El objeto que el jugador tiene lo suficientemente cerca para reclamar
    val nearbyCollectible: ActiveCollectible? = null,
    // El objeto que acabamos de recoger para mostrar el Pop-up divertido
    val showClaimedPopupFor: ActiveCollectible? = null,
    val interactionPrompt: String? = null,
    val showWastedScreen: Boolean = false
)