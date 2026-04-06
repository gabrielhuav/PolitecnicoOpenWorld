package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel(),
    onNavigateToMainMenu: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startGameLoop()
        onDispose { viewModel.stopGameLoop() }
    }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {

        if (uiState.isLoadingLocation) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            Text("Iniciando mundo...",
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
            return@Box
        }

        // CAPA 1: MAPA
        if (uiState.mapProvider == MapProvider.OSM) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false)
                        setOnTouchListener { _, _ -> true }
                        isClickable = false
                        isFocusable = false
                        controller.setZoom(uiState.zoomLevel)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    uiState.currentLocation?.let { view.controller.setCenter(it) }
                    if (view.zoomLevelDouble != uiState.zoomLevel)
                        view.controller.setZoom(uiState.zoomLevel)

                    if (uiState.isRoadNetworkReady) {
                        @Suppress("UNCHECKED_CAST")
                        val cache = (view.tag as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also { view.tag = it }
                        val activeIds = mutableSetOf<String>()

                        uiState.npcs.forEach { npc ->
                            val id = npc.id.toString()
                            activeIds.add(id)
                            val marker = cache[id] ?: Marker(view).apply {
                                title = "NPC_MARKER"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                cache[id] = this
                                view.overlays.add(this)
                            }
                            marker.position = npc.location
                            marker.rotation = npc.rotationAngle
                            val resId = context.resources.getIdentifier(
                                npc.type.drawableName, "drawable", context.packageName)
                            if (resId != 0) marker.icon = ContextCompat.getDrawable(context, resId)
                        }

                        val iter = cache.entries.iterator()
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
                        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        webViewClient = WebViewClient()
                        val lat = uiState.currentLocation?.latitude ?: 0.0
                        val lng = uiState.currentLocation?.longitude ?: 0.0
                        val zoom = uiState.zoomLevel.toInt()
                        loadDataWithBaseURL(null, buildHtml(lat, lng, zoom), "text/html", "UTF-8", null)
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
                    val npcsJson = uiState.npcs.joinToString(prefix="[", postfix="]") { npc ->
                        "{id:'${npc.id}',lat:${npc.location.latitude},lng:${npc.location.longitude},rot:${npc.rotationAngle},type:'${npc.type.name}',drawable:'${npc.type.drawableName}'}"
                    }
                    wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs($npcsJson);", null)
                }
            )
        }

        // CAPA 2: JUGADOR (AHORA MUCHO MÁS PEQUEÑO Y PROPORCIONAL)
        Box(
            modifier = Modifier.align(Alignment.Center).size(22.dp) // Reducido de 42.dp
                .shadow(2.dp, CircleShape).clip(CircleShape)
                .background(Color.White).border(1.5.dp, Color(0xFFE6A800), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, "Jugador", tint = Color(0xFFFFC107),
                modifier = Modifier.size(14.dp)) // Reducido de 30.dp
        }

        // CAPA 3: INDICADOR DE CARGA
        if (!uiState.isRoadNetworkReady) {
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                    .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), Color(0xFFD4AF37), strokeWidth = 2.dp)
                Text("Cargando calles...", color = Color.White, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        // CAPA 4: BOTÓN DE AJUSTES
        IconButton(
            onClick = { viewModel.toggleSettingsDialog(true) },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                .background(Color.White.copy(alpha = 0.8f), CircleShape)
        ) { Icon(Icons.Default.Settings, "Ajustes", tint = Color.Black) }

        // ZOOM
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

        // D-PAD + BOTONES
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DPadController(onDirectionPressed = { viewModel.moveCharacter(it) })
            ActionButtonsController(onActionPressed = { viewModel.executeAction(it) })
        }

        // DIÁLOGO DE AJUSTES
        if (uiState.showSettingsDialog) {
            var expanded by remember { mutableStateOf(false) }
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.toggleSettingsDialog(false) }) {
                Box(modifier = Modifier.fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))))
                    .border(2.dp, Color(0xFFD4AF37).copy(alpha = 0.5f),
                        androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AJUSTES", fontSize = 24.sp, fontWeight = FontWeight.Black,
                            color = Color.White, letterSpacing = 2.sp,
                            modifier = Modifier.padding(bottom = 24.dp))
                        Text("PROVEEDOR DE MAPA", color = Color(0xFFD4AF37), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp))
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth(),
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
                                    DropdownMenuItem({ Text(p.displayName, color = Color.White) },
                                        { viewModel.setMapProvider(p); expanded = false })
                                }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = { viewModel.toggleSettingsDialog(false); onNavigateToMainMenu() },
                            shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFFD32F2F), Color.White),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("SALIR AL MENÚ", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.toggleSettingsDialog(false) },
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