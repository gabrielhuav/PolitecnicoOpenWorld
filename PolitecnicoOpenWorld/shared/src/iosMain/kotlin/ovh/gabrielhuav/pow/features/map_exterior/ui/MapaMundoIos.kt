package ovh.gabrielhuav.pow.features.map_exterior.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitView
// ⚠️ Toda interoperabilidad con Objective-C exige `@OptIn(ExperimentalForeignApi)`, aunque la firma
// que uses sea correcta. Ver `09_CONVENTIONS_GOTCHAS.md` §KMP/iOS punto 3.
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * 🌎🍏 EL MAPA DEL MUNDO EN iOS — vista previa.
 *
 * ## Qué es y qué NO es
 *
 * Es el mapa Leaflet del juego, **el mismo HTML que Android**, dentro de un `WKWebView`. Sirve para
 * comprobar que el camino funciona de punta a punta en un iPhone.
 *
 * **Ya se camina:** los controles mueven al jugador de verdad, la cámara lo sigue y la niebla de
 * guerra se abre a su paso. Todo eso va por `PuenteMapaIos`, que llama a las MISMAS funciones JS
 * que usa Android (`updatePlayerMarker`, `updateMapView`, `setPlayerFog`).
 *
 * ⚠️ **Aun así NO es el mundo abierto jugable.** Faltan:
 * - **NPCs, policía, coleccionables y landmarks** — hay funciones JS para todos (`updateNpcs`,
 *   `updatePolice`, `updateCollectibles`, `updateLandmarks`), pero quien las alimenta es el
 *   `WorldMapViewModel`, que sigue en `:app` (fase 5).
 * - **Colisiones**: el jugador atraviesa edificios. Eso vive en `ExteriorCollisionsConfig`, que ya
 *   está en `commonMain`, pero lo aplica el ViewModel.
 * - **La vuelta del puente (JS → Kotlin)**: en Android es `@JavascriptInterface`; en iOS haría falta
 *   `WKScriptMessageHandler`. Sin ella el mapa no puede avisar de toques ni de arrastres.
 *
 * ## Lo importante: el HTML NO está duplicado
 *
 * `buildHtml(...)` es la MISMA función Kotlin de `commonMain` que usa `WorldMapScreenWeb.kt` en
 * Android. Si el mapa cambia, cambia para las dos plataformas a la vez. Lo único que se parametrizó
 * fue el prefijo de los assets, porque `file:///android_asset/` no existe en iOS.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
fun MapaMundoIos(alVolver: () -> Unit) {
    // Dónde está el jugador. Es el ÚNICO estado de esta pantalla: el mapa es un WebView, no
    // recompone, y lo que se le manda va por el puente.
    var jugador by remember { mutableStateOf(GeoPoint(ESCOM_LAT, ESCOM_LON)) }
    var puente by remember { mutableStateOf<PuenteMapaIos?>(null) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0D0D11))) {

        UIKitView(
            factory = {
                val config = WKWebViewConfiguration().apply {
                    // El mapa ES Leaflet entero: sin JS no hay nada que ver.
                    defaultWebpagePreferences.allowsContentJavaScript = true
                }
                WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                    // Sin esto el mapa "rebota" al llegar al borde y se siente roto para un juego.
                    scrollView.bounces = false
                    val html = buildHtml(
                        lat = ESCOM_LAT,
                        lng = ESCOM_LON,
                        zoom = ZOOM_JUEGO,
                        // ⚠️ Los assets del mundo NO están en el bundle de iOS (solo los de SF y los
                        // coleccionables). Da igual el prefijo que se pase: sin inyección de datos
                        // el HTML no pide ni una imagen. Cuando llegue el puente JS, esto tendrá que
                        // apuntar a un manejador de esquema de verdad.
                        assetBaseUrl = PREFIJO_ASSETS_PENDIENTE,
                    )
                    // baseURL nula: Leaflet y las teselas de OSM son URLs absolutas HTTPS, así que
                    // no hay nada relativo que resolver.
                    loadHTMLString(html, baseURL = null)
                    puente = PuenteMapaIos(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ⚠️ El primer empujón NO puede salir en cuanto se crea la vista: el HTML todavía se está
        // cargando y las funciones JS aún no existen (la guarda del puente lo tragaría en silencio
        // y el jugador no aparecería). `DisposableEffect` corre tras la primera composición, que
        // en la práctica ya llega tarde para el `loadHTMLString`. Aun así el puente reintenta en
        // cada movimiento, así que el marcador aparece al primer toque de los controles.
        DisposableEffect(puente) {
            puente?.moverJugador(jugador)
            onDispose { }
        }

        // El cartel de honestidad. Va ARRIBA y con `systemBarsPadding` porque el mapa se dibuja a
        // sangre: sin el inset, se metería bajo la barra de estado.
        Column(
            Modifier.align(Alignment.TopCenter).systemBarsPadding().fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "VISTA PREVIA DEL MAPA",
                color = Color(0xFFD4AF37),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), CutCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Text(
                "Puedes caminar · Faltan NPCs, coleccionables y colisiones",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CutCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        // ── Controles: caminar de verdad ─────────────────────────────────────────────────────
        Column(
            Modifier.align(Alignment.BottomStart).systemBarsPadding().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val andar = { norte: Double, este: Double ->
                jugador = jugador.desplazado(norte * PASO_METROS, este * PASO_METROS)
                puente?.moverJugador(jugador)
                puente?.moverNiebla(jugador)
                puente?.centrarEn(jugador, ZOOM_JUEGO)
            }
            BotonDireccion("▲") { andar(1.0, 0.0) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDireccion("◀") { andar(0.0, -1.0) }
                Spacer(Modifier.width(52.dp))
                BotonDireccion("▶") { andar(0.0, 1.0) }
            }
            BotonDireccion("▼") { andar(-1.0, 0.0) }
        }

        Button(
            onClick = alVolver,
            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(24.dp),
        ) {
            Text("VOLVER", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

/** Un botón redondo del pad de dirección. */
@Composable
private fun BotonDireccion(glifo: String, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A).copy(alpha = 0.85f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(52.dp),
    ) {
        Text(glifo, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/**
 * Cuánto avanza el jugador por toque. **20 m** es un paso claramente visible a zoom 16 sin que se
 * salga de la pantalla: sirve para comprobar que el puente mueve al jugador de verdad.
 *
 * ⚠️ NO es la velocidad del juego. El movimiento continuo lo trae `WorldMapMovement.kt` cuando el
 * `WorldMapViewModel` se porte (fase 5).
 */
private const val PASO_METROS = 20.0

/**
 * ESCOM del IPN — la escuela que el juego trae disponible en `SchoolCatalog.kt`.
 * No son coordenadas inventadas: son las mismas con las que arranca Android.
 */
private const val ESCOM_LAT = 19.504603
private const val ESCOM_LON = -99.145985

/** Zoom 16: la escala a la que se juega el mundo abierto. */
private const val ZOOM_JUEGO = 16

/**
 * ⚠️ Marcador de sitio. Hoy no se usa ni una imagen porque no hay inyección de datos; el día que la
 * haya, aquí va el esquema propio que resuelva desde el bundle (ver `iosApp/POW/MapaWeb.swift`,
 * que ya tiene ese manejador escrito y verificado).
 */
private const val PREFIJO_ASSETS_PENDIENTE = "pow-asset:///"
