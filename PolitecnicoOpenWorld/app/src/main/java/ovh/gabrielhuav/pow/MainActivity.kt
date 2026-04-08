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
import androidx.preference.PreferenceManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.WorldMapScreen
import java.io.File

class MainActivity : ComponentActivity() {

    private val worldMapViewModel: WorldMapViewModel by viewModels {
        WorldMapViewModel.Factory(this)
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
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "main_menu"
                    ) {
                        composable(
                            route = "main_menu",
                            exitTransition = {
                                fadeOut(animationSpec = tween(700)) + scaleOut(
                                    animationSpec = tween(700),
                                    targetScale = 1.2f
                                )
                            }
                        ) {
                            MainMenuScreen(
                                onNavigateToMap = {
                                    navController.navigate("world_map") {
                                        popUpTo("main_menu") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "world_map",
                            enterTransition = {
                                fadeIn(animationSpec = tween(1000)) + scaleIn(
                                    animationSpec = tween(1000),
                                    initialScale = 1.2f
                                )
                            }
                        ) {
                            WorldMapScreen(
                                context = this@MainActivity,
                                viewModel = worldMapViewModel,
                                onNavigateToMainMenu = {
                                    navController.navigate("main_menu") {
                                        popUpTo("main_menu") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Configura osmdroid para maximizar el uso del caché local de tiles.
     *
     * ESTRATEGIA DE CACHÉ DE TILES:
     * - Los tiles se guardan en disco en /Android/data/ovh.gabrielhuav.pow/cache/osmdroid/
     * - Con expirationOverrideDuration = 30 días, un tile descargado NO vuelve a
     *   descargarse durante un mes, incluso si el servidor lo actualizó.
     * - El tamaño máximo de caché (500 MB) es suficiente para cubrir toda la CDMX en zoom 21.
     * - tileDownloadThreads = 4 acelera la descarga inicial cuando el usuario explora nuevas zonas.
     */
    private fun configureOsmdroid() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        Configuration.getInstance().apply {
            load(this@MainActivity, prefs)
            userAgentValue = packageName

            // Directorio de caché en almacenamiento de la app (no requiere permisos adicionales)
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")

            // ─── PARÁMETROS CLAVE DE CACHÉ ───────────────────────────────────────────
            // Los tiles son válidos durante 30 días antes de intentar re-descargarlos.
            // Para un juego donde el mapa no cambia frecuentemente, 30 días es ideal.
            expirationOverrideDuration = TILE_EXPIRATION_MS

            // Tamaño máximo de caché en disco: 500 MB
            // Zona de 2km alrededor de ESCOM en zoom 21 ≈ 80-100 MB
            // 500 MB da margen para explorar zonas más amplias
            tileFileSystemCacheMaxBytes = TILE_CACHE_MAX_BYTES

            // Hilos para descargar tiles en paralelo (balance entre velocidad y batería)
            tileDownloadThreads = 4

            // Cola de descarga más grande para exploración fluida del mapa
            tileDownloadMaxQueueSize = 50

        }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
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
                    worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            worldMapViewModel.updateInitialLocation(19.5045, -99.1469)
        }
    }

    companion object {
        // 30 días en milisegundos
        private const val TILE_EXPIRATION_MS = 1000L * 60 * 60 * 24 * 30
        // 500 MB en bytes
        private const val TILE_CACHE_MAX_BYTES = 500L * 1024 * 1024
    }
}