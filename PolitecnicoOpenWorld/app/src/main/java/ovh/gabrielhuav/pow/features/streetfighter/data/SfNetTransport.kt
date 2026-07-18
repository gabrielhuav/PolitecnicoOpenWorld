package ovh.gabrielhuav.pow.features.streetfighter.data

// Transporte común del MULTIJUGADOR 1v1 del modo pelea "HUELUM VS. GOYA".
// La arquitectura (relay puro + autoridad del RECEPTOR sobre su propio HP, ver
// AUDIT_SF_MULTIPLAYER.md §3) NO cambia entre transportes: viajan los MISMOS
// mensajes JSON (SfNetMsg) y el VM habla solo con esta interfaz.
// Implementaciones:
//  - SfMatchClient: WebSocket contra MultiplayerSF/ en Render (online).
//  - SfBtClient:    BluetoothSocket RFCOMM dispositivo-a-dispositivo (sin internet);
//                   el HOST genera localmente los mensajes que en online manda el relay.
// Los métodos de SALAS/cola/lobby solo aplican online: en Bluetooth son no-op.

interface SfNetTransport {

    /** Callbacks de red. Llegan en el hilo del transporte (OkHttp/Thread BT): el VM decide el hilo. */
    interface Listener {
        fun onOpen()
        fun onMessage(msg: SfNetMsg)
        fun onClosed()
        fun onFailure(reason: String)
    }

    // ── Salas / cola pública / lobby con aprobación (solo online; no-op en BT) ──
    fun createRoom()
    fun joinRoom(code: String)
    fun quickMatch()
    fun cancelQueue()
    fun listRooms()
    fun leaveRoom()
    fun requestJoin(code: String)
    fun respondJoin(accept: Boolean)

    // ── Flujo de pelea (idéntico en ambos transportes) ──
    fun selectCharacter(name: String)
    fun selectMap(file: String)
    fun requestRematch()
    /** 🆕 Fin de RONDA intermedia (el combate sigue); MATCH_ENDED = combate decidido (2 rondas). */
    fun sendRoundEnded(winner: String)
    fun sendMatchEnded(winner: String)
    fun sendDamage(damage: Int, strength: String, atkType: String)
    /** `timer` = 🆕 sincronía del reloj de la ronda: solo lo manda el HOST (null en el invitado). */
    fun sendPlayerState(x: Float, y: Float, state: String, frame: Int, dir: Int, hp: Int, timer: Int?, fireballs: List<SfNetFireball>)

    fun close()
}
