package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash

// Estado UI inmutable del modo STREET FIGHTER. UN solo data class observado por la
// View con collectAsState() (contrato MVVM, README for IAS 01/09).

data class StreetFighterState(
    // Peleadores (0 = jugador con PRANKEDY 🆕, 1 = CPU con KEN), posiciones del JS
    val player: SfFighter = SfFighter(
        id = SfFighterId.PRANKEDY,
        playerIndex = 0,
        x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING - SfConstants.FIGHTER_START_DISTANCE,
        direction = SfDirection.RIGHT,
    ),
    val cpu: SfFighter = SfFighter(
        id = SfFighterId.KEN,
        playerIndex = 1,
        x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING + SfConstants.FIGHTER_START_DISTANCE,
        direction = SfDirection.LEFT,
    ),

    // Entidades efímeras
    val fireballs: List<SfFireball> = emptyList(),
    val splashes: List<SfHitSplash> = emptyList(),

    // Cámara (Camera.js): esquina superior izquierda del viewport en coords de mundo
    val cameraX: Float = SfConstants.STAGE_PADDING + SfConstants.STAGE_MID_POINT - SfConstants.SCENE_WIDTH / 2f,
    val cameraY: Float = 16f,

    // Batalla (StatusBar/BattleScene)
    val displayTime: Int = SfConstants.BATTLE_TIME,  // ya clampeado a >= 0
    val timeFlashing: Boolean = false,               // últimos segundos parpadean
    val playerScore: Int = 0,
    val cpuScore: Int = 0,
    val battleEnded: Boolean = false,
    val winnerIndex: Int? = null,
    val showEndMenu: Boolean = false,                // Revancha / Volver al menú
    val koFlash: Boolean = false,                    // parpadeo del icono KO en el HUD

    // Reloj de juego virtual (ms); la View lo usa para animaciones del escenario
    val gameTimeMs: Long = 0L,

    // Overlays / control
    val isPaused: Boolean = false,
    val showExitDialog: Boolean = false,

    // 🆕 Selección de personaje ANTES de pelear (arranca aquí; selectCharacter la cierra)
    val inCharacterSelect: Boolean = true,

    // ─── 🆕 MULTIJUGADOR 1v1 (servidor MultiplayerSF/ en Render, relay puro) ───
    val onlineStatus: SfOnlineStatus = SfOnlineStatus.OFF,
    val roomCode: String? = null,
    val isHost: Boolean = false,          // el anfitrión (p1) elige el mapa y arranca a la izquierda
    val onlineCountdown: Int = 0,         // 3-2-1 sincronizado por el servidor
    val onlineError: String? = null,
    val onlineMapFile: String? = null,    // mapa elegido por el anfitrión (fondo del combate)
    val opponentWantsRematch: Boolean = false,
    val activeRoomsInfo: String? = null,  // resumen "Salas activas: N · En espera: M"
)

/** Fase del flujo online (OFF = jugando offline contra la CPU). */
enum class SfOnlineStatus {
    OFF,
    CONNECTING,        // warmup del free tier de Render + abrir WebSocket
    WAITING_OPPONENT,  // sala creada, esperando al rival (mostrar el código)
    SELECTING,         // ambos en sala eligiendo peleador
    WAITING_MAP,       // personajes listos; el anfitrión elige el mapa
    COUNTDOWN,         // 3-2-1 del servidor
    FIGHTING,
    OPPONENT_LEFT,     // el rival se fue (victoria por abandono / sala rota)
}
