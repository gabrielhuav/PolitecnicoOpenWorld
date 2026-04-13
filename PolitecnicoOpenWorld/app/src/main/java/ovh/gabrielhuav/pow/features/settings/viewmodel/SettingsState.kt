package ovh.gabrielhuav.pow.features.settings.viewmodel

import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.Map,
    val mapProvider: MapProvider = MapProvider.OSM, // Usamos tu Enum nativo
    val showCacheWidget: Boolean = true,
    val showFpsWidget: Boolean = false
)