package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.main_menu.gallery.*
import ovh.gabrielhuav.pow.shared.recursos.*

private val ViewerGold = Color(0xFFD4AF37)

@Composable
internal fun GalleryZoomViewer(item: GalleryItem, initialIndex: Int, loader: GalleryImageLoader, onDismiss: () -> Unit) {
    var index by rememberSaveable(item.id) { mutableIntStateOf(initialIndex.coerceIn(item.images.indices)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF1A1016)).systemBarsPadding().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(stringResource(Res.string.gallery_zoom_hint), color = ViewerGold, textAlign = TextAlign.Center)
            key(item.images[index]) {
                ZoomableGalleryImage(item.images[index], item.title, loader, Modifier.weight(1f))
            }
            if (item.images.size > 1) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.gallery_previous), color = if (index > 0) ViewerGold else Color.Gray)
                    }
                    Text(stringResource(Res.string.gallery_image_count, index + 1, item.images.size), color = Color.White)
                    TextButton(onClick = { index++ }, enabled = index < item.images.lastIndex, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.gallery_next), color = if (index < item.images.lastIndex) ViewerGold else Color.Gray)
                    }
                }
            }
            Button(
                onClick = onDismiss, modifier = Modifier.fillMaxWidth(),
                shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
            ) { Text(stringResource(Res.string.gallery_close)) }
        }
    }
}

@Composable
private fun ZoomableGalleryImage(image: GalleryImage, description: String, loader: GalleryImageLoader, modifier: Modifier) {
    var bitmap by remember(image) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(image) { mutableStateOf(false) }
    var retry by remember(image) { mutableIntStateOf(0) }
    var zoom by remember(image) { mutableStateOf(GalleryZoom()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(image, retry) {
        failed = false
        try {
            bitmap = loader.loadDetail(image)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val ready = bitmap
        val imageSize = ready?.let { IntSize(it.width, it.height) } ?: IntSize.Zero
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.Black)
                .onSizeChanged {
                    viewport = it
                    zoom = zoom.copy(offset = constrainGalleryOffset(zoom.offset, zoom.scale, it, imageSize))
                }
                .pointerInput(imageSize) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        if (ready != null) zoom = transformGalleryZoom(zoom, centroid, pan, zoomChange, viewport, imageSize)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                ready != null -> Image(
                    ready, description, Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }, contentScale = ContentScale.Fit,
                )
                failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(Res.string.gallery_image_unavailable), color = Color.White)
                    TextButton(onClick = { retry++ }) { Text(stringResource(Res.string.gallery_retry), color = ViewerGold) }
                }
                else -> CircularProgressIndicator(color = ViewerGold)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ZoomButton(Res.string.gallery_zoom_out, ready != null && zoom.scale > 1f, Modifier.weight(1f)) {
                zoom = transformGalleryZoom(zoom, Offset(viewport.width / 2f, viewport.height / 2f), Offset.Zero, 0.5f, viewport, imageSize)
            }
            ZoomButton(Res.string.gallery_zoom_reset, ready != null, Modifier.weight(1f)) { zoom = GalleryZoom() }
            ZoomButton(Res.string.gallery_zoom_in, ready != null && zoom.scale < GALLERY_MAX_ZOOM, Modifier.weight(1f)) {
                zoom = transformGalleryZoom(zoom, Offset(viewport.width / 2f, viewport.height / 2f), Offset.Zero, 2f, viewport, imageSize)
            }
        }
        Text(stringResource(Res.string.gallery_zoom_level, (zoom.scale * 100).toInt()), color = ViewerGold)
    }
}

@Composable
private fun ZoomButton(label: org.jetbrains.compose.resources.StringResource, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp)) {
        Text(stringResource(label), color = if (enabled) ViewerGold else Color.Gray, textAlign = TextAlign.Center)
    }
}
