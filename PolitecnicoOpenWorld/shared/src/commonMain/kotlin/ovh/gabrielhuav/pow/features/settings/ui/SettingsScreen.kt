package ovh.gabrielhuav.pow.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.platform.PowModo
import ovh.gabrielhuav.pow.domain.platform.disponible
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategoryIcon
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.menu_back
import ovh.gabrielhuav.pow.shared.recursos.settings_back
import ovh.gabrielhuav.pow.shared.recursos.settings_title

/**
 * Única pantalla de Ajustes para Android e iOS.
 *
 * Los efectos nativos entran como lambdas y la cuenta Android como slot, igual que el menú
 * canónico. Las categorías del mundo abierto se consultan en [PowModo], no mediante `if (iOS)`.
 */
@Composable
fun SettingsScreen(
    controller: SettingsController,
    onNavigateBack: () -> Unit,
    onExitToMainMenu: () -> Unit,
    onMusicVolumeApplied: (Float) -> Unit = {},
    onSfxVolumeApplied: (Float) -> Unit = {},
    onNpcDensityApplied: (Float) -> Unit = {},
    onNpcEmojiLodApplied: (Boolean) -> Unit = {},
    onNpcFullEmojiApplied: (Boolean) -> Unit = {},
    onControlsSaved: (SettingsState) -> Unit = {},
    onOptimizeApplied: () -> Unit = {},
    onLanguageApplied: (String) -> Unit = {},
    accountContent: (@Composable () -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    val worldAvailable = PowModo.MUNDO_LIBRE.disponible()
    val categories = buildList {
        if (worldAvailable) {
            add(SettingsCategory.Map)
            add(SettingsCategory.Controls)
            add(SettingsCategory.Gameplay)
        }
        add(SettingsCategory.Interface)
        add(SettingsCategory.Audio)
        if (accountContent != null) add(SettingsCategory.Account)
    }
    val selected = state.selectedCategory.takeIf { it in categories } ?: SettingsCategory.Interface
    LaunchedEffect(selected) {
        if (selected != state.selectedCategory) controller.selectCategory(selected)
    }

    val background = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(background).systemBarsPadding(),
    ) {
        val portrait = maxHeight >= maxWidth
        val compactLandscape = !portrait && maxHeight <= 380.dp
        val sidebarScroll = rememberScrollState()
        val contentScroll = rememberScrollState()

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(if (compactLandscape) 8.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = stringResource(Res.string.settings_back),
                        tint = Color.White,
                    )
                }
                Text(
                    stringResource(Res.string.settings_title),
                    color = Color(0xFFD4AF37),
                    fontSize = if (compactLandscape) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (portrait) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        categories.forEach { category ->
                            CategoryTab(
                                category,
                                selected == category,
                                onClick = { controller.selectCategory(category) },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsPanel(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = state,
                        selected = selected,
                        controller = controller,
                        worldAvailable = worldAvailable,
                        contentScroll = contentScroll,
                        onMusicVolumeApplied = onMusicVolumeApplied,
                        onSfxVolumeApplied = onSfxVolumeApplied,
                        onNpcDensityApplied = onNpcDensityApplied,
                        onNpcEmojiLodApplied = onNpcEmojiLodApplied,
                        onNpcFullEmojiApplied = onNpcFullEmojiApplied,
                        onControlsSaved = onControlsSaved,
                        onOptimizeApplied = onOptimizeApplied,
                        onLanguageApplied = onLanguageApplied,
                        accountContent = accountContent,
                    )
                    Spacer(Modifier.height(16.dp))
                    BackButton(onExitToMainMenu, Modifier.fillMaxWidth().height(56.dp))
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(
                        horizontal = if (compactLandscape) 8.dp else 16.dp,
                        vertical = if (compactLandscape) 2.dp else 8.dp,
                    ),
                ) {
                    Column(
                        Modifier.weight(0.3f).fillMaxHeight().verticalScroll(sidebarScroll),
                    ) {
                        categories.forEach { category ->
                            CategoryRow(
                                category,
                                selected == category,
                                compactLandscape,
                                onClick = { controller.selectCategory(category) },
                            )
                        }
                        Spacer(Modifier.height(if (compactLandscape) 10.dp else 32.dp))
                        BackButton(
                            onExitToMainMenu,
                            Modifier.fillMaxWidth().height(if (compactLandscape) 42.dp else 56.dp),
                            compactLandscape,
                        )
                    }
                    SettingsPanel(
                        modifier = Modifier.weight(0.7f).fillMaxHeight()
                            .padding(start = if (compactLandscape) 12.dp else 24.dp),
                        state = state,
                        selected = selected,
                        controller = controller,
                        worldAvailable = worldAvailable,
                        contentScroll = contentScroll,
                        compact = compactLandscape,
                        onMusicVolumeApplied = onMusicVolumeApplied,
                        onSfxVolumeApplied = onSfxVolumeApplied,
                        onNpcDensityApplied = onNpcDensityApplied,
                        onNpcEmojiLodApplied = onNpcEmojiLodApplied,
                        onNpcFullEmojiApplied = onNpcFullEmojiApplied,
                        onControlsSaved = onControlsSaved,
                        onOptimizeApplied = onOptimizeApplied,
                        onLanguageApplied = onLanguageApplied,
                        accountContent = accountContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    modifier: Modifier,
    state: SettingsState,
    selected: SettingsCategory,
    controller: SettingsController,
    worldAvailable: Boolean,
    contentScroll: androidx.compose.foundation.ScrollState,
    compact: Boolean = false,
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
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A0A10))
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(if (compact) 12.dp else 16.dp)
            .verticalScroll(contentScroll),
    ) {
        Text(
            stringResource(selected.titleRes).uppercase(),
            fontSize = if (compact) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 24.dp))
        SettingsContent(
            state = state,
            category = selected,
            controller = controller,
            worldAvailable = worldAvailable,
            onMusicVolumeApplied = onMusicVolumeApplied,
            onSfxVolumeApplied = onSfxVolumeApplied,
            onNpcDensityApplied = onNpcDensityApplied,
            onNpcEmojiLodApplied = onNpcEmojiLodApplied,
            onNpcFullEmojiApplied = onNpcFullEmojiApplied,
            onControlsSaved = onControlsSaved,
            onOptimizeApplied = onOptimizeApplied,
            onLanguageApplied = onLanguageApplied,
            accountContent = accountContent,
        )
    }
}

