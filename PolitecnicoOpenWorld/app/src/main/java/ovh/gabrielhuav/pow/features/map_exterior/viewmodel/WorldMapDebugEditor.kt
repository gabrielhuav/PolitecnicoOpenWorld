package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CollisionPolygon
import ovh.gabrielhuav.pow.domain.models.map.CollisionWall
import ovh.gabrielhuav.pow.domain.models.map.GeoNode

// ─── EDITOR DEL DEBUG INTERIORES ──────────────────────────────────────────────
// ETAPA 3 (descomposición): el ESTADO y la LÓGICA de edición viven ahora en
// `DesignerManager` (sub-estado propio, compuesto en uiState por la fachada combine del
// VM). Estas extensiones CONSERVAN SU FIRMA (las Views no se tocaron) y solo DELEGAN;
// el export/import se queda aquí porque mezcla el sub-estado con `exteriorCollisions`
// (estado base del VM) e I/O con Context. Ver PLAN_descomponer_WorldMapViewModel.md.

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
fun WorldMapViewModel.setDebugEditTool(tool: DebugEditTool) = designerManager.setTool(tool)

// Commitea un TRAZO dibujado con el dedo (la View convierte píxeles → GeoPoints).
fun WorldMapViewModel.commitDebugStroke(tool: DebugEditTool, points: List<GeoPoint>) =
    designerManager.commitStroke(tool, points)

// Deshace el ÚLTIMO trazo dibujado (del tipo de la herramienta activa).
fun WorldMapViewModel.undoLastDebugShape() = designerManager.undoLastShape()

// Borra TODA la geometría editada (no toca las colisiones cargadas del archivo).
fun WorldMapViewModel.clearDebugEdits() = designerManager.clearEdits()

// Exporta la geometría editada + la cargada del archivo a un JSON (formato
// exterior_collisions.json: polygons + walls; más una sección navPaths para los caminos
// verde/naranja). Listo para pegar en assets/exterior_collisions.json.
fun WorldMapViewModel.exportDebugEditsToUri(context: Context, uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val edits = designerManager.state.value
            val base = _uiState.value.exteriorCollisions
            val polygons = (base?.polygons ?: emptyList()) + edits.debugEditBlocks
            val walls = (base?.walls ?: emptyList()) + edits.debugEditWalls
            val navPaths = edits.debugEditNavPed.map { path ->
                DebugNavPath(true, path.map { GeoNode(it.latitude, it.longitude) })
            } + edits.debugEditNavCar.map { path ->
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
            designerManager.setImported(data.walls, data.polygons, navPed, navCar)
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al importar el JSON de colisiones editadas", e)
        }
    }
}
