package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: PROVEEDORES DE MAPA + DESCARGA/COMPUERTAS DE TILES.
// Extraído del cuerpo de WorldMapViewModel.kt en el refactor de tamaño (las clases
// no deben superar ~1000 líneas). El ESTADO sigue viviendo en el ViewModel
// (providerPreloadJob, mapPrepStarted, tileCache, _uiState); aquí solo hay lógica.
// Convención (ver 09_CONVENTIONS_GOTCHAS.md): estos nombres NO deben duplicarse
// como miembros en WorldMapViewModel.kt — el miembro ganaría y esta extensión
// quedaría muerta.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun WorldMapViewModel.setMapProvider(provider: MapProvider) {
    val ts = if (provider == MapProvider.OSM) TileSource.LOCAL_OSM else TileSource.NETWORK
    val currentZoom = _uiState.value.zoomLevel
    val newZoom = when {
        provider == MapProvider.OSM && currentZoom < ZOOM_GAMEPLAY_OSM -> ZOOM_GAMEPLAY_OSM
        // Web: cap al zoom de juego a pie (22). Antes se clavaba a 19 al cambiar de
        // proveedor, contradiciendo el nuevo default.
        provider.isWebProvider && currentZoom > ZOOM_ON_FOOT -> ZOOM_ON_FOOT
        else -> currentZoom
    }
    _uiState.update { it.copy(mapProvider = provider, tileSource = ts, zoomLevel = newZoom) }
}

/**
 * Solicita cambiar de proveedor SIN interrumpir el actual: lo precarga en
 * segundo plano. Cuando termina, 'pendingProviderReady' se pone en true y la
 * UI avisa para confirmar el cambio.
 */
fun WorldMapViewModel.requestMapProvider(provider: MapProvider) {
    val st = _uiState.value
    if (provider == st.mapProvider) {
        // El destino ya es el activo: descartar cualquier pendiente.
        if (st.pendingProvider != null) {
            providerPreloadJob?.cancel()
            _uiState.update { it.copy(pendingProvider = null, pendingProviderReady = false) }
        }
        return
    }
    if (provider == st.pendingProvider) return // ya se está precargando

    providerPreloadJob?.cancel()
    _uiState.update { it.copy(pendingProvider = provider, pendingProviderReady = false) }
    providerPreloadJob = viewModelScope.launch {
        preloadProvider(provider)
        if (isActive && _uiState.value.pendingProvider == provider) {
            _uiState.update { it.copy(pendingProviderReady = true) }
        }
    }
}

/** Aplica el proveedor ya precargado (lo invoca el botón "Cambiar"). */
fun WorldMapViewModel.commitMapProvider() {
    val p = _uiState.value.pendingProvider ?: return
    providerPreloadJob?.cancel()
    _uiState.update { it.copy(pendingProvider = null, pendingProviderReady = false) }
    setMapProvider(p)
}

/** Descarta el cambio pendiente y se queda con el proveedor actual. */
fun WorldMapViewModel.cancelPendingProvider() {
    providerPreloadJob?.cancel()
    _uiState.update { it.copy(pendingProvider = null, pendingProviderReady = false) }
}

/** Calienta tiles/conexión del nuevo proveedor para que el cambio sea fluido. */
internal suspend fun WorldMapViewModel.preloadProvider(provider: MapProvider): Boolean = withContext(Dispatchers.IO) {
    if (!provider.isWebProvider) { delay(400); return@withContext true } // nativo/local
    val loc = _uiState.value.currentLocation ?: run { delay(400); return@withContext false }
    val z = ZOOM_GAMEPLAY_WEB.toInt()
    val n = 1 shl z
    val xCenter = ((loc.longitude + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    val latRad = Math.toRadians(loc.latitude)
    val yCenter = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n)
        .toInt().coerceIn(0, n - 1)
    var anyOk = false
    for (dx in -1..1) for (dy in -1..1) {
        if (!isActive) break
        val x = (xCenter + dx).coerceIn(0, n - 1)
        val y = (yCenter + dy).coerceIn(0, n - 1)
        tileUrlFor(provider, z, x, y)?.let { if (fetchTile(it)) anyOk = true }
    }
    anyOk
}

internal fun WorldMapViewModel.tileUrlFor(provider: MapProvider, z: Int, x: Int, y: Int): String? {
    val template = when (provider) {
        MapProvider.CARTO_VOYAGER  -> "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
        MapProvider.CARTO_DB_DARK  -> "https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png"
        MapProvider.CARTO_DB_LIGHT -> "https://a.basemaps.cartocdn.com/light_all/{z}/{x}/{y}.png"
        MapProvider.ESRI           -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
        MapProvider.ESRI_SATELLITE -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        MapProvider.OPEN_TOPO      -> "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
        MapProvider.OSM_WEB        -> "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        MapProvider.GOOGLE_MAPS    -> "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
        else -> null
    } ?: return null
    return template.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}

internal fun WorldMapViewModel.fetchTile(url: String): Boolean = try {
    val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
        connectTimeout = 4000; readTimeout = 4000
        setRequestProperty("User-Agent", "PolitecnicoOpenWorld/1.0")
    }
    val code = conn.responseCode
    runCatching { conn.inputStream.use { it.readBytes() } } // descarga para cachear en CDN/SO
    conn.disconnect()
    code in 200..299
} catch (e: Exception) { false }

