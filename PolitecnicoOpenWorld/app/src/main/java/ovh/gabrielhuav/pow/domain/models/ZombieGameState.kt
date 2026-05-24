// features/zombie_minigame/viewmodel/ZombieGameState.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.InteractableItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

data class ZombieGameState(
    val currentRoomIndex: Int = 0,

    // ─── Jugador (px de mundo) ─────────────────────────────
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerHealth: Float = 100f,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isPlayerFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val showPlayerHealthBar: Boolean = true,
    val damagePulseTrigger: Int = 0,

    // ─── Entidades ─────────────────────────────────────────
    val zombies: List<ZombieEntity> = emptyList(),
    val items: List<InteractableItem> = emptyList(),
    val totalZombies: Int = 0,
    val zombiesRemaining: Int = 0,

    // ─── Flujo ─────────────────────────────────────────────
    val showVictoryScreen: Boolean = false,
    val isExitingToWorld: Boolean = false,
    val nearbyDoorLabel: String? = null,
    val nearbyItemId: String? = null,
    val pickupToast: String? = null,

    // ─── Controles ─────────────────────────────────────────
    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * Estado de cámara calculado por frame. Vive aparte del game state porque
 * depende del tamaño del viewport (que solo conoce la UI). La UI lo recalcula
 * con remember/derivedStateOf a partir de la posición del jugador.
 */
data class CameraTransform(
    val offsetX: Float,   // traslación en px de pantalla (ya con zoom aplicado)
    val offsetY: Float,
    val scale: Float      // zoom * fitScale
)