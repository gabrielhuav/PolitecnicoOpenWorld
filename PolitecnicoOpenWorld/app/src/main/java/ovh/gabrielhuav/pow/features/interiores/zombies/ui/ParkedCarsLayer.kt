package ovh.gabrielhuav.pow.features.interiores.zombies.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.ai.LandmarkNavGraph
import ovh.gabrielhuav.pow.domain.models.map.CampusParking
import ovh.gabrielhuav.pow.domain.models.map.CampusParkingCatalog
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.parkingSlots
import ovh.gabrielhuav.pow.domain.models.zombie.ZombieRoom
import ovh.gabrielhuav.pow.features.interiores.core.viewmodel.CameraTransform
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.VehicleSpriteManager
import java.io.InputStreamReader
import kotlin.math.atan2

// ─── Ajustes visuales (tunables) ────────────────────────────────────────────────────────────
// Huella del auto en METROS. Se escala a píxeles con baseWidthMeters del campus para igualar el
// tamaño aparente del exterior. Súbelo/bájalo si los autos se ven grandes/chicos en el lobby.
private const val CAR_FOOTPRINT_METERS = 6.5f
// Paleta determinista de colores de carrocería (se reparte por índice de plaza).
private val CAR_PALETTE = intArrayOf(
    0xFFBFBFBF.toInt(), 0xFF2E5A88.toInt(), 0xFF8B2E2E.toInt(),
    0xFF3A6B3A.toInt(), 0xFFC9A227.toInt(), 0xFF2B2B2B.toInt()
)

/**
 * Un auto aparcado ya resuelto: posición fraccionaria 0-1 sobre el asset y su bitmap YA con la
 * orientación correcta. ⚠️ Los sprites de coche son FRAMES DIRECCIONALES pre-renderizados (48 por
 * modelo): el exterior pide el FRAME del ángulo (`getTintedCarNpc(headingAngle…)`). Antes el
 * interior pedía el frame 0 y lo giraba con `Modifier.rotate(...)` → TODOS los autos quedaban con
 * la orientación del frame 0 girada en plano ("mal colocados" vs el exterior). Ahora el interior
 * resuelve el MISMO frame direccional que el exterior y NO rota en Compose (fix 2026-07-04).
 */
private data class ParkedCar(val xFrac: Float, val yFrac: Float, val bitmap: ImageBitmap)

/**
 * Dibuja los autos "presentes" del estacionamiento en el LOBBY interior (escenografía pura), en las
 * posiciones del navGraph del campus (slots `isParkingSlot`, ver [CampusParkingCatalog]). Cada auto
 * arranca con la rotación BASE que deriva el mapa global, y opcionalmente se transforma como un GRUPO
 * estilo PowerPoint para calibrar (la UI vive aparte en `ParkingTuneTool`):
 *  - [headingDeg]: rota el grupo alrededor de su centroide (posiciones + orientación).
 *  - [selfRotationDeg]: gira cada auto sobre su propio eje (orientación, no posición).
 *  - [offsetXFrac]/[offsetYFrac]: traslada el grupo. [scale]: separa/junta respecto al centroide.
 *  - [flipped]: índices volteados 180° (↑↓ por isla); al diseñar, tocar un auto alterna su volteo.
 * Si el room no es un campus con estacionamiento, no dibuja nada.
 */
