package ovh.gabrielhuav.pow.features.interiores.escom.ui

import android.net.Uri
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.graphics.SurfaceTexture
import android.graphics.Matrix
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.foundation.shape.CircleShape
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
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.MetroInteriorState
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.MetroInteriorViewModel
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MetroMapOverlay(
    state: MetroInteriorState,
    viewModel: MetroInteriorViewModel,
    onTeleportToStation: (String, Float, Float) -> Unit,
    onExportGlobal: () -> Unit,
    onImportGlobal: () -> Unit
) {
    val context = LocalContext.current
    var mapBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Cargar mapa
    LaunchedEffect(Unit) {
        mapBitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open("TRANSIT/METRO/map.png").use {
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
            .background(Color.Black)
    ) {
        // Reproductor de video inmersivo de fondo
        LoopingVideoPlayer(
            assetFileName = "TRANSIT/METRO/video.mp4",
            modifier = Modifier.fillMaxSize()
        )

        if (mapBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .fillMaxHeight(0.7f)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .pointerInput(state.mapDesignerMode, state.mapDesignerMoveMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (state.mapDesignerMode && state.mapDesignerMoveMode && state.selectedGlobalWaypointIndex != -1) {
                                // Mover waypoint (ajustamos el pan según la escala y tamaño del contenedor original de 1920x1080 o el tamaño relativo)
                                // En este caso, el tamaño del contenedor no está directamente en pointerInput(Unit) sin BoxWithConstraints.
                                // Podemos pasar un delta estimado o calcular el tamaño real usando un layout modifier.
                                // Ya que `size` no está disponible directamente aquí (es un PointerInputScope sin MeasureScope), 
                                // es mejor usar el pan asumiendo un tamaño de contenedor estándar o guardando el tamaño de pantalla.
                                // Una forma simple es (pan.x / 1000f) pero lo haremos mejor:
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
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                ) {
                    val imgWidth = mapBitmap!!.width.toFloat()
                    val imgHeight = mapBitmap!!.height.toFloat()
                    val imgRatio = imgWidth / imgHeight
                    val viewRatio = maxWidth.value / maxHeight.value
                    
                    val drawWidthDp = if (imgRatio > viewRatio) maxWidth else maxHeight * imgRatio
                    val drawHeightDp = if (imgRatio > viewRatio) maxWidth / imgRatio else maxHeight

                    // Contenedor que abraza EXACTAMENTE a la imagen
                    Box(
                        modifier = Modifier
                            .size(drawWidthDp, drawHeightDp)
                            .align(Alignment.Center)
                    ) {
                        Image(
                            bitmap = mapBitmap!!,
                            contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_metro_map),
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
                                                val nx = startOffset.x / size.width
                                                val ny = startOffset.y / size.height
                                                viewModel.selectGlobalWaypointAt(nx, ny)
                                            },
                                            onDrag = { change, _ ->
                                                if (state.selectedGlobalWaypointIndex != -1) {
                                                    change.consume()
                                                    val nx = change.position.x / size.width
                                                    val ny = change.position.y / size.height
                                                    viewModel.moveSelectedGlobalWaypointTo(nx, ny)
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
                                                // Teletransportar si tap en un waypoint
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
                                    color = if (isSelected) Color.Yellow.copy(alpha = 0.8f) else Color.Cyan.copy(alpha = 0.8f),
                                    radius = radius,
                                    center = Offset(left + width / 2f, top + height / 2f)
                                )
                            }
                        }
                    }
                }
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
                Text(if (state.mapDesignerMode) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_designer_mode) else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_edit_map), color = Color.White)
            }
            
            IconButton(
                onClick = { viewModel.closeMetroMap() },
                modifier = Modifier.background(Color.Red, RoundedCornerShape(8.dp))
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
                    .background(Color(0xAA000000), RoundedCornerShape(12.dp))
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
                        modifier = Modifier.background(if (state.mapDesignerMoveMode) Color.Red else Color.DarkGray, CircleShape)
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_save))
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
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.metro_select_station), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = state.mapSearchQuery,
                    onValueChange = onSearch,
                    label = { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_search), color = Color.Gray) },
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
                    Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.menu_cancel))
                }
            }
        }
    }
}

@Composable
fun LoopingVideoPlayer(assetFileName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    
    // Copy the asset file to cache if it doesn't exist
    val cacheFile = remember(assetFileName) {
        val file = File(context.cacheDir, assetFileName.replace("/", "_"))
        if (!file.exists()) {
            try {
                context.assets.open(assetFileName).use { inputStream ->
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        file
    }

    if (cacheFile.exists()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    // Store view and video dims separately; apply matrix when both are known
                    var viewW = 0
                    var viewH = 0
                    var vidW = 0
                    var vidH = 0

                    fun applyFitHeightMatrix() {
                        if (viewW <= 0 || viewH <= 0 || vidW <= 0 || vidH <= 0) return
                        // TextureView stretches video to viewW x viewH by default.
                        // Correction: scaleX = (vidW/vidH * viewH) / viewW  →  keeps height full, width proportional.
                        val scaleX = (vidW.toFloat() * viewH) / (vidH.toFloat() * viewW)
                        val matrix = Matrix()
                        matrix.setScale(scaleX, 1f, viewW / 2f, viewH / 2f)
                        setTransform(matrix)
                    }

                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        var mediaPlayer: MediaPlayer? = null

                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            viewW = width
                            viewH = height
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(ctx, Uri.fromFile(cacheFile))
                                setSurface(Surface(surface))
                                isLooping = true
                                setVolume(0f, 0f)
                                setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                                    vidW = vWidth
                                    vidH = vHeight
                                    applyFitHeightMatrix()
                                }
                                setOnPreparedListener { mp ->
                                    // Also grab dims from prepared in case size event already fired
                                    if (vidW == 0) { vidW = mp.videoWidth; vidH = mp.videoHeight }
                                    applyFitHeightMatrix()
                                    mp.start()
                                }
                                prepareAsync()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            viewW = width
                            viewH = height
                            applyFitHeightMatrix()
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            mediaPlayer?.release()
                            mediaPlayer = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            },
            modifier = modifier
        )
    }
}
