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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.AssetPickerDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DesignerPanel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehiclePedalsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSteeringController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.GroundOverlay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.atan2
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.TeleportCatalog
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
    val base64Cache = remember { java.util.concurrent.ConcurrentHashMap<String, String>() }
    val widthCache = remember { java.util.concurrent.ConcurrentHashMap<String, Float>() }
    val heightCache = remember { java.util.concurrent.ConcurrentHashMap<String, Float>() }
    val nativeDrawableCache = remember { mutableMapOf<String, android.graphics.drawable.Drawable>() }
    val registeredWebImages = remember { mutableSetOf<String>() }
    val gson = remember { Gson() }
    // Launchers para Exportar e Importar archivos JSON en el dispositivo
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportLandmarksToUri(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importLandmarksFromUri(context, it) }
    }

    // Controladores de tiempo para el botón Y
    val coroutineScope = rememberCoroutineScope()
    var yButtonHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Cache de bitmaps de landmarks (sin tinte) por (assetPath, scale).
    val landmarkBitmapCache = remember { mutableMapOf<String, android.graphics.Bitmap?>() }

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

        // ───── CAPA 1: MAPA ────────────────────────────────────────────────────
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
                        view.setOnTouchListener(null) // Permitimos que detecte tu dedo
                        view.isClickable = true
                    } else {
                        view.setOnTouchListener { _, _ -> true } // Bloqueamos el mapa en modo juego
                        view.isClickable = false
                    }
                    uiState.currentLocation?.let { view.controller.setCenter(it) }

                    view.mapOrientation = if (uiState.isDriving) -uiState.vehicleRotation else 0f

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

                        val currentZoom = view.zoomLevelDouble
                        val isZoomedIn = currentZoom >= 16.5
                        val timeMs = System.currentTimeMillis()
                        val screenDensity = context.resources.displayMetrics.density
                        val highResRenderScale = 1.0f * screenDensity

                        // Limpieza de NPCs desconectados
                        val currentNpcIds = uiState.npcs.map { it.id }.toSet()
                        val iterator = markerCache.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (!currentNpcIds.contains(entry.key)) {
                                view.overlays.remove(entry.value)
                                iterator.remove()
                            }
                        }

                        // ─── DIBUJADO OPTIMIZADO DE NPCs ───
                        uiState.npcs.forEach { npc ->
                            val id = npc.id
                            // REGLA DE ORO: Reciclamos el marcador, no creamos uno nuevo.
                            val marker = markerCache[id] ?: Marker(view).apply {
                                title = "NPC_MARKER"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setInfoWindow(null)
                                isFlat = true
                                markerCache[id] = this
                                view.overlays.add(this)
                            }

                            if (isZoomedIn) {
                                // --- 1. EFECTO VISUAL DE MUERTE (FADE-OUT) ---
                                if (npc.isDying) {
                                    marker.setAlpha(0.3f)
                                } else {
                                    marker.setAlpha(1f)
                                }

                                if (npc.visualConfig != null) {
                                    val currentlyMoving = npc.speed > 0 || npc.isMoving
                                    val personSzDp = (24.0 + ((currentZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                    val exactPixels = (personSzDp * screenDensity).toInt()

                                    val frameIndex = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
                                        .getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0

                                    // Integramos la vida y estado en la clave de caché para que se pinte solo 1 vez
                                    val cacheKey = "PED_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${npc.health}_D${npc.isDying}"

                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        var baseDrawable = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager.getModularNpcDrawable(
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

                                } else if (npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.CAR) {
                                    var angle = npc.rotationAngle % 360f
                                    if (angle < 0) angle += 360f

                                    val frameIndex = (angle / 7.5f).roundToInt() % 48
                                    val dynamicScale = (1.4 * Math.pow(2.0, currentZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)

                                    val cacheKey = "CAR_${npc.carModel?.name}_${npc.carColor}_${frameIndex}_${dynamicScale}_H${npc.health}_D${npc.isDying}"

                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        var baseDrawable = ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager.getTintedCarNpc(
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
                                    marker.rotation = npc.rotationAngle
                                }
                            } else {
                                marker.setAlpha(0f)
                            }

                            marker.position = org.osmdroid.util.GeoPoint(npc.location.latitude, npc.location.longitude)
                        }
                        // ─── DIBUJADO DE COLECCIONABLES ──────────────────────────────────
                        val activeCollectibleIds = uiState.activeCollectibles.map { it.id }.toSet()

                        @Suppress("UNCHECKED_CAST")
                        val collectibleMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag) as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also {
                                view.setTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag, it)
                            }

                        // 1. Limpieza de coleccionables que ya fueron recogidos
                        val colIterator = collectibleMarkerCache.iterator()
                        while (colIterator.hasNext()) {
                            val entry = colIterator.next()
                            if (!activeCollectibleIds.contains(entry.key)) {
                                view.overlays.remove(entry.value)
                                colIterator.remove()
                            }
                        }

                        // 2. Actualización y dibujado
                        // ─── DIBUJADO DE COLECCIONABLES ─────────────────────────────────

                        uiState.activeCollectibles.forEach { collectible ->
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
                                // TAMAÑO FIJO MUY PEQUEÑO - Sin escalado dinámico
                                val exactPixels = (22 * screenDensity).toInt() // Solo 18dp fijos

                                val cacheKey = "COL_${collectible.assetPath}"
                                val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                    try {
                                        val bitmap = android.graphics.BitmapFactory.decodeStream(
                                            context.assets.open(collectible.assetPath)
                                        )

                                        if (bitmap != null) {
                                            // Glow amarillo muy sutil
                                            val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                                setSize(exactPixels, exactPixels)
                                                setColor(android.graphics.Color.argb(100, 255, 235, 59)) // Más transparente
                                            }

                                            // Sprite escalado para ocupar ~65% del círculo
                                            val spriteDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                            val spriteSize = (exactPixels * 0.90).toInt()
                                            spriteDrawable.setFilterBitmap(false)

                                            // Combinar en LayerDrawable
                                            val layers = arrayOf<android.graphics.drawable.Drawable>(
                                                glowDrawable,
                                                spriteDrawable
                                            )
                                            val layerDrawable = android.graphics.drawable.LayerDrawable(layers)

                                            // Centrar el sprite dentro del glow
                                            val inset = ((exactPixels - spriteSize) / 2).toInt()
                                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                                            ExactSizeDrawable(layerDrawable, exactPixels, exactPixels)
                                        } else {
                                            ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                    } catch (e: Exception) {
                                        ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                    }
                                }

                                marker.icon = cachedIcon
                                // Rotación muy lenta para destacar
                                marker.rotation = ((System.currentTimeMillis() / 30) % 360).toFloat()
                            } else {
                                marker.setAlpha(0f)
                            }

                            marker.position = org.osmdroid.util.GeoPoint(
                                collectible.latitude,
                                collectible.longitude
                            )
                        }
                    }

                    // ─── DIBUJADO DE LANDMARKS (con soporte de modo diseñador) ────────
                    @Suppress("UNCHECKED_CAST")
                    // Cambiamos el caché para que guarde una lista de Overlays genéricos por cada ID
                    val landmarkCache = (view.getTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag) as? MutableMap<Long, MutableList<org.osmdroid.views.overlay.Overlay>>)
                        ?: mutableMapOf<Long, MutableList<org.osmdroid.views.overlay.Overlay>>().also {
                            view.setTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag, it)
                        }

                    val currentIds = uiState.landmarks.map { it.id }.toSet()

                    // 1. Limpiar estructuras que fueron eliminadas de la base de datos
                    val iterator = landmarkCache.iterator()
                    while (iterator.hasNext()) {
                        val entry = iterator.next()
                        if (!currentIds.contains(entry.key)) {
                            // Quitamos del mapa todos los overlays asociados a este ID borrado
                            entry.value.forEach { overlay -> view.overlays.remove(overlay) }
                            iterator.remove()
                        }
                    }

                    uiState.landmarks.forEach { landmark ->
                        val overlays = landmarkCache.getOrPut(landmark.id) { mutableListOf() }

                        // 1. Obtener imagen de la caché
                        val bitmap = landmarkBitmapCache.getOrPut(landmark.assetPath) {
                            try {
                                context.assets.open(landmark.assetPath).use { inputStream ->
                                    android.graphics.BitmapFactory.decodeStream(inputStream)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("WorldMapScreen", "No se pudo cargar asset: ${landmark.assetPath}", e)
                                null
                            }
                        }
                        if (bitmap == null) return@forEach

                        // 2. Crear o recuperar el GroundOverlay (El que se ancla geográficamente)
                        val existingOverlay = overlays.filterIsInstance<org.osmdroid.views.overlay.GroundOverlay>().firstOrNull()
                        val groundOverlay = existingOverlay ?: org.osmdroid.views.overlay.GroundOverlay().apply {
                            overlays.add(this)
                            view.overlays.add(0, this) // El 0 asegura que el edificio se dibuje debajo de tu personaje
                        }

                        // 3. Matemáticas genéricas usando el ancho y alto del JSON
                        val centerLat = landmark.location.latitude
                        val centerLon = landmark.location.longitude
                        val center = org.osmdroid.util.GeoPoint(centerLat, centerLon)

                        // Uso las propiedades dinámicas del modelo para calcular el tamaño real
                        val halfW = (landmark.baseWidthMeters * landmark.scaleFactor) / 2.0
                        val halfH = (landmark.baseHeightMeters * landmark.scaleFactor) / 2.0
                        val d = kotlin.math.sqrt(halfW * halfW + halfH * halfH)
                        val theta = Math.toDegrees(kotlin.math.atan2(halfW, halfH))

                        // Calculamos las 4 esquinas del polígono
                        val pTL = center.destinationPoint(d, landmark.rotationAngle.toDouble() - theta)
                        val pTR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + theta)
                        val pBR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 - theta)
                        val pBL = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 + theta)

                        // Aplicamos las esquinas y la imagen
                        groundOverlay.setPosition(pTL, pTR, pBR, pBL)
                        groundOverlay.setImage(bitmap)

                        // 4. Controles del Modo Diseñador (Esto aplica para cualquier edificio)
                        val existingControl = overlays.filterIsInstance<org.osmdroid.views.overlay.Marker>().firstOrNull()
                        if (uiState.isDesignerMode) {
                            val controlMarker = existingControl ?: org.osmdroid.views.overlay.Marker(view).apply {
                                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                                icon = androidx.core.content.ContextCompat.getDrawable(
                                    context,
                                    android.R.drawable.ic_menu_edit
                                )?.mutate()
                                overlays.add(this)
                                view.overlays.add(this)
                            }

                            controlMarker.position = center

                            // Pinta el ícono de rojo si está seleccionado
                            controlMarker.icon = controlMarker.icon?.mutate()
                            if (uiState.selectedLandmarkId == landmark.id) {
                                controlMarker.icon?.setTint(android.graphics.Color.RED)
                            } else {
                                controlMarker.icon?.setTintList(null)
                            }

                            controlMarker.setOnMarkerClickListener { _, _ ->
                                viewModel.selectLandmark(landmark.id)
                                true
                            }

                            controlMarker.isDraggable = true
                            controlMarker.setOnMarkerDragListener(object : org.osmdroid.views.overlay.Marker.OnMarkerDragListener {
                                override fun onMarkerDragStart(marker: org.osmdroid.views.overlay.Marker) {
                                    viewModel.selectLandmark(landmark.id)
                                }
                                override fun onMarkerDrag(marker: org.osmdroid.views.overlay.Marker) {
                                    val dLat = marker.position.latitude - landmark.location.latitude
                                    val dLon = marker.position.longitude - landmark.location.longitude
                                    viewModel.moveSelectedLandmark(dLat, dLon)
                                }
                                override fun onMarkerDragEnd(marker: org.osmdroid.views.overlay.Marker) {}
                            })

                        } else {
                            // Limpia el control si apagas el modo diseñador
                            existingControl?.let {
                                view.overlays.remove(it)
                                overlays.remove(it)
                            }
                        }
                    }

                    view.invalidate()
                }
            )
        } else {
            // (Rama WebView intacta - los landmarks aún no se ven en proveedores web)
            val collectiblesJson = remember(uiState.activeCollectibles) {
                com.google.gson.Gson().toJson(uiState.activeCollectibles)
            }
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

                    val screenDensity = context.resources.displayMetrics.density
                    val highResRenderScale = 1.0f * screenDensity

                    val npcPayloads = uiState.npcs.map { npc ->
                        if (npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.CAR) {
                            var angle = npc.rotationAngle % 360f
                            if (angle < 0) angle += 360f

                            val frameIndex = (angle / 7.5f).roundToInt() % 48
                            val cacheKey = "${npc.carModel?.name}_${frameIndex}_${npc.carColor}_${screenDensity}"

                            val base64Image = base64Cache[cacheKey]
                            if (base64Image == null) {
                                base64Cache[cacheKey] = ""
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                    val drawable = ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager.getTintedCarNpc(
                                        context, angle, npc.carColor, highResRenderScale, npc.carModel
                                    )
                                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                    if (bitmap != null) {
                                        widthCache[cacheKey] = (bitmap.width / screenDensity) / screenDensity
                                        heightCache[cacheKey] = (bitmap.height / screenDensity) / screenDensity
                                        val outputStream = java.io.ByteArrayOutputStream()
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, outputStream)
                                        base64Cache[cacheKey] = "data:image/webp;base64," + android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                                    }
                                }
                            }
                            if (base64Image != null && base64Image.isNotEmpty() && !registeredWebImages.contains(cacheKey)) {
                                wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                registeredWebImages.add(cacheKey)
                            }

                            NpcWebPayload(
                                id = npc.id, lat = npc.location.latitude, lng = npc.location.longitude,
                                rot = npc.rotationAngle, type = "CAR",
                                imageKey = cacheKey, name = npc.displayName,
                                width = widthCache[cacheKey], height = heightCache[cacheKey]
                            )
                        } else if (npc.visualConfig != null) {
                            val timeMs = System.currentTimeMillis()
                            val currentlyMoving = npc.speed > 0 || npc.isMoving
                            val visualConfig = npc.visualConfig!!
                            val frameIndex = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
                                .getFrameIndex(context, visualConfig, currentlyMoving, timeMs) ?: 0
                            val cacheKey = "npc_mod_${visualConfig.bodyFolder}_${visualConfig.bodyPrefix}_${visualConfig.hairId}_${visualConfig.hairColor.value}_${visualConfig.shirtColor.value}_${visualConfig.pantsColor.value}_${npc.facingRight}_${frameIndex}_${screenDensity}"

                            val base64Image = base64Cache[cacheKey]
                            if (base64Image == null) {
                                base64Cache[cacheKey] = ""
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                    val bitmap = ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager.generateAssembledBitmap(context, visualConfig, currentlyMoving, timeMs)
                                    if (bitmap != null) {
                                        val outputStream = java.io.ByteArrayOutputStream()
                                        bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, outputStream)
                                        base64Cache[cacheKey] = "data:image/webp;base64," + android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.NO_WRAP)
                                    }
                                }
                            }
                            if (base64Image != null && base64Image.isNotEmpty() && !registeredWebImages.contains(cacheKey)) {
                                wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                registeredWebImages.add(cacheKey)
                            }
                            val flipScale = if (npc.facingRight) 1 else -1
                            NpcWebPayload(
                                id = npc.id, lat = npc.location.latitude, lng = npc.location.longitude,
                                rot = 0f, type = "MODULAR", imageKey = cacheKey, flip = flipScale, name = npc.displayName
                            )
                        } else {
                            NpcWebPayload(id = npc.id, lat = npc.location.latitude, lng = npc.location.longitude,
                                rot = npc.rotationAngle, type = npc.type.name, drawable = npc.type.drawableName, name = npc.displayName)
                        }
                    }
                    val npcsJson = gson.toJson(npcPayloads)
                    wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs($npcsJson);", null)
                    wv.evaluateJavascript("if(typeof updateCollectibles==='function')updateCollectibles(${JSONObject.quote(collectiblesJson)});", null)
                }
            )
        }

        // ─── CAPA 2: Personaje principal ────────────────────────────────────
        ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerCharacter(
            uiState = uiState,
            modifier = Modifier.align(Alignment.Center),
            health = viewModel.playerHealth,
            showHealthBar = viewModel.showHealthBar,
            damagePulseTrigger = viewModel.damagePulseTrigger
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
            // Indicador de modo diseñador activo
            AnimatedVisibility(visible = uiState.isDesignerMode, enter = fadeIn(), exit = fadeOut()) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFFD4AF37).copy(alpha = 0.85f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Architecture, null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Text("DISEÑADOR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Botones superiores derechos: Settings + toggle de modo diseñador
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
            ) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black) }

            // Toggle modo diseñador
            IconButton(
                onClick = { viewModel.toggleDesignerMode(!uiState.isDesignerMode) },
                modifier = Modifier
                    .background(
                        if (uiState.isDesignerMode) Color(0xFFD4AF37)
                        else Color.White.copy(alpha = 0.8f),
                        CircleShape
                    )
            ) {
                Icon(Icons.Default.Architecture, "Modo Diseñador", tint = Color.Black)
            }

            // Botón "+" para agregar nuevo asset (solo en modo diseñador)
            if (uiState.isDesignerMode) {
                IconButton(
                    onClick = { viewModel.showAssetPicker(true) },
                    modifier = Modifier
                        .background(Color(0xFF4CAF50), CircleShape)
                ) {
                    Icon(Icons.Default.Add, "Agregar Asset", tint = Color.White)
                }
            }
        }

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

        // ─── MENÚ DE VIAJE RÁPIDO (TELEPORT) DINÁMICO ─────────────────────────────────────
        if (uiState.showTeleportMenu) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleTeleportMenu(false) },
                title = { Text("Puntos de Teletransporte", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Selecciona tu estatua o destino:", fontSize = 14.sp)

                        // El LazyColumn permite que la lista sea scrolleable si agregas muchas zonas
                        LazyColumn(
                            modifier = Modifier.fillMaxHeight(0.5f), // Limita la altura a la mitad de la pantalla
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(TeleportCatalog.zones) { zone ->
                                Button(
                                    onClick = { viewModel.teleportTo(zone.latitude, zone.longitude) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(zone.name)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.toggleTeleportMenu(false) }) { Text("Cancelar") }
                }
            )
        }

        // ─── DIÁLOGO DE SELECCIÓN DE ASSET ────────────────────────────────────
        if (uiState.showAssetPicker) {
            AssetPickerDialog(
                context = context,
                onAssetSelected = { template ->
                    viewModel.addLandmarkAtPlayer(context, template)
                },
                onDismiss = { viewModel.showAssetPicker(false) }
            )
        }

        // ─── PANEL DE DISEÑADOR (cuando hay un landmark seleccionado) ─────────
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
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 130.dp, start = 12.dp, end = 12.dp)
                    .fillMaxWidth(0.9f)
            )
        }

        // ─── CONTROLES INFERIORES (ocultos en modo diseñador para no estorbar) ───
        if (!uiState.isDesignerMode) {
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
                            onExit = { isPressed ->
                                if (isPressed) {
                                    viewModel.onInteractButtonPressed()
                                    yButtonHoldJob?.cancel()
                                    yButtonHoldJob = coroutineScope.launch {
                                        kotlinx.coroutines.delay(3000)
                                        viewModel.toggleTeleportMenu(true)
                                    }
                                } else {
                                    yButtonHoldJob?.cancel()
                                }
                            }
                        )
                    }
                    if (uiState.swapControls) { pedalsComponent(); steeringComponent() }
                    else { steeringComponent(); pedalsComponent() }
                } else {
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
                                if (action == GameAction.Y) {
                                    if (isPressed) {
                                        viewModel.onInteractButtonPressed()
                                        yButtonHoldJob?.cancel()
                                        yButtonHoldJob = coroutineScope.launch {
                                            kotlinx.coroutines.delay(3000)
                                            viewModel.toggleTeleportMenu(true)
                                        }
                                    } else {
                                        yButtonHoldJob?.cancel()
                                    }
                                }
                                viewModel.updateActionState(action, isPressed)
                            },
                            onClaimCollectiblePressed = {
                                viewModel.onClaimCollectiblePressed()
                            }
                        )
                    }
                    if (uiState.swapControls) { actionComponent(); movementComponent() }
                    else { movementComponent(); actionComponent() }
                }
            }
        }
    }
    // --- SECUENCIA WASTED (TIPO GTA) ---
    if (uiState.showWastedScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            var scale by remember { mutableStateOf(0.5f) }
            LaunchedEffect(Unit) {
                androidx.compose.animation.core.animate(
                    initialValue = 0.5f,
                    targetValue = 1.3f,
                    animationSpec = tween(durationMillis = 3500, easing = LinearOutSlowInEasing)
                ) { value, _ -> scale = value }
            }

            Text(
                text = "WASTED",
                color = Color(0xFFD32F2F),
                fontSize = 60.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Serif,
                letterSpacing = 6.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .scale(scale)
            )
        }
    }
    // ─── UI SUPERPUESTA: AVISO DE COLECCIONABLE CERCANO ───────────────────────
    uiState.interactionPrompt?.let { promptText ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = promptText,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .background(
                        color = Color(0xFF3B0D1B).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }

    // ─── POP-UP DE RECOMPENSA (COLECCIONABLE) ─────────────────────────────────
    uiState.showClaimedPopupFor?.let { collectible ->
        ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog(
            collectible = collectible,
            onDismiss = { viewModel.dismissClaimedPopup() }
        )
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
        else Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text = "$label: $text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// Función auxiliar puramente matemática para inyectar la barra de vida a los sprites en memoria
private fun drawHealthBarOnDrawable(
    context: Context,
    original: android.graphics.drawable.Drawable?,
    health: Float,
    isDying: Boolean
): android.graphics.drawable.Drawable? {
    if (original !is android.graphics.drawable.BitmapDrawable || health >= 100f || isDying) {
        return original
    }

    val originalBitmap = original.bitmap
    val mutableBitmap = originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(mutableBitmap)
    val paint = android.graphics.Paint()

    // 🌟 NUEVO TAMAÑO: 95% del ancho y 24 píxeles de grosor para máxima visibilidad
    val barWidth = mutableBitmap.width * 0.95f
    val barHeight = 100f
    val left = (mutableBitmap.width - barWidth) / 2f
    val top = 0f // Pegada completamente al techo del sprite

    // Dibujamos el fondo negro (marco grueso)
    paint.color = android.graphics.Color.BLACK
    canvas.drawRect(left, top, left + barWidth, top + barHeight, paint)

    // Color según el nivel de vida
    paint.color = when {
        health > 60f -> android.graphics.Color.GREEN
        health > 30f -> android.graphics.Color.YELLOW
        else -> android.graphics.Color.RED
    }

    // 🌟 Borde interior: Restamos 6 píxeles al ancho y damos un offset de +3
    // para crear un contorno negro tipo RPG muy marcado
    val healthWidth = (barWidth - 6f) * (health / 100f)
    if (healthWidth > 0) {
        canvas.drawRect(
            left + 3f,
            top + 3f,
            left + 3f + healthWidth,
            top + barHeight - 3f,
            paint
        )
    }

    return android.graphics.drawable.BitmapDrawable(context.resources, mutableBitmap)
}
private data class NpcWebPayload(
    val id: String, val lat: Double, val lng: Double, val rot: Float, val type: String,
    val imageKey: String? = null, val drawable: String? = null, val flip: Int? = null,
    val name: String? = null, val width: Float? = null, val height: Float? = null
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
        .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }
    </style>
</head>
<body>
    <div id="map-wrapper"><div id="map"></div></div>
    <script>
        var map = L.map('map', { zoomControl: false, attributionControl: false, dragging: true, maxZoom: 22 }).setView([$lat, $lng], $zoom);
        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{ maxZoom: 22, maxNativeZoom: 18 }).addTo(map);
        var npcMarkers = {};
        var isZooming = false;
        map.on('zoomstart', function() { isZooming = true; });
        map.on('zoomend', function() { isZooming = false; });
        function updateMapView(lat, lng, z) { if (!isZooming) map.setView([lat, lng], z, { animate: false }); }
        function setMapRotation(deg) { var wrapper = document.getElementById('map-wrapper'); if (wrapper) wrapper.style.transform = 'rotate(' + deg + 'deg)'; }
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }
        function escapeHtml(value) { return String(value).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]||c; }); }
        var collectibleMarkers = {};

        function updateCollectibles(jsonStr) {
            var data = JSON.parse(jsonStr);
            
            // Limpiar marcadores existentes
            for (var key in collectibleMarkers) {
                map.removeLayer(collectibleMarkers[key]);
            }
            collectibleMarkers = {};
        
            data.forEach(function(col) {
                var pUrl = 'file:///android_asset/' + col.assetPath;
                
                // TAMAÑO ULTRA PEQUEÑO: Contenedor de 20px
                var containerSize = 20;
                var iconSize = 14; // El asset ocupa 14px dentro del círculo
                
                var html = '<div style="' +
                    'position:relative; ' +
                    'width:' + containerSize + 'px; ' +
                    'height:' + containerSize + 'px; ' +
                    'display:flex; ' +
                    'justify-content:center; ' +
                    'align-items:center;' +
                '">' +
                    // Círculo amarillo de fondo
                    '<div style="' +
                        'position:absolute; ' +
                        'width:100%; ' +
                        'height:100%; ' +
                        'background:radial-gradient(circle, rgba(255,235,59,0.5) 0%, rgba(255,235,59,0) 60%); ' +
                        'border-radius:50%;' +
                    '"></div>' +
                    // Imagen del coleccionable
                    '<img src="' + pUrl + '" style="' +
                        'position:relative; ' +
                        'width:' + iconSize + 'px; ' +
                        'height:' + iconSize + 'px; ' +
                        'object-fit:contain; ' +
                        'image-rendering:pixelated;' +
                    '">' +
                '</div>';
                
                var icon = L.divIcon({ 
                    html: html, 
                    className: '', 
                    iconSize: [containerSize, containerSize], 
                    iconAnchor: [containerSize/2, containerSize/2] 
                });
                
                collectibleMarkers[col.id] = L.marker(
                    [col.latitude, col.longitude], 
                    { icon: icon, interactive: false }
                ).addTo(map);
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
