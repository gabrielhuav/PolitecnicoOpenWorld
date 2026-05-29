package ovh.gabrielhuav.pow.features.shinecto.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.ShineCTOFloor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class ShineCTOViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        ShineCTOState(
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls()
        )
    )
    val state: StateFlow<ShineCTOState> = _state.asStateFlow()

    private var idleJob: Job? = null
    private var toastJob: Job? = null

    // ─── Interactable hitboxes (normalised) ─────────────────────────────────
    // Ground floor: EXIT on the right edge, STAIRS at centre-bottom, 2 DRINKs
    private val groundInteractables: List<Pair<ShineCTOInteractable, NormZone>> = listOf(
        ShineCTOInteractable.EXIT      to NormZone(0.80f, 0.35f, 1.00f, 0.65f),
        ShineCTOInteractable.STAIRS_UP to NormZone(0.40f, 0.78f, 0.60f, 1.00f),
        ShineCTOInteractable.DRINK     to NormZone(0.10f, 0.25f, 0.30f, 0.50f),
        ShineCTOInteractable.DRINK     to NormZone(0.55f, 0.10f, 0.75f, 0.35f)
    )

    // Upper floor: STAIRS at centre-bottom, 1 DRINK
    private val upperInteractables: List<Pair<ShineCTOInteractable, NormZone>> = listOf(
        ShineCTOInteractable.STAIRS_DOWN to NormZone(0.40f, 0.78f, 0.60f, 1.00f),
        ShineCTOInteractable.DRINK       to NormZone(0.20f, 0.15f, 0.40f, 0.40f),
        ShineCTOInteractable.DRINK       to NormZone(0.60f, 0.15f, 0.80f, 0.40f)
    )

    // ─── Movement ───────────────────────────────────────────────────────────

    private val BASE_WALK  = 0.004f
    private val BASE_RUN   = 0.008f

    fun moveByAngle(angleRad: Double) {
        val s = _state.value
        val step = effectiveStep(s)
        val dx =  cos(angleRad).toFloat() * step
        val dy = -sin(angleRad).toFloat() * step
        applyMovement(s.playerX + dx, s.playerY + dy, dx)
    }

    fun moveDirection(direction: Direction) {
        val s = _state.value
        val step = effectiveStep(s)
        val (dx, dy) = when (direction) {
            Direction.UP    -> 0f to -step
            Direction.DOWN  -> 0f to  step
            Direction.LEFT  -> -step to 0f
            Direction.RIGHT ->  step to 0f
        }
        applyMovement(s.playerX + dx, s.playerY + dy, dx)
    }

    private fun effectiveStep(s: ShineCTOState): Float =
        BASE_WALK * s.speedMultiplier   // run not implemented in this interior

    private fun applyMovement(newX: Float, newY: Float, dxForFacing: Float) {
        val cx = newX.coerceIn(0f, 1f)
        val cy = newY.coerceIn(0f, 1f)
        val facing = if (abs(dxForFacing) > 0.001f) dxForFacing > 0 else _state.value.isFacingRight

        idleJob?.cancel()
        _state.update { it.copy(playerX = cx, playerY = cy, playerAction = PlayerAction.WALK, isFacingRight = facing) }
        updateNearbyInteractable(cx, cy)

        idleJob = viewModelScope.launch {
            delay(150)
            _state.update { it.copy(playerAction = PlayerAction.IDLE) }
        }
    }

    private fun updateNearbyInteractable(px: Float, py: Float) {
        val interactables = if (_state.value.floor == ShineCTOFloor.GROUND) groundInteractables else upperInteractables
        val nearby = interactables.firstOrNull { (_, zone) -> zone.contains(px, py) }?.first
        if (_state.value.nearbyInteractable != nearby) {
            _state.update { it.copy(nearbyInteractable = nearby) }
        }
    }

    // ─── Interaction ────────────────────────────────────────────────────────

    /**
     * Returns true if the caller (screen) should navigate back to the world map.
     */
    fun onInteract(): Boolean {
        return when (_state.value.nearbyInteractable) {
            ShineCTOInteractable.EXIT -> true
            ShineCTOInteractable.STAIRS_UP -> {
                _state.update { it.copy(floor = ShineCTOFloor.UPPER, playerX = 0.5f, playerY = 0.7f, nearbyInteractable = null) }
                false
            }
            ShineCTOInteractable.STAIRS_DOWN -> {
                _state.update { it.copy(floor = ShineCTOFloor.GROUND, playerX = 0.5f, playerY = 0.7f, nearbyInteractable = null) }
                false
            }
            ShineCTOInteractable.DRINK -> {
                consumeDrink()
                false
            }
            null -> false
        }
    }

    private fun consumeDrink() {
        _state.update { s ->
            val newCount = s.drinkCount + 1
            val newSpeed = (s.speedMultiplier - ShineCTOState.SPEED_REDUCTION_PER_DRINK)
                .coerceAtLeast(ShineCTOState.MIN_SPEED)
            s.copy(drinkCount = newCount, speedMultiplier = newSpeed, showDrinkToast = true)
        }
        toastJob?.cancel()
        toastJob = viewModelScope.launch {
            delay(2200)
            _state.update { it.copy(showDrinkToast = false) }
        }
    }

    // ─── Factory ────────────────────────────────────────────────────────────

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShineCTOViewModel(SettingsRepository(context.applicationContext)) as T
    }
}

/** Normalised rectangular hitbox. */
private data class NormZone(val l: Float, val t: Float, val r: Float, val b: Float) {
    fun contains(x: Float, y: Float) = x in l..r && y in t..b
}