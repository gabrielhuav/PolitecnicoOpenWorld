package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

// ─────────────────────────────────────────────────────────────────────────────
// PARCIAL del WorldMapViewModel: MODO DISEÑADOR / LANDMARKS (carga desde Room,
// siembra de default_landmarks.json, edición, import/export). Extraído de
// WorldMapViewModel.kt en el refactor de tamaño. El estado (escomNavGraph,
// _uiState) sigue en el ViewModel. NO duplicar estos nombres como miembros.
// ─────────────────────────────────────────────────────────────────────────────

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
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
import ovh.gabrielhuav.pow.domain.models.map.LandmarkCatalogManager

fun WorldMapViewModel.loadLandmarks(context: Context) {
    loadExteriorCollisions(context) // ESTO CARGA EL JSON DE MUROS
    loadMetroStations(context)
    loadMetrobusStations(context)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            LandmarkCatalogManager.loadCatalog(context)
            val database = PowDatabase.getInstance(context)
            val dao = database.landmarkDao()
            var entities = dao.getAllLandmarks()

            if (entities.isEmpty()) {
                try {
                    val jsonString = context.assets.open("CONFIG/default_landmarks.json").bufferedReader().use { it.readText() }
                    val type = object : TypeToken<List<LandmarkEntity>>() {}.type
                    val defaultEntities: List<LandmarkEntity> = Gson().fromJson(jsonString, type)
                    dao.insertLandmarks(defaultEntities)
                    entities = dao.getAllLandmarks()
                    Log.d("WorldMapViewModel", "Mapa sembrado con éxito desde default_landmarks.json con ${entities.size} edificios.")
                } catch (e: java.io.FileNotFoundException) {
                    Log.w("WorldMapViewModel", "Archivo default_landmarks.json no encontrado.")
                } catch (e: Exception) {
                    Log.e("WorldMapViewModel", "Error leyendo default_landmarks.json", e)
                }
            }

            // Backfill: inserta el landmark de Shine si la BD ya estaba sembrada sin él
            if (entities.none { it.assetPath == "BUILDINGS/BAR/Shine.webp" }) {
                dao.insertLandmark(
                    LandmarkEntity(
                        name = "Shine CTO",
                        latitude = 19.459038634489882,
                        longitude = -99.1633282698258,
                        assetPath = "BUILDINGS/BAR/Shine.webp",
                        scaleFactor = 0.50f,
                        rotationAngle = 285f,
                        scaleX = 0.50f,
                        scaleY = 0.50f
                    )
                )
                entities = dao.getAllLandmarks()
            }

            // Backfill: inserta las puertas del Deportivo Miguel Alemán si no existen
            var backfillNeeded = false
            if (entities.none { it.name == "Entrada Campo Béisbol" }) {
                dao.insertLandmark(
                    LandmarkEntity(
                        name = "Entrada Campo Béisbol",
                        latitude = 19.494200,
                        longitude = -99.129200,
                        assetPath = "DOORS/ESCOM_DOOR.webp",
                        scaleFactor = 0.60f,
                        rotationAngle = 0.0f
                    )
                )
                backfillNeeded = true
            }
            if (entities.none { it.name == "Entrada Campo Fútbol" }) {
                dao.insertLandmark(
                    LandmarkEntity(
                        name = "Entrada Campo Fútbol",
                        latitude = 19.492800,
                        longitude = -99.127800,
                        assetPath = "DOORS/ESCOM_DOOR.webp",
                        scaleFactor = 0.60f,
                        rotationAngle = 0.0f
                    )
                )
                backfillNeeded = true
            }
            // Backfill/reubicación de la puerta "Entrada FES Aragón". Posición canónica:
            // junto al teletransporte de FES Aragón (TeleportCatalog: 19.475167, -99.047444)
            // para que al llegar por TP no haya que caminar mucho hasta la puerta.
            val fesDoorLat = 19.475167
            val fesDoorLon = -99.047530
            val existingFesDoor = entities.firstOrNull { it.name == "Entrada FES Aragón" }
            if (existingFesDoor == null) {
                dao.insertLandmark(
                    LandmarkEntity(
                        name = "Entrada FES Aragón",
                        latitude = fesDoorLat,
                        longitude = fesDoorLon,
                        assetPath = "DOORS/ESCOM_DOOR.webp",
                        scaleFactor = 0.60f,
                        rotationAngle = 0.0f,
                        scaleX = 0.60f,
                        scaleY = 0.60f
                    )
                )
                backfillNeeded = true
            } else {
                // Ya existe: si está LEJOS de la posición canónica (> ~33 m) la reubicamos
                // junto al TP. Por debajo de ese umbral se respeta el ajuste fino del Diseñador.
                val dLat = existingFesDoor.latitude - fesDoorLat
                val dLon = existingFesDoor.longitude - fesDoorLon
                if (kotlin.math.sqrt(dLat * dLat + dLon * dLon) > 0.0003) {
                    dao.updateLandmark(existingFesDoor.copy(latitude = fesDoorLat, longitude = fesDoorLon))
                    backfillNeeded = true
                }
            }
            if (backfillNeeded) {
                entities = dao.getAllLandmarks()
            }

            val templatesByAssetPath = LandmarkCatalogManager.availableAssets.associateBy { it.assetPath }

            // Cargamos el navGraph de ESCOM en memoria si no está cargado.
            // Lo hacemos una sola vez para no abrir el archivo por cada edificio.
            if (escomNavGraph == null) {
                try {
                    val inputStream = context.assets.open("CONFIG/navgraphs/escom_navgraph.json")
                    val reader = java.io.InputStreamReader(inputStream)
                    escomNavGraph = normalizeNavGraph(Gson().fromJson(reader, ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph::class.java))
                    reader.close()
                } catch (e: Exception) {
                    Log.e("WorldMapViewModel", "No se pudo cargar el navGraph de ESCOM al inicio", e)
                }
            }

            // Mapeamos las entidades de la Base de Datos a la clase Landmark
            val domainLandmarks = entities.map { entity ->
                val template = templatesByAssetPath[entity.assetPath]

                // Si el edificio es ESCOM, le inyectamos su navGraph.
                // Si mañana agregas "Zacatenco", puedes poner "else if" aquí.
                val assignedNavGraph = if (entity.assetPath.contains("building_escom", ignoreCase = true)) {
                    escomNavGraph
                } else {
                    null // Los demás edificios nacen sin cerebro (por ahora)
                }

                // Coalesce de escala: las entidades sembradas desde default_landmarks.json
                // (o JSON importados antiguos) NO traen scaleX/scaleY. Gson NO aplica los
                // valores por defecto de Kotlin a campos primitivos ausentes, así que llegan
                // como 0.0f. Un scaleX/scaleY de 0 colapsa el GroundOverlay a tamaño cero y
                // el asset se vuelve INVISIBLE. Caemos a scaleFactor y, en último caso, a 1.0.
                val effectiveScaleX = when {
                    entity.scaleX > 0f -> entity.scaleX
                    entity.scaleFactor > 0f -> entity.scaleFactor
                    else -> 1.0f
                }
                val effectiveScaleY = when {
                    entity.scaleY > 0f -> entity.scaleY
                    entity.scaleFactor > 0f -> entity.scaleFactor
                    else -> 1.0f
                }

                Landmark(
                    id = entity.id,
                    name = entity.name,
                    location = GeoPoint(entity.latitude, entity.longitude),
                    assetPath = entity.assetPath,
                    scaleX = effectiveScaleX,
                    scaleY = effectiveScaleY,
                    rotationAngle = entity.rotationAngle,
                    baseWidthMeters = template?.baseWidthMeters ?: 100f,
                    baseHeightMeters = template?.baseHeightMeters ?: 100f,

                    navGraph = assignedNavGraph
                )
            }

            _uiState.update { currentState -> currentState.copy(landmarks = domainLandmarks) }

            // DIAGNÓSTICO (filtra Logcat por POW_DBG): cuántos landmarks cargaron, cuántos con navGraph,
            // y el estado del de ESCOM (navGraph adjunto, nº de slots de estacionamiento, tamaño base).
            val escomLm = domainLandmarks.firstOrNull { it.assetPath.contains("building_escom", true) }
            val escomSlots = escomLm?.navGraph?.ways?.sumOf { w -> w.nodes.count { n -> n.isParkingSlot } } ?: 0
            Log.d("POW_DBG", "loadLandmarks: total=${domainLandmarks.size} conNavGraph=${domainLandmarks.count { it.navGraph != null }} | ESCOM: existe=${escomLm != null} navGraph=${escomLm?.navGraph != null} slots=$escomSlots baseW=${escomLm?.baseWidthMeters} baseH=${escomLm?.baseHeightMeters} scaleX=${escomLm?.scaleX} scaleY=${escomLm?.scaleY}")

        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error fatal al cargar las estructuras estáticas", e)
        }
    }
}