@Composable
fun ParkedCarsLayer(
    room: ZombieRoom,
    cam: CameraTransform,
    onScreen: (Float, Float) -> Boolean,
    headingDeg: Float,
    offsetXFrac: Float,
    offsetYFrac: Float,
    scale: Float,
    selfRotationDeg: Float,
    designing: Boolean,
    flipped: Set<Int>,
    onToggleFlip: (Int) -> Unit
) {
    val campus = remember(room.backgroundAsset) { CampusParkingCatalog.forAsset(room.backgroundAsset) }
    val context = LocalContext.current
    val density = LocalDensity.current

    // El bitmap depende del FACING final (frame direccional), así que el prefetch se re-hace si
    // cambia la calibración de orientación (heading/selfRotation/flips). En juego normal esos
    // valores son constantes (defaults del catálogo) → un solo prefetch, como antes.
    val cars by produceState(
        emptyList<ParkedCar>(), campus?.navGraphAsset, headingDeg, selfRotationDeg, flipped
    ) {
        value = if (campus == null) emptyList()
        else withContext(Dispatchers.IO) {
            buildParkedCars(context, campus, headingDeg, selfRotationDeg, flipped)
        }
    }
    if (campus == null || cars.isEmpty()) return

    // Centroide del grupo (frac), para rotar/escalar el conjunto como un rígido.
    val centroid = remember(cars) {
        val n = cars.size.toFloat()
        Pair(
            cars.sumOf { it.xFrac.toDouble() }.toFloat() / n,
            cars.sumOf { it.yFrac.toDouble() }.toFloat() / n
        )
    }

    val carWorldPx = (CAR_FOOTPRINT_METERS / campus.baseWidthMeters) * room.worldWidth
    val rad = Math.toRadians(headingDeg.toDouble())
    val cosA = Math.cos(rad).toFloat()
    val sinA = Math.sin(rad).toFloat()
    val cWX = centroid.first * room.worldWidth
    val cWY = centroid.second * room.worldHeight

    cars.forEachIndexed { i, car ->
        // Posición relativa al centroide (px de mundo) → escala → rotación de grupo → traslación.
        val relX = (car.xFrac * room.worldWidth - cWX) * scale
        val relY = (car.yFrac * room.worldHeight - cWY) * scale
        val worldX = cWX + (relX * cosA - relY * sinA) + offsetXFrac * room.worldWidth
        val worldY = cWY + (relX * sinA + relY * cosA) + offsetYFrac * room.worldHeight
        // Al diseñar NO culleamos: el lote se ve 100% poblado aunque muevas/escales el grupo fuera del
        // viewport. En juego normal sí culleamos los autos fuera de pantalla (rendimiento).
        if (!designing && !onScreen(worldX, worldY)) return@forEachIndexed

        val screenX = cam.offsetX + worldX * cam.scale
        val screenY = cam.offsetY + worldY * cam.scale
        val sizePx = carWorldPx * cam.scale
        // La ORIENTACIÓN ya viene HORNEADA en el bitmap (frame direccional resuelto en
        // buildParkedCars, igual que el exterior). Aquí NO se rota nada.
        Image(
            bitmap = car.bitmap,
            contentDescription = null,
            modifier = Modifier
                .absoluteOffset(
                    x = with(density) { (screenX - sizePx / 2f).toDp() },
                    y = with(density) { (screenY - sizePx / 2f).toDp() }
                )
                .size(with(density) { sizePx.toDp() })
                // Al diseñar: TOCAR un auto lo voltea 180° (marca ↑ vs ↓).
                .then(if (designing) Modifier.pointerInput(i) { detectTapGestures { onToggleFlip(i) } } else Modifier)
        )
    }
}

/**
 * Carga el navGraph del campus, extrae sus plazas y resuelve un auto por cada una (modelo/color
 * deterministas por índice + el FRAME DIRECCIONAL del facing final, IGUAL que el exterior:
 * base del carril + grupo + giro propio + volteo). Bloqueante: invócalo en [Dispatchers.IO].
 */
private fun buildParkedCars(
    context: Context, campus: CampusParking,
    headingDeg: Float, selfRotationDeg: Float, flipped: Set<Int>
): List<ParkedCar> {
    val navGraph = try {
        context.assets.open(campus.navGraphAsset).use { ins ->
            Gson().fromJson(InputStreamReader(ins), LandmarkNavGraph::class.java)
        }
    } catch (e: Exception) {
        return emptyList()
    } ?: return emptyList()

    val slots = navGraph.parkingSlots()
    if (slots.isEmpty()) return emptyList()

    val models = CarModel.entries

    return slots.mapIndexedNotNull { i, slot ->
        val model = models[i % models.size]
        val color = CAR_PALETTE[(i * 5) % CAR_PALETTE.size]
        // Rotación BASE = la MISMA que deriva el global (sentido del carril nodo previo→cajón), en el
        // marco del PNG SIN rotar (aspecto baseW/baseH). Coincide numéricamente con el
        // `rotationAngle` que calcula NpcAiManager.spawnParkedCar para el mismo cajón.
        val baseFacing = Math.toDegrees(
            atan2((slot.dirY * campus.baseHeightMeters).toDouble(), (slot.dirX * campus.baseWidthMeters).toDouble())
        ).toFloat()
        // Facing FINAL (base + calibración de grupo/propio + volteo) → se pide el FRAME direccional
        // de ese ángulo, exactamente como hace el exterior. NADA se rota después en Compose.
        val facing = baseFacing + headingDeg + selfRotationDeg + (if (i in flipped) 180f else 0f)
        // Tintado (palette-swap píxel a píxel) a MENOR resolución (0.5×): son escenografía pequeña en
        // el lobby, no necesitan resolución completa. Reduce ~4× el trabajo por plaza (estaba en 1.0×,
        // serializado por @Synchronized → tardaba en "verse bien" la imagen 2D al entrar/zoom — R4).
        val drawable = VehicleSpriteManager.getTintedCarNpc(context, facing, color, 0.5f, model)
        val bitmap = (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap() ?: return@mapIndexedNotNull null
        ParkedCar(slot.localX, slot.localY, bitmap)
    }
}
