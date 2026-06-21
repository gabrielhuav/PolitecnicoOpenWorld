package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.MetroStation
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CharacterSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
import kotlin.math.roundToInt

/**
 * CAPA 1 (MAPA) — rama **WEB** (Leaflet en `WebView`) extraída de `WorldMapScreen.kt` para reducir
 * el tamaño del composable gigante (mismo paquete `ui`). Es la rama `else ->` del `when(mapProvider)`
 * (todos los proveedores de tiles web: CARTO/ESRI/OSM_WEB/OPEN_TOPO/Google Web). Composable top-level
 * (sin gotcha miembro/extensión). Recibe por parámetro TODO el estado/cachés locales que cerraba:
 * `cachingClient`, `webViewRef`, `gson`, `coroutineScope`, las cachés de base64/tamaños
 * (`base64Cache`/`widthCache`/`heightCache`/`registeredWebImages`) y los holders de guarda por-frame
 * (`lastWeb*`/`webLmTick`/`webMetroTick`) que evitan reenviar payloads sin cambios al WebView (ver 09
 * §8/§9). Helpers/consts del mismo paquete (`buildHtml`/`MapJsBridge`/`CachingWebViewClient`/
 * `NpcWebPayload`/`LandmarkWebPayload`/`npcVisionRadiusMeters`/`npcWithinRadius`/`emojiToDrawable`/
 * `NPC_FOG_VISION_METERS`) se ven sin import. MVVM intacto. NO desactivar los guards/heartbeats ni
 * desacotar el `cacheKey` del frame (`% 9`) — ver 09 §8/§12.
 */