fun WorldMapViewModel.toggleDesignerMode(isDesigner: Boolean) { _uiState.update { it.copy(isDesignerMode = isDesigner, selectedLandmarkId = if (!isDesigner) null else it.selectedLandmarkId) } }
fun WorldMapViewModel.showAssetPicker(show: Boolean) { _uiState.update { it.copy(showAssetPicker = show) } }
fun WorldMapViewModel.selectLandmark(id: Long?) { _uiState.update { it.copy(selectedLandmarkId = id) } }

fun WorldMapViewModel.addLandmarkAtPlayer(context: Context, template: LandmarkAssetTemplate) {
    val playerLoc = _uiState.value.currentLocation ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val newEntity = LandmarkEntity(
                name = template.displayName,
                latitude = playerLoc.latitude,
                longitude = playerLoc.longitude,
                assetPath = template.assetPath,
                scaleFactor = template.defaultScale,
                rotationAngle = 0f,
                scaleX = template.defaultScale,
                scaleY = template.defaultScale
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
            if (it.id == id) {
                it.copy(location = GeoPoint(it.location.latitude + dLat, it.location.longitude + dLon))
            } else it
        }
        state.copy(landmarks = updated)
    }
}

/** Mueve un landmark a una posición ABSOLUTA (lo usa el lápiz del renderer web,
 *  que reporta el lat/lng final del arrastre en vez de deltas). También lo selecciona. */
