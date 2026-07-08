package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.domain.models.campaign.mission2.Mission2
import ovh.gabrielhuav.pow.domain.models.campaign.mission3.Mission3

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
fun WorldMapViewModel.selectCampaignMission(missionId: String, force: Boolean = false) {
    // `force` = MODO DESARROLLADOR: permite seguir misiones 🔒 BLOQUEADAS (salta requiresMissionId).
    if (!force && missionLogStatus(missionId) !in setOf(MissionLogStatus.AVAILABLE, MissionLogStatus.ACTIVE)) return
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
 * MODO DESARROLLADOR · "TP al objetivo": teletransporta CERCA del objetivo actual de esa misión
 * (o de su primero si no está activa), con un offset de ~40 m al norte para no caer encima del
 * trigger. Usa la extensión existente teleportTo (gate de TP incluido).
 */
fun WorldMapViewModel.devTeleportToMissionObjective(missionId: String) {
    val s = _uiState.value
    val obj = if (MissionCatalog.missionIdForObjective(s.currentObjective?.id) == missionId)
        s.currentObjective
    else
        MissionCatalog.firstObjectiveOf(missionId)
    if (obj == null) return
    toggleMissionLog(false)
    // ~0.00036° de latitud ≈ 40 m (dentro del rango pedido de 30-50 m).
    teleportTo(obj.targetLat + 0.00036, obj.targetLon)
}
