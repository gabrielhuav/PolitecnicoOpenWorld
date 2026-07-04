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

fun WorldMapViewModel.toggleMissionLog(show: Boolean) {
    _uiState.update { it.copy(showMissionLog = show) }
}

/** Estado actual de una misión del catálogo (para la UI del registro). */
fun WorldMapViewModel.missionLogStatus(missionId: String): MissionLogStatus {
    val s = _uiState.value
    if (missionId in s.completedMissions) return MissionLogStatus.COMPLETED
    if (MissionCatalog.missionIdForObjective(s.currentObjective?.id) == missionId) return MissionLogStatus.ACTIVE
    val info = MissionCatalog.missions.firstOrNull { it.id == missionId } ?: return MissionLogStatus.LOCKED
    val req = info.requiresMissionId
    return if (req == null || req in s.completedMissions) MissionLogStatus.AVAILABLE
           else MissionLogStatus.LOCKED
}

/** Marca una misión como COMPLETADA (idempotente). Se persiste en el guardado JSON. */
internal fun WorldMapViewModel.markMissionCompleted(missionId: String) {
    if (missionId in _uiState.value.completedMissions) return
    _uiState.update { it.copy(completedMissions = it.completedMissions + missionId) }
}

/**
 * SEGUIR una misión desde el registro. ARRANCA la misión si nunca corrió, o la REANUDA en su
 * fase guardada si ya iba a medias. No-op para bloqueadas/completadas (la UI las deshabilita).
 */
fun WorldMapViewModel.selectCampaignMission(missionId: String) {
    if (missionLogStatus(missionId) !in setOf(MissionLogStatus.AVAILABLE, MissionLogStatus.ACTIVE)) return
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
    }
    toggleMissionLog(false)
}

/**
 * DEJAR DE SEGUIR la misión activa: vuelves a MUNDO LIBRE puro (sin 🎯 ni ticks de misión; los
 * actores de misión se limpian en el game loop). La FASE se conserva: al volver a seguirla desde
 * el registro, continúa donde iba.
 */
fun WorldMapViewModel.unfollowActiveMission() {
    _uiState.update { it.copy(currentObjective = null, objectiveDone = false) }
    toggleMissionLog(false)
}
