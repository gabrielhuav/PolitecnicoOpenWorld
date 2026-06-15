package ovh.gabrielhuav.pow.features.map_exterior.ui

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.MapProvider
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.RoadSource
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.TileSource

@Composable
fun LowHealthAura(health: Float) {
    if (health > 35f) return

    val infiniteTransition = rememberInfiniteTransition(label = "lowHealthAura")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // A medida que la vida baja de 35 a 0, el efecto es más pronunciado
    val intensity = (1f - (health / 35f)).coerceIn(0f, 1f)
    val currentAlpha = alpha * intensity

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.6f to Color.Transparent,
                    1.0f to Color.Red.copy(alpha = currentAlpha),
                )
            )
    )
}

@Composable
internal fun CacheStatusWidget(roadSource: RoadSource, tileSource: TileSource, mapProvider: MapProvider) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CacheChip(label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_streets), text  = when (roadSource) { RoadSource.LOADING -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_loading); RoadSource.LOCAL_DB -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_local_db); RoadSource.NETWORK -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_network) }, color = when (roadSource) { RoadSource.LOADING -> Color(0xFFD4AF37); RoadSource.LOCAL_DB -> Color(0xFF4CAF50); RoadSource.NETWORK -> Color(0xFF2196F3) }, isLoading = roadSource == RoadSource.LOADING)
        if (mapProvider != MapProvider.OSM) {
            val tileLabel = when (tileSource) { TileSource.LOCAL_OSM -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tile_local_osm); TileSource.LOCAL_CACHE -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tile_local_cache); TileSource.NETWORK -> androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_tile_network) }
            val tileColor = when (tileSource) { TileSource.LOCAL_OSM, TileSource.LOCAL_CACHE -> Color(0xFF4CAF50); TileSource.NETWORK -> Color(0xFF2196F3) }
            CacheChip(label = androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.wm_chip_map), text = tileLabel, color = tileColor, isLoading = false)
        }
    }
}

@Composable
internal fun CacheChip(label: String, text: String, color: Color, isLoading: Boolean) {
    Row(modifier = Modifier.background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(8.dp), color = color, strokeWidth = 1.5.dp)
        else Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text = "$label: $text", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ZombiVideoPlayer(context: Context, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
    ) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    val file = getAssetFile(ctx, "ZOMBIES_MOD/load_zombie_mod.mp4", "temp_zombi_carga.mp4")
                    setVideoPath(file.absolutePath)
                    requestFocus()
                    setOnCompletionListener { onDismiss() }
                    setOnErrorListener { _, what, extra ->
                        Log.e("VideoPlayer", "Error de video: $what, $extra")
                        onDismiss()
                        true
                    }
                    start()
                }
            },
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
