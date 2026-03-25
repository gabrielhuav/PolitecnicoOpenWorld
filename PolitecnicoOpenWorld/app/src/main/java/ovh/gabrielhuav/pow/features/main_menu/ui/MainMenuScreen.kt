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
    viewModel: MainMenuViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    // Detectar la orientación de la pantalla
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF3B0D1B),
            Color(0xFF0D0D11)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        if (isLandscape) {
            // DISEÑO HORIZONTAL: Pantalla dividida a la mitad
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mitad Izquierda: Títulos
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "POLITÉCNICO",
                        fontSize = 36.sp, // Ligeramente más pequeño en horizontal
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "OPEN WORLD",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37),
                        letterSpacing = 8.sp,
                    )
                }

                // Mitad Derecha: Lista de Botones con Scroll
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()), // Permite deslizar si faltan botones
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MenuButtonsList(state, viewModel, onNavigateToMap)
                }
            }
        } else {
            // DISEÑO VERTICAL: Todo apilado con scroll
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .verticalScroll(rememberScrollState()), // Soluciona recortes en vertical
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "POLITÉCNICO",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 4.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "OPEN WORLD",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37),
                    letterSpacing = 8.sp,
                    modifier = Modifier.padding(bottom = 48.dp)
                )

                MenuButtonsList(state, viewModel, onNavigateToMap)
            }
        }

        // Versión en la esquina inferior derecha
        Text(
            text = "v0.1.0 - ESCOM Edition",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

// Extraemos los botones a una función separada para no repetir código entre las orientaciones
@Composable
fun MenuButtonsList(
    state: MainMenuState,
    viewModel: MainMenuViewModel,
    onNavigateToMap: () -> Unit
) {
    MenuButton(
        text = "INICIAR JUEGO",
        onClick = {
            viewModel.onStartGame()
            onNavigateToMap()
        },
        enabled = !state.isLoading
    )
    Spacer(modifier = Modifier.height(16.dp))
    MenuButton(
        text = "CARGAR PARTIDA",
        onClick = { /* TODO */ },
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp))
    MenuButton(
        text = "MULTIJUGADOR",
        onClick = { /* TODO */ },
        enabled = false
    )
    Spacer(modifier = Modifier.height(16.dp))
    MenuButton(
        text = "AJUSTES",
        onClick = { /* TODO */ },
        enabled = false
    )
}

// Tu componente MenuButton se queda igual
@Composable
fun MenuButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val buttonShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

    Button(
        onClick = onClick,
        enabled = enabled,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6B1C3A),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2A1C21),
            disabledContentColor = Color.Gray
        ),
        modifier = Modifier
            .fillMaxWidth(0.85f) // Un poco más ancho en relación a su contenedor
            .height(56.dp)
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = buttonShape
            )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}