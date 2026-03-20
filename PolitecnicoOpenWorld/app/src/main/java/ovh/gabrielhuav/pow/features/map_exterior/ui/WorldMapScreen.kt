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
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.ContextCompat
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
import org.osmdroid.views.overlay.Marker
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel

@Composable
fun WorldMapScreen(
    context: Context,
    viewModel: WorldMapViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Obtenemos la imagen original grande
    val originalDrawable = ContextCompat.getDrawable(context, ovh.gabrielhuav.pow.R.drawable.ic_car)

    //  Definimos el tamaño que queremos (por ejemplo, 100x100 pixeles es un buen tamaño inicial)
    //    Si los ves muy chicos o muy grandes, cambia este número 100 por otro.
    val tamanoDeseado = 30

    // Convertimos y ajustamos el tamaño
    val scaledCarDrawable = if (originalDrawable is BitmapDrawable) {
        val bitmap = originalDrawable.bitmap
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, tamanoDeseado, tamanoDeseado, true)
        BitmapDrawable(context.resources, scaledBitmap)
    } else {
        // Por si acaso no es un bitmap, devolvemos la original (poco probable)
        originalDrawable
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
                    // Actualizar la posición del jugador
                    uiState.currentLocation?.let { newLoc ->
                        val marker = view.overlays.filterIsInstance<Marker>().find { it.id == "PLAYER" }
                        marker?.position = newLoc
                        view.controller.animateTo(newLoc)
                    }

                    // Limpiar los carritos anteriores para no duplicarlos si te mueves
                    view.overlays.removeAll { it is Marker && it.id != "PLAYER" }

                    // Dibujar todos los NPCs (autos) que están en el estado
                    uiState.npcs.forEach { npc ->
                        val npcMarker = Marker(view).apply {
                            id = npc.id
                            position = npc.position
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            // Aquí mandamos a llamar a la imagen del carrito
                            icon = scaledCarDrawable
                            title = "Auto"
                        }
                        view.overlays.add(npcMarker)
                    }

                    // Refrescar el mapa para que aparezcan
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