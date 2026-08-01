package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

@Composable
internal fun SettingsSectionInterface(
    state: SettingsState,
    controller: SettingsController,
    worldAvailable: Boolean,
    onLanguageApplied: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSectionLanguage(state.language) {
            controller.changeLanguage(it)
            onLanguageApplied(it)
        }
        SettingsSwitch(
            stringResource(Res.string.settings_developer_mode),
            stringResource(Res.string.settings_developer_mode_desc),
            state.developerMode,
            controller::toggleDeveloperMode,
        )
        SettingsSwitch(
            stringResource(Res.string.settings_show_hitboxes),
            stringResource(Res.string.settings_show_hitboxes_desc),
            state.showHitboxes,
            controller::toggleHitboxes,
        )
        SettingsSwitch(
            stringResource(Res.string.settings_show_sf_fps),
            stringResource(Res.string.settings_show_sf_fps_desc),
            state.showSfFps,
            controller::toggleSfFps,
        )
        SettingsSwitch(
            stringResource(Res.string.settings_voice_subtitles),
            stringResource(Res.string.settings_voice_subtitles_desc),
            state.showVoiceSubtitles,
            controller::toggleVoiceSubtitles,
        )

        if (worldAvailable) {
            Spacer(Modifier.height(2.dp))
            SettingsSwitch(
                stringResource(Res.string.settings_world_shoulders),
                stringResource(Res.string.settings_world_shoulders_desc),
                state.showWorldShoulderButtons,
                controller::toggleWorldShoulderButtons,
            )
            SettingsSwitch(
                stringResource(Res.string.settings_cache_widget),
                stringResource(Res.string.settings_cache_widget_desc),
                state.showCacheWidget,
                controller::toggleCacheWidget,
            )
            SettingsSwitch(
                stringResource(Res.string.settings_fps_widget),
                stringResource(Res.string.settings_fps_widget_desc),
                state.showFpsWidget,
                controller::toggleFpsWidget,
            )
            SettingsSwitch(
                stringResource(Res.string.settings_zoom_widget),
                stringResource(Res.string.settings_zoom_widget_desc),
                state.showZoomWidget,
                controller::toggleZoomWidget,
            )
            SettingsSwitch(
                stringResource(Res.string.settings_speedometer),
                stringResource(Res.string.settings_speedometer_desc),
                state.showSpeedometer,
                controller::toggleSpeedometer,
            )
            SettingsSwitch(
                stringResource(Res.string.settings_coords_widget),
                stringResource(Res.string.settings_coords_widget_desc),
                state.showCoordsWidget,
                controller::toggleCoordsWidget,
            )
        }
    }
}
