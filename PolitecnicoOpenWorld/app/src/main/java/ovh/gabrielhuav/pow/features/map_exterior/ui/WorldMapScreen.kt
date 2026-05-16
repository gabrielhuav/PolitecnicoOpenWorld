package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import com.google.gson.Gson
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehiclePedalsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSteeringController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.draw.scale
import ovh.gabrielhuav.pow.features.settings.models.ControlType


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel(factory = WorldMapViewModel.Factory(context)),
    onNavigateToMainMenu: () -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val base64Cache = remember { mutableMapOf<String, String>() }
    val widthCache = remember { mutableMapOf<String, Float>() }
    val heightCache = remember { mutableMapOf<String, Float>() }
    // Caché para salvar la memoria RAM en OSMDroid
    val nativeDrawableCache = remember { mutableMapOf<String, android.graphics.drawable.Drawable>() }
    // Recuerda qué imágenes ya cruzaron el puente a JavaScript
    val registeredWebImages = remember { mutableSetOf<String>() }
    val gson = remember { Gson() }

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

    // NOTA: El game loop ya NO se controla aquí. Se inicia en el `init` del ViewModel y
    // se cancela en `onCleared`. Esto evita que navegar a Settings y volver detenga a
    // los NPCs, porque el loop ya no depende del ciclo de vida del Composable.

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
// ───── CAPA 1: MAPA ────────────────────────────────────────────────────────
        if (uiState.mapProvider == MapProvider.OSM) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        setOnTouchListener { _, _ -> true }
                        isClickable = false; isFocusable = false
                        controller.setZoom(uiState.zoomLevel)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    uiState.currentLocation?.let { view.controller.setCenter(it) }

                    // --- CÁMARA ESTILO GPS CORREGIDA ---
                    // Compensamos el desfase matemático: 90f - rotación
                    view.mapOrientation = if (uiState.isDriving) -uiState.vehicleRotation else 0f
                    // -------------------------

                    val zoomDiff = kotlin.math.abs(view.zoomLevelDouble - uiState.zoomLevel)
                    when {
                        zoomDiff < 0.01 -> {}
                        zoomDiff > 1.5  -> view.controller.animateTo(uiState.currentLocation, uiState.zoomLevel, 120L)
                        else            -> view.controller.setZoom(uiState.zoomLevel)
                    }

                    if (uiState.isRoadNetworkReady) {
                        @Suppress("UNCHECKED_CAST")
                        val markerCache = (view.tag as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also { view.tag = it }

                        // CULLING NATIVO: Dependemos enteramente del zoom real de la vista
                        val currentZoom = view.zoomLevelDouble
                        val isZoomedIn = currentZoom >= 16.5
                        val timeMs = System.currentTimeMillis()
                        val screenDensity = context.resources.displayMetrics.density
                        val highResRenderScale = 1.0f * screenDensity

                        // 1. LIMPIEZA DE DESCONECTADOS/ELIMINADOS
                        val currentNpcIds = uiState.npcs.map { it.id }.toSet()
                        val iterator = markerCache.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (!currentNpcIds.contains(entry.key)) {
                                view.overlays.remove(entry.value)
                                iterator.remove()
                            }
                        }

                        // 2. ACTUALIZACIÓN Y DIBUJADO DE MARCADORES ACTIVOS
                        uiState.npcs.forEach { npc ->
                            val id = npc.id
                            val marker = markerCache[id] ?: Marker(view).apply {
                                title = "NPC_MARKER"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                isFlat = true
                                markerCache[id] = this
                                view.overlays.add(this)
                            }

                            // Renderizado visual según el zoom
                            if (isZoomedIn) {
                                marker.setAlpha(1f)

                                if (npc.visualConfig != null) {
                                    val currentlyMoving = npc.speed > 0 || npc.isMoving
                                    val personSzDp = (24.0 + ((currentZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                    val exactPixels = (personSzDp * screenDensity).toInt()

                                    // Obtenemos el Frame exacto para la llave de Caché
                                    val frameIndex = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
                                        .getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0

                                    val cacheKey = "PED_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}"

                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        val baseDrawable = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager.getModularNpcDrawable(
                                            context = context,
                                            visualConfig = npc.visualConfig!!,
                                            isMoving = currentlyMoving,
                                            isFacingRight = npc.facingRight,
                                            timeMs = timeMs,
                                            scale = highResRenderScale,
                                            displayName = npc.displayName
                                        )
                                        baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                            ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    }

                                    marker.icon = cachedIcon
                                    marker.rotation = 0f

                                } else if (npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.CAR) {
                                    var angle = npc.rotationAngle % 360f
                                    if (angle < 0) angle += 360f
                                    val frameIndex = (angle / 7.5f).roundToInt() % 48
                                    val dynamicScale = (1.4 * Math.pow(2.0, currentZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)

                                    val cacheKey = "CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${dynamicScale}"

                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        val baseDrawable = ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager.getTintedCarNpc(
                                            context, npc.rotationAngle, npc.carColor, highResRenderScale, npc.carModel
                                        )
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
                                    // SVG Clásicos
                                    val cacheKey = "SVG_${npc.type.name}"
                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                        val baseDrawable = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                        baseDrawable?.let {
                                            val exactPixels = (24 * screenDensity).toInt()
                                            ExactSizeDrawable(it, exactPixels, exactPixels)
                                        } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    }

                                    marker.icon = cachedIcon
                                    marker.rotation = npc.rotationAngle
                                }
                            } else {
                                marker.setAlpha(0f)
                            }

                            // 3. ACTUALIZACIÓN PERSISTENTE DE POSICIÓN
                            marker.position = org.osmdroid.util.GeoPoint(npc.location.latitude, npc.location.longitude)
                        }
                    }
                    view.invalidate()
                }
            )
        } else {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = cachingClient
                        val lat = uiState.currentLocation?.latitude ?: 0.0
                        val lng = uiState.currentLocation?.longitude ?: 0.0
                        loadDataWithBaseURL(null, buildHtml(lat, lng, uiState.zoomLevel.toInt()), "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { wv ->
                    uiState.currentLocation?.let {
                        wv.evaluateJavascript("if(typeof updateMapView==='function')updateMapView(${it.latitude}, ${it.longitude}, ${uiState.zoomLevel.toInt()});", null)
                    }

                    // --- CÁMARA ESTILO GPS PARA LA WEB CORREGIDA ---
                    val mapRot = if (uiState.isDriving) -uiState.vehicleRotation else 0f
                    wv.evaluateJavascript("if(typeof setMapRotation==='function')setMapRotation(${mapRot});", null)
                    // -------------------------------------

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

                    // --- LA INYECCIÓN MAESTRA CORREGIDA ---
                    val screenDensity = context.resources.displayMetrics.density
                    val highResRenderScale = 1.0f * screenDensity

                    val npcPayloads = uiState.npcs.map { npc ->
                        if (npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.CAR) {
                            var angle = npc.rotationAngle % 360f
                            if (angle < 0) angle += 360f
                            val frameIndex = (angle / 7.5f).roundToInt() % 48
                            val cacheKey =
                                "${npc.carModel.name}_${frameIndex}_${npc.carColor}_${screenDensity}"

                            val base64Image = base64Cache.getOrPut(cacheKey) {
                                val drawable =
                                    ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager.getTintedCarNpc(
                                        context,
                                        npc.rotationAngle,
                                        npc.carColor,
                                        highResRenderScale,
                                        npc.carModel
                                    )
                                val bitmap =
                                    (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                if (bitmap != null) {
                                    widthCache[cacheKey] = (bitmap.width / screenDensity) / screenDensity
                                    heightCache[cacheKey] = (bitmap.height / screenDensity) / screenDensity

                                    val outputStream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(
                                        android.graphics.Bitmap.CompressFormat.WEBP,
                                        100,
                                        outputStream
                                    )
                                    "data:image/webp;base64," + android.util.Base64.encodeToString(
                                        outputStream.toByteArray(),
                                        android.util.Base64.NO_WRAP
                                    )
                                } else {
                                    ""
                                }
                            }
                            // MAGIA WEB: Si la web no conoce esta imagen, se la enviamos para que la guarde
                            if (!registeredWebImages.contains(cacheKey) && base64Image.isNotEmpty()) {
                                wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                registeredWebImages.add(cacheKey)
                            }

                            NpcWebPayload(
                                id = npc.id, lat = npc.location.latitude, lng = npc.location.longitude,
                                rot = npc.rotationAngle, type = "CAR",
                                imageKey = cacheKey,
                                name = npc.displayName,
                                width = widthCache[cacheKey], height = heightCache[cacheKey]
                            )
                        }else if (npc.visualConfig != null) {
                            val timeMs = System.currentTimeMillis()
                            val currentlyMoving = npc.speed > 0 || npc.isMoving
                            val visualConfig = npc.visualConfig!!
                            val frameIndex = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
                                .getFrameIndex(context, visualConfig, currentlyMoving, timeMs) ?: 0
                            val cacheKey = "npc_mod_${visualConfig.bodyFolder}_${visualConfig.bodyPrefix}_${visualConfig.hairId}_${visualConfig.hairColor.value}_${visualConfig.shirtColor.value}_${visualConfig.pantsColor.value}_${npc.facingRight}_${frameIndex}_${screenDensity}"

                            val base64Image = base64Cache.getOrPut(cacheKey) {
                                val bitmap = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager.generateAssembledBitmap(
                                    context, visualConfig, currentlyMoving, timeMs
                                )
                                if (bitmap != null) {
                                    val outputStream = java.io.ByteArrayOutputStream()
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, outputStream)
                                    "data:image/webp;base64," + android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                                } else { "" }
                            }
                            // MAGIA WEB
                            if (!registeredWebImages.contains(cacheKey) && base64Image.isNotEmpty()) {
                                wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                registeredWebImages.add(cacheKey)
                            }

                            val flipScale = if (npc.facingRight) 1 else -1

                            NpcWebPayload(
                                id = npc.id, lat = npc.location.latitude, lng = npc.location.longitude,
                                rot = 0f, type = "MODULAR",
                                imageKey = cacheKey,
                                flip = flipScale, name = npc.displayName
                            )

                        } else {
                            NpcWebPayload(
                                id = npc.id,
                                lat = npc.location.latitude,
                                lng = npc.location.longitude,
                                rot = npc.rotationAngle,
                                type = npc.type.name,
                                drawable = npc.type.drawableName,
                                name = npc.displayName
                            )
                        }
                    }
                    val npcsJson = gson.toJson(npcPayloads)
                    wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs($npcsJson);", null)
                }
            )
        }
        // ─── CAPA 2, 3, 4, 5, 6, 7 (Idénticas) ──────────────────────────────────
        ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerCharacter(
            uiState = uiState,
            modifier = Modifier.align(Alignment.Center)
        )

        if (!uiState.isRoadNetworkReady) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), Color(0xFFD4AF37), strokeWidth = 2.dp)
                Text("Cargando calles...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 64.dp, start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = uiState.showCacheWidget, enter = fadeIn(), exit = fadeOut()) {
                CacheStatusWidget(roadSource = uiState.roadSource, tileSource = uiState.tileSource, mapProvider = uiState.mapProvider)
            }
            AnimatedVisibility(visible = uiState.showFpsWidget, enter = fadeIn(), exit = fadeOut()) {
                CacheChip(label = "Rendimiento", text = "$currentFps FPS", color = if (currentFps >= 24) Color(0xFF4CAF50) else Color(0xFFD32F2F), isLoading = false)
            }
        }

        IconButton(
            onClick = onNavigateToSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black) }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { viewModel.zoomIn() }, modifier = Modifier
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
                .size(48.dp)
            ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
            IconButton(onClick = { viewModel.zoomOut() }, modifier = Modifier
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
                .size(48.dp)
            ) { Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        }

        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val maxScale = if (isPortrait) 1.0f else 1.4f
        val effectiveScale = uiState.controlsScale.coerceAtMost(maxScale)
        val sidePadding = if (isPortrait) 16.dp else 64.dp
        val bottomPadding = if (isPortrait) 48.dp else 32.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.isDriving) {
                // --- ESQUEMA DE CONDUCCIÓN ---
                val steeringComponent = @Composable {
                    VehicleSteeringController(
                        modifier = Modifier.scale(effectiveScale),
                        onSteerLeft = { viewModel.steerLeft(it) },
                        onSteerRight = { viewModel.steerRight(it) }
                    )
                }
                val pedalsComponent = @Composable {
                    VehiclePedalsController(
                        modifier = Modifier.scale(effectiveScale),
                        onAccelerate = { viewModel.accelerate(it) },
                        onBrake = { viewModel.brake(it) },
                        onExit = { viewModel.onInteractButtonPressed() }
                    )
                }
                if (uiState.swapControls) { pedalsComponent(); steeringComponent() }
                else { steeringComponent(); pedalsComponent() }
            } else {
                // --- ESQUEMA A PIE ---
                val movementComponent = @Composable {
                    if (uiState.controlType == ControlType.DPAD) {
                        DPadController(modifier = Modifier.scale(effectiveScale), onDirectionPressed = { viewModel.moveCharacter(it) })
                    } else {
                        JoystickController(modifier = Modifier.scale(effectiveScale), onMove = { angle -> viewModel.moveCharacterByAngle(angle) })
                    }
                }
                val actionComponent = @Composable {
                    ActionButtonsController(
                        modifier = Modifier.scale(effectiveScale),
                        onActionChanged = { action, isPressed ->
                            // SUBIR AL AUTO con Y
                            if (action == GameAction.Y && isPressed) {
                                viewModel.onInteractButtonPressed()
                            }
                            viewModel.updateActionState(action, isPressed)
                        }
                    )
                }
                if (uiState.swapControls) { actionComponent(); movementComponent() }
                else { movementComponent(); actionComponent() }
            }
        }
    }
}

