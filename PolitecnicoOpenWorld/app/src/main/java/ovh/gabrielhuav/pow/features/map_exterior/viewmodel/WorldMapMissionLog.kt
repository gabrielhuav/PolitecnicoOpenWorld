package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
import ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3
import ovh.gabrielhuav.pow.domain.models.zombie.KeyDrop

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · REGISTRO / SELECTOR DE MISIONES (estilo Witcher 3).
//
// Sustituye al viejo diálogo R7 "¿Continuar la historia o mundo libre?": el jugador SIEMPRE
// está en mundo libre y elige qué misión SEGUIR desde Opciones → "Misiones" (MissionLogDialog).
// Seguir una misión fija su objetivo (🎯 + línea guía + widget); dejar de seguirla lo limpia y
// PAUSA sus ticks (la fase se conserva y se reanuda al volver a seguirla). También lista las
// misiones COMPLETADAS (persistidas en GameSaveData.completedMissions) y las BLOQUEADAS
// (requieren la anterior). Preparado para futuras misiones SECUNDARIAS (CampaignMissionInfo.side).
// ─────────────────────────────────────────────────────────────────────────────

/** Estado de una misión para pintar el registro. */
enum class MissionLogStatus { LOCKED, AVAILABLE, ACTIVE, COMPLETED }

// showMissionLog + completedMissions los POSEE campaignManager (manager 6/6, Parte A); estas
// extensiones delegan. El objetivo de FASE (currentObjective) sigue en el VM (Parte B).
fun WorldMapViewModel.toggleMissionLog(show: Boolean) {
    campaignManager.setShowMissionLog(show)
}

/** Estado actual de una misión del catálogo (para la UI del registro). */
fun WorldMapViewModel.missionLogStatus(missionId: String): MissionLogStatus {
    val s = _uiState.value
    // REPLAY en curso: la misión rejugada se muestra ACTIVA (permite DEJAR DE SEGUIR = abandonar
    // el replay) aunque siga marcada como completada en el progreso guardado.
    if (replayingMissionId == missionId &&
        MissionCatalog.missionIdForObjective(s.currentObjective?.id) == missionId) return MissionLogStatus.ACTIVE
    if (campaignManager.isCompleted(missionId)) return MissionLogStatus.COMPLETED
    if (MissionCatalog.missionIdForObjective(s.currentObjective?.id) == missionId) return MissionLogStatus.ACTIVE
    val info = MissionCatalog.missions.firstOrNull { it.id == missionId } ?: return MissionLogStatus.LOCKED
    val req = info.requiresMissionId
    return if (req == null || campaignManager.isCompleted(req)) MissionLogStatus.AVAILABLE
           else MissionLogStatus.LOCKED
}

/** Marca una misión como COMPLETADA (idempotente). Se persiste en el guardado JSON. */
internal fun WorldMapViewModel.markMissionCompleted(missionId: String) {
    // FIN DE UN REPLAY: al volver a completar la misión rejugada se apaga el modo replay (las
    // fases ya quedaron en DONE por el propio flujo de completado; recompensas idempotentes).
    if (replayingMissionId == missionId) replayingMissionId = null
    // RECOMPENSA en DINERO: solo la PRIMERA vez (un replay ya está en completedMissions →
    // no paga de nuevo). Ver MissionRewards en WorldMapEconomy.kt.
    if (!campaignManager.isCompleted(missionId)) {
        val reward = MissionRewards.moneyFor(missionId)
        if (reward > 0) addMoney(reward)
    }
    campaignManager.markCompleted(missionId)   // idempotente (no des-marca ni duplica)
}

/**
 * SEGUIR una misión desde el registro. ARRANCA la misión si nunca corrió, o la REANUDA en su
 * fase guardada si ya iba a medias. No-op para bloqueadas/completadas (la UI las deshabilita).
 */
/**
 * Cancela el estado RUNTIME (actores en curso; NO las fases persistidas) de todas las misiones de
 * campaña MENOS la que se va a seguir. Así, al cambiar de misión, la anterior no sigue viva y
 * "pisando" el objetivo/reintento. Idempotente: limpiar una misión inactiva es no-op.
 */
