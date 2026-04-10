package ovh.gabrielhuav.pow.features.map_exterior.ui

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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel(factory = WorldMapViewModel.Factory(context)),
    onNavigateToMainMenu: () -> Unit = {}
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

        // ─���─ CAPA 1: MAPA ────────────────────────────────────────────────────────
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
                                val resId = context.resources.getIdentifier(
                                    npc.type.drawableName, "drawable", context.packageName)
                                if (resId != 0) icon = ContextCompat.getDrawable(context, resId)
                                markerCache[id] = this; view.overlays.add(this)
                            }
                            marker.position = npc.location; marker.rotation = npc.rotationAngle
                        }
                        val iter = markerCache.entries.iterator()
                        while (iter.hasNext()) {
                            val e = iter.next()
                            if (e.key !in activeIds) { view.overlays.remove(e.value); iter.remove() }
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
                    val npcsJson = uiState.npcs.joinToString(prefix = "[", postfix = "]") { npc ->
                        "{id:'${npc.id}',lat:${npc.location.latitude},lng:${npc.location.longitude}," +
                                "rot:${npc.rotationAngle},type:'${npc.type.name}',drawable:'${npc.type.drawableName}'}"
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
            onClick = { viewModel.toggleSettingsDialog(true) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black) }

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

        // ─── CAPA 7: D-PAD ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DPadController(onDirectionPressed = { viewModel.moveCharacter(it) })
            ActionButtonsController(onActionPressed = { viewModel.executeAction(it) })
        }

        // ─── DIÁLOGO DE AJUSTES IN-GAME ───────────────────────────────────────────────────
        if (uiState.showSettingsDialog) {
            var expanded by remember { mutableStateOf(false) }
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.toggleSettingsDialog(false) }) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))))
                        .border(2.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AJUSTES", fontSize = 24.sp, fontWeight = FontWeight.Black,
                            color = Color.White, letterSpacing = 2.sp,
                            modifier = Modifier.padding(bottom = 24.dp))

                        // ── Selector de proveedor de mapa ──────────────────────
                        Text("PROVEEDOR DE MAPA", color = Color(0xFFD4AF37), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp))
                        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White, containerColor = Color(0xFF2A1C21)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B1C3A)),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Text(uiState.mapProvider.displayName, Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFD4AF37))
                            }
                            DropdownMenu(expanded, { expanded = false },
                                Modifier.fillMaxWidth(0.7f).background(Color(0xFF2A1C21))
                            ) {
                                MapProvider.entries.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.displayName, color = Color.White) },
                                        onClick = { viewModel.setMapProvider(p); expanded = false }
                                    )
                                }
                            }
                        }

                        // ── Toggle widget de caché ──────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A1C21))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Widget de caché", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Muestra fuente de datos", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = uiState.showCacheWidget,
                                onCheckedChange = { viewModel.updateShowCacheWidget(it) }, // Agregaremos esta función al VM abajo
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // ── Toggle widget de FPS ──────────────────────────────
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A1C21))
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Widget de FPS", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Mide el rendimiento gráfico", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = uiState.showFpsWidget,
                                onCheckedChange = { viewModel.updateShowFpsWidget(it) }, // Agregaremos esta función al VM abajo
                                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { viewModel.toggleSettingsDialog(false); onNavigateToMainMenu() },
                            shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFFD32F2F), Color.White),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("SALIR AL MENÚ", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.toggleSettingsDialog(false) },
                            shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFF6B1C3A), Color.White),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("REANUDAR JUEGO", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    }
                }
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

private fun buildHtml(lat: Double, lng: Double, zoom: Int) = """
<!DOCTYPE html><html>
<head>
    <meta name="viewport" content="initial-scale=1.0,user-scalable=no"/>
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
    <style>body,html,#map{width:100%;height:100%;margin:0;padding:0;background:#2b2b2b;}
    .leaflet-control-attribution{display:none!important;}
    .npc-c{width:100%;height:100%;display:flex;align-items:center;justify-content:center;}</style>
</head>
<body>
<div id="map"></div>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
var map,currentTileLayer,npcMarkers={},roadNetworkReady=false;
function initMap(){
    map=L.map('map',{center:[$lat,$lng],zoom:$zoom,zoomControl:false,dragging:false,
        keyboard:false,scrollWheelZoom:false,doubleClickZoom:false,touchZoom:false});
    currentTileLayer=L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}',
        {maxZoom:22,keepBuffer:4}).addTo(map);
}
function moveMap(lat,lng){if(map)map.setView([lat,lng],map.getZoom(),{animate:false});}
function setMapZoom(z){if(map&&map.getZoom()!==z)map.setZoom(z,{animate:false});}
function changeTileUrl(url){if(currentTileLayer&&currentTileLayer._url!==url)currentTileLayer.setUrl(url);}
function setRoadNetworkReady(r){roadNetworkReady=r;}
function updateNpcs(data){
    if(!roadNetworkReady)return;
    var ids=new Set(data.map(function(n){return n.id;}));
    for(var id in npcMarkers){if(!ids.has(id)){map.removeLayer(npcMarkers[id]);delete npcMarkers[id];}}
    data.forEach(function(npc){
        if(npcMarkers[npc.id]){
            npcMarkers[npc.id].setLatLng([npc.lat,npc.lng]);
            var el=npcMarkers[npc.id].getElement();
            if(el&&el.firstChild)el.firstChild.style.transform='rotate('+npc.rot+'deg)';
        }else{
            var sz=npc.type==='CAR'?48:24;
            var url='file:///android_asset/'+npc.drawable+'.svg';
            var html='<div class="npc-c" style="transform:rotate('+npc.rot+'deg)"><img src="'+url+'" width="'+sz+'" height="'+sz+'" style="display:block"></div>';
            var icon=L.divIcon({html:html,className:'',iconSize:[sz,sz],iconAnchor:[sz/2,sz/2]});
            npcMarkers[npc.id]=L.marker([npc.lat,npc.lng],{icon:icon}).addTo(map);
        }
    });
}
initMap();
</script>
</body></html>
""".trimIndent()