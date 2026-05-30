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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
    var hasTriggeredNativePan by remember { mutableStateOf(false) }

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
        .systemBarsPadding()) {

        if (uiState.isLoadingLocation) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            Text("Iniciando mundo...", modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp))
            return@Box
        }

        // ───── CAPA 1: MAPA ────────────────────────────────────────────────────
        when (uiState.mapProvider) {
            MapProvider.OSM -> {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(uiState.zoomLevel)
                            nativeMapRef.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        if (uiState.isDesignerMode) {
                            view.setOnTouchListener(null)
                            view.isClickable = true
                        } else {
                            view.setOnTouchListener { _, event ->
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> hasTriggeredNativePan = false
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        if (!hasTriggeredNativePan) {
                                            viewModel.onMapPanStart()
                                            hasTriggeredNativePan = true
                                        }
                                    }
                                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                        if (hasTriggeredNativePan) {
                                            viewModel.onMapPanEnd()
                                            hasTriggeredNativePan = false
                                        }
                                    }
                                }
                                false
                            }
                            view.isClickable = false
                        }

                        if (!uiState.isUserPanningMap) {
                            uiState.currentLocation?.let { view.controller.setCenter(it) }
                        }

                        view.mapOrientation = if (uiState.isDriving) -uiState.vehicleRotation else 0f

                        if (uiState.isUserPanningMap) {
                            @Suppress("UNCHECKED_CAST")
                            val playerMarker = (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag) as? Marker)
                                ?: Marker(view).apply {
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    val dot = android.graphics.drawable.GradientDrawable().apply {
                                        shape = android.graphics.drawable.GradientDrawable.OVAL
                                        setColor(android.graphics.Color.GREEN)
                                        setStroke(4, android.graphics.Color.WHITE)
                                        setSize(40, 40)
                                    }
                                    icon = dot
                                    view.setTag(ovh.gabrielhuav.pow.R.id.player_marker_tag, this)
                                    view.overlays.add(this)
                                }
                            uiState.currentLocation?.let { playerMarker.position = it; playerMarker.setAlpha(1f) }
                        } else {
                            (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag) as? Marker)?.setAlpha(0f)
                        }

                        val destMarker = (view.getTag(ovh.gabrielhuav.pow.R.id.dest_marker_tag) as? Marker)
                            ?: Marker(view).apply {
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                icon = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)
                                icon?.setTint(android.graphics.Color.RED)
                                view.setTag(ovh.gabrielhuav.pow.R.id.dest_marker_tag, this)
                                view.overlays.add(this)
                            }

                        if (uiState.destinationMarker != null) {
                            destMarker.position = uiState.destinationMarker
                            destMarker.isEnabled = true
                            destMarker.isDraggable = false
                            destMarker.setAlpha(1f)
                        } else {
                            destMarker.isEnabled = false
                            destMarker.closeInfoWindow()
                            destMarker.setAlpha(0f)
                        }

                        val routeOverlay = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag) as? Polyline)
                            ?: Polyline().apply {
                                outlinePaint.color = android.graphics.Color.BLUE
                                outlinePaint.strokeWidth = 5f
                                view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag, this)
                                view.overlays.add(0, this)
                            }

                        if (uiState.destinationMarker != null && uiState.routeWaypoints.isNotEmpty() && uiState.showDestinationRoute) {
                            routeOverlay.setPoints(uiState.routeWaypoints)
                            routeOverlay.isEnabled = true
                        } else {
                            routeOverlay.isEnabled = false
                        }

                        val zoomDiff = abs(view.zoomLevelDouble - uiState.zoomLevel)
                        when {
                            zoomDiff < 0.01 -> {}
                            zoomDiff > 1.5  -> {
                                if (!uiState.isUserPanningMap) {
                                    view.controller.animateTo(uiState.currentLocation, uiState.zoomLevel, 120L)
                                }
                            }
                            else            -> view.controller.setZoom(uiState.zoomLevel)
                        }

                        if (uiState.isRoadNetworkReady) {
                            @Suppress("UNCHECKED_CAST")
                            val markerCache = (view.tag as? MutableMap<String, Marker>)
                                ?: mutableMapOf<String, Marker>().also { view.tag = it }

                            val currentZoom = view.zoomLevelDouble
                            val isZoomedIn = currentZoom >= 16
                            val timeMs = System.currentTimeMillis()
                            val screenDensity = context.resources.displayMetrics.density
                            val highResRenderScale = 1.0f * screenDensity

                            val currentNpcIds = uiState.npcs.map { it.id }.toSet()
                            val iterator = markerCache.iterator()
                            while (iterator.hasNext()) {
                                val entry = iterator.next()
                                if (!currentNpcIds.contains(entry.key)) {
                                    view.overlays.remove(entry.value)
                                    iterator.remove()
                                }
                            }

                            uiState.npcs.forEach { npc ->
                                val id = npc.id
                                val marker = markerCache[id] ?: Marker(view).apply {
                                    title = "NPC_MARKER"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    setInfoWindow(null)
                                    isFlat = true
                                    markerCache[id] = this
                                    view.overlays.add(this)
                                }

                                if (isZoomedIn) {
                                    if (npc.isDying) {
                                        marker.setAlpha(0.3f)
                                    } else {
                                        marker.setAlpha(1f)
                                    }

                                    if (npc.visualConfig != null) {
                                        val currentlyMoving = npc.speed > 0 || npc.isMoving
                                        val personSzDp = (24.0 + ((currentZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                        val exactPixels = (personSzDp * screenDensity).toInt()

                                        val frameIndex = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0
                                        val cacheKey = "PED_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${npc.health}_D${npc.isDying}"

                                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                            var baseDrawable = CharacterSpriteManager.getModularNpcDrawable(
                                                context = context,
                                                visualConfig = npc.visualConfig!!,
                                                isMoving = currentlyMoving,
                                                isFacingRight = npc.facingRight,
                                                timeMs = timeMs,
                                                scale = highResRenderScale,
                                                displayName = npc.displayName
                                            )
                                            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                            baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                                ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                        marker.icon = cachedIcon
                                        marker.rotation = 0f

                                    } else if (npc.type == NpcType.CAR) {
                                        var angle = npc.rotationAngle % 360f
                                        if (angle < 0) angle += 360f
                                        val frameIndex = (angle / 7.5f).roundToInt() % 48
                                        val dynamicScale = (1.4 * 2.0.pow(currentZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                        val cacheKey = "CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${dynamicScale}_H${npc.health}_D${npc.isDying}"

                                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                            var baseDrawable = VehicleSpriteManager.getTintedCarNpc(
                                                context, angle, npc.carColor, highResRenderScale, npc.carModel
                                            )
                                            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                            baseDrawable?.let { drawable ->
                                                val baseWidthDp = (drawable.intrinsicWidth / screenDensity) / screenDensity
                                                val baseHeightDp = (drawable.intrinsicHeight / screenDensity) / screenDensity
                                                val finalWidthPx = (baseWidthDp * dynamicScale * screenDensity).toInt()
                                                val finalHeightPx = (baseHeightDp * dynamicScale * screenDensity).toInt()
                                                ExactSizeDrawable(drawable, finalWidthPx, finalHeightPx)
                                            } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                        marker.icon = cachedIcon
                                        marker.rotation = 0f
                                    } else {
                                        val cacheKey = "SVG_${npc.type.name}_H${npc.health}_D${npc.isDying}"
                                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                            val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                            var baseDrawable = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                            baseDrawable?.let {
                                                val exactPixels = (24 * screenDensity).toInt()
                                                ExactSizeDrawable(it, exactPixels, exactPixels)
                                            } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                        marker.icon = cachedIcon
                                        marker.rotation = 0f
                                    }
                                } else {
                                    marker.setAlpha(0f)
                                }
                                marker.position = GeoPoint(npc.location.latitude, npc.location.longitude)
                            }

                            val activeCollectibleIds = allCollectibles.map { it.id }.toSet()
                            @Suppress("UNCHECKED_CAST")
                            val collectibleMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag) as? MutableMap<String, Marker>)
                                ?: mutableMapOf<String, Marker>().also { view.setTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag, it) }

                            val colIterator = collectibleMarkerCache.iterator()
                            while (colIterator.hasNext()) {
                                val entry = colIterator.next()
                                if (!activeCollectibleIds.contains(entry.key)) {
                                    view.overlays.remove(entry.value)
                                    colIterator.remove()
                                }
                            }

                            allCollectibles.forEach { collectible ->
                                Log.d("DEBUG_RENDER", "Intentando dibujar coleccionable: ${collectible.name} en ${collectible.latitude}")
                                val id = collectible.id
                                val marker = collectibleMarkerCache[id] ?: Marker(view).apply {
                                    title = "COLLECTIBLE"
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    isFlat = true
                                    collectibleMarkerCache[id] = this
                                    view.overlays.add(this)
                                }

                                if (isZoomedIn) {
                                    marker.setAlpha(1f)
                                    val exactPixels = (22 * screenDensity).toInt()
                                    val cacheKey = "COL_${collectible.assetPath}"
                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        try {
                                            val bitmap = android.graphics.BitmapFactory.decodeStream(context.assets.open(collectible.assetPath))
                                            if (bitmap != null) {
                                                val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                                                    shape = android.graphics.drawable.GradientDrawable.OVAL
                                                    setSize(exactPixels, exactPixels)
                                                    setColor(android.graphics.Color.argb(100, 255, 235, 59))
                                                }
                                                val spriteDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                                val spriteSize = (exactPixels * 0.90).toInt()
                                                spriteDrawable.setFilterBitmap(false)
                                                val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(glowDrawable, spriteDrawable))
                                                val inset = ((exactPixels - spriteSize) / 2)
                                                layerDrawable.setLayerInset(1, inset, inset, inset, inset)
                                                ExactSizeDrawable(layerDrawable, exactPixels, exactPixels)
                                            } else ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        } catch (e: Exception) {
                                            ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                    }
                                    marker.icon = cachedIcon
                                    val isHand = collectible.name == "Objeto Misterioso ESCOM"
                                    marker.rotation = if (isHand) 0f else ((System.currentTimeMillis() / 30) % 360).toFloat()
                                } else {
                                    marker.setAlpha(0f)
                                }
                                marker.position = GeoPoint(collectible.latitude, collectible.longitude)
                            }
                        }

                        @Suppress("UNCHECKED_CAST")
                        val landmarkCache = (view.getTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag) as? MutableMap<Long, MutableList<Overlay>>)
                            ?: mutableMapOf<Long, MutableList<Overlay>>().also { view.setTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag, it) }

                        val currentIds = uiState.landmarks.map { it.id }.toSet()
                        val landmarkIterator = landmarkCache.iterator()
                        while (landmarkIterator.hasNext()) {
                            val entry = landmarkIterator.next()
                            if (!currentIds.contains(entry.key)) {
                                entry.value.forEach { overlay -> view.overlays.remove(overlay) }
                                landmarkIterator.remove()
                            }
                        }

                        uiState.landmarks.forEach { landmark ->
                            val overlays = landmarkCache.getOrPut(landmark.id) { mutableListOf() }
                            val bitmap = landmarkBitmapCache.getOrPut(landmark.assetPath) {
                                try {
                                    context.assets.open(landmark.assetPath).use { val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }; android.graphics.BitmapFactory.decodeStream(it, null, o) }
                                } catch (e: Exception) { null }
                            }
                            if (bitmap == null) return@forEach

                            val isDoorAsset = landmark.assetPath.contains("DOORS/")
                            val groundOverlay = overlays.filterIsInstance<org.osmdroid.views.overlay.GroundOverlay>().firstOrNull()
                                ?: org.osmdroid.views.overlay.GroundOverlay().apply {
                                    overlays.add(this)
                                    if (isDoorAsset) view.overlays.add(this) else view.overlays.add(0, this)
                                }


                            val center = GeoPoint(landmark.location.latitude, landmark.location.longitude)
                            val halfW = (landmark.baseWidthMeters * landmark.scaleFactor) / 2.0
                            val halfH = (landmark.baseHeightMeters * landmark.scaleFactor) / 2.0
                            val d = sqrt(halfW * halfW + halfH * halfH)
                            val theta = Math.toDegrees(atan2(halfW, halfH))

                            val pTL = center.destinationPoint(d, landmark.rotationAngle.toDouble() - theta)
                            val pTR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + theta)
                            val pBR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 - theta)
                            val pBL = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 + theta)

                            groundOverlay.setPosition(pTL, pTR, pBR, pBL)
                            groundOverlay.setImage(if (isDoorAsset) buildDoorEffectBitmap(bitmap, context) else bitmap)

                            // Limpiar cualquier marcador DOOR_PULSE residual de la versión anterior
                            overlays.filterIsInstance<Marker>().filter { it.title == "DOOR_PULSE" }.forEach { m ->
                                view.overlays.remove(m); overlays.remove(m)
                            }
                            val existingControl = overlays.filterIsInstance<Marker>().firstOrNull()
                            if (uiState.isDesignerMode) {
                                val controlMarker = existingControl ?: Marker(view).apply {
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_edit)?.mutate()
                                    overlays.add(this)
                                    view.overlays.add(this)
                                }
                                controlMarker.position = center
                                if (uiState.selectedLandmarkId == landmark.id) controlMarker.icon?.setTint(android.graphics.Color.RED)
                                else controlMarker.icon?.setTintList(null)

                                controlMarker.setOnMarkerClickListener { _, _ -> viewModel.selectLandmark(landmark.id); true }
                                controlMarker.isDraggable = true
                                controlMarker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                                    override fun onMarkerDragStart(marker: Marker) { viewModel.selectLandmark(landmark.id) }
                                    override fun onMarkerDrag(marker: Marker) {
                                        viewModel.moveSelectedLandmark(marker.position.latitude - landmark.location.latitude, marker.position.longitude - landmark.location.longitude)
                                    }
                                    override fun onMarkerDragEnd(marker: Marker) {}
                                })
                            } else {
                                existingControl?.let { view.overlays.remove(it); overlays.remove(it) }
                            }
                        }

                        // ─── CAPA INTERMEDIA: RED DE CAMINOS ─────────────────────
                        val roadOverlayTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 500
                        @Suppress("UNCHECKED_CAST")
                        val roadLineCache = (view.getTag(roadOverlayTag) as? MutableList<Polyline>)
                            ?: mutableListOf<Polyline>().also { view.setTag(roadOverlayTag, it) }

                        roadLineCache.forEach { view.overlays.remove(it) }
                        roadLineCache.clear()

                        if (uiState.showRoadNetwork) {
                            val lmCount = landmarkCache.values.sumOf { it.size }
                            roadNetwork.forEach { way ->
                                val line = Polyline().apply {
                                    outlinePaint.color = if (way.isForCars)
                                        android.graphics.Color.argb(180, 255, 215, 0)
                                    else
                                        android.graphics.Color.argb(180, 130, 200, 255)
                                    outlinePaint.strokeWidth = if (way.isForCars) 6f else 4f
                                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                    outlinePaint.isAntiAlias = true
                                    setPoints(way.nodes.map { GeoPoint(it.lat, it.lon) })
                                }
                                roadLineCache.add(line)
                                view.overlays.add(lmCount.coerceAtMost(view.overlays.size), line)
                            }
                        }

                        // ─── OVERLAY DEBUG DE INTERIORES ──────────────────────────
                        @Suppress("UNCHECKED_CAST")
                        val debugMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag.let { it + 100 }) as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also {
                                view.setTag(ovh.gabrielhuav.pow.R.id.player_marker_tag.let { it + 100 }, it)
                            }

                        if (uiState.showInteriorDebugOverlay) {
                            InteriorBuilding.entries.forEach { b ->
                                val marker = debugMarkerCache[b.id] ?: Marker(view).apply {
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    val dot = android.graphics.drawable.GradientDrawable().apply {
                                        shape = android.graphics.drawable.GradientDrawable.OVAL
                                        setColor(android.graphics.Color.YELLOW)
                                        setStroke(3, android.graphics.Color.BLACK)
                                        setSize(28, 28)
                                    }
                                    icon = dot
                                    title = b.displayName
                                    debugMarkerCache[b.id] = this
                                    view.overlays.add(this)
                                }
                                marker.position = b.location
                                marker.setAlpha(1f)
                            }

                            val bbox = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }) as? Polyline)
                                ?: Polyline().apply {
                                    outlinePaint.color = android.graphics.Color.YELLOW
                                    outlinePaint.strokeWidth = 4f
                                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                    view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }, this)
                                    view.overlays.add(this)
                                }
                            val bb = EscomBoundingBox
                            bbox.setPoints(listOf(bb.topLeft, bb.topRight, bb.bottomRight, bb.bottomLeft, bb.topLeft))
                            bbox.isEnabled = true
                        } else {
                            debugMarkerCache.values.forEach { it.setAlpha(0f) }
                            (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }) as? Polyline)?.isEnabled = false
                        }

                        view.invalidate()
                    }
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

                        uiState.npcs.forEach { npc ->
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
                        val npcPayloads = uiState.npcs.map { npc ->
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

@Composable
fun LowHealthAura(health: Float) {
    if (health > 35f) return

    val infiniteTransition = rememberInfiniteTransition(label = "lowHealthAura")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // A medida que la vida baja de 35 a 0, el efecto es más pronunciado
    val intensity = (1f - (health / 35f)).coerceIn(0f, 1f)
    val currentAlpha = alpha * intensity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1.0f to Color.Red.copy(alpha = currentAlpha),
                )
            )
    )
}

