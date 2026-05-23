package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
    modifier: Modifier = Modifier,
    health: Float,
    showHealthBar: Boolean,
    damagePulseTrigger: Int
) {
    val zoomLevel = uiState.zoomLevel
    val isZoomedIn = zoomLevel >= 16.5
    val context = LocalContext.current

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    // Animación de vibración (shake)
    val shake by animateFloatAsState(
        targetValue = if (damagePulseTrigger % 2 == 0) 0f else 10f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    // Color de la barra (Verde -> Amarillo -> Rojo) calculado ANTES de usarse
    val healthColor = when {
        health > 60f -> Color(0xFF4CAF50) // Verde
        health > 30f -> Color(0xFFFFEB3B) // Amarillo
        else -> Color(0xFFF44336) // Rojo
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .size(60.dp) // Ajusta al tamaño de tu celda/jugador
            .graphicsLayer {
                translationX = shake // Aplica el temblor al recibir daño
            }
    ) {
        // --- AQUÍ VA LA BARRA DE VIDA ---
        AnimatedVisibility(
            visible = showHealthBar,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(1000)),
            modifier = Modifier.offset(y = (-10).dp) // Flotando encima de la cabeza
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                LinearProgressIndicator(
                    progress = health / 100f,
                    modifier = Modifier.fillMaxSize(),
                    color = healthColor,
                    trackColor = Color.Transparent
                )
            }
        }

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

                // CORRECCIÓN VISUAL:
                val visualRotation = 270f
                val dynamicScale = (1.4 * Math.pow(2.0, zoomLevel - 19.0)).toFloat().coerceIn(0.2f, 1.4f)

                val bitmapKey = "${carModel.name}_${visualRotation}_${carColor}_${dynamicScale}"
                var carImage by remember { mutableStateOf<ImageBitmap?>(null) }
                var lastKey by remember { mutableStateOf("") }

                if (lastKey != bitmapKey) {
                    val drawable = VehicleSpriteManager.getTintedCarNpc(
                        context,
                        visualRotation,
                        carColor,
                        dynamicScale,
                        carModel
                    )
                    val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    carImage = bitmap?.asImageBitmap()
                    lastKey = bitmapKey
                }

                carImage?.let { img ->
                    val exactWidthDp = (img.width / density).dp
                    val exactHeightDp = (img.height / density).dp

                    Image(
                        bitmap = img,
                        contentDescription = "Player Vehicle",
                        modifier = modifier.size(exactWidthDp, exactHeightDp)
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
                    val calculatedSize = (24.0 + ((zoomLevel - 18.0) * 8.0)).coerceIn(16.0, 40.0).dp
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
    } // <- Faltaba cerrar el Box principal
}

private fun getPlayerAssetPath(action: PlayerAction, frame: Int): String {
    return when (action) {
        PlayerAction.IDLE -> "PRINCIPAL/lazaroIdle/lazaro_i_$frame.webp"
        PlayerAction.WALK -> "PRINCIPAL/lazaroWalk/lazaro_w_$frame.webp"
        PlayerAction.SPECIAL -> "PRINCIPAL/lazaroSpecial/lazaro_s_$frame.webp"
        PlayerAction.RUN -> "PRINCIPAL/lazaroRun/lazaro_r_$frame.webp"
    }
}