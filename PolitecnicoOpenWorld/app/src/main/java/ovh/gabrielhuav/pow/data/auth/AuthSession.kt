package ovh.gabrielhuav.pow.data.auth

/**
 * Sesión de autenticación COMPARTIDA (singleton de proceso).
 *
 * Guarda el UID de Firebase y el último ID token del usuario logueado. Lo escribe
 * [AuthManager] tras el login/refresh y lo lee [WebSocketManager] para adjuntar el
 * token en el handshake del WebSocket (cabecera Authorization) sin tener que pasar
 * el token por todas las firmas de conexión.
 *
 * Si el usuario NO ha iniciado sesión, ambos campos son null y las conexiones se
 * hacen sin cabecera (modo anónimo / juego local). Los servidores en modo "suave"
 * lo aceptan; en modo estricto (AUTH_REQUIRED) lo rechazan.
 */
object AuthSession {
    @Volatile var uid: String? = null
    @Volatile var idToken: String? = null
    @Volatile var email: String? = null
    @Volatile var displayName: String? = null

    fun isSignedIn(): Boolean = uid != null

    fun set(uid: String?, idToken: String?, email: String?, displayName: String?) {
        this.uid = uid
        this.idToken = idToken
        this.email = email
        this.displayName = displayName
    }

    fun clear() {
        uid = null
        idToken = null
        email = null
        displayName = null
    }
}
