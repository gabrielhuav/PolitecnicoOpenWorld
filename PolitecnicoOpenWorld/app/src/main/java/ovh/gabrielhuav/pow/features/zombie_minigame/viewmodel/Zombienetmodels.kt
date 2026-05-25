package ovh.gabrielhuav.pow.features.zombie_minigame.viewmodel

import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction

/**
 * Representa a otro jugador conectado a la MISMA sala del minijuego de zombis.
 * Las coordenadas (x, y) son píxeles de mundo relativos a la ZombieRoom actual;
 * como el servidor solo nos envía jugadores de nuestra misma sala, son
 * directamente comparables con las nuestras sin normalizar.
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
 * Mensaje genérico del servidor de zombis (Gson lo deserializa de forma laxa:
 * los campos ausentes quedan null). Separado del protocolo del open world para
 * no mezclar campos de NPCs/vehículos que aquí no aplican.
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
    val players: List<ZombieServerMessage>? = null
)