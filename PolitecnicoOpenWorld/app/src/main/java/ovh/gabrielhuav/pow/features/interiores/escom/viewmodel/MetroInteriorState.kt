package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget

enum class MetroHotspot {
    TAQUILLA, TORNIQUETES, ANDEN, SALIR_TORNIQUETES, SALIDA
}

data class MetroInteriorState(
    val playerX: Float = 0.5f,
    val playerY: Float = 0.15f,
    val hasRechargedTicket: Boolean = false,
    val showMetroMap: Boolean = false,
    val isMetro1Animating: Boolean = false,
    val isMetro1Departing: Boolean = false,
    val isMetro2Animating: Boolean = false,
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
    
    // Waypoints
    val doors: List<ZoneDoor> = emptyList(),
    val selectedDoorIndex: Int = -1,

    // Metro Map Overlay
    val globalWaypoints: List<ZoneDoor> = emptyList(),
    val selectedGlobalWaypointIndex: Int = -1,
    val mapDesignerMode: Boolean = false,
    val mapDesignerMoveMode: Boolean = false,
    val mapSearchQuery: String = "",
    val allMetroStations: List<ovh.gabrielhuav.pow.domain.models.map.MetroStation> = emptyList()
)
