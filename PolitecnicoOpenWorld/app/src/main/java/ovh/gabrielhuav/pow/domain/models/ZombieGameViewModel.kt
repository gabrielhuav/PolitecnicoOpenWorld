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
import ovh.gabrielhuav.pow.domain.models.zombie.InteractableItem
import ovh.gabrielhuav.pow.domain.models.zombie.WorldRect
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

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
        const val PLAYER_WALK_STEP = 7f      // px de mundo / tick
        const val PLAYER_RUN_STEP = 13f
        const val PLAYER_RADIUS = 28f        // hitbox del jugador (px)

        const val ZOMBIE_SPEED = 2.4f        // px / tick (lento)
        const val ZOMBIE_FRAME_COUNT = 9
        const val ZOMBIE_FRAME_INTERVAL_MS = 140L
        const val ZOMBIE_RADIUS = 30f

        const val CONTACT_DIST = 56f         // suma de radios aprox
        const val ZOMBIE_DAMAGE = 12f
        const val ZOMBIE_DAMAGE_COOLDOWN_MS = 3000L  // ← 3 s por zombi

        const val PLAYER_PUNCH_DAMAGE = 34f
        const val PLAYER_ATTACK_RADIUS = 120f
        const val PLAYER_ATTACK_COOLDOWN_MS = 600L

        const val SPAWN_RADIUS_MIN = 280f    // radio de aparición alrededor del jugador
        const val SPAWN_RADIUS_MAX = 520f

        const val TICK_MS = 33L
        const val DOOR_COOLDOWN_MS = 900L
        const val ITEM_PICKUP_DIST = 70f

        val LOOT_ASSETS = listOf(
            "coleccionables/colec_5.webp" to "Laptop escomia",
            "coleccionables/colec_6.webp" to "Apuntes de Leyenda"
        )
    }

    private var lastPlayerAttackMs = 0L
    private var lastDoorTransitionMs = 0L

    init {
        loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
        startGameLoop()
    }

    // ─── ACCESO ────────────────────────────────────────────
    private fun currentRoom(): ZombieRoom = ZombieRoomCatalog.rooms[_state.value.currentRoomIndex]

    /** Rectángulos NO caminables (px de mundo) de la zona actual. */
    private fun currentBlockers(): List<WorldRect> {
        val r = currentRoom()
        return r.collisionGridFrac.map { it.toWorldRect(r.worldWidth, r.worldHeight) }
    }

    private fun isWalkable(x: Float, y: Float): Boolean {
        val r = currentRoom()
        if (x < PLAYER_RADIUS || y < PLAYER_RADIUS ||
            x > r.worldWidth - PLAYER_RADIUS || y > r.worldHeight - PLAYER_RADIUS) return false
        return currentBlockers().none { it.contains(x, y) }
    }

    // ─── CARGA DE ZONA ─────────────────────────────────────
    private fun loadRoom(index: Int) {
        val room = ZombieRoomCatalog.rooms[index]
        val now = System.currentTimeMillis()
        lastDoorTransitionMs = now

        val spawnX = room.playerSpawnFrac.x * room.worldWidth
        val spawnY = room.playerSpawnFrac.y * room.worldHeight

        // Spawn de zombis por radio alrededor del jugador
        val zombies = if (room.type == ZoneType.BUILDING && room.zombieCount > 0) {
            val lootIndex = Random.nextInt(room.zombieCount)  // un zombi lleva loot
            (0 until room.zombieCount).map { i ->
                val (zx, zy) = spawnAroundPlayer(spawnX, spawnY, room)
                ZombieEntity(
                    x = zx, y = zy,
                    lastFrameAdvanceMs = now,
                    isLootCarrier = (i == lootIndex)
                )
            }
        } else emptyList()

        _state.update {
            it.copy(
                currentRoomIndex = index,
                playerX = spawnX,
                playerY = spawnY,
                zombies = zombies,
                items = emptyList(),
                totalZombies = zombies.size,
                zombiesRemaining = zombies.size,
                nearbyDoorLabel = null,
                nearbyItemId = null,
                showVictoryScreen = false
            )
        }
    }

    private fun spawnAroundPlayer(px: Float, py: Float, room: ZombieRoom): Pair<Float, Float> {
        repeat(20) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val radius = SPAWN_RADIUS_MIN + Random.nextFloat() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN)
            val x = (px + cos(angle) * radius).coerceIn(ZOMBIE_RADIUS, room.worldWidth - ZOMBIE_RADIUS)
            val y = (py + sin(angle) * radius).coerceIn(ZOMBIE_RADIUS, room.worldHeight - ZOMBIE_RADIUS)
            val blocked = room.collisionGridFrac.any {
                it.toWorldRect(room.worldWidth, room.worldHeight).contains(x, y)
            }
            if (!blocked) return x to y
        }
        return (px + SPAWN_RADIUS_MIN) to py
    }

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
                try { tick() } catch (_: Exception) {}
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        val s = _state.value
        if (s.showVictoryScreen || s.isExitingToWorld) return
        val now = System.currentTimeMillis()
        val room = ZombieRoomCatalog.rooms[s.currentRoomIndex]
        val blockers = room.collisionGridFrac.map { it.toWorldRect(room.worldWidth, room.worldHeight) }

        var newHealth = s.playerHealth
        var pulse = s.damagePulseTrigger

        // 1. Mover + animar zombis; resolver daño con cooldown POR ZOMBI
        val movedZombies = s.zombies.map { z ->
            if (z.isDying) return@map z
            val moved = moveZombie(z, s.playerX, s.playerY, now, room, blockers)
            val dist = hypot(moved.x - s.playerX, moved.y - s.playerY)
            if (dist <= CONTACT_DIST && now - moved.lastDamageToPlayerMs >= ZOMBIE_DAMAGE_COOLDOWN_MS) {
                newHealth -= ZOMBIE_DAMAGE
                pulse += 1
                moved.copy(lastDamageToPlayerMs = now)
            } else moved
        }

        // 2. Respawn in-situ si muere
        if (newHealth <= 0f) newHealth = 100f

        // 3. Item cercano (para prompt de recoger)
        val nearItem = s.items.firstOrNull {
            !it.collected && hypot(it.x - s.playerX, it.y - s.playerY) <= ITEM_PICKUP_DIST
        }

        _state.update {
            it.copy(
                zombies = movedZombies,
                playerHealth = newHealth.coerceIn(0f, 100f),
                damagePulseTrigger = pulse,
                showPlayerHealthBar = if (pulse != it.damagePulseTrigger) true else it.showPlayerHealthBar,
                zombiesRemaining = movedZombies.count { z -> !z.isDying },
                nearbyItemId = nearItem?.id
            )
        }
    }

    private fun moveZombie(
        z: ZombieEntity, px: Float, py: Float, now: Long,
        room: ZombieRoom, blockers: List<WorldRect>
    ): ZombieEntity {
        val dx = px - z.x
        val dy = py - z.y
        val dist = hypot(dx, dy)
        val (nx, ny) = if (dist > 0.01f) (dx / dist) to (dy / dist) else 0f to 0f
        val step = if (dist > CONTACT_DIST * 0.7f) ZOMBIE_SPEED else 0f

        val targetX = (z.x + nx * step).coerceIn(ZOMBIE_RADIUS, room.worldWidth - ZOMBIE_RADIUS)
        val targetY = (z.y + ny * step).coerceIn(ZOMBIE_RADIUS, room.worldHeight - ZOMBIE_RADIUS)

        fun free(x: Float, y: Float) = blockers.none { it.contains(x, y) }
        var rx = z.x; var ry = z.y
        when {
            free(targetX, targetY) -> { rx = targetX; ry = targetY }
            free(targetX, z.y) -> rx = targetX
            free(z.x, targetY) -> ry = targetY
        }

        val advance = now - z.lastFrameAdvanceMs >= ZOMBIE_FRAME_INTERVAL_MS
        return z.copy(
            x = rx, y = ry,
            facingRight = if (abs(nx) > 0.01f) nx >= 0f else z.facingRight,
            frameIndex = if (advance) (z.frameIndex + 1) % ZOMBIE_FRAME_COUNT else z.frameIndex,
            lastFrameAdvanceMs = if (advance) now else z.lastFrameAdvanceMs
        )
    }

    // ─── COMBATE JUGADOR → ZOMBI ───────────────────────────
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
                onZombieDeath(target)
            }
        } else {
            _state.update { cur ->
                cur.copy(zombies = cur.zombies.map {
                    if (it.id == target.id) it.copy(health = newHealth) else it
                })
            }
        }
    }

    /**
     * Al morir un zombi: si era portador de loot, instancia un InteractableItem
     * en las coordenadas EXACTAS de su muerte. Luego comprueba victoria.
     */
    private fun onZombieDeath(dead: ZombieEntity) {
        _state.update { cur ->
            val remaining = cur.zombies.filter { it.id != dead.id }
            val newItems = if (dead.isLootCarrier) {
                val (asset, name) = LOOT_ASSETS.random()
                cur.items + InteractableItem(x = dead.x, y = dead.y, assetPath = asset, name = name)
            } else cur.items

            val alive = remaining.count { !it.isDying }
            val room = ZombieRoomCatalog.rooms[cur.currentRoomIndex]
            val won = room.type == ZoneType.BUILDING && alive == 0 && cur.totalZombies > 0
            cur.copy(zombies = remaining, items = newItems, zombiesRemaining = alive, showVictoryScreen = won)
        }
        if (_state.value.showVictoryScreen) {
            viewModelScope.launch {
                delay(3000L)
                loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
            }
        }
    }

    // ─── INTERACCIÓN (botón X) ─────────────────────────────
    fun onInteract() {
        val s = _state.value
        // Prioridad 1: recoger item
        val itemId = s.nearbyItemId
        if (itemId != null) {
            val item = s.items.firstOrNull { it.id == itemId } ?: return
            _state.update { cur ->
                cur.copy(
                    items = cur.items.filter { it.id != itemId },
                    nearbyItemId = null,
                    pickupToast = "Recogido: ${item.name}"
                )
            }
            viewModelScope.launch {
                delay(2000L)
                _state.update { it.copy(pickupToast = null) }
            }
            return
        }
        // Prioridad 2: cruzar puerta cercana
        val room = currentRoom()
        val door = room.doors.firstOrNull {
            it.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                .contains(s.playerX, s.playerY)
        }
        if (door != null) goToRoom(door.targetRoomId)
    }

    // ─── MOVIMIENTO DEL JUGADOR ────────────────────────────
    fun moveByAngle(angleRad: Double) {
        val s = _state.value
        val step = if (s.isRunning) PLAYER_RUN_STEP else PLAYER_WALK_STEP
        applyMovement(
            s.playerX + cos(angleRad).toFloat() * step,
            s.playerY - sin(angleRad).toFloat() * step,
            cos(angleRad).toFloat()
        )
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
        val curX = _state.value.playerX
        val curY = _state.value.playerY

        val (fx, fy) = when {
            isWalkable(newX, newY) -> newX to newY
            isWalkable(newX, curY) -> newX to curY
            isWalkable(curX, newY) -> curX to newY
            else -> {
                if (abs(dxForFacing) > 0.001f) _state.update { it.copy(isPlayerFacingRight = dxForFacing > 0) }
                return
            }
        }

        val facing = if (abs(dxForFacing) > 0.001f) dxForFacing > 0 else _state.value.isPlayerFacingRight
        val action = if (_state.value.playerAction == PlayerAction.SPECIAL) PlayerAction.SPECIAL
        else if (_state.value.isRunning) PlayerAction.RUN else PlayerAction.WALK

        idleJob?.cancel()
        _state.update { it.copy(playerX = fx, playerY = fy, playerAction = action, isPlayerFacingRight = facing) }
        idleJob = viewModelScope.launch {
            delay(150)
            if (_state.value.playerAction != PlayerAction.SPECIAL) {
                _state.update { it.copy(playerAction = PlayerAction.IDLE) }
            }
        }
        updateDoorPrompt(fx, fy)
    }

    private fun updateDoorPrompt(px: Float, py: Float) {
        val room = currentRoom()
        val door = room.doors.firstOrNull {
            it.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight).contains(px, py)
        }
        val label = door?.let { "${it.label}  (X)" }
        if (_state.value.nearbyDoorLabel != label) {
            _state.update { it.copy(nearbyDoorLabel = label) }
        }
    }

    // ─── BOTONES DE ESTADO ─────────────────────────────────
    fun setRunning(running: Boolean) {
        _state.update {
            val action = when {
                it.playerAction == PlayerAction.SPECIAL -> PlayerAction.SPECIAL
                running && it.playerAction == PlayerAction.WALK -> PlayerAction.RUN
                !running && it.playerAction == PlayerAction.RUN -> PlayerAction.WALK
                else -> it.playerAction
            }
            it.copy(isRunning = running, playerAction = action)
        }
    }

    fun setSpecial(pressed: Boolean) {
        if (pressed) {
            _state.update { it.copy(playerAction = PlayerAction.SPECIAL) }
            idleJob?.cancel()
            performPlayerAttack()
        } else {
            _state.update { it.copy(playerAction = PlayerAction.IDLE) }
        }
    }

    fun onSecondaryAction() { /* slot Y reservado: futura mecánica */ }

    fun consumeExit() { gameLoopJob?.cancel() }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        idleJob?.cancel()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ZombieGameViewModel(SettingsRepository(context.applicationContext)) as T
    }
}