package ovh.gabrielhuav.pow.features.interiores.escom.viewmodel

import android.content.Context
import ovh.gabrielhuav.pow.data.repository.MetroRepository
import ovh.gabrielhuav.pow.data.repository.MetrobusRepository
import ovh.gabrielhuav.pow.domain.models.map.TransitStation
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.NormRect
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor

/** Eje por el que entra/sale el vehículo en la animación de la pantalla. */
enum class TransitAnimationAxis { VERTICAL, HORIZONTAL }

/** Tipo de fondo del overlay del mapa de la red. */
enum class TransitOverlayType { VIDEO, GRADIENT }

/**
 * FUENTE ÚNICA de configuración de un sistema de transporte (Metro, Metrobús y futuros: Suburbano,
 * Mexibús, Tren Ligero…). El `TransitInteriorViewModel` es genérico y se comporta IGUAL para todos;
 * lo único que cambia entre sistemas vive AQUÍ. **Añadir un transporte nuevo = una entrada en
 * `TransitSystems` + sus assets + una ruta en MainActivity.** No se duplica lógica.
 *
 * Colores como `Long` (ARGB 0xFF…) para no acoplar el modelo a Compose; conviértelos con `Color(argb)`
 * en la capa UI.
 */
data class TransitSystemConfig(
    /** Id estable (prefijo de prefs y de ruta de navegación). P. ej. "metro", "metrobus". */
    val key: String,
    /** Nombre visible del sistema. P. ej. "METRO", "METROBÚS". */
    val systemName: String,

    // ── Assets (rutas dentro de assets/) ──────────────────────────────────────────────────────
    /** Prefijo común, p. ej. "TRANSIT/METRO/". Los JSON de matriz/waypoints cuelgan de aquí. */
    val assetsPrefix: String,
    /** Fondo del interior (inside.png suele estar bajo assetsPrefix). */
    val insideBackground: String,
    /** Imagen del mapa de la red (¡ojo! metro=map.png, metrobús=mapa.png). */
    val mapImage: String,
    /** Sprites del vehículo (frame 1 entrando, frame 2 de fondo). Extensión variable (.webp/.png). */
    val vehicle1Asset: String,
    val vehicle2Asset: String,
    /** Directorio base de los sprites del jugador (metro="SPRITES/PLAYER/", metrobús="PRINCIPAL/"). */
    val spriteBaseDir: String,
    /** Vídeo de fondo del overlay (solo si overlayType==VIDEO; si no, null). */
    val overlayVideoAsset: String? = null,

    // ── Persistencia (SharedPreferences) ──────────────────────────────────────────────────────
    /** Prefijo de prefs por estación: "${prefsPrefix}station_$stationName". */
    val prefsPrefix: String,

    // ── Datos de la red ───────────────────────────────────────────────────────────────────────
    /** Carga las estaciones del sistema (apunta al repositorio correspondiente). */
    val loadStations: (Context) -> List<TransitStation>,

    // ── Spawn y torniquetes (lógica del VM) ───────────────────────────────────────────────────
    val defaultSpawnX: Float,
    val defaultSpawnY: Float,
    /** Puertas por defecto si no hay JSON ni guardado (los rects difieren por sistema). */
    val defaultDoors: List<ZoneDoor>,
    /** Desplazamiento Y al cruzar el torniquete hacia el andén (metro=+0.23, metrobús=−0.50). La
     *  salida usa el inverso. */
    val turnstileBoardDeltaY: Float,

    // ── Animación / branding (lo usa la pantalla) ─────────────────────────────────────────────
    val animationAxis: TransitAnimationAxis,
    val animationDurationInMs: Int,
    val animationDurationOutMs: Int,
    val loopWaitMs: Int,
    val primaryColorArgb: Long,
    val screenBgArgb: Long,
    val interactionPromptBgArgb: Long,
    val designerToolbarBgArgb: Long,
    val designerToolbarBorderArgb: Long,
    val overlayType: TransitOverlayType,
    val overlayGradientTopArgb: Long = 0xFF000000,
    val overlayGradientBottomArgb: Long = 0xFF000000,

    // ── Strings (in-VM, español; i18n pendiente, ver ANALISIS_codigo §4) ──────────────────────
    val msgTicketReloaded: String,
    val msgNoBalance: String,
    val msgWaitVehicle: String,
    val msgExitTurnstile: String,
    val msgGlobalWaypointsSaved: String
) {
    /** Ruta de navegación de la pantalla de estación (sin args). */
    val stationRoutePrefix: String get() = "${key}_station_interior"
    fun stationPrefsName(stationName: String) = "${prefsPrefix}station_$stationName"
    val mapGlobalPrefsName: String get() = "${prefsPrefix}map_global"
    val matrixAsset: String get() = "${assetsPrefix}matrix.json"
    val waypointsAsset: String get() = "${assetsPrefix}waypoints.json"
    val globalWaypointsAsset: String get() = "${assetsPrefix}global_waypoints.json"
}