internal fun WorldMapViewModel.cancelOtherCampaignMissionsRuntime(keep: String?) {
    if (keep != MissionCatalog.MISSION_1_ID) clearCampaignPolice()   // detiene escolta + persecución M1
    if (keep != MissionCatalog.MISSION_2_ID) clearMission2Story()
    if (keep != MissionCatalog.MISSION_3_ID) clearMission3Story()    // (también para el escort/brote M3)
    if (keep != MissionCatalog.SIDE_1_ID && keep != MissionCatalog.SIDE_2_ID) clearSideMissions()
}

fun WorldMapViewModel.selectCampaignMission(missionId: String, force: Boolean = false) {
    // `force` = MODO DESARROLLADOR: permite seguir misiones 🔒 BLOQUEADAS (salta requiresMissionId).
    if (!force && missionLogStatus(missionId) !in setOf(MissionLogStatus.AVAILABLE, MissionLogStatus.ACTIVE)) return
    // Al CAMBIAR de misión se CANCELA el runtime de las demás (actores + cadena de la M1) para que
    // no se traslapen. Bug que arregla: seguir/TP a otra misión dejaba viva la anterior y, al
    // FALLAR + REINTENTAR, te reiniciaba en la misión equivocada (p. ej. la M1). Las FASES
    // persistidas NO se tocan → puedes reanudar donde ibas.
    cancelOtherCampaignMissionsRuntime(keep = missionId)
    when (missionId) {
        MissionCatalog.MISSION_1_ID -> {
            // Misión 1: si no hay objetivo suyo activo, retoma desde su primer objetivo pendiente.
            // (Su cadena escolta→chase sigue siendo auto-guiada una vez en curso.)
            val cur = _uiState.value.currentObjective?.id
            if (MissionCatalog.missionIdForObjective(cur) != MissionCatalog.MISSION_1_ID) {
                setCampaignObjective(MissionCatalog.first)
            }
        }
        MissionCatalog.MISSION_2_ID -> {
            if (mission2Phase == Mission2.PHASE_NONE) startMission2Story()
            else resumeMission2Objective()
        }
        MissionCatalog.MISSION_3_ID -> {
            if (mission3Phase == Mission3.PHASE_NONE) startMission3Story()
            else resumeMission3Objective()
        }
        // SECUNDARIAS: sin fase persistida — seguir = (re)arrancar desde su primer objetivo
        // (son cortas; si se dejó de seguir a medias, se reinician — documentado).
        MissionCatalog.SIDE_1_ID -> startSideMission1()
        MissionCatalog.SIDE_2_ID -> startSideMission2()
    }
    toggleMissionLog(false)
}

/**
 * DEJAR DE SEGUIR la misión activa: vuelves a MUNDO LIBRE puro (sin 🎯 ni ticks de misión; los
 * actores de misión se limpian en el game loop). La FASE se conserva: al volver a seguirla desde
 * el registro, continúa donde iba. Si había un REPLAY en curso, se ABANDONA (endMissionReplay
 * restaura la fase persistida a DONE — el progreso guardado no se toca).
 */
fun WorldMapViewModel.unfollowActiveMission() {
    if (replayingMissionId != null) endMissionReplay()
    _uiState.update { it.copy(currentObjective = null, objectiveDone = false) }
    toggleMissionLog(false)
}

/**
 * REJUGAR una misión ✔ COMPLETADA desde el registro. REGLA DURA — el replay NO afecta el
 * progreso guardado: `completedMissions` NO se des-marca (markMissionCompleted es idempotente),
 * `hasFirearm`/slots NO se pierden (AppNavGraph también gatea por completedMissions) y las fases
 * persistidas se protegen (buildSaveData las clampa a DONE si la misión está completada).
 * `replayingMissionId` es TRANSITORIO (vive en el VM, NO viaja en GameSaveData).
 */
