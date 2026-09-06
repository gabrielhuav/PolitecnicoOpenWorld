package ovh.gabrielhuav.pow.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.data.repository.SettingsRepository

// GATILLOS "NEÓN ARCADE" COMPARTIDOS (2026-07-26).
//
// El estilo nació en el modo pelea "Titulación por Combate" (rediseño del dueño): relleno neón + aro de
// borde, reutilizando ActionButton para el hold y la retroalimentación háptica/sonora. Se movió
// aquí para que el MUNDO ABIERTO pueda usar el MISMO lenguaje visual sin depender del modo pelea.
//
// ⚠️ OJO con lo que NO está aquí: los gatillos de SF llevan además el brillo del TUTORIAL
// (SfTutorialButtonGlow), que es propio de ese modo. Por eso SF envuelve estos botones con su
// glow en vez de que el glow viva aquí: si un adorno solo tiene sentido en un modo, no es común.

/** Paleta de los 4 gatillos. Los colores son los del rediseño "Neón Arcade" del modo pelea. */
enum class NeonTrigger(val label: String, val fill: Color, val border: Color) {
    L1("L1", Color(0xFF00F2FE), Color(0xFF00ADB5)), // cian
    L2("L2", Color(0xFFFF007F), Color(0xFFC5005E)), // rosa
    R1("R1", Color(0xFF7928CA), Color(0xFF56149F)), // violeta
    R2("R2", Color(0xFFFF7B00), Color(0xFFC85A00)), // naranja
}

/** Botón con relleno neón + aro de borde. Dispara [onPress] en el flanco de BAJADA. */
@Composable
fun NeonButton(
    label: String,
    fill: Color,
    border: Color,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.border(2.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        ActionButton(text = label, color = fill, onHoldEvent = { pressed -> if (pressed) onPress() })
    }
}

/**
 * Par de gatillos de un lado (L1+L2 o R1+R2), con el estilo neón.
 *
 * 🆕 (2026-07-26) Lo usa el MUNDO ABIERTO, donde estos botones TODAVÍA NO TIENEN ACCIÓN: por eso
 * el ajuste que los muestra viene APAGADO de fábrica (Ajustes → Interfaz). Están aquí para que el
 * día que se les dé función ya aparezcan con el aspecto correcto y en su sitio, sin rediseñar.
 * Mientras tanto [onPress] no hace nada y eso es intencional, no un bug.
 */
/**
 * ¿Están activados los gatillos? Se lee **al entrar a la pantalla**, igual que hace el resto de
 * ajustes de interiores (ver `developerMode` en ZombieGameScreen).
 *
 * ⚠️ POR QUÉ AQUÍ Y NO EN EL ViewModel: los VM de interiores construyen su estado UNA sola vez y
 * no tienen un camino de refresco como `updateControlSettings` del mundo abierto. Leerlo allí
 * hacía que el ajuste no se notara (bug 2026-07-26: en exteriores salían y en interiores no).
 * `remember` sin claves = una lectura por entrada a la pantalla: barata y siempre fresca.
 */
@Composable
fun rememberShoulderTriggersEnabled(): Boolean {
    val context = LocalContext.current
    return remember { SettingsRepository(context).getShowWorldShoulderButtons() }
}

/**
 * Envuelve un control del HUD (joystick/D-pad o diamante) añadiéndole ARRIBA su par de gatillos
 * si el ajuste está activo. Si no lo está NO dibuja nada extra: la columna envuelve un único
 * hijo, así que el HUD queda EXACTAMENTE igual que antes de existir esta función.
 *
 * Lo usan el mundo abierto y los interiores para no repetir el mismo Column en 5 pantallas.
 *
 * @param isLeft de qué lado está el control (el de la izquierda lleva L1/L2; el otro R1/R2).
 *   Ojo: depende de `swapControls`, no de una posición fija.
 * @param enabled por defecto se lee del ajuste; se puede forzar (el mundo abierto le pasa el
 *   suyo, que sí se refresca en vivo al volver de Ajustes).
 */
@Composable
fun WithShoulderTriggers(
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    enabled: Boolean = rememberShoulderTriggersEnabled(),
    control: @Composable () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (enabled) {
            NeonTriggerPair(isLeft = isLeft, modifier = Modifier.scale(scale))
            Spacer(modifier = Modifier.height(6.dp))
        }
        control()
    }
}

@Composable
fun NeonTriggerPair(
    isLeft: Boolean,
    modifier: Modifier = Modifier,
    onPress: (NeonTrigger) -> Unit = {},
) {
    val pair = if (isLeft) listOf(NeonTrigger.L1, NeonTrigger.L2)
    else listOf(NeonTrigger.R1, NeonTrigger.R2)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        pair.forEachIndexed { i, t ->
            if (i > 0) Spacer(modifier = Modifier.size(6.dp))
            NeonButton(t.label, t.fill, t.border, onPress = { onPress(t) })
        }
    }
}
