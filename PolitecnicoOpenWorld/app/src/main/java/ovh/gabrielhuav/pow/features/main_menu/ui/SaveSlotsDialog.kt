package ovh.gabrielhuav.pow.features.main_menu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.data.repository.SaveGameRepository
import ovh.gabrielhuav.pow.data.repository.SaveSlotSummary
import ovh.gabrielhuav.pow.domain.models.campaign.SchoolCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SaveSlotsMode { LOAD, SAVE }

// Diálogo de selección de SLOT de guardado. 7 slots: 2 de AUTO-GUARDADO (reservados, NO se
// puede guardar manualmente en ellos) + 5 MANUALES. Sirve para CARGAR (slots vacíos
// deshabilitados) y para GUARDAR (auto-slots deshabilitados; manuales habilitados). Muestra
// escuela, fecha, tipo (manual/autoguardado) y ubicación (mapa global o interior). Permite
// ELIMINAR cualquier partida (con confirmación). `summariesProvider` se relee tras borrar.
@Composable
fun SaveSlotsDialog(
    title: String,
    summariesProvider: () -> List<SaveSlotSummary>,
    mode: SaveSlotsMode,
    onPick: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
    // Si true, OCULTA los slots de AUTO-GUARDADO (reservados). Útil en "Nueva partida": como no
    // son seleccionables, mejor no mostrarlos para que el usuario no intente picarlos.
    hideAutoSlots: Boolean = false
) {
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    var summaries by remember { mutableStateOf(summariesProvider()) }
    var confirmDeleteSlot by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())
            ) {
                val visibleSummaries = if (hideAutoSlots)
                    summaries.filter { !SaveGameRepository.AUTO_SLOTS.contains(it.slot) }
                else summaries
                visibleSummaries.forEach { s ->
                    val isAuto = SaveGameRepository.AUTO_SLOTS.contains(s.slot)
                    val schoolName = s.schoolId?.let { id ->
                        SchoolCatalog.schools.firstOrNull { it.id == id }?.displayName ?: id
                    }
                    val kindTag = if (isAuto) "Auto" else "Manual"
                    val label = if (s.exists)
                        "Slot ${s.slot} ($kindTag) · $schoolName\n${fmt.format(Date(s.savedAt))}"
                    else
                        "Slot ${s.slot} ($kindTag) · (vacío)"
                    // Tipo de guardado del contenido (por compatibilidad con guardados antiguos).
                    val typeText = when {
                        isAuto -> "🔒 Autoguardado (reservado)"
                        s.saveType == "AUTO" -> "🔄 Autoguardado"
                        else -> "💾 Guardado manual"
                    }
                    val locationText = if (s.interiorRoomId != null) {
                        val roomName = ZombieRoomCatalog.roomById(s.interiorRoomId!!)?.displayName ?: s.interiorRoomId
                        "📍 Interior: $roomName"
                    } else {
                        "🗺️ Mapa global"
                    }
                    // GUARDAR: solo se puede en slots MANUALES. CARGAR: solo slots con partida.
                    val pickEnabled = when (mode) {
                        SaveSlotsMode.SAVE -> !isAuto
                        SaveSlotsMode.LOAD -> s.exists
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onPick(s.slot) },
                            enabled = pickEnabled,
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
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
                                Text(typeText, color = Color(0xFF80CBC4), fontSize = 10.sp)
                                if (s.exists) {
                                    Text(locationText, color = Color(0xFFB0BEC5), fontSize = 10.sp)
                                }
                                if (mode == SaveSlotsMode.SAVE && s.exists && !isAuto) {
                                    Text("Sobrescribir", color = Color(0xFFFFCC80), fontSize = 10.sp)
                                }
                            }
                        }
                        // Botón ELIMINAR (solo si hay partida en el slot).
                        if (s.exists) {
                            Spacer(Modifier.width(6.dp))
                            TextButton(onClick = { confirmDeleteSlot = s.slot }) {
                                Text("🗑", fontSize = 18.sp)
                            }
                        }
                    }

                    // Confirmación de borrado en línea para este slot.
                    if (confirmDeleteSlot == s.slot) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("¿Eliminar esta partida?", color = Color(0xFFFFAB91), fontSize = 12.sp)
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                onDelete(s.slot)
                                summaries = summariesProvider()
                                confirmDeleteSlot = null
                            }) { Text("Sí", color = Color(0xFFEF5350)) }
                            TextButton(onClick = { confirmDeleteSlot = null }) { Text("No") }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
