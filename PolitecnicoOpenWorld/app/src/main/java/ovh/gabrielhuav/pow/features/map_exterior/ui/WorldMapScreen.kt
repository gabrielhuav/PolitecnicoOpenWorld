package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
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
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerCharacter
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehiclePedalsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSteeringController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
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
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import kotlin.math.cos

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
    val base64Cache = remember { java.util.concurrent.ConcurrentHashMap<String, String>() }
    val widthCache = remember { java.util.concurrent.ConcurrentHashMap<String, Float>() }
    val heightCache = remember { java.util.concurrent.ConcurrentHashMap<String, Float>() }
    val nativeDrawableCache = remember { mutableMapOf<String, android.graphics.drawable.Drawable>() }
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
    val nativeMapRef = remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(uiState.isUserPanningMap) {
        if (!uiState.isUserPanningMap) {
            webViewRef.value?.evaluateJavascript("if(typeof exitExplorationMode==='function')exitExplorationMode();", null)
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

                        cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(newPosition), 120)
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
                        zoomGesturesEnabled = false,
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
                                val widthMeters = (landmark.baseWidthMeters * landmark.scaleFactor).toFloat()
                                val heightMeters = (landmark.baseHeightMeters * landmark.scaleFactor).toFloat()

                                val isDoorGM = landmark.assetPath.contains("DOORS/")
                                var doorAnimDescriptor by remember(landmark.id) {
                                    mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null)
                                }
                                if (isDoorGM) {
                                    LaunchedEffect(landmark.id) {
                                        while (true) {
                                            doorAnimDescriptor = BitmapDescriptorFactory.fromBitmap(
                                                buildDoorEffectBitmap(bitmap, context)
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
                                    val markerState = remember(landmark.id) { MarkerState(position = center) }
                                    markerState.position = center
                                    val pencilIcon = remember(uiState.selectedLandmarkId == landmark.id) {
                                        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_edit)?.mutate()
                                        if (uiState.selectedLandmarkId == landmark.id) drawable?.setTint(android.graphics.Color.RED)
                                        val bm = android.graphics.Bitmap.createBitmap(drawable!!.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bm)
                                        drawable.setBounds(0, 0, bm.width, bm.height)
                                        drawable.draw(canvas)
                                        BitmapDescriptorFactory.fromBitmap(bm)
                                    }
                                    com.google.maps.android.compose.Marker(
                                        state = markerState,
                                        draggable = true,
                                        icon = pencilIcon,
                                        onClick = { viewModel.selectLandmark(landmark.id); true }
                                    )
                                    LaunchedEffect(markerState.position) {
                                        if (markerState.dragState == com.google.maps.android.compose.DragState.DRAG) {
                                            viewModel.moveSelectedLandmark(markerState.position.latitude - landmark.location.latitude, markerState.position.longitude - landmark.location.longitude)
                                        }
                                    }
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
                        wv.evaluateJavascript("if(typeof setDesignerMode==='function')setDesignerMode(${uiState.isDesignerMode});", null)
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
                        val centerCullW = uiState.currentLocation
                        val cullRadiusMW = centerCullW?.let { npcVisionRadiusMeters() }
                        val visibleNpcs = if (cullRadiusMW != null && centerCullW != null) {
                            uiState.npcs.filter {
                                npcWithinRadius(it.location.latitude, it.location.longitude,
                                    centerCullW.latitude, centerCullW.longitude, cullRadiusMW)
                            }
                        } else uiState.npcs

                        val npcPayloads = visibleNpcs.map { npc ->
                            if (npc.type == NpcType.CAR) {
                                var angle = npc.rotationAngle % 360f
                                if (angle < 0) angle += 360f
                                val frameIndex = (angle / 7.5f).roundToInt() % 48
                                val cacheKey = "${npc.carModel.name}_${frameIndex}_${npc.carColor}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val drawable = VehicleSpriteManager.getTintedCarNpc(context, angle, npc.carColor, highResRenderScale, npc.carModel)
                                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            widthCache[cacheKey] = (bitmap.width / density) / density
                                            heightCache[cacheKey] = (bitmap.height / density) / density
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                                            base64Cache[cacheKey] = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, "CAR", cacheKey, null, null, npc.displayName, widthCache[cacheKey], heightCache[cacheKey])
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
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", cacheKey, null, if (npc.facingRight) 1 else -1, npc.displayName)
                            } else {
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, npc.type.name, null, npc.type.drawableName, null, npc.displayName)
                            }
                        }

                        wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs(${gson.toJson(npcPayloads)});", null)
                        wv.evaluateJavascript("if(typeof updateCollectibles==='function')updateCollectibles(${JSONObject.quote(collectiblesJson)});", null)

                        val landmarksPayload = uiState.landmarks.map {
                            LandmarkWebPayload(
                                id = it.id.toString(),
                                lat = it.location.latitude,
                                lng = it.location.longitude,
                                rotation = it.rotationAngle,
                                widthMeters = it.baseWidthMeters,
                                heightMeters = it.baseHeightMeters,
                                scale = it.scaleFactor,
                                assetPath = it.assetPath
                            )
                        }
                        val landmarksJson = gson.toJson(landmarksPayload)
                        wv.evaluateJavascript("if(typeof updateLandmarks==='function')updateLandmarks(${JSONObject.quote(landmarksJson)});", null)
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
                    }
                )
            }
        }

        // ─── CAPA DE NEBLINA (fog of war estilo Age of Empires) ──────────────
        // El radio visible se fija en METROS reales (no cambia con el zoom): se
        // convierte a píxeles según el zoom actual. Fuera del radio se aplica un
        // gris translúcido (no negro total), suficiente para ocultar NPCs.
        if (!uiState.isUserPanningMap) {
            val fogLat = uiState.currentLocation?.latitude ?: 19.5
            val fogMpp = metersPerPixel(uiState.zoomLevel, fogLat)
            val fogRevealPx = (NPC_FOG_VISION_METERS / fogMpp).toFloat()
            Canvas(modifier = Modifier.fillMaxSize()) {
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
            PlayerCharacter(uiState = uiState, modifier = Modifier.align(Alignment.Center), health = viewModel.playerHealth, showHealthBar = viewModel.showHealthBar, damagePulseTrigger = viewModel.damagePulseTrigger)
        }

        LowHealthAura(health = viewModel.playerHealth)

        if (!uiState.isRoadNetworkReady) {
            Row(modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), Color(0xFFD4AF37), strokeWidth = 2.dp)
                Text("Cargando calles...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(modifier = Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = uiState.showCacheWidget, enter = fadeIn(), exit = fadeOut()) { CacheStatusWidget(roadSource = uiState.roadSource, tileSource = uiState.tileSource, mapProvider = uiState.mapProvider) }
            AnimatedVisibility(visible = uiState.showFpsWidget, enter = fadeIn(), exit = fadeOut()) { CacheChip(label = "Rendimiento", text = "$currentFps FPS", color = if (currentFps >= 24) Color(0xFF4CAF50) else Color(0xFFD32F2F), isLoading = false) }
            AnimatedVisibility(visible = uiState.isDesignerMode, enter = fadeIn(), exit = fadeOut()) {
                Row(modifier = Modifier.background(Color(0xFFD4AF37).copy(alpha = 0.85f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Architecture, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Text("DISEÑADOR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape)) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black) }
            IconButton(onClick = { viewModel.toggleSkinSelector(true) }, modifier = Modifier.background(Color(0xFFD91B5B).copy(alpha = 0.9f), CircleShape)) { Icon(Icons.Default.Person, "Cambiar skin", tint = Color.White) }
            IconButton(onClick = { viewModel.teleportTo(19.5045, -99.1469) }, modifier = Modifier.background(Color(0xFF3B0D1B).copy(alpha = 0.8f), CircleShape)) { Icon(Icons.Default.School, "Ir a ESCOM", tint = Color.White) }
            IconButton(onClick = { viewModel.toggleDesignerMode(!uiState.isDesignerMode) }, modifier = Modifier.background(if (uiState.isDesignerMode) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.8f), CircleShape)) { Icon(Icons.Default.Architecture, "Modo Diseñador", tint = Color.Black) }
            IconButton(onClick = { viewModel.toggleInteriorDebugOverlay(!uiState.showInteriorDebugOverlay) }, modifier = Modifier.background(if (uiState.showInteriorDebugOverlay) Color(0xFFFFC107) else Color.White.copy(alpha = 0.8f), CircleShape)) { Icon(Icons.Default.LocationOn, "Debug Interiores", tint = Color.Black) }
            if (uiState.isDesignerMode) {
                IconButton(onClick = { viewModel.showAssetPicker(true) }, modifier = Modifier.background(Color(0xFF4CAF50), CircleShape)) { Icon(Icons.Default.Add, "Agregar Asset", tint = Color.White) }
            }
        }

        Column(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { viewModel.zoomIn() }, modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape).size(48.dp)) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
            IconButton(onClick = { viewModel.zoomOut() }, modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape).size(48.dp)) { Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
            if (uiState.isUserPanningMap) {
                IconButton(onClick = { viewModel.centerOnPlayer() }, modifier = Modifier.background(Color(0xFF2196F3), CircleShape).size(48.dp)) { Icon(Icons.Default.Person, "Centrar en personaje", tint = Color.White) }
            }
            if (uiState.isUserPanningMap && !uiState.isDesignerMode && !uiState.isDriving) {
                IconButton(onClick = { viewModel.toggleWaypointTargeting(!uiState.isTargetingWaypoint) }, modifier = Modifier.background(if (uiState.isTargetingWaypoint) Color(0xFFFF5722) else Color(0xFF4CAF50), CircleShape).size(48.dp)) { Icon(Icons.Default.LocationOn, "Apuntar waypoint", tint = Color.White) }
                if (uiState.destinationMarker != null && !uiState.isTargetingWaypoint) {
                    IconButton(onClick = { viewModel.clearDestinationMarker() }, modifier = Modifier.background(Color(0xFFE53935), CircleShape).size(48.dp)) { Icon(imageVector = Icons.Default.Add, contentDescription = "Eliminar destino", tint = Color.White, modifier = Modifier.rotate(45f)) }
                }
            }
        }

        if (uiState.isTargetingWaypoint) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(48.dp).graphicsLayer { translationY = -24.dp.toPx() })
                    Box(modifier = Modifier.size(4.dp).background(Color.White, CircleShape))
                }
            }
            Box(modifier = Modifier.fillMaxSize().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { viewModel.toggleWaypointTargeting(false) }, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray), shape = RoundedCornerShape(24.dp)) { Text("CANCELAR", fontWeight = FontWeight.Bold) }
                    Button(onClick = {
                        if (uiState.mapProvider == MapProvider.OSM) {
                            nativeMapRef.value?.let { mv ->
                                val center = mv.mapCenter
                                viewModel.placeDestinationMarker(center.latitude, center.longitude)
                            }
                        } else {
                            webViewRef.value?.evaluateJavascript("if(window.Android && window.Android.notifyCenterForWaypoint) { var c = map.getCenter(); window.Android.notifyCenterForWaypoint(c.lat, c.lng); }", null)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), shape = RoundedCornerShape(24.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)) { Text("ESTABLECER DESTINO", fontWeight = FontWeight.Bold) }
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
            DesignerPanel(
                landmark = selectedLandmark,
                onMove = { dLat, dLon -> viewModel.moveSelectedLandmark(dLat, dLon) },
                onRotate = { angle -> viewModel.rotateSelectedLandmark(angle) },
                onScale = { scale -> viewModel.scaleSelectedLandmark(scale) },
                onDelete = { viewModel.deleteSelectedLandmark(context) },
                onSave = { viewModel.saveSelectedLandmark(context) },
                onExport = { exportLauncher.launch("landmarks_config.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                onDeselect = { viewModel.selectLandmark(null) },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 130.dp, start = 12.dp, end = 12.dp).fillMaxWidth(0.9f)
            )
        }

        if (!uiState.isDesignerMode) {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val maxScale = if (isPortrait) 1.0f else 1.4f
            val effectiveScale = uiState.controlsScale.coerceAtMost(maxScale)
            val sidePadding = if (isPortrait) 16.dp else 64.dp
            val bottomPadding = if (isPortrait) 48.dp else 32.dp

            Row(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = bottomPadding, start = sidePadding, end = sidePadding), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isDriving) {
                    val steeringComponent = @Composable { VehicleSteeringController(modifier = Modifier.scale(effectiveScale), onSteerLeft = { viewModel.steerLeft(it) }, onSteerRight = { viewModel.steerRight(it) }) }
                    val pedalsComponent = @Composable { VehiclePedalsController(modifier = Modifier.scale(effectiveScale), onAccelerate = { viewModel.accelerate(it) }, onBrake = { viewModel.brake(it) }, onExit = { isPressed ->
                        if (isPressed) {
                            viewModel.onInteractButtonPressed()
                            yButtonHoldJob?.cancel()
                            yButtonHoldJob = coroutineScope.launch { kotlinx.coroutines.delay(3000); viewModel.toggleTeleportMenu(true) }
                        } else { yButtonHoldJob?.cancel() }
                    }) }
                    if (uiState.swapControls) { pedalsComponent(); steeringComponent() } else { steeringComponent(); pedalsComponent() }
                } else {
                    val movementComponent = @Composable {
                        if (uiState.controlType == ControlType.DPAD) DPadController(modifier = Modifier.scale(effectiveScale), onDirectionPressed = { viewModel.moveCharacter(it) })
                        else JoystickController(modifier = Modifier.scale(effectiveScale), onMove = { viewModel.moveCharacterByAngle(it) })
                    }
                    val actionComponent = @Composable {
                        ActionButtonsController(
                            modifier = Modifier.scale(effectiveScale),
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
                    if (uiState.swapControls) { actionComponent(); movementComponent() } else { movementComponent(); actionComponent() }
                }
            }
        }
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
}
