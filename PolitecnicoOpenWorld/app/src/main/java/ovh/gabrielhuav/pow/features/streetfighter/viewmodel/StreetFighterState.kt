package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtDevice
import ovh.gabrielhuav.pow.features.streetfighter.data.SfRoomSummary

// Estado UI inmutable del modo STREET FIGHTER. UN solo data class observado por la
// View con collectAsState() (contrato MVVM, README for IAS 01/09).

data class StreetFighterState(
    // Peleadores (0 = jugador con PRANKEDY, 1 = CPU con REY GRUPERO). ⚠️ El default de la CPU
    // era KEN: sus assets ahora viven SOLO en debug (copyright) y el default se DECODIFICA al
    // abrir el modo → en release crashearía. Defaults SIEMPRE de peleadores POW.
    val player: SfFighter = SfFighter(
        id = SfFighterId.PRANKEDY,
        playerIndex = 0,
        x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING - SfConstants.FIGHTER_START_DISTANCE,
        direction = SfDirection.RIGHT,
    ),
    val cpu: SfFighter = SfFighter(
        id = SfFighterId.REY_GRUPERO,
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
    val battleEnded: Boolean = false,                // fin de RONDA (congela); el combate sigue si nadie llegó a 2
    val winnerIndex: Int? = null,
    val showEndMenu: Boolean = false,                // Revancha / Volver al menú (solo con el COMBATE decidido)
    val koFlash: Boolean = false,                    // parpadeo del icono KO en el HUD

    // 🆕 ROLL-UP del HUD: HP MOSTRADO en las barras (drena GRADUAL hacia el hitPoints real;
    // subir — reset de ronda/revancha — es instantáneo). La View pinta las barras con ESTOS.
    val displayHp0: Float = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat(),
    val displayHp1: Float = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat(),

    // ─── 🆕 TUTORIAL INTERACTIVO (2026-07-21) ───
    // Modo entrenamiento guiado: el rival es un MUÑECO inerte y la pantalla pide un
    // movimiento concreto; solo se avanza cuando el jugador lo ejecuta de verdad.
    val tutorialActive: Boolean = false,
    /** Índice de la lección en curso (0-based) y total, para el progreso "3/11". */
    val tutorialLesson: Int = 0,
    val tutorialTotal: Int = 0,
    /** Nombre y pista de la lección (ya resueltos al idioma del juego). */
    val tutorialTitle: String = "",
    val tutorialHint: String = "",
    /** Pasos del combo y cuántos lleva acertados (para pintar "✓ ✓ ○"). */
    val tutorialSteps: List<String> = emptyList(),
    val tutorialStepIndex: Int = 0,
    /** Mensaje efímero de acierto ("OK" = paso, "COMPLETO" = combo entero). */
    val tutorialFlash: String = "",
    /**
     * 🆕 Aviso de ERROR: "LO QUE HICISTE → LO QUE TOCABA". Se llena cuando el jugador
     * ejecuta un movimiento reconocible distinto al que pide la lección.
     */
    val tutorialError: String = "",
    /** El jugador completó TODAS las lecciones. */
    val tutorialCompleted: Boolean = false,

    // ─── 🆕 COMBO estilo SF III 3rd Strike (2026-07-20) ───
    // Golpes CONECTADOS encadenados del combo en curso. El VM lo llena/expira (ventana
    // RAPID_HIT_WINDOW_MS); la View SOLO lo pinta ("N GOLPES") cuando comboCount >= 2.
    val comboCount: Int = 0,
    val comboPlayerId: Int = -1,   // índice del atacante del combo (0/1); -1 = sin combo

    // ─── 🆕 RONDAS estilo SF (2026-07-16): mejor de 3 — gana quien tome 2 rondas ───
    // Cada ronda termina por KO o timeout (más vida gana; EMPATE exacto → azar; online el
    // azar es DETERMINISTA con semilla compartida para que ambos lados coincidan).
    val playerRoundWins: Int = 0,
    val cpuRoundWins: Int = 0,
    val roundNumber: Int = 1,                        // 1..3
    val showRoundIntro: Boolean = false,             // banner "RONDA N / PELEA" (input congelado)

    // Reloj de juego virtual (ms); la View lo usa para animaciones del escenario
    val gameTimeMs: Long = 0L,

    // Overlays / control
    val isPaused: Boolean = false,
    val showExitDialog: Boolean = false,

    // 🆕 Selección de personaje ANTES de pelear (arranca aquí; selectCharacter la cierra)
    val inCharacterSelect: Boolean = true,

    // 🆕 Dificultad de la CPU (solo offline; online se ignora). La fija selectCharacter
    // desde el paso DIFICULTAD del flujo pre-pelea; resetRound la conserva (s.copy).
    val cpuDifficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,

    // 🆕 IA vs IA (CPU vs CPU a PESADILLA): ambos índices los controla la IA; sin input
    // táctil del jugador. Solo offline (grabación/espectáculo). startAiVsAi lo pone true.
    val aiVsAi: Boolean = false,

    // 🆕 AUTOJUEGO (gauntlet): un bot recorre muchas peleas IA vs IA seguidas para probar TODOS
    // los peleadores y detectar assets rotos (atascos/estancamientos). Ver startGauntlet* en el VM.
    val gauntletRunning: Boolean = false,            // hay un gauntlet en curso
    val gauntletProgress: String = "",               // "12/306" (peleas hechas/total)
    val gauntletFinished: Boolean = false,           // terminó → mostrar reporte
    val gauntletReport: List<String> = emptyList(),  // problemas detectados
    val gauntletReportPath: String? = null,          // ruta del .txt escrito
    // 🆕 (2026-07-18k) El gauntlet en curso es el SHOWCASE (la View muestra el botón SALTAR)
    val showcaseRunning: Boolean = false,
    // Controles QA del showcase visual: 1x/2x/4x acelera reloj, animaciones y pasos.
    val showcaseSpeed: Float = 1f,
    // 🆕 (2026-07-18k) Mapa de la pelea del autojuego (hogar del peleadór en turno)
    val gauntletMapFile: String? = null,

    // Showcase auditivo independiente: reproduce las 21 voces completas, una por una.
    val audioShowcaseRunning: Boolean = false,
    val audioShowcaseIndex: Int = 0,
    val audioShowcaseTotal: Int = 0,
    val audioShowcaseFighter: SfFighterId? = null,
    val audioShowcasePhrase: String = "",

    // ─── 🆕 MODO ARCADE (escalera de 11 peleas, offline; ver SfArcadeLadder) ───
    // Todos los personajes/mapas empiezan bloqueados y se desbloquean derrotando rivales.
    val arcadeActive: Boolean = false,                 // hay una escalera en curso
    val arcadeStep: Int = 0,                           // pelea actual 1..11 (0 = fuera)
    val arcadeTotal: Int = 0,                          // total de peleas (HUD "PELEA N / T")
    val arcadeRival: SfFighterId? = null,              // rival de la pelea actual (para carteles)
    val arcadeMapFile: String? = null,                 // fondo de la pelea de arcade
    val arcadeOutcome: SfArcadeOutcome = SfArcadeOutcome.NONE, // dirige el overlay de fin

    // ─── 🆕 Subtítulo del special (frase ES/EN del catálogo; fuente arcade HUD) ───
    // Se muestra un momento al lanzar special/bonus; se limpia cuando specialSubtitleUntilMs <= gameTimeMs.
    val specialSubtitleHud: String? = null,            // A-Z 0-9 para drawFontText
    val specialSubtitleUntilMs: Long = 0L,             // gameTimeMs límite (0 = oculto)
    val specialSubtitleStartMs: Long = 0L,             // 🆕 (2026-07-22) inicio: reparte los tramos '|' en [start,until]

    // ─── 🆕 MULTIJUGADOR 1v1 (servidor MultiplayerSF/ en Render, relay puro) ───
    val onlineStatus: SfOnlineStatus = SfOnlineStatus.OFF,
    val roomCode: String? = null,
    val isHost: Boolean = false,          // el anfitrión (p1) elige el mapa y arranca a la izquierda
    val onlineCountdown: Int = 0,         // 3-2-1 sincronizado por el servidor
    val onlineError: String? = null,
    val onlineMapFile: String? = null,    // mapa elegido por el anfitrión (fondo del combate)
    val opponentWantsRematch: Boolean = false,
    // Resumen de partidas (LIST_ROOMS): salas activas + tamaño de la lista de espera.
    // La View arma el texto (i18n) y pinta las salas en 'waiting' como tarjetas tocables.
    val activeRooms: List<SfRoomSummary> = emptyList(),
    val queueCount: Int? = null,          // null = aún sin datos del servidor

    // ─── 🆕 Lobby con APROBACIÓN (estilo AoE2, solo online) ───
    val joinRequestPending: Boolean = false, // (host) alguien pidió unirse: mostrar ACEPTAR/RECHAZAR
    val awaitingJoinOk: Boolean = false,     // (invitado) solicitud enviada; esperando al anfitrión
    val queueNotice: String? = null,         // aviso en la lista de espera (p. ej. "te rechazaron")

    // ─── 🆕 MULTIJUGADOR LOCAL por BLUETOOTH (SfBtClient; sin internet) ───
    val btMode: Boolean = false,             // la sesión online actual va por Bluetooth
    val btPicking: Boolean = false,          // selector "BUSCAR RIVAL" abierto
    val btDevices: List<SfBtDevice> = emptyList(), // emparejados + hallados por discovery
    // Falla de conexión LOCAL (BT o LAN) → overlay bloqueante con REINTENTAR (jamás se cae
    // al selector offline en silencio: elegiste BT/LAN y NO se pelea contra la IA sin
    // conexión verificada). btError/btHandshaking se REUSAN para LAN (mismo overlay/flujo).
    val btError: String? = null,
    val btRetryAddress: String? = null,      // rival BT a reintentar (null = era ANFITRIÓN)
    val btHandshaking: Boolean = false,      // socket conectado; verificando con el anfitrión

    // ─── 🆕 SERVIDOR LOCAL (LAN/Wi-Fi, SfLanClient): el jugador hostea su propia sala ───
    val lanMode: Boolean = false,            // la sesión actual va por LAN
    val lanLocalIp: String? = null,          // (host) IP a COMPARTIR con el rival; null = sin red
    val lanHostAddress: String? = null,      // (invitado) IP tecleada, para REINTENTAR
)

/** Resultado de una pelea de arcade (dirige el overlay de fin del modo arcade). */
enum class SfArcadeOutcome {
    NONE,       // no aplica (fuera del arcade o pelea en curso)
    WON,        // ganaste el escalón → CONTINUAR al siguiente rival
    LOST,       // perdiste → REINTENTAR (retrocede 1 pelea)
    COMPLETED,  // venciste al jefe final (Prankedy) → ¡campeón!
}

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
