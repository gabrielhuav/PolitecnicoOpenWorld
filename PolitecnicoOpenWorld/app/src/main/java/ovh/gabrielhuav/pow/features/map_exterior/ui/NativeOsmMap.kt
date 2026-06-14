package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.gson.Gson
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import ovh.gabrielhuav.pow.domain.models.EscomBoundingBox
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.TeleportCatalog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.ActionButtonsController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.AssetPickerDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DPadController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.DesignerPanel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.JoystickController
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerCharacter
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedySpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// REFACTOR: extensiones del VM (WorldMapDesigner.kt) usadas por los lápices del diseñador.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.moveSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectLandmark
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt
import android.util.Log
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin
import androidx.compose.runtime.MutableState
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState

@Composable
internal fun NativeOsmMap(
    uiState: WorldMapState,
    viewModel: WorldMapViewModel,
    context: Context,
    roadNetwork: List<MapWay>,
    allCollectibles: List<ActiveCollectible>,
    nativeDrawableCache: MutableMap<String, android.graphics.drawable.Drawable>,
    landmarkBitmapCache: MutableMap<String, android.graphics.Bitmap?>,
    nativeMapRef: MutableState<MapView?>,
) {
    var hasTriggeredNativePan by remember { mutableStateOf(false) }

    // OPT gama baja: holder NO observable de la última lista de NPCs renderizada. El
    // `update` corre en CADA recomposición (~30 Hz por el jugador) pero los NPCs cambian
    // a ~10 Hz; reconstruir sus marcadores solo cuando la lista cambia evita ~2/3 del
    // trabajo de sprites (lo más caro en gama baja).
    val lastNpcRenderHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.Npc>>(1) }

    // Cachés de los waypoints/rutas de policía. Se guardan en variables RECORDADAS (no en
    // view tags) para no chocar con el frágil hack de claves derivadas (id + 100 / + 400)
    // que usan los marcadores de debug/metro; añadir nuevos R.id desplazaba esos valores
    // y provocaba un ClassCastException (Polyline → Marker).
    val policeWpCache = remember { mutableMapOf<String, org.osmdroid.views.overlay.Marker>() }
    val policeRouteCache = remember { mutableMapOf<String, org.osmdroid.views.overlay.Polyline>() }
    // Cachés de los waypoints/rutas de zombis (fuera del fog of war).
    val zombieWpCache = remember { mutableMapOf<String, org.osmdroid.views.overlay.Marker>() }
    val zombieRouteCache = remember { mutableMapOf<String, org.osmdroid.views.overlay.Polyline>() }
    // Pools para las "balas" de la policía: un círculo que viaja y el emoji 🔫 en el oficial.
    val policeBulletPool = remember { mutableListOf<org.osmdroid.views.overlay.Marker>() }
    val policeGunPool = remember { mutableListOf<org.osmdroid.views.overlay.Marker>() }
    // 📞 sobre NPCs que "llaman a la policía" (p. ej. al que le robaste el coche).
    val phoneCache = remember { mutableMapOf<String, org.osmdroid.views.overlay.Marker>() }
    // 📢 sobre zombis SCOUT que están gritando (alarma de horda).
    val screamCache = remember { mutableMapOf<String, org.osmdroid.views.overlay.Marker>() }
    // OPT FPS gama baja: firma (transformación + asset) por landmark. Los GroundOverlay
    // ESTÁTICOS se re-posicionaban y RE-SUBÍAN su textura (setImage) en CADA frame; con esto
    // solo lo hacen cuando su firma cambia (las puertas, animadas, siguen refrescando imagen).
    val landmarkSigCache = remember { mutableMapOf<Long, String>() }

    // CICLO DE VIDA del MapView de osmdroid: liberarlo al salir (onPause/onDetach), si no
    // se fuga y puede dejar la pantalla rota (azul) al volver de Ajustes.
    DisposableEffect(Unit) {
        onDispose {
            try { nativeMapRef.value?.onPause() } catch (_: Exception) {}
            try { nativeMapRef.value?.onDetach() } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                // ─── CACHÉ OFFLINE UNIFICADA ────────────────────────────────
                // Enrutar osmdroid por la MISMA caché Room que las versiones Web
                // (descarga con UA de navegador + persiste). Arregla que el nativo
                // no cargara zonas nuevas y permite juego 100% offline tras visitar.
                try {
                    val roomModule = ovh.gabrielhuav.pow.data.cache.RoomTileModuleProvider(
                        ctx.applicationContext, viewModel.tileCache
                    )
                    // OVER-ZOOM: osmdroid no puede pedir teselas reales por encima de z19
                    // (máximo de OSM). El aproximador escala las teselas de z19 ya
                    // descargadas para rellenar z20–22, de modo que el zoom extra no deje
                    // el mapa en blanco. Por eso la pantalla de carga descarga z19.
                    val approximater = org.osmdroid.tileprovider.modules.MapTileApproximater()
                    approximater.addProvider(roomModule)
                    val roomProvider = org.osmdroid.tileprovider.MapTileProviderArray(
                        TileSourceFactory.MAPNIK,
                        null,
                        arrayOf<org.osmdroid.tileprovider.modules.MapTileModuleProviderBase>(
                            roomModule, approximater
                        )
                    )
                    roomProvider.setTileSource(TileSourceFactory.MAPNIK)
                    setTileProvider(roomProvider)
                } catch (_: Exception) {}
                setUseDataConnection(true)
                setMultiTouchControls(true)
                // ─── ZOOM EXTENDIDO (OVER-ZOOM) ─────────────────────────────
                // Por defecto osmdroid limita el zoom al máximo de la fuente de
                // teselas (MAPNIK = 19), por eso "topaba" y el personaje se veía
                // muy pequeño. Subimos el techo a 22 (igual que el estado del
                // ViewModel y que las versiones Web) para poder acercarse más;
                // osmdroid escala las teselas de mayor nivel disponible.
                setMaxZoomLevel(22.0)
                setMinZoomLevel(14.0)
                // Ocultar los botones de zoom NATIVOS de osmdroid: el zoom vive solo
                // en el menú anidado (Mapa → Acercar/Alejar). Evita botones duplicados.
                try {
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                    )
                } catch (_: Exception) {}
                // Canal de retorno del zoom por gesto (pinch): sincroniza el nivel de zoom
                // del mapa con el estado para que el bucle de render no lo resetee.
                addMapListener(object : org.osmdroid.events.MapListener {
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        event?.let { viewModel.onMapZoomChanged(it.zoomLevel) }
                        return false
                    }
                })
                setBackgroundColor(android.graphics.Color.parseColor("#0D0D11"))
                controller.setZoom(uiState.zoomLevel)
                // NEBLINA (fog of war) ANCLADA AL JUGADOR: como overlay del mapa, se
                // proyecta sobre la posición real del jugador y por tanto NO se queda
                // pegada al centro de la pantalla al hacer scroll/zoom.
                val fog = FogOverlay()
                overlays.add(fog)
                setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag + 600, fog)
                nativeMapRef.value = this
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            val timeMs = System.currentTimeMillis()
            val screenDensity = context.resources.displayMetrics.density
            val highResRenderScale = 1.0f * screenDensity

            if (uiState.isDesignerMode) {
                view.setOnTouchListener(null)
                view.isClickable = true
            } else {
                view.setOnTouchListener { _, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> hasTriggeredNativePan = false
                        android.view.MotionEvent.ACTION_MOVE -> {
                            if (!hasTriggeredNativePan) {
                                viewModel.onMapPanStart()
                                hasTriggeredNativePan = true
                            }
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            if (hasTriggeredNativePan) {
                                viewModel.onMapPanEnd()
                                hasTriggeredNativePan = false
                            }
                        }
                    }
                    false
                }
                view.isClickable = false
            }

            if (!uiState.isUserPanningMap) {
                uiState.currentLocation?.let { view.controller.setCenter(it) }
            }

            view.mapOrientation = if (uiState.isDriving) -uiState.vehicleRotation else 0f

            if (uiState.isUserPanningMap) {
                @Suppress("UNCHECKED_CAST")
                val playerMarker = (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag) as? Marker)
                    ?: Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        val dot = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(android.graphics.Color.GREEN)
                            setStroke(4, android.graphics.Color.WHITE)
                            setSize(40, 40)
                        }
                        icon = dot
                        view.setTag(ovh.gabrielhuav.pow.R.id.player_marker_tag, this)
                        view.overlays.add(this)
                    }
                uiState.currentLocation?.let { playerMarker.position = it; playerMarker.setAlpha(1f) }
            } else {
                (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag) as? Marker)?.setAlpha(0f)
            }

            val destMarker = (view.getTag(ovh.gabrielhuav.pow.R.id.dest_marker_tag) as? Marker)
                ?: Marker(view).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = ContextCompat.getDrawable(context, android.R.drawable.ic_dialog_map)
                    icon?.setTint(android.graphics.Color.RED)
                    view.setTag(ovh.gabrielhuav.pow.R.id.dest_marker_tag, this)
                    view.overlays.add(this)
                }

            if (uiState.destinationMarker != null) {
                destMarker.position = uiState.destinationMarker
                destMarker.isEnabled = true
                destMarker.isDraggable = false
                destMarker.setAlpha(1f)
            } else {
                destMarker.isEnabled = false
                destMarker.closeInfoWindow()
                destMarker.setAlpha(0f)
            }

            // ─── WAYPOINTS DE PATRULLAS (solo fuera del fog of war) ──────────────
            // Mientras la patrulla está FUERA de tu campo de visión, te la marca con un
            // waypoint. En cuanto entra al fog desaparece el waypoint y la ves directa.
            val playerLocWp = uiState.currentLocation
            // Solo se marcan mientras te BUSCAN (wantedLevel > 0). Al morir/perder estrellas
            // la policía se retira pero ya no debe marcarse (no te está persiguiendo).
            val patrolsToMark = if (playerLocWp != null && uiState.wantedLevel > 0) {
                uiState.npcs.filter { it.type == NpcType.POLICE_CAR &&
                    !npcWithinRadius(it.location.latitude, it.location.longitude,
                        playerLocWp.latitude, playerLocWp.longitude, NPC_FOG_VISION_METERS) }
            } else emptyList()

            val patrolWpIds = patrolsToMark.map { it.id }.toSet()
            val wpIterator = policeWpCache.iterator()
            while (wpIterator.hasNext()) {
                val entry = wpIterator.next()
                if (!patrolWpIds.contains(entry.key)) {
                    view.overlays.remove(entry.value)
                    wpIterator.remove()
                }
            }
            // Línea/ruta desde el jugador hasta cada patrulla, para ver qué tan cerca viene.
            val routeIterator = policeRouteCache.iterator()
            while (routeIterator.hasNext()) {
                val entry = routeIterator.next()
                if (!patrolWpIds.contains(entry.key)) {
                    view.overlays.remove(entry.value)
                    routeIterator.remove()
                }
            }

            patrolsToMark.forEach { patrol ->
                val wp = policeWpCache[patrol.id] ?: Marker(view).apply {
                    title = "POLICE_WAYPOINT"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    // EMOJI de patrulla mientras viene de lejos (fuera del fog). Al entrar
                    // al fog desaparece este waypoint y se ve el asset real de la patrulla.
                    val px = (26 * context.resources.displayMetrics.density).toInt()
                    icon = emojiToDrawable(context, "🚓", px)
                    policeWpCache[patrol.id] = this
                    view.overlays.add(this)
                }
                wp.position = GeoPoint(patrol.location.latitude, patrol.location.longitude)
                wp.isEnabled = true
                wp.setAlpha(1f)

                // Ruta (línea) jugador → patrulla: se acorta conforme se acercan.
                if (playerLocWp != null) {
                    val line = policeRouteCache[patrol.id] ?: Polyline().apply {
                        // Línea PUNTEADA y semi-transparente (menos invasiva en el mapa).
                        outlinePaint.color = android.graphics.Color.argb(120, 0, 90, 255)
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.style = android.graphics.Paint.Style.STROKE
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 14f), 0f)
                        setInfoWindow(null)
                        policeRouteCache[patrol.id] = this
                        view.overlays.add(0, this) // bajo los marcadores
                    }
                    line.setPoints(listOf(
                        GeoPoint(playerLocWp.latitude, playerLocWp.longitude),
                        GeoPoint(patrol.location.latitude, patrol.location.longitude)
                    ))
                    line.isEnabled = true
                }
            }

            // ─── WAYPOINTS DE ZOMBIS (solo fuera del fog of war, modo apocalipsis) ───
            // Similar a las patrullas: marca con un waypoint rojo y una línea cada zombi
            // que esté FUERA del fog of war, para saber dónde están y qué tan cerca vienen.
            val zombiesToMark = if (playerLocWp != null && uiState.globalZombieMode) {
                uiState.npcs.filter { it.type == NpcType.ZOMBIE && it.health > 0f &&
                    !npcWithinRadius(it.location.latitude, it.location.longitude,
                        playerLocWp.latitude, playerLocWp.longitude, NPC_FOG_VISION_METERS) }
            } else emptyList()

            val zombieWpIds = zombiesToMark.map { it.id }.toSet()
            val zombieWpIterator = zombieWpCache.iterator()
            while (zombieWpIterator.hasNext()) {
                val entry = zombieWpIterator.next()
                if (!zombieWpIds.contains(entry.key)) {
                    view.overlays.remove(entry.value)
                    zombieWpIterator.remove()
                }
            }
            val zombieRouteIterator = zombieRouteCache.iterator()
            while (zombieRouteIterator.hasNext()) {
                val entry = zombieRouteIterator.next()
                if (!zombieWpIds.contains(entry.key)) {
                    view.overlays.remove(entry.value)
                    zombieRouteIterator.remove()
                }
            }

            zombiesToMark.forEach { zombie ->
                val wp = zombieWpCache[zombie.id] ?: Marker(view).apply {
                    title = "ZOMBIE_WAYPOINT"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                    // EMOJI de zombi (🧟) mientras está fuera del fog. Al entrar al fog
                    // desaparece este waypoint y se ve el asset real del zombi.
                    val px = (26 * context.resources.displayMetrics.density).toInt()
                    icon = emojiToDrawable(context, "🧟", px)
                    zombieWpCache[zombie.id] = this
                    view.overlays.add(this)
                }
                wp.position = GeoPoint(zombie.location.latitude, zombie.location.longitude)
                wp.isEnabled = true
                wp.setAlpha(1f)

                // Ruta (línea) jugador → zombi: línea roja punteada para distinguir de policía.
                if (playerLocWp != null) {
                    val line = zombieRouteCache[zombie.id] ?: Polyline().apply {
                        // Línea PUNTEADA ROJA y semi-transparente.
                        outlinePaint.color = android.graphics.Color.argb(120, 255, 0, 0)
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.isAntiAlias = true
                        outlinePaint.style = android.graphics.Paint.Style.STROKE
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 14f), 0f)
                        setInfoWindow(null)
                        zombieRouteCache[zombie.id] = this
                        view.overlays.add(0, this) // bajo los marcadores
                    }
                    line.setPoints(listOf(
                        GeoPoint(playerLocWp.latitude, playerLocWp.longitude),
                        GeoPoint(zombie.location.latitude, zombie.location.longitude)
                    ))
                    line.isEnabled = true
                }
            }

            // ─── BALAS DE LA POLICÍA (círculo que viaja lento + 🔫 en el oficial) ──
            val shots = uiState.policeShots
            val pLocB = uiState.currentLocation
            if (pLocB != null) {
                val mppB = (40075016.686 * Math.cos(Math.toRadians(pLocB.latitude))) /
                        (256.0 * Math.pow(2.0, uiState.zoomLevel))
                val bulletPx = ((0.30 / mppB) * screenDensity).toInt().coerceIn(6, 22) // bala pequeña
                val gunPx = ((1.0 / mppB) * screenDensity).toInt().coerceIn(14, 72)
                val nowB = timeMs
                val BULLET_MS = 420.0
                // Crece los pools según haga falta.
                while (policeBulletPool.size < shots.size) {
                    policeBulletPool.add(Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null)
                        isEnabled = false; view.overlays.add(this)
                    })
                    // La pistola se ancla por su parte INFERIOR para que flote ARRIBA del policía.
                    policeGunPool.add(Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setInfoWindow(null)
                        isEnabled = false; view.overlays.add(this)
                    })
                }
                policeBulletPool.forEachIndexed { i, bullet ->
                    val gun = policeGunPool[i]
                    if (i < shots.size) {
                        val s = shots[i]
                        val prog = ((nowB - s.at) / BULLET_MS).coerceIn(0.0, 1.0)
                        val blat = s.from.latitude + (s.to.latitude - s.from.latitude) * prog
                        val blon = s.from.longitude + (s.to.longitude - s.from.longitude) * prog
                        // OPT GC: cacheado por tamaño (solo cambia con el zoom) en vez de
                        // crear un Bitmap nuevo por frame mientras la policía dispara.
                        bullet.icon = nativeDrawableCache.getOrPut("POLICE_BULLET_$bulletPx") {
                            dotDrawable(context, android.graphics.Color.rgb(255, 210, 0), bulletPx)
                        }
                        bullet.position = GeoPoint(blat, blon)
                        bullet.isEnabled = true
                        // 🔫 ARRIBA del policía durante casi todo el disparo (se nota bien).
                        if (prog < 0.85) {
                            gun.icon = nativeDrawableCache.getOrPut("POLICE_GUN_$gunPx") {
                                emojiToDrawable(context, "🔫", gunPx)
                            }
                            // Un pelín al norte para que quede claramente sobre su cabeza.
                            gun.position = GeoPoint(s.from.latitude + 0.000016, s.from.longitude)
                            gun.isEnabled = true
                        } else gun.isEnabled = false
                    } else {
                        bullet.isEnabled = false; gun.isEnabled = false
                    }
                }

                // ─── 📞 NPCs llamando a la policía (carjack) ─────────────────────
                val phonePx = ((0.9 / mppB) * screenDensity).toInt().coerceIn(12, 64)
                val callingNpcs = uiState.npcs.filter { it.callingUntil > nowB }
                val callIds = callingNpcs.map { it.id }.toSet()
                val phoneIt = phoneCache.iterator()
                while (phoneIt.hasNext()) {
                    val e = phoneIt.next()
                    if (!callIds.contains(e.key)) { view.overlays.remove(e.value); phoneIt.remove() }
                }
                callingNpcs.forEach { npc ->
                    val m = phoneCache[npc.id] ?: Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setInfoWindow(null)
                        phoneCache[npc.id] = this; view.overlays.add(this)
                    }
                    m.icon = nativeDrawableCache.getOrPut("NPC_PHONE_$phonePx") {
                        emojiToDrawable(context, "📞", phonePx)
                    }
                    m.position = GeoPoint(npc.location.latitude + 0.000016, npc.location.longitude)
                    m.isEnabled = true
                }

                // ─── 📢 Zombis SCOUT gritando (alarma de horda) ──────────────────
                val screamPx = ((1.0 / mppB) * screenDensity).toInt().coerceIn(14, 72)
                val screamingNpcs = uiState.npcs.filter {
                    it.type == NpcType.ZOMBIE &&
                        it.zombieRole == ovh.gabrielhuav.pow.domain.models.ZombieRole.SCOUT &&
                        it.screamUntil > nowB
                }
                val screamIds = screamingNpcs.map { it.id }.toSet()
                val screamIt = screamCache.iterator()
                while (screamIt.hasNext()) {
                    val e = screamIt.next()
                    if (!screamIds.contains(e.key)) { view.overlays.remove(e.value); screamIt.remove() }
                }
                screamingNpcs.forEach { npc ->
                    val m = screamCache[npc.id] ?: Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setInfoWindow(null)
                        screamCache[npc.id] = this; view.overlays.add(this)
                    }
                    m.icon = nativeDrawableCache.getOrPut("ZOMBIE_SCREAM_$screamPx") {
                        emojiToDrawable(context, "📢", screamPx)
                    }
                    m.position = GeoPoint(npc.location.latitude + 0.000018, npc.location.longitude)
                    m.isEnabled = true
                }
            }

            val routeOverlay = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag) as? Polyline)
                ?: Polyline().apply {
                    outlinePaint.color = android.graphics.Color.BLUE
                    outlinePaint.strokeWidth = 5f
                    view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag, this)
                    view.overlays.add(0, this)
                }

            if (uiState.destinationMarker != null && uiState.routeWaypoints.isNotEmpty() && uiState.showDestinationRoute) {
                routeOverlay.setPoints(uiState.routeWaypoints)
                routeOverlay.isEnabled = true
            } else {
                routeOverlay.isEnabled = false
            }

            val zoomDiff = abs(view.zoomLevelDouble - uiState.zoomLevel)
            when {
                zoomDiff < 0.01 -> {}
                zoomDiff > 1.5  -> {
                    if (!uiState.isUserPanningMap) {
                        view.controller.animateTo(uiState.currentLocation, uiState.zoomLevel, 120L)
                    }
                }
                else            -> view.controller.setZoom(uiState.zoomLevel)
            }

            if (uiState.isRoadNetworkReady) {
                @Suppress("UNCHECKED_CAST")
                val markerCache = (view.tag as? MutableMap<String, Marker>)
                    ?: mutableMapOf<String, Marker>().also { view.tag = it }

                val currentZoom = view.zoomLevelDouble
                val isZoomedIn = currentZoom >= 16

                // ─── CULLING POR DISTANCIA ──────────────────────────────
                // OPT: solo reconstruimos los marcadores de NPC cuando la LISTA cambió
                // (~10 Hz), no en cada recomposición por movimiento del jugador (~30 Hz).
                if (uiState.npcs !== lastNpcRenderHolder[0]) {
                  lastNpcRenderHolder[0] = uiState.npcs
                val centerCull = uiState.currentLocation
                val visibleNpcs = if (centerCull != null) {
                    val radiusM = npcVisionRadiusMeters()
                    uiState.npcs.filter {
                        npcWithinRadius(it.location.latitude, it.location.longitude,
                            centerCull.latitude, centerCull.longitude, radiusM)
                    }
                } else uiState.npcs

                val currentNpcIds = visibleNpcs.map { it.id }.toSet()
                val iterator = markerCache.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (!currentNpcIds.contains(entry.key)) {
                        view.overlays.remove(entry.value)
                        iterator.remove()
                    }
                }

                    visibleNpcs.forEach { npc ->
                        val id = npc.id
                        val marker = markerCache[id] ?: Marker(view).apply {
                            title = "NPC_MARKER"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            setInfoWindow(null)
                            isFlat = true // isFlat=true asegura que el sprite "pise" el suelo en 3D
                            markerCache[id] = this
                            view.overlays.add(this)
                        }

                        if (isZoomedIn) {
                            if (npc.isDying) {
                                marker.setAlpha(0.3f)
                            } else {
                                marker.setAlpha(1f)
                            }

                            // Calculamos exactamente cuántos metros de la vida real mide 1 pixel en la pantalla.
                            // Usamos uiState.zoomLevel (NO view.zoomLevelDouble) para que el tamaño de los NPCs
                            // coincida EXACTAMENTE con el del jugador/su vehículo (PlayerCharacter también usa
                            // uiState.zoomLevel); si no, durante los ajustes de zoom el coche del jugador se veía
                            // a veces más pequeño que los NPCs.
                            val metersPerPixel = (40075016.686 * Math.cos(Math.toRadians(npc.location.latitude))) /
                                    (256.0 * Math.pow(2.0, uiState.zoomLevel))

                            // ─── LOD DE EMOJIS (optimización gama baja) ──────────────────
                            // Si el ajuste está activo, los NPCs LEJOS (>40 m del jugador) se
                            // dibujan como un emoji barato en vez del sprite/bitmap completo; solo
                            // los MUY cercanos llevan el asset. Recorta mucho el render en gama baja.
                            // "Optimizar para gama baja" (npcFullEmoji): TODOS los NPCs como emoji,
                            // sin importar la distancia. "Optimizar dibujado" (npcEmojiLod): solo
                            // los lejanos (>40 m).
                            val useEmojiLod = uiState.npcFullEmoji ||
                                (uiState.npcEmojiLod && centerCull != null &&
                                !npcWithinRadius(npc.location.latitude, npc.location.longitude,
                                    centerCull.latitude, centerCull.longitude, 40.0))
                            if (useEmojiLod) {
                                val emoji = when (npc.type) {
                                    NpcType.CAR, NpcType.POLICE_CAR -> "🚗"
                                    NpcType.ZOMBIE -> "🧟"
                                    NpcType.POLICE_COP -> "👮"
                                    else -> "🧍"
                                }
                                val px = ((1.0 / metersPerPixel) * screenDensity).toInt().coerceIn(12, 56)
                                marker.icon = nativeDrawableCache.getOrPut("EMOJI_LOD_${npc.type.name}_$px") {
                                    emojiToDrawable(context, emoji, px)
                                }
                                marker.rotation = 0f
                            } else if (npc.type == NpcType.POLICE_CAR || npc.isPoliceSkin) {
                                // PATRULLA (o coche con skin de patrulla tras bajarte de una robada):
                                // asset especial sin repintar, mismo tamaño que un auto.
                                val exactPixels = ((4.0 / metersPerPixel) * screenDensity).toInt().coerceAtLeast(16)
                                var angle = npc.rotationAngle % 360f
                                if (angle < 0) angle += 360f
                                val frameIndex = (angle / 7.5f).roundToInt() % 48
                                val cacheKey = "POLICE_${frameIndex}_${exactPixels}_H${npc.health.toInt()}_D${npc.isDying}"
                                val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                    val baseDrawable = PoliceSpriteManager.getPoliceCar(context, angle, highResRenderScale)
                                    baseDrawable?.let { drawable ->
                                        val ratio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                                        val finalWidthPx: Int; val finalHeightPx: Int
                                        if (ratio > 1f) { finalWidthPx = exactPixels; finalHeightPx = (exactPixels / ratio).toInt() }
                                        else { finalHeightPx = exactPixels; finalWidthPx = (exactPixels * ratio).toInt() }
                                        val withHealth = drawHealthBarOnDrawable(context, drawable, npc.health, npc.isDying)
                                        ExactSizeDrawable(withHealth ?: drawable, finalWidthPx, finalHeightPx)
                                    } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                }
                                marker.icon = cachedIcon
                                marker.rotation = 0f
                            } else if (npc.type == NpcType.POLICE_COP) {
                                // POLICÍA A PIE: no hay asset de persona → se dibuja con un EMOJI.
                                val exactPixels = ((1.05 / metersPerPixel) * screenDensity).toInt().coerceAtLeast(14)
                                val cacheKey = "COP_EMOJI_${exactPixels}_H${npc.health.toInt()}_D${npc.isDying}"
                                val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                    val baseDrawable = emojiToDrawable(context, "👮", exactPixels)
                                    drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying) ?: baseDrawable
                                }
                                marker.icon = cachedIcon
                                marker.rotation = 0f
                            } else if (npc.visualConfig != null && npc.type != NpcType.ZOMBIE) {
                                val currentlyMoving = npc.speed > 0 || npc.isMoving

                                // 🧍 TAMAÑO DEL PEATÓN: algo mayor que el real (1.3 m) para que
                                // los humanos se vean bien y no diminutos.
                                val exactPixels = ((1.3 / metersPerPixel) * screenDensity).toInt().coerceAtLeast(12)

                                val frameIndex = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0
                                val cacheKey = "PED_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${npc.health}_D${npc.isDying}"

                                // FIX "peatón invisible que me pega": si el sprite del personaje aún
                                // NO está listo (assets fríos tras un TP / liberados por onTrimMemory),
                                // getModularNpcDrawable devuelve null. ANTES se cacheaba un drawable
                                // TRANSPARENTE bajo el cacheKey → el peatón quedaba invisible (pero
                                // seguía vivo y te golpeaba). Ahora: si el sprite falla NO se cachea el
                                // fallo (se reintenta cada frame) y se muestra un emoji 🧍 visible.
                                val cachedSprite = nativeDrawableCache[cacheKey]
                                if (cachedSprite != null) {
                                    marker.icon = cachedSprite
                                } else {
                                    var baseDrawable = CharacterSpriteManager.getModularNpcDrawable(
                                        context = context,
                                        visualConfig = npc.visualConfig!!,
                                        isMoving = currentlyMoving,
                                        isFacingRight = npc.facingRight,
                                        timeMs = timeMs,
                                        scale = highResRenderScale,
                                        displayName = npc.displayName
                                    )
                                    baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                    val built = baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                    if (built != null) {
                                        nativeDrawableCache[cacheKey] = built
                                        marker.icon = built
                                    } else {
                                        // Fallback SIEMPRE visible (nunca un NPC invisible). No se cachea
                                        // bajo cacheKey → se reintenta el sprite real cuando cargue.
                                        marker.icon = nativeDrawableCache.getOrPut("PED_FALLBACK_$exactPixels") {
                                            emojiToDrawable(context, "🧍", exactPixels)
                                        }
                                    }
                                }
                                marker.rotation = 0f

                            } else if (npc.type == NpcType.CAR) {
                                // TAMAÑO DEL AUTO: ampliado (4.0 m) porque se veían demasiado
                                // pequeños respecto al jugador y los peatones.
                                val exactPixels = ((4.0 / metersPerPixel) * screenDensity).toInt().coerceAtLeast(16)

                                var angle = npc.rotationAngle % 360f
                                if (angle < 0) angle += 360f
                                val frameIndex = (angle / 7.5f).roundToInt() % 48
                                val cacheKey = "CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${exactPixels}_H${npc.health}_D${npc.isDying}"

                                val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                    var baseDrawable = VehicleSpriteManager.getTintedCarNpc(
                                        context, angle, npc.carColor, highResRenderScale, npc.carModel
                                    )
                                    baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                    baseDrawable?.let { drawable ->
                                        // Mantenemos la proporción (Aspect Ratio) del sprite original
                                        val ratio = drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                                        val finalWidthPx: Int
                                        val finalHeightPx: Int

                                        if (ratio > 1f) {
                                            finalWidthPx = exactPixels
                                            finalHeightPx = (exactPixels / ratio).toInt()
                                        } else {
                                            finalHeightPx = exactPixels
                                            finalWidthPx = (exactPixels * ratio).toInt()
                                        }
                                        ExactSizeDrawable(drawable, finalWidthPx, finalHeightPx)
                                    } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                }
                                marker.icon = cachedIcon
                                marker.rotation = 0f
                            } else if (npc.type == NpcType.ZOMBIE) {
                                // 🧟 ZOMBIE: usa los assets de ZOMBIES_MOD/z_walk.
                                // Tamaño similar al de los peatones (1.3 m).
                                // Tamaño por rol: TANK más grande, RUNNER algo más pequeño.
                                val roleSizeMul = when (npc.zombieRole) {
                                    ovh.gabrielhuav.pow.domain.models.ZombieRole.TANK -> 1.45f
                                    ovh.gabrielhuav.pow.domain.models.ZombieRole.RUNNER -> 0.9f
                                    else -> 1f
                                }
                                val exactPixels = ((1.3 / metersPerPixel) * screenDensity * roleSizeMul).toInt().coerceAtLeast(12)
                                val zFrame = ((timeMs / 220L) % 9L).toInt()
                                val cacheKey = "ZOMBIE_${npc.zombieRole.name}_${npc.facingRight}_${zFrame}_${exactPixels}_H${npc.health.toInt()}_M${npc.maxHealth.toInt()}_D${npc.isDying}"

                                val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                    var baseDrawable: android.graphics.drawable.Drawable? = ovh.gabrielhuav.pow.features.map_exterior.ui.components.MapZombieSpriteManager.getZombieDrawable(
                                        context = context,
                                        npc = npc,
                                        timeMs = timeMs,
                                        scale = highResRenderScale
                                    )
                                    baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying, npc.maxHealth)
                                    baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                        ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                }
                                marker.icon = cachedIcon
                                marker.rotation = 0f
                            } else {
                                // Resto de NPCs genéricos (SVG)
                                val cacheKey = "SVG_${npc.type.name}_H${npc.health}_D${npc.isDying}"
                                val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                    val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                    var baseDrawable = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                    baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                    baseDrawable?.let {
                                        val exactPx = (24 * screenDensity).toInt()
                                        ExactSizeDrawable(it, exactPx, exactPx)
                                    } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                }
                                marker.icon = cachedIcon
                                marker.rotation = 0f
                            }
                        } else {
                            marker.setAlpha(0f)
                        }
                        marker.position = GeoPoint(npc.location.latitude, npc.location.longitude)
                    }
                } // fin guard: lista de NPCs sin cambios → no se reconstruyen marcadores

                val activeCollectibleIds = allCollectibles.map { it.id }.toSet()
                @Suppress("UNCHECKED_CAST")
                val collectibleMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag) as? MutableMap<String, Marker>)
                    ?: mutableMapOf<String, Marker>().also { view.setTag(ovh.gabrielhuav.pow.R.id.collectible_cache_tag, it) }

                val colIterator = collectibleMarkerCache.iterator()
                while (colIterator.hasNext()) {
                    val entry = colIterator.next()
                    if (!activeCollectibleIds.contains(entry.key)) {
                        view.overlays.remove(entry.value)
                        colIterator.remove()
                    }
                }

                allCollectibles.forEach { collectible ->
                    // (OPT gama baja) eliminado Log.d por-frame: se ejecutaba en cada
                    // recomposición (~30 Hz) por cada coleccionable, costoso sin valor.
                    val id = collectible.id
                    val marker = collectibleMarkerCache[id] ?: Marker(view).apply {
                        title = "COLLECTIBLE"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isFlat = true
                        collectibleMarkerCache[id] = this
                        view.overlays.add(this)
                    }

                    if (isZoomedIn) {
                        marker.setAlpha(1f)
                        val exactPixels = (22 * screenDensity).toInt()
                        val cacheKey = "COL_${collectible.assetPath}"
                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                            try {
                                val bitmap = android.graphics.BitmapFactory.decodeStream(context.assets.open(collectible.assetPath))
                                if (bitmap != null) {
                                    val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                                        shape = android.graphics.drawable.GradientDrawable.OVAL
                                        setSize(exactPixels, exactPixels)
                                        setColor(android.graphics.Color.argb(100, 255, 235, 59))
                                    }
                                    val spriteDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                    val spriteSize = (exactPixels * 0.90).toInt()
                                    spriteDrawable.setFilterBitmap(false)
                                    val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(glowDrawable, spriteDrawable))
                                    val inset = ((exactPixels - spriteSize) / 2)
                                    layerDrawable.setLayerInset(1, inset, inset, inset, inset)
                                    ExactSizeDrawable(layerDrawable, exactPixels, exactPixels)
                                } else ContextCompat.getDrawable(context, android.R.color.transparent)!!
                            } catch (e: Exception) {
                                ContextCompat.getDrawable(context, android.R.color.transparent)!!
                            }
                        }
                        marker.icon = cachedIcon
                        val isHand = collectible.name == "Objeto Misterioso ESCOM" || collectible.id == "global_zombie_hand"
                        marker.rotation = if (isHand) 0f else ((System.currentTimeMillis() / 30) % 360).toFloat()
                    } else {
                        marker.setAlpha(0f)
                    }
                    marker.position = GeoPoint(collectible.latitude, collectible.longitude)
                }
            }

            @Suppress("UNCHECKED_CAST")
            val landmarkCache = (view.getTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag) as? MutableMap<Long, MutableList<Overlay>>)
                ?: mutableMapOf<Long, MutableList<Overlay>>().also { view.setTag(ovh.gabrielhuav.pow.R.id.landmark_cache_tag, it) }

            val currentIds = uiState.landmarks.map { it.id }.toSet()
            val landmarkIterator = landmarkCache.iterator()
            while (landmarkIterator.hasNext()) {
                val entry = landmarkIterator.next()
                if (!currentIds.contains(entry.key)) {
                    entry.value.forEach { overlay -> view.overlays.remove(overlay) }
                    landmarkIterator.remove()
                    // Limpia la firma para que, si el landmark vuelve, se re-renderice (si no,
                    // la firma vieja coincidiría y el nuevo overlay quedaría sin imagen/posición).
                    landmarkSigCache.remove(entry.key)
                }
            }

            uiState.landmarks.forEach { landmark ->
                val overlays = landmarkCache.getOrPut(landmark.id) { mutableListOf() }
                val bitmap = landmarkBitmapCache.getOrPut(landmark.assetPath) {
                    try {
                        context.assets.open(landmark.assetPath).use { val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }; android.graphics.BitmapFactory.decodeStream(it, null, o) }
                    } catch (e: Exception) { null }
                }
                if (bitmap == null) return@forEach

                val isDoorAsset = landmark.assetPath.contains("DOORS/")
                val groundOverlay = overlays.filterIsInstance<org.osmdroid.views.overlay.GroundOverlay>().firstOrNull()
                    ?: org.osmdroid.views.overlay.GroundOverlay().apply {
                        overlays.add(this)
                        if (isDoorAsset) view.overlays.add(this) else view.overlays.add(0, this)
                    }


                val center = GeoPoint(landmark.location.latitude, landmark.location.longitude)
                // OPT FPS: recomputar geometría + re-subir textura SOLO si la firma cambió.
                val sig = "${landmark.location.latitude},${landmark.location.longitude},${landmark.rotationAngle}," +
                        "${landmark.scaleX},${landmark.scaleY},${landmark.baseWidthMeters},${landmark.baseHeightMeters},${landmark.assetPath}"
                val sigChanged = landmarkSigCache[landmark.id] != sig
                if (sigChanged) {
                    landmarkSigCache[landmark.id] = sig
                    val halfW = (landmark.baseWidthMeters * landmark.scaleX) / 2.0
                    val halfH = (landmark.baseHeightMeters * landmark.scaleY) / 2.0
                    val d = sqrt(halfW * halfW + halfH * halfH)
                    val theta = Math.toDegrees(atan2(halfW, halfH))
                    val pTL = center.destinationPoint(d, landmark.rotationAngle.toDouble() - theta)
                    val pTR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + theta)
                    val pBR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 - theta)
                    val pBL = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 + theta)
                    groundOverlay.setPosition(pTL, pTR, pBR, pBL)
                }
                // Puertas: brillo ANIMADO → refrescan imagen cada frame. Estáticos: solo al cambiar.
                if (isDoorAsset) {
                    groundOverlay.setImage(buildDoorEffectBitmap(bitmap, context))
                } else if (sigChanged) {
                    groundOverlay.setImage(bitmap)
                }

                // Limpiar cualquier marcador DOOR_PULSE residual de la versión anterior
                overlays.filterIsInstance<Marker>().filter { it.title == "DOOR_PULSE" }.forEach { m ->
                    view.overlays.remove(m); overlays.remove(m)
                }
                val existingControl = overlays.filterIsInstance<Marker>().firstOrNull()
                if (uiState.isDesignerMode) {
                    val controlMarker = existingControl ?: Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_edit)?.mutate()
                        overlays.add(this)
                        view.overlays.add(this)
                    }
                    controlMarker.position = center
                    if (uiState.selectedLandmarkId == landmark.id) controlMarker.icon?.setTint(android.graphics.Color.RED)
                    else controlMarker.icon?.setTintList(null)

                    controlMarker.setOnMarkerClickListener { _, _ -> viewModel.selectLandmark(landmark.id); true }
                    controlMarker.isDraggable = true
                    controlMarker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                        override fun onMarkerDragStart(marker: Marker) { viewModel.selectLandmark(landmark.id) }
                        override fun onMarkerDrag(marker: Marker) {
                            viewModel.moveSelectedLandmark(marker.position.latitude - landmark.location.latitude, marker.position.longitude - landmark.location.longitude)
                        }
                        override fun onMarkerDragEnd(marker: Marker) {}
                    })
                } else {
                    existingControl?.let { view.overlays.remove(it); overlays.remove(it) }
                }
            }

            // ─── CAPA INTERMEDIA: RED DE CAMINOS ─────────────────────
            val roadOverlayTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 500
            @Suppress("UNCHECKED_CAST")
            val roadLineCache = (view.getTag(roadOverlayTag) as? MutableList<Polyline>)
                ?: mutableListOf<Polyline>().also { view.setTag(roadOverlayTag, it) }

            roadLineCache.forEach { view.overlays.remove(it) }
            roadLineCache.clear()

            if (uiState.showRoadNetwork) {
                val lmCount = landmarkCache.values.sumOf { it.size }
                roadNetwork.forEach { way ->
                    val line = Polyline().apply {
                        outlinePaint.color = if (way.isForCars)
                            android.graphics.Color.argb(180, 255, 215, 0)
                        else
                            android.graphics.Color.argb(180, 130, 200, 255)
                        outlinePaint.strokeWidth = if (way.isForCars) 6f else 4f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                        setPoints(way.nodes.map { GeoPoint(it.lat, it.lon) })
                    }
                    roadLineCache.add(line)
                    view.overlays.add(lmCount.coerceAtMost(view.overlays.size), line)
                }
            }

            // ─── OVERLAY DEBUG DE INTERIORES ──────────────────────────
            @Suppress("UNCHECKED_CAST")
            val debugMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag.let { it + 100 }) as? MutableMap<String, Marker>)
                ?: mutableMapOf<String, Marker>().also {
                    view.setTag(ovh.gabrielhuav.pow.R.id.player_marker_tag.let { it + 100 }, it)
                }

            // Overlays del Debug Interiores: caminos del navGraph (líneas) + zonas NO caminables
            // (polígonos rojos) + bardas. Se reconstruyen SOLO cuando cambian los landmarks o las
            // colisiones (no por frame). Sig[0]=landmarks, Sig[1]=exteriorCollisions.
            @Suppress("UNCHECKED_CAST")
            val interiorPathCache = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 250 }) as? MutableList<org.osmdroid.views.overlay.Overlay>)
                ?: mutableListOf<org.osmdroid.views.overlay.Overlay>().also { view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 250 }, it) }
            @Suppress("UNCHECKED_CAST")
            val interiorPathSig = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 260 }) as? Array<Any?>)
                ?: arrayOfNulls<Any?>(2).also { view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 260 }, it) }

            if (uiState.showInteriorDebugOverlay) {
                InteriorBuilding.entries.forEach { b ->
                    val marker = debugMarkerCache[b.id] ?: Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        val dot = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(android.graphics.Color.YELLOW)
                            setStroke(3, android.graphics.Color.BLACK)
                            setSize(28, 28)
                        }
                        icon = dot
                        title = b.displayName
                        debugMarkerCache[b.id] = this
                        view.overlays.add(this)
                    }
                    marker.position = b.location
                    marker.setAlpha(1f)
                }

                val bbox = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }) as? Polyline)
                    ?: Polyline().apply {
                        outlinePaint.color = android.graphics.Color.YELLOW
                        outlinePaint.strokeWidth = 4f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                        view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }, this)
                        view.overlays.add(this)
                    }
                val bb = EscomBoundingBox
                bbox.setPoints(listOf(bb.topLeft, bb.topRight, bb.bottomRight, bb.bottomLeft, bb.topLeft))
                bbox.isEnabled = true

                // CAMINOS ADICIONALES (navGraph de los landmarks): muestra por dónde se puede
                // caminar (verde = peatonal) y por dónde van los autos (naranja). Se reconstruye
                // solo si cambió la lista de landmarks (evita rehacerlo cada frame).
                if (interiorPathSig[0] !== uiState.landmarks || interiorPathSig[1] !== uiState.exteriorCollisions) {
                    interiorPathSig[0] = uiState.landmarks
                    interiorPathSig[1] = uiState.exteriorCollisions
                    interiorPathCache.forEach { view.overlays.remove(it) }
                    interiorPathCache.clear()
                    // 1) ZONAS NO CAMINABLES (polígonos rojos translúcidos, p. ej. el edificio ESCOM).
                    uiState.exteriorCollisions?.polygons?.forEach { poly ->
                        if (poly.nodes.size < 3) return@forEach
                        val polygon = org.osmdroid.views.overlay.Polygon().apply {
                            points = poly.nodes.map { GeoPoint(it.lat, it.lon) }
                            fillPaint.color = android.graphics.Color.argb(90, 220, 40, 40)   // rojo translúcido = NO entrar
                            outlinePaint.color = android.graphics.Color.argb(230, 200, 0, 0)
                            outlinePaint.strokeWidth = 4f
                            outlinePaint.isAntiAlias = true
                        }
                        interiorPathCache.add(polygon)
                        view.overlays.add(polygon)
                    }
                    // 2) BARDAS (líneas rojas gruesas).
                    uiState.exteriorCollisions?.walls?.forEach { wall ->
                        val line = Polyline().apply {
                            outlinePaint.color = android.graphics.Color.argb(230, 220, 0, 0)
                            outlinePaint.strokeWidth = 7f
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.isAntiAlias = true
                            setPoints(listOf(GeoPoint(wall.lat1, wall.lon1), GeoPoint(wall.lat2, wall.lon2)))
                        }
                        interiorPathCache.add(line)
                        view.overlays.add(line)
                    }
                    // 3) CAMINOS del navGraph (verde = caminable, naranja = autos).
                    uiState.landmarks.forEach { lm ->
                        val ng = lm.navGraph ?: return@forEach
                        ng.ways.forEach { w ->
                            if (w.nodes.size < 2) return@forEach
                            val pts = w.nodes.map { lm.toGlobalGeoPoint(it.localX, it.localY) }
                            val line = Polyline().apply {
                                outlinePaint.color = if (w.isForPeople)
                                    android.graphics.Color.argb(230, 76, 200, 80)   // verde = caminable
                                else
                                    android.graphics.Color.argb(230, 255, 140, 0)   // naranja = autos
                                outlinePaint.strokeWidth = if (w.isForPeople) 5f else 7f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.isAntiAlias = true
                                setPoints(pts)
                            }
                            interiorPathCache.add(line)
                            view.overlays.add(line)
                        }
                    }
                }
                interiorPathCache.forEach { it.isEnabled = true }
            } else {
                debugMarkerCache.values.forEach { it.setAlpha(0f) }
                (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }) as? Polyline)?.isEnabled = false
                interiorPathCache.forEach { it.isEnabled = false }
            }

            // ─── METRO STATIONS OVERLAY ────────────────────────────────────────────────
            @Suppress("UNCHECKED_CAST")
            val metroMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 400 }) as? MutableMap<String, Marker>)
                ?: mutableMapOf<String, Marker>().also {
                    view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 400 }, it)
                }

            if (uiState.zoomLevel >= 14.0) {
                // OPT FPS: hay ~160 estaciones; antes TODAS se dibujaban cada frame. Solo
                // dibujamos las del viewport (margen 1.5× para la rotación al conducir); el
                // resto se deshabilita (osmdroid no llama draw() si isEnabled=false).
                val metroBox = try { view.boundingBox } catch (_: Exception) { null }
                // Margen (~40%) sobre la caja visible para cubrir la rotación al conducir.
                val metroLatM = if (metroBox != null) (metroBox.latNorth - metroBox.latSouth) * 0.4 else 0.0
                val metroLonM = if (metroBox != null) (metroBox.lonEast - metroBox.lonWest) * 0.4 else 0.0
                uiState.metroStations.forEach { station ->
                    val marker = metroMarkerCache[station.name] ?: Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        val exactPixels = (24 * screenDensity).toInt()
                        val cacheKey = "OSM_METRO_ICON"
                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                            try {
                                val bitmap = android.graphics.BitmapFactory.decodeStream(context.assets.open("metro_cdmx/icon.webp"))
                                if (bitmap != null) {
                                    val spriteDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                    ExactSizeDrawable(spriteDrawable, exactPixels, exactPixels)
                                } else ContextCompat.getDrawable(context, android.R.color.transparent)!!
                            } catch (e: Exception) {
                                ContextCompat.getDrawable(context, android.R.color.transparent)!!
                            }
                        }
                        icon = cachedIcon
                        title = station.name
                        snippet = station.routes.joinToString(", ")
                        isFlat = true
                        metroMarkerCache[station.name] = this
                        view.overlays.add(this)
                    }
                    marker.position = station.location
                    val inView = metroBox == null || (
                        station.location.latitude <= metroBox.latNorth + metroLatM &&
                        station.location.latitude >= metroBox.latSouth - metroLatM &&
                        station.location.longitude <= metroBox.lonEast + metroLonM &&
                        station.location.longitude >= metroBox.lonWest - metroLonM)
                    marker.isEnabled = inView
                    marker.setAlpha(if (inView) 1f else 0f)
                }
            } else {
                metroMarkerCache.values.forEach { it.isEnabled = false; it.setAlpha(0f) }
            }

            // ─── OVERLAY CREADOR DE RUTAS (MIGAS DE PAN Y CARRILES) ────────────────────────
            // Dibujamos la ruta si estamos en modo diseñador y hay puntos guardados
            if (uiState.isDesignerMode && uiState.routeDebugWaypoints.isNotEmpty()) {

                // 1. Dibujar la línea (carril) que conecta los puntos
                val debugRouteLine = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 300 }) as? Polyline)
                    ?: Polyline().apply {
                        outlinePaint.color = android.graphics.Color.CYAN // Una línea cyan brillante
                        outlinePaint.strokeWidth = 6f
                        view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 300 }, this)
                        view.overlays.add(this)
                    }
                debugRouteLine.setPoints(uiState.routeDebugWaypoints)
                debugRouteLine.isEnabled = true

                // 2. Dibujar los puntitos (migas de pan) en cada nodo capturado
                @Suppress("UNCHECKED_CAST")
                val breadcrumbCache = (view.getTag(ovh.gabrielhuav.pow.R.id.player_marker_tag.let { it + 300 }) as? MutableList<Marker>)
                    ?: mutableListOf<Marker>().also {
                        view.setTag(ovh.gabrielhuav.pow.R.id.player_marker_tag.let { it + 300 }, it)
                    }

                // Si capturamos un nuevo punto, creamos un nuevo marcador visual
                while (breadcrumbCache.size < uiState.routeDebugWaypoints.size) {
                    val m = Marker(view).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        val dot = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setColor(android.graphics.Color.CYAN)
                            setStroke(2, android.graphics.Color.WHITE)
                            setSize(16, 16)
                        }
                        icon = dot
                        view.overlays.add(this)
                    }
                    m.position = uiState.routeDebugWaypoints[breadcrumbCache.size]
                    breadcrumbCache.add(m)
                }
            } else {
                // Fuera de modo disenador / sin puntos: ocultar la linea de depuracion.
                (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 300 }) as? Polyline)?.isEnabled = false
            }

            // ─── NEBLINA ANCLADA AL JUGADOR ─────────────────────────────────────
            (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag + 600) as? FogOverlay)?.let { fog ->
                fog.player = uiState.currentLocation
                // Solo al conducir el mapa rota; a pie la neblina puede dibujarse con el rect
                // exacto de pantalla (sin el enorme sobredimensionado por rotación).
                fog.rotated = uiState.isDriving
                val lat = uiState.currentLocation?.latitude ?: 19.5
                val mpp = metersPerPixel(view.zoomLevelDouble, lat)
                fog.revealPx = if (mpp.isFinite() && mpp > 0.0)
                    (NPC_FOG_VISION_METERS / mpp).toFloat() else 300f
                // Mantener la neblina SIEMPRE al frente (los marcadores se añaden después).
                view.overlays.remove(fog); view.overlays.add(fog)
            }

            // ─── PRANKEDY: renderizado siempre en capa superior (tras la niebla) ──
            renderPrankedyOnMap(view, uiState, context, nativeDrawableCache, screenDensity, timeMs)

            view.invalidate()
        }
    )
}

