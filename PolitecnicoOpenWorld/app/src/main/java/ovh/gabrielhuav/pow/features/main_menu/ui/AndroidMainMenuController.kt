package ovh.gabrielhuav.pow.features.main_menu.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ovh.gabrielhuav.pow.BuildConfig
import ovh.gabrielhuav.pow.data.repository.SettingsRepository
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.MainMenuViewModel

/**
 * Adaptador de ANDROID para [MainMenuController] (patrón nº 4 de
 * `README for IAS/10_ARQUITECTURA_SEPARACION.md` §2bis).
 *
 * **Es una envoltura, no una reimplementación**: por dentro sigue el `MainMenuViewModel` de Hilt de
 * siempre y el `SettingsRepository` de siempre. Por eso la app en producción no cambia de
 * comportamiento — lo único que cambió es QUIÉN se lo pide a quién.
 *
 * @param scope el `viewModelScope` del propio VM: así el mapeo de estado muere con la pantalla.
 */
class AndroidMainMenuController(
    private val viewModel: MainMenuViewModel,
    private val settings: SettingsRepository,
    scope: CoroutineScope,
) : MainMenuController {

    private val _state = MutableStateFlow(MainMenuUiState())
    override val state: StateFlow<MainMenuUiState> = _state

    init {
        // Se copia solo lo que la pantalla consume; el resto del estado del VM (proveedor de mapa,
        // widgets…) es del mundo abierto y no pinta nada aquí.
        scope.launch {
            viewModel.state.collect { s ->
                _state.value = MainMenuUiState(
                    isLoading = s.isLoading,
                    showMultiplayerDialog = s.showMultiplayerDialog,
                    playerName = s.playerName,
                    isWarmingUp = s.isWarmingUp,
                    warmupSeconds = s.warmupSeconds,
                    warmupFailed = s.warmupFailed,
                )
            }
        }
    }

    /** En Android SÍ se muestra: es la build de Play, donde la etiqueta es informativa y deseada. */
    override val versionName: String get() = BuildConfig.VERSION_NAME

    override val mostrarInsignias: Boolean get() = true

    override fun onStartGame() = viewModel.onStartGame()
    override fun onMultiplayerPressed() = viewModel.onMultiplayerPressed()
    override fun updatePlayerName(nombre: String) = viewModel.updatePlayerName(nombre)
    override fun updateShowMultiplayerDialog(mostrar: Boolean) =
        viewModel.updateShowMultiplayerDialog(mostrar)
    override fun cancelWarmup() = viewModel.cancelWarmup()
    override fun dismissWarmupError() = viewModel.dismissWarmupError()

    override fun nombreGuardado(): String = settings.getPlayerName()
    override fun guardarNombre(nombre: String) = settings.savePlayerName(nombre)
}

/**
 * Chip de sesión de la esquina del menú ("Conectado: …" / "Modo local").
 *
 * Vive en `:app` porque depende de Firebase, que es Android-only. La pantalla compartida lo recibe
 * como **slot**, así que en iOS simplemente no se pinta nada y no hay dos layouts que mantener.
 */
@androidx.compose.runtime.Composable
fun ChipDeCuenta(authManager: ovh.gabrielhuav.pow.data.auth.AuthManager?) {
    val etiqueta = authManager?.currentEmail() ?: authManager?.currentDisplayName()
    androidx.compose.material3.Text(
        text = if (etiqueta != null) {
            androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.menu_signed_in_as, etiqueta)
        } else {
            androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.menu_local_mode)
        },
        color = if (etiqueta != null) {
            androidx.compose.ui.graphics.Color(0xFFD4AF37).copy(alpha = 0.7f)
        } else {
            androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
        },
        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
    )
}
