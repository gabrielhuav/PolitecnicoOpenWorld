package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3 (descomposición del god-object, ver PLAN_descomponer_WorldMapViewModel.md):
// CUARTO manager (4/6). Posee el sub-estado UI del NIVEL DE BÚSQUEDA (estrellas), el
// aviso de CARJACK y los DISPAROS de policía visibles. El VM lo compone en `uiState` vía
// la FACHADA `combine` (las Views siguen leyendo uiState.wantedLevel / .carjackWarning /
// .policeShots — no se tocó ninguna View).
//
// Además posee los TIMERS internos del subsistema (lastCrimeTime, lastWantedDecayTime,
// carjackStartTime) y las constantes de diseño, de modo que la lógica de subida/decaimiento
// del nivel y la máquina de carjack son PURAS y testeables en JVM: todos los métodos con
// tiempo reciben `now` como parámetro (sin reloj de pared).
//
// NOTA DE CORTE (ver CHECKPOINT_SENIOR_refactor.md, manager 4/6): la ORQUESTACIÓN se queda
// en el VM (parcial WorldMapWanted.kt) porque mezcla red/policía/Context/IO/otros grupos y
// solo DELEGA aquí los writes del sub-estado (corte limpio > dogma):
//   - runPoliceTick (PoliceManager.update, snap/pathfind, daño, broadcast WS, purga remota),
//   - anyAggressorAdjacent (recorre remoteEntities),
//   - handleCarjack (lee vehicleSpeed/MAX_SPEED, getLocalizedString, forceExitVehicle),
//   - forceExitVehicle (abandona el coche, updateNpcsState).
// El throttle de broadcast (lastPoliceBroadcast/POLICE_BROADCAST_MS), REMOTE_POLICE_STALE_MS
// y CARJACK_ADJ_RADIUS se quedan en el VM (son parte de esa orquestación).
// ─────────────────────────────────────────────────────────────────────────────

/** Sub-estado UI del nivel de búsqueda / carjack / disparos (campos espejo de WorldMapState). */
data class WantedSubState(
    val wantedLevel: Int = 0,
    val carjackWarning: String? = null,
    val policeShots: List<PoliceShot> = emptyList()
)

class WantedManager {

    private val _state = MutableStateFlow(WantedSubState())
    val state: StateFlow<WantedSubState> = _state.asStateFlow()

    // Timers internos del subsistema (antes @Volatile en el VM). Los tocan el game loop
    // (coroutine) y acciones de Main (abordar patrulla / morir) → se conservan @Volatile.
    @Volatile private var lastCrimeTime = 0L
    @Volatile private var lastWantedDecayTime = 0L
    @Volatile private var carjackStartTime = 0L

    // ─── NIVEL DE BÚSQUEDA (estrellas) ───────────────────────────────────────
    /**
     * Sube el nivel (con tope) y reinicia el contador de impunidad. El guard del APOCALIPSIS
     * (no hay delitos en modo zombi) vive en el VM porque necesita globalZombieMode.
     */
    fun raiseWantedLevel(amount: Int, now: Long) {
        lastCrimeTime = now
        val current = _state.value.wantedLevel
        if (current < MAX_WANTED_LEVEL) {
            _state.update { it.copy(wantedLevel = (current + amount).coerceAtMost(MAX_WANTED_LEVEL)) }
        }
    }

    /**
     * Baja el nivel gradualmente cuando dejas de delinquir. Cuantas MÁS estrellas tengas, MÁS
     * tarda en bajar cada una (el paso escala con el nivel actual): 1★ ~1×base, 5★ ~5×base.
     */
    fun tickWantedDecay(now: Long) {
        val level = _state.value.wantedLevel
        if (level <= 0) return
        if (now - lastCrimeTime < WANTED_DECAY_GRACE_MS) return
        if (now - lastWantedDecayTime < WANTED_DECAY_STEP_MS * level) return
        lastWantedDecayTime = now
        _state.update { it.copy(wantedLevel = (it.wantedLevel - 1).coerceAtLeast(0)) }
    }

    /**
     * Fija el nivel directamente (policía de campaña 1/0, restaurar guardado). Se coacciona al
     * rango válido; StateFlow no reemite si el valor no cambia (idempotente → sustituye a los
     * `if (wantedLevel != 1)` de la policía de campaña).
     */
    fun setWantedLevel(level: Int) {
        _state.update { it.copy(wantedLevel = level.coerceIn(0, MAX_WANTED_LEVEL)) }
    }

    /** Nivel MÁXIMO (robar una patrulla) + marca el delito para reiniciar el decaimiento. */
    fun setMaxWanted(now: Long) {
        lastCrimeTime = now
        _state.update { it.copy(wantedLevel = MAX_WANTED_LEVEL) }
    }

    /**
     * Pierde el nivel de búsqueda y limpia el estado de carjack (morir / teletransporte). La
     * policía NO desaparece de golpe: con wantedLevel 0 entra en modo retirada (lo gestiona el VM).
     */
    fun clearWanted() {
        carjackStartTime = 0L
        _state.update { it.copy(wantedLevel = 0, carjackWarning = null) }
    }

    // ─── CARJACK (te bajan del coche si te detienes con un perseguidor pegado) ─
    /** Limpia el estado de carjack (dejaste de estar amenazado o aceleraste). */
    fun clearCarjack() {
        if (carjackStartTime != 0L) {
            carjackStartTime = 0L
            if (_state.value.carjackWarning != null) _state.update { it.copy(carjackWarning = null) }
        }
    }

    /**
     * Arma/mantiene el aviso de carjack y devuelve `true` cuando se cumplió CARJACK_MS (el VM
     * ejecuta entonces el descenso forzado del vehículo). `warning` = texto YA localizado por el VM.
     */
    fun armCarjack(now: Long, warning: String): Boolean {
        if (carjackStartTime == 0L) carjackStartTime = now
        if (_state.value.carjackWarning != warning) _state.update { it.copy(carjackWarning = warning) }
        if (now - carjackStartTime >= CARJACK_MS) {
            carjackStartTime = 0L
            _state.update { it.copy(carjackWarning = null) }
            return true
        }
        return false
    }

    // ─── DISPAROS DE POLICÍA VISIBLES (trazo breve origen→jugador) ────────────
    /** Añade disparos SIN purgar (p. ej. la policía del apocalipsis los acumula en el game loop). */
    fun addPoliceShots(shots: List<PoliceShot>) {
        if (shots.isEmpty()) return
        _state.update { it.copy(policeShots = it.policeShots + shots) }
    }

    /** Fusiona los disparos nuevos y purga los más viejos que `ttlMs` (se dibujan unos ms). */
    fun mergeAndPrunePoliceShots(fresh: List<PoliceShot>, now: Long, ttlMs: Long) {
        val prev = _state.value.policeShots
        if (fresh.isEmpty() && prev.isEmpty()) return
        val kept = (prev + fresh).filter { now - it.at <= ttlMs }
        if (kept != prev) _state.update { it.copy(policeShots = kept) }
    }

    companion object {
        const val MAX_WANTED_LEVEL = 5
        const val WANTED_DECAY_GRACE_MS = 25000L   // tiempo sin delito antes de empezar a bajar
        const val WANTED_DECAY_STEP_MS = 15000L    // cada cuánto baja una estrella
        const val CARJACK_MS = 2500L               // tiempo quieto antes de que te bajen
    }
}
