package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

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
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget   // tipo compartido (core)
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

class ZombieInteriorViewModel(
    internal val applicationContext: Context,
    internal val settingsRepository: SettingsRepository,
    // URL del servidor de zombis. null = partida offline (un jugador).
    internal val serverUrl: String?,
    internal val playerName: String,
    // Sala donde arranca la sesión de Interiores. Por defecto el lobby de ESCOM;
    // la puerta "Entrada FES Aragón" la fija a ZombieRoomCatalog.FES_ID.
    internal val startRoomId: String = ZombieRoomCatalog.LOBBY_ID,
    // Estado restaurado al CARGAR partida dentro de un interior: inventario y progreso de ENCB_lab1.
    internal val initialInventoryKeys: List<String> = emptyList(),
    internal val initialLab1KeyFound: Boolean = false
) : ViewModel() {

    internal val soundManager = ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(applicationContext)
    internal var lastZombieSoundMs = 0L

    internal val _state = MutableStateFlow(
        ZombieGameState(
            controlType  = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls(),
            selectedSkin = settingsRepository.getPlayerSkin(),     // ← NUEVO
            showCoordsWidget = settingsRepository.getShowCoordsWidget(),
            isLoading    = false
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
    // Ventana (ms) durante la que se muestra la animación de ATAQUE (SPECIAL) tras DISPARAR.
    // Acotada por tiempo para que NO se quede pegada al moverse: move() reescribía SPECIAL en
    // bucle y, al disparar moviéndote, la animación de ataque se quedaba activa para siempre.
    internal var attackAnimUntilMs = 0L   // internal: lo usa ZombieCombat.kt (fireProjectile) y applyMovement
    internal val ATTACK_ANIM_MS = 200L
    internal var yPressStartMs = 0L
    internal var lastRoomId: String? = null
    // INTERIORES EXPANDIBLE: lobby destino del diálogo "volver al lobby" (campus-agnóstico).
    internal var pendingLobbyTarget: String? = null

    init {
        // Siembra el inventario/progreso restaurado ANTES del primer loadRoom (que los preserva).
        _state.update { it.copy(isLoading = true, inventoryKeys = initialInventoryKeys, lab1KeyFound = initialLab1KeyFound) }
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
                    // Arranca en la sala pedida (FES o lobby). Si el id no existe en el
                    // catálogo (indexOfRoom = -1), cae al lobby de ESCOM.
                    val startIdx = ZombieRoomCatalog.indexOfRoom(startRoomId)
                        .takeIf { it >= 0 } ?: ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID)
                    loadRoom(startIdx)
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
                    // El servidor de interiores usa esto para saber el modo de la sala:
                    // "zombies" (edificio/horda) o "interiores" (lobby sin zombis).
                    "mode" to currentNetMode(),
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
                    // MODO de interiores que el servidor debe detectar: "zombies" (edificio
                    // con horda o lobby con apocalipsis) vs "interiores" (lobby tranquilo).
                    "mode" to currentNetMode(),
                    "x" to (s.playerX / room.worldWidth),
                    "y" to (s.playerY / room.worldHeight),
                    "action" to s.playerAction.name,
                    "facingRight" to s.isPlayerFacingRight,
                    "health" to s.playerHealth
                )
            )
        )
    }

    internal fun sendItemPickup(itemId: String) {
        if (!isMultiplayer) return
        wsManager?.sendMessage(gson.toJson(mapOf("type" to "ITEM_PICKUP", "itemId" to itemId)))
    }

    // CAPA ZOMBI: sendZombieDamage / applyServerZombieState / effectFromName / applyEffectByName /
    // showVictory / hasEffect / playerDamageFactor → movidos a ZombieCombat.kt (extensiones).

    // ─── ACCESO ────────────────────────────────────────────
    internal fun currentRoom(): ZombieRoom = ZombieRoomCatalog.rooms[_state.value.currentRoomIndex]

    // Modo de interiores que viaja al servidor para que distinga "interiores" (lobby
    // tranquilo, sin zombis) de "zombies" (edificio con horda, o lobby con el apocalipsis
    // activado). El mundo abierto NO usa esto: allí el modo es la instancia ws
    // ("normal" = mapa global, "apocalipsis" = zombies global) vía JOIN_INSTANCE.
    internal fun currentNetMode(): String {
        val room = currentRoom()
        return if (room.type == ZoneType.BUILDING || _state.value.zombieModeActivated) "zombies"
        else "interiores"
    }

    internal fun isWalkable(x: Float, y: Float): Boolean {
        val r = currentRoom()
        if (x < PLAYER_RADIUS || y < PLAYER_RADIUS ||
            x > r.worldWidth - PLAYER_RADIUS || y > r.worldHeight - PLAYER_RADIUS) return false
        return !r.isBlockedPixel(x, y)
    }

    // INTERIORES EXPANDIBLE: el lobby (campus) que tiene una puerta hacia este edificio.
    // Campus-agnóstico (sirve para ESCOM, FES, UAM…); cae al lobby de ESCOM si no se halla.
    internal fun lobbyForBuilding(buildingId: String): ZombieRoom =
        ZombieRoomCatalog.rooms.firstOrNull { room ->
            room.type == ZoneType.LOBBY && room.doors.any { it.targetRoomId == buildingId }
        } ?: ZombieRoomCatalog.roomById(ZombieRoomCatalog.LOBBY_ID)!!

    internal fun spawnAtLobbyDoorFor(fromBuildingId: String): Pair<Float, Float>? {
        val lobby = lobbyForBuilding(fromBuildingId)
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

    // Garantiza que el spawn caiga en una celda CAMINABLE (fuera de la matriz de
    // colisión). Si el punto pedido está bloqueado (p. ej. la puerta de retorno al
    // lobby cae sobre una pared), busca en anillos crecientes el punto válido más
    // cercano. Arregla el bug "salgo del Edificio Principal y no me puedo mover".
    internal fun nearestWalkableSpawn(x: Float, y: Float, room: ZombieRoom): Pair<Float, Float> {
        fun ok(px: Float, py: Float): Boolean =
            px >= PLAYER_RADIUS && py >= PLAYER_RADIUS &&
            px <= room.worldWidth - PLAYER_RADIUS && py <= room.worldHeight - PLAYER_RADIUS &&
            !room.isBlockedPixel(px, py)

        val cx = x.coerceIn(PLAYER_RADIUS, room.worldWidth - PLAYER_RADIUS)
        val cy = y.coerceIn(PLAYER_RADIUS, room.worldHeight - PLAYER_RADIUS)
        if (ok(cx, cy)) return cx to cy

        // Búsqueda en anillos (16 direcciones) hasta cubrir la sala.
        val step = PLAYER_RADIUS
        val maxR = maxOf(room.worldWidth, room.worldHeight)
        var r = step
        while (r <= maxR) {
            for (a in 0 until 16) {
                val ang = a * (Math.PI.toFloat() / 8f)
                val px = cx + cos(ang) * r
                val py = cy + sin(ang) * r
                if (ok(px, py)) return px to py
            }
            r += step
        }
        // Último recurso: el centro de la sala (casi siempre caminable).
        return (room.worldWidth / 2f) to (room.worldHeight / 2f)
    }

    // ─── CARGA DE ZONA ─────────────────────────────────────
    internal fun loadRoom(index: Int) {
        val room = ZombieRoomCatalog.rooms[index]
        val now = System.currentTimeMillis()

        val pendingX = _state.value.pendingSpawnX
        val pendingY = _state.value.pendingSpawnY
        val rawSpawnX = pendingX ?: (room.playerSpawnFrac.x * room.worldWidth)
        val rawSpawnY = pendingY ?: (room.playerSpawnFrac.y * room.worldHeight)
        // Snap del spawn a una celda caminable (no dentro de la matriz de colisión).
        val (spawnX, spawnY) = nearestWalkableSpawn(rawSpawnX, rawSpawnY, room)

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

        // PUZZLE de llaves (Modo Historia): al ENTRAR a ENCB_lab1 —y solo si aún no se encontró la
        // correcta— se siembran llaves dispersas por la sala. En las demás salas no hay llaves.
        val newKeys = if (room.id == ZombieRoomCatalog.ENCB_LAB1_ID && !_state.value.lab1KeyFound)
            spawnLab1Keys(room) else emptyList()

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
                keys = newKeys,
                nearbyKeyId = null,
                keyMessage = null,
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

    /**
     * PUZZLE de llaves (ENCB_lab1): coloca las 5 llaves en posiciones aleatorias CAMINABLES
     * (no bloqueadas por la matriz), repartidas por la sala. Una es la correcta (LLave4).
     */
    private fun spawnLab1Keys(room: ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom): List<ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop> {
        val now = System.currentTimeMillis()
        val assets = ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop.LAB1_KEY_ASSETS
        // Construye la lista de celdas CAMINABLES (no '#') de la matriz de colisión de la sala como
        // centros en píxeles de mundo. Así las llaves caen SIEMPRE donde el jugador SÍ puede pisar
        // (antes el muestreo aleatorio podía caer en una matriz por defecto y quedaban irrecuperables).
        val rows = room.collisionMatrix?.rows
        val walkable = ArrayList<Pair<Float, Float>>()
        if (rows != null && rows.isNotEmpty()) {
            val nRows = rows.size
            val nCols = rows[0].length
            for (r in 1 until (nRows - 1).coerceAtLeast(1)) {   // evita el borde (suele estar bloqueado)
                val row = rows[r]
                for (c in 1 until (row.length - 1).coerceAtLeast(1)) {
                    if (row[c] != '#') {
                        val fx = (c + 0.5f) / nCols
                        val fy = (r + 0.5f) / nRows
                        walkable.add(fx * room.worldWidth to fy * room.worldHeight)
                    }
                }
            }
        }
        val picks = if (walkable.size >= assets.size) walkable.shuffled().take(assets.size) else null
        return assets.mapIndexed { i, asset ->
            val pos = picks?.get(i)
                ?: nearestWalkableSpawn((0.18f + 0.16f * i) * room.worldWidth, 0.50f * room.worldHeight, room)
            ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop(
                id = "key_${i}_$now",
                assetPath = asset,
                x = pos.first, y = pos.second,
                isCorrect = asset == ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop.LAB1_CORRECT_KEY
            )
        }
    }

    /** Salir del minijuego al mapa abierto (equivale a cruzar la puerta TO_WORLD). */
    fun exitToWorld() = goToRoom(ZombieRoomCatalog.EXIT_TO_WORLD)

    internal fun goToRoom(targetRoomId: String) {
        if (targetRoomId == ZombieRoomCatalog.EXIT_TO_WORLD) {
            gameLoopJob?.cancel()
            _state.update { it.copy(isExitingToWorld = true) }
            return
        }
        // MODO HISTORIA: waypoint final de ENCB_LAB2 → salir a la narrativa (cómic ENCB_OUTRO).
        if (targetRoomId == ZombieRoomCatalog.EXIT_TO_STORY_OUTRO) {
            gameLoopJob?.cancel()
            _state.update { it.copy(isExitingToStoryOutro = true) }
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
        } else if (!(fromRoom.type == ZoneType.LOBBY && targetRoom.type == ZoneType.BUILDING)) {
            // TP puerta↔puerta: aparece JUNTO a la puerta del cuarto DESTINO que regresa al cuarto de
            // ORIGEN (al pulsar "Continuar →" en el cuarto N, spawneas junto a la "← Regresar" del N+1,
            // y viceversa), en vez de en el centro. (Se excluye "lobby → edificio": esa entrada conserva
            // su spawn central + siembra de zombis.)
            // ⚠️ CLAVE: el spawn debe quedar JUSTO FUERA del hitbox de esa puerta. `onInteract` dispara la
            // puerta cuyo hitbox contiene al jugador, así que si spawneas DENTRO, la siguiente X te regresa
            // al cuarto anterior (rebote = "TP mal"). Antes se desplazaba un 30% fijo hacia el centro, lo
            // que NO bastaba si la puerta era grande o estaba cerca del centro (caso encb_lobby). Ahora se
            // empuja por la ORILLA de la puerta que da al interior (el eje donde está más pegada a un muro),
            // quedando fuera del rectángulo + margen, SEA CUAL SEA su tamaño o posición.
            targetRoom.doors.firstOrNull { it.targetRoomId == fromRoom.id }?.let { backDoor ->
                val r = backDoor.hitboxFrac
                val fx = (r.left + r.right) * 0.5f
                val fy = (r.top + r.bottom) * 0.5f
                val halfW = (r.right - r.left) * 0.5f
                val halfH = (r.bottom - r.top) * 0.5f
                val margin = 0.04f // ~4% del cuarto: te deja parado pegado a la puerta, pero fuera del hitbox
                var sxFrac = fx
                var syFrac = fy
                // Sale por el eje donde la puerta está MÁS descentrada (su lado contra el muro), hacia el centro.
                if (kotlin.math.abs(0.5f - fx) >= kotlin.math.abs(0.5f - fy)) {
                    sxFrac = fx + (if (0.5f - fx >= 0f) 1f else -1f) * (halfW + margin)
                } else {
                    syFrac = fy + (if (0.5f - fy >= 0f) 1f else -1f) * (halfH + margin)
                }
                sxFrac = sxFrac.coerceIn(0.04f, 0.96f)
                syFrac = syFrac.coerceIn(0.04f, 0.96f)
                _state.update { it.copy(pendingSpawnX = sxFrac * targetRoom.worldWidth, pendingSpawnY = syFrac * targetRoom.worldHeight) }
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

    // CAPA ZOMBI: performPlayerAttack / fireProjectile / onZombieDeath / applyEffect →
    // movidos a ZombieCombat.kt (extensiones). La simulación de zombis está en ZombieGameTick.kt.

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
            // Respawn en el lobby DEL CAMPUS donde moriste (ESCOM o FES), no siempre ESCOM.
            loadRoom(ZombieRoomCatalog.indexOfRoom(lobbyForBuilding(diedInRoom.id).id))
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
            soundManager.playItem()
            applyEffect(item.effect)
            return
        }
        // 1b. PUZZLE ENCB_lab1: RECOGER la llave cercana al INVENTARIO (1 slot). No se sabe si es la
        // correcta hasta PROBARLA en la puerta de avance.
        val keyId = s.nearbyKeyId
        if (keyId != null) {
            val key = s.keys.firstOrNull { it.id == keyId } ?: return
            if (s.inventoryKeys.size >= INVENTORY_UNLOCKED_SLOTS) {
                showKeyMessage("🎒 Inventario lleno (1 slot). Prueba la llave en la puerta de avance.")
                return
            }
            soundManager.playItem()
            _state.update { cur -> cur.copy(
                keys = cur.keys.filter { it.id != keyId },
                nearbyKeyId = null,
                inventoryKeys = cur.inventoryKeys + key.assetPath,
                keyMessage = "🔑 Llave recogida. Pruébala en la puerta de avance (→)."
            ) }
            clearKeyMessageSoon()
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

        // PUZZLE ENCB_lab1: la puerta de AVANCE (→) está CERRADA. Al pulsar acción aquí se PRUEBA la
        // llave del inventario: la correcta (LLave4) la abre; una incorrecta se DESCARTA (libera el
        // slot) para que el jugador busque otra. Tras abrir, vuelve a pulsar para cruzar.
        if (room.id == ZombieRoomCatalog.ENCB_LAB1_ID &&
            door.kind == ovh.gabrielhuav.pow.domain.models.zombie.DoorKind.EXIT_NEXT &&
            !s.lab1KeyFound) {
            val correct = ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop.LAB1_CORRECT_KEY
            when {
                s.inventoryKeys.isEmpty() ->
                    showKeyMessage("🔒 Necesitas una llave. Búscala en la sala y recógela.")
                s.inventoryKeys.any { it == correct } -> {
                    _state.update { cur -> cur.copy(
                        lab1KeyFound = true,
                        inventoryKeys = cur.inventoryKeys.filter { it != correct }
                    ) }
                    showKeyMessage("🔑 ¡Era la correcta! La puerta se abrió. Pulsa otra vez para avanzar.")
                }
                else -> {
                    _state.update { cur -> cur.copy(inventoryKeys = emptyList()) }
                    showKeyMessage("🔒 No es la correcta. Busca otra llave en la sala.")
                }
            }
            return
        }

        // Puerta de un EDIFICIO hacia el lobby de SU campus (ESCOM o FES): pide confirmación.
        // Generalizado: el destino es cualquier sala LOBBY (antes sólo el lobby de ESCOM).
        val targetIsLobby = ZombieRoomCatalog.roomById(door.targetRoomId)?.type == ZoneType.LOBBY
        if (targetIsLobby && room.type == ZoneType.BUILDING) {
            pendingLobbyTarget = door.targetRoomId
            _state.update { it.copy(showExitToLobbyDialog = true) }
            return
        }
        goToRoom(door.targetRoomId)
    }

    fun confirmExitToLobby() {
        _state.update { it.copy(showExitToLobbyDialog = false) }
        // Vuelve al lobby del campus (ESCOM por defecto si no hay destino pendiente).
        goToRoom(pendingLobbyTarget ?: ZombieRoomCatalog.LOBBY_ID)
        pendingLobbyTarget = null
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
        // ATAQUE: MELEE mantiene SPECIAL mientras se SOSTIENE el botón; RANGED solo durante la
        // ventana de animación (attackAnimUntilMs). Así, al DISPARAR moviéndote, la animación de
        // ataque ya NO se queda pegada (antes move() reescribía SPECIAL en bucle).
        val attacking = _state.value.playerAction == PlayerAction.SPECIAL &&
            (_state.value.combatMode == CombatMode.MELEE || System.currentTimeMillis() < attackAnimUntilMs)
        val action = if (attacking) PlayerAction.SPECIAL
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
            // Al DETENERte, vuelve a IDLE salvo que estés SOSTENIENDO el cuerpo a cuerpo (MELEE).
            val st = _state.value
            val meleeHeld = st.playerAction == PlayerAction.SPECIAL && st.combatMode == CombatMode.MELEE
            if (!meleeHeld) {
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

    // ─── CONTROLES (interiores) ────────────────────────────
    // Y: MANTENER abre el INVENTARIO. A: TOCAR alterna correr; MANTENER abre el menú de ARMAS.
    private var aPressStartMs = 0L

    fun onSecondaryPressed() { yPressStartMs = System.currentTimeMillis() }

    fun onSecondaryReleased() {
        val held = System.currentTimeMillis() - yPressStartMs
        if (held >= Y_HOLD_FOR_MENU_MS) {
            _state.update { it.copy(showInventory = !it.showInventory, showWeaponMenu = false) }
        }
    }

    fun onPrimaryPressed() { aPressStartMs = System.currentTimeMillis() }

    fun onPrimaryReleased() {
        val held = System.currentTimeMillis() - aPressStartMs
        if (held >= Y_HOLD_FOR_MENU_MS) {
            // MANTENER A → menú de ARMAS.
            _state.update { it.copy(showWeaponMenu = !it.showWeaponMenu, showInventory = false) }
        } else {
            // TOCAR A → alterna CORRER (interruptor).
            setRunning(!_state.value.isRunning)
        }
    }

    fun selectCombatMode(mode: CombatMode) {
        // El modo de golpe vive en el MENÚ COMBINADO (con el inventario, se abre con Y): elegir
        // un modo NO cierra el menú (el jugador puede ver/usar el inventario en el mismo panel).
        _state.update { it.copy(combatMode = mode) }
    }

    fun dismissWeaponMenu() {
        _state.update { it.copy(showWeaponMenu = false) }
    }

    fun dismissInventory() {
        _state.update { it.copy(showInventory = false) }
    }

    // Mensajes transitorios del puzzle de llaves (se limpian solos a los ~2.8 s).
    private fun clearKeyMessageSoon() {
        val msg = _state.value.keyMessage
        viewModelScope.launch {
            delay(2800)
            _state.update { if (it.keyMessage == msg) it.copy(keyMessage = null) else it }
        }
    }
    private fun showKeyMessage(msg: String) {
        _state.update { it.copy(keyMessage = msg) }
        clearKeyMessageSoon()
    }

    fun consumeExit() { gameLoopJob?.cancel() }

    fun onZombieCinematicDismissed() {
        _state.update { it.copy(showZombieCinematic = false, zombieModeActivated = true) }
        loadRoom(ZombieRoomCatalog.indexOfRoom(ZombieRoomCatalog.LOBBY_ID))
    }

    fun toggleSkinSelector(show: Boolean) {
        _state.update { it.copy(showSkinSelector = show) }
    }

    fun selectSkin(skin: ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin) {
        settingsRepository.savePlayerSkin(skin)
        _state.update { it.copy(selectedSkin = skin, showSkinSelector = false) }
    }

    // ─── MODO DISEÑADOR ────────────────────────────────────
    // REFACTOR: todas las funciones del Modo Diseñador (matriz de colisión + waypoints:
    // toggle/target/brush, select/move door, paint/resize, save/reset, export/import,
    // defaultDesignerRows) se movieron a `ZombieGameDesigner.kt` (mismo paquete) como
    // extensiones de ZombieInteriorViewModel. No quedó gemelo miembro. Call-sites en
    // `ui/ZombieGameScreen.kt` importan las extensiones. Ver 09 §0.

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
        private val playerName: String,
        private val startRoomId: String = ZombieRoomCatalog.LOBBY_ID,
        private val initialInventoryKeys: List<String> = emptyList(),
        private val initialLab1KeyFound: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ZombieInteriorViewModel(
                context.applicationContext,
                SettingsRepository(context.applicationContext),
                serverUrl,
                playerName,
                startRoomId,
                initialInventoryKeys,
                initialLab1KeyFound
            ) as T
        }
    }
}
