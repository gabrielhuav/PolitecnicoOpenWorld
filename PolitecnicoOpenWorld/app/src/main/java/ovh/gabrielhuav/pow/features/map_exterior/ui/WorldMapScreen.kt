package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import kotlin.math.abs
import androidx.compose.ui.draw.scale
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel(factory = WorldMapViewModel.Factory(context)),
    onNavigateToMainMenu: () -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // ── CÁLCULO DE FPS REAL (Solo se ejecuta si el widget está encendido) ──
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

    DisposableEffect(Unit) {
        viewModel.startGameLoop()
        onDispose { viewModel.stopGameLoop() }
    }

    val tileCache = viewModel.tileCache
    val cachingClient = remember(tileCache) {
        CachingWebViewClient(
            tileCache          = tileCache,
            getCurrentProvider = { viewModel.uiState.value.mapProvider },
            onTileServed       = { fromCache -> viewModel.notifyTileSource(fromCache) }
        )
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        if (uiState.isLoadingLocation) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            Text("Iniciando mundo...", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
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
                    val zoomDiff = abs(view.zoomLevelDouble - uiState.zoomLevel)
                    when {
                        zoomDiff < 0.01 -> {}
                        zoomDiff > 1.5  -> view.controller.animateTo(uiState.currentLocation, uiState.zoomLevel, 120L)
                        else            -> view.controller.setZoom(uiState.zoomLevel)
                    }
                    if (uiState.isRoadNetworkReady) {
                        @Suppress("UNCHECKED_CAST")
                        val markerCache = (view.tag as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also { view.tag = it }
                        val activeIds = mutableSetOf<String>()
                        uiState.npcs.forEach { npc ->
                            val id = npc.id; activeIds.add(id)
                            val marker = markerCache[id] ?: Marker(view).apply {
                                title = "NPC_MARKER"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                markerCache[id] = this; view.overlays.add(this)
                            }
                            if (npc.type == ovh.gabrielhuav.pow.domain.models.NpcType.CAR) {
                                val currentZoom = view.zoomLevelDouble

                                // Calculamos la escala proporcional, pero:
                                // coerceIn asegura que no sea menor a 0.3x (muy lejos) ni mayor a 1.8x (muy cerca)
                                val dynamicScale = (1.6 * Math.pow(2.0, currentZoom - 19.0)).toFloat().coerceIn(0.3f, 1.8f)

                                marker.icon = ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager.getTintedCarNpc(
                                    context, npc.rotationAngle, npc.carColor, dynamicScale, npc.carModel
                                )
                                marker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                                marker.rotation = 0f
                            } else {
                                val resId = context.resources.getIdentifier(
                                    npc.type.drawableName, "drawable", context.packageName)
                                if (resId != 0) marker.icon = ContextCompat.getDrawable(context, resId)
                                marker.rotation = npc.rotationAngle
                            }

                            marker.position = npc.location
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
                        wv.evaluateJavascript("if(typeof moveMap==='function')moveMap(${it.latitude},${it.longitude});", null)
                    }
                    wv.evaluateJavascript("if(typeof setMapZoom==='function')setMapZoom(${uiState.zoomLevel.toInt()});", null)
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
                    // Modifica la inyección del JSON a WebView para incluir el color HSV:
                    val npcsJson = uiState.npcs.joinToString(prefix = "[", postfix = "]") { npc ->
                        val hsv = FloatArray(3)
                        android.graphics.Color.colorToHSV(npc.carColor, hsv)
                        "{id:'${npc.id}',lat:${npc.location.latitude},lng:${npc.location.longitude}," +
                                "rot:${npc.rotationAngle},type:'${npc.type.name}',drawable:'${npc.type.drawableName}', " +
                                "hue:${hsv[0]}, dir:'${npc.carModel.dirName}', prefix:'${npc.carModel.prefix}'}"
                    }
                    wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs($npcsJson);", null)
                }
            )
        }

        // ─── CAPA 2: JUGADOR ────────────────────────────────────────────────────
        Box(
            modifier = Modifier.align(Alignment.Center).size(22.dp)
                .shadow(2.dp, CircleShape).clip(CircleShape)
                .background(Color.White).border(1.5.dp, Color(0xFFE6A800), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, "Jugador", tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
        }

        // ─── CAPA 3: INDICADOR DE CARGA DE CALLES ────────────────────────────────
        if (!uiState.isRoadNetworkReady) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), Color(0xFFD4AF37), strokeWidth = 2.dp)
                Text("Cargando calles...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ─── CAPA 4: WIDGETS DE DIAGNÓSTICO DE CACHÉ Y FPS ─────────────────────────
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = uiState.showCacheWidget, enter = fadeIn(), exit = fadeOut()) {
                CacheStatusWidget(
                    roadSource  = uiState.roadSource,
                    tileSource  = uiState.tileSource,
                    mapProvider = uiState.mapProvider
                )
            }

            AnimatedVisibility(visible = uiState.showFpsWidget, enter = fadeIn(), exit = fadeOut()) {
                CacheChip(
                    label = "Rendimiento",
                    text  = "$currentFps FPS",
                    color = if (currentFps >= 24) Color(0xFF4CAF50) else Color(0xFFD32F2F),
                    isLoading = false
                )
            }
        }

        // ─── CAPA 5: BOTÓN DE AJUSTES ─────────────────────────────────────────────
        IconButton(
            // CAMBIO: Ahora llama a la función de navegación
            onClick = onNavigateToSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black)
        }
        // ─── CAPA 6: ZOOM ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = { viewModel.zoomIn() },
                modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape).size(48.dp)
            ) { Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
            IconButton(onClick = { viewModel.zoomOut() },
                modifier = Modifier.background(Color.White.copy(alpha = 0.8f), CircleShape).size(48.dp)
            ) { Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black) }
        }

        // ─── CAPA 7: CONTROLES DE JUEGO RESPONSIVOS ────────────────────────────────
        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        // Límite de tamaño: 1.0f máximo en vertical, 1.4f máximo en horizontal
        val maxScale = if (isPortrait) 1.0f else 1.4f
        val effectiveScale = uiState.controlsScale.coerceAtMost(maxScale)

        // Límite de márgenes: Pegados a la orilla (16.dp) en vertical, más centrados (64.dp) en horizontal
        val sidePadding = if (isPortrait) 16.dp else 64.dp
        // Levantamos un poco más los botones en vertical para mayor comodidad del pulgar
        val bottomPadding = if (isPortrait) 48.dp else 32.dp

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val movementComponent = @Composable {
                if (uiState.controlType == ControlType.DPAD) {
                    DPadController(
                        modifier = Modifier.scale(effectiveScale), // Usamos la escala limitada
                        onDirectionPressed = { viewModel.moveCharacter(it) }
                    )
                } else {
                    JoystickController(
                        modifier = Modifier.scale(effectiveScale), // Usamos la escala limitada
                        onMove = { angle -> viewModel.moveCharacterByAngle(angle) }
                    )
                }
            }

            val actionComponent = @Composable {
                ActionButtonsController(
                    modifier = Modifier.scale(effectiveScale),
                    onActionPressed = { viewModel.executeAction(it) }
                )
            }

            if (uiState.swapControls) {
                actionComponent()
                movementComponent()
            } else {
                movementComponent()
                actionComponent()
            }
        }

    }
}

