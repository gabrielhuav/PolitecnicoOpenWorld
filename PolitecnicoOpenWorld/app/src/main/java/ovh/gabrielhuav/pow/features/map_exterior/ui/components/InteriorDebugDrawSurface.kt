package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import org.osmdroid.util.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CollisionPolygon
import ovh.gabrielhuav.pow.domain.models.map.CollisionWall
import ovh.gabrielhuav.pow.features.map_exterior.viewmodel.DebugEditTool
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

// ─── CAPA DE DIBUJO DEL EDITOR DEL DEBUG INTERIORES (Compose, sobre el mapa) ───
// Va POR ENCIMA del renderer del mapa (web/OSM/Google) en la jerarquía Compose, así que
// intercepta el toque ANTES que el mapa (sin importar el proveedor): por eso el mapa NO se
// mueve mientras dibujas. Convierte pantalla↔coordenadas con la proyección Web Mercator
// estándar (tiles 256 px × densidad), ASUMIENDO que el mapa está centrado en el jugador
// (en modo debug se fuerza el centrado cuando hay herramienta activa, ver renderers).
//   - WALL / NAV_PED / NAV_CAR → arrastre = LÍNEA · BLOCK → arrastre = RECTÁNGULO
// Solo intercepta el toque cuando hay herramienta activa (`tool != NONE`); con NONE deja
// pasar el gesto al mapa (para posicionar/zoom antes de dibujar).
@Composable
fun InteriorDebugDrawSurface(
    tool: DebugEditTool,
    walls: List<CollisionWall>,
    blocks: List<CollisionPolygon>,
    navPed: List<List<GeoPoint>>,
    navCar: List<List<GeoPoint>>,
    center: GeoPoint?,
    zoom: Double,
    onCommit: (DebugEditTool, List<GeoPoint>) -> Unit,
    modifier: Modifier = Modifier
) {
    if (center == null) return
    val density = LocalDensity.current.density
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Offset?>(null) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(LocalDensity.current) { maxWidth.toPx() }
        val hPx = with(LocalDensity.current) { maxHeight.toPx() }
        // Escala del mundo en píxeles de dispositivo: 256 × densidad × 2^zoom (slippy map).
        val scale = 256.0 * density * Math.pow(2.0, zoom)
        val centerWX = (center.longitude + 180.0) / 360.0 * scale
        val cLatRad = Math.toRadians(center.latitude)
        val centerWY = (1.0 - ln(tan(cLatRad) + 1.0 / cos(cLatRad)) / PI) / 2.0 * scale

        fun geoToOffset(lat: Double, lon: Double): Offset {
            val wx = (lon + 180.0) / 360.0 * scale
            val latR = Math.toRadians(lat)
            val wy = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * scale
            return Offset((wPx / 2.0 + (wx - centerWX)).toFloat(), (hPx / 2.0 + (wy - centerWY)).toFloat())
        }
        fun offsetToGeo(o: Offset): GeoPoint {
            val wx = centerWX + (o.x - wPx / 2.0)
            val wy = centerWY + (o.y - hPx / 2.0)
            val lon = wx / scale * 360.0 - 180.0
            val n = PI - 2.0 * PI * wy / scale
            val lat = Math.toDegrees(atan(sinh(n)))
            return GeoPoint(lat, lon)
        }

        val drawMod = if (tool != DebugEditTool.NONE) {
            // Consumimos DESDE el ACTION_DOWN para que el mapa (web/OSM/Google) NO reciba el
            // gesto y por tanto NO panee/zoom. awaitEachGesture nos da el ciclo down→move→up.
            Modifier.pointerInput(tool, center, zoom, wPx, hPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    start = down.position
                    current = down.position
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                        current = ch.position
                        ch.consume()
                        if (!ch.pressed) break
                    }
                    val s = start; val c = current
                    if (s != null && c != null) {
                        val gs = offsetToGeo(s); val gc = offsetToGeo(c)
                        val pts = if (tool == DebugEditTool.BLOCK)
                            listOf(
                                GeoPoint(gs.latitude, gs.longitude), GeoPoint(gs.latitude, gc.longitude),
                                GeoPoint(gc.latitude, gc.longitude), GeoPoint(gc.latitude, gs.longitude)
                            )
                        else listOf(gs, gc)
                        // Evita commitear un "punto" si fue un toque sin arrastre.
                        val moved = kotlin.math.abs(c.x - s.x) > 6f || kotlin.math.abs(c.y - s.y) > 6f
                        if (moved) onCommit(tool, pts)
                    }
                    start = null; current = null
                }
            }
        } else Modifier

        Canvas(modifier = Modifier.fillMaxSize().then(drawMod)) {
            val red = Color(0xFFDC0000); val redFill = Color(0x55DC2828)
            val green = Color(0xFF4CC850); val orange = Color(0xFFFF8C00)
            // Geometría ya dibujada (committed).
            blocks.forEach { poly ->
                if (poly.nodes.size >= 3) {
                    val path = Path()
                    poly.nodes.forEachIndexed { i, nd ->
                        val o = geoToOffset(nd.lat, nd.lon)
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    path.close()
                    drawPath(path, redFill)
                    drawPath(path, red, style = Stroke(width = 4f))
                }
            }
            walls.forEach { w ->
                drawLine(red, geoToOffset(w.lat1, w.lon1), geoToOffset(w.lat2, w.lon2), strokeWidth = 7f, cap = StrokeCap.Round)
            }
            navPed.forEach { p ->
                for (i in 0 until p.size - 1) {
                    drawLine(green, geoToOffset(p[i].latitude, p[i].longitude), geoToOffset(p[i + 1].latitude, p[i + 1].longitude), strokeWidth = 6f, cap = StrokeCap.Round)
                }
            }
            navCar.forEach { p ->
                for (i in 0 until p.size - 1) {
                    drawLine(orange, geoToOffset(p[i].latitude, p[i].longitude), geoToOffset(p[i + 1].latitude, p[i + 1].longitude), strokeWidth = 7f, cap = StrokeCap.Round)
                }
            }

            // Previsualización del trazo en curso.
            val s = start; val c = current
            if (s != null && c != null) {
                val col = when (tool) {
                    DebugEditTool.NAV_PED -> green
                    DebugEditTool.NAV_CAR -> orange
                    else -> red
                }
                if (tool == DebugEditTool.BLOCK) {
                    val tl = Offset(minOf(s.x, c.x), minOf(s.y, c.y))
                    val sz = Size(kotlin.math.abs(c.x - s.x), kotlin.math.abs(c.y - s.y))
                    drawRect(redFill, topLeft = tl, size = sz)
                    drawRect(col, topLeft = tl, size = sz, style = Stroke(width = 4f))
                } else {
                    drawLine(col, s, c, strokeWidth = 8f, cap = StrokeCap.Round)
                }
            }
        }
    }
}
