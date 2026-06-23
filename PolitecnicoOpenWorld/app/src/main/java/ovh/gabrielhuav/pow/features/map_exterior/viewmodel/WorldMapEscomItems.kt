package ovh.gabrielhuav.pow.features.map_exterior.viewmodel


import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.osmdroid.util.GeoPoint
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.cache.RoadNetworkCache
import ovh.gabrielhuav.pow.data.cache.TileCache
import ovh.gabrielhuav.pow.data.local.room.PowDatabase
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.data.repository.OverpassRepository
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.map.Landmark
import ovh.gabrielhuav.pow.domain.models.map.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.map.LandmarkAssetTemplate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.abs
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.io.InputStreamReader
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig

// ─────────────────────────────────────────────────────────────────────────────
// Items de ESCOM + inyección de coche dinámico (Modo Diseñador) extraídos del VM.
// spawnDynamicCarInEscom usa normalizeNavGraph (miembro internal del VM) y updateNpcsState.
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * Sincroniza los items de ESCOM. La "Mano del Apocalipsis" se ELIMINÓ: ya no se
     * spawnea ninguna mano (el apocalipsis se activa desde el menú de Opciones).
     */
internal fun WorldMapViewModel.spawnEscomItems(roadNetwork: List<MapWay>, cantidad: Int = 1) {
        // La "Mano del Apocalipsis" se ELIMINÓ de ESCOM (a petición). El modo zombi global
        // se activa/desactiva desde Opciones → "Activar/Desactivar Apocalipsis" (o el botón
        // flotante de salida). Aquí ya no se spawnea ninguna mano: dejamos vacíos los items
        // de ESCOM y marcamos el flag "sincronizado" para que el game loop no re-llame.
        if (_escomItems.value.any { it.id == "global_zombie_hand" }) {
            _escomItems.value = _escomItems.value.filter { it.id != "global_zombie_hand" }
        }
        _uiState.update { it.copy(isZombieHandSpawned = true) }
    }

internal fun WorldMapViewModel.collectEscomItem() {
        val loc = _uiState.value.currentLocation ?: return
        val interactionRadius = 0.00015
        val itemToCollect = _escomItems.value.find {
            distance(loc, org.osmdroid.util.GeoPoint(it.latitude, it.longitude)) <= interactionRadius
        }

        if (itemToCollect != null) {
            _escomItems.update { currentList -> currentList.filter { it.id != itemToCollect.id } }
        }
    }

internal fun WorldMapViewModel.spawnDynamicCarInEscom(context: Context) {
        // 1. Cargar el JSON del navgraph de ESCOM si no está en memoria
        if (escomNavGraph == null) {
            try {
                val inputStream = context.assets.open("CONFIG/navgraphs/escom_navgraph.json")
                val reader = java.io.InputStreamReader(inputStream)
                escomNavGraph = normalizeNavGraph(Gson().fromJson(reader, ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph::class.java))
                reader.close()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_error_escom_navgraph), android.widget.Toast.LENGTH_SHORT).show()
                return
            }
        }

        val navGraph = escomNavGraph ?: return

        // 2. Buscar el edificio ESCOM en el mapa
        val escomLandmarkBase = _uiState.value.landmarks.find { it.assetPath.contains("building_escom", ignoreCase = true) }
        if (escomLandmarkBase == null) {
            android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_error_escom_missing), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // CRUCIAL: Inyectarle el navGraph al Landmark para que la IA (NpcAiManager) pueda leer las "entryWays"
        val escomLandmark = escomLandmarkBase.copy(navGraph = navGraph)

        // 3. Obtener el carril de entrada (el objeto real LocalWay)
        val entryWayId = navGraph.entryWays.firstOrNull() ?: return
        val entryWay = navGraph.ways.find { it.id == entryWayId } ?: return
        val entryNode = entryWay.nodes.firstOrNull() ?: return

        // 4. Calcular posición global real en base al nodo local 0,0
        val spawnGeoPoint = escomLandmark.toGlobalGeoPoint(entryNode.localX, entryNode.localY)

        // 5. Crear el NPC con los estados exactos que exige el motor de IA
        val newCarId = "DYN_CAR_${System.currentTimeMillis()}"
        val newCar = ovh.gabrielhuav.pow.domain.models.map.Npc(
            id = newCarId,
            type = ovh.gabrielhuav.pow.domain.models.map.NpcType.CAR,
            location = spawnGeoPoint,
            carColor = android.graphics.Color.WHITE,
            carModel = ovh.gabrielhuav.pow.domain.models.map.CarModel.SPORT,
            rotationAngle = 0f,
            speed = ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager.CAR_SPEED,

            // 👇 PROPIEDADES QUE EVITAN QUE LA IA LO ELIMINE
            navState = ovh.gabrielhuav.pow.domain.models.map.NpcNavState.MICRO_LANDMARK,
            currentLandmark = escomLandmark, // Pasamos el objeto con el navGraph
            currentLocalWay = entryWay,      // Pasamos el objeto de la calle
            targetNodeIndex = 1,             // Le decimos que avance al nodo 1
            moveDirection = 1                // Dirección hacia adelante
        )

        // 6. Inyectarlo a la FUENTE DE LA VERDAD (remoteEntities)
        remoteEntities[newCarId] = newCar

        // 7. Refrescar la pantalla
        updateNpcsState()

        android.widget.Toast.makeText(context, getLocalizedString(ovh.gabrielhuav.pow.R.string.toast_car_injected), android.widget.Toast.LENGTH_SHORT).show()
    }

