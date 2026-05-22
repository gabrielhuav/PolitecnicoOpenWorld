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
    onNavigateToMap: (isMultiplayer: Boolean, playerName: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollectibles: () -> Unit
) {
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
                        onNavigateToSettings = onNavigateToSettings,
                        onNavigateToCollectibles = onNavigateToCollectibles
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
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToCollectibles = onNavigateToCollectibles
                )
            }
        }

        Text(
            text = "v0.1.0 - ESCOM Edition", color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )

        if (state.showMultiplayerDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.updateShowMultiplayerDialog(false) },
                title = { Text("Conectar a Servidor") },
                text = {
                    OutlinedTextField(
                        value = state.playerName,
                        onValueChange = { viewModel.updatePlayerName(it) },
                        label = { Text("Nombre de Usuario") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateShowMultiplayerDialog(false)
                            val finalName = state.playerName.ifBlank { "Jugador_${(1000..9999).random()}" }
                            onNavigateToMap(true, finalName)
                        }
                    ) { Text("Conectar") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.updateShowMultiplayerDialog(false) }) {
                        Text("Cancelar")
                    }
                }
            )
        }
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
fun MenuButtonsList(
    state: MainMenuState,
    viewModel: MainMenuViewModel,
    onNavigateToMap: (isMultiplayer: Boolean, playerName: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollectibles: () -> Unit
) {
    MenuButton(
        text = "INICIAR JUEGO",
        onClick = {
            viewModel.onStartGame()
            onNavigateToMap(false, null)
        },
        enabled = !state.isLoading
    )
    Spacer(Modifier.height(16.dp))

    MenuButton(text = "CARGAR PARTIDA", onClick = {}, enabled = false)
    Spacer(Modifier.height(16.dp))

    MenuButton(
        text = "MULTIJUGADOR",
        onClick = { viewModel.updateShowMultiplayerDialog(true) },
        enabled = true
    )
    Spacer(Modifier.height(16.dp))

    MenuButton(
        text = "AJUSTES",
        onClick = onNavigateToSettings,
        enabled = true,
        color = Color(0xFF6B1C3A)
    )
    Spacer(Modifier.height(16.dp))

    MenuButton(
        text = "COLECCIONABLES",
        onClick = onNavigateToCollectibles,
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