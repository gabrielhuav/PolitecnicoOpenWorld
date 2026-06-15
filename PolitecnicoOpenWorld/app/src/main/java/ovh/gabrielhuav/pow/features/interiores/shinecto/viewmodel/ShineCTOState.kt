package ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel

import ovh.gabrielhuav.pow.domain.models.ShineCTOFloor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible

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
    val playerHealth: Float = 100f,
    val isRunning: Boolean = false,
    val nearbyInteractable: ShineCTOInteractable? = null,
    val nearbyDrinkId: Int? = null,
    val drinks: List<ActiveDrink> = emptyList(),
    /** true cuando el jugador ha consumido DRINKS_TO_UNLOCK refrescos por primera vez */
    val shineCollectibleActive: Boolean = false,
    val showDrinkToast: Boolean = false,
    val drinkToastMessage: String = "",
    /** se activa cuando la vida llega a 0 por daño de azúcar → expulsión */
    val showClaimedPopupFor: ActiveCollectible? = null,
    val shouldExitToWorld: Boolean = false,
    val showWastedScreen: Boolean = false,
    val isLoading: Boolean = false
) {
    companion object {
        const val DRINKS_TO_UNLOCK = 10
        const val DRINK_DAMAGE = 20f
    }
}

data class ActiveDrink(
    val id: Int,
    val nx: Float,
    val ny: Float
) {
    companion object {
        const val HITBOX_HALF = 0.06f
    }
}

enum class ShineCTOInteractable(val label: String) {
    EXIT("Salir  (X)"),
    STAIRS_UP("Subir  (X)"),
    STAIRS_DOWN("Bajar  (X)"),
    DRINK("Beber  (X)"),
    SHINE_COLLECTIBLE("Refresco Especial  (X)")
}