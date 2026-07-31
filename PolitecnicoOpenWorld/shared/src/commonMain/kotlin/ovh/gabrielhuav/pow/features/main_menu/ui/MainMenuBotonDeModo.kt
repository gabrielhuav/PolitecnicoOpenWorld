package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.platform.PowModo
import ovh.gabrielhuav.pow.domain.platform.enObras
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.badge_alpha
import ovh.gabrielhuav.pow.shared.recursos.badge_wip
import ovh.gabrielhuav.pow.shared.recursos.wip_message
import ovh.gabrielhuav.pow.shared.recursos.wip_ok
import ovh.gabrielhuav.pow.shared.recursos.wip_title

/**
 * 🚧 UN BOTÓN DEL MENÚ QUE SABE SI SU MODO ESTÁ EN OBRAS.
 *
 * ## Por qué existe
 *
 * El menú de iOS tiene que verse **igual** que el de Android mientras se porta el mundo abierto,
 * para poder compararlos lado a lado. Pero un botón que navega a una pantalla a medias no vale:
 * o se cae, o deja al jugador en un sitio del que no sabe salir.
 *
 * Así que el botón se pinta, se marca **EN OBRAS**, y al pulsarlo **avisa en vez de navegar**.
 *
 * ## Lo que NO hay que hacer
 *
 * ❌ **No copies este componente para "el botón de iOS".** Es el MISMO botón en las dos
 * plataformas; lo único que cambia es la respuesta de [PowModo.enObras], que sale del catálogo.
 *
 * ❌ **No borres los botones a mano para publicar en la App Store.** Se apagan los tres de golpe
 * con `MODOS_EN_OBRAS_VISIBLES = false` en `PowModos.kt`. Ahí está explicado por qué hay que
 * hacerlo antes de firmar.
 */
@Composable
internal fun BotonDeModo(
    modo: PowModo,
    texto: String,
    mostrarInsignias: Boolean,
    habilitado: Boolean,
    /** Se llama con el modo cuando está en obras, para que el menú abra [AvisoEnObras]. */
    alPulsarEnObras: (PowModo) -> Unit,
    /** Lo que hace el botón cuando el modo SÍ se puede jugar. */
    alPulsar: () -> Unit = {},
) {
    val enObras = modo.enObras()

    // La insignia de obras se pinta SIEMPRE que el modo lo esté, aunque `mostrarInsignias` sea
    // false: esa bandera esconde PRE-ALPHA/BETA por la App Store, y aquí pasa lo contrario —
    // si el botón no lleva a ninguna parte, el jugador tiene que verlo antes de pulsarlo.
    val insignia = if (enObras) stringResource(Res.string.badge_wip) else stringResource(Res.string.badge_alpha)
    val colorInsignia = if (enObras) COLOR_INSIGNIA_OBRAS else COLOR_INSIGNIA_ALPHA

    WithCornerBadge(insignia, colorInsignia, mostrar = enObras || mostrarInsignias) {
        MenuButton(
            text = texto,
            onClick = { if (enObras) alPulsarEnObras(modo) else alPulsar() },
            // ⚠️ En obras el botón se deja PULSABLE a propósito: deshabilitado no explicaría nada
            // y el jugador creería que la app está rota.
            enabled = habilitado,
        )
    }
}

/** El aviso que sustituye a la navegación cuando el modo aún no corre en esta plataforma. */
@Composable
internal fun AvisoEnObras(alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(Res.string.wip_title)) },
        text = { Text(stringResource(Res.string.wip_message)) },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text(stringResource(Res.string.wip_ok)) }
        },
    )
    Spacer(Modifier.height(0.dp))
}

/** Naranja de obras: el mismo que ya usan los avisos del juego. */
private val COLOR_INSIGNIA_OBRAS = Color(0xFFB35C00)

/** El dorado de siempre para PRE-ALPHA, para no cambiar cómo se ve Android. */
private val COLOR_INSIGNIA_ALPHA = Color(0xFF8A5A12)
