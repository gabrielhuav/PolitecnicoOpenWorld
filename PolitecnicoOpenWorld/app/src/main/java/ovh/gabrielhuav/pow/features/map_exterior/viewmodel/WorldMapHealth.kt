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
// Salud del jugador (daño/curación/barra de vida) extraída de WorldMapViewModel.kt.
// El ESTADO (playerHealth, showHealthBar, healthBarJob, respawnImmunityUntilMs…) sigue
// en el ViewModel; estas extensiones solo lo leen/actualizan. fireImpactEffect /
// startHealthBarTimer / triggerWastedSequence se resuelven a sus extensiones/miembros.
// ─────────────────────────────────────────────────────────────────────────────

internal fun WorldMapViewModel.takeDamage(amount: Float) {
        // Inmunidad post-respawn / post-teletransporte: ignorar el daño durante los primeros
        // segundos tras reaaparecer para que ningún policía/NPC con aggro residual dispare
        // la animación de golpe de forma inesperada.
        if (System.currentTimeMillis() < respawnImmunityUntilMs) return
        // Si YA estás muerto o en la pantalla WASTED, ignora el daño: si no, los zombis cercanos
        // al cuerpo seguían llamando takeDamage durante el WASTED y el 💥 aparecía "a cada rato"
        // al morir (y re-disparaban la secuencia).
        if (_uiState.value.showWastedScreen || playerHealth <= 0f) return
        playerHealth = (playerHealth - amount).coerceAtLeast(0f)
        damagePulseTrigger++
        if (playerHealth > 0f) fireImpactEffect() // 💥 solo si SOBREVIVES (no en el golpe mortal)
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
        if (playerHealth <= 0f) {
            triggerWastedSequence()
        }
        // Notificar a Prankedy para que active su búsqueda de agresor
        if (prankedyManager.phase == ovh.gabrielhuav.pow.domain.models.ai.PrankedyPhase.HIRED) {
            prankedyManager.onPlayerDamaged()
        }
    }

internal fun WorldMapViewModel.heal(amount: Float) {
        playerHealth = (playerHealth + amount).coerceAtMost(maxPlayerHealth)
        showHealthBar = true
        if (playerHealth > 30f) {
            startHealthBarTimer(3000L)
        } else {
            healthBarJob?.cancel()
        }
    }

internal fun WorldMapViewModel.showInitialHealthBar() {
        showHealthBar = true
        startHealthBarTimer(4000L)
    }