/**
 * Catálogo de sistemas de transporte. **Para añadir Suburbano/Mexibús: copia un bloque, ajusta
 * assets/colores/strings/eje, y registra su ruta en MainActivity con `TransitSystems.SUBURBANO`.**
 */
object TransitSystems {
    val METRO = TransitSystemConfig(
        key = "metro",
        systemName = "METRO",
        assetsPrefix = "TRANSIT/METRO/",
        insideBackground = "TRANSIT/METRO/inside.png",
        mapImage = "TRANSIT/METRO/map.png",
        vehicle1Asset = "TRANSIT/METRO/metro1.webp",
        vehicle2Asset = "TRANSIT/METRO/metro2.webp",
        spriteBaseDir = "SPRITES/PLAYER/",
        overlayVideoAsset = "TRANSIT/METRO/video.mp4",
        prefsPrefix = "metro_",
        loadStations = { ctx -> MetroRepository.loadStations(ctx) },
        defaultSpawnX = 0.5f,
        defaultSpawnY = 0.15f,
        defaultDoors = listOf(
            ZoneDoor(NormRect(0.20f, 0.40f, 0.35f, 0.55f), "taquilla", "Taquilla", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.45f, 0.60f, 0.60f, 0.70f), "torniquetes", "Torniquetes", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.10f, 0.10f, 0.90f, 0.30f), "anden", "Andén", DoorKind.GENERIC)
        ),
        turnstileBoardDeltaY = 0.23f,
        animationAxis = TransitAnimationAxis.VERTICAL,
        animationDurationInMs = 1500,
        animationDurationOutMs = 2000,
        loopWaitMs = 4000,
        primaryColorArgb = 0xFFF07B00,
        screenBgArgb = 0xFF0D0D11,
        interactionPromptBgArgb = 0xFF3B0D1B,
        designerToolbarBgArgb = 0xFF1E1E24,
        designerToolbarBorderArgb = 0xFFD4AF37,
        overlayType = TransitOverlayType.VIDEO,
        msgTicketReloaded = "Has recargado tu tarjeta del metro",
        msgNoBalance = "No tienes saldo disponible",
        msgWaitVehicle = "Debes esperar a que llegue otro tren",
        msgExitTurnstile = "Has salido de los torniquetes. Ve a la taquilla para recargar tu tarjeta.",
        msgGlobalWaypointsSaved = "Waypoints globales guardados"
    )

    val METROBUS = TransitSystemConfig(
        key = "metrobus",
        systemName = "METROBÚS",
        assetsPrefix = "TRANSIT/METROBUS/",
        insideBackground = "TRANSIT/METROBUS/inside.png",
        mapImage = "TRANSIT/METROBUS/mapa.png",
        vehicle1Asset = "TRANSIT/METROBUS/bus1.png",
        vehicle2Asset = "TRANSIT/METROBUS/bus2.png",
        spriteBaseDir = "PRINCIPAL/",
        overlayVideoAsset = null,
        prefsPrefix = "metrobus_",
        loadStations = { ctx -> MetrobusRepository.loadStations(ctx) },
        defaultSpawnX = 0.5f,
        defaultSpawnY = 0.75f,
        defaultDoors = listOf(
            ZoneDoor(NormRect(0.15f, 0.35f, 0.35f, 0.50f), "taquilla", "Taquilla", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.42f, 0.52f, 0.62f, 0.64f), "torniquetes", "Torniquetes", DoorKind.GENERIC),
            ZoneDoor(NormRect(0.08f, 0.08f, 0.92f, 0.28f), "anden", "Andén", DoorKind.GENERIC)
        ),
        turnstileBoardDeltaY = -0.50f,
        animationAxis = TransitAnimationAxis.HORIZONTAL,
        animationDurationInMs = 1800,
        animationDurationOutMs = 2000,
        loopWaitMs = 5000,
        primaryColorArgb = 0xFFC21D24,
        screenBgArgb = 0xFF0D0202,
        interactionPromptBgArgb = 0xFFC21D24,
        designerToolbarBgArgb = 0xFF1E0808,
        designerToolbarBorderArgb = 0xFFC21D24,
        overlayType = TransitOverlayType.GRADIENT,
        overlayGradientTopArgb = 0xFF2D0808,
        overlayGradientBottomArgb = 0xFF0D0202,
        msgTicketReloaded = "Has recargado tu tarjeta del Metrobús",
        msgNoBalance = "No tienes saldo disponible en tu tarjeta",
        msgWaitVehicle = "Debes esperar a que llegue otro autobús",
        msgExitTurnstile = "Has salido de los torniquetes. Ve a la taquilla para recargar tu tarjeta.",
        msgGlobalWaypointsSaved = "Waypoints del Metrobús guardados"
    )
}
