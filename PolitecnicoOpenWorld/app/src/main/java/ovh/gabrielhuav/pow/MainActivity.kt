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
import ovh.gabrielhuav.pow.features.map_exterior.ui.WorldMapScreen
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.ui.theme.PolitecnicoOpenWorldTheme
import ovh.gabrielhuav.pow.features.main_menu.ui.MainMenuScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {

    private val worldMapViewModel: WorldMapViewModel by viewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Manejador moderno para pedir permisos en Android
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fetchCurrentLocation()
        } else {
            // Si deniega el permiso, lo mandamos a ESCOM
            worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Comienza a pedir permisos y buscar ubicación en segundo plano
        checkPermissionsAndFetchLocation()

        setContent {
            PolitecnicoOpenWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 1. Inicializar el controlador de navegación
                    val navController = rememberNavController()

                    // 2. Configurar el NavHost. startDestination indica qué se abre primero.
                    NavHost(
                        navController = navController,
                        startDestination = "main_menu"
                    ) {

                        // ==========================================
                        // RUTA 1: MENÚ PRINCIPAL
                        // ==========================================
                        composable(
                            route = "main_menu",
                            // El menú se expande hacia la cámara y se desvanece (efecto de atravesarlo)
                            exitTransition = {
                                fadeOut(animationSpec = tween(700)) + scaleOut(
                                    animationSpec = tween(700),
                                    targetScale = 1.2f // Crece un 20% antes de desaparecer
                                )
                            }
                        ) {
                            // CORRECCIÓN: Aquí va MainMenuScreen
                            MainMenuScreen(
                                onNavigateToMap = {
                                    navController.navigate("world_map") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ==========================================
                        // RUTA 2: EL MAPA DEL JUEGO
                        // ==========================================
                        composable(
                            route = "world_map",
                            // El mapa comienza con un zoom cercano (1.2x) y se aleja a su tamaño normal (1.0x)
                            enterTransition = {
                                fadeIn(animationSpec = tween(1000)) + scaleIn(
                                    animationSpec = tween(1000),
                                    initialScale = 1.2f
                                )
                            }
                        ) {
                            // CORRECCIÓN: Aquí va WorldMapScreen con el callback para regresar al menú
                            WorldMapScreen(
                                context = this@MainActivity,
                                viewModel = worldMapViewModel,
                                onNavigateToMainMenu = {
                                    // Navega al menú y limpia el historial para no amontonar mapas en memoria
                                    navController.navigate("main_menu") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    worldMapViewModel.updateInitialLocation(location.latitude, location.longitude)
                } else {
                    // Si el GPS no responde, fallback a ESCOM
                    worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            // Fallback en caso de error de seguridad
            worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
        }
    }
}