@Composable
private fun CacheStatusWidget(roadSource: RoadSource, tileSource: TileSource, mapProvider: MapProvider) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CacheChip(label = "Calles", text  = when (roadSource) { RoadSource.LOADING -> "Cargando..."; RoadSource.LOCAL_DB -> "Local (BD)"; RoadSource.NETWORK -> "Overpass API" }, color = when (roadSource) { RoadSource.LOADING -> Color(0xFFD4AF37); RoadSource.LOCAL_DB -> Color(0xFF4CAF50); RoadSource.NETWORK -> Color(0xFF2196F3) }, isLoading = roadSource == RoadSource.LOADING)
        if (mapProvider != MapProvider.OSM) {
            val tileLabel = when (tileSource) { TileSource.LOCAL_OSM -> "Local (osmdroid)"; TileSource.LOCAL_CACHE -> "Local (caché)"; TileSource.NETWORK -> "Red" }
            val tileColor = when (tileSource) { TileSource.LOCAL_OSM, TileSource.LOCAL_CACHE -> Color(0xFF4CAF50); TileSource.NETWORK -> Color(0xFF2196F3) }
            CacheChip(label = "Mapa", text = tileLabel, color = tileColor, isLoading = false)
        }
    }
}

@Composable
private fun CacheChip(label: String, text: String, color: Color, isLoading: Boolean) {
    Row(modifier = Modifier.background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(8.dp), color = color, strokeWidth = 1.5.dp)
        else Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text = "$label: $text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun drawHealthBarOnDrawable(context: Context, original: android.graphics.drawable.Drawable?, health: Float, isDying: Boolean): android.graphics.drawable.Drawable? {
    if (original !is android.graphics.drawable.BitmapDrawable || health >= 100f || isDying) return original
    val mutableBitmap = original.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(mutableBitmap)
    val paint = android.graphics.Paint()
    val barWidth = mutableBitmap.width * 0.95f
    val barHeight = 10f
    val left = (mutableBitmap.width - barWidth) / 2f
    val top = 0f
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(left, top, left + barWidth, top + barHeight, paint)
    paint.color = when { health > 60f -> android.graphics.Color.GREEN; health > 30f -> android.graphics.Color.YELLOW; else -> android.graphics.Color.RED }
    val healthWidth = (barWidth - 6f) * (health / 100f)
    if (healthWidth > 0) canvas.drawRect(left + 3f, top + 3f, left + 3f + healthWidth, top + barHeight - 3f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, mutableBitmap)
}

