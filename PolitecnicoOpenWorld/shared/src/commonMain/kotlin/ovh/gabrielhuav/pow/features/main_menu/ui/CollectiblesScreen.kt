package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository.Companion.FIGHTER_PREFIX
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog
import ovh.gabrielhuav.pow.features.streetfighter.ui.SfBitmapText
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.platform.imagen.rememberImagenDeAsset
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.*

@Composable
fun CollectiblesScreen(controller: CollectiblesController, onBack: () -> Unit) {
    val collectibles by controller.collectiblesList.collectAsState()
    var selected by remember { mutableStateOf<ActiveCollectible?>(null) }
    var showFighters by remember { mutableStateOf(false) }
    // `remember`: el degradado es inmutable, pero sin esto se reconstruye (Brush + List + 2 Color)
    // en CADA recomposicion — y aqui recompone al cambiar de pestana y al girar el telefono.
    val background = remember {
        Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val landscape = maxWidth > maxHeight
        val columns = if (landscape) 4 else 2
        // Las dos particiones se recalculaban enteras en cada recomposicion (2 recorridos + 2
        // listas nuevas). Dependen SOLO de la lista, asi que se atan a ella.
        val shown = remember(collectibles, showFighters) {
            collectibles.filter { it.id.startsWith(FIGHTER_PREFIX) == showFighters }
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "INVENTARIO",
                fontSize = if (landscape) 28.sp else 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp,
            )
            Text(
                "COLECCIONABLES",
                fontSize = if (landscape) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37),
                letterSpacing = 8.sp,
            )
            Spacer(Modifier.height(if (landscape) 10.dp else 18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTab(
                    stringResource(Res.string.collectibles_tab_objects),
                    !showFighters,
                ) { showFighters = false }
                SectionTab(
                    stringResource(Res.string.collectibles_tab_fighters),
                    showFighters,
                ) { showFighters = true }
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                // ⚠️ `key` NO es opcional aqui: sin el, al cambiar de pestana Compose reutiliza
                // las ranuras POR POSICION, tira el estado de cada card y vuelve a montarlas.
                // Con la clave, las cards que sobreviven al cambio se reaprovechan.
                items(shown, key = { it.id }) { item ->
                    CollectibleCard(item) {
                        selected = ActiveCollectible(
                            id = item.id,
                            name = item.name,
                            description = item.description,
                            assetPath = item.assetPath,
                            latitude = 0.0,
                            longitude = 0.0,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val shape = remember { CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) }
            Button(
                onClick = onBack,
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                modifier = Modifier.fillMaxWidth(if (landscape) 0.5f else 0.85f)
                    .height(56.dp).shadow(8.dp, shape),
            ) {
                Text(
                    stringResource(Res.string.menu_back),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }

        selected?.let { collectible ->
            if (collectible.id.startsWith(FIGHTER_PREFIX)) {
                FighterStoryDialog(collectible) { selected = null }
            } else {
                CollectibleClaimDialog(collectible) { selected = null }
            }
        }
    }
}

@Composable
private fun SectionTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        maxLines = 1,
        color = if (selected) Color(0xFF1A1016) else Color(0xFFD4AF37),
        modifier = Modifier.clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(if (selected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun FighterStoryDialog(collectible: ActiveCollectible, onDismiss: () -> Unit) {
    var showSoon by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .background(Color(0xFF1A1016))
                .border(2.dp, Color(0xFFD4AF37), CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SfBitmapText(collectible.name, glyphHeight = 20.dp)
            Spacer(Modifier.height(12.dp))
            FighterPortrait(collectible.assetPath)
            Spacer(Modifier.height(12.dp))
            Text(
                if (showSoon) stringResource(Res.string.collectibles_story_soon)
                else collectible.description,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showSoon = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
            ) {
                Text(
                    stringResource(Res.string.collectibles_view_story),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

@Composable
private fun FighterPortrait(assetPath: String) {
    // El atlas de pelea llega a 2560x7680: decodificarlo entero seria ~78 MB. Con `reduccion = 4`
    // baja a ~5 MB y la celda 0 (idle-1) sigue teniendo de sobra para 140 dp.
    // Va por la cache, asi que abrir dos veces la ficha del mismo peleador NO vuelve a decodificar.
    val atlas = rememberImagenDeAsset(assetPath, reduccion = 4)
    val portrait = remember(atlas) {
        atlas?.let {
            PowImagen.recortar(
                it,
                x = 0,
                y = 0,
                ancho = minOf(64, it.width),
                alto = minOf(64, it.height),
            )
        }
    }
    if (portrait != null) {
        Image(portrait, contentDescription = null, modifier = Modifier.size(140.dp))
    }
}

@Composable
fun CollectibleCard(item: CollectibleEntity, onClick: () -> Unit) {
    // MEDIDO: los `SPRITES/COLLECTIBLES/*.webp` son de ~600x420 y aqui se pintan a **64 dp**. A
    // tamano completo son ~1 MB cada uno en ARGB_8888 (6,7 MB los siete); con `reduccion = 2` son
    // ~0,25 MB y siguen sobrando pixeles incluso a densidad 3x (296x211 contra 192 px).
    //
    // Antes esto decodificaba DENTRO de `remember`, o sea en el hilo de composicion, y se repetia
    // cada vez que la card volvia a entrar en pantalla (LazyGrid destruye lo que sale). Ahora
    // decodifica en segundo plano y queda cacheado.
    val bitmap: ImageBitmap? =
        if (item.isCollected) rememberImagenDeAsset(item.assetPath, reduccion = 2) else null
    val shape = remember { CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.background(
            if (item.isCollected) Color(0xFF6B1C3A) else Color(0xFF2A1C21),
            shape,
        ).border(2.dp, if (item.isCollected) Color(0xFFD4AF37) else Color.DarkGray, shape)
            .clip(shape).clickable(enabled = item.isCollected, onClick = onClick)
            .padding(16.dp).height(140.dp),
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = item.name,
                modifier = Modifier.size(64.dp),
                colorFilter = if (item.isCollected) null else ColorFilter.tint(Color.Black),
            )
        } else {
            Box(Modifier.size(64.dp).background(Color.Gray, CircleShape))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (item.isCollected) item.name.uppercase() else "???",
            color = if (item.isCollected) Color.White else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp,
        )
    }
}
