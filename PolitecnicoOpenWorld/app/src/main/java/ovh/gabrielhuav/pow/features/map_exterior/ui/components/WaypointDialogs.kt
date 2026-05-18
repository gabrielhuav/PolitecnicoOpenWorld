package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.domain.models.Waypoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddWaypointDialog(
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Waypoint", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se guardará tu posición actual con el nombre que elijas.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del waypoint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun WaypointListDialog(
    waypoints: List<Waypoint>,
    onGoTo: (Waypoint) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Place, null, tint = Color(0xFFFF6B35))
                Text("Mis Waypoints", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (waypoints.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Aún no tienes waypoints guardados.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(waypoints, key = { it.id }) { waypoint ->
                        WaypointItem(
                            waypoint = waypoint,
                            dateText = dateFormat.format(Date(waypoint.createdAt)),
                            onGoTo = { onGoTo(waypoint) },
                            onDelete = { onDelete(waypoint.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@Composable
private fun WaypointItem(
    waypoint: Waypoint,
    dateText: String,
    onGoTo: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFFFF6B35), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Place, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(waypoint.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateText, color = Color.Gray, fontSize = 11.sp)
        }

        IconButton(onClick = onGoTo, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MyLocation, "Ir al waypoint", tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, "Eliminar", tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
        }
    }
}
