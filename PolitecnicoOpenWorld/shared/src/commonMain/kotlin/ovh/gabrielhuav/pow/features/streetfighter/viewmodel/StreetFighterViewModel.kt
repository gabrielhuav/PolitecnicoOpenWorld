package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

// 🍏 `PowViewModel` (`:shared`) en vez de `androidx.lifecycle.ViewModel`. En Android **ES** un
// ViewModel de androidx por dentro (`expect/actual`), así que Hilt, el ciclo de vida y el
// `NavBackStackEntry` no se enteran del cambio; en iOS es una clase normal con su propio scope.
import ovh.gabrielhuav.pow.presentation.PowViewModel
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
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BLOCK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAnimation
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDamage
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCamera
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfHealthBar
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfSplashes
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfPhysics
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStateMachine
import ovh.gabrielhuav.pow.domain.models.streetfighter.sfUsableBonusPowerCount
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_DOWNED_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_ATTACK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_NEW_MOVE_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_PARRY_STATES
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombo
import ovh.gabrielhuav.pow.features.streetfighter.data.SfComboAction
import ovh.gabrielhuav.pow.features.streetfighter.data.SfCombos
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
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetTransport
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.TimeSource

internal const val AUDIO_SHOWCASE_GAP_MS = 500L
internal const val AUDIO_SHOWCASE_FALLBACK_MS = 5000L
internal val SHOWCASE_SPEEDS = listOf(1f, 2f, 4f)
private val sfClockStart = TimeSource.Monotonic.markNow()
internal fun sfElapsedRealtime(): Long = sfClockStart.elapsedNow().inWholeMilliseconds
@Suppress("UnusedParameter")
internal fun sfLog(message: String) = Unit

// ViewModel del modo STREET FIGHTER: port fiel de Fighter.js/BattleScene.js/Fireball.js.
// - requestAnimationFrame → coroutine a ~60 fps con dt medido y RELOJ DE JUEGO VIRTUAL
//   (gameNow avanza solo si no hay pausa → los timers absolutos no necesitan desplazarse).
// - Animación por FRAME-DELAYS del JSON (delay*FRAME_TIME; 0 = FREEZE, -1 = TRANSITION).
// - Cajas push/hurt/hit POR FRAME (del JSON). Hit-freeze de 15 frames al conectar un golpe.
// - Sonidos: SharedFlow de claves; la View los reproduce con SoundPool.
// Estado inmutable: la simulación del tick trabaja en un holder mutable local (Sim) y
// publica UNA vez con _state.update (convención 09). Scope: NavBackStackEntry.

