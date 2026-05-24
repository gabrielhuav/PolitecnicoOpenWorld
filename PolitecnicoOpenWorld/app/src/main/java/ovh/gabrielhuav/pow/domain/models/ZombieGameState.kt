// features/zombie_minigame/viewmodel/ZombieGameState.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType

data class ZombieGameState(
    // ─── Zona actual ───────────────────────────────────────
    val currentRoomIndex: Int = 0,

    // ─── Jugador (coordenadas normalizadas [0,1]) ──────────
    val playerX: Float = 0.5f,
    val playerY: Float = 0.85f,
    val playerHealth: Float = 100f,
    val playerAction: PlayerAction = PlayerAction.IDLE,
    val isPlayerFacingRight: Boolean = true,
    val isRunning: Boolean = false,
    val showPlayerHealthBar: Boolean = false,
    val damagePulseTrigger: Int = 0,

    // ─── Zombis (de la zona actual) ────────────────────────
    val zombies: List<ZombieEntity> = emptyList(),
    val totalZombies: Int = 0,
    val zombiesRemaining: Int = 0,

    // ─── Flujo del juego ───────────────────────────────────
    val showVictoryScreen: Boolean = false, // se muestra al limpiar un edificio
    val isExitingToWorld: Boolean = false,  // dispara la navegación de salida
    val nearbyDoorLabel: String? = null,    // prompt "Entra a ..." / "Volver..."

    // ─── Preferencias de control (heredadas de Settings) ───
    val controlType: ControlType = ControlType.JOYSTICK,
    val controlsScale: Float = 1.0f,
    val swapControls: Boolean = false,
    val isLoading: Boolean = true
)