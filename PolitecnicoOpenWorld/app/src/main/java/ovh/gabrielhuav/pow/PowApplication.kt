package ovh.gabrielhuav.pow

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import ovh.gabrielhuav.pow.platform.assets.AssetsDeAndroid
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.audio.AudioDeAndroid
import ovh.gabrielhuav.pow.platform.audio.PowAudio

/**
 * Application de POW. Punto único de inicialización temprana del proceso.
 *
 * Inicializa Firebase de forma explícita y ROBUSTA: si falta `google-services.json`
 * (o la configuración es inválida) NO crashea — solo registra una advertencia, y el
 * juego sigue funcionando en modo local. El plugin google-services también auto-inicializa
 * Firebase vía su ContentProvider cuando el json está presente; esta llamada es un respaldo
 * idempotente que garantiza el init antes de que cualquier pantalla use AuthManager.
 */
@HiltAndroidApp
class PowApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 🍏 Fase 5: el código compartido lee assets sin conocer el `Context`, así que la fuente
        // real hay que instalarla aquí. En iOS no hace falta (el bundle es global).
        // ⚠️ ANTES QUE NADA: si una pantalla llegara a pedir un asset con esto sin instalar, el
        // fallo es inmediato y con un mensaje que dice justo esta línea.
        PowAssets.instalar(AssetsDeAndroid(this))
        PowAudio.instalar(AudioDeAndroid(this))
        try {
            com.google.firebase.FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.w("PowApplication", "Firebase no inicializado (¿falta google-services.json?): ${e.message}")
        }
    }
}
