package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.LandmarkAssetTemplate
import ovh.gabrielhuav.pow.domain.models.LandmarkCatalogManager

@Composable
fun AssetPickerDialog(
    context: Context,
    onAssetSelected: (LandmarkAssetTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var availableAssets by androidx.compose.runtime.remember {
        mutableStateOf(LandmarkCatalogManager.availableAssets)
    }

    LaunchedEffect(Unit) {
        if (availableAssets.isEmpty()) {
            withContext(Dispatchers.IO) {
                LandmarkCatalogManager.loadCatalog(context)
            }
            availableAssets = LandmarkCatalogManager.availableAssets
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar Edificio", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Selecciona un asset. Se colocará en tu posición actual.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (availableAssets.isEmpty()) {
                    Text(
                        "No hay assets disponibles.",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                } else {
                    availableAssets.forEach { template ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF2A1C21))
                                .clickable { onAssetSelected(template) }
                                .padding(12.dp)
                        ) {
                            Text(template.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                template.assetPath,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
