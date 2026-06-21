package ovh.gabrielhuav.pow.features.campaign.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.campaign.CampaignSchool
import ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog
import ovh.gabrielhuav.pow.features.campaign.viewmodel.StoryModeViewModel
// MenuButton sigue viviendo en main_menu (UI compartida del menú) → import explícito tras mover.
import ovh.gabrielhuav.pow.features.main_menu.ui.MenuButton

/**
 * Pantalla del MODO HISTORIA / Campaña. Muestra el prólogo (brote del Politécnico),
 * deja elegir la escuela de inicio (ESCOM jugable; FES Aragón y UAM en desarrollo) y
 * tiene "CARGAR PARTIDA" (habilitado solo si hay una partida guardada). "COMENZAR"
 * lleva a la pantalla de intro ("Listo para Iniciar") antes de entrar al mundo.
 */
@Composable
fun StoryModeScreen(
    onStartCampaign: (CampaignSchool) -> Unit,
    onLoadCampaign: () -> Unit,   // abre el diálogo de slots para CARGAR
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: StoryModeViewModel = viewModel(factory = StoryModeViewModel.Factory(context))
    val state by viewModel.state.collectAsState()

    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Respeta las barras del sistema (estado + navegación) para que el
                // botón "VOLVER" no choque con los botones del dispositivo.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Encabezado ───────────────────────────────────────────────
            Text(
                text = stringResource(R.string.story_title),
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.story_subtitle),
                color = Color(0xFFD4AF37),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ─── Prólogo ──────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33000000), shape = RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.story_prologue_heading),
                        color = Color(0xFFD4AF37),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.story_prologue),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        lineHeight = 21.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ─── Selección de escuela ─────────────────────────────────────
            Text(
                text = stringResource(R.string.story_choose_school),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(12.dp))

            SchoolCatalog.schools.forEach { school ->
                SchoolCard(
                    school = school,
                    selected = school.id == state.selectedSchoolId,
                    onClick = { viewModel.selectSchool(school.id) }
                )
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))

            // ─── Cargar partida (abre el selector de slots; habilitado si hay alguno) ─────────
            MenuButton(
                text = stringResource(R.string.story_load_game),
                onClick = { onLoadCampaign() },
                enabled = state.hasSave
            )
            Text(
                text = if (state.hasSave) stringResource(R.string.story_choose_slot)
                else stringResource(R.string.story_no_saves),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))

            // ─── Comenzar / Volver ────────────────────────────────────────
            MenuButton(
                text = stringResource(R.string.story_start),
                onClick = { onStartCampaign(viewModel.selectedSchool()) },
                color = Color(0xFF8C2A2A)
            )
            Spacer(Modifier.height(12.dp))
            MenuButton(
                text = stringResource(R.string.story_back),
                onClick = onBack,
                color = Color(0xFF4A1226)
            )

            // Margen extra al final del scroll, además de los insets del sistema.
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Tarjeta de una escuela. Si `available` es false (en desarrollo) se dibuja
 * apagada y no es seleccionable; la disponible muestra borde dorado al elegirla.
 */
@Composable
private fun SchoolCard(
    school: CampaignSchool,
    selected: Boolean,
    onClick: () -> Unit
) {
    val available = school.available
    val border = if (selected) BorderStroke(2.dp, Color(0xFFD4AF37)) else null
    val container = if (available) Color(0xFF2A1019) else Color(0xFF1A1418)

    Card(
        onClick = onClick,
        enabled = available,
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = container,
            disabledContainerColor = Color(0xFF1A1418)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = school.displayName,
                    color = if (available) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (available) stringResource(R.string.story_school_escom_desc)
                    else stringResource(R.string.story_school_locked_desc),
                    color = if (available) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.35f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!available) {
                Text(
                    text = stringResource(R.string.story_school_unavailable),
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .background(Color(0x22FFFFFF), shape = RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
