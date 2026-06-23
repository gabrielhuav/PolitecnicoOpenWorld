package ovh.gabrielhuav.pow.features.settings.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.i18n.LocaleHelper
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.settings.models.ControlType
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState

@Composable
fun SettingsScreen(
    state: SettingsState,
    onCategorySelected: (SettingsCategory) -> Unit,
    onMapProviderChanged: (MapProvider) -> Unit,
    onCacheToggled: (Boolean) -> Unit,
    onFpsToggled: (Boolean) -> Unit,
    onZoomWidgetToggled: (Boolean) -> Unit,
    onSpeedometerToggled: (Boolean) -> Unit,
    onCoordsWidgetToggled: (Boolean) -> Unit,
    onDeveloperModeToggled: (Boolean) -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onSfxVolumeChanged: (Float) -> Unit,
    onRoadNetworkToggled: (Boolean) -> Unit,
    onSaveClicked: () -> Unit,
    onControlTypeChanged: (ControlType) -> Unit,
    onControlsScaleChanged: (Float) -> Unit,
    onSwapControlsToggled: (Boolean) -> Unit,
    onNpcDensityChanged: (Float) -> Unit,
    onNpcEmojiLodToggled: (Boolean) -> Unit,
    onNpcFullEmojiToggled: (Boolean) -> Unit,
    onOptimizeForDevice: () -> Unit = {},
    onNavigateBack: () -> Unit,
    onExitToMainMenu: () -> Unit,
    authManager: ovh.gabrielhuav.pow.data.auth.AuthManager? = null,
    onAccountDeleted: () -> Unit = {},
    currentLanguage: String = "",
    onLanguageChanged: (String) -> Unit = {}
) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    // Estados para recordar la posición del scroll
    val sidebarScrollState = rememberScrollState()
    val contentScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(bg).systemBarsPadding()) {

        val configuration = LocalConfiguration.current
        val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        // En HORIZONTAL sobre pantallas BAJAS (lado corto pequeño, ≤380 dp) el cromo de tamaño fijo se
        // ve apretado: activamos un modo COMPACTO que reduce paddings/spacers/fuentes del menú de
        // Configuración. Solo afecta a horizontal en pantallas chicas; vertical y horizontal grande quedan igual.
        val compactLand = !isPortrait && configuration.screenHeightDp <= 380

        // Barra Superior
        Row(
            modifier = Modifier.fillMaxWidth().padding(if (compactLand) 8.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.settings_back), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.settings_title),
                color = Color(0xFFD4AF37),
                fontSize = if (compactLand) 16.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isPortrait) {
                // DISEÑO VERTICAL (PORTRAIT)
                Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {

                    // Categorías estilo "Pestañas" con scroll horizontal
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categories = listOf(SettingsCategory.Map, SettingsCategory.Controls, SettingsCategory.Gameplay, SettingsCategory.Interface, SettingsCategory.Audio, SettingsCategory.Account)
                        categories.forEach { category ->
                            CategoryItemHorizontal(
                                category = category,
                                isSelected = state.selectedCategory == category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contenido de la configuración
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A0A10))
                            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                            .verticalScroll(contentScrollState)
                    ) {
                        Text(stringResource(state.selectedCategory.titleRes).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(24.dp))
                        SettingsContent(state, onMapProviderChanged, onCacheToggled, onFpsToggled, onZoomWidgetToggled, onSpeedometerToggled, onCoordsWidgetToggled, onDeveloperModeToggled, onMusicVolumeChanged, onSfxVolumeChanged, onSaveClicked, onControlTypeChanged, onControlsScaleChanged, onSwapControlsToggled, onRoadNetworkToggled, onNpcDensityChanged, onNpcEmojiLodToggled, onNpcFullEmojiToggled, onOptimizeForDevice, authManager, onAccountDeleted, currentLanguage, onLanguageChanged)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botón "VOLVER" abajo (mismo diseño que coleccionables / Modo Historia)
                    val backShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
                    Button(
                        onClick = onExitToMainMenu,
                        shape = backShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B1C3A),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(bottom = 8.dp)
                            .shadow(elevation = 8.dp, shape = backShape)
                    ) {
                        Text(stringResource(R.string.menu_back), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    }
                }
            } else {
                // DISEÑO HORIZONTAL (LANDSCAPE). En pantallas bajas se compacta (compactLand).
                Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = if (compactLand) 8.dp else 16.dp, vertical = if (compactLand) 2.dp else 8.dp)) {
                    Column(modifier = Modifier.weight(0.3f).fillMaxHeight().verticalScroll(sidebarScrollState)) {
                        val categories = listOf(SettingsCategory.Map, SettingsCategory.Controls, SettingsCategory.Gameplay, SettingsCategory.Interface, SettingsCategory.Audio, SettingsCategory.Account)
                        categories.forEach { category ->
                            CategoryItem(category = category, isSelected = state.selectedCategory == category, onClick = { onCategorySelected(category) }, compact = compactLand)
                        }
                        Spacer(modifier = Modifier.height(if (compactLand) 10.dp else 32.dp))
                        val backShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
                        Button(
                            onClick = onExitToMainMenu,
                            shape = backShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6B1C3A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (compactLand) 42.dp else 56.dp)
                                .padding(bottom = if (compactLand) 6.dp else 16.dp)
                                .shadow(elevation = 8.dp, shape = backShape)
                        ) { Text(stringResource(R.string.menu_back), fontSize = if (compactLand) 12.sp else 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
                    }

                    Column(modifier = Modifier.weight(0.7f).fillMaxHeight().padding(start = if (compactLand) 12.dp else 24.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A0A10)).border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(if (compactLand) 12.dp else 24.dp).verticalScroll(contentScrollState)) {
                        Text(stringResource(state.selectedCategory.titleRes).uppercase(), fontSize = if (compactLand) 15.sp else 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(if (compactLand) 10.dp else 24.dp))
                        SettingsContent(state, onMapProviderChanged, onCacheToggled, onFpsToggled, onZoomWidgetToggled, onSpeedometerToggled, onCoordsWidgetToggled, onDeveloperModeToggled, onMusicVolumeChanged, onSfxVolumeChanged, onSaveClicked, onControlTypeChanged, onControlsScaleChanged, onSwapControlsToggled, onRoadNetworkToggled, onNpcDensityChanged, onNpcEmojiLodToggled, onNpcFullEmojiToggled, onOptimizeForDevice, authManager, onAccountDeleted, currentLanguage, onLanguageChanged)
                    }
                }
            }
        }
    }
}
@Composable
private fun CategoryItemHorizontal(category: SettingsCategory, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(end = 8.dp) // Mantiene la separación entre botones
            .clip(RoundedCornerShape(8.dp)) // Cambiado a 8.dp para tener el diseño rectangular
            .clickable { onClick() }
            .background(if (isSelected) Color(0xFF6B1C3A) else Color.Transparent) // Fondo transparente al no estar seleccionado
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(category.titleRes),
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CategoryItem(category: SettingsCategory, isSelected: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 4.dp).clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(if (isSelected) Color(0xFF6B1C3A) else Color.Transparent)
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(category.icon, contentDescription = null, tint = if (isSelected) Color.White else Color.Gray, modifier = Modifier.size(if (compact) 18.dp else 24.dp))
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Text(stringResource(category.titleRes), color = if (isSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else androidx.compose.ui.unit.TextUnit.Unspecified)
    }
}
@Composable
private fun SettingsContent(
    state: SettingsState,
    onMapProviderChanged: (MapProvider) -> Unit,
    onCacheToggled: (Boolean) -> Unit,
    onFpsToggled: (Boolean) -> Unit,
    onZoomWidgetToggled: (Boolean) -> Unit,
    onSpeedometerToggled: (Boolean) -> Unit,
    onCoordsWidgetToggled: (Boolean) -> Unit,
    onDeveloperModeToggled: (Boolean) -> Unit,
    onMusicVolumeChanged: (Float) -> Unit,
    onSfxVolumeChanged: (Float) -> Unit,
    onSaveClicked: () -> Unit,
    onControlTypeChanged: (ControlType) -> Unit,
    onControlsScaleChanged: (Float) -> Unit,
    onSwapControlsToggled: (Boolean) -> Unit,
    onRoadNetworkToggled: (Boolean) -> Unit,
    onNpcDensityChanged: (Float) -> Unit,
    onNpcEmojiLodToggled: (Boolean) -> Unit,
    onNpcFullEmojiToggled: (Boolean) -> Unit,
    onOptimizeForDevice: () -> Unit,
    authManager: ovh.gabrielhuav.pow.data.auth.AuthManager?,
    onAccountDeleted: () -> Unit,
    currentLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
    when (state.selectedCategory) {
        is SettingsCategory.Map -> MapProviderSetting(
            state.mapProvider, onMapProviderChanged,
            state.showRoadNetwork, onRoadNetworkToggled
        )
        is SettingsCategory.Controls -> ControlsSettingsConfig(state.tempControlType, state.tempControlsScale, state.tempSwapControls, onControlTypeChanged, onControlsScaleChanged, onSwapControlsToggled, onSaveClicked)
        is SettingsCategory.Gameplay -> GameplaySettings(state.npcDensity, onNpcDensityChanged, state.npcEmojiLod, onNpcEmojiLodToggled, state.npcFullEmoji, onNpcFullEmojiToggled, onOptimizeForDevice)
        is SettingsCategory.Interface -> DiagnosticWidgetsSetting(state.showCacheWidget, state.showFpsWidget, state.showZoomWidget, state.showSpeedometer, state.showCoordsWidget, state.developerMode, onCacheToggled, onFpsToggled, onZoomWidgetToggled, onSpeedometerToggled, onCoordsWidgetToggled, onDeveloperModeToggled, currentLanguage, onLanguageChanged)
        is SettingsCategory.Audio -> AudioSettings(state.musicVolume, state.sfxVolume, onMusicVolumeChanged, onSfxVolumeChanged)
        is SettingsCategory.Account -> AccountSettings(authManager, onAccountDeleted)
        else -> Text(stringResource(R.string.settings_none_available), color = Color.Gray)
    }
}
