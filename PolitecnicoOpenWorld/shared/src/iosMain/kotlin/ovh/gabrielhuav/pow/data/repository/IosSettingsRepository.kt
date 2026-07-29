package ovh.gabrielhuav.pow.data.repository

import com.russhwolf.settings.NSUserDefaultsSettings
import platform.Foundation.NSUserDefaults

/** Usa el mismo `standardUserDefaults` que ya consulta `IosStreetFighterEnvironment`. */
fun iosSettingsRepository(): SettingsRepository = SettingsRepository(
    settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults),
)
