package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CollisionPolygon
import ovh.gabrielhuav.pow.domain.models.CollisionWall
import ovh.gabrielhuav.pow.domain.models.GeoNode

// ─── EDITOR DEL DEBUG INTERIORES ──────────────────────────────────────────────
// Extensiones del WorldMapViewModel (sin gemelo miembro: funciones nuevas). Permiten
// EDITAR las líneas del overlay de Debug Interiores caminando con el jugador y soltando
// puntos, igual que el "Creador de Rutas". La geometría editada se guarda en campos de
// estado (debugEdit*), se dibuja en vivo (NativeOsmMap) y se exporta/importa a JSON con
// el formato de exterior_collisions.json (polygons + walls) + una sección navPaths.
// Estado SIEMPRE inmutable (_state.update { it.copy(...) }); las Views solo emiten
// intenciones. Ver doc 04/09.

// DTO de import/export combinado: colisiones rojas (formato exterior_collisions.json) +
// caminos del navGraph editados (verde peatonal / naranja autos).
data class DebugCollisionsExport(
    val polygons: List<CollisionPolygon> = emptyList(),
    val walls: List<CollisionWall> = emptyList(),
    val navPaths: List<DebugNavPath> = emptyList()
)

data class DebugNavPath(
    val isForPeople: Boolean = true,
    val points: List<GeoNode> = emptyList()
)

// Selecciona la herramienta de edición (color/tipo). Cambiar de herramienta descarta
// la forma en curso para no mezclar tipos.
fun WorldMapViewModel.setDebugEditTool(tool: DebugEditTool) {
    _uiState.update { it.copy(debugEditTool = tool, debugEditPoints = emptyList()) }
}

// Captura un punto en la posición ACTUAL del jugador (se añade a la forma en curso).
fun WorldMapViewModel.captureDebugEditPoint() {
    val s = _uiState.value
    if (s.debugEditTool == DebugEditTool.NONE) return
    val loc = s.currentLocation ?: return
    _uiState.update { it.copy(debugEditPoints = it.debugEditPoints + loc) }
}

// Deshace el último punto capturado de la forma en curso.
fun WorldMapViewModel.undoDebugEditPoint() {
    _uiState.update {
        if (it.debugEditPoints.isEmpty()) it
        else it.copy(debugEditPoints = it.debugEditPoints.dropLast(1))
    }
}

// "Commitea" la forma en curso a su lista según la herramienta activa y limpia los
// puntos. WALL/BLOCK necesitan ≥2 / ≥3 puntos; los caminos del navGraph ≥2.
fun WorldMapViewModel.finishDebugEditShape() {
    val s = _uiState.value
    val pts = s.debugEditPoints
    when (s.debugEditTool) {
        DebugEditTool.WALL -> {
            if (pts.size < 2) return
            // Cada par consecutivo de puntos es una barda (segmento rojo).
            val newWalls = pts.zipWithNext().mapIndexed { i, (a, b) ->
                CollisionWall("barda_editada_${System.currentTimeMillis()}_$i",
                    a.latitude, a.longitude, b.latitude, b.longitude)
            }
            _uiState.update { it.copy(debugEditWalls = it.debugEditWalls + newWalls, debugEditPoints = emptyList()) }
        }
        DebugEditTool.BLOCK -> {
            if (pts.size < 3) return
            val poly = CollisionPolygon("zona_editada_${System.currentTimeMillis()}",
                pts.map { GeoNode(it.latitude, it.longitude) })
            _uiState.update { it.copy(debugEditBlocks = it.debugEditBlocks + poly, debugEditPoints = emptyList()) }
        }
        DebugEditTool.NAV_PED -> {
            if (pts.size < 2) return
            _uiState.update { it.copy(debugEditNavPed = it.debugEditNavPed + listOf(pts), debugEditPoints = emptyList()) }
        }
        DebugEditTool.NAV_CAR -> {
            if (pts.size < 2) return
            _uiState.update { it.copy(debugEditNavCar = it.debugEditNavCar + listOf(pts), debugEditPoints = emptyList()) }
        }
        DebugEditTool.NONE -> {}
    }
}

// Borra TODA la geometría editada (rojas + caminos) y la forma en curso. No toca las
// colisiones cargadas del archivo (exteriorCollisions), que se siguen dibujando aparte.
fun WorldMapViewModel.clearDebugEdits() {
    _uiState.update {
        it.copy(
            debugEditPoints = emptyList(),
            debugEditWalls = emptyList(),
            debugEditBlocks = emptyList(),
            debugEditNavPed = emptyList(),
            debugEditNavCar = emptyList()
        )
    }
}

// Exporta la geometría editada + la cargada del archivo a un JSON (formato
// exterior_collisions.json: polygons + walls; más una sección navPaths para los caminos
// verde/naranja). Listo para pegar en assets/exterior_collisions.json.
fun WorldMapViewModel.exportDebugEditsToUri(context: Context, uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val s = _uiState.value
            val base = s.exteriorCollisions
            val polygons = (base?.polygons ?: emptyList()) + s.debugEditBlocks
            val walls = (base?.walls ?: emptyList()) + s.debugEditWalls
            val navPaths = s.debugEditNavPed.map { path ->
                DebugNavPath(true, path.map { GeoNode(it.latitude, it.longitude) })
            } + s.debugEditNavCar.map { path ->
                DebugNavPath(false, path.map { GeoNode(it.latitude, it.longitude) })
            }
            val json = Gson().toJson(DebugCollisionsExport(polygons, walls, navPaths))
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al exportar el JSON de colisiones editadas", e)
        }
    }
}

// Importa un JSON (formato DebugCollisionsExport) a las listas EDITADAS para seguir
// ajustándolas y verlas sobre el mapa.
fun WorldMapViewModel.importDebugEditsFromUri(context: Context, uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
            val jsonString = input?.bufferedReader().use { it?.readText() } ?: return@launch
            val data = Gson().fromJson(jsonString, DebugCollisionsExport::class.java) ?: return@launch
            val navPed = data.navPaths.filter { it.isForPeople }
                .map { p -> p.points.map { GeoPoint(it.lat, it.lon) } }
            val navCar = data.navPaths.filter { !it.isForPeople }
                .map { p -> p.points.map { GeoPoint(it.lat, it.lon) } }
            _uiState.update {
                it.copy(
                    debugEditWalls = data.walls,
                    debugEditBlocks = data.polygons,
                    debugEditNavPed = navPed,
                    debugEditNavCar = navCar,
                    debugEditPoints = emptyList()
                )
            }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al importar el JSON de colisiones editadas", e)
        }
    }
}
