package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update

/**
 * Parcial de CÁMARA/ZOOM y toggles de WIDGETS de [WorldMapViewModel] (extraído para reducir el
 * tamaño del VM; mismo paquete `viewmodel`). Zoom automático por estado (a pie/conduciendo/rápido),
 * zoom manual, canal de pinch, centrado/pan de cámara y los toggles de widgets de la UI.
 *
 * Son EXTENSIONES del VM: solo tocan miembros `internal`/`public` (`_uiState`, `MAX_SPEED`,
 * y los campos de interpolación `autoZoomMode`/`targetZoomLevel`, que viven en el VM como `internal`)
 * + las consts top-level de `WorldMapState.kt` (`ZOOM_ON_FOOT`/`ZOOM_DRIVING`/`ZOOM_DRIVING_FAST`).
 * Sin gemelo miembro. Call-sites FUERA del paquete (MainActivity, WorldMapScreen, NativeOsmMap,
 * MapJsBridge) importan estas extensiones explícitamente. MVVM intacto.
 */

fun WorldMapViewModel.toggleCacheWidget(show: Boolean) { _uiState.update { it.copy(showCacheWidget = show) } }
fun WorldMapViewModel.toggleFpsWidget(show: Boolean) { _uiState.update { it.copy(showFpsWidget = show) } }

fun WorldMapViewModel.toggleZoomWidget(show: Boolean) { _uiState.update { it.copy(showZoomWidget = show) } }

fun WorldMapViewModel.toggleSpeedometer(show: Boolean) { _uiState.update { it.copy(showSpeedometer = show) } }
fun WorldMapViewModel.toggleCoordsWidget(show: Boolean) { _uiState.update { it.copy(showCoordsWidget = show) } }
fun WorldMapViewModel.updateShowCacheWidget(show: Boolean) = _uiState.update { it.copy(showCacheWidget = show) }
fun WorldMapViewModel.updateShowFpsWidget(show: Boolean) = _uiState.update { it.copy(showFpsWidget = show) }

internal fun WorldMapViewModel.updateAutoZoom() {
    val st = _uiState.value
    val absSpeed = kotlin.math.abs(st.vehicleSpeed)
    val newMode = when {
        !st.isDriving -> 0
        autoZoomMode == 2 -> if (absSpeed < MAX_SPEED * 0.65) 1 else 2
        else -> if (absSpeed >= MAX_SPEED * 0.85) 2 else 1
    }
    if (newMode != autoZoomMode) {
        autoZoomMode = newMode
        targetZoomLevel = when (newMode) {
            0 -> ZOOM_ON_FOOT
            1 -> ZOOM_DRIVING
            else -> ZOOM_DRIVING_FAST
        }
    }
    // Interpolar zoomLevel hacia targetZoomLevel para transición suave
    val currentZoom = st.zoomLevel
    if (Math.abs(currentZoom - targetZoomLevel) > 0.01) {
        val newZoom = currentZoom + (targetZoomLevel - currentZoom) * 0.1 // 10% por tick
        _uiState.update { it.copy(zoomLevel = newZoom) }
    }
}

fun WorldMapViewModel.zoomIn()  {
    targetZoomLevel = (_uiState.value.zoomLevel + 1.0).coerceAtMost(22.0)
    _uiState.update { if (it.zoomLevel < 22.0) it.copy(zoomLevel = targetZoomLevel) else it }
}
fun WorldMapViewModel.zoomOut() {
    targetZoomLevel = (_uiState.value.zoomLevel - 1.0).coerceAtLeast(14.0)
    _uiState.update { if (it.zoomLevel > 14.0) it.copy(zoomLevel = targetZoomLevel) else it }
}

// Canal de retorno del zoom por GESTO (pinch de dos dedos) desde el mapa (web,
// OSM nativo o Google). Sin esto, el bucle de render volvía a fijar el zoom al
// valor del estado y el pinch "rebotaba". Acota a los límites de juego.
fun WorldMapViewModel.onMapZoomChanged(zoom: Double) {
    if (!zoom.isFinite()) return
    // Cuantizamos a pasos de 0.5 y solo actualizamos si el cambio es grande. Así el
    // zoom por gesto no produce micro-cambios continuos que invaliden el estado (y con
    // él la caché de sprites de NPC, cuya clave depende del tamaño en píxeles → zoom).
    val z = (Math.round(zoom * 2.0) / 2.0).coerceIn(14.0, 22.0)
    if (Math.abs(z - _uiState.value.zoomLevel) >= 0.5) {
        targetZoomLevel = z
        _uiState.update { it.copy(zoomLevel = z) }
    }
}

fun WorldMapViewModel.centerOnPlayer() { _uiState.update { it.copy(isUserPanningMap = false) } }

/** Centra en el jugador Y acerca al máximo nivel de zoom permitido. */
fun WorldMapViewModel.zoomToPlayer() {
    // Fijamos TAMBIEN el objetivo de interpolacion: si no, updateAutoZoom() arrastraba
    // el zoom de vuelta al valor anterior (p. ej. tras un zoom out) y "rebotaba".
    targetZoomLevel = 22.0
    _uiState.update { it.copy(isUserPanningMap = false, zoomLevel = 22.0) }
}

fun WorldMapViewModel.onMapPanStart() { _uiState.update { it.copy(isUserPanningMap = true) } }
fun WorldMapViewModel.onMapPanEnd() { }
