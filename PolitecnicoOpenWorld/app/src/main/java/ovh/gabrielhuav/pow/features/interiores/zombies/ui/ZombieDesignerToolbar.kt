package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.R
import ovh.gabrielhuav.pow.domain.models.zombie.DoorKind
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoomCatalog
import ovh.gabrielhuav.pow.domain.models.zombie.ZoneType
import ovh.gabrielhuav.pow.features.interiores.core.ui.CollisionMatrixDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.ui.InteriorNpcView
import ovh.gabrielhuav.pow.features.interiores.core.ui.PlayerView
import ovh.gabrielhuav.pow.features.interiores.core.ui.RemotePlayerView
import ovh.gabrielhuav.pow.features.interiores.core.ui.WaypointDesignerLayer
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.DesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.DesignerBrush
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.ZombieInteriorViewModel
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.exportMatricesToUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.exportWaypointsToUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.importMatricesFromUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.importWaypointsFromUri
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.moveSelectedDoorToWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.paintCellAtWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resetDesignerMatrix
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resetDesignerWaypoints
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.resizeDesignerMatrixBy
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.saveDesignerMatrix
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.saveDesignerWaypoints
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.selectDoorAtWorld
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.setDesignerBrush
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.setDesignerTarget
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.toggleDesignerMode
import ovh.gabrielhuav.pow.features.map_exterior.ui.SkinSelectorDialog
import ovh.gabrielhuav.pow.features.map_exterior.ui.ZombiVideoPlayer
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionMenuItem
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.OptionsMenu
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
// 🛠️ MODO DISEÑADOR del minijuego zombi (barra de herramientas)
//
// La barra con la que se pintan la matriz de colisión y los waypoints sobre el fondo de la sala.
// Es una herramienta interna: NO forma parte del juego del jugador.
// ⚠️ Lo que exporta debe seguir cuadrando FILA A FILA con el servidor zombi (ver 09 §11): el
// formato de collision_matrices.json es el que lee loadMatrixOverrides del servidor.
//
// ExtraÃ­do de StreetFighterScreen.kt (4029 lÃ­neas) en el refactor de tamaÃ±o de la Fase 5.
// Son composables/helpers TOP-LEVEL del mismo paquete: `internal` en vez de `private` para
// que la Screen los siga viendo. Sin cambios de comportamiento.
// â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

