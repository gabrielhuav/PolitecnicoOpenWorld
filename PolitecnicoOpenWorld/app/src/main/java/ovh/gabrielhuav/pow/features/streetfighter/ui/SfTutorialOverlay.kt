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
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PowButton

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
                Spacer(Modifier.height(4.dp))
                // Receta: el paso PENDIENTE resalta; los ya hechos van en verde con ✓
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    steps.forEachIndexed { i, step ->
                        val done = i < stepIndex
                        val current = i == stepIndex
                        Text(
                            text = (if (done) "✓ " else "") + step + if (i < steps.lastIndex) "   →   " else "",
                            color = when {
                                done -> Color(0xFF7BE0A8)
                                current -> Color(0xFFFFD54A)
                                else -> Color.White.copy(alpha = 0.55f)
                            },
                            fontSize = 11.sp,
                            fontWeight = if (current) FontWeight.Black else FontWeight.Bold,
                        )
                    }
                }
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

        // ── Confirmación de acierto ──
        if (flash.isNotBlank()) {
            Text(
                text = if (flash == "COMPLETO") {
                    stringResource(R.string.sf_tutorial_combo_ok)
                } else {
                    stringResource(R.string.sf_tutorial_step_ok)
                },
                color = Color(0xFFFFD54A),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // ── Controles del tutorial (abajo-izquierda, lejos del diamante de botones) ──
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 92.dp),
        ) {
            if (!completed) {
                PowButton(
                    text = stringResource(R.string.sf_tutorial_repeat),
                    onClick = onRestart,
                    color = Color(0xFF3A3A44),
                )
                Spacer(Modifier.height(4.dp))
                PowButton(
                    text = stringResource(R.string.sf_tutorial_skip),
                    onClick = onSkip,
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
