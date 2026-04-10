package ovh.gabrielhuav.pow.features.main_menu.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.MainMenuState
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.MainMenuViewModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider

@Composable
fun MainMenuScreen(
    onNavigateToMap: (MapProvider) -> Unit,   // ← pasa el provider elegido
    viewModel: MainMenuViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Controla si el panel de ajustes está expandido
    var showSettings by remember { mutableStateOf(false) }

    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    Box(modifier = Modifier.fillMaxSize().background(bg)) {

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Izquierda: título
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TitleText(small = true)
                }
                // Derecha: botones + ajustes
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MenuButtonsList(
                        state       = state,
                        viewModel   = viewModel,
                        showSettings = showSettings,
                        onToggleSettings = { showSettings = !showSettings },
                        onNavigateToMap  = { onNavigateToMap(state.selectedProvider) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TitleText(small = false)
                Spacer(modifier = Modifier.height(48.dp))
                MenuButtonsList(
                    state        = state,
                    viewModel    = viewModel,
                    showSettings = showSettings,
                    onToggleSettings = { showSettings = !showSettings },
                    onNavigateToMap  = { onNavigateToMap(state.selectedProvider) }
                )
            }
        }

        Text(
            text  = "v0.1.0 - ESCOM Edition",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun TitleText(small: Boolean) {
    Text(
        text = "POLITÉCNICO",
        fontSize = if (small) 36.sp else 42.sp,
        fontWeight = FontWeight.Black,
        color = Color.White,
        letterSpacing = 4.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text = "OPEN WORLD",
        fontSize = if (small) 22.sp else 26.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFFD4AF37),
        letterSpacing = 8.sp
    )
}

@Composable
private fun MenuButtonsList(
    state: MainMenuState,
    viewModel: MainMenuViewModel,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    onNavigateToMap: (MapProvider) -> Unit
) {
    MenuButton(
        text    = "INICIAR JUEGO",
        onClick = { viewModel.onStartGame(); onNavigateToMap(state.selectedProvider) },
        enabled = !state.isLoading
    )
    Spacer(Modifier.height(16.dp))
    MenuButton(text = "CARGAR PARTIDA", onClick = {}, enabled = false)
    Spacer(Modifier.height(16.dp))
    MenuButton(text = "MULTIJUGADOR", onClick = {}, enabled = false)
    Spacer(Modifier.height(16.dp))

    // ── Botón AJUSTES — ahora funcional ──────────────────────────────────────
    MenuButton(
        text    = if (showSettings) "CERRAR AJUSTES" else "AJUSTES",
        onClick = onToggleSettings,
        enabled = true,
        color   = if (showSettings) Color(0xFF3A3A3A) else Color(0xFF6B1C3A)
    )

    // ── Panel de ajustes desplegable ─────────────────────────────────────────
    AnimatedVisibility(
        visible = showSettings,
        enter   = fadeIn() + slideInVertically(),
        exit    = fadeOut() + slideOutVertically()
    ) {
        SettingsPanel(
            selectedProvider = state.selectedProvider,
            onProviderChange = { viewModel.setMapProvider(it) }
        )
    }
}

@Composable
private fun SettingsPanel(
    selectedProvider: MapProvider,
    onProviderChange: (MapProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A0A10))
            .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AJUSTES DE PARTIDA",
            color = Color(0xFFD4AF37),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        // Selector de proveedor de mapa
        Text(
            text = "Proveedor de mapa",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor    = Color.White,
                    containerColor  = Color(0xFF2A1C21)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6B1C3A)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(selectedProvider.displayName, Modifier.weight(1f), fontSize = 13.sp)
                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFD4AF37))
            }
            DropdownMenu(
                expanded        = expanded,
                onDismissRequest = { expanded = false },
                modifier        = Modifier.fillMaxWidth(0.8f).background(Color(0xFF2A1C21))
            ) {
                MapProvider.entries.forEach { p ->
                    DropdownMenuItem(
                        text    = { Text(p.displayName, color = Color.White, fontSize = 13.sp) },
                        onClick = { onProviderChange(p); expanded = false }
                    )
                }
            }
        }

        // Nota informativa
        Text(
            text = "El proveedor se puede cambiar durante la partida desde el menú ⚙",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
fun MenuButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = Color(0xFF6B1C3A)
) {
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Button(
        onClick  = onClick,
        enabled  = enabled,
        shape    = shape,
        colors   = ButtonDefaults.buttonColors(
            containerColor         = color,
            contentColor           = Color.White,
            disabledContainerColor = Color(0xFF2A1C21),
            disabledContentColor   = Color.Gray
        ),
        modifier = Modifier
            .fillMaxWidth(0.85f).height(56.dp)
            .shadow(elevation = if (enabled) 8.dp else 0.dp, shape = shape)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}