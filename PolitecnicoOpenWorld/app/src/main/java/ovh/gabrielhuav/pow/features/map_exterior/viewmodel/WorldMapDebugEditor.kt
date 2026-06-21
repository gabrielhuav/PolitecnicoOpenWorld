package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CollisionPolygon
import ovh.gabrielhuav.pow.domain.models.map.CollisionWall
import ovh.gabrielhuav.pow.domain.models.map.GeoNode

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

// Selecciona la herramienta de edición (color/tipo). Mientras haya una herramienta
// activa, el mapa NO panea: el touch dibuja (ver NativeOsmMap). NONE = volver a panear.
fun WorldMapViewModel.setDebugEditTool(tool: DebugEditTool) {
    _uiState.update { it.copy(debugEditTool = tool) }
}

// Commitea un TRAZO dibujado con el dedo (arrastre en el mapa, estilo Paint). La View
// (NativeOsmMap) convierte los píxeles del gesto a coordenadas (`projection.fromPixels`)
// y llama aquí: líneas (WALL/NAV_*) = [inicio, fin]; zonas (BLOCK) = 4 esquinas del
// rectángulo. Se añade a la lista del tipo correspondiente.
fun WorldMapViewModel.commitDebugStroke(tool: DebugEditTool, points: List<GeoPoint>) {
    if (points.size < 2) return
    when (tool) {
        DebugEditTool.WALL -> {
            // Cada par consecutivo de puntos es una barda (segmento rojo).
            val newWalls = points.zipWithNext().mapIndexed { i, (a, b) ->
                CollisionWall("barda_editada_${System.currentTimeMillis()}_$i",
                    a.latitude, a.longitude, b.latitude, b.longitude)
            }
            _uiState.update { it.copy(debugEditWalls = it.debugEditWalls + newWalls) }
        }
        DebugEditTool.BLOCK -> {
            if (points.size < 3) return
            val poly = CollisionPolygon("zona_editada_${System.currentTimeMillis()}",
                points.map { GeoNode(it.latitude, it.longitude) })
            _uiState.update { it.copy(debugEditBlocks = it.debugEditBlocks + poly) }
        }
        DebugEditTool.NAV_PED ->
            _uiState.update { it.copy(debugEditNavPed = it.debugEditNavPed + listOf(points)) }
        DebugEditTool.NAV_CAR ->
            _uiState.update { it.copy(debugEditNavCar = it.debugEditNavCar + listOf(points)) }
        DebugEditTool.NONE -> {}
    }
}

// Deshace el ÚLTIMO trazo dibujado del tipo de la herramienta activa (si NONE, intenta
// quitar de cualquier lista no vacía, en orden razonable).
fun WorldMapViewModel.undoLastDebugShape() {
    _uiState.update { s ->
        when {
            s.debugEditTool == DebugEditTool.WALL && s.debugEditWalls.isNotEmpty() ->
                s.copy(debugEditWalls = s.debugEditWalls.dropLast(1))
            s.debugEditTool == DebugEditTool.BLOCK && s.debugEditBlocks.isNotEmpty() ->
                s.copy(debugEditBlocks = s.debugEditBlocks.dropLast(1))
            s.debugEditTool == DebugEditTool.NAV_PED && s.debugEditNavPed.isNotEmpty() ->
                s.copy(debugEditNavPed = s.debugEditNavPed.dropLast(1))
            s.debugEditTool == DebugEditTool.NAV_CAR && s.debugEditNavCar.isNotEmpty() ->
                s.copy(debugEditNavCar = s.debugEditNavCar.dropLast(1))
            s.debugEditNavCar.isNotEmpty() -> s.copy(debugEditNavCar = s.debugEditNavCar.dropLast(1))
            s.debugEditNavPed.isNotEmpty() -> s.copy(debugEditNavPed = s.debugEditNavPed.dropLast(1))
            s.debugEditWalls.isNotEmpty() -> s.copy(debugEditWalls = s.debugEditWalls.dropLast(1))
            s.debugEditBlocks.isNotEmpty() -> s.copy(debugEditBlocks = s.debugEditBlocks.dropLast(1))
            else -> s
        }
    }
}

// Borra TODA la geometría editada (rojas + caminos). No toca las colisiones cargadas del
// archivo (exteriorCollisions), que se siguen dibujando aparte.
fun WorldMapViewModel.clearDebugEdits() {
    _uiState.update {
        it.copy(
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
                    debugEditNavCar = navCar
                )
            }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al importar el JSON de colisiones editadas", e)
        }
    }
}
