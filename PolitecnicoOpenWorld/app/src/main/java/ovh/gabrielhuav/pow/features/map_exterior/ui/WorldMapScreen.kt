package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

// Función que crea el marcador del jugador
fun createPlayerIcon(context: Context, sizePx: Int): Drawable {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.parseColor("#4CAF50")
    paint.style = Paint.Style.FILL
    val radius = sizePx / 2f
    canvas.drawCircle(radius, radius, radius, paint)

    paint.color = android.graphics.Color.WHITE
    paint.style = Paint.Style.STROKE
    val strokeWidth = (sizePx * 0.05f).coerceAtLeast(1f)
    paint.strokeWidth = strokeWidth
    canvas.drawCircle(radius, radius, radius - strokeWidth / 2f, paint)

    paint.style = Paint.Style.FILL
    val headRadius = sizePx * 0.15f
    canvas.drawCircle(sizePx / 2f, sizePx * 0.32f, headRadius, paint)

    val bodyPath = Path()
    bodyPath.moveTo(sizePx * 0.25f, sizePx * 0.75f)
    bodyPath.lineTo(sizePx * 0.35f, sizePx * 0.52f)
    bodyPath.quadTo(sizePx / 2f, sizePx * 0.42f, sizePx * 0.65f, sizePx * 0.52f)
    bodyPath.lineTo(sizePx * 0.75f, sizePx * 0.75f)
    bodyPath.close()
    canvas.drawPath(bodyPath, paint)

    return BitmapDrawable(context.resources, bitmap)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var lastCenterTriggerOSM by remember { mutableStateOf(0L) }
    var lastLocationOSM by remember { mutableStateOf<GeoPoint?>(null) }

    var lastCenterTriggerWeb by remember { mutableStateOf(0L) }
    var lastLocationWeb by remember { mutableStateOf<GeoPoint?>(null) }

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
            // CAPA 1: EL MAPA Y EL JUGADOR INTEGRADO
            // ==========================================
            if (uiState.mapProvider == MapProvider.OSM) {
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(uiState.zoomLevel)

                            val marker = Marker(this)
                            marker.id = "player"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                            val density = ctx.resources.displayMetrics.density
                            val basePx = 42f * density
                            val minPx = (28f * density).toInt() // Límite mínimo (al alejar)
                            val maxPx = (56f * density).toInt() // Límite máximo (al acercar)

                            // Escala suavizada (1.2 en lugar de 2.0) y con topes estrictos
                            val initialSize = (basePx * Math.pow(1.2, uiState.zoomLevel - 18.0))
                                .toInt()
                                .coerceIn(minPx, maxPx)

                            marker.icon = createPlayerIcon(ctx, initialSize)

                            uiState.currentLocation?.let { marker.position = it }
                            this.overlays.add(marker)

                            this.addMapListener(object : org.osmdroid.events.MapListener {
                                override fun onScroll(event: org.osmdroid.events.ScrollEvent) = false
                                override fun onZoom(event: org.osmdroid.events.ZoomEvent): Boolean {
                                    val currentZoom = event.zoomLevel
                                    val newSize = (basePx * Math.pow(1.2, currentZoom - 18.0))
                                        .toInt()
                                        .coerceIn(minPx, maxPx)

                                    if (Math.abs((marker.icon?.intrinsicWidth ?: 0) - newSize) > 2) {
                                        marker.icon = createPlayerIcon(ctx, newSize)
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                        this@apply.invalidate()
                                    }
                                    return false
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val playerMarker = view.overlays.find { it is Marker && it.id == "player" } as? Marker
                        playerMarker?.let { marker ->
                            if (marker.position != uiState.currentLocation) {
                                uiState.currentLocation?.let { marker.position = it }
                                view.invalidate()
                            }
                        }

                        if (lastCenterTriggerOSM != uiState.centerTrigger) {
                            uiState.currentLocation?.let { view.controller.animateTo(it) }
                            lastCenterTriggerOSM = uiState.centerTrigger
                        } else if (lastLocationOSM != uiState.currentLocation) {
                            uiState.currentLocation?.let { view.controller.animateTo(it) }
                            lastLocationOSM = uiState.currentLocation
                        }

                        if (view.zoomLevelDouble != uiState.zoomLevel) {
                            view.controller.setZoom(uiState.zoomLevel)
                        }
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
                                        
                                        .player-marker {
                                            border-radius: 50%;
                                            background-color: #4CAF50;
                                            border: 2px solid white;
                                            box-shadow: 0 4px 8px rgba(0,0,0,0.4);
                                            display: flex;
                                            align-items: center;
                                            justify-content: center;
                                            box-sizing: border-box;
                                        }
                                        .player-marker svg {
                                            width: 60%;
                                            height: 60%;
                                            fill: white;
                                        }
                                    </style>
                                </head>
                                <body>
                                    <div id="map"></div>
                                    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                    <script>
                                        var map;
                                        var currentTileLayer;
                                        var playerMarker;
                                        
                                        var personSvg = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>';

                                        function updatePlayerMarkerSize(currentZoom) {
                                            if(!playerMarker) return;
                                            var baseSize = 42;
                                            var scale = Math.pow(1.2, currentZoom - 18); // Escala suave
                                            var newSize = Math.max(28, Math.min(baseSize * scale, 56)); // Límites: Min 28px, Max 56px
                                            
                                            var newIcon = L.divIcon({
                                                className: 'player-marker',
                                                html: personSvg,
                                                iconSize: [newSize, newSize],
                                                iconAnchor: [newSize/2, newSize/2]
                                            });
                                            playerMarker.setIcon(newIcon);
                                        }

                                        function initMap() {
                                            map = L.map('map', {
                                                center: [$initialLat, $initialLng],
                                                zoom: $zoom,
                                                zoomControl: false,       
                                                dragging: true,          
                                                keyboard: false,
                                                scrollWheelZoom: true,   
                                                doubleClickZoom: true,
                                                touchZoom: true          
                                            });
                                            
                                            currentTileLayer = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
                                                maxZoom: 22,
                                                keepBuffer: 4 
                                            }).addTo(map);
                                            
                                            var baseSize = 42;
                                            var scale = Math.pow(1.2, $zoom - 18);
                                            var initialSize = Math.max(28, Math.min(baseSize * scale, 56));

                                            var initialIcon = L.divIcon({
                                                className: 'player-marker',
                                                html: personSvg,
                                                iconSize: [initialSize, initialSize],
                                                iconAnchor: [initialSize/2, initialSize/2]
                                            });
                                            
                                            playerMarker = L.marker([$initialLat, $initialLng], {icon: initialIcon}).addTo(map);

                                            map.on('zoom', function() {
                                                updatePlayerMarkerSize(map.getZoom());
                                            });
                                        }
                                        
                                        function moveMap(lat, lng) {
                                            if(map) { 
                                                map.setView([lat, lng], map.getZoom(), {animate: false}); 
                                            }
                                        }
                                        
                                        function updatePlayerMarkerPosition(lat, lng) {
                                            if(playerMarker) {
                                                playerMarker.setLatLng([lat, lng]);
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
                        val newLoc = uiState.currentLocation ?: return@AndroidView

                        webView.evaluateJavascript("if(typeof updatePlayerMarkerPosition === 'function') updatePlayerMarkerPosition(${newLoc.latitude}, ${newLoc.longitude});", null)

                        if (lastCenterTriggerWeb != uiState.centerTrigger) {
                            webView.evaluateJavascript("if(typeof moveMap === 'function') moveMap(${newLoc.latitude}, ${newLoc.longitude});", null)
                            lastCenterTriggerWeb = uiState.centerTrigger
                        } else if (lastLocationWeb != uiState.currentLocation) {
                            webView.evaluateJavascript("if(typeof moveMap === 'function') moveMap(${newLoc.latitude}, ${newLoc.longitude});", null)
                            lastLocationWeb = uiState.currentLocation
                        }

                        webView.evaluateJavascript("if(typeof setMapZoom === 'function') setMapZoom(${uiState.zoomLevel});", null)

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
                    onClick = { viewModel.centerOnPlayer() },
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.8f), CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Centrar en Jugador",
                        tint = Color.Black
                    )
                }

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
            // DIÁLOGO DE CONFIGURACIÓN
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