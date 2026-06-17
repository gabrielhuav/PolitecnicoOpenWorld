package ovh.gabrielhuav.pow.data.auth

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Encapsula el login con Google + Firebase Authentication.
 *
 * Diseño DEFENSIVO: si Firebase aún no está configurado (falta google-services.json,
 * o FirebaseApp no se inicializó), los métodos NO crashean — devuelven null/false y
 * el juego sigue funcionando en modo local. Así la app no se rompe antes de que se
 * agregue el json.
 *
 * Flujo:
 *  1. [signInIntent] abre el selector de cuentas de Google (Activity Result).
 *  2. [handleSignInResult] toma el resultado, autentica en Firebase y publica el UID
 *     + ID token en [AuthSession] (que lee el WebSocket para el handshake).
 *  3. [refreshToken] renueva el token (los ID token caducan ~1 h).
 *  4. [signOut] / [deleteAccount] para la pantalla de Cuenta en Ajustes.
 */
class AuthManager(context: Context) {

    private val appContext = context.applicationContext

    private val firebaseAuth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (e: Exception) {
        Log.w(TAG, "FirebaseAuth no disponible (¿falta google-services.json?): ${e.message}")
        null
    }

    // Web client ID generado por el plugin google-services en strings (default_web_client_id).
    // Se lee DINÁMICAMENTE para que el código compile aunque el recurso aún no exista.
    private val webClientId: String = run {
        val id = appContext.resources.getIdentifier("default_web_client_id", "string", appContext.packageName)
        if (id != 0) appContext.getString(id) else ""
    }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
        if (webClientId.isNotBlank()) gsoBuilder.requestIdToken(webClientId)
        GoogleSignIn.getClient(appContext, gsoBuilder.build())
    }

    /** ¿Firebase está CONFIGURADO en este build? (false si falta google-services.json / no se inicializó). */
    fun isAvailable(): Boolean = firebaseAuth != null

    /** ¿La sesión de Firebase está activa ahora mismo? */
    fun isSignedIn(): Boolean = firebaseAuth?.currentUser != null

    fun currentEmail(): String? = firebaseAuth?.currentUser?.email
    fun currentDisplayName(): String? = firebaseAuth?.currentUser?.displayName
    fun currentUid(): String? = firebaseAuth?.currentUser?.uid

    /** Si ya había sesión (app reabierta), repuebla [AuthSession] y refresca el token. */
    fun restoreSession() {
        val user = firebaseAuth?.currentUser ?: return
        AuthSession.set(user.uid, null, user.email, user.displayName)
        refreshToken { }
    }

    /** Intent para lanzar el selector de cuentas de Google (úsalo con un ActivityResultLauncher). */
    fun signInIntent(): Intent = googleSignInClient.signInIntent

    /**
     * Procesa el resultado del selector de Google: obtiene el ID token de Google, lo
     * canjea por una sesión de Firebase y publica UID + token en [AuthSession].
     * [onResult] se invoca con (éxito, mensajeDeError|null).
     */
    fun handleSignInResult(data: Intent?, onResult: (Boolean, String?) -> Unit) {
        val auth = firebaseAuth
        if (auth == null) {
            onResult(false, "Firebase no está configurado")
            return
        }
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val googleIdToken = account?.idToken
            if (googleIdToken.isNullOrBlank()) {
                onResult(false, "No se obtuvo el token de Google (revisa el web client ID)")
                return
            }
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        onResult(false, "No se pudo iniciar sesión")
                        return@addOnSuccessListener
                    }
                    AuthSession.set(user.uid, null, user.email, user.displayName)
                    // Trae el ID token para el handshake del WebSocket.
                    user.getIdToken(false)
                        .addOnSuccessListener { tok ->
                            AuthSession.idToken = tok.token
                            onResult(true, null)
                        }
                        .addOnFailureListener {
                            // La sesión existe aunque el token aún no; igual reportamos éxito.
                            onResult(true, null)
                        }
                }
                .addOnFailureListener { e ->
                    onResult(false, e.message ?: "Error de autenticación")
                }
        } catch (e: ApiException) {
            Log.w(TAG, "Google Sign-In falló: code=${e.statusCode}")
            // 12501 = SIGN_IN_CANCELLED, 16 = CANCELED: el usuario cerró el selector a propósito;
            // no mostramos error (mensaje null = sin Toast). Otros códigos sí son fallo real.
            if (e.statusCode == 12501 || e.statusCode == 16) {
                onResult(false, null)
            } else {
                onResult(false, "Inicio de sesión fallido (${e.statusCode})")
            }
        } catch (e: Exception) {
            onResult(false, e.message ?: "Error desconocido")
        }
    }

    /** Renueva el ID token (caduca ~1 h) y lo guarda en [AuthSession]. */
    fun refreshToken(onDone: (String?) -> Unit) {
        val user = firebaseAuth?.currentUser
        if (user == null) { onDone(null); return }
        user.getIdToken(true)
            .addOnSuccessListener { AuthSession.idToken = it.token; onDone(it.token) }
            .addOnFailureListener { onDone(null) }
    }

    /** Cierra sesión en Firebase y en Google, y limpia [AuthSession]. */
    fun signOut(onDone: () -> Unit) {
        firebaseAuth?.signOut()
        try {
            googleSignInClient.signOut().addOnCompleteListener { AuthSession.clear(); onDone() }
        } catch (e: Exception) {
            AuthSession.clear(); onDone()
        }
    }

    /**
     * Elimina la cuenta del usuario en Firebase (revoca el acceso) y limpia la sesión.
     * El borrado de datos del jugador en el backend lo hace el llamador (este método
     * solo borra la identidad). [onResult] = (éxito, mensajeDeError|null).
     */
    fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
        val user = firebaseAuth?.currentUser
        if (user == null) { onResult(false, "No hay sesión activa"); return }
        user.delete()
            .addOnSuccessListener {
                try { googleSignInClient.signOut() } catch (_: Exception) {}
                AuthSession.clear()
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                // delete() puede exigir re-login reciente (FirebaseAuthRecentLoginRequiredException).
                onResult(false, e.message ?: "No se pudo eliminar la cuenta")
            }
    }

    companion object { private const val TAG = "AuthManager" }
}