@Composable
private fun CacheStatusWidget(roadSource: RoadSource, tileSource: TileSource, mapProvider: MapProvider) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CacheChip(
            label = "Calles",
            text  = when (roadSource) { RoadSource.LOADING -> "Cargando..."; RoadSource.LOCAL_DB -> "Local (BD)"; RoadSource.NETWORK -> "Overpass API" },
            color = when (roadSource) { RoadSource.LOADING -> Color(0xFFD4AF37); RoadSource.LOCAL_DB -> Color(0xFF4CAF50); RoadSource.NETWORK -> Color(0xFF2196F3) },
            isLoading = roadSource == RoadSource.LOADING
        )
        if (mapProvider != MapProvider.OSM) {
            val tileLabel = when (tileSource) { TileSource.LOCAL_OSM -> "Local (osmdroid)"; TileSource.LOCAL_CACHE -> "Local (caché)"; TileSource.NETWORK -> "Red" }
            val tileColor = when (tileSource) { TileSource.LOCAL_OSM, TileSource.LOCAL_CACHE -> Color(0xFF4CAF50); TileSource.NETWORK -> Color(0xFF2196F3) }
            CacheChip(label = "Mapa", text = tileLabel, color = tileColor, isLoading = false)
        }
    }
}

@Composable
private fun CacheChip(label: String, text: String, color: Color, isLoading: Boolean) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(8.dp), color = color, strokeWidth = 1.5.dp)
        else Box(Modifier
            .size(8.dp)
            .background(color, CircleShape))
        Text(text = "$label: $text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private data class NpcWebPayload(
    val id: String,
    val lat: Double,
    val lng: Double,
    val rot: Float,
    val type: String,
    val imageKey: String? = null,
    val drawable: String? = null,
    val flip: Int? = null,
    val name: String? = null,
    val width: Float? = null,
    val height: Float? = null
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
        
        /* Wrapper sobredimensionado para absorber la rotación sin mostrar esquinas vacías */
        #map-wrapper { 
            position: absolute; 
            top: -50%; left: -50%; 
            width: 200vw; height: 200vh; 
            transform-origin: center center;
        }
        #map { width: 100%; height: 100%; background: transparent; }
        
        .leaflet-marker-icon { background: none !important; border: none !important; }
        .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }
    </style>
