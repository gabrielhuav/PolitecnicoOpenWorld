package ovh.gabrielhuav.pow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.data.repository.iosSettingsRepository
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.local.room.crearPowDatabase
import ovh.gabrielhuav.pow.features.main_menu.ui.CollectiblesScreen
import ovh.gabrielhuav.pow.features.main_menu.ui.IosMainMenuController
import ovh.gabrielhuav.pow.features.main_menu.ui.MainMenuScreen
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.MapaMundoIos
import ovh.gabrielhuav.pow.features.settings.aplicarIdiomaIos
import ovh.gabrielhuav.pow.features.settings.ui.SettingsScreen
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsViewModel
import ovh.gabrielhuav.pow.features.streetfighter.ui.OfflineStreetFighterController
import ovh.gabrielhuav.pow.features.streetfighter.ui.StreetFighterScreenCommon
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.IosStreetFighterEnvironment
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel
import platform.UIKit.UIViewController

/**
 * 🍏 LA APP DE iOS. Punto de entrada único que Swift consume:
 *
 * ```swift
 * PowAppIosKt.crearAppIos()   // devuelve un UIViewController
 * ```
 *
 * ## Por qué UN SOLO `ComposeUIViewController`
 *
 * La navegación es estado de Compose, no de SwiftUI. Si cada pantalla fuese su propio
 * `UIViewController`, tendríamos DOS árboles de navegación (el de SwiftUI y el de Compose) que hay
 * que mantener en sincronía a mano — y al volver de una pantalla se queda un host encima del otro.
 * Con un host único no puede pasar: cambiar de pantalla es cambiar una variable.
 *
 * ## Qué modos aparecen
 *
 * Los decide **`PowModos.kt`**, no este archivo. En iOS eso deja AJUSTES, COLECCIONABLES y
 * HUELUM VS. GOYA; Mundo Libre, Modo Historia y Multijugador ni se pintan. Si algún día uno de esos
 * modos llega a iOS, se cambia el catálogo (y su test), no esta navegación.
 */
fun crearAppIos(): UIViewController = ComposeUIViewController { PowAppIos() }

/** Las pantallas que iOS puede mostrar hoy. */
private enum class Pantalla { MENU, AJUSTES, COLECCIONABLES, PELEA, MAPA }

@Composable
private fun PowAppIos() {
    // Al cambiar de idioma se incrementa y `key(...)` reconstruye TODO el árbol, que es lo más
    // parecido al `activity.recreate()` de Android que se puede hacer aquí.
    var generacion by remember { mutableStateOf(0) }

    key(generacion) { ContenidoApp { generacion++ } }
}

@Composable
private fun ContenidoApp(alCambiarIdioma: () -> Unit) {
    var pantalla by remember { mutableStateOf(Pantalla.MENU) }

    // ── Dependencias, creadas UNA vez y compartidas por toda la sesión ──────────────────────────
    // Mismo `NSUserDefaults` que `IosStreetFighterEnvironment`: por eso el Modo Desarrollador que se
    // activa en Ajustes desbloquea los peleadores sin una línea de código de sincronización.
    val ajustes = remember { SettingsViewModel(iosSettingsRepository()) }

    // La BD vive lo que vive la app. Se cierra al destruir el host para no dejar el fichero abierto.
    val baseDatos = remember { crearPowDatabase() }
    DisposableEffect(Unit) { onDispose { baseDatos.close() } }
    val coleccionables = remember {
        CollectiblesViewModel(CollectibleRepository(baseDatos.collectibleDao()))
    }
    // 🗺️ La caché de la red de calles (celdas de ~2 km, TTL 7 días). La MISMA clase que Android:
    // sin ella iOS le pedía la red entera a Overpass en cada arranque y se comía un 429.
    val cacheCalles = remember { RoadNetworkCache(baseDatos.roadNetworkDao()) }

    when (pantalla) {
        Pantalla.MENU -> MainMenuScreen(
            // 🌎 MUNDO LIBRE está EN OBRAS, pero su mapa ya se puede ver: el botón abre la vista
            // previa (ver `IosMainMenuController.mundoTieneVistaPrevia`). Modo Historia y
            // Multijugador siguen abriendo solo el aviso.
            onNavigateToMap = { _, _ -> pantalla = Pantalla.MAPA },
            onNavigateToStory = {},
            onNavigateToSettings = { pantalla = Pantalla.AJUSTES },
            onNavigateToCollectibles = { pantalla = Pantalla.COLECCIONABLES },
            onNavigateToStreetFighter = { pantalla = Pantalla.PELEA },
            controller = remember { IosMainMenuController(iosSettingsRepository()) },
        )

        Pantalla.AJUSTES -> SettingsScreen(
            controller = ajustes,
            onNavigateBack = { pantalla = Pantalla.MENU },
            onExitToMainMenu = { pantalla = Pantalla.MENU },
            // El equivalente iOS de `activity.recreate()` de Android. Ver `aplicarIdiomaIos`:
            // guarda `AppleLanguages`, que es lo que el sistema mira para elegir `values-en`.
            // Sin esto el desplegable dice "English" y los textos se quedan en español.
            onLanguageApplied = { tag ->
                aplicarIdiomaIos(tag)
                // Rehace el árbol de Compose para que los `stringResource` se vuelvan a resolver.
                alCambiarIdioma()
            },
            // `accountContent = null` esconde la sección de Cuenta: en iOS no hay Google Sign-In.
            accountContent = null,
        )

        // 🌎 Vista previa del mapa. NO es el mundo jugable: sin jugador, NPCs ni landmarks,
        // porque el puente JS ↔ nativo todavía no existe en iOS.
        // 🗺️ La caché de calles se le PASA, no la crea el mapa: así vive lo que vive la sesión y
        // no se vuelve a consultar Room en cada entrada al mundo.
        Pantalla.MAPA -> MapaMundoIos(
            cacheCalles = cacheCalles,
            alVolver = { pantalla = Pantalla.MENU },
        )

        Pantalla.COLECCIONABLES -> CollectiblesScreen(
            controller = coleccionables,
            onBack = { pantalla = Pantalla.MENU },
        )

        // ⚠️ El controller de la pelea se crea DENTRO de la rama, sin `remember` de sesión: cada
        // entrada arranca una partida limpia y, al salir, su ViewModel queda libre. Reutilizarlo
        // dejaría vivo el bucle de combate del anterior.
        Pantalla.PELEA -> StreetFighterScreenCommon(
            onExitToMap = { pantalla = Pantalla.MENU },
            controller = remember {
                OfflineStreetFighterController(
                    StreetFighterViewModel(IosStreetFighterEnvironment()),
                )
            },
        )
    }
}
