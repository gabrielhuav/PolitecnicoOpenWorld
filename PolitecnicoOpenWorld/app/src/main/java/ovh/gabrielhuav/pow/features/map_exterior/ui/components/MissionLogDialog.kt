package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.campaign.MissionCatalog
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MissionLogStatus
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// Extensiones del VM (registro de misiones) → import explícito (fuera del paquete viewmodel).
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.devTeleportToMissionObjective
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.missionLogStatus
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.replayCampaignMission
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.selectCampaignMission
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.toggleMissionLog
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.unfollowActiveMission

/**
 * HOST del registro de misiones a nivel Activity/AppNavGraph (mismo patrón que SaveSlotsDialog):
 * un solo MissionLogDialog sirve al MAPA GLOBAL y a los INTERIORES (el estado vive en el
 * worldMapViewModel, Activity-scoped). PERF: mientras el diálogo está CERRADO solo se colecta
 * `showMissionLog` (distinctUntilChanged) para NO recomponer a 30 Hz con el uiState completo.
 */
@Composable
fun MissionLogHost(viewModel: WorldMapViewModel) {
    val show by remember(viewModel) {
        viewModel.uiState.map { it.showMissionLog }.distinctUntilChanged()
    }.collectAsState(initial = false)
    if (!show) return
    val uiState by viewModel.uiState.collectAsState()
    MissionLogDialog(uiState, viewModel)
}

/**
 * REGISTRO / SELECTOR DE MISIONES (estilo Witcher 3): lista las misiones de campaña (y futuras
 * secundarias) con su estado — 🔒 bloqueada · disponible · ▶ activa · ✔ completada — y permite
 * SEGUIR una (fija su objetivo: 🎯 + línea guía + widget) o DEJAR DE SEGUIR la activa (mundo
 * libre puro; la fase se conserva). MVVM: solo observa uiState y emite intenciones al VM.
 */
@Composable
fun MissionLogDialog(uiState: WorldMapState, viewModel: WorldMapViewModel) {
    if (!uiState.showMissionLog) return
    // Modo Desarrollador: desbloquea la selección de misiones 🔒 y el botón "TP al objetivo".
    // Se lee al abrir el diálogo (mismo patrón que WorldMapScreen; el early-return de arriba
    // hace que el remember se re-evalúe en cada apertura).
    val context = LocalContext.current
    val developerMode = remember { ovh.gabrielhuav.pow.data.repository.SettingsRepository(context).getDeveloperMode() }
    val anyActive = MissionCatalog.missions.any {
        viewModel.missionLogStatus(it.id) == MissionLogStatus.ACTIVE
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            // Scrim que consume el toque (cerrar solo con la ✕ o al seguir una misión).
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .heightIn(max = 480.dp)
                    .background(Color(0xF2141420), RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFFD4AF37), RoundedCornerShape(16.dp))
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.mlog_title),
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp
                )
                MissionCatalog.missions.forEach { mission ->
                    val status = viewModel.missionLogStatus(mission.id)
                    val (chip, chipColor) = when (status) {
                        MissionLogStatus.COMPLETED -> stringResource(R.string.mlog_completed) to Color(0xFF4CAF50)
                        MissionLogStatus.ACTIVE -> stringResource(R.string.mlog_active) to Color(0xFFFFC107)
                        MissionLogStatus.AVAILABLE -> stringResource(R.string.mlog_available) to Color(0xFF64B5F6)
                        MissionLogStatus.LOCKED -> stringResource(R.string.mlog_locked) to Color(0xFF9E9E9E)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E2C), RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (status == MissionLogStatus.ACTIVE) Color(0xFFFFC107) else Color(0x33FFFFFF),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(mission.titleRes),
                                color = if (status == MissionLogStatus.LOCKED) Color(0xFF9E9E9E) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            // Badge de misión SECUNDARIA (CampaignMissionInfo.side).
                            if (mission.side) {
                                Text(
                                    text = stringResource(R.string.mlog_side_badge),
                                    color = Color(0xFF80CBC4),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .background(Color(0x3380CBC4), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = chip,
                                color = chipColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .background(chipColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = if (status == MissionLogStatus.LOCKED)
                                stringResource(R.string.mlog_locked_hint)
                            else stringResource(mission.descriptionRes),
                            color = Color(0xFFB0BEC5),
                            fontSize = 12.sp
                        )
                        // Botones por misión: SEGUIR (disponible, o 🔒 bloqueada en Modo Dev),
                        // DEJAR DE SEGUIR (activa), REJUGAR (✔ completada; NO toca el progreso)
                        // y "TP al objetivo" (solo Modo Dev).
                        val showFollow = status == MissionLogStatus.AVAILABLE ||
                            (developerMode && status == MissionLogStatus.LOCKED)
                        if (showFollow || status == MissionLogStatus.ACTIVE ||
                            status == MissionLogStatus.COMPLETED || developerMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (showFollow) {
                                    Button(
                                        onClick = { viewModel.selectCampaignMission(mission.id, force = developerMode) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.mlog_follow),
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                        )
                                    }
                                }
                                if (status == MissionLogStatus.ACTIVE) {
                                    Button(
                                        onClick = { viewModel.unfollowActiveMission() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.mlog_unfollow),
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                        )
                                    }
                                }
                                if (status == MissionLogStatus.COMPLETED) {
                                    Button(
                                        onClick = { viewModel.replayCampaignMission(mission.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.mlog_replay),
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                        )
                                    }
                                }
                                if (developerMode) {
                                    Button(
                                        onClick = { viewModel.devTeleportToMissionObjective(mission.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF455A64)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.mlog_tp_objective),
                                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (!anyActive) {
                    Text(
                        text = stringResource(R.string.mlog_free_roam_hint),
                        color = Color(0xFF80CBC4),
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(1.dp))
                TextButton(
                    onClick = { viewModel.toggleMissionLog(false) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        stringResource(R.string.mlog_close),
                        color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
