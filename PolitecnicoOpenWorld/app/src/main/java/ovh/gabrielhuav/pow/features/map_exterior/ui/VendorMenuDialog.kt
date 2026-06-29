package ovh.gabrielhuav.pow.features.map_exterior.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun VendorMenuDialog(
    onBuyItem: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // Para que ocupe casi toda la pantalla en landscape
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.6f) // 60% del ancho de la pantalla (ideal para landscape)
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFFFBC02D), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TIENDA DEL CAMPUS",
                        color = Color(0xFFFBC02D),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }
                }
                
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 12.dp))

                // Lista de Items
                Text(
                    text = "Elige un consumible para recuperar salud:",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Item 1: Pizza
                VendorItemRow(
                    iconText = "🍕", 
                    name = "Rebanada de Pizza",
                    description = "Restaura la salud al máximo.",
                    price = "$15",
                    onClick = { onBuyItem("Pizza") }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Item 2: Refresco
                VendorItemRow(
                    iconText = "🥤", 
                    name = "Refresco Frío",
                    description = "Te refresca y cura tus heridas.",
                    price = "$12",
                    onClick = { onBuyItem("Refresco") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Espacio futuro para imágenes (placeholder)
                Text(
                    text = "(Si en el futuro agregas assets .webp/.png para los items, puedes cambiar el texto 🍕 por una Image() de Compose aquí)",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun VendorItemRow(
    iconText: String,
    name: String,
    description: String,
    price: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono (Emoji temporal, reemplazable por Image)
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = iconText, fontSize = 24.sp)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(text = description, color = Color.Gray, fontSize = 14.sp)
        }

        // Precio / Botón
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) {
            Text(text = price, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
