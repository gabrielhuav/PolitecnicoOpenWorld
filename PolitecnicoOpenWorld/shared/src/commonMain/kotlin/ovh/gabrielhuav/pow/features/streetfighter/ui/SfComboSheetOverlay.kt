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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.ui.components.PowButton

/**
 * 🆕 (2026-07-21) HOJA DE COMBOS: explica TODOS los controles de combate y las rutas de
 * combo del peleador, y ofrece "PROBAR" para entrar al TUTORIAL INTERACTIVO guiado.
 *
 * Es una pantalla de LECTURA: no toca el ViewModel salvo por los callbacks. Los combos
 * llegan ya resueltos al idioma desde `viewModel.comboSheet(id)` (catálogo data-driven en
 * `assets/STREETFIGHTER/DATA/combos.json`).
 */
@Composable
fun SfComboSheetOverlay(
    fighterId: SfFighterId,
    /** (nombre, pista, pasos) por combo. */
    combos: List<Triple<String, String, List<String>>>,
    onTry: () -> Unit,
    onChangeFighter: () -> Unit,
    onBack: () -> Unit,
) {
    val bg = Brush.verticalGradient(listOf(Color(0xFF13251C), Color(0xFF0D0D11)))
    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.sf_combos_title),
                color = Color(0xFF7BE0A8),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.sf_combos_with_fighter, fighterId.shortName),
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )

            // 🆕 (2026-07-22) Botones ARRIBA de la hoja (antes iban al final y nadie leía la hoja).
            Spacer(Modifier.height(16.dp))
            PowButton(
                text = stringResource(Res.string.sf_combos_try),
                onClick = onTry,
                color = Color(0xFF1C6B4A),
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            Spacer(Modifier.height(8.dp))
            PowButton(
                text = stringResource(Res.string.sf_combos_change_fighter),
                onClick = onChangeFighter,
                color = Color(0xFF8B1538),
                modifier = Modifier.fillMaxWidth(0.8f),
            )
            Spacer(Modifier.height(8.dp))
            PowButton(
                text = stringResource(Res.string.sf_back),
                onClick = onBack,
                color = Color(0xFF3A3A44),
                modifier = Modifier.fillMaxWidth(0.8f),
            )

            // ── Controles básicos (la HOJA queda debajo de los botones) ──
            Spacer(Modifier.height(18.dp))
            SectionTitle(stringResource(Res.string.sf_combos_controls))
            ControlRow(stringResource(Res.string.sf_ctl_move), stringResource(Res.string.sf_ctl_move_v))
            ControlRow(stringResource(Res.string.sf_ctl_punch), stringResource(Res.string.sf_ctl_punch_v))
            ControlRow(stringResource(Res.string.sf_ctl_kick), stringResource(Res.string.sf_ctl_kick_v))
            ControlRow(stringResource(Res.string.sf_ctl_block), stringResource(Res.string.sf_ctl_block_v))
            ControlRow(stringResource(Res.string.sf_ctl_dash), stringResource(Res.string.sf_ctl_dash_v))
            ControlRow(stringResource(Res.string.sf_ctl_parry), stringResource(Res.string.sf_ctl_parry_v))
            ControlRow(stringResource(Res.string.sf_ctl_grab), stringResource(Res.string.sf_ctl_grab_v))
            ControlRow(stringResource(Res.string.sf_ctl_sweep), stringResource(Res.string.sf_ctl_sweep_v))
            ControlRow(stringResource(Res.string.sf_ctl_overhead), stringResource(Res.string.sf_ctl_overhead_v))
            ControlRow(stringResource(Res.string.sf_ctl_special), stringResource(Res.string.sf_ctl_special_v))
            ControlRow(stringResource(Res.string.sf_ctl_super), stringResource(Res.string.sf_ctl_super_v))

            // ── Combos ──
            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(Res.string.sf_combos_list))
            combos.forEachIndexed { index, (name, hint, steps) ->
                ComboCard(index + 1, name, hint, steps)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFFFD54A),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    )
}

/** Fila "acción — cómo se hace". El valor nunca se parte para no descuadrar la lista. */
@Composable
private fun ControlRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(0.45f),
        )
        Text(
            text = value,
            color = Color(0xFF9FD8FF),
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ComboCard(number: Int, name: String, hint: String, steps: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color(0xFF1C6B4A), RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(
            text = "$number. $name",
            color = Color(0xFF7BE0A8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(3.dp))
        // Los pasos en orden: es la "receta" del combo
        Text(
            text = steps.joinToString("  →  "),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(text = hint, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}
