package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap

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
            .navigationBarsPadding() // <- ESTO EVITA QUE CHOQUE CON LA BARRA DE ANDROID
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
            // Renderización condicional basada en el proveedor
            if (uiState.mapProvider == MapProvider.OSM) {
                // ====================
                // NATIVO: OSM DROID
                // ====================
                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(uiState.zoomLevel)

                            val playerMarker = Marker(this).apply {
                                id = "PLAYER"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "Tú"
                            }
                            overlays.add(playerMarker)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // 1. Actualizar Jugador
                        uiState.currentLocation?.let { newLoc ->
                            // Asumiendo que currentLocation es MapLocation, lo adaptamos a GeoPoint.
                            // Si en tu código sigue siendo GeoPoint, quita la conversión.
                            val lat = (newLoc as? GeoPoint)?.latitude ?: (newLoc as? ovh.gabrielhuav.pow.domain.models.MapLocation)?.latitude ?: 0.0
                            val lon = (newLoc as? GeoPoint)?.longitude ?: (newLoc as? ovh.gabrielhuav.pow.domain.models.MapLocation)?.longitude ?: 0.0
                            val geoPoint = GeoPoint(lat, lon)

                            val playerMarker = view.overlays.filterIsInstance<Marker>().find { it.id == "PLAYER" }
                            playerMarker?.position = geoPoint
                            view.controller.animateTo(geoPoint)
                        }

                        // 2. Limpiar NPCs anteriores (conservando al PLAYER)
                        view.overlays.removeAll { it is Marker && it.id != "PLAYER" }

                        // 3. Dibujar NPCs Actualizados
                        uiState.npcs.forEach { npc ->
                            val npcMarker = Marker(view).apply {
                                id = npc.id
                                position = GeoPoint(npc.currentLocation.latitude, npc.currentLocation.longitude)
                                title = npc.name
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                // OBTENER EL DIBUJO Y REDUCIR SU TAMAÑO (Cambia 60x60 por lo que prefieras)
                                val originalDrawable = ContextCompat.getDrawable(view.context, R.drawable.person_icon)
                                val scaledBitmap = originalDrawable?.toBitmap(width = 70, height = 70)
                                icon = BitmapDrawable(view.resources, scaledBitmap)
                            }
                            view.overlays.add(npcMarker)
                        }

                        // ... (código de los NPCs que ya tienes) ...

                        // 4. Limpiar Autos anteriores del mapa (para evitar que dejen una "estela")
                        view.overlays.removeAll { it is Marker && it.id.startsWith("car_") }

                        // 5. Dibujar Autos Actualizados
                        uiState.cars.forEach { car ->
                            val carMarker = Marker(view).apply {
                                id = car.id
                                // Transformamos tu MapLocation o GeoPoint a la posición del marcador
                                position = GeoPoint(car.currentLocation.latitude, car.currentLocation.longitude)
                                title = car.name

                                // A los autos les ponemos el ancla en el centro para que se vean bien en la calle
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)


                                val originalDrawable = ContextCompat.getDrawable(view.context, R.drawable.car_icon)

                                // Opcional: Escalar el auto si tu imagen original es muy grande
                                // Cambia el 80x80 por el tamaño que mejor se vea en tus calles
                                val scaledBitmap = originalDrawable?.toBitmap(width = 80, height = 80)
                                icon = BitmapDrawable(view.resources, scaledBitmap)
                            }
                            view.overlays.add(carMarker)
                        }

                        // Forzar el redibujado de la pantalla con los nuevos autos
                        view.invalidate()


                    }
                )
            } else {
                // ====================
                // WEB: GOOGLE MAPS
                // ====================
                Box(modifier = Modifier.fillMaxSize()) {
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

                                webChromeClient = android.webkit.WebChromeClient()
                                webViewClient = WebViewClient()
                                android.webkit.WebView.setWebContentsDebuggingEnabled(true)

                                // Compatibilidad para extraer Lat/Lng
                                val initialLat = (uiState.currentLocation as? GeoPoint)?.latitude
                                    ?: (uiState.currentLocation as? ovh.gabrielhuav.pow.domain.models.MapLocation)?.latitude
                                    ?: 0.0
                                val initialLng = (uiState.currentLocation as? GeoPoint)?.longitude
                                    ?: (uiState.currentLocation as? ovh.gabrielhuav.pow.domain.models.MapLocation)?.longitude
                                    ?: 0.0
                                val zoom = uiState.zoomLevel.toInt()

                                val htmlData = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="initial-scale=1.0, user-scalable=no" />
                                        <style> 
                                            body, html, #map { width: 100%; height: 100%; margin: 0; padding: 0; } 
                                        </style>
                                    </head>
                                    <body>
                                        <div id="map"></div>
                                        <script>
                                            window.alert = function() {}; 
                                            window.confirm = function() { return true; };
                                            
                                            var map;
                                            var npcMarkers = {}; // Almacén de NPCs
                                            
                                            function initMap() {
                                                map = new google.maps.Map(document.getElementById('map'), {
                                                    center: {lat: $initialLat, lng: $initialLng},
                                                    zoom: $zoom,
                                                    disableDefaultUI: true,
                                                    gestureHandling: 'none',
                                                    keyboardShortcuts: false,
                                                    mapTypeId: 'roadmap'
                                                });
                                            }
                                            
                                            function moveMap(lat, lng) {
                                                if(map) { map.panTo({lat: lat, lng: lng}); }
                                            }

                                            // FUNCIÓN PARA ACTUALIZAR NPCs DESDE COMPOSE
                                            function updateNpcs(npcsJson) {
                                                if(!map) return;
                                                var npcs = JSON.parse(npcsJson);
                                                var currentIds = {};
                                                
                                                npcs.forEach(function(npc) {
                                                    currentIds[npc.id] = true;
                                                    if(npcMarkers[npc.id]) {
                                                        // Mover el NPC si ya existe
                                                        npcMarkers[npc.id].setPosition({lat: npc.lat, lng: npc.lng});
                                                    } else {
                                                        // Crear el NPC si es nuevo (Usamos un circulo azul básico)
                                                        var marker = new google.maps.Marker({
                                                            position: {lat: npc.lat, lng: npc.lng},
                                                            map: map,
                                                            title: npc.name,
                                                            icon: {
                                                                path: google.maps.SymbolPath.CIRCLE,
                                                                scale: 6,
                                                                fillColor: '#0000FF',
                                                                fillOpacity: 1,
                                                                strokeColor: '#FFFFFF',
                                                                strokeWeight: 2
                                                            }
                                                        });
                                                        npcMarkers[npc.id] = marker;
                                                    }
                                                });
                                                
                                                // Eliminar NPCs que ya no están en la lista (despawn)
                                                for (var id in npcMarkers) {
                                                    if (!currentIds[id]) {
                                                        npcMarkers[id].setMap(null);
                                                        delete npcMarkers[id];
                                                    }
                                                }
                                            }
                                
                                            setInterval(function() {
                                                var btn = document.querySelector('.dismissButton');
                                                if (btn) { btn.click(); }
                                            }, 500);
                                        </script>
                                        <script src="https://maps.googleapis.com/maps/api/js?v=3.55&callback=initMap" async defer></script>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://example.com", htmlData, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            // 1. Mover el mapa (Jugador)
                            uiState.currentLocation?.let { newLoc ->
                                val lat = (newLoc as? GeoPoint)?.latitude ?: (newLoc as? ovh.gabrielhuav.pow.domain.models.MapLocation)?.latitude ?: 0.0
                                val lon = (newLoc as? GeoPoint)?.longitude ?: (newLoc as? ovh.gabrielhuav.pow.domain.models.MapLocation)?.longitude ?: 0.0
                                webView.evaluateJavascript("moveMap($lat, $lon)", null)
                            }

                            // 2. Mover NPCs inyectando JSON
                            if (uiState.npcs.isNotEmpty()) {
                                val npcsJson = uiState.npcs.joinToString(prefix = "[", postfix = "]") { npc ->
                                    "{ \"id\": \"${npc.id}\", \"lat\": ${npc.currentLocation.latitude}, \"lng\": ${npc.currentLocation.longitude}, \"name\": \"${npc.name}\" }"
                                }
                                webView.evaluateJavascript("if(typeof updateNpcs === 'function') { updateNpcs('$npcsJson'); }", null)
                            }
                        }
                    )
                    // Personaje fijo en el centro de la pantalla para la vista Web
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Personaje Central",
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ==========================================
            // CAPA DE CONTROLES (UI)
            // ==========================================

            // 1. Botón de Configuración (Arriba a la derecha)
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

            // 2. Controles de Movimiento y Acción (Abajo)
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
                AlertDialog(
                    onDismissRequest = { viewModel.toggleSettingsDialog(false) },
                    title = { Text(text = "Configuración del Juego") },
                    text = {
                        Column {
                            Text(text = "Motor de renderizado del mapa exterior:")
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { viewModel.toggleMapProvider() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val textoBoton = if (uiState.mapProvider == MapProvider.OSM) {
                                    "Cambiar a Google Maps (Web)"
                                } else {
                                    "Cambiar a OSMDroid (Nativo)"
                                }
                                Text(textoBoton)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Actual: ${uiState.mapProvider.name}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Gray
                            )
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