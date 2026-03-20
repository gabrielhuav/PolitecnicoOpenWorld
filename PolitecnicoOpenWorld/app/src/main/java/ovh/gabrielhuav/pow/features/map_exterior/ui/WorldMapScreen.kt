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

    // Obtenemos las imágenes originales
    val originalCar1 = ContextCompat.getDrawable(context, ovh.gabrielhuav.pow.R.drawable.ic_car)
    val originalCar2 = ContextCompat.getDrawable(context, ovh.gabrielhuav.pow.R.drawable.ic_car2)
    val originalBus = ContextCompat.getDrawable(context, ovh.gabrielhuav.pow.R.drawable.ic_bus) // <-- EL AUTOBÚS

    // Función modificada para permitir diferentes tamaños
    fun scaleImage(drawable: android.graphics.drawable.Drawable?, tamano: Int): android.graphics.drawable.Drawable? {
        return if (drawable is BitmapDrawable) {
            val bitmap = drawable.bitmap
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, tamano, tamano, true)
            BitmapDrawable(context.resources, scaledBitmap)
        } else drawable
    }

    val scaledCar1 = scaleImage(originalCar1, 30) // Autos a 30px
    val scaledCar2 = scaleImage(originalCar2, 30)
    val scaledBus = scaleImage(originalBus, 45)  // <-- Autobuses un poco más grandes (45px)

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
                        val playerMarker = view.overlays.filterIsInstance<Marker>().find { it.id == "PLAYER" }

                        // ¿La posición guardada en el dibujo es diferente a la nueva del estado?
                        // (Es decir, ¿el jugador dio un paso o apenas apareció?)
                        if (playerMarker?.position?.latitude != newLoc.latitude || playerMarker?.position?.longitude != newLoc.longitude) {

                            playerMarker?.position = newLoc
                            // Usamos animateTo para que la cámara siga al jugador suavemente cuando camina
                            view.controller.setCenter(newLoc)
                        }
                    }

                    uiState.npcs.forEach { npc ->
                        // Buscamos si el auto ya está dibujado en el mapa
                        val existingMarker = view.overlays.filterIsInstance<Marker>().find { it.id == npc.id }

                        if (existingMarker != null) {
                            // Si ya existe, SOLO le cambiamos las coordenadas y la rotación (súper rápido)
                            existingMarker.position = npc.position
                            existingMarker.rotation = npc.rotation
                        } else {
                            // Si no existe (es el primer segundo del juego), lo creamos
                            val npcMarker = Marker(view).apply {
                                id = npc.id
                                position = npc.position
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                // Elegimos la imagen según el tipo (1=Auto1, 2=Auto2, 3=Autobús)
                                icon = when (npc.spriteType) {
                                    3 -> scaledBus
                                    2 -> scaledCar2
                                    else -> scaledCar1
                                }
                                rotation = npc.rotation
                                title = "Auto"
                            }
                            view.overlays.add(npcMarker)
                        }
                    }

                    // Limpieza de seguridad: Borrar marcadores de autos que ya terminaron su ruta
                    val currentNpcIds = uiState.npcs.map { it.id }
                    view.overlays.removeAll { it is Marker && it.id != "PLAYER" && it.id !in currentNpcIds }

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