// ─── WIDGETS DE DIAGNÓSTICO (Originales intactos) ─────────────────────────────

@Composable
private fun CacheStatusWidget(
    roadSource: RoadSource,
    tileSource: TileSource,
    mapProvider: MapProvider
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CacheChip(
            label = "Calles",
            text  = when (roadSource) {
                RoadSource.LOADING   -> "Cargando..."
                RoadSource.LOCAL_DB  -> "Local (BD)"
                RoadSource.NETWORK   -> "Overpass API"
            },
            color = when (roadSource) {
                RoadSource.LOADING   -> Color(0xFFD4AF37)
                RoadSource.LOCAL_DB  -> Color(0xFF4CAF50)
                RoadSource.NETWORK   -> Color(0xFF2196F3)
            },
            isLoading = roadSource == RoadSource.LOADING
        )

        if (mapProvider != MapProvider.OSM) {
            val tileLabel = when (tileSource) {
                TileSource.LOCAL_OSM   -> "Local (osmdroid)"
                TileSource.LOCAL_CACHE -> "Local (caché)"
                TileSource.NETWORK     -> "Red"
            }
            val tileColor = when (tileSource) {
                TileSource.LOCAL_OSM, TileSource.LOCAL_CACHE -> Color(0xFF4CAF50)
                TileSource.NETWORK                           -> Color(0xFF2196F3)
            }
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
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(8.dp), color = color, strokeWidth = 1.5.dp)
        } else {
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Text(text = "$label: $text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

private fun buildHtml(lat: Double, lng: Double, zoom: Int): String = """
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        body { margin: 0; padding: 0; background: #aad3df; overflow: hidden; }
        #map { width: 100vw; height: 100vh; background: #aad3df; }
        /* Evita que los iconos tengan bordes o fondos blancos */
        .leaflet-marker-icon { background: none !important; border: none !important; }
        .npc-c { pointer-events: none; display: flex; align-items: center; justify-content: center; }
        
        /* Filtro especial para teñir blanco preservando detalles oscuros */
        .car-img { 
            display: block; 
            filter: sepia(100%) saturate(300%) brightness(0.9);
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map = L.map('map', { 
            zoomControl: false, 
            attributionControl: false,
            dragging: true 
        }).setView([$lat, $lng], $zoom);

        var currentTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
        var npcMarkers = {};

        // --- FUNCIONES PUENTE PARA KOTLIN ---
        function moveMap(lat, lng) { map.setView([lat, lng], map.getZoom(), { animate: false }); }
        function setMapZoom(z) { map.setZoom(z, { animate: false }); }
        function changeTileUrl(url) { if (currentTileLayer) currentTileLayer.setUrl(url); }
        function setRoadNetworkReady(ready) { window.roadNetworkReady = ready; }

        function updateNpcs(data) {
            var currentZoom = map.getZoom();
            // Tamaño base 80px en zoom 19
            var sz = 80 * Math.pow(2, currentZoom - 19);
            sz = Math.max(15, Math.min(sz, 100)); 

            var ids = new Set(data.map(function(n) { return n.id; }));
            for (var id in npcMarkers) {
                if (!ids.has(id)) { map.removeLayer(npcMarkers[id]); delete npcMarkers[id]; }
            }

            data.forEach(function(npc) {
                // CORRECCIÓN CLAVE: Usamos npc.rot tal cual, sin sumarle ni restarle nada.
                // El sprite 000 ya es Norte, y 0 grados en tu NpcAiManager también es Norte.
                var rotAdjusted = npc.rot;
                
                var frame = Math.round(((rotAdjusted % 360) + 360) % 360 / 7.5) % 48;
                var idx = String(frame).padStart(3, '0');
                var url = 'file:///android_asset/VEHICLES/' + npc.dir + '/' + npc.prefix + idx + '.webp';

                if (npcMarkers[npc.id]) {
                    npcMarkers[npc.id].setLatLng([npc.lat, npc.lng]);
                    var el = npcMarkers[npc.id].getElement();
                    if (el) {
                        var img = el.querySelector('img');
                        if (npc.type === 'CAR' && img) {
                            img.src = url;
                            // Teñimos solo el color mediante el hue del NPC
                            img.style.filter = 'sepia(100%) saturate(400%) hue-rotate(' + npc.hue + 'deg) brightness(1.0)';
                            img.style.width = sz + 'px';
                            img.style.height = sz + 'px';
                        } else if (img) {
                            img.style.transform = 'rotate(' + npc.rot + 'deg)';
                        }
                    }
                } else {
                    var html;
                    if (npc.type === 'CAR') {
                        var filterStyle = 'filter: sepia(100%) saturate(400%) hue-rotate(' + npc.hue + 'deg) brightness(1.0);';
                        html = '<div class="npc-c"><img class="car-img" src="'+url+'" width="'+sz+'" height="'+sz+'" style="'+filterStyle+'"></div>';
                    } else {
                        var pUrl = 'file:///android_asset/' + npc.drawable + '.svg';
                        html = '<div class="npc-c" style="transform:rotate('+npc.rot+'deg)"><img src="'+pUrl+'" width="24" height="24"></div>';
                    }
                    var icon = L.divIcon({ 
                        html: html, 
                        className: '', 
                        iconSize: [sz, sz], 
                        iconAnchor: [sz/2, sz/2] 
                    });
                    npcMarkers[npc.id] = L.marker([npc.lat, npc.lng], { icon: icon }).addTo(map);
                }
            });
        }
    </script>
</body>
</html>
""".trimIndent()