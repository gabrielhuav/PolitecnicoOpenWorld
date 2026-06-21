package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.data.repository.CollisionMatrixRepository
import ovh.gabrielhuav.pow.data.repository.WaypointRepository
import ovh.gabrielhuav.pow.domain.models.zombie.CollisionMatrix
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import kotlin.math.roundToInt

/**
 * Parcial del MODO DISEÑADOR de [ZombieInteriorViewModel] (extraído para reducir el tamaño del
 * VM; mismo paquete). Edición de la MATRIZ de colisión y de los WAYPOINTS (puertas) de las
 * salas de interiores: pintar/redimensionar la rejilla, seleccionar/mover puertas, guardar/
 * descartar y exportar/importar JSON. MVVM: estado inmutable vía `_state.update { it.copy(...) }`.
 *
 * Gotcha miembro/extensión: estas funciones existen SOLO aquí (se eliminaron del VM, así que
 * NO hay gemelo miembro que gane). Solo tocan miembros `internal`/`public` del VM (`_state`,
 * `currentRoom()`, `applicationContext`, `viewModelScope`). Las constantes de rejilla viven en
 * el companion del VM → se referencian cualificadas (`ZombieInteriorViewModel.MIN_GRID`, etc.).
 * Los call-sites en `ui/ZombieGameScreen.kt` (otro paquete) importan estas extensiones.
 */

// ─── MODO DISEÑADOR DE LA MATRIZ DE COLISIÓN ───────────
fun ZombieInteriorViewModel.toggleDesignerMode() {
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
fun ZombieInteriorViewModel.setDesignerTarget(target: DesignerTarget) {
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

fun ZombieInteriorViewModel.setDesignerBrushWall(wall: Boolean) =
    _state.update { it.copy(designerBrushWall = wall) }

// ─── EDICIÓN DE WAYPOINTS (puertas) ────────────────────
/** Selecciona la puerta cuyo hitbox (fraccionario) contiene (fx,fy). */
fun ZombieInteriorViewModel.selectDoorAtWorld(xWorld: Float, yWorld: Float) {
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
fun ZombieInteriorViewModel.moveSelectedDoorToWorld(xWorld: Float, yWorld: Float) {
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
fun ZombieInteriorViewModel.saveDesignerWaypoints() {
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
fun ZombieInteriorViewModel.resetDesignerWaypoints() {
    val room = currentRoom()
    _state.update { it.copy(designerDoors = room.doors, designerDirty = false, selectedDoorIndex = -1) }
}

/** Pinta/borra la celda que contiene la coordenada de MUNDO (x,y). */
fun ZombieInteriorViewModel.paintCellAtWorld(xWorld: Float, yWorld: Float) {
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
fun ZombieInteriorViewModel.resizeDesignerMatrixBy(deltaCols: Int, deltaRows: Int) {
    val s = _state.value
    if (!s.designerMode || s.designerTarget != DesignerTarget.MATRIX || s.designerRows.isEmpty()) return
    val old = s.designerRows
    val oldRows = old.size
    val oldCols = old.maxOf { it.length }
    val newCols = (oldCols + deltaCols).coerceIn(ZombieInteriorViewModel.MIN_GRID, ZombieInteriorViewModel.MAX_GRID)
    val newRows = (oldRows + deltaRows).coerceIn(ZombieInteriorViewModel.MIN_GRID, ZombieInteriorViewModel.MAX_GRID)
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
fun ZombieInteriorViewModel.saveDesignerMatrix() {
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
fun ZombieInteriorViewModel.resetDesignerMatrix() {
    val room = currentRoom()
    val rows = room.collisionMatrix?.rows ?: defaultDesignerRows(room)
    _state.update { it.copy(designerRows = rows, designerDirty = false) }
}

fun ZombieInteriorViewModel.exportMatricesToUri(uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val json = CollisionMatrixRepository.exportJson(applicationContext)
            applicationContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            android.util.Log.e("ZombieGameVM", "Error exportando matrices", e)
        }
    }
}

fun ZombieInteriorViewModel.importMatricesFromUri(uri: android.net.Uri) {
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

fun ZombieInteriorViewModel.exportWaypointsToUri(uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val json = WaypointRepository.exportJson(applicationContext)
            applicationContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            android.util.Log.e("ZombieGameVM", "Error exportando waypoints", e)
        }
    }
}

fun ZombieInteriorViewModel.importWaypointsFromUri(uri: android.net.Uri) {
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
internal fun ZombieInteriorViewModel.defaultDesignerRows(room: ZombieRoom): List<String> {
    val cols = (room.gridCols ?: ZombieInteriorViewModel.DEFAULT_GRID_COLS).coerceAtLeast(3)
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
