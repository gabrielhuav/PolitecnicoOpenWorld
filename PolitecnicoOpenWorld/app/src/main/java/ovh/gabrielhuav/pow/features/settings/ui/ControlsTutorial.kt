package ovh.gabrielhuav.pow.features.settings.ui

// ─────────────────────────────────────────────────────────────────────────────────
// TUTORIAL OPTATIVO DE CONTROLES (2026-07-11): overlay paginado que explica qué hace
// cada botón en el MUNDO ABIERTO y en INTERIORES (misma botonera A/B/X/Y; solo cambia
// la conducción, exclusiva del exterior). Se OFRECE una sola vez al entrar por primera
// vez a cada mundo (flags TUTORIAL_*_SEEN en SettingsRepository) y siempre se puede
// re-ver desde Ajustes → Controles. Composables SIN ViewModel: son overlays puros; el
// caller decide cuándo mostrarlos (MVVM intacto). La lectura puntual de prefs en
// ControlsTutorialFirstRun sigue el patrón de MissionLogDialog (View → SettingsRepository).
// ─────────────────────────────────────────────────────────────────────────────────

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.data.repository.SettingsRepository

/**
 * Una página del tutorial. Si [buttonLetter] no es null se dibuja el botón del diamante
 * (A/B/X/Y con su color real); si es null se muestra el [emoji] (movimiento = 🕹️).
 */
data class TutorialPage(
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val buttonLetter: String? = null,
    val buttonColor: Color = Color.Gray,
    val emoji: String = "🕹️"
)

// Colores de la botonera real (ActionButtonsController): A verde, B rojo, X azul, Y amarillo.
private val BTN_A = Color(0xFF43A047)
private val BTN_B = Color(0xFFE53935)
private val BTN_X = Color(0xFF2196F3)
private val BTN_Y = Color(0xFFF9A825)

/** Páginas del tutorial del MUNDO ABIERTO (incluye conducción/teletransporte, exclusivos). */
fun exteriorTutorialPages(): List<TutorialPage> = listOf(
    TutorialPage(R.string.tutorial_page_move_title, R.string.tutorial_page_move_body),
    TutorialPage(R.string.tutorial_page_run_title, R.string.tutorial_page_run_body, "A", BTN_A),
    TutorialPage(R.string.tutorial_page_ext_interact_title, R.string.tutorial_page_ext_interact_body, "X", BTN_X),
    TutorialPage(R.string.tutorial_page_ext_attack_title, R.string.tutorial_page_ext_attack_body, "B", BTN_B),
    TutorialPage(R.string.tutorial_page_ext_vehicle_title, R.string.tutorial_page_ext_vehicle_body, "Y", BTN_Y),
    // El teletransporte vive en el MENÚ (ya no en mantener Y, retirado 2026-07-12) → emoji.
    TutorialPage(R.string.tutorial_page_ext_teleport_title, R.string.tutorial_page_ext_teleport_body, emoji = "🗺️")
)

/** Páginas del tutorial de INTERIORES (mismos botones; sin conducción). */
fun interiorTutorialPages(): List<TutorialPage> = listOf(
    TutorialPage(R.string.tutorial_page_move_title, R.string.tutorial_page_move_body),
    TutorialPage(R.string.tutorial_page_run_title, R.string.tutorial_page_run_body, "A", BTN_A),
    TutorialPage(R.string.tutorial_page_int_interact_title, R.string.tutorial_page_int_interact_body, "X", BTN_X),
    TutorialPage(R.string.tutorial_page_int_attack_title, R.string.tutorial_page_int_attack_body, "B", BTN_B),
    TutorialPage(R.string.tutorial_page_int_mode_title, R.string.tutorial_page_int_mode_body, "Y", BTN_Y)
)

/** Overlay a pantalla completa con las páginas del tutorial (paginado Anterior/Siguiente). */
@Composable
fun ControlsTutorialOverlay(
    @StringRes titleRes: Int,
    pages: List<TutorialPage>,
    onDismiss: () -> Unit
) {
    var pageIdx by remember { mutableIntStateOf(0) }
    val page = pages[pageIdx.coerceIn(0, pages.size - 1)]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Scrim que CONSUME el toque (no cierra ni deja pasar taps al juego de atrás).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.9f)
                .background(Color(0xF21A1418), RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(titleRes),
                    color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            // Icono grande: botón del diamante (con su color real) o emoji de movimiento.
            if (page.buttonLetter != null) {
                Box(
                    modifier = Modifier.size(72.dp).background(page.buttonColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(page.buttonLetter, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(page.emoji, fontSize = 56.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(page.titleRes),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(page.bodyRes),
                color = Color(0xFFCCCCCC), fontSize = 14.sp, lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))

            // Indicador de página (puntitos).
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (i == pageIdx) Color(0xFFD4AF37) else Color(0xFF4A3A40),
                                CircleShape
                            )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pageIdx > 0) {
                    OutlinedButton(onClick = { pageIdx-- }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.tutorial_prev), color = Color.White, fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = { if (pageIdx < pages.size - 1) pageIdx++ else onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A))
                ) {
                    Text(
                        stringResource(
                            if (pageIdx < pages.size - 1) R.string.tutorial_next else R.string.tutorial_done
                        ),
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/** Diálogo "¿quieres ver el tutorial?" (el tutorial es OPTATIVO: se puede rechazar). */
@Composable
fun ControlsTutorialPrompt(onAccept: () -> Unit, onDecline: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.tutorial_prompt_title)) },
        text = { Text(stringResource(R.string.tutorial_prompt_msg)) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.tutorial_prompt_yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text(stringResource(R.string.tutorial_prompt_no)) }
        }
    )
}

/**
 * PRIMERA VEZ en un mundo: ofrece el tutorial una sola vez (persistido). Pon este composable
 * al FINAL del Box raíz de la pantalla (queda por encima). [interior] elige páginas y flag.
 */
@Composable
fun ControlsTutorialFirstRun(interior: Boolean) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    var showPrompt by remember {
        mutableStateOf(if (interior) !repo.getTutorialInteriorSeen() else !repo.getTutorialExteriorSeen())
    }
    var showTutorial by remember { mutableStateOf(false) }
    if (showPrompt) {
        ControlsTutorialPrompt(
            onAccept = {
                showPrompt = false
                showTutorial = true
                if (interior) repo.saveTutorialInteriorSeen() else repo.saveTutorialExteriorSeen()
            },
            onDecline = {
                showPrompt = false
                if (interior) repo.saveTutorialInteriorSeen() else repo.saveTutorialExteriorSeen()
            }
        )
    }
    if (showTutorial) {
        ControlsTutorialOverlay(
            titleRes = if (interior) R.string.tutorial_int_title else R.string.tutorial_ext_title,
            pages = if (interior) interiorTutorialPages() else exteriorTutorialPages(),
            onDismiss = { showTutorial = false }
        )
    }
}
