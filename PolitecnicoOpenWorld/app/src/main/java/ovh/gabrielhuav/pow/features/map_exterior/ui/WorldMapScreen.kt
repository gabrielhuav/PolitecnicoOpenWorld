package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.LruCache
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.*
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.*
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel(factory = WorldMapViewModel.Factory(context)),
    onNavigateToMainMenu: () -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val base64Cache = viewModel.base64Cache
    val widthCache = viewModel.widthCache
    val heightCache = viewModel.heightCache
    val nativeDrawableCache = viewModel.nativeDrawableCache
    val registeredWebImages = viewModel.registeredWebImages
    val landmarkBitmapCache = viewModel.landmarkBitmapCache
    
    val gson = remember { Gson() }
    
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportLandmarksToUri(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importLandmarksFromUri(context, it) }
    }

    val coroutineScope = rememberCoroutineScope()
    var yButtonHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadLandmarks(context)
        viewModel.showInitialHealthBar()
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

        if (uiState.mapProvider == MapProvider.OSM) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        controller.setZoom(uiState.zoomLevel)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (uiState.isDesignerMode) {
                        view.setOnTouchListener(null)
                        view.isClickable = true
                    } else {
                        view.setOnTouchListener { _, _ -> true }
                        view.isClickable = false
                    }
                    uiState.currentLocation?.let { view.controller.setCenter(it) }

                    view.mapOrientation = if (uiState.isDriving) -uiState.vehicleRotation else 0f

                    val zoomDiff = kotlin.math.abs(view.zoomLevelDouble - uiState.zoomLevel)
                    if (zoomDiff > 1.5) {
                        view.controller.animateTo(uiState.currentLocation, uiState.zoomLevel, 120L)
                    } else if (zoomDiff >= 0.01) {
                        view.controller.setZoom(uiState.zoomLevel)
                    }

                    if (uiState.isRoadNetworkReady) {
                        @Suppress("UNCHECKED_CAST")
                        val markerCache = (view.tag as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also { view.tag = it }

                        val currentZoom = view.zoomLevelDouble
                        val isZoomedIn = currentZoom >= 16.5
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
                            val marker = markerCache[npc.id] ?: Marker(view).apply {
                                title = "NPC_MARKER"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setInfoWindow(null); isFlat = true; markerCache[npc.id] = this; view.overlays.add(this)
                            }

                            if (isZoomedIn) {
                                marker.setAlpha(if (npc.isDying) 0.3f else 1f)
                                if (npc.visualConfig != null) {
                                    val currentlyMoving = npc.speed > 0 || npc.isMoving
                                    val personSzDp = (24.0 + ((currentZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                    val exactPixels = (personSzDp * screenDensity).toInt()
                                    val frameIndex = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0
                                    val cacheKey = "PED_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${npc.health}_D${npc.isDying}"

                                    marker.icon = nativeDrawableCache.getOrPut(cacheKey) {
                                        var baseDrawable = CharacterSpriteManager.getModularNpcDrawable(context, npc.visualConfig!!, currentlyMoving, npc.facingRight, timeMs, highResRenderScale, npc.displayName)
                                        baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                        baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                            ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    }
                                    marker.rotation = 0f
                                } else if (npc.type == NpcType.CAR) {
                                    var angle = npc.rotationAngle % 360f; if (angle < 0) angle += 360f
                                    val frameIndex = (angle / 7.5f).roundToInt() % 48
                                    val dynamicScale = (1.4 * Math.pow(2.0, currentZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                    val cacheKey = "CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${dynamicScale}_H${npc.health}_D${npc.isDying}"

                                    marker.icon = nativeDrawableCache.getOrPut(cacheKey) {
                                        var baseDrawable = VehicleSpriteManager.getTintedCarNpc(context, angle, npc.carColor, highResRenderScale, npc.carModel)
                                        baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                        baseDrawable?.let { drawable ->
                                            val finalWidthPx = ((drawable.intrinsicWidth / screenDensity / screenDensity) * dynamicScale * screenDensity).toInt()
                                            val finalHeightPx = ((drawable.intrinsicHeight / screenDensity / screenDensity) * dynamicScale * screenDensity).toInt()
                                            ExactSizeDrawable(drawable, finalWidthPx, finalHeightPx)
                                        } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    }
                                    marker.rotation = 0f
                                } else {
                                    val cacheKey = "SVG_${npc.type.name}_H${npc.health}_D${npc.isDying}"
                                    marker.icon = nativeDrawableCache.getOrPut(cacheKey) {
                                        val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                        var baseDrawable = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                        baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                        baseDrawable?.let { ExactSizeDrawable(it, (24 * screenDensity).toInt(), (24 * screenDensity).toInt()) }
                                            ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    }
                                    marker.rotation = npc.rotationAngle
                                }
                            } else marker.setAlpha(0f)
                            marker.position = org.osmdroid.util.GeoPoint(npc.location.latitude, npc.location.longitude)
                        }

                        val activeCollectibleIds = uiState.activeCollectibles.map { it.id }.toSet()
                        @Suppress("UNCHECKED_CAST")
                        val colCache = (view.getTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag) as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also { view.setTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag, it) }

                        val colIt = colCache.iterator()
                        while (colIt.hasNext()) { val e = colIt.next(); if (!activeCollectibleIds.contains(e.key)) { view.overlays.remove(e.value); colIt.remove() } }

                        uiState.activeCollectibles.forEach { col ->
                            val marker = colCache[col.id] ?: Marker(view).apply { title = "COLLECTIBLE"; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); isFlat = true; colCache[col.id] = this; view.overlays.add(this) }
                            if (isZoomedIn) {
                                marker.setAlpha(1f); val exactPixels = (22 * screenDensity).toInt()
                                marker.icon = nativeDrawableCache.getOrPut("COL_${col.assetPath}") {
                                    try {
                                        val bitmap = android.graphics.BitmapFactory.decodeStream(context.assets.open(col.assetPath))
                                        if (bitmap != null) {
                                            val glow = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setSize(exactPixels, exactPixels); setColor(android.graphics.Color.argb(100, 255, 235, 59)) }
                                            val sprite = android.graphics.drawable.BitmapDrawable(context.resources, bitmap).apply { isFilterBitmap = false }
                                            val layer = android.graphics.drawable.LayerDrawable(arrayOf(glow, sprite))
                                            val inset = ((exactPixels - (exactPixels * 0.9f)) / 2).toInt()
                                            layer.setLayerInset(1, inset, inset, inset, inset)
                                            ExactSizeDrawable(layer, exactPixels, exactPixels)
                                        } else ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    } catch (e: Exception) { ContextCompat.getDrawable(context, android.R.color.transparent)!! }
                                }
                                marker.rotation = ((System.currentTimeMillis() / 30) % 360).toFloat()
                            } else marker.setAlpha(0f)
                            marker.position = org.osmdroid.util.GeoPoint(col.latitude, col.longitude)
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    val landmarkCache = (view.getTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag) as? MutableMap<Long, MutableList<org.osmdroid.views.overlay.Overlay>>)
                        ?: mutableMapOf<Long, MutableList<org.osmdroid.views.overlay.Overlay>>().also { view.setTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag, it) }

                    val currentIds = uiState.landmarks.map { it.id }.toSet()
                    val itLandmark = landmarkCache.iterator()
                    while (itLandmark.hasNext()) { val entry = itLandmark.next(); if (!currentIds.contains(entry.key)) { entry.value.forEach { view.overlays.remove(it) }; itLandmark.remove() } }

                    uiState.landmarks.forEach { landmark ->
                        val overlays = landmarkCache.getOrPut(landmark.id) { mutableListOf() }
                        val bitmap = landmarkBitmapCache.getOrPut(landmark.assetPath) {
                            try { context.assets.open(landmark.assetPath).use { android.graphics.BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
                        } ?: return@forEach

                        val groundOverlay = overlays.filterIsInstance<org.osmdroid.views.overlay.GroundOverlay>().firstOrNull() 
                            ?: org.osmdroid.views.overlay.GroundOverlay().apply { overlays.add(this); view.overlays.add(0, this) }

                        val center = org.osmdroid.util.GeoPoint(landmark.location.latitude, landmark.location.longitude)
                        val halfW = (landmark.baseWidthMeters * landmark.scaleFactor) / 2.0
                        val halfH = (landmark.baseHeightMeters * landmark.scaleFactor) / 2.0
                        val d = kotlin.math.sqrt(halfW * halfW + halfH * halfH)
                        val theta = Math.toDegrees(kotlin.math.atan2(halfW, halfH))

                        groundOverlay.setPosition(
                            center.destinationPoint(d, landmark.rotationAngle.toDouble() - theta),
                            center.destinationPoint(d, landmark.rotationAngle.toDouble() + theta),
                            center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 - theta),
                            center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 + theta)
                        )
                        groundOverlay.setImage(bitmap)

                        if (uiState.isDesignerMode) {
                            val controlMarker = overlays.filterIsInstance<org.osmdroid.views.overlay.Marker>().firstOrNull() ?: org.osmdroid.views.overlay.Marker(view).apply { setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER); icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_edit)?.mutate(); overlays.add(this); view.overlays.add(this) }
                            controlMarker.position = center; controlMarker.icon?.mutate()?.setTint(if (uiState.selectedLandmarkId == landmark.id) android.graphics.Color.RED else android.graphics.Color.BLACK)
                            controlMarker.setOnMarkerClickListener { _, _ -> viewModel.selectLandmark(landmark.id); true }
                            controlMarker.isDraggable = true
                            controlMarker.setOnMarkerDragListener(object : org.osmdroid.views.overlay.Marker.OnMarkerDragListener {
                                override fun onMarkerDragStart(m: org.osmdroid.views.overlay.Marker) { viewModel.selectLandmark(landmark.id) }
                                override fun onMarkerDrag(m: org.osmdroid.views.overlay.Marker) { viewModel.moveSelectedLandmark(m.position.latitude - landmark.location.latitude, m.position.longitude - landmark.location.longitude) }
                                override fun onMarkerDragEnd(m: org.osmdroid.views.overlay.Marker) {}
                            })
                        } else overlays.filterIsInstance<org.osmdroid.views.overlay.Marker>().firstOrNull()?.let { view.overlays.remove(it); overlays.remove(it) }
                    }
                    view.invalidate()
                }
            )
        } else {
            val collectiblesJson = remember(uiState.activeCollectibles) { Gson().toJson(uiState.activeCollectibles) }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                        settings.allowFileAccess = true; settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = cachingClient
                        loadDataWithBaseURL(null, buildHtml(uiState.currentLocation?.latitude ?: 0.0, uiState.currentLocation?.longitude ?: 0.0, uiState.zoomLevel.toInt()), "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof updateMapView==='function')updateMapView(${it.latitude}, ${it.longitude}, ${uiState.zoomLevel.toInt()});", null) }
                    wv.evaluateJavascript("if(typeof setMapRotation==='function')setMapRotation(${if (uiState.isDriving) -uiState.vehicleRotation else 0f});", null)
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

                    val sd = context.resources.displayMetrics.density
                    val payloads = uiState.npcs.map { npc ->
                        if (npc.type == NpcType.CAR) {
                            var ang = npc.rotationAngle % 360f; if (ang < 0) ang += 360f
                            val key = "${npc.carModel.name}_${(ang / 7.5f).roundToInt() % 48}_${npc.carColor}_$sd"
                            val b64 = base64Cache.getOrPut(key) {
                                VehicleSpriteManager.getTintedCarNpc(context, ang, npc.carColor, sd, npc.carModel)?.let { d ->
                                    (d as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let { b ->
                                        widthCache.put(key, (b.width / sd) / sd); heightCache.put(key, (b.height / sd) / sd)
                                        val out = java.io.ByteArrayOutputStream(); b.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                                        "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                    }
                                }
                            } ?: ""
                            if (!registeredWebImages.contains(key) && b64.isNotEmpty()) {
                                wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$key'] = '$b64';", null); registeredWebImages.add(key)
                            }
                            NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, "CAR", key, name = npc.displayName, width = widthCache.get(key), height = heightCache.get(key))
                        } else if (npc.visualConfig != null) {
                            val moving = npc.speed > 0 || npc.isMoving
                            val fi = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, moving, System.currentTimeMillis()) ?: 0
                            val key = "npc_mod_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_$fi"
                            val b64 = base64Cache.getOrPut(key) {
                                CharacterSpriteManager.generateAssembledBitmap(context, npc.visualConfig!!, moving, System.currentTimeMillis())?.let { b ->
                                    val out = java.io.ByteArrayOutputStream(); b.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                                    "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                }
                            } ?: ""
                            if (!registeredWebImages.contains(key) && b64.isNotEmpty()) {
                                wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$key'] = '$b64';", null); registeredWebImages.add(key)
                            }
                            NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", key, flip = if (npc.facingRight) 1 else -1, name = npc.displayName)
                        } else NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, npc.type.name, drawable = npc.type.drawableName, name = npc.displayName)
                    }
                    wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs(${gson.toJson(payloads)});", null)
                    wv.evaluateJavascript("if(typeof updateCollectibles==='function')updateCollectibles(${JSONObject.quote(collectiblesJson)});", null)
                }
            )
        }

        PlayerCharacter(uiState, Modifier.align(Alignment.Center), viewModel.playerHealth, viewModel.showHealthBar, viewModel.damagePulseTrigger)

        if (!uiState.isRoadNetworkReady) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp).background(Color.Black.copy(0.65f), CircleShape).padding(14.dp, 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), Color(0xFFD4AF37), 2.dp)
                Text("Cargando calles...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(uiState.showCacheWidget) { CacheStatusWidget(uiState.roadSource, uiState.tileSource, uiState.mapProvider) }
            AnimatedVisibility(uiState.showFpsWidget) { CacheChip("Rendimiento", "$currentFps FPS", if (currentFps >= 24) Color(0xFF4CAF50) else Color(0xFFD32F2F), false) }
            AnimatedVisibility(uiState.isDesignerMode) {
                Row(
                    modifier = Modifier.background(Color(0xFFD4AF37).copy(0.85f), CircleShape).padding(10.dp, 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Architecture, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("DISEÑADOR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.align(Alignment.TopEnd).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
            IconButton({ onNavigateToSettings() }, Modifier.background(Color.White.copy(0.8f), CircleShape)) { Icon(Icons.Default.Settings, null, tint = Color.Black) }
            IconButton({ viewModel.toggleDesignerMode(!uiState.isDesignerMode) }, Modifier.background(if (uiState.isDesignerMode) Color(0xFFD4AF37) else Color.White.copy(0.8f), CircleShape)) { Icon(Icons.Default.Architecture, null, tint = Color.Black) }
            if (uiState.isDesignerMode) IconButton({ viewModel.showAssetPicker(true) }, Modifier.background(Color(0xFF4CAF50), CircleShape)) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }

        Column(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton({ viewModel.zoomIn() }, Modifier.background(Color.White.copy(0.8f), CircleShape).size(48.dp)) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
            IconButton({ viewModel.zoomOut() }, Modifier.background(Color.White.copy(0.8f), CircleShape).size(48.dp)) { Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        }

        if (uiState.showTeleportMenu) {
            AlertDialog({ viewModel.toggleTeleportMenu(false) }, title = { Text("Viaje Rápido") }, text = { Button({ viewModel.teleportTo(19.5057, -99.1456) }, Modifier.fillMaxWidth()) { Text("ESCOM") } }, confirmButton = { TextButton({ viewModel.toggleTeleportMenu(false) }) { Text("Cerrar") } })
        }

        if (uiState.showAssetPicker) AssetPickerDialog(context, { viewModel.addLandmarkAtPlayer(context, it) }, { viewModel.showAssetPicker(false) })

        val selectedLandmark = uiState.landmarks.find { it.id == uiState.selectedLandmarkId }
        if (uiState.isDesignerMode && selectedLandmark != null) {
            DesignerPanel(selectedLandmark, { dLat, dLon -> viewModel.moveSelectedLandmark(dLat, dLon) }, { viewModel.rotateSelectedLandmark(it) }, { viewModel.scaleSelectedLandmark(it) }, { viewModel.deleteSelectedLandmark(context) }, { viewModel.saveSelectedLandmark(context) }, { exportLauncher.launch("landmarks.json") }, { importLauncher.launch(arrayOf("application/json")) }, { viewModel.selectLandmark(null) }, Modifier.align(Alignment.TopCenter).padding(top = 130.dp).fillMaxWidth(0.9f))
        }

        if (!uiState.isDesignerMode) {
            val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
            val scale = uiState.controlsScale.coerceAtMost(if (isPortrait) 1.0f else 1.4f)
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = if (isPortrait) 48.dp else 32.dp, start = if (isPortrait) 16.dp else 64.dp, end = if (isPortrait) 16.dp else 64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isDriving) {
                    val s = @Composable { VehicleSteeringController(modifier = Modifier.scale(scale), onSteerLeft = { viewModel.steerLeft(it) }, onSteerRight = { viewModel.steerRight(it) }) }
                    val p = @Composable { VehiclePedalsController(modifier = Modifier.scale(scale), onAccelerate = { viewModel.accelerate(it) }, onBrake = { viewModel.brake(it) }, onExit = { if (it) { viewModel.onInteractButtonPressed(); yButtonHoldJob?.cancel(); yButtonHoldJob = coroutineScope.launch { delay(3000); viewModel.toggleTeleportMenu(true) } } else yButtonHoldJob?.cancel() }) }
                    if (uiState.swapControls) { p(); s() } else { s(); p() }
                } else {
                    val m = @Composable { if (uiState.controlType == ControlType.DPAD) DPadController(modifier = Modifier.scale(scale), onDirectionPressed = { viewModel.moveCharacter(it) }) else JoystickController(modifier = Modifier.scale(scale), onMove = { viewModel.moveCharacterByAngle(it) }) }
                    val a = @Composable { ActionButtonsController(modifier = Modifier.scale(scale), onActionChanged = { act, pr -> if (act == GameAction.Y) { if (pr) { viewModel.onInteractButtonPressed(); yButtonHoldJob?.cancel(); yButtonHoldJob = coroutineScope.launch { delay(3000); viewModel.toggleTeleportMenu(true) } } else yButtonHoldJob?.cancel() }; viewModel.updateActionState(act, pr) }, onClaimCollectiblePressed = { viewModel.onClaimCollectiblePressed() }) }
                    if (uiState.swapControls) { a(); m() } else { m(); a() }
                }
            }
        }
    }

    uiState.interactionPrompt?.let { p -> Box(Modifier.fillMaxSize().padding(top = 70.dp), Alignment.TopCenter) { Text(p, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color(0xFF3B0D1B).copy(0.85f), RoundedCornerShape(8.dp)).padding(24.dp, 12.dp)) } }
    uiState.showClaimedPopupFor?.let { CollectibleClaimDialog(it, { viewModel.dismissClaimedPopup() }) }
}

