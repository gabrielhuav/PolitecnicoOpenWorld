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
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.GameAction
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
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
    // OPT gama baja: referencia de la última lista de NPCs renderizada. El `update` del
    // AndroidView corre en CADA recomposición (~30 Hz por el movimiento del jugador), pero
    // los NPCs solo cambian a ~10 Hz; reconstruir sus marcadores solo cuando la lista
    // cambia evita ~2/3 del trabajo de sprites (lo más caro en gama baja).
    // Holder NO observable (no es Compose State): leerlo/escribirlo dentro del `update`
    // del AndroidView NO debe disparar recomposición. Por eso usamos un array simple.
    val lastNpcRenderHolder = remember { arrayOfNulls<List<ovh.gabrielhuav.pow.domain.models.Npc>>(1) }

    // CICLO DE VIDA del MapView de osmdroid: al salir de esta pantalla (p. ej. ir a
    // Ajustes) hay que liberar el MapView con onDetach(); de lo contrario se fuga y, al
    // volver, puede quedar en un estado roto (pantalla azul/sin render) con OSM nativo.
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
                            setMultiTouchControls(true)
                            // Canal de retorno del zoom por gesto (pinch): sincroniza el nivel
                            // de zoom del mapa con el estado para que el bucle de render no lo
                            // resetee (sin esto, el pinch "rebotaba" al valor anterior).
                            addMapListener(object : org.osmdroid.events.MapListener {
                                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean = false
                                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                    event?.let { viewModel.onMapZoomChanged(it.zoomLevel) }
                                    return false
                                }
                            })
                            // Fondo oscuro del mapa: al rotar (modo conducción) los bordes
                            // que quedan sin tiles se ven oscuros en lugar de blancos/grises,
                            // evitando los "huecos"/artefactos visibles.
                            setBackgroundColor(android.graphics.Color.parseColor("#0D0D11"))
                            controller.setZoom(uiState.zoomLevel)
                            nativeMapRef.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
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
                            val timeMs = System.currentTimeMillis()
                            val screenDensity = context.resources.displayMetrics.density
                            val highResRenderScale = 1.0f * screenDensity

                            // ─── CULLING POR DISTANCIA ──────────────────────────────
                            // Los NPC siguen en memoria/simulación; solo se crean/actualizan
                            // marcadores para los que caen dentro del viewport. Los lejanos se
                            // quitan de las overlays (se recrean al volver a acercarse).
                            // OPT: solo reconstruimos cuando la LISTA de NPCs cambió (10 Hz),
                            // no en cada recomposición por movimiento del jugador (~30 Hz). Los
                            // marcadores ya se reproyectan solos al moverse/rotar el mapa.
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
                                    isFlat = true
                                    markerCache[id] = this
                                    view.overlays.add(this)
                                }

                                if (isZoomedIn) {
                                    if (npc.isDying) {
                                        marker.setAlpha(0.3f)
                                    } else {
                                        marker.setAlpha(1f)
                                    }

                                    if (npc.visualConfig != null) {
                                        val currentlyMoving = npc.speed > 0 || npc.isMoving
                                        val personSzDp = (24.0 + ((currentZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                        val exactPixels = (personSzDp * screenDensity).toInt()

                                        val frameIndex = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0
                                        val cacheKey = "PED_${npc.visualConfig!!.bodyFolder}_${npc.visualConfig!!.hairId}_${npc.visualConfig!!.shirtColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${npc.health}_D${npc.isDying}"

                                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
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
                                            baseDrawable?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                                ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                        marker.icon = cachedIcon
                                        marker.rotation = 0f

                                    } else if (npc.type == NpcType.CAR) {
                                        var angle = npc.rotationAngle % 360f
                                        if (angle < 0) angle += 360f
                                        val frameIndex = (angle / 7.5f).roundToInt() % 48
                                        val dynamicScale = (1.4 * 2.0.pow(currentZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                        val cacheKey = "CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${dynamicScale}_H${npc.health}_D${npc.isDying}"

                                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                            var baseDrawable = VehicleSpriteManager.getTintedCarNpc(
                                                context, angle, npc.carColor, highResRenderScale, npc.carModel
                                            )
                                            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                            baseDrawable?.let { drawable ->
                                                val baseWidthDp = (drawable.intrinsicWidth / screenDensity) / screenDensity
                                                val baseHeightDp = (drawable.intrinsicHeight / screenDensity) / screenDensity
                                                val finalWidthPx = (baseWidthDp * dynamicScale * screenDensity).toInt()
                                                val finalHeightPx = (baseHeightDp * dynamicScale * screenDensity).toInt()
                                                ExactSizeDrawable(drawable, finalWidthPx, finalHeightPx)
                                            } ?: ContextCompat.getDrawable(context, android.R.color.transparent)!!
                                        }
                                        marker.icon = cachedIcon
                                        marker.rotation = 0f
                                    } else {
                                        val cacheKey = "SVG_${npc.type.name}_H${npc.health}_D${npc.isDying}"
                                        val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                            val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                            var baseDrawable = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                            baseDrawable = drawHealthBarOnDrawable(context, baseDrawable, npc.health, npc.isDying)
                                            baseDrawable?.let {
                                                val exactPixels = (24 * screenDensity).toInt()
                                                ExactSizeDrawable(it, exactPixels, exactPixels)
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
                                Log.d("DEBUG_RENDER", "Intentando dibujar coleccionable: ${collectible.name} en ${collectible.latitude}")
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
                                    val isHand = collectible.name == "Objeto Misterioso ESCOM"
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
                            val halfW = (landmark.baseWidthMeters * landmark.scaleFactor) / 2.0
                            val halfH = (landmark.baseHeightMeters * landmark.scaleFactor) / 2.0
                            val d = sqrt(halfW * halfW + halfH * halfH)
                            val theta = Math.toDegrees(atan2(halfW, halfH))

                            val pTL = center.destinationPoint(d, landmark.rotationAngle.toDouble() - theta)
                            val pTR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + theta)
                            val pBR = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 - theta)
                            val pBL = center.destinationPoint(d, landmark.rotationAngle.toDouble() + 180.0 + theta)

                            groundOverlay.setPosition(pTL, pTR, pBR, pBL)
                            groundOverlay.setImage(if (isDoorAsset) buildDoorEffectBitmap(bitmap, context) else bitmap)

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
                        } else {
                            debugMarkerCache.values.forEach { it.setAlpha(0f) }
                            (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 200 }) as? Polyline)?.isEnabled = false
                        }

                        // ─── METRO STATIONS OVERLAY ────────────────────────────────────────────────
                        @Suppress("UNCHECKED_CAST")
                        val metroMarkerCache = (view.getTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 300 }) as? MutableMap<String, Marker>)
                            ?: mutableMapOf<String, Marker>().also {
                                view.setTag(ovh.gabrielhuav.pow.R.id.route_overlay_tag.let { it + 300 }, it)
                            }

                        if (uiState.zoomLevel >= 14.0) {
                            uiState.metroStations.forEach { station ->
                                val marker = metroMarkerCache[station.name] ?: Marker(view).apply {
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                    val screenDensity = context.resources.displayMetrics.density
                                    val exactPixels = (24 * screenDensity).toInt()
                                    val cacheKey = "OSM_METRO_ICON"
                                    val cachedIcon = nativeDrawableCache.getOrPut(cacheKey) {
                                        try {
                                            val bitmap = android.graphics.BitmapFactory.decodeStream(context.assets.open("metroCDMX/icon.webp"))
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
                                marker.setAlpha(1f)
                            }
                        } else {
                            metroMarkerCache.values.forEach { it.setAlpha(0f) }
                        }

                        view.invalidate()
                    }
                )
}
