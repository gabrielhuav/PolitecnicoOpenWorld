package ovh.gabrielhuav.pow

// REFACTOR: toggles de widgets extraídos a WorldMapCameraUi.kt (extensiones) → import explícito.
// REFACTOR: ajustes/skin extraídos a WorldMapSettings.kt (extensiones) → import explícito.
// REFACTOR: extensiones del VM (WorldMapProviders.kt) → requieren import explícito.
// MODO HISTORIA: guardado/carga de la partida (JSON). Las extensiones del VM viven
// en WorldMapSaveGame.kt y requieren import explícito desde fuera del paquete viewmodel.
// REFACTOR: extensiones del VM extraídas (campaña/teleport/shinecto) → import explícito.
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.AndroidCollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.saveGame
import ovh.gabrielhuav.pow.features.settings.viewmodel.AndroidSettingsViewModel
import ovh.gabrielhuav.pow.ui.theme.PolitecnicoOpenWorldTheme
import java.io.File

// Spawn fijo: coordenadas del punto de teletransporte "ESCOM" (ver TeleportCatalog).
// El juego SIEMPRE arranca en ESCOM, sin depender del GPS real del dispositivo.
private const val SPAWN_ESCOM_LAT = 19.504603
private const val SPAWN_ESCOM_LON = -99.145985

@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // i18n: aplica el idioma elegido (Ajustes) envolviendo el Context base antes de
    // que se inflen los recursos. "" = idioma del sistema. Ver i18n/LocaleHelper.kt.
    override fun attachBaseContext(newBase: Context) {
        val lang = ovh.gabrielhuav.pow.data.repository.SettingsRepository(newBase).getLanguage()
        super.attachBaseContext(ovh.gabrielhuav.pow.i18n.LocaleHelper.wrap(newBase, lang))
    }

    // ETAPA 4 (Hilt): `by viewModels()` SIN Factory → Hilt provee el VM (@HiltViewModel) y lo
    // scopea a la ACTIVITY (clave para WorldMapViewModel: SOBREVIVE a la navegación, el game loop /
    // gate isMapReady NO se reinician — ver 09 §12). El VM se pasa hacia abajo al AppNavGraph.
    private val worldMapViewModel: WorldMapViewModel by viewModels()

    private val settingsViewModel: AndroidSettingsViewModel by viewModels()

    private val collectiblesViewModel: AndroidCollectiblesViewModel by viewModels()

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
        // EDGE-TO-EDGE (Play Console / Android 15+): con targetSdk 35+ el sistema fuerza el dibujo
        // de borde a borde. Llamarlo aquí lo activa de forma CONSISTENTE en TODAS las versiones de
        // Android (no solo 15+) y resuelve el aviso "la pantalla de borde a borde puede no mostrarse
        // para todos los usuarios". Las pantallas Compose ya respetan las barras con systemBarsPadding().
        enableEdgeToEdge()
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
                    AppNavGraph(
                        activity = this@MainActivity,
                        worldMapViewModel = worldMapViewModel,
                        settingsViewModel = settingsViewModel,
                        collectiblesViewModel = collectiblesViewModel,
                        authManager = authManager,
                        campaignRepository = campaignRepository
                    )
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
