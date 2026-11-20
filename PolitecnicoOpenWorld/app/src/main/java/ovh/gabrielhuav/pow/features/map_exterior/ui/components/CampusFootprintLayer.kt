package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CampusFootprint
import ovh.gabrielhuav.pow.domain.models.map.FootprintKind
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

// Capa cenital estilizada (estilo Chinatown Wars): colores planos, contorno grueso, paredes y sombra
// para dar volumen. Va SOBRE el mapa (cualquier renderer) y tapa los edificios del raster.
// Cada edificio se GRABA una vez en su propia GraphicsLayer a un zoom de referencia (BAKE_ZOOM) y cada
// cuadro solo se le aplica escala+traslación según el zoom/centro: sin reconstruir paths por cuadro
// (la versión que los dibujaba cada cuadro costaba ~13 ms en el Motorola One Fusion). El edificio que
// contiene al jugador se oculta con un fundido (como GTA): su techo no tapa lo que hay dentro.

private const val BAKE_ZOOM = 20.0
private const val TILT = 1f // cuánto "se levanta" un edificio en pantalla por metro de altura visual
/**
 * Altura visual máxima del volumen. ⚠️ La colisión usa la huella en el suelo: si el techo se levantara los ~10 m reales,
 * el jugador frenaría en el borde de la huella pero se vería ENCIMA del techo dibujado y cruzando sus paredes.
 * Con ~1.5 m el techo dibujado casi coincide con lo que frena.
 */
private const val MAX_VISUAL_RISE_METERS = 1.5
private const val BAKE_MARGIN_PX = 64
private const val LAYER_MARGIN_PX = 16f
private const val LAYER_ALPHA = 0.8f
private const val FADE_MILLIS = 250
private const val MAX_VERTICES = 128 // los techos del campus llegan a ~50 vértices; más se truncarían
/**
 * Extras caros del prototipo, APAGADOS por defecto (en el Motorola cuestan ~5 ms y ~3 ms por cuadro):
 * bit 1 = contornos, 2 = sombra. El bit 4 oculta las paredes y el 8 pinta el suelo (solo para medir).
 *
 * ⚠️ El suelo (estacionamiento, canchas, parques) NO se pinta por defecto: es opaco y tapaba por completo el
 * mapa, los coches y las líneas del estacionamiento (la pantalla entera quedaba de un gris plano).
 */
@Volatile
var campusFootprintDebugStyle: Int = 0

private val ROOF = Color(0xFFF5E6C8)
private val ROOF_LOW = Color(0xFFD9CDB0)
private val WALL = Color(0xFF8A7656) // sin contorno: la pared más oscura separa el volumen
private val OUTLINE = Color(0xFF1E1B2E)
private val SHADOW = Color(0x66000000)
private val PITCH = Color(0xFF4FA35B)
private val PARKING = Color(0xFF8A8F98)
private val PARK = Color(0xFF7DBB6A)

/** Huella con sus vértices ya en "mundo unitario" Mercator (0..1), listo para escalar por zoom. */
private class ProjectedFootprint(val src: CampusFootprint) {
    val n = src.points.size
    val wx = DoubleArray(n) { (src.points[it].longitude + 180.0) / 360.0 }
    val wy = DoubleArray(n) {
        val latR = Math.toRadians(src.points[it].latitude)
        (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0
    }

    /** Punto dentro del polígono (rayo), en el mismo espacio unitario Mercator; sin asignaciones. */
    fun contains(px: Double, py: Double): Boolean {
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            if ((wy[i] > py) != (wy[j] > py) && px < (wx[j] - wx[i]) * (py - wy[i]) / (wy[j] - wy[i]) + wx[i]) inside = !inside
            j = i
        }
        return inside
    }
}

