package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ───────────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: CAMPAÑA / MODO HISTORIA (punto de entrada del spawn).
// Extraído de WorldMapViewModel.kt para SEPARAR campaña de mundo abierto. La CAMPAÑA
// depende del mundo abierto (reusa el game loop, snap-to-road, NPCs, etc.); aquí vive su
// punto de entrada (setStorySpawn). El resto de la lógica de campaña ya está en
// WorldMapCampaignPolice.kt / WorldMapCampaignRouteNpcs.kt / WorldMapPrankedy.kt /
// WorldMapSaveGame.kt. El ESTADO (inCampaign, campaign*/mission2*, campaignSchoolId,
// campaignSlot…) sigue en el ViewModel. NO duplicar como miembro (gana el miembro).
// ───────────────────────────────────────────────────────────────────────────────────

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint

// MODO HISTORIA: fija la escuela de inicio elegida en el menú de campaña.
// A diferencia de [updateInitialLocation] (gateada por isLoadingLocation, ya
// consumida en MainActivity.onCreate), esto FUERZA el punto de aparición y
// re-arma las compuertas de carga para que el mapa y las calles se descarguen
// alrededor de la escuela elegida. Se llama ANTES de navegar al mapa, cuando el
// mundo aún no está cargado.
fun WorldMapViewModel.setStorySpawn(lat: Double, lon: Double) {
    val loc = GeoPoint(lat, lon)
    // FIX "se queda cargando": prepareMapForEntry() es idempotente (gateada por
    // mapPrepStarted, que es de la Activity y persiste entre navegaciones). Si el
    // jugador ya entró al mundo una vez (p. ej. MUNDO LIBRE), re-armar isMapReady=false
    // aquí NO volvía a descargar los tiles → la compuerta de carga no se soltaba nunca.
    // Solución: hacer que el spawn de campaña se comporte como un TELETRANSPORTE, que sí
    // re-descarga (gateMapDownloadAfterTeleport NO está gateado por mapPrepStarted).
    inCampaign = true            // sesión de campaña → habilita el auto-guardado al salir
    prankedyCompanionActivated = false  // re-arma el encendido del acompañante en la ENCB
    campaignPoliceActivated = false     // re-arma la policía de escolta de la Misión 1
    mission2ChaseActivated = false      // re-arma la persecución de la Misión 2
    campaignEscortPolice.clear()
    mission2Crowd.clear()
    npcWarmupCycles = 0          // re-arma el warm-up de NPCs del gate de carga
    lastNetworkFetchLocation = null  // fuerza el re-fetch de calles alrededor de la escuela
    lastFetchAttemptMs = 0L
    _uiState.update {
        it.copy(
            currentLocation = loc,
            isLoadingLocation = false,
            isMapReady = false,        // ← re-activa la compuerta de carga del mapa
            isRoadNetworkReady = false, // ← y la de la red de calles
            npcsWarmedUp = false,       // ← y el warm-up de NPCs (orden: tiles → calles → NPCs)
            isUserPanningMap = false,   // ← recentra el mapa y reactiva la neblina
            showMissionFailed = false   // ← limpia un posible "MISIÓN FALLIDA" anterior
        )
    }
    // Descarga el mapa de la escuela ANTES de soltar al jugador (en paralelo a la
    // recarga de calles). Esto SÍ pone isMapReady=true al terminar, sin depender de
    // prepareMapForEntry (idempotente). Así "COMENZAR" carga y spawnea en la escuela.
    gateMapDownloadAfterTeleport()
    checkPrankedySpawn(loc)
}
