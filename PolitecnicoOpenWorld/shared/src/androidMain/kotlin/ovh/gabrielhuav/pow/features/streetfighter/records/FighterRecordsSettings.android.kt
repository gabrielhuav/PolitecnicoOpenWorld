package ovh.gabrielhuav.pow.features.streetfighter.records

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

@Composable
actual fun rememberFighterRecordsSettings(): Settings {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        SharedPreferencesSettings(
            context.getSharedPreferences("sf_fighter_records", Context.MODE_PRIVATE),
        )
    }
}