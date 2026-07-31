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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
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
    // Texto del aviso de "esta función todavía no está". `null` = no hay nada que decir.
    var aviso by remember { mutableStateOf<String?>(null) }

    // El aviso se va solo: un cartel fijo estorba más que informa. 4 s es lo que tarda en leerse
    // una frase corta sin prisa — el mismo orden que un Snackbar largo de Material.
    LaunchedEffect(aviso) {
        if (aviso != null) { delay(4000); aviso = null }
    }

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
            // ⚠️ `top = 60.dp` para que el cartel NO quede debajo del botón VOLVER, que está en la
            // misma esquina superior. Sin esto se solapan y no se lee ninguno de los dos.
            Modifier.align(Alignment.TopCenter).systemBarsPadding().fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 60.dp, bottom = 12.dp),
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

        // ── HUD: los MISMOS controles que Android ───────────────────────────────────────────
        HudMundoIos(
            modifier = Modifier.align(Alignment.BottomCenter),
            alMover = { angulo ->
                // El joystick da un ángulo en radianes; el mundo se mueve en metros. 0 rad = este,
                // y en pantalla la Y crece hacia ABAJO, de ahí el signo del seno.
                jugador = jugador.desplazado(
                    metrosNorte = -sin(angulo) * PASO_METROS,
                    metrosEste = cos(angulo) * PASO_METROS,
                )
                puente?.moverJugador(jugador)
                puente?.moverNiebla(jugador)
                puente?.centrarEn(jugador, ZOOM_JUEGO)
            },
            alSoltar = { /* Sin inercia todavía: el jugador se para al soltar. */ },
            alPulsarSinFuncion = { accion -> aviso = "Botón $accion: pendiente del ViewModel" },
        )

        // El aviso de "todavía no", encima del HUD y sin taparlo.
        aviso?.let {
            AvisoSinFuncion(
                it,
                Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 150.dp),
            )
        }

        // ⚠️ VOLVER va ARRIBA a la derecha, no abajo: abajo está el HUD y en vertical no caben
        // los dos sin que el pulgar acabe pulsando el que no quería.
        Button(
            onClick = alVolver,
            shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A).copy(alpha = 0.9f)),
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(12.dp),
        ) {
            Text("VOLVER", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
    }
}

/**
 * Cuánto avanza el jugador por cada tic del joystick (~30 por segundo mientras se mantiene).
 *
 * ⚠️ **NO es la velocidad del juego.** La de verdad depende de si vas a pie o en coche y la calcula
 * `WorldMapMovement.kt`, que sigue en `:app` con el `WorldMapViewModel` (fase 5). **1,2 m por tic**
 * da un caminar creíble a zoom 16 mientras tanto.
 */
private const val PASO_METROS = 1.2

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
