package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Avisos (`interactionPrompt`) cuyo texto sale de `composeResources`.
 *
 * ⚠️ **Compose Resources NO puede resolver un `StringResource` de forma síncrona**: su única
 * API fuera de la composición es `getString`, que es `suspend`. Antes esto lo hacía
 * `getLocalizedString(R.string.x)`, que sí era síncrono porque bajaba a `Context` de Android —
 * justo lo que no existe en iOS. Así que el TEXTO se publica un frame después.
 *
 * ⚠️ **Las banderas de juego se ponen ANTES y por separado.** `objectiveDone` la lee la lógica
 * de misión en el mismo tick; si viajara dentro de este `launch`, ese tick vería el valor viejo
 * y la misión avanzaría tarde. Aquí solo viaja el texto, que es un aviso para el jugador y a
 * quien un frame le da igual. **No metas banderas en este helper.**
 *
 * No se usa `runBlocking`: en Kotlin/Native sobre el hilo principal es un bloqueo de verdad.
 */
internal fun WorldMapViewModel.avisarConTitulo(prefijo: String, titulo: StringResource) {
    viewModelScope.launch {
        val texto = prefijo + getString(titulo)
        _uiState.update { it.copy(interactionPrompt = texto) }
    }
}

/**
 * Igual que [avisarConTitulo] pero para los avisos del TP del Modo Desarrollador, que además
 * se auto-limpian a los ~4 s ([devShowTpPromptInterno]).
 */
internal fun WorldMapViewModel.devAvisarConTitulo(prefijo: String, titulo: StringResource?, respaldo: String) {
    viewModelScope.launch {
        devShowTpPromptInterno(prefijo + (titulo?.let { getString(it) } ?: respaldo))
    }
}

/**
 * Aviso EFÍMERO de plataforma (el `Toast` de Android) con un texto de `composeResources`.
 *
 * ⚠️ **No confundir con [avisarConTitulo]**, que escribe en `interactionPrompt`. Aquel es un aviso
 * DEL JUEGO, se pinta dentro del mundo y existe igual en las dos plataformas; este es para errores
 * y para el Modo Desarrollador, y lo resuelve cada plataforma a su manera
 * ([WorldMapEnvironment.avisar]).
 */
internal fun WorldMapViewModel.avisarDesdeRecurso(recurso: StringResource) {
    viewModelScope.launch { entorno.avisar(getString(recurso)) }
}