@Composable
internal fun DesignerToolbar(
    target: DesignerTarget,
    brush: DesignerBrush,
    dirty: Boolean,
    roomName: String,
    hasSelectedDoor: Boolean,
    gridCols: Int,
    gridRows: Int,
    onResize: (Int, Int) -> Unit,
    onSelectTarget: (DesignerTarget) -> Unit,
    onBrush: (DesignerBrush) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExit: () -> Unit,
    portrait: Boolean,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWaypoints = target == DesignerTarget.WAYPOINTS
    // El panel del diseñador es intrusivo: se puede MOVER (asa, arrástrala) y CAMBIAR DE TAMAÑO
    // (botones −/+, escala 0.5–1) para que no tape la sala mientras editas.
    var offX by remember { mutableFloatStateOf(0f) }
    var offY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    // En pantallas BAJAS (landscape) el panel no cabía y se recortaban "Guardar"/"Exportar":
    // limitamos su alto y lo hacemos DESPLAZABLE (scroll) para que SIEMPRE se alcancen todos.
    val toolbarScroll = rememberScrollState()
    val maxToolbarH = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    Column(
        modifier = modifier
            .offset { IntOffset(offX.roundToInt(), offY.roundToInt()) }
            .systemBarsPadding()
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)   // encoge desde abajo-centro
            }
            .padding(12.dp)
            .heightIn(max = maxToolbarH)
            // Más ANGOSTO (antes 0.96 = casi toda la pantalla, tapaba el mapa de lado a lado).
            // Ocupa ~55% del ancho → deja libre la mayor parte del mapa para pintar la matriz.
            .fillMaxWidth(0.55f)
            .background(Color(0xFF1E1E24).copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFD4AF37), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ─── ASA: arrastra para MOVER · toca para recentrar · −/+ cambia el TAMAÑO ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_move_handle),
                color = Color(0xFFFFD54F), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { _, drag ->
                            offX += drag.x * scale
                            offY += drag.y * scale
                        }
                    }
                    .clickable { offX = 0f; offY = 0f }
                    .padding(vertical = 6.dp)
            )
            // 🔁 Girar VERTICAL/HORIZONTAL (solo en diseñador). "↕" = pasar a vertical; "↔" = volver a
            // horizontal. El juego es horizontal por ruta; esta es una excepción local del diseñador.
            ToolButton(if (portrait) "↔" else "↕", false, Color(0xFF5C6BC0), Modifier.width(48.dp)) { onToggleOrientation() }
            ToolButton("−", false, Color(0xFF37474F), Modifier.width(48.dp)) { scale = (scale - 0.1f).coerceIn(0.5f, 1f) }
            ToolButton("+", false, Color(0xFF37474F), Modifier.width(48.dp)) { scale = (scale + 0.1f).coerceIn(0.5f, 1f) }
        }
        // CONTENIDO DESPLAZABLE = TODA la herramienta (selector, pincel PARED/BORRAR, tamaño,
        // Guardar/Exportar/Salir). Scrollea junta; solo el asa "⠿ Mover" de arriba queda fija.
        // El panel está acotado a maxToolbarH y es angosto/movible, así que cabe o se scrollea.
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(toolbarScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        Text(
            androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_designer_room, roomName.uppercase()),
            color = Color(0xFFD4AF37), fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        // Selector de objetivo: MATRIZ de colisión o WAYPOINTS (puertas).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_matrix), !isWaypoints, Color(0xFF3A86FF), Modifier.weight(1f)) { onSelectTarget(DesignerTarget.MATRIX) }
            ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_waypoints), isWaypoints, Color(0xFFD4AF37), Modifier.weight(1f)) { onSelectTarget(DesignerTarget.WAYPOINTS) }
        }
        Text(
            if (isWaypoints)
                (if (hasSelectedDoor) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_drag_door)
                 else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_touch_door))
            else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_grid_paint),
            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
        )
        // ─── PINCEL + TAMAÑO DE LA MATRIZ (TODO dentro del MISMO scroll) ──────────────
        // PARED (inaccesible) / BORRAR (caminable) y el resize (COL/FIL). Toda la herramienta
        // scrollea JUNTA; solo el asa "⠿ Mover" de arriba queda fija para poder arrastrar siempre.
        if (!isWaypoints) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_wall), brush == DesignerBrush.WALL, Color(0xFFD32F2F), Modifier.weight(1f)) { onBrush(DesignerBrush.WALL) }
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_occluder), brush == DesignerBrush.OCCLUDER, Color(0xFF4FC3F7), Modifier.weight(1f)) { onBrush(DesignerBrush.OCCLUDER) }
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_erase), brush == DesignerBrush.ERASE, Color(0xFF4CAF50), Modifier.weight(1f)) { onBrush(DesignerBrush.ERASE) }
            }
            Text(
                androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_size_grid, gridCols, gridRows),
                color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_col_minus), false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(-1, 0) }
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_col_plus), false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(1, 0) }
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_row_minus), false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(0, -1) }
                ToolButton(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_row_plus), false, Color(0xFF3A86FF), Modifier.weight(1f)) { onResize(0, 1) }
            }
        }
        } // ← cierra el Column SCROLLABLE: SOLO el bloque medio (selector/pincel/tamaño) scrollea
        // ─── ACCIONES ANCLADAS abajo, SIEMPRE visibles (FUERA del scroll): Guardar/Reset y
        // Exportar/Importar/Salir. Antes iban DENTRO del scroll y en horizontal (pantalla baja) se
        // ocultaban → el usuario no podía exportar. Ahora quedan fijas pase lo que pase.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(if (dirty) androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_save_unsaved) else androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.int_save), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B1C3A)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_reset), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onExport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_export), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onImport,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(8.dp)
            ) { Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_import), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onExit, modifier = Modifier.height(40.dp)) {
                Text(androidx.compose.ui.res.stringResource(ovh.gabrielhuav.pow.R.string.ig_exit), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Sube por la cadena de ContextWrapper hasta la Activity (para fijar la orientación del diseñador). */
internal fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
internal fun ToolButton(label: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) color else Color(0xFF2A1C21)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// MODO HISTORIA · sprite de suelo (lata apestosa / mochila de la M2, frasco de evidencia de la M3…).
// Carga el PNG del asset (submuestreado para gama baja), lo dibuja centrado al tamaño dado
// conservando su aspecto y, si aún no carga, cae al emoji de respaldo. Reemplaza los emojis
// 🥫/🎒/🧪 por assets propios (2026-07-10).

