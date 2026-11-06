package ovh.gabrielhuav.pow.features.multimedia.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.features.multimedia.GalleryCatalog
import ovh.gabrielhuav.pow.features.multimedia.GalleryCategory
import ovh.gabrielhuav.pow.features.multimedia.GalleryItem

private val GalleryGold = Color(0xFFFFD54A)
private val GalleryCardColor = Color(0xFF351824)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultimediaScreen(onBack: () -> Unit) {
    // Se conserva al recrear la actividad; al salir del destino se inicia un catálogo nuevo.
    var selectedName by rememberSaveable { mutableStateOf(GalleryCategory.CHARACTERS.name) }
    val selected = GalleryCategory.entries.firstOrNull { it.name == selectedName }
        ?: GalleryCategory.CHARACTERS
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11))))
            .systemBarsPadding().padding(horizontal = 16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.gallery_back), color = GalleryGold)
        }
        Text(
            text = stringResource(R.string.gallery_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = GalleryGold,
            modifier = Modifier.semantics { heading() }.padding(bottom = 8.dp)
        )
        // Las categorías pasan a otra línea con texto grande en vez de quedar fuera de pantalla.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GalleryCategory.entries.forEach { category ->
                FilterChip(
                    selected = selected == category,
                    onClick = { selectedName = category.name },
                    label = { Text(stringResource(category.label)) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = GalleryCardColor,
                        labelColor = Color.White,
                        selectedContainerColor = GalleryGold,
                        selectedLabelColor = Color(0xFF211019)
                    )
                )
            }
        }
        key(selected) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(156.dp),
                state = rememberLazyGridState(),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(GalleryCatalog.itemsFor(selected), key = { it.id }) { item ->
                    GalleryCard(item)
                }
            }
        }
    }
}

@Composable
private fun GalleryCard(item: GalleryItem) {
    val title = stringResource(item.title)
    Card(colors = CardDefaults.cardColors(containerColor = GalleryCardColor)) {
        GalleryAssetImage(assetPath = item.assetPath, description = title)
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                text = stringResource(item.description),
                color = Color(0xFFE8DBE0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
