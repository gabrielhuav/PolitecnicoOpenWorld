package ovh.gabrielhuav.pow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import ovh.gabrielhuav.pow.features.interiores.escom.ui.AuditorioScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.BibliotecaScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.CafeteriaScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.CanchasFutbolScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.EdificioScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.EstacionamientoScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.MetroStationInteriorScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.PalapasScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.DeportivoBeisScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.DeportivoFutbolScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.CollectiblesScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.MainMenuScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.FesInteriorScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.StoryModeScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.StoryIntroScreen
import ovh.gabrielhuav.pow.domain.models.SchoolCatalog
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.WorldMapScreen
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// REFACTOR: extensiones del VM (WorldMapProviders.kt) → requieren import explícito.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.requestMapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setMapProvider
// MODO HISTORIA: guardado/carga de la partida (JSON). Las extensiones del VM viven
// en WorldMapSaveGame.kt y requieren import explícito desde fuera del paquete viewmodel.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.saveGame
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.loadGame
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setCampaignObjective
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.features.settings.ui.SettingsScreen
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsViewModel
import ovh.gabrielhuav.pow.features.interiores.zombies.ui.ZombieGameScreen
import ovh.gabrielhuav.pow.ui.theme.PolitecnicoOpenWorldTheme
import java.io.File
import ovh.gabrielhuav.pow.features.interiores.shinecto.ui.EasterEggDiscoveryDialog
import ovh.gabrielhuav.pow.features.interiores.shinecto.ui.ShineCTOScreen

// Spawn fijo: coordenadas del punto de teletransporte "ESCOM" (ver TeleportCatalog).
// El juego SIEMPRE arranca en ESCOM, sin depender del GPS real del dispositivo.
private const val SPAWN_ESCOM_LAT = 19.504603
private const val SPAWN_ESCOM_LON = -99.145985

class MainActivity : ComponentActivity() {

    // i18n: aplica el idioma elegido (Ajustes) envolviendo el Context base antes de
    // que se inflen los recursos. "" = idioma del sistema. Ver i18n/LocaleHelper.kt.
    override fun attachBaseContext(newBase: Context) {
        val lang = ovh.gabrielhuav.pow.data.repository.SettingsRepository(newBase).getLanguage()
        super.attachBaseContext(ovh.gabrielhuav.pow.i18n.LocaleHelper.wrap(newBase, lang))
    }

    private val worldMapViewModel: WorldMapViewModel by viewModels {
        WorldMapViewModel.Factory(this)
    }

