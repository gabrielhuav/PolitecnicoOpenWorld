package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.ui.components.PowButton

/**
 * Color del BOTÓN real que hay que pulsar, deducido de la etiqueta del paso. Es el
 * identificador visual: el jugador asocia el chip con el botón que ve en pantalla.
 */
private fun chipColor(label: String): Color = when {
    label.contains("PUÑO LIGERO") -> Color(0xFF3498DB)   // X
    label.contains("PUÑO MEDIO") -> Color(0xFFF1C40F)    // Y
    label.contains("PUÑO FUERTE") -> Color(0xFFE74C3C)   // B
    label.contains("PATADA") -> Color(0xFF2ECC71)        // A
    label.contains("PARRY") -> Color(0xFF00F2FE)         // L1 (cian neón)
    label.contains("AGARRE") -> Color(0xFF7928CA)        // R1 (violeta neón)
    label.contains("SÚPER") || label.contains("FATALITY") -> Color(0xFFFF7B00) // R2 (naranja neón)
    label.contains("BURLA") -> Color(0xFFFF007F)         // L2 (rosa neón)
    else -> Color(0xFF5B6ACD)                            // joystick / direcciones
}

/** Chip de un paso de la receta: color del botón, ✓ al acertar, resaltado si es el actual. */
@Composable
private fun StepChip(label: String, done: Boolean, current: Boolean) {
    val base = chipColor(label)
    Text(
        text = (if (done) "✓ " else "") + label,
        color = if (done) Color(0xFF7BE0A8) else Color.White,
        fontSize = 10.sp,
        fontWeight = if (current) FontWeight.Black else FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                when {
                    done -> Color(0xFF14301F)
                    current -> base.copy(alpha = 0.85f)
                    else -> base.copy(alpha = 0.25f)
                },
            )
            .border(
                width = if (current) 2.dp else 0.dp,
                color = if (current) Color.White else Color.Transparent,
                shape = RoundedCornerShape(5.dp),
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

/**
 * 🆕 (2026-07-21) HUD del TUTORIAL INTERACTIVO.
 *
 * Va ENCIMA de la pelea (el jugador sigue usando joystick y botones normales). Muestra la
 * lección en curso, la receta paso a paso con los aciertos marcados y el progreso. El VM
 * valida cada paso con el estado REAL del peleador, así que aquí solo se pinta.
 */
@Composable
fun SfTutorialOverlay(
    lesson: Int,
    total: Int,
    title: String,
    hint: String,
    steps: List<String>,
    stepIndex: Int,
    flash: String,
    /** "LO QUE HICISTE → LO QUE TOCABA" cuando el jugador se equivoca (vacío si no). */
    error: String,
    completed: Boolean,
    onSkip: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Panel de la lección (arriba, sin tapar a los peleadores) ──
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.74f))
                .border(1.dp, Color(0xFF1C6B4A), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (completed) {
                Text(
                    text = stringResource(R.string.sf_tutorial_done),
                    color = Color(0xFFFFD54A),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
            } else {
                // 🆕 (2026-07-22, pedido del dueño) LAYOUT INVERTIDO: los BOTONES (la receta
                // de chips = lo que hay que presionar) van ARRIBA — es lo primero que se ve —
                // y la "hoja" (título de la lección + pista) queda ABAJO. Antes nadie leía
                // la receta por estar debajo del texto.
                // Receta con IDENTIFICADORES VISUALES: cada paso es un chip del COLOR del
                // botón real que hay que pulsar (X azul, Y amarillo, B rojo, A verde,
                // L1 cian, R1 violeta, R2 naranja, L2 rosa) para reconocerlo de un vistazo.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    steps.forEachIndexed { i, step ->
                        StepChip(
                            label = step,
                            done = i < stepIndex,
                            current = i == stepIndex,
                        )
                        if (i < steps.lastIndex) {
                            Text(
                                text = "→",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 3.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sf_tutorial_progress, lesson + 1, total),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = title,
                    color = Color(0xFF7BE0A8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                if (hint.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = hint,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Confirmación de acierto (verde/dorado) o aviso de ERROR (rojo) ──
        if (flash.isNotBlank()) {
            Text(
                text = if (flash == "COMPLETO") {
                    stringResource(R.string.sf_tutorial_combo_ok)
                } else {
                    stringResource(R.string.sf_tutorial_step_ok)
                },
                color = if (flash == "COMPLETO") Color(0xFFFFD54A) else Color(0xFF7BE0A8),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        } else if (error.isNotBlank()) {
            // "LO QUE HICISTE → LO QUE TOCABA": el jugador ve exactamente su equivocación.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC5A1020))
                    .border(1.dp, Color(0xFFE74C3C), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.sf_tutorial_wrong),
                    color = Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = error,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── Controles del tutorial: panel PLEGABLE (🆕 2026-07-22) para que NO estorbe los
        // botones de pelea. Un botón chico "☰" abre REPETIR/SALTAR/VOLVER; se colapsa solo a
        // los ~4 s si no interactúas.
        var toolsOpen by remember { mutableStateOf(false) }
        LaunchedEffect(toolsOpen) {
            if (toolsOpen) {
                delay(4000)
                toolsOpen = false
            }
        }
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 92.dp),
        ) {
            PowButton(
                text = if (toolsOpen) "✕" else "☰",
                onClick = { toolsOpen = !toolsOpen },
                color = Color(0xFF2A2A33),
            )
            if (toolsOpen) {
                Spacer(Modifier.height(4.dp))
                if (!completed) {
                    PowButton(
                        text = stringResource(R.string.sf_tutorial_repeat),
                        onClick = { onRestart(); toolsOpen = false },
                        color = Color(0xFF3A3A44),
                    )
                    Spacer(Modifier.height(4.dp))
                    PowButton(
                        text = stringResource(R.string.sf_tutorial_skip),
                        onClick = { onSkip(); toolsOpen = false },
                        color = Color(0xFF3A3A44),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                PowButton(
                    text = stringResource(R.string.sf_back),
                    onClick = onExit,
                    color = Color(0xFF8B1538),
                )
            }
        }
    }
}
