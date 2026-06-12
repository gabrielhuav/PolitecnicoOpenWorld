package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

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

    // ─── Confirmación de salida al lobby (requerimiento 1) ──
    val showExitToLobbyDialog: Boolean = false,

    // ─── Línea punteada de ayuda al spawnear (requerimiento 5) ──
    val showExitGuide: Boolean = false,

    val nearbyDoorLabel: String? = null,
    val nearbyItemId: String? = null,
    val pickupToast: String? = null,

    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val isLoading: Boolean = true,
    val remotePlayers: List<RemoteZombiePlayer> = emptyList(),
    // NPCs civiles dentro del interior (autoritativos del servidor). Reusan RemoteZombiePlayer
    // para renderizarse como figuras humanas (deambulan/huyen de los zombis).
    val interiorNpcs: List<RemoteZombiePlayer> = emptyList(),
    val zombieModeActivated: Boolean = false,
    val showZombieCinematic: Boolean = false,

    // ─── MODO DISEÑADOR DE LA MATRIZ DE COLISIÓN ───────────
    // Análogo al modo diseñador del mapa principal: se activa/desactiva, se
    // pinta la rejilla sobre el cuarto y se guarda en collision_matrices.json.
    val designerMode: Boolean = false,
    val designerRows: List<String> = emptyList(), // matriz en edición
    val designerBrushWall: Boolean = true,         // true = pinta pared '#', false = borra '.'
    val designerDirty: Boolean = false,            // hay cambios sin guardar

    // ─── MODO DISEÑADOR DE WAYPOINTS (puertas) ─────────────
    // El diseñador alterna entre editar la MATRIZ de colisión o los WAYPOINTS
    // (puertas). En modo WAYPOINTS se arrastran las puertas sobre el fondo y se
    // guardan en waypoints.json.
    val designerTarget: DesignerTarget = DesignerTarget.MATRIX,
    val designerDoors: List<ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor> = emptyList(),
    val selectedDoorIndex: Int = -1
)

enum class DesignerTarget { MATRIX, WAYPOINTS }

data class CameraTransform(
    val offsetX: Float,
    val offsetY: Float,
    val scale: Float
)