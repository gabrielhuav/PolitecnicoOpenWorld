package ovh.gabrielhuav.pow.features.streetfighter.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfSharedSet
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.concurrencia.PowCerrojo
import ovh.gabrielhuav.pow.platform.imagen.PowImagen
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido
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
// ═════════════════════════════════════════════════════════════════════════════════════════
// 🍏 FASE 5 — ESTE ARCHIVO YA ES MULTIPLATAFORMA.
//
// Era el que más `android.graphics` usaba (Bitmap, BitmapFactory, Canvas, Matrix). NO hizo falta
// `expect/actual`: `ImageBitmap` y el `Canvas` de Compose ya son comunes, así que el port fue
// traducir llamadas — la tabla de equivalencias está en `PowImagen`.
//
// DOS CAMBIOS DE COMPORTAMIENTO que hay que tener presentes:
//
//   1. **Ya no hay `recycle()`.** `ImageBitmap` no lo tiene: la memoria la gestiona el runtime.
//      Las llamadas viejas se BORRARON, no se les buscó equivalente.
//
//   2. **`sampleSize` SE CONSERVA**, pero via `decodificarReducido` (expect/actual) — es el unico
//      trozo de imagen que necesita codigo por plataforma. Sin el, la app de Android perderia el
//      `inSampleSize` y volveria el OOM en gama baja. Ver [SfSharedSheets.sheetFor].
// ═════════════════════════════════════════════════════════════════════════════════════════

object SfSharedSheets {

    private const val CELL = 256
    private const val COLS = 10
    private const val TARGET_H = 100f
    private const val FEET_Y = 224
    private const val CENTER_X = 128
    private const val CENTER_ANCHOR_Y = 170

    /** Entradas de caché: los 2 peleadores en pantalla + 1. Era el tamaño del `LruCache`. */
    private const val MAX_CACHE = 3

    // `LruCache` es de Android y no existe en común. Se emula con un mapa + una lista de uso;
    // con 3 entradas el coste de `remove`/`add` sobre la lista es irrelevante.
    private val cache = mutableMapOf<SfFighterId, ImageBitmap>()
    private val usoReciente = mutableListOf<SfFighterId>()

    private val cerrojo = PowCerrojo()

    /**
     * Hoja del peleador: EMPAQUETADA (assets, p. ej. Ryu/Ken/Prankedy) o COMPARTIDA (armada en
     * runtime desde el set del mundo). Llamar fuera del hilo de dibujo si se puede.
     *
     * 🆕 (2026-07-21) [sampleSize] > 1 decodifica el atlas DEDICADO a resolución reducida.
     *
     * Los atlas croma llegan a 2560×7168: en ARGB_8888 son ~73 MB de RAM **por peleador** (×2 en
     * pantalla). En gama baja eso provoca OOM y además muchas GPU antiguas ni siquiera aceptan
     * texturas de ese tamaño. Con `sampleSize = 2` bajan a ~18 MB y, como los sprites se pintan a
     * ~100 px dentro de una escena virtual de 382 px, la pérdida apenas se nota en un teléfono de
     * gama baja. Las cajas/orígenes del JSON se dividen por el mismo factor en la View (ver
     * `sheetSampleScale` en StreetFighterScreen).
     *
     * ⚠️ Al portar, esto estuvo a punto de perderse: el decodificador común de Compose NO expone
     * nada como `inSampleSize`, y dejarlo caer habría sido una regresión de memoria en la app que
     * está en producción. Por eso existe [decodificarReducido] como `expect/actual` — es el ÚNICO
     * trozo de imagen que necesita código por plataforma. Ver su KDoc: en Android no hay pico de
     * memoria, en iOS sí.
     *
     * ⚠️ El [sampleSize] solo afecta al atlas DEDICADO. Las hojas COMPARTIDAS se arman en runtime
     * a partir de sprites pequeños del mundo, así que ahí nunca hubo nada que reducir.
     */
    fun sheetFor(id: SfFighterId, sampleSize: Int = 1): ImageBitmap = cerrojo.ejecutar {
        val set = id.sharedSet
            ?: return@ejecutar decodificarReducido(PowAssets.bytes(id.spriteAsset), sampleSize)
        cache[id]?.let { tocar(id); return@ejecutar it }
        buildSharedSheet(set).also { guardar(id, it) }
    }

    /** Idle completo para el selector, recortado, orientado y normalizado a la misma altura. */
    fun previewFramesFor(set: SfSharedSet): List<ImageBitmap> = runCatching {
        normalizeAnim(loadAnim(set, "Idle"))
    }.getOrDefault(emptyList())

    // ═══════════════════════════════ caché LRU mínima ═══════════════════════════════

    private fun tocar(id: SfFighterId) {
        usoReciente.remove(id)
        usoReciente.add(id)
    }

    private fun guardar(id: SfFighterId, sheet: ImageBitmap) {
        cache[id] = sheet
        tocar(id)
        while (usoReciente.size > MAX_CACHE) {
            // El primero de la lista es el que hace más tiempo que no se toca.
            cache.remove(usoReciente.removeAt(0))
        }
    }

    // ══════════════════ armado de la hoja (port de gen_sf_frames_from_npc.py) ══════════════════

