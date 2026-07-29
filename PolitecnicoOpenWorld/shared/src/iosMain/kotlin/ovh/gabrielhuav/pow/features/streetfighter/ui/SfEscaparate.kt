package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import ovh.gabrielhuav.pow.platform.audio.PowAudio
import ovh.gabrielhuav.pow.platform.audio.PowClip
import platform.UIKit.UIViewController

/**
 * 🍏🥊 ESCAPARATE: el hueco donde MIRAR en el simulador las pantallas de SF ya portadas.
 *
 * **Por qué existe.** El paso 4 del plan pide ver cada pantalla en el simulador antes de pasar a la
 * siguiente, pero `iosApp` solo tenía el mapa: no había ningún sitio donde un `@Composable` de
 * `commonMain` pudiera dibujarse en iOS. Sin esto, "portado" solo podía significar "compila", que
 * es exactamente el tipo de verificación floja que este proyecto ya ha pagado caro.
 *
 * ⚠️ **NO es el juego ni el menú de iOS.** Es un banco de pruebas: se le va añadiendo cada pantalla
 * conforme baja a `commonMain`. Cuando exista la navegación de verdad, esto se borra.
 *
 * Swift lo usa así:
 * ```swift
 * SfEscaparateKt.crearEscaparate()   // devuelve un UIViewController
 * ```
 */
fun crearEscaparate(): UIViewController = ComposeUIViewController {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D11))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ESCAPARATE SF · iOS", color = Color(0xFF8AE28A))

        // (1/7) La fuente arcade del HUD, pintada glifo a glifo desde el atlas.
        // Si se ve con las letras del juego, `PowImagen` + `PowAssets` funcionan en iOS.
        SfBitmapText(text = "HUELUM VS GOYA")
        SfBitmapText(text = "ROUND 1 FIGHT")
        SfBitmapText(text = "0123456789")

        // ⚠️ Mientras los assets NO estén en el bundle, los tres de arriba salen VACÍOS: el atlas
        // `sf_hud_pow.png` no se encuentra y `SfBitmapText` no pinta nada (por diseño, no falla).
        // Eso hace de esta pantalla también la prueba de que el bundle está bien montado.
        Text(
            "Si no ves letras arcade arriba, faltan los assets en el bundle.",
            color = Color(0xFFAA8888),
        )

        PruebaDeAudio()
    }
}

/**
 * Prueba de AUDIO en iOS: intenta cargar y sonar un efecto real del juego.
 *
 * Está aquí porque el audio es lo único de la cadena de assets que **no se puede comprobar
 * mirando**: si `AVAudioPlayer` no sabe abrir el formato, `cargarEfecto` devuelve `null` y el
 * juego se queda mudo sin un solo error. Este panel lo convierte en algo visible.
 */
@Composable
private fun PruebaDeAudio() {
    var estado by remember { mutableStateOf("pulsa para probar el audio") }
    var clip by remember { mutableStateOf<PowClip?>(null) }

    Text(estado, color = Color(0xFFE0E0A0))
    Text(
        "▶ PROBAR SONIDO",
        color = Color(0xFF8AE28A),
        modifier = Modifier.clickable {
            val ruta = "STREETFIGHTER/SOUNDS/light-attack.ogg"
            val c = PowAudio.cargarEfecto(ruta)
            clip = c
            estado = if (c == null) {
                "❌ cargarEfecto devolvió null para $ruta"
            } else {
                c.reproducir(volumen = 1f)
                "✅ cargado; reproduciendo=${c.reproduciendo}"
            }
        },
    )
}
