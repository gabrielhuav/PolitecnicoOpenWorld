package ovh.gabrielhuav.pow

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog
import ovh.gabrielhuav.pow.features.campaign.ui.StoryIntroScreen
import ovh.gabrielhuav.pow.features.campaign.ui.StoryModeScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.AuditorioScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.BibliotecaScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.CafeteriaScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.CanchasFutbolScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.DeportivoBeisScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.DeportivoFutbolScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.EdificioScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.EstacionamientoScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.FesInteriorScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.MetroStationInteriorScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.MetrobusStationInteriorScreen
import ovh.gabrielhuav.pow.features.interiores.escom.ui.PalapasScreen
import ovh.gabrielhuav.pow.features.interiores.shinecto.ui.EasterEggDiscoveryDialog
import ovh.gabrielhuav.pow.features.interiores.shinecto.ui.ShineCTOScreen
import ovh.gabrielhuav.pow.features.interiores.zombies.ui.ZombieGameScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.CollectiblesScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.MainMenuScreen
import ovh.gabrielhuav.pow.features.streetfighter.ui.StreetFighterScreen
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.WorldMapScreen
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.completeMission2Backpack
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.completeMission2Hide
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.completeMission2Rumor
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.completeMission3Evidence
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumeDevTpRoute
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumeEscomDoorNavigation
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumeNavigateToShineCTO
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumeMission2BackpackComic
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumeMission3IntroComic
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumeMission3OutroComic
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.consumePendingMission1ChaseIntro
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectCampaignMission
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.failMission2Hide
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.grantMission2StinkCan
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.loadGame
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onShineCTODiscoveryConfirmed
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.requestMapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.retryCampaignMission
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.saveGame
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectSkin
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setCampaignObjective
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setMapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setNpcDensity
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setNpcEmojiLod
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setNpcFullEmoji
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.setStorySpawn
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.startMission1Chase
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.teleportToMetroStation
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.teleportToMetrobusStation
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleCacheWidget
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleCoordsWidget
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleFpsWidget
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleMissionLog
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleSpeedometer
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleZoomWidget
import ovh.gabrielhuav.pow.features.settings.ui.SettingsScreen
import ovh.gabrielhuav.pow.features.settings.ui.AndroidAccountSettings
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsViewModel

