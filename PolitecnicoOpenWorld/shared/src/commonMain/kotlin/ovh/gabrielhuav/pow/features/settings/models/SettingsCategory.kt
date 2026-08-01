package ovh.gabrielhuav.pow.features.settings.models

import org.jetbrains.compose.resources.StringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.settings_cat_account
import ovh.gabrielhuav.pow.shared.recursos.settings_cat_audio
import ovh.gabrielhuav.pow.shared.recursos.settings_cat_controls
import ovh.gabrielhuav.pow.shared.recursos.settings_cat_gameplay
import ovh.gabrielhuav.pow.shared.recursos.settings_cat_interface
import ovh.gabrielhuav.pow.shared.recursos.settings_cat_map

// i18n: el título se guarda como recurso multiplataforma; la View lo
// resuelve con stringResource(category.titleRes). Así las categorías se traducen.
sealed class SettingsCategory(val titleRes: StringResource, val icon: SettingsCategoryIcon) {
    data object Map : SettingsCategory(Res.string.settings_cat_map, SettingsCategoryIcon.MAP)
    data object Controls : SettingsCategory(Res.string.settings_cat_controls, SettingsCategoryIcon.CONTROLS)
    data object Gameplay : SettingsCategory(Res.string.settings_cat_gameplay, SettingsCategoryIcon.GAMEPLAY)
    data object Interface : SettingsCategory(Res.string.settings_cat_interface, SettingsCategoryIcon.INTERFACE)
    data object Audio : SettingsCategory(Res.string.settings_cat_audio, SettingsCategoryIcon.AUDIO)
    data object Account : SettingsCategory(Res.string.settings_cat_account, SettingsCategoryIcon.ACCOUNT)
}

enum class SettingsCategoryIcon { MAP, CONTROLS, GAMEPLAY, INTERFACE, AUDIO, ACCOUNT }
