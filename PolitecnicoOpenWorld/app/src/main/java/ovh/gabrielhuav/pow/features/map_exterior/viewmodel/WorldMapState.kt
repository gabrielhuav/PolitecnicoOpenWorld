package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

const val ZOOM_LOADING = 18.0
const val ZOOM_GAMEPLAY_OSM = 20.0  // Nivel de zoom para OSMDroid Nativo
const val ZOOM_GAMEPLAY_WEB = 19.0  // Nivel de zoom para los proveedores Web

enum class MapProvider(val displayName: String) {
    OSM("OSMDroid (Nativo)"),
    GOOGLE_MAPS_NATIVE("Google Maps (Nativo)"),
    OSM_WEB("OpenStreetMap (Web)"),
    GOOGLE_MAPS("Google Maps (Web)"),
    CARTO_DB_DARK("CartoDB Oscuro (Web)"),
    CARTO_DB_LIGHT("CartoDB Claro (Web)"),
    ESRI("Esri World Street (Web)"),
    ESRI_SATELLITE("Esri Satélite (Web)"),
    OPEN_TOPO("OpenTopoMap (Web)");

    val isWebProvider: Boolean get() = this != OSM && this != GOOGLE_MAPS_NATIVE
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

    // Estados del personaje
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

    val isUserPanningMap: Boolean = false,

    // Multijugador
    val isMultiplayer: Boolean = false,
    val playerName: String = "",

    // ─── MODO DISEÑADOR ──────────────────────────────────────────────────
    val isDesignerMode: Boolean = false,
    val selectedLandmarkId: Long? = null,
    val showAssetPicker: Boolean = false,

    // Coleccionables
    val activeCollectibles: List<ActiveCollectible> = emptyList(),
    val nearbyCollectible: ActiveCollectible? = null,
    val showClaimedPopupFor: ActiveCollectible? = null,
    val interactionPrompt: String? = null,
    val showWastedScreen: Boolean = false,

    // ─── NAVEGACIÓN / MARCADOR DE DESTINO ────────────────────────────────
    val destinationMarker: GeoPoint? = null,
    val isTargetingWaypoint: Boolean = false,
    val routeWaypoints: List<GeoPoint> = emptyList(),
    val showDestinationRoute: Boolean = true,
    val destinationArrivalThreshold: Double = 20.0,
    val showZombiVideo: Boolean = false,
    val isZombieHandSpawned: Boolean = false,

    // ─── INTERIORES ZOMBIE ───────────────────────────────────────────────
    // Cuando el jugador activa una ZombiHand, aquí queda guardado a qué
    // edificio debe ser llevado tras terminar el video.
    val pendingInteriorDestination: InteriorBuilding? = null,

    // ─── MODO DEBUG DE INTERIORES ────────────────────────────────────────
    // Cuando está activado, se pintan los 6 marcadores fijos de los edificios
    // y el bounding box de ESCOM sobre el mapa, para ajustar coordenadas.
    val showInteriorDebugOverlay: Boolean = false,
    val showRoadNetwork: Boolean = true
)