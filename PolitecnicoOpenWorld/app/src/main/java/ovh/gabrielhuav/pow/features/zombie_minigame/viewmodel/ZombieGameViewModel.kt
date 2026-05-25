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
import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
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
    private var exitGuideJob: Job? = null

    private companion object {
        const val PLAYER_WALK_STEP = 7f
        const val PLAYER_RUN_STEP = 13f
        const val PLAYER_RADIUS = 28f

        const val ZOMBIE_SPEED = 1.3f
        const val ZOMBIE_FRAME_COUNT = 9
        const val ZOMBIE_FRAME_INTERVAL_MS = 140L
        const val ZOMBIE_RADIUS = 30f

        const val CONTACT_DIST = 56f
        const val ZOMBIE_DAMAGE = 12f
        const val ZOMBIE_DAMAGE_COOLDOWN_MS = 3000L

        const val PLAYER_PUNCH_DAMAGE = 34f
        const val PLAYER_ATTACK_RADIUS = 120f
        const val PLAYER_ATTACK_COOLDOWN_MS = 600L

        const val PROJECTILE_SPEED = 22f
        const val PROJECTILE_LIFETIME_MS = 1500L
        const val PROJECTILE_DAMAGE = 50f
        const val PROJECTILE_HIT_RADIUS = 36f
        const val RANGED_COOLDOWN_MS = 350L
        const val Y_HOLD_FOR_MENU_MS = 500L

        const val SPAWN_RADIUS_MIN = 280f
        const val SPAWN_RADIUS_MAX = 520f

        const val TICK_MS = 33L
        const val ITEM_PICKUP_DIST = 70f
        const val RETURN_SPAWN_OFFSET = 40f

        const val EXIT_GUIDE_DURATION_MS = 2000L  // requerimiento 5

        // Probabilidad de que un zombi suelte un SkillItem al morir
        const val SKILL_DROP_CHANCE = 0.45f

        // ─── Modificadores de efectos ──────────────────────
        const val SLOW_ZOMBIE_FACTOR = 0.45f   // Reloj de Arena
        const val FAST_ZOMBIE_FACTOR = 1.9f    // Adrenalina (trampa)
        const val ZOMBIE_DMG_FURY_FACTOR = 2.0f   // Furia (trampa)
        const val ZOMBIE_DMG_WEAK_FACTOR = 0.4f   // Debilidad
        const val PLAYER_DMG_BRUTE_FACTOR = 2.2f  // Fuerza Bruta
    }

    private var lastPlayerAttackMs = 0L
    private var lastRangedShotMs = 0L
    private var yPressStartMs = 0L
    private var lastRoomId: String? = null

    init {
        loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
        startGameLoop()
    }

    // ─── ACCESO ────────────────────────────────────────────
    private fun currentRoom(): ZombieRoom = ZombieRoomCatalog.rooms[_state.value.currentRoomIndex]

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

    private fun spawnAtLobbyDoorFor(fromBuildingId: String): Pair<Float, Float>? {
        val lobby = ZombieRoomCatalog.roomById(ZombieRoomCatalog.LOBBY_ID) ?: return null
        val door = lobby.doors.firstOrNull { it.targetRoomId == fromBuildingId } ?: return null
        val hb = door.hitboxFrac.toWorldRect(lobby.worldWidth, lobby.worldHeight)

        val cx = hb.centerX()
        val cy = hb.centerY()
        val mapCx = lobby.worldWidth / 2f
        val mapCy = lobby.worldHeight / 2f
        val dirX = if (mapCx >= cx) 1f else -1f
        val dirY = if (mapCy >= cy) 1f else -1f

        val sx = (cx + dirX * RETURN_SPAWN_OFFSET).coerceIn(RETURN_SPAWN_OFFSET, lobby.worldWidth - RETURN_SPAWN_OFFSET)
        val sy = (cy + dirY * RETURN_SPAWN_OFFSET).coerceIn(RETURN_SPAWN_OFFSET, lobby.worldHeight - RETURN_SPAWN_OFFSET)
        return sx to sy
    }

    // ─── EFECTOS ACTIVOS: getters ──────────────────────────
    private fun hasEffect(e: SkillEffect): Boolean =
        _state.value.activeEffects.any { it.effect == e }

    private fun zombieSpeedFactor(): Float {
        var f = 1f
        if (hasEffect(SkillEffect.RELOJ_ARENA)) f *= SLOW_ZOMBIE_FACTOR
        if (hasEffect(SkillEffect.ADRENALINA_ZOMBI)) f *= FAST_ZOMBIE_FACTOR
        return f
    }

    private fun zombieDamageFactor(): Float {
        var f = 1f
        if (hasEffect(SkillEffect.FURIA_ZOMBI)) f *= ZOMBIE_DMG_FURY_FACTOR
        if (hasEffect(SkillEffect.DEBILIDAD_ZOMBI)) f *= ZOMBIE_DMG_WEAK_FACTOR
        return f
    }

    private fun playerDamageFactor(): Float =
        if (hasEffect(SkillEffect.FUERZA_BRUTA)) PLAYER_DMG_BRUTE_FACTOR else 1f

    // ─── CARGA DE ZONA ─────────────────────────────────────
    private fun loadRoom(index: Int) {
        val room = ZombieRoomCatalog.rooms[index]
        val now = System.currentTimeMillis()

        val pendingX = _state.value.pendingSpawnX
        val pendingY = _state.value.pendingSpawnY
        val spawnX = pendingX ?: (room.playerSpawnFrac.x * room.worldWidth)
        val spawnY = pendingY ?: (room.playerSpawnFrac.y * room.worldHeight)

        val zombies = if (room.type == ZoneType.BUILDING && room.zombieCount > 0) {
            val lootIndex = Random.nextInt(room.zombieCount)
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
                pendingSpawnX = null,
                pendingSpawnY = null,
                zombies = zombies,
                items = emptyList(),
                projectiles = emptyList(),
                totalZombies = zombies.size,
                zombiesRemaining = zombies.size,
                nearbyDoorLabel = null,
                nearbyItemId = null,
                showVictoryScreen = false,
                // Limpiamos efectos al cambiar de zona
                activeEffects = emptyList(),
                // Requerimiento 5: solo mostramos guía en edificios (donde hay EXIT)
                showExitGuide = room.type == ZoneType.BUILDING
            )
        }

        // Requerimiento 5: ocultar la línea punteada 2 segundos después de spawnear.
        exitGuideJob?.cancel()
        if (room.type == ZoneType.BUILDING) {
            exitGuideJob = viewModelScope.launch {
                delay(EXIT_GUIDE_DURATION_MS)
                _state.update { it.copy(showExitGuide = false) }
            }
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

        val fromRoom = currentRoom()
        val idx = ZombieRoomCatalog.indexOfRoom(targetRoomId)
        if (idx < 0) return
        val targetRoom = ZombieRoomCatalog.rooms[idx]

        if (targetRoom.type == ZoneType.LOBBY && fromRoom.type == ZoneType.BUILDING) {
            spawnAtLobbyDoorFor(fromRoom.id)?.let { (sx, sy) ->
                _state.update { it.copy(pendingSpawnX = sx, pendingSpawnY = sy) }
            }
        }

        lastRoomId = fromRoom.id
        loadRoom(idx)
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
        if (s.showVictoryScreen || s.showWastedScreen || s.isExitingToWorld || s.showExitToLobbyDialog) return
        val now = System.currentTimeMillis()
        val room = ZombieRoomCatalog.rooms[s.currentRoomIndex]
        val blockers = room.collisionGridFrac.map { it.toWorldRect(room.worldWidth, room.worldHeight) }

        // 0. Expirar efectos vencidos (requerimiento 2: revertir modificadores)
        val stillActive = s.activeEffects.filter { it.expiresAtMs > now }
        val effectsChanged = stillActive.size != s.activeEffects.size

        val speedFactor = run {
            var f = 1f
            if (stillActive.any { it.effect == SkillEffect.RELOJ_ARENA }) f *= SLOW_ZOMBIE_FACTOR
            if (stillActive.any { it.effect == SkillEffect.ADRENALINA_ZOMBI }) f *= FAST_ZOMBIE_FACTOR
            f
        }
        val dmgFactor = run {
            var f = 1f
            if (stillActive.any { it.effect == SkillEffect.FURIA_ZOMBI }) f *= ZOMBIE_DMG_FURY_FACTOR
            if (stillActive.any { it.effect == SkillEffect.DEBILIDAD_ZOMBI }) f *= ZOMBIE_DMG_WEAK_FACTOR
            f
        }

        var newHealth = s.playerHealth
        var pulse = s.damagePulseTrigger

        // 1. Mover zombis + daño de contacto
        var workingZombies = s.zombies.map { z ->
            if (z.isDying) return@map z
            val moved = moveZombie(z, s.playerX, s.playerY, now, room, blockers, speedFactor)
            val dist = hypot(moved.x - s.playerX, moved.y - s.playerY)
            if (dist <= CONTACT_DIST && now - moved.lastDamageToPlayerMs >= ZOMBIE_DAMAGE_COOLDOWN_MS) {
                newHealth -= ZOMBIE_DAMAGE * dmgFactor
                pulse += 1
                moved.copy(lastDamageToPlayerMs = now)
            } else moved
        }

        // 2. Proyectiles
        val deadZombieIds = mutableListOf<String>()
        val survivingProjectiles = mutableListOf<Projectile>()
        for (p in s.projectiles) {
            if (now - p.bornAtMs > PROJECTILE_LIFETIME_MS) continue
            val nx = p.x + p.dirX * PROJECTILE_SPEED
            val ny = p.y + p.dirY * PROJECTILE_SPEED
            if (nx < 0f || ny < 0f || nx > room.worldWidth || ny > room.worldHeight) continue

            val hit = workingZombies.firstOrNull {
                !it.isDying && hypot(it.x - nx, it.y - ny) <= PROJECTILE_HIT_RADIUS
            }
            if (hit != null) {
                val newHp = hit.health - PROJECTILE_DAMAGE * playerDamageFactor()
                workingZombies = workingZombies.map { z ->
                    if (z.id == hit.id) {
                        if (newHp <= 0f) { deadZombieIds.add(z.id); z.copy(health = 0f, isDying = true) }
                        else z.copy(health = newHp)
                    } else z
                }
            } else {
                survivingProjectiles.add(p.copy(x = nx, y = ny))
            }
        }

        // 3. Muerte del jugador
        if (newHealth <= 0f) {
            triggerWastedSequence()
            return
        }

        val nearItem = s.items.firstOrNull {
            !it.collected && hypot(it.x - s.playerX, it.y - s.playerY) <= ITEM_PICKUP_DIST
        }

        _state.update {
            it.copy(
                zombies = workingZombies,
                projectiles = survivingProjectiles,
                playerHealth = newHealth.coerceIn(0f, 100f),
                damagePulseTrigger = pulse,
                zombiesRemaining = workingZombies.count { z -> !z.isDying },
                nearbyItemId = nearItem?.id,
                activeEffects = if (effectsChanged) stillActive else it.activeEffects
            )
        }

        deadZombieIds.forEach { id ->
            val deadZombie = workingZombies.firstOrNull { it.id == id }
            if (deadZombie != null) {
                viewModelScope.launch {
                    delay(1000L)
                    onZombieDeath(deadZombie)
                }
            }
        }
    }

    private fun moveZombie(
        z: ZombieEntity, px: Float, py: Float, now: Long,
        room: ZombieRoom, blockers: List<WorldRect>, speedFactor: Float
    ): ZombieEntity {
        val dx = px - z.x
        val dy = py - z.y
        val dist = hypot(dx, dy)
        val (nx, ny) = if (dist > 0.01f) (dx / dist) to (dy / dist) else 0f to 0f
        val step = if (dist > CONTACT_DIST * 0.7f) ZOMBIE_SPEED * speedFactor else 0f

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

    // ─── COMBATE CUERPO A CUERPO ───────────────────────────
    fun performPlayerAttack() {
        val now = System.currentTimeMillis()
        if (now - lastPlayerAttackMs < PLAYER_ATTACK_COOLDOWN_MS) return
        lastPlayerAttackMs = now

        val s = _state.value
        val target = s.zombies
            .filter { !it.isDying && hypot(it.x - s.playerX, it.y - s.playerY) <= PLAYER_ATTACK_RADIUS }
            .minByOrNull { hypot(it.x - s.playerX, it.y - s.playerY) } ?: return

        val newHealth = target.health - PLAYER_PUNCH_DAMAGE * playerDamageFactor()
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

    // ─── COMBATE A DISTANCIA ───────────────────────────────
    private fun fireProjectile() {
        val now = System.currentTimeMillis()
        if (now - lastRangedShotMs < RANGED_COOLDOWN_MS) return
        lastRangedShotMs = now

        val s = _state.value
        var dx = s.aimDirX
        var dy = s.aimDirY
        if (dx == 0f && dy == 0f) { dx = if (s.isPlayerFacingRight) 1f else -1f; dy = 0f }

        val p = Projectile(x = s.playerX, y = s.playerY, dirX = dx, dirY = dy, bornAtMs = now)
        _state.update { it.copy(projectiles = it.projectiles + p, playerAction = PlayerAction.SPECIAL) }
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(150)
            _state.update { it.copy(playerAction = PlayerAction.IDLE) }
        }
    }

    private fun onZombieDeath(dead: ZombieEntity) {
        _state.update { cur ->
            val remaining = cur.zombies.filter { it.id != dead.id }
            // Requerimiento 2: los zombis sueltan SkillItem (no coleccionables del mapa).
            val newItems = if (dead.isLootCarrier || Random.nextFloat() < SKILL_DROP_CHANCE) {
                val effect = SkillEffect.entries.random()
                cur.items + SkillItem(x = dead.x, y = dead.y, effect = effect)
            } else cur.items

            val alive = remaining.count { !it.isDying }
            val room = ZombieRoomCatalog.rooms[cur.currentRoomIndex]
            val won = room.type == ZoneType.BUILDING && alive == 0 && cur.totalZombies > 0
            cur.copy(zombies = remaining, items = newItems, zombiesRemaining = alive, showVictoryScreen = won)
        }

        if (_state.value.showVictoryScreen) {
            viewModelScope.launch {
                delay(3000L)
                _state.update { it.copy(showVictoryScreen = false) }
            }
        }
    }

    // ─── APLICAR UN EFECTO RECOGIDO ────────────────────────
    private fun applyEffect(effect: SkillEffect) {
        when (effect) {
            SkillEffect.CURA_TOTAL -> {
                _state.update { it.copy(playerHealth = 100f) }
            }
            else -> {
                val now = System.currentTimeMillis()
                _state.update { cur ->
                    // Reemplazamos el mismo efecto si ya estaba activo (refresca duración)
                    val withoutSame = cur.activeEffects.filter { it.effect != effect }
                    cur.copy(activeEffects = withoutSame + ActiveEffect(effect, now + effect.durationMs))
                }
            }
        }
        // Toast informativo
        _state.update { it.copy(effectToast = effect.displayName) }
        viewModelScope.launch {
            delay(2000L)
            _state.update { it.copy(effectToast = null) }
        }
    }

    // ─── SECUENCIA WASTED ──────────────────────────────────
    private fun triggerWastedSequence() {
        if (_state.value.showWastedScreen) return

        val diedInRoom = currentRoom()
        _state.update { it.copy(showWastedScreen = true, playerHealth = 0f, activeEffects = emptyList()) }

        viewModelScope.launch {
            delay(4000L)
            if (diedInRoom.type == ZoneType.LOBBY) {
                _state.update { it.copy(showWastedScreen = false, playerHealth = 100f) }
                return@launch
            }
            spawnAtLobbyDoorFor(diedInRoom.id)?.let { (sx, sy) ->
                _state.update { it.copy(pendingSpawnX = sx, pendingSpawnY = sy) }
            }
            lastRoomId = diedInRoom.id
            _state.update { it.copy(showWastedScreen = false, playerHealth = 100f) }
            loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
        }
    }

    // ─── INTERACCIÓN (botón X) ─────────────────────────────
    fun onInteract() {
        val s = _state.value
        // 1. Recoger SkillItem cercano
        val itemId = s.nearbyItemId
        if (itemId != null) {
            val item = s.items.firstOrNull { it.id == itemId } ?: return
            _state.update { cur ->
                cur.copy(items = cur.items.filter { it.id != itemId }, nearbyItemId = null)
            }
            applyEffect(item.effect)
            return
        }
        // 2. Puertas
        val room = currentRoom()
        val door = room.doors.firstOrNull {
            it.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                .contains(s.playerX, s.playerY)
        } ?: return

        // ── REQUERIMIENTO 1: si la puerta lleva al lobby desde un edificio,
        //    pedimos confirmación en vez de transicionar directo. ──
        if (door.targetRoomId == ZombieRoomCatalog.LOBBY_ID && room.type == ZoneType.BUILDING) {
            _state.update { it.copy(showExitToLobbyDialog = true) }
            return
        }
        goToRoom(door.targetRoomId)
    }

    // ── Confirmación de salida al lobby ──
    fun confirmExitToLobby() {
        _state.update { it.copy(showExitToLobbyDialog = false) }
        goToRoom(ZombieRoomCatalog.LOBBY_ID)
    }

    fun dismissExitToLobby() {
        _state.update { it.copy(showExitToLobbyDialog = false) }
    }

    // ─── MOVIMIENTO ────────────────────────────────────────
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

        val mdx = fx - curX
        val mdy = fy - curY
        val mdist = hypot(mdx, mdy)
        val (adx, ady) = if (mdist > 0.001f) (mdx / mdist) to (mdy / mdist)
        else (_state.value.aimDirX to _state.value.aimDirY)

        idleJob?.cancel()
        _state.update { it.copy(playerX = fx, playerY = fy, playerAction = action, isPlayerFacingRight = facing, aimDirX = adx, aimDirY = ady) }
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
        if (!pressed) {
            if (_state.value.combatMode == CombatMode.MELEE) {
                _state.update { it.copy(playerAction = PlayerAction.IDLE) }
            }
            return
        }
        when (_state.value.combatMode) {
            CombatMode.MELEE -> {
                _state.update { it.copy(playerAction = PlayerAction.SPECIAL) }
                idleJob?.cancel()
                performPlayerAttack()
            }
            CombatMode.RANGED -> fireProjectile()
        }
    }

    fun onSecondaryPressed() { yPressStartMs = System.currentTimeMillis() }

    fun onSecondaryReleased() {
        val held = System.currentTimeMillis() - yPressStartMs
        if (held >= Y_HOLD_FOR_MENU_MS) {
            _state.update { it.copy(showWeaponMenu = !it.showWeaponMenu) }
        }
    }

    fun selectCombatMode(mode: CombatMode) {
        _state.update { it.copy(combatMode = mode, showWeaponMenu = false) }
    }

    fun dismissWeaponMenu() {
        _state.update { it.copy(showWeaponMenu = false) }
    }

    fun consumeExit() { gameLoopJob?.cancel() }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        idleJob?.cancel()
        exitGuideJob?.cancel()
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ZombieGameViewModel(SettingsRepository(context.applicationContext)) as T
    }
}