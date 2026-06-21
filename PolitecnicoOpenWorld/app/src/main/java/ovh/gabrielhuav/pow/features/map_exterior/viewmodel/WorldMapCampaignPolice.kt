package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
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
// Radio (~165 m) alrededor de la puerta canónica para considerar que un landmark de puerta está
// REALMENTE en la ESCOM. El asset DOORS/ESCOM_DOOR.webp se reusa en otros campus (FES Aragón,
// campos deportivos del Deportivo Miguel Alemán); sin este filtro, esas puertas "secuestraban" el
// objetivo de la ESCOM hacia fuera del campus.
private const val ESCOM_DOOR_NEAR_RADIUS = 0.0015
// MISIÓN 2: a esta distancia de la puerta, Prankedy "entra" a la ESCOM (desaparece) — ~20 m.
private const val MISSION2_PRANKEDY_ENTER_DEG = 0.00006   // ~6.6 m: camina casi hasta la puerta antes de meterse
// Diálogo de Prankedy al meterse a la ESCOM (huyendo). Texto de historia, en español (igual que
// HIRED_PHRASES de PrankedyManager, que también están hardcodeadas).
private const val MISSION2_PRANKEDY_BYE = "Ahí nos vemos"

// Multitud de salida de la ESCOM. Salen 50+ civiles desde un PUNTO FIJO; se alejan y se
// despawnean al salir de tu fog of war, y se reemplazan por nuevos (flujo continuo).
private const val CROWD_MAX = 55
private const val CROWD_SPAWN_INTERVAL_MS = 150L      // sale 1 cada ~0.15 s → llega rápido a 50+
private const val CROWD_SPEED = 0.0000016             // caminan despacio, hacia afuera
private const val CROWD_DESPAWN_DEG = 0.0009          // ~100 m: al salir de tu fog se eliminan
private const val CROWD_SPAWN_OFFSET = 0.00006        // ~6 m del punto de salida al aparecer
// Punto FIJO desde donde SALE la multitud civil (no la puerta del objetivo). X=lon, Y=lat.
private const val CROWD_SPAWN_LAT = 19.50512
private const val CROWD_SPAWN_LON = -99.14625

// Punto FIJO desde donde APARECEN los 6 policías de la persecución de la Misión 2 (tras el
// cómic IntroPOW12..14). Antes spawneaban relativos al jugador; ahora salen siempre de aquí.
private const val MISSION2_POLICE_SPAWN_LAT = 19.50484
private const val MISSION2_POLICE_SPAWN_LON = -99.14561

// Apunta el objetivo (y por tanto el waypoint 🎯, la línea guía, la distancia del widget y la
// llegada) a la PUERTA de la ESCOM REAL: el landmark `DOORS/ESCOM_DOOR.webp` más cercano colocado
// con el Diseñador (donde el jugador interactúa para entrar). Si no hay puerta colocada, conserva
// el destino configurado. Solo aplica a los objetivos de la ESCOM (escolta / ingresar).
// ¿El punto está cerca de la puerta canónica de la ESCOM? (descarta puertas de otros campus).
private fun isNearEscomDoor(p: GeoPoint): Boolean {
    val dLat = p.latitude - ESCOM_DOOR_LAT
    val dLon = p.longitude - ESCOM_DOOR_LON
    return dLat * dLat + dLon * dLon <= ESCOM_DOOR_NEAR_RADIUS * ESCOM_DOOR_NEAR_RADIUS
}

