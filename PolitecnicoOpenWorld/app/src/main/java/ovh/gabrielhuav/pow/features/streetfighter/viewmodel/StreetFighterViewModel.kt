package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BONUS_POWER_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfArcadeLadder
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDirection
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighter
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterData
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireball
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFireballState
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHitSplash
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHurtArea
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfInput
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfProjectileEvent
import ovh.gabrielhuav.pow.domain.models.streetfighter.bonusPowerIndex
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfBonusPowerState
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetTransport
import ovh.gabrielhuav.pow.features.streetfighter.data.isSfLowEnd
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

internal const val SF_STOP_SPECIALS_EVENT = "__sf_stop_specials__"

private const val AUDIO_SHOWCASE_GAP_MS = 500L
private const val AUDIO_SHOWCASE_FALLBACK_MS = 5000L
private val SHOWCASE_SPEEDS = listOf(1f, 2f, 4f)

// ViewModel del modo STREET FIGHTER: port fiel de Fighter.js/BattleScene.js/Fireball.js.
// - requestAnimationFrame → coroutine a ~60 fps con dt medido y RELOJ DE JUEGO VIRTUAL
//   (gameNow avanza solo si no hay pausa → los timers absolutos no necesitan desplazarse).
// - Animación por FRAME-DELAYS del JSON (delay*FRAME_TIME; 0 = FREEZE, -1 = TRANSITION).
// - Cajas push/hurt/hit POR FRAME (del JSON). Hit-freeze de 15 frames al conectar un golpe.
// - Sonidos: SharedFlow de claves; la View los reproduce con SoundPool.
// Estado inmutable: la simulación del tick trabaja en un holder mutable local (Sim) y
// publica UNA vez con _state.update (convención 09). Scope: NavBackStackEntry.

