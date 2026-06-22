package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import com.google.android.gms.location.LocationServices
import android.content.res.Configuration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.gson.Gson
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceNpcSpriteManager
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import ovh.gabrielhuav.pow.domain.models.map.EscomBoundingBox
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.TeleportCatalog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.AssetPickerDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedyHireDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ObjectivesWidget
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DesignerPanel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuGroup
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuItem
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionsMenu
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CoordsWidget
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerCharacter
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleDPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleJoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.Ps4ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.ZOOM_GAMEPLAY_OSM
// REFACTOR: funciones del VM extraídas a parciales (WorldMapProviders/Designer) →
// ahora son extensiones y requieren import explícito desde el paquete ui.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.addLandmarkAtPlayer
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.cancelPendingProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.commitMapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.deleteSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.exportLandmarksToUri
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.importLandmarksFromUri
// Editor del Debug Interiores: seleccionar herramienta / deshacer / limpiar / exportar (extensiones del VM).
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.undoLastDebugShape
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.commitDebugStroke
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.clearDebugEdits
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setDebugEditTool
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.DebugEditTool
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleCampaignRouteNpcsDebug
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.exportDebugEditsToUri
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.importDebugEditsFromUri
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.loadLandmarks
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.moveSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.prepareMapForEntry
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.rotateSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.saveSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.scaleXSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.scaleYSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.showAssetPicker
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleDesignerMode
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.ZOOM_GAMEPLAY_WEB
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// REFACTOR: zoom/cámara extraídos a WorldMapCameraUi.kt (extensiones) → import explícito.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onMapZoomChanged
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.centerOnPlayer
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.zoomToPlayer
// REFACTOR: skin extraído a WorldMapSettings.kt (extensiones) → import explícito.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleSkinSelector
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectSkin
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.refreshSkin
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.isActive
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.dismissPrankedyDialog
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onHirePrankedy
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.togglePrankedy
// REFACTOR: extensiones del VM extraídas (teleport/puerta ESCOM) → import explícito.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.teleportTo
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleTeleportMenu
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onEscomDoorFadeComplete
import kotlin.math.cos
import androidx.compose.runtime.DisposableEffect

// ─── CULLING DE NPCs POR DISTANCIA ──────────────────────────────────────────
// Los NPC siguen viviendo en memoria/simulación; solo dibujamos los que caen
// dentro del viewport visible. El radio escala con el zoom (metros por pixel),
// así nunca se ocultan NPCs que de verdad están en pantalla.
// FIX "veo NPCs fuera del fog of war": el margen era +15 m sobre el radio de
// neblina (70 m), así que los civiles se dibujaban hasta 85 m, fuera de la zona
// despejada. A 0 m el culling de sprites coincide EXACTO con el borde del fog
// (los 3 renderers usan npcVisionRadiusMeters). La policía fuera del fog sigue
// mostrándose como waypoint 🚓 (handoff limpio en 70 m) y Prankedy aparte.
internal const val NPC_CULL_MARGIN_M = 0.0

// ══════════════════════════════════════════════════════════════════════════
//  RADIO DE VISIÓN (neblina). ⬅️  CAMBIA ESTE VALOR PARA VER MÁS O MENOS.
//  Está en METROS REALES, por eso NO cambia al hacer zoom: siempre ves la misma
//  distancia alrededor del jugador. Súbelo para ver más lejos, bájalo para menos.
internal const val NPC_FOG_VISION_METERS = 70.0
// ══════════════════════════════════════════════════════════════════════════

/** Radio de culling de NPCs: fijo en metros, independiente del zoom. */
internal fun npcVisionRadiusMeters(): Double = NPC_FOG_VISION_METERS + NPC_CULL_MARGIN_M

/** Metros por pixel del mapa a un zoom/latitud dados (proyección Web Mercator). */
internal fun metersPerPixel(zoom: Double, latDeg: Double): Double =
    156543.03392 * cos(Math.toRadians(latDeg)) / 2.0.pow(zoom)

