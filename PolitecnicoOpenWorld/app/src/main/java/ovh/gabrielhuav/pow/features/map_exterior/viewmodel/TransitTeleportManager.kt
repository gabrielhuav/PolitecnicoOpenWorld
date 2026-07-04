package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.map.MetroStation
import ovh.gabrielhuav.pow.domain.models.map.MetrobusStation

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3 (descomposición del god-object, ver PLAN_descomponer_WorldMapViewModel.md):
// QUINTO manager (5/6). Posee el sub-estado UI de las TRANSICIONES DE PANTALLA: el menú de
// TELETRANSPORTE, las estaciones de METRO/METROBÚS (catálogo + cercana + fade de entrada +
// estación destino) y el fade de la PUERTA de la ESCOM. El VM lo compone en `uiState` vía la
// FACHADA `combine` (las Views siguen leyendo uiState.showTeleportMenu / .metroStations /
// .nearbyMetroStation / .showMetroFade / … — no se tocó ninguna View).
//
// NOTA DE CORTE (ver CHECKPOINT_SENIOR_refactor.md, manager 5/6): la ORQUESTACIÓN se queda en
// el VM (parciales WorldMapTeleport.kt / WorldMapCollectiblesLogic.kt / WorldMapInteractions.kt /
// WorldMapShineCTO.kt) y solo DELEGA aquí los writes del sub-estado:
//   - `teleportTo` (gate del mundo, limpia NPCs, resetea compuertas + estado de OTROS grupos:
//     wanted/fade/nearby → llama a clearTransitOnTeleport() para su parte),
//   - `checkCollectibleProximity` (proximidad de estaciones MEZCLADA con coleccionables + el
//     interactionPrompt temporizado con coroutine → delega los set/clear de estación cercana),
//   - `handleInteraction` (entrar a metro/metrobús/puerta según el estado base),
//   - los fade-complete (miembros del VM llamados por la UI) que además limpian interactionPrompt.
// Los repos de estaciones (MetroRepository/MetrobusRepository, IO) y el routing de la puerta
// (InteriorEntryCatalog) se quedan en el VM.
// ─────────────────────────────────────────────────────────────────────────────

/** Sub-estado UI de teletransporte + estaciones de transporte + fades (campos espejo de WorldMapState). */
data class TransitTeleportSubState(
    // Menú de teletransporte (Puntos de Teletransporte).
    val showTeleportMenu: Boolean = false,
    // Metro: catálogo, estación cercana (radar), fade de entrada y estación destino del fade.
    val metroStations: List<MetroStation> = emptyList(),
    val nearbyMetroStation: MetroStation? = null,
    val showMetroFade: Boolean = false,
    val metroFadeCompleteStation: MetroStation? = null,
    // Metrobús: idéntico al metro.
    val metrobusStations: List<MetrobusStation> = emptyList(),
    val nearbyMetrobusStation: MetrobusStation? = null,
    val showMetrobusFade: Boolean = false,
    val metrobusFadeCompleteStation: MetrobusStation? = null,
    // Transición de la PUERTA de la ESCOM (fade → completo → navegar al interior → consumir).
    val showEscomDoorFade: Boolean = false,
    val escomDoorFadeComplete: Boolean = false,
    val pendingDoorDestination: String? = null
)

class TransitTeleportManager {

    private val _state = MutableStateFlow(TransitTeleportSubState())
    val state: StateFlow<TransitTeleportSubState> = _state.asStateFlow()

    // ─── Menú de teletransporte ──────────────────────────────────────────────
    /** Abre/cierra el diálogo de Puntos de Teletransporte. */
    fun setTeleportMenu(show: Boolean) {
        _state.update { it.copy(showTeleportMenu = show) }
    }

    // ─── Catálogo de estaciones (lo cargan los repos IO desde el VM) ─────────
    fun setMetroStations(stations: List<MetroStation>) {
        _state.update { it.copy(metroStations = stations) }
    }

