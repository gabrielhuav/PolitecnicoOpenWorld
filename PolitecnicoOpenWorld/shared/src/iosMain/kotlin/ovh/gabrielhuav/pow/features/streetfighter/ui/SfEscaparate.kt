package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import ovh.gabrielhuav.pow.features.streetfighter.data.SF_CLASSIC_THEME
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.IosStreetFighterEnvironment
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.StreetFighterViewModel
import ovh.gabrielhuav.pow.platform.audio.PowAudio
import platform.UIKit.UIViewController

/**
 * 🍏🥊 ESCAPARATE: el hueco donde MIRAR en el simulador las pantallas de SF ya portadas.
 *
 * **Por qué existe.** El paso 4 del plan pide ver cada pantalla en el simulador, pero `iosApp` solo
 * tenía el mapa: no había ningún sitio donde un `@Composable` de `commonMain` pudiera dibujarse en
 * iOS. Sin esto, "portado" solo podía significar "compila", que es el tipo de verificación floja
 * que este proyecto ya ha pagado caro.
 *
 * ⚠️ **NO es el juego ni el menú de iOS**, y NO demuestra que la pelea funcione: las pantallas se
 * pintan con datos de muestra. Es un banco de pruebas de LAYOUT y de assets. Cuando exista la
 * navegación de verdad (y un `StreetFighterController` de iOS), esto se borra.
 */
fun crearEscaparate(): UIViewController = ComposeUIViewController {
    var pantalla by remember { mutableStateOf(Pantalla.INDICE) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {
        when (pantalla) {
            Pantalla.INDICE -> Indice { pantalla = it }
            Pantalla.TUTORIAL -> SfTutorialOverlay(
                lesson = 2, total = 6,
                title = "GOLPE BÁSICO",
                hint = "Pulsa PUÑO cuando el rival esté cerca",
                steps = listOf("ACÉRCATE", "PUÑO DÉBIL", "PUÑO FUERTE"),
                stepIndex = 1,
                flash = "",
                error = "",
                completed = false,
                onSkip = {}, onRestart = {}, onExit = { pantalla = Pantalla.INDICE },
            )
            Pantalla.ESCENARIOS -> SfStageSelectOverlay(
                theme = SF_CLASSIC_THEME,
                onSelect = {},
                onBack = { pantalla = Pantalla.INDICE },
            )
            Pantalla.MENU -> SfModeMenuOverlay(
                devMode = false,
                audioShowcaseRunning = false,
                audioShowcaseIndex = 0,
                audioShowcaseTotal = 0,
                audioShowcaseFighter = null,
                audioShowcasePhrase = "",
                onArcade = {}, onPractice = {}, onAiVsAi = {}, onCombos = {},
                onMultiplayer = {}, onGauntletAll = {}, onGauntletArcade = {},
                onGauntletShowcase = {}, onAudioShowcaseStop = {},
                onBack = { pantalla = Pantalla.INDICE },
            )
            Pantalla.CARGANDO -> Box(Modifier.fillMaxSize().clickable { pantalla = Pantalla.INDICE }) {
                // De `SfSceneRenderer`: usa la fuente arcade del HUD, así que también prueba assets.
                SfLoadingOverlay(theme = SF_CLASSIC_THEME)
            }
            Pantalla.PELEA -> StreetFighterScreenCommon(
                onExitToMap = { pantalla = Pantalla.INDICE },
                controller = remember {
                    OfflineStreetFighterController(
                        StreetFighterViewModel(IosStreetFighterEnvironment()),
                    )
                },
            )
        }

        if (pantalla != Pantalla.INDICE && pantalla != Pantalla.PELEA) {
            Text(
                "‹ VOLVER",
                color = Color(0xFF8AE28A),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clickable { pantalla = Pantalla.INDICE },
            )
        }
    }
}

private enum class Pantalla { INDICE, TUTORIAL, ESCENARIOS, MENU, CARGANDO, PELEA }

@Composable
private fun Indice(ir: (Pantalla) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ESCAPARATE SF · iOS", color = Color(0xFF8AE28A))

        // La fuente arcade del HUD, pintada glifo a glifo desde el atlas: si se ve, `PowAssets` y
        // `PowImagen` funcionan en iOS.
        SfBitmapText(text = "HUELUM VS GOYA")
        SfBitmapText(text = "0123456789")

        PruebaDeAudio()

        Text("— PANTALLAS PORTADAS —", color = Color(0xFF667766))
        Enlace("1 · Tutorial") { ir(Pantalla.TUTORIAL) }
        Enlace("2 · Selector de escenario") { ir(Pantalla.ESCENARIOS) }
        Enlace("3 · Menú de modos") { ir(Pantalla.MENU) }
        Enlace("4 · Overlay de carga (SfSceneRenderer)") { ir(Pantalla.CARGANDO) }
        Enlace("5 · PELEA REAL (motor commonMain)") { ir(Pantalla.PELEA) }
    }
}

@Composable
private fun Enlace(texto: String, onClick: () -> Unit) {
    Text("▶ $texto", color = Color(0xFF8AE28A), modifier = Modifier.clickable(onClick = onClick))
}

/**
 * Prueba de AUDIO: el audio es lo único de la cadena de assets que **no se puede comprobar
 * mirando**. Si `AVAudioPlayer` no sabe abrir el formato, `cargarEfecto` devuelve `null` y el juego
 * se queda mudo sin un solo error.
 *
 * Se prueban los DOS formatos que hay en el juego (m4a tras la conversión, y un mp3 que ya estaba):
 * comparar es lo que distingue "la ruta del bundle está mal" de "el códec no se soporta".
 */
@Composable
private fun PruebaDeAudio() {
    var estado by remember { mutableStateOf("pulsa para probar el audio") }

    Text(estado, color = Color(0xFFE0E0A0))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        for (ruta in listOf(
            "STREETFIGHTER/SOUNDS/light-attack.m4a",
            "STREETFIGHTER/SOUNDS/prankedy_lobby.mp3",
        )) {
            val ext = ruta.substringAfterLast('.')
            Enlace(ext) {
                val c = PowAudio.cargarEfecto(ruta)
                estado = if (c == null) {
                    "❌ null: $ext"
                } else {
                    c.reproducir(volumen = 1f)
                    "✅ $ext suena=${c.reproduciendo}"
                }
            }
        }
    }
}
