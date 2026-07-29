package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import ovh.gabrielhuav.pow.shared.recursos.settings_music_volume
import ovh.gabrielhuav.pow.shared.recursos.settings_sfx_volume

@Composable
internal fun SettingsSectionAudio(
    state: SettingsState,
    onMusicChanged: (Float) -> Unit,
    onSfxChanged: (Float) -> Unit,
) {
    Column {
        VolumeSlider(
            stringResource(Res.string.settings_music_volume),
            state.musicVolume,
            onMusicChanged,
        )
        Spacer(Modifier.height(24.dp))
        VolumeSlider(
            stringResource(Res.string.settings_sfx_volume),
            state.sfxVolume,
            onSfxChanged,
        )
    }
}

@Composable
private fun VolumeSlider(title: String, value: Float, onChanged: (Float) -> Unit) {
    Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Text("${(value * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    Slider(
        value = value,
        onValueChange = onChanged,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFFD4AF37),
            activeTrackColor = Color(0xFF6B1C3A),
        ),
    )
}
