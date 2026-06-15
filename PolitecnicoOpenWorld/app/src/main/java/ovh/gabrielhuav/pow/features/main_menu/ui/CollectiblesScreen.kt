package ovh.gabrielhuav.pow.features.main_menu.ui

import android.content.res.Configuration
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible // Asegura que esta ruta sea correcta
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.CollectibleClaimDialog // Importar el Dialog

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

            Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(collectibles) { item ->
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
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.menu_return), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
        }

        // Mostrar el popup si hay un coleccionable seleccionado
        selectedCollectible?.let { collectible ->
            CollectibleClaimDialog(
                collectible = collectible,
                onDismiss = { selectedCollectible = null } // Cerrar al hacer clic en continuar/fuera
            )
        }
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
