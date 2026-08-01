package ovh.gabrielhuav.pow.data.repository

import android.app.ActivityManager
import android.content.Context
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Conserva la llamada histórica `SettingsRepository(context)` y el mismo fichero de preferencias.
 */
@Suppress("FunctionName")
fun SettingsRepository(context: Context): SettingsRepository {
    val preferences = context.getSharedPreferences(
        SettingsRepository.PREFS_NAME,
        Context.MODE_PRIVATE,
    )
    val lowRam = runCatching {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    }.getOrDefault(false)
    return SettingsRepository(
        settings = SharedPreferencesSettings(preferences),
        lowRamDefault = lowRam,
    )
}
