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
import androidx.compose.runtime.Composable
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
 * ⚠️ **No es el mundo abierto jugable.** No hay jugador, ni NPCs, ni landmarks, ni coleccionables:
 * todo eso llega por el **puente JS ↔ nativo**, que en Android es `MapJsBridge` y en iOS **todavía
 * no existe**. Ver `README for IAS/12_PLAN_MUNDO_ABIERTO_iOS.md` §Fase 6.
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
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

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
                "Todavía sin jugador, NPCs ni coleccionables",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CutCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
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
