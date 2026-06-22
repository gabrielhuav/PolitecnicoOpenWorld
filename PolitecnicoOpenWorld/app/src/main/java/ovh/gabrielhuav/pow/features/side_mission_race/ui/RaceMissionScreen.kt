package ovh.gabrielhuav.pow.features.side_mission_race.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.gabrielhuav.pow.domain.models.Race
import ovh.gabrielhuav.pow.domain.models.RaceCatalog
import ovh.gabrielhuav.pow.features.side_mission_race.viewmodel.RaceMissionState
import ovh.gabrielhuav.pow.features.side_mission_race.viewmodel.RaceMissionViewModel

@Composable
fun RaceMissionScreen(
    onAccept: (Race) -> Unit,
    onDecline: () -> Unit,
    raceMissionViewModel: RaceMissionViewModel = viewModel(factory = RaceMissionViewModel.Factory())
) {
    val state by raceMissionViewModel.state.collectAsState()
    var selectedRace by remember { mutableStateOf(RaceCatalog.default) }

    LaunchedEffect(state) {
        if (state == RaceMissionState.Ready) {
            onAccept(selectedRace)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A1628), Color(0xFF1A2A4A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when (val s = state) {
            is RaceMissionState.Idle -> {
                BriefingPanel(
                    selectedRace = selectedRace,
                    onSelectRace = { selectedRace = it },
                    onAccept     = { raceMissionViewModel.startCountdown() },
                    onDecline    = onDecline
                )
            }
            is RaceMissionState.Countdown -> {
                CountdownDisplay(seconds = s.secondsLeft)
            }
            is RaceMissionState.Ready -> {
                Text(
                    text       = "¡A CORRER!",
                    color      = Color(0xFFFFD700),
                    fontSize   = 60.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif
                )
            }
        }
    }
}

@Composable
private fun BriefingPanel(
    selectedRace: Race,
    onSelectRace: (Race) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    // verticalScroll evita que el contenido se corte en pantallas landscape pequeñas
    Column(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.98f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Text(
            text       = "🏃 CARRERA DEL POLITÉCNICO",
            color      = Color(0xFFFFD700),
            fontSize   = 17.sp,
            fontWeight = FontWeight.Black,
            textAlign  = TextAlign.Center
        )

        Text(
            text      = "\"Elige una carrera y demuestra que eres\nel más rápido del Politécnico.\"",
            color     = Color(0xFFCCDDEE),
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        // ── Selector de carrera ──────────────────────────────────────────
        RaceCatalog.races.forEach { race ->
            RaceCard(
                race       = race,
                isSelected = race.id == selectedRace.id,
                onClick    = { onSelectRace(race) }
            )
        }

        // ── Reglas de la carrera seleccionada ───────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E3A5F), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RuleItem("⏱", "Tiempo: ${selectedRace.timeLimitSec} segundos")
            RuleItem("⚠", "Golpear NPCs: +5 seg de penalización")
            RuleItem("🏆", "Premio al ganar por primera vez")
            RuleItem("🔄", "Se puede reintentar indefinidamente")
        }

        // ── Botones ──────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDecline) {
                Text("RECHAZAR", color = Color(0xFF888888), fontSize = 13.sp)
            }
            Button(
                onClick = onAccept,
                colors  = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD700),
                    contentColor   = Color(0xFF0A1628)
                )
            ) {
                Text(
                    text       = "¡ACEPTAR!",
                    color      = Color(0xFF0A1628),
                    fontWeight = FontWeight.Black,
                    fontSize   = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun RaceCard(race: Race, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) Color(0xFFFFD700) else Color(0xFF2A4A6F)
    val bgColor     = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF0D1F3C)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = race.name,
                color      = if (isSelected) Color(0xFFFFD700) else Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text      = race.description,
                color     = Color(0xFFAABBCC),
                fontSize  = 12.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        // Badge de tiempo
        Text(
            text       = "${race.timeLimitSec}s",
            color      = if (isSelected) Color(0xFF0A1628) else Color(0xFFFFD700),
            fontSize   = 15.sp,
            fontWeight = FontWeight.Black,
            modifier   = Modifier
                .background(
                    color  = if (isSelected) Color(0xFFFFD700) else Color(0x33FFD700),
                    shape  = RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RuleItem(icon: String, text: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Text(text, color = Color(0xFFCCDDEE), fontSize = 12.sp)
    }
}

@Composable
private fun CountdownDisplay(seconds: Int) {
    val scale by animateFloatAsState(
        targetValue    = if (seconds > 0) 1.2f else 0.8f,
        animationSpec  = tween(durationMillis = 400),
        label          = "countdown_scale"
    )
    val color = when (seconds) {
        3    -> Color(0xFF4CAF50)
        2    -> Color(0xFFFFB300)
        else -> Color(0xFFD32F2F)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = seconds.toString(),
            color      = color,
            fontSize   = 110.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif,
            modifier   = Modifier.scale(scale)
        )
        Text(
            text       = "¡PREPÁRATE!",
            color      = Color.White,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp
        )
    }
}
