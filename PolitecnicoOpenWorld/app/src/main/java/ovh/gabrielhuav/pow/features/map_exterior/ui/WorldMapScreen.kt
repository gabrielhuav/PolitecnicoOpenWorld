package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoadingLocation) {
            // Pantalla de carga mientras obtenemos el GPS
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = "Buscando señal GPS...",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        } else {
            // Renderizamos el mapa de OSMDroid
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)

                        // Configuración inicial de cámara
                        controller.setZoom(uiState.zoomLevel)
                        uiState.currentLocation?.let { controller.setCenter(it) }

                        // Capa para mostrar el "hombrecito" (Ubicación del usuario)
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                        locationOverlay.enableMyLocation()
                        locationOverlay.enableFollowLocation() // Hace que la cámara siga al usuario
                        overlays.add(locationOverlay)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // Si cambia la ubicación en el ViewModel, movemos el mapa
                    uiState.currentLocation?.let {
                        view.controller.animateTo(it)
                    }
                }
            )
        }
    }
}