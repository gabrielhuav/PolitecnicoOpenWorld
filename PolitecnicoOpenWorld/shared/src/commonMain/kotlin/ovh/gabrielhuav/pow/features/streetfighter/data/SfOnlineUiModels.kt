package ovh.gabrielhuav.pow.features.streetfighter.data

import kotlinx.serialization.Serializable

/** Dispositivo visible/emparejado para el selector local. */
data class SfBtDevice(val name: String, val address: String)

/** Una partida LAN descubierta (IP del anfitrión + nombre a mostrar). */
data class SfLanGame(val ip: String, val name: String)

/** Resumen laxo de una sala pública recibido del servidor. */
@Serializable
data class SfRoomSummary(
    val code: String = "",
    val players: Int = 0,
    val phase: String = "",
)
