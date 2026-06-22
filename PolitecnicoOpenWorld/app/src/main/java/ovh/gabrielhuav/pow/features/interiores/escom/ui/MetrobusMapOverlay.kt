package ovh.gabrielhuav.pow.features.interiores.escom.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.MetrobusInteriorState
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.MetrobusInteriorViewModel
import kotlin.math.min

private val MB_RED = Color(0xFFC21D24)
private val MB_RED_DARK = Color(0xFF8A0A0E)

@Composable
fun MetrobusMapOverlay(
    state: MetrobusInteriorState,
    viewModel: MetrobusInteriorViewModel,
    onTeleportToStation: (String, Float, Float) -> Unit,
    onExportGlobal: () -> Unit,
    onImportGlobal: () -> Unit
) {
    val context = LocalContext.current
    var mapBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) {
        mapBitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open("TRANSIT/METROBUS/mapa.png").use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A0505))
    ) {
        // Fondo degradado rojo oscuro (sin video para Metrobús)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF2D0808), Color(0xFF0D0202))
                    )
                )
        )

        if (mapBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .fillMaxHeight(0.72f)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(state.mapDesignerMode, state.mapDesignerMoveMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (state.mapDesignerMode && state.mapDesignerMoveMode && state.selectedGlobalWaypointIndex != -1) {
                                val nxDelta = pan.x / (size.width * scale)
                                val nyDelta = pan.y / (size.height * scale)
                                viewModel.moveSelectedGlobalWaypointBy(nxDelta, nyDelta)
                            } else {
                                offset += pan
                            }
                        }
                    }
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y
                        )
                ) {
                    val imgWidth = mapBitmap!!.width.toFloat()
                    val imgHeight = mapBitmap!!.height.toFloat()
                    val imgRatio = imgWidth / imgHeight
                    val viewRatio = maxWidth.value / maxHeight.value

                    val drawWidthDp = if (imgRatio > viewRatio) maxWidth else maxHeight * imgRatio
                    val drawHeightDp = if (imgRatio > viewRatio) maxWidth / imgRatio else maxHeight

                    Box(
                        modifier = Modifier
                            .size(drawWidthDp, drawHeightDp)
                            .align(Alignment.Center)
                    ) {
                        Image(
                            bitmap = mapBitmap!!,
                            contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_metrobus_map),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(state.mapDesignerMode) {
                                    if (state.mapDesignerMode) {
                                        detectDragGestures(
                                            onDragStart = { startOffset ->
                                                viewModel.selectGlobalWaypointAt(
                                                    startOffset.x / size.width,
                                                    startOffset.y / size.height
                                                )
                                            },
                                            onDrag = { change, _ ->
                                                if (state.selectedGlobalWaypointIndex != -1) {
                                                    change.consume()
                                                    viewModel.moveSelectedGlobalWaypointTo(
                                                        change.position.x / size.width,
                                                        change.position.y / size.height
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                                .pointerInput(state.mapDesignerMode) {
                                    detectTapGestures(
                                        onTap = { tapOffset ->
                                            val nx = tapOffset.x / size.width
                                            val ny = tapOffset.y / size.height
                                            if (state.mapDesignerMode) {
                                                viewModel.selectGlobalWaypointAt(nx, ny)
                                            } else {
                                                val hitDoor = state.globalWaypoints.firstOrNull {
                                                    nx in it.hitboxFrac.left..it.hitboxFrac.right &&
                                                    ny in it.hitboxFrac.top..it.hitboxFrac.bottom
                                                }
                                                if (hitDoor != null) {
                                                    onTeleportToStation(hitDoor.targetRoomId, state.playerX, state.playerY)
                                                }
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
                                val radius = min(width, height) / 4f
                                drawCircle(
                                    color = if (isSelected) Color.Yellow.copy(alpha = 0.9f) else Color(0xFFFF6B6B).copy(alpha = 0.85f),
                                    radius = radius,
                                    center = Offset(left + width / 2f, top + height / 2f)
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.6f),
                                    radius = radius * 0.5f,
                                    center = Offset(left + width / 2f, top + height / 2f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MB_RED
            )
        }

        // Encabezado del Metrobús
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .systemBarsPadding()
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(MB_RED, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_metrobus_select_dest),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Controles superiores derechos
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .systemBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.toggleMapDesignerMode() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.mapDesignerMode) MB_RED else Color.DarkGray
                )
            ) {
                Text(if (state.mapDesignerMode) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_designer_mode) else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_edit_map), color = Color.White)
            }

            IconButton(
                onClick = { viewModel.closeMetrobusMap() },
                modifier = Modifier.background(MB_RED_DARK, RoundedCornerShape(8.dp))
            ) {
                Icon(Icons.Default.Close, contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_close), tint = Color.White)
            }
        }

        // Toolbar de Diseñador
        if (state.mapDesignerMode) {
            var showAddDialog by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .systemBarsPadding()
                    .background(Color(0xBB000000), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.toggleMapDesignerMoveMode() },
                        modifier = Modifier.background(
                            if (state.mapDesignerMoveMode) MB_RED else Color.DarkGray, CircleShape
                        )
                    ) {
                        Icon(
                            imageVector = if (state.mapDesignerMoveMode) Icons.Default.PanTool else Icons.Default.TouchApp,
                            contentDescription = if (state.mapDesignerMoveMode) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_lock_map) else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_move_map),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_add_station), tint = Color.Green)
                    }
                    IconButton(onClick = { viewModel.deleteSelectedGlobalWaypoint() }) {
                        Icon(Icons.Default.Delete, contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_delete), tint = Color.Red)
                    }
                    IconButton(onClick = onImportGlobal) {
                        Icon(Icons.Default.Upload, contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_import), tint = Color.Cyan)
                    }
                    IconButton(onClick = onExportGlobal) {
                        Icon(Icons.Default.Download, contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_export), tint = Color.Cyan)
                    }
                }
                Button(
                    onClick = { viewModel.saveGlobalWaypoints() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MB_RED)
                ) {
                    Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_save_waypoints))
                }
            }

            if (showAddDialog) {
                AddMetrobusStationDialog(
                    state = state,
                    onDismiss = { showAddDialog = false },
                    onStationSelected = { stName ->
                        viewModel.addGlobalWaypoint(0.5f, 0.5f, stName)
                        showAddDialog = false
                    },
                    onSearch = { viewModel.updateMapSearchQuery(it) }
                )
            }
        }
    }
}

@Composable
private fun AddMetrobusStationDialog(
    state: MetrobusInteriorState,
    onDismiss: () -> Unit,
    onStationSelected: (String) -> Unit,
    onSearch: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E0B0B),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_metrobus_select_station),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.mapSearchQuery,
                    onValueChange = onSearch,
                    label = { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_search), color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFC21D24),
                        cursorColor = Color(0xFFC21D24)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                val filtered = state.allMetrobusStations.filter {
                    it.name.contains(state.mapSearchQuery, ignoreCase = true)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { station ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStationSelected(station.name) }
                                .padding(vertical = 10.dp, horizontal = 8.dp)
                        ) {
                            Text(station.name, color = Color.White, fontSize = 15.sp)
                        }
                        HorizontalDivider(color = Color(0xFF4A1C1C))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC21D24))
                ) {
                    Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.menu_cancel))
                }
            }
        }
    }
}
