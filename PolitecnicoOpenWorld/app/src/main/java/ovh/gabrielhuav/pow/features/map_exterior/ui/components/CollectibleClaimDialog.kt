package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible

@Composable
fun CollectibleClaimDialog(
    collectible: ActiveCollectible,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(collectible.assetPath) {
        try {
            context.assets.open(collectible.assetPath).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Dialog(onDismissRequest = { /* Obligar a presionar botón */ }) {
        val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

        Box(
            modifier = Modifier
                // CAMBIO 1: Hacemos el ancho responsivo para que se adapte al celular
                .fillMaxWidth(0.9f)
                .widthIn(max = 400.dp)
                .background(bg, shape = shape)
                .border(2.dp, Color(0xFFD4AF37), shape) // Borde dorado
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // CAMBIO 2: Agregamos scroll vertical a la columna interna
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "¡NUEVO COLECCIONABLE!",
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = collectible.name,
                        modifier = Modifier.size(110.dp)
                    )
                }

                Text(
                    text = collectible.name.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = collectible.description,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
                    shape = shape,
                    modifier = Modifier.fillMaxWidth().height(48.dp).shadow(8.dp, shape)
                ) {
                    Text("CONTINUAR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}