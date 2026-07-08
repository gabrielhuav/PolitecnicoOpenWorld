package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog

// ─────────────────────────────────────────────────────────────────────────────
// ECONOMÍA del mundo abierto (extensiones del WorldMapViewModel, SIN gemelo miembro).
//
// Dinero del jugador: se gana con COLECCIONABLES (+$25) y al COMPLETAR MISIONES por
// primera vez (MissionRewards; el hook vive en markMissionCompleted → los REPLAYS no
// duplican recompensa porque la misión ya está en completedMissions). Vive en
// WorldMapState.playerMoney y se PERSISTE en GameSaveData.playerMoney (Modo Historia).
// El HUD lo muestra como chip 💵 (WorldMapScreen). Ver 04/CAMPAIGN.
// ─────────────────────────────────────────────────────────────────────────────

// Dinero por coleccionable reclamado (hook en onClaimCollectiblePressed).
internal const val COLLECTIBLE_MONEY = 25

/** Recompensas en DINERO por misión (se pagan UNA vez, al completarla por primera vez). */
object MissionRewards {
    fun moneyFor(missionId: String): Int = when (missionId) {
        MissionCatalog.MISSION_1_ID -> 200
        MissionCatalog.MISSION_2_ID -> 300
        MissionCatalog.MISSION_3_ID -> 500
        MissionCatalog.SIDE_1_ID -> 150
        MissionCatalog.SIDE_2_ID -> 250
        else -> 0
    }
}

/**
 * Suma (o resta) dinero y lo avisa por el HUD (interactionPrompt breve, se auto-limpia).
 * Thread-safe (update atómico); llamable desde el game loop o el hilo Main.
 */
internal fun WorldMapViewModel.addMoney(amount: Int) {
    if (amount == 0) return
    val prompt = if (amount > 0) "💵 +$" + amount else "💵 -$" + (-amount)
    _uiState.update {
        it.copy(
            playerMoney = (it.playerMoney + amount).coerceAtLeast(0),
            interactionPrompt = prompt
        )
    }
    viewModelScope.launch {
        kotlinx.coroutines.delay(2500)
        // Solo limpia si el prompt sigue siendo el del dinero (no pisar otros avisos).
        _uiState.update { if (it.interactionPrompt == prompt) it.copy(interactionPrompt = null) else it }
    }
}
