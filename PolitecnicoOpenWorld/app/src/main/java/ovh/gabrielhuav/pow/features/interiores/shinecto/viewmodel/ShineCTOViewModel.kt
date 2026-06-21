package ovh.gabrielhuav.pow.features.interiores.shinecto.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOFloor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ShineCTOViewModel(
    application: android.app.Application,
    private val settingsRepository: SettingsRepository,
    private val collectibleRepository: CollectibleRepository
) : androidx.lifecycle.AndroidViewModel(application) {

    @Volatile
    private var shineCollectibleClaimed = false

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

    init {
        spawnInitialDrinks()
        viewModelScope.launch(Dispatchers.IO) {
            // Garantiza que c_shine existe antes de consultar; sin esto,
            // none{} retorna true sobre lista vacía → claimed = true en frío.
            collectibleRepository.initializeDefaultCollectiblesIfNeeded()
            val uncollected = collectibleRepository.getUncollectedCollectibles()
            shineCollectibleClaimed = uncollected.none { it.id == "c_shine" }
        }
    }

    // ─── Interactable hitboxes fijas (sin bebidas — éstas son dinámicas) ────
    private val groundInteractables: List<Pair<ShineCTOInteractable, NormZone>> = listOf(
        ShineCTOInteractable.EXIT to NormZone(0.80f, 0.35f, 1.00f, 0.65f),
        ShineCTOInteractable.STAIRS_UP to NormZone(0.40f, 0.78f, 0.60f, 1.00f)
    )
    private val upperInteractables: List<Pair<ShineCTOInteractable, NormZone>> = listOf(
        ShineCTOInteractable.STAIRS_DOWN to NormZone(0.40f, 0.78f, 0.60f, 1.00f)
    )

    // ─── Spawn seguro de bebidas ─────────────────────────────────────────────
    // Margen para no spawnear en los bordes ni encima de las puertas/escaleras.
    private val drinkSafeZones: List<NormZone> = listOf(
        NormZone(0.08f, 0.08f, 0.70f, 0.70f)   // área interior libre
    )

    private fun randomDrinkPosition(currentDrinks: List<ActiveDrink>): Pair<Float, Float> {
        repeat(30) {
            val nx = Random.nextFloat() * 0.60f + 0.10f  // [0.10, 0.70]
            val ny = Random.nextFloat() * 0.55f + 0.10f  // [0.10, 0.65]
            // No spawnear demasiado cerca de otra bebida existente
            val tooClose = currentDrinks.any { d ->
                abs(d.nx - nx) < 0.12f && abs(d.ny - ny) < 0.12f
            }
            if (!tooClose) return nx to ny
        }
        return 0.25f to 0.35f  // fallback
    }

    private var drinkIdCounter = 0

    private fun spawnInitialDrinks() {
        val drinks = mutableListOf<ActiveDrink>()
        repeat(2) {
            val (nx, ny) = randomDrinkPosition(drinks)
            drinks.add(ActiveDrink(drinkIdCounter++, nx, ny))
        }
        _state.update { it.copy(drinks = drinks) }
    }
    // ─── Movement ───────────────────────────────────────────────────────────

    private val BASE_WALK = 0.004f
    private val BASE_RUN = 0.008f

    private val SHINE_COLLECTIBLE_HITBOX = NormZone(0.29f, 0.39f, 0.41f, 0.51f)

    fun moveByAngle(angleRad: Double) {
        val s = _state.value
        val step = effectiveStep(s)
        val dx = cos(angleRad).toFloat() * step
        val dy = -sin(angleRad).toFloat() * step
        applyMovement(s.playerX + dx, s.playerY + dy, dx)
    }

    fun moveDirection(direction: Direction) {
        val s = _state.value
        val step = effectiveStep(s)
        val (dx, dy) = when (direction) {
            Direction.UP -> 0f to -step
            Direction.DOWN -> 0f to step
            Direction.LEFT -> -step to 0f
            Direction.RIGHT -> step to 0f
        }
        applyMovement(s.playerX + dx, s.playerY + dy, dx)
    }

    private fun effectiveStep(s: ShineCTOState): Float =
        if (s.isRunning) BASE_RUN else BASE_WALK

    private fun applyMovement(newX: Float, newY: Float, dxForFacing: Float) {
        val cx = newX.coerceIn(0f, 1f)
        val cy = newY.coerceIn(0f, 1f)
        val facing = if (abs(dxForFacing) > 0.001f) dxForFacing > 0 else _state.value.isFacingRight
        val action = when {
            _state.value.playerAction == PlayerAction.SPECIAL -> PlayerAction.SPECIAL
            _state.value.isRunning -> PlayerAction.RUN
            else -> PlayerAction.WALK
        }

        idleJob?.cancel()
        _state.update {
            it.copy(
                playerX = cx,
                playerY = cy,
                playerAction = action,
                isFacingRight = facing
            )
        }
        updateNearbyInteractable(cx, cy)

        idleJob = viewModelScope.launch {
            delay(150)
            if (_state.value.playerAction != PlayerAction.SPECIAL) {
                _state.update { it.copy(playerAction = PlayerAction.IDLE) }
            }
        }
    }

    private fun updateNearbyInteractable(px: Float, py: Float) {
        val interactables =
            if (_state.value.floor == ShineCTOFloor.GROUND) groundInteractables else upperInteractables
        var fixedNearby = interactables.firstOrNull { (_, zone) -> zone.contains(px, py) }?.first
        if (fixedNearby == null
            && _state.value.floor == ShineCTOFloor.GROUND
            && _state.value.shineCollectibleActive
            && SHINE_COLLECTIBLE_HITBOX.contains(px, py)
        ) {
            fixedNearby = ShineCTOInteractable.SHINE_COLLECTIBLE
        }

        val h = ActiveDrink.HITBOX_HALF
        val nearbyDrink = _state.value.drinks.firstOrNull { d ->
            px in (d.nx - h)..(d.nx + h) && py in (d.ny - h)..(d.ny + h)
        }

        val resolvedInteractable =
            fixedNearby ?: if (nearbyDrink != null) ShineCTOInteractable.DRINK else null
        val resolvedDrinkId = if (fixedNearby == null) nearbyDrink?.id else null

        if (_state.value.nearbyInteractable != resolvedInteractable || _state.value.nearbyDrinkId != resolvedDrinkId) {
            _state.update {
                it.copy(
                    nearbyInteractable = resolvedInteractable,
                    nearbyDrinkId = resolvedDrinkId
                )
            }
        }
    }

    // ─── Interaction ────────────────────────────────────────────────────────
    fun setRunning(running: Boolean) {
        _state.update { s ->
            val action = when {
                s.playerAction == PlayerAction.SPECIAL -> PlayerAction.SPECIAL
                running && s.playerAction == PlayerAction.WALK -> PlayerAction.RUN
                !running && s.playerAction == PlayerAction.RUN -> PlayerAction.WALK
                else -> s.playerAction
            }
            s.copy(isRunning = running, playerAction = action)
        }
    }

    fun setSpecial(pressed: Boolean) {
        if (pressed) {
            idleJob?.cancel()
            _state.update { it.copy(playerAction = PlayerAction.SPECIAL) }
        } else {
            _state.update { it.copy(playerAction = if (_state.value.isRunning) PlayerAction.RUN else PlayerAction.IDLE) }
        }
    }

    /**
     * Returns true if the caller (screen) should navigate back to the world map.
     */
    fun onInteract(): Boolean {
        return when (_state.value.nearbyInteractable) {
            ShineCTOInteractable.EXIT -> true
            ShineCTOInteractable.STAIRS_UP -> {
                _state.update {
                    it.copy(
                        floor = ShineCTOFloor.UPPER,
                        playerX = 0.5f,
                        playerY = 0.7f,
                        nearbyInteractable = null
                    )
                }
                false
            }

            ShineCTOInteractable.STAIRS_DOWN -> {
                _state.update {
                    it.copy(
                        floor = ShineCTOFloor.GROUND,
                        playerX = 0.5f,
                        playerY = 0.7f,
                        nearbyInteractable = null
                    )
                }
                false
            }

            ShineCTOInteractable.DRINK -> {
                consumeDrink()
                false
            }

            ShineCTOInteractable.SHINE_COLLECTIBLE -> {
                claimShineCollectible()
                false
            }

            null -> false
        }
    }

    private fun consumeDrink() {
        val drinkId = _state.value.nearbyDrinkId
        val s = _state.value
        val newCount = s.drinkCount + 1
        val remaining = if (drinkId != null) s.drinks.filter { it.id != drinkId } else s.drinks
        val (nx, ny) = randomDrinkPosition(remaining)
        val newDrinks = remaining + ActiveDrink(drinkIdCounter++, nx, ny)

        if (shineCollectibleClaimed) {
            // Fase 2: daño por exceso de azúcar
            val newHealth = (s.playerHealth - ShineCTOState.DRINK_DAMAGE).coerceAtLeast(0f)
            _state.update {
                it.copy(
                    drinkCount = newCount, playerHealth = newHealth,
                    showDrinkToast = true, drinkToastMessage = "Exceso de azúcar en el cuerpo",
                    drinks = newDrinks, nearbyInteractable = null, nearbyDrinkId = null
                )
            }
            toastJob?.cancel()
            toastJob = viewModelScope.launch {
                delay(2200)
                _state.update { it.copy(showDrinkToast = false) }
                // Fase 3: muerte → expulsión
                if (_state.value.playerHealth <= 0f) {
                    _state.update { it.copy(showWastedScreen = true) }
                    delay(4000L)
                    _state.update {
                        it.copy(showWastedScreen = false, shouldExitToWorld = true,
                            drinkCount = 0, playerHealth = 100f)
                    }
                }
            }
            return
        }

        // Fase 1: consumo normal
        val unlockCollectible =
            newCount >= ShineCTOState.DRINKS_TO_UNLOCK && !s.shineCollectibleActive
        val msg = when {
            unlockCollectible -> getLocalizedString(ovh.gabrielhuav.pow.R.string.sh_drinks_special)
            newCount == 1 -> getLocalizedString(ovh.gabrielhuav.pow.R.string.sh_drinks_first)
            newCount == 2 -> getLocalizedString(ovh.gabrielhuav.pow.R.string.sh_drinks_two)
            else -> getLocalizedString(ovh.gabrielhuav.pow.R.string.sh_drinks_count, newCount)
        }
        _state.update {
            it.copy(
                drinkCount = newCount, showDrinkToast = true, drinkToastMessage = msg,
                drinks = newDrinks,
                shineCollectibleActive = unlockCollectible || s.shineCollectibleActive,
                nearbyInteractable = null, nearbyDrinkId = null
            )
        }
        toastJob?.cancel()
        toastJob =
            viewModelScope.launch { delay(2200); _state.update { it.copy(showDrinkToast = false) } }
    }

    private fun claimShineCollectible() {
        shineCollectibleClaimed = true
        viewModelScope.launch(Dispatchers.IO) {
            collectibleRepository.claimCollectible("c_shine")
        }
        _state.update {
            it.copy(
                shineCollectibleActive = false,
                nearbyInteractable = null,
                showClaimedPopupFor = ActiveCollectible(
                    id = "c_shine",
                    name = "Refresco de la Casa",
                    description = "Dato curioso: El refresco de Shine CTO tiene fama de ser " +
                            "adictivo. Se dice que quien llega a la décima copa descubre el sabor " +
                            "secreto… aunque el precio lo paga el estómago.",
                    assetPath = "SPRITES/COLLECTIBLES/colec_shine.webp",
                    latitude = 0.0,
                    longitude = 0.0
                )
            )
        }
    }

    fun dismissShineClaimedPopup() {
        _state.update { it.copy(showClaimedPopupFor = null) }
    }

    fun getLocalizedString(resId: Int, vararg args: Any): String {
        val lang = settingsRepository.getLanguage()
        val baseContext = getApplication<android.app.Application>()
        val contextToUse = if (lang.isNotEmpty()) {
            val locale = java.util.Locale(lang)
            val config = android.content.res.Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        } else {
            baseContext
        }
        return contextToUse.getString(resId, *args)
    }


    // ─── Factory ────────────────────────────────────────────────────────────────

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = PowDatabase.getInstance(context.applicationContext)
            return ShineCTOViewModel(
                context.applicationContext as android.app.Application,
                SettingsRepository(context.applicationContext),
                CollectibleRepository(db.collectibleDao())
            ) as T
        }
    }

    /** Normalised rectangular hitbox. */
    private data class NormZone(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun contains(x: Float, y: Float) = x in l..r && y in t..b
    }
}