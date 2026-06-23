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
import ovh.gabrielhuav.pow.domain.models.map.ShineCTOLocation

internal fun WorldMapViewModel.trySpawningCollectible(playerLat: Double, playerLon: Double) {
        if (!_uiState.value.isRoadNetworkReady || roadNetwork.isEmpty()) return
        if (_uiState.value.activeCollectibles.isNotEmpty() || !isSpawningCollectible.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uncollected = collectibleRepository.getUncollectedCollectibles()
                if (uncollected.isNotEmpty()) {
                    val itemToSpawn = uncollected.random()
                    val bearing = Math.random() * 2 * Math.PI
                    val distanceMeters = 300.0 + Math.random() * 300.0
                    val clampedLat = playerLat.coerceIn(-85.0, 85.0)
                    val deltaLat = (distanceMeters * Math.cos(bearing)) / 111000.0
                    val deltaLon = (distanceMeters * Math.sin(bearing)) / (111000.0 * Math.cos(Math.toRadians(clampedLat)))
                    val offsetLat = playerLat + deltaLat
                    val offsetLon = playerLon + deltaLon
                    val tempLoc = org.osmdroid.util.GeoPoint(offsetLat, offsetLon)
                    val spawnNode = getNearestPointOnNetwork(tempLoc)
                    val activeItem = ActiveCollectible(
                        id = itemToSpawn.id,
                        name = itemToSpawn.name,
                        description = itemToSpawn.description,
                        assetPath = itemToSpawn.assetPath,
                        latitude = spawnNode.latitude,
                        longitude = spawnNode.longitude
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        _uiState.update { it.copy(activeCollectibles = listOf(activeItem)) }
                    }
                }
            } finally {
                isSpawningCollectible.set(false)
            }
        }
    }


internal fun WorldMapViewModel.checkCollectibleProximity(playerLat: Double, playerLon: Double) {
        val playerGeo = org.osmdroid.util.GeoPoint(playerLat, playerLon)
        val INTERACT_RADIUS_METERS = 15.0

        // 1. Verificar cercanía a estaciones del metro
        val metroStations = _uiState.value.metroStations
        val nearbyMetro = metroStations.minByOrNull {
            playerGeo.distanceToAsDouble(it.location)
        }

        // Las estaciones usan una zona MÁS GRANDE (METRO_INTERACT_RADIUS_METERS, 60 m) que
        // los coleccionables/puertas (15 m): el logo del metro a veces cae sobre un edificio
        // inaccesible por calles (Overpass) y con snap-to-road no se podría pisar; con la zona
        // amplia basta estar cerca en cualquier calle. Esta zona se dibuja en web y OSM nativo.
        if (nearbyMetro != null && playerGeo.distanceToAsDouble(nearbyMetro.location) <= METRO_INTERACT_RADIUS_METERS) {
            if (_uiState.value.nearbyMetroStation?.name != nearbyMetro.name) {
                _uiState.update { it.copy(nearbyMetroStation = nearbyMetro, nearbyCollectible = null) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_prompt_metro, nearbyMetro.name.uppercase())
                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
            return
        }

        // Si no está cerca de un metro, limpia el estado de metro
        if (_uiState.value.nearbyMetroStation != null) {
            _uiState.update { it.copy(nearbyMetroStation = null, interactionPrompt = null) }
        }

        // 1b. Verificar cercanía a estaciones del Metrobús
        val metrobusStations = _uiState.value.metrobusStations
        val nearbyMetrobus = metrobusStations.minByOrNull { playerGeo.distanceToAsDouble(it.location) }

        // Metrobús: zona propia MÁS PEQUEÑA que el metro (METROBUS_INTERACT_RADIUS_METERS).
        if (nearbyMetrobus != null && playerGeo.distanceToAsDouble(nearbyMetrobus.location) <= METROBUS_INTERACT_RADIUS_METERS) {
            if (_uiState.value.nearbyMetrobusStation?.name != nearbyMetrobus.name) {
                _uiState.update { it.copy(nearbyMetrobusStation = nearbyMetrobus, nearbyCollectible = null) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = "PRESIONA X PARA ENTRAR A ESTACIÓN METROBÚS ${nearbyMetrobus.name.uppercase()}"
                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
            return
        }

        if (_uiState.value.nearbyMetrobusStation != null) {
            _uiState.update { it.copy(nearbyMetrobusStation = null, interactionPrompt = null) }
        }

        // 2. Recopilamos los collectibles normales y de ESCOM (nuestro código)
        val baseItems = _uiState.value.activeCollectibles + _escomItems.value

        // Convertimos los Landmarks de tipo "Puerta" en collectibles virtuales interactuables
        val doorItems = _uiState.value.landmarks
            .filter { it.assetPath.contains("DOORS/") }
            .map { doorLandmark ->
                ActiveCollectible(
                    id = "escom_door_${doorLandmark.id}",
                    name = doorLandmark.name,
                    description = "Puerta interactiva",
                    assetPath = doorLandmark.assetPath,
                    latitude = doorLandmark.location.latitude,
                    longitude = doorLandmark.location.longitude
                )
            }

        // 3. Juntamos todo en un solo radar global
        val allPossibleItems = baseItems + doorItems

        val activeItem = allPossibleItems.minByOrNull {
            playerGeo.distanceToAsDouble(org.osmdroid.util.GeoPoint(it.latitude, it.longitude))
        } ?: return

        val itemGeo = org.osmdroid.util.GeoPoint(activeItem.latitude, activeItem.longitude)
        val distanceInMeters = playerGeo.distanceToAsDouble(itemGeo)

        // 4. Radio de detección especial para las puertas (20 metros) o estándar para objetos (15 metros)
        val radius = if (activeItem.id.startsWith("escom_door_")) ESCOM_DOOR_INTERACT_RADIUS * 100000 else 15.0

        if (distanceInMeters <= radius) {
            if (_uiState.value.nearbyCollectible?.id != activeItem.id) {
                _uiState.update { it.copy(nearbyCollectible = activeItem) }
                promptJob?.cancel()
                promptJob = viewModelScope.launch {
                    val promptText = when {
                        activeItem.id == "global_zombie_hand" -> if (_uiState.value.globalZombieMode) getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_deactivate_zombie) else getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_activate_zombie)
                        activeItem.name == "Objeto Misterioso ESCOM" -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_interact)
                        activeItem.id == ShineCTOLocation.MARKER_ID  -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_enter)
                        activeItem.id.startsWith("escom_door_")      -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_enter) // <--- Aquí aparece el texto de la puerta
                        else -> getLocalizedString(ovh.gabrielhuav.pow.R.string.wm_press_x_pickup)
                    }

                    _uiState.update { it.copy(interactionPrompt = promptText) }
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { it.copy(interactionPrompt = null) }
                }
            }
        } else {
            if (_uiState.value.nearbyCollectible != null) {
                promptJob?.cancel()
                promptJob = null
                _uiState.update { it.copy(nearbyCollectible = null, interactionPrompt = null) }
            }
        }
    }

