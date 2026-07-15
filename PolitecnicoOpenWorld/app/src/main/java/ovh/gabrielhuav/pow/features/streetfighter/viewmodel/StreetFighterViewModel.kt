package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import android.content.Context
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.streetfighter.SF_HURT_STATES
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackStrength
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfAttackType
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfBox
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
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
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.data.SfFrameCatalog
import ovh.gabrielhuav.pow.features.streetfighter.data.SfMatchClient
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetFireball
import ovh.gabrielhuav.pow.features.streetfighter.data.SfNetMsg
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import kotlin.math.abs
import kotlin.random.Random

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

    // Historial de direcciones para el hadouken (↓ ↘ → + puño), estilo ControlHistory
    private val controlHistory = ArrayDeque<Pair<Int, Long>>() // (zona, gameNow)
    private var lastZone = 0

    // ---- IA de la CPU ----
    private var cpuNextDecisionMs = 0L
    private var cpuHold = SfInput()     // intención sostenida (caminar)

    // ---- batalla ----
    private var hurtFreezeUntilMs = 0L  // hit-freeze (FighterStruckDelay)
    private var time = SfConstants.BATTLE_TIME
    private var timeTimerMs = 0L
    private var timeFlashTimerMs = 0L
    private var useFlashFrames = false
    private var koFlashTimerMs = 0L
    private var koFrame = 0
    private var endMenuAtMs = 0L

    // ─── 🆕 MULTIJUGADOR 1v1 (relay puro contra MultiplayerSF/ en Render) ───
    // Cada cliente simula a SU peleador (índice 0 local); el rival (índice 1) llega
    // por red: posición/estado/frame/hp vía OPPONENT_STATE y el daño que ME hacen vía
    // PLAYER_DAMAGE (autoridad del RECEPTOR sobre su propio HP).
    private var matchClient: SfMatchClient? = null
    @Volatile private var remoteSnapshot: SfNetMsg? = null
    private val netDamageQueue = ConcurrentLinkedQueue<SfNetMsg>()
    private var myOnlineChar: SfFighterId? = null
    private var oppOnlineChar: SfFighterId? = null
    private var lastNetSendMs = 0L
    private var onlineEndSent = false
    private var countdownJob: Job? = null
    private val isOnline: Boolean get() = _state.value.onlineStatus != SfOnlineStatus.OFF
    private val inOnlineFight: Boolean get() = _state.value.onlineStatus == SfOnlineStatus.FIGHTING

    private companion object {
        const val TICK_MS = 16L
        // Táctil: el joystick emite cada ~33 ms al sostenerse; con 150 ms el input quedaba "pegado"
        // ~150 ms tras soltar (se sentía que "no reacciona"). 100 ms sigue siendo seguro (>33 ms).
        const val JOYSTICK_IDLE_MS = 100L
        // Ventana del cuarto de círculo del especial. Más ancha = más fácil en táctil.
        const val HADOUKEN_WINDOW_MS = 1100L
        const val END_MENU_DELAY_MS = 4200L
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
        SfFighterState.CROUCH_UP, SfFighterState.CROUCH_DOWN, SfFighterState.CROUCH,
        SfFighterState.CROUCH_TURN, SfFighterState.LIGHT_PUNCH, SfFighterState.MEDIUM_PUNCH,
        SfFighterState.HEAVY_PUNCH,
    )

    private val attackValidFrom = setOf(
        SfFighterState.IDLE, SfFighterState.WALK_FORWARD, SfFighterState.WALK_BACKWARD,
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
        ),
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
    )

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
                delay(TICK_MS)
                val real = SystemClock.elapsedRealtime()
                val dtMs = (real - lastRealMs).coerceAtMost(100L)
                lastRealMs = real
                val s = _state.value
                // El reloj de juego se detiene en pausa, diálogo de salida y selector de personaje
                if (s.isPaused || s.showExitDialog || s.inCharacterSelect) continue
                gameNow += dtMs
                tick(gameNow, dtMs / 1000f)
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

        if (!sim.battleEnded) updateTimer(sim, now)

        if (online) {
            applyRemoteSnapshot(sim, now)   // posición/estado/hp del rival (red)
            processNetDamage(sim, now)      // daño que el rival ME mandó (yo soy la autoridad de mi HP)
        }

        // Hit-freeze: al conectar un golpe, los peleadores se congelan 15 frames
        val frozen = now < hurtFreezeUntilMs
        if (!frozen) {
            updateFighter(sim, 0, buildPlayerInput(now, sim), now, dt)
            // El rival: CPU offline; por RED online (no se simula localmente)
            if (!online) updateFighter(sim, 1, buildCpuInput(now, sim), now, dt)
        }
        updateFireballs(sim, now, dt)
        updateSplashes(sim, now)
        updateCamera(sim)
        updateKoFlash(sim, now)

        if (online) {
            sendNetState(sim, now)
            appendRemoteFireballs(sim)      // render de los proyectiles del rival
        }

        val showEnd = sim.battleEnded && now >= endMenuAtMs
        _state.update(sim, now, showEnd)
    }

    private fun MutableStateFlow<StreetFighterState>.update(sim: Sim, now: Long, showEnd: Boolean) {
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
        )
    }

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

    private fun isAnimationCompleted(f: SfFighter): Boolean {
        val anim = animOf(f)
        return anim[f.animationFrame.coerceIn(0, anim.size - 1)].delay == -1
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
                _soundEvents.tryEmit("hadouken")
            }
            else -> Unit // CROUCH / CROUCH_UP / IDLE_TURN / CROUCH_TURN: sin init
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
        updateAttackBoxCollided(sim, idx, now)
    }

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
                if (nf.y > SfConstants.STAGE_FLOOR) {
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
            SfFighterState.IDLE_TURN -> if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.IDLE, now)
            SfFighterState.CROUCH_TURN -> if (isAnimationCompleted(f)) changeState(sim, idx, SfFighterState.CROUCH, now)

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
                // handleHadouken: el fireball sale en el frame 3
                if (f.animationFrame == 3 && !f.fireballFired) {
                    sim.setFighter(idx, f.copy(fireballFired = true))
                    val meta = attackMeta.getValue(f.state)
                    sim.fireballs.add(
                        SfFireball(
                            ownerIndex = idx,
                            x = f.x + 76f * f.direction.sign,
                            y = f.y - 57f,
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
        val state = when (strength) {
            SfAttackStrength.LIGHT -> SfFighterState.SPECIAL_1_LIGHT
            SfAttackStrength.MEDIUM -> SfFighterState.SPECIAL_1_MEDIUM
            SfAttackStrength.HEAVY -> SfFighterState.SPECIAL_1_HEAVY
        }
        return changeState(sim, idx, state, now)
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

    // ------------------------------------------------------------------
    // Cajas por frame + constraints + colisión de ataque
    // ------------------------------------------------------------------

    private fun frameDef(f: SfFighter) = dataFor(f).frames.getValue(animOf(f)[f.animationFrame.coerceIn(0, animOf(f).size - 1)].frameKey)

    private fun pushBoxWorld(f: SfFighter): SfBox = SfBox.fromList(frameDef(f).push).toWorld(f.x, f.y, f.direction)

    private fun updateStageConstraints(sim: Sim, idx: Int, dt: Float) {
        var f = sim.fighter(idx)
        val push = SfBox.fromList(frameDef(f).push)

        // Límites del viewport (como el JS, contra la cámara)
        if (f.x - sim.camX + SfConstants.FIGHTER_DEFAULT_WIDTH > SfConstants.SCENE_WIDTH) {
            f = f.copy(x = sim.camX + SfConstants.SCENE_WIDTH - SfConstants.FIGHTER_DEFAULT_WIDTH)
        } else if (f.x - sim.camX - SfConstants.FIGHTER_DEFAULT_WIDTH < 0f) {
            f = f.copy(x = sim.camX + SfConstants.FIGHTER_DEFAULT_WIDTH)
        }
        sim.setFighter(idx, f)

        // Empuje al traslaparse los pushbox (updateStageConstraints del JS)
        var opp = sim.fighter(1 - idx)
        if (!pushBoxWorld(f).overlaps(pushBoxWorld(opp))) return

        val pushableStates = setOf(
            SfFighterState.IDLE, SfFighterState.CROUCH, SfFighterState.JUMP_UP,
            SfFighterState.JUMP_BACKWARD, SfFighterState.JUMP_FORWARD,
        )
        if (f.x <= opp.x) {
            f = f.copy(x = maxOf(opp.x + SfBox.fromList(frameDef(opp).push).x - (push.x + push.width), push.width - 1f))
            if (opp.state in pushableStates) opp = opp.copy(x = opp.x + SfConstants.FIGHTER_PUSH_FRICTION * dt)
        } else {
            f = f.copy(x = minOf(sim.camX + SfConstants.SCENE_WIDTH - push.width, opp.x + SfBox.fromList(frameDef(opp).push).width))
            if (opp.state in pushableStates) opp = opp.copy(x = opp.x - SfConstants.FIGHTER_PUSH_FRICTION * dt)
        }
        sim.setFighter(idx, f)
        sim.setFighter(1 - idx, opp)
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
            // Quirk fiel del JS: si un área NO traslapa, se sale del chequeo completo
            if (!actualHit.overlaps(hurtBox)) return

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

        // ONLINE: el HP del RIVAL es suyo (autoridad del receptor). Si MI golpe/proyectil
        // conecta con él, solo AVISO (PLAYER_DAMAGE) + efectos optimistas locales; su HP y
        // su pose de daño llegarán en su siguiente OPPONENT_STATE.
        if (inOnlineFight && defenderIdx == 1) {
            _soundEvents.tryEmit("${strength.name.lowercase()}-${type.name.lowercase()}-hit")
            sim.setFighter(attackerIdx, attacker.copy(attackStruck = true))
            matchClient?.sendDamage(strength.damage, strength.name, type.name)
            hitPos?.let { (x, y) ->
                sim.splashes.add(SfHitSplash(x = x, y = y, playerId = attackerIdx, strength = strength, animationTimerMs = now))
            }
            hurtFreezeUntilMs = now + (SfConstants.FIGHTER_STRUCK_DELAY * SfConstants.FRAME_TIME_MS).toLong() / 2
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

        if (defender.hitPoints <= 0) {
            changeState(sim, defenderIdx, SfFighterState.KO, now)
            sim.setFighter(attackerIdx, sim.fighter(attackerIdx).copy(victory = true))
            sim.winner = attackerIdx
            sim.battleEnded = true
            endMenuAtMs = now + END_MENU_DELAY_MS
            // ONLINE: si el que cayó soy YO (defensor local), publico el resultado
            if (inOnlineFight) sendOnlineEnd(winnerIdx = attackerIdx)
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

    // ------------------------------------------------------------------
    // Fireballs (Fireball.js) — animación, movimiento, colisión
    // ------------------------------------------------------------------

    // Delays de la animación del fireball (frames del JS); los recortes viven en la View
    private val fireballActiveDelays = listOf(5, 2, 5, 1)
    private val fireballCollidedDelays = listOf(13, 3, 7)
    private val fireballBox = SfBox(-15f, -13f, 30f, 24f)

    private fun updateFireballs(sim: Sim, now: Long, dt: Float) {
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
            // onTimeEnd: gana quien tenga más vida (empate = jugador, como el JS >=)
            val playerWins = sim.p0.hitPoints >= sim.p1.hitPoints
            val winnerIdx = if (playerWins) 0 else 1
            sim.setFighter(winnerIdx, sim.fighter(winnerIdx).copy(victory = true))
            changeState(sim, 1 - winnerIdx, SfFighterState.KO, now)
            sim.winner = winnerIdx
            sim.battleEnded = true
            endMenuAtMs = now + END_MENU_DELAY_MS
            if (isOnline) sendOnlineEnd(winnerIdx) // timeout: ambos lo calculan; el guard evita doble envío
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
    // ------------------------------------------------------------------

    private fun buildCpuInput(now: Long, sim: Sim): SfInput {
        if (sim.battleEnded) return SfInput()
        if (now < cpuNextDecisionMs) return cpuHold

        cpuNextDecisionMs = now + Random.nextLong(280L, 620L)
        val dist = abs(sim.p1.x - sim.p0.x)
        val roll = Random.nextFloat()

        cpuHold = when {
            dist > 190f -> when {
                roll < 0.12f -> SfInput(special = SfAttackStrength.entries.random()) // hadouken lejano
                roll < 0.25f -> SfInput(up = true, forward = true)                    // salto adelante
                else -> SfInput(forward = true)
            }
            dist > 90f -> when {
                roll < 0.70f -> SfInput(forward = true)
                roll < 0.85f -> SfInput(backward = true)
                else -> SfInput(down = true)
            }
            else -> when {
                roll < 0.45f -> randomCpuAttack()
                roll < 0.65f -> SfInput(backward = true)
                roll < 0.75f -> SfInput(up = true)
                else -> SfInput()
            }
        }
        // Los botones son de UN tick: se entregan una vez y la intención queda solo direccional
        val oneShot = cpuHold
        cpuHold = cpuHold.copy(
            lightPunch = false, mediumPunch = false, heavyPunch = false,
            lightKick = false, mediumKick = false, heavyKick = false, special = null,
        )
        return oneShot
    }

    private fun randomCpuAttack(): SfInput {
        val strength = SfAttackStrength.entries.random()
        return if (Random.nextBoolean()) {
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
        }
    }

    /** Revancha: offline reinicia ya; online la PIDE (arranca cuando la pidan los dos). */
    fun restartBattle() {
        if (isOnline) {
            matchClient?.requestRematch()
            return
        }
        val s = _state.value
        startBattle(playerId = s.player.id, cpuId = s.cpu.id)
    }

    /**
     * Selector: fija el personaje. Online avisa y espera al rival; offline arranca ya
     * contra `rivalId` (🆕 el jugador también ELIGE al enemigo; null = Ken/Ryu default).
     */
    fun selectCharacter(id: SfFighterId, rivalId: SfFighterId? = null) {
        if (isOnline) {
            myOnlineChar = id
            matchClient?.selectCharacter(id.name)
            return // la pelea arranca cuando el servidor mande FIGHT_START
        }
        val cpuId = rivalId ?: if (id == SfFighterId.KEN) SfFighterId.RYU else SfFighterId.KEN
        startBattle(playerId = id, cpuId = cpuId)
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

    private fun startBattle(playerId: SfFighterId, cpuId: SfFighterId) {
        resetInternals()
        val base = StreetFighterState()
        _state.value = base.copy(
            player = base.player.copy(id = playerId),
            cpu = base.cpu.copy(id = cpuId),
            inCharacterSelect = false,
        )
    }

    /** Reinicio de todos los relojes/colas internos (resetGameState del JS). */
    private fun resetInternals() {
        gameNow = 0L
        lastRealMs = SystemClock.elapsedRealtime()
        hurtFreezeUntilMs = 0L
        time = SfConstants.BATTLE_TIME
        timeTimerMs = 0L
        timeFlashTimerMs = 0L
        useFlashFrames = false
        koFlashTimerMs = 0L
        koFrame = 0
        endMenuAtMs = 0L
        cpuNextDecisionMs = 0L
        cpuHold = SfInput()
        pendingAttacks.clear()
        controlHistory.clear()
        lastZone = 0
        lastNetSendMs = 0L
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
            matchClient = client
            client.connect(
                BuildConfig.SF_SERVER_URL,
                object : SfMatchClient.Listener {
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

    /** Sale de la sala y vuelve al selector offline (con error opcional a mostrar). */
    fun cancelOnline(errorMsg: String? = null) {
        countdownJob?.cancel()
        matchClient?.leaveRoom()
        matchClient?.close()
        matchClient = null
        remoteSnapshot = null
        netDamageQueue.clear()
        myOnlineChar = null
        oppOnlineChar = null
        onlineEndSent = false
        resetInternals()
        _state.value = StreetFighterState(onlineError = errorMsg)
    }

    /** El ANFITRIÓN elige el mapa (null = al azar del tema); el server lo replica. */
    fun chooseMapOnline(file: String?) {
        val resolved = file ?: SF_CLASSIC_THEME.fullBackgrounds.randomOrNull()?.file ?: return
        matchClient?.selectMap(resolved)
    }

    private fun handleNetMessage(msg: SfNetMsg) {
        val s = _state.value
        when (msg.type) {
            "ROOM_CREATED" -> _state.value = s.copy(
                onlineStatus = SfOnlineStatus.WAITING_OPPONENT, roomCode = msg.code, isHost = true,
            )
            "ROOM_JOINED" -> _state.value = s.copy(
                onlineStatus = SfOnlineStatus.SELECTING, roomCode = msg.code, isHost = false,
            )
            "OPPONENT_JOINED" -> _state.value = s.copy(onlineStatus = SfOnlineStatus.SELECTING)
            // Sala pública: en lista de espera (roomCode null → la UI muestra "buscando rival")
            "QUEUED" -> _state.value = s.copy(
                onlineStatus = SfOnlineStatus.WAITING_OPPONENT, roomCode = null,
            )
            "ROOMS_LIST" -> _state.value = s.copy(
                activeRoomsInfo = "Salas activas: ${msg.rooms?.size ?: 0} · En espera: ${msg.queue ?: 0}",
            )
            "ERROR" -> cancelOnline(msg.message ?: "Error del servidor")
            "CHARACTERS_SELECTED" -> {
                val oppName = if (s.isHost) msg.char2 else msg.char1
                oppOnlineChar = oppName?.let { n -> runCatching { SfFighterId.valueOf(n) }.getOrNull() }
                    ?: SfFighterId.KEN
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
                )
            }
            "OPPONENT_LEFT", "OPPONENT_DISCONNECTED" -> {
                if (s.onlineStatus == SfOnlineStatus.FIGHTING && !s.battleEnded) {
                    // Victoria por abandono
                    onlineEndSent = true
                    _state.value = s.copy(
                        battleEnded = true, winnerIndex = 0, showEndMenu = true,
                        onlineStatus = SfOnlineStatus.OPPONENT_LEFT,
                    )
                } else if (s.isHost) {
                    // El invitado se fue en la antesala: la sala sigue viva esperando a otro
                    _state.value = s.copy(
                        onlineStatus = SfOnlineStatus.WAITING_OPPONENT, opponentWantsRematch = false,
                    )
                } else {
                    cancelOnline("El anfitrión cerró la sala")
                }
            }
        }
    }

    /** FIGHT_START: arranca la pelea online. El anfitrión pelea a la IZQUIERDA. */
    private fun startOnlineBattle() {
        val s = _state.value
        resetInternals()
        onlineEndSent = false
        remoteSnapshot = null
        netDamageQueue.clear()
        val base = StreetFighterState()
        val my = myOnlineChar ?: SfFighterId.PRANKEDY
        val opp = oppOnlineChar ?: SfFighterId.KEN
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
        )
    }

    /** Aplica el último OPPONENT_STATE al peleador remoto (índice 1). */
    private fun applyRemoteSnapshot(sim: Sim, now: Long) {
        val rs = remoteSnapshot ?: return
        val st = rs.state?.let { n -> runCatching { SfFighterState.valueOf(n) }.getOrNull() } ?: sim.p1.state
        sim.p1 = sim.p1.copy(
            x = rs.x ?: sim.p1.x,
            y = rs.y ?: sim.p1.y,
            state = st,
            animationFrame = rs.frame ?: 0,
            direction = if ((rs.dir ?: 1) >= 0) SfDirection.RIGHT else SfDirection.LEFT,
            hitPoints = rs.hp ?: sim.p1.hitPoints,
        )
        // Si su propio estado reporta 0 HP, gané (él manda MATCH_ENDED; esto lo adelanta)
        if ((rs.hp ?: 1) <= 0 && !sim.battleEnded) {
            sim.winner = 0
            sim.battleEnded = true
            endMenuAtMs = now + END_MENU_DELAY_MS
            sendOnlineEnd(winnerIdx = 0)
        }
    }

    /** Aplica a MI peleador el daño que me mandó el rival (yo decido bloqueo con MI estado). */
    private fun processNetDamage(sim: Sim, now: Long) {
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

    /** Manda MI estado al rival cada ~66 ms (posición, pose, frame, HP y mis proyectiles). */
    private fun sendNetState(sim: Sim, now: Long) {
        if (now - lastNetSendMs < 66) return
        lastNetSendMs = now
        val f = sim.p0
        matchClient?.sendPlayerState(
            x = f.x, y = f.y, state = f.state.name, frame = f.animationFrame,
            dir = f.direction.sign, hp = f.hitPoints,
            fireballs = sim.fireballs.filter { it.ownerIndex == 0 }.map {
                SfNetFireball(it.x, it.y, it.direction.sign, it.strength.name, it.state.name, it.animationFrame)
            },
        )
    }

    /** Añade los proyectiles del RIVAL (render-only; su dueño calcula las colisiones). */
    private fun appendRemoteFireballs(sim: Sim) {
        remoteSnapshot?.fireballs?.forEach { nf ->
            sim.fireballs.add(
                SfFireball(
                    ownerIndex = 1,
                    x = nf.x, y = nf.y,
                    direction = if (nf.dir >= 0) SfDirection.RIGHT else SfDirection.LEFT,
                    strength = runCatching { SfAttackStrength.valueOf(nf.strength) }.getOrDefault(SfAttackStrength.LIGHT),
                    velocity = 0f,
                    state = runCatching { SfFireballState.valueOf(nf.state) }.getOrDefault(SfFireballState.ACTIVE),
                    animationFrame = nf.frame,
                ),
            )
        }
    }

    /** Publica el fin de pelea UNA sola vez ("p1" = anfitrión). */
    private fun sendOnlineEnd(winnerIdx: Int) {
        if (onlineEndSent) return
        onlineEndSent = true
        val iAmP1 = _state.value.isHost
        val side = if (winnerIdx == 0) (if (iAmP1) "p1" else "p2") else (if (iAmP1) "p2" else "p1")
        matchClient?.sendMatchEnded(side)
    }

    /** MATCH_ENDED recibido: reconcilia el final (por si mi sim aún no lo detectaba). */
    private fun endFromNet(winnerSide: String?) {
        val s = _state.value
        if (s.battleEnded && s.showEndMenu) return
        val winnerIdx = when (winnerSide) {
            "p1" -> if (s.isHost) 0 else 1
            "p2" -> if (s.isHost) 1 else 0
            else -> 0
        }
        onlineEndSent = true
        _state.value = s.copy(battleEnded = true, winnerIndex = winnerIdx, showEndMenu = true)
    }

    private fun onNetDropped(reason: String?) {
        if (!isOnline) return
        cancelOnline(reason?.let { "Conexión perdida: $it" } ?: "Conexión perdida con el servidor")
    }

    override fun onCleared() {
        matchClient?.close()
        super.onCleared()
    }
}
