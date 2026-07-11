package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.repository.CampaignRepository
import ovh.gabrielhuav.pow.data.repository.GameSaveData
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.data.repository.SavedNpc
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerSkin

// ─── GUARDADO / CARGA DE LA PARTIDA (MODO HISTORIA, CON SLOTS) ────────────────
// Extensiones del WorldMapViewModel (sin gemelo miembro). Construyen/aplican el snapshot
// completo de la sesión (posición, vida, nivel de búsqueda, vehículo, skin, NPCs cercanos
// y el objetivo de campaña) usando SaveGameRepository (un JSON por slot). El estado
// inmutable se actualiza con _uiState.update { it.copy(...) }. Ver doc 02/07.

// Radio (grados ≈ 130 m) alrededor del jugador para considerar un NPC "cercano".
private const val SAVE_NPC_RADIUS_DEG = 0.0012

// Construye el snapshot de la sesión actual. `saveType` = "MANUAL" / "AUTO".
fun WorldMapViewModel.buildSaveData(schoolId: String, saveType: String = "MANUAL"): GameSaveData {
    val s = _uiState.value
    val loc = s.currentLocation ?: GeoPoint(19.504603, -99.145985) // fallback: ESCOM
    val nearby = s.npcs
        .filter {
            kotlin.math.abs(it.location.latitude - loc.latitude) < SAVE_NPC_RADIUS_DEG &&
                kotlin.math.abs(it.location.longitude - loc.longitude) < SAVE_NPC_RADIUS_DEG
        }
        // NO congelar NPCs DE MISIÓN (M2_* de la Misión 2, M3_* del cordón/paparazzi de la
        // Misión 3, CAMPAIGN_COP_* de la escolta/chase, ESCOM_FLOOD_* de la multitud, SM_/SMZ_
        // de misiones SECUNDARIAS, DYN_ de eventos dinámicos): al cargar se re-inyectarían como
        // civiles "adoptados" por la IA Y ADEMÁS el tick de misión re-spawnea los suyos →
        // duplicados/zombies huérfanos.
        .filterNot {
            it.id.startsWith("M2_") || it.id.startsWith("M3_") ||
                it.id.startsWith("CAMPAIGN_COP_") || it.id.startsWith("ESCOM_FLOOD_") ||
                it.id.startsWith("SM_") || it.id.startsWith("SMZ_") || it.id.startsWith("DYN_") ||
                it.id.startsWith("CAMPUS_")   // vida de campus: efímera (WorldMapCampusLife.kt)
        }
        .take(40)
        .map {
            SavedNpc(
                id = it.id,
                type = it.type.name,
                lat = it.location.latitude,
                lon = it.location.longitude,
                health = it.health,
                rotation = it.rotationAngle
            )
        }
    // REJUGAR MISIONES — REGLA DURA: el guardado NUNCA regresa el progreso. Si una misión ya
    // está ✔ COMPLETADA, su fase se persiste CLAMPADA a DONE aunque en memoria vaya a la mitad
    // (replay en curso, o el reset transitorio de setStorySpawn en un reintento). Y el objetivo
    // de un replay NO se guarda (es transitorio): la partida queda como mundo libre.
    // completedMissions lo POSEE CampaignManager (fachada combine): leerlo del manager, NO de
    // _uiState.value (que ya no se escribe → siempre daría lista vacía).
    val m2Done = campaignManager.isCompleted(MissionCatalog.MISSION_2_ID)
    val m3Done = campaignManager.isCompleted(MissionCatalog.MISSION_3_ID)
    val savedM2Phase = if (m2Done) maxOf(mission2Phase, ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.PHASE_DONE) else mission2Phase
    val savedM3Phase = if (m3Done) maxOf(mission3Phase, ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3.PHASE_DONE) else mission3Phase
    val replayObjective = replayingMissionId != null &&
        MissionCatalog.missionIdForObjective(s.currentObjective?.id) == replayingMissionId
    return GameSaveData(
        schoolId = schoolId,
        lat = loc.latitude,
        lon = loc.longitude,
        health = playerHealth,
        // wantedLevel lo POSEE WantedManager (fachada combine): el read síncrono debe venir del
        // manager, NO de _uiState.value (que ya no se escribe → siempre daría 0).
        wantedLevel = wantedManager.state.value.wantedLevel,
        isDriving = s.isDriving,
        isDrivingPoliceCar = s.isDrivingPoliceCar,
        vehicleModel = s.currentVehicleModel?.name,
        vehicleColor = s.currentVehicleColor,
        skin = s.selectedSkin.name,
        nearbyNpcs = nearby,
        objectiveId = if (replayObjective) null else s.currentObjective?.id,
        objectiveDone = if (replayObjective) false else s.objectiveDone,
        interiorRoomId = currentInteriorRoomId,   // null si está en el mapa global
        inventoryKeys = currentInteriorInventory,
        lab1KeyFound = currentInteriorLab1KeyFound,
        // MISIÓN 2 · "El rumor": fase de la máquina de estados (0 = no iniciada; clamp de replay).
        mission2Phase = savedM2Phase,
        // MISIÓN 3 · "Regreso a la ENCB" + recompensa (arma de fuego) + registro de misiones.
        mission3Phase = savedM3Phase,
        hasFirearm = hasFirearm,
        // ECONOMÍA: el dinero es campo plano de _uiState (no lo posee ningún manager).
        playerMoney = s.playerMoney,
        completedMissions = campaignManager.state.value.completedMissions,
        saveType = saveType,
        savedAt = System.currentTimeMillis()
    )
}