/** ¿El NPC está dentro del radio del jugador? (aprox. plana, suficiente a esta escala). */
internal fun npcWithinRadius(
    npcLat: Double, npcLon: Double, centerLat: Double, centerLon: Double, radiusM: Double
): Boolean {
    val dLat = (npcLat - centerLat) * 111_320.0
    val dLon = (npcLon - centerLon) * 111_320.0 * cos(Math.toRadians(centerLat))
    return dLat * dLat + dLon * dLon <= radiusM * radiusM
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel(factory = WorldMapViewModel.Factory(context)),
    onNavigateToMainMenu: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToInterior: (String) -> Unit = {},
    onRequestSaveGame: () -> Unit = {},
    // MODO HISTORIA: reintentar la misión fallida sin volver al menú (recarga el slot activo).
    onRetryMission: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // Modo Desarrollador: si está APAGADO se ocultan los botones de prueba del menú de Opciones
    // (Teletransportarse, Diseñador/Debug, Activar Apocalipsis, Desactivar Prankedy). Se lee al entrar.
    val devModeContext = androidx.compose.ui.platform.LocalContext.current
    val developerMode = remember { ovh.gabrielhuav.pow.data.repository.SettingsRepository(devModeContext).getDeveloperMode() }
    val roadNetwork by viewModel.roadNetworkFlow.collectAsState()
    val escomItems by viewModel.escomItems.collectAsState()
    val allCollectibles = uiState.activeCollectibles + escomItems
    val base64Cache = remember { mutableStateMapOf<String, String>() }
    val widthCache = remember { mutableStateMapOf<String, Float>() }
    val heightCache = remember { mutableStateMapOf<String, Float>() }
    // OPT memoria gama baja (≤2 GB): esta caché de drawables (NPCs, patrullas, balas,
    // collectibles…) se indexa por FIRMA VISUAL (incluye salud/zoom/frame), así que en
    // sesiones largas crecía SIN LÍMITE y podía agotar la RAM (OOM). La acotamos con un
    // LRU por orden de acceso (mismo patrón que googleMapsIconCache): al pasar el tope se
    // descarta la entrada más vieja; si vuelve a hacer falta se regenera (idéntico en
    // pantalla). Sigue siendo un MutableMap, así que getOrPut/iterator no cambian.
    val nativeDrawableCache = remember {
        object : java.util.LinkedHashMap<String, android.graphics.drawable.Drawable>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.drawable.Drawable>?): Boolean {
                return size > 384
            }
        }
    }
    val registeredWebImages = remember { mutableSetOf<String>() }
    val googleMapsIconCache = remember {
        object : java.util.LinkedHashMap<String, com.google.android.gms.maps.model.BitmapDescriptor>(150, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.google.android.gms.maps.model.BitmapDescriptor>?): Boolean {
                return size > 2000
            }
        }
    }
    val gson = remember { Gson() }
    val coroutineScope = rememberCoroutineScope()
    // REFACTOR: `yButtonHoldJob` se movió a WorldMapControls.kt (la pulsación larga de Y/△
    // vive ahora junto a los controles).

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportLandmarksToUri(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importLandmarksFromUri(context, it) }
    }
    // Editor del Debug Interiores: exportar/importar la geometría editada (colisiones + caminos).
    val collisionsExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportDebugEditsToUri(context, it) }
    }
    val collisionsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importDebugEditsFromUri(context, it) }
    }

    val landmarkBitmapCache = remember { mutableMapOf<String, android.graphics.Bitmap?>() }
    var hasTriggeredNativePan by remember { mutableStateOf(false) }

    // Nuevos estados para las interacciones del Diseñador
    var showDesignerHint by remember { mutableStateOf(false) }
    var showExitDesignerConfirm by remember { mutableStateOf(false) }
    var originalLandmarkState by remember { mutableStateOf<ovh.gabrielhuav.pow.domain.models.map.Landmark?>(null) }

    // Efecto para controlar la leyenda efímera de 3 segundos
    LaunchedEffect(uiState.isDesignerMode) {
        if (uiState.isDesignerMode) {
            showDesignerHint = true
            delay(3000)
            showDesignerHint = false
        }
    }

    // Captura del snapshot del landmark seleccionado para revertir de forma precisa
    LaunchedEffect(uiState.selectedLandmarkId) {
        val currentId = uiState.selectedLandmarkId
        if (currentId != null) {
            originalLandmarkState = uiState.landmarks.find { it.id == currentId }
        } else {
            originalLandmarkState = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadLandmarks(context)
        viewModel.showInitialHealthBar()
    }

    //Configuración de ciclo de vida de la skin
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSkin()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // El game loop del mapa global es Activity-scoped y NO se detiene al entrar a un interior; por eso
    // su bloque de audio (stopWalk cada tick con el jugador exterior quieto) PISABA el sonido de pasos de
    // los interiores. Marcamos cuándo el mapa global está en primer plano (gatea ese audio en el VM) y
    // PARAMOS sus sonidos al salir del mapa, para que el interior gestione el suyo sin interferencia.
    DisposableEffect(Unit) {
        viewModel.worldMapForeground = true
        onDispose {
            viewModel.worldMapForeground = false
            viewModel.soundManager.stopWalk()
            viewModel.soundManager.stopRun()
            viewModel.soundManager.stopCar()
        }
    }

    // Cuando el video de carga termina y hay un destino pendiente, navegar.
    // Si la interacción fue con la mano (pendingZombieMinigame), vamos al minijuego
    // de zombis (que arranca en el lobby/croquis). Si no, al interior normal.
    LaunchedEffect(uiState.showZombiVideo, uiState.pendingInteriorDestination) {
        val target = uiState.pendingInteriorDestination
        if (target != null && !uiState.showZombiVideo) {
            viewModel.clearPendingInteriorDestination()
            if (viewModel.pendingZombieMinigame) {
                viewModel.clearPendingZombieMinigame()
                onNavigateToInterior("interiores_zombies")
            } else {
                onNavigateToInterior(target.routeName)
            }
        }
    }

    var currentFps by remember { mutableIntStateOf(0) }
    if (uiState.showFpsWidget) {
        LaunchedEffect(Unit) {
            var frameCount = 0
            var lastTime = System.currentTimeMillis()
            while (true) {
                withFrameNanos {
                    frameCount++
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastTime >= 1000) {
                        currentFps = frameCount
                        frameCount = 0
                        lastTime = currentTime
                    }
                }
            }
        }
    }

    val tileCache = viewModel.tileCache
    val cachingClient = remember(tileCache) {
        CachingWebViewClient(
            tileCache          = tileCache,
            getCurrentProvider = { viewModel.uiState.value.mapProvider },
            onTileServed       = { fromCache -> viewModel.notifyTileSource(fromCache) }
        )
    }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // OPT gama baja: última lista de NPCs enviada al WebView. Solo reenviamos al JS
    // cuando la lista cambia (~10 Hz), no en cada recomposición por moverse el jugador.
    val lastWebNpcHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.map.Npc>>(1) }
    // OPT FPS web (ahora proveedor por defecto): la lista de landmarks solo cambia al
    // editarlos/cargarlos, pero su JSON se serializaba y enviaba al WebView en CADA frame.
    // Guardamos la última referencia para reenviar updateLandmarks solo cuando cambie.
    val lastWebLandmarkHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.map.Landmark>>(1) }
    // Heartbeat: re-enviar landmarks al WebView cada ~45 frames por si el primer envío
    // (al cambiar la lista) llegó antes de que el HTML definiera updateLandmarks.
    val webLmTick = remember { intArrayOf(0) }
    // Si en el frame anterior se enviaron waypoints de patrulla al WebView, para poder
    // limpiarlos al dejar de estar buscado sin spamear updatePolice cuando no hay policías.
    val lastWebPoliceHolder = remember { booleanArrayOf(false) }
    val lastWebZombieHolder = remember { booleanArrayOf(false) }
    // 🚇 Estaciones de metro (estáticas): se reenvían al WebView solo al cambiar la lista
    // (+ heartbeat), como los landmarks. El icono se carga del asset TRANSIT/METRO/icon.webp.
    val lastWebMetroHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.map.MetroStation>>(1) }
    val webMetroTick = remember { intArrayOf(0) }
    // Debug Interiores (web): solo reenviamos el navGraph al WebView cuando cambia el
    // estado del overlay o la lista de landmarks (no por frame).
    val lastWebIpOn = remember { booleanArrayOf(false) }
    val lastWebIpLm = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.map.Landmark>>(1) }
    val lastWebIpColl = remember { arrayOfNulls<ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig>(1) }
    val nativeMapRef = remember { mutableStateOf<MapView?>(null) }

    // ─── ESTADO DEL MENÚ DE OPCIONES (con submenús anidados) ──────────────────
    var optionsExpanded by remember { mutableStateOf(false) }
    var optionsOpenGroup by remember { mutableStateOf<String?>(null) }
    // NOTA: el menú de Opciones in-game NO cambia la orientación (el juego va SIEMPRE en
    // horizontal). Solo los menús de RUTA (Ajustes, etc.) permiten rotar — lo gestiona
    // MainActivity por destino de navegación. Ver 09.

    LaunchedEffect(uiState.isUserPanningMap) {
        if (!uiState.isUserPanningMap) {
            webViewRef.value?.evaluateJavascript("if(typeof exitExplorationMode==='function')exitExplorationMode();", null)
        } else {
            // Al arrastrar el mapa, abrir el menú directamente en el submenú "Mapa"
            // (zoom, centrar, waypoint…). El acordeón cierra cualquier otro submenú.
            optionsExpanded = true
            optionsOpenGroup = "mapa"
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0D0D11))
        .systemBarsPadding()) {

        // ─── GATE DE CARGA: no se entra hasta tener ubicación, calles Y mapa ─────
        // Cuando hay ubicación y calles, se descarga el mapa del proveedor actual.
        LaunchedEffect(uiState.currentLocation != null, uiState.isRoadNetworkReady) {
            if (uiState.currentLocation != null && uiState.isRoadNetworkReady) {
                viewModel.prepareMapForEntry()
            }
        }
        // El mundo NO se revela hasta que la ESCENA esté REALMENTE lista: tiles + calles + el/los
        // landmark(s) cercano(s) DECODIFICADO(S) (p. ej. la ENTRADA de la ESCOM) + NPCs/coches ya
        // sembrados. Así la pantalla de carga dura lo necesario (más en gama baja) y no se entra
        // "en blanco" ni sin coches/NPCs.
        var sceneReady by remember { mutableStateOf(false) }
        // Reinicia el gate en cada (re)carga del mundo (teleport, volver de interior).
        LaunchedEffect(uiState.isMapReady) { if (!uiState.isMapReady) sceneReady = false }
        // SONDEO: con tiles+calles listos, decodifica los assets de landmarks cercanos y espera a
        // que (1) exista al menos un landmark cercano y TODOS estén decodificados, y (2) ya haya
        // NPCs/coches sembrados. Timeout de seguridad de 15 s para no atascar JAMÁS la carga.
        LaunchedEffect(uiState.isMapReady, uiState.isRoadNetworkReady) {
            if (!uiState.isMapReady || !uiState.isRoadNetworkReady) return@LaunchedEffect
            val start = System.currentTimeMillis()
            var lastLog = 0L
            while (isActive) {
                val loc = uiState.currentLocation
                if (loc != null) {
                    val nearby = uiState.landmarks.filter {
                        kotlin.math.abs(it.location.latitude - loc.latitude) < 0.02 &&
                            kotlin.math.abs(it.location.longitude - loc.longitude) < 0.02
                    }
                    // Decodifica (una vez) los assets de landmarks que falten en la caché.
                    for (lm in nearby) {
                        if (landmarkBitmapCache.containsKey(lm.assetPath)) continue
                        val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                context.assets.open(lm.assetPath).use { st ->
                                    val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
                                    android.graphics.BitmapFactory.decodeStream(st, null, o)
                                }
                            } catch (e: Exception) { null }
                        }
                        landmarkBitmapCache[lm.assetPath] = bmp
                    }
                    // (1) Hay landmark cercano y TODOS decodificados (intentados). (2) Ya hay NPCs/coches.
                    val landmarksOk = nearby.isNotEmpty() &&
                        nearby.all { landmarkBitmapCache.containsKey(it.assetPath) }
                    val npcsOk = uiState.npcs.isNotEmpty()
                    // DIAGNÓSTICO (POW_DBG, cada ~1 s): qué está pendiente para soltar la carga.
                    val nowLog = System.currentTimeMillis()
                    if (nowLog - lastLog > 1000L) {
                        lastLog = nowLog
                        android.util.Log.d("POW_DBG",
                            "gate: nearbyLm=${nearby.size} lmDecoded=${nearby.count { landmarkBitmapCache.containsKey(it.assetPath) }} " +
                            "npcs=${uiState.npcs.size} mapReady=${uiState.isMapReady} roadsReady=${uiState.isRoadNetworkReady} " +
                            "t=${(nowLog - start) / 1000}s")
                    }
                    if (landmarksOk && npcsOk) {
                        android.util.Log.d("POW_DBG", "gate: LISTO (landmarks+NPCs) en ${(System.currentTimeMillis() - start) / 1000}s")
                        sceneReady = true; break
                    }
                }
                // Timeout de seguridad: nunca dejar al jugador atrapado en la carga. Generoso (30 s)
                // para gama baja: que dé tiempo a sembrar NPCs/coches y decodificar landmarks.
                if (System.currentTimeMillis() - start > 30000L) {
                    android.util.Log.w("POW_DBG", "gate: TIMEOUT 30s — se entra aunque falten assets (npcs=${uiState.npcs.size})")
                    sceneReady = true; break
                }
                kotlinx.coroutines.delay(200)
            }
        }
        val worldReady = !uiState.isLoadingLocation && uiState.isRoadNetworkReady &&
            uiState.isMapReady && uiState.npcsWarmedUp && sceneReady
        if (!worldReady) {
            val tipTeleport = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tip_teleport)
            val tipDesigner = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tip_designer)
            val tipWanted = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tip_wanted)
            val tipShine = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tip_shine)
            val tipCarjack = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tip_carjack)

            val tips = listOf(
                tipTeleport,
                tipCarjack,
                tipDesigner,
                tipWanted,
                tipShine
            )
            var currentTipIndex by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while(isActive) {
                    kotlinx.coroutines.delay(3500)
                    currentTipIndex = (currentTipIndex + 1) % tips.size
                }
            }

            // Progreso compuesto: ubicación → calles → tiles del mapa → NPCs/edificios.
            val progress = when {
                uiState.isLoadingLocation -> 0.05f
                !uiState.isRoadNetworkReady -> 0.25f
                !uiState.isMapReady -> 0.35f + 0.55f * uiState.mapLoadProgress
                else -> 0.92f // mapa listo: sembrando NPCs y edificios
            }.coerceIn(0f, 1f)
            val statusText = when {
                uiState.isLoadingLocation -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_getting_location)
                !uiState.isRoadNetworkReady -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_loading_streets)
                !uiState.isMapReady -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_downloading_map)
                else -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_placing_npcs_buildings)
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFFD4AF37))
                Spacer(Modifier.height(16.dp))
                Text(
                    "${(progress * 100).toInt()}%",
                    color = Color(0xFFD4AF37),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    statusText,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_preparing_world),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tip_format, tips[currentTipIndex]),
                    color = Color(0xFFD4AF37),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            return@Box
        }

        // ───── CAPA 1: MAPA ────────────────────────────────────────────────────
        when (uiState.mapProvider) {
            MapProvider.OSM -> {
                NativeOsmMap(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    roadNetwork = roadNetwork,
                    allCollectibles = allCollectibles,
                    nativeDrawableCache = nativeDrawableCache,
                    landmarkBitmapCache = landmarkBitmapCache,
                    nativeMapRef = nativeMapRef,
                )
            }
            MapProvider.GOOGLE_MAPS_NATIVE -> {
                // REFACTOR: rama Google extraída a WorldMapScreenGoogle.kt (mismo paquete) para
                // reducir el tamaño de este composable. Las cachés locales se pasan por parámetro.
                GoogleMapLayer(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    roadNetwork = roadNetwork,
                    allCollectibles = allCollectibles,
                    landmarkBitmapCache = landmarkBitmapCache,
                    googleMapsIconCache = googleMapsIconCache,
                )
            }
            else -> {
                // REFACTOR: rama WEB (Leaflet/WebView) extraída a WorldMapScreenWeb.kt (mismo paquete)
                // para reducir el tamaño de este composable. Cachés/holders locales se pasan por parámetro.
                WebMapLayer(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context,
                    roadNetwork = roadNetwork,
                    allCollectibles = allCollectibles,
                    cachingClient = cachingClient,
                    webViewRef = webViewRef,
                    gson = gson,
                    coroutineScope = coroutineScope,
                    base64Cache = base64Cache,
                    widthCache = widthCache,
                    heightCache = heightCache,
                    registeredWebImages = registeredWebImages,
                    lastWebNpcHolder = lastWebNpcHolder,
                    lastWebLandmarkHolder = lastWebLandmarkHolder,
                    webLmTick = webLmTick,
                    lastWebMetroHolder = lastWebMetroHolder,
                    webMetroTick = webMetroTick,
                    lastWebIpOn = lastWebIpOn,
                    lastWebIpLm = lastWebIpLm,
                    lastWebIpColl = lastWebIpColl,
                    lastWebPoliceHolder = lastWebPoliceHolder,
                    lastWebZombieHolder = lastWebZombieHolder,
                )
            }
        }

        // ───── CAPA DE DIBUJO DEL EDITOR (Debug Interiores) ─────────────────────
        // Va SOBRE el mapa (cualquier renderer: web/OSM/Google) y DEBAJO de los botones y
        // el panel (que se dibujan después). Con herramienta activa intercepta el toque:
        // el mapa NO se mueve y dibujas líneas/rectángulos. Con NONE deja pasar el gesto.
        if (uiState.showInteriorDebugOverlay) {
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.InteriorDebugDrawSurface(
                tool = uiState.debugEditTool,
                walls = uiState.debugEditWalls,
                blocks = uiState.debugEditBlocks,
                navPed = uiState.debugEditNavPed,
                navCar = uiState.debugEditNavCar,
                center = uiState.currentLocation,
                zoom = uiState.zoomLevel,
                onCommit = { t, pts -> viewModel.commitDebugStroke(t, pts) }
            )
        }

        // ─── CAPA DE NEBLINA (fog of war estilo Age of Empires) — SIEMPRE ACTIVA ──
        // El radio visible se fija en METROS reales (no cambia con el zoom): se
        // convierte a píxeles según el zoom actual. Fuera del radio se aplica un
        // gris translúcido (no negro total), suficiente para ocultar NPCs.
        // Antes solo se dibujaba con !isUserPanningMap, por eso "desaparecía" al
        // mover el mapa. Ahora es INCONDICIONAL y el radio se acota al tamaño de
        // pantalla para que el anillo de neblina nunca quede fuera de cuadro (a
        // zoom bajo el radio en píxeles podía superar la pantalla y no verse).
        // Para OSM Nativo y proveedores Web la neblina se dibuja DENTRO del mapa
        // (anclada a la posición real del jugador), así que aquí solo se pinta para
        // el SDK nativo de Google, donde no hay overlay propio.
        if (uiState.mapProvider == MapProvider.GOOGLE_MAPS_NATIVE) run {
            val fogLat = uiState.currentLocation?.latitude ?: 19.5
            val fogMpp = metersPerPixel(uiState.zoomLevel, fogLat)
            // Defensa: nunca dejar que mpp degenerado convierta el radio en Infinity/NaN
            // (eso pintaría la pantalla entera del color de la neblina).
            val rawRevealPx = if (fogMpp.isFinite() && fogMpp > 0.0)
                (NPC_FOG_VISION_METERS / fogMpp).toFloat()
            else 400f
            Canvas(modifier = Modifier.fillMaxSize()) {
                // El radio despejado nunca supera ~40% de la pantalla ni baja de 40px:
                // garantiza que la neblina SIEMPRE sea visible en los bordes.
                val fogRevealPx = rawRevealPx.coerceIn(40f, size.minDimension * 0.40f)
                val outer = fogRevealPx * 1.8f
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            (fogRevealPx / outer).coerceIn(0f, 0.99f) to Color.Transparent,
                            1.0f to Color(0x80222A33) // gris azulado translúcido (~50%)
                        ),
                        center = center,
                        radius = outer
                    )
                )
            }
        }

        if (!uiState.isUserPanningMap) {
            // MUERTE: al morir, el jugador queda como "fantasmita" (semitransparente),
            // igual que en el modo zombis.
            val ghostModifier = if (uiState.showWastedScreen)
                Modifier.align(Alignment.Center).alpha(0.3f)
            else Modifier.align(Alignment.Center)
            PlayerCharacter(uiState = uiState, modifier = ghostModifier, health = viewModel.playerHealth, showHealthBar = viewModel.showHealthBar, damagePulseTrigger = viewModel.damagePulseTrigger)
        }

        LowHealthAura(health = viewModel.playerHealth)

        // ─── 💥 FX DE IMPACTO/COLISIÓN ───────────────────────────────────────────
        // Destello de "💥" en el centro (posición del jugador) cuando un NPC te golpea
        // o cuando atropellas a alguien, para que la colisión se NOTE.
        val impactScale = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(viewModel.impactEffectTrigger) {
            if (viewModel.impactEffectTrigger > 0) {
                impactScale.snapTo(0.5f)
                impactScale.animateTo(1.5f, animationSpec = androidx.compose.animation.core.tween(150))
                impactScale.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(280))
            }
        }
        if (impactScale.value > 0.01f) {
            Text(
                text = "💥",
                fontSize = 56.sp,
                modifier = Modifier.align(Alignment.Center).scale(impactScale.value)
            )
        }

        // ─── DESTELLO ROJO DE DAÑO ───────────────────────────────────────────────
        // En CADA golpe recibido (damagePulseTrigger) parpadea un viñeteado rojo, como
        // en el modo zombis, para que se note claramente que te hicieron daño.
        val dmgFlash = remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(viewModel.damagePulseTrigger) {
            if (viewModel.damagePulseTrigger > 0) {
                dmgFlash.snapTo(0.55f)
                dmgFlash.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(420))
            }
        }
        if (dmgFlash.value > 0.01f) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        0.0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1.0f to Color.Red.copy(alpha = dmgFlash.value)
                    )
                )
            )
        }

        // ─── BARRA DE VIDA FIJA (HUD) ────────────────────────────────────────────
        // Siempre visible (como en el modo zombis) para que se vea cuánta vida tienes
        // y cuándo te hacen daño. Arriba a la izquierda, bajo el botón de Ajustes.
        if (!uiState.isDesignerMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 12.dp)
                    .width(170.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(9.dp))
            ) {
                LinearProgressIndicator(
                    progress = (viewModel.playerHealth / 100f).coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxSize(),
                    color = when {
                        viewModel.playerHealth > 60f -> Color(0xFF4CAF50)
                        viewModel.playerHealth > 30f -> Color(0xFFFFEB3B)
                        else -> Color(0xFFF44336)
                    },
                    trackColor = Color.Transparent
                )
                Text(
                    "${viewModel.playerHealth.toInt()} HP",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // ─── NIVEL DE BÚSQUEDA (estrellas estilo GTA) ────────────────────────────
        if (uiState.wantedLevel > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 36.dp, start = 12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(5) { i ->
                    Text(
                        text = if (i < uiState.wantedLevel) "⭐" else "☆",
                        fontSize = 16.sp,
                        color = if (i < uiState.wantedLevel) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }

        // ─── WIDGET DE OBJETIVO (Modo Historia) ──────────────────────────────────
        // Centrado arriba y difuminado para no chocar con los widgets de las esquinas.
        uiState.currentObjective?.let { obj ->
            ObjectivesWidget(
                objective = obj,
                done = uiState.objectiveDone,
                playerLocation = uiState.currentLocation,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            )
        }

        // ─── MISIÓN FALLIDA (Modo Historia: la policía mató a Prankedy) ──────────
        // Pantalla a pantalla completa, estilo "WASTED", con el texto EN 2 LÍNEAS.
        if (uiState.showMissionFailed) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xDD000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_mission_failed),
                        color = Color(0xFFD32F2F),
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp,
                        lineHeight = 60.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(28.dp))
                    // REINTENTAR: reinicia la misión en sitio (sin volver a la pantalla de inicio).
                    Button(
                        onClick = { onRetryMission() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(0.6f).height(50.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_retry_mission), color = Color.White, fontWeight = FontWeight.Bold,
                            fontSize = 15.sp, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { onNavigateToMainMenu() }) {
                        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_exit_to_menu), color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ─── AVISO DE CARJACK (te van a bajar del auto) ──────────────────────────
        uiState.carjackWarning?.let { warn ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-90).dp)
                    .background(Color(0xCCB71C1C), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(warn, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (!uiState.isRoadNetworkReady) {
            Row(modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), Color(0xFFD4AF37), strokeWidth = 2.dp)
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_loading_streets), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ─── ESTADO DE PRE-DESCARGA DE LA ZONA (offline) ─────────────────────
        // No bloqueante: el jugador puede moverse mientras descarga. Avisa si la
        // zona quedó incompleta por falta de red (juego offline garantizado solo
        // cuando termina al 100%).
        if (uiState.zonePrefetchActive || uiState.zoneOfflineWarning || uiState.zoneOfflineReady) {
            val (chipText, chipColor) = when {
                uiState.zonePrefetchActive ->
                    androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_downloading_zone, (uiState.zonePrefetchProgress * 100).roundToInt()) to Color(0xCC1E2A38)
                uiState.zoneOfflineWarning ->
                    androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_offline_incomplete) to Color(0xCC8A1F1F)
                else ->
                    androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_zone_ready_offline) to Color(0xCC1F5A2E)
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (!uiState.isRoadNetworkReady) 104.dp else 72.dp)
                    .background(chipColor, CircleShape).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (uiState.zonePrefetchActive) {
                    CircularProgressIndicator(Modifier.size(14.dp), Color(0xFF7FB2FF), strokeWidth = 2.dp)
                }
                Text(chipText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = uiState.showCacheWidget, enter = fadeIn(), exit = fadeOut()) { CacheStatusWidget(roadSource = uiState.roadSource, tileSource = uiState.tileSource, mapProvider = uiState.mapProvider) }
            AnimatedVisibility(visible = uiState.showFpsWidget, enter = fadeIn(), exit = fadeOut()) { CacheChip(label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_performance), text = "$currentFps FPS", color = if (currentFps >= 24) Color(0xFF4CAF50) else Color(0xFFD32F2F), isLoading = false) }
            // Widget de nivel de zoom (Ajustes → Interfaz): muestra el zoom actual en vivo
            // para identificar el nivel óptimo (pinch para cambiarlo).
            AnimatedVisibility(visible = uiState.showZoomWidget, enter = fadeIn(), exit = fadeOut()) {
                CacheChip(label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_zoom), text = "z = ${"%.1f".format(uiState.zoomLevel)}", color = Color(0xFF7FB2FF), isLoading = false)
            }
            // Velocímetro (Ajustes → Interfaz): velocidad en km/h, SOLO al conducir.
            // CALIBRADO a sensación de manejo, no al desplazamiento geográfico real: el
            // avatar recorre el mapa a ~204 km/h reales a tope (movimiento acelerado del
            // juego), lo que se veía irrealista. Se mapea linealmente MAX_SPEED → 120 km/h.
            AnimatedVisibility(visible = uiState.showSpeedometer && uiState.isDriving, enter = fadeIn(), exit = fadeOut()) {
                val speedAbs = kotlin.math.abs(uiState.vehicleSpeed)
                val frac = (speedAbs / 0.000017).toFloat().coerceIn(0f, 1f) // MAX_SPEED del coche
                val kmh = (frac * 120f).roundToInt()
                CacheChip(
                    label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_speed),
                    text = "🚗 $kmh km/h",
                    color = when {
                        frac < 0.5f -> Color(0xFF4CAF50)
                        frac < 0.85f -> Color(0xFFFFB300)
                        else -> Color(0xFFE53935)
                    },
                    isLoading = false
                )
            }
            // Widget de coordenadas (Ajustes → Interfaz): X=longitud, Y=latitud, Z=GLOBAL.
            AnimatedVisibility(visible = uiState.showCoordsWidget, enter = fadeIn(), exit = fadeOut()) {
                val loc = uiState.currentLocation
                CoordsWidget(
                    x = loc?.let { "%.5f".format(it.longitude) } ?: "--",
                    y = loc?.let { "%.5f".format(it.latitude) } ?: "--",
                    z = "GLOBAL"
                )
            }
            AnimatedVisibility(visible = uiState.isDesignerMode, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFD4AF37).copy(alpha = 0.85f), CircleShape)
                        .clickable { showExitDesignerConfirm = true } // Se vuelve interactivo
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Architecture, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_designer), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Dialogo para la confirmación de salida del modo
        if (showExitDesignerConfirm) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showExitDesignerConfirm = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF3B0D1B), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_designer_exit), color = Color(0xFFD4AF37), fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Text(
                            androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_designer_exit_confirm),
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showExitDesignerConfirm = false },
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFFFFF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_designer_continue), color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showExitDesignerConfirm = false
                                    viewModel.toggleDesignerMode(false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_designer_accept), color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Pintado del Banner flotante temporal de la leyenda requerida (CENTRAL)
        AnimatedVisibility(
            visible = showDesignerHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_designer_edit_hint),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Arriba a la derecha: Ajustes SIEMPRE visible + UN único menú desplegable
        // que contiene submenús anidados ("menú de menús"). Así no hay botones
        // sueltos que se sobrepongan con el mapa. Acordeón: abrir un submenú cierra
        // el otro. Al arrastrar el mapa, se abre solo en el submenú "Mapa".
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape)) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black) }
            OptionsMenu(
                expanded = optionsExpanded,
                onExpandedChange = { optionsExpanded = it },
                openGroupId = optionsOpenGroup,
                onOpenGroupChange = { optionsOpenGroup = it },
                entries = buildList {
                    add(
                        OptionMenuGroup(
                            id = "opciones", label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_fab_options), icon = Icons.Default.Tune,
                            items = buildList {
                                add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_change_skin), Icons.Default.Person, Color(0xFFD91B5B)) { viewModel.toggleSkinSelector(true) })
                                // MODO HISTORIA: guardado manual → abre el selector de slots.
                                add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_save_game), Icons.Default.School, Color(0xFF4CAF50)) {
                                    onRequestSaveGame()
                                })
                                // Teletransportarse: solo en Modo Desarrollador.
                                if (developerMode) add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_teleport), Icons.Default.LocationOn, Color(0xFFFF9800)) { viewModel.toggleTeleportMenu(true) })
                                // (Submenú "Ir a…" eliminado: "Ir a ESCOM" ya es el primer punto de
                                // "Teletransportarse…" y "Ir a tu Ubicación (GPS)" se movió al inicio
                                // de esa misma lista.)
                                // Submenú anidado "Diseñador / Debug": solo en Modo Desarrollador.
                                if (developerMode) add(
                                    OptionMenuGroup(
                                        id = "disenador_debug",
                                        label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_fab_designer),
                                        icon = Icons.Default.Architecture,
                                        tint = if (uiState.isDesignerMode || uiState.showInteriorDebugOverlay) Color(0xFFD4AF37) else Color.White,
                                        items = buildList {
                                            add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_mode_designer), Icons.Default.Architecture, if (uiState.isDesignerMode) Color(0xFFD4AF37) else Color.White) { viewModel.toggleDesignerMode(!uiState.isDesignerMode) })
                                            add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_debug_interiors), Icons.Default.LocationOn, if (uiState.showInteriorDebugOverlay) Color(0xFFFFC107) else Color.White) { viewModel.toggleInteriorDebugOverlay(!uiState.showInteriorDebugOverlay) })
                                            if (uiState.isDesignerMode) {
                                                add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_add_asset), Icons.Default.Add, Color(0xFF4CAF50)) { viewModel.showAssetPicker(true) })
                                            }
                                        }
                                    )
                                )
                                // Apocalipsis Zombi Global: solo en Modo Desarrollador.
                                if (developerMode) add(OptionMenuItem(
                                    if (uiState.globalZombieMode) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_apocalypse_off) else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_apocalypse_on),
                                    Icons.Default.Warning,
                                    if (uiState.globalZombieMode) Color(0xFFE53935) else Color.White
                                ) { viewModel.toggleGlobalZombieMode() })
                                // Prankedy (toggle manual hostil): solo en Modo Desarrollador.
                                if (developerMode) add(OptionMenuItem(
                                    if (uiState.prankedyEnabled) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_prankedy_off) else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_prankedy_on),
                                    Icons.Default.Face,
                                    if (uiState.prankedyEnabled) Color(0xFFD4AF37) else Color.White
                                ) { viewModel.togglePrankedy() })
                            }
                        )
                    )
                    add(
                        OptionMenuGroup(
                            id = "mapa", label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_map), icon = Icons.Default.LocationOn,
                            items = buildList {
                                // (Submenú "Zoom (acercar / alejar)" eliminado: el zoom se hace
                                // con pinch de dos dedos en los tres renderers.)
                                // Centrar en jugador: SIEMPRE disponible (antes solo al panear),
                                // para volver al jugador en cualquier momento. Si el usuario ha
                                // cambiado el zoom respecto al de juego, esta opción EVOLUCIONA a
                                // un submenú con androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_center_player) y androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_zoom_player).
                                run {
                                    // Default actual por estado: 22 a pie, 21 conduciendo (20 rápido).
                                    val defaultZoom = if (uiState.isDriving)
                                        ovh.gabrielhuav.pow.features.map_exterior.viewmodel.ZOOM_DRIVING
                                    else
                                        ovh.gabrielhuav.pow.features.map_exterior.viewmodel.ZOOM_ON_FOOT
                                    val isZoomed = kotlin.math.abs(uiState.zoomLevel - defaultZoom) >= 0.5
                                    if (isZoomed) {
                                        add(
                                            OptionMenuGroup(
                                                id = "centrar_jugador", label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_fab_center),
                                                icon = Icons.Default.Person,
                                                items = buildList {
                                                    add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_center_player), Icons.Default.Person, Color(0xFF2196F3)) { viewModel.centerOnPlayer() })
                                                    add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_zoom_player), Icons.Default.Add, Color(0xFFD4AF37)) { viewModel.zoomToPlayer() })
                                                }
                                            )
                                        )
                                    } else {
                                        add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_center_player), Icons.Default.Person, Color(0xFF2196F3)) { viewModel.centerOnPlayer() })
                                    }
                                }
                                if (uiState.isTargetingWaypoint) {
                                    // Apuntando: confirmar o cancelar TAMBIÉN desde el menú (no
                                    // botones flotantes que tapen los controles).
                                    add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_set_destination), Icons.Default.LocationOn, Color(0xFF4CAF50)) {
                                        if (uiState.mapProvider == MapProvider.OSM) {
                                            nativeMapRef.value?.let { mv ->
                                                val center = mv.mapCenter
                                                viewModel.placeDestinationMarker(center.latitude, center.longitude)
                                            }
                                        } else {
                                            webViewRef.value?.evaluateJavascript("if(window.Android && window.Android.notifyCenterForWaypoint) { var c = map.getCenter(); window.Android.notifyCenterForWaypoint(c.lat, c.lng); }", null)
                                        }
                                    })
                                    add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_cancel_aim), Icons.Default.Close, Color(0xFFE53935)) { viewModel.toggleWaypointTargeting(false) })
                                } else if (uiState.isUserPanningMap && !uiState.isDesignerMode && !uiState.isDriving) {
                                    add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_aim_waypoint), Icons.Default.LocationOn, Color(0xFF4CAF50)) { viewModel.toggleWaypointTargeting(true) })
                                    if (uiState.destinationMarker != null) {
                                        add(OptionMenuItem(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_opt_remove_destination), Icons.Default.Close, Color(0xFFE53935)) { viewModel.clearDestinationMarker() })
                                    }
                                }
                            }
                        )
                    )
                }
            )
        }

        // Cuando se está apuntando un waypoint, solo se muestra la cruz central; el
        // confirmar/cancelar vive en el menú anidado (Mapa) para no tapar los controles.
        if (uiState.isTargetingWaypoint) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(48.dp).graphicsLayer { translationY = -24.dp.toPx() })
                    Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                }
            }
        }

        // ─── AVISO DE CAMBIO DE PROVEEDOR (precarga en segundo plano) ─────────
        val pending = uiState.pendingProvider
        if (pending != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .background(Color(0xE61A1A22), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (!uiState.pendingProviderReady) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), Color(0xFFD4AF37), strokeWidth = 2.dp)
                        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_preparing_model, pending.displayName), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_model_ready, pending.displayName), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.commitMapProvider() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_change), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            TextButton(onClick = { viewModel.cancelPendingProvider() }) {
                                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_discard), color = Color(0xFFCCCCCC), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showTeleportMenu) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleTeleportMenu(false) },
                title = { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tp_title), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tp_subtitle), fontSize = 14.sp)
                        LazyColumn(modifier = Modifier.fillMaxHeight(0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Primera opción: tu ubicación REAL (GPS del dispositivo, p. ej. volver a
                            // casa). Movida aquí desde el antiguo submenú "Ir a…".
                            item {
                                Button(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        try {
                                            // Lectura FRESCA de alta precisión (lastLocation es caché y
                                            // podía mandarte a una ubicación vieja/imprecisa).
                                            val fused = LocationServices.getFusedLocationProviderClient(context)
                                            fused.getCurrentLocation(
                                                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null
                                            ).addOnSuccessListener { loc ->
                                                if (loc != null) viewModel.teleportTo(loc.latitude, loc.longitude)
                                                else fused.lastLocation.addOnSuccessListener { l ->
                                                    if (l != null) viewModel.teleportTo(l.latitude, l.longitude)
                                                }
                                            }.addOnFailureListener {
                                                try {
                                                    fused.lastLocation.addOnSuccessListener { l ->
                                                        if (l != null) viewModel.teleportTo(l.latitude, l.longitude)
                                                    }
                                                } catch (_: SecurityException) {}
                                            }
                                        } catch (_: SecurityException) {}
                                    }
                                    viewModel.toggleTeleportMenu(false)
                                }, modifier = Modifier.fillMaxWidth()) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tp_gps)) }
                            }
                            items(TeleportCatalog.zones) { zone ->
                                Button(onClick = { viewModel.teleportTo(zone.latitude, zone.longitude) }, modifier = Modifier.fillMaxWidth()) { Text(zone.name) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.toggleTeleportMenu(false) }) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_close)) } }
            )
        }

        if (uiState.showAssetPicker) {
            AssetPickerDialog(context = context, onAssetSelected = { viewModel.addLandmarkAtPlayer(context, it) }, onDismiss = { viewModel.showAssetPicker(false) })
        }

        if (uiState.showSkinSelector) {
            SkinSelectorDialog(
                currentSkin    = uiState.selectedSkin,
                context        = context,
                onSkinSelected = { viewModel.selectSkin(it) },
                onDismiss      = { viewModel.toggleSkinSelector(false) },
                developerMode  = developerMode
            )
        }

        val selectedLandmark = uiState.landmarks.find { it.id == uiState.selectedLandmarkId }
        if (uiState.isDesignerMode && selectedLandmark != null) {
            val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

            DesignerPanel(
                landmark = selectedLandmark,
                onMove = { dLat, dLon -> viewModel.moveSelectedLandmark(dLat, dLon) },
                onRotate = { angle -> viewModel.rotateSelectedLandmark(angle) },
                onScaleX = { sx -> viewModel.scaleXSelectedLandmark(sx) },
                onScaleY = { sy -> viewModel.scaleYSelectedLandmark(sy) },
                onDelete = { viewModel.deleteSelectedLandmark(context) },
                onSave = { viewModel.saveSelectedLandmark(context) },
                onExport = { exportLauncher.launch("landmarks_config.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onDeselect = { viewModel.selectLandmark(null) },
                isParkingMode = uiState.isParkingSlotMode,
                onToggleParkingMode = { isChecked -> viewModel.toggleParkingMode(isChecked) },
                onNewWay = { viewModel.startNewWay() },
                onDebugPoint = { viewModel.debugPlayerLocalCoordinates(context) },
                onSpawnTestCar = { viewModel.spawnDynamicCarInEscom(context) },
                onRevert = {
                    val currentLandmark = uiState.landmarks.find { it.id == uiState.selectedLandmarkId }
                    if (currentLandmark != null && originalLandmarkState != null) {
                        val deltaLat = originalLandmarkState!!.location.latitude - currentLandmark.location.latitude
                        val deltaLon = originalLandmarkState!!.location.longitude - currentLandmark.location.longitude
                        viewModel.moveSelectedLandmark(deltaLat, deltaLon)
                        viewModel.rotateSelectedLandmark(originalLandmarkState!!.rotationAngle)
                        viewModel.scaleXSelectedLandmark(originalLandmarkState!!.scaleX)
                        viewModel.scaleYSelectedLandmark(originalLandmarkState!!.scaleY)
                    }
                },
                modifier = if (isPortrait) {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.32f) // Altura controlada en vertical
                } else {
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp)
                        .fillMaxWidth(0.35f) // 45% del ancho en horizontal
                        .fillMaxHeight()     // Ocupa casi todo el alto lateral
                }
            )
        }

        // ─── PANEL DEL EDITOR DE LÍNEAS (Debug Interiores) ───────────────────────
        // Visible cuando el overlay de Debug Interiores está activo. Barra horizontal
        // abajo (los controles de movimiento se ocultan al editar). Se DIBUJA con el dedo
        // sobre el mapa: arrastre = línea (bardas/caminos) o rectángulo (zonas rojas).
        if (uiState.showInteriorDebugOverlay) {
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.InteriorDebugEditorPanel(
                tool = uiState.debugEditTool,
                wallsCount = uiState.debugEditWalls.size,
                blocksCount = uiState.debugEditBlocks.size,
                navPedCount = uiState.debugEditNavPed.size,
                navCarCount = uiState.debugEditNavCar.size,
                routeNpcsActive = uiState.npcs.any { it.id.startsWith("CAMPAIGN_ROUTE_") },
                onSelectTool = { viewModel.setDebugEditTool(it) },
                onUndo = { viewModel.undoLastDebugShape() },
                onClear = { viewModel.clearDebugEdits() },
                onExport = { collisionsExportLauncher.launch("exterior_collisions_editado.json") },
                onImport = { collisionsImportLauncher.launch(arrayOf("application/json", "*/*")) },
                onToggleRouteNpcs = { viewModel.toggleCampaignRouteNpcsDebug() },
                onExit = {
                    viewModel.setDebugEditTool(DebugEditTool.NONE)
                    viewModel.toggleInteriorDebugOverlay(false)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.6f)
                    .padding(8.dp)
            )
        }

        // (El widget de OBJETIVO se dibuja UNA sola vez, arriba-centro — ver más arriba.
        // Antes había aquí un segundo widget arriba-izquierda que duplicaba el objetivo.)

        // REFACTOR: controles (vals de layout + botón salir apocalipsis + fila D-pad/
        // joystick/acciones) extraídos a WorldMapScreenControls.kt (mismo paquete).
        // Es una extensión de BoxScope → se invoca dentro de este Box.
        WorldMapControls(uiState = uiState, viewModel = viewModel, optionsExpanded = optionsExpanded)
        //}
    }

    // REFACTOR: overlays/diálogos extraídos a WorldMapScreenOverlays.kt (mismo paquete)
    // para reducir el tamaño de este archivo. MVVM intacto (solo observa uiState + intenciones).
    WorldMapOverlays(
        uiState = uiState,
        viewModel = viewModel,
        onNavigateToInterior = onNavigateToInterior
    )
}
