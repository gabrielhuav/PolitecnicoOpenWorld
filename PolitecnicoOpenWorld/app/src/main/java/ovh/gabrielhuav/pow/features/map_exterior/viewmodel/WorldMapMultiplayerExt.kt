package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.data.network.WebSocketManager
import ovh.gabrielhuav.pow.domain.models.CharacterVisualConfig
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.domain.models.Npc
import ovh.gabrielhuav.pow.domain.models.NpcType
import ovh.gabrielhuav.pow.domain.models.ai.NpcAiManager
import kotlin.math.cos

// ─── WEBSOCKET MULTIJUGADOR ───────────────────────────────────────────────────

fun WorldMapViewModel.connectToMultiplayer(serverUrl: String, playerName: String) {
    myPlayerDisplayName = playerName
    _uiState.update { it.copy(isMultiplayer = true, playerName = playerName) }
    if (webSocketManager == null) {
        Log.d("WorldMapVM", "Iniciando conexión multijugador a $serverUrl")
        webSocketManager = WebSocketManager(serverUrl)
        messagesCollectorJob?.cancel()
        messagesCollectorJob = viewModelScope.launch(Dispatchers.IO) {
            webSocketManager?.messagesFlow?.collect { messageJson ->
                handleMultiplayerMessage(messageJson)
            }
        }
    }
    if (webSocketManager?.isConnected() == false) {
        webSocketManager?.connect()
    }
}

fun WorldMapViewModel.disconnectFromMultiplayer() {
    _uiState.update { it.copy(isMultiplayer = false, playerName = "") }
    webSocketManager?.disconnect()
    webSocketManager = null
    messagesCollectorJob?.cancel()
    messagesCollectorJob = null
    remoteEntities.clear()
    updateNpcsState()
}

internal fun WorldMapViewModel.updateNpcsState() {
    _uiState.update { it.copy(npcs = remoteEntities.values.toList()) }
}

internal fun WorldMapViewModel.handleMultiplayerMessage(messageJson: String) {
    try {
        val msg = gson.fromJson(messageJson, ServerMessage::class.java)

        when (msg.type) {
            "SESSION_INIT" -> {
                msg.sessionId?.let { myPlayerUUID = it }
            }

            "SYNC_ALL_NPCS" -> {
                msg.npcs?.forEach { remoteNpc ->
                    if (remoteNpc.ownerId != myPlayerUUID) addRemoteEntity(remoteNpc)
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
                    if (remoteNpc.ownerId != myPlayerUUID) addRemoteEntity(remoteNpc)
                }
                updateNpcsState()
            }

            "NPC_DESTROY" -> {
                msg.npcId?.let { remoteEntities.remove(it); updateNpcsState() }
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
                        if (entry.value.displayName.isNullOrEmpty()) {
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
                if (msg.targetId == myPlayerUUID && msg.damage != null) {
                    takeDamage(msg.damage)
                }
            }

            else -> {
                if (msg.id != null && msg.id != myPlayerUUID && msg.x != null && msg.y != null) {
                    val isRemoteMoving  = msg.action == "WALK" || msg.action == "RUN"
                    val isRemoteDriving = msg.isDriving == true

                    val multiplayerConfig = CharacterVisualConfig(
                        bodyFolder  = "otherPlayer",
                        bodyPrefix  = "p_mult_",
                        hairId      = 1,
                        hairColor   = androidx.compose.ui.graphics.Color.White,
                        shirtColor  = androidx.compose.ui.graphics.Color.Cyan,
                        pantsColor  = androidx.compose.ui.graphics.Color.DarkGray
                    )

                    val remoteCarModel = try {
                        msg.carModel?.let { CarModel.valueOf(it) } ?: CarModel.SEDAN
                    } catch (e: Exception) { CarModel.SEDAN }

                    val otherPlayer = Npc(
                        id             = msg.id,
                        type           = if (isRemoteDriving) NpcType.CAR else NpcType.PERSON,
                        location       = GeoPoint(msg.y, msg.x),
                        rotationAngle  = if (isRemoteDriving) ((msg.vehicleRotation ?: 0f) + 270f) % 360f else 0f,
                        speed          = 0.0,
                        isRemote       = true,
                        isMoving       = isRemoteMoving || isRemoteDriving,
                        facingRight    = msg.facingRight == true,
                        carModel       = remoteCarModel,
                        carColor       = msg.carColor ?: 0xFFFFFFFF.toInt(),
                        visualConfig   = if (!isRemoteDriving) multiplayerConfig else null,
                        displayName    = msg.displayName,
                        health         = msg.health ?: 100f,
                        isDying        = (msg.health ?: 100f) <= 0f
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
    val npcType = try { NpcType.valueOf(remote.npcType) } catch (e: Exception) { NpcType.PERSON }

    val cModel = try {
        remote.carModel?.let { CarModel.valueOf(it) } ?: CarModel.SEDAN
    } catch (e: Exception) { CarModel.SEDAN }
    val cColor = remote.carColor ?: 0xFFFFFFFF.toInt()

    val visualConfig = if (npcType == NpcType.PERSON) {
        CharacterVisualConfig(
            bodyFolder  = "npc_walk_1",
            bodyPrefix  = "npc_walk_1_",
            hairId      = remote.hairId ?: 1,
            hairColor   = remote.hairColor?.let  { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.White,
            shirtColor  = remote.shirtColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.LightGray,
            pantsColor  = remote.pantsColor?.let { androidx.compose.ui.graphics.Color(it) } ?: androidx.compose.ui.graphics.Color.DarkGray
        )
    } else null

    val isMoving    = npcType == NpcType.PERSON
    val facingRight = cos(Math.toRadians(remote.rotation.toDouble())) >= 0
    val restoredSpeed = if (npcType == NpcType.CAR) NpcAiManager.CAR_SPEED else NpcAiManager.PERSON_SPEED

    remoteEntities[remote.id] = Npc(
        id            = remote.id,
        type          = npcType,
        location      = GeoPoint(remote.y, remote.x),
        rotationAngle = remote.rotation,
        speed         = restoredSpeed,
        isRemote      = true,
        isMoving      = isMoving,
        facingRight   = facingRight,
        ownerId       = remote.ownerId,
        carModel      = cModel,
        carColor      = cColor,
        visualConfig  = visualConfig,
        displayName   = null
    )
}
