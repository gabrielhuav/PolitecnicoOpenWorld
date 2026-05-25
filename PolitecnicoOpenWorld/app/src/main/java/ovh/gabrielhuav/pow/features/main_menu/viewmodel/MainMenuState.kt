package ovh.gabrielhuav.pow.features.main_menu.viewmodel

import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider

data class MainMenuState(
    val isLoading: Boolean = false,
    val selectedProvider: MapProvider = MapProvider.OSM,
    val showCacheWidget: Boolean = true,
    val showFpsWidget: Boolean = false,
    val showMultiplayerDialog: Boolean = false, // ← Controla el diálogo de nombre
    val playerName: String = "",                // ← Guarda el nombre

    // ─── Warm-up del servidor de Render (plan gratuito) ────────────
    // Cuando el usuario toca "MULTIJUGADOR" calentamos el server antes
    // de mostrar el diálogo de nombre, porque en el plan free Render
    // suspende el servicio tras minutos de inactividad y tarda hasta
    // ~50 s en levantar. Mientras dura este proceso bloqueamos la UI
    // con un spinner cancelable.
    val isWarmingUp: Boolean = false,
    val warmupSeconds: Int = 0,
    val warmupFailed: Boolean = false
)
