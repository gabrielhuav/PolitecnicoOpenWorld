package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
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
    }
}
