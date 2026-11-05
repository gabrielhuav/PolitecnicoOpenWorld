package ovh.gabrielhuav.pow.features.streetfighter.records

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberFighterRecordsSettings(): Settings =
    remember { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }