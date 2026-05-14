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
import ovh.gabrielhuav.pow.domain.models.CarModel
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.WorldMapState

@Composable
fun PlayerCharacter(
    uiState: WorldMapState,
    modifier: Modifier = Modifier
) {
    val zoomLevel = uiState.zoomLevel
    val isZoomedIn = zoomLevel >= 18
    val context = LocalContext.current

    if (!isZoomedIn) {
        // --- MARCADOR ALEJADO ---
        Box(
            modifier = modifier
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, if (uiState.isDriving) Color.Blue else Color(0xFFD91B5B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Jugador",
                tint = if (uiState.isDriving) Color.Blue else Color(0xFFD91B5B),
                modifier = Modifier.size(14.dp)
            )
        }
    } else {
        if (uiState.isDriving) {
            // ==========================================
            // MODO CONDUCTOR (Vehículo)
            // ==========================================
            val carModel = uiState.currentVehicleModel ?: CarModel.SEDAN
            val carColor = uiState.currentVehicleColor ?: 0xFFFFFFFF.toInt()
            val rotation = uiState.vehicleRotation

            // Escalado dinámico usando la misma matemática del mapa
            val dynamicScale = (1.4 * Math.pow(2.0, zoomLevel - 19.0)).toFloat().coerceIn(0.2f, 2.5f)
            val baseSizeDp = 110.0 // Tamaño base aproximado para el auto
            val calculatedSize = (baseSizeDp * dynamicScale).dp

            val bitmapKey = "${carModel.name}_${rotation}_${carColor}_${dynamicScale}"
            var carImage by remember { mutableStateOf<ImageBitmap?>(null) }
            var lastKey by remember { mutableStateOf("") }

            if (lastKey != bitmapKey) {
                // 2. CORRECCIÓN: Pasamos los argumentos en el mismo orden que en WorldMapScreen
                // context, rotationAngle, carColor, scale, carModel
                val drawable = VehicleSpriteManager.getTintedCarNpc(
                    context,
                    rotation,
                    carColor,
                    dynamicScale,
                    carModel
                )
                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                carImage = bitmap?.asImageBitmap()
                lastKey = bitmapKey
            }

            carImage?.let { img ->
                Image(
                    bitmap = img,
                    contentDescription = "Player Vehicle",
                    modifier = modifier.size(calculatedSize)
                )
            }

        } else {
            // ==========================================
            // MODO A PIE (Peatón Original)
            // ==========================================
            val action = uiState.playerAction
            val isFacingRight = uiState.isPlayerFacingRight

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
                        PlayerAction.IDLE -> 1000L
                        PlayerAction.WALK -> 100L
                        PlayerAction.SPECIAL -> 300L
                        PlayerAction.RUN -> 100L
                    }

                    delay(frameDelay)
                    currentFrame = (currentFrame % maxFrames) + 1
                }
            }

            currentImage?.let { img ->
                val calculatedSize = (48.0 + ((zoomLevel - 18.0) * 16.0)).coerceIn(48.0, 90.0).dp
                val visualCompensation = when (action) {
                    PlayerAction.IDLE -> 1.0f
                    PlayerAction.WALK -> 1.0f
                    PlayerAction.RUN -> 1.3f
                    PlayerAction.SPECIAL -> 1.15f
                }

                Image(
                    bitmap = img,
                    contentDescription = "Personaje Principal",
                    modifier = modifier
                        .size(calculatedSize)
                        .graphicsLayer {
                            scaleX = if (isFacingRight) visualCompensation else -visualCompensation
                            scaleY = visualCompensation
                        }
                )
            }
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