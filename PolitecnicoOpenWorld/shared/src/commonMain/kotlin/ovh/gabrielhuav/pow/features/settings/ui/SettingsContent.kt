package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.settings_none_available

/** Orquestador pequeño: cada sección vive en su propio fichero. */
@Composable
internal fun SettingsContent(
    state: SettingsState,
    category: SettingsCategory,
    controller: SettingsController,
    worldAvailable: Boolean,
    onMusicVolumeApplied: (Float) -> Unit,
    onSfxVolumeApplied: (Float) -> Unit,
    onNpcDensityApplied: (Float) -> Unit,
    onNpcEmojiLodApplied: (Boolean) -> Unit,
    onNpcFullEmojiApplied: (Boolean) -> Unit,
    onControlsSaved: (SettingsState) -> Unit,
    onOptimizeApplied: () -> Unit,
    onLanguageApplied: (String) -> Unit,
    accountContent: (@Composable () -> Unit)?,
) {
    when (category) {
        SettingsCategory.Map -> SettingsSectionMap(state, controller)
        SettingsCategory.Controls -> SettingsSectionControls(state, controller) {
            controller.saveControlsSettings()
            onControlsSaved(state)
        }
        SettingsCategory.Gameplay -> SettingsSectionGameplay(
            state = state,
            onDensityChanged = {
                controller.changeNpcDensity(it)
                onNpcDensityApplied(it)
            },
            onEmojiLodChanged = {
                controller.toggleNpcEmojiLod(it)
                onNpcEmojiLodApplied(it)
            },
            onFullEmojiChanged = {
                controller.toggleNpcFullEmoji(it)
                onNpcFullEmojiApplied(it)
            },
            onOptimize = {
                controller.changeNpcDensity(0.4f)
                controller.toggleNpcEmojiLod(true)
                controller.toggleNpcFullEmoji(true)
                onOptimizeApplied()
            },
        )
        SettingsCategory.Interface -> SettingsSectionInterface(
            state = state,
            controller = controller,
            worldAvailable = worldAvailable,
            onLanguageApplied = onLanguageApplied,
        )
        SettingsCategory.Audio -> SettingsSectionAudio(
            state = state,
            onMusicChanged = {
                controller.changeMusicVolume(it)
                onMusicVolumeApplied(it)
            },
            onSfxChanged = {
                controller.changeSfxVolume(it)
                onSfxVolumeApplied(it)
            },
        )
        SettingsCategory.Account -> accountContent?.invoke()
            ?: Text(stringResource(Res.string.settings_none_available), color = Color.Gray)
    }
}
