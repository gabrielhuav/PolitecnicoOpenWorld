package ovh.gabrielhuav.pow.features.multimedia.ui

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.multimedia.gallerySampleSize

private sealed interface GalleryImageState {
    data object Loading : GalleryImageState
    data object Unavailable : GalleryImageState
    data class Ready(val bitmap: ImageBitmap) : GalleryImageState
}

@Composable
internal fun GalleryAssetImage(assetPath: String, description: String) {
    val assets = LocalContext.current.assets
    val state by produceState<GalleryImageState>(GalleryImageState.Loading, assetPath, assets) {
        // El trabajo de disco y decodificación no debe bloquear las pulsaciones del usuario.
        value = withContext(Dispatchers.IO) { loadGalleryImage(assets, assetPath) }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF15131C)),
        contentAlignment = Alignment.Center
    ) {
        when (val image = state) {
            GalleryImageState.Loading -> CircularProgressIndicator(color = Color(0xFFFFD54A))
            GalleryImageState.Unavailable -> Text(
                text = stringResource(R.string.gallery_image_unavailable),
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            is GalleryImageState.Ready -> Image(
                bitmap = image.bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        }
    }
}

private fun loadGalleryImage(assets: AssetManager, path: String): GalleryImageState = try {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    options.inSampleSize = gallerySampleSize(options.outWidth, options.outHeight)
    options.inJustDecodeBounds = false
    val bitmap = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
    bitmap?.let { GalleryImageState.Ready(it.asImageBitmap()) } ?: GalleryImageState.Unavailable
} catch (_: IOException) {
    // Un recurso ausente se informa en su tarjeta y no impide volver al menú.
    GalleryImageState.Unavailable
}