/**
 * Descarga el mapa alrededor del spawn ANTES de permitir entrar. Reporta
 * progreso (0f..1f) en mapLoadProgress y al terminar pone isMapReady=true.
 * Idempotente: solo corre una vez por sesión de pantalla.
 */
fun WorldMapViewModel.prepareMapForEntry() {
    if (mapPrepStarted) return
    mapPrepStarted = true
    viewModelScope.launch {
        val provider = _uiState.value.mapProvider
        if (provider == MapProvider.GOOGLE_MAPS_NATIVE) {
            // SDK nativo de Google: las teselas las gestiona el SDK; progreso breve.
            for (i in 1..10) {
                _uiState.update { it.copy(mapLoadProgress = i / 10f) }
                delay(70)
            }
            _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
            return@launch
        }
        if (provider == MapProvider.OSM) {
            // OSM Nativo: descargar de VERDAD las teselas alrededor del jugador a
            // nivel MÁXIMO real (z19) y a un nivel MEDIO (z17) para que el mapa esté
            // listo y nítido al instante (incluido el over-zoom 20–22, que se escala
            // a partir de z19). Antes solo se simulaba progreso y por eso "no cargaba".
            downloadOsmNativeForEntry()
            _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
            return@launch
        }
        downloadMapAround(provider)
        _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
    }
}

