package ovh.gabrielhuav.pow.features.settings.ui

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSaveClicked: () -> Unit,
    onControlTypeChanged: (ControlType) -> Unit,
    onControlsScaleChanged: (Float) -> Unit,
    onSwapControlsToggled: (Boolean) -> Unit,
    onFreeNavigationToggled: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onExitToMainMenu: () -> Unit
) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    val sidebarScrollState = rememberScrollState()
    val contentScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().background(bg).systemBarsPadding()) {

        // Barra Superior
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Text(
                text = "AJUSTES DEL JUEGO",
                color = Color(0xFFD4AF37),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            val configuration = LocalConfiguration.current
            val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

            if (isPortrait) {
                // DISEÑO VERTICAL (PORTRAIT)
                Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categories = listOf(
                            SettingsCategory.Map,
                            SettingsCategory.Controls,
                            SettingsCategory.Gameplay,
                            SettingsCategory.Interface
                        )
                        categories.forEach { category ->
                            CategoryItemHorizontal(
                                category = category,
                                isSelected = state.selectedCategory == category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                        Text(
                            state.selectedCategory.title.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        SettingsContent(
                            state, onMapProviderChanged, onCacheToggled, onFpsToggled,
                            onSaveClicked, onControlTypeChanged, onControlsScaleChanged,
                            onSwapControlsToggled, onFreeNavigationToggled
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onExitToMainMenu,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f))
                    ) {
                        Text("SALIR AL MENÚ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // DISEÑO HORIZONTAL (LANDSCAPE)
                Row(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.weight(0.3f).fillMaxHeight().verticalScroll(sidebarScrollState)) {
                        val categories = listOf(
                            SettingsCategory.Map,
                            SettingsCategory.Controls,
                            SettingsCategory.Gameplay,
                            SettingsCategory.Interface
                        )
                        categories.forEach { category ->
                            CategoryItem(
                                category = category,
                                isSelected = state.selectedCategory == category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        OutlinedButton(
                            onClick = onExitToMainMenu,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                            border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f))
                        ) { Text("SALIR AL MENÚ", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }

                    Column(
                        modifier = Modifier.weight(0.7f).fillMaxHeight().padding(start = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1A0A10))
                            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(24.dp)
                            .verticalScroll(contentScrollState)
                    ) {
                        Text(
                            state.selectedCategory.title.uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        SettingsContent(
                            state, onMapProviderChanged, onCacheToggled, onFpsToggled,
                            onSaveClicked, onControlTypeChanged, onControlsScaleChanged,
                            onSwapControlsToggled, onFreeNavigationToggled
                        )
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
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(if (isSelected) Color(0xFF6B1C3A) else Color.Transparent)
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
            text = category.title,
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun CategoryItem(category: SettingsCategory, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(if (isSelected) Color(0xFF6B1C3A) else Color.Transparent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(category.icon, contentDescription = null, tint = if (isSelected) Color.White else Color.Gray)
        Spacer(Modifier.width(12.dp))
        Text(category.title, color = if (isSelected) Color.White else Color.Gray, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsContent(
    state: SettingsState,
    onMapProviderChanged: (MapProvider) -> Unit,
    onCacheToggled: (Boolean) -> Unit,
    onFpsToggled: (Boolean) -> Unit,
    onSaveClicked: () -> Unit,
    onControlTypeChanged: (ControlType) -> Unit,
    onControlsScaleChanged: (Float) -> Unit,
    onSwapControlsToggled: (Boolean) -> Unit,
    onFreeNavigationToggled: (Boolean) -> Unit
) {
    when (state.selectedCategory) {
        is SettingsCategory.Map -> MapProviderSetting(state.mapProvider, onMapProviderChanged)
        is SettingsCategory.Controls -> ControlsSettingsConfig(
            state.controlType, state.controlsScale, state.swapControls,
            onControlTypeChanged, onControlsScaleChanged, onSwapControlsToggled, onSaveClicked
        )
        is SettingsCategory.Gameplay -> GameplayCategoryContent(
            provider = state.mapProvider,
            freeNavigation = state.freeNavigation,
            onFreeNavigationToggled = onFreeNavigationToggled
        )
        is SettingsCategory.Interface -> DiagnosticWidgetsSetting(
            state.showCacheWidget, state.showFpsWidget, onCacheToggled, onFpsToggled
        )
    }
}

@Composable
private fun MapProviderSetting(current: MapProvider, onChanged: (MapProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var tempProvider by remember(current) { mutableStateOf(current) }
    val hasPendingChange = tempProvider != current

    Column {
        Text("Proveedor de Mapa", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    containerColor = Color(0xFF2A1C21)
                ),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A))
            ) {
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
                            tempProvider = provider
                            expanded = false
                        }
                    )
                }
            }
        }

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
                Text("Cambiar Mapa", color = if (hasPendingChange) Color.White else Color.Gray)
            }

            OutlinedButton(
                onClick = { tempProvider = current },
                enabled = hasPendingChange,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.Gray
                ),
                border = BorderStroke(
                    1.dp,
                    if (hasPendingChange) Color(0xFFD4AF37) else Color(0xFF2A1C21)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Restaurar Mapa")
            }
        }
    }
}

@Composable
private fun ControlsSettingsConfig(
    type: ControlType,
    scale: Float,
    isSwapped: Boolean,
    onTypeChanged: (ControlType) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onSwapChanged: (Boolean) -> Unit,
    onSaveClicked: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    val maxScale = if (isPortrait) 1.0f else 1.4f
    val safeScale = scale.coerceAtMost(maxScale)

    LaunchedEffect(scale, maxScale) {
        if (scale > maxScale) {
            onScaleChanged(safeScale)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

        Column {
            Text("Estilo de Movimiento", color = Color.White, fontWeight = FontWeight.Bold)
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

        Column {
            Text(
                "Tamaño en Pantalla: ${(safeScale * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isPortrait) "Límite ajustado a 100% por modo vertical."
                else "No superará los límites de la pantalla.",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Slider(
                value = safeScale,
                onValueChange = onScaleChanged,
                valueRange = 0.6f..maxScale,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4AF37),
                    activeTrackColor = Color(0xFF6B1C3A)
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Intercambiar Lados", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Mueve la acción a la izquierda", color = Color.Gray, fontSize = 12.sp)
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSaveClicked,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
        ) {
            Text("GUARDAR CONFIGURACIÓN", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameplayCategoryContent(
    provider: MapProvider,
    freeNavigation: Boolean,
    onFreeNavigationToggled: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // El toggle solo aparece si el proveedor actual soporta navegación libre.
        // En esta fase inicial, solo MapProvider.OSM tiene supportsFreeNavigation = true.
        if (provider.supportsFreeNavigation) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Navegación libre",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Desplaza con un dedo, zoom con dos. La ubicación del jugador se conserva.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = freeNavigation,
                    onCheckedChange = onFreeNavigationToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFD4AF37),
                        checkedTrackColor = Color(0xFF6B1C3A)
                    )
                )
            }
        } else {
            Text(
                "No hay ajustes de jugabilidad disponibles para el proveedor de mapa actual (${provider.displayName}).",
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DiagnosticWidgetsSetting(
    cacheEnabled: Boolean,
    fpsEnabled: Boolean,
    onCacheToggled: (Boolean) -> Unit,
    onFpsToggled: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Widget de caché", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Muestra fuente de datos", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(
                checked = cacheEnabled,
                onCheckedChange = onCacheToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD4AF37),
                    checkedTrackColor = Color(0xFF6B1C3A)
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Widget de FPS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Mide el rendimiento gráfico", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(
                checked = fpsEnabled,
                onCheckedChange = onFpsToggled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFD4AF37),
                    checkedTrackColor = Color(0xFF6B1C3A)
                )
            )
        }
    }
}