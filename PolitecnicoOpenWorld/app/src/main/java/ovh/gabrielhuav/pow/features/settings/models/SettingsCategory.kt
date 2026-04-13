package ovh.gabrielhuav.pow.features.settings.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

sealed class SettingsCategory(val title: String, val icon: ImageVector) {
    object Controls : SettingsCategory("Controles", Icons.Default.Gamepad)
    object Gameplay : SettingsCategory("Jugabilidad", Icons.Default.SportsEsports)
    object Interface : SettingsCategory("Interfaz", Icons.Default.Layers)
    object Map : SettingsCategory("Mapa", Icons.Default.Map)
}