package ovh.gabrielhuav.pow.features.interiores.shinecto.ui


import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ovh.gabrielhuav.pow.R

/**
 * Easter-egg discovery modal.
 * Visually identical to [CollectibleClaimDialog] — reuses the dark gradient +
 * gold border + CutCornerShape language of the rest of the game.
 *
 * [onConfirm] is called when the player taps ENTRAR — the caller navigates
 * to the ShineCTO interior.
 */
@Composable
fun EasterEggDiscoveryDialog(onConfirm: () -> Unit) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialog(
        onDismissRequest = { /* non-dismissable: player must choose */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val bg = Brush.verticalGradient(listOf(Color(0xFF1B0A2E), Color(0xFF0D0D11)))
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.78f else 0.93f)
                .wrapContentHeight()
                .background(bg, shape)
                .border(2.dp, Color(0xFFD4AF37), shape)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Glyph / emoji easter-egg indicator ──────────────────
                Text(
                    text = "🏢",
                    fontSize = if (isLandscape) 48.sp else 64.sp
                )

                Text(
                    text = stringResource(R.string.ee_title),
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Black,
                    fontSize = if (isLandscape) 16.sp else 20.sp,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Shine CTO",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isLandscape) 26.sp else 32.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.ee_text),
                    color = Color.LightGray,
                    fontSize = if (isLandscape) 13.sp else 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B1C3A),
                        contentColor = Color.White
                    ),
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(52.dp)
                        .shadow(8.dp, shape)
                ) {
                    Text(
                        stringResource(R.string.ee_enter),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }
            }
        }
    }
}