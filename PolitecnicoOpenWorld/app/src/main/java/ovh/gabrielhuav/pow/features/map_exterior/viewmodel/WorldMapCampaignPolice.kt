package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · POLICÍA DE LA CAMPAÑA (Misiones 1 y 2)
//
// Lógica SEPARADA del sistema de búsqueda del MUNDO LIBRE (PoliceManager / WorldMapWanted.kt).
//  · Misión 1 (ESCOLTAR_PRANKEDY): 2 policías a pie te SIGUEN a distancia, despacio (1★).
//  · Misión 2 (INGRESAR_ESCOM): 6 policías te PERSIGUEN desde lejos para obligarte a entrar a
//    la ESCOM; a la vez una MULTITUD de NPCs SALE de la puerta de la ESCOM (hora de salida) y
//    se despawnea al salir de tu fog of war.
// ─────────────────────────────────────────────────────────────────────────────

// Puerta NORTE de la ESCOM (de WorldMapEscom.spawnEscomDoors): de aquí sale la multitud.
private const val ESCOM_DOOR_LAT = 19.50490
private const val ESCOM_DOOR_LON = -99.14674

// Multitud de salida de la ESCOM.
private const val CROWD_MAX = 14
private const val CROWD_SPAWN_INTERVAL_MS = 450L      // sale 1 cada ~0.45 s (flujo continuo)
private const val CROWD_SPEED = 0.0000016             // caminan despacio, hacia afuera
private const val CROWD_DESPAWN_DEG = 0.0009          // ~100 m: al salir de tu fog se eliminan
private const val CROWD_SPAWN_OFFSET = 0.00006        // ~6 m de la puerta al aparecer

internal fun WorldMapViewModel.isCampaignEscortActive(): Boolean {
    if (!inCampaign) return false
    val obj = _uiState.value.currentObjective ?: return false
    return obj.id == MissionCatalog.ESCOLTAR_PRANKEDY.id && !_uiState.value.objectiveDone
}

internal fun WorldMapViewModel.isMission2ChaseActive(): Boolean {
    if (!inCampaign) return false
    val obj = _uiState.value.currentObjective ?: return false
    return obj.id == MissionCatalog.INGRESAR_ESCOM.id && !_uiState.value.objectiveDone
}

// ── MISIÓN 1: escolta (2 policías que te siguen a distancia, despacio) ──
internal fun WorldMapViewModel.runCampaignEscortTick(playerLoc: GeoPoint) {
    val snap = roadSnap(playerLoc)
    if (!campaignPoliceActivated) {
        campaignPoliceActivated = true
        campaignEscortPolice.spawn(playerLoc.latitude, playerLoc.longitude, snap)
    }
    campaignEscortPolice.tick(
        playerLat = playerLoc.latitude, playerLon = playerLoc.longitude,
        now = System.currentTimeMillis(), snap = snap,
        pathfind = { from, to -> findRoadRoute(from, to) }
    )
    if (_uiState.value.wantedLevel != 1) _uiState.update { it.copy(wantedLevel = 1) }
    updateNpcsState()
}

// ── MISIÓN 2: persecución (6 policías) + multitud saliendo de la ESCOM ──
internal fun WorldMapViewModel.runMission2Tick(playerLoc: GeoPoint) {
    val snap = roadSnap(playerLoc)
    if (!mission2ChaseActivated) {
        mission2ChaseActivated = true
        campaignEscortPolice.spawnChase(6, playerLoc.latitude, playerLoc.longitude, snap)
        mission2CrowdLastSpawn = 0L
    }
    campaignEscortPolice.tick(
        playerLat = playerLoc.latitude, playerLon = playerLoc.longitude,
        now = System.currentTimeMillis(), snap = snap,
        pathfind = { from, to -> findRoadRoute(from, to) }
    )
    updateEscomCrowd(playerLoc)
    if (_uiState.value.wantedLevel != 1) _uiState.update { it.copy(wantedLevel = 1) }
    updateNpcsState()
}