open class StreetFighterViewModel(
    // 🆕 (2026-07-21) Recompensa de ARCADE en DIFÍCIL: el coleccionable del rival vencido.
    val environment: StreetFighterEnvironment = DefaultStreetFighterEnvironment,
) : PowViewModel() {

    val appContext: Any? get() = environment.platformContext

    internal val _state = MutableStateFlow(StreetFighterState())
    val state: StateFlow<StreetFighterState> = _state.asStateFlow()

    /** Claves de sonido (nombre base del .m4a en STREETFIGHTER/SOUNDS). */
    internal val _soundEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val soundEvents: SharedFlow<String> = _soundEvents.asSharedFlow()

    internal var audioShowcaseJob: Job? = null

    internal val specialPhrases by lazy {
        ovh.gabrielhuav.pow.features.streetfighter.data.SfSpecialPhrases.load()
    }

    // ── 🆕 (2026-07-18o/p/q) PACK DE VOCES por EVENTO con FRASE (subtítulo) por peleadór ──
    // Material del dueño (nuevoMaterial18JUL/Audios). Cada evento (intro/attack/hurt/power/win) es
    // una lista de LÍNEAS (archivo `special_*` + frase); se elige una al azar. Se reproducen por la
    // ruta "special_*" (MediaPlayer en la Screen, apto para clips largos). Policías: HOMBRE y
    // GRANADERO comparten intro/attack (win difiere: granadero = "3 de diana"); igual MUJER/GRANADERA.
    internal data class SfVoiceLine(val file: String, val phrase: String = "")
    internal class SfVoicePack(
        val intro: List<SfVoiceLine> = emptyList(),
        val attack: List<SfVoiceLine> = emptyList(),
        val hurt: List<SfVoiceLine> = emptyList(),
        val power: List<SfVoiceLine> = emptyList(),
        val win: List<SfVoiceLine> = emptyList(),
        val loss: List<SfVoiceLine> = emptyList(),
        val lowHp: List<SfVoiceLine> = emptyList(),
    )

    internal val sfVoicePacks: Map<SfFighterId, SfVoicePack> = run {
        // HOMBRE: policía + granadero comparten intro y ATTACK; el WIN difiere.
        val hIntro = SfVoiceLine("special_pol_h_intro", "¡Está prohibido beber en la vía pública!")
        val hAttack = SfVoiceLine("special_pol_h_attack", "Buenas joven, ¿si sabe porque lo detuvimos?")
        val polH = SfVoicePack(
            intro = listOf(hIntro), attack = listOf(hAttack),
            win = listOf(SfVoiceLine("special_pol_h_win", "¿Se cree más chingón que nosotros o qué joven?")),
            power = listOf(SfVoiceLine("special_policia_cdmx_hombre_power"))
        )
        // WIN de granadero (H y M) = "3 de diana" (bugle). Usa special_granadero_win (special_gr_win se
        // eliminó por pedido del dueño 2026-07-18s).
        val grWin = SfVoiceLine("special_granadero_win")
        val grH = SfVoicePack(intro = listOf(hIntro), attack = listOf(hAttack), win = listOf(grWin))
        // MUJER: policía + granadera comparten ATTACK y HURT; el WIN difiere.
        // (2026-07-19) special_pol_m_attack se divide en attack_1 (primer segundo) y attack_2 (segundo 1 al 3).
        val mAttack = listOf(
            SfVoiceLine("special_pol_m_attack_1"),
            SfVoiceLine("special_pol_m_attack_2")
        )
        val mHurt = listOf(SfVoiceLine("special_pol_m_hurt"))
        val polM = SfVoicePack(
            attack = mAttack,
            hurt = mHurt,
            win = listOf(SfVoiceLine("special_policia_cdmx_mujer_win", "Si dices policía, me comprometí como mujer a que la ciudadanía sintiera una mejor seguridad")),
        )
        val grM = SfVoicePack(attack = mAttack, hurt = mHurt, win = listOf(grWin))
        // 🆕 (2026-07-18s) Paparazzi 1: su ataque ESPECIAL (poder) = special_paparazzi_5 (el audio
        // correcto; special_paparazzi_1 era duplicado y se borró). DAÑO = 3 variantes.
        val papz1 = SfVoicePack(
            power = listOf(SfVoiceLine("special_paparazzi_5")),
            hurt = listOf(
                SfVoiceLine("special_papz1_hurt_1"), SfVoiceLine("special_papz1_hurt_2"),
                SfVoiceLine("special_papz1_hurt_3"),
            ),
        )
        mapOf(
            SfFighterId.POLICIA_CDMX_HOMBRE to polH,
            SfFighterId.POLICIA_GRANADERO_HOMBRE to grH,
            SfFighterId.GRANADERO to grH,
            SfFighterId.POLICIA_CDMX to polM,
            SfFighterId.POLICIA_GRANADERO_MUJER to grM,
            SfFighterId.PAPARAZZI_1 to papz1,
            SfFighterId.PAPARAZZI_5 to SfVoicePack(
                attack = listOf(SfVoiceLine("special_paparazzi_5_attack")),
                hurt = listOf(SfVoiceLine("special_paparazzi_5_hurt"))
            ),
            // 🆕 (2026-07-18s) audios del dueño mapeados al evento correcto:
            // La Tzitzimime = GOLPE NORMAL (attack). Rey Grupero = INTRO.
            // Paramédico Cruz Roja = WIN. (Su special_<id> también suena en su poder por fallback.)
            // 🆕 (2026-07-18t) La Llorona: pack COMPLETO con audios dedicados del dueño
            // (special/power, attack recortado seg 7-11, hurt).
            SfFighterId.LA_LLORONA to SfVoicePack(
                attack = listOf(SfVoiceLine("special_llorona_attack")),
                hurt = listOf(SfVoiceLine("special_llorona_hurt")),
                power = listOf(SfVoiceLine("special_llorona_power")),
            ),
            SfFighterId.LA_TZITZIMIME to SfVoicePack(
                attack = listOf(SfVoiceLine("special_la_tzitzimime_attack")),
                hurt = listOf(SfVoiceLine("special_la_tzitzimime_hurt")),
                power = listOf(SfVoiceLine("special_la_tzitzimime_power")),
                win = listOf(SfVoiceLine("special_la_tzitzimime_win"))
            ),
            SfFighterId.REY_GRUPERO to SfVoicePack(
                intro = listOf(SfVoiceLine("special_rey_grupero")),
                attack = listOf(SfVoiceLine("special_rey_grupero_attack")),
                hurt = listOf(SfVoiceLine("special_rey_grupero_hurt")),
                power = listOf(SfVoiceLine("special_rey_grupero_power"))
            ),
            SfFighterId.PARAMEDICO_CRUZ_ROJA to SfVoicePack(
                win = listOf(SfVoiceLine("special_paramedico_cruz_roja_win", "No olvides que saber primeros auxilios marca la diferencia y salva vidas.")),
                power = listOf(SfVoiceLine("special_power_electricity"))
            ),
            // 🆕 (2026-07-18u) Charro Negro: 2 gritos de ataque + 3 de daño (su special_charro_negro
            // sigue como fallback del poder especial).
            SfFighterId.CHARRO_NEGRO to SfVoicePack(
                attack = listOf(
                    SfVoiceLine("special_charro_attack_1"),
                    SfVoiceLine("special_charro_attack_2"),
                    SfVoiceLine("special_charro_attack_3")
                ),
                hurt = listOf(
                    SfVoiceLine("special_charro_hurt_1"),
                    SfVoiceLine("special_charro_hurt_2"),
                    SfVoiceLine("special_charro_hurt_3")
                )
            ),
            // 🆕 (2026-07-19) Señor de la Tienda: 2 gritos de ataque + 2 de daño (hurt_2 es "puerquito") + 1 de victoria
            SfFighterId.SENOR_TIENDA to SfVoicePack(
                attack = listOf(
                    SfVoiceLine("special_senor_tienda_attack_1"),
                    SfVoiceLine("special_senor_tienda_attack_2")
                ),
                hurt = listOf(
                    SfVoiceLine("special_senor_tienda_hurt_1"),
                    SfVoiceLine("special_senor_tienda_hurt_2", "¡Ya me agarraste de tu puerquito!")
                ),
                win = listOf(
                    SfVoiceLine("special_senor_tienda_win")
                )
            ),
            // 🆕 (2026-07-19) Robot: audio de ataque, daño (hurt) y victoria
            SfFighterId.ROBOT to SfVoicePack(
                attack = listOf(SfVoiceLine("special_robot_attack")),
                hurt = listOf(SfVoiceLine("special_robot_hurt")),
                win = listOf(SfVoiceLine("special_robot_win"))
            ),
            SfFighterId.LA_PRESIDENTA to SfVoicePack(
                attack = listOf(SfVoiceLine("special_la_presidenta_attack")),
                hurt = listOf(SfVoiceLine("special_la_presidenta_hurt")),
                power = listOf(SfVoiceLine("special_la_presidenta_power")),
                // 🆕 (2026-07-20, audit sf_audio_audit) el corte 19JUL cambió el contenido del
                // audio: la frase real ya no es "Sí. Siempre…" sino esta (verificada con Whisper).
                win = listOf(SfVoiceLine("special_la_presidenta_win", "Porque patria se escribe con A de mujer"))
            ),
            // 🆕 (2026-07-19) Escomboy / Escomgirl: mapeo de su poder especial de electricidad (compartido)
            // 🆕 (2026-07-19) Escomboy / Escomgirl: mapeo de sus audios de voz e impactos
            SfFighterId.ESCOMBOY to SfVoicePack(
                attack = listOf(SfVoiceLine("special_escomboy_attack")),
                power = listOf(SfVoiceLine("special_power_electricity"))
            ),
            SfFighterId.ESCOMGIRL to SfVoicePack(
                attack = listOf(SfVoiceLine("special_escomgirl_attack")),
                hurt = listOf(SfVoiceLine("special_escomgirl_hurt")),
                power = listOf(SfVoiceLine("special_power_electricity")),
                loss = listOf(SfVoiceLine("special_escomgirl_loss"))
            ),
            // 🆕 (2026-07-19) Yoalli Ehecatl: pack completo de ataques, daños y poder especial
            SfFighterId.YOALLI_EHECATL to SfVoicePack(
                attack = listOf(
                    SfVoiceLine("special_yoalli_ehecatl_attack_1"),
                    SfVoiceLine("special_yoalli_ehecatl_attack_2")
                ),
                hurt = listOf(
                    SfVoiceLine("special_yoalli_ehecatl_hurt_1"),
                    SfVoiceLine("special_yoalli_ehecatl_hurt_2")
                ),
                power = listOf(SfVoiceLine("special_yoalli_ehecatl"))
            ),
            // 🆕 (2026-07-19) Prankedy: audios de daño (hurt), derrota (loss), ataques (attack), victoria (win), super (power) y lowHp
            SfFighterId.PRANKEDY to SfVoicePack(
                attack = listOf(
                    SfVoiceLine("special_prankedy_attack_1"),
                    SfVoiceLine("special_prankedy_attack_2"),
                    SfVoiceLine("special_prankedy_attack_3"),
                    SfVoiceLine("special_prankedy_attack_4")
                ),
                hurt = listOf(
                    SfVoiceLine("special_prankedy_hurt_1"),
                    SfVoiceLine("special_prankedy_hurt_1"),
                    SfVoiceLine("special_prankedy_hurt_2"),
                    SfVoiceLine("special_prankedy_hurt_2"),
                    SfVoiceLine("special_prankedy_hurt_4"),
                    SfVoiceLine("special_prankedy_hurt_4"),
                    SfVoiceLine("special_prankedy_hurt_3")
                ),
                power = listOf(SfVoiceLine("special_prankedy_power")),
                win = listOf(SfVoiceLine("special_prankedy_win")),
                loss = listOf(SfVoiceLine("special_prankedy_loss")),
                lowHp = listOf(SfVoiceLine("special_prankedy_lowhp"))
            ),
        )
    }

    // 🆕 (2026-07-18u) GRITO DE ATAQUE POR DEFECTO (masculino): sonido normal (no especial) que
    // suena AL AZAR cuando un peleadór HOMBRE golpea y NO tiene voz de ataque propia. El ENEMIGO
    // (índice 1) lo emite más seguido; el jugador (índice 0) muy rara vez (para no saturar tu voz).
    // Archivo: special_male_attack_grunt.m4a (del "ZA ZA", seg 8-10).
    internal val maleGruntClip = "special_male_attack_grunt"
    internal val sfMaleFighters = setOf(
        SfFighterId.PRANKEDY, SfFighterId.SENOR_TIENDA, SfFighterId.PAPARAZZI_1, SfFighterId.PAPARAZZI_5,
        SfFighterId.REY_GRUPERO, SfFighterId.ESCOMBOY, SfFighterId.CHARRO_NEGRO, SfFighterId.LAZARO,
        SfFighterId.POLICIA_CDMX_HOMBRE, SfFighterId.POLICIA_GRANADERO_HOMBRE, SfFighterId.GRANADERO,
        SfFighterId.PARAMEDICO_CRUZ_ROJA, SfFighterId.PARAMEDICO,
    )

    // 🆕 (2026-07-18s) Peleadores SIN voz a propósito (special_<id>.m4a borrado por el dueño):
    // su poder especial suena con el hadouken genérico. La auditoría NO los marca como faltantes.
    internal val sfVoicelessFighters = setOf(
        SfFighterId.LAZARO, SfFighterId.PARAMEDICO, SfFighterId.PRANKEDY,
    )

    /** Emite un clip de voz `special_<name>.m4a` si el asset existe. true = se emitió. */
    internal val voiceSubtitlesEnabled = environment.showVoiceSubtitles

    internal val voicePhrases by lazy {
        ovh.gabrielhuav.pow.features.streetfighter.data.SfVoicePhrases.load()
    }

    /** Emite una LÍNEA del evento (al azar) + su subtítulo si tiene frase (catálogo > inline). */
    internal val lastHurtVoiceMs = LongArray(2) { 0L }
    internal val lastAttackVoiceMs = LongArray(2) { 0L }
    internal val lowHpVoiceTriggered = BooleanArray(2) { false }
    // 🆕 (2026-07-19c) Sin cooldown en hurt para permitir interrupción inmediata en combos
    internal val hurtVoiceCooldownMs = 0L
    internal val attackVoiceCooldownMs = 4200L

    /** Voz de DAÑO (pack HURT, variante al azar) con cooldown por índice.
     * Si la vida baja del 25% (<= 50 de 200) y tiene audio de lowHp de una sola vez, lo prioriza. */
    internal val arcadeRepo = environment.arcade

    // 🆕 Gama baja: tick más lento (~30 fps) y menos trabajo por segundo (ver SfDeviceTier).
    private val lowEndDevice: Boolean = environment.lowEndDevice
    private val tickMs: Long = if (lowEndDevice) 33L else 16L
    private val gauntletAuditSteps = 6

    /** true si el dispositivo es gama baja (la View reduce previews/fondos). */
    fun isLowEndDevice(): Boolean = lowEndDevice

    /** ¿Hay pelea arcade a medias para retomar? (solo lectura; no I/O pesado). */
    fun hasArcadeSession(): Boolean = arcadeRepo.hasSession()

    /** 🆕 Modo Desarrollador (Ajustes): si está ON, TODO desbloqueado (personajes y mapas). */
    fun devUnlockAll(): Boolean = environment.developerMode

    /** 🆕 (2026-07-19) Si el personaje fue realmente desbloqueado por progresión (inicial o ganado). */
    fun isFighterActuallyUnlocked(id: SfFighterId): Boolean =
        // 🆕 (2026-07-19) Con sesión Google en Firebase + Modo Desarrollador se REVELA el arte a
        // color (sin silueta pixelada). Solo en ese caso; para el resto sigue oculto.
        revealLockedArt() || arcadeRepo.unlockedFighters().contains(id.name)

    /** 🆕 Ajustes → "Mostrar hitboxes": dibuja las cajas push/hurt/hit sobre los peleadores. */
    fun showHitboxes(): Boolean = environment.showHitboxes

    /** 🆕 (2026-07-25) Ajustes → "Mostrar FPS (pelea)": contador de cuadros por segundo en el HUD. */
    fun showSfFps(): Boolean = environment.showSfFps

    /**
     * 🆕 REVELAR el arte de los bloqueados (a color, sin pixelar) — siempre que el Modo Desarrollador esté activo.
     */
    fun revealLockedArt(): Boolean =
        environment.developerMode

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
    internal var arcadeLadder: List<SfArcadeLadder.Step> = emptyList()
    internal var arcadeMapCurrent: String? = null
    /** Dificultad elegida al iniciar arcade (Fácil/Medio/Difícil → mapas día/noche/apocalipsis). */
    internal var arcadeChosenDifficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL
    internal var arcadePlayer: SfFighterId = SfFighterId.ESCOMBOY
    // 🆕 (2026-07-22) Derrotas SEGUIDAS en arcade: solo se retrocede un escalón a la 3ª (se
    // reinicia al ganar y al empezar una campaña). Antes se retrocedía en CADA derrota.
    internal var arcadeLossStreak = 0

    // Frame data por peleador (cache perezoso por identidad; soporta CUALQUIER SfFighterId)
    private val dataCache = mutableMapOf<SfFighterId, SfFighterData>()
    internal fun dataFor(f: SfFighter): SfFighterData =
        dataCache.getOrPut(f.id) { SfFrameCatalog.load(f.id) }

    // ---- reloj de juego virtual ----
    private var loopJob: Job? = null
    private var lastRealMs = 0L
    internal var gameNow = 0L            // ms de JUEGO (no avanza en pausa)
    // 🆕 (2026-07-22) La Screen avisa si el overlay CARGANDO está visible: el reloj de JUEGO se
    // CONGELA mientras carga. Si no, en gama baja el 3-2-1 y hasta el arranque se gastan OCULTOS
    // tras el "CARGANDO" (la pelea "empieza antes de verla"). Al cargar, gameNow queda en 0.
    private var uiAssetsLoading = false

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
    internal val cpuNextDecisionMs = LongArray(2) { 0L }
    internal val cpuHold = Array(2) { SfInput() } // intención sostenida (caminar) por índice
    // 🆕 Cooldown de especial/bonus por peleador (ms de juego). Evita spam de hadoukens
    // en PESADILLA / IA vs IA que llenaba la pantalla y no se podía contrarrestar.
    internal val specialCooldownUntil = LongArray(2) { 0L }
    // 🆕 Intensidad de la CPU 0f..1f (POR FASES del arcade): 0 = como en VS; 1 = máxima. Escala
    // la CADENCIA de decisión (reacciona más rápido) y la agresividad/bloqueo. En VS es 0
    // (comportamiento idéntico al de siempre); el arcade la sube según avanzas en la escalera.
    // IA vs IA la fija en 1f (máxima, como la final del arcade).
    internal var cpuIntensity = 0f
    // Contador de “solo caminar sin atacar” por índice: si se atascan cerca sin golpear, forzamos ataque.
    internal val cpuStaleApproach = IntArray(2) { 0 }
    // gameNow del último golpe/special emitido por la IA (watchdog anti “congelados”).
    internal val cpuLastOffenseMs = LongArray(2) { 0L }
    // Preferencia de “espacio” tras clinch (retrocede un rato en IA vs IA).
    internal val cpuWantsSpaceUntilMs = LongArray(2) { 0L }
    // Recuperación tras estancamiento: durante una ventana corta ambos cierran distancia.
    internal val cpuForceEngageUntilMs = LongArray(2) { 0L }
    // 🆕 (2026-07-25) INTENCIÓN DE FATALITY comprometida: una vez que la IA decide el fatality
    // (medidor lleno; rival aturdido = garantizado), lo COMPLETA dentro de esta ventana — corre y
    // suelta el súper — SOBREPONIÉNDOSE al clinch/watchdog que si no lo abortaban al cerrar
    // distancia. Antes el fatality de la IA "nunca" salía por eso. 0 = sin intención activa.
    internal val cpuFatalityUntilMs = LongArray(2) { 0L }
    // Momento en que cada CPU lleno su barra. Impide que el azar conserve el medidor durante
    // toda la ronda: al vencer el plazo de su dificultad, se acerca y ejecuta la SUPER ART.
    internal val cpuSuperReadySinceMs = LongArray(2) { 0L }
    // Variedad ofensiva: memoria de los últimos tres golpes (fuerza×tipo, 0..5) por CPU.
    internal val cpuAttackHistory = Array(2) { ArrayDeque<Int>() }
    // 🆕 (2026-07-18n) Dificultad POR ÍNDICE solo para IA vs IA: si ambos son iguales (dos
    // PESADILLA) se esquivan sin fin y NADIE gana. Se le baja la dificultad a UNO al azar para
    // desnivelar la pelea y que alguien gane. null = usar la dificultad global (VS/arcade normal).
    internal val cpuDiffOverride = arrayOfNulls<SfCpuDifficulty>(2)
    // Antibucle de combo: tras tres impactos rápidos, el defensor recibe una ventana de escape.
    internal val lastHitTakenMs = LongArray(2) { 0L }
    internal val rapidHitsTaken = IntArray(2)
    internal val comboEscapeUntilMs = LongArray(2)
    // 🆕 (2026-07-25) WALL SPLAT: un golpe pesado con el rival contra la pared lo "aplasta" y
    // rebota al centro abriendo UNA continuación de combo. UNA sola vez por combo (se reinicia al
    // empezar un combo nuevo) → JUSTO para ambos y SIN infinitos en la esquina ("que te traben").
    internal val wallSplatUsed = BooleanArray(2)
    // 🆕 (2026-07-20) COMBO estilo SF III 3rd Strike: golpes CONECTADOS encadenados por
    // ATACANTE (índice). Alimenta el contador del HUD ("N GOLPES") y el escalado de daño.
    // Expira si pasa la ventana RAPID_HIT_WINDOW_MS sin conectar otro golpe.
    internal val comboHits = IntArray(2)
    internal val comboLastHitMs = LongArray(2)
    // 🆕 (2026-07-21) MOVESET 3rd Strike: ventana activa del parry, castigo tras recibirlo,
    // control del "un ataque aéreo por salto" y detección del doble toque para el dash.
    internal val parryActiveUntilMs = LongArray(2)
    internal val parryStunUntilMs = LongArray(2)
    // 🆕 (2026-07-22) MAREO/STUN + decaimientos de medidores (molde de parryStunUntilMs):
    // fin del aturdimiento, último golpe CONECTADO (gracia del decaimiento del súper) y
    // acumuladores fraccionales del decaimiento por tick (los medidores son Int).
    internal val stunUntilMs = LongArray(2)
    internal val superKeepMs = LongArray(2)
    private val dizzyDecayAcc = FloatArray(2)
    private val superDecayAcc = FloatArray(2)
    internal val airAttackUsed = BooleanArray(2)
    private val lastForwardTapMs = LongArray(2)
    private val lastBackwardTapMs = LongArray(2)
    private val dirHeldPrev = Array(2) { false to false } // (adelante, atrás) del tick previo
    /** 🆕 ¿Estaba cubriéndose ABAJO (atrás+abajo) en el último tick? Lo llena updateFighter. */
    internal val defenderBlockingLow = BooleanArray(2)

    // 🆕 DIAGNÓSTICO / anti-atasco (2026-07-18h): detecta animaciones que NO terminan (assets sin
    // frame -1 / incompletas → peleador congelado, "se pegan y no se mueven") y estancamientos sin
    // daño; fuerza la salida a IDLE y registra el problema para corregir el asset después.
    private val stuckSig = arrayOfNulls<Pair<SfFighterState, Int>>(2) // (estado, frame) vigilado por índice
    private val stuckSinceMs = LongArray(2) { 0L }
    private val lastHpSeen = intArrayOf(-1, -1)
    private var lastDamageMs = 0L
    private var lastStalemateLogMs = -100000L
    internal val assetIssues = LinkedHashSet<String>() // problemas detectados (deduplicados)
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
        // 🆕 (2026-07-21) El moveset nuevo TAMBIÉN debe terminar su animación: si una hoja
        // llega incompleta (sin frame -1), el watchdog lo saca a IDLE en vez de congelarlo.
    ) + SF_BONUS_POWER_STATES + SF_NEW_MOVE_STATES

    // 🆕 AUTOJUEGO (gauntlet): cola de parejas a pelear en IA vs IA, encadenadas automáticamente.
    internal var gauntletActive = false
    internal data class GauntletFight(
        val player: SfFighterId,
        val rival: SfFighterId,
        val difficulty: SfCpuDifficulty = SfCpuDifficulty.PESADILLA,
        val intensity: Float = 1f,
        val mapFile: String? = null,
    )

    internal val gauntletQueue = ArrayDeque<GauntletFight>()
    internal var gauntletTotal = 0
    internal var gauntletDone = 0
    internal var gauntletKoRounds = 0
    internal var gauntletTimeoutRounds = 0
    internal val gauntletFightCapMs = 325000L // tres rounds de 99 s + intros/transiciones
    // Tope de la pelea EN CURSO: en campaña depende de la dificultad; en showcase se calcula
    // por pasos para no cortar HURT/KO/VICTORY ni las metamorfosis con un TIMEOUT falso.
    internal var gauntletFightCapCurMs = 60000L
    // 🆕 SHOWCASE: variante del gauntlet que recorre TODAS las animaciones + sonidos de cada
    // peleador (script de moves), para QA visual/auditiva de los assets (watchStuck loguea los rotos).
    internal var showcaseMode = false
    internal var gauntletCampaignMode = false
    internal var showcaseStep = 0
    internal var showcaseStepUntilMs = 0L
    internal var showcaseFiredStep = -1
    internal var showcaseSpeed = 1f
    // Ventana por animación: > stuckLimitMs (1800) para que watchStuck alcance a REGISTRAR y
    // rescatar una anim atascada antes de que el guion fuerce el siguiente estado (y > cooldown
    // de special/bonus). 🆕 2026-07-18j: era 1600 y enmascaraba atascos en los pasos forzados.
    internal val showcaseStepMs = 2000L
    // Estado que el paso actual del guion FUERZA (bypass de validFrom); null = paso por input.
    internal var showcaseForcedState: SfFighterState? = null

    // ---- batalla ----
    internal var hurtFreezeUntilMs = 0L  // hit-freeze (FighterStruckDelay)
    internal var time = SfConstants.BATTLE_TIME
    internal var timeTimerMs = 0L
    private var timeFlashTimerMs = 0L
    private var useFlashFrames = false
    private var koFlashTimerMs = 0L
    private var koFrame = 0
    internal var endMenuAtMs = 0L

    // ─── 🆕 RONDAS (mejor de 3) ───
    internal var matchOver = false          // alguien ya tomó 2 rondas → el fin muestra el menú
    internal var roundResetAtMs = 0L        // >0 = hay ronda nueva programada (intermedio corriendo)
    internal var roundIntroUntilMs = 0L     // banner "RONDA N / PELEA": input y timer congelados
    internal var roundGraceUntilMs = 0L     // tras el reset, ignora snapshots/daño viejos del rival
    private var roundEndSent = false       // guard de ROUND_ENDED (como onlineEndSent por ronda)
    private var introVoiceSent = false      // 🆕 voz de intro del policía: una vez por ronda

    // 🆕 (2026-07-25) GRADO de la ronda que acaba de cerrar (PERFECT/COMBO/SUPER/TIME): endRound
    // lo calcula y resetRound lo vuelca al estado para pintarlo bajo la barra del ganador en la
    // ronda siguiente (estilo SF III). Se limpia al arrancar un combate nuevo.
    private var pendingRoundOutcome = SfRoundOutcome.NORMAL
    private var pendingRoundOutcomeWinner = -1

    // 🆕 (2026-07-25) SISTEMA DE CALIFICACIÓN estilo SF III (E..MS): mide el desempeño del JUGADOR
    // (índice 0) A LO LARGO DEL COMBATE (no por ronda) y da una nota al terminar. Se resetea por
    // combate (resetInternals). Métricas: daño hecho/recibido, parries, combo más largo, súpers/
    // fatalities conectados, VARIEDAD de golpes y rondas perfectas.
    internal var gradeDealt = 0
    internal var gradeTaken = 0
    internal var gradeParries = 0
    internal var gradeMaxCombo = 0
    internal var gradeSupers = 0
    internal var gradeHits = 0
    private var gradePerfects = 0
    internal val gradeMoves = HashSet<SfFighterState>()
    // Solo se califica cuando hay un HUMANO en el índice 0 (no IA vs IA / showcase / gauntlet / tutorial).
    internal val gradeTrackingOn: Boolean
        get() = !_state.value.aiVsAi && !_state.value.tutorialActive && !showcaseMode && !gauntletActive

    // 🆕 (2026-07-25) BARRERA "AMBOS LISTOS" (punto 2): tras FIGHT_START cada teléfono decodifica
    // sus atlas bajo CARGANDO; el que ya terminó ESPERA el PLAYER_READY del rival para que la
    // ronda arranque sincronizada. Con un fallback por timeout para no colgarse si el rival/server
    // no soporta el mensaje (cliente viejo o server sin redeploy) → degrada al arranque de siempre.
    private var awaitingPeerReady = false
    private var localReadySent = false
    internal var peerReady = false
    private var readyBarrierUntilMs = 0L    // tope de seguridad (tiempo REAL; gameNow está congelado)
    private var readyArmedRealMs = 0L       // tiempo REAL al armar; ventana para que arranque CARGANDO

    // ─── 🆕 MULTIJUGADOR 1v1 (relay puro contra MultiplayerSF/ en Render) ───
    // Cada cliente simula a SU peleador (índice 0 local); el rival (índice 1) llega
    // por red: posición/estado/frame/hp vía OPPONENT_STATE y el daño que ME hacen vía
    // PLAYER_DAMAGE (autoridad del RECEPTOR sobre su propio HP).
    // TRANSPORTE intercambiable: SfMatchClient (WebSocket/Render) o SfBtClient (Bluetooth
    // local). Mismos mensajes/arquitectura; el VM solo habla con la interfaz.
    internal var transport: SfNetTransport? = null
    // 🆕 (2026-07-26) P2P: cuando la sala ONLINE ya tiene a los 2, se intenta subir la pelea a
    // una conexión DIRECTA teléfono-a-teléfono (Render pasa a ser solo cupido). Si no se logra,
    // `transport` sigue mandando todo por el relay: el decorador cae solo. Ver SfWebRtcClient.
    var webRtc: Any? = null
    // El cliente de relay crudo: hace de canal de SEÑALIZACIÓN y de respaldo del P2P.
    internal var relayClient: SfMatchClient? = null
    var btScanner: Any? = null   // discovery del selector "BUSCAR RIVAL"
    // 🆕 (2026-07-26) Autodescubrimiento LAN por UDP: baliza del host + escucha del invitado.
    var lanDiscovery: Any? = null
    var remoteSnapshot: SfNetMsg? = null
    val netDamageQueue = ArrayDeque<SfNetMsg>()
    // 🆕 (2026-07-26) AUDIO SINCRONIZADO EN RED. Antes los dos jugadores VEÍAN lo mismo pero no
    // OÍAN lo mismo: `applyRemoteSnapshot` asigna el estado del rival DIRECTO, sin pasar por
    // `changeState`, que es donde se emite todo el audio. Ahora:
    //  · las VOCES de mi peleador se acumulan aquí y viajan en el PLAYER_STATE (campo `audio`),
    //    porque los packs eligen con `.random()` y sorteadas por separado sonarían distintas;
    //  · los SFX DETERMINISTAS del rival (whoosh, aterrizaje) los DERIVA el receptor de su
    //    `state` en `emitRemoteStateSfx` — no hace falta mandarlos;
    //  · los impactos (`*-hit`) NO se tocan: ya suenan en AMBOS lados (atacante y receptor).
    internal val pendingNetAudio = mutableListOf<String>()
    // Solo se captura mientras corre la voz del peleador LOCAL en una pelea en red.
    internal var netAudioCapture = false
    // Último estado del rival ya sonorizado (para detectar la TRANSICIÓN, no el estado sostenido).
    internal var lastRemoteSfxState: SfFighterState? = null
    internal var myOnlineChar: SfFighterId? = null
    internal var oppOnlineChar: SfFighterId? = null
    internal var lastNetSendMs = 0L
    // 🆕 INTERPOLACIÓN del rival (SESIÓN 4): el snapshot llega a ~15 Hz; la posición se
    // ALISA con lerp por tick (y los proyectiles remotos se EXTRAPOLAN por la edad del
    // snapshot). lastSeenSnapshot detecta el CAMBIO de referencia (se compara identidad
    // en el tick, hilo Main).
    internal var lastSeenSnapshot: SfNetMsg? = null
    internal var remoteSnapshotAtMs = 0L
    // 🆕 ROLL-UP del HUD: HP mostrado (drena gradual hacia el real; subir = instantáneo)
    internal var dispHp0 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    internal var dispHp1 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    internal var onlineEndSent = false
    internal var countdownJob: Job? = null
    internal var roomsRefreshJob: Job? = null   // refresca LIST_ROOMS mientras estás en la lista de espera
    internal val isOnline: Boolean get() = _state.value.onlineStatus != SfOnlineStatus.OFF
    internal val inOnlineFight: Boolean get() = _state.value.onlineStatus == SfOnlineStatus.FIGHTING

    // ⚠️ `internal`, no `private`: las constantes las leen los PARCIALES del VM
    // (StreetFighterNet.kt y siguientes), que son extensiones en el mismo paquete.
    internal companion object {
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
        const val ROUND_INTRO_MS = 3600L         // 🆕 (2026-07-22) 3-2-1 + PELEA con input congelado
        const val ROUND_GRACE_MS = 1200L         // ignora estado/daño del rival en vuelo tras el reset
        // 🆕 (2026-07-25) BARRERA "AMBOS LISTOS": tope de espera del PLAYER_READY del rival tras
        // cargar los atlas; si no llega (cliente viejo / server sin redeploy), se arranca igual.
        const val READY_TIMEOUT_MS = 8000L
        // Ventana tras armar la barrera para que la Screen encienda CARGANDO (ronda 1). Sin CARGANDO
        // pasada esta ventana = atlas ya en memoria (rondas 2/3) → se avisa PLAYER_READY.
        const val READY_SETTLE_MS = 400L
        // 🆕 (2026-07-25) Tag de logcat para diagnosticar el flujo de red (sobre todo LAN).
        const val SF_NET_TAG = "SF-NET"
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
        // 🆕 (2026-07-25) WALL SPLAT: zona de "pared" (px desde el borde jugable) y rebote al centro
        // (despega al defensor y crea espacio en el escape de esquina).
        const val WALL_SPLAT_ZONE = 60f
        const val WALL_SPLAT_BOUNCE_PX = 46f
        // 🆕 Combos (3rd Strike): el HUD muestra el contador desde 2 golpes; cada golpe
        // encadenado hace -10% de daño (piso 50%) para que el combo no sea letal gratis.
        const val COMBO_DISPLAY_MIN = 2
        // 🆕 (2026-07-21) Tope de vida de una RUTA de combo encolada por la IA: si no la
        // completa a tiempo (el rival se soltó, la interrumpieron), se descarta en vez de
        // quedarse insistiendo con pasos que ya no aplican.
        const val COMBO_ROUTE_TIMEOUT_MS = 2200L
        /** Cuánto dura en pantalla el "¡OK!" de un paso acertado del tutorial. */
        const val TUTORIAL_FLASH_MS = 700L
        /** Espaciado entre avisos de error del tutorial (no saturar de mensajes). */
        const val TUTORIAL_ERROR_COOLDOWN_MS = 1500L
        // 🆕 (2026-07-22) Cuenta 3-2-1 entre lecciones (no se revisa input mientras corre).
        const val TUTORIAL_LESSON_COUNTDOWN_MS = 3000L
        const val MAX_ACTIVE_FIREBALLS_PER_FIGHTER = 1
        const val MAX_FIREBALLS_TOTAL = 4
        // Rangos IA (px): clinch → separar; melee → golpear; mid → footsies
        const val CPU_CLINCH_DIST = 58f
        const val CPU_MELEE_DIST = 105f
        const val CPU_MID_DIST = 175f
        // 🆕 (2026-07-25) Ventana para COMPLETAR un fatality comprometido (correr + soltar súper).
        const val FATALITY_INTENT_MS = 1500L
        // 🆕 (2026-07-22, Fase 2b) Límites del escenario extraídos a SfConstants (los usa
        // SfPhysics.clampToStage). Alias `const` para no tocar los usos internos del VM
        // (y evitar que detekt MayBeConst los marque tras volver const los de SfConstants).
        const val STAGE_X_MIN = SfConstants.STAGE_X_MIN
        const val STAGE_X_MAX = SfConstants.STAGE_X_MAX
        // 🆕 Interpolación del rival: tasa del lerp (≈rate*dt por tick) y distancia a partir
        // de la cual se SNAPEA (teleport/reset de ronda — no perseguirlo lerpeando)
        const val NET_LERP_RATE = 14f
        const val NET_SNAP_DIST = 80f
        // 🆕 Extrapolación de proyectiles remotos: tope de edad del snapshot (no sobrepasar)
        const val NET_FB_MAX_AGE_S = 0.25f
        // 🆕 (2026-07-26) Tope de claves de voz por snapshot: son eventos raros (un puñado por
        // pelea). El tope solo evita que la lista crezca sin límite si algo dejara de enviarse.
        const val NET_AUDIO_MAX_PER_SNAPSHOT = 4
        // 🆕 Sincronía del timer: el invitado adopta el del host si difieren >= este umbral
        const val TIMER_RESYNC_DIFF = 2
        // 🆕 Roll-up del HUD: velocidad de drenado de la barra (HP por segundo)
        const val HP_DRAIN_PER_SEC = 200f
        const val ZONE_DOWN = 1
        const val ZONE_FORWARD_DOWN = 2
        const val ZONE_FORWARD = 3
    }

    // 🆕 (2026-07-22, Fase 1) attackMeta (metadatos de ataque) extraido a SfDamage.ATTACK_META.
    internal val attackMeta = SfDamage.ATTACK_META

    // 🆕 (2026-07-22, Fase 1) La MAQUINA DE ESTADOS se extrajo a SfStateMachine (dato
    // PURO, testeable en JVM). Aqui quedan ALIAS para no tocar los usos internos de estas
    // tablas; el comportamiento no cambia (misma tabla validFrom). Solo se conservan los que
    // el VM sigue usando: las sub-listas (special/neutral/crouch/air) ya viven DENTRO de
    // SfStateMachine.VALID_FROM, así que sus alias quedaron muertos y se retiraron.
    internal val knockdownStates = SfStateMachine.KNOCKDOWN_STATES
    internal val attackValidFrom = SfStateMachine.ATTACK_VALID_FROM
    private val validFrom = SfStateMachine.VALID_FROM

    init {
        startGameLoop()
    }

    // ------------------------------------------------------------------
    // Game loop (reloj virtual)
    // ------------------------------------------------------------------

    /** 🆕 (2026-07-22) La Screen avisa si el overlay CARGANDO está visible → congela el reloj. */
    fun setAssetsLoadingUi(loading: Boolean) {
        val was = uiAssetsLoading
        uiAssetsLoading = loading
        // 🆕 (2026-07-25) Al TERMINAR de decodificar los atlas en una pelea online, avisa al rival
        // que ya estoy listo (barrera "ambos listos", punto 2).
        if (was && !loading && awaitingPeerReady && !localReadySent) sendLocalReady()
    }

    // ═══════════ 🆕 (2026-07-25) BARRERA "AMBOS LISTOS" (punto 2) ═══════════
    // Tras FIGHT_START cada teléfono decodifica sus atlas; el que ya cargó ESPERA el PLAYER_READY
    // del rival para arrancar la ronda sincronizada. Aplica a los 3 transportes; degrada por
    // timeout si el rival/server no soporta el mensaje. La verifican el tick (freeze + timeout) y
    // los eventos (carga local terminada / PLAYER_READY entrante).

    /**
     * Arma la barrera para la ronda que empieza (FIGHT_START o rondas 2/3). Solo online. NO avisa
     * de inmediato: al llegar FIGHT_START la Screen aún no encendió CARGANDO, así que el tick espera
     * la ventana READY_SETTLE_MS antes de dar por buena la ausencia de carga (rondas 2/3 = atlas ya
     * en memoria) y avisar. En la ronda 1, `setAssetsLoadingUi(false)` avisa al terminar de decodificar.
     */
    internal fun armReadyBarrier() {
        awaitingPeerReady = true
        localReadySent = false
        peerReady = false
        val nowReal = sfElapsedRealtime()
        readyArmedRealMs = nowReal
        readyBarrierUntilMs = nowReal + READY_TIMEOUT_MS
        sfLog("barrera armada (esperando PLAYER_READY del rival)")
    }

    private fun sendLocalReady() {
        if (localReadySent) return
        localReadySent = true
        transport?.sendReady()
        sfLog("PLAYER_READY enviado")
        maybeStartAfterReady()
    }

    /** Libera la barrera cuando AMBOS avisaron (o venció el timeout, desde el tick). */
    internal fun maybeStartAfterReady() {
        if (awaitingPeerReady && localReadySent && peerReady) releaseReadyBarrier(gameNow)
    }

    private fun releaseReadyBarrier(now: Long) {
        if (!awaitingPeerReady) return
        awaitingPeerReady = false
        roundIntroUntilMs = now + ROUND_INTRO_MS // 3-2-1 PELEA sincronizado a partir de aquí
        if (_state.value.waitingForOpponentReady) {
            _state.value = _state.value.copy(waitingForOpponentReady = false)
        }
        sfLog("barrera liberada: arranca la ronda")
    }

    private fun startGameLoop() {
        if (loopJob?.isActive == true) return
        lastRealMs = sfElapsedRealtime()
        loopJob = scope.launch {
            while (isActive) {
                delay(tickMs) // 16 ms (~60) o 33 ms (~30) en gama baja
                val real = sfElapsedRealtime()
                val dtMs = (real - lastRealMs).coerceAtMost(100L)
                lastRealMs = real
                val s = _state.value
                // 🆕 (2026-07-25) BARRERA "AMBOS LISTOS" (punto 2): el reloj de juego NO avanza
                // hasta que AMBOS teléfonos cargaron sus atlas (o venza el timeout de seguridad).
                if (awaitingPeerReady) {
                    // Sin CARGANDO tras la ventana de asentamiento = atlas ya en memoria (rondas 2/3)
                    // → avisa. En la ronda 1, setAssetsLoadingUi(false) avisa al terminar de decodificar.
                    val settled = real - readyArmedRealMs >= READY_SETTLE_MS
                    if (!localReadySent && !uiAssetsLoading && settled) sendLocalReady()
                    if (awaitingPeerReady) { // sigue esperando al rival
                        if (real >= readyBarrierUntilMs) {
                            releaseReadyBarrier(gameNow) // el rival no avisó → arranca igual
                        } else {
                            val waiting = !uiAssetsLoading // yo cargué; espero al rival
                            if (s.waitingForOpponentReady != waiting) {
                                _state.value = s.copy(waitingForOpponentReady = waiting)
                            }
                            continue
                        }
                    }
                }
                // El reloj de juego se detiene en pausa, diálogo de salida, selector de personaje
                // y 🆕 mientras la Screen muestra CARGANDO (para que el 3-2-1 se vea al terminar).
                if (s.isPaused || s.showExitDialog || s.inCharacterSelect || uiAssetsLoading) continue
                // La auditoría de campaña es un bot de QA, no una modalidad de juego: simula
                // varios ticks estables por frame para recorrer sus 135 peleas en tiempo útil.
                // El showcase conserva velocidad real para que cada audio pueda oírse completo.
                val auditSteps = if (gauntletActive && !showcaseMode) gauntletAuditSteps else 1
                for (step in 0 until auditSteps) {
                    if (step > 0 && !gauntletActive) break
                    val speed = if (gauntletActive) showcaseSpeed else 1f
                    val scaledDtMs = (dtMs * speed).toLong().coerceAtLeast(1L)
                    gameNow += scaledDtMs
                    tick(gameNow, scaledDtMs / 1000f)
                    if (step + 1 < auditSteps) yield()
                }
            }
        }
    }

    /** Holder mutable de la simulación de UN tick; se publica al final. */
    // ⚠️ `internal`: los parciales del VM (extensiones en el mismo paquete) reciben el `Sim`.
    class Sim(
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
        // 🆕 (2026-07-18o) VOZ DE INTRO del policía al arrancar la pelea (una sola vez por
        // combate/ronda; solo suena si el peleadór tiene intro — hoy policía HOMBRE).
        if (!introVoiceSent && !showcaseMode && roundIntroUntilMs > 0L && now < roundIntroUntilMs) {
            introVoiceSent = true
            withNetAudioCapture(0) { emitIntroVoice(s.player.id, now) }
            // 🆕 (2026-07-26) EN RED la intro del rival la manda ÉL (viaja en `audio`): si se
            // sorteara aquí, cada teléfono elegiría una línea DISTINTA del mismo pack y no
            // estaríamos oyendo lo mismo. Sin red (arcade/práctica) se emite como siempre.
            if (!online) emitIntroVoice(s.cpu.id, now)
        }
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
            platformApplyRemoteSnapshot(sim, now, dt)
            platformProcessNetDamage(sim, now)
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
        if (!online) collideFireballPairsCommon(sim, now)
        updateSplashes(sim, now)
        updateCamera(sim)
        updateKoFlash(sim, now)

        if (online) {
            platformAppendRemoteFireballs(sim, now)
            // 🆕 FIREBALL-VS-FIREBALL online: los MÍOS contra los del rival (simétrico: él hace
            // lo mismo con los suyos). ANTES de sendNetState para avisar el COLLIDED sin demora.
            collideFireballPairsCommon(sim, now)
            platformSendNetState(sim, now)
        }

        // 🆕 ROLL-UP del HUD: el HP mostrado drena gradual hacia el real (subir = instantáneo,
        // así el reset de ronda/revancha rellena la barra solo, sin tocar los resets)
        dispHp0 = rollUpHp(dispHp0, sim.p0.hitPoints, dt)
        dispHp1 = rollUpHp(dispHp1, sim.p1.hitPoints, dt)

        // 🆕 (2026-07-21) TUTORIAL: valida el paso en curso con el estado REAL del jugador.
        tickTutorial(sim, now)

        // El menú de fin SOLO con el COMBATE decidido (2 rondas); entre rondas solo se congela
        val showEnd = sim.battleEnded && matchOver && now >= endMenuAtMs
        _state.update(sim, now, showEnd)
    }

    private fun MutableStateFlow<StreetFighterState>.update(sim: Sim, now: Long, showEnd: Boolean) {
        val subActive = value.specialSubtitleUntilMs > 0L && now < value.specialSubtitleUntilMs
        // 🆕 (2026-07-20) COMBO del HUD: expira al pasar la ventana sin conectar otro golpe;
        // se muestra el del atacante con el combo MÁS RECIENTE (solo uno pega a la vez).
        for (i in 0..1) {
            if (comboHits[i] > 0 && now - comboLastHitMs[i] > RAPID_HIT_WINDOW_MS) comboHits[i] = 0
        }
        val comboIdx = when {
            comboHits[0] >= COMBO_DISPLAY_MIN && comboLastHitMs[0] >= comboLastHitMs[1] -> 0
            comboHits[1] >= COMBO_DISPLAY_MIN -> 1
            comboHits[0] >= COMBO_DISPLAY_MIN -> 0
            else -> -1
        }
        value = value.copy(
            comboCount = if (comboIdx >= 0) comboHits[comboIdx] else 0,
            comboPlayerId = comboIdx,
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
            // 🆕 (2026-07-22) 3-2-1: los últimos 600 ms del intro son "PELEA" (countdown 0).
            roundIntroCountdown = (roundIntroUntilMs - now - 600L).let {
                if (it > 0L) ((it + 999L) / 1000L).toInt() else 0
            },
            displayHp0 = dispHp0,
            displayHp1 = dispHp1,
            // limpiar subtítulo del special al expirar
            specialSubtitleHud = if (subActive) value.specialSubtitleHud else null,
            specialSubtitleUntilMs = if (subActive) value.specialSubtitleUntilMs else 0L,
            specialSubtitleStartMs = if (subActive) value.specialSubtitleStartMs else 0L,
        )
    }

    /** 🆕 Roll-up del HUD: drena hacia el HP real a HP_DRAIN_PER_SEC; subir es instantáneo. */
    // 🆕 (Fase 2c) El cálculo vive en `SfHealthBar` (`:shared`), con tests que corren en iOS.
    private fun rollUpHp(disp: Float, target: Int, dt: Float): Float =
        SfHealthBar.rollUp(disp, target, dt)

    // ------------------------------------------------------------------
    // Animación por frames (setAnimationFrame / updateAnimation del JS)
    // ------------------------------------------------------------------

    internal fun animOf(f: SfFighter) = dataFor(f).animations.getValue(f.state.jsKey)

    // 🆕 (2026-07-22, Fase 2a) La lógica de avance de animación vive en SfAnimation (puro,
    // testeable en JVM). Estas funciones quedan como ENVOLTORIOS que pasan animOf(f):
    // mismos nombres y firmas → cero cambios en sus call sites, comportamiento idéntico.
    internal fun withAnimationFrame(f: SfFighter, frame: Int, now: Long): SfFighter {
        val anim = animOf(f)
        val idx = SfAnimation.frameIndex(anim, frame)
        return f.copy(
            animationFrame = idx,
            animationTimerMs = SfAnimation.frameTimerMs(anim, idx, now),
        )
    }

    private fun updateAnimation(f: SfFighter, now: Long): SfFighter {
        if (!SfAnimation.shouldAdvance(animOf(f), f.animationFrame, f.animationTimerMs, now)) {
            return f // FREEZE/TRANSITION o aún no toca
        }
        return withAnimationFrame(f, f.animationFrame + 1, now)
    }

    /** Anima solo la pose neutral durante la presentacion, sin mover ni aceptar input. */
    private fun updateRoundIntroAnimation(f: SfFighter, now: Long): SfFighter {
        if (f.state != SfFighterState.IDLE) return f
        return if (f.animationTimerMs <= 0L) withAnimationFrame(f, 0, now)
        else updateAnimation(f, now)
    }

    // (El "por qué" del último-frame-como-fin — fix 2026-07-18 de hojas sin -1 — vive ahora
    // en el KDoc de SfAnimation.isCompleted; misma semántica, solo cambió de sitio.)
    internal fun isAnimationCompleted(f: SfFighter): Boolean =
        SfAnimation.isCompleted(animOf(f), f.animationFrame)

    // ------------------------------------------------------------------
    // Cambio de estado + inits (changeState del JS)
    // ------------------------------------------------------------------

    /**
     * 🆕 (2026-07-21) ¿El peleador TIENE arte para este estado? Los movimientos nuevos
     * (hojas 20-29) no existen para todos: sin esta guarda, quien no los tenga entraría a
     * un estado sin animación y se quedaría congelado.
     */
    internal fun hasAnim(f: SfFighter, state: SfFighterState): Boolean =
        !dataFor(f).animations[state.jsKey].isNullOrEmpty() || hasAlphaFallback(f, state)

    /**
     * 🆕 (2026-07-21) PLACEHOLDER "ALPHA": si a un peleador le falta la hoja de un
     * movimiento, se usa la del ESTUDIANTE de su mismo género (ESCOMBOY / ESCOMGIRL)
     * como marcador — la View lo pinta en SILUETA NEGRA PIXELADA con el rótulo "ALPHA",
     * igual que los personajes bloqueados. Así el movimiento SE PUEDE JUGAR y se ve claro
     * que ese arte todavía no es definitivo.
     */
    fun alphaFallbackId(id: SfFighterId): SfFighterId =
        if (id in sfMaleFighters) SfFighterId.ESCOMBOY else SfFighterId.ESCOMGIRL

    private fun hasAlphaFallback(f: SfFighter, state: SfFighterState): Boolean {
        val fallbackId = alphaFallbackId(f.id)
        if (fallbackId == f.id) return false
        return !SfFrameCatalog.load(fallbackId)
            .animations[state.jsKey].isNullOrEmpty()
    }

    /** ¿Este estado se está dibujando con el placeholder ALPHA (arte prestada)? */
    fun usesAlphaFallback(f: SfFighter): Boolean =
        f.state in SF_NEW_MOVE_STATES &&
            dataFor(f).animations[f.state.jsKey].isNullOrEmpty() &&
            hasAlphaFallback(f, f.state)

    internal fun changeState(sim: Sim, idx: Int, newState: SfFighterState, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (newState != f.state && validFrom.getValue(newState).let { f.state !in it }) return false
        if (newState in SF_NEW_MOVE_STATES && !hasAnim(f, newState)) return false

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
                // 🆕 grito al golpear (pack; con cooldown). El whoosh de arriba NO se captura:
                // el rival lo deriva del estado en emitRemoteStateSfx.
                withNetAudioCapture(idx) { emitAttackVoice(nf.id, idx, now) }
            }
            SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY -> {
                nf = nf.copy(velocityX = 0f, velocityY = 0f, attackStruck = false, fireballFired = false)
                // Especial por personaje + subtítulo de frase (ES/EN catálogo)
                withNetAudioCapture(idx) { emitSpecialVoice(nf.id, now) }
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
                withNetAudioCapture(idx) { emitSpecialVoice(nf.id, now) }
            }
            // ── 🆕 (2026-07-21) MOVESET 3rd Strike ──
            SfFighterState.DASH_FORWARD ->
                nf = nf.copy(velocityX = SfConstants.DASH_FORWARD_VELOCITY, velocityY = 0f)
            SfFighterState.RUN -> nf = nf.copy(velocityX = SfConstants.RUN_VELOCITY, velocityY = 0f)
            SfFighterState.DASH_BACKWARD ->
                nf = nf.copy(velocityX = SfConstants.DASH_BACKWARD_VELOCITY, velocityY = 0f)
            SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
            SfFighterState.CROUCH_HEAVY_PUNCH, SfFighterState.SWEEP,
            SfFighterState.LONG_KICK, SfFighterState.OVERHEAD, SfFighterState.GRAB,
            -> {
                nf = nf.copy(velocityX = 0f, velocityY = 0f, attackStruck = false)
                _soundEvents.tryEmit("${attackMeta.getValue(newState).strength.name.lowercase()}-attack")
                withNetAudioCapture(idx) { emitAttackVoice(nf.id, idx, now) }
            }
            // Los aéreos NO ponen la velocidad a cero: conservan el arco del salto.
            SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK -> {
                nf = nf.copy(attackStruck = false)
                _soundEvents.tryEmit("medium-attack")
            }
            SfFighterState.SUPER_ART, SfFighterState.FATALITY -> {
                // Súper y fatality CONSUMEN el medidor entero: no se repiten sin recargarlo.
                nf = nf.copy(
                    velocityX = 0f, velocityY = 0f, attackStruck = false, superMeter = 0,
                )
                withNetAudioCapture(idx) { emitSpecialVoice(nf.id, now) }
            }
            SfFighterState.THROW -> nf = nf.copy(velocityX = 0f, velocityY = 0f)
            SfFighterState.THROWN -> nf = nf.copy(velocityX = 0f, velocityY = 0f, downed = true)
            SfFighterState.GET_UP -> nf = nf.copy(velocityX = 0f, velocityY = 0f)
            SfFighterState.BLOCK_HIGH, SfFighterState.BLOCK_LOW,
            SfFighterState.PARRY_HIGH, SfFighterState.PARRY_LOW,
            SfFighterState.TAUNT, SfFighterState.HURT_CROUCH,
            -> nf = nf.copy(velocityX = 0f, velocityY = 0f)
            // 🆕 (2026-07-22) MAREO: congelado ~2 s y el medidor de mareo se VACÍA al entrar
            // (si no, otro golpe lo re-aturdía en bucle). Sale a IDLE en runStateHandler.
            SfFighterState.STUN -> {
                nf = nf.copy(velocityX = 0f, velocityY = 0f, dizzyMeter = 0)
                stunUntilMs[idx.coerceIn(0, 1)] = now + SfConstants.STUN_DURATION_MS
                _soundEvents.tryEmit("land") // golpe seco al caer mareado
            }
            else -> Unit // CROUCH / CROUCH_UP / IDLE_TURN / CROUCH_TURN: sin init
        }
        // 🆕 El parry abre su ventana ACTIVA al entrar (fuera de ella no protege).
        if (newState in SF_PARRY_STATES) parryActiveUntilMs[idx.coerceIn(0, 1)] =
            now + SfConstants.PARRY_WINDOW_MS
        // 🆕 Cada salto NUEVO devuelve el derecho a un ataque aéreo.
        if (newState == SfFighterState.JUMP_START || newState == SfFighterState.JUMP_LAND) {
            airAttackUsed[idx.coerceIn(0, 1)] = false
        }
        // 🆕 (2026-07-18k) VICTORY con la VOZ del peleadór (reutiliza su special_<id>.m4a):
        // la celebración de fin de ronda estaba muda; el dueño pidió reutilizar audios
        // correctos antes que dejar animaciones sin sonido.
        if (newState == SfFighterState.VICTORY && f.state != SfFighterState.VICTORY) {
            withNetAudioCapture(idx) { emitWinVoice(nf.id, now) }
        }
        // 🆕 (2026-07-19) Voz de DERROTA: cuando un peleadór entra en KO, emite su quejido de loss.
        if (newState == SfFighterState.KO && f.state != SfFighterState.KO) {
            withNetAudioCapture(idx) { emitLossVoice(nf.id, now) }
        }
        sim.setFighter(idx, nf)
        return true
    }

    // ------------------------------------------------------------------
    // Update de un peleador (Fighter.update del JS: handler → posición →
    // slide → animación → constraints → colisión de ataque)
    // ------------------------------------------------------------------

    private fun updateFighter(sim: Sim, idx: Int, input: SfInput, now: Long, dt: Float) {
        // 🆕 (2026-07-21) Cubrirse ABAJO = agachado + atrás. Se registra ANTES de correr el
        // handler porque applyAttackHit lo consulta al resolver el golpe de este tick.
        defenderBlockingLow[idx.coerceIn(0, 1)] = input.down && input.backward
        // 🆕 Castigo del parry: el atacante parriado se queda vendido (no actúa).
        if (now < parryStunUntilMs[idx.coerceIn(0, 1)]) {
            runStateHandler(sim, idx, SfInput(), now, dt)
            finishFighterUpdate(sim, idx, now, dt)
            return
        }
        runStateHandler(sim, idx, input, now, dt)
        finishFighterUpdate(sim, idx, now, dt)
    }

    /** Cola común del update: posición, slide, animación, límites y colisión de ataque. */
    private fun finishFighterUpdate(sim: Sim, idx: Int, now: Long, dt: Float) {
        // 🆕 (2026-07-22, Fase 1) cinemática de un tick (posición + slide) extraída a SfPhysics (puro).
        sim.setFighter(idx, SfPhysics.step(sim.fighter(idx), dt))

        sim.setFighter(idx, updateAnimation(sim.fighter(idx), now))
        updateStageConstraints(sim, idx, dt)
        // Doble seguro: tras anim/empuje, NUNCA fuera de pantalla (IA vs IA)
        sim.setFighter(idx, clampFighterToStage(sim.fighter(idx)))
        watchStuck(sim, idx, now) // 🆕 red de seguridad: desatasca animaciones que no terminan
        applyMeterDecay(sim, idx, now, dt) // 🆕 (2026-07-22) mareo/stun + decaimientos
        updateAttackBoxCollided(sim, idx, now)
    }

    /**
     * 🆕 (2026-07-22) MAREO/STUN + decaimiento de medidores (corre cada tick, TODOS los modos).
     * - Mareo lleno → estado STUN (~2 s congelado, estrellitas en la Screen) y el medidor se
     *   vacía al entrar (changeState). Un golpe durante el stun lo saca por HURT_*.
     * - El mareo DECAE tras ~1.5 s sin recibir; el súper decae LENTO tras ~4 s sin conectar
     *   (la barra ya LLENA no decae: la súper cargada no se pierde sola).
     * - ONLINE: el peleador REMOTO (idx 1) se pisa por snapshot → solo se procesa el LOCAL;
     *   su STUN llega por el `state` del snapshot (enum.name, parse defensivo).
     */
    private fun applyMeterDecay(sim: Sim, idx: Int, now: Long, dt: Float) {
        val i = idx.coerceIn(0, 1)
        if (i == 1 && _state.value.onlineStatus != SfOnlineStatus.OFF) return
        if (sim.battleEnded) return
        var f = sim.fighter(idx)
        if (f.state == SfFighterState.KO || f.state == SfFighterState.VICTORY) return
        // Disparo del STUN (dureza MODERADA): solo en piso, no derribado ni transformándose.
        if (f.dizzyMeter >= SfConstants.DIZZY_METER_MAX &&
            f.state != SfFighterState.STUN &&
            !f.downed && !f.isAirborne && !isMetamorphosing(f)
        ) {
            changeState(sim, idx, SfFighterState.STUN, now)
            f = sim.fighter(idx)
        }
        // Decaimiento del MAREO (acumulador fraccional: el medidor es Int).
        if (f.state != SfFighterState.STUN &&
            f.dizzyMeter in 1 until SfConstants.DIZZY_METER_MAX &&
            now - lastHitTakenMs[i] > SfConstants.DIZZY_DECAY_GRACE_MS
        ) {
            dizzyDecayAcc[i] += SfConstants.DIZZY_DECAY_PER_SEC * dt
            val drop = dizzyDecayAcc[i].toInt()
            if (drop > 0) {
                dizzyDecayAcc[i] -= drop
                sim.setFighter(
                    idx,
                    sim.fighter(idx).copy(dizzyMeter = (f.dizzyMeter - drop).coerceAtLeast(0)),
                )
            }
        }
        // Decaimiento LENTO del súper si dejas de acertar.
        val g = sim.fighter(idx)
        if (g.superMeter in 1 until SfConstants.SUPER_METER_MAX &&
            now - superKeepMs[i] > SfConstants.SUPER_DECAY_GRACE_MS
        ) {
            superDecayAcc[i] += SfConstants.SUPER_DECAY_PER_SEC * dt
            val drop = superDecayAcc[i].toInt()
            if (drop > 0) {
                superDecayAcc[i] -= drop
                sim.setFighter(idx, g.copy(superMeter = (g.superMeter - drop).coerceAtLeast(0)))
            }
        }
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
                sfLog(issue)
                if (gauntletActive) logAssetIssue(issue)
                lastStalemateLogMs = now
            }
            cpuNextDecisionMs[0] = 0L
            cpuNextDecisionMs[1] = 0L
            cpuHold[0] = SfInput()
            cpuHold[1] = SfInput()
            cpuComboQueue[0].clear()
            cpuComboQueue[1].clear()
            cpuComboAwaiting[0] = null
            cpuComboAwaiting[1] = null
            cpuLastOffenseMs[0] = 0L
            cpuLastOffenseMs[1] = 0L
            cpuWantsSpaceUntilMs[0] = 0L
            cpuWantsSpaceUntilMs[1] = 0L
            cpuForceEngageUntilMs[0] = now + 3000L
            cpuForceEngageUntilMs[1] = now + 3000L
        }
    }

    internal fun logAssetIssue(msg: String) {
        if (assetIssues.add(msg)) sfLog(msg)
    }

    /** Reporte de problemas detectados en la sesión (assets rotos / atascos / estancamientos). */
    fun diagnosticsReport(): List<String> = assetIssues.toList()

    private val fireballActiveDelays = listOf(5, 2, 5, 1)
    internal val fireballCollidedDelays = listOf(13, 3, 7)
    internal val fireballBox = SfBox(-15f, -13f, 30f, 24f)

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

    // 🆕 (Fase 2c) Avance de los chispazos → `SfSplashes` (`:shared`).
    private fun updateSplashes(sim: Sim, now: Long) = SfSplashes.advance(sim.splashes, now)

    // ------------------------------------------------------------------
    // Cámara (Camera.js)
    // ------------------------------------------------------------------

    // 🆕 (Fase 2c) El seguimiento de cámara vive en `SfCamera` (`:shared`), sin Android y con
    // tests que corren también en iOS. Aquí solo queda volcarlo al Sim.
    private fun updateCamera(sim: Sim) {
        val pos = SfCamera.follow(sim.p0, sim.p1, sim.camX)
        sim.camX = pos.x
        sim.camY = pos.y
    }

    // ------------------------------------------------------------------
    // Timer + KO flash (StatusBar.js)
    // ------------------------------------------------------------------

    private fun updateTimer(sim: Sim, now: Long) {
        // 🆕 (2026-07-21) TUTORIAL: sin reloj. Una lección no se puede "perder por tiempo".
        if (inTutorial) return
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
            // timeout: ambos lo calculan; los guards evitan doble envío
            endRound(sim, winnerIdx, now, computeRoundOutcome(sim, winnerIdx, koState = null, byTime = true))
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
        // 🆕 (2026-07-22) Auto-encarar al jugador ANTES de leer forward/backward: tras cruzar de
        // lado, la cara vieja invertía las direcciones y girar era "muy complicado" (había que
        // soltar todo y quedar quieto). Ahora se voltea solo, como ya hacía la CPU.
        repairFacing(sim, 0, now)
        val idle = sfElapsedRealtime() - joyLastMs > JOYSTICK_IDLE_MS
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

        // 🆕 (2026-07-21) DASH por DOBLE TOQUE de dirección (3rd Strike). Se detecta el
        // FLANCO (no estaba pulsada y ahora sí): si hubo otro toque dentro de la ventana,
        // sale el dash. Sostener la dirección sigue siendo caminar, como siempre.
        val (prevF, prevB) = dirHeldPrev[0]
        var dashF = false
        var dashB = false
        if (forward && !prevF) {
            dashF = now - lastForwardTapMs[0] <= SfConstants.DASH_DOUBLE_TAP_MS
            lastForwardTapMs[0] = now
        }
        if (backward && !prevB) {
            dashB = now - lastBackwardTapMs[0] <= SfConstants.DASH_DOUBLE_TAP_MS
            lastBackwardTapMs[0] = now
        }
        dirHeldPrev[0] = forward to backward

        val parry = pendingParry; pendingParry = false
        val grab = pendingGrab; pendingGrab = false
        val taunt = pendingTaunt; pendingTaunt = false
        val superArt = pendingSuperArt; pendingSuperArt = false

        return SfInput(
            up = up, down = down, forward = forward, backward = backward,
            lightPunch = lp, mediumPunch = mp, heavyPunch = hp,
            lightKick = lk, mediumKick = mk, heavyKick = hk,
            special = special,
            bonusPower = bonusPower,
            dashForward = dashF, dashBackward = dashB,
            parry = parry, grab = grab, taunt = taunt, superArt = superArt,
        )
    }

    // ── 🆕 (2026-07-21) Intenciones del moveset nuevo que emite la View (botones) ──
    private var pendingParry = false
    private var pendingGrab = false
    private var pendingTaunt = false
    private var pendingSuperArt = false

    /** Botón PARRY: desvía el golpe si se aprieta a tiempo (alto de pie, bajo agachado). */
    fun onParryPressed() { pendingParry = true }

    /** Botón AGARRE: si el rival está pegado, lo lanza. */
    fun onGrabPressed() { pendingGrab = true }

    /** Botón BURLA. */
    fun onTauntPressed() { pendingTaunt = true }

    /** Botón SÚPER: solo sale con el medidor lleno. */
    fun onSuperArtPressed() { pendingSuperArt = true }

    /** ¿El peleador del jugador tiene el moveset nuevo? (para mostrar u ocultar botones). */
    fun playerHasNewMoves(): Boolean {
        val f = _state.value.player
        return !dataFor(f).animations[SfFighterState.PARRY_HIGH.jsKey].isNullOrEmpty()
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
    internal val cpuThreatStates = setOf(
        SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
        SfFighterState.LIGHT_KICK, SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
        SfFighterState.SPECIAL_1_LIGHT, SfFighterState.SPECIAL_1_MEDIUM, SfFighterState.SPECIAL_1_HEAVY,
        // 🆕 (2026-07-21) Los golpes nuevos también son AMENAZA (la IA los bloquea/parria)
    ) + SF_BONUS_POWER_STATES + SF_NEW_ATTACK_STATES

    /** Estados en los que el RIVAL está vulnerable (recuperación) → la avanzada CASTIGA. */
    internal val cpuPunishStates = setOf(
        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
        SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
        SfFighterState.JUMP_LAND, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH_UP,
        // 🆕 (2026-07-21) Recuperaciones nuevas que se pueden castigar
        SfFighterState.HURT_CROUCH, SfFighterState.TAUNT, SfFighterState.THROW,
        SfFighterState.SWEEP, SfFighterState.SUPER_ART,
    )

    // ── 🆕 (2026-07-21) COMBOS del catálogo (assets/DATA/combos.json) ──
    // La IA encola los pasos de una ruta y los ejecuta EN ORDEN; el tutorial usa la misma
    // traducción acción→input para validar lo que hace el jugador.

    internal val comboCatalog by lazy { SfCombos.universal() }
    internal fun signatureCombo(id: SfFighterId) = SfCombos.signature(id)

    /** Cola de acciones pendientes por índice (la IA ejecuta una por decisión). */
    internal val cpuComboQueue = Array(2) { ArrayDeque<SfComboAction>() }
    internal data class CpuComboAttempt(
        val action: SfComboAction,
        val stateBefore: SfFighterState,
        val animationTimerBeforeMs: Long,
    )
    /** Paso emitido que todavía debe ser confirmado por un cambio real de estado/animación. */
    internal val cpuComboAwaiting = arrayOfNulls<CpuComboAttempt>(2)
    internal val cpuComboUntilMs = LongArray(2)

    // ⚠️ Estas DOS se quedan como MIEMBROS a propósito: son extensiones de `SfInput` declaradas
    // DENTRO de la clase (doble receptor: el VM + el SfInput). Fuera de la clase Kotlin no
    // admite dos receptores, así que NO se pueden mover a un parcial. No lo intentes.

    internal fun SfInput.hasAttackOrSpecial(): Boolean =
        lightPunch || mediumPunch || heavyPunch || lightKick || mediumKick || heavyKick ||
            special != null || bonusPower != null || superArt || grab || parry || taunt ||
            dashForward || dashBackward || up

    internal fun SfInput.isOnlyWalkToward(me: SfFighter, foe: SfFighter): Boolean {
        if (hasAttackOrSpecial() || up || down) return false
        val toward = cpuMoveTowardFlags(me, foe)
        return (forward && toward.forward && !backward) || (backward && toward.backward && !forward)
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
        joyLastMs = sfElapsedRealtime()
    }

    /** 🆕 (2026-07-22) Al SOLTAR el joystick: limpia direcciones YA (sin esperar el timer idle),
     *  para que agacharse/caminar se corten al instante (antes se sentía "pegado"). */
    fun onJoystickRelease() {
        joyLeft = false; joyRight = false; joyUp = false; joyDown = false
        joyLastMs = sfElapsedRealtime()
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
        val idle = sfElapsedRealtime() - joyLastMs > JOYSTICK_IDLE_MS
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
    /**
     * 🆕 (2026-07-21) ¿Este poder tiene animación EMPAQUETADA? Un poder RETIRADO (su hoja
     * traía arte de otro personaje, ver `BONUS_REMOVED_POWERS` en `pack_sf_character.py`)
     * no la tiene, y `tryBonusPower` lo rechaza. Sin esta comprobación el selector se
     * paraba igualmente en él y esa pulsación se perdía sin que el jugador supiera por qué.
     */
    private fun bonusPowerHasAnim(f: SfFighter, power: Int): Boolean {
        val state = sfBonusPowerState(power) ?: return false
        return !dataFor(f).animations[state.jsKey].isNullOrEmpty()
    }

    fun onBonusPowerPressed() {
        val s = _state.value
        if (s.battleEnded || s.isPaused || s.showExitDialog) return
        val count = usableBonusPowerCount(s.player.id)
        if (count <= 0) return
        // Avanza hasta el siguiente poder que SÍ se pueda lanzar; si ninguno lo tiene,
        // deja el cursor como estaba en vez de girar en vacío.
        var next = bonusPowerCursor
        repeat(count) {
            next = next % count + 1
            if (bonusPowerHasAnim(s.player, next)) {
                bonusPowerCursor = next
                pendingBonusPower = next
                return
            }
        }
    }

    fun requestExit() {
        _state.value = _state.value.copy(showExitDialog = true)
    }

    fun dismissExitDialog() {
        lastRealMs = sfElapsedRealtime()
        _state.value = _state.value.copy(showExitDialog = false)
    }

    fun togglePause() {
        lastRealMs = sfElapsedRealtime()
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
    internal var tutorialCombos: List<SfCombo> = emptyList()
    internal var tutorialFlashUntilMs = 0L
    internal var tutorialErrorUntilMs = 0L
    // 🆕 (2026-07-22) Hasta cuándo corre la cuenta 3-2-1 de la lección recién cargada.
    internal var tutorialLessonReadyMs = 0L

    /** ¿Este combate es el tutorial? (lo consultan el tick y la IA para inhibir al muñeco). */
    internal val inTutorial: Boolean get() = _state.value.tutorialActive
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
            platformCancelOnline()
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
    /**
     * 🆕 (2026-07-18j) Estados EXTRA del showcase tras los poderes: inalcanzables por input.
     * Orden pensado: giros primero (terminan en IDLE/CROUCH→sube solo), luego los 6 HURT,
     * y al final KO (queda tendido) → VICTORY (se levanta a celebrar).
     */
    internal val showcaseExtraStates = listOf(
        SfFighterState.IDLE_TURN, SfFighterState.CROUCH_TURN,
        SfFighterState.HURT_HEAD_LIGHT, SfFighterState.HURT_HEAD_MEDIUM, SfFighterState.HURT_HEAD_HEAVY,
        SfFighterState.HURT_BODY_LIGHT, SfFighterState.HURT_BODY_MEDIUM, SfFighterState.HURT_BODY_HEAVY,
        SfFighterState.KO, SfFighterState.VICTORY,
    )
    internal fun resetInternals() {
        gameNow = 0L
        lastRealMs = sfElapsedRealtime()
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
        cpuFatalityUntilMs[0] = 0L
        cpuFatalityUntilMs[1] = 0L
        cpuSuperReadySinceMs[0] = 0L
        cpuSuperReadySinceMs[1] = 0L
        cpuComboQueue[0].clear()
        cpuComboQueue[1].clear()
        cpuComboAwaiting[0] = null
        cpuComboAwaiting[1] = null
        cpuComboUntilMs.fill(0L)
        cpuAttackHistory[0].clear()
        cpuAttackHistory[1].clear()
        cpuDiffOverride[0] = null
        cpuDiffOverride[1] = null
        introVoiceSent = false
        lastHurtVoiceMs.fill(0L)
        lastAttackVoiceMs.fill(0L)
        lastHitTakenMs.fill(0L)
        lowHpVoiceTriggered.fill(false)
        rapidHitsTaken.fill(0)
        comboEscapeUntilMs.fill(0L)
        wallSplatUsed.fill(false)
        comboHits.fill(0)
        comboLastHitMs.fill(0L)
        // 🆕 (2026-07-21) moveset nuevo
        parryActiveUntilMs.fill(0L)
        parryStunUntilMs.fill(0L)
        // 🆕 (2026-07-22) mareo/stun + decaimientos
        stunUntilMs.fill(0L)
        superKeepMs.fill(0L)
        dizzyDecayAcc.fill(0f)
        superDecayAcc.fill(0f)
        airAttackUsed.fill(false)
        lastForwardTapMs.fill(0L)
        lastBackwardTapMs.fill(0L)
        defenderBlockingLow.fill(false)
        dirHeldPrev[0] = false to false
        dirHeldPrev[1] = false to false
        pendingParry = false
        pendingGrab = false
        pendingTaunt = false
        pendingSuperArt = false
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
        // 🆕 (2026-07-25) pelea nueva: grado de ronda y barrera "ambos listos" en limpio
        pendingRoundOutcome = SfRoundOutcome.NORMAL
        pendingRoundOutcomeWinner = -1
        awaitingPeerReady = false
        localReadySent = false
        peerReady = false
        // 🆕 (2026-07-25) Calificación del combate: contadores en cero (se mide POR combate).
        gradeDealt = 0
        gradeTaken = 0
        gradeParries = 0
        gradeMaxCombo = 0
        gradeSupers = 0
        gradeHits = 0
        gradePerfects = 0
        gradeMoves.clear()
        // 🆕 SESIÓN 4: gameNow vuelve a 0 → resetear también lo anclado a él y el HUD
        lastSeenSnapshot = null
        remoteSnapshotAtMs = 0L
        // 🆕 (2026-07-26) Audio de red: ni voces a medio mandar ni transición de SFX heredada
        pendingNetAudio.clear()
        lastRemoteSfxState = null
        dispHp0 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
        dispHp1 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    }


    /**
     * 🆕 Fin de RONDA (KO, timeout o adelanto por red). Suma la ronda al ganador y decide:
     * ¿alguien llegó a ROUNDS_TO_WIN? → COMBATE terminado (menú de fin, MATCH_ENDED).
     * ¿No? → congela con "X WINS" y programa la ronda siguiente (ROUND_ENDED al rival).
     */
    /**
     * 🆕 (2026-07-25) Calcula la NOTA del combate (E..MS) del jugador, estilo SF III. Premia la
     * OFENSIVA (daño, combos, súpers), la técnica (parries, variedad de golpes) y las rondas
     * perfectas; penaliza el daño RECIBIDO. Umbrales afinados por razonamiento (sin dispositivo):
     * ajustar si en la práctica cuesta demasiado subir de nota.
     */
    private fun computeMatchGrade(): String {
        val score = gradeDealt * 1.0 -
            gradeTaken * 0.7 +
            gradeParries * 45 +
            gradeMaxCombo * 18 +
            gradeSupers * 35 +
            gradeMoves.size * 10 +
            gradePerfects * 70
        return when {
            score >= 700 -> "MS" // Master
            score >= 520 -> "S"
            score >= 380 -> "A"
            score >= 260 -> "B"
            score >= 160 -> "C"
            score >= 80 -> "D"
            else -> "E"
        }
    }

    internal fun endRound(
        sim: Sim,
        winnerIdx: Int,
        now: Long,
        outcome: SfRoundOutcome = SfRoundOutcome.NORMAL,
    ) {
        if (sim.battleEnded) return
        sim.winner = winnerIdx
        sim.battleEnded = true
        // 🆕 (2026-07-25) Grado de la ronda (PERFECT/COMBO/SUPER/TIME): resetRound lo pinta bajo
        // la barra del ganador en la ronda siguiente. Solo importa si HABRÁ ronda siguiente.
        pendingRoundOutcome = outcome
        pendingRoundOutcomeWinner = winnerIdx
        // 🆕 (2026-07-25) Calificación: una ronda PERFECTA del jugador (ganó con la vida al máximo).
        if (gradeTrackingOn && winnerIdx == 0 && outcome == SfRoundOutcome.PERFECT) gradePerfects++
        val s = _state.value
        val w0 = s.playerRoundWins + if (winnerIdx == 0) 1 else 0
        val w1 = s.cpuRoundWins + if (winnerIdx == 1) 1 else 0
        _state.value = _state.value.copy(playerRoundWins = w0, cpuRoundWins = w1)
        if (w0 >= ROUNDS_TO_WIN || w1 >= ROUNDS_TO_WIN) {
            matchOver = true
            endMenuAtMs = now + END_MENU_DELAY_MS
            // 🆕 (2026-07-25) GRADO del combate (E..MS): solo si el JUGADOR ganó (nota de rendimiento).
            if (gradeTrackingOn && winnerIdx == 0) {
                _state.value = _state.value.copy(matchGrade = computeMatchGrade())
            }
            if (isOnline) sendOnlineEnd(winnerIdx)
            // 🆕 ARCADE (offline): desbloqueo/avance de la escalera al decidirse el combate.
            if (_state.value.arcadeActive) handleArcadeMatchEnd(winnerIdx)
        } else {
            roundResetAtMs = now + ROUND_RESET_DELAY_MS
            if (isOnline && !roundEndSent) {
                roundEndSent = true
                transport?.sendRoundEnded(networkSideOf(winnerIdx), outcome.name)
            }
        }
    }

    /**
     * 🆕 (2026-07-25) Calcula el GRADO de una ronda ganada (estilo SF III). PERFECT es computable
     * en cualquier lado (HP del ganador al máximo); SUPER/COMBO requieren conocer el golpe de KO
     * (solo en el lado que lo simuló → `koState`/`comboHits`); TIME lo fija el timeout.
     */
    internal fun computeRoundOutcome(
        sim: Sim,
        winnerIdx: Int,
        koState: SfFighterState?,
        byTime: Boolean,
    ): SfRoundOutcome = when {
        byTime -> SfRoundOutcome.TIME
        sim.fighter(winnerIdx).hitPoints >= SfConstants.HEALTH_MAX_HIT_POINTS -> SfRoundOutcome.PERFECT
        koState == SfFighterState.SUPER_ART || koState == SfFighterState.FATALITY -> SfRoundOutcome.SUPER
        comboHits[winnerIdx.coerceIn(0, 1)] >= COMBO_DISPLAY_MIN -> SfRoundOutcome.COMBO
        else -> SfRoundOutcome.NORMAL
    }

    /** ROUND_ENDED recibido: reconcilia el fin de RONDA si mi sim aún no lo detectaba. */
    internal fun roundEndedFromNet(winnerSide: String?, outcomeName: String?) {
        val s = _state.value
        if (s.battleEnded || s.onlineStatus != SfOnlineStatus.FIGHTING) return
        val winnerIdx = networkIndexOf(winnerSide)
        val now = gameNow
        roundEndSent = true // ya lo publicó el otro lado; no re-enviar
        // 🆕 (2026-07-25) Grado que viajó desde el lado que simuló el KO (PERFECT/COMBO/SUPER/TIME).
        pendingRoundOutcome = outcomeName
            ?.let { n -> runCatching { SfRoundOutcome.valueOf(n) }.getOrNull() }
            ?: SfRoundOutcome.NORMAL
        pendingRoundOutcomeWinner = winnerIdx
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
        // 🆕 (2026-07-22, decisión del dueño) La METAMORFOSIS PERSISTE entre rondas: si La
        // Presidenta ya se transformó en Yoalli, la ronda siguiente ARRANCA como Yoalli y NO
        // se re-transforma (antes `metamorphosed` volvía al false del base con el id nuevo →
        // estado inconsistente: "en la segunda regresa a ser La Presidenta").
        // 🆕 (2026-07-25, decisión del dueño) La barra de PODER (súper) PERSISTE entre rondas: si
        // llenaste el medidor en la ronda 1 sigue lleno en la 2 y la 3 (como en el SF original). El
        // mareo/STUN (dizzyMeter) NO se conserva — queda en el default de `base` (0) → "se regenera".
        // El KO no vacía el súper (solo lo consumen SUPER_ART/FATALITY), así que ambos lados lo llevan.
        val p0 = base.player.copy(
            id = s.player.id,
            metamorphosed = s.player.metamorphosed,
            superMeter = s.player.superMeter,
            x = if (playerLeft) leftX else rightX,
            direction = if (playerLeft) SfDirection.RIGHT else SfDirection.LEFT,
        )
        val p1 = base.cpu.copy(
            id = s.cpu.id,
            metamorphosed = s.cpu.metamorphosed,
            superMeter = s.cpu.superMeter,
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
        introVoiceSent = false // 🆕 la intro del policía vuelve a sonar en la ronda nueva
        cpuNextDecisionMs[0] = 0L
        cpuNextDecisionMs[1] = 0L
        cpuHold[0] = SfInput()
        cpuHold[1] = SfInput()
        specialCooldownUntil[0] = 0L
        specialCooldownUntil[1] = 0L
        cpuFatalityUntilMs[0] = 0L
        cpuFatalityUntilMs[1] = 0L
        cpuSuperReadySinceMs[0] = 0L
        cpuSuperReadySinceMs[1] = 0L
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
        cpuComboQueue[0].clear()
        cpuComboQueue[1].clear()
        cpuComboAwaiting[0] = null
        cpuComboAwaiting[1] = null
        cpuComboUntilMs.fill(0L)
        lastHitTakenMs.fill(0L)
        rapidHitsTaken.fill(0)
        comboEscapeUntilMs.fill(0L)
        wallSplatUsed.fill(false)
        comboHits.fill(0)
        comboLastHitMs.fill(0L)
        // 🆕 (2026-07-21) moveset nuevo (la ronda nueva arranca sin ventanas abiertas)
        parryActiveUntilMs.fill(0L)
        parryStunUntilMs.fill(0L)
        // 🆕 (2026-07-22) mareo/stun + decaimientos
        stunUntilMs.fill(0L)
        superKeepMs.fill(0L)
        dizzyDecayAcc.fill(0f)
        superDecayAcc.fill(0f)
        airAttackUsed.fill(false)
        defenderBlockingLow.fill(false)
        dirHeldPrev[0] = false to false
        dirHeldPrev[1] = false to false
        lastHurtVoiceMs.fill(0L)
        lastAttackVoiceMs.fill(0L)
        lowHpVoiceTriggered.fill(false)
        pendingAttacks.clear()
        pendingBonusPower = null
        controlHistory.clear()
        lastZone = 0
        remoteSnapshot = null
        lastSeenSnapshot = null   // 🆕 SESIÓN 4: la edad del snapshot arranca con el próximo
        remoteSnapshotAtMs = 0L
        // 🆕 (2026-07-26) La ronda nueva arranca sin voces pendientes ni transición heredada
        pendingNetAudio.clear()
        lastRemoteSfxState = null
        netDamageQueue.clear()
        onlineEndSent = false
        roundEndSent = false
        roundGraceUntilMs = now + ROUND_GRACE_MS
        // 🆕 (2026-07-25) Online: la ronda nueva también espera la barrera "ambos listos" (evita que
        // el de gama baja arranque tarde en la ronda 2/3). releaseReadyBarrier fija el intro.
        if (onlineFight) {
            roundIntroUntilMs = 0L
            armReadyBarrier()
        } else {
            roundIntroUntilMs = now + ROUND_INTRO_MS
        }
        // 🆕 (2026-07-25) GRADO de la ronda que acaba de cerrar → bajo la barra del ganador.
        val label = if (pendingRoundOutcome == SfRoundOutcome.NORMAL) "" else pendingRoundOutcome.label
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
            waitingForOpponentReady = false,
            roundResultLabel = label,
            roundResultWinnerIdx = if (label.isEmpty()) -1 else pendingRoundOutcomeWinner,
        )
    }

    /** Publica el fin del COMBATE una sola vez ("p1" = anfitrión). */
    private fun sendOnlineEnd(winnerIdx: Int) {
        if (onlineEndSent) return
        onlineEndSent = true
        transport?.sendMatchEnded(networkSideOf(winnerIdx))
    }

    /** MATCH_ENDED recibido: reconcilia el final del COMBATE (por si mi sim no lo detectaba). */
    internal fun endFromNet(winnerSide: String?) {
        val s = _state.value
        if (s.battleEnded && s.showEndMenu) return
        val winnerIdx = networkIndexOf(winnerSide)
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

    internal fun onNetDropped(reason: String?) {
        if (!isOnline) return
        val s = _state.value
        if ((s.btMode || s.lanMode) && !s.battleEnded && s.onlineStatus != SfOnlineStatus.OPPONENT_LEFT) {
            // BT/LAN sin pelea terminada: reintento (overlay), no selector offline
            platformOnLocalLinkFailed(reason)
            return
        }
        platformCancelOnline(reason?.let { "Conexión perdida: $it" } ?: "Conexión perdida con el servidor")
    }

    private fun collideFireballPairsCommon(sim: Sim, now: Long) {
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
                sim.fireballs[i] = a.copy(
                    velocity = a.velocity * 0.33f,
                    state = SfFireballState.COLLIDED,
                    animationFrame = 0,
                    animationTimerMs = now +
                        (fireballCollidedDelays[0] * SfConstants.FRAME_TIME_MS).toLong(),
                )
                sim.fireballs[j] = b.copy(
                    velocity = b.velocity * 0.33f,
                    state = SfFireballState.COLLIDED,
                    animationFrame = 0,
                    animationTimerMs = now +
                        (fireballCollidedDelays[0] * SfConstants.FRAME_TIME_MS).toLong(),
                )
                _soundEvents.tryEmit("light-punch-hit")
                return
            }
        }
    }

    private fun networkSideOf(winnerIdx: Int): String {
        val iAmP1 = _state.value.isHost
        return if (winnerIdx == 0) {
            if (iAmP1) "p1" else "p2"
        } else {
            if (iAmP1) "p2" else "p1"
        }
    }

    private fun networkIndexOf(side: String?): Int = when (side) {
        "p1" -> if (_state.value.isHost) 0 else 1
        "p2" -> if (_state.value.isHost) 1 else 0
        else -> 0
    }

    protected open fun platformApplyRemoteSnapshot(sim: Sim, now: Long, dt: Float) = Unit
    protected open fun platformProcessNetDamage(sim: Sim, now: Long) = Unit
    protected open fun platformAppendRemoteFireballs(sim: Sim, now: Long) = Unit
    protected open fun platformSendNetState(sim: Sim, now: Long) = Unit
    protected open fun platformCancelOnline(errorMsg: String? = null) = Unit
    protected open fun platformOnLocalLinkFailed(reason: String?) = Unit
    protected open fun platformStopBtScan() = Unit

    // ⚠️ `alLimpiar()`, NO `onCleared()`: en `PowViewModel` el `onCleared` de androidx es FINAL y
    // delega aquí, para que este teardown se ejecute igual en Android y en iOS. Cancelar el scope
    // lo hace la clase base; aquí solo va lo que es de esta pantalla.
    override fun alLimpiar() {
        transport?.close()
        platformStopBtScan()
    }
}
