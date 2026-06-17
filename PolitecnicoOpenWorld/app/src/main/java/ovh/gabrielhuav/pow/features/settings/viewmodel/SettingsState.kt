package ovh.gabrielhuav.pow.features.settings.viewmodel

import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.Map,
    val mapProvider: MapProvider = MapProvider.CARTO_VOYAGER, // Default: CARTO Voyager (web; sirve z20 → calles más nítidas que OSM Web)
    val showCacheWidget: Boolean = true,
    val showFpsWidget: Boolean = false,
    val showZoomWidget: Boolean = false, // widget de nivel de zoom (Interfaz)
    val showSpeedometer: Boolean = true, // widget velocímetro al conducir (Interfaz)
    val showCoordsWidget: Boolean = false, // widget de coordenadas X/Y/Z (Interfaz)
    val developerMode: Boolean = false, // Modo Desarrollador: revela botones/opciones de prueba que se ocultarán en la versión final

    // ─── Audio: volumen de música y efectos (0f..1f) ─────────────────────────
    val musicVolume: Float = 1.0f,
    val sfxVolume: Float = 1.0f,
    // ─── Valores COMMITEADOS (los que el juego usa de verdad) ────────────────
    val controlType: ControlType = ControlType.JOYSTICK, // Default: JOYSTICK
    val controlsScale: Float = 1.0f, // Rango recomendado: 0.6f a 1.4f
    val swapControls: Boolean = false, // false = Izq: Movimiento, Der: Acción
    val showRoadNetwork: Boolean = true,

    // ─── Jugabilidad: población de NPCs ──────────────────────────────────────
    val npcDensity: Float = 1.0f,        // multiplicador de cantidad de NPCs (0.4–1.6)
    val npcEmojiLod: Boolean = false,    // "Optimizar dibujado de NPCs": NPCs lejanos como emoji
    val npcFullEmoji: Boolean = false,   // "Optimizar para gama baja": TODOS los NPCs como emoji

    // ─── i18n: idioma de la UI (BCP-47; "" = seguir el del sistema) ──────────
    val language: String = "",

    // ─── Valores TEMPORALES de controles ─────────────────────────────────────
    // La UI de controles edita estos; NO afectan al juego hasta presionar GUARDAR,
    // momento en el que se copian a los committeados de arriba y se persisten.
    val tempControlType: ControlType = ControlType.JOYSTICK,
    val tempControlsScale: Float = 1.0f,
    val tempSwapControls: Boolean = false
) {
    /** ¿Hay cambios de controles sin guardar? */
    val hasUnsavedControlChanges: Boolean
        get() = tempControlType != controlType ||
                tempControlsScale != controlsScale ||
                tempSwapControls != swapControls
}