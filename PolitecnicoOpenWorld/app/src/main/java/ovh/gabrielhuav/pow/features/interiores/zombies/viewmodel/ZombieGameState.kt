package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin   // ← NUEVO
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget   // tipo compartido (core)

data class ZombieGameState(
    val currentRoomIndex: Int = 0,

    val pendingSpawnX: Float? = null,
    val pendingSpawnY: Float? = null,

    val playerX: Float = 0f,
    val playerY: Float = 0f,
    val playerHealth: Float = 100f,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isPlayerFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val showPlayerHealthBar: Boolean = true,
    val damagePulseTrigger: Int = 0,
    val aimDirX: Float = 1f,
    val aimDirY: Float = 0f,

    // ─── Skin del jugador ────────────────────────────────────────────────
    val selectedSkin: PlayerSkin = PlayerSkin.LAZARO,       // ← NUEVO
    val showSkinSelector: Boolean = false,                   // ← NUEVO

    val zombies: List<ZombieEntity> = emptyList(),
    val items: List<SkillItem> = emptyList(),
    val projectiles: List<Projectile> = emptyList(),
    val totalZombies: Int = 0,
    val zombiesRemaining: Int = 0,

    // ─── Efectos activos (buffs/debuffs) ───────────────────
    val activeEffects: List<ActiveEffect> = emptyList(),
    val effectToast: String? = null,

    val combatMode: CombatMode = CombatMode.MELEE,
    val showWeaponMenu: Boolean = false,

    val showVictoryScreen: Boolean = false,
    val showWastedScreen: Boolean = false,
    val isExitingToWorld: Boolean = false,
    // MODO HISTORIA: el waypoint final de ENCB_LAB2 NO cambia de sala física, sino que
    // pide salir del motor de interiores y reanudar la narrativa (cómic ENCB_OUTRO).
    val isExitingToStoryOutro: Boolean = false,

    val showExitToLobbyDialog: Boolean = false,
    val showExitGuide: Boolean = false,

    val nearbyDoorLabel: String? = null,
    val nearbyItemId: String? = null,
    val pickupToast: String? = null,

    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val isLoading: Boolean = true,
    val remotePlayers: List<RemoteZombiePlayer> = emptyList(),
    val interiorNpcs: List<RemoteZombiePlayer> = emptyList(),
    val zombieModeActivated: Boolean = false,
    val showZombieCinematic: Boolean = false,

    // ─── MODO DISEÑADOR DE LA MATRIZ DE COLISIÓN ───────────
    val designerMode: Boolean = false,
    val designerRows: List<String> = emptyList(),
    val designerBrushWall: Boolean = true,
    val designerDirty: Boolean = false,

    // ─── MODO DISEÑADOR DE WAYPOINTS (puertas) ─────────────
    val designerTarget: DesignerTarget = DesignerTarget.MATRIX,
    val designerDoors: List<ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor> = emptyList(),
    val selectedDoorIndex: Int = -1
)

// DesignerTarget y CameraTransform se movieron a
