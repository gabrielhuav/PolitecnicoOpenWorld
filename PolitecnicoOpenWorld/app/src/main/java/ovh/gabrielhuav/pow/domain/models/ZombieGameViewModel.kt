// features/zombie_minigame/viewmodel/ZombieGameViewModel.kt
package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.interior.viewmodel.CollisionGrid
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class ZombieGameViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        ZombieGameState(
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls(),
            isLoading = false
        )
    )
    val state: StateFlow<ZombieGameState> = _state.asStateFlow()

    private var gameLoopJob: Job? = null
    private var idleJob: Job? = null

    private companion object {
        const val PLAYER_WALK_STEP = 0.006f
        const val PLAYER_RUN_STEP = 0.011f

        const val ZOMBIE_SPEED = 0.0018f
        const val ZOMBIE_FRAME_COUNT = 9
        const val ZOMBIE_FRAME_INTERVAL_MS = 140L

        const val CONTACT_RADIUS = 0.06f
        const val ZOMBIE_DAMAGE = 8f
        const val ZOMBIE_DAMAGE_COOLDOWN_MS = 500L

        const val PLAYER_PUNCH_DAMAGE = 34f
        const val PLAYER_ATTACK_RADIUS = 0.10f
        const val PLAYER_ATTACK_COOLDOWN_MS = 600L

        const val TICK_MS = 33L

        // Cooldown para no atravesar puertas en cadena al pisar una hitbox
        const val DOOR_COOLDOWN_MS = 800L
    }

    private var lastPlayerAttackMs = 0L
    private val zombieDamageCooldowns = HashMap<String, Long>()
    private var lastDoorTransitionMs = 0L

    init {
        // Empezamos siempre en el lobby (el hub)
        loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
        startGameLoop()
    }

    // ─── ACCESO A ZONA / GRID ──────────────────────────────

    private fun currentRoom(): ZombieRoom =
        ZombieRoomCatalog.rooms[_state.value.currentRoomIndex]

    private fun currentGrid(): CollisionGrid =
        ZombieRoomCatalog.collisionGrids[_state.value.currentRoomIndex]

    // ─── CARGA DE ZONA ─────────────────────────────────────

    private fun loadRoom(index: Int) {
        val room = ZombieRoomCatalog.rooms[index]
        val grid = ZombieRoomCatalog.collisionGrids[index]

        val zombies = room.zombieSpawns
            .map { snapToWalkable(grid, it.x, it.y) }
            .map { (sx, sy) -> ZombieEntity(x = sx, y = sy) }

        val (spawnX, spawnY) = snapToWalkable(grid, room.playerSpawn.x, room.playerSpawn.y)

        zombieDamageCooldowns.clear()
        lastDoorTransitionMs = System.currentTimeMillis()

        _state.update { s ->
            s.copy(
                currentRoomIndex = index,
                playerX = spawnX,
                playerY = spawnY,
                zombies = zombies,
                totalZombies = zombies.size,
                zombiesRemaining = zombies.count { !it.isDying },
                nearbyDoorLabel = null,
                // La victoria solo aplica a edificios; al cargar limpiamos el flag
                showVictoryScreen = false
            )
        }
    }

    private fun snapToWalkable(grid: CollisionGrid, x: Float, y: Float): Pair<Float, Float> {
        if (grid.isWalkable(x, y)) return x to y
        val stepX = 1f / CollisionGrid.COLS
        val stepY = 1f / CollisionGrid.ROWS
        for (radius in 1..maxOf(CollisionGrid.COLS, CollisionGrid.ROWS)) {
            for (dy in -radius..radius) {
                for (dx in -radius..radius) {
                    val nx = (x + dx * stepX).coerceIn(0f, 1f)
                    val ny = (y + dy * stepY).coerceIn(0f, 1f)
                    if (grid.isWalkable(nx, ny)) return nx to ny
                }
            }
        }
        return 0.5f to 0.5f
    }

    /** Navega a la zona destino; "__WORLD__" dispara salida al mapa principal. */
    private fun goToRoom(targetRoomId: String) {
        if (targetRoomId == ZombieRoomCatalog.EXIT_TO_WORLD) {
            gameLoopJob?.cancel()
            _state.update { it.copy(isExitingToWorld = true) }
            return
        }
        val idx = ZombieRoomCatalog.indexOfRoom(targetRoomId)
        if (idx >= 0) loadRoom(idx)
    }

    // ─── GAME LOOP ─────────────────────────────────────────

    private fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                try {
                    tick()
                } catch (e: Exception) {
                }
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val s = _state.value
        if (s.showVictoryScreen || s.isExitingToWorld) return

        val now = System.currentTimeMillis()
        val grid = ZombieRoomCatalog.collisionGrids[s.currentRoomIndex]

        // 1. Mover zombis hacia el jugador + animar (respetando paredes)
        val movedZombies = s.zombies.map { z ->
            if (z.isDying) return@map z
            moveZombieTowardsPlayer(z, s.playerX, s.playerY, now, grid)
        }

        // 2. Daño zombi -> jugador (cooldown por zombi)
        var pendingDamage = 0f
        for (z in movedZombies) {
            if (z.isDying) continue
            val d = hypot((z.x - s.playerX), (z.y - s.playerY))
            if (d <= CONTACT_RADIUS) {
                val last = zombieDamageCooldowns[z.id] ?: 0L
                if (now - last >= ZOMBIE_DAMAGE_COOLDOWN_MS) {
                    zombieDamageCooldowns[z.id] = now
                    pendingDamage += ZOMBIE_DAMAGE
                }
            }
        }

        _state.update { cur ->
            var newHealth = cur.playerHealth
            var pulse = cur.damagePulseTrigger
            var showBar = cur.showPlayerHealthBar

            if (pendingDamage > 0f) {
                newHealth -= pendingDamage
                pulse += 1
                showBar = true
            }

            // ─── MUERTE Y RESPAWN IN-SITU ───
            if (newHealth <= 0f) {
                newHealth = 100f  // mismas coordenadas, misma zona
            }

            cur.copy(
                zombies = movedZombies,
                playerHealth = newHealth.coerceIn(0f, 100f),
                damagePulseTrigger = pulse,
                showPlayerHealthBar = showBar,
                zombiesRemaining = movedZombies.count { !it.isDying }
            )
        }
    }

    private fun moveZombieTowardsPlayer(
        z: ZombieEntity, px: Float, py: Float, now: Long, grid: CollisionGrid
    ): ZombieEntity {
        val dx = px - z.x
        val dy = py - z.y
        val dist = hypot(dx, dy)

        val (nx, ny) = if (dist > 0.0001f) (dx / dist) to (dy / dist) else 0f to 0f
        val step = if (dist > CONTACT_RADIUS * 0.6f) ZOMBIE_SPEED else 0f

        val targetX = (z.x + nx * step).coerceIn(0.02f, 0.98f)
        val targetY = (z.y + ny * step).coerceIn(0.02f, 0.98f)

        var resolvedX = z.x
        var resolvedY = z.y
        when {
            grid.isWalkable(targetX, targetY) -> { resolvedX = targetX; resolvedY = targetY }
            grid.isWalkable(targetX, z.y) -> { resolvedX = targetX }
            grid.isWalkable(z.x, targetY) -> { resolvedY = targetY }
        }

        val movedThisTick = resolvedX != z.x || resolvedY != z.y
        val facing = if (abs(nx) > 0.0001f) nx >= 0f else z.facingRight

        val advance = now - z.lastFrameAdvanceMs >= ZOMBIE_FRAME_INTERVAL_MS
        val newFrame = if (advance && (movedThisTick || step > 0f)) (z.frameIndex + 1) % ZOMBIE_FRAME_COUNT else z.frameIndex
        val newFrameTime = if (advance) now else z.lastFrameAdvanceMs

        return z.copy(
            x = resolvedX, y = resolvedY,
            facingRight = facing,
            frameIndex = newFrame,
            lastFrameAdvanceMs = newFrameTime
        )
    }

    // ─── COMBATE: JUGADOR -> ZOMBI ─────────────────────────

    fun performPlayerAttack() {
        val now = System.currentTimeMillis()
        if (now - lastPlayerAttackMs < PLAYER_ATTACK_COOLDOWN_MS) return
        lastPlayerAttackMs = now

        val s = _state.value
        val target = s.zombies
            .filter { !it.isDying && hypot(it.x - s.playerX, it.y - s.playerY) <= PLAYER_ATTACK_RADIUS }
            .minByOrNull { hypot(it.x - s.playerX, it.y - s.playerY) } ?: return

        val newHealth = target.health - PLAYER_PUNCH_DAMAGE

        if (newHealth <= 0f) {
            _state.update { cur ->
                cur.copy(zombies = cur.zombies.map {
                    if (it.id == target.id) it.copy(health = 0f, isDying = true) else it
                })
            }
            viewModelScope.launch {
                delay(1000L)
                removeZombie(target.id)
            }
        } else {
            _state.update { cur ->
                cur.copy(zombies = cur.zombies.map {
                    if (it.id == target.id) it.copy(health = newHealth) else it
                })
            }
        }
    }

    private fun removeZombie(id: String) {
        zombieDamageCooldowns.remove(id)
        _state.update { cur ->
            val remaining = cur.zombies.filter { it.id != id }
            val aliveCount = remaining.count { !it.isDying }
            // Victoria SOLO si estamos en un edificio con zombis y los limpiamos
            val room = ZombieRoomCatalog.rooms[cur.currentRoomIndex]
            val won = room.type == ZoneType.BUILDING && aliveCount == 0 && cur.totalZombies > 0
            cur.copy(
                zombies = remaining,
                zombiesRemaining = aliveCount,
                showVictoryScreen = won
            )
        }
        if (_state.value.showVictoryScreen) {
            triggerBuildingClearedSequence()
        }
    }

    /**
     * Al limpiar un edificio: mostramos "Congratulations" y regresamos al LOBBY
     * (no al mapa principal). Desde el lobby el jugador decide si sale al mundo
     * o entra a otro edificio.
     */
    private fun triggerBuildingClearedSequence() {
        viewModelScope.launch {
            delay(3000L)
            val lobbyIdx = ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID)
            loadRoom(lobbyIdx)
        }
    }

    // ─── MOVIMIENTO DEL JUGADOR ────────────────────────────

    fun moveByAngle(angleRad: Double) {
        val s = _state.value
        val step = if (s.isRunning) PLAYER_RUN_STEP else PLAYER_WALK_STEP
        val dx = cos(angleRad).toFloat() * step
        val dy = -sin(angleRad).toFloat() * step
        applyMovement(s.playerX + dx, s.playerY + dy, dx)
    }

    fun moveDirection(direction: Direction) {
        val s = _state.value
        val step = if (s.isRunning) PLAYER_RUN_STEP else PLAYER_WALK_STEP
        val (dx, dy) = when (direction) {
            Direction.UP -> 0f to -step
            Direction.DOWN -> 0f to step
            Direction.LEFT -> -step to 0f
            Direction.RIGHT -> step to 0f
        }
        applyMovement(s.playerX + dx, s.playerY + dy, dx)
    }

    private fun applyMovement(newX: Float, newY: Float, dxForFacing: Float) {
        val grid = currentGrid()
        val clampedX = newX.coerceIn(0f, 1f)
        val clampedY = newY.coerceIn(0f, 1f)
        val curX = _state.value.playerX
        val curY = _state.value.playerY

        val (finalX, finalY) = when {
            grid.isWalkable(clampedX, clampedY) -> clampedX to clampedY
            grid.isWalkable(clampedX, curY) -> clampedX to curY
            grid.isWalkable(curX, clampedY) -> curX to clampedY
            else -> {
                if (abs(dxForFacing) > 0.0001f) {
                    _state.update { it.copy(isPlayerFacingRight = dxForFacing > 0) }
                }
                return
            }
        }

        val facing = if (abs(dxForFacing) > 0.0001f) dxForFacing > 0 else _state.value.isPlayerFacingRight
        val action = if (_state.value.isRunning) PlayerAction.RUN else PlayerAction.WALK

        idleJob?.cancel()
        _state.update {
            it.copy(playerX = finalX, playerY = finalY, playerAction = action, isPlayerFacingRight = facing)
        }

        idleJob = viewModelScope.launch {
            delay(150)
            if (_state.value.playerAction != PlayerAction.SPECIAL) {
                _state.update { it.copy(playerAction = PlayerAction.IDLE) }
            }
        }

        checkDoors(finalX, finalY)
    }

    /**
     * Comprueba si el jugador está sobre alguna puerta. Si lo está, transiciona.
     * Un cooldown evita disparos en cadena justo tras aparecer en una zona.
     */
    private fun checkDoors(px: Float, py: Float) {
        val room = currentRoom()
        val door = room.doors.firstOrNull { it.hitbox.contains(px, py) }

        if (door == null) {
            if (_state.value.nearbyDoorLabel != null) {
                _state.update { it.copy(nearbyDoorLabel = null) }
            }
            return
        }

        // Mostrar etiqueta de la puerta
        if (_state.value.nearbyDoorLabel != door.label) {
            _state.update { it.copy(nearbyDoorLabel = door.label) }
        }

        val now = System.currentTimeMillis()
        if (now - lastDoorTransitionMs < DOOR_COOLDOWN_MS) return
        goToRoom(door.targetRoomId)
    }

    fun setRunning(running: Boolean) = _state.update { it.copy(isRunning = running) }

    fun showInitialHealthBar() {
        _state.update { it.copy(showPlayerHealthBar = true) }
        viewModelScope.launch {
            delay(4000L)
            _state.update { it.copy(showPlayerHealthBar = false) }
        }
    }

    fun consumeExit() {
        gameLoopJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        idleJob?.cancel()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ZombieGameViewModel(
                settingsRepository = SettingsRepository(context.applicationContext)
            ) as T
        }
    }
}