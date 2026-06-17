package ovh.gabrielhuav.pow.features.main_menu.ui

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.MainMenuState
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.MainMenuViewModel

@Composable
fun MainMenuScreen(
    onNavigateToMap: (isMultiplayer: Boolean, playerName: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollectibles: () -> Unit,
    onNavigateToStory: () -> Unit,
    authManager: ovh.gabrielhuav.pow.data.auth.AuthManager? = null
) {
    val viewModel: MainMenuViewModel = viewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Nombre de jugador recordado entre sesiones (SharedPreferences). Se prellena al abrir.
    val settingsRepo = remember { ovh.gabrielhuav.pow.data.repository.SettingsRepository(context) }
    LaunchedEffect(Unit) {
        if (state.playerName.isBlank()) {
            val saved = settingsRepo.getPlayerName()
            if (saved.isNotBlank()) viewModel.updatePlayerName(saved)
        }
    }

    // GATE de Google Sign-In: el MULTIJUGADOR (y, a futuro, los LOGROS) exigen sesión.
    // El juego local / Modo Historia NO requieren login. Al volver del selector de Google,
    // si el login fue OK se continúa con el flujo normal de multijugador (warmup + nombre).
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        authManager?.handleSignInResult(result.data) { ok, err ->
            if (ok) {
                if (state.playerName.isBlank()) {
                    authManager.currentDisplayName()?.let { viewModel.updatePlayerName(it) }
                }
                viewModel.onMultiplayerPressed()
            } else if (!err.isNullOrBlank()) {
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            }
        }
    }
    // Acción del botón MULTIJUGADOR (y reintento):
    //  - Si Firebase NO está configurado en este build (sin google-services.json, p. ej. clones/PRs),
    //    se entra ANÓNIMO: los servidores en modo suave aceptan la conexión sin token.
    //  - Si Firebase está configurado y NO hay sesión, se abre Google Sign-In.
    //  - Si ya hay sesión, se sigue el flujo normal.
    val onMultiplayer: () -> Unit = {
        if (authManager == null || !authManager.isAvailable() || authManager.isSignedIn())
            viewModel.onMultiplayerPressed()
        else
            signInLauncher.launch(authManager.signInIntent())
    }

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
                        onNavigateToCollectibles = onNavigateToCollectibles,
                        onNavigateToStory = onNavigateToStory,
                        onMultiplayerClick = onMultiplayer
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
                    onNavigateToCollectibles = onNavigateToCollectibles,
                    onNavigateToStory = onNavigateToStory,
                    onMultiplayerClick = onMultiplayer
                )
            }
        }

        Text(
            text = stringResource(R.string.menu_version, BuildConfig.VERSION_NAME), color = Color.White.copy(alpha = 0.3f),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )

        // Chip de estado de sesión (abajo-izquierda): "Conectado: …" o "Modo local".
        val accountLabel = authManager?.currentEmail() ?: authManager?.currentDisplayName()
        Text(
            text = if (accountLabel != null) stringResource(R.string.menu_signed_in_as, accountLabel)
                   else stringResource(R.string.menu_local_mode),
            color = if (accountLabel != null) Color(0xFFD4AF37).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        )

        // ─── Diálogo de nombre del jugador (solo aparece tras warmup OK) ──
        if (state.showMultiplayerDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.updateShowMultiplayerDialog(false) },
                title = { Text(stringResource(R.string.menu_mp_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = state.playerName,
                        onValueChange = { viewModel.updatePlayerName(it) },
                        label = { Text(stringResource(R.string.menu_mp_username_label)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateShowMultiplayerDialog(false)
                            // "Jugador_" es un id generado de respaldo (no es texto de UI traducible).
                            val finalName = state.playerName.ifBlank { "Jugador_${(1000..9999).random()}" }
                            settingsRepo.savePlayerName(finalName)   // recuérdalo para la próxima vez
                            onNavigateToMap(true, finalName)
                        }
                    ) { Text(stringResource(R.string.menu_mp_connect)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.updateShowMultiplayerDialog(false) }) {
                        Text(stringResource(R.string.menu_cancel))
                    }
                }
            )
        }

        // ─── Spinner bloqueante mientras Render despierta ────────────────
        if (state.isWarmingUp) {
            WarmupDialog(
                secondsElapsed = state.warmupSeconds,
                onCancel = { viewModel.cancelWarmup() }
            )
        }

        // ─── Banner de error si el warmup hace timeout ───────────────────
        if (state.warmupFailed) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissWarmupError() },
                title = { Text(stringResource(R.string.menu_warmup_fail_title)) },
                text = { Text(stringResource(R.string.menu_warmup_fail_text)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.dismissWarmupError()
                        onMultiplayer() // reintenta (re-aplica el gate de sesión)
                    }) { Text(stringResource(R.string.menu_retry)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissWarmupError() }) {
                        Text(stringResource(R.string.menu_close))
                    }
                }
            )
        }
    }
}