private fun renderPrankedyOnMap(
    view: MapView,
    uiState: WorldMapState,
    context: Context,
    nativeDrawableCache: MutableMap<String, android.graphics.drawable.Drawable>,
    screenDensity: Float,
    timeMs: Long
) {
    val prankedyTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 700
    val prankedyProjTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 701
    val prankedyIndicatorTag = ovh.gabrielhuav.pow.R.id.route_overlay_tag + 702

    val prankedyLoc = uiState.prankedyLocation

    // Mostramos a Prankedy si tiene ubicación y el jugador NO va en coche (al conducir el
    // compañero "sube" → su asset se oculta; reaparece al bajarse). Esto cubre las fases
    // NOT_HIRED (vagabundo) y HIRED (compañero); en fase DEAD location es null → oculto.
    val shouldShow = prankedyLoc != null && !uiState.isDriving
    
    val prankedyMarker = (view.getTag(prankedyTag) as? Marker) ?: Marker(view).apply {
        title = "PRANKEDY_MARKER"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        view.setTag(prankedyTag, this)
        view.overlays.add(this) // al final de overlays (arriba de todo)
    }

    val prankedyIndicatorMarker = (view.getTag(prankedyIndicatorTag) as? Marker) ?: Marker(view).apply {
        title = "PRANKEDY_INDICATOR"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) // Flota arriba
        setInfoWindow(null)
        view.setTag(prankedyIndicatorTag, this)
        view.overlays.add(this)
    }
    
    if (shouldShow) {
        // Moverlo al final siempre (encima de fog y otros markers)
        view.overlays.remove(prankedyMarker)
        view.overlays.add(prankedyMarker)
        view.overlays.remove(prankedyIndicatorMarker)
        view.overlays.add(prankedyIndicatorMarker)

        val metersPerPixel = (40075016.686 * kotlin.math.cos(Math.toRadians(prankedyLoc!!.latitude))) /
                (256.0 * 2.0.pow(view.zoomLevelDouble))
        // Tamaño en METROS reales = peatón (~1.3 m), para igualar a los NPCs normales.
        val exactPixels = ((1.3 / metersPerPixel) * screenDensity).toInt().coerceAtLeast(24)
        val cacheKey = "PRANKEDY_${uiState.prankedyAnimState}_${uiState.prankedyFacingRight}_${(timeMs / 180L) % 8}_${exactPixels}"
        
        val icon = nativeDrawableCache.getOrPut(cacheKey) {
            var baseDrawable: android.graphics.drawable.Drawable? = PrankedySpriteManager.getDrawable(context, uiState.prankedyAnimState, timeMs, screenDensity, uiState.prankedyFacingRight)
            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, uiState.prankedyHealth, false, ovh.gabrielhuav.pow.domain.models.ai.PrankedyManager.MAX_HEALTH)
            baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                ?: emojiToDrawable(context, "🎭", exactPixels) // Fallback visible si falla asset
        }
        prankedyMarker.icon = icon
        prankedyMarker.position = prankedyLoc
        prankedyMarker.isEnabled = true
        prankedyMarker.setAlpha(1f)

        // Indicador flotante (🎭) - Aumentado a 80dp
        val indicatorSize = (80 * screenDensity).toInt()
        val indicatorIcon = nativeDrawableCache.getOrPut("PR_IND_$indicatorSize") {
            emojiToDrawable(context, "🎭", indicatorSize)
        }
        prankedyIndicatorMarker.icon = indicatorIcon
        prankedyIndicatorMarker.position = prankedyLoc
        // Indicador 🎭 flotante DESHABILITADO: que Prankedy se vea como un peatón más
        // (mismo tamaño, sin emoji gigante encima).
        prankedyIndicatorMarker.isEnabled = false
        prankedyIndicatorMarker.setAlpha(0f)
        
    } else {
        prankedyMarker.isEnabled = false
        prankedyMarker.setAlpha(0f)
        prankedyIndicatorMarker.isEnabled = false
        prankedyIndicatorMarker.setAlpha(0f)
    }

    // Proyectil
    val projMarker = (view.getTag(prankedyProjTag) as? Marker) ?: Marker(view).apply {
        title = "PRANKEDY_PROJ_MARKER"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        setInfoWindow(null)
        view.setTag(prankedyProjTag, this)
        view.overlays.add(this)
    }

    if (uiState.prankedyProjectileActive && uiState.prankedyProjectileStart != null && uiState.prankedyProjectileTarget != null) {
        view.overlays.remove(projMarker)
        view.overlays.add(projMarker)

        val start = uiState.prankedyProjectileStart
        val end = uiState.prankedyProjectileTarget
        val p = uiState.prankedyProjectileProgress

        val currentLat = start.latitude + (end.latitude - start.latitude) * p
        val currentLon = start.longitude + (end.longitude - start.longitude) * p
        projMarker.position = GeoPoint(currentLat, currentLon)

        val mpp = (40075016.686 * kotlin.math.cos(Math.toRadians(currentLat))) /
                (256.0 * 2.0.pow(view.zoomLevelDouble))
        val exactPixels = ((0.6 / mpp) * screenDensity).toInt().coerceAtLeast(8)
        val cacheKey = "PRANKEDY_PROJ_${(timeMs / 100L) % 4}_${exactPixels}"

        val icon = nativeDrawableCache.getOrPut(cacheKey) {
            PrankedySpriteManager.getProjectileDrawable(context, timeMs, screenDensity)
                ?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                ?: emojiToDrawable(context, "📦", exactPixels) // Fallback visible
        }
        projMarker.icon = icon
        projMarker.isEnabled = true
        projMarker.setAlpha(1f)
    } else {
        projMarker.isEnabled = false
        projMarker.setAlpha(0f)
    }
}


