package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.res.Configuration
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Modal de "Personaje desbloqueado" para el NPC Prankedy.
 * Muestra la info del personaje y un botón para contratarlo.
 * Diseño alineado con CollectibleClaimDialog (mismo estilo visual del juego).
 */
@Composable
fun PrankedyUnlockDialog(
    onHire: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Cargar la miniatura de Prankedy (primer frame de walk).
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        preview = withContext(Dispatchers.IO) {
            try {
                context.assets.open("assetsNPC/Prankedy/p_walk/p_walk_1.webp").use {
                    BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val bg = Brush.verticalGradient(
            listOf(Color(0xFF1A0A2E), Color(0xFF0D0D11))
        )
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.75f else 0.92f)
                .fillMaxHeight(if (isLandscape) 0.92f else 0.78f)
                .background(bg, shape = shape)
                .border(2.dp, Color(0xFFFFD700), shape)    // Dorado (#FFD700)
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
                // Título
                Text(
                    text = "PERSONAJE DESBLOQUEADO",
                    color = Color(0xFFFFD700),    // Dorado
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (isLandscape) 18.sp else 22.sp,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Preview del personaje
                preview?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = "Prankedy",
                        modifier = Modifier.size(if (isLandscape) 90.dp else 120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Nombre
                Text(
                    text = "PRANKEDY",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isLandscape) 24.sp else 28.sp,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Descripción
                Text(
                    text = "Prankedy es el misterioso y escurridizo rey de las bromas de " +
                            "internet, un personaje anónimo que siembra el caos en las calles " +
                            "sacando de quicio a desconocidos con situaciones absurdas y humor " +
                            "pesado. Con el rostro siempre oculto, este irreverente creador de " +
                            "contenido es experto en esquivar golpes y escapar de guardias de " +
                            "seguridad, viviendo siempre al filo del arresto por mero " +
                            "entretenimiento.",
                    color = Color(0xFFCCCCCC),
                    fontSize = if (isLandscape) 13.sp else 15.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Contratar
                Button(
                    onClick = {
                        onHire()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),    // Dorado
                        contentColor = Color(0xFF1A0A2E)       // Texto oscuro sobre dorado
                    ),
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(56.dp)
                        .shadow(8.dp, shape)
                ) {
                    Text(
                        "CONTRATAR",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Botón cerrar discreto
                Text(
                    text = "Quizás después…",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(8.dp)
                        .then(
                            Modifier.background(Color.Transparent)
                        ),
                )
            }
        }
    }
}