@Composable
private fun CategoryTab(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .background(if (selected) Color(0xFF6B1C3A) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon.image(), null, tint = if (selected) Color.White else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(category.titleRes), color = if (selected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 4.dp)
            .clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .background(if (selected) Color(0xFF6B1C3A) else Color.Transparent)
            .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon.image(), null, tint = if (selected) Color.White else Color.Gray, modifier = Modifier.size(if (compact) 18.dp else 24.dp))
        Spacer(Modifier.width(if (compact) 8.dp else 12.dp))
        Text(stringResource(category.titleRes), color = if (selected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else 14.sp)
    }
}

private fun SettingsCategoryIcon.image(): ImageVector = when (this) {
    SettingsCategoryIcon.MAP -> Icons.Default.Map
    SettingsCategoryIcon.CONTROLS -> Icons.Default.Gamepad
    SettingsCategoryIcon.GAMEPLAY -> Icons.Default.SportsEsports
    SettingsCategoryIcon.INTERFACE -> Icons.Default.Layers
    SettingsCategoryIcon.AUDIO -> Icons.Default.VolumeUp
    SettingsCategoryIcon.ACCOUNT -> Icons.Default.AccountCircle
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier, compact: Boolean = false) {
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Button(
        onClick = onClick,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6B1C3A),
            contentColor = Color.White,
        ),
        modifier = modifier.shadow(8.dp, shape),
    ) {
        Text(
            stringResource(Res.string.menu_back),
            fontSize = if (compact) 12.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
    }
}