internal fun WorldMapViewModel.syncObjectiveToEscomDoor(playerLoc: GeoPoint) {
    val obj = _uiState.value.currentObjective ?: return
    if (obj.id != MissionCatalog.ESCOLTAR_PRANKEDY.id && obj.id != MissionCatalog.INGRESAR_ESCOM.id) return
    // Puertas de la ESCOM donde el jugador interactúa para entrar. Preferimos las colocadas con el
    // Diseñador (landmarks DOORS/ESCOM_DOOR.webp), pero SOLO las que están REALMENTE en la ESCOM
    // (cerca de la puerta canónica): el mismo asset se reusa en FES Aragón y en los campos
    // deportivos, y esas puertas sacaban el objetivo fuera del campus. Si no hay ninguna puerta de
    // ESCOM colocada, caemos a las placeholder (escom_door_*), que SÍ están en la ESCOM.
    val landmarkDoors = _uiState.value.landmarks
        .filter { it.assetPath == ESCOM_DOOR_ASSET }
        .map { it.location }
        .filter { isNearEscomDoor(it) }
    val placeholderDoors = _escomItems.value
        .filter { it.id.startsWith("escom_door_") }.map { GeoPoint(it.latitude, it.longitude) }
    val doors = if (landmarkDoors.isNotEmpty()) landmarkDoors else placeholderDoors
    if (doors.isEmpty()) return
    // ELECCIÓN DETERMINISTA: la puerta (ya solo de ESCOM) más cercana a la PUERTA CANÓNICA. Como
    // las coords canónicas son fijas, no parpadea entre puertas.
    val chosen = doors.minByOrNull { d ->
        val dLat = d.latitude - ESCOM_DOOR_LAT
        val dLon = d.longitude - ESCOM_DOOR_LON
        dLat * dLat + dLon * dLon
    } ?: return
    if (kotlin.math.abs(obj.targetLat - chosen.latitude) > 1e-6 ||
        kotlin.math.abs(obj.targetLon - chosen.longitude) > 1e-6) {
        _uiState.update { it.copy(currentObjective = obj.copy(targetLat = chosen.latitude, targetLon = chosen.longitude)) }
    }
}

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
    // Los policías PERSIGUEN A PRANKEDY (lo van a atacar) y se acercan cada vez más con el tiempo.
    val pkLoc = prankedyManager.location
    val tgtLat = pkLoc?.latitude ?: playerLoc.latitude
    val tgtLon = pkLoc?.longitude ?: playerLoc.longitude
    val pkDamage = campaignEscortPolice.tick(
        playerLat = playerLoc.latitude, playerLon = playerLoc.longitude,
        targetLat = tgtLat, targetLon = tgtLon,
        now = System.currentTimeMillis(), snap = snap,
        pathfind = { from, to -> findRoadRoute(from, to) }
    )
    // La policía SÍ puede dañar a Prankedy (tú no). Si lo MATAN → MISIÓN FALLIDA.
    if (pkDamage > 0f && pkLoc != null) {
        val died = prankedyManager.takeDamage(pkDamage)
        _uiState.update { it.copy(prankedyHealth = prankedyManager.health) }
        if (died) {
            clearCampaignPolice()
            _uiState.update { it.copy(showMissionFailed = true, prankedyLocation = null, prankedyVisible = false) }
        }
    }
    if (_uiState.value.wantedLevel != 1) _uiState.update { it.copy(wantedLevel = 1) }
    updateNpcsState()
}

