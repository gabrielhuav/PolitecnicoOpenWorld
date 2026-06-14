package ovh.gabrielhuav.pow.features.interiores.escom.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInQuart
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.MetroInteriorState
import ovh.gabrielhuav.pow.features.interiores.escom.viewmodel.MetroInteriorViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.interiores.core.ui.CollisionMatrixDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.ui.WaypointDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import kotlin.math.max

private val BACKGROUND_ASSET_PATH = "metro_cdmx/inside.png"

@Composable
fun MetroStationInteriorScreen(
    stationName: String,
    spawnX: Float = -1f,
    spawnY: Float = -1f,
    onExit: (String) -> Unit,
    onTeleportToStation: (String, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val viewModel: MetroInteriorViewModel = viewModel(
        factory = MetroInteriorViewModel.Factory(context, stationName, spawnX, spawnY)
    )
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val density = LocalDensity.current

    // Cargar la imagen de fondo y trenes
    var background by remember { mutableStateOf<ImageBitmap?>(null) }
    var metro1Bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var metro2Bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(BACKGROUND_ASSET_PATH) {
        withContext(Dispatchers.IO) {
            try {
                context.assets.open(BACKGROUND_ASSET_PATH).use {
                    background = BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) { }
            try {
                context.assets.open("metro_cdmx/metro1.webp").use {
                    metro1Bitmap = BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) { }
            try {
                context.assets.open("metro_cdmx/metro2.webp").use {
                    metro2Bitmap = BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) { }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMatricesToUri(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMatricesFromUri(it) } }

    val exportWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportWaypointsToUri(it) } }
    val importWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importWaypointsFromUri(it) } }

    val exportGlobalWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportGlobalWaypointsToUri(it) } }
    val importGlobalWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importGlobalWaypointsFromUri(it) } }

    BackHandler { onExit(stationName) }

    // Observar si el ViewModel solicita salir de la estación (waypoint "salida")
    LaunchedEffect(state.exitStationRequested) {
        if (state.exitStationRequested) {
            viewModel.consumeExitStation()
            onExit(stationName)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        // Canvas / Cámara principal inmersiva
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewW = with(density) { maxWidth.toPx() }
            val viewH = with(density) { maxHeight.toPx() }

            val bgImg = background
            val worldW = bgImg?.width?.toFloat() ?: 1920f
            val worldH = bgImg?.height?.toFloat() ?: 1080f

            val metro1YOffset = remember { Animatable(-worldH) }
            val metro2YOffset = remember { Animatable(worldH) }

            LaunchedEffect(state.isMetro1Animating, state.spawnWithAnimation) {
                if (state.isMetro1Animating || state.spawnWithAnimation) {
                    metro1YOffset.snapTo(-worldH)
                    metro1YOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 1500, easing = EaseOutQuart)
                    )
                    viewModel.onMetro1AnimationFinished()
                }
            }

            LaunchedEffect(state.isMetro1Departing) {
                if (state.isMetro1Departing) {
                    metro1YOffset.snapTo(0f)
                    metro1YOffset.animateTo(
                        targetValue = worldH,
                        animationSpec = tween(durationMillis = 2000, easing = EaseInQuart)
                    )
                }
            }

            var isMetro2Visible by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                while (true) {
                    isMetro2Visible = true
                    metro2YOffset.snapTo(worldH)
                    metro2YOffset.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 1500, easing = EaseOutQuart)
                    )
                    delay(4000)
                    metro2YOffset.animateTo(
                        targetValue = -worldH,
                        animationSpec = tween(durationMillis = 1500, easing = EaseInQuart)
                    )
                    isMetro2Visible = false
                    delay(5000)
                }
            }

            val playerWorldX = state.playerX * worldW
            val playerWorldY = state.playerY * worldH

            // Zoom ajustado para que la altura de la imagen encaje con la pantalla
            val zoom = 1.0f
            val cam = computeCamera(playerWorldX, playerWorldY, worldW, worldH, viewW, viewH, zoom)

            fun toScreenX(nx: Float) = cam.offsetX + (nx * worldW) * cam.scale
            fun toScreenY(ny: Float) = cam.offsetY + (ny * worldH) * cam.scale

            // --- FONDO (CANVAS) ---
            if (bgImg != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(cam.offsetX, cam.offsetY) {
                        scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                            drawImage(
                                image = bgImg,
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(worldW.toInt(), worldH.toInt())
                            )
                            

                            if (isMetro2Visible) {
                                metro2Bitmap?.let { m2 ->
                                    drawImage(
                                        image = m2,
                                        dstOffset = IntOffset(0, metro2YOffset.value.toInt()),
                                        dstSize = IntSize(worldW.toInt(), worldH.toInt())
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- MODO DISEÑADOR (CAPA MATRIZ) ---
            CollisionMatrixDesignerLayer(
                enabled = state.designerMode && state.designerTarget == DesignerTarget.MATRIX,
                rows = state.designerRows,
                worldWidth = worldW,
                worldHeight = worldH,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onPaintWorld = { x, y ->
                    viewModel.paintCellAtWorld(x / worldW, y / worldH)
                },
                modifier = Modifier.matchParentSize()
            )
            
            // --- MODO DISEÑADOR (CAPA WAYPOINTS) ---
            WaypointDesignerLayer(
                enabled = state.designerMode && state.designerTarget == DesignerTarget.WAYPOINTS,
                doors = state.doors,
                selectedIndex = state.selectedDoorIndex,
                worldWidth = worldW,
                worldHeight = worldH,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onSelectWorld = { x, y -> viewModel.selectDoor(x / worldW, y / worldH) },
                onDragWorld = { x, y -> viewModel.dragDoor(x / worldW, y / worldH) },
                modifier = Modifier.matchParentSize()
            )

            // (Eliminado el pintado debug de ZONAS DE INTERACCIÓN a petición)

            // --- JUGADOR ---
            val playerSizeBase = 300f
            val playerSizePx = playerSizeBase * cam.scale
            val playerPxX = toScreenX(state.playerX)
            val playerPxY = toScreenY(state.playerY)

            if (state.isPlayerVisible) {
                Box(
                    modifier = Modifier
                        .absoluteOffset(
                            x = with(density) { playerPxX.toDp() } - with(density) { (playerSizePx / 2).toDp() },
                            y = with(density) { playerPxY.toDp() } - with(density) { (playerSizePx / 2).toDp() }
                        )
                        .size(with(density) { playerSizePx.toDp() })
                ) {
                    MetroPlayerSprite(state)
                }
            }

            // --- CANVAS 2: METRO 1 (SOBRE EL JUGADOR) ---
            val isMetro1Visible = state.isMetro1Animating || state.spawnWithAnimation || state.showMetroMap || state.isMetro1Departing || state.isBoardingWalkActive || state.isDisembarkingWalkActive
            if (isMetro1Visible && metro1Bitmap != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(cam.offsetX, cam.offsetY) {
                        scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                            metro1Bitmap?.let { m1 ->
                                drawImage(
                                    image = m1,
                                    dstOffset = IntOffset(0, metro1YOffset.value.toInt()),
                                    dstSize = IntSize(worldW.toInt(), worldH.toInt())
                                )
                            }
                        }
                    }
                }
            }
        }

        if (background == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFF07B00))
            }
        }

        // --- HUD Y CONTROLES ---
        if (!state.designerMode) {
            
            val sidePadding = if (isPortrait) 8.dp else 32.dp
            val bottomPadding = if (isPortrait) 32.dp else 20.dp
            val maxScale = if (isPortrait) 0.95f else 1.3f
            val scale = state.controlsScale.coerceIn(0.6f, maxScale)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding, start = sidePadding, end = sidePadding)
                    .systemBarsPadding()
                    .graphicsLayer { alpha = if (state.areControlsEnabled) 1f else 0.4f },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val movement = @Composable {
                    if (state.controlType == ControlType.DPAD)
                        DPadController(modifier = Modifier.scale(scale), onDirectionPressed = { viewModel.moveDirection(it) })
                    else
                        JoystickController(modifier = Modifier.scale(scale), onMove = { viewModel.moveByAngle(it) })
                }
                
                val actions = @Composable {
                    ActionButtonsController(
                        modifier = Modifier.scale(scale),
                        onActionChanged = { action, pressed ->
                            when (action) {
                                GameAction.A -> viewModel.setRunning(pressed)
                                GameAction.X -> if (pressed) viewModel.interactWithHotspot()
                                else -> {} // B y Y no tienen uso en Metro
                            }
                        },
                        onClaimCollectiblePressed = { viewModel.interactWithHotspot() }
                    )
                }

                if (state.swapControls) { actions(); movement() } else { movement(); actions() }
            }
        }

        // --- INTERACTION PROMPT ---
        if (!state.designerMode) {
            state.activeDoor?.let { door ->
                Box(Modifier.fillMaxSize().padding(top = 110.dp), Alignment.TopCenter) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_press_x_door, door.label.uppercase()),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
        }

        // --- TOAST MENSAJES ---
        state.messageToast?.let { msg ->
            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.TopCenter) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // --- BARRA SUPERIOR Y BOTÓN DISEÑADOR ---
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .systemBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onExit(stationName) },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, "Salir", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_station_name, stationName).uppercase(),
                color = Color(0xFFF07B00),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // Botón Diseñador
        IconButton(
            onClick = { viewModel.toggleDesignerMode() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp)
                .background(Color.White.copy(alpha = 0.85f), CircleShape)
        ) {
            Icon(Icons.Default.Architecture, "Diseñador", tint = Color.Black)
        }

        // --- TOOLBAR DISEÑADOR ---
        if (state.designerMode) {
            val gridRows = state.designerRows.size
            val gridCols = state.designerRows.maxOfOrNull { it.length } ?: 0
            
            DesignerToolbar(
                target = state.designerTarget,
                brushWall = state.designerBrushWall,
                dirty = state.designerDirty,
                roomName = stationName,
                hasSelectedDoor = state.selectedDoorIndex != -1,
                gridCols = gridCols,
                gridRows = gridRows,
                onResize = viewModel::resizeDesignerMatrixBy,
                onResizeWaypoint = viewModel::resizeDoor,
                onSelectTarget = viewModel::setDesignerTarget,
                onBrush = viewModel::setDesignerBrushWall,
                onSave = viewModel::saveDesignerMatrix,
                onReset = viewModel::resetDesignerMatrix,
                onExport = {
                    if (state.designerTarget == DesignerTarget.WAYPOINTS) {
                        exportWpLauncher.launch("metro_waypoints_${stationName}.json")
                    } else {
                        exportLauncher.launch("metro_matrix_${stationName}.json")
                    }
                },
                onImport = {
                    if (state.designerTarget == DesignerTarget.WAYPOINTS) {
                        importWpLauncher.launch(arrayOf("application/json"))
                    } else {
                        importLauncher.launch(arrayOf("application/json"))
                    }
                },
                onExit = viewModel::toggleDesignerMode,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (state.showMetroMap) {
            MetroMapOverlay(
                state = state,
                viewModel = viewModel,
                onTeleportToStation = onTeleportToStation,
                onExportGlobal = { exportGlobalWpLauncher.launch("global_waypoints.json") },
                onImportGlobal = { importGlobalWpLauncher.launch(arrayOf("application/json")) }
            )
        }
    }
}