@Composable
private fun CacheStatusWidget(rs: RoadSource, ts: TileSource, mp: MapProvider) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CacheChip(
            label = "Calles",
            text = when (rs) { RoadSource.LOADING -> "Cargando..."; RoadSource.LOCAL_DB -> "Local (BD)"; else -> "Red" },
            color = when (rs) { RoadSource.LOADING -> Color(0xFFD4AF37); RoadSource.LOCAL_DB -> Color(0xFF4CAF50); else -> Color(0xFF2196F3) },
            loading = rs == RoadSource.LOADING
        )
        if (mp != MapProvider.OSM) CacheChip("Mapa", if (ts == TileSource.NETWORK) "Red" else "Caché", if (ts == TileSource.NETWORK) Color(0xFF2196F3) else Color(0xFF4CAF50), false)
    }
}

@Composable
private fun CacheChip(label: String, text: String, color: Color, loading: Boolean) {
    Row(
        modifier = Modifier.background(Color.Black.copy(0.72f), RoundedCornerShape(20.dp)).padding(10.dp, 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(8.dp), color, 1.5.dp) else Box(Modifier.size(8.dp).background(color, CircleShape))
        Text("$label: $text", color = Color.White, fontSize = 11.sp)
    }
}

private fun drawHealthBarOnDrawable(context: Context, original: android.graphics.drawable.Drawable?, health: Float, dying: Boolean): android.graphics.drawable.Drawable? {
    if (original !is android.graphics.drawable.BitmapDrawable || health >= 100f || dying) return original
    val bitmap = original.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(bitmap); val paint = android.graphics.Paint()
    val w = bitmap.width * 0.95f; val h = (bitmap.height * 0.08f).coerceIn(10f, 40f); val l = (bitmap.width - w) / 2f
    paint.color = android.graphics.Color.BLACK; canvas.drawRect(l, 0f, l + w, h, paint)
    paint.color = when { health > 60f -> android.graphics.Color.GREEN; health > 30f -> android.graphics.Color.YELLOW; else -> android.graphics.Color.RED }
    val hw = (w - 6f) * (health / 100f); if (hw > 0) canvas.drawRect(l + 3f, 3f, l + 3f + hw, h - 3f, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

private data class NpcWebPayload(val id: String, val lat: Double, val lng: Double, val rot: Float, val type: String, val imageKey: String? = null, val drawable: String? = null, val flip: Int? = null, val name: String? = null, val width: Float? = null, val height: Float? = null)

private fun buildHtml(lat: Double, lng: Double, z: Int): String = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" /><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" /><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><style>body { margin: 0; padding: 0; background: #aad3df; overflow: hidden; } #map-wrapper { position: absolute; top: -50%; left: -50%; width: 200vw; height: 200vh; transform-origin: center center; } #map { width: 100%; height: 100%; background: transparent; } .leaflet-marker-icon { background: none !important; border: none !important; } .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }</style></head><body><div id="map-wrapper"><div id="map"></div></div><script>
var map = L.map('map', { zoomControl: false, attributionControl: false, dragging: true, maxZoom: 22 }).setView([$lat, $lng], $z);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{ maxZoom: 22, maxNativeZoom: 18 }).addTo(map);
var npcMarkers = {}; var isZooming = false;
map.on('zoomstart', function() { isZooming = true; }); map.on('zoomend', function() { isZooming = false; });
function updateMapView(lat, lng, z) { if (!isZooming) map.setView([lat, lng], z, { animate: false }); }
function setMapRotation(deg) { var w = document.getElementById('map-wrapper'); if (w) w.style.transform = 'rotate(' + deg + 'deg)'; }
function escapeHtml(v) { return String(v).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c; }); }
function updateNpcs(data) {
    if (isZooming) return; var z = map.getZoom(); var ids = new Set(z >= 16.5 ? data.map(n => n.id) : []);
    for (var id in npcMarkers) if (!ids.has(id)) { map.removeLayer(npcMarkers[id]); delete npcMarkers[id]; }
    if (z < 16.5) return; var ds = Math.max(0.2, Math.min(1.4 * Math.pow(2, z - 19), 1.4));
    data.forEach(n => {
        var fw, fh; if (n.type === 'CAR') { fw = Math.round(n.width * ds); fh = Math.round(n.height * ds); } else if (n.type === 'MODULAR') { var sz = Math.max(16, Math.min(24.0 + ((z - 18.0) * 8.0), 40)); fw = sz; fh = sz; } else { fw = 24; fh = 24; }
        if (npcMarkers[n.id]) { npcMarkers[n.id].setLatLng([n.lat, n.lng]); }
        else {
            var html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%); width:'+fw+'px; height:'+fh+'px;"><img src="'+(window.imgCache ? window.imgCache[n.imageKey] : '')+'" style="width:100%; height:100%; display:block;"></div>';
            npcMarkers[n.id] = L.marker([n.lat, n.lng], { icon: L.divIcon({ html: html, className: '', iconSize: [0, 0] }) }).addTo(map);
        }
    });
}
</script></body></html>""".trimIndent()

private class ExactSizeDrawable(private val base: android.graphics.drawable.Drawable, private val ew: Int, private val eh: Int) : android.graphics.drawable.Drawable() {
    override fun getIntrinsicWidth() = ew
    override fun getIntrinsicHeight() = eh
    override fun draw(canvas: android.graphics.Canvas) { val b = bounds; base.setBounds(b.left, b.top, b.right, b.bottom); base.draw(canvas) }
    override fun setAlpha(alpha: Int) { base.alpha = alpha }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { base.colorFilter = cf }
    @Deprecated("Deprecated in Java") override fun getOpacity() = base.opacity
}

private fun <K : Any, V : Any> LruCache<K, V>.getOrPut(key: K, defaultValue: () -> V?): V? {
    val value = get(key)
    return if (value == null) {
        val answer = defaultValue()
        if (answer != null) put(key, answer)
        answer
    } else value
}
