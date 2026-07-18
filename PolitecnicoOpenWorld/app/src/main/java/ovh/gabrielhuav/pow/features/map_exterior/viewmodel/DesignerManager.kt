package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CollisionPolygon
import ovh.gabrielhuav.pow.domain.models.map.CollisionWall
import ovh.gabrielhuav.pow.domain.models.map.GeoNode

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3 (descomposición del god-object, ver PLAN_descomponer_WorldMapViewModel.md):
// PRIMER manager con SUB-ESTADO PROPIO. Posee el estado del EDITOR DEL DEBUG INTERIORES
// (overlay + herramienta + geometría editada), antes 6 campos de WorldMapState escritos
// por extensiones del VM. El VM lo compone en `uiState` vía la FACHADA `combine` (la
// FORMA que ve la UI no cambia: las Views siguen leyendo uiState.debugEdit*).
//
// PATRÓN A REPLICAR en los siguientes managers (Collectibles, Combat, Wanted, …):
// - MutableStateFlow<XSubState> privado + StateFlow público; update { it.copy(...) }.
// - SIN dependencias de Android/VM: lógica pura → testeable en JVM.
// - Las extensiones del VM conservan su FIRMA y delegan aquí (las Views no se tocan).
// ─────────────────────────────────────────────────────────────────────────────

/** Sub-estado del editor de Debug Interiores (campos espejo de WorldMapState). */
data class DesignerEditState(
    val showInteriorDebugOverlay: Boolean = false,
    val debugEditTool: DebugEditTool = DebugEditTool.NONE,
    val debugEditWalls: List<CollisionWall> = emptyList(),     // bardas ROJAS editadas
    val debugEditBlocks: List<CollisionPolygon> = emptyList(), // zonas ROJAS editadas
    val debugEditNavPed: List<List<GeoPoint>> = emptyList(),   // caminos VERDES (peatonal)
    val debugEditNavCar: List<List<GeoPoint>> = emptyList()    // caminos NARANJAS (autos)
)

class DesignerManager {

    private val _state = MutableStateFlow(DesignerEditState())
    val state: StateFlow<DesignerEditState> = _state.asStateFlow()

    /** Muestra/oculta el overlay de Debug Interiores. */
    fun toggleOverlay(show: Boolean) {
        _state.update { it.copy(showInteriorDebugOverlay = show) }
    }

    /** Herramienta activa (con herramienta ≠ NONE el mapa no panea: el touch dibuja). */
    fun setTool(tool: DebugEditTool) {
        _state.update { it.copy(debugEditTool = tool) }
    }

    /**
     * Commitea un TRAZO dibujado con el dedo (la View convierte píxeles → GeoPoints y llama
     * aquí): líneas (WALL/NAV_*) = [inicio, fin]; zonas (BLOCK) = 4 esquinas del rectángulo.
     */
    fun commitStroke(tool: DebugEditTool, points: List<GeoPoint>) {
        if (points.size < 2) return
        when (tool) {
            DebugEditTool.WALL -> {
                // Cada par consecutivo de puntos es una barda (segmento rojo).
                val newWalls = points.zipWithNext().mapIndexed { i, (a, b) ->
                    CollisionWall("barda_editada_${System.currentTimeMillis()}_$i",
                        a.latitude, a.longitude, b.latitude, b.longitude)
                }
                _state.update { it.copy(debugEditWalls = it.debugEditWalls + newWalls) }
            }
            DebugEditTool.BLOCK -> {
                if (points.size < 3) return
                val poly = CollisionPolygon("zona_editada_${System.currentTimeMillis()}",
                    points.map { GeoNode(it.latitude, it.longitude) })
                _state.update { it.copy(debugEditBlocks = it.debugEditBlocks + poly) }
            }
            DebugEditTool.NAV_PED ->
                _state.update { it.copy(debugEditNavPed = it.debugEditNavPed + listOf(points)) }
            DebugEditTool.NAV_CAR ->
                _state.update { it.copy(debugEditNavCar = it.debugEditNavCar + listOf(points)) }
            DebugEditTool.NONE -> {}
        }
    }

    /** Deshace el ÚLTIMO trazo del tipo de la herramienta activa (o de cualquiera si NONE). */
    fun undoLastShape() {
        _state.update { s ->
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

    /** Borra TODA la geometría editada (no toca las colisiones cargadas del archivo). */
    fun clearEdits() {
        _state.update {
            it.copy(
                debugEditWalls = emptyList(),
                debugEditBlocks = emptyList(),
                debugEditNavPed = emptyList(),
                debugEditNavCar = emptyList()
            )
        }
    }

    /** Reemplaza la geometría editada con la de un JSON importado (para seguir ajustándola). */
    fun setImported(
        walls: List<CollisionWall>,
        blocks: List<CollisionPolygon>,
        navPed: List<List<GeoPoint>>,
        navCar: List<List<GeoPoint>>
    ) {
        _state.update {
            it.copy(
                debugEditWalls = walls,
                debugEditBlocks = blocks,
                debugEditNavPed = navPed,
                debugEditNavCar = navCar
            )
        }
    }
}
