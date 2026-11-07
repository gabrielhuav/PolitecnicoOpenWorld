package ovh.gabrielhuav.pow.features.map_exterior.ui

/**
 * Contrato puro entre el estado del mundo y el WebView cuando el jugador cambia entre
 * peatón y vehículo. Mantiene todas las capas ancladas al mismo centro de viewport.
 */
internal data class WebVehicleRenderPlan(
    val rotationDegrees: Float,
    val shouldRecenterAfterResize: Boolean,
    val showAuxiliaryPlayerMarker: Boolean,
)

internal fun webVehicleRenderPlan(
    isDriving: Boolean,
    previousDriving: Boolean?,
    vehicleRotation: Float,
    isUserPanningMap: Boolean,
): WebVehicleRenderPlan = WebVehicleRenderPlan(
    rotationDegrees = if (isDriving) -vehicleRotation else 0f,
    shouldRecenterAfterResize = !isUserPanningMap && previousDriving != isDriving,
    // El marcador Leaflet solo representa al jugador mientras explora el mapa libremente.
    // En juego normal PlayerCharacter es la única representación visible.
    showAuxiliaryPlayerMarker = isUserPanningMap,
)

/** Un `null` indica que el HTML todavía no expuso el puente JS del viewport. */
internal fun shouldCommitVehicleViewportTransition(
    javascriptResult: String,
    hasLocation: Boolean,
): Boolean = hasLocation && (javascriptResult == "true" || javascriptResult == "false")
