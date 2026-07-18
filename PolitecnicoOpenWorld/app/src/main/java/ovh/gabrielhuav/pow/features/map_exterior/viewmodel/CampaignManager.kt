package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3 (descomposición del god-object, ver PLAN_descomponer_WorldMapViewModel.md):
// SEXTO manager (6/6) — el grupo CAMPAÑA es el más enredado, así que se migra en 2 partes
// (ver CHECKPOINT_SENIOR_refactor.md). PARTE A (este manager): el REGISTRO/SELECTOR DE MISIONES:
//   - `showMissionLog`: visibilidad del diálogo del registro (Opciones → "Misiones").
//   - `completedMissions`: ids de misiones ✔ COMPLETADAS (progreso, PERSISTIDO en el guardado JSON).
// El VM lo compone en `uiState` vía la FACHADA `combine` (las Views siguen leyendo
// uiState.showMissionLog / uiState.completedMissions — no se tocó ninguna View).
//
// NOTA DE CORTE (PARTE B, pendiente): el ESTADO DE FASE DE MISIÓN (currentObjective/objectiveDone/
// storyConvoSpeaker/storyConvoText/pendingMission1ChaseIntro/mission3EnterEncb/campaignRouteWaypoints/
// showMissionFailed) NO se migra aquí: lo escriben ~26 sitios repartidos por los ticks de misión
// (WorldMapMission2/3.kt, WorldMapCampaignPolice.kt, WorldMapSaveGame.kt…), es decir, está entrelazado
// con el GAME LOOP. Su migración se evalúa en un checkpoint dedicado (o se documenta como corte limpio).
// La ORQUESTACIÓN del registro (selectCampaignMission/replay/unfollow/devTeleport, que además leen/
// escriben el objetivo de fase) se queda en WorldMapMissionLog.kt y solo DELEGA aquí los 2 campos.
// ─────────────────────────────────────────────────────────────────────────────

/** Sub-estado UI del registro de misiones (campos espejo de WorldMapState). */
data class CampaignSubState(
    val showMissionLog: Boolean = false,
    val completedMissions: List<String> = emptyList()
)

class CampaignManager {

    private val _state = MutableStateFlow(CampaignSubState())
    val state: StateFlow<CampaignSubState> = _state.asStateFlow()

    /** Abre/cierra el diálogo del registro de misiones. */
    fun setShowMissionLog(show: Boolean) {
        _state.update { it.copy(showMissionLog = show) }
    }

    /** ¿La misión ya está marcada como completada? */
    fun isCompleted(missionId: String): Boolean = missionId in _state.value.completedMissions

    /**
     * Marca una misión como COMPLETADA (IDEMPOTENTE — no des-marca ni duplica). Es la regla dura
     * del REJUGAR: volver a completar una misión rejugada no cambia el progreso guardado.
     */
    fun markCompleted(missionId: String) {
        if (missionId in _state.value.completedMissions) return
        _state.update { it.copy(completedMissions = it.completedMissions + missionId) }
    }

    /** Restaura el conjunto de misiones completadas al CARGAR una partida (ya coalescido de NULL). */
    fun setCompletedMissions(missions: List<String>) {
        _state.update { it.copy(completedMissions = missions) }
    }
}
