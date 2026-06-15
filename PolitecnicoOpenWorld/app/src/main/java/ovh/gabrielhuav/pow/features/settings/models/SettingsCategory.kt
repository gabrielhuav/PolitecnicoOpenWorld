package ovh.gabrielhuav.pow.features.settings.models

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector
import ovh.gabrielhuav.pow.R

// i18n: el título se guarda como referencia de recurso (@StringRes); la View lo
// resuelve con stringResource(category.titleRes). Así las categorías se traducen.
sealed class SettingsCategory(@StringRes val titleRes: Int, val icon: ImageVector) {
    object Map : SettingsCategory(R.string.settings_cat_map, Icons.Default.Map)
    object Controls : SettingsCategory(R.string.settings_cat_controls, Icons.Default.Gamepad)
    object Gameplay : SettingsCategory(R.string.settings_cat_gameplay, Icons.Default.SportsEsports)
    object Interface : SettingsCategory(R.string.settings_cat_interface, Icons.Default.Layers)
}
