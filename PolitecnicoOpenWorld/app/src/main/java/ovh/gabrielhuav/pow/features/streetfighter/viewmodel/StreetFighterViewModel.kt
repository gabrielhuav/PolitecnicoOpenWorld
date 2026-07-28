package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
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
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_BLOCK_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAnimation
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfDamage
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
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.data.auth.AuthManager
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.data.repository.SfArcadeRepository
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfBtClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanDiscovery
import ovh.gabrielhuav.pow.features.streetfighter.data.SfLanGame
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetTransport
import ovh.gabrielhuav.pow.features.streetfighter.data.SfWebRtcClient
import ovh.gabrielhuav.pow.features.streetfighter.data.isSfLowEnd
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

internal const val SF_STOP_SPECIALS_EVENT = "__sf_stop_specials__"

internal const val AUDIO_SHOWCASE_GAP_MS = 500L
internal const val AUDIO_SHOWCASE_FALLBACK_MS = 5000L
internal val SHOWCASE_SPEEDS = listOf(1f, 2f, 4f)

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
    @ApplicationContext internal val appContext: Context,
    // 🆕 (2026-07-21) Recompensa de ARCADE en DIFÍCIL: el coleccionable del rival vencido.
    internal val collectibleRepo: ovh.gabrielhuav.pow.data.repository.CollectibleRepository,
) : ViewModel() {

    internal val _state = MutableStateFlow(StreetFighterState())
    val state: StateFlow<StreetFighterState> = _state.asStateFlow()

    /** Claves de sonido (nombre base del .ogg en STREETFIGHTER/SOUNDS). */
    internal val _soundEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val soundEvents: SharedFlow<String> = _soundEvents.asSharedFlow()

    internal var audioShowcaseJob: Job? = null

    internal val specialPhrases by lazy {
        ovh.gabrielhuav.pow.features.streetfighter.data.SfSpecialPhrases.load(appContext)
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
    // Archivo: special_male_attack_grunt.ogg (del "ZA ZA", seg 8-10).
    internal val maleGruntClip = "special_male_attack_grunt"
    internal val sfMaleFighters = setOf(
        SfFighterId.PRANKEDY, SfFighterId.SENOR_TIENDA, SfFighterId.PAPARAZZI_1, SfFighterId.PAPARAZZI_5,
        SfFighterId.REY_GRUPERO, SfFighterId.ESCOMBOY, SfFighterId.CHARRO_NEGRO, SfFighterId.LAZARO,
        SfFighterId.POLICIA_CDMX_HOMBRE, SfFighterId.POLICIA_GRANADERO_HOMBRE, SfFighterId.GRANADERO,
        SfFighterId.PARAMEDICO_CRUZ_ROJA, SfFighterId.PARAMEDICO,
    )

    // 🆕 (2026-07-18s) Peleadores SIN voz a propósito (special_<id>.ogg borrado por el dueño):
    // su poder especial suena con el hadouken genérico. La auditoría NO los marca como faltantes.
    internal val sfVoicelessFighters = setOf(
        SfFighterId.LAZARO, SfFighterId.PARAMEDICO, SfFighterId.PRANKEDY,
    )

    /** Emite un clip de voz `special_<name>.ogg` si el asset existe. true = se emitió. */
    internal val voiceSubtitlesEnabled = SettingsRepository(appContext).getShowVoiceSubtitles()

    internal val voicePhrases by lazy {
        ovh.gabrielhuav.pow.features.streetfighter.data.SfVoicePhrases.load(appContext)
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
    internal val arcadeRepo = SfArcadeRepository(appContext)

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

    /** 🆕 (2026-07-19) Si el personaje fue realmente desbloqueado por progresión (inicial o ganado). */
    fun isFighterActuallyUnlocked(id: SfFighterId): Boolean =
        // 🆕 (2026-07-19) Con sesión Google en Firebase + Modo Desarrollador se REVELA el arte a
        // color (sin silueta pixelada). Solo en ese caso; para el resto sigue oculto.
        revealLockedArt() || arcadeRepo.unlockedFighters().contains(id.name)

    /** 🆕 Ajustes → "Mostrar hitboxes": dibuja las cajas push/hurt/hit sobre los peleadores. */
    fun showHitboxes(): Boolean = SettingsRepository(appContext).getShowHitboxes()

    /** 🆕 (2026-07-25) Ajustes → "Mostrar FPS (pelea)": contador de cuadros por segundo en el HUD. */
    fun showSfFps(): Boolean = SettingsRepository(appContext).getShowSfFps()

    /**
     * 🆕 REVELAR el arte de los bloqueados (a color, sin pixelar) — siempre que el Modo Desarrollador esté activo.
     */
    fun revealLockedArt(): Boolean =
        SettingsRepository(appContext).getDeveloperMode()

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
        dataCache.getOrPut(f.id) { SfFrameCatalog.load(appContext, f.id) }

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
    // Variedad ofensiva: memoria de los últimos tres golpes (fuerza×tipo, 0..5) por CPU.
    internal val cpuAttackHistory = Array(2) { ArrayDeque<Int>() }
    // 🆕 (2026-07-18n) Dificultad POR ÍNDICE solo para IA vs IA: si ambos son iguales (dos
    // PESADILLA) se esquivan sin fin y NADIE gana. Se le baja la dificultad a UNO al azar para
    // desnivelar la pelea y que alguien gane. null = usar la dificultad global (VS/arcade normal).
    internal val cpuDiffOverride = arrayOfNulls<SfCpuDifficulty>(2)
    // Antibucle de combo: tras tres impactos rápidos, el defensor recibe una ventana de escape.
    private val lastHitTakenMs = LongArray(2) { 0L }
    private val rapidHitsTaken = IntArray(2)
    private val comboEscapeUntilMs = LongArray(2)
    // 🆕 (2026-07-25) WALL SPLAT: un golpe pesado con el rival contra la pared lo "aplasta" y
    // rebota al centro abriendo UNA continuación de combo. UNA sola vez por combo (se reinicia al
    // empezar un combo nuevo) → JUSTO para ambos y SIN infinitos en la esquina ("que te traben").
    private val wallSplatUsed = BooleanArray(2)
    // 🆕 (2026-07-20) COMBO estilo SF III 3rd Strike: golpes CONECTADOS encadenados por
    // ATACANTE (índice). Alimenta el contador del HUD ("N GOLPES") y el escalado de daño.
    // Expira si pasa la ventana RAPID_HIT_WINDOW_MS sin conectar otro golpe.
    private val comboHits = IntArray(2)
    private val comboLastHitMs = LongArray(2)
    // 🆕 (2026-07-21) MOVESET 3rd Strike: ventana activa del parry, castigo tras recibirlo,
    // control del "un ataque aéreo por salto" y detección del doble toque para el dash.
    private val parryActiveUntilMs = LongArray(2)
    private val parryStunUntilMs = LongArray(2)
    // 🆕 (2026-07-22) MAREO/STUN + decaimientos de medidores (molde de parryStunUntilMs):
    // fin del aturdimiento, último golpe CONECTADO (gracia del decaimiento del súper) y
    // acumuladores fraccionales del decaimiento por tick (los medidores son Int).
    private val stunUntilMs = LongArray(2)
    private val superKeepMs = LongArray(2)
    private val dizzyDecayAcc = FloatArray(2)
    private val superDecayAcc = FloatArray(2)
    private val airAttackUsed = BooleanArray(2)
    private val lastForwardTapMs = LongArray(2)
    private val lastBackwardTapMs = LongArray(2)
    private val dirHeldPrev = Array(2) { false to false } // (adelante, atrás) del tick previo
    /** 🆕 ¿Estaba cubriéndose ABAJO (atrás+abajo) en el último tick? Lo llena updateFighter. */
    private val defenderBlockingLow = BooleanArray(2)

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
    private var hurtFreezeUntilMs = 0L  // hit-freeze (FighterStruckDelay)
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
    private var gradeDealt = 0
    private var gradeTaken = 0
    private var gradeParries = 0
    private var gradeMaxCombo = 0
    private var gradeSupers = 0
    private var gradeHits = 0
    private var gradePerfects = 0
    private val gradeMoves = HashSet<SfFighterState>()
    // Solo se califica cuando hay un HUMANO en el índice 0 (no IA vs IA / showcase / gauntlet / tutorial).
    private val gradeTrackingOn: Boolean
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
    internal var webRtc: SfWebRtcClient? = null
    // El cliente de relay crudo: hace de canal de SEÑALIZACIÓN y de respaldo del P2P.
    internal var relayClient: SfMatchClient? = null
    internal var btScanner: SfBtClient? = null   // discovery del selector "BUSCAR RIVAL"
    // 🆕 (2026-07-26) Autodescubrimiento LAN por UDP: baliza del host + escucha del invitado.
    internal var lanDiscovery: SfLanDiscovery? = null
    @Volatile internal var remoteSnapshot: SfNetMsg? = null
    internal val netDamageQueue = ConcurrentLinkedQueue<SfNetMsg>()
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
    private var dispHp0 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
    private var dispHp1 = SfConstants.HEALTH_MAX_HIT_POINTS.toFloat()
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
    private val knockdownStates = SfStateMachine.KNOCKDOWN_STATES
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
        val nowReal = SystemClock.elapsedRealtime()
        readyArmedRealMs = nowReal
        readyBarrierUntilMs = nowReal + READY_TIMEOUT_MS
        Log.d(SF_NET_TAG, "barrera armada (esperando PLAYER_READY del rival)")
    }

    private fun sendLocalReady() {
        if (localReadySent) return
        localReadySent = true
        transport?.sendReady()
        Log.d(SF_NET_TAG, "PLAYER_READY enviado")
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
        Log.d(SF_NET_TAG, "barrera liberada: arranca la ronda")
    }

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
    internal class Sim(
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
    private fun rollUpHp(disp: Float, target: Int, dt: Float): Float =
        if (target >= disp) target.toFloat()
        else maxOf(target.toFloat(), disp - HP_DRAIN_PER_SEC * dt)

    // ------------------------------------------------------------------
    // Animación por frames (setAnimationFrame / updateAnimation del JS)
    // ------------------------------------------------------------------

    private fun animOf(f: SfFighter) = dataFor(f).animations.getValue(f.state.jsKey)

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
    private fun isAnimationCompleted(f: SfFighter): Boolean =
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
        return !SfFrameCatalog.load(appContext, fallbackId)
            .animations[state.jsKey].isNullOrEmpty()
    }

    /** ¿Este estado se está dibujando con el placeholder ALPHA (arte prestada)? */
    fun usesAlphaFallback(f: SfFighter): Boolean =
        f.state in SF_NEW_MOVE_STATES &&
            dataFor(f).animations[f.state.jsKey].isNullOrEmpty() &&
            hasAlphaFallback(f, f.state)

    private fun changeState(sim: Sim, idx: Int, newState: SfFighterState, now: Long): Boolean {
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
        // 🆕 (2026-07-18k) VICTORY con la VOZ del peleadór (reutiliza su special_<id>.ogg):
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

    internal fun logAssetIssue(msg: String) {
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
                    input.special?.let { trySpecial(sim, idx, it, now) } == true -> Unit
                    !input.forward -> changeState(sim, idx, SfFighterState.IDLE, now)
                    input.up -> changeState(sim, idx, SfFighterState.JUMP_FORWARD, now)
                    input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
                    // 🆕 (2026-07-22) Caminando adelante YA cuenta como "adelante + ataque":
                    // medio = OVERHEAD, patada fuerte = PATADA LARGA. Antes solo salían pulsando
                    // →+ataque en el MISMO frame desde IDLE (casi imposible) — por eso el Overhead
                    // del tutorial (lección 17) no se podía hacer. Si el peleador no los tiene,
                    // changeState devuelve false y cae al golpe normal.
                    input.mediumPunch && changeState(sim, idx, SfFighterState.OVERHEAD, now) -> Unit
                    input.heavyKick && changeState(sim, idx, SfFighterState.LONG_KICK, now) -> Unit
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
                // 🆕 (2026-07-21) Ataque AÉREO en pleno salto (abre combos al aterrizar).
                if (tryAirAttacks(sim, idx, input, now)) return
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
                if (input.special?.let { trySpecial(sim, idx, it, now) } == true) return
                // 🆕 (2026-07-21) Arsenal AGACHADO: parry bajo + los 4 golpes bajos.
                // La barrida (patada fuerte) es el remate que derriba.
                if (input.parry && changeState(sim, idx, SfFighterState.PARRY_LOW, now)) return
                if (tryCrouchAttacks(sim, idx, input, now)) return
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
                        if (input.bonusPower?.let { tryBonusPower(sim, idx, it, now) } == true) return
                        if (input.special?.let { trySpecial(sim, idx, it, now) } == true) return
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
                // 🆕 (2026-07-20) chain cancel ligero→medio (o especial) si CONECTÓ
                if (tryChainCancel(sim, idx, input, now)) return
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.MEDIUM_PUNCH, SfFighterState.HEAVY_PUNCH,
            SfFighterState.MEDIUM_KICK, SfFighterState.HEAVY_KICK,
            -> {
                // 🆕 (2026-07-20) chain cancel medio→fuerte / cualquier golpe→especial si CONECTÓ
                if (tryChainCancel(sim, idx, input, now)) return
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            }

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
                // 🆕 (2026-07-25) BONUS_POWER_10 de YOALLI = metamorfosis PRINCIPAL (jefe FINAL del
                // arcade): al terminar la anim el id pasa a LA PRESIDENTA con VIDA LLENA y se QUEDA.
                if (f.id == SfFighterId.YOALLI_EHECATL && f.state == SfFighterState.BONUS_POWER_10) {
                    if (isAnimationCompleted(f)) {
                        completeYoalliMetamorphosis(sim, idx, now)
                    }
                    return
                }
                // Dirección opuesta (histórica, hoy inactiva en gameplay: P11 no es lanzable y no hay
                // disparo automático): La Presidenta → Yoalli. Se conserva por simetría/animación.
                if (f.id == SfFighterId.LA_PRESIDENTA && f.state == SfFighterState.BONUS_POWER_11) {
                    if (isAnimationCompleted(f)) {
                        completePresidentaMetamorphosis(sim, idx, now)
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
                // 🆕 (2026-07-21) El proyectil sale al ENTRAR al último cuadro del personaje
                // (los poderes de proyectil solo tienen 2 poses propias) y viaja marcado con
                // su índice de poder para dibujarse con SU efecto (bonus-N-2/3/4).
                if (f.animationFrame == 1 && !f.fireballFired) {
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
                            bonusPower = f.state.bonusPowerIndex() ?: 0,
                        ),
                    )
                }
                if (isAnimationCompleted(sim.fighter(idx))) {
                    sim.setFighter(idx, sim.fighter(idx).copy(fireballFired = false))
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }

            // ── 🆕 (2026-07-21) MOVESET 3rd Strike ──
            // Movilidad: el dash termina con su animación y frena en seco. 🆕 Si al acabar
            // se sigue sosteniendo ADELANTE, encadena a CARRERA (dash-run de 3rd Strike).
            SfFighterState.DASH_FORWARD, SfFighterState.DASH_BACKWARD -> {
                if (isAnimationCompleted(f)) {
                    if (f.state == SfFighterState.DASH_FORWARD && input.forward &&
                        changeState(sim, idx, SfFighterState.RUN, now)
                    ) {
                        return
                    }
                    sim.setFighter(idx, f.copy(velocityX = 0f))
                    changeState(sim, idx, SfFighterState.IDLE, now)
                }
            }
            // 🆕 CARRERA: se mantiene mientras se sostenga adelante; se puede saltar o
            // atacar desde ella (por eso vale la pena correr).
            SfFighterState.RUN -> {
                when {
                    // 🆕 FATALITY: súper EN CARRERA con el medidor lleno (su comando propio)
                    input.superArt && tryFatality(sim, idx, now) -> Unit
                    input.up -> changeState(sim, idx, SfFighterState.JUMP_START, now)
                    tryAttacks(sim, idx, input, now) -> Unit
                    !input.forward -> {
                        sim.setFighter(idx, f.copy(velocityX = 0f))
                        changeState(sim, idx, SfFighterState.IDLE, now)
                    }
                }
            }
            // 🆕 FATALITY: al terminar la cinemática el atacante CRUZA al otro lado del
            // rival (con giro), que es el remate espectacular pedido.
            SfFighterState.FATALITY -> if (isAnimationCompleted(f)) {
                crossToOtherSide(sim, idx, now)
            }
            // Poses de intro/burla: terminan y vuelven a guardia.
            SfFighterState.IDLE_RELAXED, SfFighterState.TALK ->
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            // Defensa: el bloqueo se sostiene mientras se siga cubriendo.
            // 🆕 (2026-07-26) BLOQUEO RESPONSIVO: antes exigían soltar atrás Y que la animación
            // terminara para salir, y NO aceptaban ninguna otra acción → al cubrirse quedabas
            // "atrapado" y los controles "no respondían". Ahora reaccionan al INSTANTE como
            // WALK_BACKWARD/CROUCH (el blockstun real lo da hurtFreezeUntilMs durante el golpe, no
            // esta animación). Sostener la dirección sigue cubriendo; cualquier otra intención sale ya.
            // Terminado el hit-freeze del golpe bloqueado, la guardia REBOTA al instante al estado
            // neutro correspondiente (WALK_BACKWARD alto / CROUCH bajo) — que son totalmente
            // responsivos y VUELVEN a bloquear si les pegan otra vez. Así se acaba el "quedarse
            // atrapado" en la pose de bloqueo (antes exigía terminar la animación).
            SfFighterState.BLOCK_HIGH -> when {
                !input.backward -> changeState(sim, idx, SfFighterState.IDLE, now)
                input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
                else -> changeState(sim, idx, SfFighterState.WALK_BACKWARD, now)
            }
            SfFighterState.BLOCK_LOW -> when {
                !input.down -> changeState(sim, idx, SfFighterState.CROUCH_UP, now)
                else -> changeState(sim, idx, SfFighterState.CROUCH, now)
            }
            SfFighterState.PARRY_HIGH -> if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.PARRY_LOW -> if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.CROUCH, now)
            }
            // Ataques agachado: encadenan entre sí (cancel) y vuelven a cuclillas.
            SfFighterState.CROUCH_PUNCH, SfFighterState.CROUCH_KICK,
            SfFighterState.CROUCH_HEAVY_PUNCH,
            -> {
                if (tryCrouchChainCancel(sim, idx, input, now)) return
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.CROUCH, now)
            }
            // La barrida NO cancela: es el final de la cadena baja (derriba).
            SfFighterState.SWEEP -> if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.CROUCH, now)
            }
            // Aéreos: siguen cayendo; al tocar el suelo aterrizan como un salto normal.
            SfFighterState.AIR_PUNCH, SfFighterState.AIR_KICK -> {
                val nf = f.copy(velocityY = f.velocityY + SfConstants.GRAVITY * dt)
                sim.setFighter(idx, nf)
                if (nf.y >= SfConstants.STAGE_FLOOR && nf.velocityY >= 0f) {
                    sim.setFighter(idx, nf.copy(y = SfConstants.STAGE_FLOOR, velocityX = 0f))
                    forceState(sim, idx, SfFighterState.JUMP_LAND, now)
                    _soundEvents.tryEmit("land")
                }
            }
            SfFighterState.LONG_KICK, SfFighterState.OVERHEAD ->
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            // Agarre: si conectó (attackStruck) pasa al lanzamiento; si no, recupera.
            SfFighterState.GRAB -> {
                if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.THROW -> if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.TAUNT -> if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.SUPER_ART -> if (isAnimationCompleted(f)) {
                changeState(sim, idx, SfFighterState.IDLE, now)
            }
            SfFighterState.HURT_CROUCH -> if (isAnimationCompleted(f)) {
                val opp = sim.fighter(1 - idx)
                sim.setFighter(1 - idx, opp.copy(attackStruck = false))
                changeState(sim, idx, SfFighterState.CROUCH, now)
            }
            // Derribo: cae, se queda un momento y se levanta solo (wake-up).
            SfFighterState.THROWN -> if (isAnimationCompleted(f)) {
                if (!forceState(sim, idx, SfFighterState.GET_UP, now)) {
                    forceState(sim, idx, SfFighterState.IDLE, now)
                }
            }
            SfFighterState.GET_UP -> if (isAnimationCompleted(f)) {
                sim.setFighter(idx, sim.fighter(idx).copy(downed = false))
                forceState(sim, idx, SfFighterState.IDLE, now)
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
            // 🆕 (2026-07-22) MAREADO: ignora TODOS los inputs; sale solo (o antes, si un
            // golpe lo mete a HURT_*: STUN está en SF_HURT_STATES).
            SfFighterState.STUN -> {
                if (now >= stunUntilMs[idx.coerceIn(0, 1)]) {
                    forceState(sim, idx, SfFighterState.IDLE, now)
                }
            }
            SfFighterState.VICTORY -> Unit
        }
    }

    /** Transiciones comunes de estados neutros (handleIdle del JS): salto/agacharse/caminar/ataques. */
    private fun handleCommonNeutral(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean {
        if (input.bonusPower?.let { tryBonusPower(sim, idx, it, now) } == true) return true
        // 🆕 (2026-07-21) La SÚPER manda sobre todo lo demás (si hay medidor y arte).
        if (input.superArt && trySuperArt(sim, idx, now)) return true
        if (input.special?.let { trySpecial(sim, idx, it, now) } == true) return true
        // 🆕 Defensa y utilidades antes de moverse: parry, agarre, burla y dashes.
        if (input.parry && changeState(sim, idx, SfFighterState.PARRY_HIGH, now)) return true
        if (input.grab && tryGrab(sim, idx, now)) return true
        if (input.dashForward && changeState(sim, idx, SfFighterState.DASH_FORWARD, now)) return true
        if (input.dashBackward && changeState(sim, idx, SfFighterState.DASH_BACKWARD, now)) return true
        if (input.taunt && changeState(sim, idx, SfFighterState.TAUNT, now)) return true
        return when {
            input.up -> changeState(sim, idx, SfFighterState.JUMP_START, now)
            input.down -> changeState(sim, idx, SfFighterState.CROUCH_DOWN, now)
            // 🆕 Normales con DIRECCIÓN (3rd Strike): adelante+fuerte = patada larga,
            // adelante+medio = overhead (rompe guardia baja). Si el peleador no los tiene,
            // changeState devuelve false y sigue el camino normal (caminar/golpe suelto).
            input.forward && input.heavyKick &&
                changeState(sim, idx, SfFighterState.LONG_KICK, now) -> true
            input.forward && input.mediumPunch &&
                changeState(sim, idx, SfFighterState.OVERHEAD, now) -> true
            input.forward -> changeState(sim, idx, SfFighterState.WALK_FORWARD, now)
            input.backward -> changeState(sim, idx, SfFighterState.WALK_BACKWARD, now)
            else -> tryAttacks(sim, idx, input, now)
        }
    }

    /** 🆕 (2026-07-21) SUPER ART: exige medidor lleno + arte propia. */
    private fun trySuperArt(sim: Sim, idx: Int, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (!f.superReady) return false
        return changeState(sim, idx, SfFighterState.SUPER_ART, now)
    }

    /**
     * 🆕 (2026-07-21) FATALITY ("poder súper especial"): comando propio = **súper EN
     * CARRERA** con el medidor lleno. Es el movimiento más devastador del peleador y
     * termina con el atacante CRUZANDO al otro lado del rival.
     */
    private fun tryFatality(sim: Sim, idx: Int, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (!f.superReady) return false
        return changeState(sim, idx, SfFighterState.FATALITY, now)
    }

    /**
     * 🆕 (2026-07-21) Remate del fatality: el atacante aparece AL OTRO LADO del rival y
     * queda encarándolo (el giro lo da la propia animación de la cinemática). Se respeta el
     * límite del escenario para no dejarlo fuera de pantalla.
     */
    private fun crossToOtherSide(sim: Sim, idx: Int, now: Long) {
        val me = sim.fighter(idx)
        val foe = sim.fighter(1 - idx)
        val wasLeft = me.x <= foe.x
        val target = if (wasLeft) {
            foe.x + SfConstants.FATALITY_CROSS_OFFSET
        } else {
            foe.x - SfConstants.FATALITY_CROSS_OFFSET
        }
        sim.setFighter(
            idx,
            me.copy(
                x = target.coerceIn(STAGE_X_MIN, STAGE_X_MAX),
                velocityX = 0f,
                direction = if (wasLeft) SfDirection.LEFT else SfDirection.RIGHT,
            ),
        )
        // El rival también queda encarando al atacante tras el cruce
        sim.setFighter(1 - idx, foe.copy(direction = if (wasLeft) SfDirection.RIGHT else SfDirection.LEFT))
        forceState(sim, idx, SfFighterState.IDLE, now)
    }

    /** 🆕 (2026-07-21) AGARRE: solo tiene sentido pegado al rival (como en el arcade). */
    private fun tryGrab(sim: Sim, idx: Int, now: Long): Boolean {
        val me = sim.fighter(idx)
        val foe = sim.fighter(1 - idx)
        if (abs(me.x - foe.x) > SfConstants.GRAB_RANGE) return false
        // No se puede agarrar a quien está en el aire ni derribado
        if (foe.isAirborne || foe.state in SF_DOWNED_STATES) return false
        return changeState(sim, idx, SfFighterState.GRAB, now)
    }

    /**
     * 🆕 (2026-07-21) Golpes AGACHADO (3rd Strike): ligero = jab/patadita bajos,
     * puño fuerte = antiaéreo, patada fuerte = BARRIDA (derriba).
     */
    private fun tryCrouchAttacks(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean = when {
        input.heavyKick -> changeState(sim, idx, SfFighterState.SWEEP, now)
        input.heavyPunch -> changeState(sim, idx, SfFighterState.CROUCH_HEAVY_PUNCH, now)
        input.lightPunch || input.mediumPunch ->
            changeState(sim, idx, SfFighterState.CROUCH_PUNCH, now)
        input.lightKick || input.mediumKick ->
            changeState(sim, idx, SfFighterState.CROUCH_KICK, now)
        else -> false
    }

    /**
     * 🆕 (2026-07-21) Ataques AÉREOS: UNO por salto (`airAttackUsed`), como en el arcade;
     * si no, se podían encadenar patadas infinitas en el mismo brinco.
     */
    private fun tryAirAttacks(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean {
        if (airAttackUsed[idx.coerceIn(0, 1)]) return false
        val wantsPunch = input.lightPunch || input.mediumPunch || input.heavyPunch
        val wantsKick = input.lightKick || input.mediumKick || input.heavyKick
        val state = when {
            wantsKick -> SfFighterState.AIR_KICK
            wantsPunch -> SfFighterState.AIR_PUNCH
            else -> return false
        }
        if (!changeState(sim, idx, state, now)) return false
        airAttackUsed[idx.coerceIn(0, 1)] = true
        return true
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

    /**
     * 🆕 (2026-07-21) Fuerza un estado saltándose `validFrom` (derribos, aterrizajes y
     * levantadas: los dispara la LÓGICA, no una transición de input). Respeta `hasAnim`:
     * si el peleador no tiene ese arte, devuelve false y el llamador decide el fallback.
     */
    private fun forceState(sim: Sim, idx: Int, newState: SfFighterState, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (newState in SF_NEW_MOVE_STATES && !hasAnim(f, newState)) return false
        var nf = f.copy(state = newState, velocityX = 0f)
        nf = withAnimationFrame(nf, 0, now)
        if (newState == SfFighterState.THROWN) nf = nf.copy(downed = true)
        if (newState == SfFighterState.IDLE) nf = nf.copy(downed = false, velocityY = 0f)
        sim.setFighter(idx, nf)
        return true
    }

    /**
     * 🆕 (2026-07-21) Cadena BAJA (3rd Strike): un golpe agachado que CONECTÓ encadena al
     * siguiente. El remate natural es la BARRIDA, que derriba. Mismo criterio que arriba:
     * sin `attackStruck` no hay cancel (en fallo se paga la recuperación completa).
     */
    private fun tryCrouchChainCancel(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (!f.attackStruck) return false
        return when {
            input.heavyKick -> changeState(sim, idx, SfFighterState.SWEEP, now)
            input.heavyPunch && f.state != SfFighterState.CROUCH_HEAVY_PUNCH ->
                changeState(sim, idx, SfFighterState.CROUCH_HEAVY_PUNCH, now)
            else -> false
        }
    }

    /**
     * 🆕 (2026-07-20) CHAIN CANCEL estilo SF III 3rd Strike: un golpe normal que CONECTÓ
     * (attackStruck) puede cancelarse ANTES de terminar en el siguiente golpe de mayor
     * fuerza (ligero→medio→fuerte, puño o patada) o en el ESPECIAL (special cancel; respeta
     * el cooldown y el tope de proyectiles de trySpecial). En fallo (whiff) NO hay cancel:
     * se sufre la recuperación completa, como en el arcade original.
     */
    private fun tryChainCancel(sim: Sim, idx: Int, input: SfInput, now: Long): Boolean {
        val f = sim.fighter(idx)
        if (!f.attackStruck) return false
        if (input.special?.let { trySpecial(sim, idx, it, now) } == true) return true
        val next = when (attackMeta[f.state]?.strength) {
            SfAttackStrength.LIGHT -> when {
                input.mediumPunch -> SfFighterState.MEDIUM_PUNCH
                input.mediumKick -> SfFighterState.MEDIUM_KICK
                else -> null
            }
            SfAttackStrength.MEDIUM -> when {
                input.heavyPunch -> SfFighterState.HEAVY_PUNCH
                input.heavyKick -> SfFighterState.HEAVY_KICK
                else -> null
            }
            else -> null
        } ?: return false
        return changeState(sim, idx, next, now)
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

    /** Poderes Grok “lanzables” (excluye las metamorfosis automáticas: P11 Presidenta, P10 Yoalli). */
    internal fun usableBonusPowerCount(id: SfFighterId): Int = sfUsableBonusPowerCount(id)

    /**
     * Fin de BONUS_POWER_11 de La Presidenta → Yoalli Ehécatl con VIDA LLENA. El cambio de id es
     * PERMANENTE. ⚠️ 🆕 (2026-07-25) Dirección HISTÓRICA/inactiva en gameplay: la metamorfosis
     * automática del arcade ahora es la INVERSA (Yoalli→Presidenta, ver [tryYoalliMetamorphosis] y
     * [completeYoalliMetamorphosis]). Se conserva por simetría (animación disponible).
     */
    private fun completePresidentaMetamorphosis(sim: Sim, idx: Int, now: Long) {
        val f = sim.fighter(idx)
        if (f.id != SfFighterId.LA_PRESIDENTA) {
            changeState(sim, idx, SfFighterState.IDLE, now)
            return
        }
        // 🆕 (2026-07-22, decisión del dueño) Al completar la metamorfosis arranca con la
        // VIDA LLENA otra vez (antes 50%): es su "segunda vida" del round 1.
        completeMetamorphosis(sim, idx, SfFighterId.YOALLI_EHECATL, SfConstants.HEALTH_MAX_HIT_POINTS, now)
    }

    /**
     * Fin de BONUS_POWER_10 de Yoalli: se convierte en LA PRESIDENTA con la VIDA LLENA (su
     * "segunda vida" del round 1). 🆕 (2026-07-25) Antes conservaba la vida (~1/4); ahora es la
     * metamorfosis PRINCIPAL del arcade (Yoalli jefe FINAL → Presidenta), espejo de lo que hacía
     * La Presidenta. El cambio de id es PERMANENTE (persiste entre rondas).
     */
    private fun completeYoalliMetamorphosis(sim: Sim, idx: Int, now: Long) {
        val f = sim.fighter(idx)
        if (f.id != SfFighterId.YOALLI_EHECATL) {
            changeState(sim, idx, SfFighterState.IDLE, now)
            return
        }
        completeMetamorphosis(sim, idx, SfFighterId.LA_PRESIDENTA, SfConstants.HEALTH_MAX_HIT_POINTS, now)
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
     * Corrige el encaramiento antes de interpretar `forward/backward`. Un cross-up o un empuje
     * puede intercambiar los lados mientras sigue caminando; con la cara vieja, la siguiente orden
     * de acercarse se convierte en alejarse. 🆕 (2026-07-22) Se usa para la CPU **y el jugador**
     * (antes solo la CPU se auto-encaraba; al jugador le tocaba soltar todo y quedar quieto para
     * girar, lo que se sentía "muy complicado" tras cruzar de lado).
     */
    internal fun repairFacing(sim: Sim, idx: Int, now: Long) {
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

    // 🆕 (2026-07-22, Fase 2b) Extraído a SfPhysics.clampToStage (puro); el VM solo delega.
    private fun clampFighterToStage(f: SfFighter): SfFighter = SfPhysics.clampToStage(f)

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

    /**
     * 🆕 (2026-07-21) Daño REAL de un ataque. Los movimientos nuevos no siempre usan el
     * daño de su fuerza base: la súper, el fatality y el agarre tienen el suyo. Se usa
     * tanto offline como al avisar por RED (sendDamage), para que en línea peguen igual.
     */
    private fun damageForAttack(attacker: SfFighter, strength: SfAttackStrength): Int =
        SfDamage.forAttack(attacker.state, strength)

    /** 🆕 (2026-07-21) Suma al medidor de súper con tope en el máximo. */
    private fun chargeSuper(f: SfFighter, amount: Int): Int =
        (f.superMeter + amount).coerceIn(0, SfConstants.SUPER_METER_MAX)

    /**
     * 🆕 (2026-07-21) LANZAMIENTO: el agarre conectó. Daño fijo, el atacante ejecuta THROW
     * y el rival sale despedido y queda DERRIBADO (THROWN → GET_UP). Sin pose de HURT: el
     * lanzamiento tiene su propia animación de recibirlo.
     */
    private fun applyThrow(sim: Sim, attackerIdx: Int, defenderIdx: Int, now: Long) {
        val attacker = sim.fighter(attackerIdx)
        var defender = sim.fighter(defenderIdx)
        _soundEvents.tryEmit("heavy-punch-hit")
        defender = defender.copy(
            hitPoints = (defender.hitPoints - SfConstants.THROW_DAMAGE).coerceAtLeast(0),
            slideVelocity = SfConstants.THROW_PUSH_VELOCITY,
            slideFriction = SfAttackStrength.HEAVY.slideFriction,
            direction = attacker.direction.opposite(),
            superMeter = chargeSuper(defender, SfConstants.SUPER_METER_ON_TAKE),
        )
        sim.setFighter(defenderIdx, defender)
        sim.setFighter(
            attackerIdx,
            attacker.copy(superMeter = chargeSuper(attacker, SfConstants.SUPER_METER_ON_HIT)),
        )
        superKeepMs[attackerIdx] = now // 🆕 conectó el agarre: su súper no decae aún
        if (attackerIdx == 0) sim.score0 += SfAttackStrength.HEAVY.score
        else sim.score1 += SfAttackStrength.HEAVY.score
        // El atacante pasa a la animación de lanzar (si la tiene)
        changeState(sim, attackerIdx, SfFighterState.THROW, now)
        if (defender.hitPoints <= 0) {
            changeState(sim, defenderIdx, SfFighterState.KO, now)
            sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(victory = true))
            if (gauntletActive && !showcaseMode) gauntletKoRounds++
            endRound(sim, attackerIdx, now, computeRoundOutcome(sim, attackerIdx, SfFighterState.THROW, byTime = false))
        } else if (!forceState(sim, defenderIdx, SfFighterState.THROWN, now)) {
            // Sin arte de "ser lanzado": al menos reacciona con el daño clásico
            changeState(sim, defenderIdx, SfFighterState.HURT_BODY_HEAVY, now)
        }
        withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) }
        hurtFreezeUntilMs =
            now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
    }

    /** handleAttackHit del JS + BattleScene.handleAttackHit (daño, score, KO, splash, hit-freeze). */
    internal fun applyAttackHit(
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
            // 🆕 (2026-07-21) ONLINE: hay que avisar el daño REAL del movimiento. Súper,
            // fatality y agarre NO usan el daño de su fuerza base; sin esto, en línea un
            // fatality pegaba como un golpe fuerte normal (28 en vez de 70).
            transport?.sendDamage(damageForAttack(attacker, strength), strength.name, type.name)
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

        // 🆕 (2026-07-21) TUTORIAL: el muñeco NO pierde vida (la lección no debe acabarse por
        // KO) pero sí reacciona y suena, para que se vea que el golpe conectó.
        if (inTutorial && defenderIdx == 1) {
            _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            hitPos?.let { (x, y) ->
                sim.splashes.add(
                    SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now),
                )
            }
            // El jugador sí carga medidor: así puede practicar la SÚPER del catálogo.
            sim.setFighter(
                attackerIdx,
                sim.fighter(attackerIdx)
                    .copy(superMeter = chargeSuper(sim.fighter(attackerIdx), SfConstants.SUPER_METER_ON_HIT)),
            )
            superKeepMs[attackerIdx] = now // 🆕 en tutorial también refresca la gracia
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
            return
        }

        // 🆕 (2026-07-21) DERRIBADO = INVULNERABLE (como en el arcade): no se puede seguir
        // golpeando a quien está en el suelo o levantándose.
        if (defender.state in SF_DOWNED_STATES) {
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            return
        }

        // 🆕 (2026-07-21) PARRY (la firma de 3rd Strike): dentro de su ventana ACTIVA el
        // golpe se anula ENTERO — cero daño, sin pose de daño — y el ATACANTE se queda
        // vendido un momento (castigo). Es la recompensa por leer el golpe.
        if (defender.state in SF_PARRY_STATES && now < parryActiveUntilMs[defenderIdx]) {
            _soundEvents.tryEmit("land") // chasquido seco del desvío
            // 🆕 (2026-07-26) Este SFX sí VIAJA (no se deriva): el parry lo resuelve solo quien
            // se defiende, y el estado PARRY_* no basta para deducirlo — pararse sin desviar
            // nada NO suena. Sin esto, el atacante no oía que le habían leído el golpe.
            if (defenderIdx == 0 && inOnlineFight) queueNetAudio("land")
            if (defenderIdx == 0 && gradeTrackingOn) gradeParries++ // 🆕 calificación: parry logrado
            parryStunUntilMs[attackerIdx] = now + SfConstants.PARRY_ADVANTAGE_MS
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            sim.setFighter(
                defenderIdx,
                defender.copy(superMeter = chargeSuper(defender, SfConstants.SUPER_METER_ON_HIT)),
            )
            superKeepMs[defenderIdx] = now // 🆕 el parry exitoso también "acierta"
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
            return
        }

        // 🆕 (2026-07-21) AGARRE que conecta -> LANZAMIENTO: daño fijo, el rival sale
        // volando y queda DERRIBADO (luego se levanta solo). No usa pose de HURT.
        if (attacker.state == SfFighterState.GRAB) {
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            applyThrow(sim, attackerIdx, defenderIdx, now)
            return
        }

        // BLOQUEO (estilo SF): caminar HACIA ATRÁS = cubrirse. El golpe entra "chip":
        // daño /4 (mínimo 1), medio retroceso, sin pose de HURT, sin splash ni puntos.
        // 🆕 (2026-07-21) También cubre AGACHADO (atrás+abajo) y sostener la guardia; y el
        // OVERHEAD ROMPE la guardia baja (por eso existe), como en el arcade.
        val crouchGuard = defender.state in setOf(
            SfFighterState.CROUCH, SfFighterState.CROUCH_DOWN, SfFighterState.BLOCK_LOW,
        )
        val overheadBreaks = attacker.state == SfFighterState.OVERHEAD && crouchGuard
        val blocked = !overheadBreaks && (
            defender.state == SfFighterState.WALK_BACKWARD ||
                defender.state in SF_BLOCK_STATES ||
                (crouchGuard && defenderBlockingLow[defenderIdx])
            )
        // 🆕 (2026-07-20) COMBO (3rd Strike): golpe limpio dentro de la ventana = encadena;
        // el daño escala hacia abajo (-10% por golpe encadenado, piso 50%).
        val chainHit = !blocked && now - lastHitTakenMs[defenderIdx] <= RAPID_HIT_WINDOW_MS
        if (!blocked) {
            comboHits[attackerIdx] = if (chainHit) comboHits[attackerIdx] + 1 else 1
            comboLastHitMs[attackerIdx] = now
        }
        // 🆕 (2026-07-22, Fase 1) daño base + resolución (bloqueo/chip/combo) en SfDamage (puro).
        val baseDamage = damageForAttack(attacker, strength)
        val chipAttack = attacker.state in SfDamage.CHIP_ATTACK_STATES
        val damage = SfDamage.resolvedDamage(baseDamage, blocked, chipAttack, comboHits[attackerIdx])

        // 🆕 (2026-07-25) CALIFICACIÓN (E..MS): mide el desempeño del JUGADOR (índice 0) en el combate.
        if (gradeTrackingOn && !blocked) {
            if (attackerIdx == 0) {
                gradeDealt += damage
                gradeHits++
                if (comboHits[0] > gradeMaxCombo) gradeMaxCombo = comboHits[0]
                gradeMoves.add(attacker.state)
                if (attacker.state == SfFighterState.SUPER_ART || attacker.state == SfFighterState.FATALITY) {
                    gradeSupers++
                }
            }
            if (defenderIdx == 0) gradeTaken += damage
        }

        _soundEvents.tryEmit(
            if (blocked) "land" // golpe amortiguado (thud)
            else "${strength.name.lowercase()}-${type.name.lowercase()}-hit"
        )

        // 🆕 (2026-07-21) MEDIDOR DE SÚPER: carga al pegar y al recibir (el que va perdiendo
        // también acumula, como en 3rd Strike). El bloqueo carga menos.
        attacker = attacker.copy(
            attackStruck = true,
            superMeter = chargeSuper(
                attacker,
                if (blocked) SfConstants.SUPER_METER_ON_BLOCK else SfConstants.SUPER_METER_ON_HIT,
            ),
        )
        superKeepMs[attackerIdx] = now // 🆕 (2026-07-22) conectó: su súper no decae aún
        defender = defender.copy(
            slideVelocity = strength.slideVelocity * (if (blocked) 0.5f else 1f),
            slideFriction = strength.slideFriction,
            hitPoints = (defender.hitPoints - damage).coerceAtLeast(0),
            direction = attacker.direction.opposite(), // BattleScene: el golpeado queda de frente
            superMeter = chargeSuper(
                defender,
                if (blocked) SfConstants.SUPER_METER_ON_BLOCK else SfConstants.SUPER_METER_ON_TAKE,
            ),
            // 🆕 (2026-07-22) MAREO: sube al RECIBIR (proporcional al daño, con tope por
            // golpe). Bloqueado NO marea. Al llenarse, applyMeterDecay dispara el STUN.
            dizzyMeter = if (blocked) {
                defender.dizzyMeter
            } else {
                (defender.dizzyMeter + minOf(damage, SfConstants.DIZZY_HIT_CAP))
                    .coerceAtMost(SfConstants.DIZZY_METER_MAX)
            },
        )
        if (!blocked) {
            // 🆕 (2026-07-25) Combo NUEVO (no encadenado) → el wall splat vuelve a estar disponible.
            if (!chainHit) wallSplatUsed[defenderIdx] = false
            rapidHitsTaken[defenderIdx] = if (chainHit) rapidHitsTaken[defenderIdx] + 1 else 1
            lastHitTakenMs[defenderIdx] = now

            // 🆕 (2026-07-25) WALL SPLAT: golpe PESADO (o barrida) con el rival EMPUJADO contra la
            // pared → se despega REBOTANDO al centro, quedando a rango para CONTINUAR el combo (ruta
            // nueva). UNA sola vez por combo (justo para ambos, sin infinitos). Simétrico por lado.
            val heavyEnough = strength == SfAttackStrength.HEAVY || attacker.state == SfFighterState.SWEEP
            val atRightWall = attacker.direction == SfDirection.RIGHT &&
                defender.x >= SfConstants.STAGE_X_MAX - WALL_SPLAT_ZONE
            val atLeftWall = attacker.direction == SfDirection.LEFT &&
                defender.x <= SfConstants.STAGE_X_MIN + WALL_SPLAT_ZONE
            if (heavyEnough && (atRightWall || atLeftWall) && !wallSplatUsed[defenderIdx] && defender.hitPoints > 0) {
                wallSplatUsed[defenderIdx] = true
                // Rebote al centro: el defensor se DESPEGA de la pared y vuelve hacia el atacante,
                // que queda a rango para CONTINUAR el combo (la ruta nueva). El hit-stun normal de
                // más abajo da la ventana; el rebote es la posición.
                val bouncedX = if (atRightWall) defender.x - WALL_SPLAT_BOUNCE_PX else defender.x + WALL_SPLAT_BOUNCE_PX
                defender = defender.copy(x = bouncedX, slideVelocity = 0f)
                _soundEvents.tryEmit("heavy-punch-hit") // golpe seco del aplastón
            }

            if (rapidHitsTaken[defenderIdx] >= RAPID_HITS_BEFORE_ESCAPE && defender.hitPoints > 0) {
                comboEscapeUntilMs[defenderIdx] = now + COMBO_ESCAPE_MS
                rapidHitsTaken[defenderIdx] = 0
                wallSplatUsed[defenderIdx] = false // el combo terminó → wall splat disponible otra vez
                defender = defender.copy(
                    slideVelocity = maxOf(defender.slideVelocity, strength.slideVelocity * 1.35f),
                )
                // 🆕 (2026-07-25) ANTI-"TRABE" en la esquina: el empuje del escape se lo comía la
                // pared, así que el defensor quedaba atrapado. Ahora, si está contra la pared, se
                // EMPUJA AL ATACANTE hacia atrás (crea ESPACIO real, sin meter al defensor en overlap).
                if (isNearStageCorner(defender.x)) {
                    attacker = attacker.copy(x = attacker.x - WALL_SPLAT_BOUNCE_PX * attacker.direction.sign)
                }
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
            // 🆕 (2026-07-21) Bloqueado: ahora se VE la pose de guardia (alta o baja según
            // cómo se estuviera cubriendo). Si el peleador no tiene esas hojas, se queda
            // como antes (sin cambio de estado) — nunca se rompe.
            val guard = if (crouchGuard) SfFighterState.BLOCK_LOW else SfFighterState.BLOCK_HIGH
            changeState(sim, defenderIdx, guard, now)
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
            return
        }

        // 🆕 YOALLI EHÉCATL (jefe FINAL) no “pierde” al KO: a ≤1/4 de vida (o daño letal) se
        // metamorfosea en LA PRESIDENTA con la VIDA LLENA (una sola vez). Invulnerable en la anim.
        if (tryYoalliMetamorphosis(sim, defenderIdx, attackerIdx, now)) {
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
            return
        }

        if (defender.hitPoints <= 0) {
            changeState(sim, defenderIdx, SfFighterState.KO, now)
            sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(victory = true))
            // 🆕 KO = fin de RONDA (mejor de 3); endRound decide si el combate terminó
            if (gauntletActive && !showcaseMode) gauntletKoRounds++
            endRound(sim, attackerIdx, now, computeRoundOutcome(sim, attackerIdx, attacker.state, byTime = false))
        } else if (attacker.state in knockdownStates &&
            forceState(sim, defenderIdx, SfFighterState.THROWN, now)
        ) {
            // 🆕 (2026-07-21) DERRIBO: la barrida y la súper tumban al rival, que cae y se
            // levanta solo (invulnerable mientras esté en el suelo). Si no tiene el arte,
            // `forceState` devuelve false y cae al camino de HURT normal de abajo.
            sim.setFighter(
                defenderIdx,
                sim.fighter(defenderIdx).copy(
                    slideVelocity = SfConstants.THROW_PUSH_VELOCITY * 0.5f,
                    slideFriction = SfAttackStrength.HEAVY.slideFriction,
                ),
            )
            withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) }
        } else {
            // 🆕 (2026-07-21) Golpe recibido EN CUCLILLAS: pose de daño agachado propia.
            if (crouchGuard && changeState(sim, defenderIdx, SfFighterState.HURT_CROUCH, now)) {
                withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) }
                hurtFreezeUntilMs =
                    now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
                return
            }
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
            withNetAudioCapture(defenderIdx) { emitHurtVoice(defender.id, defenderIdx, defender.hitPoints, now) } // 🆕 voz de daño (con cooldown / lowHp)
        }
        hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong()
    }

    /**
     * 🆕 (2026-07-25) Si la defensora es YOALLI EHÉCATL (jefe FINAL) sin haber metamorfoseado y el
     * golpe la deja en ≤25% HP (o la mataría), lanza BONUS_POWER_10 y NO aplica KO.
     * Al terminar la anim (ver handler BONUS_POWER_*), el id pasa a LA PRESIDENTA con VIDA LLENA.
     * @return true si se consumió el golpe como metamorfosis (el caller no hace KO/hurt).
     */
    private fun tryYoalliMetamorphosis(
        sim: Sim,
        defenderIdx: Int,
        attackerIdx: Int,
        now: Long,
    ): Boolean {
        val d = sim.fighter(defenderIdx)
        // 🆕 (2026-07-25, decisión del dueño) INVERTIDA: ahora es YOALLI EHÉCATL (jefe FINAL del
        // arcade) quien a ≤1/4 de vida se metamorfosea en LA PRESIDENTA (antes era al revés).
        if (d.id != SfFighterId.YOALLI_EHECATL || d.metamorphosed || d.metamorphosing) return false
        // 🆕 (2026-07-22, decisión del dueño) La metamorfosis automática SOLO ocurre en el
        // ROUND 1. Si sobrevivió el round 1 sin transformarse, ya no se transforma.
        if (_state.value.roundNumber != 1) return false
        val maxHp = SfConstants.HEALTH_MAX_HIT_POINTS
        val threshold = maxHp / 4 // 50 de 200
        if (d.hitPoints > threshold) return false
        // Ya está en ≤1/4 (el HP se restó arriba). Arranca anim de metamorfosis (Yoalli→Presidenta).
        // FORZAR estado: puede venir de HURT (validFrom de BONUS_POWER no lo incluye).
        val pinnedHp = d.hitPoints.coerceIn(1, threshold)
        var nf = d.copy(
            state = SfFighterState.BONUS_POWER_10,
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
        // metamorfosis Yoalli → grito + subtítulo (viaja si es MI peleadora: el rival la oye)
        withNetAudioCapture(defenderIdx) { emitSpecialVoice(d.id, now) }
        return true
    }

    /** Invulnerable durante cualquiera de las dos direcciones de la metamorfosis. */
    internal fun isMetamorphosing(f: SfFighter): Boolean =
        f.metamorphosing ||
            (f.id == SfFighterId.LA_PRESIDENTA && f.state == SfFighterState.BONUS_POWER_11 && !f.metamorphosed) ||
            (f.id == SfFighterId.YOALLI_EHECATL && f.state == SfFighterState.BONUS_POWER_10)

    // ------------------------------------------------------------------
    // Fireballs (Fireball.js) — animación, movimiento, colisión
    // ------------------------------------------------------------------

    // Delays de la animación del fireball (frames del JS); los recortes viven en la View
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
    @Volatile private var pendingParry = false
    @Volatile private var pendingGrab = false
    @Volatile private var pendingTaunt = false
    @Volatile private var pendingSuperArt = false

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

    internal val comboCatalog by lazy { SfCombos.universal(appContext) }
    internal fun signatureCombo(id: SfFighterId) = SfCombos.signature(appContext, id)

    /** Cola de acciones pendientes por índice (la IA ejecuta una por decisión). */
    internal val cpuComboQueue = Array(2) { ArrayDeque<SfComboAction>() }
    internal val cpuComboUntilMs = LongArray(2)

    // ⚠️ Estas DOS se quedan como MIEMBROS a propósito: son extensiones de `SfInput` declaradas
    // DENTRO de la clase (doble receptor: el VM + el SfInput). Fuera de la clase Kotlin no
    // admite dos receptores, así que NO se pueden mover a un parcial. No lo intentes.

    internal fun SfInput.hasAttackOrSpecial(): Boolean =
        lightPunch || mediumPunch || heavyPunch || lightKick || mediumKick || heavyKick ||
            special != null || bonusPower != null

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
        joyLastMs = SystemClock.elapsedRealtime()
    }

    /** 🆕 (2026-07-22) Al SOLTAR el joystick: limpia direcciones YA (sin esperar el timer idle),
     *  para que agacharse/caminar se corten al instante (antes se sentía "pegado"). */
    fun onJoystickRelease() {
        joyLeft = false; joyRight = false; joyUp = false; joyDown = false
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
        cpuFatalityUntilMs[0] = 0L
        cpuFatalityUntilMs[1] = 0L
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
                transport?.sendRoundEnded(sideOf(winnerIdx), outcome.name)
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
        val winnerIdx = idxOf(winnerSide)
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
        transport?.sendMatchEnded(sideOf(winnerIdx))
    }

    /** MATCH_ENDED recibido: reconcilia el final del COMBATE (por si mi sim no lo detectaba). */
    internal fun endFromNet(winnerSide: String?) {
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

    internal fun onNetDropped(reason: String?) {
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