@Composable
fun CampusFootprintLayer(
    footprints: List<CampusFootprint>,
    center: GeoPoint?,
    zoom: Double,
    modifier: Modifier = Modifier,
) {
    if (center == null || footprints.isEmpty()) return
    val density = LocalDensity.current.density
    val projected = remember(footprints) { footprints.map { ProjectedFootprint(it) } }
    val centerState = rememberUpdatedState(center)
    val zoomState = rememberUpdatedState(zoom)

    // Edificio que contiene al jugador (-1 = ninguno): su capa se funde a transparente al entrar y vuelve al salir.
    val playerX = (center.longitude + 180.0) / 360.0
    val playerLatR = Math.toRadians(center.latitude)
    val playerY = (1.0 - ln(tan(playerLatR) + 1.0 / cos(playerLatR)) / PI) / 2.0
    var hiddenIdx = -1
    for (i in projected.indices) {
        val k = projected[i].src.kind
        if ((k == FootprintKind.BUILDING || k == FootprintKind.ROOF) && projected[i].contains(playerX, playerY)) {
            hiddenIdx = i
            break
        }
    }
    var fadingIdx by remember { mutableIntStateOf(-1) } // último edificio ocultado: es el que se funde al salir
    SideEffect { if (hiddenIdx >= 0) fadingIdx = hiddenIdx }
    val fade by animateFloatAsState(if (hiddenIdx >= 0) 0f else 1f, tween(FADE_MILLIS), label = "campusRoofFade")
    val fadeState = rememberUpdatedState(fade)
    val fadingState = rememberUpdatedState(fadingIdx)

    Box(
        modifier.fillMaxSize().drawWithCache {
            val s0 = 256.0 * density * 2.0.pow(BAKE_ZOOM)
            var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            for (pf in projected) for (i in 0 until pf.n) {
                minX = minOf(minX, pf.wx[i]); maxX = maxOf(maxX, pf.wx[i])
                minY = minOf(minY, pf.wy[i]); maxY = maxOf(maxY, pf.wy[i])
            }
            val refLat = Math.toRadians(footprints.first().points.first().latitude)
            val mppBake = 156543.03392 * cos(refLat) / 2.0.pow(BAKE_ZOOM) / density
            val maxRisePx = (footprints.maxOf { minOf(it.heightMeters, MAX_VISUAL_RISE_METERS) } / mppBake).toFloat() * TILT
            val originX = minX * s0 - BAKE_MARGIN_PX
            val originY = minY * s0 - BAKE_MARGIN_PX - maxRisePx
            // El grosor de línea en el zoom de juego (22) debe ser ~2.2 dp: se graba a escala 1/4.
            val lineW = (0.55f * density).coerceAtLeast(1f)

            // Una capa por huella, del tamaño de ESE edificio (null = no se dibuja: el suelo va apagado por defecto).
            // ⚠️ Nunca grabar cada capa con el tamaño de todo el campus (~15000×10000 px): con alpha Android pide un
            // búfer fuera de pantalla de ese tamaño y el Motorola llegó a reiniciarse. Por eso además el alpha se
            // modula por dibujo (ModulateAlpha) y no se compone en un búfer.
            val layers = arrayOfNulls<GraphicsLayer>(projected.size)
            val layerLeft = FloatArray(projected.size)
            val layerTop = FloatArray(projected.size)
            val path = Path()
            val sx = FloatArray(MAX_VERTICES)
            val sy = FloatArray(MAX_VERTICES)
            for ((idx, pf) in projected.withIndex()) {
                val isGround = pf.src.kind != FootprintKind.BUILDING && pf.src.kind != FootprintKind.ROOF
                if (isGround && (campusFootprintDebugStyle and 8) == 0) continue
                val n = minOf(pf.n, sx.size)
                val rise = (minOf(pf.src.heightMeters, MAX_VISUAL_RISE_METERS) / mppBake).toFloat() * TILT
                var bMinX = Float.MAX_VALUE; var bMaxX = -Float.MAX_VALUE
                var bMinY = Float.MAX_VALUE; var bMaxY = -Float.MAX_VALUE
                for (i in 0 until n) {
                    val x = (pf.wx[i] * s0 - originX).toFloat()
                    val y = (pf.wy[i] * s0 - originY).toFloat()
                    bMinX = minOf(bMinX, x); bMaxX = maxOf(bMaxX, x)
                    bMinY = minOf(bMinY, y); bMaxY = maxOf(bMaxY, y)
                }
                // Margen para la sombra (hacia el sureste) y las paredes (hacia arriba).
                val left = kotlin.math.floor(bMinX - LAYER_MARGIN_PX)
                val top = kotlin.math.floor(bMinY - rise - LAYER_MARGIN_PX)
                val width = ceil(bMaxX + rise + LAYER_MARGIN_PX - left).toInt().coerceAtLeast(1)
                val height = ceil(bMaxY + rise + LAYER_MARGIN_PX - top).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    sx[i] = (pf.wx[i] * s0 - originX).toFloat() - left
                    sy[i] = (pf.wy[i] * s0 - originY).toFloat() - top
                }
                layerLeft[idx] = left
                layerTop[idx] = top
                val layer = obtainGraphicsLayer()
                layers[idx] = layer
                layer.compositingStrategy = CompositingStrategy.ModulateAlpha
                layer.alpha = LAYER_ALPHA
                layer.record(this, layoutDirection, IntSize(width, height)) {
                    when (pf.src.kind) {
                        FootprintKind.PARK -> fillPoly(path, sx, sy, n, 0f, PARK, null, lineW)
                        FootprintKind.PARKING -> fillPoly(path, sx, sy, n, 0f, PARKING, OUTLINE, lineW)
                        FootprintKind.PITCH -> fillPoly(path, sx, sy, n, 0f, PITCH, OUTLINE, lineW)
                        FootprintKind.BUILDING, FootprintKind.ROOF -> drawVolume(path, sx, sy, n, rise, lineW,
                            if (pf.src.kind == FootprintKind.ROOF) ROOF_LOW else ROOF)
                    }
                }
            }

            onDrawBehind {
                val c = centerState.value
                val scale = 256.0 * density * 2.0.pow(zoomState.value)
                val cLatR = Math.toRadians(c.latitude)
                val cwx = (c.longitude + 180.0) / 360.0 * scale
                val cwy = (1.0 - ln(tan(cLatR) + 1.0 / cos(cLatR)) / PI) / 2.0 * scale
                val k = (scale / s0).toFloat()
                val tx = (size.width / 2.0 + originX * k - cwx).toFloat()
                val ty = (size.height / 2.0 + originY * k - cwy).toFloat()
                val fading = fadingState.value
                val fadeValue = fadeState.value
                withTransform({
                    translate(tx, ty)
                    scale(k, k, pivot = Offset.Zero)
                }) {
                    for (i in layers.indices) {
                        val layer = layers[i] ?: continue
                        layer.alpha = if (i == fading) LAYER_ALPHA * fadeValue else LAYER_ALPHA
                        if (layer.alpha > 0.01f) translate(layerLeft[i], layerTop[i]) { drawLayer(layer) }
                    }
                }
            }
        },
    )
}

