package ovh.gabrielhuav.pow

import android.Manifest
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
import ovh.gabrielhuav.pow.features.interior.ui.AuditorioScreen
import ovh.gabrielhuav.pow.features.interior.ui.BibliotecaScreen
import ovh.gabrielhuav.pow.features.interior.ui.CafeteriaScreen
import ovh.gabrielhuav.pow.features.interior.ui.CanchasFutbolScreen
import ovh.gabrielhuav.pow.features.interior.ui.EdificioScreen
import ovh.gabrielhuav.pow.features.interior.ui.EstacionamientoScreen
import ovh.gabrielhuav.pow.features.interior.ui.PalapasScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.CollectiblesScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.MainMenuScreen
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.WorldMapScreen
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.settings.ui.SettingsScreen
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsViewModel
import ovh.gabrielhuav.pow.features.zombie_minigame.ui.ZombieGameScreen
import ovh.gabrielhuav.pow.ui.theme.PolitecnicoOpenWorldTheme
import java.io.File

class MainActivity : ComponentActivity() {

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
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
                    LaunchedEffect(settingsState.mapProvider, settingsState.showCacheWidget, settingsState.showFpsWidget) {
                        worldMapViewModel.setMapProvider(settingsState.mapProvider)
                        worldMapViewModel.toggleCacheWidget(settingsState.showCacheWidget)
                        worldMapViewModel.toggleFpsWidget(settingsState.showFpsWidget)
                    }

                    val navController = rememberNavController()

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
                                onControlTypeChanged = { settingsViewModel.changeControlType(it) },
                                onControlsScaleChanged = { settingsViewModel.changeControlsScale(it) },
                                onSwapControlsToggled = { settingsViewModel.toggleSwapControls(it) },
                                onNavigateBack = {
                                    if (navController.currentDestination?.route == "settings") {
                                        navController.popBackStack()
                                    }
                                },
                                onSaveClicked = {
                                    // 1. Guardar persistentemente en el dispositivo
                                    settingsViewModel.saveControlsSettings()

                                    // 2. Notificar al mapa
                                    worldMapViewModel.updateControlSettings(
                                        type = settingsState.controlType,
                                        scale = settingsState.controlsScale,
                                        swap = settingsState.swapControls
                                    )

                                    android.widget.Toast.makeText(this@MainActivity, "Configuración de controles guardada", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                // Lógica para regresar al menú principal limpiando el mapa
                                onExitToMainMenu = {
                                    worldMapViewModel.disconnectFromMultiplayer()
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
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
                                }
                            )
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

                        // ─── MINIJUEGO DE ZOMBIS ──────────────────────────────────
                        // Anillo circular de cuartos con IA de zombis, combate mutuo
                        // y pantalla de victoria. Al ganar (o al salir), se hace
                        // popBackStack hasta world_map para preservar el open world.
                        // debugHitboxes = true para calibrar exitHitbox y ver la
                        // matriz de colisión pintada sobre cada cuarto.
                        composable(route = "zombie_minigame") {
                            val wmState by worldMapViewModel.uiState.collectAsState()
                            ZombieGameScreen(
                                onExitToWorld = {
                                    navController.popBackStack("world_map", inclusive = false)
                                },
                                isMultiplayer = wmState.isMultiplayer,
                                playerName = wmState.playerName,
                                debugHitboxes = false
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
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null)
                    worldMapViewModel.updateInitialLocation(location.latitude, location.longitude)
                else
                    worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
            }
        } catch (e: SecurityException) {
            worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
        }
    }
}