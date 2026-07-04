package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible

// ─────────────────────────────────────────────────────────────────────────────
// ETAPA 3 (descomposición del god-object, ver PLAN_descomponer_WorldMapViewModel.md):
// SEGUNDO manager con SUB-ESTADO PROPIO (manager 2/6). Posee SOLO el sub-estado UI
// del grupo COLECCIONABLES de WorldMapState: los objetos activos en el mapa, el que
// tienes cerca (radar) y el popup de "reclamado". El VM lo compone en `uiState` vía la
// FACHADA `combine` (la FORMA que ve la UI no cambia: las Views siguen leyendo
// uiState.activeCollectibles / .nearbyCollectible / .showClaimedPopupFor).
//
// NOTA DE CORTE (ver CHECKPOINT_SENIOR_refactor.md, aviso del manager 2/6): los ítems
// de ESCOM (`_escomItems`), el flag `isZombieHandSpawned` y el candado de spawn
// `isSpawningCollectible` NO se mueven aquí: son estado NO-UI enredado con el game loop
// y la zona ESCOM. Se quedan en el VM (corte limpio > dogma).
//
// Las funciones que ORQUESTAN (proximidad/spawn/claim) mezclan Context/IO/otros grupos,
// así que siguen como extensiones del VM y solo DELEGAN aquí la parte del sub-estado.
// ─────────────────────────────────────────────────────────────────────────────

/** Sub-estado UI de coleccionables (campos espejo de WorldMapState). */
data class CollectiblesUiState(
    val activeCollectibles: List<ActiveCollectible> = emptyList(),
    val nearbyCollectible: ActiveCollectible? = null,
    val showClaimedPopupFor: ActiveCollectible? = null
)

class CollectiblesManager {

    private val _state = MutableStateFlow(CollectiblesUiState())
    val state: StateFlow<CollectiblesUiState> = _state.asStateFlow()

    // ─── Coleccionables activos en el mapa ───────────────────────────────────
    /** Reemplaza la lista de coleccionables activos (spawn de un objeto normal). */
    fun setActive(items: List<ActiveCollectible>) {
        _state.update { it.copy(activeCollectibles = items) }
    }

    /** Añade un coleccionable persistente (p. ej. el marcador ShineCTO) sin borrar los demás. */
    fun addActive(item: ActiveCollectible) {
        _state.update { it.copy(activeCollectibles = it.activeCollectibles + item) }
    }

    /** Vacía la lista de coleccionables activos. */
    fun clearActive() {
        _state.update { it.copy(activeCollectibles = emptyList()) }
    }

    // ─── Radar (objeto que tienes cerca) ─────────────────────────────────────
    /** Fija el coleccionable/puerta/NPC que está dentro del radio de interacción. */
    fun setNearby(item: ActiveCollectible?) {
        _state.update { it.copy(nearbyCollectible = item) }
    }

    /** Limpia el objeto cercano (al alejarse, al entrar a metro/puerta, etc.). */
    fun clearNearby() {
        _state.update { it.copy(nearbyCollectible = null) }
    }

    // ─── Popup de "reclamado" ────────────────────────────────────────────────
    /**
     * Reclama un coleccionable: vacía la lista + el radar y muestra el popup de recompensa,
     * en UNA sola actualización atómica del sub-estado.
     */
    fun claim(item: ActiveCollectible) {
        _state.update {
            it.copy(
                activeCollectibles = emptyList(),
                nearbyCollectible = null,
                showClaimedPopupFor = item
            )
        }
    }

    /** Cierra el popup de "reclamado". */
    fun dismissClaimedPopup() {
        _state.update { it.copy(showClaimedPopupFor = null) }
    }
}
