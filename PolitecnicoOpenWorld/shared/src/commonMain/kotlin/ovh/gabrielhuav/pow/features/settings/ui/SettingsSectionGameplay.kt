package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

@Composable
internal fun SettingsSectionGameplay(
    state: SettingsState,
    onDensityChanged: (Float) -> Unit,
    onEmojiLodChanged: (Boolean) -> Unit,
    onFullEmojiChanged: (Boolean) -> Unit,
    onOptimize: () -> Unit,
) {
    Column {
        Button(
            onClick = onOptimize,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF6B1C3A),
                contentColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(Res.string.settings_optimize_device),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(Res.string.settings_optimize_device_desc),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(Res.string.settings_npc_count),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(Res.string.settings_npc_count_desc, (state.npcDensity * 100).toInt()),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
        )
        Slider(
            value = state.npcDensity,
            onValueChange = onDensityChanged,
            valueRange = 0.4f..1.6f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFD4AF37),
                activeTrackColor = Color(0xFF6B1C3A),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.settings_npc_less), color = Color.Gray, fontSize = 11.sp)
            Text(stringResource(Res.string.settings_npc_more), color = Color.Gray, fontSize = 11.sp)
        }
        Spacer(Modifier.height(24.dp))
        SettingsSwitch(
            stringResource(Res.string.settings_npc_lod_title),
            stringResource(Res.string.settings_npc_lod_desc),
            state.npcEmojiLod,
            onEmojiLodChanged,
        )
        Spacer(Modifier.height(24.dp))
        SettingsSwitch(
            stringResource(Res.string.settings_npc_full_title),
            stringResource(Res.string.settings_npc_full_desc),
            state.npcFullEmoji,
            onFullEmojiChanged,
        )
    }
}
