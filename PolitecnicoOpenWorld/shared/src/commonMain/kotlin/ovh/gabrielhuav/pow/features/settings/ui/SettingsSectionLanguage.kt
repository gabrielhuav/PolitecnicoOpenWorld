package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.settings_language
import ovh.gabrielhuav.pow.shared.recursos.settings_language_system

private val SupportedLanguages = listOf("" to "", "es" to "Español", "en" to "English")

@Composable
internal fun SettingsSectionLanguage(current: String, onChanged: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = if (current.isBlank()) {
        stringResource(Res.string.settings_language_system)
    } else {
        SupportedLanguages.firstOrNull { it.first == current }?.second ?: current
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            stringResource(Res.string.settings_language),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    containerColor = Color(0xFF2A1C21),
                ),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A)),
            ) {
                Text(currentLabel)
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFD4AF37))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A1C21)),
            ) {
                SupportedLanguages.forEach { (tag, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (tag.isBlank()) {
                                    stringResource(Res.string.settings_language_system)
                                } else {
                                    label
                                },
                                color = Color.White,
                            )
                        },
                        onClick = {
                            expanded = false
                            if (tag != current) onChanged(tag)
                        },
                    )
                }
            }
        }
    }
}
