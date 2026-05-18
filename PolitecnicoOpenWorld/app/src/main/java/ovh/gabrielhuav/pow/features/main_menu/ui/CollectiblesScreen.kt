package ovh.gabrielhuav.pow.features.main_menu.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ovh.gabrielhuav.pow.data.local.room.entity.CollectibleEntity
import ovh.gabrielhuav.pow.domain.models.ActiveCollectible
import ovh.gabrielhuav.pow.features.main_menu.viewmodel.CollectiblesViewModel

@Composable
fun CollectiblesScreen(
    viewModel: CollectiblesViewModel,
    onBack: () -> Unit
) {
    val collectibles by viewModel.collectiblesList.collectAsState()
    val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(16.dp) // Opcional: usar windowInsetsPadding(WindowInsets.safeDrawing) si quieres soportar notches
    ) {
        // --- CABECERA ESTILO MAIN MENU ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp, top = 16.dp)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
            ) {
                Text("← VOLVER", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "INVENTARIO DE",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "COLECCIONABLES",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD4AF37), // Dorado POW
                    letterSpacing = 4.sp
                )
            }
        }

        // --- CUADRÍCULA CORREGIDA ---
        LazyVerticalGrid(
            // CAMBIO CLAVE: Adaptive ajusta automáticamente las columnas según el ancho de pantalla
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(collectibles) { item ->
                CollectibleCard(item)
            }
        }
    }
}

@Composable
fun CollectibleCard(item: CollectibleEntity) {
    val context = LocalContext.current
    val bitmap = remember(item.assetPath) {
        try {
            context.assets.open(item.assetPath).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)
    val cardColor = if (item.isCollected) Color(0xFF6B1C3A) else Color(0xFF1A0A0F) // Guinda vs Oscuro
    val borderColor = if (item.isCollected) Color(0xFFD4AF37) else Color.DarkGray

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(cardColor, shape = shape)
            .border(2.dp, borderColor, shape = shape)
            .padding(12.dp)
            .height(210.dp) // Altura para la tarjeta
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = if (item.isCollected) item.name else "Coleccionable desconocido",
                modifier = Modifier.size(70.dp),
                colorFilter = if (!item.isCollected) ColorFilter.tint(Color.Black) else null
            )
        } else {
            Box(modifier = Modifier.size(70.dp).background(Color.Gray, CircleShape))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (item.isCollected) item.name.uppercase() else "???",
            color = if (item.isCollected) Color.White else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (item.isCollected) {
            Text(
                text = item.description,
                color = Color.LightGray,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun CollectibleClaimDialog(
    collectible: ActiveCollectible,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(collectible.assetPath) {
        try {
            context.assets.open(collectible.assetPath).use {
                BitmapFactory.decodeStream(it).asImageBitmap()
            }
        } catch (e: Exception) { null }
    }

    Dialog(onDismissRequest = { /* Obligar a presionar botón */ }) {
        val bg = Brush.verticalGradient(listOf(Color(0xFF3B0D1B), Color(0xFF0D0D11)))
        val shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp)

        Box(
            modifier = Modifier
                // CAMBIO 1: Responsividad en el ancho
                .fillMaxWidth(0.9f)
                .widthIn(max = 400.dp)
                .background(bg, shape = shape)
                .border(2.dp, Color(0xFFD4AF37), shape)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                // CAMBIO 2: Agregar scroll vertical para que no se corte en Landscape
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "¡NUEVO COLECCIONABLE!",
                    color = Color(0xFFD4AF37),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center
                )

                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = collectible.name,
                        modifier = Modifier.size(110.dp)
                    )
                }

                Text(
                    text = collectible.name.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = collectible.description,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A), contentColor = Color.White),
                    shape = shape,
                    modifier = Modifier.fillMaxWidth().height(48.dp).shadow(8.dp, shape)
                ) {
                    Text("CONTINUAR", fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}