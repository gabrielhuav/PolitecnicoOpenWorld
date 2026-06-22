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

internal fun WorldMapViewModel.spawnEscomDoors() {
        // Las coordenadas exactas se ajustan con el Modo Diseñador.
        // Estos valores son placeholders; reemplázalos con los guardados en Room DB
        // una vez hayas colocado las puertas con el Diseñador.
        val doors = listOf(
            ActiveCollectible(
                id          = "escom_door_norte",
                name        = "Puerta Norte ESCOM",
                description = "door",
                assetPath   = ESCOM_DOOR_ASSET,
                latitude    = 19.50490,
                longitude   = -99.14674
            ),
            ActiveCollectible(
                id          = "escom_door_sur",
                name        = "Puerta Sur ESCOM",
                description = "door",
                assetPath   = ESCOM_DOOR_ASSET,
                latitude    = 19.50420,
                longitude   = -99.14674
            )
        )
        _escomItems.value = doors
        _uiState.update { it.copy(isZombieHandSpawned = true) }
    }


internal fun WorldMapViewModel.isInsideEscom(lat: Double, lon: Double): Boolean {
        return abs(lat - ESCOM_BASE_LAT) < ESCOM_OFFSET &&
                abs(lon - ESCOM_BASE_LON) < ESCOM_OFFSET
    }


internal fun WorldMapViewModel.spawnOustedDriver(carLocation: GeoPoint) {
        // DE-DUP (2026-06-21): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes de
        // eliminarlo. El miembro divergía: el conductor aparece más cerca (+0.00002 vs +0.00005) y
        // REACCIONA según personalidad (trait/fear/aggro/llamar a la policía); la extensión vieja no.
        // El conductor desalojado aparece JUNTO al coche (~2 m), como si se bajara por la puerta.
        val offsetLoc = GeoPoint(carLocation.latitude + 0.00002, carLocation.longitude + 0.00002)
        val randomHairId = (1..5).random()
        val randomHairColor = listOf(
            androidx.compose.ui.graphics.Color.Black,
            androidx.compose.ui.graphics.Color.DarkGray,
            androidx.compose.ui.graphics.Color(0xFF8B4513),
            androidx.compose.ui.graphics.Color(0xFFDAA520)
        ).random()
        val randomShirtColor = listOf(
            androidx.compose.ui.graphics.Color.White,
            androidx.compose.ui.graphics.Color.Red,
            androidx.compose.ui.graphics.Color.Blue,
            androidx.compose.ui.graphics.Color.Green
        ).random()
        val visualConfig = ovh.gabrielhuav.pow.domain.models.map.CharacterVisualConfig(
            bodyFolder = "npc_walk_1",
            bodyPrefix = "npc_walk_1_",
            hairId = randomHairId,
            hairColor = randomHairColor,
            shirtColor = randomShirtColor,
            pantsColor = androidx.compose.ui.graphics.Color.DarkGray
        )
        // REACCIÓN AL ROBO según personalidad: el cobarde huye (estado de miedo), el
        // agresivo te embiste (estado aggro) y el pasivo simplemente se aleja andando.
        val trait = NpcAiManager.rollTrait()
        val now = System.currentTimeMillis()
        val driver = Npc(
            id = UUID.randomUUID().toString(),
            type = NpcType.PERSON,
            location = offsetLoc,
            speed = NpcAiManager.PERSON_SPEED,
            isMoving = true,
            visualConfig = visualConfig,
            trait = trait,
            fearUntil = if (trait == ovh.gabrielhuav.pow.domain.models.map.NpcTrait.COWARD) now + NpcAiManager.FEAR_DURATION_MS else 0L,
            fearFromLat = carLocation.latitude,
            fearFromLon = carLocation.longitude,
            aggroUntil = if (trait == ovh.gabrielhuav.pow.domain.models.map.NpcTrait.AGGRESSIVE) now + NpcAiManager.AGGRO_DURATION_MS else 0L,
            // Llama a la policía unos segundos (muestra 📞 sobre su cabeza).
            callingUntil = now + 4000L
        )
        remoteEntities[driver.id] = driver
    }