// Guarda la partida COMPLETA en un SLOT (JSON) + la partida ligera (escuela, SharedPreferences)
// que habilita "CARGAR PARTIDA". `auto` = true → AUTO-GUARDADO: ignora `slot` y escribe en un
// slot de auto-guardado reservado (rotando entre los 2). `auto` = false → guardado MANUAL en el
// `slot` elegido (debe ser un slot manual). Fija el slot escrito como activo.
fun WorldMapViewModel.saveGame(context: Context, slot: Int, auto: Boolean = false) {
    val repo = SaveGameRepository(context)
    val targetSlot = if (auto) repo.nextAutoSlot() else slot
    campaignSlot = targetSlot
    val type = if (auto) "AUTO" else "MANUAL"
    repo.save(targetSlot, buildSaveData(campaignSchoolId, type))
    CampaignRepository(context).saveCampaign(campaignSchoolId)
}

// Restaura el estado guardado SOBRE el mundo ya en carga (setStorySpawn fija posición y
// re-arma las compuertas). Restaura vida/estado/vehículo/skin/objetivo y re-inyecta los
// NPCs cercanos como entidades remotas para que reaparezcan al instante (la IA los adopta).
fun WorldMapViewModel.restoreSaveData(data: GameSaveData) {
    playerHealth = data.health.coerceIn(1f, 100f)
    val skin = PlayerSkin.entries.firstOrNull { it.name == data.skin } ?: PlayerSkin.LAZARO
    val model = data.vehicleModel?.let { name -> CarModel.entries.firstOrNull { it.name == name } }
    val objective = MissionCatalog.byId(data.objectiveId)
    // Recordamos el interior guardado (null = mapa global). MainActivity decide la ruta de
    // reentrada (un interior o el mapa global) a partir de este valor tras loadGame.
    currentInteriorRoomId = data.interiorRoomId
    // Restaura inventario y progreso del puzzle de ENCB_lab1 (MainActivity los pasa al reabrir
    // el interior para sembrar el estado del ZombieInteriorViewModel).
    currentInteriorInventory = data.inventoryKeys
    currentInteriorLab1KeyFound = data.lab1KeyFound
    // MISIONES 2/3: restaura fases; los ticks re-arman solos los actores de la fase (los NPCs de
    // misión no se guardan). setStorySpawn ya limpió la pizarra. + Arma de fuego y registro.
    mission2Phase = data.mission2Phase
    mission3Phase = data.mission3Phase
    hasFirearm = data.hasFirearm
    // ⚠️ Gotcha Gson: en guardados VIEJOS una lista AUSENTE llega NULL en runtime aunque el tipo
    // Kotlin sea no-nulo (Gson no aplica defaults de Kotlin). Coalesce defensivo.
    @Suppress("USELESS_ELVIS")
    val restoredCompleted: List<String> = data.completedMissions ?: emptyList()
    // wantedLevel (WantedManager) y completedMissions (CampaignManager) los POSEEN sus managers
    // (fachada combine): se restauran vía el manager, NO en el copy de _uiState.
    wantedManager.setWantedLevel(data.wantedLevel)
    campaignManager.setCompletedMissions(restoredCompleted)
    _uiState.update {
        it.copy(
            isDriving = data.isDriving,
            isDrivingPoliceCar = data.isDrivingPoliceCar,
            currentVehicleModel = model,
            currentVehicleColor = data.vehicleColor,
            selectedSkin = skin,
            currentObjective = objective,
            objectiveDone = data.objectiveDone,
            // ECONOMÍA: dinero guardado (0 en guardados antiguos — Int primitivo).
            playerMoney = data.playerMoney
        )
    }
    data.nearbyNpcs.forEach { sn ->
        val type = NpcType.entries.firstOrNull { it.name == sn.type } ?: NpcType.PERSON
        remoteEntities[sn.id] = Npc(
            id = sn.id,
            type = type,
            location = GeoPoint(sn.lat, sn.lon),
            speed = 0.0,
            health = sn.health,
            rotationAngle = sn.rotation
        )
    }
}