private fun DrawScope.fillPoly(path: Path, xs: FloatArray, ys: FloatArray, n: Int, dy: Float, fill: Color, line: Color?, lineW: Float) {
    path.rewind()
    path.moveTo(xs[0], ys[0] - dy)
    for (i in 1 until n) path.lineTo(xs[i], ys[i] - dy)
    path.close()
    drawPath(path, fill)
    if (line != null && (campusFootprintDebugStyle and 1) != 0) drawPath(path, line, style = Stroke(width = lineW, join = StrokeJoin.Round))
}

private fun DrawScope.drawVolume(path: Path, xs: FloatArray, ys: FloatArray, n: Int, rise: Float, lineW: Float, roofColor: Color) {
    // Sombra proyectada al sureste, bajo el edificio.
    path.rewind()
    path.moveTo(xs[0] + rise * 0.55f, ys[0] + rise * 0.35f)
    for (i in 1 until n) path.lineTo(xs[i] + rise * 0.55f, ys[i] + rise * 0.35f)
    path.close()
    if ((campusFootprintDebugStyle and 2) != 0) drawPath(path, SHADOW)

    // Orientación en pantalla (y hacia abajo) para saber qué paredes dan al sur (visibles).
    var area2 = 0f
    for (i in 0 until n) {
        val j = if (i + 1 == n) 0 else i + 1
        area2 += xs[i] * ys[j] - xs[j] * ys[i]
    }
    val s = if (area2 > 0f) 1f else -1f
    for (i in 0 until n) {
        val j = if (i + 1 == n) 0 else i + 1
        val ny = -(xs[j] - xs[i]) * s // componente y de la normal exterior
        if (ny <= 0f || (campusFootprintDebugStyle and 4) != 0) continue // pared que da al norte: la tapa el techo
        path.rewind()
        path.moveTo(xs[i], ys[i])
        path.lineTo(xs[j], ys[j])
        path.lineTo(xs[j], ys[j] - rise)
        path.lineTo(xs[i], ys[i] - rise)
        path.close()
        drawPath(path, WALL)
        if ((campusFootprintDebugStyle and 1) != 0) drawPath(path, OUTLINE, style = Stroke(width = lineW * 0.7f, join = StrokeJoin.Round))
    }
    fillPoly(path, xs, ys, n, rise, roofColor, OUTLINE, lineW)
}
