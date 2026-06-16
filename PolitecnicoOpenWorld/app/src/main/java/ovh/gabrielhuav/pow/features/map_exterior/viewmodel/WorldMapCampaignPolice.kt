package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.MissionCatalog

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · POLICÍA DE LA MISIÓN 1 (ESCOLTA A LA ESCOM)
//
// Lógica SEPARADA del sistema de búsqueda del MUNDO LIBRE (PoliceManager / WorldMapWanted.kt)
// para que los comportamientos de la campaña NO choquen con los del mundo libre. Aquí solo hay
// 2 policías A PIE que SIGUEN al jugador a una distancia considerable y despacio, con 1★ de HUD.
// El estado (campaignEscortPolice, campaignPoliceActivated) vive en el ViewModel.
// ─────────────────────────────────────────────────────────────────────────────

// ¿Estamos en la misión de escolta (Misión 1) y aún sin cumplir? Solo entonces aparece esta policía.
internal fun WorldMapViewModel.isCampaignEscortActive(): Boolean {
    if (!inCampaign) return false
    val obj = _uiState.value.currentObjective ?: return false
    return obj.id == MissionCatalog.ESCOLTAR_PRANKEDY.id && !_uiState.value.objectiveDone
}

// Tick de la policía de la campaña (lo llama el game loop SOLO si isCampaignEscortActive()).
// Spawnea 2 policías detrás del jugador una vez, fija 1★ y los hace seguir a pie, despacio.
internal fun WorldMapViewModel.runCampaignEscortTick(playerLoc: GeoPoint) {
    // Snap a calle (salvo en zona libre del campus: ESCOM/ENCB), igual que el resto de entidades.
    val snap: (GeoPoint) -> GeoPoint = { p ->
        val freeZone = isFreeMovementZone(playerLoc.latitude, playerLoc.longitude)
        if (!freeZone && _uiState.value.isRoadNetworkReady) getNearestPointOnNetwork(p) else p
    }

    if (!campaignPoliceActivated) {
        campaignPoliceActivated = true
        campaignEscortPolice.spawn(playerLoc.latitude, playerLoc.longitude, snap)
    }

    // Siguen al jugador por la RED DE CALLES (A*) para no atascarse, despacio.
    campaignEscortPolice.tick(
        playerLat = playerLoc.latitude,
        playerLon = playerLoc.longitude,
        now = System.currentTimeMillis(),
        snap = snap,
        pathfind = { from, to -> findRoadRoute(from, to) }
    )

    // 1 ESTRELLA mientras dura la escolta (solo indicador; NO invoca al sistema de búsqueda real,
    // que está desactivado durante la misión —el game loop no llama a runPoliceTick aquí—).
    if (_uiState.value.wantedLevel != 1) _uiState.update { it.copy(wantedLevel = 1) }

    updateNpcsState()
}

// Limpia la policía de la campaña (misión cumplida o al salir de la campaña). Idempotente.
internal fun WorldMapViewModel.clearCampaignEscort() {
    val wasActive = campaignEscortPolice.isActive()
    campaignPoliceActivated = false
    if (wasActive) {
        campaignEscortPolice.clear()
        // Quita la estrella de la escolta (no había nivel de búsqueda real que conservar).
        _uiState.update { it.copy(wantedLevel = 0) }
        updateNpcsState()
    }
}
