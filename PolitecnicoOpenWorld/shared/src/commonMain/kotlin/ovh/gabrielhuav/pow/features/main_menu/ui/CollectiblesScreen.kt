package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.features.main_menu.gallery.GalleryCategory
import ovh.gabrielhuav.pow.shared.recursos.*

@Composable
fun CollectiblesScreen(controller: CollectiblesController, onBack: () -> Unit) {
    val gallery by controller.gallery.collectAsState()
    var section by rememberSaveable { mutableStateOf(GalleryCategory.FIGHTERS.name) }
    val category = GalleryCategory.entries.firstOrNull { it.name == section } ?: GalleryCategory.FIGHTERS
    LaunchedEffect(controller) { controller.loadGallery() }
    val background = remember {
        Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val landscape = maxWidth > maxHeight
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.menu_collectibles),
                fontSize = if (landscape) 22.sp else 26.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFD4AF37),
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (landscape) 10.dp else 18.dp))
            Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTab(
                    stringResource(Res.string.gallery_fighters), category == GalleryCategory.FIGHTERS,
                    Modifier.weight(1f),
                ) { section = GalleryCategory.FIGHTERS.name }
                SectionTab(
                    stringResource(Res.string.gallery_maps), category == GalleryCategory.MAPS,
                    Modifier.weight(1f),
                ) { section = GalleryCategory.MAPS.name }
            }
            Spacer(Modifier.height(10.dp))
            CollectiblesGallery(gallery, category, controller::loadGallery, Modifier.weight(1f))
            Spacer(Modifier.height(16.dp))
            val shape = remember { CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) }
            Button(
                onClick = onBack, shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(if (landscape) 0.5f else 0.85f).height(56.dp).shadow(8.dp, shape),
            ) {
                Text(stringResource(Res.string.menu_back), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun SectionTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(if (selected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.08f))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontSize = 14.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            color = if (selected) Color(0xFF1A1016) else Color(0xFFD4AF37),
        )
    }
}
