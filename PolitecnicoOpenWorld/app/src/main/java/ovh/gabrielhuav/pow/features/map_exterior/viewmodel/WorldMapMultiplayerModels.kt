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
    val pantsColor: Int? = null,
    // Estado de combate replicado para que TODOS los clientes vean la barra de vida del
    // NPC y su muerte (atropello, golpes). El servidor lo reenvía tal cual (spread).
    val health: Float? = null,
    val isDying: Boolean? = null,
    // Estado de embestida (aggro) replicado: así cualquier cliente sabe que este NPC está
    // atacando y le aplica daño por contacto a SU jugador (no solo el host de zona).
    val aggroUntil: Long? = null,
    // ─── Rol de zombi (apocalipsis) ───────────────────────────────────────────
    // El maxHealth se DERIVA del rol en el cliente remoto (no se envía) para ahorrar bytes.
    val zombieRole: String? = null,
    val screamUntil: Long? = null
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
    // ─── Modo Zombi Global (apocalipsis en el mapa) ───────────────────────────
    // OBSOLETO: el apocalipsis YA NO es un flag global difundido (ZOMBIE_MODE_SET fue
    // eliminado). Ahora es la INSTANCIA del mundo (JOIN_INSTANCE / SYNC_ALL_NPCS); ver
    // WorldMapViewModel.setZombieInstance y 09 §0. Este campo quedó sin lectores (vestigial).
    val active: Boolean? = null,
)
