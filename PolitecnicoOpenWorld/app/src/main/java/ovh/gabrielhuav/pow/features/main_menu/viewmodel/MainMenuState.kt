package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider

data class MainMenuState(
    val isLoading: Boolean = false,
    val selectedProvider: MapProvider = MapProvider.OSM
)