fun WorldMapViewModel.replayCampaignMission(missionId: String) {
    if (missionLogStatus(missionId) != MissionLogStatus.COMPLETED) return
    // Rejugar TAMBIÉN aísla: cancela el runtime de las demás misiones (igual que selectCampaignMission).
    cancelOtherCampaignMissionsRuntime(keep = missionId)
    when (missionId) {
        MissionCatalog.MISSION_1_ID -> {
            // M1 no tiene fase persistida propia: su cadena (escolta → chase) se re-arma con un
            // respawn en el inicio de campaña. ⚠️ setStorySpawn RESETEA mission2/3Phase (y el
            // flag de replay): se capturan ANTES y se restauran DESPUÉS.
            val keep2 = mission2Phase
            val keep3 = mission3Phase
            val school = ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog.schools
                .firstOrNull { it.id == campaignSchoolId }
                ?: ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog.default
            setStorySpawn(school.latitude, school.longitude)
            mission2Phase = keep2
            mission3Phase = keep3
            setCampaignObjective(MissionCatalog.first)
        }
        MissionCatalog.MISSION_2_ID -> startMission2Story()
        MissionCatalog.MISSION_3_ID -> startMission3Story()
        // SECUNDARIAS: rejugar = re-arrancar. La recompensa NO se duplica (markMissionCompleted
        // solo paga si la misión no estaba en completedMissions).
        MissionCatalog.SIDE_1_ID -> startSideMission1()
        MissionCatalog.SIDE_2_ID -> startSideMission2()
        else -> return
    }
    replayingMissionId = missionId   // DESPUÉS del arranque (setStorySpawn lo limpia)
    toggleMissionLog(false)
    android.util.Log.d("POW_DBG", "REGISTRO: rejugando misión $missionId (progreso intacto)")
}

/**
 * Termina/abandona el REPLAY en curso restaurando la fase persistida a DONE (la misión ya
 * estaba completada) y limpiando sus actores. No toca completedMissions ni recompensas.
 */
internal fun WorldMapViewModel.endMissionReplay() {
    when (replayingMissionId) {
        MissionCatalog.MISSION_1_ID -> clearCampaignPolice()
        MissionCatalog.MISSION_2_ID -> { clearMission2Story(); mission2Phase = Mission2.PHASE_DONE }
        MissionCatalog.MISSION_3_ID -> { clearMission3Story(); mission3Phase = Mission3.PHASE_DONE }
        // SECUNDARIAS: no hay fase que restaurar; solo limpiar actores (paramédico/zombis SMZ_).
        MissionCatalog.SIDE_1_ID, MissionCatalog.SIDE_2_ID -> clearSideMissions()
    }
    replayingMissionId = null
}

/**
 * MODO DESARROLLADOR · "TP al objetivo" = TP al CHECKPOINT de la fase ACTUAL (2026-07-12).
 *
 * Semántica (petición del dueño, refinada 2026-07-13b): el TP CUMPLE el objetivo ACTUAL y te
 * lleva al LUGAR donde se juega el SIGUIENTE — una SALA de interiores o un punto del MAPA:
 *  · Si el objetivo pide un ÍTEM, se te da en el INVENTARIO y se quita del mapa (p. ej. la llave
 *    correcta del laboratorio en la M1; `lab1KeyFound=true` evita que se siembre en lab1).
 *  · En la M2, CADA TP completa la fase en curso (perder a la policía / rumor / brote / plática,
 *    `devCompleteMission2Phase`) y te deja en el punto de la siguiente — sin esto el TP te
 *    regresaba a la persecución una y otra vez (QA 2026-07-13).
 *  · La ÚLTIMA fase de una misión (p. ej. la mochila) NO se completa: el TP te deja en la escena
 *    y la juegas tú. ⚠️ NUNCA completar misiones enteras desde aquí (bug 2026-07-12).
 * Funciona desde el MAPA y desde INTERIORES: la navegación pendiente viaja en
 * `WorldMapState.devTpRoute` y la ejecuta AppNavGraph (pop a world_map y, si aplica, navigate a
 * la sala).
 *
 * Checkpoints por misión:
 *  - M1 · `ir_encb`  → sala `encb_lab2` CON la llave correcta EN EL INVENTARIO y
 *    `currentInteriorLab1KeyFound=true` (el waypoint del fondo dispara la 2ª secuencia de cómic
 *    ENCB_OUTRO y la historia sigue con la escolta).
 *  - M1 · `escoltar_prankedy` → mapa cerca de la puerta ESCOM + Prankedy invocado/warpeado a ti.
 *  - M1 · `ingresar_escom` → mapa cerca de la puerta. · `buscar_pistas_escom` → lobby ESCOM.
 *  - M2 · el TP cumple la fase actual y te deja en la SIGUIENTE: esconderse→rumor (lobby ESCOM),
 *    rumor→brote (mapa), brote→plática (mapa, Prankedy ya spawneado), plática→mochila (salón
 *    `escom_salon_m2`; el cómic de la mochila se consume para no chocar con la navegación del
 *    TP). En la fase mochila el TP solo te lleva al salón (se juega).
 *  - M3 · VIAJE/INFILTRACIÓN → mapa (ENCB). · ASALTO → cadena `encb_lobby` (evidencia en lab1).
 *  - Secundarias → mapa (su objetivo actual).
 */
