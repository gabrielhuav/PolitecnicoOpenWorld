package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GOLD = Color(0xFFD4AF37)

/** Una entrada del menú: acción directa o grupo (submenú anidado). */
sealed interface OptionEntry

/** Acción directa (una opción pulsable). */
data class OptionMenuItem(
    val label: String,
    val icon: ImageVector,
    val tint: Color = GOLD,
    val onClick: () -> Unit
) : OptionEntry

/** Grupo desplegable anidado ("menú dentro del menú"). */
data class OptionMenuGroup(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color = GOLD,
    // Puede contener acciones Y otros grupos ("menú de menús" a cualquier nivel).
    val items: List<OptionEntry>
) : OptionEntry

/**
 * Menú desplegable de opciones con submenús anidados (acordeón).
 *
 * Estado HOISTEADO al caller para permitir abrir/cerrar de forma programática
 * (p. ej. abrir el submenú "Mapa" automáticamente al arrastrar el mapa).
 *
 *  - [expanded]: si el menú principal está abierto.
 *  - [openGroupId]: id del único submenú abierto (acordeón). null = ninguno.
 *    Al abrir un submenú, el anterior se cierra automáticamente (lo gestiona el
 *    caller poniendo el nuevo id).
 */
@Composable
fun OptionsMenu(
    entries: List<OptionEntry>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    openGroupId: String?,
    onOpenGroupChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Estado de apertura de subgrupos PROFUNDOS (nivel >= 1). El nivel 0 sigue
    // controlado por openGroupId (acordeón hoisteado) para mantener compatibilidad
    // con los callers existentes.
    val openSub = remember { mutableStateMapOf<String, Boolean>() }
    val closeAll: () -> Unit = {
        onExpandedChange(false); onOpenGroupChange(null); openSub.clear()
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Botón principal (abre/cierra el menú de menús).
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.background(if (expanded) GOLD else Color.White.copy(alpha = 0.85f), CircleShape)
        ) {
            Icon(if (expanded) Icons.Default.Close else Icons.Default.Tune, "Opciones", tint = Color.Black)
        }

        if (expanded) {
            entries.forEach { entry ->
                OptionEntryRow(
                    entry = entry,
                    depth = 0,
                    isOpen = { id, d -> if (d == 0) id == openGroupId else openSub[id] == true },
                    toggle = { id, d ->
                        if (d == 0) onOpenGroupChange(if (id == openGroupId) null else id)
                        else openSub[id] = !(openSub[id] == true)
                    },
                    closeAll = closeAll
                )
            }
        }
    }
}

/**
 * Renderiza una entrada (acción o grupo) de forma RECURSIVA, soportando
 * "menús de menús" a cualquier profundidad. El nivel 0 usa el estado de acordeón
 * hoisteado (openGroupId); los niveles profundos usan estado interno (openSub).
 */
@Composable
private fun OptionEntryRow(
    entry: OptionEntry,
    depth: Int,
    isOpen: (String, Int) -> Boolean,
    toggle: (String, Int) -> Unit,
    closeAll: () -> Unit
) {
    when (entry) {
        is OptionMenuItem -> OptionRow(entry.icon, entry.label, entry.tint, depth = depth) {
            closeAll()
            entry.onClick()
        }
        is OptionMenuGroup -> {
            val open = isOpen(entry.id, depth)
            OptionRow(
                icon = entry.icon,
                label = entry.label,
                tint = entry.tint,
                depth = depth,
                highlighted = open,
                trailing = if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight
            ) {
                toggle(entry.id, depth)
            }
            if (open) {
                entry.items.forEach { sub ->
                    OptionEntryRow(sub, depth + 1, isOpen, toggle, closeAll)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    depth: Int = 0,
    highlighted: Boolean = false,
    trailing: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (highlighted) Color(0xFF2A2A33).copy(alpha = 0.97f) else Color(0xFF1E1E24).copy(alpha = 0.95f)
            )
            .border(1.dp, GOLD, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(start = (14 + depth * 14).dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Spacer(Modifier.width(2.dp))
            Icon(trailing, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
        }
    }
}
