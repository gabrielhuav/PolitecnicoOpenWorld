package ovh.gabrielhuav.pow.features.interiores.zombies.ui

// REFACTOR: funciones del Modo Diseñador extraídas a ZombieGameDesigner.kt (parcial del VM)
// → ahora son extensiones y requieren import explícito desde el paquete ui. Ver 09 §0.
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.platform.imagen.decodeAssetSampled
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.interiores.core.ui.CollisionMatrixDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.ui.InteriorNpcView
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerView
import ovh.gabrielhuav.pow.features.interiores.core.ui.RemotePlayerView
import ovh.gabrielhuav.pow.features.interiores.core.ui.WaypointDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.DesignerBrush
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieInteriorViewModel
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.exportMatricesToUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.exportWaypointsToUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.importMatricesFromUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.importWaypointsFromUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.moveSelectedDoorToWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.paintCellAtWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resetDesignerMatrix
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resetDesignerWaypoints
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resizeDesignerMatrixBy
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.saveDesignerMatrix
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.saveDesignerWaypoints
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.selectDoorAtWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.setDesignerBrush
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.setDesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.toggleDesignerMode
import ovh.gabrielhuav.pow.features.map_exterior.ui.SkinSelectorDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.ZombiVideoPlayer
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuItem
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionsMenu
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

private const val ZOMBIE_SPRITE_BASE = 60f
private const val PLAYER_SPRITE_BASE = 56f

// Colores de las auras de luz. Constantes top-level para no asignar la lista
// en cada drawCircle de cada frame (presión de GC en gama baja).
private val PLAYER_LIGHT_COLORS = listOf(Color(0x80FFF59D), Color(0x33FFEB3B), Color.Transparent)
private val ZOMBIE_LIGHT_COLORS = listOf(Color(0x6676FF03), Color(0x2664DD17), Color.Transparent)
private const val PLAYER_LIGHT_RADIUS = PLAYER_SPRITE_BASE * 2.5f
private const val ZOMBIE_LIGHT_RADIUS = ZOMBIE_SPRITE_BASE * 2f

