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
import android.content.pm.ActivityInfo
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
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
import ovh.gabrielhuav.pow.features.interiores.escom.ui.MetrobusStationInteriorScreen
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
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.retryCampaignMission
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setCampaignObjective
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumePendingMission2Intro
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.startMission2
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

    // Autenticación Google + Firebase. Gestiona login, token (para el handshake WS) y borrado de cuenta.
    private val authManager by lazy { ovh.gabrielhuav.pow.data.auth.AuthManager(this) }

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

    // Al pasar la app a segundo plano (botón home, multitarea, pantalla apagada, etc.):
    //  1. AUTO-GUARDADO del Modo Historia: si estamos en campaña, persistimos el estado
    //     completo en el slot activo para no perder el progreso (antes solo se guardaba
    //     al salir explícitamente al menú → si cerrabas la app empezabas de 0).
    //  2. Detenemos TODO el audio (incl. la música del Modo Historia, que seguía sonando
    //     en segundo plano). Se reanuda en onResume.
    override fun onPause() {
        super.onPause()
        if (worldMapViewModel.inCampaign) {
            worldMapViewModel.saveGame(this, worldMapViewModel.campaignSlot, auto = true)
        }
        ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this).pauseAllForBackground()
    }

    // Al volver al primer plano: reanuda las pistas que estaban sonando antes de salir.
    override fun onResume() {
        super.onResume()
        ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this).resumeAllFromBackground()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureOsmdroid()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        checkPermissionsAndFetchLocation()
        // Si ya había sesión de Google/Firebase (app reabierta), repuebla AuthSession + refresca token.
        authManager.restoreSession()

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
                    LaunchedEffect(settingsState.mapProvider, settingsState.showCacheWidget, settingsState.showFpsWidget, settingsState.showZoomWidget, settingsState.showSpeedometer, settingsState.showCoordsWidget, settingsState.showRoadNetwork) {
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
                        worldMapViewModel.toggleCoordsWidget(settingsState.showCoordsWidget)
                        worldMapViewModel.setShowRoadNetwork(settingsState.showRoadNetwork)
                    }

                    // CONTROLES (D-pad/joystick, escala, swap): se aplican EN VIVO al GUARDARLOS en
                    // Ajustes — `settingsState` ya trae los valores COMMITTEADOS por saveControlsSettings,
                    // así que no hay que salir al menú y volver a entrar para que el cambio surta efecto.
                    LaunchedEffect(settingsState.controlType, settingsState.controlsScale, settingsState.swapControls) {
                        worldMapViewModel.updateControlSettings(
                            settingsState.controlType,
                            settingsState.controlsScale,
                            settingsState.swapControls
                        )
                    }

                    val navController = rememberNavController()

                    // ORIENTACIÓN: el JUEGO (mapa global, interiores y cómics) va SIEMPRE en
                    // horizontal; solo los menús (main_menu, story_mode, settings, collectibles)
                    // permiten vertical. ÚNICA fuente de verdad: por DESTINO de navegación (evita
                    // carreras de dispose entre pantallas).
                    DisposableEffect(navController) {
                        val portraitRoutes = setOf("main_menu", "story_mode", "settings", "collectibles")
                        val listener = NavController.OnDestinationChangedListener { _, destination, arguments ->
                            val route = destination.route
                            // AJUSTES abierto DESDE EL JUEGO (fromGame=true) debe permanecer
                            // horizontal como el resto del juego; abierto desde el menú sí
                            // permite vertical. El arg llega en el Bundle del destino.
                            val fromGame = arguments?.getBoolean("fromGame") == true
                            val isMenu = route != null && !fromGame && portraitRoutes.any {
                                route == it || route.startsWith("$it/") || route.startsWith("$it?")
                            }
                            this@MainActivity.requestedOrientation =
                                if (isMenu) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            if (isMenu) {
                                // Al volver a un menú, corta TODA la música de fondo del juego/cómic
                                // (MediaPlayers en loop), que se quedaba sonando al salir.
                                val sm = ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity)
                                sm.stopInvestigarMusic(); sm.stopLugarSeguroMusic(); sm.stopMainMusic()
                                sm.stopPrankedyRemixMusic(); sm.stopAllStorySounds()
                            }
                        }
                        navController.addOnDestinationChangedListener(listener)
                        onDispose { navController.removeOnDestinationChangedListener(listener) }
                    }

                    // Diálogo de GUARDAR (selector de slots) a nivel de Activity: lo disparan
                    // tanto el mapa global como los interiores (callback onRequestSaveGame),
                    // porque el estado vive en el worldMapViewModel (Activity-scoped).
                    var showSaveDialog by remember { mutableStateOf(false) }
                    if (showSaveDialog) {
                        ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsDialog(
                            title = "Guardar partida",
                            summariesProvider = { SaveGameRepository(this@MainActivity).summaries() },
                            mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.SAVE,
                            onPick = { slot ->
                                showSaveDialog = false
                                worldMapViewModel.saveGame(this@MainActivity, slot)
                                android.widget.Toast.makeText(this@MainActivity, "Partida guardada (slot $slot)", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { slot -> SaveGameRepository(this@MainActivity).clear(slot) },
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
                                    worldMapViewModel.currentInteriorRoomId = null
                                    // MUNDO LIBRE no tiene objetivo de campaña: lo limpiamos para que
                                    // el cuadro de OBJETIVO no quede colgado del Modo Historia.
                                    worldMapViewModel.setCampaignObjective(null)
                                    // La música es exclusiva de la misión: en MUNDO LIBRE no debe sonar.
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).apply {
                                        stopInvestigarMusic(); stopLugarSeguroMusic(); stopMainMusic()
                                    }
                                    if (isMultiplayer && !playerName.isNullOrBlank()) {
                                        // Refresca el ID token (caduca ~1 h) ANTES del handshake y luego
                                        // conecta. Sin sesión, refreshToken responde null al instante y
                                        // se conecta en modo anónimo igualmente.
                                        authManager.refreshToken {
                                            worldMapViewModel.connectToMultiplayer(BuildConfig.MULTIPLAYER_SERVER_URL, playerName)
                                        }
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
                                },
                                authManager = authManager
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
                            // COMENZAR: antes de la intro se elige el SLOT MANUAL donde quedará la
                            // partida nueva (los 2 slots de auto-guardado salen deshabilitados).
                            var newGameSchool by remember { mutableStateOf<ovh.gabrielhuav.pow.domain.models.CampaignSchool?>(null) }
                            // PARTIDA NUEVA: PRIMERO se elige el PERSONAJE; al elegir se fija la skin
                            // y se continúa al selector de slot (newGameSchool).
                            var charPickSchool by remember { mutableStateOf<ovh.gabrielhuav.pow.domain.models.CampaignSchool?>(null) }
                            StoryModeScreen(
                                onStartCampaign = { school -> charPickSchool = school },
                                onLoadCampaign = { showLoadDialog = true },
                                onBack = { navController.popBackStack() }
                            )
                            // Selector de personaje (Hombre/Mujer/No binario; LÁZARO solo en Modo Dev).
                            charPickSchool?.let { school ->
                                val devModeChar = remember {
                                    ovh.gabrielhuav.pow.data.repository.SettingsRepository(this@MainActivity).getDeveloperMode()
                                }
                                ovh.gabrielhuav.pow.features.main_menu.ui.NewGameCharacterDialog(
                                    context = this@MainActivity,
                                    includeLazaro = devModeChar,
                                    onPick = { skin ->
                                        worldMapViewModel.selectSkin(skin)
                                        charPickSchool = null
                                        newGameSchool = school   // continúa al selector de slot
                                    },
                                    onDismiss = { charPickSchool = null }
                                )
                            }
                            newGameSchool?.let { school ->
                                ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsDialog(
                                    title = "Nueva partida · elige slot",
                                    summariesProvider = { SaveGameRepository(this@MainActivity).summaries() },
                                    mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.SAVE,
                                    onDelete = { slot -> SaveGameRepository(this@MainActivity).clear(slot) },
                                    onPick = { slot ->
                                        newGameSchool = null
                                        navController.navigate("story_intro/${school.id}?slot=$slot")
                                    },
                                    onDismiss = { newGameSchool = null },
                                    // Nueva partida: oculta los 2 slots de autoguardado (no son
                                    // seleccionables) para que el usuario no intente picarlos.
                                    hideAutoSlots = true
                                )
                            }
                            if (showLoadDialog) {
                                ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsDialog(
                                    title = "Cargar partida",
                                    summariesProvider = { SaveGameRepository(this@MainActivity).summaries() },
                                    mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.LOAD,
                                    onDelete = { slot -> SaveGameRepository(this@MainActivity).clear(slot) },
                                    onPick = { slot ->
                                        showLoadDialog = false
                                        worldMapViewModel.disconnectFromMultiplayer()
                                        if (worldMapViewModel.loadGame(this@MainActivity, slot)) {
                                            // El mundo siempre queda configurado (loadGame fijó spawn/estado).
                                            navController.navigate("world_map") {
                                                popUpTo("main_menu") { inclusive = true }
                                            }
                                            // Si la partida se guardó DENTRO de un interior, reentramos a esa
                                            // sala (sobre world_map, que queda en el backstack para "Salir al mapa").
                                            val roomId = worldMapViewModel.currentInteriorRoomId
                                            if (roomId != null) {
                                                // FIX: al CARGAR en un interior (p. ej. ENCB) la música no
                                                // sonaba (la entrada normal sí la arranca). Arrancamos el
                                                // tema "investigar" (interiores) antes de navegar a la sala.
                                                ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).playInvestigarMusic()
                                                navController.navigate("interiores_zombies?startRoom=$roomId")
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
                            route = "story_intro/{schoolId}?slot={slot}",
                            arguments = listOf(
                                androidx.navigation.navArgument("schoolId") {
                                    type = androidx.navigation.NavType.StringType
                                },
                                androidx.navigation.navArgument("slot") {
                                    type = androidx.navigation.NavType.IntType
                                    defaultValue = -1   // -1 = no se eligió slot manual
                                }
                            )
                        ) { backStackEntry ->
                            val schoolId = backStackEntry.arguments?.getString("schoolId")
                            val school = SchoolCatalog.schools.firstOrNull { it.id == schoolId }
                                ?: SchoolCatalog.default
                            // Slot MANUAL elegido al COMENZAR (donde quedará esta partida nueva).
                            val chosenSlot = backStackEntry.arguments?.getInt("slot") ?: -1
                            StoryIntroScreen(
                                school = school,
                                onBegin = {
                                    // COMENZAR partida NUEVA: el AUTO-GUARDADO usa los 2 slots reservados
                                    // (rotando). Limpiamos esos 2 para empezar fresco. Además, si el jugador
                                    // eligió un SLOT MANUAL, guardamos ahí la partida inicial. Fija la Misión 1.
                                    campaignRepository.saveCampaign(school.id)
                                    SaveGameRepository(this@MainActivity).clearAutoSlots()
                                    worldMapViewModel.campaignSchoolId = school.id
                                    worldMapViewModel.campaignSlot = SaveGameRepository.AUTO_SLOTS.first()
                                    // La campaña ARRANCA en el interior del Lobby de la ENCB, así que
                                    // el guardado inicial debe apuntar AHÍ (no al mapa global); si no,
                                    // al cargar esa partida te mandaba al mundo en vez del interior.
                                    worldMapViewModel.currentInteriorRoomId =
                                        ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ENCB_LOBBY_ID
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    worldMapViewModel.setStorySpawn(school.latitude, school.longitude)
                                    worldMapViewModel.setCampaignObjective(ovh.gabrielhuav.pow.domain.models.MissionCatalog.first)
                                    // Partida NUEVA: inventario fresco (el VM es Activity-scoped y persiste).
                                    worldMapViewModel.currentInteriorInventory = emptyList()
                                    worldMapViewModel.currentInteriorLab1KeyFound = false
                                    // Guardado MANUAL inicial en el slot elegido (para que "CARGAR PARTIDA"
                                    // lo muestre desde ya). Los autoguardados posteriores van a los slots auto.
                                    if (chosenSlot in SaveGameRepository.MANUAL_SLOTS) {
                                        worldMapViewModel.saveGame(this@MainActivity, chosenSlot)
                                    }
                                    // Tras el último panel de la intro (IntroPOW8), la transición
                                    // entra al PRIMER interior de la campaña: el Lobby de la ENCB.
                                    // Inicia la música de investigar.
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).playInvestigarMusic()
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
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).stopInvestigarMusic()
                                    navController.navigate("world_map") {
                                        popUpTo("encb_lobby") { inclusive = true }
                                    }
                                },
                                isMultiplayer = wmState.isMultiplayer,
                                playerName = wmState.playerName,
                                onNavigateToSettings = { navController.navigate("settings?fromGame=true") },
                                debugHitboxes = false,
                                startRoomId = ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ENCB_LOBBY_ID,
                                onRequestSaveGame = { showSaveDialog = true },
                                // Recuerda en qué sala de interiores está el jugador (para el guardado).
                                onRoomChanged = { roomId -> worldMapViewModel.currentInteriorRoomId = roomId },
                                // Waypoint final de ENCB_LAB2 → reanuda la narrativa (cómic
                                // ENCB_OUTRO). popUpTo encb_lobby inclusive libera el motor de
                                // interiores (la cadena de salas) antes de mostrar el cómic.
                                onPlayStoryOutro = {
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).stopInvestigarMusic()
                                    navController.navigate("story_outro") {
                                        popUpTo("encb_lobby") { inclusive = true }
                                    }
                                },
                                // INVENTARIO: restaura el progreso guardado y persiste cada cambio en el VM del mundo.
                                initialInventoryKeys = worldMapViewModel.currentInteriorInventory,
                                initialLab1KeyFound = worldMapViewModel.currentInteriorLab1KeyFound,
                                onInteriorProgress = { keys, found ->
                                    worldMapViewModel.currentInteriorInventory = keys
                                    worldMapViewModel.currentInteriorLab1KeyFound = found
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
                                    // SPAWN EXCLUSIVO DEL MODO HISTORIA: al terminar el outro
                                    // (IntroPOW11), el jugador entra al mapa global en el punto de
                                    // arranque de la Misión 1 (checkpoint de la escolta).
                                    // setStorySpawn fija la posición y activa inCampaign=true.
                                    worldMapViewModel.setStorySpawn(ovh.gabrielhuav.pow.domain.models.MissionCatalog.MISSION1_SPAWN_LAT, ovh.gabrielhuav.pow.domain.models.MissionCatalog.MISSION1_SPAWN_LON)
                                    worldMapViewModel.currentInteriorRoomId = null
                                    // Inicia la música de dirigirse al lugar seguro
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).playLugarSeguroMusic()
                                    navController.navigate("world_map") {
                                        popUpTo("story_outro") { inclusive = true }
                                    }
                                },
                                onBack = {
                                    // Misma transición narrativa (saltar/volver el outro): mismo checkpoint.
                                    worldMapViewModel.setStorySpawn(ovh.gabrielhuav.pow.domain.models.MissionCatalog.MISSION1_SPAWN_LAT, ovh.gabrielhuav.pow.domain.models.MissionCatalog.MISSION1_SPAWN_LON)
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).playLugarSeguroMusic()
                                    navController.navigate("world_map") {
                                        popUpTo("story_outro") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ─── MODO HISTORIA · Misión 2 (llegada a la ESCOM: IntroPOW12..14) ──
                        // Cómic que se reproduce al cumplir la Misión 1. Al terminar, arranca la
                        // Misión 2 (objetivo "Ingresa a la ESCOM" + persecución de 6 policías +
                        // multitud saliendo de la ESCOM) y vuelve al mundo (popBackStack a world_map,
                        // que sigue debajo en el backstack).
                        composable(route = "story_mission2") {
                            StoryIntroScreen(
                                school = SchoolCatalog.default,
                                sequenceId = ovh.gabrielhuav.pow.domain.models.StoryComicCatalog.MISSION2_INTRO_ID,
                                onBegin = {
                                    worldMapViewModel.startMission2()
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                onBack = {
                                    worldMapViewModel.startMission2()
                                    navController.popBackStack("world_map", inclusive = false)
                                }
                            )
                        }


                        // Registramos la ruta de Ajustes
                        composable(
                            route = "settings?fromGame={fromGame}",
                            arguments = listOf(
                                androidx.navigation.navArgument("fromGame") {
                                    type = androidx.navigation.NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) {
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
                                onCoordsWidgetToggled = {
                                    settingsViewModel.toggleCoordsWidget(it)
                                    worldMapViewModel.toggleCoordsWidget(it)
                                },
                                onDeveloperModeToggled = { settingsViewModel.toggleDeveloperMode(it) },
                                // Audio: persisten en Ajustes Y se aplican en vivo al SoundManager.
                                onMusicVolumeChanged = {
                                    settingsViewModel.changeMusicVolume(it)
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).setMusicVolume(it)
                                },
                                onSfxVolumeChanged = {
                                    settingsViewModel.changeSfxVolume(it)
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).setSfxVolume(it)
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
                                    if (navController.currentDestination?.route?.startsWith("settings") == true) {
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
                                authManager = authManager,
                                // ELIMINAR CUENTA: AuthManager ya borró la identidad en Firebase; aquí
                                // se borran los DATOS LOCALES del jugador (partidas de campaña) y se vuelve al menú.
                                onAccountDeleted = {
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    try {
                                        val sg = SaveGameRepository(this@MainActivity)
                                        for (slot in 1..SaveGameRepository.SLOT_COUNT) sg.clear(slot)
                                        campaignRepository.clearCampaign()
                                    } catch (_: Exception) {}
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
                                    if (worldMapViewModel.inCampaign) worldMapViewModel.saveGame(this@MainActivity, worldMapViewModel.campaignSlot, auto = true)
                                    worldMapViewModel.disconnectFromMultiplayer()

                                    // Detener audios del modo historia al salir al menu
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).apply {
                                        stopInvestigarMusic()
                                        stopLugarSeguroMusic()
                                        stopMainMusic()
                                    }
                                    
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
                                            if (worldMapViewModel.inCampaign) worldMapViewModel.saveGame(this@MainActivity, worldMapViewModel.campaignSlot, auto = true)
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
                                    // Desde el JUEGO: mantener horizontal en Ajustes.
                                    navController.navigate("settings?fromGame=true")
                                },
                                // Callback que se dispara cuando el video de ZombiHand
                                // termina y hay un edificio destino pendiente.
                                onNavigateToInterior = { routeName ->
                                    navController.navigate(routeName)
                                },
                                // "Guardar partida" → abre el selector de slots (a nivel Activity).
                                onRequestSaveGame = { showSaveDialog = true },
                                // MISIÓN FALLIDA → "Reintentar": recarga el slot activo (reinicia la
                                // misión) sin pasar por el menú principal.
                                onRetryMission = { worldMapViewModel.retryCampaignMission(this@MainActivity) }
                            )
                            // ─── ShineCTO: navegar al interior cuando el VM lo indique ───
                            val uiState by worldMapViewModel.uiState.collectAsState()

                            LaunchedEffect(uiState.navigateToShineCTO) {
                                if (uiState.navigateToShineCTO) {
                                    worldMapViewModel.consumeNavigateToShineCTO()
                                    navController.navigate("shinecto_interior")
                                }
                            }

                            // MODO HISTORIA · Misión 1 cumplida (llegaste a la ESCOM) → cómic
                            // IntroPOW12..14; al volver arranca la persecución de la Misión 2.
                            LaunchedEffect(uiState.pendingMission2Intro) {
                                if (uiState.pendingMission2Intro) {
                                    // OJO: NO consumir el flag ANTES del delay; al cambiar el flag
                                    // se cancela este LaunchedEffect y el cómic nunca se lanzaba.
                                    // Deja sonar el jingle de "misión cumplida" un momento y LUEGO
                                    // navega al cómic (consumir + navigate van seguidos, sin suspensión).
                                    kotlinx.coroutines.delay(2200)
                                    worldMapViewModel.consumePendingMission2Intro()
                                    navController.navigate("story_mission2")
                                }
                            }

                            // MODO HISTORIA · MISIÓN FALLIDA (la policía mató a Prankedy): la pantalla
                            // se queda con botones "REINTENTAR MISIÓN" (recarga el slot) y "Salir al
                            // menú"; ya NO vuelve sola al menú. (Ver WorldMapScreen / retryCampaignMission.)

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
                        
                        // ─── ESTACIONES METROBÚS ──────────────────────────────────
                        composable(
                            route = "metrobus_station_interior/{stationName}?spawnX={spawnX}&spawnY={spawnY}",
                            arguments = listOf(
                                androidx.navigation.navArgument("stationName") { type = androidx.navigation.NavType.StringType },
                                androidx.navigation.navArgument("spawnX") { type = androidx.navigation.NavType.FloatType; defaultValue = -1f },
                                androidx.navigation.navArgument("spawnY") { type = androidx.navigation.NavType.FloatType; defaultValue = -1f }
                            )
                        ) { backStackEntry ->
                            val stationName = backStackEntry.arguments?.getString("stationName") ?: "Desconocida"
                            val spawnX = backStackEntry.arguments?.getFloat("spawnX") ?: -1f
                            val spawnY = backStackEntry.arguments?.getFloat("spawnY") ?: -1f
                            MetrobusStationInteriorScreen(
                                stationName = stationName,
                                spawnX = spawnX,
                                spawnY = spawnY,
                                onExit = { currentStation ->
                                    worldMapViewModel.teleportToMetrobusStation(currentStation)
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                onTeleportToStation = { newStation, x, y ->
                                    navController.navigate("metrobus_station_interior/$newStation?spawnX=$x&spawnY=$y") {
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
                            // MODO HISTORIA: tras la Misión 1 (INGRESAR_ESCOM cumplida), al entrar al
                            // interior de la ESCOM (lobby) se muestra el objetivo "Busca pistas en la ESCOM".
                            // El objetivo exterior NO cambia (allá sigue "Ingresa a la ESCOM, Cumplido").
                            val interiorObjective = if (
                                worldMapViewModel.inCampaign &&
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID &&
                                wmState.currentObjective?.id == ovh.gabrielhuav.pow.domain.models.MissionCatalog.INGRESAR_ESCOM.id &&
                                wmState.objectiveDone
                            ) ovh.gabrielhuav.pow.domain.models.MissionCatalog.BUSCAR_PISTAS_ESCOM else null
                            ZombieGameScreen(
                                onExitToWorld = {
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).stopInvestigarMusic()
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                isMultiplayer = wmState.isMultiplayer,
                                playerName = wmState.playerName,
                                onNavigateToSettings = { navController.navigate("settings?fromGame=true") },
                                debugHitboxes = false,
                                startRoomId = startRoom,
                                // "Guardar partida" disponible también en interiores (mismo selector
                                // de slots; el estado del mundo se conserva en el worldMapViewModel).
                                onRequestSaveGame = { showSaveDialog = true },
                                // Recuerda la sala actual (para el guardado / reentrada al CARGAR).
                                onRoomChanged = { roomId -> worldMapViewModel.currentInteriorRoomId = roomId },
                                // Si se CARGA una partida directamente en la cadena ENCB y se llega al
                                // waypoint final de ENCB_LAB2, reanuda la narrativa (cómic ENCB_OUTRO).
                                onPlayStoryOutro = {
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(this@MainActivity).stopInvestigarMusic()
                                    navController.navigate("story_outro") {
                                        // Limpia el interior y el world_map base (el outro reentra al mundo).
                                        popUpTo("world_map") { inclusive = true }
                                    }
                                },
                                interiorObjective = interiorObjective,
                                // INVENTARIO: restaura el progreso guardado y persiste cada cambio en el VM del mundo.
                                initialInventoryKeys = worldMapViewModel.currentInteriorInventory,
                                initialLab1KeyFound = worldMapViewModel.currentInteriorLab1KeyFound,
                                onInteriorProgress = { keys, found ->
                                    worldMapViewModel.currentInteriorInventory = keys
                                    worldMapViewModel.currentInteriorLab1KeyFound = found
                                }
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