@Composable
private fun TitleText(small: Boolean) {
    AutoResizeText(
        text = "POLITÉCNICO", targetFontSize = if (small) 36.sp else 42.sp,
        fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 0.1.em,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
    )
    AutoResizeText(
        text = "OPEN WORLD", targetFontSize = if (small) 22.sp else 26.sp,
        fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37), letterSpacing = 0.3.em,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Texto de una sola línea que reduce su tamaño de fuente automáticamente hasta
 * caber en el ancho disponible. Garantiza que el contenido NUNCA se corte ni
 * salte de línea, sin importar el tamaño/relación de aspecto de la pantalla.
 * El letterSpacing se expresa en `em` para que escale junto con la fuente.
 */
@Composable
private fun AutoResizeText(
    text: String,
    targetFontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight,
    letterSpacing: TextUnit,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text, targetFontSize) { mutableStateOf(targetFontSize) }
    var readyToDraw by remember(text, targetFontSize) { mutableStateOf(false) }
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        letterSpacing = letterSpacing,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = modifier.drawWithContent { if (readyToDraw) drawContent() },
        onTextLayout = { result ->
            if (result.didOverflowWidth || result.lineCount > 1) {
                fontSize *= 0.92f
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
fun MenuButtonsList(
    state: MainMenuState,
    viewModel: MainMenuViewModel,
    onNavigateToMap: (isMultiplayer: Boolean, playerName: String?) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCollectibles: () -> Unit,
    onNavigateToStory: () -> Unit,
    onMultiplayerClick: () -> Unit = { viewModel.onMultiplayerPressed() }
) {
    // MUNDO LIBRE: el open world sin campaña (antes "Iniciar Juego"). Spawn por defecto.
    MenuButton(
        text = stringResource(R.string.menu_start_game),
        onClick = {
            viewModel.onStartGame()
            onNavigateToMap(false, null)
        },
        enabled = !state.isLoading && !state.isWarmingUp
    )
    Spacer(Modifier.height(16.dp))

    // MODO HISTORIA: abre la pantalla de campaña (prólogo + elegir escuela + cargar partida).
    MenuButton(
        text = stringResource(R.string.menu_load_game),
        onClick = onNavigateToStory,
        enabled = !state.isWarmingUp
    )
    Spacer(Modifier.height(16.dp))

    // El botón MULTIJUGADOR dispara el warmup ANTES de mostrar el diálogo
    // de nombre. Mientras dura el warmup queda deshabilitado para evitar
    // que el usuario lance dos pings en paralelo.
    MenuButton(
        text = stringResource(R.string.menu_multiplayer),
        onClick = onMultiplayerClick,
        enabled = !state.isWarmingUp
    )
    Spacer(Modifier.height(16.dp))

    MenuButton(
        text = stringResource(R.string.menu_settings),
        onClick = onNavigateToSettings,
        enabled = !state.isWarmingUp,
        color = Color(0xFF6B1C3A)
    )
    Spacer(Modifier.height(16.dp))

    MenuButton(
        text = stringResource(R.string.menu_collectibles),
        onClick = onNavigateToCollectibles,
        enabled = !state.isWarmingUp,
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

/**
 * Diálogo modal NO descartable (solo el botón CANCELAR cierra) que muestra
 * el progreso del warmup del servidor de Render. Estilo coherente con el
 * resto del menú: gradiente vino + acento dorado, esquinas cortadas.
 */
@Composable
private fun WarmupDialog(secondsElapsed: Int, onCancel: () -> Unit) {
    Dialog(
        onDismissRequest = { /* no descartable por fuera */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF1A0A10), shape = shape)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.menu_warmup_title),
                    color = Color(0xFFD4AF37),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                CircularProgressIndicator(
                    color = Color(0xFFD4AF37),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = stringResource(R.string.menu_warmup_text, secondsElapsed),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Text(
                    text = stringResource(R.string.menu_warmup_hint),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.menu_cancel_caps), fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                }
            }
        }
    }
}
