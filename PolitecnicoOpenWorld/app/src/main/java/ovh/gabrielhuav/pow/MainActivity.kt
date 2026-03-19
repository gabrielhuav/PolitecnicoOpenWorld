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
            // Si el usuario deniega el permiso, lo mandamos a ESCOM por defecto
            worldMapViewModel.updateLocation(19.5045, -99.1469)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración obligatoria para OSMDroid (Políticas de OpenStreetMap)
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = packageName

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkPermissionsAndFetchLocation()

        setContent {
            PolitecnicoOpenWorldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Llamamos a nuestro Composable del mapa
                    WorldMapScreen(context = this, viewModel = worldMapViewModel)
                }
            }
        }
    }

    private fun checkPermissionsAndFetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation()
        } else {
            // Pedimos permisos al usuario
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
                    worldMapViewModel.updateLocation(location.latitude, location.longitude)
                } else {
                    // Si el GPS está apagado o no responde, fallback a ESCOM
                    worldMapViewModel.updateLocation(19.5045, -99.1469)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            worldMapViewModel.updateLocation(19.5045, -99.1469)
        }
    }
}