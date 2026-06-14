package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties // <--- IMPORTANTE
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible

@Composable
fun CollectibleClaimDialog(
    collectible: ActiveCollectible,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val bitmap = remember(collectible.assetPath) {
        try {
            context.assets.open(collectible.assetPath).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    // EL SECRETO: Desactivar el ancho restrictivo por defecto de la plataforma
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

        Box(
            modifier = Modifier
                // Ahora sí nos obedece: Mucho más ancho (85% horizontal, 95% vertical)
                .fillMaxWidth(if (isLandscape) 0.85f else 0.95f)
                .fillMaxHeight(if (isLandscape) 0.95f else 0.85f)
                .background(bg, shape = shape)
                .border(2.dp, Color(0xFFD4AF37), shape)
                // --- DETECCIÓN DE DOBLE CLIC PARA CERRAR ---
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onDismiss() })
                }
                .padding(32.dp), // Más padding para que respire
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_new_collectible),
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isLandscape) 20.sp else 24.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = collectible.name,
                        modifier = Modifier.size(if (isLandscape) 100.dp else 140.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = collectible.name.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isLandscape) 22.sp else 26.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = collectible.description,
                    color = Color.LightGray,
                    fontSize = if (isLandscape) 16.sp else 18.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
                    shape = shape,
                    // Botón estilizado como el del menú
                    modifier = Modifier.fillMaxWidth(0.7f).height(56.dp).shadow(8.dp, shape)
                ) {
                    Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.common_continue), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}
