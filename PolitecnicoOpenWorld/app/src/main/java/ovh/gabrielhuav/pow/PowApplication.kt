package ovh.gabrielhuav.pow

import android.app.Application
import android.util.Log

/**
 * Application de POW. Punto único de inicialización temprana del proceso.
 *
 * Inicializa Firebase de forma explícita y ROBUSTA: si falta `google-services.json`
 * (o la configuración es inválida) NO crashea — solo registra una advertencia, y el
 * juego sigue funcionando en modo local. El plugin google-services también auto-inicializa
 * Firebase vía su ContentProvider cuando el json está presente; esta llamada es un respaldo
 * idempotente que garantiza el init antes de que cualquier pantalla use AuthManager.
 */
class PowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.w("PowApplication", "Firebase no inicializado (¿falta google-services.json?): ${e.message}")
        }
    }
}
