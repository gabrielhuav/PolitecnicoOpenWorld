package ovh.gabrielhuav.pow.features.shinecto.viewmodel


import ovh.gabrielhuav.pow.domain.models.ShineCTOFloor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

/**
 * UI state for the ShineCTO interior.
 *
 * [playerX] / [playerY]  : normalised [0,1] position (same convention as InteriorState).
 * [drinkCount]           : number of drinks consumed (each one reduces speed multiplier).
 * [speedMultiplier]      : effective walk-speed factor, clamped to [MIN_SPEED].
 * [floor]                : which background is shown.
 */
data class ShineCTOState(
    val floor: ShineCTOFloor = ShineCTOFloor.GROUND,
    val playerX: Float = 0.5f,
    val playerY: Float = 0.5f,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isFacingRight: Boolean = true,
    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val drinkCount: Int = 0,
    val speedMultiplier: Float = 1.0f,
    val nearbyInteractable: ShineCTOInteractable? = null,
    val showDrinkToast: Boolean = false,
    val isLoading: Boolean = false
) {
    companion object {
        const val MIN_SPEED = 0.25f
        const val SPEED_REDUCTION_PER_DRINK = 0.15f
    }
}

/** Interactables that can appear in either floor. */
enum class ShineCTOInteractable(val label: String) {
    EXIT("Salir  (X)"),
    STAIRS_UP("Subir  (X)"),
    STAIRS_DOWN("Bajar  (X)"),
    DRINK("Beber  (X)")
}