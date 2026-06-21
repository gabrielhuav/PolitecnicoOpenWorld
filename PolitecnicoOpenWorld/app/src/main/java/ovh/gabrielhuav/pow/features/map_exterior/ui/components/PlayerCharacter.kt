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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.map.CarModel
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

    // ── 📐 FÓRMULA CARTOGRÁFICA DE TAMAÑOS REALES ───
    // Usamos la latitud actual para calcular los metros por pixel
    val lat = uiState.currentLocation?.latitude ?: 19.0
    val metersPerPixel = (40075016.686 * Math.cos(Math.toRadians(lat))) / (256.0 * Math.pow(2.0, zoomLevel))

    // ── Skin activa ──────────────────────────────────────────────────────
    val skin = uiState.selectedSkin

    // ── Animación de daño ────────────────────────────────────────────────
    val shake by animateFloatAsState(
        targetValue = if (damagePulseTrigger % 2 == 0) 0f else 10f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    val healthColor = when {
        health > 60f -> Color(0xFF4CAF50)
        health > 30f -> Color(0xFFFFEB3B)
        else         -> Color(0xFFF44336)
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .size(60.dp)
            .graphicsLayer { translationX = shake }
    ) {
        // ── Barra de vida ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showHealthBar,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(1000)),
            modifier = Modifier.offset(y = (-10).dp)
        ) {
            // Barra manual (Box) en vez de LinearProgressIndicator: el indicador de
            // Material dibuja extremos REDONDEADOS y a vida baja quedaba como una "bolita"
            // de color. Con un relleno recto se ve como una barra real. Tamaño reducido
            // para igualar al de los NPCs.
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth((health / 100f).coerceIn(0f, 1f))
                        .background(healthColor)
                )
            }
        }

        if (!isZoomedIn) {
            // ── Marcador alejado ─────────────────────────────────────────
            Box(
                modifier = modifier
                    .size(22.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        1.5.dp,
                        if (uiState.isDriving) Color.Blue else Color(0xFFD91B5B),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_player),
                    tint = if (uiState.isDriving) Color.Blue else Color(0xFFD91B5B),
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            if (uiState.isDriving) {
                // ── Modo conductor ───────────────────────────────────────

                // Coche del jugador: 4.0 m, en paridad con los autos NPC (antes 2.5 m,
                // por eso se veía pequeño al entrar en modo conducción).
                val exactCarDp = (4.0 / metersPerPixel).dp.coerceAtLeast(16.dp)

                val carModel = uiState.currentVehicleModel ?: CarModel.SEDAN
                val carColor = uiState.currentVehicleColor ?: 0xFFFFFFFF.toInt()
                val isPoliceCar = uiState.isDrivingPoliceCar
                val visualRotation = 270f

                // Usamos la densidad pura para generar un sprite nítido
                val renderScale = density

                // Si conduces una PATRULLA robada se usa el asset de policía (sin tinte),
                // igual que las patrullas NPC; si no, el auto normal tintado por color/modelo.
                val bitmapKey = if (isPoliceCar) "POLICE_${visualRotation}_$renderScale"
                    else "${carModel.name}_${visualRotation}_${carColor}_$renderScale"
                var carImage by remember { mutableStateOf<ImageBitmap?>(null) }
                var lastKey by remember { mutableStateOf("") }

                if (lastKey != bitmapKey) {
                    val drawable = if (isPoliceCar)
                        PoliceSpriteManager.getPoliceCar(context, visualRotation, renderScale)
                    else
                        VehicleSpriteManager.getTintedCarNpc(
                            context, visualRotation, carColor, renderScale, carModel
                        )
                    val bitmap =
                        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    carImage = bitmap?.asImageBitmap()
                    lastKey = bitmapKey
                }

                carImage?.let { img ->
                    // Calculamos la proporción (Aspect Ratio) para no apachurrar el auto
                    val ratio = img.width.toFloat() / img.height.toFloat()
                    val finalWidthDp = if (ratio > 1f) exactCarDp else exactCarDp * ratio
                    val finalHeightDp = if (ratio > 1f) exactCarDp / ratio else exactCarDp

                    Image(
                        bitmap = img,
                        contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_player_vehicle),
                        // requiredSize: IGNORA el límite de 60.dp del Box contenedor; si no,
                        // a zoom alto el coche del jugador quedaba recortado (~60dp) y se veía
                        // más pequeño que los autos NPC (que no tienen ese tope).
                        modifier = modifier.requiredSize(finalWidthDp, finalHeightDp)
                    )
                }

            } else {
                // ── Modo a pie ───────────────────────────────────────────

                // 🧍 Jugador a pie: TAMAÑO FIJO en pantalla, DESACOPLADO del zoom. El avatar del
                // jugador NO debe encogerse al alejar la cámara; solo cambia su POSICIÓN en el mundo.
                // (Los NPCs/peatones/coches SÍ siguen el tamaño real-meter dependiente del zoom; el
                // tamaño fijo es solo para el jugador, a petición.) ~38dp ≈ el tamaño que tenía a pie
                // en el zoom por defecto (z22), así no "salta" al ajustar el comportamiento.
                val exactPersonDp = 38.dp

                val action       = uiState.playerAction
                val isFacingRight = uiState.isPlayerFacingRight

                var currentFrame by remember { mutableIntStateOf(1) }
                var currentImage by remember { mutableStateOf<ImageBitmap?>(null) }

                // Cache de bitmaps por ruta de asset
                val bitmapCache = remember { mutableMapOf<String, ImageBitmap?>() }
                // Cache de la FRACCIÓN VERTICAL opaca (alto del personaje / alto del lienzo)
                // por asset. Sirve para normalizar el tamaño en pantalla entre animaciones:
                // CORRER suele venir en un lienzo más alto, así que al ajustarse al cuadro el
                // personaje se veía MÁS PEQUEÑO que CAMINAR. Compensamos con esta fracción.
                val fracCache = remember { mutableMapOf<String, Float>() }
                // Referencia de tamaño = fracción opaca de CAMINAR, PRECALCULADA y ESTÁTICA en
                // `PlayerSkin.walkBodyFraction`. Antes se medía en runtime (async) y arrancaba en
                // null: durante la 1ª vuelta de la animación la normalización se desactivaba y los
                // frames cambiaban de tamaño. Con la constante está lista desde el primer frame.

                // Relanzar cuando cambia la acción O la skin
                LaunchedEffect(action, skin) {
                    currentFrame = 1
                    while (true) {
                        val (maxFrames, frameDelay) = skinAnimParams(action, skin)
                        val assetPath = skin.assetPath(action, currentFrame)

                        if (!bitmapCache.containsKey(assetPath)) {
                            val decoded = withContext(Dispatchers.IO) {
                                try {
                                    context.assets.open(assetPath).use { stream ->
                                        BitmapFactory.decodeStream(stream)
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            bitmapCache[assetPath] = decoded?.asImageBitmap()
                            if (decoded != null) fracCache[assetPath] = opaqueVerticalFraction(decoded)
                        }

                        currentImage = bitmapCache[assetPath]

                        delay(frameDelay)
                        currentFrame = (currentFrame % maxFrames) + 1
                    }
                }

                currentImage?.let { img ->
                    // Normalización de tamaño: hacemos que la ALTURA DEL PERSONAJE (contenido
                    // opaco) sea SIEMPRE la misma (la de CAMINAR), sin importar cuánto padding
                    // traiga el lienzo de cada animación. Así CORRER deja de verse más pequeño.
                    val assetPath = skin.assetPath(action, currentFrame)
                    val frac = (fracCache[assetPath] ?: 0.6f).coerceIn(0.05f, 1f)
                    val ref = skin.walkBodyFraction.coerceIn(0.05f, 1f)
                    // renderScale compensa skins cuyo personaje ocupa poca fracción del lienzo
                    // (p. ej. escomboy en 256² con mucho margen) → así no se ven más pequeñas.
                    val boxHeightDp = exactPersonDp * (ref / frac) * skin.renderScale
                    val aspect = if (img.height > 0) img.width.toFloat() / img.height.toFloat() else 1f
                    val boxWidthDp = boxHeightDp * aspect
                    Image(
                        bitmap = img,
                        contentDescription = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.cd_main_character),
                        modifier = modifier
                            .requiredSize(boxWidthDp, boxHeightDp)
                            .graphicsLayer {
                                scaleX = if (isFacingRight) 1f else -1f
                            }
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Helpers
// ────────────────────────────────────────────────────────────────────────────

/** Devuelve la ruta de asset correcta delegando a la skin activa. */
private fun PlayerSkin.assetPath(action: PlayerAction, frame: Int): String = when (action) {
    PlayerAction.IDLE    -> idlePath(frame)
    PlayerAction.WALK    -> walkPath(frame)
    PlayerAction.RUN     -> runPath(frame)
    PlayerAction.SPECIAL -> specialPath(frame)
}

/**
 * Fracción vertical [0..1] que ocupa el contenido OPACO del bitmap dentro de su lienzo
 * (alto del personaje / alto del lienzo). Se usa para normalizar el tamaño en pantalla
 * del jugador entre animaciones cuyos lienzos tienen distinto padding (p. ej. CORRER
 * suele venir en un lienzo más alto, por eso al ajustarse al cuadro se veía más pequeño
 * que CAMINAR). Muestrea filas/columnas (no escanea cada píxel) para ser barato; el
 * resultado se cachea por asset, así que solo se calcula una vez por imagen.
 */
private fun opaqueVerticalFraction(bmp: android.graphics.Bitmap): Float {
    val w = bmp.width
    val h = bmp.height
    if (w <= 0 || h <= 0) return 1f
    val stepX = (w / 48).coerceAtLeast(1)
    val stepY = (h / 200).coerceAtLeast(1)
    var top = -1
    var bottom = -1
    var y = 0
    while (y < h) {
        var rowHasContent = false
        var x = 0
        while (x < w) {
            // alpha > ~6% → píxel "visible"
            if ((bmp.getPixel(x, y) ushr 24) > 16) { rowHasContent = true; break }
            x += stepX
        }
        if (rowHasContent) {
            if (top < 0) top = y
            bottom = y
        }
        y += stepY
    }
    if (top < 0 || bottom < 0) return 1f
    return ((bottom - top + 1).toFloat() / h).coerceIn(0.05f, 1f)
}

/** Parámetros de animación (frames totales, delay en ms) según acción y skin. */
private fun skinAnimParams(action: PlayerAction, skin: PlayerSkin): Pair<Int, Long> = when (action) {
    PlayerAction.IDLE    -> skin.idleFrames    to 1000L
    PlayerAction.WALK    -> skin.walkFrames    to 100L
    PlayerAction.RUN     -> skin.runFrames     to 100L
    PlayerAction.SPECIAL -> skin.specialFrames to 300L
}