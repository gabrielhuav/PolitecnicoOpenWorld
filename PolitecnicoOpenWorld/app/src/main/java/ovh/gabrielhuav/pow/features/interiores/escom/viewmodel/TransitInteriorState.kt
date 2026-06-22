package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import ovh.gabrielhuav.pow.domain.models.map.TransitStation
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget

/** Hotspots interactivos dentro de una estación de transporte (común a metro/metrobús/…). */
enum class TransitHotspot {
    TAQUILLA, TORNIQUETES, ANDEN, SALIR_TORNIQUETES, SALIDA
}

/**
 * Estado UNIFICADO del interior de una estación de transporte. Campos con nombres NEUTROS
 * (`isVehicle1Animating`, `showTransitMap`, `allStations`…) para servir a cualquier sistema.
 *
 * Trae GETTERS DE COMPATIBILIDAD con los nombres antiguos (isMetro1Animating, isBus1Animating,
 * showMetroMap, showMetrobusMap, allMetroStations, allMetrobusStations) para que las pantallas
 * existentes sigan leyendo sin renombrar cada referencia. Son solo de LECTURA (no entran en
 * copy()/equals). Se pueden retirar cuando las pantallas usen los nombres neutros.
 */
data class TransitInteriorState(
    val playerX: Float = 0.5f,
    val playerY: Float = 0.15f,
    val hasRechargedTicket: Boolean = false,
    val showTransitMap: Boolean = false,
    val isVehicle1Animating: Boolean = false,
    val isVehicle1Departing: Boolean = false,
    val isVehicle2Animating: Boolean = false,
    val spawnWithAnimation: Boolean = false,
    val isFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isPlayerVisible: Boolean = true,
    val areControlsEnabled: Boolean = true,
    val isBoardingWalkActive: Boolean = false,
    val isDisembarkingWalkActive: Boolean = false,

    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,

    val activeDoor: ZoneDoor? = null,
    val messageToast: String? = null,
    val exitStationRequested: Boolean = false,

    // Diseñador
    val designerMode: Boolean = false,
    val designerTarget: DesignerTarget = DesignerTarget.MATRIX,
    val designerRows: List<String> = emptyList(),
    val designerBrushWall: Boolean = true,
    val designerDirty: Boolean = false,

    // Waypoints interiores
    val doors: List<ZoneDoor> = emptyList(),
    val selectedDoorIndex: Int = -1,

    // Mapa global de la red
    val globalWaypoints: List<ZoneDoor> = emptyList(),
    val selectedGlobalWaypointIndex: Int = -1,
    val mapDesignerMode: Boolean = false,
    val mapDesignerMoveMode: Boolean = false,
    val mapSearchQuery: String = "",
    val allStations: List<TransitStation> = emptyList()
) {
    // ── Alias de compatibilidad (solo lectura) ──────────────────────────────────
    val showMetroMap: Boolean get() = showTransitMap
    val showMetrobusMap: Boolean get() = showTransitMap
    val isMetro1Animating: Boolean get() = isVehicle1Animating
    val isBus1Animating: Boolean get() = isVehicle1Animating
    val isMetro1Departing: Boolean get() = isVehicle1Departing
    val isBus1Departing: Boolean get() = isVehicle1Departing
    val isMetro2Animating: Boolean get() = isVehicle2Animating
    val isBus2Animating: Boolean get() = isVehicle2Animating
    val allMetroStations: List<TransitStation> get() = allStations
    val allMetrobusStations: List<TransitStation> get() = allStations
}