fun WorldMapViewModel.devTeleportToMissionObjective(missionId: String) {
    // 1) SEGUIR la misión (con `force`: también las 🔒) o REJUGARLA si está ✔ (replay transitorio:
    //    el progreso guardado no se toca). Esto ARMA la fase actual (🎯 + actores del tick).
    if (missionLogStatus(missionId) == MissionLogStatus.COMPLETED) {
        replayCampaignMission(missionId)
    } else {
        selectCampaignMission(missionId, force = true)
    }
    toggleMissionLog(false)

    // 2) CUMPLIR el objetivo ACTUAL de la M2 (petición del dueño 2026-07-13b): cada TP completa
    //    la fase en curso (perder a la policía / rumor / brote / plática) y te lleva al punto de
    //    la SIGUIENTE. La última fase (mochila) NO se completa: el TP te deja en el salón a
    //    jugarla — la misión nunca se completa por TP.
    if (missionId == MissionCatalog.MISSION_2_ID &&
        mission2Phase in Mission2.PHASE_HIDE..Mission2.PHASE_TALK) {
        devCompleteMission2Phase()
    }

    // 3) Objetivo de la FASE actual (selectCampaign/replay/auto-cumplido ya fijaron currentObjective).
    val s = _uiState.value
    val obj = if (MissionCatalog.missionIdForObjective(s.currentObjective?.id) == missionId)
        s.currentObjective
    else
        MissionCatalog.firstObjectiveOf(missionId)

    // 4) ¿El checkpoint de esta fase se juega en un INTERIOR? → sala + requisitos concedidos.
    val zc = ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
    val interiorRoom: String? = when {
        // M1 · exploración de la ENCB: el checkpoint es el LABORATORIO FINAL con la llave
        // correcta EN EL INVENTARIO (y quitada del salón: con lab1KeyFound=true lab1 ya no
        // siembra llaves) — cruzas el waypoint tú mismo y sigue el cómic/escolta.
        missionId == MissionCatalog.MISSION_1_ID && obj?.id == MissionCatalog.IR_ENCB.id -> {
            currentInteriorLab1KeyFound = true
            currentInteriorInventory =
                currentInteriorInventory.filterNot { KeyDrop.entryMission(it) == KeyDrop.MISSION_1 } +
                    KeyDrop.inventoryEntry(KeyDrop.MISSION_1, KeyDrop.LAB1_CORRECT_KEY)
            zc.ENCB_LAB2_ID
        }
        missionId == MissionCatalog.MISSION_1_ID && obj?.id == MissionCatalog.BUSCAR_PISTAS_ESCOM.id ->
            zc.LOBBY_ID
        // M2 · fase RUMOR se juega DENTRO del lobby de la ESCOM (a ESCONDERSE ya no se llega:
        // el paso 2 la cumple y aquí la fase es al menos RUMOR).
        missionId == MissionCatalog.MISSION_2_ID && mission2Phase == Mission2.PHASE_RUMOR ->
            zc.LOBBY_ID
        // M2 · fase MOCHILA: el salón EN CLASES (ahí lanzas la lata y recoges la mochila).
        missionId == MissionCatalog.MISSION_2_ID && mission2Phase == Mission2.PHASE_BACKPACK ->
            zc.ESCOM_SALON_M2_ID
        // M3 · ASALTO: la cadena ENCB sembrada con zombis (la evidencia está en encb_lab1).
        missionId == MissionCatalog.MISSION_3_ID && mission3Phase == Mission3.PHASE_ASSAULT ->
            zc.ENCB_LOBBY_ID
        else -> null
    }
    if (interiorRoom != null) {
        soundManager.stopWalk()
        soundManager.stopRun()
        currentInteriorRoomId = interiorRoom
        _uiState.update { it.copy(devTpRoute = "interiores_zombies?startRoom=$interiorRoom") }
        devShowTpPrompt("🧪 TP al checkpoint: " +
            (obj?.let { o -> getLocalizedString(o.titleRes) } ?: interiorRoom))
        return
    }

    // 5) Checkpoint de MAPA: TP CERCA del objetivo (~0.00036° ≈ 40 m al norte, para no caer
    //    encima del trigger — el jugador camina el último tramo y la fase se juega, no se salta).
    if (obj == null) return
    if (currentInteriorRoomId != null) {
        // Venías de un interior: se sale al mapa SIN completar nada (la fase sigue como estaba).
        currentInteriorRoomId = null
        soundManager.stopInvestigarMusic()
    }
    teleportTo(obj.targetLat + 0.00036, obj.targetLon)
    // Requisito de la escolta (M1): Prankedy acompañante JUNTO a ti al llegar.
    if (obj.id == MissionCatalog.ESCOLTAR_PRANKEDY.id) devEnsurePrankedyEscort()
    _uiState.update { it.copy(devTpRoute = DEV_TP_TO_MAP) }
    devShowTpPrompt("🧪 TP al objetivo: ${getLocalizedString(obj.titleRes)}")
}

