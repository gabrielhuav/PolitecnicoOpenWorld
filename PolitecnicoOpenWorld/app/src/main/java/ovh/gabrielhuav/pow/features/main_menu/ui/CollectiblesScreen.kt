package ovh.gabrielhuav.pow.features.main_menu.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.data.repository.CollectibleRepository.Companion.FIGHTER_PREFIX
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.features.streetfighter.ui.SfBitmapText
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog

@Composable
fun CollectiblesScreen(
    viewModel: CollectiblesViewModel,
    onBack: () -> Unit
) {
    val collectibles by viewModel.collectiblesList.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
    val columnsCount = if (isLandscape) 4 else 2

    // Estado para controlar qué coleccionable mostrar en el popup
    var selectedCollectible by remember { mutableStateOf<ActiveCollectible?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Respeta las barras del sistema (status + barra de navegación del teléfono)
                // para que "VOLVER AL MENÚ" no quede tapado por la barra de gestos/botones.
                .systemBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "INVENTARIO",
                fontSize = if (isLandscape) 28.sp else 36.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "COLECCIONABLES",
                fontSize = if (isLandscape) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37),
                letterSpacing = 8.sp
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 18.dp))

            // 🆕 (2026-07-21) DOS SECCIONES: los coleccionables del mundo (OBJETOS) y los
            // de PELEADOR, que se ganan venciéndolos en ARCADE (Difícil). Se distinguen por
            // el prefijo del id, así que no hizo falta migrar la tabla de Room.
            val fighters = collectibles.filter { it.id.startsWith(FIGHTER_PREFIX) }
            val objects = collectibles.filterNot { it.id.startsWith(FIGHTER_PREFIX) }
            var showFighters by remember { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTab(
                    text = stringResource(R.string.collectibles_tab_objects),
                    selected = !showFighters,
                    onClick = { showFighters = false },
                )
                SectionTab(
                    text = stringResource(R.string.collectibles_tab_fighters),
                    selected = showFighters,
                    onClick = { showFighters = true },
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            val shown = if (showFighters) fighters else objects
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(shown) { item ->
                    CollectibleCard(
                        item = item,
                        onClick = {
                            if (item.isCollected) {
                                // Convertir la entidad a un modelo ActiveCollectible para usar en el Dialog
                                selectedCollectible = ActiveCollectible(
                                    id = item.id,
                                    name = item.name,
                                    description = item.description,
                                    assetPath = item.assetPath,
                                    latitude = 0.0, // Irrelevante aquí
                                    longitude = 0.0 // Irrelevante aquí
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
            Button(
                onClick = onBack,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B1C3A),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.5f else 0.85f)
                    .height(56.dp)
                    .shadow(elevation = 8.dp, shape = shape)
            ) {
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.menu_back), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }

        // Mostrar el popup si hay un coleccionable seleccionado
        selectedCollectible?.let { collectible ->
            if (collectible.id.startsWith(FIGHTER_PREFIX)) {
                // 🆕 Los de PELEADOR llevan su propio diálogo con "VER HISTORIA".
                FighterStoryDialog(
                    collectible = collectible,
                    onDismiss = { selectedCollectible = null },
                )
            } else {
                CollectibleClaimDialog(
                    collectible = collectible,
                    onDismiss = { selectedCollectible = null } // Cerrar al hacer clic en continuar/fuera
                )
            }
        }
    }
}

/** Pestaña de sección (OBJETOS / PELEADORES) del inventario. */
@Composable
private fun SectionTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        maxLines = 1,
        softWrap = false,
        color = if (selected) Color(0xFF1A1016) else Color(0xFFD4AF37),
        modifier = Modifier
            .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
            .background(if (selected) Color(0xFFD4AF37) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/**
 * 🆕 (2026-07-21) Ficha del peleador coleccionado. La HISTORIA todavía no está escrita:
 * el botón queda visible pero anuncia "Próximamente" (implementación futura acordada con
 * el dueño), para que la pantalla ya muestre su sitio definitivo.
 */
@Composable
private fun FighterStoryDialog(collectible: ActiveCollectible, onDismiss: () -> Unit) {
    var showSoon by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .background(Color(0xFF1A1016))
                .border(2.dp, Color(0xFFD4AF37), CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 🆕 El NOMBRE va con la fuente arcade del modo pelea (pedido del dueño:
            // "las letras serán las mismas del SF"). Solo tiene A-Z/0-9: SfBitmapText
            // sanea el texto (acentos y signos fuera) antes de pintarlo.
            SfBitmapText(text = collectible.name, glyphHeight = 20.dp)
            Spacer(Modifier.height(12.dp))
            FighterPortrait(assetPath = collectible.assetPath)
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (showSoon) {
                    stringResource(R.string.collectibles_story_soon)
                } else {
                    collectible.description
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showSoon = true },
                shape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6B1C3A),
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.collectibles_view_story),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}

/**
 * Retrato del peleador recortado de su ATLAS de combate (celda 0 = idle-1, 256×256): así
 * no hace falta arte extra ni otro archivo en el APK. Se decodifica SOLO la región
 * necesaria con BitmapRegionDecoder (barato incluso en gama baja: no carga el atlas entero,
 * que llega a 2560×7680).
 */
@Composable
private fun FighterPortrait(assetPath: String) {
    val context = LocalContext.current
    val bmp = remember(assetPath) {
        runCatching {
            context.assets.open(assetPath).use { stream ->
                val decoder = android.graphics.BitmapRegionDecoder.newInstance(stream, false)
                    ?: return@use null
                decoder.decodeRegion(
                    android.graphics.Rect(0, 0, 256, 256),
                    BitmapFactory.Options().apply { inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 },
                )
            }
        }.getOrNull()
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
        )
    }
}

@Composable
fun CollectibleCard(item: CollectibleEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val bitmap = remember(item.isCollected, item.assetPath) {
        if (!item.isCollected) {
            null
        } else {
            try {
                context.assets.open(item.assetPath).use {
                    BitmapFactory.decodeStream(it).asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }

    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    val cardColor = if (item.isCollected) Color(0xFF6B1C3A) else Color(0xFF2A1C21)
    val borderColor = if (item.isCollected) Color(0xFFD4AF37) else Color.DarkGray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .background(cardColor, shape = shape)
            .border(2.dp, borderColor, shape = shape)
            .clip(shape) // Asegura que el ripple del clic respete la forma
            .clickable(enabled = item.isCollected, onClick = onClick) // Solo click si lo tiene
            .padding(16.dp)
            // Se redujo la altura ya que quitamos la descripción
            .height(140.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = if (item.isCollected) item.name else "???",
                modifier = Modifier.size(64.dp),
                colorFilter = if (!item.isCollected) ColorFilter.tint(Color.Black) else null
            )
        } else {
            Box(modifier = Modifier.size(64.dp).background(Color.Gray, CircleShape))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (item.isCollected) item.name.uppercase() else "???",
            color = if (item.isCollected) Color.White else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )
    }
}
