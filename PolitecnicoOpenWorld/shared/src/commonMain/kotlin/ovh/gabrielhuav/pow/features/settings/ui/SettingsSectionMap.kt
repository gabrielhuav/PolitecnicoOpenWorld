package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

@Composable
internal fun SettingsSectionMap(state: SettingsState, controller: SettingsController) {
    var expanded by remember { mutableStateOf(false) }
    var pending by remember(state.mapProvider) { mutableStateOf(state.mapProvider) }
    val changed = pending != state.mapProvider
    Column {
        Text(
            stringResource(Res.string.settings_map_provider),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    containerColor = Color(0xFF2A1C21),
                ),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A)),
            ) {
                Text(pending.displayName, Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFD4AF37))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A1C21)),
            ) {
                MapProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName, color = Color.White) },
                        onClick = { pending = provider; expanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = { controller.changeMapProvider(pending) },
                enabled = changed,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.settings_map_change))
            }
            OutlinedButton(
                onClick = { pending = state.mapProvider },
                enabled = changed,
                border = BorderStroke(1.dp, Color(0xFFD4AF37)),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.settings_map_restore))
            }
        }
        Spacer(Modifier.height(24.dp))
        SettingsSwitch(
            stringResource(Res.string.settings_road_network),
            stringResource(Res.string.settings_road_network_desc),
            state.showRoadNetwork,
            controller::toggleRoadNetwork,
        )
    }
}