// ── MISIÓN 2: persecución (6 policías) + multitud saliendo de la ESCOM ──
internal fun WorldMapViewModel.runMission2Tick(playerLoc: GeoPoint) {
    val snap = roadSnap(playerLoc)
    // ENTRADA de la ESCOM marcada con 🎯 (objetivo sincronizado al landmark real de la puerta).
    val door = mission2DoorTarget()
    if (!mission2ChaseActivated) {
        mission2ChaseActivated = true
        // Los 6 policías aparecen desde un PUNTO FIJO (MISSION2_POLICE_SPAWN), no relativos al
        // jugador. `awayFrom = door` los coloca en el lado contrario a la entrada para empujarte
        // hacia ella; la multitud sale de la propia entrada.
        campaignEscortPolice.spawnChase(
            6, MISSION2_POLICE_SPAWN_LAT, MISSION2_POLICE_SPAWN_LON, snap,
            awayFromLat = door.latitude, awayFromLon = door.longitude
        )
        android.util.Log.d("POW_DBG", "Misión 2: spawnChase de 6 policías desde ($MISSION2_POLICE_SPAWN_LAT,$MISSION2_POLICE_SPAWN_LON)")
        mission2CrowdLastSpawn = 0L
    }
    // MISIÓN 2: PERSIGUEN A PRANKEDY mientras huye a la puerta; cuando entra (location == null)
    // pasan a perseguir al JUGADOR.
    val pk = prankedyManager.location
    campaignEscortPolice.tick(
        playerLat = playerLoc.latitude, playerLon = playerLoc.longitude,
        targetLat = pk?.latitude ?: playerLoc.latitude,
        targetLon = pk?.longitude ?: playerLoc.longitude,
        now = System.currentTimeMillis(), snap = snap,
        pathfind = { from, to -> findRoadRoute(from, to) }
    )
    // REMATE: en cuanto Prankedy se les ESCAPÓ a la ESCOM, los 6 policías se DIRIGEN a la PUERTA
    // (donde se metió Prankedy / donde estás tú), se reúnen ahí, "platican"/reaccionan y se reparten:
    // 3 ENTRAN a la ESCOM (como Prankedy) y 3 SE REGRESAN por donde llegaron. Se dispara UNA vez.
    // (Sin esperar a que estén cerca: la fase de REUNIÓN los trae a la puerta desde donde sea, así
    // SIEMPRE los ves llegar a la entrada, aunque hayan quedado lejos.)
    if (mission2PrankedyEntered && campaignEscortPolice.isActive() && !campaignEscortPolice.isResolving()) {
        // Punto de reunión = donde EXACTAMENTE se metió Prankedy (no la puerta del objetivo, que queda
        // unos metros más allá). Si por lo que sea no se guardó, cae a la puerta.
        val gather = mission2PrankedyExitPoint ?: door
        campaignEscortPolice.startResolution(
            gather.latitude, gather.longitude,
            MISSION2_POLICE_SPAWN_LAT, MISSION2_POLICE_SPAWN_LON,
            System.currentTimeMillis()
        )
        android.util.Log.d("POW_DBG", "Misión 2 REMATE: Prankedy entró en (${gather.latitude},${gather.longitude}) → 6 policías van AHÍ, platican y se reparten")
    }
    updateEscomCrowd(playerLoc, door)
    if (_uiState.value.wantedLevel != 1) _uiState.update { it.copy(wantedLevel = 1) }
    updateNpcsState()
}

