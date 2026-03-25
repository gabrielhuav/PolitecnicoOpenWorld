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
    onNavigateToMainMenu: () -> Unit = {} // <-- NUEVO PARÁMETRO para regresar al menú
) {
    val uiState by viewModel.uiState.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startGameLoop()
        onDispose { viewModel.stopGameLoop() }
    }

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
            // CAPA 1: EL MAPA Y SUS NPCs NATIVOS
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
                        uiState.currentLocation?.let { newLoc ->
                            view.controller.setCenter(newLoc)
                        }
                        if (view.zoomLevelDouble != uiState.zoomLevel) {
                            view.controller.setZoom(uiState.zoomLevel)
                        }

                        // --- LÓGICA DE NPCs PARA OSMDROID ---
                        // Mantenemos un caché de marcadores de NPCs asociado al MapView
                        @Suppress("UNCHECKED_CAST")
                        val npcMarkersCache = (view.tag as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also { view.tag = it }

                        // Conjunto de NPCs que siguen presentes en este frame
                        val activeNpcIds = mutableSetOf<String>()

                        // Actualizamos o creamos marcadores para los NPCs actuales
                        uiState.npcs.forEach { npc ->
                            val npcId = npc.id.toString()
                            activeNpcIds.add(npcId)

                            val npcMarker = npcMarkersCache[npcId] ?: Marker(view).apply {
                                title = "NPC_MARKER"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                npcMarkersCache[npcId] = this
                                view.overlays.add(this)
                            }

                            // Actualizamos siempre la posición, rotación e icono
                            npcMarker.position = npc.location
                            npcMarker.rotation = npc.rotationAngle

                            val iconResId = context.resources.getIdentifier(
                                npc.type.drawableName,
                                "drawable",
                                context.packageName
                            )
                            if (iconResId != 0) {
                                npcMarker.icon = ContextCompat.getDrawable(context, iconResId)
                            }
                        }

                        // Eliminamos marcadores de NPCs que ya no existen en el estado
                        val iterator = npcMarkersCache.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (entry.key !in activeNpcIds) {
                                view.overlays.remove(entry.value)
                                iterator.remove()
                            }
                        }
                        view.invalidate() // Forzamos el redibujado de marcadores
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
                            // Habilitamos acceso a archivos locales por si decides cargar imágenes PNG/SVG desde 'assets'
                            settings.allowFileAccess = true
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
                                        .npc-container { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
                                    </style>
                                </head>
                                <body>
                                    <div id="map"></div>
                                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                    <script>
                                        var map;
                                        var currentTileLayer;
                                        var npcMarkers = {}; // Diccionario para guardar marcadores activos
                                        
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

                                        // --- LÓGICA DE NPCs  ---
                                        function updateNpcs(npcsData) {
                                            var currentIds = new Set(npcsData.map(function(n) { return n.id; }));

                                            // 1. Eliminar NPCs que ya no existen o se alejaron
                                            for (var id in npcMarkers) {
                                                if (!currentIds.has(id)) {
                                                    map.removeLayer(npcMarkers[id]);
                                                    delete npcMarkers[id];
                                                }
                                            }

                                            // 2. Mover o crear NPCs
                                            npcsData.forEach(function(npc) {
                                                if (npcMarkers[npc.id]) {
                                                    // Si el NPC ya existe, actualizamos su posición y rotación
                                                    npcMarkers[npc.id].setLatLng([npc.lat, npc.lng]);
                                                    var el = npcMarkers[npc.id].getElement();
                                                    if (el && el.firstChild) {
                                                        el.firstChild.style.transform = 'rotate(' + npc.rot + 'deg)';
                                                    }
                                                } else {
                                                    // Si es un NPC nuevo, lo creamos

                                                    // puedes cambiar el <span> por <img src="file:///android_asset/" + npc.drawable + ".png">
                                                    var size = npc.type === 'CAR' ? 48 : 24; // Ajusta el tamaño en píxeles según necesites
                                                    // npc.drawable contiene el nombre que definiste en tu NpcType ("ic_npc_car" o "ic_npc_person")
                                                    var imageUrl = 'file:///android_asset/' + npc.drawable + '.svg';
                                                    
                                                    var htmlContent = '<div class="npc-container" style="transform: rotate(' + npc.rot + 'deg);">';
                                                    htmlContent += '<img src="' + imageUrl + '" width="' + size + '" height="' + size + '" style="display: block;">';
                                                    htmlContent += '</div>';

                                                    var icon = L.divIcon({
                                                        html: htmlContent,
                                                        className: '', // Vaciamos para no heredar estilos por defecto de leaflet
                                                        iconSize: [size, size],
                                                        iconAnchor: [size/2, size/2]
                                                    });

                                                    var marker = L.marker([npc.lat, npc.lng], {icon: icon}).addTo(map);
                                                    npcMarkers[npc.id] = marker;
                                                }
                                            });
                                        }
                                        
                                        initMap();
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()

                            // Usar base URL null permite que si luego cargas recursos locales, Leaflet no los bloquee por Cross-Origin
                            loadDataWithBaseURL(null, htmlData, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { webView ->
                        // Actualizar cámara
                        uiState.currentLocation?.let { newLoc ->
                            webView.evaluateJavascript("if(typeof moveMap === 'function') moveMap(${newLoc.latitude}, ${newLoc.longitude});", null)
                        }
                        webView.evaluateJavascript("if(typeof setMapZoom === 'function') setMapZoom(${uiState.zoomLevel});", null)

                        // Actualizar Tiles del mapa
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

                        // --- PUENTE KOTLIN -> JAVASCRIPT PARA NPCs ---
                        // Convertimos la lista de NPCs de Kotlin a un array JSON válido para inyectarlo
                        val npcsJsonArray = uiState.npcs.joinToString(prefix = "[", postfix = "]") { npc ->
                            "{ id: '${npc.id}', lat: ${npc.location.latitude}, lng: ${npc.location.longitude}, rot: ${npc.rotationAngle}, type: '${npc.type.name}', drawable: '${npc.type.drawableName}' }"
                        }
                        webView.evaluateJavascript("if(typeof updateNpcs === 'function') updateNpcs($npcsJsonArray);", null)
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

                androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.toggleSettingsDialog(false) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))
                                )
                            )
                            .border(2.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "AJUSTES",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )

                            // Título del Dropdown
                            Text(
                                text = "PROVEEDOR DE MAPA",
                                color = Color(0xFFD4AF37),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(bottom = 8.dp)
                            )

                            // Dropdown estilizado
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { isDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.White,
                                        containerColor = Color(0xFF2A1C21)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B1C3A)),
                                    contentPadding = PaddingValues(16.dp)
                                ) {
                                    Text(
                                        text = uiState.mapProvider.displayName,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar", tint = Color(0xFFD4AF37))
                                }

                                DropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.7f).background(Color(0xFF2A1C21))
                                ) {
                                    MapProvider.entries.forEach { provider ->
                                        DropdownMenuItem(
                                            text = { Text(provider.displayName, color = Color.White) },
                                            onClick = {
                                                viewModel.setMapProvider(provider)
                                                isDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Botón Regresar al Menú
                            Button(
                                onClick = {
                                    viewModel.toggleSettingsDialog(false)
                                    onNavigateToMainMenu()
                                },
                                shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD32F2F), // Rojo de advertencia para salir
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(text = "SALIR AL MENÚ", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Botón Continuar
                            Button(
                                onClick = { viewModel.toggleSettingsDialog(false) },
                                shape = androidx.compose.foundation.shape.CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF6B1C3A), // Guinda oficial
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            ) {
                                Text(text = "REANUDAR JUEGO", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}