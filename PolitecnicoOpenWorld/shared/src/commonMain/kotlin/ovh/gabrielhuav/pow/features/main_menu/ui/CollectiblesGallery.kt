package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.main_menu.gallery.*
import ovh.gabrielhuav.pow.shared.recursos.*

private val GalleryGold = Color(0xFFD4AF37)
private val GalleryShape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

@Composable
internal fun CollectiblesGallery(
    state: GalleryState,
    category: GalleryCategory,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(category) { mutableStateOf("") }
    var selectedId by rememberSaveable(category) { mutableStateOf<String?>(null) }
    val loader = remember { GalleryImageLoader() }
    val items = remember(state.items, category, query) {
        state.items.filter { it.category == category && it.title.contains(query.trim(), ignoreCase = true) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text(stringResource(Res.string.gallery_search)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = GalleryGold, unfocusedBorderColor = GalleryGold,
                focusedLabelColor = GalleryGold, unfocusedLabelColor = Color.White,
                cursorColor = GalleryGold,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(Res.string.gallery_count, items.size), color = GalleryGold,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GalleryGold)
            }
            state.failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.gallery_load_failed), color = Color.White)
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.gallery_retry), color = GalleryGold) }
            }
            items.isEmpty() -> Text(stringResource(Res.string.gallery_empty), color = Color.White)
            else -> key(category, query) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        GalleryCard(item, loader) { selectedId = item.id }
                    }
                }
            }
        }
    }
    state.items.firstOrNull { it.id == selectedId }?.let { item ->
        GalleryDetail(item, loader) { selectedId = null }
    }
}

@Composable
private fun GalleryCard(item: GalleryItem, loader: GalleryImageLoader, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(GalleryShape).background(Color(0xFF6B1C3A))
            .border(2.dp, GalleryGold, GalleryShape).clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GalleryAssetImage(item.images.first(), item.title, loader, Modifier.fillMaxWidth().height(100.dp))
        Text(
            item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            item.subtitle(), color = GalleryGold, fontSize = 12.sp, maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GalleryDetail(item: GalleryItem, loader: GalleryImageLoader, onDismiss: () -> Unit) {
    var index by rememberSaveable(item.id) { mutableIntStateOf(0) }
    var showViewer by rememberSaveable(item.id) { mutableStateOf(false) }
    val image = item.images[index.coerceIn(item.images.indices)]
    if (showViewer) {
        GalleryZoomViewer(item, index, loader) { showViewer = false }
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).clip(GalleryShape).background(Color(0xFF1A1016))
                .border(2.dp, GalleryGold, GalleryShape).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(item.subtitle(), color = GalleryGold, textAlign = TextAlign.Center)
            key(image) {
                GalleryAssetImage(
                    image, item.title, loader,
                    Modifier.fillMaxWidth().height(220.dp).clickable { showViewer = true },
                )
            }
            TextButton(onClick = { showViewer = true }) {
                Text(
                    stringResource(if (item.category == GalleryCategory.FIGHTERS) Res.string.gallery_sheet else Res.string.gallery_zoom_open),
                    color = GalleryGold,
                )
            }
            if (item.images.size > 1) {
                Text(stringResource(Res.string.gallery_image_count, index + 1, item.images.size), color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.gallery_previous))
                    }
                    TextButton(onClick = { index++ }, enabled = index < item.images.lastIndex, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.gallery_next))
                    }
                }
            }
            Button(
                onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = GalleryShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
            ) { Text(stringResource(Res.string.gallery_close)) }
        }
    }
}

@Composable
private fun GalleryAssetImage(image: GalleryImage, description: String, loader: GalleryImageLoader, modifier: Modifier) {
    var bitmap by remember(image) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(image) { mutableStateOf(false) }
    LaunchedEffect(image) {
        try {
            bitmap = loader.load(image)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val ready = bitmap
        when {
            ready != null -> Image(ready, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            failed -> Text(stringResource(Res.string.gallery_image_unavailable), color = Color.White, textAlign = TextAlign.Center)
            else -> CircularProgressIndicator(Modifier.size(24.dp), color = GalleryGold)
        }
    }
}

@Composable
private fun GalleryItem.subtitle(): String = stringResource(
    when (lighting) {
        GalleryLighting.DAY -> Res.string.gallery_day
        GalleryLighting.NIGHT -> Res.string.gallery_night
        GalleryLighting.APOCALYPSE -> Res.string.gallery_apocalypse
        null -> source.label()
    },
)

private fun GallerySource.label(): StringResource = when (this) {
    GallerySource.FIGHTERS -> Res.string.gallery_combat
    GallerySource.STAGES -> Res.string.gallery_stages
    GallerySource.INTERIORS -> Res.string.gallery_interiors
    GallerySource.TRANSIT -> Res.string.gallery_transit
}
