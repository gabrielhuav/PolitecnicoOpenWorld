package ovh.gabrielhuav.pow.features.main_menu.ui

import kotlinx.coroutines.flow.StateFlow

/**
 * 🍏 Lo que el MENÚ PRINCIPAL necesita de la plataforma, y nada más.
 *
 * Mismo patrón que `StreetFighterController`, y por el mismo motivo: `MainMenuScreen` usaba
 * `hiltViewModel()`, `LocalContext`, `SettingsRepository` y `BuildConfig`. Nada de eso existe en
 * `commonMain`, así que se invierte la dependencia: la pantalla pide, la plataforma provee.
 *
 * En Android lo implementa un adaptador que envuelve el `MainMenuViewModel` de Hilt de siempre,
 * así que **la app en producción no cambia de comportamiento**. En iOS lo implementa una clase
 * mínima: allí no hay multijugador ni calentamiento de servidor que gestionar.
 */
interface MainMenuController {

    val state: StateFlow<MainMenuUiState>

    /**
     * Nombre de versión para la esquina (p. ej. `"1.0.0.14"`), o **`null` para no mostrar nada**.
     *
     * Devuelve solo el número: el texto que lo envuelve ("PRE-ALPHA - v%s") sigue viviendo en
     * `Res.string.menu_version`, que está traducido. Si se formatease aquí, el inglés se perdería.
     *
     * ⚠️ En iOS va `null` A PROPÓSITO (decisión del dueño, 2026-07-28): la App Store no admite
     * etiquetas tipo PREALPHA/BETA en una ficha publicada. En Android se mantiene tal cual.
     */
    val versionName: String?

    /**
     * Si se pintan las insignias de esquina (**PRE-ALPHA**, **BETA**) sobre los botones.
     *
     * ⚠️ `false` en iOS, por lo mismo que [etiquetaVersion]. Los modos que hoy llevan insignia
     * ALPHA (Mundo Libre, Historia, Multijugador) ya se esconden solos en iOS vía
     * `PowModo.disponible()`; esto apaga además la **BETA** de "Huelum vs. Goya".
     */
    val mostrarInsignias: Boolean

    /**
     * Si MUNDO LIBRE, **aun estando en obras, tiene algo que enseñar**.
     *
     * `true` en iOS: el mundo todavía no se juega, pero el **mapa ya se puede ver** (Leaflet en
     * `WKWebView`, el mismo HTML que Android). El botón abre esa vista previa en vez del aviso de
     * "en obras", y la insignia EN OBRAS **se queda puesta** porque sigue sin ser jugable.
     *
     * En Android da igual lo que valga: allí `PowModo.MUNDO_LIBRE.enObras()` es `false` y el botón
     * entra al mundo de verdad.
     */
    val mundoTieneVistaPrevia: Boolean get() = false

    fun onStartGame()
    fun onMultiplayerPressed()
    fun updatePlayerName(nombre: String)
    fun updateShowMultiplayerDialog(mostrar: Boolean)
    fun cancelWarmup()
    fun dismissWarmupError()

    /** Nombre recordado entre sesiones. Vacío si no hay ninguno. */
    fun nombreGuardado(): String
    fun guardarNombre(nombre: String)
}

/**
 * El estado que la pantalla realmente consume. Es un SUBCONJUNTO del `MainMenuState` de Android:
 * aquí no entra `selectedProvider` ni los widgets del mapa, que son del mundo abierto y la pantalla
 * no los mira.
 */
data class MainMenuUiState(
    val isLoading: Boolean = false,
    val showMultiplayerDialog: Boolean = false,
    val playerName: String = "",
    val isWarmingUp: Boolean = false,
    val warmupSeconds: Int = 0,
    val warmupFailed: Boolean = false,
)
