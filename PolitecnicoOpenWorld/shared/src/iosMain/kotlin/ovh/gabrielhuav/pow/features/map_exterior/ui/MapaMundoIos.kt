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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.iosSettingsRepository
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.cargarColisionesExteriores
import ovh.gabrielhuav.pow.domain.models.map.chocaAlMoverse
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
import ovh.gabrielhuav.pow.platform.orientacion.ForzarHorizontal
import platform.CoreGraphics.CGRectZero
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
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
 * **Y las bardas frenan:** `cargarColisionesExteriores()` + `chocaAlMoverse()`, los dos de
 * `commonMain`, con la MISMA regla que aplica Android.
 *
 * ⚠️ **Aun así NO es el mundo abierto jugable.** Faltan:
 * - **NPCs, policía, coleccionables y landmarks** — hay funciones JS para todos (`updateNpcs`,
 *   `updatePolice`, `updateCollectibles`, `updateLandmarks`), pero quien las alimenta es el
 *   `WorldMapViewModel`, que sigue en `:app` (fase 5).
 * ✅ **La vuelta del puente (JS → Kotlin) ya está** (2026-08-17): `PuenteJsIos` con
 * `WKScriptMessageHandler`, y un shim que define `window.Android` para que **el HTML compartido no
 * cambie**. Hoy la usa el toque del mapa, que coloca el marcador de destino igual que en Android.
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
    // 🔄 HORIZONTAL, igual que Android. Es lo que permite que el HUD use el MISMO tamaño de
    // controles que Android y que el modo pelea; en vertical no caben dos de 180 dp. Se libera
    // solo al salir de esta pantalla (ver `OrientacionIos.kt`).
    ForzarHorizontal()

    // Dónde está el jugador. Es el ÚNICO estado de esta pantalla: el mapa es un WebView, no
    // recompone, y lo que se le manda va por el puente.
    var jugador by remember { mutableStateOf(GeoPoint(ESCOM_LAT, ESCOM_LON)) }
    var puente by remember { mutableStateOf<PuenteMapaIos?>(null) }
    // Texto del aviso de "esta función todavía no está". `null` = no hay nada que decir.
    var aviso by remember { mutableStateOf<String?>(null) }

    // ⏳ `true` en cuanto el HTML del mapa terminó de cargar y ya acepta llamadas.
    var htmlListo by remember { mutableStateOf(false) }

    // ⚠️ El `remember` NO es por rendimiento: `navigationDelegate` es una referencia DÉBIL y sin
    // alguien que lo sostenga se libera y el aviso de carga no llega nunca (ver `CargaMapaIos`).
    val carga = remember { CargaMapaIos { htmlListo = true } }

    // 🧍/🚗 ¿Emoji o sprites de verdad? Es el ajuste "Optimizar para gama baja"
    // (`npcFullEmoji`) de Jugabilidad, el MISMO que ya usaba Android en su mapa web. Se lee una
    // vez al entrar: cambiarlo a media partida no es un caso que exista (se cambia en Ajustes,
    // que es otra pantalla).
    val usarEmoji = remember { iosSettingsRepository().getNpcFullEmoji() }

    // 🧱 Los muros del campus. Se leen UNA vez del bundle (`assets/CONFIG/`) y no cambian.
    // ⚠️ Si el archivo faltara, `cargarColisionesExteriores` devuelve vacío y se puede atravesar
    // todo: es su modo degradado a propósito — mejor un mundo sin bardas que un crash al entrar.
    val colisiones = remember { cargarColisionesExteriores() }

    // 🧠 EL CEREBRO DE LOS NPCs, el MISMO que Android (`:shared` desde el 2026-08-15).
    val cerebro = remember { NpcAiManager() }
    var npcs by remember { mutableStateOf<List<Npc>>(emptyList()) }
    var estadoCalles by remember { mutableStateOf(EstadoCalles.DESCARGANDO) }

    // 1️⃣ Las calles. Sin red, `updateNpcs` sale por `if (!networkIsReady) return` y NO hay NPCs:
    // no es un detalle de arranque, es el contrato del manager.
    LaunchedEffect(Unit) {
        // ⚠️ **Overpass limita por IP y devuelve 429 en cuanto pides seguido.** Android casi no lo
        // nota porque cachea la red en Room (celdas de 2 km, TTL 7 días) y solo baja lo que le
        // falta; iOS todavía NO tiene esa caché, así que pide en cada arranque y el 429 es
        // frecuente — pasó en la segunda prueba del simulador. Hasta que exista la caché, se
        // reintenta con espera creciente. **La caché sigue pendiente y es lo que toca después.**
        repeat(INTENTOS_CALLES) { intento ->
            val calles = OverpassRepository().fetchRoadNetwork(ESCOM_LAT, ESCOM_LON)
            if (calles.isNotEmpty()) {
                cerebro.updateRoadNetwork(calles)
                estadoCalles = EstadoCalles.LISTO
                return@LaunchedEffect
            }
            estadoCalles = EstadoCalles.REINTENTANDO
            delay(4000L * (intento + 1))
        }
        // Se dice en pantalla: sin esto, "Overpass me limitó" y "el código no funciona" se ven igual.
        estadoCalles = EstadoCalles.SIN_RED
    }

    // 2️⃣ El tick. 33 ms = ~30 Hz, el mismo ritmo que el game loop de Android.
    // ⚠️ `amIHost = true` porque en iOS no hay multijugador: este cliente es la autoridad de su
    // propio mundo. Con `false` el manager no simula nada (y es correcto que así sea).
    LaunchedEffect(estadoCalles) {
        if (estadoCalles != EstadoCalles.LISTO) return@LaunchedEffect
        while (true) {
            cerebro.updateNpcs(jugador, amIHost = true)
            // ⚠️ **`getServerNpcs()`, NO el flujo `npcs`.** Son dos cosas distintas y el nombre
            // engaña: `npcs` (`StateFlow`) solo lo escribe `setRemoteNpcs`, o sea los NPCs que
            // llegan por RED. Los que simula la IA de este cliente viven en `serverNpcs`. Leyendo
            // el flujo se ve un mundo vacío para siempre, sin ningún error: el tick corre, spawnea
            // y mueve, y la pantalla recibe una lista vacía. Android lo hace bien porque el VM
            // llama a `getServerNpcs()`.
            // ⚠️ **`.toList()` NO sobra.** `getServerNpcs()` devuelve la lista VIVA (el propio
            // `PowListaConcurrente`), no una foto. Asignarla tal cual a un estado de Compose es
            // asignar SIEMPRE la misma referencia: Compose no ve cambio, no recompone, y el
            // contador se queda clavado en el primer número mientras el mapa se llena de NPCs
            // (pasó: decía "2" con 25 en pantalla). Además evita leer la lista desde la UI
            // mientras la IA la muta.
            npcs = cerebro.getServerNpcs().toList()
            puente?.actualizarNpcs(npcs, usarEmoji = usarEmoji)
            delay(33)
        }
    }

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
                    // 🖼️ LOS ASSETS DEL MUNDO. Sin este manejador el HTML pide
                    // `pow-asset:///SPRITES/…` y WebKit lo descarta sin decir nada: es lo que
                    // dejaba el mapa sin un solo sprite. Ver `AssetsWebIos`.
                    setURLSchemeHandler(AssetsWebIos(), forURLScheme = ESQUEMA_ASSETS_POW)
                    // 🌉 LA VUELTA DEL PUENTE (JS → Kotlin). El shim define `window.Android`, que es
                    // lo que llama el HTML COMPARTIDO, así que el mapa no sabe en qué plataforma
                    // corre. Ver `PuenteJsIos`.
                    userContentController.addUserScript(
                        WKUserScript(
                            source = PUENTE_JS_SHIM,
                            // Al PRINCIPIO del documento: si entrara después, el mapa ya habría
                            // intentado usar `window.Android` y no existiría.
                            injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                            forMainFrameOnly = true,
                        ),
                    )
                    userContentController.addScriptMessageHandler(
                        scriptMessageHandler = PuenteJsIos(
                            alTocarMapa = { lat, lon ->
                                // Mismo gesto que Android: el toque coloca el destino. Se vuelve a
                                // encender el modo porque el JS lo apaga tras cada toque.
                                val destino = GeoPoint(lat, lon)
                                puente?.marcarDestino(destino)
                                puente?.modoColocarDestino(true)
                            },
                        ),
                        name = PUENTE_JS_CANAL,
                    )
                }
                WKWebView(frame = CGRectZero.readValue(), configuration = config).apply {
                    // Sin esto el mapa "rebota" al llegar al borde y se siente roto para un juego.
                    scrollView.bounces = false
                    // ⏳ Quien avisa de que el HTML ya existe. Sin esto, el estado inicial se
                    // empujaba antes de tiempo y la guarda del puente se lo tragaba (ver
                    // `CargaMapaIos`: es lo que dejaba el toque del mapa muerto para siempre).
                    navigationDelegate = carga
                    val html = buildHtml(
                        lat = ESCOM_LAT,
                        lng = ESCOM_LON,
                        zoom = ZOOM_JUEGO,
                        // 🖼️ Los sprites del mundo salen del bundle por `pow-asset://`, que resuelve
                        // `AssetsWebIos` (registrado arriba en la configuración).
                        assetBaseUrl = PREFIJO_ASSETS_POW,
                    )
                    // baseURL nula: Leaflet y las teselas de OSM son URLs absolutas HTTPS, así que
                    // no hay nada relativo que resolver.
                    loadHTMLString(html, baseURL = null)
                    puente = PuenteMapaIos(this)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // 🌉 EL ESTADO INICIAL, cuando el HTML ya puede recibirlo.
        //
        // ⚠️ Antes esto era un `DisposableEffect` que corría justo tras crear la vista, o sea con
        // la página todavía cargando: las dos llamadas se perdían en silencio (la guarda
        // `typeof f === 'function'` del puente). El marcador del jugador se recuperaba al primer
        // paso, pero `modoColocarDestino` **no se recuperaba nunca** y el toque en el mapa quedaba
        // muerto. Ahora lo dispara `CargaMapaIos` desde `didFinishNavigation`.
        LaunchedEffect(puente, htmlListo) {
            if (!htmlListo) return@LaunchedEffect
            val p = puente ?: return@LaunchedEffect
            p.moverJugador(jugador)
            // El JS solo avisa de los toques si el modo "colocar destino" está encendido, y lo
            // apaga tras cada uno. Se enciende aquí para que el PRIMER toque ya funcione.
            p.modoColocarDestino(true)
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
                textoEstado(estadoCalles, npcs.size),
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
                val destino = jugador.desplazado(
                    metrosNorte = -sin(angulo) * PASO_METROS,
                    metrosEste = cos(angulo) * PASO_METROS,
                )
                // 🧱 Si el paso cruza un muro, NO se mueve. Misma regla que `WorldMapMovement.kt`
                // en Android: se comprueba el TRAYECTO, no solo el destino, para que un paso largo
                // no atraviese una barda de un salto.
                val choca = colisiones.chocaAlMoverse(
                    jugador.latitude, jugador.longitude, destino.latitude, destino.longitude,
                )
                if (!choca) {
                    jugador = destino
                    puente?.moverJugador(jugador)
                    puente?.moverNiebla(jugador)
                    puente?.centrarEn(jugador, ZOOM_JUEGO)
                }
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

/**
 * Zoom 17.
 *
 * ⚠️ **No es un gusto, es un requisito:** `updateNpcs` del HTML no dibuja NI UN NPC por debajo de
 * **16.5** (`isZoomedIn`). A 16 el puente manda los datos, el JS los recibe y no aparece nada, sin
 * error por ningún lado. Si algún día se baja el zoom, hay que tocar también esa guarda del HTML.
 */
private const val ZOOM_JUEGO = 17

/** Cuántas veces se le insiste a Overpass antes de rendirse y decirlo en pantalla. */
private const val INTENTOS_CALLES = 4

/**
 * ⚠️ Marcador de sitio. Hoy no se usa ni una imagen porque no hay inyección de datos; el día que la
 * haya, aquí va el esquema propio que resuelva desde el bundle (ver `iosApp/POW/MapaWeb.swift`,
 * que ya tiene ese manejador escrito y verificado).
 */
private const val PREFIJO_ASSETS_PENDIENTE = "pow-asset:///"

/** En qué punto está la descarga de calles, que es de lo que dependen los NPCs. */
private enum class EstadoCalles { DESCARGANDO, REINTENTANDO, LISTO, SIN_RED }

/**
 * El texto del cartel. Dice la VERDAD de lo que está pasando: sin esto, "Overpass me limitó por
 * IP" y "el código no funciona" se ven exactamente igual en pantalla.
 */
private fun textoEstado(estado: EstadoCalles, cuantos: Int): String = when (estado) {
    EstadoCalles.DESCARGANDO -> "Descargando las calles de Overpass…"
    EstadoCalles.REINTENTANDO -> "Overpass limitó la petición (429). Reintentando…"
    EstadoCalles.SIN_RED -> "Sin calles: Overpass no respondió (suele ser el límite por IP). Se camina, sin NPCs."
    EstadoCalles.LISTO -> "Caminas, las bardas frenan y la IA mueve $cuantos NPCs"
}