fun WorldMapViewModel.moveLandmarkTo(id: Long, lat: Double, lon: Double) {
    _uiState.update { state ->
        val updated = state.landmarks.map {
            if (it.id == id) it.copy(location = GeoPoint(lat, lon)) else it
        }
        state.copy(landmarks = updated, selectedLandmarkId = id)
    }
}

fun WorldMapViewModel.rotateSelectedLandmark(angle: Float) {
    val id = _uiState.value.selectedLandmarkId ?: return
    _uiState.update { state ->
        val updated = state.landmarks.map {
            if (it.id == id) it.copy(rotationAngle = angle)
            else it
        }
        state.copy(landmarks = updated)
    }
}

fun WorldMapViewModel.scaleXSelectedLandmark(scaleX: Float) {
    val id = _uiState.value.selectedLandmarkId ?: return
    _uiState.update { state ->
        val updated = state.landmarks.map {
            if (it.id == id) it.copy(scaleX = scaleX) else it
        }
        state.copy(landmarks = updated)
    }
}

fun WorldMapViewModel.scaleYSelectedLandmark(scaleY: Float) {
    val id = _uiState.value.selectedLandmarkId ?: return
    _uiState.update { state ->
        val updated = state.landmarks.map {
            if (it.id == id) it.copy(scaleY = scaleY) else it
        }
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
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al guardar JSON en archivo", e)
        }
    }
}

fun WorldMapViewModel.importLandmarksFromUri(context: Context, uri: android.net.Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val jsonString = inputStream?.bufferedReader().use { it?.readText() } ?: return@launch
            val type = object : TypeToken<List<LandmarkEntity>>() {}.type
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
    val id = _uiState.value.selectedLandmarkId ?: return
    val currentLandmark = _uiState.value.landmarks.find { it.id == id } ?: return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val dao = PowDatabase.getInstance(context).landmarkDao()
            val updatedEntity = LandmarkEntity(
                id = currentLandmark.id,
                name = currentLandmark.name,
                latitude = currentLandmark.location.latitude,
                longitude = currentLandmark.location.longitude,
                assetPath = currentLandmark.assetPath,
                scaleFactor = currentLandmark.scaleX,
                rotationAngle = currentLandmark.rotationAngle,
                scaleX = currentLandmark.scaleX,
                scaleY = currentLandmark.scaleY
            )
            dao.updateLandmark(updatedEntity)
        } catch (e: Exception) {
            Log.e("WorldMapViewModel", "Error al actualizar landmark", e)
        }
    }
}
