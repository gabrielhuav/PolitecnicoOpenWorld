package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.MissionCatalog

// ─────────────────────────────────────────────────────────────────────────────
// MODO HISTORIA · MISIÓN 3: "La Señal del Dr. Ramírez"
//
// Flujo:
//   1. Jugador sale del interior de ESCOM con `BUSCAR_PISTAS_ESCOM` activo
//      → onExitEscomInteriorCampaign() → pendingMission3Intro = true
//   2. MainActivity navega a `story_mission3` (cómic de transición)
//   3. Al terminar el cómic → startMission3() → objetivo LOCALIZAR_RAMIREZ en el mapa
//   4. checkMission3Completion() detecta llegada → showChapter1End = true
//   5. WorldMapScreen muestra overlay "Fin del Capítulo 1 · Continuará..."
// ─────────────────────────────────────────────────────────────────────────────

/** Llamado al salir del interior de ESCOM durante campaña. Dispara el cómic de M3. */
fun WorldMapViewModel.onExitEscomInteriorCampaign() {
    if (!inCampaign) return
    val obj = _uiState.value.currentObjective ?: return
    if (obj.id != MissionCatalog.BUSCAR_PISTAS_ESCOM.id) return
    _uiState.update { it.copy(pendingMission3Intro = true) }
}

/** Consume el flag (llamado por MainActivity antes de navegar al cómic). */
fun WorldMapViewModel.consumePendingMission3Intro() {
    _uiState.update { it.copy(pendingMission3Intro = false) }
}

/** Inicia la Misión 3 en el mapa abierto. Llamado al terminar el cómic de transición. */
fun WorldMapViewModel.startMission3() {
    if (!inCampaign) return
    setCampaignObjective(MissionCatalog.LOCALIZAR_RAMIREZ)
    soundManager.playMisionCumplida()
}

/**
 * Detecta si el jugador cumplió la Misión 3 (llegó con el Dr. Ramírez).
 * checkObjectiveProgress() ya pone objectiveDone = true; aquí activamos el
 * overlay "Fin del Capítulo 1" la primera vez que lo detectamos.
 * Llamado desde el game loop (WorldMapViewModel.kt) dentro del bloque inCampaign.
 */
internal fun WorldMapViewModel.checkMission3Completion() {
    val s = _uiState.value
    if (s.currentObjective?.id != MissionCatalog.LOCALIZAR_RAMIREZ.id) return
    if (!s.objectiveDone) return
    if (s.showChapter1End) return
    _uiState.update { it.copy(showChapter1End = true) }
}

/** Cierra el overlay "Fin del Capítulo 1" y limpia el estado de campaña. */
fun WorldMapViewModel.dismissChapter1End() {
    setCampaignObjective(null)
    _uiState.update { it.copy(showChapter1End = false) }
    inCampaign = false // El capítulo terminó; vuelve al modo libre
}
