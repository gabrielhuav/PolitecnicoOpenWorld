package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction

/**
 * Representa a otro jugador conectado a la MISMA sala del minijuego de zombis.
 * Las coordenadas (x, y) se guardan en PÍXELES de mundo relativos a la
 * ZombieRoom actual (ya convertidas desde la fracción que envía el servidor),
 * para que el render no cambie respecto a la versión anterior.
 */
data class RemoteZombiePlayer(
    val id: String,
    val displayName: String,
    val x: Float,
    val y: Float,
    val action: PlayerAction,
    val facingRight: Boolean,
    val health: Float
)

/**
 * Zombi tal como lo envía el servidor (posición FRACCIONARIA [0,1]).
 */
data class NetZombie(
    val id: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val health: Float = 100f,
    val maxHealth: Float = 100f,
    val facingRight: Boolean = true,
    val frameIndex: Int = 0,
    val isDying: Boolean = false,
    val isLootCarrier: Boolean = false
)

/**
 * Item en el suelo tal como lo envía el servidor (posición FRACCIONARIA [0,1]).
 */
data class NetItem(
    val id: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val effect: String = "CURA_TOTAL"
)

/**
 * NPC civil dentro de un interior, tal como lo envía el servidor (autoritativo, pos
 * FRACCIONARIA [0,1]). Los civiles deambulan y huyen de los zombis; si los atrapan, se
 * convierten en zombi (apocalipsis se propaga).
 */
data class NetInteriorNpc(
    val id: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val facingRight: Boolean = true,
    val frameIndex: Int = 0
)

/**
 * Mensaje genérico del servidor de zombis (Gson lo deserializa de forma laxa:
 * los campos ausentes quedan null). Separado del protocolo del open world para
 * no mezclar campos de NPCs/vehículos que aquí no aplican.
 *
 * Coordenadas de jugador (x,y) son FRACCIONARIAS [0,1] en el cable.
 *
 * Campos añadidos en Fase 1:
 *  - zombies / items / totalZombies : estado autoritativo de la sala (ZOMBIE_STATE)
 *  - effect                         : efecto concedido al recoger item (ITEM_GRANTED)
 *  - cleared                        : edificio despejado (ROOM_CLEARED)
 */
data class ZombieServerMessage(
    val type: String? = null,
    val sessionId: String? = null,
    val id: String? = null,
    val displayName: String? = null,
    val roomId: String? = null,
    val zone: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val action: String? = null,
    val facingRight: Boolean? = null,
    val health: Float? = null,
    val players: List<ZombieServerMessage>? = null,
    // ZOMBIE_STATE
    val zombies: List<NetZombie>? = null,
    val items: List<NetItem>? = null,
    val npcs: List<NetInteriorNpc>? = null,
    val totalZombies: Int? = null,
    // ITEM_GRANTED
    val effect: String? = null,
    val zombieId: String? = null,
    val cleared: Boolean? = null
)