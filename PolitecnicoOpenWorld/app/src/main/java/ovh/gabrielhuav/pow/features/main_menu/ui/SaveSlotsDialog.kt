package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.data.repository.SaveSlotSummary
import ovh.gabrielhuav.pow.domain.models.SchoolCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SaveSlotsMode { LOAD, SAVE }

// Diálogo de selección de SLOT de guardado. Sirve para CARGAR (slots vacíos deshabilitados)
// y para GUARDAR (todos habilitados; los llenos muestran "Sobrescribir"). Muestra escuela
// y fecha de cada slot.
@Composable
fun SaveSlotsDialog(
    title: String,
    summaries: List<SaveSlotSummary>,
    mode: SaveSlotsMode,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summaries.forEach { s ->
                    val schoolName = s.schoolId?.let { id ->
                        SchoolCatalog.schools.firstOrNull { it.id == id }?.displayName ?: id
                    }
                    val label = if (s.exists)
                        "Slot ${s.slot} · $schoolName\n${fmt.format(Date(s.savedAt))}"
                    else
                        "Slot ${s.slot} · (vacío)"
                    val enabled = mode == SaveSlotsMode.SAVE || s.exists
                    Button(
                        onClick = { onPick(s.slot) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (s.exists) Color(0xFF37474F) else Color(0xFF263238)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (s.exists) FontWeight.Bold else FontWeight.Normal
                            )
                            if (mode == SaveSlotsMode.SAVE && s.exists) {
                                Text("Sobrescribir", color = Color(0xFFFFCC80), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