// MULTITUD: spawnea NPCs en la puerta de la ESCOM y los aleja; los despawnea al salir del fog.
private fun WorldMapViewModel.updateEscomCrowd(playerLoc: GeoPoint) {
    val now = System.currentTimeMillis()
    // Spawn por goteo desde la puerta (en una dirección aleatoria, ya separados unos metros).
    if (mission2Crowd.size < CROWD_MAX && now - mission2CrowdLastSpawn > CROWD_SPAWN_INTERVAL_MS) {
        mission2CrowdLastSpawn = now
        val ang = Math.random() * 2.0 * Math.PI
        val loc = GeoPoint(
            ESCOM_DOOR_LAT + sin(ang) * CROWD_SPAWN_OFFSET,
            ESCOM_DOOR_LON + cos(ang) * CROWD_SPAWN_OFFSET
        )
        val id = "ESCOM_FLOOD_${now}_${(0..9999).random()}"
        mission2Crowd[id] = Npc(
            id = id,
            type = NpcType.PERSON,
            location = loc,
            speed = CROWD_SPEED,
            isRemote = false,
            isMoving = true,
            visualConfig = randomCrowdVisual()
        )
    }
    // Mueve cada NPC ALEJÁNDOLO de la puerta y lo despawnea si sale del fog del jugador.
    for (npc in mission2Crowd.values.toList()) {
        val dDoorLat = npc.location.latitude - ESCOM_DOOR_LAT
        val dDoorLon = npc.location.longitude - ESCOM_DOOR_LON
        val a = if (dDoorLat * dDoorLat + dDoorLon * dDoorLon > 1e-12)
            atan2(dDoorLat, dDoorLon) else Math.random() * 2.0 * Math.PI
        val moved = GeoPoint(
            npc.location.latitude + sin(a) * CROWD_SPEED,
            npc.location.longitude + cos(a) * CROWD_SPEED
        )
        val dpLat = moved.latitude - playerLoc.latitude
        val dpLon = moved.longitude - playerLoc.longitude
        if (sqrt(dpLat * dpLat + dpLon * dpLon) > CROWD_DESPAWN_DEG) {
            mission2Crowd.remove(npc.id)   // salió de tu fog → se elimina
        } else {
            mission2Crowd[npc.id] = npc.copy(location = moved, facingRight = cos(a) >= 0, isMoving = true)
        }
    }
}

private fun randomCrowdVisual(): CharacterVisualConfig {
    val hairColors = listOf(
        androidx.compose.ui.graphics.Color.Black,
        androidx.compose.ui.graphics.Color.DarkGray,
        androidx.compose.ui.graphics.Color(0xFF8B4513),
        androidx.compose.ui.graphics.Color(0xFFDAA520)
    )
    val shirtColors = listOf(
        androidx.compose.ui.graphics.Color.White, androidx.compose.ui.graphics.Color.Red,
        androidx.compose.ui.graphics.Color.Blue, androidx.compose.ui.graphics.Color.Green,
        androidx.compose.ui.graphics.Color(0xFF9C27B0), androidx.compose.ui.graphics.Color(0xFFFF9800)
    )
    return CharacterVisualConfig(
        bodyFolder = "npc_walk_1",
        bodyPrefix = "npc_walk_1_",
        hairId = (1..5).random(),
        hairColor = hairColors.random(),
        shirtColor = shirtColors.random(),
        pantsColor = androidx.compose.ui.graphics.Color(0xFF37474F)
    )
}

// Snap a calle salvo en zona libre del campus (ESCOM/ENCB), igual que el resto de entidades.
private fun WorldMapViewModel.roadSnap(playerLoc: GeoPoint): (GeoPoint) -> GeoPoint = { p ->
    val freeZone = isFreeMovementZone(playerLoc.latitude, playerLoc.longitude)
    if (!freeZone && _uiState.value.isRoadNetworkReady) getNearestPointOnNetwork(p) else p
}

// MainActivity lo llama al navegar al cómic de la Misión 2 (evita re-disparar).
internal fun WorldMapViewModel.consumePendingMission2Intro() {
    _uiState.update { it.copy(pendingMission2Intro = false) }
}

// Arranca la Misión 2: nuevo objetivo "Ingresa a la ESCOM". La persecución (6 policías) y la
// multitud que sale de la ESCOM arrancan SOLAS en el game loop al estar activo este objetivo.
internal fun WorldMapViewModel.startMission2() {
    mission2ChaseActivated = false   // re-arma el spawn de la persecución
    mission2Crowd.clear()
    setCampaignObjective(MissionCatalog.INGRESAR_ESCOM)
}

// Limpia TODA la policía de campaña (escolta + persecución + multitud). Idempotente.
internal fun WorldMapViewModel.clearCampaignPolice() {
    val had = campaignEscortPolice.isActive() || mission2Crowd.isNotEmpty()
    campaignPoliceActivated = false
    mission2ChaseActivated = false
    campaignEscortPolice.clear()
    mission2Crowd.clear()
    if (had) {
        _uiState.update { it.copy(wantedLevel = 0) }
        updateNpcsState()
    }
}