// ─────────────────────────────────────────────────────────────────────────────
// Grafo de navegación de la app (NavHost) + orquestación de ajustes/orientación/guardado,
// extraído de MainActivity.onCreate/setContent (refactor de tamaño). Recibe la Activity y los
// ViewModels/repos Activity-scoped como parámetros (eran privados en MainActivity).
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppNavGraph(
    activity: MainActivity,
    worldMapViewModel: WorldMapViewModel,
    settingsViewModel: SettingsViewModel,
    collectiblesViewModel: CollectiblesViewModel,
    authManager: ovh.gabrielhuav.pow.data.auth.AuthManager,
    campaignRepository: CampaignRepository
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
                    // 🆕 (2026-07-26) `showWorldShoulderButtons` va TAMBIÉN como clave: su
                    // interruptor vive en Ajustes → INTERFAZ, no en Controles, así que sin esto
                    // el efecto no se relanzaba y los gatillos no aparecían hasta reiniciar.
                    LaunchedEffect(settingsState.controlType, settingsState.controlsScale, settingsState.swapControls, settingsState.showWorldShoulderButtons) {
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
                            // EXCEPCIÓN: el interior de Metrobús se DIBUJÓ en VERTICAL (assets portrait:
                            // inside/bus1/bus2 = 1168×1347, mapa 2551×3402). En horizontal se recortaba a
                            // una franja ("todo vertical"). Esa ruta corre en PORTRAIT para que el arte
                            // encaje. (El metro sí es horizontal: sus vehículos son 2816×1536.)
                            val isMetrobusStation = route?.startsWith("metrobus_station_interior") == true
                            activity.requestedOrientation = when {
                                isMetrobusStation -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                                isMenu -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                            if (isMenu) {
                                // Al volver a un menú, corta TODA la música de fondo del juego/cómic
                                // (MediaPlayers en loop), que se quedaba sonando al salir.
                                val sm = ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity)
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
                            title = stringResource(R.string.wm_opt_save_game),
                            summariesProvider = { SaveGameRepository(activity).summaries() },
                            mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.SAVE,
                            onPick = { slot ->
                                showSaveDialog = false
                                worldMapViewModel.saveGame(activity, slot)
                                android.widget.Toast.makeText(activity, activity.getString(R.string.save_toast_saved, slot), android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { slot -> SaveGameRepository(activity).clear(slot) },
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
                            // La pantalla vive en `:shared` y no sabe de Hilt ni de Context: se le
                            // pasa un controller (ver 10_ARQUITECTURA_SEPARACION.md §2bis, patrón 4).
                            val menuVm: ovh.gabrielhuav.pow.features.main_menu.viewmodel.MainMenuViewModel =
                                androidx.hilt.navigation.compose.hiltViewModel()
                            val menuScope = androidx.compose.runtime.rememberCoroutineScope()
                            val menuController = remember(menuVm) {
                                ovh.gabrielhuav.pow.features.main_menu.ui.AndroidMainMenuController(
                                    viewModel = menuVm,
                                    settings = ovh.gabrielhuav.pow.data.repository.SettingsRepository(activity),
                                    scope = menuScope,
                                )
                            }
                            // Al volver del selector de Google: si el login fue OK se continúa con el
                            // flujo normal de multijugador (warmup + nombre).
                            val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                            ) { result ->
                                authManager.handleSignInResult(result.data) { ok, err ->
                                    if (ok) {
                                        if (menuController.state.value.playerName.isBlank()) {
                                            authManager.currentDisplayName()
                                                ?.let { menuController.updatePlayerName(it) }
                                        }
                                        menuController.onMultiplayerPressed()
                                    } else if (!err.isNullOrBlank()) {
                                        android.widget.Toast
                                            .makeText(activity, err, android.widget.Toast.LENGTH_LONG)
                                            .show()
                                    }
                                }
                            }
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
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).apply {
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
                                onNavigateToStreetFighter = {
                                    navController.navigate("street_fighter")
                                },
                                controller = menuController,
                                // GATE de Google Sign-In: MULTIJUGADOR (y a futuro los LOGROS) exigen
                                // sesión; el juego local y el Modo Historia NO. Si Firebase no está
                                // configurado en este build (clones/PRs sin google-services.json) se
                                // entra ANÓNIMO: los servidores en modo suave aceptan sin token.
                                // Vive AQUÍ y no en la pantalla porque usa Intent/ActivityResult, que
                                // no existen en iOS.
                                onMultiplayer = {
                                    if (!authManager.isAvailable() || authManager.isSignedIn()) {
                                        menuController.onMultiplayerPressed()
                                    } else {
                                        signInLauncher.launch(authManager.signInIntent())
                                    }
                                },
                                chipDeCuenta = { ovh.gabrielhuav.pow.features.main_menu.ui.ChipDeCuenta(authManager) },
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
                            var newGameSchool by remember { mutableStateOf<ovh.gabrielhuav.pow.domain.models.campaign.CampaignSchool?>(null) }
                            // PARTIDA NUEVA: PRIMERO se elige el PERSONAJE; al elegir se fija la skin
                            // y se continúa al selector de slot (newGameSchool).
                            var charPickSchool by remember { mutableStateOf<ovh.gabrielhuav.pow.domain.models.campaign.CampaignSchool?>(null) }
                            StoryModeScreen(
                                onStartCampaign = { school -> charPickSchool = school },
                                onLoadCampaign = { showLoadDialog = true },
                                onBack = { navController.popBackStack() }
                            )
                            // Selector de personaje (Hombre/Mujer/No binario; LÁZARO solo en Modo Dev).
                            charPickSchool?.let { school ->
                                val devModeChar = remember {
                                    ovh.gabrielhuav.pow.data.repository.SettingsRepository(activity).getDeveloperMode()
                                }
                                ovh.gabrielhuav.pow.features.main_menu.ui.NewGameCharacterDialog(
                                    context = activity,
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
                                    title = stringResource(R.string.save_dialog_new_slot),
                                    summariesProvider = { SaveGameRepository(activity).summaries() },
                                    mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.SAVE,
                                    onDelete = { slot -> SaveGameRepository(activity).clear(slot) },
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
                                    title = stringResource(R.string.save_dialog_load),
                                    summariesProvider = { SaveGameRepository(activity).summaries() },
                                    mode = ovh.gabrielhuav.pow.features.main_menu.ui.SaveSlotsMode.LOAD,
                                    onDelete = { slot -> SaveGameRepository(activity).clear(slot) },
                                    onPick = { slot ->
                                        showLoadDialog = false
                                        worldMapViewModel.disconnectFromMultiplayer()
                                        if (worldMapViewModel.loadGame(activity, slot)) {
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
                                                ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).playInvestigarMusic()
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
                                    SaveGameRepository(activity).clearAutoSlots()
                                    worldMapViewModel.campaignSchoolId = school.id
                                    worldMapViewModel.campaignSlot = SaveGameRepository.AUTO_SLOTS.first()
                                    // La campaña ARRANCA en el interior del Lobby de la ENCB, así que
                                    // el guardado inicial debe apuntar AHÍ (no al mapa global); si no,
                                    // al cargar esa partida te mandaba al mundo en vez del interior.
                                    worldMapViewModel.currentInteriorRoomId =
                                        ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ENCB_LOBBY_ID
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    worldMapViewModel.setStorySpawn(school.latitude, school.longitude)
                                    worldMapViewModel.setCampaignObjective(ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.first)
                                    // Partida NUEVA: inventario fresco (el VM es Activity-scoped y persiste).
                                    worldMapViewModel.currentInteriorInventory = emptyList()
                                    worldMapViewModel.currentInteriorLab1KeyFound = false
                                    // Guardado MANUAL inicial en el slot elegido (para que "CARGAR PARTIDA"
                                    // lo muestre desde ya). Los autoguardados posteriores van a los slots auto.
                                    if (chosenSlot in SaveGameRepository.MANUAL_SLOTS) {
                                        worldMapViewModel.saveGame(activity, chosenSlot)
                                    }
                                    // Tras el último panel de la intro (IntroPOW8), la transición
                                    // entra al PRIMER interior de la campaña: el Lobby de la ENCB.
                                    // Inicia la música de investigar.
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).playInvestigarMusic()
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
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).stopInvestigarMusic()
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
                                // "Misiones" también en el lobby ENCB de campaña (diálogo global).
                                onRequestMissionLog = if (worldMapViewModel.inCampaign) {
                                    { worldMapViewModel.toggleMissionLog(true) }
                                } else null,
                                // Recuerda en qué sala de interiores está el jugador (para el guardado).
                                onRoomChanged = { roomId -> worldMapViewModel.currentInteriorRoomId = roomId },
                                // Waypoint final de ENCB_LAB2 → reanuda la narrativa (cómic
                                // ENCB_OUTRO). popUpTo encb_lobby inclusive libera el motor de
                                // interiores (la cadena de salas) antes de mostrar el cómic.
                                onPlayStoryOutro = {
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).stopInvestigarMusic()
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
                                },
                                // Cadena ENCB = siempre Misión 1 en curso → la llave es objeto de misión.
                                missionItemsLocked = true
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
                                sequenceId = ovh.gabrielhuav.pow.domain.models.campaign.StoryComicCatalog.ENCB_OUTRO_ID,
                                onBegin = {
                                    // SPAWN EXCLUSIVO DEL MODO HISTORIA: al terminar el outro
                                    // (IntroPOW11), el jugador entra al mapa global en el punto de
                                    // arranque de la Misión 1 (checkpoint de la escolta).
                                    // setStorySpawn fija la posición y activa inCampaign=true.
                                    worldMapViewModel.setStorySpawn(ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION1_SPAWN_LAT, ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION1_SPAWN_LON)
                                    worldMapViewModel.currentInteriorRoomId = null
                                    // Inicia la música de dirigirse al lugar seguro
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).playLugarSeguroMusic()
                                    navController.navigate("world_map") {
                                        popUpTo("story_outro") { inclusive = true }
                                    }
                                },
                                onBack = {
                                    // Misma transición narrativa (saltar/volver el outro): mismo checkpoint.
                                    worldMapViewModel.setStorySpawn(ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION1_SPAWN_LAT, ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION1_SPAWN_LON)
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).playLugarSeguroMusic()
                                    navController.navigate("world_map") {
                                        popUpTo("story_outro") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ─── MODO HISTORIA · Misión 2 (llegada a la ESCOM: IntroPOW12..14) ──
                        // Cómic que se reproduce al cumplir la Misión 1. Al terminar, arranca la
                        // persecución final de la Misión 1 (objetivo "Ingresa a la ESCOM" + 6
                        // policías + multitud saliendo de la ESCOM) y vuelve al mundo (popBackStack
                        // a world_map, que sigue debajo en el backstack).
                        composable(route = "story_mission1_chase") {
                            StoryIntroScreen(
                                school = SchoolCatalog.default,
                                sequenceId = ovh.gabrielhuav.pow.domain.models.campaign.StoryComicCatalog.MISSION1_CHASE_INTRO_ID,
                                onBegin = {
                                    worldMapViewModel.startMission1Chase()
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                onBack = {
                                    worldMapViewModel.startMission1Chase()
                                    navController.popBackStack("world_map", inclusive = false)
                                }
                            )
                        }

                        // ─── MODO HISTORIA · Misión 2 "La mochila" (IntroPOW16..18) ──────────
                        // Puente narrativo tras hablar con Prankedy (fase MOCHILA). Al terminar,
                        // vuelve al mundo con el objetivo "recupera la mochila" ya fijado.
                        composable(route = "story_mission2_backpack") {
                            val goBack: () -> Unit = { navController.popBackStack("world_map", inclusive = false) }
                            StoryIntroScreen(
                                school = SchoolCatalog.default,
                                sequenceId = ovh.gabrielhuav.pow.domain.models.campaign.StoryComicCatalog.MISSION2_BACKPACK_INTRO_ID,
                                onBegin = goBack,
                                onBack = goBack
                            )
                        }

                        // 🆕 ─── MODO HISTORIA · CIERRE de la Misión 3 (IntroPOW23..24) ─────────
                        // Se reproduce al completar la M3 (evidencia + arma). Solo vuelve al
                        // mundo: el gancho a la M4 queda sembrado ("¿a quién se lo llevamos?").
                        composable(route = "story_mission3_outro") {
                            val goBack: () -> Unit = { navController.popBackStack("world_map", inclusive = false) }
                            StoryIntroScreen(
                                school = SchoolCatalog.default,
                                sequenceId = ovh.gabrielhuav.pow.domain.models.campaign.StoryComicCatalog.MISSION3_OUTRO_ID,
                                onBegin = goBack,
                                onBack = goBack
                            )
                        }

                        // ─── MODO HISTORIA · Misión 3 "Regreso a la ENCB" (IntroPOW19..22) ──
                        // Puente narrativo al completar la M2. Al terminar, SIGUE la Misión 3
                        // (fija su 🎯) y vuelve al mundo para que la historia continúe.
                        composable(route = "story_mission3_intro") {
                            val goM3: () -> Unit = {
                                worldMapViewModel.selectCampaignMission(
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_3_ID,
                                    force = true
                                )
                                navController.popBackStack("world_map", inclusive = false)
                            }
                            StoryIntroScreen(
                                school = SchoolCatalog.default,
                                sequenceId = ovh.gabrielhuav.pow.domain.models.campaign.StoryComicCatalog.MISSION3_INTRO_ID,
                                onBegin = goM3,
                                onBack = goM3
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
                            SettingsScreen(
                                controller = settingsViewModel,
                                onMusicVolumeApplied = {
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).setMusicVolume(it)
                                },
                                onSfxVolumeApplied = {
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).setSfxVolume(it)
                                },
                                onNpcDensityApplied = worldMapViewModel::setNpcDensity,
                                onNpcEmojiLodApplied = worldMapViewModel::setNpcEmojiLod,
                                onNpcFullEmojiApplied = worldMapViewModel::setNpcFullEmoji,
                                onOptimizeApplied = {
                                    val minD = ovh.gabrielhuav.pow.data.repository.SettingsRepository.NPC_DENSITY_MIN
                                    worldMapViewModel.setNpcDensity(minD)
                                    worldMapViewModel.setNpcEmojiLod(true)
                                    worldMapViewModel.setNpcFullEmoji(true)
                                    android.widget.Toast.makeText(activity, activity.getString(R.string.settings_optimize_applied), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onNavigateBack = {
                                    settingsViewModel.discardControlsChanges()
                                    if (navController.currentDestination?.route?.startsWith("settings") == true) {
                                        navController.popBackStack()
                                    }
                                },
                                onControlsSaved = { saved ->
                                    worldMapViewModel.updateControlSettings(
                                        type = saved.tempControlType,
                                        scale = saved.tempControlsScale,
                                        swap = saved.tempSwapControls,
                                    )
                                    android.widget.Toast.makeText(activity, activity.getString(R.string.settings_controls_saved), android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onExitToMainMenu = {
                                    settingsViewModel.discardControlsChanges()
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onLanguageApplied = { activity.recreate() },
                                accountContent = {
                                    AndroidAccountSettings(authManager) {
                                        worldMapViewModel.disconnectFromMultiplayer()
                                        runCatching {
                                            val saves = SaveGameRepository(activity)
                                            for (slot in 1..SaveGameRepository.SLOT_COUNT) saves.clear(slot)
                                            campaignRepository.clearCampaign()
                                        }
                                        navController.navigate("main_menu") {
                                            popUpTo("main_menu") { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                },
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
                                    if (worldMapViewModel.inCampaign) worldMapViewModel.saveGame(activity, worldMapViewModel.campaignSlot, auto = true)
                                    worldMapViewModel.disconnectFromMultiplayer()

                                    // Detener audios del modo historia al salir al menu
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).apply {
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
                                            if (worldMapViewModel.inCampaign) worldMapViewModel.saveGame(activity, worldMapViewModel.campaignSlot, auto = true)
                                            worldMapViewModel.disconnectFromMultiplayer()
                                            activity.finish()
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
                                context = activity,
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
                                onRetryMission = { worldMapViewModel.retryCampaignMission(activity) }
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
                            // IntroPOW12..15; al volver arranca la persecución final (chase).
                            LaunchedEffect(uiState.pendingMission1ChaseIntro) {
                                if (uiState.pendingMission1ChaseIntro) {
                                    // OJO: NO consumir el flag ANTES del delay; al cambiar el flag
                                    // se cancela este LaunchedEffect y el cómic nunca se lanzaba.
                                    // Deja sonar el jingle de "misión cumplida" un momento y LUEGO
                                    // navega al cómic (consumir + navigate van seguidos, sin suspensión).
                                    kotlinx.coroutines.delay(2200)
                                    worldMapViewModel.consumePendingMission1ChaseIntro()
                                    navController.navigate("story_mission1_chase")
                                }
                            }

                            // MODO HISTORIA · M2 fase MOCHILA → cómic "La mochila" (IntroPOW16..18).
                            // Se dispara en el mapa (tras hablar con Prankedy); al volver sigue la fase 5.
                            LaunchedEffect(uiState.pendingMission2BackpackComic) {
                                if (uiState.pendingMission2BackpackComic) {
                                    kotlinx.coroutines.delay(2200)
                                    worldMapViewModel.consumeMission2BackpackComic()
                                    navController.navigate("story_mission2_backpack")
                                }
                            }

                            // MODO HISTORIA · M2 completada → cómic "Regreso a la ENCB" (IntroPOW19..22).
                            // La bandera se pone a true DENTRO del salón (al recoger la mochila): se
                            // ESPERA a estar en el mapa (no en un interior) para no navegar encima de él.
                            LaunchedEffect(uiState.pendingMission3IntroComic) {
                                if (uiState.pendingMission3IntroComic) {
                                    while (worldMapViewModel.currentInteriorRoomId != null) {
                                        kotlinx.coroutines.delay(200)
                                    }
                                    kotlinx.coroutines.delay(600)
                                    worldMapViewModel.consumeMission3IntroComic()
                                    navController.navigate("story_mission3_intro")
                                }
                            }

                            // 🆕 MODO HISTORIA · M3 completada (evidencia) → cómic de CIERRE
                            // "mission3_outro" (IntroPOW23..24). La bandera se pone DENTRO de la
                            // ENCB (auto-salida 2.6 s después): se espera a estar en el mapa.
                            LaunchedEffect(uiState.pendingMission3OutroComic) {
                                if (uiState.pendingMission3OutroComic) {
                                    while (worldMapViewModel.currentInteriorRoomId != null) {
                                        kotlinx.coroutines.delay(200)
                                    }
                                    kotlinx.coroutines.delay(600)
                                    worldMapViewModel.consumeMission3OutroComic()
                                    navController.navigate("story_mission3_outro")
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
                                controller = collectiblesViewModel,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // ─── STREET FIGHTER (minijuego dev, port fiel de StreetFighter-main) ───
                        // Pelea 1v1 clásica Ryu vs Ken (CPU) con los sprites/sonidos originales
                        // (assets/STREETFIGHTER). Se entra desde el menú principal SOLO con Modo
                        // Desarrollador. La ruta NO está en portraitRoutes → landscape. Ver 07.
                        composable(route = "street_fighter") {
                            StreetFighterScreen(
                                onExitToMap = { navController.popBackStack() }
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
                            // MISIÓN 3 · ASALTO: entrar a la cadena ENCB durante la fase 3 siembra
                            // zombis + la evidencia (la navegación la disparó WorldMapMission3).
                            val mission3Assault = worldMapViewModel.inCampaign &&
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ENCB_LOBBY_ID &&
                                worldMapViewModel.mission3Phase ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3.PHASE_ASSAULT
                            // MISIÓN 2 · fase 1 "ESCONDERSE": se juega DENTRO del lobby de la ESCOM.
                            val mission2Hide = worldMapViewModel.inCampaign &&
                                worldMapViewModel.mission2Phase ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_HIDE &&
                                wmState.currentObjective?.id ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_ESCONDERSE_POLICIA.id
                            val mission2Rumor = worldMapViewModel.inCampaign &&
                                worldMapViewModel.mission2Phase ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_RUMOR &&
                                wmState.currentObjective?.id ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_PISTA_RUMOR.id
                            // 🥫 Salvaguarda: en la fase MOCHILA la lata debe ir en el inventario
                            // (cubre partidas guardadas ANTES de que la lata fuera un ítem).
                            if (worldMapViewModel.inCampaign && worldMapViewModel.mission2Phase ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_BACKPACK) {
                                worldMapViewModel.grantMission2StinkCan()
                            }
                            val interiorObjective = when {
                                // MISIÓN 2 · fase ESCONDERSE: el lobby muestra su objetivo (prioridad
                                // sobre "Busca pistas": la búsqueda policial está en curso).
                                mission2Hide &&
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID ->
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_ESCONDERSE_POLICIA
                                mission2Rumor &&
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID ->
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_PISTA_RUMOR
                                worldMapViewModel.inCampaign &&
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID &&
                                wmState.currentObjective?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id &&
                                wmState.objectiveDone ->
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.BUSCAR_PISTAS_ESCOM
                                // MISIÓN 2 · fase MOCHILA: el salón muestra su propio objetivo.
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.ESCOM_SALON_M2_ID ->
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_RECUPERAR_MOCHILA
                                // MISIÓN 2 · fase MOCHILA por el flujo NORMAL (🆕 2026-07-13): entras
                                // por el lobby y navegas lobby → Edificio Principal → salón; el
                                // objetivo guía toda la sesión de interiores.
                                worldMapViewModel.inCampaign &&
                                startRoom == ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog.LOBBY_ID &&
                                worldMapViewModel.mission2Phase ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_BACKPACK &&
                                wmState.currentObjective?.id ==
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_RECUPERAR_MOCHILA.id ->
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M2_RECUPERAR_MOCHILA
                                // MISIÓN 3 · ASALTO: la cadena ENCB muestra "Recupera la evidencia".
                                mission3Assault ->
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.M3_RECUPERAR_EVIDENCIA
                                else -> null
                            }
                            ZombieGameScreen(
                                onExitToWorld = {
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).stopInvestigarMusic()
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
                                // "Misiones" también en interiores (abre el MissionLogDialog global,
                                // hospedado tras el NavHost). Solo en campaña. Si desde aquí se sigue
                                // una misión de exterior, su 🎯 se ve al salir al mapa (el objetivo
                                // vive en el worldMapViewModel).
                                onRequestMissionLog = if (worldMapViewModel.inCampaign) {
                                    { worldMapViewModel.toggleMissionLog(true) }
                                } else null,
                                // Recuerda la sala actual (para el guardado / reentrada al CARGAR).
                                onRoomChanged = { roomId -> worldMapViewModel.currentInteriorRoomId = roomId },
                                // Si se CARGA una partida directamente en la cadena ENCB y se llega al
                                // waypoint final de ENCB_LAB2, reanuda la narrativa (cómic ENCB_OUTRO).
                                onPlayStoryOutro = {
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).stopInvestigarMusic()
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
                                },
                                // MISIÓN 2 · fase MOCHILA: al recoger la mochila de Prankedy en el
                                // salón, se completa la Misión 2 en el VM del mundo.
                                onMission2BackpackRecovered = {
                                    worldMapViewModel.completeMission2Backpack()
                                },
                                // MISIÓN 2: la mochila desbloquea TODOS los slots del inventario
                                // (persistido vía mission2Phase). REJUGAR: durante un replay la fase
                                // va a la mitad en memoria, pero los slots NO se pierden → también
                                // gatea por completedMissions. MISIÓN 3: gate del arma de fuego
                                // (solo campaña) + modo asalto ENCB + callback de la evidencia.
                                // 🆕 2026-07-13: default 2 slots (llave M1 + lata M2 conviven).
                                initialUnlockedSlots = if (worldMapViewModel.mission2Phase >=
                                    ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_DONE ||
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_2_ID in
                                        wmState.completedMissions) 4 else 2,
                                firearmUnlocked = !worldMapViewModel.inCampaign || worldMapViewModel.hasFirearm,
                                mission3Assault = mission3Assault,
                                onMission3EvidenceRecovered = {
                                    worldMapViewModel.completeMission3Evidence()
                                },
                                // MISIÓN 2 · fase ESCONDERSE (lobby): armado en runtime + desenlaces.
                                mission2Hide = mission2Hide,
                                onMission2HideCompleted = {
                                    // Aguantaste: avanza a la fase RUMOR (el jugador sale cuando quiera;
                                    // al salir, el 🎯 ya apunta a la escena del rumor en el campus).
                                    worldMapViewModel.completeMission2Hide()
                                },
                                onMission2HideFailed = {
                                    // Te reconocieron: MISIÓN FALLIDA. Se sale al mapa global, donde
                                    // vive la pantalla de fallo + REINTENTAR (re-arma desde la fase 1).
                                    worldMapViewModel.failMission2Hide()
                                    worldMapViewModel.currentInteriorRoomId = null
                                    ovh.gabrielhuav.pow.features.audio.SoundManager.getInstance(activity).stopInvestigarMusic()
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                mission2Rumor = mission2Rumor,
                                onMission2RumorCompleted = {
                                    worldMapViewModel.completeMission2Rumor()
                                },
                                // 🆕 OBJETOS DE MISIÓN: la llave de la M1 no se desecha mientras
                                // las misiones 1-2 estén en curso (M2 completada implica M1).
                                missionItemsLocked = worldMapViewModel.inCampaign &&
                                    ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.MISSION_2_ID !in
                                        wmState.completedMissions
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

                    // ─── REGISTRO / SELECTOR DE MISIONES a nivel Activity (mismo patrón que el
                    // SaveSlotsDialog de arriba): un solo MissionLogDialog sirve al MAPA GLOBAL y a
                    // los INTERIORES (estado showMissionLog en el worldMapViewModel, Activity-scoped).
                    // Va DESPUÉS del NavHost para dibujarse ENCIMA de la pantalla actual (es un
                    // overlay Compose, no un Dialog de ventana). MissionLogHost solo colecta
                    // showMissionLog mientras está cerrado (no recompone a 30 Hz).
                    ovh.gabrielhuav.pow.features.map_exterior.ui.components.MissionLogHost(worldMapViewModel)

                    // ─── MODO DEV · "TP al objetivo" por CHECKPOINTS (2026-07-12): ejecuta la
                    // navegación pendiente (WorldMapState.devTpRoute) desde CUALQUIER pantalla:
                    // pop a world_map y, si el checkpoint es una SALA, navigate al interior.
                    // Coroutine pura sobre el flow (colecta SOLO devTpRoute, sin recomponer a
                    // 30 Hz — mismo espíritu que MissionLogHost). Ver WorldMapMissionLog.kt.
                    LaunchedEffect(Unit) {
                        worldMapViewModel.uiState
                            .map { it.devTpRoute }
                            .distinctUntilChanged()
                            .collect { route ->
                                if (route == null) return@collect
                                worldMapViewModel.consumeDevTpRoute()
                                navController.popBackStack("world_map", inclusive = false)
                                if (route != ovh.gabrielhuav.pow.features.map_exterior.viewmodel.DEV_TP_TO_MAP) {
                                    navController.navigate(route)
                                }
                            }
                    }
}