    fun setMetrobusStations(stations: List<MetrobusStation>) {
        _state.update { it.copy(metrobusStations = stations) }
    }

    // ─── Radar de estación cercana (proximidad) ──────────────────────────────
    /** Fija/limpia la estación de metro dentro del radio de interacción (null = ninguna). */
    fun setNearbyMetro(station: MetroStation?) {
        _state.update { it.copy(nearbyMetroStation = station) }
    }

    /** Fija/limpia la estación de metrobús dentro del radio de interacción (null = ninguna). */
    fun setNearbyMetrobus(station: MetrobusStation?) {
        _state.update { it.copy(nearbyMetrobusStation = station) }
    }

    // ─── Fade de entrada a la estación ───────────────────────────────────────
    /** Arranca el fundido de entrada al interior del metro. */
    fun beginMetroFade() {
        _state.update { it.copy(showMetroFade = true) }
    }

    /** Arranca el fundido de entrada al interior del metrobús. */
    fun beginMetrobusFade() {
        _state.update { it.copy(showMetrobusFade = true) }
    }

    /**
     * Fin del fundido de metro: si hay estación cercana, apaga el fade, fija la estación destino
     * (la UI navega al interior) y limpia el radar. Devuelve true si disparó (para que el VM limpie
     * el interactionPrompt, que es de OTRO grupo).
     */
    fun onMetroFadeComplete(): Boolean {
        val station = _state.value.nearbyMetroStation ?: return false
        _state.update {
            it.copy(showMetroFade = false, metroFadeCompleteStation = station, nearbyMetroStation = null)
        }
        return true
    }

    /** Consume la estación destino del metro tras navegar (evita re-disparar la navegación). */
    fun consumeMetroFadeComplete() {
        _state.update { it.copy(metroFadeCompleteStation = null) }
    }

    /** Fin del fundido de metrobús (análogo a onMetroFadeComplete). */
    fun onMetrobusFadeComplete(): Boolean {
        val station = _state.value.nearbyMetrobusStation ?: return false
        _state.update {
            it.copy(showMetrobusFade = false, metrobusFadeCompleteStation = station, nearbyMetrobusStation = null)
        }
        return true
    }

    /** Consume la estación destino del metrobús tras navegar. */
    fun consumeMetrobusFadeComplete() {
        _state.update { it.copy(metrobusFadeCompleteStation = null) }
    }

    // ─── Transición de la puerta de la ESCOM ─────────────────────────────────
    /** Arranca el fundido de la puerta de la ESCOM hacia `destination` (ruta del interior). */
    fun beginEscomDoorFade(destination: String) {
        _state.update { it.copy(showEscomDoorFade = true, pendingDoorDestination = destination) }
    }

    /** Fin del fundido de la puerta: apaga el fade y marca "completo" (la UI navega). */
    fun onEscomDoorFadeComplete() {
        _state.update { it.copy(showEscomDoorFade = false, escomDoorFadeComplete = true) }
    }

    /** Consume la navegación de la puerta: limpia el flag/destino y devuelve la ruta pendiente. */
    fun consumeEscomDoorNavigation(): String? {
        val dest = _state.value.pendingDoorDestination
        _state.update { it.copy(escomDoorFadeComplete = false, pendingDoorDestination = null) }
        return dest
    }

    // ─── Limpieza al teletransportarse ───────────────────────────────────────
    /**
     * Pizarra limpia de transporte en cada TP: cierra el menú y limpia fades/estaciones cercanas
     * (un *FadeCompleteStation pendiente disparaba navegación al volver al mapa → "salir del metro
     * me mandaba al metrobús"). NO toca los catálogos (persisten) ni la puerta ESCOM.
     */
    fun clearTransitOnTeleport() {
        _state.update {
            it.copy(
                showTeleportMenu = false,
                showMetroFade = false,
                metroFadeCompleteStation = null,
                nearbyMetroStation = null,
                showMetrobusFade = false,
                metrobusFadeCompleteStation = null,
                nearbyMetrobusStation = null
            )
        }
    }
}