internal suspend fun WorldMapViewModel.downloadMapAround(provider: MapProvider): Boolean = withContext(Dispatchers.IO) {
    val loc = _uiState.value.currentLocation
        ?: run { _uiState.update { it.copy(mapLoadProgress = 1f) }; return@withContext false }
    val z = ZOOM_GAMEPLAY_WEB.toInt()
    val n = 1 shl z
    val xC = ((loc.longitude + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(loc.latitude)
    val yC = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    val coords = ArrayList<Pair<Int, Int>>()
    for (dx in -1..1) for (dy in -2..2) coords.add((xC + dx) to (yC + dy)) // 3x5 alrededor
    val total = coords.size
    var done = 0
    var okAny = false
    for ((x, y) in coords) {
        if (!isActive) break
        val xx = x.coerceIn(0, n - 1); val yy = y.coerceIn(0, n - 1)
        tileUrlFor(provider, z, xx, yy)?.let { if (fetchTile(it)) okAny = true }
        done++
        _uiState.update { it.copy(mapLoadProgress = done.toFloat() / total) }
    }
    okAny
}

// ─── COMPUERTA DE MAPA TRAS TELETRANSPORTE ────────────────────────────────
// El teletransporte (ESCOM / "Ir a tu Ubicación") NO debe soltarte hasta que
// el mapa de la zona esté descargado, sea cual sea el proveedor. Re-activa la
// compuerta (isMapReady=false) y descarga el vecindario inmediato:
//  - OSM nativo: guarda REAL en Room (bucket "osm") → render inmediato + offline.
//  - Web: calienta el CDN (luego el WebView + CachingWebViewClient cachean a Room).
//  - Google nativo: sin prefetch por URL, progreso breve.
// Corre en paralelo a la recarga de calles; worldReady se cumple cuando AMBOS
// (calles + tiles) terminan (y los NPCs hacen su warm-up, ver npcsWarmedUp).
internal fun WorldMapViewModel.gateMapDownloadAfterTeleport() {
    viewModelScope.launch {
        _uiState.update { it.copy(isMapReady = false, mapLoadProgress = 0f) }
        downloadGateTiles()
        _uiState.update { it.copy(mapLoadProgress = 1f, isMapReady = true) }
    }
}

internal suspend fun WorldMapViewModel.downloadGateTiles() = withContext(Dispatchers.IO) {
    val loc = _uiState.value.currentLocation ?: return@withContext
    val provider = _uiState.value.mapProvider
    if (provider == MapProvider.GOOGLE_MAPS_NATIVE) {
        // Mapa nativo de Google: las teselas las gestiona el SDK; solo simulamos
        // un breve progreso para mostrar la compuerta de forma consistente.
        for (i in 1..6) { _uiState.update { it.copy(mapLoadProgress = i / 6f) }; delay(60) }
        return@withContext
    }
    if (provider == MapProvider.OSM) {
        // OSM Nativo tras teletransporte: misma estrategia que la entrada (z19 + z17)
        // para que la nueva zona quede nítida y lista para el over-zoom.
        downloadOsmNativeForEntry()
        return@withContext
    }
    val z = if (provider.isWebProvider) ZOOM_GAMEPLAY_WEB.toInt() else 18
    val n = 1 shl z
    val xC = ((loc.longitude + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(loc.latitude)
    val yC = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    // Vecindario inmediato 3x3 (suficiente para soltar al jugador); el resto de la
    // zona (~2km) lo completa prefetchCurrentZoneTiles en segundo plano para offline.
    val coords = ArrayList<Pair<Int, Int>>()
    for (dx in -1..1) for (dy in -1..1) coords.add((xC + dx) to (yC + dy))
    val total = coords.size
    var done = 0
    for ((x, y) in coords) {
        if (!isActive) break
        val xx = x.coerceIn(0, n - 1); val yy = y.coerceIn(0, n - 1)
        if (provider == MapProvider.OSM) {
            val url = "https://tile.openstreetmap.org/$z/$xx/$yy.png"
            val key = sha256Hex(url)
            if (tileCache.getTileByUrl("osm", key) == null) {
                val bytes = downloadTileBytes(url)
                if (bytes != null && bytes.isNotEmpty()) tileCache.putTileByUrl("osm", key, bytes)
            }
        } else {
            tileUrlFor(provider, z, xx, yy)?.let { fetchTile(it) }
        }
        done++
        _uiState.update { it.copy(mapLoadProgress = done.toFloat() / total) }
    }
}

// ─── PREFETCH OSM NATIVO (pantalla de carga) ──────────────────────────────
// Descarga y persiste en Room (bucket "osm", mismo esquema de clave que
// RoomTileModuleProvider) un vecindario alrededor del jugador en DOS niveles:
//  - z19 (máximo real de OSM): nitidez y base para el over-zoom 20–22.
//  - z17 (medio): respaldo para alejar y para que el over-zoom tenga de dónde
//    escalar aunque falte algún z19 puntual.
internal suspend fun WorldMapViewModel.downloadOsmNativeForEntry() = withContext(Dispatchers.IO) {
    val loc = _uiState.value.currentLocation
        ?: run { _uiState.update { it.copy(mapLoadProgress = 1f) }; return@withContext }
    // (zoom, radio en teselas). z19 con radio 2 (5x5) cubre la zona inmediata;
    // z17 con radio 1 (3x3) da contexto al alejar.
    val plan = listOf(19 to 2, 17 to 1)
    val total = plan.sumOf { (_, r) -> (2 * r + 1) * (2 * r + 1) }
    var done = 0
    for ((z, r) in plan) {
        if (!isActive) break
        val n = 1 shl z
        val xC = ((loc.longitude + 180.0) / 360.0 * n).toInt()
        val latRad = Math.toRadians(loc.latitude)
        val yC = ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        for (dx in -r..r) for (dy in -r..r) {
            if (!isActive) break
            val x = (xC + dx).coerceIn(0, n - 1)
            val y = (yC + dy).coerceIn(0, n - 1)
            cacheOsmTileToRoom(z, x, y)
            done++
            _uiState.update { it.copy(mapLoadProgress = (done.toFloat() / total).coerceIn(0f, 1f)) }
        }
    }
}

/** Descarga (si falta) un tile OSM canónico y lo guarda en Room bucket "osm". */
internal fun WorldMapViewModel.cacheOsmTileToRoom(z: Int, x: Int, y: Int) {
    val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
    val key = sha256Hex(url)
    if (tileCache.getTileByUrl("osm", key) != null) return
    val bytes = downloadTileBytes(url)
    if (bytes != null && bytes.isNotEmpty()) tileCache.putTileByUrl("osm", key, bytes)
}

internal fun WorldMapViewModel.downloadTileBytes(url: String): ByteArray? {
    var c: java.net.HttpURLConnection? = null
    return try {
        c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 12000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PolitecnicoOpenWorld/1.0")
            setRequestProperty("Accept", "image/png,image/webp,image/*,*/*")
            setRequestProperty("Referer", "https://www.openstreetmap.org/")
        }
        if (c.responseCode == java.net.HttpURLConnection.HTTP_OK) c.inputStream.readBytes() else null
    } catch (e: Exception) { null } finally { c?.disconnect() }
}

internal fun WorldMapViewModel.sha256Hex(input: String): String =
    java.security.MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
