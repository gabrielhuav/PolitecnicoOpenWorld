package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updatePoliceSystem()
            kotlinx.coroutines.delay(1000) // Revisa cada 1 segundo
        }
    }

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
            // Renderizamos el mapa
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

                    // Actualizar/Agregar Policías
                    uiState.policeNPCs.forEach { police ->
                        val policeMarker = view.overlays.filterIsInstance<Marker>().find { it.id == police.id }
                            ?: Marker(view).apply {
                                id = police.id
                                title = "Policía"
                                icon = context.getDrawable(R.drawable.ic_police_car) // Debes agregar este recurso
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                view.overlays.add(this)
                            }
                        policeMarker.position = police.location
                        // Cambiar icono si está persiguiendo (opcional)
                        policeMarker.title = if (police.isChasing) "¡PERSECUCIÓN!" else "Patrulla"
                    }
                //Forzar el redibujado para ver el movimiento fluido
                view.invalidate()
                }
            )

            // CAPA DE CONTROLES (UI)
            // Usamos un Row para poner uno a cada lado automáticamente
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, // Manda el D-pad a la Izq y Letras a la Der
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cruceta Izquierda
                DPadController(
                    onDirectionPressed = { direction -> viewModel.moveCharacter(direction) }
                )

                // Botones Derecha
                ActionButtonsController(
                    onActionPressed = { action -> viewModel.executeAction(action) }
                )
            }
        }
    }
}