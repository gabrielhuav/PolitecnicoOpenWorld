package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.common_continue
import ovh.gabrielhuav.pow.shared.recursos.wm_new_collectible

@Composable
fun CollectibleClaimDialog(collectible: ActiveCollectible, onDismiss: () -> Unit) {
    val bitmap = remember(collectible.assetPath) {
        runCatching { PowImagen.deAsset(collectible.assetPath) }.getOrNull()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val landscape = maxWidth > maxHeight
            val background = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
            val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
            Box(
                Modifier.fillMaxWidth(if (landscape) 0.85f else 0.95f)
                    .fillMaxHeight(if (landscape) 0.95f else 0.85f)
                    .background(background, shape).border(2.dp, Color(0xFFD4AF37), shape)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDismiss() }) }
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(Res.string.wm_new_collectible),
                        color = Color(0xFFD4AF37),
                        fontWeight = FontWeight.Bold,
                        fontSize = if (landscape) 20.sp else 24.sp,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    if (bitmap != null) {
                        Image(
                            bitmap,
                            contentDescription = collectible.name,
                            modifier = Modifier.size(if (landscape) 100.dp else 140.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        collectible.name.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = if (landscape) 22.sp else 26.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        collectible.description,
                        color = Color.LightGray,
                        fontSize = if (landscape) 16.sp else 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                        shape = shape,
                        modifier = Modifier.fillMaxWidth(0.7f).height(56.dp).shadow(8.dp, shape),
                    ) {
                        Text(
                            stringResource(Res.string.common_continue),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                    }
                }
            }
        }
    }
}
