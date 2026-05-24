package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.InteractableItem
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

data class ZombieGameState(
    val currentRoomIndex: Int = 0,

    // ─── Continuidad de posición ───────────────────────────
    val pendingSpawnX: Float? = null,
    val pendingSpawnY: Float? = null,

    // ─── Jugador (px de mundo) ─────────────────────────────
    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerHealth: Float = 100f,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isPlayerFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val showPlayerHealthBar: Boolean = true,
    val damagePulseTrigger: Int = 0,
    // Última dirección de movimiento (vector unitario), para apuntar el disparo
    val aimDirX: Float = 1f,
    val aimDirY: Float = 0f,

    // ─── Entidades ─────────────────────────────────────────
    val zombies: List<ZombieEntity> = emptyList(),
    val items: List<InteractableItem> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val totalZombies: Int = 0,
    val zombiesRemaining: Int = 0,

    // ─── Combate ───────────────────────────────────────────
    val combatMode: CombatMode = CombatMode.MELEE,
    val showWeaponMenu: Boolean = false,

    // ─── Flujo ─────────────────────────────────────────────
    val showVictoryScreen: Boolean = false,
    val showWastedScreen: Boolean = false,
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

/** Transformación de cámara calculada por frame en la UI. */
data class CameraTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float
)