    private fun buildSharedSheet(set: SfSharedSet): ImageBitmap {
        // Animaciones fuente (recortadas a bbox + espejadas), NORMALIZADAS por animación.
        // Mismos fallbacks que el tool (ya normalizados: reusar la lista es seguro).
        val idle = normalizeAnim(loadAnim(set, "Idle"))
        var walk = normalizeAnim(loadAnim(set, "Walk"))
        var run = normalizeAnim(loadAnim(set, "Run"))
        var special = normalizeAnim(loadAnim(set, "Special"))
        require(idle.isNotEmpty()) { "Set compartido sin Idle/: ${set.basePath}${set.folder}" }
        if (walk.isEmpty()) walk = run.ifEmpty { idle }
        if (run.isEmpty()) run = walk
        if (special.isEmpty()) special = idle

        // Los 75 cuadros generados (los alias stun-1/2 y "jump-start/land" se resuelven al pegar).
        // scale = 1f: la normalización de tamaño ya se aplicó POR ANIMACIÓN arriba.
        val gen = buildGenFrames(idle, walk, run, special, scale = 1f)

        // Cuadros en el ORDEN del template ryu.json (mismo layout que SfFrameCatalog.remap)
        val order = SfFrameCatalog.templateFrameOrder()
        val rows = (order.size + COLS - 1) / COLS
        return PowImagen.lienzo(COLS * CELL, rows * CELL) {
            order.forEachIndexed { idx, key ->
                val cell = gen[aliasFor(key)] ?: return@forEachIndexed // hueco = celda transparente
                drawImage(
                    image = cell,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(cell.width, cell.height),
                    dstOffset = IntOffset((idx % COLS) * CELL, (idx / COLS) * CELL),
                    dstSize = IntSize(cell.width, cell.height),
                )
            }
        }
        // (Aquí antes iba `gen.values.forEach { it.recycle() }`: ver la nota 1 de la cabecera.)
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
    private fun normalizeAnim(frames: List<ImageBitmap>): List<ImageBitmap> {
        if (frames.isEmpty()) return frames
        val heights = frames.map { it.height }.sorted()
        val median = heights[heights.size / 2].toFloat()
        if (median <= 0f) return frames
        val s = TARGET_H / median
        return frames.map { f ->
            PowImagen.escalar(
                f,
                max(1, (f.width * s).roundToInt()),
                max(1, (f.height * s).roundToInt()),
            )
        }
    }

    /** Carga y prepara una animación fuente: orden numérico, bbox, flip y filtro de corruptos. */
    private fun loadAnim(set: SfSharedSet, action: String): List<ImageBitmap> {
        val dir = "${set.basePath}${set.folder}$action".trimEnd('/')
        // `PowAssets.listar` ya devuelve vacío si el directorio no existe — mismo contrato que el
        // `assets.list(dir) ?: return emptyList()` de antes (ver `PowAssetsTest`).
        val names = PowAssets.listar(dir)
        if (names.isEmpty()) return emptyList()
        val frames = names
            .filter { it.endsWith(".webp", true) || it.endsWith(".png", true) }
            .sortedBy { n -> Regex("_(\\d+)\\.\\w+$").find(n)?.groupValues?.get(1)?.toIntOrNull() ?: 0 }
            .mapNotNull { n -> runCatching { PowImagen.deAsset("$dir/$n") }.getOrNull() }
            .map { raw ->
                val cropped = PowImagen.recortarAOpaco(raw)
                if (set.flip) PowImagen.voltearHorizontal(cropped) else cropped
            }
        // Filtro de cuadros DEGENERADOS (fuentes corruptas): alto < 40% del máximo del set
        val hMax = frames.maxOfOrNull { it.height } ?: return frames
        return frames.filter { it.height >= hMax * 0.4f }
    }

    /** Los 75 cuadros con las MISMAS transformaciones del tool (rotaciones/aplastados). */
    private fun buildGenFrames(
        idle: List<ImageBitmap>,
        walk: List<ImageBitmap>,
        run: List<ImageBitmap>,
        special: List<ImageBitmap>,
        scale: Float,
    ): Map<String, ImageBitmap> {
        fun <T> pick(l: List<T>, i: Int): T = l[i % l.size]
        val out = mutableMapOf<String, ImageBitmap>()
        // rot = grados ANTIHORARIOS (semántica de PIL, como el tool)
        fun f(src: ImageBitmap, rot: Float = 0f, squashY: Float = 1f, dx: Int = 0, center: Boolean = false) =
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
        src: ImageBitmap,
        scale: Float,
        rotCcw: Float,
        squashY: Float,
        dx: Int,
        centerAnchor: Boolean,
    ): ImageBitmap {
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale * squashY).roundToInt())
        val escalado = PowImagen.escalar(src, w, h)
        // ⚠️ `PowImagen.rotar` YA niega el ángulo por dentro (PIL gira antihorario, Compose
        // horario). Aquí se le pasa el valor de PIL tal cual, igual que hacía el código de Android
        // con su `postRotate(-rotCcw)`. Negarlo otra vez aquí espejaría todas las reacciones.
        val img = if (rotCcw != 0f) PowImagen.rotar(escalado, rotCcw) else escalado
        val x = CENTER_X - img.width / 2 + dx
        val y = if (centerAnchor) CENTER_ANCHOR_Y - img.height / 2 else FEET_Y - img.height
        return PowImagen.lienzo(CELL, CELL) {
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(x, y),
                dstSize = IntSize(img.width, img.height),
            )
        }
    }
}
