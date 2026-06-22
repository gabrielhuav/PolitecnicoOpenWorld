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

internal fun WorldMapViewModel.startMovementAction(isMovingRight: Boolean? = null) {
        idleJob?.cancel()
        val newFacingRight = isMovingRight ?: _uiState.value.isPlayerFacingRight
        val currentAction = if (_uiState.value.isRunning) PlayerAction.RUN else PlayerAction.WALK
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            if (_uiState.value.playerAction != currentAction || _uiState.value.isPlayerFacingRight != newFacingRight) {
                _uiState.update { it.copy(playerAction = currentAction, isPlayerFacingRight = newFacingRight) }
            }
        }
        if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
            idleJob = viewModelScope.launch {
                delay(150)
                if (_uiState.value.playerAction != PlayerAction.SPECIAL) {
                    _uiState.update { it.copy(playerAction = PlayerAction.IDLE) }
                }
            }
        }
    }

internal fun WorldMapViewModel.startHealthBarTimer(delayMillis: Long) {
        healthBarJob?.cancel()
        healthBarJob = viewModelScope.launch {
            delay(delayMillis)
            showHealthBar = false
        }
    }

internal fun WorldMapViewModel.triggerWastedSequence() {
        // DE-DUP (2026-06-21, par 3): sincronizado al MIEMBRO canónico de WorldMapViewModel.kt antes
        // de borrarlo. El miembro DIVERGÍA de esta extensión vieja: (a) respawn normal = TELETRANSPORTE
        // a la ESCOM (coords fijas), NO ~80 m del lugar de muerte; (b) resetea wantedLevel + carjackStartTime
        // (modo retirada de la policía). Cascada verificada SEGURA: el único call externo, clearCampaignPolice(),
        // tiene UNA sola definición (ext WorldMapCampaignPolice.kt) — sin gemelo divergente.
        viewModelScope.launch(Dispatchers.Main) {
            // Al morir te bajas del coche (no se respawnea conduciendo) y se quita el pánico de la zona.
            _uiState.update {
                it.copy(
                    showWastedScreen = true,
                    isDriving = false,
                    currentVehicleModel = null,
                    currentVehicleColor = null,
                    vehicleSpeed = 0.0
                )
            }
            // MODO HISTORIA: morir DURANTE una misión de campaña = MISIÓN FALLIDA (reinicia desde el
            // último checkpoint con "REINTENTAR MISIÓN"), NO el respawn normal — si no, el jugador podría
            // dejarse matar para SALTARSE todo el trayecto de la escolta de Prankedy.
            val missionObj = _uiState.value.currentObjective
            val inMission = inCampaign && (
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.ESCOLTAR_PRANKEDY.id ||
                missionObj?.id == ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog.INGRESAR_ESCOM.id)
            if (inMission) {
                delay(2500L)
                relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
                clearCampaignPolice()
                carjackStartTime = 0L
                playerHealth = maxPlayerHealth
                damagePulseTrigger = 0
                impactEffectTrigger = 0
                _uiState.update { it.copy(wantedLevel = 0, carjackWarning = null, isDrivingPoliceCar = false, showWastedScreen = false, showMissionFailed = true) }
                return@launch
            }
            delay(4000L)
            // Limpiar el estado de combate (rachas / NPCs implacables / cooldowns) para no revivir perseguido.
            relentlessNpcs.clear(); npcHitStreak.clear(); npcContactCooldowns.clear()
            // Al morir se pierde el nivel de búsqueda, pero la policía NO desaparece de golpe: con
            // wantedLevel = 0 entra en modo retirada (se aleja hasta despawnear).
            carjackStartTime = 0L
            _uiState.update { it.copy(wantedLevel = 0, carjackWarning = null) }
            // RESPAWN EN ESCOM: Al morir, el jugador es llevado de vuelta a la ESCOM.
            val respawn = GeoPoint(19.504603, -99.145985)
            _uiState.update { it.copy(currentLocation = respawn, showWastedScreen = false) }
            playerHealth = maxPlayerHealth
            // Reiniciar contadores de animación y activar inmunidad temporal (2 s) para que ningún
            // policía/NPC con aggro residual dispare la animación de daño justo al reaaparecer.
            damagePulseTrigger = 0
            impactEffectTrigger = 0
            respawnImmunityUntilMs = System.currentTimeMillis() + 2000L
        }
    }
