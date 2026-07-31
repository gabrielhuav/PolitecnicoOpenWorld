package ovh.gabrielhuav.pow.features.main_menu.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import ovh.gabrielhuav.pow.data.repository.SettingsRepository

/**
 * Implementación de iOS de [MainMenuController] (patrón nº 4 de
 * `README for IAS/10_ARQUITECTURA_SEPARACION.md` §2bis).
 *
 * Es deliberadamente **mínima**: en iOS el menú solo ofrece AJUSTES, COLECCIONABLES y
 * HUELUM VS. GOYA (ver `PowModos.kt`), así que todo lo de multijugador —diálogo de nombre,
 * calentamiento del servidor de Render, Google Sign-In— **no tiene a quién servir**.
 *
 * Los métodos de esa rama no lanzan ni registran nada: la pantalla NUNCA los llama porque el botón
 * de multijugador ni siquiera se pinta. Están para cumplir el contrato, no para usarse.
 *
 * @param settings el MISMO almacén (`NSUserDefaults`) que usa `IosStreetFighterEnvironment`. Por eso
 *   el nombre del jugador y el Modo Desarrollador se comparten entre Ajustes y la pelea sin código
 *   de sincronización.
 */
class IosMainMenuController(
    private val settings: SettingsRepository,
) : MainMenuController {

    private val _state = MutableStateFlow(MainMenuUiState())
    override val state: StateFlow<MainMenuUiState> = _state

    /**
     * ⚠️ `null` A PROPÓSITO: la App Store no admite etiquetas PRE-ALPHA/BETA en una ficha publicada
     * (decisión del dueño, 2026-07-28). En Android se sigue mostrando.
     */
    override val versionName: String? = null

    /** Por lo mismo que [versionName]: nada de esquinas PRE-ALPHA/BETA en iOS. */
    override val mostrarInsignias: Boolean = false

    override fun onStartGame() = Unit
    override fun onMultiplayerPressed() = Unit
    override fun updateShowMultiplayerDialog(mostrar: Boolean) = Unit
    override fun cancelWarmup() = Unit
    override fun dismissWarmupError() = Unit

    override fun updatePlayerName(nombre: String) {
        _state.value = _state.value.copy(playerName = nombre)
    }

    override fun nombreGuardado(): String = settings.getPlayerName()
    override fun guardarNombre(nombre: String) = settings.savePlayerName(nombre)
}
