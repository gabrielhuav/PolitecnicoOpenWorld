package ovh.gabrielhuav.pow.features.streetfighter.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.LruCache
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfSharedSet
import kotlin.math.max
import kotlin.math.roundToInt

// ASSETS COMPARTIDOS entre el mundo abierto y "HUELUM VS. GOYA" (2026-07-15).
//
// Los peleadores con `SfFighterId.sharedSet != null` NO tienen sprite sheet propio en el APK:
// su hoja de pelea se ARMA AQUÍ EN RUNTIME desde el MISMO set de sprites que usa el mundo
// (SPRITES/PLAYER/<skin>{Idle,Walk,Run,Special}/ o SPRITES/NPC/<Char>/{Idle,...}/). Es el
// port Kotlin de tools/gen_sf_frames_from_npc.py + pack_sf_character.py (misma técnica, mismas
// poses aproximadas ALPHA): un solo juego de assets alimenta AMBAS modalidades.
//
// TAMAÑOS HETEROGÉNEOS — la normalización vive en el CÓDIGO: los sets fuente tienen lienzos
// distintos (robot 256², lázaro 338×422, escomgirl hasta 542×681…). Aquí NO importa: cada
// cuadro se recorta a su bbox opaco y CADA ANIMACIÓN se normaliza por su altura mediana a
// TARGET_H=100 px, con pies en (128,224) del lienzo 256². Es el mismo estándar que el packer
// fuerza para sheets dedicados: compartidos y dedicados miden igual en la UI de pelea.
//
// Memoria (09 §6): la hoja armada mide 2560×2048 (≈20 MB ARGB) — IGUAL que decodificar los
// PNG empaquetados que había antes; cache LRU de 3 (los 2 peleadores en pantalla + 1).
// Costo de armado: una vez por peleador por sesión (~decenas de ms), al elegirlo.

object SfSharedSheets {

    private const val CELL = 256
    private const val COLS = 10
    private const val TARGET_H = 100f
    private const val FEET_Y = 224
    private const val CENTER_X = 128
    private const val CENTER_ANCHOR_Y = 170

    private val cache = LruCache<SfFighterId, Bitmap>(3)

    /**
     * Hoja del peleador: EMPAQUETADA (assets, p. ej. Ryu/Ken/Prankedy) o COMPARTIDA
     * (armada en runtime desde el set del mundo). Llamar fuera del hilo de dibujo si se puede.
     */
    @Synchronized
    fun sheetFor(context: Context, id: SfFighterId): Bitmap {
        val set = id.sharedSet
            ?: return context.assets.open(id.spriteAsset).use { BitmapFactory.decodeStream(it) }
        cache.get(id)?.let { return it }
        val sheet = buildSharedSheet(context, set)
        cache.put(id, sheet)
        return sheet
    }

    /** Preview para el selector: 1er cuadro del Idle del SET fuente (con flip si aplica). */
    fun previewFor(context: Context, set: SfSharedSet): Bitmap? = runCatching {
        val raw = context.assets.open("${set.basePath}${set.folder}Idle/${set.prefix}i_1.webp")
            .use { BitmapFactory.decodeStream(it) } ?: return null
        if (set.flip) flipHorizontal(raw) else raw
    }.getOrNull()

    // ══════════════════ armado de la hoja (port de gen_sf_frames_from_npc.py) ══════════════════