private data class NpcWebPayload(val id: String, val lat: Double, val lng: Double, val rot: Float, val type: String, val imageKey: String? = null, val drawable: String? = null, val flip: Int? = null, val name: String? = null, val width: Float? = null, val height: Float? = null)

private data class LandmarkWebPayload(
    val id: String,
    val lat: Double,
    val lng: Double,
    val rotation: Float,
    val widthMeters: Float,
    val heightMeters: Float,
    val scale: Float,
    val assetPath: String
)

private fun buildHtml(lat: Double, lng: Double, zoom: Int): String = """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        body { margin: 0; padding: 0; background: #aad3df; overflow: hidden; }
        #map-wrapper { position: absolute; top: -50%; left: -50%; width: 200vw; height: 200vh; transform-origin: center center; }
        #map { width: 100%; height: 100%; background: transparent; }
        .leaflet-marker-icon { background: none !important; border: none !important; }
        .leaflet-div-icon { background: transparent !important; border: none !important; }
        .lm-c { background: transparent !important; }
        .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }
        @keyframes neonPulse{0%,100%{filter:drop-shadow(0 0 4px gold) drop-shadow(0 0 10px rgba(255,165,0,.45));}50%{filter:drop-shadow(0 0 14px gold) drop-shadow(0 0 28px orange);}}
        @keyframes shimmerSlide{0%{left:-45%;}100%{left:135%;}}
        .lm-door-wrap{overflow:hidden;}
        .lm-door-img{animation:neonPulse 1.1s ease-in-out infinite;}
        .lm-shimmer{position:absolute;top:0;left:-45%;width:35%;height:100%;background:linear-gradient(105deg,transparent,rgba(255,225,70,.65),transparent);animation:shimmerSlide 2.2s linear infinite;pointer-events:none;}
    </style>
</head>
<body>
    <div id="map-wrapper"><div id="map"></div></div>
    <script>
        var map = L.map('map', { 
            zoomControl: false, 
            attributionControl: false, 
            dragging: false, 
            touchZoom: false,
            doubleClickZoom: false,
            scrollWheelZoom: false,
            boxZoom: false,
            keyboard: false,
            maxZoom: 22 
        }).setView([$lat, $lng], $zoom);
        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{ maxZoom: 22, maxNativeZoom: 18 }).addTo(map);
        map.createPane('landmarkPane');
        map.getPane('landmarkPane').style.zIndex = 300;
        map.createPane('doorPane');
        map.getPane('doorPane').style.zIndex = 450;
        map.getPane('doorPane').style.pointerEvents = 'none';

        
        var npcMarkers = {};
        var collectibleMarkers = {};
        var landmarkMarkers = {};

        var isZooming = false;
        var isExplorationMode = false;
        
        map.on('zoomstart', function() { isZooming = true; });
        map.on('zoomend', function() { isZooming = false; });
        map.on('zoom', function() { resizeLandmarks(); });

        map.on('dragstart', function() {
            isExplorationMode = true;
            if (window.Android && window.Android.notifyMapPanStart) window.Android.notifyMapPanStart();
        });
        map.on('dragend', function() {
            if (window.Android && window.Android.notifyMapPanEnd) window.Android.notifyMapPanEnd();
        });
        
        function updateMapView(lat, lng, z) { if (!isZooming && !isExplorationMode) map.setView([lat, lng], z, { animate: false }); }
        
        function setDesignerMode(isDesigner) {
            if (isDesigner) {
                map.dragging.enable();
                map.touchZoom.enable();
                map.scrollWheelZoom.enable();
            } else {
                map.dragging.disable();
                map.touchZoom.disable();
                map.scrollWheelZoom.disable();
            }
        }
        
        function setMapRotation(deg) { var wrapper = document.getElementById('map-wrapper'); if (wrapper) wrapper.style.transform = 'rotate(' + deg + 'deg)'; }
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }
        function exitExplorationMode() { isExplorationMode = false; }
        function escapeHtml(value) { return String(value).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c; }); }

        function updateLandmarks(jsonStr) {
            var data = JSON.parse(jsonStr);
            var currentIds = new Set(data.map(function(l){ return String(l.id); })); 

            for (var id in landmarkMarkers) {
                if (!currentIds.has(id)) {
                    map.removeLayer(landmarkMarkers[id]);
                    delete landmarkMarkers[id];
                }
            }

            data.forEach(function(lm) {
                var pUrl = 'file:///android_asset/' + lm.assetPath;
                var exactWidthMeters = lm.widthMeters * lm.scale;
                var exactHeightMeters = lm.heightMeters * lm.scale;

                var isDoor = lm.assetPath.indexOf('DOORS/') >= 0;
                var existingPane = landmarkMarkers[lm.id] ? landmarkMarkers[lm.id].options.pane : null;
                var expectedPane = isDoor ? 'doorPane' : 'landmarkPane';
                if (existingPane && existingPane !== expectedPane) {
                    map.removeLayer(landmarkMarkers[lm.id]);
                    delete landmarkMarkers[lm.id];
                }
                if (landmarkMarkers[lm.id]) {
                    landmarkMarkers[lm.id].setLatLng([lm.lat, lm.lng]);
                    var el = landmarkMarkers[lm.id].getElement();
                    if (el) {
                        var wrapper = el.querySelector('.lm-c');
                        if (wrapper) {
                            wrapper.dataset.wMeters = exactWidthMeters;
                            wrapper.dataset.hMeters = exactHeightMeters;
                            wrapper.dataset.rot = lm.rotation;
                            wrapper.dataset.lat = lm.lat;
                        }
                    }
                } else {
                    var html = '<div class="lm-c' + (isDoor ? ' lm-door-wrap' : '') + '" ' +
                               'data-w-meters="' + exactWidthMeters + '" ' +
                               'data-h-meters="' + exactHeightMeters + '" ' +
                               'data-rot="' + lm.rotation + '" ' +
                               'data-lat="' + lm.lat + '" ' +
                               'style="position:absolute; transform: translate(-50%, -50%) rotate('+lm.rotation+'deg); pointer-events: none; z-index: -100;">' +
                               '<img src="'+pUrl+'"' + (isDoor ? ' class="lm-door-img"' : '') + ' style="width:100%; height:100%; display:block; object-fit:fill;">' +
                               (isDoor ? '<div class="lm-shimmer"></div>' : '') +
                               '</div>';

                    var icon = L.divIcon({ html: html, className: '', iconSize: [0,0] });
                    
                    var marker = L.marker([lm.lat, lm.lng], { icon: icon, pane: isDoor ? 'doorPane' : 'landmarkPane', interactive: false }).addTo(map);
                    landmarkMarkers[lm.id] = marker;
                }
            });
            resizeLandmarks();
        }

        function resizeLandmarks() {
            var zoom = map.getZoom();
            var elements = document.querySelectorAll('.lm-c');
            
            for (var i = 0; i < elements.length; i++) {
                var wrapper = elements[i];
                var wMeters = parseFloat(wrapper.dataset.wMeters);
                var hMeters = parseFloat(wrapper.dataset.hMeters);
                var lat = parseFloat(wrapper.dataset.lat);
                var rot = parseFloat(wrapper.dataset.rot);

                var pixelsPerMeter = (256 * Math.pow(2, zoom)) / (40075016 * Math.cos(lat * Math.PI / 180));
                
                var wPx = wMeters * pixelsPerMeter;
                var hPx = hMeters * pixelsPerMeter;

                wrapper.style.width = wPx + 'px';
                wrapper.style.height = hPx + 'px';
                wrapper.style.transform = 'translate(-50%, -50%) rotate(' + rot + 'deg)';
            }
        }

        function updateCollectibles(jsonStr) {
            var data = JSON.parse(jsonStr);
            for (var key in collectibleMarkers) { map.removeLayer(collectibleMarkers[key]); }
            collectibleMarkers = {};

            data.forEach(function(col) {
                var pUrl = 'file:///android_asset/' + col.assetPath;
                var containerSize = 20;
                var iconSize = 14;
                var html = '<div style="position:relative; width:' + containerSize + 'px; height:' + containerSize + 'px; display:flex; justify-content:center; align-items:center;">' +
                    '<div style="position:absolute; width:100%; height:100%; background:radial-gradient(circle, rgba(255,235,59,0.5) 0%, rgba(255,235,59,0) 60%); border-radius:50%;"></div>' +
                    '<img src="' + pUrl + '" style="position:relative; width:' + iconSize + 'px; height:' + iconSize + 'px; object-fit:contain; image-rendering:pixelated;">' +
                '</div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [containerSize, containerSize], iconAnchor: [containerSize/2, containerSize/2] });
                collectibleMarkers[col.id] = L.marker([col.latitude, col.longitude], { icon: icon, interactive: false, zIndexOffset: 500 }).addTo(map);
            });
        }
        
        function updateNpcs(data) {
            if (isZooming) return;
            var currentZoom = map.getZoom();
            var isZoomedIn = currentZoom >= 16.5;
            var ids = new Set();
            if (isZoomedIn) ids = new Set(data.map(function(n){ return n.id; }));
            for (var id in npcMarkers) if (!ids.has(id)) { map.removeLayer(npcMarkers[id]); delete npcMarkers[id]; }
            if (!isZoomedIn) return;
            var dynamicScale = Math.max(0.2, Math.min(1.4 * Math.pow(2, currentZoom - 19), 1.4));
            data.forEach(function(npc) {
                var finalW, finalH;
                if (npc.type === 'CAR') { finalW = Math.round(npc.width * dynamicScale); finalH = Math.round(npc.height * dynamicScale); }
                else if (npc.type === 'MODULAR') { var sz = Math.max(16, Math.min(24.0 + ((currentZoom - 18.0) * 8.0), 40)); finalW = sz; finalH = sz; }
                else { finalW = 24; finalH = 24; }
                var nameTagHtml = '';
                if (npc.name) {
                    var safeName = escapeHtml(npc.name);
                    nameTagHtml = '<div style="position:absolute; top:-28px; left:50%; transform:translateX(-50%); color:#D4AF37; background:rgba(0,0,0,0.65); padding:2px 6px; border-radius:4px; font-size:16px; font-weight:bold; white-space:nowrap; text-shadow:1px 1px 0 #000; z-index:100;">' + safeName + '</div>';
                }
                if (npcMarkers[npc.id]) {
                    npcMarkers[npc.id].setLatLng([npc.lat, npc.lng]);
                    var el = npcMarkers[npc.id].getElement();
                    if (el) {
                        var wrapper = el.querySelector('.npc-c');
                        var img = el.querySelector('img');
                        if ((npc.type === 'CAR' || npc.type === 'MODULAR') && img && wrapper) {
                            var cachedImg = window.imgCache ? window.imgCache[npc.imageKey] : '';
                            if (!cachedImg) return;
                            if (img.src !== cachedImg) img.src = cachedImg;
                            wrapper.style.width = finalW + 'px';
                            wrapper.style.height = finalH + 'px';
                            if (npc.flip !== undefined) img.style.transform = 'scaleX(' + npc.flip + ')';
                        } else if (wrapper && npc.type !== 'CAR' && npc.type !== 'MODULAR') {
                            wrapper.style.transform = 'translate(-50%, -50%) rotate(0deg)';
                        }
                    }
                } else {
                    var html = '';
                    if (npc.type === 'CAR' || npc.type === 'MODULAR') {
                        var cachedImg = window.imgCache ? window.imgCache[npc.imageKey] : '';
                        if (!cachedImg) return;
                        var flipStyle = (npc.flip !== undefined) ? 'transform: scaleX(' + npc.flip + ');' : '';
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%); width:'+finalW+'px; height:'+finalH+'px;">' + nameTagHtml + '<img src="'+cachedImg+'" style="width:100%; height:100%; display:block; ' + flipStyle + '"></div>';
                    } else {
                        var pUrl = 'file:///android_asset/' + npc.drawable + '.svg';
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%) rotate(0deg); width:24px; height:24px;">' + nameTagHtml + '<img src="'+pUrl+'" style="width:100%; height:100%; display:block;"></div>';
                    }
                    var icon = L.divIcon({ html: html, className: '', iconSize: [0, 0] });
                    npcMarkers[npc.id] = L.marker([npc.lat, npc.lng], { icon: icon, zIndexOffset: 1000 }).addTo(map);
                }
            });
        }
        var playerMarker = null;
        function updatePlayerMarker(lat, lng, isInFreeNavigation) {
            if (!isInFreeNavigation) {
                if (playerMarker) { map.removeLayer(playerMarker); playerMarker = null; }
                return;
            }
            if (lat === null || lng === null) return;
            if (!playerMarker) {
                var html = '<div style="position:relative; width:40px; height:40px; display:flex; justify-content:center; align-items:center;">' +
                    '<div style="width:20px; height:20px; background:radial-gradient(circle at 30% 30%, #4CAF50, #2E7D32); border-radius:50%; border:3px solid #FFF; box-shadow: 0 2px 8px rgba(0,0,0,0.4);"></div>' +
                    '</div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [40, 40], iconAnchor: [20, 20] });
                playerMarker = L.marker([lat, lng], { icon: icon, interactive: false, zIndexOffset: 1000 }).addTo(map);
            } else {
                playerMarker.setLatLng([lat, lng]);
            }
        }
        var destinationMarker = null;
        var destinationRoute = null;
        var isPlacingDestinationMarker = false;
        function updateDestinationPlacingMode(isPlacing) {
            isPlacingDestinationMarker = isPlacing;
            var mapElement = document.getElementById('map');
            if (mapElement) {
                mapElement.style.cursor = isPlacing ? 'crosshair' : 'grab';
            }
        }
        function updateDestinationMarker(lat, lng) {
            if (!destinationMarker) {
                var html = '<div style="position:relative; width:32px; height:40px; display:flex; justify-content:center; align-items:flex-start;">' +
                    '<svg width="32" height="40" viewBox="0 0 32 40" xmlns="http://www.w3.org/2000/svg" style="filter: drop-shadow(0px 2px 4px rgba(0,0,0,0.3));">' +
                    '<path d="M16 0C9.4 0 4 5.4 4 12c0 7 12 25 12 25s12-18 12-25c0-6.6-5.4-12-12-12z" fill="#F44336"/>' +
                    '<circle cx="16" cy="12" r="5" fill="#FFF"/>' +
                    '</svg></div>';
                var icon = L.divIcon({ html: html, className: '', iconSize: [32, 40], iconAnchor: [16, 40] });
                destinationMarker = L.marker([lat, lng], { icon: icon, draggable: false, zIndexOffset: 900 }).addTo(map);
            } else {
                destinationMarker.setLatLng([lat, lng]);
            }
        }
        function updateDestinationRoute(playerLat, playerLng, routePoints, showRoute) {
            if (destinationRoute) {
                map.removeLayer(destinationRoute);
                destinationRoute = null;
            }
            if (showRoute && routePoints && routePoints.length > 0) {
                var points = [];
                for (var i = 0; i < routePoints.length; i++) {
                    var pt = routePoints[i];
                    if (pt && typeof pt.lat !== 'undefined' && typeof pt.lng !== 'undefined') {
                        points.push([pt.lat, pt.lng]);
                    }
                }
                if (points.length > 1) {
                    destinationRoute = L.polyline(points, {
                        color: '#2196F3',
                        weight: 3,
                        opacity: 0.7,
                        dashArray: '5, 5',
                        lineCap: 'round',
                        lineJoin: 'round'
                    }).addTo(map);
                }
            }
        }
        function clearDestinationMarker() {
            if (destinationMarker) {
                map.removeLayer(destinationMarker);
                destinationMarker = null;
            }
            if (destinationRoute) {
                map.removeLayer(destinationRoute);
                destinationRoute = null;
            }
            isPlacingDestinationMarker = false;
            updateDestinationPlacingMode(false);
        }
        map.on('click', function(e) {
            if (isPlacingDestinationMarker && window.Android && window.Android.notifyMapClick) {
                window.Android.notifyMapClick(e.latlng.lat, e.latlng.lng);
                isPlacingDestinationMarker = false;
                updateDestinationPlacingMode(false);
            }
        });
        
        var roadLayers = {};
        function updateRoads(jsonStr) {
            var data = JSON.parse(jsonStr);
            var currentIds = new Set(data.map(function(w){ return String(w.id); }));
            for (var id in roadLayers) {
                if (!currentIds.has(id)) {
                    map.removeLayer(roadLayers[id]);
                    delete roadLayers[id];
                }
            }
            data.forEach(function(way) {
                var latlngs = way.nodes.map(function(n){ return [n.lat, n.lon]; });
                if (roadLayers[way.id]) {
                    roadLayers[way.id].setLatLngs(latlngs);
                    roadLayers[way.id].bringToFront();
                } else {
                    var color = way.isForCars ? '#FFD700' : '#82C8FF';
                    var weight = way.isForCars ? 4 : 3;
                    roadLayers[way.id] = L.polyline(latlngs, {
                        color: color, weight: weight, opacity: 0.85,
                        lineCap: 'round', lineJoin: 'round', interactive: false
                    }).addTo(map);
                    roadLayers[way.id].bringToFront();
                }
            });
        }
    </script>
</body>
</html>
""".trimIndent()