    // Instanciamos el ViewModel de los ajustes usando su Factory
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(this)
    }

    private val collectiblesViewModel: CollectiblesViewModel by viewModels {
        CollectiblesViewModel.Factory(this)
    }
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Persistencia de la partida del MODO HISTORIA (campaña). Es el punto de DI:
    // MainActivity escribe el guardado al INICIAR/CARGAR (las pantallas solo emiten intención).
    private val campaignRepository: CampaignRepository by lazy { CampaignRepository(this) }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            worldMapViewModel.updateInitialLocation(SPAWN_ESCOM_LAT, SPAWN_ESCOM_LON)
        }
    }

    // OPT memoria gama baja (≤2 GB): cuando el sistema avisa de presión de memoria,
    // soltamos las cachés de sprites (NPCs/vehículos/patrullas/zombis). Se regeneran bajo
    // demanda; evita que una sesión larga acumule bitmaps hasta el OOM en equipos con poca
    // RAM. No altera el juego: solo recicla memoria reconstruible.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager.clearCaches()
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager.clearCaches()
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceSpriteManager.clearCaches()
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceNpcSpriteManager.clearCaches()
            ovh.gabrielhuav.pow.features.map_exterior.ui.components.MapZombieSpriteManager.clearCaches()
            ovh.gabrielhuav.pow.features.interiores.zombies.ui.ZombieSpriteManager.clearCaches()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkPermissionsAndFetchLocation()

        setContent {
            PolitecnicoOpenWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 1. EL ORQUESTADOR GLOBAL: Sincroniza los ajustes en segundo plano
                    // Esto evita recomposiciones destructivas al navegar.
                    val settingsState by settingsViewModel.state.collectAsState()
                    var providerInitialized by remember { mutableStateOf(false) }
                    LaunchedEffect(settingsState.mapProvider, settingsState.showCacheWidget, settingsState.showFpsWidget, settingsState.showZoomWidget, settingsState.showSpeedometer, settingsState.showRoadNetwork) {
                        if (!providerInitialized) {
                            // Arranque: aplica el proveedor guardado de inmediato (sin aviso).
                            worldMapViewModel.setMapProvider(settingsState.mapProvider)
                            providerInitialized = true
                        } else {
                            // Cambios posteriores: precarga en segundo plano y avisa para cambiar.
                            worldMapViewModel.requestMapProvider(settingsState.mapProvider)
                        }
                        worldMapViewModel.toggleCacheWidget(settingsState.showCacheWidget)
                        worldMapViewModel.toggleFpsWidget(settingsState.showFpsWidget)
                        worldMapViewModel.toggleZoomWidget(settingsState.showZoomWidget)
                        worldMapViewModel.toggleSpeedometer(settingsState.showSpeedometer)
                        worldMapViewModel.setShowRoadNetwork(settingsState.showRoadNetwork)
                    }

                    val navController = rememberNavController()

                    // Diálogo de GUARDAR (selector de slots) a nivel de Activity: lo disparan
                    // tanto el mapa global como los interiores (callback onRequestSaveGame),
                    // porque el estado vive en el worldMapViewModel (Activity-scoped).
                    var showSaveDialog by remember { mutableStateOf(false) }
                    if (showSaveDialog) {
                        ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsDialog(
                            title = "Guardar partida",
                            summaries = SaveGameRepository(this@MainActivity).summaries(),
                            mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.SAVE,
                            onPick = { slot ->
                                showSaveDialog = false
                                worldMapViewModel.saveGame(this@MainActivity, slot)
                                android.widget.Toast.makeText(this@MainActivity, "Partida guardada (slot $slot)", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onDismiss = { showSaveDialog = false }
                        )
                    }

                    NavHost(navController = navController, startDestination = "main_menu") {

                        composable(
                            route = "main_menu",
                            exitTransition = {
                                fadeOut(animationSpec = tween(700)) +
                                        scaleOut(animationSpec = tween(700), targetScale = 1.2f)
                            }
                        ) {
                            MainMenuScreen(
                                onNavigateToMap = { isMultiplayer, playerName ->
                                    // MUNDO LIBRE (sin campaña): no es una sesión de Modo Historia,
                                    // así que NO se auto-guarda al salir.
                                    worldMapViewModel.inCampaign = false
                                    if (isMultiplayer && !playerName.isNullOrBlank()) {
                                        // Usando la variable de entorno de Gradle (BuildConfig)
                                        worldMapViewModel.connectToMultiplayer(BuildConfig.MULTIPLAYER_SERVER_URL, playerName)
                                    } else {
                                        worldMapViewModel.disconnectFromMultiplayer()
                                    }

                                    navController.navigate("world_map") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                onNavigateToCollectibles = {
                                    navController.navigate("collectibles")
                                },
                                onNavigateToStory = {
                                    navController.navigate("story_mode")
                                }
                            )
                        }

                        // ─── MODO HISTORIA / Campaña ──────────────────────────────
                        // Prólogo + selección de escuela. "COMENZAR" lleva a la intro
                        // ("Listo para Iniciar"); "CARGAR PARTIDA" reanuda directo en la
                        // escuela guardada. ESCOM es la única jugable por ahora.
                        composable(route = "story_mode") {
                            // CARGAR PARTIDA: muestra el selector de slots. Al elegir un slot con
                            // partida, restaura el estado completo (posición/vida/buscado/vehículo/
                            // skin/NPCs/objetivo) y entra al mundo.
                            var showLoadDialog by remember { mutableStateOf(false) }
                            StoryModeScreen(
                                onStartCampaign = { school ->
                                    navController.navigate("story_intro/${school.id}")
                                },
                                onLoadCampaign = { showLoadDialog = true },
                                onBack = { navController.popBackStack() }
                            )
                            if (showLoadDialog) {
                                ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsDialog(
                                    title = "Cargar partida",
                                    summaries = SaveGameRepository(this@MainActivity).summaries(),
                                    mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.LOAD,
                                    onPick = { slot ->
                                        showLoadDialog = false
                                        worldMapViewModel.disconnectFromMultiplayer()
                                        if (worldMapViewModel.loadGame(this@MainActivity, slot)) {
                                            navController.navigate("world_map") {
                                                popUpTo("main_menu") { inclusive = true }
                                            }
                                        }
                                    },
                                    onDismiss = { showLoadDialog = false }
                                )
                            }
                        }

                        // ─── MODO HISTORIA · Intro ("Listo para Iniciar") ─────────
                        // Placeholder narrativo. Al INICIAR, GUARDA la partida (para que
                        // "CARGAR PARTIDA" funcione luego) y arranca el mundo en la escuela.
                        composable(
                            route = "story_intro/{schoolId}",
                            arguments = listOf(
                                androidx.navigation.navArgument("schoolId") {
                                    type = androidx.navigation.NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val schoolId = backStackEntry.arguments?.getString("schoolId")
                            val school = SchoolCatalog.schools.firstOrNull { it.id == schoolId }
                                ?: SchoolCatalog.default
                            StoryIntroScreen(
                                school = school,
                                onBegin = {
                                    // COMENZAR partida NUEVA: ocupa el primer slot VACÍO (para no
                                    // pisar otras partidas) y fija el objetivo de la Misión 1.
                                    // El estado se guardará al salir o con "Guardar partida".
                                    campaignRepository.saveCampaign(school.id)
                                    val slot = SaveGameRepository(this@MainActivity).firstEmptySlot()
                                    SaveGameRepository(this@MainActivity).clear(slot)
                                    worldMapViewModel.campaignSchoolId = school.id
                                    worldMapViewModel.campaignSlot = slot
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    worldMapViewModel.setStorySpawn(school.latitude, school.longitude)
                                    worldMapViewModel.setCampaignObjective(ovh.gabrielhuav.pow.domain.models.MissionCatalog.first)
                                    // Tras el último panel de la intro (IntroPOW8), la transición
                                    // entra al PRIMER interior de la campaña: el Lobby de la ENCB.
                                    // popUpTo main_menu inclusive DESTRUYE la pantalla de la intro
                                    // (story_intro) y libera los bitmaps IntroPOW1..8 de memoria.
                                    navController.navigate("encb_lobby") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // ─── MODO HISTORIA · Lobby ENCB (primer interior JUGABLE) ──
                        // Reusa el MOTOR DE INTERIORES (ZombieGameScreen) con la sala
                        // `encb_lobby` (zona segura, sin zombis/mano/waypoints; ver
                        // ZombieRoomCatalog). Mismos controles, cámara, colisiones y aura
                        // que el lobby de ESCOM. Es una sesión de campaña offline
                        // (onBegin ya hizo disconnectFromMultiplayer). Al salir (menú de
                        // Opciones → "Salir al mapa") arranca el open world ya configurado
                        // (spawn/objetivo/slot); popUpTo encb_lobby inclusive libera el lobby.
                        composable(route = "encb_lobby") {
                            val wmState by worldMapViewModel.uiState.collectAsState()
                            ZombieGameScreen(
                                onExitToWorld = {
                                    navController.navigate("world_map") {
                                        popUpTo("encb_lobby") { inclusive = true }
                                    }
                                },
                                isMultiplayer = wmState.isMultiplayer,
                                playerName = wmState.playerName,
                                onNavigateToSettings = { navController.navigate("settings") },
                                debugHitboxes = false,
                                startRoomId = ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ENCB_LOBBY_ID,
                                onRequestSaveGame = { showSaveDialog = true },
                                // Waypoint final de ENCB_LAB2 → reanuda la narrativa (cómic
                                // ENCB_OUTRO). popUpTo encb_lobby inclusive libera el motor de
                                // interiores (la cadena de salas) antes de mostrar el cómic.
                                onPlayStoryOutro = {
                                    navController.navigate("story_outro") {
                                        popUpTo("encb_lobby") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ─── MODO HISTORIA · Outro (2ª parte de la intro: IntroPOW9..11) ──
                        // Reusa el visor de cómic (StoryIntroScreen) con la secuencia
                        // ENCB_OUTRO. Al ser otra pantalla, la UI de juego (joysticks/objetivo)
                        // queda oculta por completo. Al terminar el último panel (IntroPOW11) o
                        // saltar, se entra al MUNDO LIBRE ya configurado en la campaña
                        // (spawn/objetivo/slot fijados al INICIAR la intro).
                        composable(route = "story_outro") {
                            StoryIntroScreen(
                                school = SchoolCatalog.default,
                                sequenceId = ovh.gabrielhuav.pow.domain.models.StoryComicCatalog.ENCB_OUTRO_ID,
                                onBegin = {
                                    // SPAWN ENCB EXCLUSIVO DEL MODO HISTORIA: solo aquí, al
                                    // terminar el outro (IntroPOW11), el jugador aparece en la
                                    // ENCB. setStorySpawn fija la posición y activa inCampaign=true.
                                    worldMapViewModel.setStorySpawn(19.5001588, -99.1450298)
                                    navController.navigate("world_map") {
                                        popUpTo("story_outro") { inclusive = true }
                                    }
                                },
                                onBack = {
                                    // Misma transición narrativa (saltar/volver el outro): ENCB.
                                    worldMapViewModel.setStorySpawn(19.5001588, -99.1450298)
                                    navController.navigate("world_map") {
                                        popUpTo("story_outro") { inclusive = true }
                                    }
                                }
                            )
                        }


                        // Registramos la ruta de Ajustes
                        composable(route = "settings") {
                            val settingsState by settingsViewModel.state.collectAsState()

                            SettingsScreen(
                                state = settingsState,
                                onCategorySelected = { settingsViewModel.selectCategory(it) },
                                onMapProviderChanged = { settingsViewModel.changeMapProvider(it) },
                                onCacheToggled = { settingsViewModel.toggleCacheWidget(it) },
                                onFpsToggled = { settingsViewModel.toggleFpsWidget(it) },
                                onZoomWidgetToggled = {
                                    settingsViewModel.toggleZoomWidget(it)
                                    worldMapViewModel.toggleZoomWidget(it)
                                },
                                onSpeedometerToggled = {
                                    settingsViewModel.toggleSpeedometer(it)
                                    worldMapViewModel.toggleSpeedometer(it)
                                },
                                onRoadNetworkToggled = { settingsViewModel.toggleRoadNetwork(it) },
                                onControlTypeChanged = { settingsViewModel.changeControlType(it) },
                                onControlsScaleChanged = { settingsViewModel.changeControlsScale(it) },
                                onSwapControlsToggled = { settingsViewModel.toggleSwapControls(it) },
                                // Jugabilidad: persisten en Ajustes Y se aplican en vivo al mapa.
                                onNpcDensityChanged = {
                                    settingsViewModel.changeNpcDensity(it)
                                    worldMapViewModel.setNpcDensity(it)
                                },
                                onNpcEmojiLodToggled = {
                                    settingsViewModel.toggleNpcEmojiLod(it)
                                    worldMapViewModel.setNpcEmojiLod(it)
                                },
                                onNpcFullEmojiToggled = {
                                    settingsViewModel.toggleNpcFullEmoji(it)
                                    worldMapViewModel.setNpcFullEmoji(it)
                                },
                                onNavigateBack = {
                                    // Descartar cambios de controles no guardados al salir.
                                    settingsViewModel.discardControlsChanges()
                                    if (navController.currentDestination?.route == "settings") {
                                        navController.popBackStack()
                                    }
                                },
                                onSaveClicked = {
                                    // 1. Sincronizar temporales → committeados y persistir.
                                    settingsViewModel.saveControlsSettings()

                                    // 2. Notificar al mapa con los valores recién guardados (temporales,
                                    //    que son los que acaban de pasar a ser los definitivos).
                                    worldMapViewModel.updateControlSettings(
                                        type = settingsState.tempControlType,
                                        scale = settingsState.tempControlsScale,
                                        swap = settingsState.tempSwapControls
                                    )

                                    android.widget.Toast.makeText(this@MainActivity, getString(R.string.settings_controls_saved), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                // Lógica para regresar al menú principal limpiando el mapa
                                onExitToMainMenu = {
                                    // Descartar cambios de controles no guardados al salir.
                                    settingsViewModel.discardControlsChanges()
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                // i18n: idioma actual + cambio (persiste; SettingsScreen recrea la Activity).
                                currentLanguage = settingsState.language,
                                onLanguageChanged = { tag -> settingsViewModel.changeLanguage(tag) }
                            )
                        }

                        composable(
                            route = "world_map",
                            // RESTAURAMOS LA ANIMACIÓN DE ENTRADA
                            // Esto evita que el motor gráfico se congele al cambiar de pantalla.
                            enterTransition = {
                                fadeIn(animationSpec = tween(1000)) +
                                        scaleIn(animationSpec = tween(1000), initialScale = 1.2f)
                            }
                        ) {
                            // Lógica compartida para volver al menú principal
                            val navigateBackToMainMenu = remember(worldMapViewModel, navController) {
                                {
                                    // AUTO-GUARDADO: si estamos en Modo Historia, persistimos el
                                    // estado completo en el slot activo antes de volver al menú.
                                    if (worldMapViewModel.inCampaign) worldMapViewModel.saveGame(this@MainActivity, worldMapViewModel.campaignSlot)
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    navController.navigate("main_menu") {
                                        popUpTo("world_map") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }

                            // Diálogo de confirmación para salir del mapa
                            var showExitDialog by remember { mutableStateOf(false) }

                            if (showExitDialog) {
                                AlertDialog(
                                    onDismissRequest = { showExitDialog = false },
                                    title = { Text(stringResource(R.string.exit_dialog_title)) },
                                    text = { Text(stringResource(R.string.exit_dialog_text)) },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showExitDialog = false
                                            navigateBackToMainMenu()
                                        }) {
                                            Text(stringResource(R.string.exit_dialog_confirm))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = {
                                            showExitDialog = false
                                            // AUTO-GUARDADO también al cerrar la app desde el diálogo.
                                            if (worldMapViewModel.inCampaign) worldMapViewModel.saveGame(this@MainActivity, worldMapViewModel.campaignSlot)
                                            worldMapViewModel.disconnectFromMultiplayer()
                                            this@MainActivity.finish()
                                        }) {
                                            Text(stringResource(R.string.exit_dialog_dismiss))
                                        }
                                    }
                                )
                            }

                            // Interceptar el botón de atrás nativo
                            BackHandler(enabled = !showExitDialog) {
                                showExitDialog = true
                            }

                            WorldMapScreen(
                                context = this@MainActivity,
                                viewModel = worldMapViewModel,
                                onNavigateToMainMenu = navigateBackToMainMenu,
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                },
                                // Callback que se dispara cuando el video de ZombiHand
                                // termina y hay un edificio destino pendiente.
                                onNavigateToInterior = { routeName ->
                                    navController.navigate(routeName)
                                },
                                // "Guardar partida" → abre el selector de slots (a nivel Activity).
                                onRequestSaveGame = { showSaveDialog = true }
                            )
                            // ─── ShineCTO: navegar al interior cuando el VM lo indique ───
                            val uiState by worldMapViewModel.uiState.collectAsState()

                            LaunchedEffect(uiState.navigateToShineCTO) {
                                if (uiState.navigateToShineCTO) {
                                    worldMapViewModel.consumeNavigateToShineCTO()
                                    navController.navigate("shinecto_interior")
                                }
                            }

                            // NUEVO BLOQUE: Navegar al minijuego tras el fade de la puerta
                            LaunchedEffect(uiState.escomDoorFadeComplete) {
                                if (uiState.escomDoorFadeComplete) {
                                    val destination = worldMapViewModel.consumeEscomDoorNavigation() ?: "interiores_zombies"
                                    navController.navigate(destination)
                                }
                            }

                            // ─── Metro Stations Fade ───────────────────────────────────
                            LaunchedEffect(uiState.metroFadeCompleteStation) {
                                val station = uiState.metroFadeCompleteStation
                                if (station != null) {
                                    worldMapViewModel.consumeMetroFadeComplete()
                                    navController.navigate("metro_station_interior/${station.name}")
                                }
                            }

                            // ─── ShineCTO: dialog de descubrimiento ───────────────────────
                            if (uiState.showShineCTODiscovery) {
                                EasterEggDiscoveryDialog(
                                    onConfirm = { worldMapViewModel.onShineCTODiscoveryConfirmed() }
                                )
                            }
                        }

                        composable(route = "collectibles") {
                            CollectiblesScreen(
                                viewModel = collectiblesViewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // ─── INTERIORES ZOMBIE ────────────────────────────────────
                        // Cada edificio es un destino independiente. Al hacer back o
                        // tocar el botón de salir, se hace popBackStack hasta world_map
                        // (sin inclusive) para preservar el estado del open world.
                        composable(route = "interior_auditorio") {
                            AuditorioScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_biblioteca") {
                            BibliotecaScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_cafeteria") {
                            CafeteriaScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_edificio") {
                            EdificioScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_estacionamiento") {
                            EstacionamientoScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_palapas") {
                            PalapasScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_canchas_futbol") {
                            CanchasFutbolScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_deportivo_beis") {
                            DeportivoBeisScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_deportivo_futbol") {
                            DeportivoFutbolScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        composable(route = "interior_fes") {
                            FesInteriorScreen(
                                onExit = { navController.popBackStack("world_map", inclusive = false) }
                            )
                        }
                        
                        // ─── ESTACIONES METRO ──────────────────────────────────────
                        composable(
                            route = "metro_station_interior/{stationName}?spawnX={spawnX}&spawnY={spawnY}",
                            arguments = listOf(
                                androidx.navigation.navArgument("stationName") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("spawnX") { type = androidx.navigation.NavType.FloatType; defaultValue = -1f },
                                androidx.navigation.navArgument("spawnY") { type = androidx.navigation.NavType.FloatType; defaultValue = -1f }
                            )
                        ) { backStackEntry ->
                            val stationName = backStackEntry.arguments?.getString("stationName") ?: "Desconocida"
                            val spawnX = backStackEntry.arguments?.getFloat("spawnX") ?: -1f
                            val spawnY = backStackEntry.arguments?.getFloat("spawnY") ?: -1f
                            MetroStationInteriorScreen(
                                stationName = stationName,
                                spawnX = spawnX,
                                spawnY = spawnY,
                                onExit = { currentStation ->
                                    worldMapViewModel.teleportToMetroStation(currentStation)
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                onTeleportToStation = { newStation, x, y ->
                                    navController.navigate("metro_station_interior/$newStation?spawnX=$x&spawnY=$y") {
                                        popUpTo("world_map") { inclusive = false }
                                    }
                                }
                            )
                        }

                        // ─── INTERIORES (motor de salas) ──────────────────────────
                        // Salas con IA de zombis, combate y pantalla de victoria. Es el
                        // sistema de INTERIORES de cualquier edificio: el arg opcional
                        // `startRoom` elige la sala inicial (lobby de ESCOM por defecto;
                        // las puertas FES pasan `fes_interior`). Al salir, popBackStack
                        // hasta world_map para preservar el open world.
                        composable(
                            route = "interiores_zombies?startRoom={startRoom}",
                            arguments = listOf(
                                androidx.navigation.navArgument("startRoom") {
                                    type = androidx.navigation.NavType.StringType
                                    defaultValue = ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID
                                }
                            )
                        ) { backStackEntry ->
                            val wmState by worldMapViewModel.uiState.collectAsState()
                            val startRoom = backStackEntry.arguments?.getString("startRoom")
                                ?: ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID
                            ZombieGameScreen(
                                onExitToWorld = {
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                isMultiplayer = wmState.isMultiplayer,
                                playerName = wmState.playerName,
                                onNavigateToSettings = { navController.navigate("settings") },
                                debugHitboxes = false,
                                startRoomId = startRoom,
                                // "Guardar partida" disponible también en interiores (mismo selector
                                // de slots; el estado del mundo se conserva en el worldMapViewModel).
                                onRequestSaveGame = { showSaveDialog = true }
                            )
                        }

                        composable(route = "shinecto_interior") {
                            ShineCTOScreen(
                                onExitToWorld = {
                                    navController.popBackStack("world_map", inclusive = false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun configureOsmdroid() {
        Configuration.getInstance().apply {
            load(this@MainActivity, PreferenceManager.getDefaultSharedPreferences(this@MainActivity))
            userAgentValue = packageName
            val osmDir = File(filesDir, "osmdroid")
            if (osmDir.mkdirs() || osmDir.exists()) {
                osmdroidBasePath  = osmDir
                osmdroidTileCache = File(osmDir, "tiles")
            }
            expirationOverrideDuration  = 1000L * 60 * 60 * 24 * 30
            tileFileSystemCacheMaxBytes = 500L * 1024 * 1024
            tileDownloadThreads         = 4
            tileDownloadMaxQueueSize    = 50
        }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            requestPermissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun fetchCurrentLocation() {
        // SPAWN FIJO EN ESCOM: el juego siempre arranca en el punto del teletransporte
        // "ESCOM" (ver TeleportCatalog), sin depender del GPS real del dispositivo. Antes
        // se spawneaba en la ubicación GPS; ahora ESCOM es el punto de inicio canónico.
        worldMapViewModel.updateInitialLocation(SPAWN_ESCOM_LAT, SPAWN_ESCOM_LON)
    }
}