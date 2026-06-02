package ovh.gabrielhuav.pow.features.interior.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.DesignerTarget

enum class MetroHotspot {
    TAQUILLA, TORNIQUETES, ANDEN
}

data class MetroInteriorState(
    val playerX: Float = 0.5f,
    val playerY: Float = 0.15f,
    val hasRechargedTicket: Boolean = false,
    val showMetroMap: Boolean = false,
    val isFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val playerAction: PlayerAction = PlayerAction.IDLE,

    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,

    val activeDoor: ZoneDoor? = null,
    val messageToast: String? = null,

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
    val allMetroStations: List<ovh.gabrielhuav.pow.domain.models.MetroStation> = emptyList()
)
