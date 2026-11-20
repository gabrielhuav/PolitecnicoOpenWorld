package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.domain.models.map.CampusFootprintsFile
import ovh.gabrielhuav.pow.domain.models.map.RUTA_HUELLAS_CAMPUS
import ovh.gabrielhuav.pow.domain.models.map.toFootprints
import ovh.gabrielhuav.pow.platform.assets.PowAssets

// Prototipo del mapa cenital estilizado (estilo Chinatown Wars): carga las huellas de OSM del
// campus y activa/desactiva la capa Compose `CampusFootprintLayer`. Ver 04_MAP_EXTERIOR.md.

/** Activa o apaga la capa; la primera vez carga el archivo en IO. */
internal fun WorldMapViewModel.setCampusFootprints(show: Boolean) {
    if (!show) {
        _uiState.update { it.copy(showCampusFootprints = false) }
        return
    }
    if (_uiState.value.campusFootprints.isNotEmpty()) {
        _uiState.update { it.copy(showCampusFootprints = true) }
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val file = PowJson.decodeFromString<CampusFootprintsFile>(PowAssets.texto(RUTA_HUELLAS_CAMPUS))
            val footprints = file.toFootprints()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(campusFootprints = footprints, showCampusFootprints = true) }
            }
            Log.d("Footprints", "cargadas ${footprints.size} huellas")
        } catch (e: Exception) {
            Log.e("Footprints", "Error: ${e.message}")
        }
    }
}
