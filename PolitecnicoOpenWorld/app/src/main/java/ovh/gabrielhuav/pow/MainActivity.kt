package ovh.gabrielhuav.pow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.ui.theme.PolitecnicoOpenWorldTheme
import ovh.gabrielhuav.pow.features.main_menu.ui.MainMenuScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.preference.PreferenceManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.WorldMapScreen
import ovh.gabrielhuav.pow.features.settings.ui.SettingsScreen
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsViewModel
import java.io.File
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val worldMapViewModel: WorldMapViewModel by viewModels {
        WorldMapViewModel.Factory(this)
    }

    // Instanciamos el ViewModel de los ajustes usando su Factory
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(this)
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
                    LaunchedEffect(
                        settingsState.mapProvider,
                        settingsState.showCacheWidget,
                        settingsState.showFpsWidget,
                        settingsState.freeNavigation
                    ) {
                        worldMapViewModel.setMapProvider(settingsState.mapProvider)
                        worldMapViewModel.toggleCacheWidget(settingsState.showCacheWidget)
                        worldMapViewModel.toggleFpsWidget(settingsState.showFpsWidget)
                        worldMapViewModel.toggleFreeNavigation(settingsState.freeNavigation)
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
                                onNavigateToMap = {
                                    // Ya no sincronizamos aquí, el orquestador lo hace solo.
                                    navController.navigate("world_map") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
                                }
                            )
                        }

                        // NUEVO: Registramos la nueva ruta de Ajustes
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
                                onFreeNavigationToggled = { settingsViewModel.toggleFreeNavigation(it) },
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
                                // NUEVO: Lógica para regresar al menú principal limpiando el mapa
                                onExitToMainMenu = {
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true } // Borra la pila de forma segura usando un destino existente
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable(
                            route = "world_map",
                            // 2. RESTAURAMOS LA ANIMACIÓN DE ENTRADA
                            // Esto evita que el motor gráfico se congele al cambiar de pantalla.
                            enterTransition = {
                                fadeIn(animationSpec = tween(1000)) +
                                        scaleIn(animationSpec = tween(1000), initialScale = 1.2f)
                            }
                        ) {
                            WorldMapScreen(
                                context = this@MainActivity,
                                viewModel = worldMapViewModel,
                                onNavigateToMainMenu = {
                                    navController.navigate("main_menu") {
                                        popUpTo("world_map") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToSettings = {
                                    navController.navigate("settings")
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