/**
 * Barra de herramientas del Modo Diseñador idéntica a ZombieGameScreen.
 */
@Composable
private fun DesignerToolbar(
    target: DesignerTarget,
    brushWall: Boolean,
    dirty: Boolean,
    roomName: String,
    hasSelectedDoor: Boolean,
    gridCols: Int,
    gridRows: Int,
    onResize: (Int, Int) -> Unit,
    onResizeWaypoint: (Float, Float) -> Unit = { _, _ -> },
    onSelectTarget: (DesignerTarget) -> Unit,
    onBrush: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWaypoints = target == DesignerTarget.WAYPOINTS
    Column(
        modifier = modifier
            .systemBarsPadding()
            .padding(12.dp)
            .fillMaxWidth(0.96f)
            .background(Color(0xFF1E1E24).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_designer_room, roomName.uppercase()),
            color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        // Selector de objetivo: MATRIZ de colisión o WAYPOINTS (puertas).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolButton("MATRIZ", !isWaypoints, Color(0xFF3A86FF), Modifier.weight(1f)) { onSelectTarget(DesignerTarget.MATRIX) }
            ToolButton("WAYPOINTS", isWaypoints, Color(0xFFD4AF37), Modifier.weight(1f)) { onSelectTarget(DesignerTarget.WAYPOINTS) }
        }
        Text(
            if (isWaypoints)
                (if (hasSelectedDoor) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_drag_door)
                 else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_touch_door))
            else "Toca o arrastra sobre la rejilla. Rojo = pared.",
            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
        )
            if (isWaypoints && hasSelectedDoor) {
                Text(
                    androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_waypoint_size),
                    color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ToolButton("ANCHO −", false, Color(0xFFD4AF37), Modifier.weight(1f)) { onResizeWaypoint(-0.02f, 0f) }
                    ToolButton("ANCHO +", false, Color(0xFFD4AF37), Modifier.weight(1f)) { onResizeWaypoint(0.02f, 0f) }
                    ToolButton("ALTO −", false, Color(0xFFD4AF37), Modifier.weight(1f)) { onResizeWaypoint(0f, -0.02f) }
                    ToolButton("ALTO +", false, Color(0xFFD4AF37), Modifier.weight(1f)) { onResizeWaypoint(0f, 0.02f) }
                }
            }
        if (!isWaypoints) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("PARED", brushWall, Color(0xFFD32F2F), Modifier.weight(1f)) { onBrush(true) }
                ToolButton("BORRAR", !brushWall, Color(0xFF4CAF50), Modifier.weight(1f)) { onBrush(false) }
            }
            // ─── TAMAÑO DE LA MATRIZ ───────────────────────────────
            Text(
                androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_size_grid, gridCols, gridRows),
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton("COL −", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(-1, 0) }
                ToolButton("COL +", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(1, 0) }
                ToolButton("FIL −", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(0, -1) }
                ToolButton("FIL +", false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(0, 1) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (dirty) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_save_unsaved) else "GUARDAR", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_reset), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_export), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_import), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onExit, modifier = Modifier.height(40.dp)) {
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_exit), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) color else Color(0xFF2A1C21)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun computeCamera(
    playerX: Float, playerY: Float, worldW: Float, worldH: Float,
    viewW: Float, viewH: Float, zoom: Float
): CameraTransform {
    if (viewW <= 0f || viewH <= 0f) return CameraTransform(0f, 0f, 1f)
    // En lugar de hacer 'fit' estricto en X y Y, hacemos que el alto mundial llene el alto de la pantalla,
    // y aplicamos el multiplicador zoom (1.0 = alto perfecto).
    val scale = (viewH / worldH) * zoom
    val scaledW = worldW * scale
    val scaledH = worldH * scale
    var offsetX = viewW / 2f - playerX * scale
    var offsetY = viewH / 2f - playerY * scale
    offsetX = if (scaledW <= viewW) (viewW - scaledW) / 2f else offsetX.coerceIn(viewW - scaledW, 0f)
    offsetY = if (scaledH <= viewH) (viewH - scaledH) / 2f else offsetY.coerceIn(viewH - scaledH, 0f)
    return CameraTransform(offsetX, offsetY, scale)
}

