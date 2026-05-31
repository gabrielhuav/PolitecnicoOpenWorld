package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

enum class Direction { UP, DOWN, LEFT, RIGHT }
enum class GameAction { A, B, X, Y }

data class MultiplayerPlayer(
    val type: String = "PLAYER_UPDATE",
    val id: String,
    val displayName: String = "",
    val x: Double,
    val y: Double,
    val action: String,
    val facingRight: Boolean,
    val isDriving: Boolean = false,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null,
    val health: Float = 100f
)

data class MultiplayerNpc(
    val id: String,
    val x: Double,
    val y: Double,
    val rotation: Float,
    val npcType: String,
    val ownerId: String? = null,
    val carModel: String? = null,
    val carColor: Int? = null,
    val hairId: Int? = null,
    val hairColor: Int? = null,
    val shirtColor: Int? = null,
    val pantsColor: Int? = null
)

internal data class ServerMessage(
    val type: String? = null,
    val id: String? = null,
    val sessionId: String? = null,
    val x: Double? = null,
    val y: Double? = null,
    val action: String? = null,
    val facingRight: Boolean? = null,
    val displayName: String? = null,
    val isDriving: Boolean? = null,
    val carModel: String? = null,
    val carColor: Int? = null,
    val vehicleRotation: Float? = null,
    val npc: MultiplayerNpc? = null,
    val npcs: List<MultiplayerNpc>? = null,
    val npcId: String? = null,
    val orphanedNpcs: List<String>? = null,
    val activeNpcIds: List<String>? = null,
    val isZoneHost: Boolean? = null,
    val health: Float? = null,
    val targetId: String? = null,
    val damage: Float? = null,
)
