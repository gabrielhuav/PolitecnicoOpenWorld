package ovh.gabrielhuav.pow.features.map_exterior.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PrankedyHireDialog
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapViewModel
// REFACTOR: extensiones del VM (Prankedy / fade puerta ESCOM) → import explícito.
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.dismissPrankedyDialog
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onEscomDoorFadeComplete
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.onHirePrankedy

/**
 * Overlays y diálogos superpuestos de [WorldMapScreen] (pantalla WASTED, vídeo zombi,
 * prompts de interacción, diálogo de Prankedy, popup de coleccionable y los fades de
 * puerta ESCOM / metro / metrobús). Extraído de WorldMapScreen.kt para reducir su tamaño
 * (Compose, mismo paquete `ui`). MVVM: solo observa `uiState` y emite intenciones al VM.
 */
@Composable
fun WorldMapOverlays(
    uiState: WorldMapState,
    viewModel: WorldMapViewModel,
    onNavigateToInterior: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    if (uiState.showWastedScreen) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, onClick = {})) {
            var scale by remember { mutableStateOf(0.5f) }
            LaunchedEffect(Unit) {
                androidx.compose.animation.core.animate(initialValue = 0.5f, targetValue = 1.3f, animationSpec = tween(durationMillis = 3500, easing = LinearOutSlowInEasing)) { value, _ -> scale = value }
            }
            Text(text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_wasted), color = Color(0xFFD32F2F), fontSize = 60.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Serif, letterSpacing = 6.sp, modifier = Modifier.align(Alignment.Center).scale(scale))
        }
    }
    if (uiState.showZombiVideo) {
        ZombiVideoPlayer(
            context = context,
            onDismiss = { viewModel.dismissVideo() }
        )
    }

    uiState.interactionPrompt?.let { promptText ->
        Box(modifier = Modifier.fillMaxSize().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
            Text(text = promptText, color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 2.sp, modifier = Modifier.background(color = Color(0xFF3B0D1B).copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp)).padding(horizontal = 24.dp, vertical = 12.dp))
        }
    }

    uiState.prankedyDialogue?.let { dialogueText ->
        Box(modifier = Modifier.fillMaxSize().padding(top = 130.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = dialogueText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(color = Color(0xFF222222).copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFFFFCC00), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }

    if (uiState.showPrankedyHireDialog) {
        PrankedyHireDialog(
            context = context,
            isHireable = uiState.prankedyIsHireable,
            hireableInSeconds = uiState.prankedyHireableInSeconds,
            onHire = { viewModel.onHirePrankedy() },
            onDismiss = { viewModel.dismissPrankedyDialog() }
        )
    }

    uiState.showClaimedPopupFor?.let { collectible ->
        CollectibleClaimDialog(collectible = collectible, onDismiss = { viewModel.dismissClaimedPopup() })
    }

    // ─── ESCOM Door Fade Overlay ─────────────────────────────────────────────
    val escomFadeAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(uiState.showEscomDoorFade) {
        if (uiState.showEscomDoorFade) {
            escomFadeAlpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
            viewModel.onEscomDoorFadeComplete()
            kotlinx.coroutines.delay(200)
            escomFadeAlpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(400))
        }
    }

    if (escomFadeAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = escomFadeAlpha.value))
        )
    }

    // ─── Metro Door Fade Overlay ─────────────────────────────────────────────
    val metroFadeAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(uiState.showMetroFade) {
        if (uiState.showMetroFade) {
            metroFadeAlpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
            viewModel.onMetroFadeComplete()
            kotlinx.coroutines.delay(200)
            metroFadeAlpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(400))
        }
    }
    if (metroFadeAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = metroFadeAlpha.value))
        )
    }

    LaunchedEffect(uiState.metroFadeCompleteStation) {
        val station = uiState.metroFadeCompleteStation
        if (station != null) {
            viewModel.consumeMetroFadeComplete()
            onNavigateToInterior("metro_station_interior/${station.name}")
        }
    }

    // ─── Metrobús Fade Overlay ───────────────────────────────────────────────
    val metrobusFadeAlpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(uiState.showMetrobusFade) {
        if (uiState.showMetrobusFade) {
            metrobusFadeAlpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(600))
            viewModel.onMetrobusFadeComplete()
            kotlinx.coroutines.delay(200)
            metrobusFadeAlpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(400))
        }
    }
    if (metrobusFadeAlpha.value > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFC21D24).copy(alpha = metrobusFadeAlpha.value))
        )
    }

    LaunchedEffect(uiState.metrobusFadeCompleteStation) {
        val station = uiState.metrobusFadeCompleteStation
        if (station != null) {
            viewModel.consumeMetrobusFadeComplete()
            onNavigateToInterior("metrobus_station_interior/${station.name}")
        }
    }
}
