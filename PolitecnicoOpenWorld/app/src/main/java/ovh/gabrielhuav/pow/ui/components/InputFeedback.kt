package ovh.gabrielhuav.pow.ui.components

import android.content.Context
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import ovh.gabrielhuav.pow.data.repository.SettingsRepository

/**
 * Retroalimentacion al PULSAR botones de la UI de juego (D-pad, botones A/B/X/Y, diamante PS4,
 * controles de vehiculo). Compartida por INTERIORES y EXTERIOR (los controles viven aqui). Canales:
 *   - HAPTICA: View.performHapticFeedback (respeta ajustes del sistema, SIN permiso VIBRATE).
 *   - SONIDO: AudioManager.playSoundEffect(FX_KEY_CLICK) a volumen = "Efectos" (Ajustes -> Audio).
 * El resalte VISUAL lo aplica cada boton con su estado `pressed`. Se crea 1 vez con rememberInputFeedback().
 */
class InputFeedback(
    private val view: View,
    private val audio: AudioManager?,
    private val sfxVolume: Float
) {
    /** Vibracion + click. Llamar en el flanco de BAJADA (cuando el dedo TOCA el boton). */
    fun tap() {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
        if (sfxVolume > 0f) {
            audio?.playSoundEffect(AudioManager.FX_KEY_CLICK, sfxVolume.coerceIn(0f, 1f))
        }
    }
}

@Composable
fun rememberInputFeedback(): InputFeedback {
    val view = LocalView.current
    val context = LocalContext.current
    return remember(view) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val sfx = SettingsRepository(context).getSfxVolume()
        InputFeedback(view, audio, sfx)
    }
}
