package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.LandmarkAssetTemplate
import ovh.gabrielhuav.pow.domain.models.LandmarkCatalogManager

// ─── SISTEMA DE LANDMARKS ─────────────────────────────────────────────────────

fun WorldMapViewModel.loadLandmarks(context: Context) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            LandmarkCatalogManager.loadCatalog(context)
            val database = PowDatabase.getInstance(context)
            val dao = database.landmarkDao()
            var entities = dao.getAllLandmarks()
            if (entities.isEmpty()) {
                try {
                    val jsonString = context.assets.open("default_landmarks.json").bufferedReader().use { it.readText() }
                    val type = object : TypeToken<List<LandmarkEntity>>() {}.type
                    val defaultEntities: List<LandmarkEntity> = Gson().fromJson(jsonString, type)
                    dao.insertLandmarks(defaultEntities)
                    entities = dao.getAllLandmarks()
                    Log.d("WorldMapViewModel", "Mapa sembrado con ${entities.size} edificios desde default_landmarks.json.")
                } catch (e: java.io.FileNotFoundException) {
                    Log.w("WorldMapViewModel", "Archivo default_landmarks.json no encontrado.")
                } catch (e: Exception) {
                    Log.e("WorldMapViewModel", "Error leyendo default_landmarks.json", e)
                }
            }
            val templatesByAssetPath = LandmarkCatalogManager.availableAssets.associateBy { it.assetPath }
            val domainLandmarks = entities.map { entity ->
                val template = templatesByAssetPath[entity.assetPath]
                Landmark(
                    id              = entity.id,
                    name            = entity.name,
                    location        = GeoPoint(entity.latitude, entity.longitude),
                    assetPath       = entity.assetPath,
                    scaleFactor     = entity.scaleFactor,
                    rotationAngle   = entity.rotationAngle,
                    baseWidthMeters = template?.baseWidthMeters  ?: 100f,
                    baseHeightMeters = template?.baseHeightMeters ?: 100f
                )
            }
            _uiState.update { currentState -> currentState.copy(landmarks = domainLandmarks) }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error fatal al cargar las estructuras estáticas", e)
        }
    }
}

fun WorldMapViewModel.toggleDesignerMode(isDesigner: Boolean) {
    _uiState.update { it.copy(
        isDesignerMode    = isDesigner,
        selectedLandmarkId = if (!isDesigner) null else it.selectedLandmarkId
    )}
}

fun WorldMapViewModel.showAssetPicker(show: Boolean) {
    _uiState.update { it.copy(showAssetPicker = show) }
}

fun WorldMapViewModel.selectLandmark(id: Long?) {
    _uiState.update { it.copy(selectedLandmarkId = id) }
}

fun WorldMapViewModel.addLandmarkAtPlayer(context: Context, template: LandmarkAssetTemplate) {
    val playerLoc = _uiState.value.currentLocation ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val newEntity = LandmarkEntity(
                name          = template.displayName,
                latitude      = playerLoc.latitude,
                longitude     = playerLoc.longitude,
                assetPath     = template.assetPath,
                scaleFactor   = template.defaultScale,
                rotationAngle = 0f
            )
            val newId = dao.insertLandmark(newEntity)
            loadLandmarks(context)
            _uiState.update { it.copy(showAssetPicker = false, selectedLandmarkId = newId) }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al agregar landmark", e)
        }
    }
}

fun WorldMapViewModel.moveSelectedLandmark(dLat: Double, dLon: Double) {
    val id = _uiState.value.selectedLandmarkId ?: return
    _uiState.update { state ->
        val updated = state.landmarks.map {
            if (it.id == id)
                it.copy(location = GeoPoint(it.location.latitude + dLat, it.location.longitude + dLon))
            else it
        }
        state.copy(landmarks = updated)
    }
}

fun WorldMapViewModel.rotateSelectedLandmark(angle: Float) {
    val id = _uiState.value.selectedLandmarkId ?: return
    _uiState.update { state ->
        val updated = state.landmarks.map { if (it.id == id) it.copy(rotationAngle = angle) else it }
        state.copy(landmarks = updated)
    }
}

fun WorldMapViewModel.scaleSelectedLandmark(scale: Float) {
    val id = _uiState.value.selectedLandmarkId ?: return
    _uiState.update { state ->
        val updated = state.landmarks.map { if (it.id == id) it.copy(scaleFactor = scale) else it }
        state.copy(landmarks = updated)
    }
}

fun WorldMapViewModel.deleteSelectedLandmark(context: Context) {
    val id = _uiState.value.selectedLandmarkId ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val entity = dao.getLandmarkById(id)
            if (entity != null) {
                dao.deleteLandmark(entity)
                loadLandmarks(context)
                _uiState.update { it.copy(selectedLandmarkId = null) }
            }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al borrar landmark", e)
        }
    }
}

fun WorldMapViewModel.exportLandmarksToUri(context: Context, uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val entities = dao.getAllLandmarks()
            val jsonString = Gson().toJson(entities)
            context.contentResolver.openOutputStream(uri)?.use { it.write(jsonString.toByteArray()) }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al guardar JSON en archivo", e)
        }
    }
}

fun WorldMapViewModel.importLandmarksFromUri(context: Context, uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString  = inputStream?.bufferedReader().use { it?.readText() } ?: return@launch
            val type        = object : TypeToken<List<LandmarkEntity>>() {}.type
            val importedEntities: List<LandmarkEntity> = Gson().fromJson(jsonString, type)
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val currentLandmarks = dao.getAllLandmarks()
            currentLandmarks.forEach { dao.deleteLandmark(it) }
            dao.insertLandmarks(importedEntities)
            loadLandmarks(context)
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al importar JSON desde archivo", e)
        }
    }
}

fun WorldMapViewModel.saveSelectedLandmark(context: Context) {
    val id              = _uiState.value.selectedLandmarkId ?: return
    val currentLandmark = _uiState.value.landmarks.find { it.id == id } ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val updatedEntity = LandmarkEntity(
                id            = currentLandmark.id,
                name          = currentLandmark.name,
                latitude      = currentLandmark.location.latitude,
                longitude     = currentLandmark.location.longitude,
                assetPath     = currentLandmark.assetPath,
                scaleFactor   = currentLandmark.scaleFactor,
                rotationAngle = currentLandmark.rotationAngle
            )
            dao.updateLandmark(updatedEntity)
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al actualizar landmark", e)
        }
    }
}
