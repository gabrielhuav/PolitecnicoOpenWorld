package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel base para todas las screens de interior.
 *
 * Cada edificio crea su propia instancia (scoped al NavBackStackEntry) y le
 * pasa su matriz de colisión. La lógica de movimiento es idéntica para todos:
 * el jugador se mueve sobre coordenadas normalizadas [0,1] y se valida cada
 * paso contra la matriz.
 */
// ETAPA 4 (Hilt): @AssistedInject — la MATRIZ DE COLISIÓN (collisionGrid) es un arg de RUNTIME que
// construye cada pantalla (no es inyectable), así que va @Assisted; el SettingsRepository lo inyecta
// Hilt (AppModule). El @AssistedFactory (abajo) lo usa hiltViewModel(creationCallback) en la pantalla.
@dagger.hilt.android.lifecycle.HiltViewModel(assistedFactory = InteriorViewModel.Factory::class)
open class InteriorViewModel @dagger.assisted.AssistedInject constructor(
    // Publico (solo lectura) para que la capa de OCLUSION de InteriorScreenBase lea las celdas '2'.
    @dagger.assisted.Assisted val collisionGrid: CollisionGrid,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        InteriorState(
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            showShoulderButtons = settingsRepository.getShowWorldShoulderButtons(),
            swapControls = settingsRepository.getSwapControls(),
            showCoordsWidget = settingsRepository.getShowCoordsWidget(),
            isLoading = false
        )
    )
    val state: StateFlow<InteriorState> = _state.asStateFlow()

    private var idleJob: Job? = null

    // Paso por tick. Como las coordenadas son [0,1], el paso es muy pequeño.
    private val WALK_STEP = 0.004f
    private val RUN_STEP = 0.008f

    /**
     * Mueve al jugador según un ángulo en radianes (lo manda el joystick).
     * angleRad = 0   → derecha
     * angleRad = π/2 → arriba (convención matemática; en pantalla Y crece hacia abajo)
     */
    fun moveByAngle(angleRad: Double) {
        val current = _state.value
        val step = if (current.isRunning) RUN_STEP else WALK_STEP

        val dx = cos(angleRad).toFloat() * step
        // Invertimos el seno porque en Compose Y crece hacia abajo
        val dy = -sin(angleRad).toFloat() * step

        val newX = current.playerX + dx
        val newY = current.playerY + dy

        applyMovement(newX, newY, dx)
    }

    /**
     * Mueve al jugador con D-pad (4 direcciones discretas).
     */
    fun moveDirection(direction: Direction) {
        val current = _state.value
        val step = if (current.isRunning) RUN_STEP else WALK_STEP

        val (dx, dy) = when (direction) {
            Direction.UP    -> 0f to -step
            Direction.DOWN  -> 0f to  step
            Direction.LEFT  -> -step to 0f
            Direction.RIGHT ->  step to 0f
        }

        applyMovement(current.playerX + dx, current.playerY + dy, dx)
    }

    private fun applyMovement(newX: Float, newY: Float, dxForFacing: Float) {
        val clampedX = newX.coerceIn(0f, 1f)
        val clampedY = newY.coerceIn(0f, 1f)

        // Intento 1: posición completa
        if (collisionGrid.isWalkable(clampedX, clampedY)) {
            updatePlayer(clampedX, clampedY, dxForFacing)
            return
        }
        // Intento 2: solo X (deslizar contra pared horizontal)
        if (collisionGrid.isWalkable(clampedX, _state.value.playerY)) {
            updatePlayer(clampedX, _state.value.playerY, dxForFacing)
            return
        }
        // Intento 3: solo Y (deslizar contra pared vertical)
        if (collisionGrid.isWalkable(_state.value.playerX, clampedY)) {
            updatePlayer(_state.value.playerX, clampedY, dxForFacing)
            return
        }
        // Bloqueado: actualizamos solo la dirección visual
        if (abs(dxForFacing) > 0.0001f) {
            _state.update { it.copy(isFacingRight = dxForFacing > 0) }
        }
    }

    private fun updatePlayer(x: Float, y: Float, dxForFacing: Float) {
        val current = _state.value
        val facing = if (abs(dxForFacing) > 0.0001f) dxForFacing > 0 else current.isFacingRight
        val action = if (current.isRunning) PlayerAction.RUN else PlayerAction.WALK

        idleJob?.cancel()
        _state.update {
            it.copy(playerX = x, playerY = y, playerAction = action, isFacingRight = facing)
        }

        // Volver a IDLE si dejamos de movernos en ~150ms
        idleJob = viewModelScope.launch {
            delay(150)
            _state.update { it.copy(playerAction = PlayerAction.IDLE) }
        }
    }

    fun setRunning(running: Boolean) {
        _state.update { it.copy(isRunning = running) }
    }

    /**
     * Posición inicial del jugador. Cada edificio puede llamar esto al
     * construirse si quiere un spawn point distinto al centro.
     */
    fun setInitialPosition(x: Float, y: Float) {
        if (collisionGrid.isWalkable(x, y)) {
            _state.update { it.copy(playerX = x, playerY = y) }
        }
    }

    // ETAPA 4 (Hilt): @AssistedFactory — reemplaza al ViewModelProvider.Factory manual. La pantalla
    // lo invoca vía hiltViewModel<InteriorViewModel, Factory>(creationCallback = { it.create(grid) }).
    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(collisionGrid: CollisionGrid): InteriorViewModel
    }
}