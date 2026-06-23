package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.usecases.CalculateLocalCoordinatesUseCase
import java.util.Locale

/** Un nodo capturado en la textura local de un edificio. */
data class RouteNode(
    val localX: Float,
    val localY: Float,
    val isParking: Boolean
)

/** Estado inmutable del diseñador de rutas/estacionamientos. */
data class DesignerState(
    val currentLaneId: Int = 1,
    val isParkingMode: Boolean = false,
    val capturedNodes: List<RouteNode> = emptyList()
)

/**
 * Orquesta la captura de coordenadas del Modo Diseñador de forma reactiva (StateFlow), reemplazando el
 * antiguo flujo basado en Logcat (`WorldMapViewModel.debugPlayerLocalCoordinates`). Acumula los nodos EN
 * MEMORIA para exportarlos luego a un archivo .json vía SAF (ver `MainActivity`).
 *
 * Es una clase plana (no `ViewModel` de Android): `MainActivity` la sostiene como campo y la pasa a
 * `WorldMapScreen`; así MainActivity puede leer su estado (`serializeNodesToJson`) para el guardado SAF.
 * El Modo Diseñador es in-game (orientación bloqueada), por lo que no se necesita sobrevivir a cambios
 * de configuración; exporta antes de salir.
 *
 * MVVM: solo expone estado inmutable + intenciones; el cálculo lo hace el caso de uso PURO.
 */
class DesignerViewModel(
    private val calculateLocalCoordinates: CalculateLocalCoordinatesUseCase = CalculateLocalCoordinatesUseCase()
) {

    private val _state = MutableStateFlow(DesignerState())
    val state: StateFlow<DesignerState> = _state.asStateFlow()

    /** Alterna la categoría de los próximos nodos (ruta normal vs cajón de estacionamiento). */
    fun toggleParkingMode(enabled: Boolean) {
        _state.update { it.copy(isParkingMode = enabled) }
    }

    /**
     * Empieza un carril NUEVO: incrementa el id y VACÍA los nodos acumulados (se asume que ya exportaste
     * el carril anterior). Las "migas de pan" visuales las limpia `WorldMapViewModel` desde el call-site.
     * (Difiere del prototipo, que conservaba los nodos — eso re-exportaba el carril previo con otro id.)
     */
    fun startNewLane() {
        _state.update {
            it.copy(
                currentLaneId = it.currentLaneId + 1,
                capturedNodes = emptyList()
            )
        }
    }

    /**
     * "CAPTURAR": convierte la ubicación GPS a coordenada local del edificio seleccionado, valida el
     * margen (±0.15 = overscan legacy) y, si cae dentro, acumula el nodo. Reemplaza la escritura a Logcat.
     *
     * @return `true` si el punto era válido y se capturó; `false` si quedó fuera del edificio (el call-site
     *         usa esto para la miga visual + el Toast, igual que el flujo anterior).
     */
    fun onCaptureClicked(landmark: Landmark, currentLoc: GeoPoint): Boolean {
        val local = calculateLocalCoordinates(
            pointLat = currentLoc.latitude,
            pointLon = currentLoc.longitude,
            centerLat = landmark.location.latitude,
            centerLon = landmark.location.longitude,
            rotationAngle = landmark.rotationAngle,
            baseWidthMeters = landmark.baseWidthMeters,
            baseHeightMeters = landmark.baseHeightMeters,
            scaleX = landmark.scaleX,
            scaleY = landmark.scaleY
        )

        if (!local.isValid(margin = 0.15f)) return false

        val node = RouteNode(local.x, local.y, _state.value.isParkingMode)
        _state.update { it.copy(capturedNodes = it.capturedNodes + node) }
        return true
    }

    /** ¿Hay algo que exportar? */
    fun hasNodes(): Boolean = _state.value.capturedNodes.isNotEmpty()

    /**
     * Serializa los nodos del carril actual al MISMO formato JSON que imprimía el flujo legacy
     * (ids 1-indexed, decimales con punto vía `Locale.US`, descripciones en español).
     */
    fun serializeNodesToJson(): String {
        val s = _state.value
        val laneId = s.currentLaneId
        val sb = StringBuilder()
        sb.append("{\n  \"nodes\": [\n")
        s.capturedNodes.forEachIndexed { index, node ->
            val formX = String.format(Locale.US, "%.4f", node.localX)
            val formY = String.format(Locale.US, "%.4f", node.localY)
            val desc = if (node.isParking) "Cajón de estacionamiento" else "Punto de ruta (Carril $laneId)"
            sb.append(
                """
                |    {
                |      "id": ${index + 1},
                |      "localX": $formX,
                |      "localY": $formY,
                |      "isParkingSlot": ${node.isParking},
                |      "description": "$desc"
                |    }
                """.trimMargin()
            )
            sb.append(if (index < s.capturedNodes.size - 1) ",\n" else "\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }
}
