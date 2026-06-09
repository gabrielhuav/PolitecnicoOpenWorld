package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.CollisionMatrixRepository
import ovh.gabrielhuav.pow.data.repository.WaypointRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.zombie.ActiveEffect
import ovh.gabrielhuav.pow.domain.models.zombie.CollisionMatrix
import ovh.gabrielhuav.pow.domain.models.zombie.CombatMode
import ovh.gabrielhuav.pow.domain.models.zombie.Projectile
import ovh.gabrielhuav.pow.domain.models.zombie.SkillEffect
import ovh.gabrielhuav.pow.domain.models.zombie.SkillItem
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieEntity
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieType
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class ZombieGameViewModel(
    internal val applicationContext: Context,
    internal val settingsRepository: SettingsRepository,
    // URL del servidor de zombis. null = partida offline (un jugador).
    internal val serverUrl: String?,
    internal val playerName: String
) : ViewModel() {

    internal val _state = MutableStateFlow(
        ZombieGameState(
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls(),
            isLoading = false
        )
    )
    val state: StateFlow<ZombieGameState> = _state.asStateFlow()

    internal var gameLoopJob: Job? = null
    internal var idleJob: Job? = null
    internal var exitGuideJob: Job? = null

    // ─── Red multijugador ──────────────────────────────────
    internal val gson = Gson()
    internal var wsManager: WebSocketManager? = null
    internal var wsCollectorJob: Job? = null
    internal var mySessionId: String = "ZPlayer_${UUID.randomUUID()}"
    internal val remotePlayers = ConcurrentHashMap<String, RemoteZombiePlayer>()
    internal var lastNetSendMs = 0L
    internal val isMultiplayer: Boolean get() = serverUrl != null

    // Cooldown de daño por contacto en online (los zombis se reemplazan en cada
    // broadcast del servidor, así que el cooldown no puede vivir en la entidad).
    internal val contactCooldown = ConcurrentHashMap<String, Long>()


    internal var lastPlayerAttackMs = 0L
    internal var lastRangedShotMs = 0L
    internal var yPressStartMs = 0L
    internal var lastRoomId: String? = null

    init {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ZombieRoomCatalog.init(applicationContext)
                    // Aplicar matrices guardadas (Modo Diseñador) sobre el catálogo.
                    CollisionMatrixRepository.loadAll(applicationContext).forEach { (roomId, rows) ->
                        if (rows.isNotEmpty()) {
                            ZombieRoomCatalog.roomById(roomId)?.collisionMatrix = CollisionMatrix(rows)
                        }
                    }
                    // Aplicar waypoints (puertas) guardados / de fábrica sobre el catálogo.
                    WaypointRepository.loadAll(applicationContext).forEach { (roomId, doors) ->
                        if (doors.isNotEmpty()) {
                            ZombieRoomCatalog.roomById(roomId)?.doors = doors
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("ZombieGameVM", "Advertencia: Falló la inicialización del catálogo. Se usarán tamaños por defecto.", e)
            } finally {
                if (isActive) {
                    _state.update { it.copy(isLoading = false) }
                    loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
                    startGameLoop()
                    connectIfNeeded()
                }
            }
        }
    }

    // ─── CONEXIÓN MULTIJUGADOR ─────────────────────────────
    internal fun connectIfNeeded() {
        val url = serverUrl ?: return
        wsManager = WebSocketManager(url)
        wsCollectorJob = viewModelScope.launch(Dispatchers.IO) {
            wsManager?.messagesFlow?.collect { handleServerMessage(it) }
        }
        wsManager?.connect()
        viewModelScope.launch {
            delay(600)
            sendJoinRoom()
        }
    }

    internal fun sendJoinRoom() {
        if (!isMultiplayer) return
        val room = currentRoom()
        wsManager?.sendMessage(
            gson.toJson(
                mapOf(
                    "type" to "JOIN_ROOM",
                    "roomId" to room.id,
                    "displayName" to playerName,
                    // FRACCIONES [0,1]
                    "x" to (_state.value.playerX / room.worldWidth),
                    "y" to (_state.value.playerY / room.worldHeight)
                )
            )
        )
    }

    internal fun handleServerMessage(json: String) {
        try {
            val msg = gson.fromJson(json, ZombieServerMessage::class.java)
            when (msg.type) {
                "SESSION_INIT" -> msg.sessionId?.let { mySessionId = it }

                "ROOM_SNAPSHOT" -> {
                    remotePlayers.clear()
                    msg.players?.forEach { upsertRemote(it) }
                    pushRemotePlayersToState()
                }

                "PLAYER_UPDATE" -> {
                    if (msg.id != null && msg.id != mySessionId) {
                        upsertRemote(msg)
                        pushRemotePlayersToState()
                    }
                }

                "PLAYER_LEFT_ROOM" -> {
                    msg.id?.let { remotePlayers.remove(it) }
                    pushRemotePlayersToState()
                }

                // COORDS MANEJADAS POR EL SERVIDOR: el servidor rechazó una posición dentro de
                // pared; ajustamos al jugador local a la posición válida (fracción → píxeles).
                "PLAYER_CORRECT" -> {
                    val mx = msg.x; val my = msg.y
                    if (mx != null && my != null) {
                        val room = currentRoom()
                        _state.update { it.copy(playerX = mx * room.worldWidth, playerY = my * room.worldHeight) }
                    }
                }

                // ─── Fase 1: zombis autoritativos ───
                "ZOMBIE_STATE" -> {
                    if (msg.roomId == null || msg.roomId == currentRoom().id) applyServerZombieState(msg)
                }
                "ROOM_CLEARED" -> {
                    if (msg.roomId == null || msg.roomId == currentRoom().id) showVictory()
                }
                "ITEM_GRANTED" -> {
                    msg.effect?.let { applyEffectByName(it) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZombieNet", "Error parseando mensaje: ${e.message}")
        }
    }

    // Convierte FRACCIÓN [0,1] → píxeles de la sala actual.
    internal fun upsertRemote(m: ZombieServerMessage) {
        val id = m.id ?: return
        if (id == mySessionId) return
        val room = currentRoom()
        remotePlayers[id] = RemoteZombiePlayer(
            id = id,
            displayName = m.displayName ?: "",
            x = (m.x ?: 0f) * room.worldWidth,
            y = (m.y ?: 0f) * room.worldHeight,
            action = runCatching { PlayerAction.valueOf(m.action ?: "IDLE") }.getOrDefault(PlayerAction.IDLE),
            facingRight = m.facingRight ?: true,
            health = m.health ?: 100f
        )
    }

    internal fun pushRemotePlayersToState() {
        _state.update { it.copy(remotePlayers = remotePlayers.values.toList()) }
    }

    internal fun sendPlayerUpdate(now: Long) {
        if (!isMultiplayer) return
        if (now - lastNetSendMs < NET_SEND_INTERVAL_MS) return
        lastNetSendMs = now
        val s = _state.value
        val room = currentRoom()
        wsManager?.sendMessage(
            gson.toJson(
                mapOf(
                    "type" to "PLAYER_UPDATE",
                    "displayName" to playerName,
                    "x" to (s.playerX / room.worldWidth),
                    "y" to (s.playerY / room.worldHeight),
                    "action" to s.playerAction.name,
                    "facingRight" to s.isPlayerFacingRight,
                    "health" to s.playerHealth
                )
            )
        )
    }

    internal fun sendZombieDamage(zombieId: String, damage: Float) {
        if (!isMultiplayer) return
        wsManager?.sendMessage(
            gson.toJson(mapOf("type" to "ZOMBIE_DAMAGE", "zombieId" to zombieId, "damage" to damage))
        )
    }

    internal fun sendItemPickup(itemId: String) {
        if (!isMultiplayer) return
        wsManager?.sendMessage(gson.toJson(mapOf("type" to "ITEM_PICKUP", "itemId" to itemId)))
    }

    // Reemplaza zombis e items locales con el estado autoritativo del servidor.
    // Convierte fracción [0,1] → píxeles con las dimensiones de la sala actual.
    internal fun applyServerZombieState(msg: ZombieServerMessage) {
        val room = currentRoom()
        val w = room.worldWidth; val h = room.worldHeight
        val zs = msg.zombies?.map { nz ->
            ZombieEntity(
                id = nz.id, x = nz.x * w, y = nz.y * h,
                health = nz.health, maxHealth = nz.maxHealth,
                facingRight = nz.facingRight, frameIndex = nz.frameIndex,
                isDying = nz.isDying, isLootCarrier = nz.isLootCarrier,
                type = ZombieType.NORMAL
            )
        } ?: emptyList()
        val its = msg.items?.map { ni ->
            SkillItem(id = ni.id, x = ni.x * w, y = ni.y * h, effect = effectFromName(ni.effect))
        } ?: emptyList()
        // NPCs civiles (autoritativos): se renderizan como figuras humanas (RemotePlayerView).
        val civs = msg.npcs?.map { nn ->
            ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel.RemoteZombiePlayer(
                id = nn.id, displayName = "",
                x = nn.x * w, y = nn.y * h,
                action = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction.WALK,
                facingRight = nn.facingRight, health = 100f
            )
        } ?: emptyList()
        _state.update {
            it.copy(
                zombies = zs,
                items = its,
                interiorNpcs = civs,
                totalZombies = msg.totalZombies ?: it.totalZombies,
                zombiesRemaining = zs.count { z -> !z.isDying }
            )
        }
    }

    internal fun effectFromName(name: String?): SkillEffect =
        runCatching { SkillEffect.valueOf(name ?: "") }.getOrDefault(SkillEffect.CURA_TOTAL)

    internal fun applyEffectByName(name: String) = applyEffect(effectFromName(name))

    internal fun showVictory() {
        if (currentRoom().type != ZoneType.BUILDING) return
        if (_state.value.showVictoryScreen) return
        _state.update { it.copy(showVictoryScreen = true) }
        viewModelScope.launch {
            delay(3000L)
            _state.update { it.copy(showVictoryScreen = false) }
        }
    }

    // ─── ACCESO ────────────────────────────────────────────
    internal fun currentRoom(): ZombieRoom = ZombieRoomCatalog.rooms[_state.value.currentRoomIndex]

    internal fun isWalkable(x: Float, y: Float): Boolean {
        val r = currentRoom()
        if (x < PLAYER_RADIUS || y < PLAYER_RADIUS ||
            x > r.worldWidth - PLAYER_RADIUS || y > r.worldHeight - PLAYER_RADIUS) return false
        return !r.isBlockedPixel(x, y)
    }

    internal fun spawnAtLobbyDoorFor(fromBuildingId: String): Pair<Float, Float>? {
        val lobby = ZombieRoomCatalog.roomById(ZombieRoomCatalog.LOBBY_ID) ?: return null
        val door = lobby.doors.firstOrNull { it.targetRoomId == fromBuildingId } ?: return null
        val hb = door.hitboxFrac.toWorldRect(lobby.worldWidth, lobby.worldHeight)

        val cx = hb.centerX(); val cy = hb.centerY()
        val mapCx = lobby.worldWidth / 2f; val mapCy = lobby.worldHeight / 2f
        val dirX = if (mapCx >= cx) 1f else -1f
        val dirY = if (mapCy >= cy) 1f else -1f

        val sx = (cx + dirX * RETURN_SPAWN_OFFSET).coerceIn(RETURN_SPAWN_OFFSET, lobby.worldWidth - RETURN_SPAWN_OFFSET)
        val sy = (cy + dirY * RETURN_SPAWN_OFFSET).coerceIn(RETURN_SPAWN_OFFSET, lobby.worldHeight - RETURN_SPAWN_OFFSET)
        return sx to sy
    }

    // ─── EFECTOS ACTIVOS: getters ──────────────────────────
    internal fun hasEffect(e: SkillEffect): Boolean =
        _state.value.activeEffects.any { it.effect == e }

    internal fun playerDamageFactor(): Float =
        if (hasEffect(SkillEffect.FUERZA_BRUTA)) PLAYER_DMG_BRUTE_FACTOR else 1f

    // ─── CARGA DE ZONA ─────────────────────────────────────
    internal fun loadRoom(index: Int) {
        val room = ZombieRoomCatalog.rooms[index]
        val now = System.currentTimeMillis()

        val pendingX = _state.value.pendingSpawnX
        val pendingY = _state.value.pendingSpawnY
        val spawnX = pendingX ?: (room.playerSpawnFrac.x * room.worldWidth)
        val spawnY = pendingY ?: (room.playerSpawnFrac.y * room.worldHeight)

        val hasWeapon = _state.value.combatMode == CombatMode.RANGED

        // En ONLINE los zombis los crea el servidor (llegan por ZOMBIE_STATE).
        val isZombieEligible = room.type == ZoneType.BUILDING ||
                (room.type == ZoneType.LOBBY && _state.value.zombieModeActivated)
        val effectiveZombieCount = if (room.type == ZoneType.LOBBY) 5 else room.zombieCount
        val zombies = if (!isMultiplayer && isZombieEligible && effectiveZombieCount > 0 && _state.value.zombieModeActivated) {
            val lootIndex = Random.nextInt(effectiveZombieCount)
            (0 until effectiveZombieCount).map { i ->
                val (zx, zy) = spawnAroundPlayer(spawnX, spawnY, room)
                val type = if (hasWeapon && Random.nextFloat() < 0.4f) ZombieType.STALKER else ZombieType.NORMAL
                ZombieEntity(
                    x = zx, y = zy,
                    lastFrameAdvanceMs = now,
                    isLootCarrier = (i == lootIndex),
                    type = type
                )
            }
        } else emptyList()

        contactCooldown.clear()

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
                activeEffects = emptyList(),
                showExitGuide = room.type == ZoneType.BUILDING,
                // Al cambiar de sala, salir del modo diseñador para evitar confusión.
                designerMode = false,
                designerDirty = false,
                designerRows = emptyList(),
                designerDoors = emptyList(),
                selectedDoorIndex = -1
            )
        }

        exitGuideJob?.cancel()
        if (room.type == ZoneType.BUILDING) {
            exitGuideJob = viewModelScope.launch {
                delay(EXIT_GUIDE_DURATION_MS)
                _state.update { it.copy(showExitGuide = false) }
            }
        }

        if (isMultiplayer) {
            remotePlayers.clear()
            pushRemotePlayersToState()
            sendJoinRoom()
        }
    }

    internal fun spawnAroundPlayer(px: Float, py: Float, room: ZombieRoom): Pair<Float, Float> {
        repeat(20) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val radius = SPAWN_RADIUS_MIN + Random.nextFloat() * (SPAWN_RADIUS_MAX - SPAWN_RADIUS_MIN)
            val x = (px + cos(angle) * radius).coerceIn(ZOMBIE_RADIUS, room.worldWidth - ZOMBIE_RADIUS)
            val y = (py + sin(angle) * radius).coerceIn(ZOMBIE_RADIUS, room.worldHeight - ZOMBIE_RADIUS)
            if (!room.isBlockedPixel(x, y)) return x to y
        }
        return (px + SPAWN_RADIUS_MIN) to py
    }

    /** Salir del minijuego al mapa abierto (equivale a cruzar la puerta TO_WORLD). */
    fun exitToWorld() = goToRoom(ZombieRoomCatalog.EXIT_TO_WORLD)

    internal fun goToRoom(targetRoomId: String) {
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
    internal fun startGameLoop() {
        if (gameLoopJob?.isActive == true) return
        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                try { tick() } catch (_: Exception) {}
                delay(TICK_MS)
            }
        }
    }


    // ─── OFFLINE: simulación local completa ────────────────

    // ─── ONLINE: zombis del servidor; aquí solo proyectiles/contacto/items ─────

    // Movimiento de zombi con colisión por matriz (deslizamiento por eje).

    // Empuja un zombi alejándolo de (fromX,fromY), respetando colisiones (slide por eje).

    // ─── COMBATE CUERPO A CUERPO ───────────────────────────
    fun performPlayerAttack() {
        val now = System.currentTimeMillis()
        if (now - lastPlayerAttackMs < PLAYER_ATTACK_COOLDOWN_MS) return
        lastPlayerAttackMs = now

        val s = _state.value
        val target = s.zombies
            .filter { !it.isDying && hypot(it.x - s.playerX, it.y - s.playerY) <= PLAYER_ATTACK_RADIUS }
            .minByOrNull { hypot(it.x - s.playerX, it.y - s.playerY) } ?: return

        if (isMultiplayer) {
            sendZombieDamage(target.id, PLAYER_PUNCH_DAMAGE * playerDamageFactor())
            return
        }

        val room = currentRoom()
        val (kx, ky) = knockbackZombie(target.x, target.y, s.playerX, s.playerY, room, MELEE_KNOCKBACK)
        val newHealth = target.health - PLAYER_PUNCH_DAMAGE * playerDamageFactor()
        if (newHealth <= 0f) {
            _state.update { cur ->
                cur.copy(zombies = cur.zombies.map {
                    if (it.id == target.id) it.copy(health = 0f, isDying = true, x = kx, y = ky) else it
                })
            }
            viewModelScope.launch { delay(1000L); onZombieDeath(target) }
        } else {
            _state.update { cur ->
                cur.copy(zombies = cur.zombies.map {
                    if (it.id == target.id) it.copy(health = newHealth, x = kx, y = ky) else it
                })
            }
        }
    }

    // ─── COMBATE A DISTANCIA ───────────────────────────────
    internal fun fireProjectile() {
        if (_state.value.showWastedScreen) return // muerto: no dispara
        val now = System.currentTimeMillis()
        if (now - lastRangedShotMs < RANGED_COOLDOWN_MS) return
        lastRangedShotMs = now

        val s = _state.value
        var dx = s.aimDirX
        var dy = s.aimDirY
        if (dx == 0f && dy == 0f) { dx = if (s.isPlayerFacingRight) 1f else -1f; dy = 0f }

        val p = Projectile(x = s.playerX, y = s.playerY, dirX = dx, dirY = dy, bornAtMs = now)

        // Recoil: empujar al jugador hacia atrás (opuesto a la mira), con corrección
        // de posición por eje para no atravesar colisiones.
        val rbX = s.playerX - dx * PLAYER_RECOIL
        val rbY = s.playerY - dy * PLAYER_RECOIL
        val (recoilX, recoilY) = when {
            isWalkable(rbX, rbY) -> rbX to rbY
            isWalkable(rbX, s.playerY) -> rbX to s.playerY
            isWalkable(s.playerX, rbY) -> s.playerX to rbY
            else -> s.playerX to s.playerY
        }

        _state.update {
            it.copy(
                projectiles = it.projectiles + p,
                playerAction = PlayerAction.SPECIAL,
                playerX = recoilX,
                playerY = recoilY
            )
        }
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(150)
            _state.update { it.copy(playerAction = PlayerAction.IDLE) }
        }
    }

    // OFFLINE: muerte + drop + victoria (online lo decide el servidor).
    internal fun onZombieDeath(dead: ZombieEntity) {
        _state.update { cur ->
            val remaining = cur.zombies.filter { it.id != dead.id }
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
    internal fun applyEffect(effect: SkillEffect) {
        when (effect) {
            SkillEffect.CURA_TOTAL -> {
                _state.update { it.copy(playerHealth = 100f) }
            }
            else -> {
                val now = System.currentTimeMillis()
                _state.update { cur ->
                    val withoutSame = cur.activeEffects.filter { it.effect != effect }
                    cur.copy(activeEffects = withoutSame + ActiveEffect(effect, now + effect.durationMs))
                }
            }
        }
        _state.update { it.copy(effectToast = effect.displayName) }
        viewModelScope.launch {
            delay(2000L)
            _state.update { it.copy(effectToast = null) }
        }
    }

    // ─── SECUENCIA WASTED ──────────────────────────────────
    internal fun triggerWastedSequence() {
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
            if (isMultiplayer) {
                sendItemPickup(itemId) // el servidor lo retira y responde ITEM_GRANTED
                return
            }
            val item = s.items.firstOrNull { it.id == itemId } ?: return
            _state.update { cur ->
                cur.copy(items = cur.items.filter { it.id != itemId }, nearbyItemId = null)
            }
            applyEffect(item.effect)
            return
        }
        // 2b. Mano zombi en lobby
        if (currentRoom().id == ZombieRoomCatalog.LOBBY_ID) {
            val handNx = 0.50f
            val handNy = 0.45f
            val room = currentRoom()
            val handWx = handNx * room.worldWidth
            val handWy = handNy * room.worldHeight
            val distToHand = hypot(s.playerX - handWx, s.playerY - handWy)
            if (distToHand < 80f && !s.zombieModeActivated) {
                _state.update { it.copy(showZombieCinematic = true) }
                return
            }
        }

        // 2. Puertas
        val room = currentRoom()
        val door = room.doors.firstOrNull {
            it.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                .contains(s.playerX, s.playerY)
        } ?: return

        if (door.targetRoomId == ZombieRoomCatalog.LOBBY_ID && room.type == ZoneType.BUILDING) {
            _state.update { it.copy(showExitToLobbyDialog = true) }
            return
        }
        goToRoom(door.targetRoomId)
    }

    fun confirmExitToLobby() {
        _state.update { it.copy(showExitToLobbyDialog = false) }
        goToRoom(ZombieRoomCatalog.LOBBY_ID)
    }

    fun dismissExitToLobby() {
        _state.update { it.copy(showExitToLobbyDialog = false) }
    }

    // ─── MOVIMIENTO ────────────────────────────────────────
    fun moveByAngle(angleRad: Double) {
        if (_state.value.designerMode) return
        val s = _state.value
        val step = if (s.isRunning) PLAYER_RUN_STEP else PLAYER_WALK_STEP
        applyMovement(
            s.playerX + cos(angleRad).toFloat() * step,
            s.playerY - sin(angleRad).toFloat() * step,
            cos(angleRad).toFloat()
        )
    }

    fun moveDirection(direction: Direction) {
        if (_state.value.designerMode) return
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

    internal fun applyMovement(newX: Float, newY: Float, dxForFacing: Float) {
        // MUERTE: durante la pantalla WASTED el jugador NO se mueve (queda como
        // "fantasmita" mientras corre la animación de muerte).
        if (_state.value.showWastedScreen) return
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

    internal fun updateDoorPrompt(px: Float, py: Float) {
        val room = currentRoom()
        val door = room.doors.firstOrNull {
            it.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight).contains(px, py)
        }
        val handLabel = if (currentRoom().id == ZombieRoomCatalog.LOBBY_ID && !_state.value.zombieModeActivated) {
            val room = currentRoom()
            val handWx = 0.50f * room.worldWidth
            val handWy = 0.45f * room.worldHeight
            if (hypot(px - handWx, py - handWy) < 80f) "Mano Misteriosa  (X)" else null
        } else null

        val label = handLabel ?: door?.let { "${it.label}  (X)" }
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
        if (_state.value.designerMode) return
        if (_state.value.showWastedScreen) return // muerto: no ataca
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

    fun onZombieCinematicDismissed() {
        _state.update { it.copy(showZombieCinematic = false, zombieModeActivated = true) }
        loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
    }

    // ─── MODO DISEÑADOR DE LA MATRIZ DE COLISIÓN ───────────
    fun toggleDesignerMode() {
        val s = _state.value
        if (!s.designerMode) {
            val room = currentRoom()
            val rows = room.collisionMatrix?.rows ?: defaultDesignerRows(room)
            _state.update {
                it.copy(
                    designerMode = true,
                    designerRows = rows,
                    designerDirty = false,
                    designerDoors = room.doors,
                    selectedDoorIndex = -1
                )
            }
        } else {
            _state.update { it.copy(designerMode = false) }
        }
    }

    /** Alterna entre editar la MATRIZ de colisión o los WAYPOINTS (puertas). */
    fun setDesignerTarget(target: DesignerTarget) {
        if (_state.value.designerTarget == target) return
        val room = currentRoom()
        _state.update {
            it.copy(
                designerTarget = target,
                designerDirty = false,
                // refrescar el dataset del objetivo recién seleccionado
                designerRows = if (target == DesignerTarget.MATRIX)
                    (room.collisionMatrix?.rows ?: defaultDesignerRows(room)) else it.designerRows,
                designerDoors = if (target == DesignerTarget.WAYPOINTS) room.doors else it.designerDoors,
                selectedDoorIndex = -1
            )
        }
    }

    fun setDesignerBrushWall(wall: Boolean) =
        _state.update { it.copy(designerBrushWall = wall) }

    // ─── EDICIÓN DE WAYPOINTS (puertas) ────────────────────
    /** Selecciona la puerta cuyo hitbox (fraccionario) contiene (fx,fy). */
    fun selectDoorAtWorld(xWorld: Float, yWorld: Float) {
        val room = currentRoom()
        if (room.worldWidth <= 0f || room.worldHeight <= 0f) return
        val fx = xWorld / room.worldWidth
        val fy = yWorld / room.worldHeight
        val doors = _state.value.designerDoors
        val idx = doors.indexOfFirst {
            fx in it.hitboxFrac.left..it.hitboxFrac.right &&
                fy in it.hitboxFrac.top..it.hitboxFrac.bottom
        }
        _state.update { it.copy(selectedDoorIndex = idx) }
    }

    /** Mueve la puerta seleccionada para que su CENTRO quede en (xWorld,yWorld). */
    fun moveSelectedDoorToWorld(xWorld: Float, yWorld: Float) {
        val s = _state.value
        val idx = s.selectedDoorIndex
        if (idx < 0 || idx >= s.designerDoors.size) return
        val room = currentRoom()
        if (room.worldWidth <= 0f || room.worldHeight <= 0f) return
        val fx = (xWorld / room.worldWidth)
        val fy = (yWorld / room.worldHeight)
        val door = s.designerDoors[idx]
        val halfW = (door.hitboxFrac.right - door.hitboxFrac.left) / 2f
        val halfH = (door.hitboxFrac.bottom - door.hitboxFrac.top) / 2f
        // Mantener el rectángulo dentro de [0,1].
        val cx = fx.coerceIn(halfW, 1f - halfW)
        val cy = fy.coerceIn(halfH, 1f - halfH)
        val moved = door.copy(
            hitboxFrac = ovh.gabrielhuav.pow.domain.models.zombie.NormRect(
                left = cx - halfW, top = cy - halfH, right = cx + halfW, bottom = cy + halfH
            )
        )
        val updated = s.designerDoors.toMutableList().also { it[idx] = moved }
        _state.update { it.copy(designerDoors = updated, designerDirty = true) }
    }

    /** Guarda los waypoints en waypoints.json y los aplica a la sala en caliente. */
    fun saveDesignerWaypoints() {
        val s = _state.value
        val room = currentRoom()
        val doors = s.designerDoors
        room.doors = doors
        _state.update { it.copy(designerDirty = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                WaypointRepository.save(applicationContext, room.id, doors)
            } catch (e: Exception) {
                android.util.Log.e("ZombieGameVM", "Error guardando waypoints de ${room.id}", e)
            }
        }
    }

    /** Descarta cambios de waypoints y vuelve a las puertas actuales de la sala. */
    fun resetDesignerWaypoints() {
        val room = currentRoom()
        _state.update { it.copy(designerDoors = room.doors, designerDirty = false, selectedDoorIndex = -1) }
    }

    /** Pinta/borra la celda que contiene la coordenada de MUNDO (x,y). */
    fun paintCellAtWorld(xWorld: Float, yWorld: Float) {
        val s = _state.value
        if (!s.designerMode || s.designerRows.isEmpty()) return
        val room = currentRoom()
        val numRows = s.designerRows.size
        val numCols = s.designerRows.maxOf { it.length }
        if (numCols == 0) return
        val col = ((xWorld / room.worldWidth) * numCols).toInt().coerceIn(0, numCols - 1)
        val row = ((yWorld / room.worldHeight) * numRows).toInt().coerceIn(0, numRows - 1)
        val ch = if (s.designerBrushWall) '#' else '.'
        // Normaliza la fila a numCols (rellena con '.') por si el JSON era irregular.
        val current = s.designerRows[row].padEnd(numCols, '.')
        if (current[col] == ch) return
        val updated = s.designerRows.toMutableList()
        val arr = current.toCharArray()
        arr[col] = ch
        updated[row] = String(arr)
        _state.update { it.copy(designerRows = updated, designerDirty = true) }
    }

    /**
     * Cambia el tamaño de la matriz en edición (modo MATRIZ) añadiendo/quitando
     * columnas y/o filas. Conserva lo ya pintado (anclado arriba-izquierda):
     * las celdas nuevas se crean caminables ('.') y al reducir se recorta.
     */
    fun resizeDesignerMatrixBy(deltaCols: Int, deltaRows: Int) {
        val s = _state.value
        if (!s.designerMode || s.designerTarget != DesignerTarget.MATRIX || s.designerRows.isEmpty()) return
        val old = s.designerRows
        val oldRows = old.size
        val oldCols = old.maxOf { it.length }
        val newCols = (oldCols + deltaCols).coerceIn(MIN_GRID, MAX_GRID)
        val newRows = (oldRows + deltaRows).coerceIn(MIN_GRID, MAX_GRID)
        if (newCols == oldCols && newRows == oldRows) return
        val grid = (0 until newRows).map { r ->
            buildString {
                for (c in 0 until newCols) {
                    val ch = if (r < oldRows && c < old[r].length) old[r][c] else '.'
                    append(ch)
                }
            }
        }
        _state.update { it.copy(designerRows = grid, designerDirty = true) }
    }

    /** Guarda en el JSON local y aplica la matriz a la sala en caliente. */
    fun saveDesignerMatrix() {
        val s = _state.value
        if (s.designerRows.isEmpty()) return
        val room = currentRoom()
        val rows = s.designerRows
        // Aplica en caliente de inmediato (barato, en memoria) y persiste el JSON
        // en disco fuera del hilo principal para no congelar la UI / StrictMode.
        room.collisionMatrix = CollisionMatrix(rows)
        _state.update { it.copy(designerDirty = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                CollisionMatrixRepository.save(applicationContext, room.id, rows)
            } catch (e: Exception) {
                android.util.Log.e("ZombieGameVM", "Error guardando matriz de ${room.id}", e)
            }
        }
    }

    /** Descarta cambios y vuelve a la matriz actual de la sala. */
    fun resetDesignerMatrix() {
        val room = currentRoom()
        val rows = room.collisionMatrix?.rows ?: defaultDesignerRows(room)
        _state.update { it.copy(designerRows = rows, designerDirty = false) }
    }

    fun exportMatricesToUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = CollisionMatrixRepository.exportJson(applicationContext)
                applicationContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                android.util.Log.e("ZombieGameVM", "Error exportando matrices", e)
            }
        }
    }

    fun importMatricesFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = applicationContext.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                CollisionMatrixRepository.importJson(applicationContext, json)
                CollisionMatrixRepository.loadAll(applicationContext).forEach { (roomId, rows) ->
                    if (rows.isNotEmpty()) {
                        ZombieRoomCatalog.roomById(roomId)?.collisionMatrix = CollisionMatrix(rows)
                    }
                }
                // Refrescar la rejilla en edición si seguimos en diseñador.
                if (_state.value.designerMode) {
                    val room = currentRoom()
                    val rows = room.collisionMatrix?.rows ?: defaultDesignerRows(room)
                    _state.update { it.copy(designerRows = rows, designerDirty = false) }
                }
            } catch (e: Exception) {
                android.util.Log.e("ZombieGameVM", "Error importando matrices", e)
            }
        }
    }

    fun exportWaypointsToUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = WaypointRepository.exportJson(applicationContext)
                applicationContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            } catch (e: Exception) {
                android.util.Log.e("ZombieGameVM", "Error exportando waypoints", e)
            }
        }
    }

    fun importWaypointsFromUri(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = applicationContext.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() } ?: return@launch
                WaypointRepository.importJson(applicationContext, json)
                WaypointRepository.loadAll(applicationContext).forEach { (roomId, doors) ->
                    if (doors.isNotEmpty()) {
                        ZombieRoomCatalog.roomById(roomId)?.doors = doors
                    }
                }
                if (_state.value.designerMode && _state.value.designerTarget == DesignerTarget.WAYPOINTS) {
                    _state.update { it.copy(designerDoors = currentRoom().doors, designerDirty = false, selectedDoorIndex = -1) }
                }
            } catch (e: Exception) {
                android.util.Log.e("ZombieGameVM", "Error importando waypoints", e)
            }
        }
    }

    // Rejilla por defecto al editar un cuarto que aún no tiene matriz: solo el
    // borde como pared, interior totalmente caminable. Es un punto de partida
    // NEUTRO (no inventa obstáculos) — tú pintas las paredes reales encima del
    // dibujo del cuarto. Más columnas = trazo más fino.
    internal fun defaultDesignerRows(room: ZombieRoom): List<String> {
        val cols = (room.gridCols ?: DEFAULT_GRID_COLS).coerceAtLeast(3)
        // Filas derivadas del aspect ratio del asset para que cada celda sea
        // ~cuadrada: cellW = W/cols y cellH = H/rows ⇒ con rows = cols*(H/W),
        // cellH ≈ cellW (píxeles cuadrados, no se reescala la celda).
        val aspect = if (room.worldWidth > 0f) room.worldHeight / room.worldWidth else 1f
        val numRows = (cols * aspect).roundToInt().coerceAtLeast(3)
        return (0 until numRows).map { r ->
            buildString {
                for (c in 0 until cols) {
                    val border = r == 0 || r == numRows - 1 || c == 0 || c == cols - 1
                    append(if (border) '#' else '.')
                }
            }
        }
    }

    companion object {
        // Columnas por defecto de la rejilla de colisión cuando una sala no
        // define gridCols. Cambiar este valor (o gridCols por sala en
        // ZombieRoomCatalog) hace las celdas más finas o más gruesas.
        const val DEFAULT_GRID_COLS = 30
        // Límites del tamaño de la matriz editable (en celdas por lado).
        const val MIN_GRID = 3
        const val MAX_GRID = 120
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        idleJob?.cancel()
        exitGuideJob?.cancel()
        wsCollectorJob?.cancel()
        wsManager?.disconnect()
    }

    class Factory(
        private val context: Context,
        private val serverUrl: String?,
        private val playerName: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ZombieGameViewModel(
                context.applicationContext,
                SettingsRepository(context.applicationContext),
                serverUrl,
                playerName
            ) as T
        }
    }
}
