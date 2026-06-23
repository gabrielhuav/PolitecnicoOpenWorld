package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.NormRect
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * ViewModel UNIFICADO del interior de una estación de transporte (Metro, Metrobús y futuros). Toda
 * la lógica (movimiento, hotspots/torniquetes, animación de abordaje, diseñador de matriz/puertas,
 * waypoints globales, persistencia) vive AQUÍ, una sola vez. Lo específico de cada sistema entra por
 * `config: TransitSystemConfig` (assets, prefs, repo, spawn, offset de torniquete, strings, branding).
 *
 * Reemplaza a `MetroInteriorViewModel` y `MetrobusInteriorViewModel` (eliminados): antes la misma
 * lógica estaba DUPLICADA en dos archivos. Las pantallas crean este VM con la config adecuada vía
 * `Factory(context, config, stationName, spawnX, spawnY)`. Métodos y estado con nombres neutros (sistema-agnósticos).
 */
class TransitInteriorViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    val config: TransitSystemConfig,
    private val stationName: String,
    private val spawnX: Float = -1f,
    private val spawnY: Float = -1f
) : ViewModel() {

    private val _state = MutableStateFlow(
        TransitInteriorState(
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls(),
            selectedSkin = settingsRepository.getPlayerSkin()   // respeta la skin elegida (no siempre Lázaro)
        )
    )
    val state: StateFlow<TransitInteriorState> = _state.asStateFlow()

    private var idleJob: Job? = null
    private var collisionGrid: CollisionGrid = CollisionGrid.emptyWithBorder()

    private var gridRows = 30
    private var gridCols = 20

    private val WALK_STEP = 0.004f
    private val RUN_STEP = 0.008f

    private val prefs = context.getSharedPreferences(config.stationPrefsName(stationName), Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        val savedRows = prefs.getString("matrix", null)
        val savedDoors = prefs.getString("doors", null)

        var initialRows = if (savedRows != null) {
            try { gson.fromJson<List<String>>(savedRows, object : TypeToken<List<String>>() {}.type) }
            catch (e: Exception) { null }
        } else null

        if (initialRows.isNullOrEmpty()) {
            try {
                context.assets.open(config.matrixAsset).use { inp ->
                    val json = InputStreamReader(inp).readText()
                    initialRows = gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                }
            } catch (e: Exception) { }
        }

        val defaultRows = initialRows ?: List(gridRows) { r ->
            if (r == 0 || r == gridRows - 1) "#".repeat(gridCols)
            else "#" + ".".repeat(gridCols - 2) + "#"
        }

        gridRows = defaultRows.size
        gridCols = defaultRows.maxOfOrNull { it.length } ?: gridCols

        var initialDoors = if (savedDoors != null) {
            try { gson.fromJson<List<ZoneDoor>>(savedDoors, object : TypeToken<List<ZoneDoor>>() {}.type) }
            catch (e: Exception) { null }
        } else null

        if (initialDoors.isNullOrEmpty()) {
            try {
                context.assets.open(config.waypointsAsset).use { inp ->
                    val json = InputStreamReader(inp).readText()
                    initialDoors = gson.fromJson<List<ZoneDoor>>(json, object : TypeToken<List<ZoneDoor>>() {}.type)
                }
            } catch (e: Exception) { }
        }

        val defaultDoors = initialDoors ?: config.defaultDoors

        val globalPrefs = context.getSharedPreferences(config.mapGlobalPrefsName, Context.MODE_PRIVATE)
        val savedGlobalWaypoints = globalPrefs.getString("global_waypoints", null)

        var initialGlobalWaypoints: List<ZoneDoor>? = null
        if (savedGlobalWaypoints != null) {
            try {
                initialGlobalWaypoints = gson.fromJson<List<ZoneDoor>>(savedGlobalWaypoints, object : TypeToken<List<ZoneDoor>>() {}.type)
            } catch (e: Exception) { }
        }

        if (initialGlobalWaypoints.isNullOrEmpty()) {
            try {
                context.assets.open(config.globalWaypointsAsset).use { inp ->
                    val json = InputStreamReader(inp).readText()
                    initialGlobalWaypoints = gson.fromJson<List<ZoneDoor>>(json, object : TypeToken<List<ZoneDoor>>() {}.type)
                }
            } catch (e: Exception) { }
        }

        val allStations = config.loadStations(context)

        var startX = config.defaultSpawnX
        var startY = config.defaultSpawnY
        var recharged = false

        if (spawnX != -1f && spawnY != -1f) {
            startX = spawnX
            startY = spawnY
            recharged = true
        }

        _state.update {
            it.copy(
                designerRows = defaultRows,
                doors = defaultDoors,
                globalWaypoints = initialGlobalWaypoints ?: emptyList(),
                allStations = allStations,
                playerX = startX,
                playerY = startY,
                hasRechargedTicket = recharged,
                spawnWithAnimation = recharged,
                isPlayerVisible = !recharged
            )
        }
        updateCollisionGrid(defaultRows)
    }

    fun moveByAngle(angleRad: Double) {
        if (_state.value.designerMode || !_state.value.areControlsEnabled) return
        val current = _state.value
        val step = if (current.isRunning) RUN_STEP else WALK_STEP
        val dx = cos(angleRad).toFloat() * step
        val dy = -sin(angleRad).toFloat() * step
        applyMovement(current.playerX + dx, current.playerY + dy, dx)
    }

    fun moveDirection(direction: Direction) {
        if (_state.value.designerMode || !_state.value.areControlsEnabled) return
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
        val curX = _state.value.playerX
        val curY = _state.value.playerY

        val (fx, fy) = when {
            collisionGrid.isWalkable(clampedX, clampedY) -> clampedX to clampedY
            collisionGrid.isWalkable(clampedX, curY)     -> clampedX to curY
            collisionGrid.isWalkable(curX, clampedY)     -> curX to clampedY
            else -> {
                if (abs(dxForFacing) > 0.0001f) {
                    _state.update { it.copy(isFacingRight = dxForFacing > 0) }
                }
                return
            }
        }

        updatePlayer(fx, fy, dxForFacing)
        checkHotspots(fx, fy)
    }

    private fun updatePlayer(x: Float, y: Float, dxForFacing: Float) {
        val current = _state.value
        val facing = if (abs(dxForFacing) > 0.0001f) dxForFacing > 0 else current.isFacingRight
        val action = if (current.isRunning) PlayerAction.RUN else PlayerAction.WALK

        idleJob?.cancel()
        _state.update { it.copy(playerX = x, playerY = y, playerAction = action, isFacingRight = facing) }

        idleJob = viewModelScope.launch {
            delay(150)
            _state.update { it.copy(playerAction = PlayerAction.IDLE) }
        }
    }

    fun setRunning(running: Boolean) {
        _state.update { it.copy(isRunning = running) }
    }

    // --- HOTSPOTS (DOORS) ---
    private fun checkHotspots(x: Float, y: Float) {
        val doors = _state.value.doors
        val detected = doors.firstOrNull { door ->
            val r = door.hitboxFrac
            x in r.left..r.right && y in r.top..r.bottom
        }
        if (_state.value.activeDoor != detected) {
            _state.update { it.copy(activeDoor = detected) }
        }
    }

    fun interactWithHotspot() {
        if (!_state.value.areControlsEnabled) return
        val door = _state.value.activeDoor ?: return
        when (door.targetRoomId) {
            "taquilla" -> {
                _state.update { it.copy(hasRechargedTicket = true, messageToast = getLocalizedString(config.msgTicketReloadedRes)) }
                viewModelScope.launch { delay(2000); _state.update { it.copy(messageToast = null) } }
            }
            "torniquetes" -> {
                if (_state.value.hasRechargedTicket) {
                    viewModelScope.launch {
                        idleJob?.cancel()
                        _state.update { it.copy(areControlsEnabled = false, playerAction = PlayerAction.WALK) }
                        val startY = _state.value.playerY
                        val steps = 40
                        val stepDelay = 1200L / steps
                        for (i in 1..steps) {
                            val fraction = i.toFloat() / steps
                            _state.update { it.copy(playerY = startY + fraction * config.turnstileBoardDeltaY, playerAction = PlayerAction.WALK) }
                            delay(stepDelay)
                        }
                        _state.update { it.copy(areControlsEnabled = true, playerAction = PlayerAction.IDLE) }
                    }
                } else {
                    _state.update { it.copy(messageToast = getLocalizedString(config.msgNoBalanceRes)) }
                    viewModelScope.launch { delay(2000); _state.update { it.copy(messageToast = null) } }
                }
            }
            "anden" -> {
                if (!_state.value.isVehicle1Departing) {
                    _state.update { it.copy(isVehicle1Animating = true, areControlsEnabled = false, isBoardingWalkActive = true) }
                } else {
                    _state.update { it.copy(messageToast = getLocalizedString(config.msgWaitVehicleRes)) }
                    viewModelScope.launch { delay(2000); _state.update { it.copy(messageToast = null) } }
                }
            }
            "salir_torniquetes" -> {
                viewModelScope.launch {
                    idleJob?.cancel()
                    _state.update { it.copy(areControlsEnabled = false, playerAction = PlayerAction.WALK) }
                    val startY = _state.value.playerY
                    val steps = 40
                    val stepDelay = 1200L / steps
                    for (i in 1..steps) {
                        val fraction = i.toFloat() / steps
                        // Salir = inverso del abordaje; coerceIn cubre ambos sentidos (metro sube, metrobús baja).
                        _state.update { it.copy(playerY = (startY - fraction * config.turnstileBoardDeltaY).coerceIn(0f, 1f), playerAction = PlayerAction.WALK) }
                        delay(stepDelay)
                    }
                    _state.update {
                        it.copy(
                            areControlsEnabled = true,
                            playerAction = PlayerAction.IDLE,
                            hasRechargedTicket = false,
                            messageToast = getLocalizedString(config.msgExitTurnstileRes)
                        )
                    }
                    delay(3000); _state.update { it.copy(messageToast = null) }
                }
            }
            "salida" -> {
                _state.update { it.copy(exitStationRequested = true) }
            }
            else -> {
                _state.update { it.copy(messageToast = "Interacción con ${door.label}") }
                viewModelScope.launch { delay(2000); _state.update { it.copy(messageToast = null) } }
            }
        }
    }

    /** Resuelve un string localizado al idioma elegido por el jugador (Ajustes). El context del VM es
     *  applicationContext (sin el wrap de idioma de la Activity), así que aplicamos el locale aquí. */
    fun getLocalizedString(resId: Int, vararg args: Any): String {
        val lang = settingsRepository.getLanguage()
        val ctx = if (lang.isNotEmpty()) {
            val locale = java.util.Locale(lang)
            val cfg = android.content.res.Configuration(context.resources.configuration)
            cfg.setLocale(locale)
            context.createConfigurationContext(cfg)
        } else context
        return ctx.getString(resId, *args)
    }

    /** Cierra el mapa de la red y reproduce el desembarco. (Antes closeMetroMap/closeMetrobusMap.) */
    fun closeTransitMap() {
        _state.update { it.copy(showTransitMap = false) }
        viewModelScope.launch {
            idleJob?.cancel()
            _state.update {
                it.copy(
                    isPlayerVisible = true,
                    playerAction = PlayerAction.WALK,
                    isFacingRight = false,
                    areControlsEnabled = false,
                    isDisembarkingWalkActive = true
                )
            }
            val startX = _state.value.playerX
            val targetX = startX - 0.05f
            val steps = 20
            val stepDelay = 600L / steps
            for (i in 1..steps) {
                val fraction = i.toFloat() / steps
                _state.update { it.copy(playerX = startX + (targetX - startX) * fraction, playerAction = PlayerAction.WALK) }
                delay(stepDelay)
            }
            _state.update {
                it.copy(
                    playerAction = PlayerAction.IDLE,
                    areControlsEnabled = true,
                    isVehicle1Departing = true,
                    isDisembarkingWalkActive = false
                )
            }
            delay(5000)
            _state.update { it.copy(isVehicle1Departing = false) }
        }
    }

    /** Fin de la animación del vehículo 1. (Antes onMetro1AnimationFinished/onBus1AnimationFinished.) */
    fun onVehicle1AnimationFinished() {
        if (_state.value.isVehicle1Animating) {
            if (_state.value.isBoardingWalkActive) {
                viewModelScope.launch {
                    idleJob?.cancel()
                    val startX = _state.value.playerX
                    val targetX = startX + 0.05f
                    val steps = 20
                    val stepDelay = 600L / steps
                    _state.update { it.copy(playerAction = PlayerAction.WALK, isFacingRight = true) }
                    for (i in 1..steps) {
                        val fraction = i.toFloat() / steps
                        _state.update { it.copy(playerX = startX + (targetX - startX) * fraction, playerAction = PlayerAction.WALK) }
                        delay(stepDelay)
                    }
                    _state.update {
                        it.copy(
                            isPlayerVisible = false,
                            playerAction = PlayerAction.IDLE,
                            isBoardingWalkActive = false,
                            isVehicle1Animating = false,
                            showTransitMap = true
                        )
                    }
                }
            } else {
                _state.update { it.copy(isVehicle1Animating = false, showTransitMap = true) }
            }
        } else if (_state.value.spawnWithAnimation) {
            viewModelScope.launch {
                idleJob?.cancel()
                delay(500)
                val startX = _state.value.playerX
                val targetX = startX - 0.05f
                _state.update {
                    it.copy(
                        spawnWithAnimation = false,
                        isPlayerVisible = true,
                        playerAction = PlayerAction.WALK,
                        isFacingRight = false,
                        areControlsEnabled = false,
                        isDisembarkingWalkActive = true
                    )
                }
                val steps = 20
                val stepDelay = 600L / steps
                for (i in 1..steps) {
                    val fraction = i.toFloat() / steps
                    _state.update { it.copy(playerX = startX + (targetX - startX) * fraction, playerAction = PlayerAction.WALK) }
                    delay(stepDelay)
                }
                _state.update {
                    it.copy(
                        playerAction = PlayerAction.IDLE,
                        areControlsEnabled = true,
                        isVehicle1Departing = true,
                        isDisembarkingWalkActive = false
                    )
                }
                delay(5000)
                _state.update { it.copy(isVehicle1Departing = false) }
            }
        }
    }


    fun consumeExitStation() {
        _state.update { it.copy(exitStationRequested = false) }
    }

    // --- DISEÑADOR ---
    fun toggleDesignerMode() {
        _state.update { it.copy(designerMode = !it.designerMode) }
    }

    fun setDesignerTarget(target: DesignerTarget) {
        _state.update { it.copy(designerTarget = target) }
    }

    fun setDesignerBrushWall(brushWall: Boolean) {
        _state.update { it.copy(designerBrushWall = brushWall) }
    }

    fun paintCellAtWorld(normalizedX: Float, normalizedY: Float) {
        val s = _state.value
        if (s.designerTarget != DesignerTarget.MATRIX) return
        val currentRows = s.designerRows.toMutableList()
        val rows = currentRows.size
        val cols = currentRows.maxOfOrNull { it.length } ?: 0
        if (rows == 0 || cols == 0) return
        val c = (normalizedX * cols).toInt().coerceIn(0, cols - 1)
        val r = (normalizedY * rows).toInt().coerceIn(0, rows - 1)
        val rowStr = currentRows[r].padEnd(cols, '.')
        val charArr = rowStr.toCharArray()
        val newChar = if (s.designerBrushWall) '#' else '.'
        if (charArr[c] == newChar) return
        charArr[c] = newChar
        currentRows[r] = String(charArr)
        _state.update { it.copy(designerRows = currentRows, designerDirty = true) }
        updateCollisionGrid(currentRows)
    }

    fun saveDesignerMatrix() {
        prefs.edit()
            .putString("matrix", gson.toJson(_state.value.designerRows))
            .putString("doors", gson.toJson(_state.value.doors))
            .apply()
        _state.update { it.copy(designerDirty = false) }
    }

    // --- IMPORTACIÓN / EXPORTACIÓN SAF ---
    fun exportMatricesToUri(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(gson.toJson(_state.value.designerRows).toByteArray())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importMatricesFromUri(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val rows = gson.fromJson<List<String>>(InputStreamReader(inp).readText(), object : TypeToken<List<String>>() {}.type)
                if (rows != null) {
                    gridRows = rows.size
                    gridCols = rows.maxOfOrNull { it.length } ?: 0
                    _state.update { it.copy(designerRows = rows, designerDirty = true) }
                    updateCollisionGrid(rows)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun exportWaypointsToUri(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(gson.toJson(_state.value.doors).toByteArray())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importWaypointsFromUri(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val ds = gson.fromJson<List<ZoneDoor>>(InputStreamReader(inp).readText(), object : TypeToken<List<ZoneDoor>>() {}.type)
                if (ds != null) _state.update { it.copy(doors = ds, designerDirty = true) }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun exportGlobalWaypointsToUri(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(gson.toJson(_state.value.globalWaypoints).toByteArray())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importGlobalWaypointsFromUri(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val ds = gson.fromJson<List<ZoneDoor>>(InputStreamReader(inp).readText(), object : TypeToken<List<ZoneDoor>>() {}.type)
                if (ds != null) {
                    _state.update { it.copy(globalWaypoints = ds, designerDirty = true) }
                    saveGlobalWaypoints()
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun resetDesignerMatrix() {
        val defaultRows = List(gridRows) { r ->
            if (r == 0 || r == gridRows - 1) "#".repeat(gridCols)
            else "#" + ".".repeat(gridCols - 2) + "#"
        }
        _state.update { it.copy(designerRows = defaultRows, designerDirty = true) }
        updateCollisionGrid(defaultRows)
    }

    fun resizeDesignerMatrixBy(deltaCols: Int, deltaRows: Int) {
        val newCols = (gridCols + deltaCols).coerceIn(5, 100)
        val newRows = (gridRows + deltaRows).coerceIn(5, 100)
        if (newCols == gridCols && newRows == gridRows) return
        val oldRows = _state.value.designerRows
        val newRowsList = mutableListOf<String>()
        for (r in 0 until newRows) {
            if (r < oldRows.size) {
                var rowStr = oldRows[r]
                rowStr = if (newCols > gridCols) rowStr + ".".repeat(newCols - gridCols)
                         else rowStr.substring(0, newCols)
                newRowsList.add(rowStr)
            } else {
                newRowsList.add(".".repeat(newCols))
            }
        }
        gridCols = newCols; gridRows = newRows
        _state.update { it.copy(designerRows = newRowsList, designerDirty = true) }
        updateCollisionGrid(newRowsList)
    }

    // --- EDICIÓN DE WAYPOINTS ---
    fun selectDoor(x: Float, y: Float) {
        val s = _state.value
        val clickedIndex = s.doors.indexOfLast { door ->
            val r = door.hitboxFrac
            x in r.left..r.right && y in r.top..r.bottom
        }
        if (clickedIndex != -1) _state.update { it.copy(selectedDoorIndex = clickedIndex) }
    }

    fun dragDoor(x: Float, y: Float) {
        val s = _state.value
        val idx = s.selectedDoorIndex
        if (idx !in s.doors.indices) return
        val door = s.doors[idx]; val old = door.hitboxFrac
        val w = old.right - old.left; val h = old.bottom - old.top
        val newLeft = (x - w / 2f).coerceIn(0f, 1f - w)
        val newTop = (y - h / 2f).coerceIn(0f, 1f - h)
        val newList = s.doors.toMutableList()
        newList[idx] = door.copy(hitboxFrac = NormRect(newLeft, newTop, newLeft + w, newTop + h))
        _state.update { it.copy(doors = newList, designerDirty = true) }
    }

    fun resizeDoor(deltaW: Float, deltaH: Float) {
        val s = _state.value
        val idx = s.selectedDoorIndex
        if (idx !in s.doors.indices) return
        val door = s.doors[idx]; val old = door.hitboxFrac
        val w = ((old.right - old.left) + deltaW).coerceIn(0.02f, 1f)
        val h = ((old.bottom - old.top) + deltaH).coerceIn(0.02f, 1f)
        val cx = (old.left + old.right) / 2f; val cy = (old.top + old.bottom) / 2f
        val newLeft = (cx - w / 2f).coerceIn(0f, 1f - w)
        val newTop = (cy - h / 2f).coerceIn(0f, 1f - h)
        val newList = s.doors.toMutableList()
        newList[idx] = door.copy(hitboxFrac = NormRect(newLeft, newTop, newLeft + w, newTop + h))
        _state.update { it.copy(doors = newList, designerDirty = true) }
    }

    fun toggleMapDesignerMode() {
        _state.update { it.copy(mapDesignerMode = !it.mapDesignerMode, mapDesignerMoveMode = false) }
    }

    fun toggleMapDesignerMoveMode() {
        _state.update { it.copy(mapDesignerMoveMode = !it.mapDesignerMoveMode) }
    }

    fun updateMapSearchQuery(query: String) {
        _state.update { it.copy(mapSearchQuery = query) }
    }

    fun addGlobalWaypoint(x: Float, y: Float, stationName: String) {
        val newWp = ZoneDoor(NormRect(x - 0.02f, y - 0.02f, x + 0.02f, y + 0.02f), stationName, stationName, DoorKind.GENERIC)
        val list = _state.value.globalWaypoints + newWp
        _state.update { it.copy(globalWaypoints = list, selectedGlobalWaypointIndex = list.size - 1) }
    }

    fun selectGlobalWaypointAt(x: Float, y: Float) {
        val list = _state.value.globalWaypoints
        val idx = list.indexOfFirst { door ->
            val r = door.hitboxFrac
            x in r.left..r.right && y in r.top..r.bottom
        }
        _state.update { it.copy(selectedGlobalWaypointIndex = idx) }
    }

    fun moveSelectedGlobalWaypointTo(x: Float, y: Float) {
        val idx = _state.value.selectedGlobalWaypointIndex; if (idx == -1) return
        val list = _state.value.globalWaypoints.toMutableList()
        val door = list[idx]
        val hw = (door.hitboxFrac.right - door.hitboxFrac.left) / 2
        val hh = (door.hitboxFrac.bottom - door.hitboxFrac.top) / 2
        list[idx] = door.copy(hitboxFrac = NormRect(x - hw, y - hh, x + hw, y + hh))
        _state.update { it.copy(globalWaypoints = list) }
    }

    fun moveSelectedGlobalWaypointBy(dx: Float, dy: Float) {
        val idx = _state.value.selectedGlobalWaypointIndex; if (idx == -1) return
        val list = _state.value.globalWaypoints.toMutableList()
        val door = list[idx]; val rect = door.hitboxFrac
        val newLeft = (rect.left + dx).coerceIn(0f, 1f - (rect.right - rect.left))
        val newTop = (rect.top + dy).coerceIn(0f, 1f - (rect.bottom - rect.top))
        list[idx] = door.copy(hitboxFrac = NormRect(newLeft, newTop, newLeft + (rect.right - rect.left), newTop + (rect.bottom - rect.top)))
        _state.update { it.copy(globalWaypoints = list) }
    }

    fun deleteSelectedGlobalWaypoint() {
        val idx = _state.value.selectedGlobalWaypointIndex; if (idx == -1) return
        val list = _state.value.globalWaypoints.toMutableList()
        list.removeAt(idx)
        _state.update { it.copy(globalWaypoints = list, selectedGlobalWaypointIndex = -1) }
    }

    fun saveGlobalWaypoints() {
        val globalPrefs = context.getSharedPreferences(config.mapGlobalPrefsName, Context.MODE_PRIVATE)
        globalPrefs.edit().putString("global_waypoints", gson.toJson(_state.value.globalWaypoints)).apply()
        _state.update { it.copy(messageToast = getLocalizedString(config.msgGlobalWaypointsSavedRes)) }
        viewModelScope.launch { delay(2000); _state.update { it.copy(messageToast = null) } }
    }

    private fun updateCollisionGrid(rows: List<String>) {
        if (rows.isEmpty()) return
        val rCount = rows.size
        val cCount = rows[0].length
        val g = Array(rCount) { IntArray(cCount) }
        for (r in 0 until rCount) {
            val s = rows[r]
            for (c in 0 until cCount) {
                g[r][c] = if (c < s.length && s[c] == '#') 0 else 1
            }
        }
        collisionGrid = CollisionGrid(g)
    }

    class Factory(
        private val context: Context,
        private val config: TransitSystemConfig,
        private val stationName: String,
        private val spawnX: Float,
        private val spawnY: Float
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransitInteriorViewModel(
                context = context.applicationContext,
                settingsRepository = SettingsRepository(context.applicationContext),
                config = config,
                stationName = stationName,
                spawnX = spawnX,
                spawnY = spawnY
            ) as T
        }
    }
}
