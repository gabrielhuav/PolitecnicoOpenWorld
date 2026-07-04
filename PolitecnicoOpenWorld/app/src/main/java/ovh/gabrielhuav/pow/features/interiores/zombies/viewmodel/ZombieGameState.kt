package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
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

    // ─── PUZZLE DE LLAVES (Modo Historia · ENCB_lab1) ──────────────────────
    // Llaves dispersas en la sala; el jugador inspecciona/prueba hasta hallar la correcta, que
    // abre la puerta de avance. `lab1KeyFound` persiste mientras se siga en la cadena ENCB.
    val keys: List<ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop> = emptyList(),
    val nearbyKeyId: String? = null,
    val lab1KeyFound: Boolean = false,
    val keyMessage: String? = null,

    // ─── MISIÓN 2 · SALÓN DE LA MOCHILA (escom_salon_m2) ────────────────────
    // Lata apestosa: al lanzarla (X), los NPCs ambientales EVACÚAN el salón; cuando queda vacío
    // aparece la MOCHILA de Prankedy (emoji 🎒, sin asset dedicado). Recogerla (X) marca
    // mission2BackpackTaken → ZombieGameScreen dispara onMission2BackpackRecovered (completa la
    // Misión 2 en el VM del mundo). Estos campos solo aplican en esa sala.
    val mission2StinkThrown: Boolean = false,
    // Dónde CAYÓ la lata (se dibuja 🥫 en el suelo + 💨 mientras evacúan; sin asset dedicado).
    val mission2StinkX: Float? = null,
    val mission2StinkY: Float? = null,
    val mission2BackpackX: Float? = null,
    val mission2BackpackY: Float? = null,
    val mission2BackpackNearby: Boolean = false,
    val mission2BackpackTaken: Boolean = false,

    // ─── MISIÓN 3 · ASALTO A LA ENCB (evidencia del laboratorio) ────────────
    // La evidencia 🧪 aparece en encb_lab1 (solo en modo asalto); recogerla (X) dispara
    // onMission3EvidenceRecovered en ZombieGameScreen y el auto-regreso al mapa.
    val mission3EvidenceX: Float? = null,
    val mission3EvidenceY: Float? = null,
    val mission3EvidenceNearby: Boolean = false,
    val mission3EvidenceTaken: Boolean = false,

    // ─── INVENTARIO ───────────────────────────────────────────────────────
    // `inventoryUnlockedSlots` slots USABLES (1 al inicio; TODOS al recuperar la mochila de
    // Prankedy — Misión 2); el resto se muestran bloqueados (rojo). `inventoryKeys` = entradas
    // "misión|asset" recogidas. Se GUARDA en las partidas (junto con lab1KeyFound). Abre con Y.
    val showInventory: Boolean = false,
    val inventoryKeys: List<String> = emptyList(),
    val inventoryUnlockedSlots: Int = 1,
    // MISIÓN 3 (recompensa): sin arma de fuego, el modo RANGED está BLOQUEADO (solo campaña;
    // fuera de campaña/multijugador llega true desde AppNavGraph).
    val firearmUnlocked: Boolean = true,

    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val showCoordsWidget: Boolean = false, // widget de coordenadas X/Y/Z (Ajustes → Interfaz)
    val isLoading: Boolean = true,
    val remotePlayers: List<RemoteZombiePlayer> = emptyList(),
    val interiorNpcs: List<RemoteZombiePlayer> = emptyList(),
    val ambientNpcs: List<AmbientNpc> = emptyList(),
    val zombieModeActivated: Boolean = false,
    val showZombieCinematic: Boolean = false,

    // ─── MODO DISEÑADOR DE LA MATRIZ DE COLISIÓN ───────────
    val designerMode: Boolean = false,
    val designerRows: List<String> = emptyList(),
    // Pincel activo: PARED, OBJETO_QUE_TAPA o BORRAR. Antes era Boolean; ahora enum para la oclusion.
    val designerBrush: DesignerBrush = DesignerBrush.WALL,
    val designerDirty: Boolean = false,

    // ─── MODO DISEÑADOR DE WAYPOINTS (puertas) ─────────────
    val designerTarget: DesignerTarget = DesignerTarget.MATRIX,
    val designerDoors: List<ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor> = emptyList(),
    val selectedDoorIndex: Int = -1
)

/** Pincel del Modo Disenador de la MATRIZ: WALL='#', OCCLUDER='^' (tapa+y-sort), ERASE='.'. */
enum class DesignerBrush { WALL, OCCLUDER, ERASE }

// DesignerTarget y CameraTransform se movieron a
