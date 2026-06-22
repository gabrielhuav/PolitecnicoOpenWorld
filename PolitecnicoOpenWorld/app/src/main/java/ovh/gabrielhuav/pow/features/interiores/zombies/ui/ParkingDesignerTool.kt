package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ovh.gabrielhuav.pow.domain.models.map.CampusParkingCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import kotlin.math.roundToInt

/**
 * Herramienta de calibración EN VIVO del estacionamiento del lobby (separada del render de
 * escenografía, que vive en `ParkedCarsLayer`). Se abre desde el selector del botón "Diseñador" →
 * "Estacionamiento" (solo Modo Desarrollador). Edita una transformación de GRUPO sobre los autos
 * (rotar pivote, girar cada uno sobre su eje, mover fino/grueso, escalar) + voltear ↑↓ por auto, y
 * exporta la calibración. NO toca el render ni el exterior: solo emite intenciones (callbacks).
 */
@Composable
fun BoxScope.ParkingTuneTool(
    room: ZombieRoom,
    active: Boolean,
    angle: Float,
    onAngle: (Float) -> Unit,
    offX: Float,
    onOffX: (Float) -> Unit,
    offY: Float,
    onOffY: (Float) -> Unit,
    scale: Float,
    onScale: (Float) -> Unit,
    selfAngle: Float,
    onSelfAngle: (Float) -> Unit,
    flippedCount: Int,
    onClearFlips: () -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit
) {
    if (!active) return
    if (CampusParkingCatalog.forAsset(room.backgroundAsset) == null) return

    val rotBy: (Float) -> Unit = { d -> onAngle(((angle + d) % 360f + 360f) % 360f) }
    val selfRotBy: (Float) -> Unit = { d -> onSelfAngle(((selfAngle + d) % 360f + 360f) % 360f) }

    Column(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .systemBarsPadding()
            .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
            .background(Color(0xCC11131A), RoundedCornerShape(10.dp))
            .padding(8.dp)
            .widthIn(max = 184.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("ESTACIONAMIENTO", color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 12.sp)
        // ── ROTAR EL GRUPO (pivote alrededor del centro: mueve y orienta el conjunto) ──
        Text("rotar grupo  ${angle.roundToInt()}°", color = Color(0xFFAAB0B8), fontSize = 10.sp)
        Slider(
            value = angle, onValueChange = onAngle, valueRange = 0f..360f,
            modifier = Modifier.width(160.dp).height(22.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TuneBtn("-45") { rotBy(-45f) }
            TuneBtn("-1°") { rotBy(-1f) }
            TuneBtn("+1°") { rotBy(1f) }
            TuneBtn("+45") { rotBy(45f) }
            TuneBtn("180") { rotBy(180f) }
        }
        // ── GIRAR CADA AUTO sobre su PROPIO EJE (cambia orientación, NO posición) ──
        Text("girar c/auto  ${selfAngle.roundToInt()}°", color = Color(0xFFAAB0B8), fontSize = 10.sp)
        Slider(
            value = selfAngle, onValueChange = onSelfAngle, valueRange = 0f..360f,
            modifier = Modifier.width(160.dp).height(22.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TuneBtn("-45") { selfRotBy(-45f) }
            TuneBtn("-1°") { selfRotBy(-1f) }
            TuneBtn("+1°") { selfRotBy(1f) }
            TuneBtn("+45") { selfRotBy(45f) }
            TuneBtn("180") { selfRotBy(180f) }
        }
        // ── VOLTEAR ↑↓ POR AUTO (para islas: unos miran arriba, otros abajo) ──
        Text("toca un auto = voltear ↑↓  (${flippedCount})", color = Color(0xFFAAB0B8), fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TuneBtn("limpiar ↑↓") { onClearFlips() }
        }
        // ── MOVER EN GRUPO (fino + grueso) ──
        Text("mover", color = Color(0xFFAAB0B8), fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TuneBtn("←") { onOffX(offX - 0.004f) }
            TuneBtn("→") { onOffX(offX + 0.004f) }
            TuneBtn("↑") { onOffY(offY - 0.004f) }
            TuneBtn("↓") { onOffY(offY + 0.004f) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TuneBtn("◀") { onOffX(offX - 0.02f) }
            TuneBtn("▶") { onOffX(offX + 0.02f) }
            TuneBtn("▲") { onOffY(offY - 0.02f) }
            TuneBtn("▼") { onOffY(offY + 0.02f) }
        }
        Text("pos (${"%.3f".format(offX)}, ${"%.3f".format(offY)})", color = Color(0xFFAAB0B8), fontSize = 10.sp)
        // ── ESCALAR EN GRUPO (separar/juntar) ──
        Text("escala  x${"%.2f".format(scale)}", color = Color(0xFFAAB0B8), fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TuneBtn("− tam") { onScale((scale - 0.05f).coerceAtLeast(0.2f)) }
            TuneBtn("+ tam") { onScale((scale + 0.05f).coerceAtMost(5f)) }
        }
        // ── EXPORTAR / CERRAR ──
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onExport,
                modifier = Modifier.height(32.dp).widthIn(min = 86.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("EXPORTAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onClose,
                modifier = Modifier.height(32.dp).widthIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("CERRAR", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TuneBtn(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(30.dp).widthIn(min = 34.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2F3A)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
