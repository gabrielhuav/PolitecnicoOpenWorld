package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modal de contratación del compañero Prankedy.
 *
 * Diseño: sigue exactamente el mismo design system que [CollectibleClaimDialog]:
 * - Gradiente vertical oscuro (crimson → negro)
 * - Borde dorado 2dp
 * - [CutCornerShape] topStart / bottomEnd
 * - Doble-tap para cerrar
 *
 * @param isHireable          true si el jugador puede contratar ahora mismo.
 * @param hireableInSeconds   segundos restantes de penalización (0 si ya es contratable).
 * @param onHire              callback al pulsar "CONTRATAR".
 * @param onDismiss           callback para cerrar sin contratar.
 */
@Composable
fun PrankedyHireDialog(
    context: Context,
    isHireable: Boolean,
    hireableInSeconds: Int,
    onHire: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Carga asíncrona del frame idle-1 de Prankedy como imagen de presentación
    var prankedyBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        prankedyBitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open("SPRITES/NPC/Prankedy/p_idle/p_idle_1.webp").use { s ->
                    BitmapFactory.decodeStream(s)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }

    // Countdown en vivo
    var secsLeft by remember(hireableInSeconds) { mutableIntStateOf(hireableInSeconds) }
    LaunchedEffect(isHireable, hireableInSeconds) {
        if (!isHireable && hireableInSeconds > 0) {
            secsLeft = hireableInSeconds
            while (secsLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                secsLeft--
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.82f else 0.93f)
                .fillMaxHeight(if (isLandscape) 0.92f else 0.82f)
                .clip(CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A0A2E), Color(0xFF0D0D11))
                    )
                )
                .border(2.dp, Color(0xFFD4AF37), CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDismiss() }) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Título ────────────────────────────────────────────────────
                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_prankedy_found),
                    color = Color(0xFFD4AF37),
                    fontSize = if (isLandscape) 18.sp else 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace
                )

                // ── Imagen de Prankedy (o indicador de emergencia) ────────────
                val imgSizeDp = if (isLandscape) 90.dp else 130.dp
                Box(
                    modifier = Modifier
                        .size(imgSizeDp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A1A3E))
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = prankedyBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_prankedy),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        // ── Indicador de emergencia si el asset no cargó ──────
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎭", fontSize = 48.sp)
                            Text(
                                "PRANKEDY",
                                color = Color(0xFFD4AF37),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ── Nombre ────────────────────────────────────────────────────
                Text(
                    text = "PRANKEDY",
                    color = Color.White,
                    fontSize = if (isLandscape) 20.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )

                // ── Descripción ───────────────────────────────────────────────
                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_prankedy_desc_full),
                    color = Color(0xFFBBBBBB),
                    fontSize = if (isLandscape) 13.sp else 15.sp,
                    lineHeight = if (isLandscape) 18.sp else 22.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                // ── Botón CONTRATAR o PENALIZACIÓN ────────────────────────────
                Button(
                    onClick = { if (isHireable) onHire() },
                    enabled = isHireable,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(52.dp),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHireable) Color(0xFF4A1A6E) else Color(0xFF333333),
                        disabledContainerColor = Color(0xFF2A2A2A),
                        contentColor = Color.White,
                        disabledContentColor = Color(0xFF888888)
                    )
                ) {
                    if (isHireable) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_prankedy_hire),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_prankedy_cooldown, secsLeft),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // ── Botón CERRAR ──────────────────────────────────────────────
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_close),
                        color = Color(0xFF888888),
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_double_tap_close),
                    color = Color(0xFF555555),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
