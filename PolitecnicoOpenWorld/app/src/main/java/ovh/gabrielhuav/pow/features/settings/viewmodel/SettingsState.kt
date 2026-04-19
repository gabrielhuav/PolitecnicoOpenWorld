package ovh.gabrielhuav.pow.features.settings.viewmodel

import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.Map,
    val mapProvider: MapProvider = MapProvider.OSM, // Usamos tu Enum nativo
    val showCacheWidget: Boolean = true,
    val showFpsWidget: Boolean = false,
    val controlType: ControlType = ControlType.DPAD,
    val controlsScale: Float = 1.0f, // Rango recomendado: 0.6f a 1.4f
    val swapControls: Boolean = false, // false = Izq: Movimiento, Der: Acción
    val freeNavigation: Boolean = false
)