// "CARGAR PARTIDA": lee el slot; si existe, fija el spawn en la posición guardada
// (re-armando las compuertas como un teletransporte) y restaura el estado. Devuelve true
// si cargó. El menú hace fallback al spawn de la escuela si el slot está vacío.
fun WorldMapViewModel.loadGame(context: Context, slot: Int): Boolean {
    val data = SaveGameRepository(context).load(slot) ?: return false
    campaignSlot = slot
    campaignSchoolId = data.schoolId
    setStorySpawn(data.lat, data.lon)   // re-descarga el mundo en la posición guardada
    restoreSaveData(data)
    return true
}

// "REINTENTAR MISIÓN": reinicia la misión actual SIN volver al menú (en vez de reabrir y cargar
// la partida a mano). Equivale a recargar el slot ACTIVO: `setStorySpawn` (dentro de loadGame)
// re-arma el acompañante (Prankedy) y la policía de campaña, y restaura posición/objetivo del
// inicio de la misión. Limpia la pantalla de "MISIÓN FALLIDA". Si no hay slot guardado, al menos
// re-arma la escolta de la Misión 1.
fun WorldMapViewModel.retryCampaignMission(context: Context) {
    clearCampaignPolice()
    // REPLAY: reintentar la misión que se está REJUGANDO no apaga el modo replay (setStorySpawn
    // lo limpia como parte de su pizarra limpia; aquí se restaura al final). EXCEPCIÓN: si el
    // reintento cae al fallback de loadGame, el guardado es canónico y el replay se cancela.
    var keepReplay = replayingMissionId
    // Objetivo que estabas haciendo al fallar (triggerWastedSequence NO cambia el objetivo).
    val failedObjId = _uiState.value.currentObjective?.id
    // Aísla el reintento: cancela el runtime de las OTRAS misiones (deja solo la que se reintenta).
    // Evita que, al fallar habiendo cambiado de misión, quede viva la anterior y el retry se
    // "confunda" (p. ej. reiniciaba en la Misión 1).
    cancelOtherCampaignMissionsRuntime(keep = MissionCatalog.missionIdForObjective(failedObjId))
    if (failedObjId == MissionCatalog.ESCOLTAR_PRANKEDY.id) {
        // ESCOLTA (Misión 1): "vuelve a empezar desde que entras al mapa global" → reaparece en el
        // CHECKPOINT de entrada (MISSION1_SPAWN), NO en la posición guardada (que era el START en
        // ESCOM/IPN y por eso te "teleportaba a ESCOM"). setStorySpawn re-arma el acompañante y la
        // policía de escolta; checkPrankedySpawn + respawnPrankedyCompanionHere ponen a Prankedy contigo.
        setStorySpawn(MissionCatalog.MISSION1_SPAWN_LAT, MissionCatalog.MISSION1_SPAWN_LON)
        setCampaignObjective(MissionCatalog.ESCOLTAR_PRANKEDY)
        playerHealth = maxPlayerHealth
    } else if (failedObjId != null && failedObjId.startsWith(
            ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.OBJECTIVE_ID_PREFIX)) {
        // MISIÓN 2 · "El rumor": checkpoint = ENTRADA del campus (spawn ESCOM canónico) y la
        // misión se REINICIA completa desde la fase 1 (startMission2Story fija fase + objetivo).
        setStorySpawn(
            ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.RETRY_SPAWN_LAT,
            ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.RETRY_SPAWN_LON
        )
        startMission2Story()
        playerHealth = maxPlayerHealth
    } else if (failedObjId != null && failedObjId.startsWith(
            ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3.OBJECTIVE_ID_PREFIX)) {
        // MISIÓN 3 · "Regreso a la ENCB": checkpoint = entrada al vecindario ENCB; se reinicia
        // completa desde la fase VIAJE (el cordón se re-arma al acercarte).
        setStorySpawn(
            ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3.RETRY_SPAWN_LAT,
            ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3.RETRY_SPAWN_LON
        )
        startMission3Story()
        playerHealth = maxPlayerHealth
    } else {
        // Fallback: recargar el slot activo restaura el estado GUARDADO (canónico) → un replay
        // en curso se cancela (las fases guardadas ya van clampadas a DONE).
        keepReplay = null
        if (!loadGame(context, campaignSlot)) setCampaignObjective(MissionCatalog.ESCOLTAR_PRANKEDY)
    }
    // Prankedy DEBE estar contigo al reintentar (SOLO en la Misión 1: respawnPrankedyCompanionHere
    // re-fija el objetivo ESCOLTAR_PRANKEDY — en la Misión 2 eso pisaría el objetivo del retry).
    if (failedObjId?.startsWith(
            ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2.OBJECTIVE_ID_PREFIX) != true) {
        respawnPrankedyCompanionHere()
    }
    replayingMissionId = keepReplay   // el reintento continúa el replay (si lo había)
    _uiState.update { it.copy(showMissionFailed = false) }
}

