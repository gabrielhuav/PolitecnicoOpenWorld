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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
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
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import ovh.gabrielhuav.pow.domain.models.EscomBoundingBox
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.TeleportCatalog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.AssetPickerDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DesignerPanel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuGroup
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuItem
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionsMenu
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerCharacter
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleDPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.Ps4ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.ZOOM_GAMEPLAY_OSM
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.ZOOM_GAMEPLAY_WEB
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
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
import kotlin.math.cos
import kotlin.math.sin

// ─── CULLING DE NPCs POR DISTANCIA ──────────────────────────────────────────
// Los NPC siguen viviendo en memoria/simulación; solo dibujamos los que caen
// dentro del viewport visible. El radio escala con el zoom (metros por pixel),
// así nunca se ocultan NPCs que de verdad están en pantalla.
internal const val NPC_CULL_MARGIN_M = 15.0

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
    onNavigateToInterior: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val roadNetwork by viewModel.roadNetworkFlow.collectAsState()
    val escomItems by viewModel.escomItems.collectAsState()
    val allCollectibles = uiState.activeCollectibles + escomItems
    val base64Cache = remember { mutableStateMapOf<String, String>() }
    val widthCache = remember { mutableStateMapOf<String, Float>() }
    val heightCache = remember { mutableStateMapOf<String, Float>() }
    // OPT memoria gama baja (≤2 GB): esta caché de drawables (NPCs, patrullas, balas,
    // coleccionables…) se indexa por FIRMA VISUAL (incluye salud/zoom/frame), así que en
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
    var yButtonHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportLandmarksToUri(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importLandmarksFromUri(context, it) }
    }

    val landmarkBitmapCache = remember { mutableMapOf<String, android.graphics.Bitmap?>() }
    var hasTriggeredNativePan by remember { mutableStateOf(false) }

    // Nuevos estados para las interacciones del Diseñador
    var showDesignerHint by remember { mutableStateOf(false) }
    var showExitDesignerConfirm by remember { mutableStateOf(false) }
    var originalLandmarkState by remember { mutableStateOf<ovh.gabrielhuav.pow.domain.models.Landmark?>(null) }

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

    // Cuando el video de carga termina y hay un destino pendiente, navegar.
    // Si la interacción fue con la mano (pendingZombieMinigame), vamos al minijuego
    // de zombis (que arranca en el lobby/croquis). Si no, al interior normal.
    LaunchedEffect(uiState.showZombiVideo, uiState.pendingInteriorDestination) {
        val target = uiState.pendingInteriorDestination
        if (target != null && !uiState.showZombiVideo) {
            viewModel.clearPendingInteriorDestination()
            if (viewModel.pendingZombieMinigame) {
                viewModel.clearPendingZombieMinigame()
                onNavigateToInterior("zombie_minigame")
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
    val lastWebNpcHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.Npc>>(1) }
    // OPT FPS web (ahora proveedor por defecto): la lista de landmarks solo cambia al
    // editarlos/cargarlos, pero su JSON se serializaba y enviaba al WebView en CADA frame.
    // Guardamos la última referencia para reenviar updateLandmarks solo cuando cambie.
    val lastWebLandmarkHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.Landmark>>(1) }
    // Heartbeat: re-enviar landmarks al WebView cada ~45 frames por si el primer envío
    // (al cambiar la lista) llegó antes de que el HTML definiera updateLandmarks.
    val webLmTick = remember { intArrayOf(0) }
    // Si en el frame anterior se enviaron waypoints de patrulla al WebView, para poder
    // limpiarlos al dejar de estar buscado sin spamear updatePolice cuando no hay policías.
    val lastWebPoliceHolder = remember { booleanArrayOf(false) }
    val nativeMapRef = remember { mutableStateOf<MapView?>(null) }

    // ─── ESTADO DEL MENÚ DE OPCIONES (con submenús anidados) ──────────────────
    var optionsExpanded by remember { mutableStateOf(false) }
    var optionsOpenGroup by remember { mutableStateOf<String?>(null) }

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
        val worldReady = !uiState.isLoadingLocation && uiState.isRoadNetworkReady && uiState.isMapReady
        if (!worldReady) {
            val tips = remember { listOf(
                "Mantén presionado Y para teletransportarte a otros lugares.",
                "Presiona Y cerca de un auto para robarlo.",
                "Usa el modo diseñador para personalizar el mapa con tus propios edificios.",
                "Golpear civiles o policías aumentará tu nivel de búsqueda.",
                "Visita el Shine CTO para descubrir secretos del Politécnico."
            )}
            var currentTipIndex by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while(isActive) {
                    kotlinx.coroutines.delay(3500)
                    currentTipIndex = (currentTipIndex + 1) % tips.size
                }
            }

            // Progreso compuesto: ubicación → calles → descarga de tiles del mapa.
            val progress = when {
                uiState.isLoadingLocation -> 0.05f
                !uiState.isRoadNetworkReady -> 0.25f
                else -> 0.35f + 0.65f * uiState.mapLoadProgress
            }.coerceIn(0f, 1f)
            val statusText = when {
                uiState.isLoadingLocation -> "Obteniendo tu ubicación..."
                !uiState.isRoadNetworkReady -> "Cargando las calles..."
                else -> "Descargando el mapa..."
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
                    "Preparando Politécnico Open World",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Consejo: ${tips[currentTipIndex]}",
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
                val escom = LatLng(19.505411765791404, -99.14526888961194)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(escom, 18f)
                }

                LaunchedEffect(uiState.currentLocation, uiState.isDriving, uiState.zoomLevel) {
                    if (!uiState.isUserPanningMap) {
                        val targetLat = uiState.currentLocation?.latitude ?: escom.latitude
                        val targetLng = uiState.currentLocation?.longitude ?: escom.longitude
                        val targetZoom = uiState.zoomLevel.toFloat()
                        val targetBearing = if (uiState.isDriving) uiState.vehicleRotation else 0f

                        val newPosition = CameraPosition.builder()
                            .target(LatLng(targetLat, targetLng))
                            .zoom(targetZoom)
                            .bearing(targetBearing)
                            .tilt(0f)
                            .build()

                        // OPT FPS Google nativo: la posición del jugador cambia ~30 Hz; animar
                        // (120 ms) en CADA cambio encadenaba animaciones que se cancelaban entre
                        // sí (thrash de la cámara). move() reposiciona al instante: igual de fluido
                        // (las posiciones ya llegan a 30 Hz) y mucho más barato.
                        cameraPositionState.move(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(newPosition))
                    }
                }

                // Canal de retorno del zoom por gesto (pinch) en Google native: solo cuando
                // el movimiento de cámara lo inició el USUARIO, propagamos el nuevo zoom al
                // estado para que no rebote al seguir al jugador. Los movimientos
                // programáticos (seguimiento/zoom por botón) se ignoran.
                LaunchedEffect(cameraPositionState) {
                    snapshotFlow { cameraPositionState.position.zoom }
                        .collect { z ->
                            if (cameraPositionState.cameraMoveStartedReason ==
                                com.google.maps.android.compose.CameraMoveStartedReason.GESTURE) {
                                viewModel.onMapZoomChanged(z.toDouble())
                            }
                        }
                }

                val propiedadesMap = remember {
                    try {
                        MapProperties(
                            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, ovh.gabrielhuav.pow.R.raw.estilo_google_maps)
                        )
                    } catch (e: Exception) {
                        MapProperties()
                    }
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = propiedadesMap,
                    uiSettings = MapUiSettings(
                        zoomGesturesEnabled = true,   // pinch (dos dedos) para zoom, igual que web/OSM
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = uiState.isDesignerMode || uiState.isUserPanningMap,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false
                    )
                ) {
                    uiState.landmarks.forEach { landmark ->
                        key(landmark.id) {
                            val bitmap = landmarkBitmapCache.getOrPut(landmark.assetPath) {
                                try {
                                    context.assets.open(landmark.assetPath).use { inputStream ->
                                        val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }; android.graphics.BitmapFactory.decodeStream(inputStream, null, o)
                                    }
                                } catch (e: Exception) { null }
                            }

                            if (bitmap != null) {
                                val center = LatLng(landmark.location.latitude, landmark.location.longitude)
                                val widthMeters = (landmark.baseWidthMeters * landmark.scaleX).toFloat()
                                val heightMeters = (landmark.baseHeightMeters * landmark.scaleY).toFloat()

                                val isDoorGM = landmark.assetPath.contains("DOORS/")
                                var doorAnimDescriptor by remember(landmark.id) {
                                    mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null)
                                }
                                if (isDoorGM) {
                                    LaunchedEffect(landmark.id) {
                                        while (true) {
                                            doorAnimDescriptor = BitmapDescriptorFactory.fromBitmap(
                                                // Assuming buildDoorEffectBitmap exists in your project.
                                                // Used fallback icon if undefined, but matching original code structure.
                                                bitmap
                                            )
                                            delay(80L)
                                        }
                                    }
                                }
                                val descriptor = if (isDoorGM) {
                                    doorAnimDescriptor ?: googleMapsIconCache.getOrPut("LANDMARK_${landmark.assetPath}") {
                                        BitmapDescriptorFactory.fromBitmap(bitmap)
                                    }
                                } else {
                                    googleMapsIconCache.getOrPut("LANDMARK_${landmark.assetPath}") {
                                        BitmapDescriptorFactory.fromBitmap(bitmap)
                                    }
                                }

                                GroundOverlay(
                                    position = GroundOverlayPosition.create(center, widthMeters, heightMeters),
                                    image = descriptor,
                                    bearing = landmark.rotationAngle,
                                    transparency = 0f,
                                    zIndex = if (landmark.assetPath.contains("DOORS/")) 10f else 0f
                                )

                                if (uiState.isDesignerMode) {
                                    val isSelected = uiState.selectedLandmarkId == landmark.id
                                    val lat = landmark.location.latitude
                                    val lng = landmark.location.longitude
                                    val rotRad = Math.toRadians(landmark.rotationAngle.toDouble())
                                    val metersToLat = 1.0 / 111111.0
                                    val metersToLon = 1.0 / (111111.0 * cos(Math.toRadians(lat)))
                                    val halfW = (landmark.baseWidthMeters * landmark.scaleX) / 2.0
                                    val halfH = (landmark.baseHeightMeters * landmark.scaleY) / 2.0

                                    fun getPoint(dx: Double, dy: Double): LatLng {
                                        val rx = dx * cos(rotRad) - dy * sin(rotRad)
                                        val ry = dx * sin(rotRad) + dy * cos(rotRad)
                                        return LatLng(lat + ry * metersToLat, lng + rx * metersToLon)
                                    }

                                    val points = listOf(
                                        getPoint(-halfW, halfH),
                                        getPoint(halfW, halfH),
                                        getPoint(halfW, -halfH),
                                        getPoint(-halfW, -halfH)
                                    )

                                    com.google.maps.android.compose.Polygon(
                                        points = points,
                                        fillColor = Color.Transparent,
                                        strokeColor = if (isSelected) Color.Red else Color.Transparent,
                                        strokeWidth = if (isSelected) 8f else 0f,
                                        clickable = true,
                                        onClick = { viewModel.selectLandmark(landmark.id) }
                                    )
                                }
                            }
                        }
                    }
                    // Mostrar calles solo si estamos cerca
                    if (uiState.showRoadNetwork && uiState.zoomLevel >= 15.5) {
                        roadNetwork.forEach { way ->
                            key("road_${way.id}") {
                                com.google.maps.android.compose.Polyline(
                                    points = way.nodes.map { LatLng(it.lat, it.lon) },
                                    color = if (way.isForCars) Color(0xFFFFD700) else Color(0xFF82C8FF),
                                    width = if (way.isForCars) 8f else 5f,
                                    zIndex = 1000f,
                                    clickable = false
                                )
                            }
                        }
                    }

                    if (uiState.zoomLevel >= 15.5) {
                        val screenDensity = context.resources.displayMetrics.density
                        val timeMs = System.currentTimeMillis()
                        val currentZoom = uiState.zoomLevel
                        val renderZoom = round(currentZoom * 2) / 2.0

                        // Culling por neblina: solo se dibujan los NPC dentro del radio de visión (fijo en metros).
                        val centerCull = uiState.currentLocation
                        val cullRadiusM = centerCull?.let { npcVisionRadiusMeters() }

                        uiState.npcs.forEach { npc ->
                            if (cullRadiusM != null && centerCull != null &&
                                !npcWithinRadius(npc.location.latitude, npc.location.longitude,
                                    centerCull.latitude, centerCull.longitude, cullRadiusM)
                            ) return@forEach
                            key(npc.id) {
                                val qHealth = npc.health.toInt()
                                val cacheKey = when {
                                    npc.visualConfig != null -> {
                                        val currentlyMoving = npc.speed > 0 || npc.isMoving
                                        val personSzDp = (24.0 + ((renderZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                        val exactPixels = (personSzDp * screenDensity).toInt()
                                        val frameIndex = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0
                                        val config = npc.visualConfig!!
                                        "GM_PED_${config.bodyFolder}_${config.hairId}_${config.hairColor.value}_${config.shirtColor.value}_${config.pantsColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${qHealth}_D${npc.isDying}"
                                    }
                                    npc.type == NpcType.CAR -> {
                                        var angle = npc.rotationAngle % 360f
                                        if (angle < 0) angle += 360f
                                        val frameIndex = (angle / 7.5f).roundToInt() % 48
                                        val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                        "GM_CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${dynamicScale}_H${qHealth}_D${npc.isDying}"
                                    }
                                    npc.type == NpcType.POLICE_CAR -> {
                                        var angle = npc.rotationAngle % 360f
                                        if (angle < 0) angle += 360f
                                        val frameIndex = (angle / 7.5f).roundToInt() % 48
                                        val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                        "GM_POLICE_${frameIndex}_${dynamicScale}"
                                    }
                                    npc.type == NpcType.POLICE_COP -> "GM_COP_EMOJI"
                                    else -> "GM_SVG_${npc.type.name}_H${qHealth}_D${npc.isDying}"
                                }

                                val iconDescriptor = googleMapsIconCache.getOrPut(cacheKey) {
                                    val drawable = when {
                                        npc.visualConfig != null -> {
                                            val currentlyMoving = npc.speed > 0 || npc.isMoving
                                            val personSzDp = (24.0 + ((renderZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                            val exactPixels = (personSzDp * screenDensity).toInt()
                                            var d = CharacterSpriteManager.getModularNpcDrawable(context, npc.visualConfig!!, currentlyMoving, npc.facingRight, timeMs, screenDensity, npc.displayName)
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                        }
                                        npc.type == NpcType.CAR -> {
                                            val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                            var d = VehicleSpriteManager.getTintedCarNpc(context, npc.rotationAngle, npc.carColor, screenDensity, npc.carModel)
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d?.let {
                                                val fw = ((it.intrinsicWidth / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                val fh = ((it.intrinsicHeight / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                ExactSizeDrawable(it, fw, fh)
                                            }
                                        }
                                        npc.type == NpcType.POLICE_CAR -> {
                                            val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                            val d = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceSpriteManager.getPoliceCar(context, npc.rotationAngle, screenDensity)
                                            d?.let {
                                                val fw = ((it.intrinsicWidth / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                val fh = ((it.intrinsicHeight / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                ExactSizeDrawable(it, fw, fh)
                                            }
                                        }
                                        npc.type == NpcType.POLICE_COP -> {
                                            val px = (18 * screenDensity).toInt()
                                            emojiToDrawable(context, "👮", px)
                                        }
                                        else -> {
                                            val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                            var d = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d?.let { ExactSizeDrawable(it, (24 * screenDensity).toInt(), (24 * screenDensity).toInt()) }
                                        }
                                    }
                                    val bitmap = if (drawable != null) {
                                        val bm = android.graphics.Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bm)
                                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                                        drawable.draw(canvas)
                                        bm
                                    } else null
                                    if (bitmap != null) BitmapDescriptorFactory.fromBitmap(bitmap) else BitmapDescriptorFactory.defaultMarker()
                                }
                                val position = LatLng(npc.location.latitude, npc.location.longitude)
                                val markerState = remember { MarkerState(position = position) }
                                markerState.position = position

                                com.google.maps.android.compose.Marker(
                                    state = markerState,
                                    icon = iconDescriptor,
                                    rotation = 0f,
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    flat = true,
                                    alpha = if (npc.isDying) 0.5f else 1.0f
                                )
                            }
                        }
                    }

                    if (uiState.zoomLevel >= 16.0) {
                        allCollectibles.forEach { collectible ->
                            key(collectible.id) {
                                val screenDensity = context.resources.displayMetrics.density
                                val exactPixels = (22 * screenDensity).toInt()
                                val cacheKey = "GM_COL_${collectible.assetPath}"

                                val iconDescriptor = googleMapsIconCache.getOrPut(cacheKey) {
                                    try {
                                        val bitmap = context.assets.open(collectible.assetPath).use {
                                            android.graphics.BitmapFactory.decodeStream(it)
                                        }
                                        if (bitmap != null) {
                                            val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                                setSize(exactPixels, exactPixels)
                                                setColor(android.graphics.Color.argb(100, 255, 235, 59))
                                            }
                                            val spriteDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                            val spriteSize = (exactPixels * 0.90).toInt()
                                            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(glowDrawable, spriteDrawable))
                                            val inset = ((exactPixels - spriteSize) / 2)
                                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)
                                            val finalBm = android.graphics.Bitmap.createBitmap(exactPixels, exactPixels, android.graphics.Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(finalBm)
                                            layerDrawable.setBounds(0, 0, exactPixels, exactPixels)
                                            layerDrawable.draw(canvas)
                                            BitmapDescriptorFactory.fromBitmap(finalBm)
                                        } else BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                                    } catch (e: Exception) { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW) }
                                }
                                val position = LatLng(collectible.latitude, collectible.longitude)
                                val markerState = remember { MarkerState(position = position) }
                                markerState.position = position

                                com.google.maps.android.compose.Marker(
                                    state = markerState,
                                    icon = iconDescriptor,
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    flat = true,
                                    rotation = ((System.currentTimeMillis() / 30) % 360).toFloat()
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                val collectiblesJson = remember(allCollectibles) { gson.toJson(allCollectibles) }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            webViewClient = cachingClient
                            addJavascriptInterface(MapJsBridge(viewModel), "Android")
                            val lat = uiState.currentLocation?.latitude ?: 0.0
                            val lng = uiState.currentLocation?.longitude ?: 0.0

                            loadDataWithBaseURL("file:///android_asset/", buildHtml(lat, lng, uiState.zoomLevel.toInt()), "text/html", "UTF-8", null)
                            webViewRef.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { wv ->
                        webViewRef.value = wv
                        if (!uiState.isUserPanningMap) {
                            uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof updateMapView==='function')updateMapView(${it.latitude}, ${it.longitude}, ${uiState.zoomLevel.toInt()});", null) }
                        }
                        uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof updatePlayerMarker==='function')updatePlayerMarker(${it.latitude}, ${it.longitude}, ${uiState.isUserPanningMap});", null) }
                        // Neblina anclada al jugador (se redibuja también en cada gesto vía JS).
                        uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof setPlayerFog==='function')setPlayerFog(${it.latitude}, ${it.longitude});", null) }
                        wv.evaluateJavascript("if(typeof setDesignerMode==='function')setDesignerMode(${uiState.isDesignerMode});", null)
                        // OPT FPS web: el contenedor solo se agranda (para rotación) al CONDUCIR; a
                        // pie es del tamaño de la pantalla. El JS ignora llamadas repetidas (guard
                        // _driving), así que llamarlo cada frame es barato y robusto (se auto-corrige
                        // aunque se pierda una transición a pie↔conducir).
                        wv.evaluateJavascript("if(typeof setMapOversize==='function')setMapOversize(${uiState.isDriving});", null)
                        val mapRot = if (uiState.isDriving) -uiState.vehicleRotation else 0f
                        wv.evaluateJavascript("if(typeof setMapRotation==='function')setMapRotation(${mapRot});", null)
                        val tileUrl = when (uiState.mapProvider) {
                            MapProvider.CARTO_DB_DARK  -> "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                            MapProvider.CARTO_DB_LIGHT -> "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
                            MapProvider.ESRI           -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
                            MapProvider.ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                            MapProvider.OPEN_TOPO      -> "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
                            MapProvider.OSM_WEB        -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                            else -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                        }
                        wv.evaluateJavascript("if(typeof changeTileUrl==='function')changeTileUrl('$tileUrl');", null)
                        wv.evaluateJavascript("if(typeof setRoadNetworkReady==='function')setRoadNetworkReady(${uiState.isRoadNetworkReady});", null)

                        val density = context.resources.displayMetrics.density
                        val highResRenderScale = 1.0f * density

                        // Culling por distancia: solo enviamos al WebView los NPC dentro del
                        // viewport. Evita generar bitmaps/base64 y marcadores JS para NPC lejanos.
                        // OPT: solo cuando la lista de NPCs cambió (no en cada recomposición).
                        if (uiState.npcs !== lastWebNpcHolder[0]) {
                          lastWebNpcHolder[0] = uiState.npcs
                        val centerCullW = uiState.currentLocation
                        val cullRadiusMW = centerCullW?.let { npcVisionRadiusMeters() }
                        val visibleNpcs = if (cullRadiusMW != null && centerCullW != null) {
                            uiState.npcs.filter {
                                npcWithinRadius(it.location.latitude, it.location.longitude,
                                    centerCullW.latitude, centerCullW.longitude, cullRadiusMW)
                            }
                        } else uiState.npcs

                        val npcPayloads = visibleNpcs.map { npc ->
                            if (npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR) {
                                // FIX web: la PATRULLA (POLICE_CAR) caía al `else` y se dibujaba con
                                // su SVG genérico en vez del asset real. Ahora la tratamos como un
                                // coche-imagen: generamos su sprite (PoliceSpriteManager, sin tintar),
                                // lo registramos en imgCache y lo enviamos como tipo "CAR".
                                val isPolice = npc.type == NpcType.POLICE_CAR
                                var angle = npc.rotationAngle % 360f
                                if (angle < 0) angle += 360f
                                val frameIndex = (angle / 7.5f).roundToInt() % 48
                                val cacheKey = if (isPolice) "POLICE_${frameIndex}_${density}"
                                               else "${npc.carModel.name}_${frameIndex}_${npc.carColor}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val drawable = if (isPolice)
                                            ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceSpriteManager.getPoliceCar(context, angle, highResRenderScale)
                                        else
                                            VehicleSpriteManager.getTintedCarNpc(context, angle, npc.carColor, highResRenderScale, npc.carModel)
                                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            val w = (bitmap.width / density) / density
                                            val h = (bitmap.height / density) / density
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                                            val b64 = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)

                                            // IMPORTANTE: Actualizar el estado en el hilo principal dispara la recomposición
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                widthCache[cacheKey] = w
                                                heightCache[cacheKey] = h
                                                base64Cache[cacheKey] = b64
                                            }
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, "CAR", cacheKey, null, null, npc.displayName, widthCache[cacheKey], heightCache[cacheKey], health = npc.health, isDying = npc.isDying)
                            } else if (npc.visualConfig != null) {
                                val currentlyMoving = npc.speed > 0 || npc.isMoving
                                val config = npc.visualConfig!!
                                val frameIndex = CharacterSpriteManager.getFrameIndex(context, config, currentlyMoving, System.currentTimeMillis()) ?: 0
                                val cacheKey = "npc_mod_${config.bodyFolder}_${config.hairId}_${npc.facingRight}_${frameIndex}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val bitmap = CharacterSpriteManager.generateAssembledBitmap(context, config, currentlyMoving, System.currentTimeMillis())
                                        if (bitmap != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                                            base64Cache[cacheKey] = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", cacheKey, null, if (npc.facingRight) 1 else -1, npc.displayName, health = npc.health, isDying = npc.isDying)
                            } else if (npc.type == NpcType.POLICE_COP) {
                                // FIX web: el policía A PIE no tiene asset → en web salía como un SVG
                                // genérico (vector verde). Igual que en nativo lo dibujamos como emoji
                                // 👮: generamos su bitmap una vez, lo cacheamos y lo enviamos como
                                // imagen de peatón (type "MODULAR", tamaño ~persona).
                                val cacheKey = "cop_emoji_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val px = (96 * density).toInt().coerceAtLeast(48)
                                        val bitmap = (emojiToDrawable(context, "👮", px) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                            val b64 = "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                base64Cache[cacheKey] = b64
                                            }
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", cacheKey, null, 1, npc.displayName, health = npc.health, isDying = npc.isDying)
                            } else {
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, npc.type.name, null, npc.type.drawableName, null, npc.displayName, health = npc.health, isDying = npc.isDying)
                            }
                        }

                        wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs(${gson.toJson(npcPayloads)});", null)
                        } // fin guard web: lista de NPCs sin cambios → no se reenvía al WebView
                        wv.evaluateJavascript("if(typeof updateCollectibles==='function')updateCollectibles(${JSONObject.quote(collectiblesJson)});", null)

                        // OPT FPS web: serializar y reenviar landmarks SOLO cuando cambian
                        // (+ heartbeat). Antes se hacía gson.toJson + evaluateJavascript en CADA
                        // frame aunque los landmarks no cambian durante el juego.
                        webLmTick[0]++
                        if (uiState.landmarks !== lastWebLandmarkHolder[0] || webLmTick[0] % 45 == 0) {
                        lastWebLandmarkHolder[0] = uiState.landmarks
                        val landmarksPayload = uiState.landmarks.map {
                            LandmarkWebPayload(
                                id = it.id.toString(),
                                lat = it.location.latitude,
                                lng = it.location.longitude,
                                rotation = it.rotationAngle,
                                widthMeters = it.baseWidthMeters,
                                heightMeters = it.baseHeightMeters,
                                scale = it.scaleX,
                                scaleX = it.scaleX,
                                scaleY = it.scaleY,
                                assetPath = it.assetPath,
                                selected = it.id == uiState.selectedLandmarkId
                            )
                        }
                        val landmarksJson = gson.toJson(landmarksPayload)
                        wv.evaluateJavascript("if(typeof updateLandmarks==='function')updateLandmarks(${JSONObject.quote(landmarksJson)});", null)
                        } // fin guard landmarks web (solo se reenvían al cambiar / heartbeat)
                        if (uiState.showRoadNetwork) {
                            val roadsPayload = roadNetwork.map { way ->
                                mapOf(
                                    "id" to way.id.toString(),
                                    "isForCars" to way.isForCars,
                                    "nodes" to way.nodes.map { mapOf("lat" to it.lat, "lon" to it.lon) }
                                )
                            }
                            val roadsJson = gson.toJson(roadsPayload)
                            wv.evaluateJavascript("if(typeof updateRoads==='function')updateRoads(${JSONObject.quote(roadsJson)});", null)
                        } else {
                            wv.evaluateJavascript("if(typeof updateRoads==='function')updateRoads('[]');", null)
                        }
                        val destMarker = uiState.destinationMarker
                        if (destMarker != null) wv.evaluateJavascript("if(typeof updateDestinationMarker==='function')updateDestinationMarker(${destMarker.latitude}, ${destMarker.longitude});", null)
                        else wv.evaluateJavascript("if(typeof clearDestinationMarker==='function')clearDestinationMarker();", null)
                        wv.evaluateJavascript("if(typeof updateDestinationPlacingMode==='function')updateDestinationPlacingMode(${uiState.isTargetingWaypoint});", null)
                        if (uiState.destinationMarker != null && uiState.routeWaypoints.isNotEmpty() && uiState.showDestinationRoute) {
                            val currentLoc = uiState.currentLocation
                            if (currentLoc != null) {
                                val routeJson = uiState.routeWaypoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }.let { gson.toJson(it) }
                                wv.evaluateJavascript("if(typeof updateDestinationRoute==='function')updateDestinationRoute(${currentLoc.latitude}, ${currentLoc.longitude}, $routeJson, true);", null)
                            }
                        } else wv.evaluateJavascript("if(typeof updateDestinationRoute==='function')updateDestinationRoute(0, 0, [], false);", null)

                        // Waypoints de patrullas FUERA de la neblina (paridad con OSM nativo):
                        // 🚓 + línea punteada jugador→patrulla mientras te buscan. Las patrullas
                        // DENTRO de la neblina ya se dibujan como sprite (no llevan waypoint).
                        val plocW = uiState.currentLocation
                        val patrolsW = if (plocW != null && uiState.wantedLevel > 0) {
                            uiState.npcs.filter {
                                it.type == NpcType.POLICE_CAR &&
                                    !npcWithinRadius(it.location.latitude, it.location.longitude,
                                        plocW.latitude, plocW.longitude, NPC_FOG_VISION_METERS)
                            }
                        } else emptyList()
                        if (patrolsW.isNotEmpty() || lastWebPoliceHolder[0]) {
                            lastWebPoliceHolder[0] = patrolsW.isNotEmpty()
                            val policePayload = patrolsW.map {
                                mapOf("id" to it.id, "lat" to it.location.latitude, "lng" to it.location.longitude)
                            }
                            wv.evaluateJavascript("if(typeof updatePolice==='function')updatePolice(${plocW?.latitude ?: 0.0}, ${plocW?.longitude ?: 0.0}, ${gson.toJson(policePayload)});", null)
                        }
                    }
                )
            }
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
                Text("Cargando calles...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ─── ESTADO DE PRE-DESCARGA DE LA ZONA (offline) ─────────────────────
        // No bloqueante: el jugador puede moverse mientras descarga. Avisa si la
        // zona quedó incompleta por falta de red (juego offline garantizado solo
        // cuando termina al 100%).
        if (uiState.zonePrefetchActive || uiState.zoneOfflineWarning || uiState.zoneOfflineReady) {
            val (chipText, chipColor) = when {
                uiState.zonePrefetchActive ->
                    "Descargando zona ${(uiState.zonePrefetchProgress * 100).roundToInt()}%" to Color(0xCC1E2A38)
                uiState.zoneOfflineWarning ->
                    "Sin conexión: zona incompleta" to Color(0xCC8A1F1F)
                else ->
                    "Zona lista offline ✓" to Color(0xCC1F5A2E)
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
            AnimatedVisibility(visible = uiState.showFpsWidget, enter = fadeIn(), exit = fadeOut()) { CacheChip(label = "Rendimiento", text = "$currentFps FPS", color = if (currentFps >= 24) Color(0xFF4CAF50) else Color(0xFFD32F2F), isLoading = false) }
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
                    Text("DISEÑADOR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                        Text("ABANDONAR DISEÑADOR", color = Color(0xFFD4AF37), fontWeight = FontWeight.Black, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Text(
                            "¿Deseas salir del modo diseñador y restaurar la interfaz de juego normal?",
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
                                Text("Seguir editando", color = Color(0xFFFFFFFF), fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    showExitDesignerConfirm = false
                                    viewModel.toggleDesignerMode(false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Aceptar", color = Color.Black, fontWeight = FontWeight.Bold)
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
                    text = "Presiona sobre un edificio para editarlo",
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
                            id = "opciones", label = "Opciones", icon = Icons.Default.Tune,
                            items = buildList {
                                add(OptionMenuItem("Cambiar skin", Icons.Default.Person, Color(0xFFD91B5B)) { viewModel.toggleSkinSelector(true) })
                                add(OptionMenuItem("Teletransportarse...", Icons.Default.LocationOn, Color(0xFFFF9800)) { viewModel.toggleTeleportMenu(true) })
                                // (Submenú "Ir a…" eliminado: "Ir a ESCOM" ya es el primer punto de
                                // "Teletransportarse…" y "Ir a tu Ubicación (GPS)" se movió al inicio
                                // de esa misma lista.)
                                add(OptionMenuItem("Modo Diseñador", Icons.Default.Architecture, if (uiState.isDesignerMode) Color(0xFFD4AF37) else Color.White) { viewModel.toggleDesignerMode(!uiState.isDesignerMode) })
                                add(OptionMenuItem("Debug Interiores", Icons.Default.LocationOn, if (uiState.showInteriorDebugOverlay) Color(0xFFFFC107) else Color.White) { viewModel.toggleInteriorDebugOverlay(!uiState.showInteriorDebugOverlay) })
                                if (uiState.isDesignerMode) {
                                    add(OptionMenuItem("Agregar Asset", Icons.Default.Add, Color(0xFF4CAF50)) { viewModel.showAssetPicker(true) })
                                }
                            }
                        )
                    )
                    add(
                        OptionMenuGroup(
                            id = "mapa", label = "Mapa", icon = Icons.Default.LocationOn,
                            items = buildList {
                                add(OptionMenuItem("Acercar (zoom +)", Icons.Default.Add) { viewModel.zoomIn() })
                                add(OptionMenuItem("Alejar (zoom −)", Icons.Default.Remove) { viewModel.zoomOut() })
                                // Centrar en jugador: SIEMPRE disponible (antes solo al panear),
                                // para volver al jugador en cualquier momento. Si el usuario ha
                                // cambiado el zoom respecto al de juego, esta opción EVOLUCIONA a
                                // un submenú con "Centrar en jugador" y "Hacer zoom en el jugador".
                                run {
                                    val defaultZoom = if (uiState.mapProvider == MapProvider.OSM) ZOOM_GAMEPLAY_OSM else ZOOM_GAMEPLAY_WEB
                                    val isZoomed = kotlin.math.abs(uiState.zoomLevel - defaultZoom) >= 0.5
                                    if (isZoomed) {
                                        add(
                                            OptionMenuGroup(
                                                id = "centrar_jugador", label = "Centrar en jugador",
                                                icon = Icons.Default.Person,
                                                items = buildList {
                                                    add(OptionMenuItem("Centrar en jugador", Icons.Default.Person, Color(0xFF2196F3)) { viewModel.centerOnPlayer() })
                                                    add(OptionMenuItem("Hacer zoom en el jugador", Icons.Default.Add, Color(0xFFD4AF37)) { viewModel.zoomToPlayer() })
                                                }
                                            )
                                        )
                                    } else {
                                        add(OptionMenuItem("Centrar en jugador", Icons.Default.Person, Color(0xFF2196F3)) { viewModel.centerOnPlayer() })
                                    }
                                }
                                if (uiState.isTargetingWaypoint) {
                                    // Apuntando: confirmar o cancelar TAMBIÉN desde el menú (no
                                    // botones flotantes que tapen los controles).
                                    add(OptionMenuItem("Establecer destino aquí", Icons.Default.LocationOn, Color(0xFF4CAF50)) {
                                        if (uiState.mapProvider == MapProvider.OSM) {
                                            nativeMapRef.value?.let { mv ->
                                                val center = mv.mapCenter
                                                viewModel.placeDestinationMarker(center.latitude, center.longitude)
                                            }
                                        } else {
                                            webViewRef.value?.evaluateJavascript("if(window.Android && window.Android.notifyCenterForWaypoint) { var c = map.getCenter(); window.Android.notifyCenterForWaypoint(c.lat, c.lng); }", null)
                                        }
                                    })
                                    add(OptionMenuItem("Cancelar apuntado", Icons.Default.Close, Color(0xFFE53935)) { viewModel.toggleWaypointTargeting(false) })
                                } else if (uiState.isUserPanningMap && !uiState.isDesignerMode && !uiState.isDriving) {
                                    add(OptionMenuItem("Apuntar waypoint", Icons.Default.LocationOn, Color(0xFF4CAF50)) { viewModel.toggleWaypointTargeting(true) })
                                    if (uiState.destinationMarker != null) {
                                        add(OptionMenuItem("Eliminar destino", Icons.Default.Close, Color(0xFFE53935)) { viewModel.clearDestinationMarker() })
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
                        Text("Preparando ${pending.displayName}...", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${pending.displayName} listo ✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.commitMapProvider() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("Cambiar", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                            TextButton(onClick = { viewModel.cancelPendingProvider() }) {
                                Text("Descartar", color = Color(0xFFCCCCCC), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showTeleportMenu) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleTeleportMenu(false) },
                title = { Text("Puntos de Teletransporte", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selecciona tu estatua o destino:", fontSize = 14.sp)
                        LazyColumn(modifier = Modifier.fillMaxHeight(0.5f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Primera opción: tu ubicación REAL (GPS del dispositivo, p. ej. volver a
                            // casa). Movida aquí desde el antiguo submenú "Ir a…".
                            item {
                                Button(onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        try {
                                            LocationServices.getFusedLocationProviderClient(context)
                                                .lastLocation
                                                .addOnSuccessListener { loc -> if (loc != null) viewModel.teleportTo(loc.latitude, loc.longitude) }
                                        } catch (_: SecurityException) {}
                                    }
                                    viewModel.toggleTeleportMenu(false)
                                }, modifier = Modifier.fillMaxWidth()) { Text("📍 Ir a tu Ubicación (GPS)") }
                            }
                            items(TeleportCatalog.zones) { zone ->
                                Button(onClick = { viewModel.teleportTo(zone.latitude, zone.longitude) }, modifier = Modifier.fillMaxWidth()) { Text(zone.name) }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { viewModel.toggleTeleportMenu(false) }) { Text("Cancelar") } }
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
                onDismiss      = { viewModel.toggleSkinSelector(false) }
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

        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val maxScale = if (isPortrait) 1.0f else 1.4f
        val effectiveScale = uiState.controlsScale.coerceAtMost(maxScale)
        val sidePadding = if (isPortrait) 16.dp else 64.dp
        val bottomPadding = if (isPortrait) 48.dp else 32.dp

        // En HORIZONTAL, al abrir el menú de Opciones, este (arriba a la derecha) se
        // extiende hacia abajo y choca con el control de la derecha (D-pad/diamante).
        // Desplazamos ese control hacia la izquierda mientras el menú está abierto para
        // que el usuario pueda usar el menú (con su scroll) sin que tape los botones.
        val isMenuOpenLandscape = optionsExpanded && !isPortrait
        val rightCtrlShift by animateDpAsState(
            targetValue = if (isMenuOpenLandscape) (-150).dp else 0.dp,
            label = "rightCtrlShift"
        )
        val rightShiftMod = Modifier.offset(x = rightCtrlShift)

        if (!uiState.isDesignerMode) { // Oculta joystick y botones en modo diseñador
            Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = bottomPadding, start = sidePadding, end = sidePadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isDriving) {
                // D-pad de conducción: SOLO gira (IZQ/DER). Arriba/abajo quedan inertes
                // a propósito — gas y freno viven únicamente en el diamante PS4.
                val drivingDpad = @Composable { m: Modifier ->
                    VehicleDPadController(
                        modifier = m.scale(effectiveScale),
                        onUp = { /* sin uso en conducción */ },
                        onDown = { /* sin uso en conducción */ },
                        onLeft = { viewModel.steerLeft(it) },
                        onRight = { viewModel.steerRight(it) }
                    )
                }
                // Diamante estilo PS4: △ SALIR · ✕ gas · ○ freno · □ freno de mano.
                val drivingActions = @Composable { m: Modifier ->
                    Ps4ActionButtonsController(
                        modifier = m.scale(effectiveScale),
                        onAccelerate = { viewModel.accelerate(it) },
                        onBrake = { viewModel.brake(it) },
                        onHandbrake = { viewModel.brake(it) },
                        onExit = { isPressed ->
                            if (isPressed) {
                                viewModel.onInteractButtonPressed()
                                yButtonHoldJob?.cancel()
                                yButtonHoldJob = coroutineScope.launch { kotlinx.coroutines.delay(3000); viewModel.toggleTeleportMenu(true) }
                            } else { yButtonHoldJob?.cancel() }
                        }
                    )
                }
                // El control de la DERECHA (segundo) recibe el desplazamiento.
                if (uiState.swapControls) { drivingActions(Modifier); drivingDpad(rightShiftMod) } else { drivingDpad(Modifier); drivingActions(rightShiftMod) }
            } else {
                    val movementComponent = @Composable { m: Modifier ->
                        if (uiState.controlType == ControlType.DPAD) DPadController(modifier = m.scale(effectiveScale), onDirectionPressed = { viewModel.moveCharacter(it) })
                        else JoystickController(modifier = m.scale(effectiveScale), onMove = { viewModel.moveCharacterByAngle(it) })
                    }
                    val actionComponent = @Composable { m: Modifier ->
                        ActionButtonsController(
                            modifier = m.scale(effectiveScale),
                            onActionChanged = { action, isPressed ->
                                if (action == GameAction.X && isPressed) {
                                    viewModel.handleInteraction()
                                }
                                if (action == GameAction.Y) {
                                    if (isPressed) {
                                        viewModel.onInteractButtonPressed()
                                        yButtonHoldJob?.cancel()
                                        yButtonHoldJob = coroutineScope.launch { kotlinx.coroutines.delay(3000); viewModel.toggleTeleportMenu(true) }
                                    } else {
                                        yButtonHoldJob?.cancel()
                                    }
                                }
                                viewModel.updateActionState(action, isPressed)
                            },
                            onClaimCollectiblePressed = { viewModel.onClaimCollectiblePressed() }
                        )
                    }
                    // El control de la DERECHA (segundo) recibe el desplazamiento.
                    if (uiState.swapControls) { actionComponent(Modifier); movementComponent(rightShiftMod) } else { movementComponent(Modifier); actionComponent(rightShiftMod) }
                }
            }
        }
        //}
    }

    if (uiState.showWastedScreen) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = {})) {
            var scale by remember { mutableStateOf(0.5f) }
            LaunchedEffect(Unit) {
                androidx.compose.animation.core.animate(initialValue = 0.5f, targetValue = 1.3f, animationSpec = tween(durationMillis = 3500, easing = LinearOutSlowInEasing)) { value, _ -> scale = value }
            }
            Text(text = "WASTED", color = Color(0xFFD32F2F), fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif, letterSpacing = 6.sp, modifier = Modifier.align(Alignment.Center).scale(scale))
        }
    }
    if (uiState.showZombiVideo) {
        ZombiVideoPlayer(
            context = context,
            onDismiss = { viewModel.dismissVideo() }
        )
    }

    uiState.interactionPrompt?.let { promptText ->
        Box(modifier = Modifier.fillMaxSize().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
            Text(text = promptText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp, modifier = Modifier.background(color = Color(0xFF3B0D1B).copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp)).padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }

    uiState.showClaimedPopupFor?.let { collectible ->
        CollectibleClaimDialog(collectible = collectible, onDismiss = { viewModel.dismissClaimedPopup() })
    }

    // ─── ESCOM Door Fade Overlay ─────────────────────────────────────────────
    val escomFadeAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(uiState.showEscomDoorFade) {
        if (uiState.showEscomDoorFade) {
            escomFadeAlpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
            viewModel.onEscomDoorFadeComplete()
            kotlinx.coroutines.delay(200)
            escomFadeAlpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(400))
        }
    }

    if (escomFadeAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = escomFadeAlpha.value))
        )
    }

    // ─── Metro Door Fade Overlay ─────────────────────────────────────────────
    val metroFadeAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(uiState.showMetroFade) {
        if (uiState.showMetroFade) {
            metroFadeAlpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
            viewModel.onMetroFadeComplete()
            kotlinx.coroutines.delay(200)
            metroFadeAlpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(400))
        }
    }
    if (metroFadeAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = metroFadeAlpha.value))
        )
    }

    LaunchedEffect(uiState.metroFadeCompleteStation) {
        val station = uiState.metroFadeCompleteStation
        if (station != null) {
            viewModel.consumeMetroFadeComplete()
            onNavigateToInterior("metro_station_interior/${station.name}")
        }
    }
}
