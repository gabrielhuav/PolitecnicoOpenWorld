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
                        uiState.currentLocation?.let { newLoc ->
                            val marker = view.overlays.filterIsInstance<Marker>().find { it.id == "PLAYER" }
                            marker?.position = newLoc
                            view.controller.animateTo(newLoc)
                        }
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
                                // 1. Forzar medidas explícitas para evitar que el WebView mida 0x0
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                // 2. Forzar aceleración por hardware para evitar pantallas blancas en el emulador
                                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                webChromeClient = android.webkit.WebChromeClient()
                                webViewClient = WebViewClient()
                                android.webkit.WebView.setWebContentsDebuggingEnabled(true)

                                val initialLat = uiState.currentLocation?.latitude ?: 0.0
                                val initialLng = uiState.currentLocation?.longitude ?: 0.0
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
                                
                                            // EL TRUCO DEFINITIVO: Clic automático en el botón "Aceptar"
                                            setInterval(function() {
                                                var btn = document.querySelector('.dismissButton');
                                                if (btn) {
                                                    btn.click();
                                                }
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
                            uiState.currentLocation?.let { newLoc ->
                                webView.evaluateJavascript("moveMap(${newLoc.latitude}, ${newLoc.longitude})", null)
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