// Prompt del TP dev con AUTO-LIMPIEZA (~4 s): antes se quedaba pegado en pantalla (y encimado
// con el widget de objetivo). Solo se borra si sigue siendo EL MISMO texto (no pisa prompts
// posteriores de misión).
private fun WorldMapViewModel.devShowTpPrompt(text: String) {
    _uiState.update { it.copy(interactionPrompt = text) }
    viewModelScope.launch {
        delay(4000L)
        _uiState.update { if (it.interactionPrompt == text) it.copy(interactionPrompt = null) else it }
    }
}

/** Sentinela de devTpRoute: "solo vuelve al mapa global" (sin navegar a un interior). */
const val DEV_TP_TO_MAP = "world_map"

/** Consumido por AppNavGraph tras ejecutar la navegación pendiente del TP dev. */
fun WorldMapViewModel.consumeDevTpRoute() {
    _uiState.update { it.copy(devTpRoute = null) }
}

// Requisito de la escolta de la M1: Prankedy acompañante (HIRED) warpeado al jugador. Mismo
// patrón mínimo que ensureM3PrankedyEscort (spawnCompanion + warpTo; NO toca el objetivo).
private fun WorldMapViewModel.devEnsurePrankedyEscort() {
    val loc = _uiState.value.currentLocation ?: return
    val pm = prankedyManager
    if (pm.phase != ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED || pm.location == null) {
        pm.spawnCompanion(loc, roadNetwork, System.currentTimeMillis())
    }
    pm.warpTo(loc)
    _uiState.update { it.copy(
        prankedyEnabled = true,
        prankedyLocation = pm.location,
        prankedyVisible = pm.location != null && !it.isDriving,
        prankedyPhase = pm.phase
    ) }
}