// MULTITUD: spawnea NPCs en la ENTRADA marcada con 🎯 (la puerta del objetivo) y los aleja; los
// despawnea al salir del fog. `door` es la entrada real (objetivo sincronizado), no una constante.
private fun WorldMapViewModel.updateEscomCrowd(playerLoc: GeoPoint, door: GeoPoint) {
    // La multitud sale de un PUNTO FIJO (no de la puerta del objetivo) y se aleja de él.
    val doorLat = CROWD_SPAWN_LAT
    val doorLon = CROWD_SPAWN_LON
    val now = System.currentTimeMillis()
    // Spawn por goteo desde la entrada (en una dirección aleatoria, ya separados unos metros).
    if (mission2Crowd.size < CROWD_MAX && now - mission2CrowdLastSpawn > CROWD_SPAWN_INTERVAL_MS) {
        mission2CrowdLastSpawn = now
        val ang = Math.random() * 2.0 * Math.PI
        val loc = GeoPoint(
            doorLat + sin(ang) * CROWD_SPAWN_OFFSET,
            doorLon + cos(ang) * CROWD_SPAWN_OFFSET
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
    // Mueve cada NPC y lo despawnea si sale del fog del jugador. La GRAN MAYORÍA (~80%) camina
    // HACIA donde aparecen los policías (MISSION2_POLICE_SPAWN) → multitud y policías van en
    // direcciones OPUESTAS (se cruzan); una minoría se dispersa alejándose de la salida.
    for (npc in mission2Crowd.values.toList()) {
        val towardPolice = (npc.id.hashCode() % 5) != 0   // ~4 de cada 5
        val a = if (towardPolice) {
            atan2(
                MISSION2_POLICE_SPAWN_LAT - npc.location.latitude,
                MISSION2_POLICE_SPAWN_LON - npc.location.longitude
            )
        } else {
            val dDoorLat = npc.location.latitude - doorLat
            val dDoorLon = npc.location.longitude - doorLon
            if (dDoorLat * dDoorLat + dDoorLon * dDoorLon > 1e-12)
                atan2(dDoorLat, dDoorLon) else Math.random() * 2.0 * Math.PI
        }
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
    mission2PrankedyEntered = false  // re-arma la huida de Prankedy a la puerta
    mission2PrankedyExitPoint = null // re-arma el punto de reunión del REMATE
    mission2Crowd.clear()
    setCampaignObjective(MissionCatalog.INGRESAR_ESCOM)
    android.util.Log.d("POW_DBG", "startMission2(): objetivo=INGRESAR_ESCOM, se re-arma persecución+multitud")
}

// MISIÓN 2: Prankedy CORRE hacia la puerta de la ESCOM y se METE (desaparece) diciendo
// "Ahí nos vemos", mientras la policía lo persigue por detrás y la multitud sale de la puerta.
// Reusa la IA de seguimiento (tickFollow) pero con la PUERTA como objetivo en vez del jugador.
// El game loop la llama en vez de runPrankedyTick mientras dura la huida.
internal fun WorldMapViewModel.runMission2PrankedyEscape(playerLoc: GeoPoint, now: Long) {
    val pm = prankedyManager
    val pkLoc = pm.location
    if (!_uiState.value.prankedyEnabled || pkLoc == null) return
    val door = mission2DoorTarget()
    val dLat = pkLoc.latitude - door.latitude
    val dLon = pkLoc.longitude - door.longitude
    if (sqrt(dLat * dLat + dLon * dLon) <= MISSION2_PRANKEDY_ENTER_DEG) {
        // Llegó a la puerta: muestra el diálogo y, tras un momento, ENTRA a la ESCOM (desaparece).
        mission2PrankedyEntered = true
        // Guarda el punto EXACTO donde se metió: aquí se reúne la policía a "platicar" (REMATE).
        mission2PrankedyExitPoint = pkLoc
        _uiState.update { it.copy(prankedyDialogue = MISSION2_PRANKEDY_BYE) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(2400)   // pausa más larga frente a la puerta: se nota que se mete
            prankedyManager.deactivate()
            _uiState.update {
                it.copy(prankedyEnabled = false, prankedyVisible = false,
                        prankedyLocation = null, prankedyDialogue = null)
            }
        }
        return
    }
    // Camina HACIA LA PUERTA (no hacia el jugador): tickFollow con target = puerta. CAMINANDO
    // (playerRunning=false, BASTANTE LENTO para que se note) y SIN snap a calles (beeline directo a
    // la puerta, que está fuera de la vía → evita que "nunca llegue" por quedarse en la calle).
    pm.tick(
        playerLoc = door,
        npcs = emptyList(),
        isDriving = false,
        now = now,
        roadNetwork = roadNetwork,
        snapToRoad = { p -> p },
        playerRunning = false,
        catchup = false   // huida LENTA a la puerta: sin catch-up ni teleport
    )
    _uiState.update {
        it.copy(
            prankedyLocation = pm.location,
            prankedyAnimState = pm.animState,
            prankedyFacingRight = pm.facingRight,
            prankedyVisible = pm.location != null && !it.isDriving,
            prankedyPhase = pm.phase
        )
    }
}

// Limpia TODA la policía de campaña (escolta + persecución + multitud). Idempotente.
// ENTRADA de la ESCOM marcada por el objetivo 🎯 (sincronizada en runtime al landmark real de la
// puerta vía syncObjectiveToEscomDoor). De aquí SALE la multitud y hacia aquí se empuja al jugador.
// Si aún no hay objetivo activo, cae a la puerta canónica.
private fun WorldMapViewModel.mission2DoorTarget(): GeoPoint {
    val obj = _uiState.value.currentObjective
    return if (obj != null) GeoPoint(obj.targetLat, obj.targetLon)
           else GeoPoint(ESCOM_DOOR_LAT, ESCOM_DOOR_LON)
}

internal fun WorldMapViewModel.clearCampaignPolice() {
    // Los NPCs sembrados sobre la línea roja también se retiran al terminar/salir de la campaña.
    clearCampaignRouteNpcs()
    val had = campaignEscortPolice.isActive() || mission2Crowd.isNotEmpty()
    campaignPoliceActivated = false
    mission2ChaseActivated = false
    mission2PrankedyEntered = false
    mission2PrankedyExitPoint = null
    campaignEscortPolice.clear()
    mission2Crowd.clear()
    if (had) {
        _uiState.update { it.copy(wantedLevel = 0) }
        updateNpcsState()
    }
}
