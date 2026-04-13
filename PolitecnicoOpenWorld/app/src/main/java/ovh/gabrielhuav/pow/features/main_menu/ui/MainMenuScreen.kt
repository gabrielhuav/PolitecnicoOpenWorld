package ovh.gabrielhuav.pow.features.main_menu.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun MainMenuScreen(
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // Instanciamos el ViewModel del menú principal
    val viewModel: MainMenuViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) { TitleText(small = true) }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MenuButtonsList(
                        state = state,
                        viewModel = viewModel,
                        onNavigateToMap = onNavigateToMap,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TitleText(small = false)
                Spacer(modifier = Modifier.height(48.dp))
                MenuButtonsList(
                    state = state,
                    viewModel = viewModel,
                    onNavigateToMap = onNavigateToMap,
                    onNavigateToSettings = onNavigateToSettings
                )
            }
        }

        Text(
            text = "v0.1.0 - ESCOM Edition", color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
private fun TitleText(small: Boolean) {
    Text(
        text = "POLITÉCNICO", fontSize = if (small) 36.sp else 42.sp,
        fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 4.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Text(
        text = "OPEN WORLD", fontSize = if (small) 22.sp else 26.sp,
        fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37), letterSpacing = 8.sp
    )
}

@Composable
private fun MenuButtonsList(
    state: MainMenuState,
    viewModel: MainMenuViewModel,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    MenuButton(
        text = "INICIAR JUEGO",
        onClick = {
            viewModel.onStartGame()
            onNavigateToMap()
        },
        enabled = !state.isLoading
    )
    Spacer(Modifier.height(16.dp))
    MenuButton(text = "CARGAR PARTIDA", onClick = {}, enabled = false)
    Spacer(Modifier.height(16.dp))
    MenuButton(text = "MULTIJUGADOR", onClick = {}, enabled = false)
    Spacer(Modifier.height(16.dp))

    // El botón ahora simplemente llama a la navegación
    MenuButton(
        text = "AJUSTES",
        onClick = onNavigateToSettings,
        enabled = true,
        color = Color(0xFF6B1C3A)
    )
}

@Composable
fun MenuButton(text: String, onClick: () -> Unit, enabled: Boolean = true, color: Color = Color(0xFF6B1C3A)) {
    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    Button(
        onClick = onClick, enabled = enabled, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White, disabledContainerColor = Color(0xFF2A1C21), disabledContentColor = Color.Gray),
        modifier = Modifier.fillMaxWidth(0.85f).height(56.dp).shadow(elevation = if (enabled) 8.dp else 0.dp, shape = shape)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
}