    private fun buildSharedSheet(context: Context, set: SfSharedSet): Bitmap {
        // Animaciones fuente (recortadas a bbox + espejadas), NORMALIZADAS por animación.
        // Mismos fallbacks que el tool (ya normalizados: reusar la lista es seguro).
        val idle = normalizeAnim(loadAnim(context, set, "Idle"))
        var walk = normalizeAnim(loadAnim(context, set, "Walk"))
        var run = normalizeAnim(loadAnim(context, set, "Run"))
        var special = normalizeAnim(loadAnim(context, set, "Special"))
        require(idle.isNotEmpty()) { "Set compartido sin Idle/: ${set.basePath}${set.folder}" }
        if (walk.isEmpty()) walk = run.ifEmpty { idle }
        if (run.isEmpty()) run = walk
        if (special.isEmpty()) special = idle

        // Los 75 cuadros generados (los alias stun-1/2 y "jump-start/land" se resuelven al pegar).
        // scale = 1f: la normalización de tamaño ya se aplicó POR ANIMACIÓN arriba.
        val gen = buildGenFrames(idle, walk, run, special, scale = 1f)

        // Cuadros en el ORDEN del template ryu.json (mismo layout que SfFrameCatalog.remap)
        val order = SfFrameCatalog.templateFrameOrder(context)
        val rows = (order.size + COLS - 1) / COLS
        val sheet = Bitmap.createBitmap(COLS * CELL, rows * CELL, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        order.forEachIndexed { idx, key ->
            val cell = gen[aliasFor(key)] ?: return@forEachIndexed // hueco = celda transparente
            canvas.drawBitmap(cell, ((idx % COLS) * CELL).toFloat(), ((idx / COLS) * CELL).toFloat(), null)
        }
        gen.values.forEach { if (!it.isRecycled) it.recycle() }
        return sheet
    }

    /** Mapeo de alias del packer: stun-1/2 reusan stun-3; "jump-start/land" = su cuadro único. */
    private fun aliasFor(key: String): String = when (key) {
        "jump-start/land" -> "jump-start-land-1"
        "stun-1", "stun-2" -> "stun-3"
        else -> key
    }

    /**
     * 🆕 El "IF DE TAMAÑOS" POR ANIMACIÓN (2026-07-16): antes había UNA escala por personaje
     * (medida del idle) y si el set fuente traía OTRA animación dibujada a otra escala
     * (lienzos/zoom distintos por acción — pasa en los sets de PLAYER: lázaro idle 338×422
     * vs run 256², escomgirl run 542×681…), la figura CRECÍA/ENCOGÍA al caminar/correr/
     * atacar. Ahora CADA animación se mide (mediana de alturas de sus cuadros, robusta a
     * outliers; los corruptos ya se filtraron) y se escala a TARGET_H: la figura mide lo
     * MISMO en todas las animaciones, vengan del tamaño que vengan. El movimiento natural
     * DENTRO de una animación (rebote al correr) se conserva porque la escala es por
     * animación, no por cuadro.
     */
    private fun normalizeAnim(frames: List<Bitmap>): List<Bitmap> {
        if (frames.isEmpty()) return frames
        val heights = frames.map { it.height }.sorted()
        val median = heights[heights.size / 2].toFloat()
        if (median <= 0f) return frames
        val s = TARGET_H / median
        return frames.map { f ->
            val w = max(1, (f.width * s).roundToInt())
            val h = max(1, (f.height * s).roundToInt())
            Bitmap.createScaledBitmap(f, w, h, true)
        }
    }

    /** Carga y prepara una animación fuente: orden numérico, bbox, flip y filtro de corruptos. */
    private fun loadAnim(context: Context, set: SfSharedSet, action: String): List<Bitmap> {
        val dir = "${set.basePath}${set.folder}$action".trimEnd('/')
        val names = runCatching { context.assets.list(dir)?.toList() }.getOrNull() ?: return emptyList()
        val frames = names
            .filter { it.endsWith(".webp", true) || it.endsWith(".png", true) }
            .sortedBy { n -> Regex("_(\\d+)\\.\\w+$").find(n)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
            .mapNotNull { n ->
                runCatching {
                    context.assets.open("$dir/$n").use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            .map { raw ->
                val cropped = cropToOpaque(raw)
                if (set.flip) flipHorizontal(cropped) else cropped
            }
        // Filtro de cuadros DEGENERADOS (fuentes corruptas): alto < 40% del máximo del set
        val hMax = frames.maxOfOrNull { it.height } ?: return frames
        return frames.filter { it.height >= hMax * 0.4f }
    }

    /** Los 75 cuadros con las MISMAS transformaciones del tool (rotaciones/aplastados). */
    private fun buildGenFrames(
        idle: List<Bitmap>,
        walk: List<Bitmap>,
        run: List<Bitmap>,
        special: List<Bitmap>,
        scale: Float,
    ): Map<String, Bitmap> {
        fun <T> pick(l: List<T>, i: Int): T = l[i % l.size]
        val out = mutableMapOf<String, Bitmap>()
        // rot = grados ANTIHORARIOS (semántica de PIL, como el tool)
        fun f(src: Bitmap, rot: Float = 0f, squashY: Float = 1f, dx: Int = 0, center: Boolean = false) =
            makeFrame(src, scale, rot, squashY, dx, center)

        out["idle-1"] = f(pick(idle, 0)); out["idle-2"] = f(pick(idle, 1))
        out["idle-3"] = f(pick(idle, 2)); out["idle-4"] = f(pick(idle, 1))
        for (i in 0 until 6) {
            out["forwards-${i + 1}"] = f(pick(walk, i))
            out["backwards-${i + 1}"] = f(pick(walk, walk.size - 1 - (i % walk.size)))
        }
        out["jump-start-land-1"] = f(pick(idle, 0), squashY = 0.85f)
        for (i in 0 until 6) out["jump-up-${i + 1}"] = f(pick(run, i))
        // Voltereta: cuadro de carrera rotado (giro hacia adelante, sentido horario)
        for (i in 0 until 7) out["jump-roll-${i + 1}"] = f(pick(run, 2), rot = -(i * 51f), center = true)
        out["crouch-1"] = f(pick(idle, 0), squashY = 0.85f)
        out["crouch-2"] = f(pick(idle, 1), squashY = 0.70f)
        out["crouch-3"] = f(pick(idle, 2), squashY = 0.58f)
        for (i in 0 until 3) {
            out["idle-turn-${i + 1}"] = f(pick(idle, 2 - i))
            out["crouch-turn-${i + 1}"] = f(pick(idle, 2 - i), squashY = 0.58f)
        }
        // Ataques (aproximados con Special)
        out["light-punch-1"] = f(pick(special, 0)); out["light-punch-2"] = f(pick(special, 1))
        out["med-punch-1"] = f(pick(special, 0)); out["med-punch-2"] = f(pick(special, 1))
        out["med-punch-3"] = f(pick(special, 2))
        out["heavy-punch-1"] = f(pick(special, special.size - 1))
        out["light-kick-1"] = f(pick(special, 1)); out["light-kick-2"] = f(pick(special, 2))
        out["med-kick-1"] = f(pick(special, special.size - 1))
        for (i in 0 until 5) out["heavy-kick-${i + 1}"] = f(pick(special, i))
        // Reacciones (idle inclinado/aplastado)
        out["hit-face-1"] = f(pick(idle, 0), rot = 8f, dx = -4)
        out["hit-face-2"] = f(pick(idle, 1), rot = 12f, dx = -6)
        out["hit-face-3"] = f(pick(idle, 2), rot = 14f, dx = -8)
        out["hit-face-4"] = f(pick(idle, 1), rot = 10f, dx = -6)
        out["hit-stomach-1"] = f(pick(idle, 1), rot = -6f, dx = -4, squashY = 0.92f)
        out["hit-stomach-2"] = f(pick(idle, 1), rot = -10f, dx = -6, squashY = 0.88f)
        out["hit-stomach-3"] = f(pick(idle, 2), rot = -12f, dx = -6, squashY = 0.85f)
        out["hit-stomach-4"] = f(pick(idle, 2), rot = -8f, dx = -4, squashY = 0.85f)
        out["stun-3"] = f(pick(idle, 1), rot = 16f)
        out["fall-1"] = f(pick(idle, 0), rot = 20f, dx = -6)
        out["fall-2"] = f(pick(idle, 0), rot = 45f, dx = -10)
        out["fall-3"] = f(pick(idle, 0), rot = 70f, dx = -14)
        out["fall-4"] = f(pick(idle, 0), rot = 90f)
        out["fall-5"] = f(pick(idle, 0), rot = 90f)
        // Victoria / especial
        for (i in 0 until 4) {
            out["victory-${i + 1}"] = f(pick(special, i))
            out["special-${i + 1}"] = f(pick(special, i))
        }
        return out
    }

    /** Escala/aplasta/rota y ancla un cuadro fuente en un lienzo 256² (pies en 128,224). */
    private fun makeFrame(
        src: Bitmap,
        scale: Float,
        rotCcw: Float,
        squashY: Float,
        dx: Int,
        centerAnchor: Boolean,
    ): Bitmap {
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale * squashY).roundToInt())
        var img = Bitmap.createScaledBitmap(src, w, h, true)
        if (rotCcw != 0f) {
            // PIL rota ANTIHORARIO con ángulo positivo; Matrix.postRotate es HORARIO → negar
            val m = Matrix().apply { postRotate(-rotCcw) }
            img = Bitmap.createBitmap(img, 0, 0, img.width, img.height, m, true)
        }
        val canvas = Bitmap.createBitmap(CELL, CELL, Bitmap.Config.ARGB_8888)
        val x = CENTER_X - img.width / 2 + dx
        val y = if (centerAnchor) CENTER_ANCHOR_Y - img.height / 2 else FEET_Y - img.height
        Canvas(canvas).drawBitmap(img, x.toFloat(), y.toFloat(), null)
        return canvas
    }

    /** Recorta al rectángulo con píxeles opacos (equivalente a Image.getbbox de PIL). */
    private fun cropToOpaque(bmp: Bitmap): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            val rowBase = y * w
            for (x in 0 until w) {
                if ((px[rowBase + x] ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return bmp
        return Bitmap.createBitmap(bmp, minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    private fun flipHorizontal(bmp: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(-1f, 1f) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
