package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import ovh.gabrielhuav.pow.features.settings.models.ControlType

const val ZOOM_LOADING = 18.0
const val ZOOM_GAMEPLAY_OSM = 22.0  // Nivel de zoom para OSMDroid Nativo (máximo por defecto)
const val ZOOM_GAMEPLAY_WEB = 19.0  // Nivel máx. de TILES reales que se pre-descargan en web (NO es el zoom de juego)

// ─── Zoom de juego por estado (todos los proveedores) ────────────────────────
const val ZOOM_ON_FOOT      = 22.0  // a pie (default)
const val ZOOM_DRIVING      = 21.0  // conduciendo
const val ZOOM_DRIVING_FAST = 20.0  // conduciendo MUY rápido (≥85% de MAX_SPEED, histéresis al 65%)

enum class MapProvider(val displayName: String) {
    OSM("OSMDroid (Nativo)"),
    GOOGLE_MAPS_NATIVE("Google Maps (Nativo)"),
    CARTO_VOYAGER("CARTO Voyager (Web)"),   // DEFAULT: sirve teselas hasta z20 → máximo detalle de calles
    OSM_WEB("OpenStreetMap (Web)"),
    GOOGLE_MAPS("Google Maps (Web)"),
    CARTO_DB_DARK("CartoDB Oscuro (Web)"),
    CARTO_DB_LIGHT("CartoDB Claro (Web)"),
    ESRI("Esri World Street (Web)"),
    ESRI_SATELLITE("Esri Satélite (Web)"),
    OPEN_TOPO("OpenTopoMap (Web)");

    val isWebProvider: Boolean get() = this != OSM && this != GOOGLE_MAPS_NATIVE
}

// Un disparo de policía (origen → jugador) con su marca de tiempo, para dibujar la
// "bala"/trazo unos milisegundos y que se vea de dónde viene el balazo.
data class PoliceShot(
    val from: GeoPoint,
    val to: GeoPoint,
    val at: Long
)

enum class RoadSource { LOADING, LOCAL_DB, NETWORK }
enum class TileSource  { LOCAL_OSM, LOCAL_CACHE, NETWORK }

data class WorldMapState(
    val currentLocation: GeoPoint? = null,
    val isLoadingLocation: Boolean = true,
    val zoomLevel: Double = ZOOM_LOADING,
    val mapProvider: MapProvider = MapProvider.CARTO_VOYAGER, // Default: CARTO Voyager (web, z20 = máximo detalle)
    // Cambio de proveedor con precarga: el nuevo se precarga en segundo plano mientras
    // sigues usando el actual. Cuando 'pendingProviderReady' es true, se avisa para cambiar.
    val pendingProvider: MapProvider? = null,
    val pendingProviderReady: Boolean = false,
    // Carga inicial del mapa: se descargan los tiles del proveedor alrededor del
    // spawn antes de dejar entrar. 'mapLoadProgress' va de 0f a 1f (solo tiles).
    val isMapReady: Boolean = false,
    val mapLoadProgress: Float = 0f,
    // Última fase del gate de carga: los NPCs (campus/estacionamientos/calles) ya
    // corrieron sus primeros ciclos de IA. worldReady = ubicación && calles && mapa && NPCs.
    val npcsWarmedUp: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val npcs: List<Npc> = emptyList(),
    val isRoadNetworkReady: Boolean = false,
    val roadSource: RoadSource = RoadSource.LOADING,
    val tileSource: TileSource = TileSource.NETWORK,
    val showCacheWidget: Boolean = true,
    val showFpsWidget: Boolean = false,
    // Widget de nivel de zoom (Ajustes → Interfaz): muestra el zoom actual en vivo,
    // útil para encontrar el nivel óptimo antes de fijarlo por defecto.
    val showZoomWidget: Boolean = false,
    // Widget velocímetro (Ajustes → Interfaz): velocidad en km/h, visible solo al conducir.
    val showSpeedometer: Boolean = true,
    val controlType: ControlType = ControlType.DPAD,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,

    // ─── Skin del jugador ────────────────────────────────────────────────
    val selectedSkin: PlayerSkin = PlayerSkin.LAZARO,
    val showSkinSelector: Boolean = false,

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

    // ─── APOCALIPSIS ZOMBI GLOBAL ────────────────────────────────────────
    val globalZombieMode: Boolean = false,

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

    // ─── NIVEL DE BÚSQUEDA (estilo GTA) ──────────────────────────────────────
    // Sube al golpear NPCs; mientras sea > 0 aparecen patrullas que te persiguen.
    val wantedLevel: Int = 0,
    // Aviso cuando un perseguidor (policía o NPC) está por bajarte del vehículo.
    val carjackWarning: String? = null,
    // Disparos de policía activos (se dibujan como trazos breves y luego se purgan).
    val policeShots: List<PoliceShot> = emptyList(),

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

    // NUEVAS VARIABLES PARA EL CREADOR DE RUTAS
    val routeDebugWaypoints: List<GeoPoint> = emptyList(), // Las "migas de pan"
    val isParkingSlotMode: Boolean = false,                // Flag del Checkbox
    val currentWayId: Int = 100,                           // ID del carril actual

    // Easter Eggs y Opciones extra
    val showRoadNetwork: Boolean = true,

    // ─── Jugabilidad: optimización de NPCs (gama baja) ───────────────────────
    // Si está activo, los NPCs lejanos se dibujan como emoji (más barato) y solo los
    // MUY cercanos usan el asset completo. Se configura en Ajustes → Jugabilidad.
    val npcEmojiLod: Boolean = false,

    // ─── Jugabilidad: optimizar para gama baja (emoji TOTAL) ─────────────────
    // Si está activo, TODOS los NPCs se dibujan como emoji (🧍🚗🧟👮) sin importar la
    // distancia: cero generación de sprites/bitmaps. Para equipos muy débiles.
    val npcFullEmoji: Boolean = false,

    // ─── ShineCTO Easter Egg ────────────────────────────────────────────────
    val showShineCTODiscovery: Boolean = false,
    val navigateToShineCTO: Boolean = false,

    // ─── ESCOM Door transition ───────────────────────────────────────────────
    val showEscomDoorFade: Boolean = false,
    val escomDoorFadeComplete: Boolean = false,
    val pendingDoorDestination: String? = null,

    // ─── Metro Stations ───────────────────────────────────────────────────────
    val metroStations: List<ovh.gabrielhuav.pow.domain.models.MetroStation> = emptyList(),
    val nearbyMetroStation: ovh.gabrielhuav.pow.domain.models.MetroStation? = null,
    val showMetroFade: Boolean = false,
    val metroFadeCompleteStation: ovh.gabrielhuav.pow.domain.models.MetroStation? = null,

    // ─── Pre-descarga de tiles de la zona actual (offline) ───────────────────
    // Solo aplica al proveedor nativo OSM (caché Room unificada). Permite seguir
    // jugando mientras descarga (no bloqueante) y avisa si quedó incompleta por
    // falta de red, para garantizar juego sin conexión tras visitar la zona.
    val zonePrefetchActive: Boolean = false,
    val zonePrefetchProgress: Float = 0f,   // 0f..1f
    val zoneOfflineReady: Boolean = false,
    val zoneOfflineWarning: Boolean = false
)