@Composable
internal fun WebMapLayer(
    uiState: WorldMapState,
    viewModel: WorldMapViewModel,
    context: Context,
    roadNetwork: List<MapWay>,
    allCollectibles: List<ActiveCollectible>,
    cachingClient: CachingWebViewClient,
    webViewRef: MutableState<WebView?>,
    gson: Gson,
    coroutineScope: CoroutineScope,
    base64Cache: MutableMap<String, String>,
    widthCache: MutableMap<String, Float>,
    heightCache: MutableMap<String, Float>,
    registeredWebImages: MutableSet<String>,
    lastWebNpcHolder: Array<List<Npc>?>,
    lastWebLandmarkHolder: Array<List<Landmark>?>,
    webLmTick: IntArray,
    lastWebMetroHolder: Array<List<MetroStation>?>,
    webMetroTick: IntArray,
    lastWebIpOn: BooleanArray,
    lastWebIpLm: Array<List<Landmark>?>,
    lastWebIpColl: Array<ExteriorCollisionsConfig?>,
    lastWebPoliceHolder: BooleanArray,
    lastWebZombieHolder: BooleanArray,
) {
                val collectiblesJson = remember(allCollectibles) { gson.toJson(allCollectibles) }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowFileAccessFromFileURLs = true
                            settings.allowUniversalAccessFromFileURLs = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            webViewClient = cachingClient
                            addJavascriptInterface(MapJsBridge(viewModel), "Android")
                            val lat = uiState.currentLocation?.latitude ?: 0.0
                            val lng = uiState.currentLocation?.longitude ?: 0.0

                            loadDataWithBaseURL("file:///android_asset/", buildHtml(lat, lng, uiState.zoomLevel.toInt()), "text/html", "UTF-8", null)
                            webViewRef.value = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { wv ->
                        webViewRef.value = wv
                        val timeMs = System.currentTimeMillis()
                        if (!uiState.isUserPanningMap) {
                            // Zoom SIN truncar (.toInt() peleaba con syncZoom cuando el estado
                            // queda en medios pasos tras un pinch, p. ej. 21.5 vs 21).
                            uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof updateMapView==='function')updateMapView(${it.latitude}, ${it.longitude}, ${uiState.zoomLevel});", null) }
                        }
                        uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof updatePlayerMarker==='function')updatePlayerMarker(${it.latitude}, ${it.longitude}, ${uiState.isUserPanningMap});", null) }
                        // Zoom automático por estado: sync explícito e incondicional (el JS
                        // decide si aplica; respeta el pinch reciente del usuario).
                        wv.evaluateJavascript("if(typeof syncZoom==='function')syncZoom(${uiState.zoomLevel});", null)
                        // Neblina anclada al jugador (se redibuja también en cada gesto vía JS).
                        uiState.currentLocation?.let { wv.evaluateJavascript("if(typeof setPlayerFog==='function')setPlayerFog(${it.latitude}, ${it.longitude});", null) }
                        wv.evaluateJavascript("if(typeof setDesignerMode==='function')setDesignerMode(${uiState.isDesignerMode});", null)
                        // Lápiz seleccionado (Modo Diseñador web): tinta el ✏️ del landmark activo.
                        val selLmJs = uiState.selectedLandmarkId?.let { "'$it'" } ?: "null"
                        wv.evaluateJavascript("if(typeof setSelectedLandmark==='function')setSelectedLandmark($selLmJs);", null)
                        // OPT FPS web: el contenedor solo se agranda (para rotación) al CONDUCIR; a
                        // pie es del tamaño de la pantalla. El JS ignora llamadas repetidas (guard
                        // _driving), así que llamarlo cada frame es barato y robusto (se auto-corrige
                        // aunque se pierda una transición a pie↔conducir).
                        wv.evaluateJavascript("if(typeof setMapOversize==='function')setMapOversize(${uiState.isDriving});", null)
                        val mapRot = if (uiState.isDriving) -uiState.vehicleRotation else 0f
                        wv.evaluateJavascript("if(typeof setMapRotation==='function')setMapRotation(${mapRot});", null)
                        val tileUrl = when (uiState.mapProvider) {
                            MapProvider.CARTO_VOYAGER  -> "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png"
                            MapProvider.CARTO_DB_DARK  -> "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
                            MapProvider.CARTO_DB_LIGHT -> "https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
                            MapProvider.ESRI           -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
                            MapProvider.ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                            MapProvider.OPEN_TOPO      -> "https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png"
                            MapProvider.OSM_WEB        -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                            else -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
                        }
                        // Zoom máximo REAL de cada proveedor: a partir de ahí Leaflet escala
                        // (over-zoom). CARTO sirve z20 → más detalle de calles que OSM (z19).
                        val tileMaxNative = when (uiState.mapProvider) {
                            MapProvider.CARTO_VOYAGER, MapProvider.CARTO_DB_DARK,
                            MapProvider.CARTO_DB_LIGHT -> 20
                            MapProvider.OSM_WEB        -> 19
                            MapProvider.ESRI, MapProvider.ESRI_SATELLITE -> 19
                            MapProvider.OPEN_TOPO      -> 17
                            else -> 20 // Google Web
                        }
                        wv.evaluateJavascript("if(typeof changeTileUrl==='function')changeTileUrl('$tileUrl', $tileMaxNative);", null)
                        wv.evaluateJavascript("if(typeof setRoadNetworkReady==='function')setRoadNetworkReady(${uiState.isRoadNetworkReady});", null)

                        val density = context.resources.displayMetrics.density
                        val highResRenderScale = 1.0f * density

                        // Culling por distancia: solo enviamos al WebView los NPC dentro del
                        // viewport. Evita generar bitmaps/base64 y marcadores JS para NPC lejanos.
                        // OPT: solo cuando la lista de NPCs cambió (no en cada recomposición).
                        if (uiState.npcs !== lastWebNpcHolder[0]) {
                          lastWebNpcHolder[0] = uiState.npcs
                        val centerCullW = uiState.currentLocation
                        val cullRadiusMW = centerCullW?.let { npcVisionRadiusMeters() }
                        val visibleNpcs = if (cullRadiusMW != null && centerCullW != null) {
                            uiState.npcs.filter {
                                npcWithinRadius(it.location.latitude, it.location.longitude,
                                    centerCullW.latitude, centerCullW.longitude, cullRadiusMW)
                            }
                        } else uiState.npcs

                        val npcPayloads = visibleNpcs.map { npc ->
                            if (uiState.npcFullEmoji) {
                                // "Optimizar para gama baja": TODOS los NPCs como emoji. No se genera
                                // ningún sprite/bitmap de personaje: solo un bitmap por emoji (cacheado).
                                val emoji = when (npc.type) {
                                    NpcType.CAR, NpcType.POLICE_CAR -> "🚗"
                                    NpcType.ZOMBIE -> "🧟"
                                    NpcType.POLICE_COP -> "👮"
                                    else -> "🧍"
                                }
                                val cacheKey = "full_emoji_${emoji}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val px = (96 * density).toInt().coerceAtLeast(48)
                                        val bitmap = (emojiToDrawable(context, emoji, px) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                            val b64 = "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                base64Cache[cacheKey] = b64
                                            }
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                val isCarType = npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR
                                val webHp = if (npc.maxHealth > 0f) (npc.health / npc.maxHealth * 100f) else npc.health
                                NpcWebPayload(
                                    npc.id, npc.location.latitude, npc.location.longitude, 0f,
                                    if (isCarType) "CAR" else "MODULAR", cacheKey, null, 1, npc.displayName,
                                    if (isCarType) 1f else null, if (isCarType) 1f else null,
                                    health = webHp, isDying = npc.isDying
                                )
                            } else if (npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR) {
                                // FIX web: la PATRULLA (POLICE_CAR) caía al `else` y se dibujaba con
                                // su SVG genérico en vez del asset real. Ahora la tratamos como un
                                // coche-imagen: generamos su sprite (PoliceSpriteManager, sin tintar),
                                // lo registramos en imgCache y lo enviamos como tipo "CAR".
                                val isPolice = npc.type == NpcType.POLICE_CAR || npc.isPoliceSkin
                                var angle = npc.rotationAngle % 360f
                                if (angle < 0) angle += 360f
                                val frameIndex = (angle / 7.5f).roundToInt() % 48
                                val cacheKey = if (isPolice) "POLICE_${frameIndex}_${density}"
                                               else "${npc.carModel.name}_${frameIndex}_${npc.carColor}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val drawable = if (isPolice)
                                            ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceSpriteManager.getPoliceCar(context, angle, highResRenderScale)
                                        else
                                            VehicleSpriteManager.getTintedCarNpc(context, angle, npc.carColor, highResRenderScale, npc.carModel)
                                        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        if (bitmap != null) {
                                            val w = (bitmap.width / density) / density
                                            val h = (bitmap.height / density) / density
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 100, out)
                                            val b64 = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)

                                            // IMPORTANTE: Actualizar el estado en el hilo principal dispara la recomposición
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                widthCache[cacheKey] = w
                                                heightCache[cacheKey] = h
                                                base64Cache[cacheKey] = b64
                                            }
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, "CAR", cacheKey, null, null, npc.displayName, widthCache[cacheKey], heightCache[cacheKey], health = npc.health, isDying = npc.isDying)
                            } else if (npc.visualConfig != null && npc.type != ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE) {
                                val currentlyMoving = npc.speed > 0 || npc.isMoving
                                val config = npc.visualConfig!!
                                val frameIndex = CharacterSpriteManager.getFrameIndex(context, config, currentlyMoving, timeMs) ?: 0
                                val cacheKey = "npc_mod_${config.bodyFolder}_${config.hairId}_${npc.facingRight}_${frameIndex}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val bitmap = CharacterSpriteManager.generateAssembledBitmap(context, config, currentlyMoving, timeMs)
                                        if (bitmap != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                                            base64Cache[cacheKey] = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", cacheKey, null, if (npc.facingRight) 1 else -1, npc.displayName, health = npc.health, isDying = npc.isDying)
                            } else if (npc.type == ovh.gabrielhuav.pow.domain.models.map.NpcType.ZOMBIE) {
                                // FIX web: el frame DEBE acotarse a los 9 del walk (% 9), igual que
                                // getZombieDrawable. Antes era (timeMs/220).toInt() (entero creciente),
                                // así que cada frame creaba un cacheKey nuevo cuya imagen base64 (async)
                                // nunca llegaba a registrarse a tiempo → el zombi no se veía en web.
                                val frameIndex = ((timeMs / 220L) % 9L).toInt()
                                // El rol entra en la clave para que cada tinte (palette swap) se cachee aparte.
                                val cacheKey = "ZOMBIE_WEB_${npc.zombieRole.name}_${npc.facingRight}_${frameIndex}_D${npc.isDying}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val drawable = ovh.gabrielhuav.pow.features.map_exterior.ui.components.MapZombieSpriteManager.getZombieDrawable(
                                            context = context,
                                            npc = npc,
                                            timeMs = timeMs,
                                            scale = highResRenderScale
                                        )
                                        val bitmap = drawable?.bitmap
                                        if (bitmap != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                                            val b64 = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { base64Cache[cacheKey] = b64 }
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                // Vida normalizada a 0-100 (el JS dibuja la barra asumiendo max 100):
                                // así la barra del web es proporcional al maxHealth del rol.
                                val webHp = if (npc.maxHealth > 0f) (npc.health / npc.maxHealth * 100f) else npc.health
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", cacheKey, null, 1, null, health = webHp, isDying = npc.isDying)
                            } else if (npc.type == NpcType.POLICE_COP) {
                                val isAttacking = npc.policeCanShoot && !npc.isMoving
                                val animFrame = if (isAttacking) 0 else ((timeMs / 150L) % 6).toInt()
                                val cacheKey = "cop_sprite_${isAttacking}_${animFrame}_${npc.facingRight}_${density}"
                                val base64Image = base64Cache[cacheKey]
                                if (base64Image == null) {
                                    base64Cache[cacheKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val px = (96 * density).toInt().coerceAtLeast(48)
                                        var bitmap = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PoliceNpcSpriteManager.getDrawable(
                                            context, isAttacking, timeMs, density, npc.facingRight
                                        )?.bitmap
                                        if (bitmap == null) {
                                            bitmap = (emojiToDrawable(context, "👮", px) as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                        }
                                        if (bitmap != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                            val b64 = "data:image/png;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                base64Cache[cacheKey] = b64
                                            }
                                        }
                                    }
                                }
                                if (!base64Image.isNullOrEmpty() && !registeredWebImages.contains(cacheKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$cacheKey'] = '$base64Image';", null)
                                    registeredWebImages.add(cacheKey)
                                }
                                val webHp = if (npc.maxHealth > 0f) (npc.health / npc.maxHealth * 100f) else npc.health
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, 0f, "MODULAR", cacheKey, null, 1, null, health = webHp, isDying = npc.isDying)
                            } else {
                                NpcWebPayload(npc.id, npc.location.latitude, npc.location.longitude, npc.rotationAngle, npc.type.name, null, npc.type.drawableName, null, npc.displayName, health = npc.health, isDying = npc.isDying)
                            }
                        }

                        wv.evaluateJavascript("if(typeof updateNpcs==='function')updateNpcs(${gson.toJson(npcPayloads)});", null)
                        } // fin guard web: lista de NPCs sin cambios → no se reenvía al WebView

                        // BURBUJAS 💬 de "platica" (remate Misión 2): se envían CADA frame (fuera del
                        // guard) para seguir la posición de cada policía mientras dura `talkingUntil`.
                        val nowBubble = System.currentTimeMillis()
                        val talkPayload = uiState.npcs
                            .filter { it.talkingUntil > nowBubble }
                            .map { mapOf("id" to it.id, "lat" to it.location.latitude, "lng" to it.location.longitude) }
                        wv.evaluateJavascript("if(typeof updateTalkBubbles==='function')updateTalkBubbles(${gson.toJson(talkPayload)});", null)

                        wv.evaluateJavascript("if(typeof updateCollectibles==='function')updateCollectibles(${JSONObject.quote(collectiblesJson)});", null)

                        // OPT FPS web: serializar y reenviar landmarks SOLO cuando cambian
                        // (+ heartbeat). Antes se hacía gson.toJson + evaluateJavascript en CADA
                        // frame aunque los landmarks no cambian durante el juego.
                        webLmTick[0]++
                        if (uiState.landmarks !== lastWebLandmarkHolder[0] || webLmTick[0] % 45 == 0) {
                        lastWebLandmarkHolder[0] = uiState.landmarks
                        val landmarksPayload = uiState.landmarks.map {
                            LandmarkWebPayload(
                                id = it.id.toString(),
                                lat = it.location.latitude,
                                lng = it.location.longitude,
                                rotation = it.rotationAngle,
                                widthMeters = it.baseWidthMeters,
                                heightMeters = it.baseHeightMeters,
                                scale = it.scaleX,
                                scaleX = it.scaleX,
                                scaleY = it.scaleY,
                                assetPath = it.assetPath
                            )
                        }
                        val landmarksJson = gson.toJson(landmarksPayload)
                        wv.evaluateJavascript("if(typeof updateLandmarks==='function')updateLandmarks(${JSONObject.quote(landmarksJson)});", null)
                        } // fin guard landmarks web (solo se reenvían al cambiar / heartbeat)

                        // 🚇 ESTACIONES DE METRO: icono fijo en cada estación. Estáticas, así que
                        // solo se reenvían al cambiar la lista (+ heartbeat, por si el primer envío
                        // llegó antes de que el HTML definiera updateMetro).
                        webMetroTick[0]++
                        if (uiState.metroStations !== lastWebMetroHolder[0] || webMetroTick[0] % 45 == 0) {
                            lastWebMetroHolder[0] = uiState.metroStations
                            val metroPayload = uiState.metroStations.map {
                                mapOf("name" to it.name, "lat" to it.location.latitude, "lng" to it.location.longitude)
                            }
                            wv.evaluateJavascript("if(typeof updateMetro==='function')updateMetro(${JSONObject.quote(gson.toJson(metroPayload))});", null)
                        }
                        if (uiState.showRoadNetwork) {
                            val roadsPayload = roadNetwork.map { way ->
                                mapOf(
                                    "id" to way.id.toString(),
                                    "isForCars" to way.isForCars,
                                    "nodes" to way.nodes.map { mapOf("lat" to it.lat, "lon" to it.lon) }
                                )
                            }
                            val roadsJson = gson.toJson(roadsPayload)
                            wv.evaluateJavascript("if(typeof updateRoads==='function')updateRoads(${JSONObject.quote(roadsJson)});", null)
                        } else {
                            wv.evaluateJavascript("if(typeof updateRoads==='function')updateRoads('[]');", null)
                        }

                        // 🔧 DEBUG INTERIORES (web): dibuja el navGraph de los landmarks (ESCOM)
                        // para ver por dónde se puede caminar (verde) y por dónde van autos (naranja).
                        // Convertimos localX/localY → global aquí (el Leaflet no tiene esa geometría).
                        val ipOn = uiState.showInteriorDebugOverlay
                        if (ipOn != lastWebIpOn[0] || (ipOn && (uiState.landmarks !== lastWebIpLm[0] || uiState.exteriorCollisions !== lastWebIpColl[0]))) {
                            lastWebIpOn[0] = ipOn
                            lastWebIpLm[0] = uiState.landmarks
                            lastWebIpColl[0] = uiState.exteriorCollisions
                            if (ipOn) {
                                // Caminos del navGraph (verde/naranja).
                                val paths = uiState.landmarks.flatMap { lm ->
                                    val ng = lm.navGraph ?: return@flatMap emptyList<Map<String, Any>>()
                                    ng.ways.filter { it.nodes.size >= 2 }.map { w ->
                                        mapOf(
                                            "id" to "${lm.id}_${w.id}",
                                            "walk" to w.isForPeople,
                                            "nodes" to w.nodes.map {
                                                val g = lm.toGlobalGeoPoint(it.localX, it.localY)
                                                mapOf("lat" to g.latitude, "lng" to g.longitude)
                                            }
                                        )
                                    }
                                }
                                // Zonas NO caminables (polígonos rojos) + bardas (líneas rojas).
                                val cfg = uiState.exteriorCollisions
                                val blocks = cfg?.polygons?.filter { it.nodes.size >= 3 }?.mapIndexed { i, poly ->
                                    mapOf("id" to "blk_$i", "nodes" to poly.nodes.map { mapOf("lat" to it.lat, "lng" to it.lon) })
                                } ?: emptyList()
                                val walls = cfg?.walls?.mapIndexed { i, wl ->
                                    mapOf("id" to "wl_$i", "nodes" to listOf(
                                        mapOf("lat" to wl.lat1, "lng" to wl.lon1),
                                        mapOf("lat" to wl.lat2, "lng" to wl.lon2)
                                    ))
                                } ?: emptyList()
                                val ipObj = mapOf("paths" to paths, "blocks" to blocks, "walls" to walls)
                                wv.evaluateJavascript("if(typeof updateInteriorPaths==='function')updateInteriorPaths(${JSONObject.quote(gson.toJson(ipObj))});", null)
                            } else {
                                wv.evaluateJavascript("if(typeof updateInteriorPaths==='function')updateInteriorPaths('{}');", null)
                            }
                        }
                        val destMarker = uiState.destinationMarker
                        if (destMarker != null) wv.evaluateJavascript("if(typeof updateDestinationMarker==='function')updateDestinationMarker(${destMarker.latitude}, ${destMarker.longitude});", null)
                        else wv.evaluateJavascript("if(typeof clearDestinationMarker==='function')clearDestinationMarker();", null)
                        wv.evaluateJavascript("if(typeof updateDestinationPlacingMode==='function')updateDestinationPlacingMode(${uiState.isTargetingWaypoint});", null)
                        if (uiState.destinationMarker != null && uiState.routeWaypoints.isNotEmpty() && uiState.showDestinationRoute) {
                            val currentLoc = uiState.currentLocation
                            if (currentLoc != null) {
                                val routeJson = uiState.routeWaypoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }.let { gson.toJson(it) }
                                wv.evaluateJavascript("if(typeof updateDestinationRoute==='function')updateDestinationRoute(${currentLoc.latitude}, ${currentLoc.longitude}, $routeJson, true);", null)
                            }
                        } else wv.evaluateJavascript("if(typeof updateDestinationRoute==='function')updateDestinationRoute(0, 0, [], false);", null)

                        // MODO HISTORIA: línea GPS roja de campaña (ENCB → ESCOM). Se dibuja/limpia
                        // según el estado; al llegar a ESCOM el VM la vacía y aquí se borra sola.
                        if (uiState.campaignRouteWaypoints.isNotEmpty()) {
                            val campJson = uiState.campaignRouteWaypoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude) }.let { gson.toJson(it) }
                            wv.evaluateJavascript("if(typeof updateCampaignRoute==='function')updateCampaignRoute($campJson);", null)
                        } else wv.evaluateJavascript("if(typeof updateCampaignRoute==='function')updateCampaignRoute([]);", null)

                        // Waypoints de patrullas FUERA de la neblina (paridad con OSM nativo):
                        // 🚓 + línea punteada jugador→patrulla mientras te buscan. Las patrullas
                        // DENTRO de la neblina ya se dibujan como sprite (no llevan waypoint).
                        val plocW = uiState.currentLocation
                        // Patrullas (mundo libre) + 2 policías de la ESCOLTA de campaña (a pie).
                        val patrolsW = if (plocW != null && uiState.wantedLevel > 0) {
                            uiState.npcs.filter {
                                (it.type == NpcType.POLICE_CAR ||
                                    (it.type == NpcType.POLICE_COP && it.id.startsWith("CAMPAIGN_COP"))) &&
                                    !npcWithinRadius(it.location.latitude, it.location.longitude,
                                        plocW.latitude, plocW.longitude, NPC_FOG_VISION_METERS)
                            }
                        } else emptyList()
                        if (patrolsW.isNotEmpty() || lastWebPoliceHolder[0]) {
                            lastWebPoliceHolder[0] = patrolsW.isNotEmpty()
                            val policePayload = patrolsW.map {
                                mapOf("id" to it.id, "lat" to it.location.latitude, "lng" to it.location.longitude,
                                    "emoji" to if (it.type == NpcType.POLICE_COP) "👮" else "🚓")
                            }
                            wv.evaluateJavascript("if(typeof updatePolice==='function')updatePolice(${plocW?.latitude ?: 0.0}, ${plocW?.longitude ?: 0.0}, ${gson.toJson(policePayload)});", null)
                        }

                        // Waypoint del OBJETIVO (🎯) + línea jugador→objetivo (te indica a dónde ir).
                        val campObjW = uiState.currentObjective
                        val campPlocW = uiState.currentLocation
                        if (campObjW != null && !uiState.objectiveDone && campPlocW != null) {
                            wv.evaluateJavascript("if(typeof updateObjectiveWp==='function')updateObjectiveWp(${campPlocW.latitude}, ${campPlocW.longitude}, ${campObjW.targetLat}, ${campObjW.targetLon});", null)
                        } else {
                            wv.evaluateJavascript("if(typeof updateObjectiveWp==='function')updateObjectiveWp(null,null,null,null);", null)
                        }

                        // Waypoints de ZOMBIS FUERA del fog (paridad con OSM nativo): 🧟 + línea
                        // ROJA punteada jugador→zombi en modo apocalipsis. Los zombis DENTRO del
                        // fog ya se dibujan con su sprite (no llevan waypoint).
                        val zombiesW = if (plocW != null && uiState.globalZombieMode) {
                            uiState.npcs.filter {
                                it.type == NpcType.ZOMBIE && it.health > 0f &&
                                    !npcWithinRadius(it.location.latitude, it.location.longitude,
                                        plocW.latitude, plocW.longitude, NPC_FOG_VISION_METERS)
                            }
                        } else emptyList()
                        if (zombiesW.isNotEmpty() || lastWebZombieHolder[0]) {
                            lastWebZombieHolder[0] = zombiesW.isNotEmpty()
                            val zombiePayload = zombiesW.map {
                                mapOf("id" to it.id, "lat" to it.location.latitude, "lng" to it.location.longitude)
                            }
                            wv.evaluateJavascript("if(typeof updateZombies==='function')updateZombies(${plocW?.latitude ?: 0.0}, ${plocW?.longitude ?: 0.0}, ${gson.toJson(zombiePayload)});", null)
                        }

                        // ─── PRANKEDY (compañero) en WEB ──────────────────────────────────
                        // Su sprite NO es un NPC normal (assets propios), así que se dibuja con
                        // su propio marcador Leaflet. Se envía CADA frame (FUERA del guard de la
                        // lista de NPCs) porque se mueve suave siguiendo al jugador.
                        run {
                            val pkLoc = uiState.prankedyLocation
                            if (pkLoc != null && !uiState.isDriving) {
                                val pkTime = timeMs
                                val pkAnim = uiState.prankedyAnimState
                                // Índice de frame respetando el intervalo por animación (IDLE va más lento).
                                val pkFrame = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedySpriteManager
                                    .currentFrameIndex0(pkAnim, pkTime)
                                // El bitmap se genera SIEMPRE mirando a la derecha (facingRight = true);
                                // la orientación la aplica el CSS (transform: scaleX(flip)) en updatePrankedy.
                                // Antes se volteaba el bitmap Y además el CSS → doble volteo: al ir a la
                                // izquierda Prankedy acababa mirando a la derecha. Por eso el bitmap NO se
                                // voltea aquí y la clave de caché ya no depende de facingRight.
                                val pkKey = "PRANKEDY_WEB_${pkAnim.name}_$pkFrame"
                                val pkB64 = base64Cache[pkKey]
                                if (pkB64 == null) {
                                    base64Cache[pkKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val d = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedySpriteManager
                                            .getDrawable(context, pkAnim, timeMs, highResRenderScale, facingRight = true)
                                        val bmp = d?.bitmap
                                        if (bmp != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                                            val b64 = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { base64Cache[pkKey] = b64 }
                                        }
                                    }
                                }
                                if (!pkB64.isNullOrEmpty() && !registeredWebImages.contains(pkKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$pkKey'] = '$pkB64';", null)
                                    registeredWebImages.add(pkKey)
                                }
                                val pkFlip = if (uiState.prankedyFacingRight) 1 else -1
                                val pkDlg = uiState.prankedyDialogue?.let { JSONObject.quote(it) } ?: "null"
                                val pkHealth = uiState.prankedyHealth
                                val pkMaxHealth = ovh.gabrielhuav.pow.domain.models.ai.PrankedyManager.MAX_HEALTH
                                wv.evaluateJavascript("if(typeof updatePrankedy==='function')updatePrankedy({lat:${pkLoc.latitude},lng:${pkLoc.longitude},imageKey:'$pkKey',flip:$pkFlip,dialogue:$pkDlg,health:$pkHealth,maxHealth:$pkMaxHealth});", null)
                            } else {
                                wv.evaluateJavascript("if(typeof clearPrankedy==='function')clearPrankedy();", null)
                            }
                        }

                        // ─── PRANKEDY: proyectil (tanque de gas, p_objeto) en WEB ──────────
                        run {
                            val pjStart = uiState.prankedyProjectileStart
                            val pjEnd = uiState.prankedyProjectileTarget
                            if (uiState.prankedyProjectileActive && pjStart != null && pjEnd != null && !uiState.isDriving) {
                                val pjP = uiState.prankedyProjectileProgress
                                val pjLat = pjStart.latitude + (pjEnd.latitude - pjStart.latitude) * pjP
                                val pjLon = pjStart.longitude + (pjEnd.longitude - pjStart.longitude) * pjP
                                val pjTime = timeMs
                                val pjFrame = ((pjTime / 150L) % 3L).toInt()
                                val pjKey = "PRANKEDY_PROJ_WEB_$pjFrame"
                                val pjB64 = base64Cache[pjKey]
                                if (pjB64 == null) {
                                    base64Cache[pjKey] = ""
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val d = ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedySpriteManager
                                            .getProjectileDrawable(context, timeMs, highResRenderScale)
                                        val bmp = d?.bitmap
                                        if (bmp != null) {
                                            val out = java.io.ByteArrayOutputStream()
                                            bmp.compress(android.graphics.Bitmap.CompressFormat.WEBP, 90, out)
                                            val b64 = "data:image/webp;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { base64Cache[pjKey] = b64 }
                                        }
                                    }
                                }
                                if (!pjB64.isNullOrEmpty() && !registeredWebImages.contains(pjKey)) {
                                    wv.evaluateJavascript("if(!window.imgCache) window.imgCache={}; window.imgCache['$pjKey'] = '$pjB64';", null)
                                    registeredWebImages.add(pjKey)
                                }
                                wv.evaluateJavascript("if(typeof updatePrankedyProjectile==='function')updatePrankedyProjectile({lat:$pjLat,lng:$pjLon,imageKey:'$pjKey'});", null)
                            } else {
                                wv.evaluateJavascript("if(typeof clearPrankedyProjectile==='function')clearPrankedyProjectile();", null)
                            }
                        }
                    }
                )
}