/**
 * Sprite del jugador para el interior.
 */
@Composable
private fun MetroPlayerSprite(state: MetroInteriorState) {
    val context = LocalContext.current
    val action = state.playerAction
    val isFacingRight = state.isFacingRight

    var currentFrame by remember { mutableIntStateOf(1) }
    var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }
    val bitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }

    LaunchedEffect(action) {
        currentFrame = 1
        while (true) {
            val maxFrames = when (action) {
                PlayerAction.IDLE -> 6
                PlayerAction.WALK -> 6
                PlayerAction.SPECIAL -> 8
                PlayerAction.RUN -> 6
            }
            val assetPath = when (action) {
                PlayerAction.IDLE    -> "MAIN/lazaroIdle/lazaro_i_$currentFrame.webp"
                PlayerAction.WALK    -> "MAIN/lazaroWalk/lazaro_w_$currentFrame.webp"
                PlayerAction.SPECIAL -> "MAIN/lazaroSpecial/lazaro_s_$currentFrame.webp"
                PlayerAction.RUN     -> "MAIN/lazaroRun/lazaro_r_$currentFrame.webp"
            }
            if (!bitmapCache.containsKey(assetPath)) {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        context.assets.open(assetPath).use {
                            BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    } catch (e: Exception) { null }
                }
                bitmapCache[assetPath] = bmp
            }
            currentImage = bitmapCache[assetPath]

            val frameDelay = when (action) {
                PlayerAction.IDLE -> 1000L
                PlayerAction.WALK -> 100L
                PlayerAction.RUN  -> 100L
                PlayerAction.SPECIAL -> 300L
            }
            delay(frameDelay)
            currentFrame = (currentFrame % maxFrames) + 1
        }
    }

    val img = currentImage
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_metro_player),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (isFacingRight) 1f else -1f
                }
        )
    }
}
