package ovh.gabrielhuav.pow.features.settings.ui

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
import ovh.gabrielhuav.pow.features.settings.models.SettingsCategory
import ovh.gabrielhuav.pow.features.settings.viewmodel.SettingsState

@Composable
fun SettingsScreen(
    state: SettingsState,
    onCategorySelected: (SettingsCategory) -> Unit,
    onMapProviderChanged: (MapProvider) -> Unit,
    onCacheToggled: (Boolean) -> Unit,
    onFpsToggled: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    onExitToMainMenu: () -> Unit
) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    // Estados para recordar la posición del scroll
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
            // Columna Izquierda: Categorías (Con Scroll)
            Column(
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
                    .verticalScroll(sidebarScrollState) // Habilita el desplazamiento vertical
            ) {
                val categories = listOf(
                    SettingsCategory.Controls,
                    SettingsCategory.Gameplay,
                    SettingsCategory.Interface,
                    SettingsCategory.Map
                )
                categories.forEach { category ->
                    CategoryItem(
                        category = category,
                        isSelected = state.selectedCategory == category,
                        onClick = { onCategorySelected(category) }
                    )
                }

                // Usamos altura fija en lugar de weight porque estamos dentro de un Scroll
                Spacer(modifier = Modifier.height(32.dp))

                // BOTÓN DE SALIR AL MENÚ PRINCIPAL
                OutlinedButton(
                    onClick = onExitToMainMenu,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.5f))
                ) {
                    Text("SALIR AL MENÚ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Columna Derecha: Contenido (Con Scroll)
            Column(
                modifier = Modifier
                    .weight(0.7f)
                    .fillMaxHeight()
                    .padding(start = 24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A0A10))
                    .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(24.dp)
                    .verticalScroll(contentScrollState) // Habilita el desplazamiento vertical
            ) {
                Text(
                    text = state.selectedCategory.title.uppercase(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))

                when (state.selectedCategory) {
                    is SettingsCategory.Map -> {
                        MapProviderSetting(state.mapProvider, onMapProviderChanged)
                    }
                    is SettingsCategory.Interface -> {
                        DiagnosticWidgetsSetting(
                            cacheEnabled = state.showCacheWidget,
                            fpsEnabled = state.showFpsWidget,
                            onCacheToggled = onCacheToggled,
                            onFpsToggled = onFpsToggled
                        )
                    }
                    else -> {
                        Text("Sin ajustes disponibles actualmente.", color = Color.Gray)
                    }
                }
            }
        }
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
private fun MapProviderSetting(current: MapProvider, onChanged: (MapProvider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Proveedor de Mapa", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White, containerColor = Color(0xFF2A1C21)),
                border = BorderStroke(1.dp, Color(0xFF6B1C3A))
            ) {
                Text(current.displayName, Modifier.weight(1f))
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
                        onClick = { onChanged(provider); expanded = false }
                    )
                }
            }
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
        // Toggle de Caché
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Widget de caché", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Muestra fuente de datos", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(
                checked = cacheEnabled,
                onCheckedChange = onCacheToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }

        // Toggle de FPS
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Widget de FPS", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Mide el rendimiento gráfico", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
            Switch(
                checked = fpsEnabled,
                onCheckedChange = onFpsToggled,
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD4AF37), checkedTrackColor = Color(0xFF6B1C3A))
            )
        }
    }
}