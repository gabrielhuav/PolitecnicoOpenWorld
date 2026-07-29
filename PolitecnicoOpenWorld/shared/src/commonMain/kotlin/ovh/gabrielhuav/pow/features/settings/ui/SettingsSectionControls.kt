package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

@Composable
internal fun SettingsSectionControls(
    state: SettingsState,
    controller: SettingsController,
    onSave: () -> Unit,
) {
    BoxWithConstraints {
        val portrait = maxHeight >= maxWidth
        val maxScale = if (portrait) 1f else 1.4f
        val safeScale = state.tempControlsScale.coerceAtMost(maxScale)
        LaunchedEffect(state.tempControlsScale, maxScale) {
            if (state.tempControlsScale > maxScale) controller.changeControlsScale(safeScale)
        }
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text(
                    stringResource(Res.string.settings_move_style),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ControlType.entries.forEach { option ->
                        Button(
                            onClick = { controller.changeControlType(option) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.tempControlType == option) {
                                    Color(0xFF6B1C3A)
                                } else {
                                    Color(0xFF2A1C21)
                                },
                            ),
                        ) {
                            Text(option.displayName, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Column {
                Text(
                    stringResource(
                        Res.string.settings_screen_size,
                        (safeScale * 100).toInt(),
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        if (portrait) {
                            Res.string.settings_size_portrait
                        } else {
                            Res.string.settings_size_landscape
                        },
                    ),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
                Slider(
                    value = safeScale,
                    onValueChange = controller::changeControlsScale,
                    valueRange = 0.6f..maxScale,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFD4AF37),
                        activeTrackColor = Color(0xFF6B1C3A),
                    ),
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(Res.string.settings_swap_sides),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(Res.string.settings_swap_sides_desc),
                        color = Color.Gray,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = state.tempSwapControls,
                    onCheckedChange = controller::toggleSwapControls,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD4AF37),
                        checkedTrackColor = Color(0xFF6B1C3A),
                    ),
                )
            }

            var tutorialWorld by remember { mutableStateOf<Int?>(null) }
            Column {
                Text(
                    stringResource(Res.string.settings_tutorial_section),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(Res.string.settings_tutorial_hint),
                    color = Color.Gray,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { tutorialWorld = 0 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21)),
                    ) {
                        Text(
                            stringResource(Res.string.settings_tutorial_exterior),
                            fontSize = 11.sp,
                        )
                    }
                    Button(
                        onClick = { tutorialWorld = 1 },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21)),
                    ) {
                        Text(
                            stringResource(Res.string.settings_tutorial_interior),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            if (tutorialWorld != null) {
                Dialog(
                    onDismissRequest = { tutorialWorld = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    ControlsTutorialOverlay(
                        titleRes = if (tutorialWorld == 1) {
                            Res.string.tutorial_int_title
                        } else {
                            Res.string.tutorial_ext_title
                        },
                        pages = if (tutorialWorld == 1) {
                            interiorTutorialPages()
                        } else {
                            exteriorTutorialPages()
                        },
                        onDismiss = { tutorialWorld = null },
                    )
                }
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37)),
            ) {
                Text(
                    stringResource(Res.string.settings_save),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