@Composable
fun ZombieGameScreen(
    onExitToWorld: () -> Unit,
    isMultiplayer: Boolean,
    playerName: String,
    onNavigateToSettings: () -> Unit = {},
    debugHitboxes: Boolean = false,
    // Sala inicial de Interiores: por defecto el lobby de ESCOM; la puerta FES la fija a FES_ID.
    startRoomId: String = ZombieRoomCatalog.LOBBY_ID,
    // MODO HISTORIA: abre el selector de slots para guardar la partida (también en interiores).
    onRequestSaveGame: () -> Unit = {},
    // MODO HISTORIA: abre el REGISTRO DE MISIONES (MissionLogDialog, hospedado a nivel
    // AppNavGraph con el worldMapViewModel Activity-scoped). null = fuera de campaña (se
    // oculta el ítem "Misiones" del menú de Opciones). MVVM: este VM de interiores NO se
    // acopla al del mundo; solo emite la intención por callback.
    onRequestMissionLog: (() -> Unit)? = null,
    // MODO HISTORIA: el waypoint final de ENCB_LAB2 pide reanudar la narrativa (cómic ENCB_OUTRO).
    onPlayStoryOutro: () -> Unit = {},
    // MODO HISTORIA: notifica la sala actual (id de ZombieRoomCatalog) al entrar y en cada
    // cambio de sala, para que el guardado sepa en qué interior estaba el jugador.
    onRoomChanged: (String) -> Unit = {},
    // MODO HISTORIA: objetivo a mostrar DENTRO del interior (p. ej. "Busca pistas en la ESCOM"
    // tras la Misión 1). null = no mostrar widget de objetivo. El objetivo del mapa exterior NO
    // se altera (allá sigue "Ingresa a la ESCOM, Cumplido").
    interiorObjective: ovh.gabrielhuav.pow.domain.models.campaign.CampaignObjective? = null,
    // INVENTARIO: estado restaurado al CARGAR partida dentro del interior (assetPaths de llaves +
    // progreso de ENCB_lab1) y callback para PERSISTIRLO (lo escribe MainActivity en el VM del mundo).
    initialInventoryKeys: List<String> = emptyList(),
    initialLab1KeyFound: Boolean = false,
    onInteriorProgress: (List<String>, Boolean) -> Unit = { _, _ -> },
    // MISIÓN 2 · salón de la mochila: se dispara al RECOGER la mochila de Prankedy (el VM del
    // mundo completa la Misión 2 vía completeMission2Backpack; lo cablea AppNavGraph).
    onMission2BackpackRecovered: () -> Unit = {},
    // MISIÓN 2 (mochila): slots de inventario desbloqueados al entrar (1 → 4 tras la mochila).
    initialUnlockedSlots: Int = 2,
    // MISIÓN 3 (recompensa): ¿ya tiene arma de fuego? (gate del modo RANGED; true fuera de campaña).
    firearmUnlocked: Boolean = true,
    // MISIÓN 3 (asalto ENCB): siembra zombis en la cadena ENCB + la evidencia 🧪 en encb_lab1.
    mission3Assault: Boolean = false,
    // MISIÓN 3: se dispara al RECOGER la evidencia (el mundo completa la misión + arma de fuego).
    onMission3EvidenceRecovered: () -> Unit = {},
    // MISIÓN 2 · fase 1 "ESCONDERSE" (lobby): true mientras la fase esté activa y seguida. Se
    // pasa en RUNTIME (no al crear el VM): así también arma la búsqueda si sigues la Misión 2
    // desde el registro estando YA dentro del lobby. Desenlaces → callbacks al VM del mundo.
    mission2Hide: Boolean = false,
    onMission2HideCompleted: () -> Unit = {},
    onMission2HideFailed: () -> Unit = {},
    mission2Rumor: Boolean = false,
    onMission2RumorCompleted: () -> Unit = {},
    // 🆕 OBJETOS DE MISIÓN bloqueados contra desechar (llave M1) mientras las misiones 1-2
    // estén en curso. Runtime, como mission2Hide.
    missionItemsLocked: Boolean = false
) {
    val context = LocalContext.current
    // Modo Desarrollador: si está APAGADO se ocultan botones de prueba (Diseñador, y "Salir al mapa"
    // durante la Misión 1). Se lee una vez al entrar a la pantalla.
    val developerMode = remember { ovh.gabrielhuav.pow.data.repository.SettingsRepository(context).getDeveloperMode() }
    val serverUrl = if (isMultiplayer) ovh.gabrielhuav.pow.BuildConfig.INTERIORS_SERVER_URL else null
    val viewModel: ZombieInteriorViewModel = androidx.hilt.navigation.compose.hiltViewModel<ZombieInteriorViewModel, ZombieInteriorViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(
                serverUrl, playerName, startRoomId, initialInventoryKeys, initialLab1KeyFound,
                initialUnlockedSlots, firearmUnlocked, mission3Assault
            )
        }
    )
    val state by viewModel.state.collectAsState()
    val density = LocalDensity.current

    // Puente de PERSISTENCIA: cada cambio de inventario/progreso del puzzle se empuja al VM del
    // mundo (vía MainActivity) para que el guardado lo capture.
    LaunchedEffect(state.inventoryKeys, state.lab1KeyFound) {
        onInteriorProgress(state.inventoryKeys, state.lab1KeyFound)
    }

    // Export/Import del JSON de matrices (igual que el mapa principal con landmarks).
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportMatricesToUri(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importMatricesFromUri(it) } }

    // Export/Import del JSON de waypoints (puertas).
    val exportWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportWaypointsToUri(it) } }
    val importWpLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importWaypointsFromUri(it) } }

    LaunchedEffect(state.isExitingToWorld) {
        if (state.isExitingToWorld) { viewModel.consumeExit(); onExitToWorld() }
    }
    // MODO HISTORIA: salida del motor de interiores hacia el cómic ENCB_OUTRO.
    LaunchedEffect(state.isExitingToStoryOutro) {
        if (state.isExitingToStoryOutro) { viewModel.consumeExit(); onPlayStoryOutro() }
    }
    // MISIÓN 2 · salón: al recoger la mochila se avisa al mundo (completa la misión). Una vez.
    LaunchedEffect(state.mission2BackpackTaken) {
        if (state.mission2BackpackTaken) onMission2BackpackRecovered()
    }
    // MISIÓN 3 · asalto: al recoger la evidencia se avisa al mundo (misión + arma de fuego).
    LaunchedEffect(state.mission3EvidenceTaken) {
        if (state.mission3EvidenceTaken) onMission3EvidenceRecovered()
    }
    // MISIÓN 2 · fase ESCONDERSE: arma/desarma la búsqueda en el lobby según la fase del mundo
    // (runtime; ver setMission2Hide) y notifica el desenlace UNA vez.
    LaunchedEffect(mission2Hide) { viewModel.setMission2Hide(mission2Hide) }
    LaunchedEffect(state.mission2HideCompleted) {
        if (state.mission2HideCompleted) onMission2HideCompleted()
    }
    LaunchedEffect(state.mission2HideFailed) {
        if (state.mission2HideFailed) onMission2HideFailed()
    }
    LaunchedEffect(mission2Rumor) { viewModel.setMission2Rumor(mission2Rumor) }
    LaunchedEffect(missionItemsLocked) { viewModel.setMissionItemsLocked(missionItemsLocked) }
    LaunchedEffect(state.mission2RumorCompleted) {
        if (state.mission2RumorCompleted) onMission2RumorCompleted()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.soundManager.stopWalk()
            viewModel.soundManager.stopRun()
        }
    }

    val room = ZombieRoomCatalog.rooms[state.currentRoomIndex]
    // Avisa la sala actual (entrada + cada transición interna) para el guardado de partida.
    LaunchedEffect(state.currentRoomIndex) { onRoomChanged(room.id) }
    val effectiveBgAsset = when {
        room.id == ZombieRoomCatalog.LOBBY_ID && state.zombieModeActivated ->
            "BUILDINGS/building_escom_zombie.webp"
        room.type == ZoneType.BUILDING && !state.zombieModeActivated ->
            "INTERIORS/ESCOM/z_${room.id.removePrefix("za_")}.webp"
        else -> room.backgroundAsset
    }
    var background by remember(effectiveBgAsset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(effectiveBgAsset) {
        background = withContext(Dispatchers.IO) {
            try { context.assets.open(effectiveBgAsset).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }
            catch (e: Exception) {
                android.util.Log.e("ZombieGameScreen", "No se pudo cargar fondo $effectiveBgAsset: ${e.message}")
                null
            }
        }
    }

    // ─── FEEDBACK DE DAÑO: screen shake + flash/viñeta roja ──────────────────
    // Screen shake disparado por cada incremento de damagePulseTrigger (recibir daño).
    var shakeX by remember { mutableStateOf(0f) }
    var shakeY by remember { mutableStateOf(0f) }
    // Flash rojo breve al recibir daño.
    var flashAlpha by remember { mutableStateOf(0f) }
    // Calibración de los autos del estacionamiento del lobby (transformación de GRUPO estilo
    // PowerPoint). La FUENTE es un JSON en assets por campus (CONFIG/parking/<assetMatch>.json) que
    // produce el calibrador en vivo al EXPORTAR; reproduce el acomodo del exterior. Se carga async
    // (I/O de assets) y llena estos estados; el modo desarrollador (ParkingTuneTool) los re-ajusta en
    // vivo. Añadir/ajustar un campus = soltar su .json, sin tocar Kotlin.
    var parkAngle by remember { mutableStateOf(0f) }
    var parkOffX by remember { mutableStateOf(0f) }
    var parkOffY by remember { mutableStateOf(0f) }
    var parkScale by remember { mutableStateOf(1f) }
    var parkSelfAngle by remember { mutableStateOf(0f) }   // giro de cada auto sobre su propio eje
    var parkFlipped by remember { mutableStateOf(setOf<Int>()) }   // autos volteados 180° (identificador ↑↓)
    LaunchedEffect(room.backgroundAsset) {
        val campus = ovh.gabrielhuav.pow.domain.models.map.CampusParkingCatalog.forAsset(room.backgroundAsset)
            ?: return@LaunchedEffect
        val calib = withContext(Dispatchers.IO) {
            // Ya no recibe `Context`: lee por `PowAssets`, la costura multiplataforma. Sigue
            // siendo el mismo archivo de `assets/` y el mismo AssetManager por debajo.
            ovh.gabrielhuav.pow.domain.models.map.CampusParkingCatalog.loadCalibration(campus)
        }
        parkAngle = calib.headingDeg
        parkOffX = calib.offsetXFrac
        parkOffY = calib.offsetYFrac
        parkScale = calib.scale
        parkSelfAngle = calib.selfRotationDeg
        parkFlipped = calib.flipped.toSet()
    }
    // Diseñador de estacionamiento (se abre desde el selector del botón "Diseñador").
    var parkingDesignerActive by remember { mutableStateOf(false) }
    var designerChooserOpen by remember { mutableStateOf(false) }
    // 🔁 ORIENTACIÓN DEL DISEÑADOR: el juego va SIEMPRE horizontal (orientación por RUTA en
    // AppNavGraph/MainActivity), pero el panel del diseñador es alto y en horizontal estorba. SOLO
    // mientras el diseñador está activo ofrecemos un botón para girar a VERTICAL; al salir se
    // restaura horizontal. Excepción local y acotada (análoga a la del Metrobús). Ver 05/09.
    var designerPortrait by remember { mutableStateOf(false) }
    LaunchedEffect(state.designerMode, designerPortrait) {
        val activity = context.findActivity()
        if (!state.designerMode) {
            if (designerPortrait) designerPortrait = false
            activity?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation =
                if (designerPortrait) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    // Exporta la calibración (ángulo + offset del grupo) a un .json vía SAF.
    val parkingExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { u ->
            try {
                val match = ovh.gabrielhuav.pow.domain.models.map.CampusParkingCatalog
                    .forAsset(room.backgroundAsset)?.assetMatch ?: ""
                val json = "{\n" +
                    "  \"campus\": \"$match\",\n" +
                    "  \"backgroundAsset\": \"${room.backgroundAsset}\",\n" +
                    "  \"headingDeg\": $parkAngle,\n" +
                    "  \"offsetXFrac\": $parkOffX,\n" +
                    "  \"offsetYFrac\": $parkOffY,\n" +
                    "  \"scale\": $parkScale,\n" +
                    "  \"selfRotationDeg\": $parkSelfAngle,\n" +
                    "  \"flipped\": [${parkFlipped.sorted().joinToString(",")}]\n" +
                    "}"
                context.contentResolver.openOutputStream(u)?.use { os -> os.write(json.toByteArray()) }
                android.widget.Toast.makeText(context, "Calibración exportada", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error al exportar", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(state.damagePulseTrigger) {
        if (state.damagePulseTrigger > 0) {
            flashAlpha = 0.5f
            val steps = 9
            val intensity = 26f
            for (i in 0 until steps) {
                val decay = 1f - i / steps.toFloat()
                shakeX = (Random.nextFloat() * 2f - 1f) * intensity * decay
                shakeY = (Random.nextFloat() * 2f - 1f) * intensity * decay
                flashAlpha = 0.5f * decay
                delay(28)
            }
            shakeX = 0f; shakeY = 0f; flashAlpha = 0f
        }
    }
    // Pulso de vida baja: la viñeta roja late cuando el jugador está crítico.
    val lowHp = state.playerHealth <= 35f && state.playerHealth > 0f
    val lowHpTransition = rememberInfiniteTransition(label = "lowHp")
    val lowHpPulse by lowHpTransition.animateFloat(
        initialValue = 0.10f, targetValue = 0.34f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "lowHpPulse"
    )
    // La intensidad base de la viñeta escala con la vida perdida.
    val hpLossFactor = (1f - state.playerHealth / 100f).coerceIn(0f, 1f)
    val vignetteAlpha = (hpLossFactor * 0.32f +
            (if (lowHp) lowHpPulse else 0f) +
            flashAlpha).coerceIn(0f, 0.85f)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(shakeX.roundToInt(), shakeY.roundToInt()) }
        ) {
            val viewportWpx = with(density) { maxWidth.toPx() }
            val viewportHpx = with(density) { maxHeight.toPx() }

            val cam = remember(state.playerX, state.playerY, viewportWpx, viewportHpx, room.id) {
                computeCamera(state.playerX, state.playerY, room.worldWidth, room.worldHeight, viewportWpx, viewportHpx, room.zoom)
            }

            // Brushes de luz reutilizables: centrados en (0,0) y dibujados con translate,
            // así un único shader sirve para todas las entidades (antes se creaba uno por
            // entidad por frame). 'remember' evita recrearlos en cada recomposición.
            val playerLightBrush = remember {
                Brush.radialGradient(PLAYER_LIGHT_COLORS, center = Offset.Zero, radius = PLAYER_LIGHT_RADIUS)
            }
            val zombieLightBrush = remember {
                Brush.radialGradient(ZOMBIE_LIGHT_COLORS, center = Offset.Zero, radius = ZOMBIE_LIGHT_RADIUS)
            }

            // Límites del mundo visibles (frustum culling). Solo dibujamos/recomponemos
            // entidades dentro de esta ventana + un margen, evitando trabajo fuera de pantalla.
            val cullMargin = ZOMBIE_SPRITE_BASE
            val viewLeft = (-cam.offsetX) / cam.scale - cullMargin
            val viewTop = (-cam.offsetY) / cam.scale - cullMargin
            val viewRight = (viewportWpx - cam.offsetX) / cam.scale + cullMargin
            val viewBottom = (viewportHpx - cam.offsetY) / cam.scale + cullMargin
            fun onScreen(wx: Float, wy: Float) =
                wx >= viewLeft && wx <= viewRight && wy >= viewTop && wy <= viewBottom

            // ─── CAPA DEL MUNDO (fondo) ─────────────────────────
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bg = background ?: return@Canvas
                translate(cam.offsetX, cam.offsetY) {
                    scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                        drawImage(
                            image = bg,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(room.worldWidth.toInt(), room.worldHeight.toInt())
                        )
                        if (debugHitboxes) {
                            room.doors.forEach { d ->
                                val r = d.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                                drawRect(Color(0x5500FF00), Offset(r.left, r.top), Size(r.right - r.left, r.bottom - r.top))
                            }
                        }
                    }
                }
            }

            // ─── AUTOS ESTACIONADOS (lobby) ─────────────────────
            // Misma fuente que el exterior: nodos isParkingSlot del navGraph del campus (ver
            // CampusParkingCatalog). Escenografía sin colisión; solo aparece donde el fondo es un
            // asset de campus con estacionamiento (p. ej. el lobby de ESCOM = building_escom.webp).
            ParkedCarsLayer(
                room = room, cam = cam, onScreen = { wx, wy -> onScreen(wx, wy) },
                headingDeg = parkAngle, offsetXFrac = parkOffX, offsetYFrac = parkOffY, scale = parkScale,
                selfRotationDeg = parkSelfAngle, designing = parkingDesignerActive,
                flipped = parkFlipped,
                onToggleFlip = { i -> parkFlipped = if (i in parkFlipped) parkFlipped - i else parkFlipped + i }
            )

            // ─── CAPA DE ILUMINACIÓN DINÁMICA (auras) ───────────
            if (room.type == ZoneType.BUILDING && !state.designerMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    translate(cam.offsetX, cam.offsetY) {
                        scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                            // Un solo shader por tipo, posicionado con translate (sin recrear gradientes).
                            translate(state.playerX, state.playerY) {
                                drawCircle(playerLightBrush, PLAYER_LIGHT_RADIUS, Offset.Zero)
                            }
                            state.remotePlayers.forEach { rp ->
                                if (onScreen(rp.x, rp.y)) translate(rp.x, rp.y) {
                                    drawCircle(playerLightBrush, PLAYER_LIGHT_RADIUS, Offset.Zero)
                                }
                            }
                            state.zombies.forEach { z ->
                                if (!z.isDying && onScreen(z.x, z.y)) translate(z.x, z.y) {
                                    drawCircle(zombieLightBrush, ZOMBIE_LIGHT_RADIUS, Offset.Zero)
                                }
                            }
                        }
                    }
                }
            }

            fun toScreenX(wx: Float) = cam.offsetX + wx * cam.scale
            fun toScreenY(wy: Float) = cam.offsetY + wy * cam.scale

            // ─── LÍNEA PUNTEADA DE SALIDA ───────────────────────
            if (state.showExitGuide && !state.designerMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val px = toScreenX(state.playerX)
                    val py = toScreenY(state.playerY)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(24f, 18f), 0f)
                    room.doors.forEach { d ->
                        if (d.kind == DoorKind.EXIT_NEXT || d.kind == DoorKind.EXIT_PREV || d.kind == DoorKind.GENERIC) {
                            val r = d.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                            val ex = toScreenX(r.centerX())
                            val ey = toScreenY(r.centerY())
                            val color = when (d.kind) {
                                DoorKind.EXIT_NEXT, DoorKind.EXIT_PREV -> Color(0xFFFF9800)
                                else -> Color(0xFFD4AF37)
                            }
                            drawLine(color, Offset(px, py), Offset(ex, ey), strokeWidth = 6f, pathEffect = dash, cap = StrokeCap.Round)
                            // 🧭 PUNTA DE FLECHA en la puerta: convierte la línea en una FLECHA clara de
                            // "ve por aquí" (R5: jugadores no sabían a dónde ir). "V" hecha con 2 líneas.
                            val ddx = ex - px; val ddy = ey - py
                            val len = hypot(ddx.toDouble(), ddy.toDouble()).toFloat()
                            if (len > 1f) {
                                val ux = ddx / len; val uy = ddy / len          // unidad jugador→puerta
                                val headLen = 26f; val headW = 15f
                                val bx = ex - ux * headLen; val by = ey - uy * headLen
                                val pxp = -uy; val pyp = ux                      // perpendicular
                                drawLine(color, Offset(ex, ey), Offset(bx + pxp * headW, by + pyp * headW), strokeWidth = 7f, cap = StrokeCap.Round)
                                drawLine(color, Offset(ex, ey), Offset(bx - pxp * headW, by - pyp * headW), strokeWidth = 7f, cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }

            // Indicadores de puertas
            if (!state.designerMode) {
                room.doors.forEach { door ->
                    val r = door.hitboxFrac.toWorldRect(room.worldWidth, room.worldHeight)
                    DoorIndicator(
                        label = door.label, kind = door.kind,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(r.centerX()).toDp() } - 40.dp,
                            y = with(density) { toScreenY(r.centerY()).toDp() } - 40.dp
                        )
                    )
                }

                // Items en el suelo
                state.items.forEach { item ->
                    if (!onScreen(item.x, item.y)) return@forEach
                    SkillGroundItem(
                        effect = item.effect,
                        highlighted = state.nearbyItemId == item.id,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(item.x).toDp() } - 18.dp,
                            y = with(density) { toScreenY(item.y).toDp() } - 18.dp
                        )
                    )
                }

                // Llaves del puzzle (Modo Historia · ENCB_lab1) en el suelo.
                state.keys.forEach { key ->
                    if (!onScreen(key.x, key.y)) return@forEach
                    KeyGroundItem(
                        assetPath = key.assetPath,
                        highlighted = state.nearbyKeyId == key.id,
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(key.x).toDp() } - 22.dp,
                            y = with(density) { toScreenY(key.y).toDp() } - 22.dp
                        )
                    )
                }

                // MISIÓN 2 · salón: mochila de Prankedy en el suelo (asset propio; 2026-07-10,
                // antes emoji 🎒).
                run {
                    val bpX = state.mission2BackpackX
                    val bpY = state.mission2BackpackY
                    if (bpX != null && bpY != null && !state.mission2BackpackTaken && onScreen(bpX, bpY)) {
                        val bpSize = 64f * cam.scale
                        StoryGroundSprite(
                            assetPath = "CAMPAIGN/MISSION2/mochila_prankedy.webp",
                            sizePx = bpSize,
                            fallbackEmoji = "🎒",
                            contentAlpha = if (state.mission2BackpackNearby) 1f else 0.88f,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(bpX).toDp() } - with(density) { (bpSize / 2).toDp() },
                                y = with(density) { toScreenY(bpY).toDp() } - with(density) { (bpSize / 2).toDp() }
                            )
                        )
                    }
                }

                // MISIÓN 3 · asalto ENCB: la EVIDENCIA del laboratorio (asset propio; 2026-07-10,
                // antes emoji 🧪).
                run {
                    val evX = state.mission3EvidenceX
                    val evY = state.mission3EvidenceY
                    if (evX != null && evY != null && !state.mission3EvidenceTaken && onScreen(evX, evY)) {
                        val evSize = 48f * cam.scale
                        StoryGroundSprite(
                            assetPath = "CAMPAIGN/MISSION3/evidencia_frasco.webp",
                            sizePx = evSize,
                            fallbackEmoji = "🧪",
                            contentAlpha = if (state.mission3EvidenceNearby) 1f else 0.88f,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(evX).toDp() } - with(density) { (evSize / 2).toDp() },
                                y = with(density) { toScreenY(evY).toDp() } - with(density) { (evSize / 2).toDp() }
                            )
                        )
                    }
                }

                // Proyectiles
                val bulletSize = 10f * cam.scale
                state.projectiles.forEach { p ->
                    if (!onScreen(p.x, p.y)) return@forEach
                    Box(
                        modifier = Modifier.absoluteOffset(
                            x = with(density) { toScreenX(p.x).toDp() } - with(density) { (bulletSize / 2).toDp() },
                            y = with(density) { toScreenY(p.y).toDp() } - with(density) { (bulletSize / 2).toDp() }
                        ).size(with(density) { bulletSize.toDp() })
                            .clip(CircleShape)
                            .background(Color(0xFFFFEB3B))
                            .border(1.dp, Color(0xFFFF6F00), CircleShape)
                    )
                }

                // Zombis
                val zSize = ZOMBIE_SPRITE_BASE * cam.scale
                state.zombies.forEach { z ->
                    if (!onScreen(z.x, z.y)) return@forEach
                    key(z.id) {
                        ZombieView(
                            type = z.type, frameIndex = z.frameIndex, facingRight = z.facingRight,
                            isAttacking = z.isAttacking, isDying = z.isDying,
                            health = z.health, maxHealth = z.maxHealth, sizePx = zSize,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(z.x).toDp() } - with(density) { (zSize / 2).toDp() },
                                y = with(density) { toScreenY(z.y).toDp() } - with(density) { (zSize / 2).toDp() }
                            )
                        )
                    }
                }

                // Jugadores remotos y NPCs. `room.playerScaleMul` aplica IGUAL que al jugador:
                // en salas con fondo que achica los sprites (salones ENCB / salón M2, ×3) los
                // NPCs ambientales se veían diminutos junto al jugador (QA 2026-07-13).
                val rpSize = PLAYER_SPRITE_BASE * cam.scale * room.playerScaleMul
                state.remotePlayers.forEach { rp ->
                    if (!onScreen(rp.x, rp.y)) return@forEach
                    key(rp.id) {
                        RemotePlayerView(
                            name = rp.displayName,
                            action = rp.action,
                            facingRight = rp.facingRight,
                            sizePx = rpSize,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(rp.x).toDp() } - with(density) { (rpSize / 2).toDp() },
                                y = with(density) { toScreenY(rp.y).toDp() } - with(density) { (rpSize / 2).toDp() }
                            )
                        )
                    }
                }

                // NPCs civiles del interior (autoritativos del servidor): figuras humanas que
                // deambulan/huyen de los zombis. Reusan RemotePlayerView (sin nombre).
                state.interiorNpcs.forEach { npc ->
                    if (!onScreen(npc.x, npc.y)) return@forEach
                    key("civ_${npc.id}") {
                        RemotePlayerView(
                            name = "",
                            action = npc.action,
                            facingRight = npc.facingRight,
                            sizePx = rpSize,
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(npc.x).toDp() } - with(density) { (rpSize / 2).toDp() },
                                y = with(density) { toScreenY(npc.y).toDp() } - with(density) { (rpSize / 2).toDp() }
                            )
                        )
                    }
                }

                // NPCs AMBIENTALES (Modo Historia, offline): estudiantes/docentes que deambulan
                // por la ESCOM. Render con su SKIN propia (sprite completo), sin nombre ni audio.
                state.ambientNpcs.forEach { npc ->
                    if (!onScreen(npc.x, npc.y)) return@forEach
                    key("anpc_${npc.id}") {
                        InteriorNpcView(
                            skin = npc.skin,
                            action = npc.action,
                            facingRight = npc.facingRight,
                            sizePx = rpSize,
                            modifier = Modifier
                                .absoluteOffset(
                                    x = with(density) { toScreenX(npc.x).toDp() } - with(density) { (rpSize / 2).toDp() },
                                    y = with(density) { toScreenY(npc.y).toDp() } - with(density) { (rpSize / 2).toDp() }
                                )
                                // 🆕 COMBATE: colapsado en el piso (rotado + desvanecido) al morir.
                                .graphicsLayer {
                                    if (npc.isDying) { rotationZ = 90f; alpha = 0.65f }
                                }
                        )
                        // 🆕 Barrita de vida SOLO si está dañado (paridad con el exterior).
                        if (!npc.isDying && npc.health < 100f) {
                            val hbW = 34.dp
                            Box(
                                modifier = Modifier
                                    .absoluteOffset(
                                        x = with(density) { toScreenX(npc.x).toDp() } - hbW / 2,
                                        y = with(density) { toScreenY(npc.y).toDp() } -
                                            with(density) { (rpSize / 2).toDp() } - 8.dp
                                    )
                                    .width(hbW)
                                    .height(4.dp)
                                    .background(Color(0xAA000000), RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(npc.health / 100f)
                                        .height(4.dp)
                                        .background(Color(0xFFE53935), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                        // 🆕 BURBUJA de plática (vida universitaria): frase traducible sobre la
                        // cabeza mientras el NPC "habla" (la fija/limpia stepAmbientNpcs).
                        npc.speechRes?.let { res ->
                            Text(
                                text = stringResource(res),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp,
                                modifier = Modifier
                                    .absoluteOffset(
                                        x = with(density) { toScreenX(npc.x).toDp() } - 70.dp,
                                        y = with(density) { toScreenY(npc.y).toDp() } -
                                            with(density) { (rpSize / 2).toDp() } - 34.dp
                                    )
                                    .width(140.dp)
                                    .background(Color(0xD0101018), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // 🆕 MISIÓN 2: la LATA APESTOSA tirada en el piso (asset propio; 2026-07-10, antes
                // emoji 🥫). La lata ya trae el vapor apestoso integrado en el sprite.
                run {
                    val stX = state.mission2StinkX
                    val stY = state.mission2StinkY
                    if (stX != null && stY != null && onScreen(stX, stY)) {
                        val canSize = 44f * cam.scale
                        StoryGroundSprite(
                            assetPath = "CAMPAIGN/MISSION2/lata_apestosa.webp",
                            sizePx = canSize,
                            fallbackEmoji = "🥫",
                            modifier = Modifier.absoluteOffset(
                                x = with(density) { toScreenX(stX).toDp() } - with(density) { (canSize / 2).toDp() },
                                y = with(density) { toScreenY(stY).toDp() } - with(density) { (canSize / 2).toDp() }
                            )
                        )
                    }
                }

                // ─── CAPA DE NEBLINA (fog of war) centrada en el jugador ──────────
                // Se dibuja DENTRO de la capa del mundo (debajo del HUD y los
                // controles) para que SOLO afecte al mapa, nunca a la GUI.
                if (!state.designerMode) {
                    val fogCx = toScreenX(state.playerX)
                    val fogCy = toScreenY(state.playerY)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val reveal = size.minDimension * 0.50f
                        val outer = reveal * 1.9f
                        drawRect(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    (reveal / outer) to Color.Transparent,
                                    0.86f to Color(0xC005060A),
                                    1.0f to Color(0xEE05060A)
                                ),
                                center = Offset(fogCx, fogCy),
                                radius = outer
                            )
                        )
                    }
                }

                // Jugador local. `room.playerScaleMul` agranda el sprite solo en salas que
                // lo necesitan (p. ej. ENCB_salon1, donde el fondo lo hacía ver diminuto).
                val pSize = PLAYER_SPRITE_BASE * cam.scale * room.playerScaleMul
                // MUERTE: al morir, el jugador queda como "fantasmita" (semitransparente),
                // igual que la animación de muerte de un NPC.
                val ghostAlpha = if (state.showWastedScreen) 0.3f else 1f
                PlayerView(
                    action = state.playerAction, facingRight = state.isPlayerFacingRight,
                    damagePulse = state.damagePulseTrigger, sizePx = pSize,
                    skin = state.selectedSkin,                         // ← NUEVO
                    modifier = Modifier
                        .absoluteOffset(
                            x = with(density) { toScreenX(state.playerX).toDp() } - with(density) { (pSize / 2).toDp() },
                            y = with(density) { toScreenY(state.playerY).toDp() } - with(density) { (pSize / 2).toDp() }
                        )
                        .alpha(ghostAlpha)
                )

                // ─── CAPA DE OCLUSION (profundidad) ──────────────────────────────
                // Los objetos '^' de la matriz "tapan" al jugador: se REDIBUJA el trozo del fondo de
                // esas celdas ENCIMA del jugador cuando el objeto esta DELANTE (su base al sur de los
                // pies del jugador). Asi el jugador pasa POR DETRAS al norte y POR DELANTE al sur.
                // Decision por OBJETO (celdas '^' contiguas comparten la Y-base), memoizada por matriz.
                val occRows = room.collisionMatrix?.rows
                val occBg = background
                if (occRows != null && occBg != null) {
                    val occluders = remember(occRows, room.worldWidth, room.worldHeight) {
                        computeOccluders(occRows, room.worldWidth, room.worldHeight)
                    }
                    if (occluders.isNotEmpty()) {
                        val occCols = occRows.maxOf { it.length }.coerceAtLeast(1)
                        val occRowsN = occRows.size
                        val occCellW = room.worldWidth / occCols
                        val occCellH = room.worldHeight / occRowsN
                        val feetY = state.playerY
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            translate(cam.offsetX, cam.offsetY) {
                                scale(cam.scale, cam.scale, pivot = Offset.Zero) {
                                    occluders.forEach { oc ->
                                        if (oc.anchorBottomY <= feetY) return@forEach
                                        val wx0 = oc.col * occCellW
                                        val wy0 = oc.row * occCellH
                                        if (!onScreen(wx0 + occCellW / 2f, wy0 + occCellH / 2f)) return@forEach
                                        val sx = (wx0 / room.worldWidth * occBg.width).toInt().coerceIn(0, occBg.width - 1)
                                        val sy = (wy0 / room.worldHeight * occBg.height).toInt().coerceIn(0, occBg.height - 1)
                                        val sw = (occCellW / room.worldWidth * occBg.width).toInt()
                                            .coerceIn(1, occBg.width - sx)
                                        val sh = (occCellH / room.worldHeight * occBg.height).toInt()
                                            .coerceIn(1, occBg.height - sy)
                                        drawImage(
                                            image = occBg,
                                            srcOffset = IntOffset(sx, sy),
                                            srcSize = IntSize(sw, sh),
                                            dstOffset = IntOffset(wx0.toInt(), wy0.toInt()),
                                            dstSize = IntSize(occCellW.toInt() + 1, occCellH.toInt() + 1)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ─── Mano zombi fija en el lobby (desaparece tras activar el modo zombie) ──
            // Solo visible en Modo Desarrollador (Interfaz): es la que activa el modo zombi.
            if (developerMode && room.id == ZombieRoomCatalog.LOBBY_ID && !state.zombieModeActivated) {
                val handNx = 0.50f
                val handNy = 0.45f
                val handSizePx = 64f * cam.scale
                val handSizeDp = with(density) { handSizePx.toDp() }
                val handScreenX = cam.offsetX + handNx * room.worldWidth * cam.scale
                val handScreenY = cam.offsetY + handNy * room.worldHeight * cam.scale

                var handBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(Unit) {
                    handBitmap = withContext(Dispatchers.IO) {
                        try {
                            context.assets.decodeAssetSampled(
                                "SPRITES/ZOMBIE/zombie_hand.webp",
                                requestedWidth = 256,
                                requestedHeight = 256,
                            )?.asImageBitmap()
                        } catch (e: Exception) { null }
                    }
                }
                handBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_zombie_hand),
                        modifier = Modifier
                            .absoluteOffset(
                                x = with(density) { (handScreenX - handSizePx / 2f).toDp() },
                                y = with(density) { (handScreenY - handSizePx / 2f).toDp() }
                            )
                            .size(handSizeDp)
                    )
                }
            }

            // ─── CAPA DEL MODO DISEÑADOR: MATRIZ (rejilla editable) ─────
            CollisionMatrixDesignerLayer(
                enabled = state.designerMode && state.designerTarget == DesignerTarget.MATRIX,
                rows = state.designerRows,
                worldWidth = room.worldWidth,
                worldHeight = room.worldHeight,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onPaintWorld = viewModel::paintCellAtWorld,
                modifier = Modifier.matchParentSize()
            )

            // ─── CAPA DEL MODO DISEÑADOR: WAYPOINTS (puertas) ─────
            WaypointDesignerLayer(
                enabled = state.designerMode && state.designerTarget == DesignerTarget.WAYPOINTS,
                doors = state.designerDoors,
                selectedIndex = state.selectedDoorIndex,
                worldWidth = room.worldWidth,
                worldHeight = room.worldHeight,
                camOffsetX = cam.offsetX,
                camOffsetY = cam.offsetY,
                camScale = cam.scale,
                onSelectWorld = viewModel::selectDoorAtWorld,
                onDragWorld = viewModel::moveSelectedDoorToWorld,
                modifier = Modifier.matchParentSize()
            )
        }

        if (background == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color(0xFFD4AF37)) }
        }

        // ─── VIÑETA / FLASH ROJO DE DAÑO ────────────────────────────────────
        // Capa no interactiva sobre el mundo: borde rojo radial cuya intensidad
        // escala con la vida perdida, late en vida baja y destella al recibir daño.
        if (vignetteAlpha > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Red.copy(alpha = vignetteAlpha)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = max(size.width, size.height) * 0.72f
                )
                drawRect(brush = brush)
            }
        }

        // IMPORTANTE (orden de capas / z-order en Compose):
        // El HUD de juego es un Box a pantalla completa. Si el botón del
        // diseñador se declarara ANTES del HUD, el HUD quedaría ENCIMA y
        // robaría los toques de la esquina (por eso "no aparecía" el botón).
        // Por eso primero pintamos el HUD y AL FINAL el botón + la toolbar,
        // garantizando que reciban los toques.

        if (!state.designerMode) {
            // ─── HUD DE JUEGO ───────────────────────────────────
            ZombieHud(
                state = state,
                roomName = room.displayName,
                isBuilding = room.type == ZoneType.BUILDING,
                onMoveDir = viewModel::moveDirection,
                onMoveAngle = viewModel::moveByAngle,
                onRun = viewModel::setRunning,
                onInteract = viewModel::onInteract,
                onSpecial = viewModel::setSpecial,
                onSecondaryPressed = viewModel::onSecondaryPressed,
                onSecondaryReleased = viewModel::onSecondaryReleased,
                onSelectMode = viewModel::selectCombatMode,
                onDismissInventory = viewModel::dismissInventory,
                onTestKey = viewModel::testInventoryKey,
                onDiscardKey = viewModel::discardInventoryKey
            )

            // Aviso de llave (cuando el jugador está sobre una). keyMessage (resultado de probar /
            // puerta cerrada) tiene prioridad y es transitorio.
            val keyPrompt = if (state.nearbyKeyId != null)
                stringResource(R.string.zgame_key_prompt) else null
            // MISIÓN 2 · salón de la mochila: prompt de la LATA APESTOSA (con la clase adentro
            // Y la lata EN EL INVENTARIO — sin ella el salón es un aula normal) o de RECOGER la
            // mochila (cuando ya apareció y estás encima).
            val hasStinkCan = state.inventoryKeys.any {
                ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop.entryAsset(it) ==
                    ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop.M2_STINK_CAN
            }
            val m2Prompt = when {
                room.id == ZombieRoomCatalog.ESCOM_SALON_M2_ID &&
                    !state.mission2StinkThrown && state.ambientNpcs.isNotEmpty() && hasStinkCan ->
                    stringResource(R.string.zgame_stink_prompt)
                state.mission2BackpackNearby && !state.mission2BackpackTaken ->
                    stringResource(R.string.zgame_backpack_prompt)
                // MISIÓN 3: recoger la evidencia del laboratorio.
                state.mission3EvidenceNearby && !state.mission3EvidenceTaken ->
                    stringResource(R.string.zgame_evidence_prompt)
                else -> null
            }
            // Z-ORDER: el panel de INVENTARIO es un modal a pantalla completa (va por ENCIMA de todo).
            // Con el inventario ABIERTO suprimimos los avisos de PROXIMIDAD (puerta "Continuar →" / llave),
            // que se dibujan después del HUD y se traslapaban por encima del inventario. Sí mantenemos
            // keyMessage (resultado de PROBAR la llave) y los toasts: son la retroalimentación de usarlo.
            val proximityPrompt = if (state.showInventory) null else (state.nearbyDoorLabel ?: keyPrompt ?: m2Prompt)
            (state.keyMessage ?: proximityPrompt ?: state.pickupToast ?: state.effectToast)?.let { prompt ->
                Box(Modifier.fillMaxSize().padding(top = 110.dp), Alignment.TopCenter) {
                    Text(prompt.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp,
                        modifier = Modifier.background(Color(0xFF3B0D1B).copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }

            // ─── SUBTÍTULOS de la conversación de la Misión 2 (Rumor) ───
            if (state.storyConvoText != null) {
                Box(modifier = Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .background(color = Color(0xE0101018), shape = RoundedCornerShape(12.dp))
                            .border(2.dp, Color(0xFFFFCC00), RoundedCornerShape(12.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        state.storyConvoSpeaker?.let { speaker ->
                            Text(
                                text = speaker,
                                color = Color(0xFFFFCC00),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = state.storyConvoText ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ─── OBJETIVO (salas del Modo Historia ENCB) ────────────────────────
            // Banner superpuesto mientras el jugador esté en la cadena lineal de la ENCB
            // (lobby → salón → lab1 → lab2). En la MISIÓN 3 (asalto) NO aplica: ahí el objetivo
            // lo muestra interiorObjective (ObjectivesWidget) y este banner sobraría.
            if (room.id in ZombieRoomCatalog.ENCB_STORY_ROOM_IDS && interiorObjective == null) {
                Box(
                    Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp),
                    Alignment.TopCenter
                ) {
                    Text(
                        stringResource(R.string.zgame_objective_investigate),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .alpha(0.85f)   // difuminado para no chocar con los widgets
                            .background(Color(0x99000000), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ─── OBJETIVO DE CAMPAÑA EN INTERIORES + countdown de la fase ESCONDERSE (M2) ──
            // Mismo widget de objetivo que el mapa exterior (arriba-centro) y, DEBAJO, el countdown
            // de la búsqueda policial (fase 1 de la M2 en el lobby). Van en un MISMO Column apilado
            // para que NUNCA se traslapen: el objetivo puede ser de varias líneas (p. ej. "Policía
            // en el lobby: aguanta X s sin que te vean") y con posiciones fijas se encimaban.
            if (interiorObjective != null || state.mission2HideRemainingSec != null) {
                // QA 2026-07-13: la tarjeta completa (etiqueta + título + descripción) encima del
                // countdown tapaba media pantalla durante la búsqueda policial. Se muestra completa
                // unos segundos (para leer QUÉ hacer) y luego se PLIEGA a solo el título.
                var objectiveCompact by remember { mutableStateOf(false) }
                LaunchedEffect(interiorObjective?.id) {
                    objectiveCompact = false
                    if (interiorObjective != null) {
                        delay(6000)
                        objectiveCompact = true
                    }
                }
                Column(
                    Modifier.fillMaxSize().systemBarsPadding().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    interiorObjective?.let { obj ->
                        ovh.gabrielhuav.pow.features.map_exterior.ui.components.ObjectivesWidget(
                            objective = obj,
                            done = false,
                            playerLocation = null,
                            compact = objectiveCompact
                        )
                    }
                    state.mission2HideRemainingSec?.let { secs ->
                        Text(
                            stringResource(R.string.zgame_hide_countdown, secs),
                            color = Color(0xFFFFCDD2),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .alpha(0.85f)
                                .background(Color(0xB33B0D1B), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // ─── CALIBRADOR DE AUTOS DEL ESTACIONAMIENTO (solo dev, lobby de campus) ──
            // Rota en grupo (orientación uniforme) y mueve ↑↓←→ los autos del lobby en vivo, para
            // encontrar los valores; luego se fijan como defaults en ParkedCarsLayer.
            ParkingTuneTool(
                room = room, active = parkingDesignerActive,
                angle = parkAngle, onAngle = { parkAngle = it },
                offX = parkOffX, onOffX = { parkOffX = it },
                offY = parkOffY, onOffY = { parkOffY = it },
                scale = parkScale, onScale = { parkScale = it },
                selfAngle = parkSelfAngle, onSelfAngle = { parkSelfAngle = it },
                flippedCount = parkFlipped.size, onClearFlips = { parkFlipped = emptySet() },
                onExport = { parkingExportLauncher.launch("parking_calibracion.json") },
                onClose = { parkingDesignerActive = false }
            )

            // ─── SELECTOR DEL BOTÓN "DISEÑADOR" (Colisiones/Waypoints | Estacionamiento) ──
            if (designerChooserOpen) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .background(Color(0xFF1E1E24), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Text("DISEÑADOR", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Button(
                            onClick = { designerChooserOpen = false; viewModel.toggleDesignerMode() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))
                        ) { Text("Colisiones / Waypoints", color = Color.White) }
                        Button(
                            onClick = { designerChooserOpen = false; parkingDesignerActive = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) { Text("Estacionamiento", color = Color.White) }
                        TextButton(onClick = { designerChooserOpen = false }) {
                            Text("Cancelar", color = Color.White)
                        }
                    }
                }
            }

            // ─── DIÁLOGO DE CONFIRMACIÓN DE SALIDA ──────────────
            if (state.showExitToLobbyDialog) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xAA000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .background(Color(0xFF1E1E24), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                            .padding(24.dp)
                    ) {
                        Text(stringResource(R.string.zgame_exit_lobby_title), color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            stringResource(R.string.zgame_exit_lobby_text),
                            color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { viewModel.dismissExitToLobby() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_no), color = Color.White, fontWeight = FontWeight.Bold) }
                            Button(
                                onClick = { viewModel.confirmExitToLobby() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.common_yes), color = Color.White, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ─── VICTORIA ───────────────────────────────────────
            if (state.showVictoryScreen) {
                Box(Modifier.fillMaxSize().background(Color(0xCC000000)), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.zgame_victory_title), color = Color(0xFFD4AF37), fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.zgame_victory_text), color = Color.White, fontSize = 16.sp)
                    }
                }
            }

            // ─── WASTED ─────────────────────────────────────────
            if (state.showWastedScreen) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) {
                    var wastedScale by remember { mutableFloatStateOf(0.5f) }
                    LaunchedEffect(Unit) {
                        animate(
                            initialValue = 0.5f,
                            targetValue = 1.3f,
                            animationSpec = tween(durationMillis = 3500, easing = LinearOutSlowInEasing)
                        ) { value, _ -> wastedScale = value }
                    }
                    Text(
                        text = "WASTED",
                        color = Color(0xFFD32F2F),
                        fontSize = 60.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Serif,
                        letterSpacing = 6.sp,
                        modifier = Modifier.scale(wastedScale)
                    )
                }
            }
        }

        // ─── BOTÓN DE CONFIGURACIÓN (siempre) + MENÚ DE OPCIONES ─
        // Arriba a la derecha: el botón de Ajustes SIEMPRE visible, y debajo el
        // menú desplegable con el resto de opciones (que no son controles). Se
        // declara AL FINAL (encima del HUD) para recibir los toques. En modo
        // diseñador la toolbar manda, así que solo dejamos Ajustes.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.background(Color.White.copy(alpha = 0.85f), CircleShape)
            ) {
                Icon(Icons.Default.Settings, stringResource(R.string.zgame_cd_settings), tint = Color.Black)
            }
            // En MODO DISEÑADOR, botón de SALIR SIEMPRE visible: la toolbar inferior puede quedar
            // recortada en pantallas bajas (sobre todo en MATRIZ, que tiene más filas), así que sin
            // esto el usuario se quedaba "atrapado" en el modo diseñador.
            if (state.designerMode) {
                IconButton(
                    onClick = { viewModel.toggleDesignerMode() },
                    modifier = Modifier.background(Color(0xFFD32F2F).copy(alpha = 0.92f), CircleShape)
                ) {
                    Icon(Icons.Default.ExitToApp, stringResource(R.string.ig_exit), tint = Color.White)
                }
            }
            if (!state.designerMode) {
                // "Elegir personaje" (selector de skin) vive en el menú de Opciones; el juego va
                // SIEMPRE en horizontal (este menú NO cambia la orientación).
                var optionsExpanded by remember { mutableStateOf(false) }
                OptionsMenu(
                    expanded = optionsExpanded,
                    onExpandedChange = { optionsExpanded = it },
                    openGroupId = null,
                    onOpenGroupChange = {},
                    entries = run {
                        // Misión 1 = cadena de salas ENCB del Modo Historia.
                        val inMission1 = room.id in ZombieRoomCatalog.ENCB_STORY_ROOM_IDS
                        val sChar = stringResource(R.string.wm_choose_character)
                        val sDesigner = stringResource(R.string.zgame_opt_designer)
                        val sExitMap = stringResource(R.string.zgame_opt_exit_map)
                        val sSaveGame = stringResource(R.string.wm_opt_save_game)
                        val sMissions = stringResource(R.string.wm_opt_missions)
                        buildList {
                            // "Elegir personaje" (selector de skin), movido aquí desde el botón suelto. Solo en Modo Dev.
                            if (developerMode) {
                                add(OptionMenuItem(sChar, Icons.Default.Person, Color(0xFFD91B5B)) { viewModel.toggleSkinSelector(true) })
                            }
                            // MODO HISTORIA: REGISTRO DE MISIONES también en interiores (el diálogo
                            // vive a nivel AppNavGraph). Solo en campaña (callback non-null).
                            onRequestMissionLog?.let { openLog ->
                                add(OptionMenuItem(sMissions, Icons.Default.LocationOn, Color(0xFFFFC107)) { openLog() })
                            }
                            // "Diseñador": solo en Modo Desarrollador. Abre un selector
                            // (Colisiones/Waypoints | Estacionamiento) en vez de ir directo.
                            if (developerMode) add(OptionMenuItem(sDesigner, Icons.Default.Architecture) { designerChooserOpen = true })
                            // MODO HISTORIA: guardar partida también desde interiores (selector de slots).
                            add(OptionMenuItem(sSaveGame, Icons.Default.Save) { onRequestSaveGame() })
                            // "Salir al mapa": en Misión 1 se oculta salvo en Modo Desarrollador.
                            if (developerMode || !inMission1) add(OptionMenuItem(sExitMap, Icons.Default.ExitToApp) { viewModel.exitToWorld() })
                        }
                    }
                )
            }
        }

        // ─── CINEMÁTICA ZOMBIE (se muestra al interactuar con la mano en el lobby) ──
        if (state.showZombieCinematic) {
            ZombiVideoPlayer(
                context = context,
                onDismiss = { viewModel.onZombieCinematicDismissed() }
            )
        }
        if (state.showSkinSelector) {
            SkinSelectorDialog(
                currentSkin    = state.selectedSkin,
                context        = context,
                onSkinSelected = { viewModel.selectSkin(it) },
                onDismiss      = { viewModel.toggleSkinSelector(false) },
                developerMode  = developerMode   // las skins de prueba (Lázaro + NPC) solo en Modo Dev
            )
        }
        // ─── TOOLBAR DEL DISEÑADOR ──────────────────────────────
        if (state.designerMode) {
            val isWaypoints = state.designerTarget == DesignerTarget.WAYPOINTS
            val gridRows = state.designerRows.size
            val gridCols = state.designerRows.maxOfOrNull { it.length } ?: 0
            DesignerToolbar(
                target = state.designerTarget,
                brush = state.designerBrush,
                dirty = state.designerDirty,
                roomName = room.displayName,
                hasSelectedDoor = state.selectedDoorIndex >= 0,
                gridCols = gridCols,
                gridRows = gridRows,
                onResize = viewModel::resizeDesignerMatrixBy,
                onSelectTarget = viewModel::setDesignerTarget,
                onBrush = viewModel::setDesignerBrush,
                onSave = { if (isWaypoints) viewModel.saveDesignerWaypoints() else viewModel.saveDesignerMatrix() },
                onReset = { if (isWaypoints) viewModel.resetDesignerWaypoints() else viewModel.resetDesignerMatrix() },
                onExport = {
                    if (isWaypoints) exportWpLauncher.launch("waypoints.json")
                    else exportLauncher.launch("collision_matrices.json")
                },
                onImport = {
                    if (isWaypoints) importWpLauncher.launch(arrayOf("application/json", "*/*"))
                    else importLauncher.launch(arrayOf("application/json", "*/*"))
                },
                onExit = viewModel::toggleDesignerMode,
                portrait = designerPortrait,
                onToggleOrientation = { designerPortrait = !designerPortrait },
                // Esquina inferior-IZQUIERDA por defecto (no centrado): así NO tapa el centro del
                // mapa al pintar la matriz. Es arrástrable (asa ⠿) y escalable (−/+).
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // 🆕 TUTORIAL de controles de INTERIORES (optativo, 2026-07-11): se OFRECE una sola vez
        // al entrar por primera vez a un interior. Se puede re-ver en Ajustes → Controles.
        if (!state.designerMode) {
            ovh.gabrielhuav.pow.features.settings.ui.ControlsTutorialFirstRun(interior = true)
        }
    }
}

/**
 * Barra de herramientas del Modo Diseñador de la matriz de colisión.
 * Pinta paredes / borra, guarda (persiste en collision_matrices.json y aplica en
 * caliente), resetea, y exporta/importa el JSON por SAF para copiarlo al servidor.
 */