// ─── OBJETIVOS DE CAMPAÑA ─────────────────────────────────────────────────────
// Fija el objetivo activo (lo llama MainActivity al COMENZAR una campaña nueva).
fun WorldMapViewModel.setCampaignObjective(objective: ovh.gabrielhuav.pow.domain.models.campaign.CampaignObjective?) {
    // Al cambiar de objetivo (nueva misión, MUNDO LIBRE, etc.) se limpia un posible "MISIÓN FALLIDA".
    _uiState.update { it.copy(currentObjective = objective, objectiveDone = false, showMissionFailed = false) }
}

// (El viejo diálogo R7 "¿Continuar la historia o mundo libre?" se ELIMINÓ: la escolta encadena
// DIRECTO con el cómic + la persecución final (son parte de la Misión 1), y a partir de ahí el
// jugador elige qué misión seguir desde el REGISTRO DE MISIONES — ver WorldMapMissionLog.kt.)

// Comprueba si el jugador llegó al objetivo (lo llama el game loop). Al entrar en el radio
// de llegada, marca el objetivo como cumplido y avisa por el HUD.
fun WorldMapViewModel.checkObjectiveProgress(location: GeoPoint) {
    val s = _uiState.value
    val obj = s.currentObjective ?: return
    if (s.objectiveDone) return
    // ⚠️ NO auto-completar INGRESAR_ESCOM por cercanía: esa fase ES la PERSECUCIÓN y se cierra al
    // ENTRAR por la puerta (X → handleInteraction). Si se completara al estar cerca, como tras la
    // escolta ya estás pegado a la puerta, se cumplía al INSTANTE → la persecución (`runMission1ChaseTick`,
    // gateada por `!objectiveDone`) NUNCA arrancaba (policías/multitud/huida de Prankedy) y además
    // sonaba el jingle 2 veces. Su radio = 0 hace que el guard de abajo la salte (cierre = narrativo/X).
    // Objetivos con radio <= 0 (p. ej. ESCOLTAR_PRANKEDY / INGRESAR_ESCOM) NO se cumplen por llegada:
    // es NARRATIVO. Sin esta guarda, como su destino coincide con el spawn de la ENCB, la
    // distancia daba 0 ( <= 0 ) y se marcaba "cumplido" nada más empezar. Ver CampaignMission.kt.
    if (obj.arriveRadiusMeters <= 0.0) return
    val dLat = location.latitude - obj.targetLat
    val dLon = location.longitude - obj.targetLon
    // Conversión grados→metros aprox. (1° lat ≈ 111_320 m; lon corregido por latitud).
    val mLat = dLat * 111_320.0
    val mLon = dLon * 111_320.0 * kotlin.math.cos(Math.toRadians(obj.targetLat))
    val dist = kotlin.math.sqrt(mLat * mLat + mLon * mLon)
    // DIAGNÓSTICO (POW_DBG, ~2 s): tu distancia al objetivo, el radio, y la distancia de Prankedy al
    // destino (la ESCOLTA exige AMBOS < su radio). Si tuDist baja pero no se cumple → Prankedy está lejos.
    run {
        val nowDbg = System.currentTimeMillis()
        if (nowDbg - lastObjDbgMs > 2000L) {
            lastObjDbgMs = nowDbg
            val pk = prankedyManager.location
            val pkDist = pk?.let {
                val a = (it.latitude - obj.targetLat) * 111_320.0
                val b = (it.longitude - obj.targetLon) * 111_320.0 * kotlin.math.cos(Math.toRadians(obj.targetLat))
                kotlin.math.sqrt(a * a + b * b)
            }
            android.util.Log.d("POW_DBG", "objetivo=${obj.id} tuDist=${"%.1f".format(dist)}m radio=${obj.arriveRadiusMeters}m prankedyDist=${pkDist?.let { "%.1f".format(it) }}m")
        }
    }
    if (dist <= obj.arriveRadiusMeters) {
        // ESCOLTA: solo se cumple si PRANKEDY también está junto a la puerta (lo escoltaste de
        // verdad). Llegar tú SOLO a la puerta (al salir de un interior, tras un respawn o en coche)
        // NO debe completar la misión — ese era el bug de "te sales de la ESCOM y se completa sola".
        if (obj.id == MissionCatalog.ESCOLTAR_PRANKEDY.id) {
            // Si Prankedy SIGUE contigo, exige que también esté cerca de la puerta (lo escoltaste de
            // verdad). Pero si ya NO existe (entró/desapareció), NO bloquees el cumplimiento: antes el
            // `?: return` lo hacía IMPOSIBLE de cumplir si Prankedy era null (R9). Radio 45→60 m (holgado).
            val pk = prankedyManager.location
            if (pk != null) {
                val pmLat = (pk.latitude - obj.targetLat) * 111_320.0
                val pmLon = (pk.longitude - obj.targetLon) * 111_320.0 * kotlin.math.cos(Math.toRadians(obj.targetLat))
                if (kotlin.math.sqrt(pmLat * pmLat + pmLon * pmLon) > 60.0) return
            }
        }
        _uiState.update { it.copy(objectiveDone = true, interactionPrompt = "✅ Objetivo cumplido: ${getLocalizedString(obj.titleRes)}") }
        // Jingle de "misión cumplida".
        soundManager.playMisionCumplida()
        // ESCOLTA cumplida (llegaste a la PUERTA de la ESCOM con Prankedy) → cómic IntroPOW12..15
        // y, al volver, la persecución final. Encadena DIRECTO (sin el viejo diálogo R7: el
        // jugador administra sus misiones desde el registro — Opciones → "Misiones").
        if (obj.id == MissionCatalog.ESCOLTAR_PRANKEDY.id) {
            android.util.Log.d("POW_DBG", "MISIÓN 1 (ESCOLTAR) CUMPLIDA → cómic + persecución final")
            _uiState.update { it.copy(pendingMission1ChaseIntro = true) }
        }
    }
}
