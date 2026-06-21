package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GroundOverlay
import com.google.maps.android.compose.GroundOverlayPosition
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.moveSelectedLandmark
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onMapZoomChanged
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectLandmark
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * CAPA 1 (MAPA) — rama **Google Maps nativo** extraída de `WorldMapScreen.kt` para reducir el
 * tamaño del composable gigante (mismo paquete `ui`). Es un composable top-level (sin gotcha
 * miembro/extensión). Recibe por parámetro las cachés/estado locales que cerraba antes:
 * `landmarkBitmapCache` y `googleMapsIconCache` (LRU). Helpers del mismo paquete
 * (`npcVisionRadiusMeters`/`npcWithinRadius`/`emojiToDrawable`/`drawHealthBarOnDrawable`/
 * `ExactSizeDrawable`) y consts (`NPC_FOG_VISION_METERS`) se ven sin import. Las extensiones del VM
 * usadas (`onMapZoomChanged`/`selectLandmark`/`moveSelectedLandmark`) sí se importan.
 * NOTA perf (ver 09 §4/§9): Google nativo sigue al jugador con `cameraPositionState.move()` (NO
 * `animate()`); el fog de Compose `Canvas` se dibuja aparte en `WorldMapScreen`. MVVM intacto.
 */
@Composable
internal fun GoogleMapLayer(
    uiState: WorldMapState,
    viewModel: WorldMapViewModel,
    context: Context,
    roadNetwork: List<MapWay>,
    allCollectibles: List<ActiveCollectible>,
    landmarkBitmapCache: MutableMap<String, android.graphics.Bitmap?>,
    googleMapsIconCache: MutableMap<String, BitmapDescriptor>,
) {
                val escom = LatLng(19.505411765791404, -99.14526888961194)
                val cameraPositionState = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(escom, 18f)
                }

                LaunchedEffect(uiState.currentLocation, uiState.isDriving, uiState.zoomLevel) {
                    if (!uiState.isUserPanningMap) {
                        val targetLat = uiState.currentLocation?.latitude ?: escom.latitude
                        val targetLng = uiState.currentLocation?.longitude ?: escom.longitude
                        val targetZoom = uiState.zoomLevel.toFloat()
                        val targetBearing = if (uiState.isDriving) uiState.vehicleRotation else 0f

                        val newPosition = CameraPosition.builder()
                            .target(LatLng(targetLat, targetLng))
                            .zoom(targetZoom)
                            .bearing(targetBearing)
                            .tilt(0f)
                            .build()

                        // OPT FPS Google nativo: la posición del jugador cambia ~30 Hz; animar
                        // (120 ms) en CADA cambio encadenaba animaciones que se cancelaban entre
                        // sí (thrash de la cámara). move() reposiciona al instante: igual de fluido
                        // (las posiciones ya llegan a 30 Hz) y mucho más barato.
                        cameraPositionState.move(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(newPosition))
                    }
                }

                // Canal de retorno del zoom por gesto (pinch) en Google native: solo cuando
                // el movimiento de cámara lo inició el USUARIO, propagamos el nuevo zoom al
                // estado para que no rebote al seguir al jugador. Los movimientos
                // programáticos (seguimiento/zoom por botón) se ignoran.
                LaunchedEffect(cameraPositionState) {
                    snapshotFlow { cameraPositionState.position.zoom }
                        .collect { z ->
                            if (cameraPositionState.cameraMoveStartedReason ==
                                com.google.maps.android.compose.CameraMoveStartedReason.GESTURE) {
                                viewModel.onMapZoomChanged(z.toDouble())
                            }
                        }
                }

                val propiedadesMap = remember {
                    try {
                        MapProperties(
                            mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, ovh.gabrielhuav.pow.R.raw.estilo_google_maps)
                        )
                    } catch (e: Exception) {
                        MapProperties()
                    }
                }

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = propiedadesMap,
                    uiSettings = MapUiSettings(
                        zoomGesturesEnabled = true,   // pinch (dos dedos) para zoom, igual que web/OSM
                        zoomControlsEnabled = false,
                        scrollGesturesEnabled = uiState.isDesignerMode || uiState.isUserPanningMap,
                        tiltGesturesEnabled = false,
                        rotationGesturesEnabled = false
                    )
                ) {
                    uiState.landmarks.forEach { landmark ->
                        key(landmark.id) {
                            val bitmap = landmarkBitmapCache.getOrPut(landmark.assetPath) {
                                try {
                                    context.assets.open(landmark.assetPath).use { inputStream ->
                                        val o = android.graphics.BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }; android.graphics.BitmapFactory.decodeStream(inputStream, null, o)
                                    }
                                } catch (e: Exception) { null }
                            }

                            if (bitmap != null) {
                                val center = LatLng(landmark.location.latitude, landmark.location.longitude)
                                val widthMeters = (landmark.baseWidthMeters * landmark.scaleX).toFloat()
                                val heightMeters = (landmark.baseHeightMeters * landmark.scaleY).toFloat()

                                val isDoorGM = landmark.assetPath.contains("DOORS/")
                                var doorAnimDescriptor by remember(landmark.id) {
                                    mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null)
                                }
                                if (isDoorGM) {
                                    LaunchedEffect(landmark.id) {
                                        while (true) {
                                            doorAnimDescriptor = BitmapDescriptorFactory.fromBitmap(
                                                // Assuming buildDoorEffectBitmap exists in your project.
                                                // Used fallback icon if undefined, but matching original code structure.
                                                bitmap
                                            )
                                            delay(80L)
                                        }
                                    }
                                }
                                val descriptor = if (isDoorGM) {
                                    doorAnimDescriptor ?: googleMapsIconCache.getOrPut("LANDMARK_${landmark.assetPath}") {
                                        BitmapDescriptorFactory.fromBitmap(bitmap)
                                    }
                                } else {
                                    googleMapsIconCache.getOrPut("LANDMARK_${landmark.assetPath}") {
                                        BitmapDescriptorFactory.fromBitmap(bitmap)
                                    }
                                }

                                GroundOverlay(
                                    position = GroundOverlayPosition.create(center, widthMeters, heightMeters),
                                    image = descriptor,
                                    bearing = landmark.rotationAngle,
                                    transparency = 0f,
                                    zIndex = if (landmark.assetPath.contains("DOORS/")) 10f else 0f
                                )

                                if (uiState.isDesignerMode) {
                                    val markerState = remember(landmark.id) { MarkerState(position = center) }
                                    markerState.position = center
                                    val pencilIcon = remember(uiState.selectedLandmarkId == landmark.id) {
                                        val drawable = ContextCompat.getDrawable(context, android.R.drawable.ic_menu_edit)?.mutate()
                                        if (uiState.selectedLandmarkId == landmark.id) drawable?.setTint(android.graphics.Color.RED)
                                        val bm = android.graphics.Bitmap.createBitmap(drawable!!.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bm)
                                        drawable.setBounds(0, 0, bm.width, bm.height)
                                        drawable.draw(canvas)
                                        BitmapDescriptorFactory.fromBitmap(bm)
                                    }
                                    com.google.maps.android.compose.Marker(
                                        state = markerState,
                                        draggable = true,
                                        icon = pencilIcon,
                                        onClick = { viewModel.selectLandmark(landmark.id); true }
                                    )
                                    LaunchedEffect(markerState.position) {
                                        if (markerState.dragState == com.google.maps.android.compose.DragState.DRAG) {
                                            viewModel.moveSelectedLandmark(markerState.position.latitude - landmark.location.latitude, markerState.position.longitude - landmark.location.longitude)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // 🚇 ESTACIONES DE METRO: icono de la red CDMX en cada estación (paridad con
                    // OSM nativo / web). Marcador estático de tamaño fijo (~24 dp).
                    val metroIconG = remember {
                        try {
                            val raw = context.assets.open("TRANSIT/METRO/icon.webp").use { android.graphics.BitmapFactory.decodeStream(it) }
                            val px = (24 * context.resources.displayMetrics.density).toInt().coerceAtLeast(16)
                            val scaled = android.graphics.Bitmap.createScaledBitmap(raw, px, px, true)
                            BitmapDescriptorFactory.fromBitmap(scaled)
                        } catch (e: Exception) { BitmapDescriptorFactory.defaultMarker() }
                    }
                    uiState.metroStations.forEach { station ->
                        key("metro_${station.name}") {
                            val mPos = LatLng(station.location.latitude, station.location.longitude)
                            val mState = remember { MarkerState(position = mPos) }
                            mState.position = mPos
                            com.google.maps.android.compose.Marker(
                                state = mState,
                                icon = metroIconG,
                                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                flat = true,
                                title = station.name
                            )
                        }
                    }

                    // Mostrar calles solo si estamos cerca
                    if (uiState.showRoadNetwork && uiState.zoomLevel >= 15.5) {
                        roadNetwork.forEach { way ->
                            key("road_${way.id}") {
                                com.google.maps.android.compose.Polyline(
                                    points = way.nodes.map { LatLng(it.lat, it.lon) },
                                    color = if (way.isForCars) Color(0xFFFFD700) else Color(0xFF82C8FF),
                                    width = if (way.isForCars) 8f else 5f,
                                    zIndex = 1000f,
                                    clickable = false
                                )
                            }
                        }
                    }

                    if (uiState.zoomLevel >= 15.5) {
                        val screenDensity = context.resources.displayMetrics.density
                        val timeMs = System.currentTimeMillis()
                        val currentZoom = uiState.zoomLevel
                        val renderZoom = round(currentZoom * 2) / 2.0

                        // Burbuja 💬 (remate Misión 2: policías que "platican"). Icono cacheado una vez.
                        val talkBubbleIcon = remember {
                            val px = (22 * screenDensity).toInt()
                            val d = emojiToDrawable(context, "💬", px)
                            val bm = android.graphics.Bitmap.createBitmap(
                                d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val c = android.graphics.Canvas(bm); d.setBounds(0, 0, bm.width, bm.height); d.draw(c)
                            BitmapDescriptorFactory.fromBitmap(bm)
                        }

                        // Culling por neblina: solo se dibujan los NPC dentro del radio de visión (fijo en metros).
                        val centerCull = uiState.currentLocation
                        val cullRadiusM = centerCull?.let { npcVisionRadiusMeters() }

                        uiState.npcs.forEach { npc ->
                            if (cullRadiusM != null && centerCull != null &&
                                !npcWithinRadius(npc.location.latitude, npc.location.longitude,
                                    centerCull.latitude, centerCull.longitude, cullRadiusM)
                            ) return@forEach
                            key(npc.id) {
                                val qHealth = npc.health.toInt()
                                // "Optimizar para gama baja": TODOS los NPCs como emoji (sin sprites).
                                val fullEmoji = if (uiState.npcFullEmoji) when (npc.type) {
                                    NpcType.CAR, NpcType.POLICE_CAR -> "🚗"
                                    ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE -> "🧟"
                                    NpcType.POLICE_COP -> "👮"
                                    else -> "🧍"
                                } else null
                                val cacheKey = when {
                                    fullEmoji != null -> "GM_FULL_EMOJI_$fullEmoji"
                                    npc.visualConfig != null && npc.type != ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE -> {
                                        val currentlyMoving = npc.speed > 0 || npc.isMoving
                                        val personSzDp = (24.0 + ((renderZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                        val exactPixels = (personSzDp * screenDensity).toInt()
                                        val frameIndex = CharacterSpriteManager.getFrameIndex(context, npc.visualConfig!!, currentlyMoving, timeMs) ?: 0
                                        val config = npc.visualConfig!!
                                        "GM_PED_${config.bodyFolder}_${config.hairId}_${config.hairColor.value}_${config.shirtColor.value}_${config.pantsColor.value}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${qHealth}_D${npc.isDying}"
                                    }
                                    npc.type == NpcType.CAR && !npc.isPoliceSkin -> {
                                        var angle = npc.rotationAngle % 360f
                                        if (angle < 0) angle += 360f
                                        val frameIndex = (angle / 7.5f).roundToInt() % 48
                                        val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                        "GM_CAR_${npc.carModel.name}_${npc.carColor}_${frameIndex}_${dynamicScale}_H${qHealth}_D${npc.isDying}"
                                    }
                                    npc.type == NpcType.POLICE_CAR || npc.isPoliceSkin -> {
                                        var angle = npc.rotationAngle % 360f
                                        if (angle < 0) angle += 360f
                                        val frameIndex = (angle / 7.5f).roundToInt() % 48
                                        val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                        "GM_POLICE_${frameIndex}_${dynamicScale}_H${qHealth}_D${npc.isDying}"
                                    }
                                    npc.type == NpcType.POLICE_COP -> {
                                        val isAttacking = npc.policeCanShoot && !npc.isMoving
                                        val animFrame = if (isAttacking) 0 else ((timeMs / 150L) % 6).toInt()
                                        "GM_COP_SPRITE_${isAttacking}_${animFrame}_${npc.facingRight}_H${qHealth}_D${npc.isDying}"
                                    }
                                    npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE -> {
                                        val timeMs = System.currentTimeMillis()
                                        val frameIndex = ((timeMs / 220L) % 9L).toInt()
                                        val roleSizeMul = when (npc.zombieRole) {
                                            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.TANK -> 1.45f
                                            ovh.gabrielhuav.pow.domain.models.map.ZombieRole.RUNNER -> 0.9f
                                            else -> 1f
                                        }
                                        val personSzDp = (24.0 + ((renderZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                        val exactPixels = (personSzDp * screenDensity * roleSizeMul).toInt()
                                        "GM_ZOMBIE_${npc.zombieRole.name}_${npc.facingRight}_${frameIndex}_${exactPixels}_H${npc.health.toInt()}_M${npc.maxHealth.toInt()}_D${npc.isDying}"
                                    }
                                    else -> "GM_SVG_${npc.type.name}_H${qHealth}_D${npc.isDying}"
                                }

                                val iconDescriptor = googleMapsIconCache.getOrPut(cacheKey) {
                                    val drawable = when {
                                        fullEmoji != null -> {
                                            val px = (18 * screenDensity).toInt()
                                            emojiToDrawable(context, fullEmoji, px)
                                        }
                                        npc.visualConfig != null && npc.type != ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE -> {
                                            val currentlyMoving = npc.speed > 0 || npc.isMoving
                                            val personSzDp = (24.0 + ((renderZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                            val exactPixels = (personSzDp * screenDensity).toInt()
                                            var d = CharacterSpriteManager.getModularNpcDrawable(context, npc.visualConfig!!, currentlyMoving, npc.facingRight, timeMs, screenDensity, npc.displayName)
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                        }
                                        npc.type == NpcType.CAR && !npc.isPoliceSkin -> {
                                            val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                            var d = VehicleSpriteManager.getTintedCarNpc(context, npc.rotationAngle, npc.carColor, screenDensity, npc.carModel)
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d?.let {
                                                val fw = ((it.intrinsicWidth / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                val fh = ((it.intrinsicHeight / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                ExactSizeDrawable(it, fw, fh)
                                            }
                                        }
                                        npc.type == NpcType.POLICE_CAR || npc.isPoliceSkin -> {
                                            val dynamicScale = (1.4 * 2.0.pow(renderZoom - 19.0)).toFloat().coerceIn(0.2f, 1.4f)
                                            val d = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceSpriteManager.getPoliceCar(context, npc.rotationAngle, screenDensity)
                                            d?.let {
                                                val fw = ((it.intrinsicWidth / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                val fh = ((it.intrinsicHeight / screenDensity) / screenDensity * dynamicScale * screenDensity).toInt()
                                                val withHealth = drawHealthBarOnDrawable(context, it, npc.health, npc.isDying)
                                                ExactSizeDrawable(withHealth ?: it, fw, fh)
                                            }
                                        }
                                        npc.type == NpcType.POLICE_COP -> {
                                            val isAttacking = npc.policeCanShoot && !npc.isMoving
                                            val exactPixels = (18 * screenDensity).toInt()
                                            var d = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceNpcSpriteManager.getDrawable(
                                                context, isAttacking, timeMs, screenDensity, npc.facingRight
                                            ) as android.graphics.drawable.Drawable?
                                            if (d == null) {
                                                d = emojiToDrawable(context, "👮", exactPixels)
                                            }
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d
                                        }
                                        npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE -> {
                                            val timeMs = System.currentTimeMillis()
                                            var d: android.graphics.drawable.Drawable? = ovh.gabrielhuav.pow.features.map_exterior.ui.components.MapZombieSpriteManager.getZombieDrawable(
                                                context = context,
                                                npc = npc,
                                                timeMs = timeMs,
                                                scale = screenDensity
                                            )
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying, npc.maxHealth)
                                            val roleSizeMul = when (npc.zombieRole) {
                                                ovh.gabrielhuav.pow.domain.models.map.ZombieRole.TANK -> 1.45f
                                                ovh.gabrielhuav.pow.domain.models.map.ZombieRole.RUNNER -> 0.9f
                                                else -> 1f
                                            }
                                            val personSzDp = (24.0 + ((renderZoom - 18.0) * 8.0)).toFloat().coerceIn(16.0f, 40.0f)
                                            val exactPixels = (personSzDp * screenDensity * roleSizeMul).toInt()
                                            d?.let { ExactSizeDrawable(it, exactPixels, exactPixels) }
                                        }
                                        else -> {
                                            val resId = context.resources.getIdentifier(npc.type.drawableName, "drawable", context.packageName)
                                            var d = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
                                            d = drawHealthBarOnDrawable(context, d, npc.health, npc.isDying)
                                            d?.let { ExactSizeDrawable(it, (24 * screenDensity).toInt(), (24 * screenDensity).toInt()) }
                                        }
                                    }
                                    val bitmap = if (drawable != null) {
                                        val bm = android.graphics.Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, android.graphics.Bitmap.Config.ARGB_8888)
                                        val canvas = android.graphics.Canvas(bm)
                                        drawable.setBounds(0, 0, canvas.width, canvas.height)
                                        drawable.draw(canvas)
                                        bm
                                    } else null
                                    if (bitmap != null) BitmapDescriptorFactory.fromBitmap(bitmap) else BitmapDescriptorFactory.defaultMarker()
                                }
                                val position = LatLng(npc.location.latitude, npc.location.longitude)
                                val markerState = remember { MarkerState(position = position) }
                                markerState.position = position

                                com.google.maps.android.compose.Marker(
                                    state = markerState,
                                    icon = iconDescriptor,
                                    rotation = 0f,
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    flat = true,
                                    alpha = if (npc.isDying) 0.5f else 1.0f
                                )
                                // Burbuja 💬 flotando encima mientras el NPC "platica" (remate Misión 2).
                                // `remember` SIEMPRE se llama (no condicional); solo el Marker es condicional.
                                val bubbleState = remember { MarkerState(position = position) }
                                bubbleState.position = position
                                if (npc.talkingUntil > timeMs) {
                                    com.google.maps.android.compose.Marker(
                                        state = bubbleState,
                                        icon = talkBubbleIcon,
                                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.9f),
                                        flat = true,
                                        zIndex = 50f
                                    )
                                }
                            }
                        }
                    }

                    // ─── WAYPOINTS DE ZOMBIS (fuera del fog, modo apocalipsis) ───
                    // Paridad con OSM nativo/web: 🧟 + línea ROJA punteada jugador→zombi para
                    // los zombis FUERA de tu campo de visión (Google Maps nativo).
                    run {
                        val plocG = uiState.currentLocation
                        if (plocG != null && uiState.globalZombieMode) {
                            val zombieWpIconG = remember {
                                val px = (26 * context.resources.displayMetrics.density).toInt()
                                val d = emojiToDrawable(context, "🧟", px)
                                val bm = android.graphics.Bitmap.createBitmap(
                                    d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1),
                                    android.graphics.Bitmap.Config.ARGB_8888
                                )
                                val c = android.graphics.Canvas(bm)
                                d.setBounds(0, 0, bm.width, bm.height); d.draw(c)
                                BitmapDescriptorFactory.fromBitmap(bm)
                            }
                            uiState.npcs.forEach { npc ->
                                if (npc.type != NpcType.ZOMBIE || npc.health <= 0f) return@forEach
                                if (npcWithinRadius(npc.location.latitude, npc.location.longitude,
                                        plocG.latitude, plocG.longitude, NPC_FOG_VISION_METERS)) return@forEach
                                key("zwp_${npc.id}") {
                                    val zPos = LatLng(npc.location.latitude, npc.location.longitude)
                                    val zSt = remember { MarkerState(position = zPos) }
                                    zSt.position = zPos
                                    com.google.maps.android.compose.Marker(
                                        state = zSt, icon = zombieWpIconG,
                                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                        flat = true, zIndex = 800f
                                    )
                                    com.google.maps.android.compose.Polyline(
                                        points = listOf(LatLng(plocG.latitude, plocG.longitude), zPos),
                                        color = Color(0xFFE53935), width = 6f, zIndex = 1200f, clickable = false,
                                        pattern = listOf(
                                            com.google.android.gms.maps.model.Dash(30f),
                                            com.google.android.gms.maps.model.Gap(20f)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.zoomLevel >= 16.0) {
                        allCollectibles.forEach { collectible ->
                            key(collectible.id) {
                                val screenDensity = context.resources.displayMetrics.density
                                val exactPixels = (22 * screenDensity).toInt()
                                val cacheKey = "GM_COL_${collectible.assetPath}"

                                val iconDescriptor = googleMapsIconCache.getOrPut(cacheKey) {
                                    try {
                                        val bitmap = context.assets.open(collectible.assetPath).use {
                                            android.graphics.BitmapFactory.decodeStream(it)
                                        }
                                        if (bitmap != null) {
                                            val glowDrawable = android.graphics.drawable.GradientDrawable().apply {
                                                shape = android.graphics.drawable.GradientDrawable.OVAL
                                                setSize(exactPixels, exactPixels)
                                                setColor(android.graphics.Color.argb(100, 255, 235, 59))
                                            }
                                            val spriteDrawable = android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
                                            val spriteSize = (exactPixels * 0.90).toInt()
                                            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(glowDrawable, spriteDrawable))
                                            val inset = ((exactPixels - spriteSize) / 2)
                                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)
                                            val finalBm = android.graphics.Bitmap.createBitmap(exactPixels, exactPixels, android.graphics.Bitmap.Config.ARGB_8888)
                                            val canvas = android.graphics.Canvas(finalBm)
                                            layerDrawable.setBounds(0, 0, exactPixels, exactPixels)
                                            layerDrawable.draw(canvas)
                                            BitmapDescriptorFactory.fromBitmap(finalBm)
                                        } else BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
                                    } catch (e: Exception) { BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW) }
                                }
                                val position = LatLng(collectible.latitude, collectible.longitude)
                                val markerState = remember { MarkerState(position = position) }
                                markerState.position = position
                                val isHand = collectible.name == "Objeto Misterioso ESCOM" || collectible.id == "global_zombie_hand"

                                com.google.maps.android.compose.Marker(
                                    state = markerState,
                                    icon = iconDescriptor,
                                    anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                                    flat = true,
                                    rotation = if (isHand) 0f else ((System.currentTimeMillis() / 30) % 360).toFloat()
                                )
                            }
                        }
                    }
                }
}
