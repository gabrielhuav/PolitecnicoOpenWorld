package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfExtraordinarioHud
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFinisherPractice
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.sf_exit
import ovh.gabrielhuav.pow.shared.recursos.sf_extra_aprobado
import ovh.gabrielhuav.pow.shared.recursos.sf_extra_hide_steps
import ovh.gabrielhuav.pow.shared.recursos.sf_extra_range
import ovh.gabrielhuav.pow.shared.recursos.sf_extra_reprobado
import ovh.gabrielhuav.pow.shared.recursos.sf_extra_steps
import ovh.gabrielhuav.pow.ui.components.PowButton

// ────────────────────────────────────────────────────────────────────────────
// 🆕 EXAMEN EXTRAORDINARIO: HUD propio del modo (encima de la pelea).
//   • Abajo al centro (entre joystick y botones): [PASOS] y [SALIR].
//   • PASOS abre el panel con el comando en FLECHAS FÍSICAS (se voltean según el lado) y
//     palomea cada dirección que ya metiste. Cerrado por defecto para no estorbar.
//   • Al centro: ¡APROBADO! / ¡REPROBADO! del último intento.
// La distancia la marca la JERGA del piso (SfJerga.kt), no este panel.
// ────────────────────────────────────────────────────────────────────────────

/** Color del botón real del diamante (el mismo código de colores del tutorial). */
private fun extraChipColor(step: String): Color = when (step) {
    "X" -> Color(0xFF3498DB)
    "Y" -> Color(0xFFF1C40F)
    "B" -> Color(0xFFE74C3C)
    "A" -> Color(0xFF2ECC71)
    else -> Color(0xFF5B6ACD) // flechas = joystick
}

@Composable
fun SfExtraordinarioOverlay(
    hud: SfExtraordinarioHud,
    showSteps: Boolean,
    onToggleSteps: () -> Unit,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showSteps) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.74f))
                        .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = hud.moveName,
                        color = Color(0xFFD4AF37),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        hud.steps.forEachIndexed { i, step ->
                            ExtraStepChip(step = step, done = i < hud.stepIndex, current = i == hud.stepIndex)
                            if (i < hud.steps.lastIndex) {
                                Text(
                                    text = if (i == hud.steps.lastIndex - 1) "+" else "·",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 3.dp),
                                )
                            }
                        }
                    }
                    if (hud.rangeLabel.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = stringResource(Res.string.sf_extra_range, hud.rangeLabel),
                            color = if (hud.inRange) Color(0xFF7BE0A8) else Color(0xFFFF8A80),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PowButton(
                    text = stringResource(if (showSteps) Res.string.sf_extra_hide_steps else Res.string.sf_extra_steps),
                    onClick = onToggleSteps,
                    color = Color(0xFF6B4E16),
                    modifier = Modifier.widthIn(min = 118.dp), // crece con letra grande (H-3)
                )
                PowButton(
                    text = stringResource(Res.string.sf_exit),
                    onClick = onExit,
                    color = Color(0xFF8B1538),
                    modifier = Modifier.widthIn(min = 118.dp), // crece con letra grande (H-3)
                )
            }
        }

        if (hud.flash.isNotBlank()) {
            val aprobado = hud.flash == SfFinisherPractice.FLASH_APROBADO
            Text(
                text = stringResource(if (aprobado) Res.string.sf_extra_aprobado else Res.string.sf_extra_reprobado),
                color = if (aprobado) Color(0xFF7BE0A8) else Color(0xFFFF5252),
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ExtraStepChip(step: String, done: Boolean, current: Boolean) {
    val color = extraChipColor(step)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (done) color.copy(alpha = 0.35f) else color.copy(alpha = if (current) 0.95f else 0.6f))
            .border(
                width = if (current) 2.dp else 1.dp,
                color = if (current) Color.White else Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (done) "✓" else step,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