private fun buildDoorEffectBitmap(src: android.graphics.Bitmap, ctx: android.content.Context): android.graphics.Bitmap {
    val t = System.currentTimeMillis(); val cycle = (t % 2200L) / 2200f
    val bw = src.width.toFloat(); val bh = src.height.toFloat()
    val out = android.graphics.Bitmap.createBitmap(src.width, src.height, android.graphics.Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(out); cv.drawBitmap(src, 0f, 0f, null)
    val sx = cycle * (bw * 1.7f) - bw * 0.35f
    cv.drawRect(0f, 0f, bw, bh, android.graphics.Paint().apply {
        isAntiAlias = true
        shader = android.graphics.LinearGradient(sx, 0f, sx + bw * 0.25f, bh,
            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(215, 255, 225, 70), android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
    })
    val sd = ctx.resources.displayMetrics.density; val sw = 3.5f * sd
    val ga = (125 + (130 * Math.sin(t / 360.0)).toInt()).coerceIn(0, 255)
    cv.drawRect(sw / 2f, sw / 2f, bw - sw / 2f, bh - sw / 2f, android.graphics.Paint().apply {
        isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = sw
        color = android.graphics.Color.argb(ga, 255, 200, 0)
        maskFilter = android.graphics.BlurMaskFilter(sw * 2.8f, android.graphics.BlurMaskFilter.Blur.OUTER)
    })
    return out
}

private class MapJsBridge(private val vm: WorldMapViewModel) {
    @JavascriptInterface fun notifyMapPanStart() { vm.onMapPanStart() }
    @JavascriptInterface fun notifyMapPanEnd() { vm.onMapPanEnd() }
    @JavascriptInterface fun notifyMapClick(latitude: Double, longitude: Double) {
        vm.placeDestinationMarker(latitude, longitude)
    }
    @JavascriptInterface fun notifyCenterForWaypoint(latitude: Double, longitude: Double) {
        vm.placeDestinationMarker(latitude, longitude)
    }
}

private class ExactSizeDrawable(
    private val base: android.graphics.drawable.Drawable,
    private val exactWidthPx: Int,
    private val exactHeightPx: Int
) : android.graphics.drawable.Drawable() {
    override fun getIntrinsicWidth() = exactWidthPx
    override fun getIntrinsicHeight() = exactHeightPx
    override fun draw(canvas: android.graphics.Canvas) {
        val b = this.getBounds()
        base.setBounds(b.left, b.top, b.right, b.bottom)
        base.draw(canvas)
    }
    override fun setAlpha(alpha: Int) { base.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { base.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = base.opacity
}
fun getAssetFile(context: Context, assetPath: String, fileName: String): java.io.File {
    val file = java.io.File(context.cacheDir, fileName)
    if (!file.exists()) {
        context.assets.open(assetPath).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return file
}

@Composable
fun ZombiVideoPlayer(context: Context, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    val file = getAssetFile(ctx, "ZOMBIS_MOD/Carga_Mod_Zombi.mp4", "temp_zombi_carga.mp4")
                    setVideoPath(file.absolutePath)
                    requestFocus()
                    setOnCompletionListener { onDismiss() }
                    setOnErrorListener { _, what, extra ->
                        Log.e("VideoPlayer", "Error de video: $what, $extra")
                        onDismiss()
                        true
                    }
                    start()
                }
            },
            modifier = Modifier.align(Alignment.Center)
        )
    }
}