@HiltViewModel
class StreetFighterViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(StreetFighterState())
    val state: StateFlow<StreetFighterState> = _state.asStateFlow()

    /** Claves de sonido (nombre base del .ogg en STREETFIGHTER/SOUNDS). */
    private val _soundEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val soundEvents: SharedFlow<String> = _soundEvents.asSharedFlow()

    private var audioShowcaseJob: Job? = null

    /**
     * SFX del especial/bonus por peleador: `special_<sf_fighter_id_lower>.ogg`
     * (pipeline tools/scrape_sf_voices.py + pack_sf_character_sfx.py).
     * La View hace fallback a `hadouken` si falta el asset.
     */
    private fun specialSfxKey(id: SfFighterId): String = "special_${id.name.lowercase()}"

    /** Frases special (ES/EN + HUD) de los 21 peleadores. Lazy desde assets. */
    private val specialPhrases by lazy {
        ovh.gabrielhuav.pow.features.streetfighter.data.SfSpecialPhrases.load(appContext)
    }

    /** Emite SFX + subtítulo arcade de la frase del special. */
    private fun emitSpecialVoice(id: SfFighterId, now: Long) {
        _soundEvents.tryEmit(specialSfxKey(id))
        val phrase = specialPhrases[id] ?: return
        val until = now + phrase.subtitleMs
        _state.update { s ->
            s.copy(
                specialSubtitleHud = phrase.hudLine(),
                specialSubtitleUntilMs = until,
            )
        }
    }

    /** Reproduce otra vez la voz completa del peleador visible en el showcase. */
    fun replayCurrentShowcaseAudio() {
        if (!gauntletActive || !showcaseMode) return
        emitSpecialVoice(_state.value.player.id, gameNow)
    }

    /** Recorre las 21 voces completas respetando la duración real de cada OGG. */
    fun startAudioShowcase() {
        if (audioShowcaseJob?.isActive == true) return
        val fighters = SfArcadeLadder.ALL_PARTICIPANTS.filter { it in specialPhrases }
        _soundEvents.tryEmit(SF_STOP_SPECIALS_EVENT)
        audioShowcaseJob = viewModelScope.launch {
            try {
                fighters.forEachIndexed { index, id ->
                    val phrase = specialPhrases.getValue(id)
                    _state.update {
                        it.copy(
                            audioShowcaseRunning = true,
                            audioShowcaseIndex = index + 1,
                            audioShowcaseTotal = fighters.size,
                            audioShowcaseFighter = id,
                            audioShowcasePhrase = phrase.phraseEs,
                        )
                    }
                    _soundEvents.emit(specialSfxKey(id))
                    delay(specialAudioDurationMs(id) + AUDIO_SHOWCASE_GAP_MS)
                }
            } finally {
                _state.update {
                    it.copy(
                        audioShowcaseRunning = false,
                        audioShowcaseIndex = 0,
                        audioShowcaseTotal = 0,
                        audioShowcaseFighter = null,
                        audioShowcasePhrase = "",
                    )
                }
            }
        }
    }

    /** Detiene el recorrido auditivo y también el MediaPlayer que esté hablando. */
    fun stopAudioShowcase() {
        audioShowcaseJob?.cancel()
        audioShowcaseJob = null
        _soundEvents.tryEmit(SF_STOP_SPECIALS_EVENT)
    }

    private fun specialAudioDurationMs(id: SfFighterId): Long {
        val fallbackMs = specialPhrases[id]?.subtitleMs ?: AUDIO_SHOWCASE_FALLBACK_MS
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                appContext.assets.openFd("STREETFIGHTER/SOUNDS/${specialSfxKey(id)}.ogg").use { fd ->
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: fallbackMs
            } finally {
                retriever.release()
            }
        }.getOrDefault(fallbackMs)
    }

    // 🆕 Progreso del ARCADE (guardado LOCAL). Define qué peleadores/mapas están desbloqueados.
    private val arcadeRepo = SfArcadeRepository(appContext)

    // 🆕 Gama baja: tick más lento (~30 fps) y menos trabajo por segundo (ver SfDeviceTier).
    private val lowEndDevice: Boolean = appContext.isSfLowEnd()
    private val tickMs: Long = if (lowEndDevice) 33L else 16L
    private val gauntletAuditSteps = 6

    /** true si el dispositivo es gama baja (la View reduce previews/fondos). */
    fun isLowEndDevice(): Boolean = lowEndDevice

    /** ¿Hay pelea arcade a medias para retomar? (solo lectura; no I/O pesado). */
    fun hasArcadeSession(): Boolean = arcadeRepo.hasSession()

    /** 🆕 Modo Desarrollador (Ajustes): si está ON, TODO desbloqueado (personajes y mapas). */
    fun devUnlockAll(): Boolean = SettingsRepository(appContext).getDeveloperMode()

    /** 🆕 Ajustes → "Mostrar hitboxes": dibuja las cajas push/hurt/hit sobre los peleadores. */
    fun showHitboxes(): Boolean = SettingsRepository(appContext).getShowHitboxes()

    /** Ids desbloqueados (arcade) como SfFighterId (ignora nombres inválidos). */
    private fun unlockedIds(): Set<SfFighterId> =
        arcadeRepo.unlockedFighters().mapNotNull { name ->
            runCatching { SfFighterId.valueOf(name) }.getOrNull()
        }.toSet()

    /**
     * Roster SELECCIONABLE (lo lee la View): los DESBLOQUEADOS del arcade (o TODOS con Modo
     * Desarrollador). Es función (no val) para releer el progreso en vivo tras desbloquear.
     * (RYU/KEN se eliminaron del juego; ver SfFighterId.)
     */
    fun selectableFighters(): List<SfFighterId> {
        if (devUnlockAll()) return SfArcadeLadder.ALL_PARTICIPANTS
        val unlocked = unlockedIds()
        return SfArcadeLadder.ALL_PARTICIPANTS.filter { it in unlocked }
    }

    /** Personajes del arcade AÚN bloqueados (la View los pinta con candado 🔒). Vacío en Dev. */
    fun lockedFighters(): List<SfFighterId> {
        if (devUnlockAll()) return emptyList()
        val unlocked = unlockedIds()
        return SfArcadeLadder.ALL_PARTICIPANTS.filter { it !in unlocked }
    }

    /** Archivos de mapa desbloqueados (para el selector con candado 🔒). */
    fun unlockedMaps(): Set<String> = arcadeRepo.unlockedMaps()

    // Estado interno de la escalera de arcade en curso (la lista pesada NO va al UiState).
    private var arcadeLadder: List<SfArcadeLadder.Step> = emptyList()
    private var arcadeMapCurrent: String? = null
    /** Dificultad elegida al iniciar arcade (Fácil/Medio/Difícil → mapas día/noche/apocalipsis). */
    private var arcadeChosenDifficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL
    private var arcadePlayer: SfFighterId = SfFighterId.ESCOMBOY

    // Frame data por peleador (cache perezoso por identidad; soporta CUALQUIER SfFighterId)
    private val dataCache = mutableMapOf<SfFighterId, SfFighterData>()
    private fun dataFor(f: SfFighter): SfFighterData =
        dataCache.getOrPut(f.id) { SfFrameCatalog.load(appContext, f.id) }

    // ---- reloj de juego virtual ----
    private var loopJob: Job? = null
    private var lastRealMs = 0L
    private var gameNow = 0L            // ms de JUEGO (no avanza en pausa)

    // ---- input táctil del jugador ----
    private var joyLeft = false
    private var joyRight = false
    private var joyUp = false
    private var joyDown = false
    private var joyLastMs = 0L          // real time; timeout = soltado
    private val pendingAttacks = ArrayDeque<Pair<SfAttackStrength, SfAttackType>>()
    private var pendingBonusPower: Int? = null
    private var bonusPowerCursor = 0

    // Historial de direcciones para el hadouken (↓ ↘ → + puño), estilo ControlHistory
    private val controlHistory = ArrayDeque<Pair<Int, Long>>() // (zona, gameNow)
    private var lastZone = 0

    // ---- IA de la CPU (POR PELEADOR: índice 0 y 1; en VS normal solo corre el 1) ----
    // Arrays de tamaño 2: en modo normal CPU = índice 1 (idéntico al de siempre); en IA vs IA
    // ambos índices tienen su propia cadencia e intención sostenida.
    private val cpuNextDecisionMs = LongArray(2) { 0L }
    private val cpuHold = Array(2) { SfInput() } // intención sostenida (caminar) por índice
    // 🆕 Cooldown de especial/bonus por peleador (ms de juego). Evita spam de hadoukens
    // en PESADILLA / IA vs IA que llenaba la pantalla y no se podía contrarrestar.
    private val specialCooldownUntil = LongArray(2) { 0L }
    // 🆕 Intensidad de la CPU 0f..1f (POR FASES del arcade): 0 = como en VS; 1 = máxima. Escala
    // la CADENCIA de decisión (reacciona más rápido) y la agresividad/bloqueo. En VS es 0
    // (comportamiento idéntico al de siempre); el arcade la sube según avanzas en la escalera.
    // IA vs IA la fija en 1f (máxima, como la final del arcade).
    private var cpuIntensity = 0f
    // Contador de “solo caminar sin atacar” por índice: si se atascan cerca sin golpear, forzamos ataque.
    private val cpuStaleApproach = IntArray(2) { 0 }
    // gameNow del último golpe/special emitido por la IA (watchdog anti “congelados”).
    private val cpuLastOffenseMs = LongArray(2) { 0L }
    // Preferencia de “espacio” tras clinch (retrocede un rato en IA vs IA).
    private val cpuWantsSpaceUntilMs = LongArray(2) { 0L }
    // Recuperación tras estancamiento: durante una ventana corta ambos cierran distancia.
    private val cpuForceEngageUntilMs = LongArray(2) { 0L }
    // Variedad ofensiva: memoria de los últimos tres golpes (fuerza×tipo, 0..5) por CPU.
    private val cpuAttackHistory = Array(2) { ArrayDeque<Int>() }
    // Antibucle de combo: tras tres impactos rápidos, el defensor recibe una ventana de escape.
    private val lastHitTakenMs = LongArray(2) { Long.MIN_VALUE }
    private val rapidHitsTaken = IntArray(2)
    private val comboEscapeUntilMs = LongArray(2)

    // 🆕 DIAGNÓSTICO / anti-atasco (2026-07-18h): detecta animaciones que NO terminan (assets sin
    // frame -1 / incompletas → peleador congelado, "se pegan y no se mueven") y estancamientos sin
    // daño; fuerza la salida a IDLE y registra el problema para corregir el asset después.
    private val stuckSig = arrayOfNulls<Pair<SfFighterState, Int>>(2) // (estado, frame) vigilado por índice
    private val stuckSinceMs = LongArray(2) { 0L }
    private val lastHpSeen = intArrayOf(-1, -1)
    private var lastDamageMs = 0L
    private var lastStalemateLogMs = -100000L
    private val assetIssues = LinkedHashSet<String>() // problemas detectados (deduplicados)
    private val stuckLimitMs = 1800L      // un estado transitorio congelado más de esto = asset roto
    private val stalemateMs = 12000L      // sin daño de nadie más de esto = estancamiento sospechoso
    /** Estados TRANSITORIOS que DEBEN completar; si se congelan, la hoja del peleador está mal. */
    private val mustCompleteStates: Set<SfFighterState> = setOf(
        SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
        SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
        SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
        SfFighterState.JUMP_START, SfFighterState.JUMP_UP,
        SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_LAND,
        SfFighterState.CROUCH_DOWN,
        SfFighterState.CROUCH_UP, SfFighterState.IDLE_TURN, SfFighterState.CROUCH_TURN,
    ) + SF_BONUS_POWER_STATES

    // 🆕 AUTOJUEGO (gauntlet): cola de parejas a pelear en IA vs IA, encadenadas automáticamente.
    private var gauntletActive = false
    private data class GauntletFight(
        val player: SfFighterId,
        val rival: SfFighterId,
        val difficulty: SfCpuDifficulty = SfCpuDifficulty.PESADILLA,
        val intensity: Float = 1f,
        val mapFile: String? = null,
    )

    private val gauntletQueue = ArrayDeque<GauntletFight>()
    private var gauntletTotal = 0
    private var gauntletDone = 0
    private var gauntletKoRounds = 0
    private var gauntletTimeoutRounds = 0
    private val gauntletFightCapMs = 325000L // tres rounds de 99 s + intros/transiciones
    // Tope de la pelea EN CURSO: en campaña depende de la dificultad; en showcase se calcula
    // por pasos para no cortar HURT/KO/VICTORY ni las metamorfosis con un TIMEOUT falso.
    private var gauntletFightCapCurMs = 60000L
    // 🆕 SHOWCASE: variante del gauntlet que recorre TODAS las animaciones + sonidos de cada
    // peleador (script de moves), para QA visual/auditiva de los assets (watchStuck loguea los rotos).
    private var showcaseMode = false
    private var showcaseStep = 0
    private var showcaseStepUntilMs = 0L
    private var showcaseFiredStep = -1
    private var showcaseSpeed = 1f
    // Ventana por animación: > stuckLimitMs (1800) para que watchStuck alcance a REGISTRAR y
    // rescatar una anim atascada antes de que el guion fuerce el siguiente estado (y > cooldown
    // de special/bonus). 🆕 2026-07-18j: era 1600 y enmascaraba atascos en los pasos forzados.
    private val showcaseStepMs = 2000L
    // Estado que el paso actual del guion FUERZA (bypass de validFrom); null = paso por input.
    private var showcaseForcedState: SfFighterState? = null

    // ---- batalla ----
    private var hurtFreezeUntilMs = 0L  // hit-freeze (FighterStruckDelay)
    private var time = SfConstants.BATTLE_TIME
    private var timeTimerMs = 0L
    private var timeFlashTimerMs = 0L
    private var useFlashFrames = false
    private var koFlashTimerMs = 0L
    private var koFrame = 0
    private var endMenuAtMs = 0L

    // ─── 🆕 RONDAS (mejor de 3) ───
    private var matchOver = false          // alguien ya tomó 2 rondas → el fin muestra el menú
    private var roundResetAtMs = 0L        // >0 = hay ronda nueva programada (intermedio corriendo)
    private var roundIntroUntilMs = 0L     // banner "RONDA N / PELEA": input y timer congelados
    private var roundGraceUntilMs = 0L     // tras el reset, ignora snapshots/daño viejos del rival
    private var roundEndSent = false       // guard de ROUND_ENDED (como onlineEndSent por ronda)

    // ─── 🆕 MULTIJUGADOR 1v1 (relay puro contra MultiplayerSF/ en Render) ───
    // Cada cliente simula a SU peleador (índice 0 local); el rival (índice 1) llega
    // por red: posición/estado/frame/hp vía OPPONENT_STATE y el daño que ME hacen vía
    // PLAYER_DAMAGE (autoridad del RECEPTOR sobre su propio HP).
    // TRANSPORTE intercambiable: SfMatchClient (WebSocket/Render) o SfBtClient (Bluetooth
    // local). Mismos mensajes/arquitectura; el VM solo habla con la interfaz.
    private var transport: SfNetTransport? = null
    private var btScanner: SfBtClient? = null   // discovery del selector "BUSCAR RIVAL"
    @Volatile private var remoteSnapshot: SfNetMsg? = null
    private val netDamageQueue = ConcurrentLinkedQueue<SfNetMsg>()
    private var myOnlineChar: SfFighterId? = null
    private var oppOnlineChar: SfFighterId? = null
    private var lastNetSendMs = 0L
    // 🆕 INTERPOLACIÓN del rival (SESIÓN 4): el snapshot llega a ~15 Hz; la posición se
    // ALISA con lerp por tick (y los proyectiles remotos se EXTRAPOLAN por la edad del
    // snapshot). lastSeenSnapshot detecta el CAMBIO de referencia (se compara identidad
    // en el tick, hilo Main).
    private var lastSeenSnapshot: SfNetMsg? = null
    private var remoteSnapshotAtMs = 0L
    // 🆕 ROLL-UP del HUD: HP mostrado (drena gradual hacia el real; subir = instantáneo)
    private var dispHp0 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    private var dispHp1 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    private var onlineEndSent = false
    private var countdownJob: Job? = null
    private var roomsRefreshJob: Job? = null   // refresca LIST_ROOMS mientras estás en la lista de espera
    private val isOnline: Boolean get() = _state.value.onlineStatus != SfOnlineStatus.OFF
    private val inOnlineFight: Boolean get() = _state.value.onlineStatus == SfOnlineStatus.FIGHTING

    private companion object {
        // TICK_MS por defecto; el loop usa [tickMs] (33 ms en gama baja).
        const val TICK_MS_DEFAULT = 16L
        // Táctil: el joystick emite cada ~33 ms al sostenerse; con 150 ms el input quedaba "pegado"
        // ~150 ms tras soltar (se sentía que "no reacciona"). 100 ms sigue siendo seguro (>33 ms).
        const val JOYSTICK_IDLE_MS = 100L
        // Ventana del cuarto de círculo del especial. Más ancha = más fácil en táctil.
        const val HADOUKEN_WINDOW_MS = 1100L
        const val END_MENU_DELAY_MS = 4200L
        // Lista de espera pública: cada cuánto se re-pide LIST_ROOMS (resumen + salas tocables)
        const val ROOMS_REFRESH_MS = 5000L
        // 🆕 Rondas (mejor de 3, dinámica original de SF)
        const val ROUNDS_TO_WIN = 2
        const val ROUND_RESET_DELAY_MS = 3500L   // "X WINS" en pantalla antes de la ronda nueva
        const val ROUND_INTRO_MS = 1800L         // banner "RONDA N / PELEA" con input congelado
        const val ROUND_GRACE_MS = 1200L         // ignora estado/daño del rival en vuelo tras el reset
        // 🆕 Especiales: cooldown + tope de proyectiles (PESADILLA spameaba y no se contrarrestaba)
        // Cooldowns de special: cortos para que la pelea se sienta viva (sin muro de proyectiles:
        // el tope por peleadór ya limita a 1 activo).
        const val SPECIAL_COOLDOWN_MS = 1200L
        const val SPECIAL_COOLDOWN_AIVSAI_MS = 1500L
        const val BONUS_COOLDOWN_MS = 2600L
        const val BONUS_COOLDOWN_AIVSAI_MS = 3200L
        const val RAPID_HIT_WINDOW_MS = 1200L
        const val COMBO_ESCAPE_MS = 950L
        const val RAPID_HITS_BEFORE_ESCAPE = 3
        const val MAX_ACTIVE_FIREBALLS_PER_FIGHTER = 1
        const val MAX_FIREBALLS_TOTAL = 4
        // Rangos IA (px): clinch → separar; melee → golpear; mid → footsies
        const val CPU_CLINCH_DIST = 58f
        const val CPU_MELEE_DIST = 105f
        const val CPU_MID_DIST = 175f
        // Límites mundiales del escenario (padding + stage). Mantienen a los peleadores visibles.
        val STAGE_X_MIN = SfConstants.STAGE_PADDING + 24f
        val STAGE_X_MAX = SfConstants.STAGE_PADDING + SfConstants.STAGE_WIDTH - 24f
        // 🆕 Interpolación del rival: tasa del lerp (≈rate*dt por tick) y distancia a partir
        // de la cual se SNAPEA (teleport/reset de ronda — no perseguirlo lerpeando)
        const val NET_LERP_RATE = 14f
        const val NET_SNAP_DIST = 80f
        // 🆕 Extrapolación de proyectiles remotos: tope de edad del snapshot (no sobrepasar)
        const val NET_FB_MAX_AGE_S = 0.25f
        // 🆕 Sincronía del timer: el invitado adopta el del host si difieren >= este umbral
        const val TIMER_RESYNC_DIFF = 2
        // 🆕 Roll-up del HUD: velocidad de drenado de la barra (HP por segundo)
        const val HP_DRAIN_PER_SEC = 200f
        const val ZONE_DOWN = 1
        const val ZONE_FORWARD_DOWN = 2
        const val ZONE_FORWARD = 3
    }

    // Metadatos de los estados de ataque (tipo + fuerza), como el states{} del JS
    private data class AttackMeta(val strength: SfAttackStrength, val type: SfAttackType)

    private val attackMeta = mapOf(
        SfFighterState.LIGHT_PUNCH to AttackMeta(SfAttackStrength.LIGHT, SfAttackType.PUNCH),
        SfFighterState.MEDIUM_PUNCH to AttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
        SfFighterState.HEAVY_PUNCH to AttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
        SfFighterState.LIGHT_KICK to AttackMeta(SfAttackStrength.LIGHT, SfAttackType.KICK),
        SfFighterState.MEDIUM_KICK to AttackMeta(SfAttackStrength.MEDIUM, SfAttackType.KICK),
        SfFighterState.HEAVY_KICK to AttackMeta(SfAttackStrength.HEAVY, SfAttackType.KICK),
        SfFighterState.SPECIAL_1_LIGHT to AttackMeta(SfAttackStrength.LIGHT, SfAttackType.PUNCH),
        SfFighterState.SPECIAL_1_MEDIUM to AttackMeta(SfAttackStrength.MEDIUM, SfAttackType.PUNCH),
        SfFighterState.SPECIAL_1_HEAVY to AttackMeta(SfAttackStrength.HEAVY, SfAttackType.PUNCH),
    )

    // validFrom del JS (Fighter.js states + los specials que añade el constructor de Ryu/Ken)
    private val specialValidFrom = setOf(
        SfFighterState.IDLE, SfFighterState.IDLE_TURN, SfFighterState.WALK_FORWARD,
        SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
        SfFighterState.CROUCH_UP, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH,
        SfFighterState.CROUCH_TURN, SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH,
        SfFighterState.HEAVY_PUNCH,
    )

    private val attackValidFrom = setOf(
        SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        // Tras giro: la IA/jugador debe poder golpear sin esperar a completar IDLE_TURN
        SfFighterState.IDLE_TURN, SfFighterState.JUMP_LAND, SfFighterState.CROUCH_UP,
    )

    private val validFrom: Map<SfFighterState, Set<SfFighterState>> = mapOf(
        SfFighterState.IDLE to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
            SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
            SfFighterState.CROUCH_UP, SfFighterState.JUMP_LAND, SfFighterState.IDLE_TURN,
            SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
            SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
            SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
            // 🆕 Tras poderes Grok / metamorfosis Presidenta→Yoalli se vuelve a IDLE
        ) + SF_BONUS_POWER_STATES.toSet(),
        SfFighterState.WALK_FORWARD to setOf(
            SfFighterState.IDLE, SfFighterState.JUMP_FORWARD, SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
        ),
        SfFighterState.WALK_BACKWARD to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_LAND,
        ),
        SfFighterState.JUMP_START to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
        ),
        SfFighterState.JUMP_LAND to setOf(
            SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD,
        ),
        SfFighterState.JUMP_UP to setOf(SfFighterState.IDLE, SfFighterState.JUMP_START),
        SfFighterState.JUMP_FORWARD to setOf(SfFighterState.JUMP_START, SfFighterState.WALK_FORWARD),
        SfFighterState.JUMP_BACKWARD to setOf(SfFighterState.JUMP_START, SfFighterState.WALK_BACKWARD),
        SfFighterState.CROUCH_DOWN to setOf(
            SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD, SfFighterState.JUMP_LAND,
        ),
        SfFighterState.CROUCH to setOf(SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_TURN),
        SfFighterState.CROUCH_UP to setOf(SfFighterState.CROUCH),
        SfFighterState.IDLE_TURN to setOf(
            SfFighterState.IDLE, SfFighterState.JUMP_LAND, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
        ),
        SfFighterState.CROUCH_TURN to setOf(SfFighterState.CROUCH),
        SfFighterState.LIGHT_PUNCH to attackValidFrom,
        SfFighterState.MEDIUM_PUNCH to attackValidFrom,
        SfFighterState.HEAVY_PUNCH to attackValidFrom,
        SfFighterState.LIGHT_KICK to attackValidFrom,
        SfFighterState.MEDIUM_KICK to attackValidFrom,
        SfFighterState.HEAVY_KICK to attackValidFrom,
        SfFighterState.HURT_HEAD_LIGHT to SF_HURT_STATES,
        SfFighterState.HURT_HEAD_MEDIUM to SF_HURT_STATES,
        SfFighterState.HURT_HEAD_HEAVY to SF_HURT_STATES,
        SfFighterState.HURT_BODY_LIGHT to SF_HURT_STATES,
        SfFighterState.HURT_BODY_MEDIUM to SF_HURT_STATES,
        SfFighterState.HURT_BODY_HEAVY to SF_HURT_STATES,
        SfFighterState.SPECIAL_1_LIGHT to specialValidFrom,
        SfFighterState.SPECIAL_1_MEDIUM to specialValidFrom,
        SfFighterState.SPECIAL_1_HEAVY to specialValidFrom,
        SfFighterState.VICTORY to SfFighterState.entries.toSet(),
        SfFighterState.KO to SfFighterState.entries.toSet(),
    ) + SF_BONUS_POWER_STATES.associateWith { specialValidFrom }

    init {
        startGameLoop()
    }

    // ------------------------------------------------------------------
    // Game loop (reloj virtual)
    // ------------------------------------------------------------------

    private fun startGameLoop() {
        if (loopJob?.isActive == true) return
        lastRealMs = SystemClock.elapsedRealtime()
        loopJob = viewModelScope.launch {
            while (isActive) {
                delay(tickMs) // 16 ms (~60) o 33 ms (~30) en gama baja
                val real = SystemClock.elapsedRealtime()
                val dtMs = (real - lastRealMs).coerceAtMost(100L)
                lastRealMs = real
                val s = _state.value
                // El reloj de juego se detiene en pausa, diálogo de salida y selector de personaje
                if (s.isPaused || s.showExitDialog || s.inCharacterSelect) continue
                // La auditoría de campaña es un bot de QA, no una modalidad de juego: simula
                // varios ticks estables por frame para recorrer sus 135 peleas en tiempo útil.
                // El showcase conserva velocidad real para que cada audio pueda oírse completo.
                val auditSteps = if (gauntletActive && !showcaseMode) gauntletAuditSteps else 1
                for (step in 0 until auditSteps) {
                    if (step > 0 && !gauntletActive) break
                    val speed = if (showcaseMode) showcaseSpeed else 1f
                    val scaledDtMs = (dtMs * speed).toLong().coerceAtLeast(1L)
                    gameNow += scaledDtMs
                    tick(gameNow, scaledDtMs / 1000f)
                    if (step + 1 < auditSteps) yield()
                }
            }
        }
    }

    /** Holder mutable de la simulación de UN tick; se publica al final. */
    private class Sim(
        var p0: SfFighter,
        var p1: SfFighter,
        val fireballs: MutableList<SfFireball>,
        val splashes: MutableList<SfHitSplash>,
        var camX: Float,
        var camY: Float,
        var score0: Int,
        var score1: Int,
        var winner: Int?,
        var battleEnded: Boolean,
    ) {
        fun fighter(i: Int): SfFighter = if (i == 0) p0 else p1
        fun setFighter(i: Int, f: SfFighter) { if (i == 0) p0 = f else p1 = f }
    }

    private fun tick(now: Long, dt: Float) {
        // 🆕 AUTOJUEGO: al terminar el combate (o agotar el tope) encadena la siguiente pelea.
        if (gauntletActive && maybeAdvanceGauntlet(now)) return
        // 🆕 RONDAS: ¿toca arrancar la ronda nueva? (resetea el estado ANTES de armar el Sim)
        if (roundResetAtMs > 0 && now >= roundResetAtMs) resetRound(now)
        val s = _state.value
        val online = s.onlineStatus == SfOnlineStatus.FIGHTING
        val sim = Sim(
            p0 = s.player, p1 = s.cpu,
            // ONLINE: solo se re-simulan MIS fireballs; los del rival son render-only
            fireballs = (if (online) s.fireballs.filter { it.ownerIndex == 0 } else s.fireballs).toMutableList(),
            splashes = s.splashes.toMutableList(),
            camX = s.cameraX, camY = s.cameraY,
            score0 = s.playerScore, score1 = s.cpuScore,
            winner = s.winnerIndex, battleEnded = s.battleEnded,
        )

        // El timer NO corre durante el banner "RONDA N / PELEA".
        // 🆕 (2026-07-18j) Tampoco en SHOWCASE: el guion completo (~68 s con la metamorfosis de
        // La Presidenta) supera los ~66 s reales del timer → TIME OVER cortaba los pasos finales.
        if (!sim.battleEnded && !showcaseMode && now >= roundIntroUntilMs) updateTimer(sim, now)

        if (online) {
            applyRemoteSnapshot(sim, now, dt) // posición/estado/hp del rival (red, interpolado)
            processNetDamage(sim, now)        // daño que el rival ME mandó (yo soy la autoridad de mi HP)
        }

        // Durante el banner la pelea sigue bloqueada, pero el Idle completo avanza: los
        // personajes ya no parecen estampas congeladas antes de "PELEA". Hit-freeze si
        // conserva el congelado total porque forma parte de la respuesta visual del golpe.
        when {
            now < hurtFreezeUntilMs -> Unit
            now < roundIntroUntilMs -> {
                sim.setFighter(0, updateRoundIntroAnimation(sim.fighter(0), now))
                sim.setFighter(1, updateRoundIntroAnimation(sim.fighter(1), now))
            }
            else -> {
                if (showcaseMode) {
                    // 🆕 (2026-07-18k) RITMO: si la animación del paso ya terminó (ambos en
                    // reposo) se adelanta la ventana (~400 ms tras arrancar el paso) en vez de
                    // esperar los 2 s fijos. KO/VICTORY (nunca vuelven a IDLE) y los pasos
                    // sostenidos de caminar/agachar (0..2) usan su ventana completa. El botón
                    // SALTAR de la View fuerza el fin en cualquier momento.
                    // (solo si el paso YA DISPARÓ: si aún espera el IDLE para soltar su input,
                    // adelantar aquí se lo saltaría)
                    val stepStart = showcaseStepUntilMs - showcaseStepMs
                    if (showcaseStep >= 3 && showcaseFiredStep == showcaseStep &&
                        now > stepStart + 400L &&
                        sim.p0.state == SfFighterState.IDLE && sim.p1.state == SfFighterState.IDLE
                    ) {
                        showcaseStepUntilMs = now
                    }
                    // SHOWCASE: ambos espejan un script que recorre todas las animaciones + sonidos
                    val inp = showcaseInput(now, sim.p0)
                    // 🆕 (2026-07-18j) Pasos FORZADOS del guion (giros, HURT_*, KO, VICTORY y la
                    // metamorfosis de La Presidenta): inalcanzables por input — se aplican directo
                    // (bypass de validFrom) UNA vez por paso; watchStuck los vigila igual.
                    showcaseForcedState?.let { st ->
                        forceShowcaseState(sim, 0, st, now)
                        forceShowcaseState(sim, 1, st, now)
                        showcaseForcedState = null
                    }
                    updateFighter(sim, 0, inp, now, dt)
                    updateFighter(sim, 1, inp, now, dt)
                } else if (s.aiVsAi) {
                    // IA vs IA: ambos peleadores bajo CPU (PESADILLA); sin input humano
                    updateFighter(sim, 0, buildCpuInput(now, sim, 0), now, dt)
                    updateFighter(sim, 1, buildCpuInput(now, sim, 1), now, dt)
                } else {
                    updateFighter(sim, 0, buildPlayerInput(now, sim), now, dt)
                    // El rival: CPU offline (índice 1); por RED online (no se simula localmente)
                    if (!online) updateFighter(sim, 1, buildCpuInput(now, sim, 1), now, dt)
                }
            }
        }
        watchStalemate(sim, now) // 🆕 diagnóstico de estancamiento (sin daño) + empujón a la IA
        updateFireballs(sim, now, dt)
        // 🆕 FIREBALL-VS-FIREBALL offline: ambos dueños viven en sim.fireballs
        if (!online) collideFireballPairs(sim, now)
        updateSplashes(sim, now)
        updateCamera(sim)
        updateKoFlash(sim, now)

        if (online) {
            appendRemoteFireballs(sim, now) // render de los proyectiles del rival (extrapolados)
            // 🆕 FIREBALL-VS-FIREBALL online: los MÍOS contra los del rival (simétrico: él hace
            // lo mismo con los suyos). ANTES de sendNetState para avisar el COLLIDED sin demora.
            collideFireballPairs(sim, now)
            sendNetState(sim, now)
        }

        // 🆕 ROLL-UP del HUD: el HP mostrado drena gradual hacia el real (subir = instantáneo,
        // así el reset de ronda/revancha rellena la barra solo, sin tocar los resets)
        dispHp0 = rollUpHp(dispHp0, sim.p0.hitPoints, dt)
        dispHp1 = rollUpHp(dispHp1, sim.p1.hitPoints, dt)

        // El menú de fin SOLO con el COMBATE decidido (2 rondas); entre rondas solo se congela
        val showEnd = sim.battleEnded && matchOver && now >= endMenuAtMs
        _state.update(sim, now, showEnd)
    }

    private fun MutableStateFlow<StreetFighterState>.update(sim: Sim, now: Long, showEnd: Boolean) {
        val subActive = value.specialSubtitleUntilMs > 0L && now < value.specialSubtitleUntilMs
        value = value.copy(
            player = sim.p0,
            cpu = sim.p1,
            fireballs = sim.fireballs,
            splashes = sim.splashes,
            cameraX = sim.camX,
            cameraY = sim.camY,
            displayTime = maxOf(time, 0),
            timeFlashing = useFlashFrames,
            playerScore = sim.score0,
            cpuScore = sim.score1,
            battleEnded = sim.battleEnded,
            winnerIndex = sim.winner,
            showEndMenu = showEnd,
            koFlash = koFrame == 1,
            gameTimeMs = now,
            showRoundIntro = now < roundIntroUntilMs,
            displayHp0 = dispHp0,
            displayHp1 = dispHp1,
            // limpiar subtítulo del special al expirar
            specialSubtitleHud = if (subActive) value.specialSubtitleHud else null,
            specialSubtitleUntilMs = if (subActive) value.specialSubtitleUntilMs else 0L,
        )
    }

    /** 🆕 Roll-up del HUD: drena hacia el HP real a HP_DRAIN_PER_SEC; subir es instantáneo. */
    private fun rollUpHp(disp: Float, target: Int, dt: Float): Float =
        if (target >= disp) target.toFloat()
        else maxOf(target.toFloat(), disp - HP_DRAIN_PER_SEC * dt)

    // ------------------------------------------------------------------
    // Animación por frames (setAnimationFrame / updateAnimation del JS)
    // ------------------------------------------------------------------

    private fun animOf(f: SfFighter) = dataFor(f).animations.getValue(f.state.jsKey)

    private fun withAnimationFrame(f: SfFighter, frame: Int, now: Long): SfFighter {
        val anim = animOf(f)
        val idx = if (frame >= anim.size) 0 else frame
        return f.copy(
            animationFrame = idx,
            animationTimerMs = now + (anim[idx].delay * SfConstants.FRAME_TIME_MS).toLong(),
        )
    }

    private fun updateAnimation(f: SfFighter, now: Long): SfFighter {
        val anim = animOf(f)
        val delay = anim[f.animationFrame.coerceIn(0, anim.size - 1)].delay
        if (delay <= 0 || now <= f.animationTimerMs) return f // FREEZE/TRANSITION o aún no toca
        return withAnimationFrame(f, f.animationFrame + 1, now)
    }

    /** Anima solo la pose neutral durante la presentacion, sin mover ni aceptar input. */
    private fun updateRoundIntroAnimation(f: SfFighter, now: Long): SfFighter {
        if (f.state != SfFighterState.IDLE) return f
        return if (f.animationTimerMs <= 0L) withAnimationFrame(f, 0, now)
        else updateAnimation(f, now)
    }

    private fun isAnimationCompleted(f: SfFighter): Boolean {
        val anim = animOf(f)
        val idx = f.animationFrame.coerceIn(0, anim.size - 1)
        // Completa si el frame actual es TERMINADOR (-1) o si ya llegamos al ÚLTIMO frame.
        // 🆕 (fix 2026-07-18) Varias hojas ALPHA/compartidas (p. ej. los estudiantes del arcade)
        // NO traen el frame -1 al final; withAnimationFrame hace wrap a 0 → la animación entra en
        // bucle y isAnimationCompleted jamás era true → el peleador quedaba ATASCADO (el jugador
        // "no se podía mover" en arcade; la CPU se congelaba). Tratar el último frame como fin evita
        // el bucle SIN acortar las animaciones bien formadas (en ellas el -1 ES el último frame, así
        // que el resultado no cambia para datos correctos).
        return anim[idx].delay == -1 || idx >= anim.size - 1
    }

    // ------------------------------------------------------------------
    // Cambio de estado + inits (changeState del JS)
    // ------------------------------------------------------------------

    private fun changeState(sim: Sim, idx: Int, newState: SfFighterState, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (newState != f.state && validFrom.getValue(newState).let { f.state !in it }) return false

        var nf = f.copy(state = newState)
        nf = withAnimationFrame(nf, 0, now)

        when (newState) {
            SfFighterState.IDLE -> {
                nf = nf.copy(velocityX = 0f, velocityY = 0f)
                // handleIdleInit: el ataque del rival vuelve a poder pegar
                val opp = sim.fighter(1 - idx)
                sim.setFighter(1 - idx, opp.copy(attackStruck = false))
            }
            SfFighterState.WALK_FORWARD -> nf = nf.copy(velocityX = SfConstants.WALK_FORWARD_VELOCITY)
            SfFighterState.WALK_BACKWARD -> nf = nf.copy(velocityX = SfConstants.WALK_BACKWARD_VELOCITY)
            SfFighterState.JUMP_UP -> nf = nf.copy(velocityX = 0f, velocityY = SfConstants.JUMP_VELOCITY)
            SfFighterState.JUMP_FORWARD ->
                nf = nf.copy(velocityX = SfConstants.JUMP_FORWARD_VELOCITY, velocityY = SfConstants.JUMP_VELOCITY)
            SfFighterState.JUMP_BACKWARD ->
                nf = nf.copy(velocityX = SfConstants.JUMP_BACKWARD_VELOCITY, velocityY = SfConstants.JUMP_VELOCITY)
            SfFighterState.JUMP_START, SfFighterState.JUMP_LAND, SfFighterState.CROUCH_DOWN,
            SfFighterState.VICTORY, SfFighterState.KO,
            SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
            SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
            -> nf = nf.copy(velocityX = 0f, velocityY = 0f)
            SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
            -> {
                nf = nf.copy(velocityX = 0f, velocityY = 0f, attackStruck = false)
                _soundEvents.tryEmit("${attackMeta.getValue(newState).strength.name.lowercase()}-attack")
            }
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY -> {
                nf = nf.copy(velocityX = 0f, velocityY = 0f, attackStruck = false, fireballFired = false)
                // Especial por personaje + subtítulo de frase (ES/EN catálogo)
                emitSpecialVoice(nf.id, now)
            }
            SfFighterState.BONUS_POWER_1, SfFighterState.BONUS_POWER_2, SfFighterState.BONUS_POWER_3,
            SfFighterState.BONUS_POWER_4, SfFighterState.BONUS_POWER_5, SfFighterState.BONUS_POWER_6,
            SfFighterState.BONUS_POWER_7, SfFighterState.BONUS_POWER_8, SfFighterState.BONUS_POWER_9,
            SfFighterState.BONUS_POWER_10, SfFighterState.BONUS_POWER_11,
            -> {
                val returnsToPresidenta = nf.id == SfFighterId.YOALLI_EHECATL &&
                    newState == SfFighterState.BONUS_POWER_10
                nf = nf.copy(
                    velocityX = 0f,
                    velocityY = 0f,
                    attackStruck = false,
                    fireballFired = false,
                    metamorphosing = nf.metamorphosing || returnsToPresidenta,
                )
                emitSpecialVoice(nf.id, now)
            }
            else -> Unit // CROUCH / CROUCH_UP / IDLE_TURN / CROUCH_TURN: sin init
        }
        // 🆕 (2026-07-18k) VICTORY con la VOZ del peleadór (reutiliza su special_<id>.ogg):
        // la celebración de fin de ronda estaba muda; el dueño pidió reutilizar audios
        // correctos antes que dejar animaciones sin sonido.
        if (newState == SfFighterState.VICTORY && f.state != SfFighterState.VICTORY) {
            emitSpecialVoice(nf.id, now)
        }
        sim.setFighter(idx, nf)
        return true
    }

    // ------------------------------------------------------------------
    // Update de un peleador (Fighter.update del JS: handler → posición →
    // slide → animación → constraints → colisión de ataque)
    // ------------------------------------------------------------------

    private fun updateFighter(sim: Sim, idx: Int, input: SfInput, now: Long, dt: Float) {
        runStateHandler(sim, idx, input, now, dt)

        // updatePositions: x += (vx - slide) * dir * dt ; y += vy * dt
        var f = sim.fighter(idx)
        f = f.copy(
            x = f.x + (f.velocityX - f.slideVelocity) * f.direction.sign * dt,
            y = f.y + f.velocityY * dt,
        )

        // updateSlide
        if (f.slideVelocity > 0f) {
            val slide = (f.slideVelocity - f.slideFriction * dt).coerceAtLeast(0f)
            f = f.copy(slideVelocity = slide, slideFriction = if (slide > 0f) f.slideFriction else 0f)
        }
        sim.setFighter(idx, f)

        sim.setFighter(idx, updateAnimation(sim.fighter(idx), now))
        updateStageConstraints(sim, idx, dt)
        // Doble seguro: tras anim/empuje, NUNCA fuera de pantalla (IA vs IA)
        sim.setFighter(idx, clampFighterToStage(sim.fighter(idx)))
        watchStuck(sim, idx, now) // 🆕 red de seguridad: desatasca animaciones que no terminan
        updateAttackBoxCollided(sim, idx, now)
    }

    /**
     * 🆕 Red de seguridad + DIAGNÓSTICO (2026-07-18h). Si un estado transitorio se queda en el
     * MISMO frame demasiado tiempo, su animación no termina (hoja sin frame -1 / incompleta): lo
     * saca a IDLE (arregla "se pegan y no se mueven") y registra el asset roto.
     */
    private fun watchStuck(sim: Sim, idx: Int, now: Long) {
        val f = sim.fighter(idx)
        if (f.state !in mustCompleteStates) { stuckSig[idx] = null; return }
        val sig = f.state to f.animationFrame
        if (stuckSig[idx] != sig) {
            stuckSig[idx] = sig
            stuckSinceMs[idx] = now
            return
        }
        if (now - stuckSinceMs[idx] > stuckLimitMs) {
            logAssetIssue("ATASCO ${f.id.name}: ${f.state} congelado en frame ${f.animationFrame} (anim sin terminador -1 o incompleta)")
            var nf = f.copy(state = SfFighterState.IDLE, velocityX = 0f, velocityY = 0f)
            nf = withAnimationFrame(nf, 0, now)
            sim.setFighter(idx, nf)
            stuckSig[idx] = null
            stuckSinceMs[idx] = now
        }
    }

    /**
     * 🆕 DIAGNÓSTICO: si pasa mucho tiempo sin que NADIE pierda vida, algo falla (hitboxes/rango/
     * IA pasiva). Registra el estancamiento y "pica" a ambas CPU para que ataquen ya.
     */
    private fun watchStalemate(sim: Sim, now: Long) {
        // 🆕 En showcase NO aplica: es un guion de animaciones, no una pelea (evita
        // "ESTANCAMIENTO" falso en el reporte).
        if (sim.battleEnded || showcaseMode || now < roundIntroUntilMs) return
        val hp0 = sim.p0.hitPoints
        val hp1 = sim.p1.hitPoints
        if (lastHpSeen[0] < 0) { lastHpSeen[0] = hp0; lastHpSeen[1] = hp1; lastDamageMs = now; return }
        if (hp0 != lastHpSeen[0] || hp1 != lastHpSeen[1]) lastDamageMs = now // hubo daño o ronda nueva
        lastHpSeen[0] = hp0
        lastHpSeen[1] = hp1
        val noDamageMs = now - lastDamageMs
        if (noDamageMs > stalemateMs) {
            val reportAfterMs = when (_state.value.cpuDifficulty) {
                SfCpuDifficulty.BASICA -> 45000L
                SfCpuDifficulty.NORMAL -> 35000L
                SfCpuDifficulty.AVANZADA, SfCpuDifficulty.PESADILLA -> 25000L
            }
            if (noDamageMs > reportAfterMs && now - lastStalemateLogMs > reportAfterMs) {
                val issue =
                    "ESTANCAMIENTO ${sim.p0.id.name} vs ${sim.p1.id.name}: " +
                        "sin daño >${reportAfterMs / 1000}s; " +
                        "P1 x=${sim.p0.x.toInt()} ${sim.p0.direction}/${sim.p0.state} hp=$hp0; " +
                        "P2 x=${sim.p1.x.toInt()} ${sim.p1.direction}/${sim.p1.state} hp=$hp1"
                android.util.Log.w("SF-DIAG", issue)
                if (gauntletActive) logAssetIssue(issue)
                lastStalemateLogMs = now
            }
            cpuNextDecisionMs[0] = 0L
            cpuNextDecisionMs[1] = 0L
            cpuHold[0] = SfInput()
            cpuHold[1] = SfInput()
            cpuLastOffenseMs[0] = 0L
            cpuLastOffenseMs[1] = 0L
            cpuWantsSpaceUntilMs[0] = 0L
            cpuWantsSpaceUntilMs[1] = 0L
            cpuForceEngageUntilMs[0] = now + 3000L
            cpuForceEngageUntilMs[1] = now + 3000L
        }
    }

    private fun logAssetIssue(msg: String) {
        if (assetIssues.add(msg)) android.util.Log.w("SF-DIAG", msg)
    }

    /** Reporte de problemas detectados en la sesión (assets rotos / atascos / estancamientos). */
    fun diagnosticsReport(): List<String> = assetIssues.toList()

    private fun runStateHandler(sim: Sim, idx: Int, input: SfInput, now: Long, dt: Float) {
        val f = sim.fighter(idx)
        when (f.state) {
            SfFighterState.IDLE -> {
                if (f.victory) { changeState(sim, idx, SfFighterState.VICTORY, now); return }
                if (!handleCommonNeutral(sim, idx, input, now)) {
                    // getDirection: encararse al rival (solo en idle/crouch, como el JS)
                    maybeTurn(sim, idx, SfFighterState.IDLE_TURN, now)
                }
            }
            SfFighterState.WALK_FORWARD -> {
                when {
                    input.special != null && trySpecial(sim, idx, input.special, now) -> Unit
                    !input.forward -> changeState(sim, idx, SfFighterState.IDLE, now)
                    input.up -> changeState(sim, idx, SfFighterState.JUMP_FORWARD, now)
                    input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
                    else -> tryAttacks(sim, idx, input, now)
                }
            }
            SfFighterState.WALK_BACKWARD -> {
                when {
                    !input.backward -> changeState(sim, idx, SfFighterState.IDLE, now)
                    input.up -> changeState(sim, idx, SfFighterState.JUMP_BACKWARD, now)
                    input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
                    else -> tryAttacks(sim, idx, input, now)
                }
            }
            SfFighterState.JUMP_START -> {
                if (isAnimationCompleted(f)) {
                    when {
                        input.backward -> changeState(sim, idx, SfFighterState.JUMP_BACKWARD, now)
                        input.forward -> changeState(sim, idx, SfFighterState.JUMP_FORWARD, now)
                        else -> changeState(sim, idx, SfFighterState.JUMP_UP, now)
                    }
                }
            }
            SfFighterState.JUMP_UP, SfFighterState.JUMP_FORWARD, SfFighterState.JUMP_BACKWARD -> {
                // handleJump: gravedad + aterrizaje
                val nf = f.copy(velocityY = f.velocityY + SfConstants.GRAVITY * dt)
                sim.setFighter(idx, nf)
                // El clamp del tick anterior deja `y` EXACTAMENTE en el piso. Con `>` nunca
                // aterrizaba: conservaba JUMP_* para siempre aunque ya estuviera abajo.
                if (nf.y >= SfConstants.STAGE_FLOOR && nf.velocityY >= 0f) {
                    sim.setFighter(idx, nf.copy(y = SfConstants.STAGE_FLOOR))
                    changeState(sim, idx, SfFighterState.JUMP_LAND, now)
                    // 🆕 CROSS-UP: al aterrizar, ENCARA de inmediato al rival (sin animación de giro).
                    // Si brincaste por encima quedabas viendo al lado contrario y "adelante" apuntaba
                    // LEJOS del rival → no le podías pegar. Orientar al tocar piso lo arregla.
                    val landed = sim.fighter(idx)
                    val opp = sim.fighter(1 - idx)
                    val facing = if (landed.x <= opp.x) SfDirection.RIGHT else SfDirection.LEFT
                    if (facing != landed.direction) sim.setFighter(idx, landed.copy(direction = facing))
                    _soundEvents.tryEmit("land")
                }
            }
            SfFighterState.JUMP_LAND -> {
                if (f.animationFrame > 0) {
                    if (!handleCommonNeutral(sim, idx, input, now) && isAnimationCompleted(sim.fighter(idx))) {
                        changeState(sim, idx, SfFighterState.IDLE, now)
                    }
                }
            }
            SfFighterState.CROUCH_DOWN -> {
                if (isAnimationCompleted(f)) {
                    changeState(sim, idx, SfFighterState.CROUCH, now)
                } else if (!input.down) {
                    // Quirk del JS: aborta la bajada arrancando la subida sin validFrom
                    var nf = f.copy(state = SfFighterState.CROUCH_UP)
                    nf = withAnimationFrame(nf, 0, now)
                    sim.setFighter(idx, nf)
                }
            }
            SfFighterState.CROUCH -> {
                if (input.special != null && trySpecial(sim, idx, input.special, now)) return
                if (!input.down) changeState(sim, idx, SfFighterState.CROUCH_UP, now)
                else maybeTurn(sim, idx, SfFighterState.CROUCH_TURN, now)
            }
            SfFighterState.CROUCH_UP -> if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            SfFighterState.IDLE_TURN -> {
                // Cancelar giro con input (jugador + IA). Si el input es ataque, puede salir
                // directo al golpe (validFrom incluye IDLE_TURN) sin pasar por IDLE.
                val wantsMove = input.forward || input.backward || input.up || input.down
                val wantsOffense = input.lightPunch || input.mediumPunch || input.heavyPunch ||
                    input.lightKick || input.mediumKick || input.heavyKick ||
                    input.special != null || input.bonusPower != null
                when {
                    wantsOffense -> {
                        if (input.bonusPower != null && tryBonusPower(sim, idx, input.bonusPower, now)) return
                        if (input.special != null && trySpecial(sim, idx, input.special, now)) return
                        if (tryAttacks(sim, idx, input, now)) return
                        if (changeState(sim, idx, SfFighterState.IDLE, now)) {
                            handleCommonNeutral(sim, idx, input, now)
                        }
                    }
                    wantsMove -> {
                        if (changeState(sim, idx, SfFighterState.IDLE, now)) {
                            handleCommonNeutral(sim, idx, input, now)
                        }
                    }
                    isAnimationCompleted(f) -> changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }
            SfFighterState.CROUCH_TURN -> {
                val wantsAction = !input.down || input.special != null ||
                    input.lightPunch || input.mediumPunch || input.heavyPunch
                when {
                    wantsAction && !input.down -> changeState(sim, idx, SfFighterState.CROUCH_UP, now)
                    isAnimationCompleted(f) -> changeState(sim, idx, SfFighterState.CROUCH, now)
                }
            }

            SfFighterState.LIGHT_PUNCH, SfFighterState.LIGHT_KICK -> {
                // Los ataques ligeros se pueden re-disparar desde el frame 2 (JS)
                if (f.animationFrame < 2) return
                val retap = (f.state == SfFighterState.LIGHT_PUNCH && input.lightPunch) ||
                    (f.state == SfFighterState.LIGHT_KICK && input.lightKick)
                if (retap) {
                    var nf = withAnimationFrame(f, 0, now).copy(attackStruck = false)
                    sim.setFighter(idx, nf)
                    _soundEvents.tryEmit("light-attack")
                    return
                }
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
            -> if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)

            SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
            SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
            -> {
                if (isAnimationCompleted(f)) {
                    val opp = sim.fighter(1 - idx)
                    sim.setFighter(1 - idx, opp.copy(attackStruck = false))
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }

            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY -> {
                val meta = attackMeta.getValue(f.state)
                val event = dataFor(f).projectileEvents[meta.strength] ?: SfProjectileEvent()
                // Cada hoja dedicada marca el cuadro exacto donde el objeto emite su efecto.
                if (f.animationFrame == event.animationFrame && !f.fireballFired) {
                    sim.setFighter(idx, f.copy(fireballFired = true))
                    sim.fireballs.add(
                        SfFireball(
                            ownerIndex = idx,
                            x = f.x + event.offsetX * f.direction.sign,
                            y = f.y + event.offsetY,
                            direction = f.direction,
                            strength = meta.strength,
                            velocity = meta.strength.fireballVelocity,
                            animationTimerMs = now,
                        ),
                    )
                }
                if (isAnimationCompleted(sim.fighter(idx))) {
                    sim.setFighter(idx, sim.fighter(idx).copy(fireballFired = false))
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }

            SfFighterState.BONUS_POWER_1, SfFighterState.BONUS_POWER_2, SfFighterState.BONUS_POWER_3,
            SfFighterState.BONUS_POWER_4, SfFighterState.BONUS_POWER_5, SfFighterState.BONUS_POWER_6,
            SfFighterState.BONUS_POWER_7, SfFighterState.BONUS_POWER_8, SfFighterState.BONUS_POWER_9,
            SfFighterState.BONUS_POWER_10, SfFighterState.BONUS_POWER_11,
            -> {
                // 🆕 BONUS_POWER_11 de La Presidenta = SOLO metamorfosis (sin proyectil spam):
                // al terminar la anim el id pasa a YOALLI con 50% HP y se QUEDA (no vuelve a Presidenta).
                if (f.id == SfFighterId.LA_PRESIDENTA && f.state == SfFighterState.BONUS_POWER_11) {
                    if (isAnimationCompleted(f)) {
                        completePresidentaMetamorphosis(sim, idx, now)
                    }
                    return
                }
                // BONUS_POWER_10 de Yoalli reproduce la misma metamorfosis en reversa y
                // recupera la identidad de La Presidenta, sin proyectil ni cambio de vida.
                if (f.id == SfFighterId.YOALLI_EHECATL && f.state == SfFighterState.BONUS_POWER_10) {
                    if (isAnimationCompleted(f)) {
                        completeYoalliMetamorphosis(sim, idx, now)
                    }
                    return
                }
                // Otros bonus: proyectil en cuadro central (Yoalli HEAVY; resto MEDIUM).
                val strength = if (f.id == SfFighterId.YOALLI_EHECATL) {
                    SfAttackStrength.HEAVY
                } else {
                    SfAttackStrength.MEDIUM
                }
                val event = dataFor(f).projectileEvents[strength] ?: SfProjectileEvent()
                if (f.animationFrame == 2 && !f.fireballFired) {
                    sim.setFighter(idx, f.copy(fireballFired = true))
                    sim.fireballs.add(
                        SfFireball(
                            ownerIndex = idx,
                            x = f.x + event.offsetX * f.direction.sign,
                            y = f.y + event.offsetY,
                            direction = f.direction,
                            strength = strength,
                            velocity = strength.fireballVelocity,
                            animationTimerMs = now,
                        ),
                    )
                }
                if (isAnimationCompleted(sim.fighter(idx))) {
                    sim.setFighter(idx, sim.fighter(idx).copy(fireballFired = false))
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }

            SfFighterState.KO -> {
                // handleFallBack: cae hasta el piso en el frame 2 (fall-2 = FREEZE)
                if (f.animationFrame == 2) {
                    if (f.y >= SfConstants.STAGE_FLOOR) {
                        var nf = withAnimationFrame(f, 3, now)
                        nf = nf.copy(velocityY = 0f, y = SfConstants.STAGE_FLOOR)
                        sim.setFighter(idx, nf)
                    } else {
                        sim.setFighter(idx, f.copy(velocityY = 120f))
                    }
                }
            }
            SfFighterState.VICTORY -> Unit
        }
    }

    /** Transiciones comunes de estados neutros (handleIdle del JS): salto/agacharse/caminar/ataques. */
    private fun handleCommonNeutral(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean {
        if (input.bonusPower != null && tryBonusPower(sim, idx, input.bonusPower, now)) return true
        if (input.special != null && trySpecial(sim, idx, input.special, now)) return true
        return when {
            input.up -> changeState(sim, idx, SfFighterState.JUMP_START, now)
            input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
            input.forward -> changeState(sim, idx, SfFighterState.WALK_FORWARD, now)
            input.backward -> changeState(sim, idx, SfFighterState.WALK_BACKWARD, now)
            else -> tryAttacks(sim, idx, input, now)
        }
    }

    private fun tryAttacks(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean = when {
        input.lightPunch -> changeState(sim, idx, SfFighterState.LIGHT_PUNCH, now)
        input.mediumPunch -> changeState(sim, idx, SfFighterState.MEDIUM_PUNCH, now)
        input.heavyPunch -> changeState(sim, idx, SfFighterState.HEAVY_PUNCH, now)
        input.lightKick -> changeState(sim, idx, SfFighterState.LIGHT_KICK, now)
        input.mediumKick -> changeState(sim, idx, SfFighterState.MEDIUM_KICK, now)
        input.heavyKick -> changeState(sim, idx, SfFighterState.HEAVY_KICK, now)
        else -> false
    }

    private fun trySpecial(sim: Sim, idx: Int, strength: SfAttackStrength, now: Long): Boolean {
        // Cooldown + tope de proyectiles propios activos (evita muro de hadoukens)
        if (now < specialCooldownUntil[idx.coerceIn(0, 1)]) return false
        val ownBalls = sim.fireballs.count {
            it.ownerIndex == idx && it.state == SfFireballState.ACTIVE
        }
        if (ownBalls >= MAX_ACTIVE_FIREBALLS_PER_FIGHTER) return false
        val state = when (strength) {
            SfAttackStrength.LIGHT -> SfFighterState.SPECIAL_1_LIGHT
            SfAttackStrength.MEDIUM -> SfFighterState.SPECIAL_1_MEDIUM
            SfAttackStrength.HEAVY -> SfFighterState.SPECIAL_1_HEAVY
        }
        val ok = changeState(sim, idx, state, now)
        if (ok) {
            // IA vs IA / PESADILLA: cooldown más largo para que el rival pueda reaccionar
            val aiVs = _state.value.aiVsAi
            specialCooldownUntil[idx.coerceIn(0, 1)] = now + if (aiVs) SPECIAL_COOLDOWN_AIVSAI_MS
            else SPECIAL_COOLDOWN_MS
        }
        return ok
    }

    private fun tryBonusPower(sim: Sim, idx: Int, power: Int, now: Long): Boolean {
        if (now < specialCooldownUntil[idx.coerceIn(0, 1)]) return false
        val fighter = sim.fighter(idx)
        // La Presidenta: P1..P10 son poderes; P11 es SOLO la metamorfosis automática (no se elige).
        val maxUsable = usableBonusPowerCount(fighter.id)
        if (power !in 1..maxUsable) return false
        val state = sfBonusPowerState(power) ?: return false
        if (dataFor(fighter).animations[state.jsKey].isNullOrEmpty()) return false
        val ok = changeState(sim, idx, state, now)
        if (ok) {
            specialCooldownUntil[idx.coerceIn(0, 1)] = now + if (_state.value.aiVsAi) {
                BONUS_COOLDOWN_AIVSAI_MS
            } else {
                BONUS_COOLDOWN_MS
            }
        }
        return ok
    }

    /** Poderes Grok “lanzables” (excluye metamorfosis P11 de La Presidenta). */
    private fun usableBonusPowerCount(id: SfFighterId): Int = when (id) {
        SfFighterId.LA_PRESIDENTA -> (id.bonusPowerCount - 1).coerceAtLeast(0) // 1..10
        else -> id.bonusPowerCount
    }

    /**
     * Fin de BONUS_POWER_11 de La Presidenta: se convierte en Yoalli Ehécatl a 50% HP.
     * El cambio de id es PERMANENTE (no “vuelve” a Presidenta al idle).
     */
    private fun completePresidentaMetamorphosis(sim: Sim, idx: Int, now: Long) {
        val f = sim.fighter(idx)
        if (f.id != SfFighterId.LA_PRESIDENTA) {
            changeState(sim, idx, SfFighterState.IDLE, now)
            return
        }
        val halfHp = SfConstants.HEALTH_MAX_HIT_POINTS / 2
        completeMetamorphosis(sim, idx, SfFighterId.YOALLI_EHECATL, halfHp, now)
    }

    /** Fin de BONUS_POWER_10 de Yoalli: regresa a La Presidenta conservando su vida. */
    private fun completeYoalliMetamorphosis(sim: Sim, idx: Int, now: Long) {
        val f = sim.fighter(idx)
        if (f.id != SfFighterId.YOALLI_EHECATL) {
            changeState(sim, idx, SfFighterState.IDLE, now)
            return
        }
        completeMetamorphosis(sim, idx, SfFighterId.LA_PRESIDENTA, f.hitPoints, now)
    }

    private fun completeMetamorphosis(
        sim: Sim,
        idx: Int,
        targetId: SfFighterId,
        targetHitPoints: Int,
        now: Long,
    ) {
        val transformed = clampFighterToStage(
            sim.fighter(idx).copy(
                id = targetId,
                hitPoints = targetHitPoints,
                metamorphosing = false,
                metamorphosed = true,
                fireballFired = false,
                attackStruck = false,
                velocityX = 0f,
                velocityY = 0f,
                slideVelocity = 0f,
                slideFriction = 0f,
                y = SfConstants.STAGE_FLOOR,
            ),
        )
        sim.setFighter(idx, transformed)
        changeState(sim, idx, SfFighterState.IDLE, now)
        val after = sim.fighter(idx)
        sim.setFighter(
            idx,
            clampFighterToStage(
                after.copy(
                    id = targetId,
                    hitPoints = targetHitPoints,
                    metamorphosed = true,
                    metamorphosing = false,
                ),
            ),
        )
        // HUD: forzar roll-up hacia la vida resultante de la transformación.
        if (idx == 0) dispHp0 = targetHitPoints.toFloat() else dispHp1 = targetHitPoints.toFloat()
    }

    private fun maybeTurn(sim: Sim, idx: Int, turnState: SfFighterState, now: Long) {
        val f = sim.fighter(idx)
        val opp = sim.fighter(1 - idx)
        val facing = if (f.x <= opp.x) SfDirection.RIGHT else SfDirection.LEFT
        if (facing != f.direction) {
            sim.setFighter(idx, f.copy(direction = facing))
            changeState(sim, idx, turnState, now)
        }
    }

    /**
     * Corrige el encaramiento de la CPU antes de interpretar `forward/backward`. Un cross-up o
     * un empuje puede intercambiar los lados mientras sigue caminando; con la cara vieja, la
     * siguiente orden de acercarse se convierte en alejarse y la pelea termina estancada.
     */
    private fun repairCpuFacing(sim: Sim, idx: Int, now: Long) {
        val fighter = sim.fighter(idx)
        val opponent = sim.fighter(1 - idx)
        val expected = if (fighter.x <= opponent.x) SfDirection.RIGHT else SfDirection.LEFT
        if (fighter.direction == expected || fighter.isAirborne ||
            fighter.state == SfFighterState.KO || fighter.state == SfFighterState.VICTORY ||
            fighter.metamorphosing
        ) return

        sim.setFighter(idx, fighter.copy(direction = expected))
        if (fighter.state == SfFighterState.WALK_FORWARD ||
            fighter.state == SfFighterState.WALK_BACKWARD ||
            fighter.state == SfFighterState.IDLE_TURN
        ) {
            changeState(sim, idx, SfFighterState.IDLE, now)
        }
    }

    // ------------------------------------------------------------------
    // Cajas por frame + constraints + colisión de ataque
    // ------------------------------------------------------------------

    private fun frameDef(f: SfFighter) = dataFor(f).frames.getValue(animOf(f)[f.animationFrame.coerceIn(0, animOf(f).size - 1)].frameKey)

    private fun pushBoxWorld(f: SfFighter): SfBox = SfBox.fromList(frameDef(f).push).toWorld(f.x, f.y, f.direction)

    private fun updateStageConstraints(sim: Sim, idx: Int, dt: Float) {
        var f = sim.fighter(idx)
        val push = SfBox.fromList(frameDef(f).push)

        // 1) Clamp AL ESCENARIO MUNDO (nunca fuera del stage — evita “desaparecer”
        // en IA vs IA cuando el empuje/slide los lanza fuera de cámara).
        f = clampFighterToStage(f)

        // 2) Límites del viewport (como el JS, contra la cámara)
        val margin = SfConstants.FIGHTER_DEFAULT_WIDTH
        if (f.x - sim.camX + margin > SfConstants.SCENE_WIDTH) {
            f = f.copy(x = sim.camX + SfConstants.SCENE_WIDTH - margin)
        } else if (f.x - sim.camX - margin < 0f) {
            f = f.copy(x = sim.camX + margin)
        }
        f = clampFighterToStage(f)
        sim.setFighter(idx, f)

        // Empuje al traslaparse los pushbox (updateStageConstraints del JS)
        var opp = sim.fighter(1 - idx)
        if (!pushBoxWorld(f).overlaps(pushBoxWorld(opp))) {
            // Aun sin overlap, re-asegura al rival (el otro update lo hará también)
            return
        }

        // Incluye caminar: si no, al chocar en WALK se “congelan” empujándose sin resolverse.
        val pushableStates = setOf(
            SfFighterState.IDLE, SfFighterState.CROUCH, SfFighterState.JUMP_UP,
            SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_FORWARD,
            SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
            SfFighterState.IDLE_TURN, SfFighterState.CROUCH_TURN,
        )
        if (f.x <= opp.x) {
            val nx = opp.x + SfBox.fromList(frameDef(opp).push).x - (push.x + push.width)
            f = f.copy(x = nx.coerceIn(STAGE_X_MIN, STAGE_X_MAX))
            if (opp.state in pushableStates) {
                opp = clampFighterToStage(
                    opp.copy(x = opp.x + SfConstants.FIGHTER_PUSH_FRICTION * dt),
                )
            }
        } else {
            val nx = minOf(
                sim.camX + SfConstants.SCENE_WIDTH - push.width.coerceAtLeast(1f),
                opp.x + SfBox.fromList(frameDef(opp).push).width,
            )
            f = f.copy(x = nx.coerceIn(STAGE_X_MIN, STAGE_X_MAX))
            if (opp.state in pushableStates) {
                opp = clampFighterToStage(
                    opp.copy(x = opp.x - SfConstants.FIGHTER_PUSH_FRICTION * dt),
                )
            }
        }
        sim.setFighter(idx, clampFighterToStage(f))
        sim.setFighter(1 - idx, clampFighterToStage(opp))
    }

    /** Mantener al peleador DENTRO del escenario (mundo). Y nunca por debajo del piso. */
    private fun clampFighterToStage(f: SfFighter): SfFighter {
        var x = f.x
        var y = f.y
        if (x.isNaN() || x.isInfinite()) x = SfConstants.STAGE_MID_POINT + SfConstants.STAGE_PADDING
        if (y.isNaN() || y.isInfinite()) y = SfConstants.STAGE_FLOOR
        x = x.coerceIn(STAGE_X_MIN, STAGE_X_MAX)
        // No permitir caer bajo el piso; el salto puede subir pero con tope de aire
        y = y.coerceIn(SfConstants.STAGE_FLOOR - 220f, SfConstants.STAGE_FLOOR)
        return if (x != f.x || y != f.y) f.copy(x = x, y = y) else f
    }

    private fun updateAttackBoxCollided(sim: Sim, idx: Int, now: Long) {
        val attacker = sim.fighter(idx)
        val meta = attackMeta[attacker.state] ?: return
        if (attacker.attackStruck) return
        val hit = frameDef(attacker).hit ?: return
        if (hit[2] == 0 || hit[3] == 0) return
        val actualHit = SfBox.fromList(hit).toWorld(attacker.x, attacker.y, attacker.direction)

        val defender = sim.fighter(1 - idx)
        val hurtRows = frameDef(defender).hurt ?: return
        for ((i, area) in SfHurtArea.entries.withIndex()) {
            val hurtBox = SfBox.fromList(hurtRows.getOrNull(i)).toWorld(defender.x, defender.y, defender.direction)
            // Un golpe puede tocar cuerpo o piernas sin tocar cabeza. Salir aquí descartaba
            // las zonas posteriores y volvía inofensivos muchos ataques válidos.
            if (!actualHit.overlaps(hurtBox)) continue

            var hitX = (actualHit.x + actualHit.width / 2f + hurtBox.x + hurtBox.width / 2f) / 2f
            var hitY = (actualHit.y + hurtBox.y + actualHit.height / 2f + hurtBox.width / 2f) / 2f
            hitX += 4f - Random.nextFloat() * SfConstants.HIT_SPLASH_RANDOMNESS
            hitY += 4f - Random.nextFloat() * SfConstants.HIT_SPLASH_RANDOMNESS

            applyAttackHit(sim, idx, meta.strength, meta.type, area, hitX to hitY, now)
            return
        }
    }

    /** handleAttackHit del JS + BattleScene.handleAttackHit (daño, score, KO, splash, hit-freeze). */
    private fun applyAttackHit(
        sim: Sim,
        attackerIdx: Int,
        strength: SfAttackStrength,
        type: SfAttackType,
        area: SfHurtArea,
        hitPos: Pair<Float, Float>?,
        now: Long,
    ) {
        val defenderIdx = 1 - attackerIdx
        var attacker = sim.fighter(attackerIdx)
        var defender = sim.fighter(defenderIdx)

        // 🆕 (2026-07-18j) SHOWCASE: los golpes/proyectiles espejados NO restan vida ni cambian
        // el estado (el guion controla las poses; antes los 10 poderes de La Presidenta sumaban
        // 200 de daño → KO y el combate se cortaba a media pasarela). Solo suenan y hacen splash
        // (de paso es el QA de los .ogg de impacto).
        if (showcaseMode) {
            _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            hitPos?.let { (x, y) ->
                sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
            }
            return
        }

        // Metamorfosis en curso: invulnerable (no se puede “matar” a media anim)
        if (isMetamorphosing(defender)) {
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            return
        }

        // ONLINE: el HP del RIVAL es suyo (autoridad del receptor). Si MI golpe/proyectil
        // conecta con él, solo AVISO (PLAYER_DAMAGE) + efectos optimistas locales; su HP y
        // su pose de daño llegarán en su siguiente OPPONENT_STATE.
        if (inOnlineFight && defenderIdx == 1) {
            _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            transport?.sendDamage(strength.damage, strength.name, type.name)
            hitPos?.let { (x, y) ->
                sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
            }
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
            return
        }

        // Ventana de salida tras una cadena rápida: el siguiente impacto no vuelve a encerrar
        // al defensor en HURT. El atacante consume su golpe para que no reintente cada frame.
        if (now < comboEscapeUntilMs[defenderIdx]) {
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            return
        }

        // BLOQUEO (estilo SF): caminar HACIA ATRÁS = cubrirse. El golpe entra "chip":
        // daño /4 (mínimo 1), medio retroceso, sin pose de HURT, sin splash ni puntos.
        val blocked = defender.state == SfFighterState.WALK_BACKWARD
        val damage = if (blocked) maxOf(1, strength.damage / 4) else strength.damage

        _soundEvents.tryEmit(
            if (blocked) "land" // golpe amortiguado (thud)
            else "${strength.name.lowercase()}-${type.name.lowercase()}-hit"
        )

        attacker = attacker.copy(attackStruck = true)
        defender = defender.copy(
            slideVelocity = strength.slideVelocity * (if (blocked) 0.5f else 1f),
            slideFriction = strength.slideFriction,
            hitPoints = (defender.hitPoints - damage).coerceAtLeast(0),
            direction = attacker.direction.opposite(), // BattleScene: el golpeado queda de frente
        )
        if (!blocked) {
            val chained = now - lastHitTakenMs[defenderIdx] <= RAPID_HIT_WINDOW_MS
            rapidHitsTaken[defenderIdx] = if (chained) rapidHitsTaken[defenderIdx] + 1 else 1
            lastHitTakenMs[defenderIdx] = now
            if (rapidHitsTaken[defenderIdx] >= RAPID_HITS_BEFORE_ESCAPE && defender.hitPoints > 0) {
                comboEscapeUntilMs[defenderIdx] = now + COMBO_ESCAPE_MS
                rapidHitsTaken[defenderIdx] = 0
                defender = defender.copy(
                    slideVelocity = maxOf(defender.slideVelocity, strength.slideVelocity * 1.35f),
                )
            }
        }
        if (!blocked) {
            if (attackerIdx == 0) sim.score0 += strength.score else sim.score1 += strength.score
        }
        sim.setFighter(attackerIdx, attacker)
        sim.setFighter(defenderIdx, defender)

        if (!blocked) hitPos?.let { (x, y) ->
            sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
        }

        if (blocked && defender.hitPoints > 0) {
            // Bloqueado: sin cambio de estado (sigue cubriéndose) y hit-freeze corto
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
            return
        }

        // 🆕 LA PRESIDENTA no “pierde” al KO: a ≤1/4 de vida (o daño letal) se metamorfosea
        // a Yoalli Ehécatl con 50% de vida (una sola vez). Invulnerable durante la anim.
        if (tryPresidentaMetamorphosis(sim, defenderIdx, attackerIdx, now)) {
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
            return
        }

        if (defender.hitPoints <= 0) {
            changeState(sim, defenderIdx, SfFighterState.KO, now)
            sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(victory = true))
            // 🆕 KO = fin de RONDA (mejor de 3); endRound decide si el combate terminó
            if (gauntletActive && !showcaseMode) gauntletKoRounds++
            endRound(sim, attackerIdx, now)
        } else {
            val hurtState = when (area) {
                SfHurtArea.BODY -> when (strength) {
                    SfAttackStrength.LIGHT -> SfFighterState.HURT_BODY_LIGHT
                    SfAttackStrength.MEDIUM -> SfFighterState.HURT_BODY_MEDIUM
                    SfAttackStrength.HEAVY -> SfFighterState.HURT_BODY_HEAVY
                }
                else -> when (strength) { // HEAD y LEGS caen a cabeza (default del JS)
                    SfAttackStrength.LIGHT -> SfFighterState.HURT_HEAD_LIGHT
                    SfAttackStrength.MEDIUM -> SfFighterState.HURT_HEAD_MEDIUM
                    SfAttackStrength.HEAVY -> SfFighterState.HURT_HEAD_HEAVY
                }
            }
            changeState(sim, defenderIdx, hurtState, now)
        }
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
    }

    /**
     * Si la defensora es La Presidenta sin haber metamorfoseado y el golpe la deja en
     * ≤25% HP (o la mataría), lanza BONUS_POWER_11 y NO aplica KO.
     * Al terminar la anim (ver handler BONUS_POWER_*), el id pasa a YOALLI con 50% HP.
     * @return true si se consumió el golpe como metamorfosis (el caller no hace KO/hurt).
     */
    private fun tryPresidentaMetamorphosis(
        sim: Sim,
        defenderIdx: Int,
        attackerIdx: Int,
        now: Long,
    ): Boolean {
        val d = sim.fighter(defenderIdx)
        if (d.id != SfFighterId.LA_PRESIDENTA || d.metamorphosed || d.metamorphosing) return false
        val maxHp = SfConstants.HEALTH_MAX_HIT_POINTS
        val threshold = maxHp / 4 // 50 de 200
        if (d.hitPoints > threshold) return false
        // Ya está en ≤1/4 (el HP se restó arriba). Arranca anim de metamorfosis.
        // FORZAR estado: puede venir de HURT (validFrom de BONUS_POWER no lo incluye).
        val pinnedHp = d.hitPoints.coerceIn(1, threshold)
        var nf = d.copy(
            state = SfFighterState.BONUS_POWER_11,
            hitPoints = pinnedHp,
            metamorphosing = true,
            metamorphosed = false,
            velocityX = 0f,
            velocityY = 0f,
            slideVelocity = 0f,
            slideFriction = 0f,
            attackStruck = false,
            fireballFired = false,
            y = SfConstants.STAGE_FLOOR,
        )
        nf = withAnimationFrame(nf, 0, now)
        sim.setFighter(defenderIdx, clampFighterToStage(nf))
        sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(attackStruck = true))
        emitSpecialVoice(d.id, now) // metamorfosis Presidenta → grito + subtítulo
        return true
    }

    /** Invulnerable durante cualquiera de las dos direcciones de la metamorfosis. */
    private fun isMetamorphosing(f: SfFighter): Boolean =
        f.metamorphosing ||
            (f.id == SfFighterId.LA_PRESIDENTA && f.state == SfFighterState.BONUS_POWER_11 && !f.metamorphosed) ||
            (f.id == SfFighterId.YOALLI_EHECATL && f.state == SfFighterState.BONUS_POWER_10)

    // ------------------------------------------------------------------
    // Fireballs (Fireball.js) — animación, movimiento, colisión
    // ------------------------------------------------------------------

    // Delays de la animación del fireball (frames del JS); los recortes viven en la View
    private val fireballActiveDelays = listOf(5, 2, 5, 1)
    private val fireballCollidedDelays = listOf(13, 3, 7)
    private val fireballBox = SfBox(-15f, -13f, 30f, 24f)

    private fun updateFireballs(sim: Sim, now: Long, dt: Float) {
        // Tope global: en PESADILLA/IA-vs-IA se acumulaban decenas → lag + muro imbloqueable
        if (sim.fireballs.size > MAX_FIREBALLS_TOTAL) {
            val keep = sim.fireballs
                .sortedByDescending { it.state == SfFireballState.ACTIVE }
                .take(MAX_FIREBALLS_TOTAL)
            sim.fireballs.clear()
            sim.fireballs.addAll(keep)
        }
        if (sim.fireballs.isEmpty()) return
        val iterator = sim.fireballs.listIterator()
        while (iterator.hasNext()) {
            var fb = iterator.next()
            val delays = if (fb.state == SfFireballState.ACTIVE) fireballActiveDelays else fireballCollidedDelays

            // Animación
            if (now > fb.animationTimerMs) {
                var frame = fb.animationFrame + 1
                if (frame >= delays.size) {
                    frame = 0
                    if (fb.state == SfFireballState.COLLIDED) { iterator.remove(); continue }
                }
                fb = fb.copy(
                    animationFrame = frame,
                    animationTimerMs = now + (delays[frame] * SfConstants.FRAME_TIME_MS).toLong(),
                )
            }

            // Movimiento + fuera de pantalla
            fb = fb.copy(x = fb.x + fb.velocity * fb.direction.sign * dt)
            if (fb.x - sim.camX >= SfConstants.SCENE_WIDTH + 56f || fb.x - sim.camX <= -56f) {
                iterator.remove(); continue
            }

            // Colisión con el rival
            if (fb.state == SfFireballState.ACTIVE) {
                val defenderIdx = 1 - fb.ownerIndex
                val defender = sim.fighter(defenderIdx)
                val fbBox = fireballBox.toWorld(fb.x, fb.y, fb.direction)
                val hurtRows = frameDef(defender).hurt
                val hitArea = hurtRows?.let { rows ->
                    SfHurtArea.entries.withIndex().firstOrNull { (i, _) ->
                        fbBox.overlaps(SfBox.fromList(rows.getOrNull(i)).toWorld(defender.x, defender.y, defender.direction))
                    }?.value
                }
                if (hitArea != null && defender.state in SF_HURT_STATES) {
                    fb = fb.copy(state = SfFireballState.COLLIDED, animationFrame = 0, velocity = fb.velocity * 0.33f,
                        animationTimerMs = now + (fireballCollidedDelays[0] * SfConstants.FRAME_TIME_MS).toLong())
                    applyAttackHit(sim, fb.ownerIndex, fb.strength, SfAttackType.PUNCH, hitArea, null, now)
                }
            }
            iterator.set(fb)
        }
    }

    // ------------------------------------------------------------------
    // Splashes (HitSplash.js: 4 frames, avanza cada 4*FRAME_TIME)
    // ------------------------------------------------------------------

    private fun updateSplashes(sim: Sim, now: Long) {
        if (sim.splashes.isEmpty()) return
        val iterator = sim.splashes.listIterator()
        val stepMs = (4 * SfConstants.FRAME_TIME_MS).toLong()
        while (iterator.hasNext()) {
            val sp = iterator.next()
            if (sp.animationTimerMs + stepMs > now) continue
            if (sp.animationFrame + 1 >= 4) iterator.remove()
            else iterator.set(sp.copy(animationFrame = sp.animationFrame + 1, animationTimerMs = now))
        }
    }

    // ------------------------------------------------------------------
    // Cámara (Camera.js)
    // ------------------------------------------------------------------

    private fun updateCamera(sim: Sim) {
        val lowX = minOf(sim.p0.x, sim.p1.x)
        val highX = maxOf(sim.p0.x, sim.p1.x)
        var camX = sim.camX

        if (highX - lowX > SfConstants.SCENE_WIDTH - SfConstants.SCROLL_BOUNDARY * 2) {
            camX = lowX + (highX - lowX) / 2f - SfConstants.SCENE_WIDTH / 2f
        } else {
            listOf(sim.p0, sim.p1).forEach { fighter ->
                if (fighter.x < camX + SfConstants.SCROLL_BOUNDARY) {
                    camX = fighter.x - SfConstants.SCROLL_BOUNDARY
                } else if (fighter.x > camX + SfConstants.SCENE_WIDTH - SfConstants.SCROLL_BOUNDARY) {
                    camX = fighter.x + SfConstants.SCROLL_BOUNDARY - SfConstants.SCENE_WIDTH
                }
            }
        }
        sim.camX = camX.coerceIn(
            SfConstants.STAGE_PADDING,
            SfConstants.STAGE_WIDTH + SfConstants.STAGE_PADDING - SfConstants.SCENE_WIDTH,
        )
        sim.camY = (-4f + kotlin.math.floor(minOf(sim.p0.y, sim.p1.y) / 10f))
            .coerceIn(0f, SfConstants.STAGE_HEIGHT - SfConstants.SCENE_HEIGHT)
    }

    // ------------------------------------------------------------------
    // Timer + KO flash (StatusBar.js)
    // ------------------------------------------------------------------

    private fun updateTimer(sim: Sim, now: Long) {
        if (now > timeTimerMs + SfConstants.TIME_DELAY_MS) {
            time -= 1
            timeTimerMs = now
        }
        if (time < 15 && time > -1 && now > timeFlashTimerMs + SfConstants.TIME_FLASH_DELAY_MS) {
            timeFlashTimerMs = now
            useFlashFrames = !useFlashFrames
        }
        if (time == -2 && !sim.battleEnded) {
            // 🆕 onTimeEnd estilo SF: gana la RONDA quien tenga más vida; EMPATE exacto →
            // AZAR (offline: Random real; online: azar DETERMINISTA con semilla compartida
            // — roundNumber + rondas ganadas son iguales en ambos lados — para que los dos
            // teléfonos "sorteen" al MISMO ganador sin mensajes extra).
            val s = _state.value
            val winnerIdx = when {
                sim.p0.hitPoints > sim.p1.hitPoints -> 0
                sim.p0.hitPoints < sim.p1.hitPoints -> 1
                !isOnline -> if (Random.nextBoolean()) 0 else 1
                else -> {
                    val seed = s.roundNumber * 31L + s.playerRoundWins + s.cpuRoundWins
                    val hostWins = Random(seed).nextBoolean()
                    if (hostWins == s.isHost) 0 else 1
                }
            }
            sim.setFighter(winnerIdx, sim.fighter(winnerIdx).copy(victory = true))
            changeState(sim, 1 - winnerIdx, SfFighterState.KO, now)
            if (gauntletActive && !showcaseMode) {
                gauntletTimeoutRounds++
                logAssetIssue(
                    "VICTORIA POR TIEMPO ${sim.p0.id.name} vs ${sim.p1.id.name}: " +
                        "la ronda ${_state.value.roundNumber} no terminó por KO",
                )
            }
            endRound(sim, winnerIdx, now) // timeout: ambos lo calculan; los guards evitan doble envío
        }
    }

    private fun updateKoFlash(sim: Sim, now: Long) {
        val critical = sim.p0.hitPoints <= SfConstants.HEALTH_CRITICAL_HIT_POINTS ||
            sim.p1.hitPoints <= SfConstants.HEALTH_CRITICAL_HIT_POINTS
        if (!critical) { koFrame = 0; return }
        val delay = if (koFrame == 0) (4 * SfConstants.FRAME_TIME_MS).toLong() else (7 * SfConstants.FRAME_TIME_MS).toLong()
        if (koFlashTimerMs + delay > now) return
        koFlashTimerMs = now
        koFrame = 1 - koFrame
    }

    // ------------------------------------------------------------------
    // Input del jugador (joystick + 6 botones + detección de hadouken)
    // ------------------------------------------------------------------

    private fun buildPlayerInput(now: Long, sim: Sim): SfInput {
        val idle = SystemClock.elapsedRealtime() - joyLastMs > JOYSTICK_IDLE_MS
        val left = joyLeft && !idle
        val right = joyRight && !idle
        val up = joyUp && !idle
        val down = joyDown && !idle
        val dir = sim.p0.direction
        val forward = (right && dir == SfDirection.RIGHT) || (left && dir == SfDirection.LEFT)
        val backward = (left && dir == SfDirection.RIGHT) || (right && dir == SfDirection.LEFT)

        // Registrar zona para el hadouken: ↓ → ↘ → →
        val zone = when {
            down && forward -> ZONE_FORWARD_DOWN
            down -> ZONE_DOWN
            forward -> ZONE_FORWARD
            else -> 0
        }
        if (zone != lastZone) {
            lastZone = zone
            if (zone != 0) {
                controlHistory.addLast(zone to now)
                while (controlHistory.size > 8) controlHistory.removeFirst()
            }
        }

        var lp = false; var mp = false; var hp = false
        var lk = false; var mk = false; var hk = false
        var special: SfAttackStrength? = null
        val bonusPower = pendingBonusPower
        pendingBonusPower = null
        while (pendingAttacks.isNotEmpty()) {
            val (strength, type) = pendingAttacks.removeFirst()
            if (type == SfAttackType.PUNCH && isHadoukenSequence(now)) {
                special = strength
                controlHistory.clear(); lastZone = 0
            } else when (type) {
                SfAttackType.PUNCH -> when (strength) {
                    SfAttackStrength.LIGHT -> lp = true
                    SfAttackStrength.MEDIUM -> mp = true
                    SfAttackStrength.HEAVY -> hp = true
                }
                SfAttackType.KICK -> when (strength) {
                    SfAttackStrength.LIGHT -> lk = true
                    SfAttackStrength.MEDIUM -> mk = true
                    SfAttackStrength.HEAVY -> hk = true
                }
            }
        }

        return SfInput(
            up = up, down = down, forward = forward, backward = backward,
            lightPunch = lp, mediumPunch = mp, heavyPunch = hp,
            lightKick = lk, mediumKick = mk, heavyKick = hk,
            special = special,
            bonusPower = bonusPower,
        )
    }

    /**
     * ¿El historial reciente forma un cuarto de círculo hacia adelante (↓ … →)? TÁCTIL-TOLERANTE:
     * basta ver ABAJO (↓ o la diagonal ↘) y DESPUÉS ADELANTE (→). La diagonal ↘ ya NO es
     * obligatoria (antes exigía ↓ → ↘ → → exacto, casi imposible con el pulgar) — así los
     * "hadoukens" salen mucho más fácil sin disparar falsos (aún requiere ir de abajo a adelante).
     */
    private fun isHadoukenSequence(now: Long): Boolean {
        val recent = controlHistory.filter { now - it.second <= HADOUKEN_WINDOW_MS }.map { it.first }
        var sawDown = false
        for (z in recent) {
            if (z == ZONE_DOWN || z == ZONE_FORWARD_DOWN) sawDown = true
            else if (z == ZONE_FORWARD && sawDown) return true
        }
        return false
    }

    // ------------------------------------------------------------------
    // IA de la CPU (el JS original era 2 jugadores humanos; aquí P2 = CPU)
    // 🆕 DIFICULTAD (2026-07-16): BASICA (lenta, sin poderes, para aprender),
    // NORMAL (la IA clásica del port) y AVANZADA (reactiva: bloquea, castiga,
    // anti-aéreo, esquiva hadoukens y lanza MUCHOS poderes — casi imposible).
    // Se elige en el paso DIFICULTAD del flujo offline pre-pelea; resetRound
    // y la revancha la CONSERVAN (viaja en state.cpuDifficulty vía s.copy).
    // ------------------------------------------------------------------

    /** Estados en los que el RIVAL está atacando (la IA avanzada BLOQUEA al verlos). */
    private val cpuThreatStates = setOf(
        SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
        SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
    ) + SF_BONUS_POWER_STATES

    /** Estados en los que el RIVAL está vulnerable (recuperación) → la avanzada CASTIGA. */
    private val cpuPunishStates = setOf(
        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
        SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
        SfFighterState.JUMP_LAND, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_UP,
    )

    /**
     * IA de UN peleador (`selfIndex` 0 o 1). En VS normal solo se llama con 1 (CPU);
     * en IA vs IA se llama para 0 y 1. Cadencia e intención sostenida son POR índice.
     *
     * 2026-07-18i: rangos SF (clinch/melee/mid/far), aproximación en **mundo** (no solo
     * “forward” de la cara), separación al pegarse, desync IA vs IA, watchdog ofensivo.
     */
    private fun buildCpuInput(now: Long, sim: Sim, selfIndex: Int): SfInput {
        if (sim.battleEnded) return SfInput()
        val i = selfIndex.coerceIn(0, 1)
        repairCpuFacing(sim, i, now)
        if (now < cpuNextDecisionMs[i]) return cpuHold[i]

        val difficulty = _state.value.cpuDifficulty
        val aiVs = _state.value.aiVsAi
        // Desync en IA vs IA: P1 piensa un poco desfasado → no se copian el espejo eterno
        val desync = if (aiVs) (i * 17L) else 0L
        val baseDelay = when (difficulty) {
            SfCpuDifficulty.BASICA -> Random.nextLong(650L, 1100L)
            SfCpuDifficulty.NORMAL -> Random.nextLong(160L, 340L)
            SfCpuDifficulty.AVANZADA -> Random.nextLong(55L, 120L)
            SfCpuDifficulty.PESADILLA -> Random.nextLong(35L, 75L)
        }
        cpuNextDecisionMs[i] = now + desync +
            (baseDelay * (1f - 0.42f * cpuIntensity)).toLong().coerceAtLeast(30L)

        var decision = when (difficulty) {
            SfCpuDifficulty.BASICA -> basicCpuDecision(sim, i)
            SfCpuDifficulty.NORMAL -> normalCpuDecision(sim, i, now)
            SfCpuDifficulty.AVANZADA -> smartCpuDecision(sim, i, now, nightmare = false)
            SfCpuDifficulty.PESADILLA -> smartCpuDecision(sim, i, now, nightmare = true)
        }

        val me = sim.fighter(i)
        val foe = sim.fighter(1 - i)
        val dist = abs(me.x - foe.x)

        if (now < cpuForceEngageUntilMs[i] && !me.isAirborne) {
            decision = if (dist > CPU_MELEE_DIST * 0.75f) {
                cpuApproach(me, foe)
            } else {
                variedCpuAttack(i)
            }
        }

        // 🆕 (2026-07-18j) Ofensiva REAL: el reloj del watchdog se alimenta del ESTADO del
        // peleadór (está atacando de verdad), no solo de la intención. Antes un ataque decidido
        // pero DESCARTADO (cooldown de special, validFrom, HURT en curso) contaba como ofensiva
        // → pasividad larga sin corrección ("se quedan quietos").
        if (me.state in attackMeta || me.state in SF_BONUS_POWER_STATES) cpuLastOffenseMs[i] = now

        // Watchdog: si lleva demasiado tiempo SIN ofensiva → forzar acción.
        // 🆕 (fix 2026-07-18) Antes solo actuaba a < 150 px; en IA vs IA ambos se quedaban
        // CAMINANDO / mirándose a media distancia sin que saltara nunca. Ahora cubre CUALQUIER
        // distancia: si están LEJOS obliga a CERRAR distancia (approach), y en rango de golpe
        // fuerza ataque o clinch break. Así nunca se estancan sin pelear.
        val watchdogLimit = when {
            difficulty == SfCpuDifficulty.BASICA && aiVs -> 3500L
            difficulty == SfCpuDifficulty.BASICA -> null
            aiVs -> 420L
            else -> 700L
        }
        if (watchdogLimit != null) {
            val staleMs = now - cpuLastOffenseMs[i]
            if (staleMs > watchdogLimit && !decision.hasAttackOrSpecial()) {
                // 🆕 (2026-07-18j) Con pasividad extrema (>2×limit) el golpe es OBLIGATORIO en
                // rango de pelea: garantiza que NUNCA pasen ~2 s sin acción estando cerca.
                val forceHit = staleMs > watchdogLimit * 2
                val attack = if (difficulty == SfCpuDifficulty.BASICA) {
                    cpuAttack(SfAttackStrength.LIGHT, punch = Random.nextBoolean())
                } else {
                    variedCpuAttack(i)
                }
                decision = when {
                    dist < CPU_CLINCH_DIST -> cpuClinchBreak(me, foe, now, i)
                    dist < 150f -> if (forceHit || Random.nextFloat() < 0.75f) attack else cpuJumpIn(me, foe)
                    else -> cpuApproach(me, foe) // pasivo demasiado tiempo y lejos → acercarse YA
                }
            }
        }

        // Anti-walk-loop: caminar hacia el rival sin golpear de cerca
        val onlyWalkIn = decision.isOnlyWalkToward(me, foe)
        if (onlyWalkIn && dist < 120f) {
            cpuStaleApproach[i]++
            if (cpuStaleApproach[i] >= 2) {
                decision = if (dist < CPU_CLINCH_DIST) {
                    cpuClinchBreak(me, foe, now, i)
                } else {
                    variedCpuAttack(i)
                }
                cpuStaleApproach[i] = 0
            }
        } else if (decision.hasAttackOrSpecial()) {
            cpuStaleApproach[i] = 0
        }

        cpuHold[i] = decision

        // Bonus powers (show en IA vs IA; raros vs humano)
        val bonusCount = usableBonusPowerCount(me.id)
        val bonusChance = when {
            difficulty == SfCpuDifficulty.BASICA -> 0f
            dist > 170f -> if (aiVs) 0.04f else 0.02f
            me.id == SfFighterId.LA_PRESIDENTA && !aiVs -> 0.035f
            aiVs -> 0.07f
            else -> 0.045f
        }
        val bonusStateReady = bonusCount > 0 && me.state in attackValidFrom && !me.metamorphosing
        val bonusPositionReady = !isNearStageCorner(me.x) && dist in 70f..200f
        val bonusCooldownReady = now >= specialCooldownUntil[i]
        if (bonusStateReady && bonusPositionReady && bonusCooldownReady &&
            Random.nextFloat() < bonusChance) {
            cpuHold[i] = SfInput(bonusPower = Random.nextInt(1, bonusCount + 1))
        }

        val oneShot = cpuHold[i]
        // Sostener direcciones (presión / walk-back); botones y salto = un tick
        cpuHold[i] = oneShot.copy(
            lightPunch = false, mediumPunch = false, heavyPunch = false,
            lightKick = false, mediumKick = false, heavyKick = false,
            special = null, bonusPower = null, up = false,
        )
        return oneShot
    }

    private fun SfInput.hasAttackOrSpecial(): Boolean =
        lightPunch || mediumPunch || heavyPunch || lightKick || mediumKick || heavyKick ||
            special != null || bonusPower != null

    private fun SfInput.isOnlyWalkToward(me: SfFighter, foe: SfFighter): Boolean {
        if (hasAttackOrSpecial() || up || down) return false
        val toward = cpuMoveTowardFlags(me, foe)
        return (forward && toward.forward && !backward) || (backward && toward.backward && !forward)
    }

    /** ¿Borde del stage? */
    private fun isNearStageCorner(x: Float): Boolean =
        x <= STAGE_X_MIN + 52f || x >= STAGE_X_MAX - 52f

    /** Flags de input para moverse HACIA el rival en coordenadas del mundo (corrige cara invertida). */
    private fun cpuMoveTowardFlags(me: SfFighter, foe: SfFighter): SfInput {
        val wantRight = foe.x > me.x
        val faceRight = me.direction == SfDirection.RIGHT
        return if (wantRight == faceRight) SfInput(forward = true) else SfInput(backward = true)
    }

    /** Alejarse del rival (crear espacio / clinch break). */
    private fun cpuRetreatFlags(me: SfFighter, foe: SfFighter): SfInput {
        val wantRight = foe.x > me.x
        val faceRight = me.direction == SfDirection.RIGHT
        // Invertir “hacia”
        return if (wantRight == faceRight) SfInput(backward = true) else SfInput(forward = true)
    }

    private fun cpuApproach(me: SfFighter, foe: SfFighter): SfInput {
        if (isNearStageCorner(me.x) && abs(me.x - foe.x) > 40f) {
            // Salir de esquina hacia el centro/rival
            return cpuMoveTowardFlags(me, foe)
        }
        return cpuMoveTowardFlags(me, foe)
    }

    private fun cpuJumpIn(me: SfFighter, foe: SfFighter): SfInput {
        val t = cpuMoveTowardFlags(me, foe)
        return t.copy(up = true)
    }

    private fun cpuJumpBack(me: SfFighter, foe: SfFighter): SfInput {
        val t = cpuRetreatFlags(me, foe)
        return t.copy(up = true)
    }

    /** Muy pegados: NO seguir caminando adentro — retroceder, golpear o brincar fuera. */
    private fun cpuClinchBreak(me: SfFighter, foe: SfFighter, now: Long, selfIndex: Int): SfInput {
        val roll = Random.nextFloat()
        val aiVs = _state.value.aiVsAi
        cpuWantsSpaceUntilMs[selfIndex] = now + if (aiVs) {
            Random.nextLong(160L, 300L)
        } else {
            Random.nextLong(280L, 520L)
        }
        if (aiVs) {
            // 🆕 (2026-07-18j) ROLES ASIMÉTRICOS: antes ambos índices rodaban la MISMA tabla y
            // solían decidir lo mismo (los dos retro o los dos golpe ligero) → se quedaban
            // "pegados" sin resolverse. Ahora se alterna por índice y tiempo: uno GOLPEA
            // (variado) mientras el otro SE SEPARA (retro/salto) — el clinch siempre termina
            // en acción visible.
            val attackerTurn = ((now / 900L).toInt() + selfIndex) % 2 == 0
            return when {
                attackerTurn && roll < 0.70f -> variedCpuAttack(selfIndex)
                attackerTurn -> cpuJumpIn(me, foe) // cross-up por encima
                roll < 0.42f -> cpuRetreatFlags(me, foe)
                roll < 0.68f -> cpuJumpBack(me, foe)
                else -> variedCpuAttack(selfIndex)
            }
        }
        return when {
            roll < 0.28f -> cpuRetreatFlags(me, foe)
            roll < 0.48f -> cpuJumpBack(me, foe)
            roll < 0.72f -> cpuAttack(SfAttackStrength.LIGHT, punch = Random.nextBoolean())
            roll < 0.88f -> cpuAttack(SfAttackStrength.MEDIUM, punch = true)
            else -> cpuJumpIn(me, foe) // cross-up / saltar por encima
        }
    }

    private fun hasIncomingFireball(sim: Sim, me: SfFighter, selfIndex: Int, range: Float): Boolean {
        val opp = 1 - selfIndex
        return sim.fireballs.any { fb ->
            fb.ownerIndex == opp && fb.state == SfFireballState.ACTIVE &&
                abs(fb.x - me.x) < range && (me.x - fb.x) * fb.direction.sign > 0f
        }
    }

    private fun ownFireballActive(sim: Sim, selfIndex: Int): Boolean =
        sim.fireballs.any { it.ownerIndex == selfIndex && it.state == SfFireballState.ACTIVE }

    /** Ajuste ligero por personaje sobre el mismo motor: zoners priorizan poderes; rushers presión. */
    private data class CpuStyle(val specialBias: Float, val pressureBias: Float)

    private fun cpuStyle(id: SfFighterId): CpuStyle = when (id) {
        SfFighterId.ROBOT,
        SfFighterId.CHARRO_NEGRO,
        SfFighterId.LA_LLORONA,
        SfFighterId.LA_TZITZIMIME,
        SfFighterId.YOALLI_EHECATL,
        SfFighterId.LA_PRESIDENTA,
        -> CpuStyle(specialBias = 1.25f, pressureBias = 0.90f)

        SfFighterId.ESCOMBOY,
        SfFighterId.ESCOMGIRL,
        SfFighterId.POLICIA_CDMX_HOMBRE,
        SfFighterId.POLICIA_CDMX,
        SfFighterId.POLICIA_GRANADERO_HOMBRE,
        SfFighterId.POLICIA_GRANADERO_MUJER,
        -> CpuStyle(specialBias = 0.80f, pressureBias = 1.12f)

        else -> CpuStyle(specialBias = 1f, pressureBias = 1f)
    }

    // ------------------------------------------------------------------
    // Decisiones por dificultad
    // ------------------------------------------------------------------

    /** BÁSICA — aprendible: lenta, pocos golpes, sin poderes. */
    private fun basicCpuDecision(sim: Sim, selfIndex: Int): SfInput {
        val me = sim.fighter(selfIndex)
        val foe = sim.fighter(1 - selfIndex)
        val dist = abs(me.x - foe.x)
        val roll = Random.nextFloat()
        if (dist < CPU_CLINCH_DIST && roll < 0.55f) return cpuRetreatFlags(me, foe)
        return when {
            dist > 190f -> if (roll < 0.7f) cpuApproach(me, foe) else SfInput()
            dist > 95f -> when {
                roll < 0.5f -> cpuApproach(me, foe)
                roll < 0.78f -> SfInput()
                else -> cpuRetreatFlags(me, foe)
            }
            else -> when {
                roll < 0.28f -> cpuAttack(SfAttackStrength.LIGHT, punch = Random.nextBoolean())
                roll < 0.55f -> cpuRetreatFlags(me, foe)
                else -> SfInput()
            }
        }
    }

    /** NORMAL — pelea real: acerca, golpea, special raro, clinch break. */
    private fun normalCpuDecision(sim: Sim, selfIndex: Int, now: Long): SfInput {
        val me = sim.fighter(selfIndex)
        val foe = sim.fighter(1 - selfIndex)
        val dist = abs(me.x - foe.x)
        val roll = Random.nextFloat()
        val corner = isNearStageCorner(me.x)
        val specialBias = cpuStyle(me.id).specialBias

        if (now < cpuWantsSpaceUntilMs[selfIndex] && dist < CPU_MELEE_DIST) {
            return if (roll < 0.7f) cpuRetreatFlags(me, foe) else variedCpuAttack(selfIndex)
        }
        if (corner && dist > 50f) return cpuApproach(me, foe)
        if (dist < CPU_CLINCH_DIST) return cpuClinchBreak(me, foe, now, selfIndex)

        if (hasIncomingFireball(sim, me, selfIndex, 240f) && !me.isAirborne) {
            return if (roll < 0.7f) cpuJumpIn(me, foe) else cpuRetreatFlags(me, foe)
        }
        if (foe.state in cpuThreatStates && dist < 140f) {
            return when {
                roll < 0.42f -> cpuRetreatFlags(me, foe)
                dist < 95f && roll < 0.72f -> cpuAttack(SfAttackStrength.LIGHT, punch = true)
                else -> cpuJumpBack(me, foe)
            }
        }
        if (foe.isAirborne && dist < 130f) {
            return cpuAttack(SfAttackStrength.HEAVY, punch = true)
        }
        if (foe.state in cpuPunishStates && dist < CPU_MELEE_DIST) {
            return cpuAttack(SfAttackStrength.MEDIUM, punch = Random.nextBoolean())
        }

        return when {
            dist > CPU_MID_DIST -> when {
                !ownFireballActive(sim, selfIndex) && roll < 0.16f ->
                    SfInput(special = SfAttackStrength.LIGHT)
                roll < 0.30f -> cpuJumpIn(me, foe)
                else -> cpuApproach(me, foe)
            }
            dist > CPU_MELEE_DIST -> when {
                roll < 0.58f -> cpuApproach(me, foe)
                !ownFireballActive(sim, selfIndex) &&
                    now >= specialCooldownUntil[selfIndex] &&
                    roll < 0.58f + 0.10f * specialBias -> SfInput(special = SfAttackStrength.LIGHT)
                roll < 0.88f -> cpuJumpIn(me, foe)
                else -> cpuRetreatFlags(me, foe)
            }
            else -> when { // melee
                roll < 0.72f + 0.12f * cpuIntensity -> variedCpuAttack(selfIndex)
                roll < 0.88f -> cpuRetreatFlags(me, foe) // micro-spacing
                else -> cpuJumpIn(me, foe)
            }
        }
    }

    /**
     * AVANZADA + PESADILLA — núcleo SF:
     * defense (fireball/anti-air/block) → punish → clinch/spacing → pressure por rango.
     * @param nightmare más agresivo (PESADILLA / IA vs IA show).
     */
    private fun smartCpuDecision(sim: Sim, selfIndex: Int, now: Long, nightmare: Boolean): SfInput {
        val me = sim.fighter(selfIndex)
        val foe = sim.fighter(1 - selfIndex)
        val dist = abs(me.x - foe.x)
        val roll = Random.nextFloat()
        val corner = isNearStageCorner(me.x)
        val aiVs = _state.value.aiVsAi
        val ownFb = ownFireballActive(sim, selfIndex)
        val style = cpuStyle(me.id)

        val specialFarBase = when {
            nightmare && aiVs -> 0.24f
            nightmare -> 0.18f
            else -> 0.14f + 0.08f * cpuIntensity
        }
        val specialMidBase = when {
            nightmare && aiVs -> 0.16f
            nightmare -> 0.11f
            else -> 0.08f + 0.05f * cpuIntensity
        }
        val specialFar = (specialFarBase * style.specialBias).coerceAtMost(0.34f)
        val specialMid = (specialMidBase * style.specialBias).coerceAtMost(0.24f)
        val blockChance = if (nightmare) 0.55f else 0.72f + 0.1f * cpuIntensity
        val attackMelee = ((if (nightmare) 0.78f else 0.70f) * style.pressureBias)
            .coerceIn(0.62f, 0.88f)

        // Espacio pedido tras clinch
        if (now < cpuWantsSpaceUntilMs[selfIndex] && dist < CPU_MID_DIST) {
            return when {
                roll < 0.55f -> cpuRetreatFlags(me, foe)
                roll < 0.78f -> variedCpuAttack(selfIndex)
                else -> cpuJumpIn(me, foe)
            }
        }

        // Esquina: salir hacia el rival (nunca spamear desde el borde)
        if (corner && dist > 45f) return cpuApproach(me, foe)

        // Clinch / “pegaditos”
        if (dist < CPU_CLINCH_DIST) return cpuClinchBreak(me, foe, now, selfIndex)

        // Fireball entrante
        if (hasIncomingFireball(sim, me, selfIndex, if (nightmare) 300f else 260f) && !me.isAirborne) {
            return when {
                roll < 0.50f -> cpuJumpIn(me, foe)
                roll < 0.78f -> SfInput(up = true) // jump neutral
                else -> cpuRetreatFlags(me, foe) // block / walk-back
            }
        }

        // Anti-aéreo
        if (foe.isAirborne && dist < (if (nightmare) 170f else 145f)) {
            return cpuAttack(SfAttackStrength.HEAVY, punch = true)
        }

        // Bloqueo ante amenaza (mid); de cerca tradea
        if (foe.state in cpuThreatStates) {
            when {
                dist in 95f..190f && roll < blockChance -> return cpuRetreatFlags(me, foe) // block walk-back
                dist < 95f && roll < 0.28f -> return cpuRetreatFlags(me, foe)
                dist < 95f && roll < 0.68f -> return cpuAttack(SfAttackStrength.LIGHT, punch = true)
            }
        }

        // Castigo recovery
        if (foe.state in cpuPunishStates && dist < (if (nightmare) 145f else 125f)) {
            return cpuAttack(
                if (nightmare || roll < 0.55f) SfAttackStrength.HEAVY else SfAttackStrength.MEDIUM,
                punch = Random.nextBoolean(),
            )
        }

        // Footsies / presión por rango (🆕 2026-07-18j: golpes con memoria anti-repetición y
        // fuerza del special al azar — la pelea se ve VARIADA, no el mismo ataque en bucle)
        return when {
            dist > CPU_MID_DIST -> when {
                !corner && !ownFb && now >= specialCooldownUntil[selfIndex] && roll < specialFar ->
                    SfInput(special = SfAttackStrength.entries.random())
                aiVs && roll < specialFar + 0.22f -> cpuJumpIn(me, foe)
                !aiVs && roll < 0.28f -> cpuJumpIn(me, foe)
                !aiVs && roll < 0.38f -> cpuRetreatFlags(me, foe) // baitear
                else -> cpuApproach(me, foe)
            }
            dist > CPU_MELEE_DIST -> when {
                !ownFb && now >= specialCooldownUntil[selfIndex] && roll < specialMid ->
                    SfInput(special = SfAttackStrength.MEDIUM)
                aiVs && roll < 0.76f -> cpuApproach(me, foe)
                aiVs && roll < 0.90f -> cpuJumpIn(me, foe)
                aiVs -> cpuApproach(me, foe)
                roll < 0.55f -> cpuApproach(me, foe)
                roll < 0.72f -> cpuJumpIn(me, foe)
                roll < 0.86f -> cpuRetreatFlags(me, foe)
                else -> cpuApproach(me, foe)
            }
            else -> when { // melee range (no clinch)
                roll < attackMelee + 0.1f * cpuIntensity -> variedCpuAttack(selfIndex)
                roll < 0.90f -> cpuRetreatFlags(me, foe) // tick throw-ish spacing
                else -> cpuJumpIn(me, foe)
            }
        }
    }

    /** Arma un SfInput de golpe (puño o patada) de la fuerza pedida. */
    private fun cpuAttack(strength: SfAttackStrength, punch: Boolean): SfInput = if (punch) {
        when (strength) {
            SfAttackStrength.LIGHT -> SfInput(lightPunch = true)
            SfAttackStrength.MEDIUM -> SfInput(mediumPunch = true)
            SfAttackStrength.HEAVY -> SfInput(heavyPunch = true)
        }
    } else {
        when (strength) {
            SfAttackStrength.LIGHT -> SfInput(lightKick = true)
            SfAttackStrength.MEDIUM -> SfInput(mediumKick = true)
            SfAttackStrength.HEAVY -> SfInput(heavyKick = true)
        }
    }

    /**
     * 🆕 (2026-07-18j) Golpe al azar SIN repetir el último (firma fuerza×tipo por peleadór).
     * Sustituye a randomCpuAttack(): con 6 combos y memoria de 1, la IA mezcla puños/patadas
     * y fuerzas en vez de encadenar el MISMO ataque una y otra vez.
     */
    private fun variedCpuAttack(selfIndex: Int): SfInput {
        val i = selfIndex.coerceIn(0, 1)
        val history = cpuAttackHistory[i]
        val sig = (0 until 6).filterNot(history::contains).ifEmpty { (0 until 6).toList() }.random()
        history.addLast(sig)
        while (history.size > 3) history.removeFirst()
        return cpuAttack(SfAttackStrength.entries[sig / 2], punch = sig % 2 == 0)
    }

    // ------------------------------------------------------------------
    // Intenciones de la View
    // ------------------------------------------------------------------

    /** Joystick de POW: 8 direcciones a partir del ángulo (arriba = +sin). */
    fun onJoystickMove(angleRad: Double) {
        val cosA = kotlin.math.cos(angleRad)
        val sinA = kotlin.math.sin(angleRad)
        joyRight = cosA > 0.38
        joyLeft = cosA < -0.38
        joyUp = sinA > 0.5
        joyDown = sinA < -0.5
        joyLastMs = SystemClock.elapsedRealtime()
    }

    /** Encola un ataque (usado por los botones del diamante Xbox). */
    fun onAttackPressed(strength: SfAttackStrength, type: SfAttackType) {
        val s = _state.value
        if (s.battleEnded || s.isPaused || s.showExitDialog) return
        pendingAttacks.addLast(strength to type)
    }

    /**
     * Patada del botón A (diamante Xbox de 4 botones vs los 6 ataques del arcade):
     * la FUERZA depende del joystick — neutro = ligera, adelante = media, atrás = fuerte.
     */
    fun onKickPressed() {
        val s = _state.value
        if (s.battleEnded || s.isPaused || s.showExitDialog) return
        val idle = SystemClock.elapsedRealtime() - joyLastMs > JOYSTICK_IDLE_MS
        val dir = s.player.direction
        val forward = !idle && ((joyRight && dir == SfDirection.RIGHT) || (joyLeft && dir == SfDirection.LEFT))
        val backward = !idle && ((joyLeft && dir == SfDirection.RIGHT) || (joyRight && dir == SfDirection.LEFT))
        val strength = when {
            forward -> SfAttackStrength.MEDIUM
            backward -> SfAttackStrength.HEAVY
            else -> SfAttackStrength.LIGHT
        }
        pendingAttacks.addLast(strength to SfAttackType.KICK)
    }

    /** Recorre todos los poderes Grok disponibles; cada toque ejecuta el siguiente. */
    fun onBonusPowerPressed() {
        val s = _state.value
        if (s.battleEnded || s.isPaused || s.showExitDialog) return
        val count = usableBonusPowerCount(s.player.id)
        if (count <= 0) return
        bonusPowerCursor = bonusPowerCursor % count + 1
        pendingBonusPower = bonusPowerCursor
    }

    fun requestExit() {
        _state.value = _state.value.copy(showExitDialog = true)
    }

    fun dismissExitDialog() {
        lastRealMs = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(showExitDialog = false)
    }

    fun togglePause() {
        lastRealMs = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }

    /** Pausa FORZADA (bloquear el celular / app a segundo plano): nunca des-pausa. */
    fun forcePause() {
        val s = _state.value
        if (!s.isPaused && !s.inCharacterSelect && !s.showEndMenu) {
            _state.value = s.copy(isPaused = true)
            // 🆕 Guardar progreso arcade al ir a segundo plano (barato: 1 putString async).
            // NO se guarda cada tick → sin lag en gama baja.
            if (s.arcadeActive) persistArcadeSession(s)
        }
    }

    /** Snapshot ligero de la pelea arcade (ids + rondas). apply() = no bloquea UI. */
    private fun persistArcadeSession(s: StreetFighterState = _state.value) {
        if (!s.arcadeActive || arcadeLadder.isEmpty()) return
        arcadeRepo.saveSession(
            SfArcadeRepository.ArcadeSession(
                playerId = arcadePlayer.name,
                step = s.arcadeStep,
                total = s.arcadeTotal,
                ladderRivals = arcadeLadder.map { it.rival.name },
                mapFile = s.arcadeMapFile,
                playerRoundWins = s.playerRoundWins,
                cpuRoundWins = s.cpuRoundWins,
                difficulty = s.cpuDifficulty.name,
                paused = true,
            ),
        )
    }

    /**
     * Retoma la pelea arcade guardada (si hay). Devuelve true si se restauró.
     * Reconstruye la escalera desde los ids guardados (sin re-aleatorizar).
     */
    fun resumeArcadeSession(): Boolean {
        val ses = arcadeRepo.loadSession() ?: return false
        val player = runCatching { SfFighterId.valueOf(ses.playerId) }.getOrNull() ?: return false
        val rivals = ses.ladderRivals.mapNotNull { n ->
            runCatching { SfFighterId.valueOf(n) }.getOrNull()
        }
        if (rivals.isEmpty() || ses.step !in 1..rivals.size) return false
        arcadePlayer = player
        val savedDiff = runCatching { SfCpuDifficulty.valueOf(ses.difficulty) }
            .getOrDefault(SfCpuDifficulty.NORMAL)
        // Inferir base del arcade (la pelea puede haber subido a jefe)
        arcadeChosenDifficulty = when (savedDiff) {
            SfCpuDifficulty.PESADILLA -> SfCpuDifficulty.AVANZADA
            else -> savedDiff
        }
        arcadeLadder = rivals.mapIndexed { i, rival ->
            val n = i + 1
            SfArcadeLadder.Step(
                index = n,
                rival = rival,
                mapFile = SfStageCatalog.mapForRival(rival, arcadeChosenDifficulty),
                isBoss = n >= rivals.size - 2,
                isFinal = n == rivals.size,
            )
        }
        arcadeMapCurrent = ses.mapFile
            ?: arcadeLadder.getOrNull(ses.step - 1)?.mapFile
            ?: SfArcadeLadder.MAP_FIRST
        val stepData = arcadeLadder[ses.step - 1]
        val diff = savedDiff
        resetInternals()
        cpuIntensity = if (arcadeLadder.size > 1) {
            0.20f + 0.80f * (ses.step - 1).toFloat() / (arcadeLadder.size - 1)
        } else {
            1f
        }
        roundIntroUntilMs = ROUND_INTRO_MS
        val base = StreetFighterState()
        _state.value = base.copy(
            player = base.player.copy(id = player),
            cpu = base.cpu.copy(id = stepData.rival),
            inCharacterSelect = false,
            showRoundIntro = true,
            isPaused = true, // reanuda con overlay PAUSA (Continuar)
            cpuDifficulty = diff,
            arcadeActive = true,
            arcadeStep = ses.step,
            arcadeTotal = ses.total.coerceAtLeast(rivals.size),
            arcadeRival = stepData.rival,
            arcadeMapFile = arcadeMapCurrent,
            playerRoundWins = ses.playerRoundWins.coerceIn(0, ROUNDS_TO_WIN),
            cpuRoundWins = ses.cpuRoundWins.coerceIn(0, ROUNDS_TO_WIN),
            arcadeOutcome = SfArcadeOutcome.NONE,
        )
        return true
    }

    /** Descarta la pelea a medias (el usuario elige "Nueva partida"). */
    fun discardArcadeSession() {
        arcadeRepo.clearSession()
    }

    /** Revancha: offline reinicia ya; online la PIDE (arranca cuando la pidan los dos). */
    fun restartBattle() {
        if (isOnline) {
            transport?.requestRematch()
            return
        }
        val s = _state.value
        // IA vs IA: revancha con los mismos dos peleadores a PESADILLA
        if (s.aiVsAi) {
            startAiVsAi(s.player.id, s.cpu.id, s.cpuDifficulty)
            return
        }
        // Revancha offline: MISMA dificultad de CPU que la pelea anterior
        startBattle(playerId = s.player.id, cpuId = s.cpu.id, difficulty = s.cpuDifficulty)
    }

    /**
     * Selector: fija el personaje. Online avisa y espera al rival; offline arranca ya
     * contra `rivalId` (🆕 el jugador también ELIGE al enemigo; null = Ken/Ryu default)
     * con la `difficulty` elegida (🆕 paso DIFICULTAD del flujo pre-pelea; online se ignora).
     */
    fun selectCharacter(
        id: SfFighterId,
        rivalId: SfFighterId? = null,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,
    ) {
        if (isOnline) {
            myOnlineChar = id
            transport?.selectCharacter(id.name)
            return // la pelea arranca cuando el servidor mande FIGHT_START
        }
        // Rival default (rivalId null): un peleador POW distinto al elegido.
        val cpuId = rivalId ?: if (id == SfFighterId.PRANKEDY) SfFighterId.REY_GRUPERO else SfFighterId.PRANKEDY
        startBattle(playerId = id, cpuId = cpuId, difficulty = difficulty)
    }

    /** Vuelve al selector de personaje (desde el menú de fin de pelea). */
    fun backToCharacterSelect() {
        if (isOnline) {
            cancelOnline()
            return
        }
        resetInternals()
        _state.value = StreetFighterState() // inCharacterSelect = true por default
    }

    private fun startBattle(
        playerId: SfFighterId,
        cpuId: SfFighterId,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,
    ) {
        resetInternals()
        roundIntroUntilMs = ROUND_INTRO_MS // banner "RONDA 1 / PELEA" (gameNow arranca en 0)
        val base = StreetFighterState()
        _state.value = base.copy(
            player = base.player.copy(id = playerId),
            cpu = base.cpu.copy(id = cpuId),
            inCharacterSelect = false,
            showRoundIntro = true,
            cpuDifficulty = difficulty,
            // aiVsAi queda false por default del base (VS / práctica humanos)
        )
    }

    /**
     * 🆕 IA vs IA (CPU vs CPU) a dificultad PESADILLA e intensidad máxima: para grabar
     * en video / espectáculo. Copia de [startBattle] con `aiVsAi = true`. Solo OFFLINE.
     * `a` = índice 0 (izquierda), `b` = índice 1 (derecha). buildPlayerInput NO se usa.
     */
    fun startAiVsAi(
        a: SfFighterId,
        b: SfFighterId,
        difficulty: SfCpuDifficulty = SfCpuDifficulty.PESADILLA,
        intensity: Float = 1f,
    ) {
        resetInternals()
        // Intensidad al máximo (igual que la final del arcade); resetInternals la deja en 0
        cpuIntensity = intensity.coerceIn(0f, 1f)
        roundIntroUntilMs = ROUND_INTRO_MS // banner "RONDA 1 / PELEA" (gameNow arranca en 0)
        val base = StreetFighterState()
        _state.value = base.copy(
            player = base.player.copy(id = a),
            cpu = base.cpu.copy(id = b),
            inCharacterSelect = false,
            showRoundIntro = true,
            cpuDifficulty = difficulty,
            aiVsAi = true,
        )
    }

    // ------------------------------------------------------------------
    // 🆕 AUTOJUEGO (gauntlet): recorre muchas peleas IA vs IA seguidas para PROBAR a todos los
    // peleadores y volcar un reporte de assets rotos (atascos/estancamientos detectados por
    // watchStuck/watchStalemate). Al terminar escribe un .txt y muestra el reporte en pantalla.
    // ------------------------------------------------------------------

    /** Bot 1: TODOS contra TODOS (round-robin). ~N² peleas — déjalo corriendo/grabando. */
    fun startGauntletRoundRobin() {
        showcaseMode = false
        val roster = SfArcadeLadder.ALL_PARTICIPANTS
        val q = ArrayDeque<GauntletFight>()
        for (a in roster) for (b in roster) if (a != b) q.add(GauntletFight(a, b))
        beginGauntlet(q)
    }

    /**
     * Bot 3: SHOWCASE de assets — cada peleador recorre TODAS sus animaciones (caminar, saltar,
     * agacharse, giros, los 6 golpes, especial L/M/F, poderes y 🆕 2026-07-18j: también los
     * HURT_*, KO, VICTORY y la metamorfosis de La Presidenta) reproduciendo sus sonidos, para
     * verlos/oírlos y detectar los rotos. Además corre una AUDITORÍA ESTÁTICA por peleadór
     * (animaciones faltantes/vacías, frames rotos, .ogg del special y SFX del tema).
     * Recorre TODOS los peleadores. (No es pelea real.)
     */
    fun startShowcase() {
        showcaseMode = true
        showcaseSpeed = 1f
        val q = ArrayDeque<GauntletFight>()
        SfArcadeLadder.ALL_PARTICIPANTS.forEach {
            q.add(GauntletFight(it, it)) // Espejo: se ve la animación en ambos.
        }
        beginGauntlet(q)
    }

    /** Bot 2: las 9 campañas completas (3 protagonistas × 3 dificultades), 135 peleas reales. */
    fun startGauntletArcade() {
        showcaseMode = false
        val difficulties = listOf(
            SfCpuDifficulty.BASICA,
            SfCpuDifficulty.NORMAL,
            SfCpuDifficulty.AVANZADA,
        )
        val q = ArrayDeque<GauntletFight>()
        SfArcadeLadder.STARTERS.forEach { player ->
            difficulties.forEach { difficulty ->
                val ladder = SfArcadeLadder.build(player, difficulty)
                ladder.forEach { step ->
                    q.add(
                        GauntletFight(
                            player = player,
                            rival = step.rival,
                            difficulty = SfArcadeLadder.difficultyForStep(difficulty, step),
                            intensity = SfArcadeLadder.intensityForStep(step.index, ladder.size),
                            mapFile = step.mapFile,
                        ),
                    )
                }
            }
        }
        beginGauntlet(q)
    }

    private fun beginGauntlet(q: ArrayDeque<GauntletFight>) {
        assetIssues.clear()
        gauntletQueue.clear()
        gauntletQueue.addAll(q)
        gauntletTotal = q.size
        gauntletDone = 0
        gauntletKoRounds = 0
        gauntletTimeoutRounds = 0
        gauntletActive = true
        _state.update { it.copy(gauntletFinished = false, gauntletReport = emptyList(), gauntletReportPath = null) }
        if (showcaseMode) auditThemeSounds() // 🆕 SFX compartidos del tema (una vez por corrida)
        startNextGauntletFight()
    }

    private fun startNextGauntletFight() {
        val next = gauntletQueue.removeFirstOrNull()
        if (next == null) {
            finishGauntlet()
            return
        }
        gauntletDone++
        startAiVsAi(next.player, next.rival, next.difficulty, next.intensity)
        gauntletActive = true
        if (showcaseMode) {
            showcaseStep = -1 // el primer tick lo sube a 0 (paso "caminar")
            showcaseStepUntilMs = 0L
            showcaseFiredStep = -2
            showcaseForcedState = null
            // Cap POR PASOS: el guion completo (con extras/metamorfosis) supera los 60 s fijos
            gauntletFightCapCurMs = (showcaseTotalSteps(next.player) + 3L) * showcaseStepMs + 4000L
            // 🆕 Auditoría estática del peleadór (anims + frames + special_<id>.ogg)
            auditFighterAssets(next.player)
        } else {
            gauntletFightCapCurMs = gauntletFightCapMs
        }
        // 🆕 (2026-07-18k) Cada pelea del autojuego usa el MAPA HOGAR del peleadór en turno:
        // showcase = hogar de DÍA del peleadór mostrado (se ve claro para QA); gauntlets IA vs IA
        // = hogar del rival en su variante APOCALIPSIS (acorde a PESADILLA). Así el bot también
        // recorre/prueba los fondos.
        val mapFile = if (showcaseMode) {
            SfStageCatalog.homeStage(next.player).file(SfStageCatalog.Lighting.DAY)
        } else {
            next.mapFile ?: SfStageCatalog.mapForRival(next.rival, next.difficulty)
        }
        _state.update {
            it.copy(
                gauntletRunning = true,
                gauntletProgress = "$gauntletDone/$gauntletTotal",
                showcaseRunning = showcaseMode,
                showcaseSpeed = showcaseSpeed,
                gauntletMapFile = mapFile,
            )
        }
    }

    /**
     * SALTAR (View): da por terminado el bloque del peleador actual y avanza en el siguiente
     * tick. No basta con cortar la pose: el tope temporal también debe quedar satisfecho para
     * que el showcase no espere el resto del guion con el personaje inmóvil.
     */
    fun skipShowcaseFighter() {
        if (!gauntletActive || !showcaseMode) return
        showcaseForcedState = null
        showcaseStep = showcaseTotalSteps(_state.value.player.id) + 1
        showcaseFiredStep = showcaseStep
        showcaseStepUntilMs = _state.value.gameTimeMs
    }

    /** Termina solo la animación actual y conserva al mismo peleador para el paso siguiente. */
    fun skipToNextShowcaseAnimation() {
        if (!gauntletActive || !showcaseMode) return
        val now = _state.value.gameTimeMs
        showcaseForcedState = null
        showcaseFiredStep = -2
        showcaseStepUntilMs = now
        _state.update {
            it.copy(
                player = resetShowcaseFighter(it.player, now),
                cpu = resetShowcaseFighter(it.cpu, now),
            )
        }
    }

    /** Alterna 1x → 2x → 4x para acelerar todo el showcase visual. */
    fun cycleShowcaseSpeed() {
        if (!gauntletActive || !showcaseMode) return
        val index = SHOWCASE_SPEEDS.indexOf(showcaseSpeed).coerceAtLeast(0)
        showcaseSpeed = SHOWCASE_SPEEDS[(index + 1) % SHOWCASE_SPEEDS.size]
        _state.update { it.copy(showcaseSpeed = showcaseSpeed) }
    }

    private fun resetShowcaseFighter(fighter: SfFighter, now: Long): SfFighter =
        withAnimationFrame(
            fighter.copy(
                state = SfFighterState.IDLE,
                velocityX = 0f,
                velocityY = 0f,
                slideVelocity = 0f,
                slideFriction = 0f,
                attackStruck = false,
                fireballFired = false,
                y = SfConstants.STAGE_FLOOR,
            ),
            frame = 0,
            now = now,
        )

    /** Se llama al inicio del tick: encadena la siguiente pelea al terminar el combate o al vencer el tope. */
    private fun maybeAdvanceGauntlet(now: Long): Boolean {
        val showcaseDone = showcaseMode && showcaseStep > showcaseTotalSteps(_state.value.player.id)
        val ended = matchOver && now >= endMenuAtMs
        val timedOut = now >= gauntletFightCapCurMs
        if (!showcaseDone && !ended && !timedOut) return false
        if (timedOut && !ended && !showcaseDone) {
            val s = _state.value
            logAssetIssue(
                "TIMEOUT ${s.player.id.name} vs ${s.cpu.id.name}: la pelea no terminó en " +
                    "${gauntletFightCapCurMs / 1000}s (posible atasco/estancamiento)",
            )
        }
        startNextGauntletFight()
        return true
    }

    private fun finishGauntlet() {
        gauntletActive = false
        showcaseMode = false
        val issues = assetIssues.toList()
        val path = writeGauntletReport(issues)
        _state.update {
            it.copy(
                gauntletRunning = false,
                showcaseRunning = false,
                gauntletMapFile = null,
                gauntletFinished = true,
                gauntletReport = issues,
                gauntletReportPath = path,
                inCharacterSelect = true, // al terminar, vuelve al selector (con el reporte encima)
            )
        }
    }

    private fun writeGauntletReport(issues: List<String>): String? = runCatching {
        val dir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val file = java.io.File(dir, "sf_diagnostico_$stamp.txt")
        val header = "POW — Diagnóstico IA vs IA (autojuego)\n" +
            "Peleas: $gauntletDone/$gauntletTotal\n" +
            "Rondas por KO: $gauntletKoRounds\n" +
            "Rondas por tiempo: $gauntletTimeoutRounds\n" +
            "Problemas: ${issues.size}\n\n"
        file.writeText(header + if (issues.isEmpty()) "Sin problemas detectados." else issues.joinToString("\n"))
        file.absolutePath
    }.getOrNull()

    /** Detiene el gauntlet en curso y muestra el reporte con lo detectado hasta ahora. */
    fun stopGauntlet() {
        if (!gauntletActive) return
        gauntletQueue.clear()
        finishGauntlet()
    }

    /** Cierra el overlay del reporte del gauntlet. */
    fun dismissGauntletReport() {
        _state.update { it.copy(gauntletFinished = false) }
    }

    /**
     * 🆕 (2026-07-18j) Estados EXTRA del showcase tras los poderes: inalcanzables por input.
     * Orden pensado: giros primero (terminan en IDLE/CROUCH→sube solo), luego los 6 HURT,
     * y al final KO (queda tendido) → VICTORY (se levanta a celebrar).
     */
    private val showcaseExtraStates = listOf(
        SfFighterState.IDLE_TURN, SfFighterState.CROUCH_TURN,
        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
        SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
        SfFighterState.KO, SfFighterState.VICTORY,
    )

    /**
     * Último índice de paso del showcase para [id]: 0..12 = moves por input, 13.. = poderes,
     * luego [showcaseExtraStates] forzados y — SOLO La Presidenta — la metamorfosis final
     * (BONUS_POWER_11 → termina convertida en Yoalli).
     */
    private fun showcaseTotalSteps(id: SfFighterId): Int =
        12 + usableBonusPowerCount(id) + showcaseExtraStates.size +
            (if (id == SfFighterId.LA_PRESIDENTA) 1 else 0)

    /**
     * Input SCRIPTED del showcase: avanza un paso cada [showcaseStepMs] y ejecuta la animación
     * correspondiente (una vez por paso). Los botones son de un tick (fireNow); las direcciones se
     * sostienen. Los pasos EXTRA no emiten input: dejan el estado en [showcaseForcedState] y el
     * tick lo aplica con [forceShowcaseState]. watchStuck detecta las animaciones que no terminan.
     */
    private fun showcaseInput(now: Long, f: SfFighter): SfInput {
        val id = f.id
        if (now >= showcaseStepUntilMs) {
            showcaseStep++
            showcaseStepUntilMs = now + showcaseStepMs
        }
        val step = showcaseStep
        // 🆕 (2026-07-18k) Los pasos de UN toque (salto/golpes/specials/poderes) esperan a que
        // el peleadór esté en IDLE para disparar: p. ej. el SALTO se PERDÍA porque el input caía
        // mientras aún subía del agachado (CROUCH→CROUCH_UP) y JUMP_START no es válido desde ahí.
        // Los pasos sostenidos (0..2) y los forzados (extras) disparan de inmediato.
        val oneShotStep = step in 3..(12 + usableBonusPowerCount(id))
        val fireNow = step != showcaseFiredStep &&
            (!oneShotStep || f.state == SfFighterState.IDLE)
        if (fireNow) showcaseFiredStep = step
        return when (step) {
            0 -> SfInput(forward = true)
            1 -> SfInput(backward = true)
            2 -> SfInput(down = true)
            3 -> if (fireNow) SfInput(up = true) else SfInput()
            4 -> if (fireNow) SfInput(lightPunch = true) else SfInput()
            5 -> if (fireNow) SfInput(mediumPunch = true) else SfInput()
            6 -> if (fireNow) SfInput(heavyPunch = true) else SfInput()
            7 -> if (fireNow) SfInput(lightKick = true) else SfInput()
            8 -> if (fireNow) SfInput(mediumKick = true) else SfInput()
            9 -> if (fireNow) SfInput(heavyKick = true) else SfInput()
            10 -> if (fireNow) SfInput(special = SfAttackStrength.LIGHT) else SfInput()
            11 -> if (fireNow) SfInput(special = SfAttackStrength.MEDIUM) else SfInput()
            12 -> if (fireNow) SfInput(special = SfAttackStrength.HEAVY) else SfInput()
            else -> {
                val usable = usableBonusPowerCount(id)
                val bp = step - 12 // paso 13 → poder 1
                if (bp in 1..usable) {
                    if (fireNow) SfInput(bonusPower = bp) else SfInput()
                } else {
                    // 🆕 Pasos FORZADOS: giros, HURT_*, KO, VICTORY y metamorfosis Presidenta
                    if (fireNow) {
                        val extraIdx = step - 13 - usable
                        showcaseForcedState = when {
                            extraIdx in showcaseExtraStates.indices -> showcaseExtraStates[extraIdx]
                            extraIdx == showcaseExtraStates.size &&
                                id == SfFighterId.LA_PRESIDENTA -> SfFighterState.BONUS_POWER_11
                            else -> null
                        }
                    }
                    SfInput()
                }
            }
        }
    }

    /**
     * 🆕 (2026-07-18j) Fuerza un estado del guion del showcase saltándose validFrom (QA de
     * assets, no gameplay). Si la animación NO existe en el JSON del peleadór, lo reporta y
     * no fuerza nada (evita el crash de animOf con getValue).
     */
    private fun forceShowcaseState(sim: Sim, idx: Int, st: SfFighterState, now: Long) {
        val f = sim.fighter(idx)
        if (dataFor(f).animations[st.jsKey].isNullOrEmpty()) {
            logAssetIssue("FALTA ANIM ${f.id.name}: ${st.jsKey}")
            return
        }
        var nf = f.copy(
            state = st, velocityX = 0f, velocityY = 0f,
            slideVelocity = 0f, slideFriction = 0f,
            attackStruck = false, fireballFired = false,
            y = SfConstants.STAGE_FLOOR,
        )
        nf = withAnimationFrame(nf, 0, now)
        sim.setFighter(idx, nf)
        // 🆕 (2026-07-18k) AUDIO del guion: los estados forzados NO pasan por applyAttackHit/
        // changeState, así que su sonido se emite aquí reutilizando los .ogg correctos del tema
        // (pedido del dueño: mejor repetir un audio correcto que dejar la animación muda).
        // Solo idx 0: el guion es espejo y emitir dos veces duplicaba el volumen.
        if (idx == 0) when (st) {
            SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_BODY_LIGHT ->
                _soundEvents.tryEmit("light-punch-hit")
            SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_BODY_MEDIUM ->
                _soundEvents.tryEmit("medium-punch-hit")
            SfFighterState.HURT_HEAD_HEAVY, SfFighterState.HURT_BODY_HEAVY ->
                _soundEvents.tryEmit("heavy-punch-hit")
            SfFighterState.KO -> _soundEvents.tryEmit("heavy-kick-hit") // golpe final (thud)
            SfFighterState.VICTORY -> emitSpecialVoice(nf.id, now) // su voz al celebrar
            SfFighterState.BONUS_POWER_11 -> emitSpecialVoice(nf.id, now) // metamorfosis
            else -> Unit // giros: sin SFX (tampoco lo tienen en pelea real)
        }
    }

    /**
     * 🆕 AUDITORÍA ESTÁTICA por peleadór (2026-07-18j): recorre TODAS las claves de animación
     * esperadas (SfFighterState.jsKey, poderes solo hasta su bonusPowerCount) y reporta las que
     * FALTEN o estén vacías, y las que referencien frames inexistentes; además verifica la voz
     * de su special (special_<id>.ogg). Corre al armar cada peleadór del showcase.
     */
    private fun auditFighterAssets(id: SfFighterId) {
        val data = runCatching { SfFrameCatalog.load(appContext, id) }.getOrElse {
            logAssetIssue("JSON ILEGIBLE ${id.name} (${id.jsonAsset}): ${it.message}")
            return
        }
        for (st in SfFighterState.entries) {
            val bonusIdx = st.bonusPowerIndex()
            if (bonusIdx != null && bonusIdx > id.bonusPowerCount) continue // poderes que no tiene
            val anim = data.animations[st.jsKey]
            if (anim.isNullOrEmpty()) {
                logAssetIssue("FALTA ANIM ${id.name}: ${st.jsKey}")
                continue
            }
            anim.forEach { fr ->
                if (fr.frameKey !in data.frames) {
                    logAssetIssue("FRAME ROTO ${id.name}: ${st.jsKey} usa '${fr.frameKey}' y no existe")
                }
            }
            val visibleSteps = anim.filter { it.delay >= 0 }
            val uniqueSources = visibleSteps.mapNotNull { frame ->
                data.frames[frame.frameKey]?.src
            }.distinct()
            if (visibleSteps.size >= 3 && uniqueSources.size == 1) {
                logAssetIssue(
                    "ANIM RELLENO ${id.name}: ${st.jsKey} repite una sola pose " +
                        "en ${visibleSteps.size} pasos",
                )
            }
        }
        if (!sfAssetExists("STREETFIGHTER/SOUNDS/${specialSfxKey(id)}.ogg")) {
            logAssetIssue("FALTA SONIDO ${specialSfxKey(id)}.ogg (${id.name})")
        }
    }

    /** 🆕 Verifica los .ogg COMPARTIDOS del tema (golpes/impactos/land/hadouken) + música. */
    private fun auditThemeSounds() {
        SF_CLASSIC_THEME.soundKeys.forEach { key ->
            if (!sfAssetExists("${SF_CLASSIC_THEME.soundsDir}$key.ogg")) {
                logAssetIssue("FALTA SFX DEL TEMA: $key.ogg")
            }
        }
        if (!sfAssetExists(SF_CLASSIC_THEME.soundsDir + SF_CLASSIC_THEME.musicFile)) {
            logAssetIssue("FALTA MUSICA DEL TEMA: ${SF_CLASSIC_THEME.musicFile}")
        }
        // 🆕 (2026-07-18k) Música por progresión (lobby + pistas de batalla de Prankedy)
        (listOfNotNull(SF_CLASSIC_THEME.lobbyMusic.takeIf { it.isNotBlank() }) +
            SF_CLASSIC_THEME.battleMusic).forEach { m ->
            if (!sfAssetExists(SF_CLASSIC_THEME.soundsDir + m)) {
                logAssetIssue("FALTA MUSICA (progresión): $m")
            }
        }
    }

    /** ¿Existe el asset? (open+close barato; solo se usa en auditorías puntuales). */
    private fun sfAssetExists(path: String): Boolean =
        runCatching { appContext.assets.open(path).close() }.isSuccess

    // ------------------------------------------------------------------
    // 🆕 MODO ARCADE (escalera de 11 peleas, OFFLINE). Ver SfArcadeLadder + SfArcadeRepository.
    // Todos los personajes/mapas empiezan bloqueados; se desbloquean derrotando rivales.
    // ------------------------------------------------------------------

    /**
     * Arranca el arcade: peleadór + dificultad **Fácil / Medio / Difícil**.
     * - Fácil (BASICA) → mapas de **día** del rival
     * - Medio (NORMAL) → mapas de **noche**
     * - Difícil (AVANZADA/PESADILLA) → **noche apocalíptica**
     * La IA base es la elegida; en jefes/final sube un escalón (cap PESADILLA).
     */
    fun startArcade(playerId: SfFighterId, difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL) {
        arcadePlayer = playerId
        // PESADILLA del selector de práctica se trata como Difícil (apocalipsis + IA dura)
        arcadeChosenDifficulty = when (difficulty) {
            SfCpuDifficulty.PESADILLA -> SfCpuDifficulty.AVANZADA
            else -> difficulty
        }
        arcadeLadder = SfArcadeLadder.build(playerId, arcadeChosenDifficulty)
        arcadeMapCurrent = arcadeLadder.firstOrNull()?.mapFile
            ?: SfStageCatalog.mapForRival(SfFighterId.PARAMEDICO_CRUZ_ROJA, arcadeChosenDifficulty)
        startArcadeStep(1)
    }

    /** Prepara y arranca la pelea del escalón `step` (1..TOTAL). */
    private fun startArcadeStep(step: Int) {
        if (arcadeLadder.isEmpty()) return
        val idx = step.coerceIn(1, arcadeLadder.size)
        val stepData = arcadeLadder[idx - 1]
        // Mapa del rival (ya con iluminación según dificultad elegida)
        arcadeMapCurrent = stepData.mapFile
            ?: SfStageCatalog.mapForRival(stepData.rival, arcadeChosenDifficulty)
        resetInternals()
        // Intensidad sube por fase (piso 0.2) encima de la dificultad elegida
        cpuIntensity = SfArcadeLadder.intensityForStep(idx, arcadeLadder.size)
        roundIntroUntilMs = ROUND_INTRO_MS // banner "RONDA 1 / PELEA"
        val base = StreetFighterState()
        _state.value = base.copy(
            player = base.player.copy(id = arcadePlayer),
            cpu = base.cpu.copy(id = stepData.rival),
            inCharacterSelect = false,
            showRoundIntro = true,
            cpuDifficulty = arcadeDifficultyForStep(stepData),
            arcadeActive = true,
            arcadeStep = idx,
            arcadeTotal = arcadeLadder.size,
            arcadeRival = stepData.rival,
            arcadeMapFile = arcadeMapCurrent,
            arcadeOutcome = SfArcadeOutcome.NONE,
        )
    }

    /**
     * Dificultad de la pelea = base elegida (Fácil/Medio/Difícil) + escalones en jefes.
     * Mapas ya se fijaron con [arcadeChosenDifficulty] al construir la escalera.
     */
    private fun arcadeDifficultyForStep(step: SfArcadeLadder.Step): SfCpuDifficulty =
        SfArcadeLadder.difficultyForStep(arcadeChosenDifficulty, step)

    /**
     * Fin del COMBATE en arcade (offline): si GANASTE, desbloquea según la DIFICULTAD ELEGIDA y
     * guarda el progreso; si perdiste, marca la derrota. El overlay lo dibuja la View según
     * `arcadeOutcome`. Lo llama endRound cuando alguien llega a ROUNDS_TO_WIN.
     *
     * 🆕 (2026-07-18) REGLAS DE DESBLOQUEO por [arcadeChosenDifficulty] (decisión del dueño):
     *  - FÁCIL (BASICA)   → SOLO el MAPA del rival (día). El peleadór NO se desbloquea.
     *  - MEDIO (NORMAL)   → el PELEADÓR + su mapa (noche). Es el mínimo para tener al personaje.
     *  - DIFÍCIL (AVANZADA/apocalíptica) → NADA por ahora (próximamente: animaciones/poderes).
     * La escalera SIEMPRE avanza al ganar (independiente del desbloqueo).
     */
    private fun handleArcadeMatchEnd(winnerIdx: Int) {
        val s = _state.value
        val step = arcadeLadder.getOrNull(s.arcadeStep - 1) ?: return
        val outcome = if (winnerIdx == 0) {
            when (arcadeChosenDifficulty) {
                SfCpuDifficulty.BASICA -> {
                    // Solo el mapa del rival (variante de la pelea = día)
                    step.mapFile?.let { arcadeRepo.unlockMap(it) }
                }
                SfCpuDifficulty.NORMAL -> {
                    // Peleadór + su mapa (noche): mínimo para desbloquear al personaje
                    arcadeRepo.unlockFighter(step.rival.name)
                    step.mapFile?.let { arcadeRepo.unlockMap(it) }
                }
                else -> Unit // AVANZADA/PESADILLA (apocalíptica): aún no desbloquea nada
            }
            arcadeRepo.setLadderStep(s.arcadeStep)
            if (s.arcadeStep >= arcadeLadder.size) SfArcadeOutcome.COMPLETED else SfArcadeOutcome.WON
        } else {
            SfArcadeOutcome.LOST
        }
        // Combate resuelto → la sesión a medias ya no aplica
        arcadeRepo.clearSession()
        _state.value = _state.value.copy(arcadeOutcome = outcome)
    }

    /** CONTINUAR tras ganar un escalón → siguiente rival (o salir si era la final). */
    fun arcadeContinue() {
        val s = _state.value
        if (!s.arcadeActive) return
        if (s.arcadeOutcome == SfArcadeOutcome.COMPLETED) { arcadeExit(); return }
        startArcadeStep(s.arcadeStep + 1)
    }

    /** REINTENTAR tras perder → retrocede 1 pelea (repite la anterior; nunca antes de la 1ª). */
    fun arcadeRetry() {
        val s = _state.value
        if (!s.arcadeActive) return
        startArcadeStep((s.arcadeStep - 1).coerceAtLeast(1))
    }

    /** Salir del arcade → volver al selector de personaje (fresco). */
    fun arcadeExit() {
        // Si había pelea en curso, guarda antes de salir (por si el usuario vuelve)
        val s = _state.value
        if (s.arcadeActive && !s.showEndMenu) persistArcadeSession(s)
        else arcadeRepo.clearSession()
        arcadeLadder = emptyList()
        resetInternals()
        _state.value = StreetFighterState()
    }

    /** Reinicio de todos los relojes/colas internos (resetGameState del JS). */
    private fun resetInternals() {
        gameNow = 0L
        lastRealMs = SystemClock.elapsedRealtime()
        lastHpSeen.fill(-1)
        lastDamageMs = 0L
        lastStalemateLogMs = -100000L
        hurtFreezeUntilMs = 0L
        time = SfConstants.BATTLE_TIME
        timeTimerMs = 0L
        timeFlashTimerMs = 0L
        useFlashFrames = false
        koFlashTimerMs = 0L
        koFrame = 0
        endMenuAtMs = 0L
        cpuNextDecisionMs[0] = 0L
        cpuNextDecisionMs[1] = 0L
        cpuHold[0] = SfInput()
        cpuHold[1] = SfInput()
        specialCooldownUntil[0] = 0L
        specialCooldownUntil[1] = 0L
        cpuStaleApproach[0] = 0
        cpuStaleApproach[1] = 0
        cpuLastOffenseMs[0] = 0L
        cpuLastOffenseMs[1] = 0L
        cpuWantsSpaceUntilMs[0] = 0L
        cpuWantsSpaceUntilMs[1] = 0L
        cpuForceEngageUntilMs[0] = 0L
        cpuForceEngageUntilMs[1] = 0L
        cpuAttackHistory[0].clear()
        cpuAttackHistory[1].clear()
        lastHitTakenMs.fill(Long.MIN_VALUE)
        rapidHitsTaken.fill(0)
        comboEscapeUntilMs.fill(0L)
        cpuIntensity = 0f // VS: sin escalado; arcade/IA-vs-IA la suben después
        pendingAttacks.clear()
        pendingBonusPower = null
        controlHistory.clear()
        lastZone = 0
        lastNetSendMs = 0L
        // 🆕 rondas: pelea nueva = marcador y relojes de ronda en cero
        matchOver = false
        roundResetAtMs = 0L
        roundIntroUntilMs = 0L
        roundGraceUntilMs = 0L
        roundEndSent = false
        // 🆕 SESIÓN 4: gameNow vuelve a 0 → resetear también lo anclado a él y el HUD
        lastSeenSnapshot = null
        remoteSnapshotAtMs = 0L
        dispHp0 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
        dispHp1 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    }

    // ══════════════════════════════════════════════════════════════════
    // 🆕 MULTIJUGADOR 1v1 — intents y manejo de red (relay puro)
    // ══════════════════════════════════════════════════════════════════

    /** Crea sala privada o se une con código. */
    fun startOnline(create: Boolean, code: String? = null) =
        connectOnline { c -> if (create) c.createRoom() else c.joinRoom(code.orEmpty()) }

    /** SALA PÚBLICA: entra a la lista de espera; el server empareja al llegar otro. */
    fun startOnlineQuick() = connectOnline { c ->
        c.quickMatch()
        c.listRooms() // de paso, el resumen de partidas activas
    }

    /** Conecta (despertando el free tier de Render primero) y ejecuta la acción inicial. */
    private fun connectOnline(onReady: (SfMatchClient) -> Unit) {
        if (isOnline) return
        _state.value = _state.value.copy(onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null)
        viewModelScope.launch(Dispatchers.IO) {
            if (!SfMatchClient.warmupBlocking(BuildConfig.SF_SERVER_URL)) {
                _state.value = _state.value.copy(
                    onlineStatus = SfOnlineStatus.OFF,
                    onlineError = "No se pudo despertar el servidor (plan gratis de Render). Intenta de nuevo.",
                )
                return@launch
            }
            val client = SfMatchClient()
            transport = client
            client.connect(
                BuildConfig.SF_SERVER_URL,
                object : SfNetTransport.Listener {
                    override fun onOpen() {
                        onReady(client)
                    }
                    override fun onMessage(msg: SfNetMsg) {
                        // Llega en el hilo de OkHttp → se serializa con el tick en Main
                        viewModelScope.launch { handleNetMessage(msg) }
                    }
                    override fun onClosed() {
                        viewModelScope.launch { onNetDropped(null) }
                    }
                    override fun onFailure(reason: String) {
                        viewModelScope.launch { onNetDropped(reason) }
                    }
                },
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // 🆕 MULTIJUGADOR LOCAL por BLUETOOTH (SfBtClient; mismo flujo que online)
    // Los permisos runtime (CONNECT/SCAN/ADVERTISE en Android 12+) los pide la
    // View ANTES de llamar estos intents.
    // ══════════════════════════════════════════════════════════════════

    /** Listener común de red para los transportes que no necesitan acción al abrir (BT). */
    private fun makeNetListener() = object : SfNetTransport.Listener {
        override fun onOpen() = Unit
        override fun onMessage(msg: SfNetMsg) {
            viewModelScope.launch { handleNetMessage(msg) }
        }
        override fun onClosed() {
            viewModelScope.launch { onNetDropped(null) }
        }
        override fun onFailure(reason: String) {
            viewModelScope.launch { onNetDropped(reason) }
        }
    }

    /** ANFITRIÓN Bluetooth: visible + accept; el flujo sigue como online (ROOM_CREATED "BT"). */
    fun startBtHost() {
        if (isOnline) return
        stopBtScanInternal()
        _state.value = _state.value.copy(
            onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null, btMode = true,
            btError = null, btRetryAddress = null, btHandshaking = false,
            lanMode = false, lanLocalIp = null, lanHostAddress = null,
        )
        val client = SfBtClient(appContext)
        transport = client
        client.startHost(makeNetListener())
    }

    /** BUSCAR RIVAL: abre el selector y llena btDevices (emparejados + discovery). */
    fun startBtScan() {
        if (isOnline) return
        stopBtScanInternal()
        _state.value = _state.value.copy(
            btPicking = true, btMode = true, btDevices = emptyList(), onlineError = null,
        )
        val scanner = SfBtClient(appContext)
        btScanner = scanner
        val ok = scanner.startScan { dev ->
            _state.update { s ->
                if (s.btDevices.any { it.address == dev.address }) s
                else s.copy(btDevices = s.btDevices + dev)
            }
        }
        if (!ok) {
            stopBtScanInternal()
            _state.value = _state.value.copy(
                btPicking = false, btMode = false,
                onlineError = "Bluetooth apagado o no disponible: enciéndelo e intenta de nuevo.",
            )
        }
    }

    /** Cierra el selector de dispositivos sin conectar. */
    fun cancelBtScan() {
        stopBtScanInternal()
        _state.value = _state.value.copy(btPicking = false, btMode = false, btDevices = emptyList())
    }

    /** INVITADO Bluetooth: conecta al host elegido (el flujo sigue como online). */
    fun connectBtDevice(address: String) {
        if (isOnline) return
        stopBtScanInternal()
        _state.value = _state.value.copy(
            btPicking = false, onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null,
            btMode = true, btError = null, btRetryAddress = address, btHandshaking = false,
            lanMode = false, lanLocalIp = null, lanHostAddress = null,
        )
        val client = SfBtClient(appContext)
        transport = client
        client.connectToHost(address, makeNetListener())
    }

    // ══════════════════════════════════════════════════════════════════
    // 🆕 SERVIDOR LOCAL (LAN/Wi-Fi): el jugador hostea su propia sala, estilo LAN party.
    // Sin permisos nuevos (solo INTERNET) ni cambios de Play Console.
    // ══════════════════════════════════════════════════════════════════

    /** HOST LAN: abre el servidor y muestra la IP a compartir (misma red Wi-Fi/hotspot). */
    fun startLanHost() {
        if (isOnline) return
        stopBtScanInternal()
        _state.value = _state.value.copy(
            onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null,
            btMode = false, lanMode = true,
            lanLocalIp = SfLanClient.localIpAddress(), lanHostAddress = null,
            btError = null, btRetryAddress = null, btHandshaking = false,
        )
        val client = SfLanClient()
        transport = client
        client.startHost(makeNetListener())
    }

    /** INVITADO LAN: conecta a la IP que muestra la pantalla del host. */
    fun connectLanHost(addressRaw: String) {
        if (isOnline) return
        val address = addressRaw.trim()
        if (address.isEmpty()) return
        stopBtScanInternal()
        _state.value = _state.value.copy(
            onlineStatus = SfOnlineStatus.CONNECTING, onlineError = null,
            btMode = false, lanMode = true,
            lanLocalIp = null, lanHostAddress = address,
            btError = null, btRetryAddress = null, btHandshaking = false,
        )
        val client = SfLanClient()
        transport = client
        client.connectToHost(address, makeNetListener())
    }

    /** Cierra el overlay de error BT/LAN y regresa (EXPLÍCITAMENTE) al selector offline. */
    fun dismissBtError() {
        _state.value = _state.value.copy(
            btError = null, btRetryAddress = null, btMode = false,
            lanMode = false, lanLocalIp = null, lanHostAddress = null,
        )
    }

    /**
     * Falla del enlace LOCAL (BT o LAN) ANTES de pelear → overlay bloqueante con REINTENTAR.
     * Regla: elegiste jugar por BT/LAN, así que JAMÁS se cae en silencio al selector offline
     * (nada de terminar peleando contra la IA creyendo que era tu rival); se reintenta
     * hasta que la conexión esté VERIFICADA o el jugador cancele explícitamente.
     */
    private fun onLocalLinkFailed(reason: String?) {
        val s = _state.value
        transport?.close()
        transport = null
        remoteSnapshot = null
        netDamageQueue.clear()
        myOnlineChar = null
        oppOnlineChar = null
        onlineEndSent = false
        resetInternals()
        _state.value = StreetFighterState(
            btMode = s.btMode,
            lanMode = s.lanMode,
            btError = reason ?: "No se pudo conectar",
            btRetryAddress = s.btRetryAddress,
            lanHostAddress = s.lanHostAddress,
        )
    }

    private fun stopBtScanInternal() {
        btScanner?.stopScan()
        btScanner?.close()
        btScanner = null
    }

    /**
     * 🆕 Lobby estilo AoE2: al tocar una sala en 'waiting' se SOLICITA unirse (REQUEST_JOIN);
     * el ANFITRIÓN decide (ACEPTAR → ROOM_JOINED / RECHAZAR → JOIN_REJECTED y de regreso a
     * la lista de espera). El server saca al solicitante de la cola mientras el host decide.
     */
    fun requestJoinRoom(code: String) {
        val s = _state.value
        if (s.onlineStatus != SfOnlineStatus.WAITING_OPPONENT || s.roomCode != null || s.awaitingJoinOk) return
        transport?.requestJoin(code)
        _state.value = s.copy(awaitingJoinOk = true, queueNotice = null)
    }

    /** (HOST) Responde la solicitud de unión pendiente: aceptar mete al rival a la sala. */
    fun respondJoin(accept: Boolean) {
        transport?.respondJoin(accept)
        _state.value = _state.value.copy(joinRequestPending = false)
    }

    /** Sale de la sala y vuelve al selector offline (con error opcional a mostrar). */
    fun cancelOnline(errorMsg: String? = null) {
        countdownJob?.cancel()
        roomsRefreshJob?.cancel()
        // Lo fino es AVISAR antes de cerrar el WS: CANCEL_QUEUE saca de la lista de espera
        // (el server también limpia la cola en close, pero así no queda ventana) y LEAVE_ROOM
        // libera la sala; el server ignora el que no aplique.
        transport?.cancelQueue()
        transport?.leaveRoom()
        transport?.close()
        transport = null
        stopBtScanInternal()
        remoteSnapshot = null
        netDamageQueue.clear()
        myOnlineChar = null
        oppOnlineChar = null
        onlineEndSent = false
        resetInternals()
        _state.value = StreetFighterState(onlineError = errorMsg)
    }

    /** El ANFITRIÓN elige el mapa (null = al azar entre DESBLOQUEADOS); el server lo replica. */
    fun chooseMapOnline(file: String?) {
        val unlocked = unlockedMaps()
        val pool = SF_CLASSIC_THEME.fullBackgrounds.map { it.file }.let { all ->
            if (devUnlockAll()) all else all.filter { it in unlocked }
        }
        val resolved = file ?: pool.randomOrNull() ?: return
        // No permitir hostear un mapa bloqueado (salvo Modo Dev)
        if (!devUnlockAll() && resolved !in unlocked && file != null) return
        transport?.selectMap(resolved)
    }

    private fun handleNetMessage(msg: SfNetMsg) {
        val s = _state.value
        when (msg.type) {
            "ROOM_CREATED" -> _state.value = s.copy(
                onlineStatus = SfOnlineStatus.WAITING_OPPONENT, roomCode = msg.code, isHost = true,
            )
            "ROOM_JOINED" -> _state.value = s.copy(
                onlineStatus = SfOnlineStatus.SELECTING, roomCode = msg.code, isHost = false,
                awaitingJoinOk = false, queueNotice = null,
            )
            "OPPONENT_JOINED" -> {
                if (s.battleEnded || !s.inCharacterSelect) {
                    // Un rival NUEVO entró cuando la pelea anterior ya corrió/terminó (p. ej.
                    // en BT el host sigue aceptando tras un abandono): sala en limpio, como
                    // en REMATCH_ACCEPTED — sin esto quedaba SELECTING sobre el fin de pelea.
                    resetInternals()
                    onlineEndSent = false
                    remoteSnapshot = null
                    netDamageQueue.clear()
                    myOnlineChar = null
                    oppOnlineChar = null
                    _state.value = StreetFighterState(
                        onlineStatus = SfOnlineStatus.SELECTING,
                        roomCode = s.roomCode,
                        isHost = s.isHost,
                        btMode = s.btMode,
                        lanMode = s.lanMode,
                        lanLocalIp = s.lanLocalIp,
                    )
                } else {
                    _state.value = s.copy(
                        onlineStatus = SfOnlineStatus.SELECTING, joinRequestPending = false,
                    )
                }
            }
            // Sala pública: en lista de espera (roomCode null → la UI muestra "buscando rival")
            "QUEUED" -> {
                _state.value = s.copy(
                    onlineStatus = SfOnlineStatus.WAITING_OPPONENT, roomCode = null,
                    awaitingJoinOk = false,
                )
                startRoomsRefresh()
            }
            // (BT) Socket conectado; verificando con el anfitrión (progreso en la UI)
            "BT_HANDSHAKE" -> _state.value = s.copy(btHandshaking = true)
            // ─── 🆕 Lobby con aprobación ───
            // (HOST) alguien pide unirse → la View muestra ACEPTAR/RECHAZAR
            "JOIN_REQUESTED" -> _state.value = s.copy(joinRequestPending = true)
            "JOIN_REQUEST_CANCELLED" -> _state.value = s.copy(joinRequestPending = false)
            // (INVITADO) rechazado/sala llena → vuelve a la lista de espera con el aviso
            "JOIN_REJECTED" -> {
                transport?.quickMatch() // re-entra a la cola pública
                _state.value = s.copy(awaitingJoinOk = false, queueNotice = msg.message)
            }
            "ROOMS_LIST" -> _state.value = s.copy(
                activeRooms = msg.rooms ?: emptyList(),
                queueCount = msg.queue ?: 0,
            )
            "ERROR" -> cancelOnline(msg.message ?: "Error del servidor")
            "CHARACTERS_SELECTED" -> {
                val oppName = if (s.isHost) msg.char2 else msg.char1
                // Parse defensivo: un id inválido/eliminado (p. ej. "RYU"/"KEN" de un cliente viejo) → PRANKEDY.
                oppOnlineChar = oppName?.let { n -> runCatching { SfFighterId.valueOf(n) }.getOrNull() }
                    ?: SfFighterId.PRANKEDY
                _state.value = s.copy(onlineStatus = SfOnlineStatus.WAITING_MAP)
            }
            "MAP_SELECTED" -> {
                _state.value = s.copy(
                    onlineStatus = SfOnlineStatus.COUNTDOWN,
                    onlineMapFile = msg.map,
                    onlineCountdown = 3,
                )
                countdownJob?.cancel()
                countdownJob = viewModelScope.launch {
                    for (n in 2 downTo 1) {
                        delay(1000)
                        _state.value = _state.value.copy(onlineCountdown = n)
                    }
                }
            }
            "FIGHT_START" -> startOnlineBattle()
            "OPPONENT_STATE" -> remoteSnapshot = msg
            "PLAYER_DAMAGE" -> netDamageQueue.add(msg)
            "ROUND_ENDED" -> roundEndedFromNet(msg.winner) // 🆕 fin de RONDA intermedia
            "MATCH_ENDED" -> endFromNet(msg.winner)
            "REMATCH_REQUESTED" -> _state.value = s.copy(opponentWantsRematch = true)
            "REMATCH_ACCEPTED" -> {
                resetInternals()
                onlineEndSent = false
                remoteSnapshot = null
                netDamageQueue.clear()
                myOnlineChar = null
                oppOnlineChar = null
                _state.value = StreetFighterState(
                    onlineStatus = SfOnlineStatus.SELECTING,
                    roomCode = s.roomCode,
                    isHost = s.isHost,
                    btMode = s.btMode, // la revancha BT/LAN sigue en su transporte
                    lanMode = s.lanMode,
                    lanLocalIp = s.lanLocalIp,
                )
            }
            "OPPONENT_LEFT", "OPPONENT_DISCONNECTED" -> {
                if (s.onlineStatus == SfOnlineStatus.FIGHTING && !s.battleEnded) {
                    // Victoria por abandono (decide el COMBATE, no solo la ronda)
                    onlineEndSent = true
                    matchOver = true
                    endMenuAtMs = 0L
                    roundResetAtMs = 0L
                    _state.value = s.copy(
                        battleEnded = true, winnerIndex = 0, showEndMenu = true,
                        onlineStatus = SfOnlineStatus.OPPONENT_LEFT,
                        playerRoundWins = ROUNDS_TO_WIN,
                    )
                } else if (s.isHost) {
                    // El invitado se fue en la antesala: la sala sigue viva esperando a otro
                    _state.value = s.copy(
                        onlineStatus = SfOnlineStatus.WAITING_OPPONENT, opponentWantsRematch = false,
                    )
                } else if (s.btMode || s.lanMode) {
                    // BT/LAN: se perdió al anfitrión en la antesala → overlay de REINTENTAR
                    onLocalLinkFailed("Se perdió la conexión con el anfitrión")
                } else {
                    cancelOnline("El anfitrión cerró la sala")
                }
            }
        }
    }

    /**
     * Mientras estás en la LISTA DE ESPERA pública, re-pide LIST_ROOMS cada ROOMS_REFRESH_MS
     * (resumen + tarjetas de salas). Se auto-detiene al emparejarte/unirte/cancelar.
     */
    private fun startRoomsRefresh() {
        roomsRefreshJob?.cancel()
        roomsRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(ROOMS_REFRESH_MS)
                val st = _state.value
                if (st.onlineStatus != SfOnlineStatus.WAITING_OPPONENT || st.roomCode != null) break
                transport?.listRooms()
            }
        }
    }

    /** FIGHT_START: arranca la pelea online. El anfitrión pelea a la IZQUIERDA. */
    private fun startOnlineBattle() {
        val s = _state.value
        resetInternals()
        roundIntroUntilMs = ROUND_INTRO_MS // banner "RONDA 1 / PELEA" tras el countdown
        onlineEndSent = false
        remoteSnapshot = null
        netDamageQueue.clear()
        val base = StreetFighterState()
        val my = myOnlineChar ?: SfFighterId.PRANKEDY
        val opp = oppOnlineChar ?: SfFighterId.PRANKEDY
        val leftX = base.player.x
        val rightX = base.cpu.x
        _state.value = base.copy(
            player = base.player.copy(
                id = my,
                x = if (s.isHost) leftX else rightX,
                direction = if (s.isHost) SfDirection.RIGHT else SfDirection.LEFT,
            ),
            cpu = base.cpu.copy(
                id = opp,
                x = if (s.isHost) rightX else leftX,
                direction = if (s.isHost) SfDirection.LEFT else SfDirection.RIGHT,
            ),
            inCharacterSelect = false,
            onlineStatus = SfOnlineStatus.FIGHTING,
            roomCode = s.roomCode,
            isHost = s.isHost,
            onlineMapFile = s.onlineMapFile,
            btMode = s.btMode, // conservar el transporte para overlays post-pelea
            lanMode = s.lanMode,
            lanLocalIp = s.lanLocalIp,
        )
    }

    /**
     * Aplica el último OPPONENT_STATE al peleador remoto (índice 1).
     * 🆕 INTERPOLADO (SESIÓN 4): el snapshot llega a ~15 Hz; la posición se ALISA con un lerp
     * exponencial por tick (NET_LERP_RATE) en vez de saltar cada 4 ticks. Si la distancia
     * supera NET_SNAP_DIST (reset de ronda/teleport) se SNAPEA — no perseguirlo lerpeando.
     * Pose/frame/dirección/HP se aplican DIRECTO (interpolarlos falsearía la pelea).
     */
    private fun applyRemoteSnapshot(sim: Sim, now: Long, dt: Float) {
        val rs = remoteSnapshot ?: return
        if (rs !== lastSeenSnapshot) {
            lastSeenSnapshot = rs
            remoteSnapshotAtMs = now // edad del snapshot (para extrapolar sus proyectiles)
        }
        val st = rs.state?.let { n -> runCatching { SfFighterState.valueOf(n) }.getOrNull() } ?: sim.p1.state
        val tx = rs.x ?: sim.p1.x
        val ty = rs.y ?: sim.p1.y
        val far = abs(tx - sim.p1.x) > NET_SNAP_DIST || abs(ty - sim.p1.y) > NET_SNAP_DIST
        val alpha = if (far) 1f else (dt * NET_LERP_RATE).coerceAtMost(1f)
        sim.p1 = sim.p1.copy(
            x = sim.p1.x + (tx - sim.p1.x) * alpha,
            y = sim.p1.y + (ty - sim.p1.y) * alpha,
            state = st,
            animationFrame = rs.frame ?: 0,
            direction = if ((rs.dir ?: 1) >= 0) SfDirection.RIGHT else SfDirection.LEFT,
            hitPoints = rs.hp ?: sim.p1.hitPoints,
        )
        // 🆕 SINCRONÍA DEL TIMER: el HOST manda su reloj en PLAYER_STATE; el invitado lo
        // ADOPTA solo si el drift acumulado es >= TIMER_RESYNC_DIFF (el conteo local sigue
        // bajando suave; esto solo re-ancla). GRACIA post-reset: un timer viejo en vuelo de
        // la ronda anterior NO debe pisar el 99 recién reseteado.
        if (!_state.value.isHost && !sim.battleEnded && now >= roundGraceUntilMs) {
            rs.timer?.let { t ->
                if (abs(time - t) >= TIMER_RESYNC_DIFF) {
                    time = t
                    timeTimerMs = now
                }
            }
        }
        // Si su propio estado reporta 0 HP, gané la RONDA (él manda ROUND/MATCH_ENDED; esto
        // lo adelanta). GRACIA post-reset: ignora snapshots viejos en vuelo con hp=0.
        if ((rs.hp ?: 1) <= 0 && !sim.battleEnded && now >= roundGraceUntilMs) {
            endRound(sim, winnerIdx = 0, now = now)
        }
    }

    /** Aplica a MI peleador el daño que me mandó el rival (yo decido bloqueo con MI estado). */
    private fun processNetDamage(sim: Sim, now: Long) {
        // Ronda terminada o gracia post-reset: el daño en vuelo del rival ya no cuenta
        if (sim.battleEnded || now < roundGraceUntilMs) {
            netDamageQueue.clear()
            return
        }
        while (true) {
            val m = netDamageQueue.poll() ?: break
            val strength = m.strength?.let { n -> runCatching { SfAttackStrength.valueOf(n) }.getOrNull() }
                ?: SfAttackStrength.LIGHT
            val type = m.atkType?.let { n -> runCatching { SfAttackType.valueOf(n) }.getOrNull() }
                ?: SfAttackType.PUNCH
            val hitX = (sim.p0.x + sim.p1.x) / 2f
            val hitY = minOf(sim.p0.y, sim.p1.y) - 54f
            applyAttackHit(sim, attackerIdx = 1, strength, type, SfHurtArea.BODY, hitX to hitY, now)
        }
    }

    /** Manda MI estado al rival cada ~66 ms (posición, pose, frame, HP, 🆕 timer del host y mis proyectiles). */
    private fun sendNetState(sim: Sim, now: Long) {
        if (now - lastNetSendMs < 66) return
        lastNetSendMs = now
        val f = sim.p0
        transport?.sendPlayerState(
            x = f.x, y = f.y, state = f.state.name, frame = f.animationFrame,
            dir = f.direction.sign, hp = f.hitPoints,
            // 🆕 SINCRONÍA: solo el HOST es autoridad del reloj (el relay lo pasa tal cual)
            timer = if (_state.value.isHost) time else null,
            fireballs = sim.fireballs.filter { it.ownerIndex == 0 }.map {
                SfNetFireball(it.x, it.y, it.direction.sign, it.strength.name, it.state.name, it.animationFrame)
            },
        )
    }

    /**
     * Añade los proyectiles del RIVAL (render-only; su dueño calcula las colisiones).
     * 🆕 EXTRAPOLADOS (SESIÓN 4): entre snapshots (~66 ms) los ACTIVE avanzan a su velocidad
     * nominal según la EDAD del snapshot (tope NET_FB_MAX_AGE_S) — antes se congelaban 4 ticks.
     */
    private fun appendRemoteFireballs(sim: Sim, now: Long) {
        val fbs = remoteSnapshot?.fireballs ?: return
        val ageS = ((now - remoteSnapshotAtMs).coerceAtLeast(0L) / 1000f).coerceAtMost(NET_FB_MAX_AGE_S)
        fbs.forEach { nf ->
            val strength = runCatching { SfAttackStrength.valueOf(nf.strength) }.getOrDefault(SfAttackStrength.LIGHT)
            val fbState = runCatching { SfFireballState.valueOf(nf.state) }.getOrDefault(SfFireballState.ACTIVE)
            val dir = if (nf.dir >= 0) SfDirection.RIGHT else SfDirection.LEFT
            // Solo los ACTIVOS vuelan; un COLLIDED se queda donde reventó
            val x = if (fbState == SfFireballState.ACTIVE) {
                nf.x + strength.fireballVelocity * dir.sign * ageS
            } else {
                nf.x
            }
            sim.fireballs.add(
                SfFireball(
                    ownerIndex = 1,
                    x = x, y = nf.y,
                    direction = dir,
                    strength = strength,
                    velocity = 0f,
                    state = fbState,
                    animationFrame = nf.frame,
                ),
            )
        }
    }

    /**
     * 🆕 FIREBALL-VS-FIREBALL (SESIÓN 4): dos proyectiles ACTIVOS de DUEÑOS OPUESTOS que se
     * traslapan REVIENTAN los dos (pose COLLIDED, como al pegar). Offline cancela ambos de
     * verdad; online el del rival es render-only — aquí se revienta MI copia y el rival hará
     * lo propio con la suya en su lado (~66 ms; el parpadeo de su copia es aceptado).
     */
    private fun collideFireballPairs(sim: Sim, now: Long) {
        if (sim.fireballs.size < 2) return
        for (i in sim.fireballs.indices) {
            val a = sim.fireballs[i]
            if (a.state != SfFireballState.ACTIVE) continue
            for (j in i + 1 until sim.fireballs.size) {
                val b = sim.fireballs[j]
                if (b.state != SfFireballState.ACTIVE || b.ownerIndex == a.ownerIndex) continue
                val boxA = fireballBox.toWorld(a.x, a.y, a.direction)
                val boxB = fireballBox.toWorld(b.x, b.y, b.direction)
                if (!boxA.overlaps(boxB)) continue
                sim.fireballs[i] = collidedFireball(a, now)
                sim.fireballs[j] = collidedFireball(b, now)
                _soundEvents.tryEmit("light-punch-hit")
                return // a lo sumo un cruce por tick (2 pares simultáneos es rarísimo)
            }
        }
    }

    private fun collidedFireball(fb: SfFireball, now: Long): SfFireball = fb.copy(
        state = SfFireballState.COLLIDED,
        animationFrame = 0,
        velocity = fb.velocity * 0.33f,
        animationTimerMs = now + (fireballCollidedDelays[0] * SfConstants.FRAME_TIME_MS).toLong(),
    )

    /** Índice local → lado de red ("p1" = anfitrión), para ROUND/MATCH_ENDED. */
    private fun sideOf(winnerIdx: Int): String {
        val iAmP1 = _state.value.isHost
        return if (winnerIdx == 0) (if (iAmP1) "p1" else "p2") else (if (iAmP1) "p2" else "p1")
    }

    /** Lado de red → índice local (reconciliación de ROUND/MATCH_ENDED entrantes). */
    private fun idxOf(side: String?): Int = when (side) {
        "p1" -> if (_state.value.isHost) 0 else 1
        "p2" -> if (_state.value.isHost) 1 else 0
        else -> 0
    }

    /**
     * 🆕 Fin de RONDA (KO, timeout o adelanto por red). Suma la ronda al ganador y decide:
     * ¿alguien llegó a ROUNDS_TO_WIN? → COMBATE terminado (menú de fin, MATCH_ENDED).
     * ¿No? → congela con "X WINS" y programa la ronda siguiente (ROUND_ENDED al rival).
     */
    private fun endRound(sim: Sim, winnerIdx: Int, now: Long) {
        if (sim.battleEnded) return
        sim.winner = winnerIdx
        sim.battleEnded = true
        val s = _state.value
        val w0 = s.playerRoundWins + if (winnerIdx == 0) 1 else 0
        val w1 = s.cpuRoundWins + if (winnerIdx == 1) 1 else 0
        _state.value = _state.value.copy(playerRoundWins = w0, cpuRoundWins = w1)
        if (w0 >= ROUNDS_TO_WIN || w1 >= ROUNDS_TO_WIN) {
            matchOver = true
            endMenuAtMs = now + END_MENU_DELAY_MS
            if (isOnline) sendOnlineEnd(winnerIdx)
            // 🆕 ARCADE (offline): desbloqueo/avance de la escalera al decidirse el combate.
            if (_state.value.arcadeActive) handleArcadeMatchEnd(winnerIdx)
        } else {
            roundResetAtMs = now + ROUND_RESET_DELAY_MS
            if (isOnline && !roundEndSent) {
                roundEndSent = true
                transport?.sendRoundEnded(sideOf(winnerIdx))
            }
        }
    }

    /** ROUND_ENDED recibido: reconcilia el fin de RONDA si mi sim aún no lo detectaba. */
    private fun roundEndedFromNet(winnerSide: String?) {
        val s = _state.value
        if (s.battleEnded || s.onlineStatus != SfOnlineStatus.FIGHTING) return
        val winnerIdx = idxOf(winnerSide)
        val now = gameNow
        roundEndSent = true // ya lo publicó el otro lado; no re-enviar
        val w0 = s.playerRoundWins + if (winnerIdx == 0) 1 else 0
        val w1 = s.cpuRoundWins + if (winnerIdx == 1) 1 else 0
        _state.value = s.copy(
            playerRoundWins = w0, cpuRoundWins = w1,
            battleEnded = true, winnerIndex = winnerIdx,
        )
        if (w0 >= ROUNDS_TO_WIN || w1 >= ROUNDS_TO_WIN) {
            matchOver = true
            endMenuAtMs = now + END_MENU_DELAY_MS
        } else {
            roundResetAtMs = now + ROUND_RESET_DELAY_MS
        }
    }

    /**
     * 🆕 Arranca la ronda siguiente: HP/posiciones/timer frescos (mismos peleadores y mapa),
     * banner "RONDA N / PELEA" con input congelado y GRACIA para ignorar mensajes en vuelo
     * de la ronda anterior. Cada lado la arranca con su propio reloj (desfase de ms,
     * aceptable — misma decisión que el timer online, ver AUDIT §4).
     */
    private fun resetRound(now: Long) {
        roundResetAtMs = 0L
        val s = _state.value
        val base = StreetFighterState()
        val onlineFight = s.onlineStatus == SfOnlineStatus.FIGHTING
        val leftX = base.player.x
        val rightX = base.cpu.x
        // Mismo acomodo que al arrancar: offline jugador a la izquierda; online el HOST
        val playerLeft = !onlineFight || s.isHost
        val p0 = base.player.copy(
            id = s.player.id,
            x = if (playerLeft) leftX else rightX,
            direction = if (playerLeft) SfDirection.RIGHT else SfDirection.LEFT,
        )
        val p1 = base.cpu.copy(
            id = s.cpu.id,
            x = if (playerLeft) rightX else leftX,
            direction = if (playerLeft) SfDirection.LEFT else SfDirection.RIGHT,
        )
        // Relojes/colas de la ronda (como resetInternals pero SIN tocar marcador de rondas)
        time = SfConstants.BATTLE_TIME
        timeTimerMs = now
        timeFlashTimerMs = 0L
        useFlashFrames = false
        koFlashTimerMs = 0L
        koFrame = 0
        hurtFreezeUntilMs = 0L
        endMenuAtMs = 0L
        cpuNextDecisionMs[0] = 0L
        cpuNextDecisionMs[1] = 0L
        cpuHold[0] = SfInput()
        cpuHold[1] = SfInput()
        specialCooldownUntil[0] = 0L
        specialCooldownUntil[1] = 0L
        lastHitTakenMs.fill(Long.MIN_VALUE)
        rapidHitsTaken.fill(0)
        comboEscapeUntilMs.fill(0L)
        pendingAttacks.clear()
        pendingBonusPower = null
        controlHistory.clear()
        lastZone = 0
        remoteSnapshot = null
        lastSeenSnapshot = null   // 🆕 SESIÓN 4: la edad del snapshot arranca con el próximo
        remoteSnapshotAtMs = 0L
        netDamageQueue.clear()
        onlineEndSent = false
        roundEndSent = false
        roundGraceUntilMs = now + ROUND_GRACE_MS
        roundIntroUntilMs = now + ROUND_INTRO_MS
        // s.copy conserva aiVsAi / cpuDifficulty / arcade*
        _state.value = s.copy(
            player = p0,
            cpu = p1,
            fireballs = emptyList(),
            splashes = emptyList(),
            cameraX = base.cameraX,
            cameraY = base.cameraY,
            displayTime = SfConstants.BATTLE_TIME,
            timeFlashing = false,
            battleEnded = false,
            winnerIndex = null,
            showEndMenu = false,
            koFlash = false,
            roundNumber = s.roundNumber + 1,
            showRoundIntro = true,
        )
    }

    /** Publica el fin del COMBATE una sola vez ("p1" = anfitrión). */
    private fun sendOnlineEnd(winnerIdx: Int) {
        if (onlineEndSent) return
        onlineEndSent = true
        transport?.sendMatchEnded(sideOf(winnerIdx))
    }

    /** MATCH_ENDED recibido: reconcilia el final del COMBATE (por si mi sim no lo detectaba). */
    private fun endFromNet(winnerSide: String?) {
        val s = _state.value
        if (s.battleEnded && s.showEndMenu) return
        val winnerIdx = idxOf(winnerSide)
        onlineEndSent = true
        matchOver = true
        endMenuAtMs = 0L      // que el tick no re-oculte el menú
        roundResetAtMs = 0L   // cancela cualquier ronda programada
        _state.value = s.copy(
            battleEnded = true, winnerIndex = winnerIdx, showEndMenu = true,
            // reconcilia el marcador: el ganador tiene las rondas del combate
            playerRoundWins = if (winnerIdx == 0) ROUNDS_TO_WIN else s.playerRoundWins,
            cpuRoundWins = if (winnerIdx == 1) ROUNDS_TO_WIN else s.cpuRoundWins,
        )
    }

    private fun onNetDropped(reason: String?) {
        if (!isOnline) return
        val s = _state.value
        if ((s.btMode || s.lanMode) && !s.battleEnded && s.onlineStatus != SfOnlineStatus.OPPONENT_LEFT) {
            // BT/LAN sin pelea terminada: reintento (overlay), no selector offline
            onLocalLinkFailed(reason)
            return
        }
        cancelOnline(reason?.let { "Conexión perdida: $it" } ?: "Conexión perdida con el servidor")
    }

    override fun onCleared() {
        transport?.close()
        stopBtScanInternal()
        super.onCleared()
    }
}
