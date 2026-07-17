package ovh.gabrielhuav.pow.features.settings.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.i18n.LocaleHelper

// ─────────────────────────────────────────────────────────────────────────────
// Secciones de Ajustes extraídas de SettingsScreen.kt (refactor de tamaño). Son composables
// `internal` del mismo paquete; las despacha SettingsContent en SettingsScreen.kt. MVVM intacto.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun GameplaySettings(
    npcDensity: Float,
    onNpcDensityChanged: (Float) -> Unit,
    npcEmojiLod: Boolean,
    onNpcEmojiLodToggled: (Boolean) -> Unit,
    npcFullEmoji: Boolean,
    onNpcFullEmojiToggled: (Boolean) -> Unit,
    onOptimizeForDevice: () -> Unit
) {
    Column {
        // ─── Preset de UN TOQUE: optimizar para gama baja ────────────────────
        // Aplica de golpe los 3 ajustes ligeros (densidad mínima + ambos emoji).
        Button(
            onClick = onOptimizeForDevice,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.settings_optimize_device), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.settings_optimize_device_desc), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(Modifier.height(20.dp))

        // ─── Densidad de NPCs ────────────────────────────────────────────────
        Text(stringResource(R.string.settings_npc_count), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        val pct = (npcDensity * 100).toInt()
        Text(
            stringResource(R.string.settings_npc_count_desc, pct),
            color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = npcDensity,
            onValueChange = onNpcDensityChanged,
            valueRange = 0.4f..1.6f,
            steps = 5, // 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFD4AF37),
                activeTrackColor = Color(0xFF6B1C3A)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.settings_npc_less), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            Text(stringResource(R.string.settings_npc_more), color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        }

        Spacer(Modifier.height(24.dp))

        // ─── LOD de emojis ("Optimizar dibujado de NPCs") ────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_npc_lod_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.settings_npc_lod_desc),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
            }
            Switch(
                checked = npcEmojiLod,
                onCheckedChange = onNpcEmojiLodToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD4AF37),
                    checkedTrackColor = Color(0xFF6B1C3A)
                )
            )
        }

        Spacer(Modifier.height(24.dp))

        // ─── Emoji TOTAL ("Optimizar para gama baja") ────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_npc_full_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.settings_npc_full_desc),
                    color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                )
            }
            Switch(
                checked = npcFullEmoji,
                onCheckedChange = onNpcFullEmojiToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD4AF37),
                    checkedTrackColor = Color(0xFF6B1C3A)
                )
            )
        }
    }
}
@Composable
internal fun MapProviderSetting(
    current: MapProvider,
    onChanged: (MapProvider) -> Unit,
    showRoadNetwork: Boolean,
    onRoadNetworkToggled: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // NUEVO: Estado local para retener la selección antes de aplicarla
    var tempProvider by remember(current) { mutableStateOf(current) }
    val hasPendingChange = tempProvider != current

    Column {
        Text(stringResource(R.string.settings_map_provider), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, containerColor = Color(0xFF2A1C21)),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A))
            ) {
                // CAMBIO: Mostrar el proveedor temporal en lugar del current
                Text(tempProvider.displayName, Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFD4AF37))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A1C21))
            ) {
                MapProvider.entries.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.displayName, color = Color.White) },
                        onClick = {
                            // CAMBIO: Solo actualizamos el estado temporal, no llamamos a onChanged aún
                            tempProvider = provider
                            expanded = false
                        }
                    )
                }
            }
        }

        // NUEVO: Fila con los botones "Cambiar Mapa" y "Restaurar Mapa"
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { onChanged(tempProvider) },
                enabled = hasPendingChange,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B1C3A),
                    disabledContainerColor = Color(0xFF2A1C21)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_map_change), color = if (hasPendingChange) Color.White else Color.Gray)
            }

            OutlinedButton(
                onClick = { tempProvider = current },
                enabled = hasPendingChange,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray
                ),
                border = BorderStroke(1.dp, if (hasPendingChange) Color(0xFFD4AF37) else Color(0xFF2A1C21)),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_map_restore))
            }
        }
        // ── Agregar al final de la Column de MapProviderSetting ──
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(stringResource(R.string.settings_road_network), color = Color.White, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_road_network_desc), color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = showRoadNetwork,
                onCheckedChange = onRoadNetworkToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD4AF37),
                    checkedTrackColor = Color(0xFF6B1C3A)
                )
            )
        }
    }
}
@Composable
internal fun ControlsSettingsConfig(
    type: ControlType,
    scale: Float,
    isSwapped: Boolean,
    onTypeChanged: (ControlType) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onSwapChanged: (Boolean) -> Unit,
    onSaveClicked: () -> Unit
) {
    // Detectar si estamos en vertical u horizontal para el tamaño máximo
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    // Calculamos el límite dinámico: 1.0f en vertical, 1.4f en horizontal
    val maxScale = if (isPortrait) 1.0f else 1.4f
    val safeScale = scale.coerceAtMost(maxScale) // Evita que se pase del límite actual

    LaunchedEffect(scale, maxScale) {
        if (scale > maxScale) {
            onScaleChanged(safeScale)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // 1. Selector de Tipo
        Column {
            Text(stringResource(R.string.settings_move_style), color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlType.entries.forEach { option ->
                    Button(
                        onClick = { onTypeChanged(option) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == option) Color(0xFF6B1C3A) else Color(0xFF2A1C21)
                        )
                    ) {
                        Text(option.displayName, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Deslizador de Tamaño (ACTUALIZADO CON RESPONSIVIDAD)
        Column {
            Text(stringResource(R.string.settings_screen_size, (safeScale * 100).toInt()), color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                text = if (isPortrait) stringResource(R.string.settings_size_portrait) else stringResource(R.string.settings_size_landscape),
                color = Color.Gray,
                fontSize = 12.sp
            )
            Slider(
                value = safeScale,
                onValueChange = onScaleChanged,
                valueRange = 0.6f..maxScale, // El rango termina en el límite que calculamos arriba
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4AF37),
                    activeTrackColor = Color(0xFF6B1C3A)
                )
            )
        }

        // 3. Inversión (Modo Zurdo)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(stringResource(R.string.settings_swap_sides), color = Color.White, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_swap_sides_desc), color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = isSwapped,
                onCheckedChange = onSwapChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD4AF37),
                    checkedTrackColor = Color(0xFF6B1C3A)
                )
            )
        }

        // 🆕 4. Referencia y TUTORIAL de controles (optativo, 2026-07-11): además del que se
        // ofrece la primera vez en cada mundo, aquí se puede RE-VER cuando se quiera.
        var tutorialWorld by remember { mutableStateOf<Int?>(null) }   // null = cerrado; 0 = exterior; 1 = interiores
        Column {
            Text(stringResource(R.string.settings_tutorial_section), color = Color.White, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.settings_tutorial_hint), color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { tutorialWorld = 0 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21))
                ) {
                    Text(stringResource(R.string.settings_tutorial_exterior), color = Color.White, fontSize = 11.sp)
                }
                Button(
                    onClick = { tutorialWorld = 1 },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A1C21))
                ) {
                    Text(stringResource(R.string.settings_tutorial_interior), color = Color.White, fontSize = 11.sp)
                }
            }
        }
        if (tutorialWorld != null) {
            // Dialog a pantalla completa (usePlatformDefaultWidth=false) para que el overlay
            // no quede constreñido por la columna scrolleable de Ajustes.
            Dialog(
                onDismissRequest = { tutorialWorld = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                ControlsTutorialOverlay(
                    titleRes = if (tutorialWorld == 1) R.string.tutorial_int_title else R.string.tutorial_ext_title,
                    pages = if (tutorialWorld == 1) interiorTutorialPages() else exteriorTutorialPages(),
                    onDismiss = { tutorialWorld = null }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSaveClicked,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
        ) {
            Text(stringResource(R.string.settings_save), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// i18n: selector de idioma de la UI. Al elegir uno persiste vía el VM y recrea la
// Activity para que MainActivity.attachBaseContext aplique el nuevo locale.
// Para añadir más idiomas (p. ej. ruso) basta con crear res/values-ru/strings.xml y
// sumar su etiqueta a LocaleHelper.SUPPORTED; este selector los muestra solo.
@Composable
internal fun LanguageSetting(current: String, onChanged: (String) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = if (current.isBlank())
        stringResource(R.string.settings_language_system)
    else LocaleHelper.SUPPORTED.firstOrNull { it.first == current }?.second ?: current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(R.string.settings_language), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, containerColor = Color(0xFF2A1C21)),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A))
            ) {
                Text(currentLabel)
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFD4AF37))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color(0xFF2A1C21))
            ) {
                LocaleHelper.SUPPORTED.forEach { (tag, label) ->
                    val display = if (tag.isBlank()) stringResource(R.string.settings_language_system) else label
                    DropdownMenuItem(
                        text = { Text(display, color = Color.White) },
                        onClick = {
                            expanded = false
                            if (tag != current) {
                                onChanged(tag)
                                // Recrear la Activity para aplicar el nuevo idioma.
                                (context as? android.app.Activity)?.recreate()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun AudioSettings(
    musicVolume: Float,
    sfxVolume: Float,
    onMusicVolumeChanged: (Float) -> Unit,
    onSfxVolumeChanged: (Float) -> Unit
) {
    Column {
        // ─── Volumen de música ───────────────────────────────────────────────
        Text(stringResource(R.string.settings_music_volume), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${(musicVolume * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Slider(
            value = musicVolume,
            onValueChange = onMusicVolumeChanged,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFD4AF37), activeTrackColor = Color(0xFF6B1C3A))
        )

        Spacer(Modifier.height(24.dp))

        // ─── Volumen de efectos ──────────────────────────────────────────────
        Text(stringResource(R.string.settings_sfx_volume), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("${(sfxVolume * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Slider(
            value = sfxVolume,
            onValueChange = onSfxVolumeChanged,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Color(0xFFD4AF37), activeTrackColor = Color(0xFF6B1C3A))
        )
    }
}

// ─── CUENTA / AUTENTICACIÓN (Google Sign-In) ──────────────────────────────────
// Inicio/cierre de sesión y ELIMINACIÓN de cuenta (obligatorio en Play Store).
// El juego local y el Modo Historia NO requieren sesión; solo el multijugador la exige.
@Composable
internal fun AccountSettings(
    authManager: ovh.gabrielhuav.pow.data.auth.AuthManager?,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    var signedIn by remember { mutableStateOf(authManager?.isSignedIn() == true) }
    var accountLabel by remember { mutableStateOf(authManager?.currentEmail() ?: authManager?.currentDisplayName()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager?.handleSignInResult(result.data) { ok, err ->
            if (ok) {
                signedIn = true
                accountLabel = authManager.currentEmail() ?: authManager.currentDisplayName()
            } else if (!err.isNullOrBlank()) {
                // err == null = el usuario canceló el selector → sin aviso.
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.settings_account_desc), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Justify)

        if (signedIn) {
            Text(
                stringResource(R.string.settings_account_signed_in_as, accountLabel ?: ""),
                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(
                onClick = { authManager?.signOut { signedIn = false; accountLabel = null } },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A))
            ) { Text(stringResource(R.string.settings_account_sign_out)) }

            Spacer(Modifier.height(8.dp))

            // Botón DESTRUCTIVO: elimina la cuenta de Firebase y los datos locales.
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
            ) { Text(stringResource(R.string.settings_account_delete), color = Color.White, fontWeight = FontWeight.Bold) }
        } else {
            Text(stringResource(R.string.settings_account_not_signed_in), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
            Button(
                onClick = { authManager?.let { signInLauncher.launch(it.signInIntent()) } },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) { Text(stringResource(R.string.settings_account_sign_in), color = Color.White, fontWeight = FontWeight.Bold) }
        }

        // Enlace a la política de privacidad pública (siempre visible; requisito de Play Store).
        val privacyUrl = stringResource(R.string.settings_privacy_url)
        TextButton(
            onClick = {
                try {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(privacyUrl)))
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.settings_account_privacy), color = Color(0xFFD4AF37)) }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_account_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_account_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    authManager?.deleteAccount { ok, err ->
                        if (ok) {
                            signedIn = false
                            accountLabel = null
                            Toast.makeText(context, R.string.settings_account_delete_done, Toast.LENGTH_LONG).show()
                            onAccountDeleted()
                        } else {
                            Toast.makeText(context, err ?: context.getString(R.string.settings_account_relogin_needed), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.settings_account_delete), color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.menu_cancel)) }
            }
        )
    }
}

@Composable
internal fun DiagnosticWidgetsSetting(
    cacheEnabled: Boolean,
    fpsEnabled: Boolean,
    zoomWidgetEnabled: Boolean,
    speedometerEnabled: Boolean,
    coordsWidgetEnabled: Boolean,
    developerModeEnabled: Boolean,
    hitboxesEnabled: Boolean,
    onCacheToggled: (Boolean) -> Unit,
    onFpsToggled: (Boolean) -> Unit,
    onZoomWidgetToggled: (Boolean) -> Unit,
    onSpeedometerToggled: (Boolean) -> Unit,
    onCoordsWidgetToggled: (Boolean) -> Unit,
    onDeveloperModeToggled: (Boolean) -> Unit,
    onHitboxesToggled: (Boolean) -> Unit,
    currentLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Selector de idioma (i18n)
        LanguageSetting(currentLanguage, onLanguageChanged)

        // Modo Desarrollador: revela botones/opciones de prueba (se ocultarán en la
        // versión final). Las pantallas deben observar state.developerMode.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_developer_mode), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_developer_mode_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = developerModeEnabled,
                onCheckedChange = onDeveloperModeToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // 🆕 Mostrar hitboxes del modo pelea (estilo Minecraft F3+B): dibuja las cajas
        // push/hurt/hit sobre los peleadores. Útil para ver dónde "vive" cada asset.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_show_hitboxes), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_show_hitboxes_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = hitboxesEnabled,
                onCheckedChange = onHitboxesToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // Toggle de Caché
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            // weight(1f) deja que la descripción ocupe el ancho disponible; así
            // TextAlign.Justify reparte el texto cuando ocupa 2+ líneas (la última
            // línea queda alineada a la izquierda, así que en 1 línea no se nota).
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_cache_widget), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_cache_widget_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = cacheEnabled,
                onCheckedChange = onCacheToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // Toggle de FPS
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_fps_widget), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_fps_widget_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = fpsEnabled,
                onCheckedChange = onFpsToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // Toggle de nivel de Zoom
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_zoom_widget), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_zoom_widget_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = zoomWidgetEnabled,
                onCheckedChange = onZoomWidgetToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // Toggle de Velocímetro
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_speedometer), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_speedometer_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = speedometerEnabled,
                onCheckedChange = onSpeedometerToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // Toggle del widget de coordenadas (X / Y / Z)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_coords_widget), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.settings_coords_widget_desc), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Justify)
            }
            Switch(
                checked = coordsWidgetEnabled,
                onCheckedChange = onCoordsWidgetToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }
    }
}