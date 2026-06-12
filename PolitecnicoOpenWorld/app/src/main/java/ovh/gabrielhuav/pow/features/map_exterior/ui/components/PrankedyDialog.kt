package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PrankedyDialog(
    isFirstUnlock: Boolean,
    onHire: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewBitmap = remember {
        try {
            context.assets.open("assetsNPC/Prankedy/p_idle/p_idle_1.webp").use {
                BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        } catch (_: Exception) { null }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
                .background(bg, shape = shape)
                .border(2.dp, Color(0xFFD4AF37), shape)
                .padding(28.dp),
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
                    text = if (isFirstUnlock) "¡PERSONAJE DESBLOQUEADO!" else "PRANKEDY",
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "Prankedy",
                        modifier = Modifier.size(120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "PRANKEDY",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Prankedy es el misterioso y escurridizo rey de las bromas de internet, " +
                            "un personaje anónimo que siembra el caos en las calles sacando de quicio " +
                            "a desconocidos con situaciones absurdas y humor pesado. Con el rostro " +
                            "siempre oculto, este irreverente creador de contenido es experto en " +
                            "esquivar golpes y escapar de guardias de seguridad, viviendo siempre " +
                            "al filo del arresto por mero entretenimiento.",
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onHire,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6B1C3A),
                        contentColor = Color.White
                    ),
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(54.dp)
                        .shadow(8.dp, shape)
                ) {
                    Text(
                        "CONTRATAR",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFFAAAAAA)
                    ),
                    modifier = Modifier.fillMaxWidth(0.5f)
                ) {
                    Text("Ahora no", fontSize = 13.sp)
                }
            }
        }
    }
}