/**
 * Overlay de osmdroid que pinta la neblina (fog of war) centrada en la posición
 * GEOGRÁFICA del jugador. Al proyectarla en cada frame, la zona despejada sigue
 * al jugador durante scroll y zoom en vez de quedarse fija al centro de pantalla.
 */
private class FogOverlay : Overlay() {
    var player: GeoPoint? = null
    var revealPx: Float = 300f
    var rotated: Boolean = false
    private val paint = android.graphics.Paint().apply { isAntiAlias = true }

    override fun draw(c: android.graphics.Canvas, pProjection: org.osmdroid.views.Projection) {
        val pl = player ?: return
        val pt = pProjection.toPixels(pl, null)
        val maxReveal = Math.min(c.width, c.height) * 0.40f
        val reveal = revealPx.coerceIn(40f, if (maxReveal > 40f) maxReveal else 40f)
        val outer = reveal * 1.8f
        val stop = (reveal / outer).coerceIn(0f, 0.99f)
        paint.shader = android.graphics.RadialGradient(
            pt.x.toFloat(), pt.y.toFloat(), outer,
            intArrayOf(0x00000000, 0x00000000, 0x80222A33.toInt()),
            floatArrayOf(0f, stop, 1f),
            android.graphics.Shader.TileMode.CLAMP
        )
        // OPT FPS: a pie (sin rotación) basta el rect EXACTO de pantalla. Antes se dibujaba
        // SIEMPRE un rect de ~2×diagonal por lado (≈10× el área de pantalla) con un
        // RadialGradient — un overdraw enorme en CADA frame. Solo al CONDUCIR el lienzo está
        // rotado y necesita el sobredimensionado a la diagonal para no dejar huecos al girar.
        val w = c.width.toFloat(); val h = c.height.toFloat()
        if (!rotated) {
            c.drawRect(0f, 0f, w, h, paint)
        } else {
            val diag = sqrt(w * w + h * h)
            val cx = w / 2f; val cy = h / 2f
            c.drawRect(cx - diag, cy - diag, cx + diag, cy + diag, paint)
        }
    }
}