</head>
<body>
    <div id="map-wrapper"><div id="map"></div></div>
    <script>
        var map = L.map('map', { 
            zoomControl: false, 
            attributionControl: false,
            dragging: true,
            maxZoom: 22
        }).setView([$lat, $lng], $zoom);

        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{
            maxZoom: 22,         
            maxNativeZoom: 18
        }).addTo(map);
        var npcMarkers = {};

        // --- SISTEMA ANTI-DESINCRONIZACIÓN ---
        var isZooming = false;
        map.on('zoomstart', function() { isZooming = true; });
        map.on('zoomend', function() { isZooming = false; });

        function updateMapView(lat, lng, z) { 
            if (!isZooming) {
                map.setView([lat, lng], z, { animate: false }); 
            }
        }
        
        // ROTACIÓN DEL MAPA COMPLETO
        function setMapRotation(deg) {
            var wrapper = document.getElementById('map-wrapper');
            if (wrapper) {
                wrapper.style.transform = 'rotate(' + deg + 'deg)';
            }
        }
        
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }
        function escapeHtml(value) {
            return String(value).replace(/[&<>"']/g, function(char) {
                var replacements = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
                return replacements[char] || char;
            });
        }

        function updateNpcs(data) {
            if (isZooming) return;
        
            var currentZoom = map.getZoom();
            var isZoomedIn = currentZoom >= 16.5;
            
            var ids = new Set();
            if (isZoomedIn) {
                ids = new Set(data.map(function(n) { return n.id; }));
            }
            
            for (var id in npcMarkers) {
                if (!ids.has(id)) { 
                    map.removeLayer(npcMarkers[id]); 
                    delete npcMarkers[id]; 
                }
            }
        
            if (!isZoomedIn) return;
        
            var dynamicScale = 1.4 * Math.pow(2, currentZoom - 19);
            dynamicScale = Math.max(0.2, Math.min(dynamicScale, 1.4));
            
            data.forEach(function(npc) {
                var finalW, finalH;
                
                if (npc.type === 'CAR') {
                    finalW = Math.round(npc.width * dynamicScale);
                    finalH = Math.round(npc.height * dynamicScale);
                } else if (npc.type === 'MODULAR') {
                    var personSz = 24.0 + ((currentZoom - 18.0) * 8.0);
                    var sz = Math.max(16, Math.min(personSz, 40));
                    finalW = sz;
                    finalH = sz;
                } else {
                    finalW = 24;
                    finalH = 24;
                }
        
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
                            wrapper.style.transform = 'translate(-50%, -50%) rotate(' + npc.rot + 'deg)';
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
                        html = '<div class="npc-c" style="position:absolute; transform: translate(-50%, -50%) rotate('+npc.rot+'deg); width:24px; height:24px;">' + nameTagHtml + '<img src="'+pUrl+'" style="width:100%; height:100%; display:block;"></div>';
                    }
                    
                    var icon = L.divIcon({ html: html, className: '', iconSize: [0, 0] });
                    npcMarkers[npc.id] = L.marker([npc.lat, npc.lng], { icon: icon }).addTo(map);
                }
            });
        }
    </script>
</body>
</html>
""".trimIndent()

// === HERRAMIENTA DE ESCALADO NATIVO ===
// Permite redimensionar sprites en OSMDroid sin gastar memoria recreando Bitmaps
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
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = base.opacity
}