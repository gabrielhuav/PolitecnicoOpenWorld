package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ───────────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: TELETRANSPORTE (gate de TP del mundo abierto + estaciones
// de Metro/Metrobús). Extraído de WorldMapViewModel.kt en el refactor de tamaño. El ESTADO
// (lastNetworkFetchLocation, lastFetchAttemptMs, respawnImmunityUntilMs, carjackStartTime,
// remoteEntities, policeManager…) sigue en el ViewModel; aquí solo hay lógica. Llama a
// extensiones de otros parciales (gateMapDownloadAfterTeleport, warpPrankedyCompanionTo).
// NO duplicar estos nombres como miembros (gana el miembro y la extensión queda muerta).
// ───────────────────────────────────────────────────────────────────────────────────

import android.content.Context
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.repository.MetroRepository
import ovh.gabrielhuav.pow.data.repository.MetrobusRepository

fun WorldMapViewModel.teleportToMetroStation(stationName: String) {
    val station = _uiState.value.metroStations.find { it.name.equals(stationName, ignoreCase = true) }
    station?.let {
        teleportTo(it.location.latitude, it.location.longitude)
    }
}

fun WorldMapViewModel.loadMetroStations(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        val stations = MetroRepository.loadStations(context)
        _uiState.update { it.copy(metroStations = stations) }
    }
}

fun WorldMapViewModel.teleportToMetrobusStation(stationName: String) {
val station = _uiState.value.metrobusStations.find { it.name.equals(stationName, ignoreCase = true) }
station?.let { teleportTo(it.location.latitude, it.location.longitude) }
}

fun WorldMapViewModel.loadMetrobusStations(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        val stations = MetrobusRepository.loadStations(context)
        _uiState.update { it.copy(metrobusStations = stations) }
    }
}

fun WorldMapViewModel.toggleTeleportMenu(show: Boolean) { _uiState.update { it.copy(showTeleportMenu = show) } }

fun WorldMapViewModel.teleportTo(lat: Double, lon: Double) {
    // GATE DE TELETRANSPORTE: no se acepta otro TP hasta que el mundo actual esté
    // COMPLETAMENTE listo (mapa descargado + red de calles). Evita TPs encadenados
    // que dejaban la carga a medias y los NPCs mal puestos.
    val st0 = _uiState.value
    if (!st0.isLoadingLocation && (!st0.isMapReady || !st0.isRoadNetworkReady)) {
        _uiState.update { it.copy(showTeleportMenu = false, interactionPrompt = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_wait_loading)) }
        viewModelScope.launch {
            delay(2500)
            _uiState.update { if (it.interactionPrompt?.startsWith("⏳") == true) it.copy(interactionPrompt = null) else it }
        }
        return
    }
    val newLocation = org.osmdroid.util.GeoPoint(lat, lon)
    // Limpia los NPCs locales de la zona vieja: se regeneran cuando la nueva zona
    // esté completamente lista (ver gate del game loop). Sin esto quedaban NPCs
    // "fantasma" de la zona anterior mientras cargaba la nueva.
    val staleNpcIds = remoteEntities.entries
        .filter { it.value.displayName.isNullOrEmpty() }
        .map { it.key }
    staleNpcIds.forEach { remoteEntities.remove(it) }
    npcAiManager.setServerNpcs(emptyList())
    npcWarmupCycles = 0   // re-arma el warm-up de NPCs del gate de carga
    // TELETRANSPORTE = borrón y cuenta nueva del combate: si no, los NPCs/policías que
    // te perseguían en la zona vieja quedaban con aggro y daban un golpe "fantasma"
    // justo al llegar. Limpiamos perseguidores, policía y nivel de búsqueda.
    // También se reinician los triggers de animación de daño y se activa la inmunidad
    // temporal para que ningún golpe residual de la zona anterior dispare la animación
    // al llegar al destino.
    relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
    damagePulseTrigger = 0
    impactEffectTrigger = 0
    respawnImmunityUntilMs = System.currentTimeMillis() + 2000L
    carjackStartTime = 0L
    val clearedPolice = policeManager.clearAll()
    for ((id, npc) in remoteEntities) {
        if (npc.aggroUntil > 0L) remoteEntities[id] = npc.copy(aggroUntil = 0L)
    }
    webSocketManager?.let { ws ->
        viewModelScope.launch(Dispatchers.IO) {
            clearedPolice.forEach { pid ->
                try { ws.sendMessage(gson.toJson(mapOf("type" to "POLICE_DESTROY", "npcId" to pid))) } catch (_: Exception) {}
            }
        }
    }
    _uiState.update {
        it.copy(
            currentLocation = newLocation,
            showTeleportMenu = false,
            isRoadNetworkReady = false,
            isMapReady = false,        // ← re-activa la compuerta: no soltar hasta descargar
            npcsWarmedUp = false,      // ← y tampoco hasta que la IA siembre los NPCs
            isUserPanningMap = false,  // ← recentra el mapa y reactiva la neblina
            wantedLevel = 0,
            carjackWarning = null
        )
    }
    // El acompañante (Prankedy, campaña) se TELETRANSPORTA contigo (si no, quedaba atrás).
    warpPrankedyCompanionTo(newLocation)
    lastNetworkFetchLocation = null
    lastFetchAttemptMs = 0L
    // Descarga el mapa de la nueva zona ANTES de soltar al jugador (en paralelo a
    // la recarga de calles). worldReady = calles listas && mapa listo.
    gateMapDownloadAfterTeleport()
}
