package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PlayerCharacter(
    action: PlayerAction,
    isFacingRight: Boolean,
    zoomLevel: Double,
    useWebScale: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isZoomedIn = zoomLevel >= 18
    val context = LocalContext.current

    if (!isZoomedIn) {
        Box(
            modifier = modifier
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color(0xFFD91B5B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = "Jugador", tint = Color(0xFFD91B5B), modifier = Modifier.size(14.dp))
        }


    } else {
        var currentFrame by remember { mutableIntStateOf(1) }
        var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }

        val bitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }

        LaunchedEffect(action) {
            currentFrame = 1
            while (true) {
                val maxFrames = when (action) {
                    PlayerAction.IDLE -> 6
                    PlayerAction.WALK -> 6
                    PlayerAction.SPECIAL -> 8
                    PlayerAction.RUN -> 6
                }

                val assetPath = getPlayerAssetPath(action, currentFrame)

                if (!bitmapCache.containsKey(assetPath)) {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            context.assets.open(assetPath).use { inputStream ->
                                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    bitmapCache[assetPath] = bitmap
                }

                currentImage = bitmapCache[assetPath]

                val frameDelay = when (action) {
                    PlayerAction.IDLE -> 1000L // Respiración tranquila / estático
                    PlayerAction.WALK -> 100L // Caminata fluida
                    PlayerAction.SPECIAL -> 300L
                    PlayerAction.RUN -> 100L
                }

                delay(frameDelay)
                currentFrame = (currentFrame % maxFrames) + 1
            }
        }

        currentImage?.let { img ->
            // Si es mapa web, usamos valores más grandes (64-120), si no, los originales (48-90)
            val baseSize = if (useWebScale) 64.0 else 48.0
            val maxSize = if (useWebScale) 120.0 else 90.0
            val zoomFactor = if (useWebScale) 24.0 else 16.0

            val calculatedSize = (baseSize + ((zoomLevel - 18.0) * zoomFactor)).coerceIn(baseSize, maxSize).dp

            // Compensación visual mayor para el mapa web
            val visualCompensation = when (action) {
                PlayerAction.IDLE -> if (useWebScale) 1.05f else 1.0f
                PlayerAction.WALK -> if (useWebScale) 1.05f else 1.0f
                PlayerAction.RUN -> if (useWebScale) 1.3f else 1.3f
                PlayerAction.SPECIAL -> if (useWebScale) 1.2f else 1.15f
            }
            Image(
                bitmap = img,
                contentDescription = "Personaje Principal",
                modifier = modifier
                    .size(calculatedSize)
                    .graphicsLayer {
                        // Aplicamos la compensación visual combinada con el espejeo
                        scaleX = if (isFacingRight) visualCompensation else -visualCompensation
                        scaleY = visualCompensation // Aplicamos la escala en el eje Y para mantener la proporción
                    }
            )
        }
    }
}

private fun getPlayerAssetPath(action: PlayerAction, frame: Int): String {
    return when (action) {
        PlayerAction.IDLE -> "PRINCIPAL/lazaroIdle/lazaro_i_$frame.webp"
        PlayerAction.WALK -> "PRINCIPAL/lazaroWalk/lazaro_w_$frame.webp"
        PlayerAction.SPECIAL -> "PRINCIPAL/lazaroSpecial/lazaro_s_$frame.webp"
        PlayerAction.RUN -> "PRINCIPAL/lazaroRun/lazaro_r_$frame.webp"
    }
}