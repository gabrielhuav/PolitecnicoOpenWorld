package ovh.gabrielhuav.pow.features.interior.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneDoor
import ovh.gabrielhuav.pow.features.interior.viewmodel.MetroInteriorState
import ovh.gabrielhuav.pow.features.interior.viewmodel.MetroInteriorViewModel
import kotlin.math.roundToInt

@Composable
fun MetroMapOverlay(
    state: MetroInteriorState,
    viewModel: MetroInteriorViewModel,
    onTeleportToStation: (String) -> Unit
) {
    val context = LocalContext.current
    var mapBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Cargar mapa
    LaunchedEffect(Unit) {
        mapBitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open("metroCDMX/mapa.png").use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        if (mapBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset += pan
                        }
                    }
            ) {
                // Contenedor gráfico para el zoom y pan
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                ) {
                    Image(
                        bitmap = mapBitmap!!,
                        contentDescription = "Mapa del Metro",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Renderizamos waypoints sobre un Canvas que comparta las mismas transformaciones
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(state.mapDesignerMode) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    if (state.mapDesignerMode && state.selectedGlobalWaypointIndex != -1) {
                                        change.consume()
                                        // Calcular coordenadas normalizadas
                                        val nx = (change.position.x) / size.width
                                        val ny = (change.position.y) / size.height
                                        viewModel.moveSelectedGlobalWaypointTo(nx, ny)
                                    }
                                }
                            )
                        }
                ) {
                    state.globalWaypoints.forEachIndexed { index, wp ->
                        val isSelected = state.selectedGlobalWaypointIndex == index
                        val r = wp.hitboxFrac
                        val left = r.left * size.width
                        val top = r.top * size.height
                        val width = (r.right - r.left) * size.width
                        val height = (r.bottom - r.top) * size.height

                        drawRect(
                            color = if (isSelected) Color.Yellow.copy(alpha = 0.6f) else Color.Cyan.copy(alpha = 0.6f),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                    }
                }

                // Detector de taps (separado de drag) para no interferir
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(state.mapDesignerMode) {
                            detectTapGestures(
                                onTap = { tapOffset ->
                                    val nx = tapOffset.x / size.width
                                    val ny = tapOffset.y / size.height
                                    if (state.mapDesignerMode) {
                                        viewModel.selectGlobalWaypointAt(nx, ny)
                                    } else {
                                        // Teletransportar si tap en un waypoint
                                        val hitDoor = state.globalWaypoints.firstOrNull {
                                            nx in it.hitboxFrac.left..it.hitboxFrac.right &&
                                            ny in it.hitboxFrac.top..it.hitboxFrac.bottom
                                        }
                                        if (hitDoor != null) {
                                            onTeleportToStation(hitDoor.targetRoomId)
                                        }
                                    }
                                }
                            )
                        }
                )
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }

        // Toolbar normal (Cerrar mapa)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .systemBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.toggleMapDesignerMode() },
                colors = ButtonDefaults.buttonColors(containerColor = if (state.mapDesignerMode) Color(0xFFF07B00) else Color.DarkGray)
            ) {
                Text(if (state.mapDesignerMode) "Modo Diseñador" else "Editar Mapa", color = Color.White)
            }
            
            IconButton(
                onClick = { viewModel.closeMetroMap() },
                modifier = Modifier.background(Color.Red, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }

        // Toolbar de Diseñador
        if (state.mapDesignerMode) {
            var showAddDialog by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .systemBarsPadding()
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Añadir Estación", tint = Color.Green)
                }
                IconButton(onClick = { viewModel.deleteSelectedGlobalWaypoint() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
                }
                Button(onClick = { viewModel.saveGlobalWaypoints() }) {
                    Text("Guardar")
                }
            }

            if (showAddDialog) {
                AddStationDialog(
                    state = state,
                    onDismiss = { showAddDialog = false },
                    onStationSelected = { stationName ->
                        // Añadir waypoint al centro de la vista (aprox 0.5f, 0.5f)
                        viewModel.addGlobalWaypoint(0.5f, 0.5f, stationName)
                        showAddDialog = false
                    },
                    onSearch = { viewModel.updateMapSearchQuery(it) }
                )
            }
        }
    }
}

@Composable
fun AddStationDialog(
    state: MetroInteriorState,
    onDismiss: () -> Unit,
    onStationSelected: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E24),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Seleccionar Estación", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = state.mapSearchQuery,
                    onValueChange = onSearch,
                    label = { Text("Buscar", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF07B00),
                        cursorColor = Color(0xFFF07B00)
                    )
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val filtered = state.allMetroStations.filter { 
                    it.name.contains(state.mapSearchQuery, ignoreCase = true) 
                }
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { station ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStationSelected(station.name) }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        ) {
                            Text(station.name, color = Color.White, fontSize = 16.sp)
                        }
                        HorizontalDivider(color = Color.DarkGray)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}
