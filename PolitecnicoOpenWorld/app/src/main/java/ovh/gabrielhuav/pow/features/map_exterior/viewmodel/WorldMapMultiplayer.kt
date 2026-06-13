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
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.InteriorBuilding
import ovh.gabrielhuav.pow.domain.models.MapWay
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.data.local.room.entity.LandmarkEntity
import ovh.gabrielhuav.pow.domain.models.Landmark
import ovh.gabrielhuav.pow.domain.models.LandmarkCatalogManager
import ovh.gabrielhuav.pow.domain.models.LandmarkAssetTemplate
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
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ovh.gabrielhuav.pow.domain.models.ShineCTOLocation

internal fun WorldMapViewModel.handleMultiplayerMessage(messageJson: String) {
        try {
            val msg = gson.fromJson(messageJson, ServerMessage::class.java)

            when (msg.type) {
                "SESSION_INIT" -> {
                    msg.sessionId?.let { myPlayerUUID = it }
                }

                "SYNC_ALL_NPCS" -> {
                    msg.npcs?.forEach { remoteNpc ->
                        if (remoteNpc.ownerId != myPlayerUUID) {
                            addRemoteEntity(remoteNpc)
                        }
                    }
                    updateNpcsState()
                }

                "ROLE_UPDATE" -> {
                    msg.isZoneHost?.let {
                        isServerDelegatedHost = it
                        Log.d("Multiplayer", "Mi rol en esta zona ahora es Host: $it")
                    }
                }

                "NPC_SPAWN", "NPC_UPDATE" -> {
                    msg.npc?.let {
                        if (it.ownerId != myPlayerUUID) {
                            addRemoteEntity(it)
                            updateNpcsState()
                        }
                    }
                }

                "NPC_BATCH_UPDATE" -> {
                    msg.npcs?.forEach { remoteNpc ->
                        if (remoteNpc.ownerId != myPlayerUUID) {
                            addRemoteEntity(remoteNpc)
                        }
                    }
                    updateNpcsState()
                }

                "NPC_DESTROY" -> {
                    msg.npcId?.let {
                        remoteEntities.remove(it)
                        updateNpcsState()
                    }
                }

                // ─── POLICÍA REMOTA (de otro jugador): solo render ───────────────
                "POLICE_BATCH_UPDATE" -> {
                    val now = System.currentTimeMillis()
                    msg.npcs?.forEach { p ->
                        if (p.ownerId != myPlayerUUID) {
                            val type = try { NpcType.valueOf(p.npcType) } catch (e: Exception) { NpcType.POLICE_COP }
                            remotePolice[p.id] = Npc(
                                id = p.id,
                                type = type,
                                location = GeoPoint(p.y, p.x),
                                rotationAngle = p.rotation,
                                speed = 0.0,
                                isRemote = true,
                                isMoving = true,
                                facingRight = cos(Math.toRadians(p.rotation.toDouble())) >= 0,
                                ownerId = p.ownerId,
                                policeDisembarked = type == NpcType.POLICE_COP
                            )
                            remotePoliceSeen[p.id] = now
                        }
                    }
                    updateNpcsState()
                }

                "POLICE_DESTROY" -> {
                    msg.npcId?.let {
                        remotePolice.remove(it)
                        remotePoliceSeen.remove(it)
                        updateNpcsState()
                    }
                }

                "DISCONNECT" -> {
                    msg.id?.let { remoteEntities.remove(it) }
                    msg.orphanedNpcs?.forEach { remoteEntities.remove(it) }
                    updateNpcsState()
                }

                "MASTER_SYNC_CHECK" -> {
                    msg.activeNpcIds?.let { officialIds ->
                        val officialSet = officialIds.toSet()
                        var stateChanged = false
                        val iterator = remoteEntities.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            // Solo limpiamos NPCs que vinieron del servidor (isRemote = true).
                            // Los NPCs que ESTE cliente spawnea localmente como host (isRemote = false)
                            // todavía pueden no estar en activeNpcIds por la latencia de propagación;
                            // borrarlos aquí los hacía parpadear/desaparecer. El host es su autoridad
                            // y los limpia por la vía de pendingDespawns/NPC_DESTROY, no por este sync.
                            if (entry.value.displayName.isNullOrEmpty() && entry.value.isRemote) {
                                if (!officialSet.contains(entry.key)) {
                                    iterator.remove()
                                    stateChanged = true
                                }
                            }
                        }
                        if (stateChanged) updateNpcsState()
                    }
                }

                "PLAYER_DAMAGE" -> {
                    val incomingDamage = msg.damage
                    if (msg.targetId == myPlayerUUID && incomingDamage != null) {
                        // handleMultiplayerMessage corre en Dispatchers.IO, pero takeDamage muta
                        // estado Compose (playerHealth, showHealthBar, damagePulseTrigger). Lo
                        // enrutamos al hilo Main para evitar carreras con la secuencia de muerte
                        // (triggerWastedSequence ya corre en Main).
                        viewModelScope.launch(Dispatchers.Main) { takeDamage(incomingDamage) }
                    }
                    // MIEDO AL COMBATE: si soy el host de la zona, los civiles cercanos al
                    // objetivo del golpe se dispersan. La posición del objetivo es la mía si el
                    // golpe es contra mí, o la del jugador remoto golpeado si la conozco.
                    if (isServerDelegatedHost) {
                        val targetLoc = if (msg.targetId == myPlayerUUID) {
                            _uiState.value.currentLocation
                        } else {
                            remoteEntities[msg.targetId]?.location
                        }
                        targetLoc?.let { npcAiManager.triggerFear(it.latitude, it.longitude) }
                    }
                }

                else -> {
                    if (msg.id != null && msg.id != myPlayerUUID && msg.x != null && msg.y != null) {

                        val isRemoteMoving = msg.action == "WALK" || msg.action == "RUN"
                        val isRemoteDriving = msg.isDriving == true

                        val multiplayerConfig = ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
                            bodyFolder = "otherPlayer",
                            bodyPrefix = "p_mult_",
                            hairId = 1,
                            hairColor = androidx.compose.ui.graphics.Color.White,
                            shirtColor = androidx.compose.ui.graphics.Color.Cyan,
                            pantsColor = androidx.compose.ui.graphics.Color.DarkGray
                        )

                        val remoteCarModel = try {
                            msg.carModel?.let { ovh.gabrielhuav.pow.domain.models.CarModel.valueOf(it) }
                                ?: ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
                        } catch(e: Exception) {
                            ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
                        }

                        val otherPlayer = Npc(
                            id = msg.id,
                            type = if (isRemoteDriving) NpcType.CAR else NpcType.PERSON,
                            location = GeoPoint(msg.y, msg.x),
                            rotationAngle = if (isRemoteDriving) ((msg.vehicleRotation ?: 0f) + 270f) % 360f else 0f,
                            speed = 0.0,
                            isRemote = true,
                            isMoving = isRemoteMoving || isRemoteDriving,
                            facingRight = msg.facingRight == true,
                            carModel = remoteCarModel,
                            carColor = msg.carColor ?: 0xFFFFFFFF.toInt(),
                            visualConfig = if (!isRemoteDriving) multiplayerConfig else null,
                            displayName = msg.displayName,
                            health = msg.health ?: 100f,
                            isDying = (msg.health ?: 100f) <= 0f
                        )
                        remoteEntities[msg.id] = otherPlayer
                        updateNpcsState()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorldMapVM", "Error procesando JSON: ${e.message}")
        }
    }

internal fun WorldMapViewModel.addRemoteEntity(remote: MultiplayerNpc) {
        // Anti-duplicación: coche recién abordado por MÍ → ignorar reinserciones remotas
        // (paridad con el miembro privado de WorldMapViewModel, que es el activo).
        if (isCarTombstoned(remote.id)) return
        val npcType = try { NpcType.valueOf(remote.npcType) } catch(e: Exception) { NpcType.PERSON }

        val cModel = try {
            remote.carModel?.let { ovh.gabrielhuav.pow.domain.models.CarModel.valueOf(it) }
                ?: ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN
        } catch (e: Exception) { ovh.gabrielhuav.pow.domain.models.CarModel.SEDAN }
        val cColor = remote.carColor ?: 0xFFFFFFFF.toInt()

        val visualConfig = if (npcType == NpcType.PERSON) {
            ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig(
                bodyFolder = "npc_walk_1",
                bodyPrefix = "npc_walk_1_",
                hairId = remote.hairId ?: 1,
                hairColor  = remote.hairColor?.let  { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.White,
                shirtColor = remote.shirtColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.LightGray,
                pantsColor = remote.pantsColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.DarkGray
            )
        } else null

        val isMoving = npcType == NpcType.PERSON
        val facingRight = cos(Math.toRadians(remote.rotation.toDouble())) >= 0

        val restoredSpeed = if (npcType == NpcType.CAR) NpcAiManager.CAR_SPEED else NpcAiManager.PERSON_SPEED

        remoteEntities[remote.id] = Npc(
            id = remote.id,
            type = npcType,
            location = GeoPoint(remote.y, remote.x),
            rotationAngle = remote.rotation,
            speed = restoredSpeed,
            isRemote = true,
            isMoving = isMoving,
            facingRight = facingRight,
            ownerId = remote.ownerId,
            carModel = cModel,
            carColor = cColor,
            visualConfig = visualConfig,
            displayName = null
        )
    }

internal fun WorldMapViewModel.updateNpcsState() {
        // Civiles/jugadores remotos + policía propia (simulada) + policía remota (solo render).
        val combined = remoteEntities.values + policeManager.activeUnits() + remotePolice.values
        _uiState.update { it.copy(npcs = combined.toList()) }
    }
