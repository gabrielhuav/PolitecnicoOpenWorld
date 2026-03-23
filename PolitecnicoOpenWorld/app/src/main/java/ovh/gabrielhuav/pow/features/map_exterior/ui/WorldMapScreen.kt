package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
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
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        if (uiState.isLoadingLocation) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            Text(
                text = "Iniciando mundo...",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        } else {
            // ==========================================
            // CAPA 1: EL MAPA
            // ==========================================
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
                        // 1. Actualizar Jugador
                        uiState.currentLocation?.let { newLoc ->
                            view.controller.setCenter(newLoc)
                        }
                        if (view.zoomLevelDouble != uiState.zoomLevel) {
                            view.controller.setZoom(uiState.zoomLevel)
                        }

                        // 2. Limpiar NPCs anteriores
                        view.overlays.removeAll { it is Marker && it.id != "PLAYER" && !(it.id?.startsWith("car_") ?: false) }

                        // 3. Dibujar NPCs Actualizados
                        uiState.npcs.forEach { npc ->
                            val npcMarker = Marker(view).apply {
                                id = npc.id
                                position = GeoPoint(npc.currentLocation.latitude, npc.currentLocation.longitude)
                                title = npc.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                val originalDrawable = ContextCompat.getDrawable(view.context, R.drawable.person_icon)
                                val scaledBitmap = originalDrawable?.toBitmap(width = 70, height = 70)
                                icon = BitmapDrawable(view.resources, scaledBitmap)
                            }
                            view.overlays.add(npcMarker)
                        }

                        // 4. Limpiar Autos anteriores del mapa
                        view.overlays.removeAll { it is Marker && it.id?.startsWith("car_") == true }

                        // 5. Dibujar Autos Actualizados
                        uiState.cars.forEach { car ->
                            val carMarker = Marker(view).apply {
                                id = car.id
                                position = GeoPoint(car.currentLocation.latitude, car.currentLocation.longitude)
                                title = car.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                                val originalDrawable = ContextCompat.getDrawable(view.context, R.drawable.car_icon)
                                val scaledBitmap = originalDrawable?.toBitmap(width = 80, height = 80)
                                icon = BitmapDrawable(view.resources, scaledBitmap)
                            }
                            view.overlays.add(carMarker)
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
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            webViewClient = WebViewClient()

                            val initialLat = uiState.currentLocation?.latitude ?: 0.0
                            val initialLng = uiState.currentLocation?.longitude ?: 0.0
                            val zoom = uiState.zoomLevel.toInt()

                            val htmlData = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="initial-scale=1.0, user-scalable=no" />
                                    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                    <style> 
                                        body, html, #map { width: 100%; height: 100%; margin: 0; padding: 0; background-color: #2b2b2b; } 
                                        .leaflet-control-attribution { display: none !important; }
                                    </style>
                                </head>
                                <body>
                                    <div id="map"></div>
                                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                    <script>
                                        var map;
                                        var currentTileLayer;
                                        var npcMarkers = {};
                                        var carMarkers = {};
                                        
                                        function initMap() {
                                            map = L.map('map', {
                                                center: [$initialLat, $initialLng],
                                                zoom: $zoom,
                                                zoomControl: false,       
                                                dragging: false,          
                                                keyboard: false,
                                                scrollWheelZoom: false,
                                                doubleClickZoom: false,
                                                touchZoom: false
                                            });
                                            
                                            currentTileLayer = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                                                maxZoom: 22,
                                                keepBuffer: 4 
                                            }).addTo(map);
                                        }
                                        
                                        function moveMap(lat, lng) {
                                            if(map) { 
                                                map.setView([lat, lng], map.getZoom(), {animate: false}); 
                                            }
                                        }

                                        function setMapZoom(newZoom) {
                                            if(map && map.getZoom() !== newZoom) {
                                                map.setZoom(newZoom, {animate: false});
                                            }
                                        }
                                        
                                        function changeTileUrl(newUrl) {
                                            if(currentTileLayer && currentTileLayer._url !== newUrl) {
                                                currentTileLayer.setUrl(newUrl);
                                            }
                                        }

                                        function updateNpcs(npcsJson) {
                                            if(!map) return;
                                            var npcs = JSON.parse(npcsJson);
                                            var currentIds = {};
                                            
                                            npcs.forEach(function(npc) {
                                                currentIds[npc.id] = true;
                                                if(npcMarkers[npc.id]) {
                                                    npcMarkers[npc.id].setLatLng([npc.lat, npc.lng]);
                                                } else {
                                                    var marker = L.circleMarker([npc.lat, npc.lng], {
                                                        radius: 5,
                                                        fillColor: '#3498DB',
                                                        color: '#FFFFFF',
                                                        weight: 2,
                                                        opacity: 1,
                                                        fillOpacity: 1
                                                    }).addTo(map);
                                                    marker.bindTooltip(npc.name);
                                                    npcMarkers[npc.id] = marker;
                                                }
                                            });
                                            
                                            for (var id in npcMarkers) {
                                                if (!currentIds[id]) {
                                                    map.removeLayer(npcMarkers[id]);
                                                    delete npcMarkers[id];
                                                }
                                            }
                                        }

                                        function updateCars(carsJson) {
                                            if(!map) return;
                                            var cars = JSON.parse(carsJson);
                                            var currentIds = {};
                                            
                                            cars.forEach(function(car) {
                                                currentIds[car.id] = true;
                                                if(carMarkers[car.id]) {
                                                    carMarkers[car.id].setLatLng([car.lat, car.lng]);
                                                } else {
                                                    var marker = L.circleMarker([car.lat, car.lng], {
                                                        radius: 7,
                                                        fillColor: '#E74C3C',
                                                        color: '#FFFFFF',
                                                        weight: 2,
                                                        opacity: 1,
                                                        fillOpacity: 1
                                                    }).addTo(map);
                                                    marker.bindTooltip(car.name);
                                                    carMarkers[car.id] = marker;
                                                }
                                            });
                                            
                                            for (var id in carMarkers) {
                                                if (!currentIds[id]) {
                                                    map.removeLayer(carMarkers[id]);
                                                    delete carMarkers[id];
                                                }
                                            }
                                        }
                                        
                                        initMap();
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()

                            loadDataWithBaseURL("https://example.com", htmlData, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { webView ->
                        uiState.currentLocation?.let { newLoc ->
                            webView.evaluateJavascript("if(typeof moveMap === 'function') moveMap(${newLoc.latitude}, ${newLoc.longitude});", null)
                        }
                        webView.evaluateJavascript("if(typeof setMapZoom === 'function') setMapZoom(${uiState.zoomLevel});", null)

                        // Mover NPCs
                        val npcsJson = uiState.npcs.joinToString(prefix = "[", postfix = "]") { npc ->
                            "{ \"id\": \"${npc.id}\", \"lat\": ${npc.currentLocation.latitude}, \"lng\": ${npc.currentLocation.longitude}, \"name\": \"${npc.name}\" }"
                        }
                        webView.evaluateJavascript("if(typeof updateNpcs === 'function') { updateNpcs('$npcsJson'); }", null)

                        // Mover Autos
                        val carsJson = uiState.cars.joinToString(prefix = "[", postfix = "]") { car ->
                            "{ \"id\": \"${car.id}\", \"lat\": ${car.currentLocation.latitude}, \"lng\": ${car.currentLocation.longitude}, \"name\": \"${car.name}\" }"
                        }
                        webView.evaluateJavascript("if(typeof updateCars === 'function') { updateCars('$carsJson'); }", null)

                        val tileUrl = when (uiState.mapProvider) {
                            MapProvider.CARTO_DB_DARK -> "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                            MapProvider.CARTO_DB_LIGHT -> "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
                            MapProvider.ESRI -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
                            MapProvider.ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                            MapProvider.OPEN_TOPO -> "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
                            MapProvider.OSM_WEB -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                            else -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                        }
                        webView.evaluateJavascript("if(typeof changeTileUrl === 'function') changeTileUrl('$tileUrl');", null)
                    }
                )
            }

            // ==========================================
            // CAPA 2: JUGADOR (Hombrecito Amarillo)
            // ==========================================
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, Color(0xFFE6A800), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Pegman Player",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(30.dp)
                )
            }

            // ==========================================
            // CAPA 3: CONTROLES DE UI
            // ==========================================
            IconButton(
                onClick = { viewModel.toggleSettingsDialog(true) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Ajustes",
                    tint = Color.Black
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = { viewModel.zoomIn() },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(48.dp)
                ) {
                    Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
                IconButton(
                    onClick = { viewModel.zoomOut() },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(48.dp)
                ) {
                    Text("-", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DPadController(
                    onDirectionPressed = { direction -> viewModel.moveCharacter(direction) }
                )
                ActionButtonsController(
                    onActionPressed = { action -> viewModel.executeAction(action) }
                )
            }

            // ==========================================
            // DIÁLOGO DE CONFIGURACIÓN (MENÚ DESPLEGABLE)
            // ==========================================
            if (uiState.showSettingsDialog) {
                var isDropdownExpanded by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { viewModel.toggleSettingsDialog(false) },
                    title = { Text(text = "Ajustes del Juego") },
                    text = {
                        Column {
                            Text("Proveedor de Mapa exterior:", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(8.dp))

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { isDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Text(
                                        text = uiState.mapProvider.displayName,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar")
                                }

                                DropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    MapProvider.entries.forEach { provider ->
                                        DropdownMenuItem(
                                            text = { Text(provider.displayName) },
                                            onClick = {
                                                viewModel.setMapProvider(provider)
                                                isDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.toggleSettingsDialog(false) }) {
                            Text("Cerrar")
                        }
                    }
                )
            }
        }
    }
}