package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import android.app.Application
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
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import android.net.Uri
import ovh.gabrielhuav.pow.domain.models.zombie.NormRect
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.Direction
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import ovh.gabrielhuav.pow.data.repository.MetroRepository

class MetroInteriorViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val stationName: String,
    private val spawnX: Float = -1f,
    private val spawnY: Float = -1f
) : ViewModel() {

    private val _state = MutableStateFlow(
        MetroInteriorState(
            controlType = settingsRepository.getControlType(),
            controlsScale = settingsRepository.getControlsScale(),
            swapControls = settingsRepository.getSwapControls()
        )
    )
    val state: StateFlow<MetroInteriorState> = _state.asStateFlow()

    private var idleJob: Job? = null
    private var collisionGrid: CollisionGrid = CollisionGrid.emptyWithBorder()
    
    // Configuración del tamaño de la matriz
    private var gridRows = 30
    private var gridCols = 20

    // Constantes de pasos de movimiento (coordenadas [0,1])
    private val WALK_STEP = 0.004f
    private val RUN_STEP = 0.008f

    private val prefs = context.getSharedPreferences("metro_station_$stationName", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        val savedRows = prefs.getString("matrix", null)
        val savedDoors = prefs.getString("doors", null)

        var initialRows = if (savedRows != null) {
            try {
                gson.fromJson<List<String>>(savedRows, object : TypeToken<List<String>>() {}.type)
            } catch (e: Exception) { null }
        } else null
        
        if (initialRows.isNullOrEmpty()) {
            try {
                context.assets.open("TRANSIT/METRO/matrix.json").use { inp ->
                    val json = InputStreamReader(inp).readText()
                    initialRows = gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
                }
            } catch (e: Exception) { }
        }

        val defaultRows = initialRows ?: List(gridRows) { r ->
            if (r == 0 || r == gridRows - 1) "#".repeat(gridCols)
            else "#" + ".".repeat(gridCols - 2) + "#"
        }

        // Sincronizar gridRows/gridCols con la matriz cargada
        gridRows = defaultRows.size
        gridCols = defaultRows.maxOfOrNull { it.length } ?: gridCols
        
        var initialDoors = if (savedDoors != null) {
            try {
                gson.fromJson<List<ZoneDoor>>(savedDoors, object : TypeToken<List<ZoneDoor>>() {}.type)
            } catch (e: Exception) { null }
        } else null
        
        if (initialDoors.isNullOrEmpty()) {
            try {
                context.assets.open("TRANSIT/METRO/waypoints.json").use { inp ->
                    val json = InputStreamReader(inp).readText()
                    initialDoors = gson.fromJson<List<ZoneDoor>>(json, object : TypeToken<List<ZoneDoor>>() {}.type)
                }
            } catch (e: Exception) { }
        }

        val defaultDoors = initialDoors ?: listOf(
            ZoneDoor(NormRect(0.20f, 0.40f, 0.35f, 0.55f), "taquilla", "Taquilla", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.45f, 0.60f, 0.60f, 0.70f), "torniquetes", "Torniquetes", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.10f, 0.10f, 0.90f, 0.30f), "anden", "Andén", DoorKind.GENERIC)
        )
        
        // Cargar mapa global
        val globalPrefs = context.getSharedPreferences("metro_map_global", Context.MODE_PRIVATE)
        val savedGlobalWaypoints = globalPrefs.getString("global_waypoints", null)
        
        var initialGlobalWaypoints: List<ZoneDoor>? = null
        if (savedGlobalWaypoints != null) {
            try {
                initialGlobalWaypoints = gson.fromJson<List<ZoneDoor>>(savedGlobalWaypoints, object : TypeToken<List<ZoneDoor>>() {}.type)
            } catch (e: Exception) { }
        }
        
        if (initialGlobalWaypoints.isNullOrEmpty()) {
            try {
                context.assets.open("TRANSIT/METRO/global_waypoints.json").use { inp ->
                    val json = InputStreamReader(inp).readText()
                    initialGlobalWaypoints = gson.fromJson<List<ZoneDoor>>(json, object : TypeToken<List<ZoneDoor>>() {}.type)
                }
            } catch (e: Exception) { }
        }

        val allStations = MetroRepository.loadStations(context)

        var startX = 0.5f
        var startY = 0.15f
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
                allMetroStations = allStations,
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
            collisionGrid.isWalkable(clampedX, curY) -> clampedX to curY
            collisionGrid.isWalkable(curX, clampedY) -> curX to clampedY
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
        _state.update {
            it.copy(playerX = x, playerY = y, playerAction = action, isFacingRight = facing)
        }

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
                _state.update { it.copy(hasRechargedTicket = true, messageToast = "Has recargado tu tarjeta del metro") }
                viewModelScope.launch {
                    delay(2000)
                    _state.update { it.copy(messageToast = null) }
                }
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
                            _state.update { it.copy(playerY = startY + fraction * 0.23f, playerAction = PlayerAction.WALK) }
                            delay(stepDelay)
                        }
                        _state.update { it.copy(areControlsEnabled = true, playerAction = PlayerAction.IDLE) }
                    }
                } else {
                    _state.update { it.copy(messageToast = "No tienes saldo disponible") }
                    viewModelScope.launch {
                        delay(2000)
                        _state.update { it.copy(messageToast = null) }
                    }
                }
            }
            "anden" -> {
                if (!_state.value.isMetro1Departing) {
                    _state.update { it.copy(isMetro1Animating = true, areControlsEnabled = false, isBoardingWalkActive = true) }
                } else {
                    _state.update { it.copy(messageToast = "Debes esperar a que llegue otro tren") }
                    viewModelScope.launch {
                        delay(2000)
                        _state.update { it.copy(messageToast = null) }
                    }
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
                        _state.update { it.copy(playerY = (startY - fraction * 0.23f).coerceAtLeast(0f), playerAction = PlayerAction.WALK) }
                        delay(stepDelay)
                    }
                    _state.update {
                        it.copy(
                            areControlsEnabled = true,
                            playerAction = PlayerAction.IDLE,
                            hasRechargedTicket = false,
                            messageToast = "Has salido de los torniquetes. Ve a la taquilla para recargar tu tarjeta."
                        )
                    }
                    delay(3000)
                    _state.update { it.copy(messageToast = null) }
                }
            }
            "salida" -> {
                // Señaliza al Screen que debe ejecutar onExit
                _state.update { it.copy(exitStationRequested = true) }
            }
            else -> {
                _state.update { it.copy(messageToast = "Interacción con ${door.label}") }
                viewModelScope.launch {
                    delay(2000)
                    _state.update { it.copy(messageToast = null) }
                }
            }
        }
    }

    fun closeMetroMap() {
        _state.update { it.copy(showMetroMap = false) }
        viewModelScope.launch {
            idleJob?.cancel()
            _state.update { it.copy(isPlayerVisible = true, playerAction = PlayerAction.WALK, isFacingRight = false, areControlsEnabled = false, isDisembarkingWalkActive = true) }
            val startX = _state.value.playerX
            val targetX = startX - 0.05f
            val steps = 20
            val stepDelay = 600L / steps
            for (i in 1..steps) {
                val fraction = i.toFloat() / steps
                _state.update { it.copy(playerX = startX + (targetX - startX) * fraction, playerAction = PlayerAction.WALK) }
                delay(stepDelay)
            }
            _state.update { it.copy(playerAction = PlayerAction.IDLE, areControlsEnabled = true, isMetro1Departing = true, isDisembarkingWalkActive = false) }
            
            delay(5000)
            _state.update { it.copy(isMetro1Departing = false) }
        }
    }

    fun onMetro1AnimationFinished() {
        if (_state.value.isMetro1Animating) {
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
                    _state.update { it.copy(
                        isPlayerVisible = false,
                        playerAction = PlayerAction.IDLE,
                        isBoardingWalkActive = false,
                        isMetro1Animating = false,
                        showMetroMap = true
                    ) }
                }
            } else {
                _state.update { it.copy(isMetro1Animating = false, showMetroMap = true) }
            }
        } else if (_state.value.spawnWithAnimation) {
            viewModelScope.launch {
                idleJob?.cancel()
                delay(500) // Esperar a que el metro se asiente
                val startX = _state.value.playerX
                val targetX = startX - 0.05f
                
                _state.update { it.copy(spawnWithAnimation = false, isPlayerVisible = true, playerAction = PlayerAction.WALK, isFacingRight = false, areControlsEnabled = false, isDisembarkingWalkActive = true) }
                
                val steps = 20
                val stepDelay = 600L / steps
                for (i in 1..steps) {
                    val fraction = i.toFloat() / steps
                    _state.update { it.copy(playerX = startX + (targetX - startX) * fraction, playerAction = PlayerAction.WALK) }
                    delay(stepDelay)
                }
                _state.update { it.copy(playerAction = PlayerAction.IDLE, areControlsEnabled = true, isMetro1Departing = true, isDisembarkingWalkActive = false) }
                delay(5000)
                _state.update { it.copy(isMetro1Departing = false) }
            }
        }
    }

    /** Consume la solicitud de salida después de que el Screen haya llamado onExit. */
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
        
        // Usar las dimensiones reales de la matriz cargada, no los valores fijos
        val currentRows = s.designerRows.toMutableList()
        val rows = currentRows.size
        val cols = currentRows.maxOfOrNull { it.length } ?: 0
        if (rows == 0 || cols == 0) return

        val c = (normalizedX * cols).toInt().coerceIn(0, cols - 1)
        val r = (normalizedY * rows).toInt().coerceIn(0, rows - 1)

        val rowStr = currentRows[r].padEnd(cols, '.')
        val charArr = rowStr.toCharArray()
        val oldChar = charArr[c]
        val newChar = if (s.designerBrushWall) '#' else '.'
        if (oldChar == newChar) return

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
                val json = gson.toJson(_state.value.designerRows)
                out.write(json.toByteArray())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importMatricesFromUri(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val json = InputStreamReader(inp).readText()
                val rows = gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
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
                val json = gson.toJson(_state.value.doors)
                out.write(json.toByteArray())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importWaypointsFromUri(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val json = InputStreamReader(inp).readText()
                val ds = gson.fromJson<List<ZoneDoor>>(json, object : TypeToken<List<ZoneDoor>>() {}.type)
                if (ds != null) {
                    _state.update { it.copy(doors = ds, designerDirty = true) }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun exportGlobalWaypointsToUri(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val json = gson.toJson(_state.value.globalWaypoints)
                out.write(json.toByteArray())
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun importGlobalWaypointsFromUri(uri: Uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                val json = InputStreamReader(inp).readText()
                val ds = gson.fromJson<List<ZoneDoor>>(json, object : TypeToken<List<ZoneDoor>>() {}.type)
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
        var currentCols = gridCols
        var currentRows = gridRows
        val newCols = (currentCols + deltaCols).coerceIn(5, 100)
        val newRows = (currentRows + deltaRows).coerceIn(5, 100)
        if (newCols == currentCols && newRows == currentRows) return

        val oldRows = _state.value.designerRows
        val newRowsList = mutableListOf<String>()
        for (r in 0 until newRows) {
            if (r < oldRows.size) {
                var rowStr = oldRows[r]
                if (newCols > currentCols) rowStr += ".".repeat(newCols - currentCols)
                else if (newCols < currentCols) rowStr = rowStr.substring(0, newCols)
                newRowsList.add(rowStr)
            } else {
                newRowsList.add(".".repeat(newCols))
            }
        }
        gridCols = newCols
        gridRows = newRows

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
        if (clickedIndex != -1) {
            _state.update { it.copy(selectedDoorIndex = clickedIndex) }
        }
    }

    fun dragDoor(x: Float, y: Float) {
        val s = _state.value
        val idx = s.selectedDoorIndex
        if (idx !in s.doors.indices) return

        val door = s.doors[idx]
        val old = door.hitboxFrac
        val w = old.right - old.left
        val h = old.bottom - old.top

        val newLeft = (x - w / 2f).coerceIn(0f, 1f - w)
        val newTop = (y - h / 2f).coerceIn(0f, 1f - h)
        val newHitbox = NormRect(newLeft, newTop, newLeft + w, newTop + h)

        val newList = s.doors.toMutableList()
        newList[idx] = door.copy(hitboxFrac = newHitbox)
        _state.update { it.copy(doors = newList, designerDirty = true) }
    }

    fun resizeDoor(deltaW: Float, deltaH: Float) {
        val s = _state.value
        val idx = s.selectedDoorIndex
        if (idx !in s.doors.indices) return

        val door = s.doors[idx]
        val old = door.hitboxFrac
        
        var w = (old.right - old.left) + deltaW
        var h = (old.bottom - old.top) + deltaH
        
        w = w.coerceIn(0.02f, 1f)
        h = h.coerceIn(0.02f, 1f)
        
        val cx = (old.left + old.right) / 2f
        val cy = (old.top + old.bottom) / 2f
        
        val newLeft = (cx - w / 2f).coerceIn(0f, 1f - w)
        val newTop = (cy - h / 2f).coerceIn(0f, 1f - h)
        val newHitbox = NormRect(newLeft, newTop, newLeft + w, newTop + h)
        
        val newList = s.doors.toMutableList()
        newList[idx] = door.copy(hitboxFrac = newHitbox)
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
        val idx = _state.value.selectedGlobalWaypointIndex
        if (idx == -1) return
        val list = _state.value.globalWaypoints.toMutableList()
        val door = list[idx]
        val hw = (door.hitboxFrac.right - door.hitboxFrac.left) / 2
        val hh = (door.hitboxFrac.bottom - door.hitboxFrac.top) / 2
        list[idx] = door.copy(hitboxFrac = NormRect(x - hw, y - hh, x + hw, y + hh))
        _state.update { it.copy(globalWaypoints = list) }
    }

    fun moveSelectedGlobalWaypointBy(dx: Float, dy: Float) {
        val idx = _state.value.selectedGlobalWaypointIndex
        if (idx == -1) return
        val list = _state.value.globalWaypoints.toMutableList()
        val door = list[idx]
        val rect = door.hitboxFrac
        val newLeft = (rect.left + dx).coerceIn(0f, 1f - (rect.right - rect.left))
        val newTop = (rect.top + dy).coerceIn(0f, 1f - (rect.bottom - rect.top))
        val newRight = newLeft + (rect.right - rect.left)
        val newBottom = newTop + (rect.bottom - rect.top)
        list[idx] = door.copy(hitboxFrac = NormRect(newLeft, newTop, newRight, newBottom))
        _state.update { it.copy(globalWaypoints = list) }
    }

    fun deleteSelectedGlobalWaypoint() {
        val idx = _state.value.selectedGlobalWaypointIndex
        if (idx == -1) return
        val list = _state.value.globalWaypoints.toMutableList()
        list.removeAt(idx)
        _state.update { it.copy(globalWaypoints = list, selectedGlobalWaypointIndex = -1) }
    }

    fun saveGlobalWaypoints() {
        val globalPrefs = context.getSharedPreferences("metro_map_global", Context.MODE_PRIVATE)
        globalPrefs.edit().putString("global_waypoints", gson.toJson(_state.value.globalWaypoints)).apply()
        _state.update { it.copy(messageToast = "Waypoints globales guardados") }
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
        private val stationName: String,
        private val spawnX: Float,
        private val spawnY: Float
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MetroInteriorViewModel(
                context = context.applicationContext,
                settingsRepository = SettingsRepository(context.applicationContext),
                stationName = stationName,
                spawnX = spawnX,
                spawnY = spawnY
            ) as T
        }
    }
}
